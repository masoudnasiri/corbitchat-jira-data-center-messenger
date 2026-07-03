package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.corbitlogic.jira.internalmessenger.ao.JimMobileDevice;
import com.corbitlogic.jira.internalmessenger.event.JimEventExecutor;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileAvatars;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.mobile.MobilePreferences;
import com.corbitlogic.jira.internalmessenger.mobile.push.MobilePushEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link JimMobilePushService}. Orchestrates feature checks, dedupe and
 * per-device delivery; the actual HTTP transport lives in {@link JimMobileFcmClient}.
 */
public class JimMobilePushServiceImpl implements JimMobilePushService {

    private static final Logger log = LoggerFactory.getLogger(JimMobilePushServiceImpl.class);

    /** Suppress identical (dedupeKey) sends within this window. */
    private static final long DEDUPE_WINDOW_MS = 45_000L;
    private static final int DEDUPE_MAX_ENTRIES = 5_000;
    private static final int PREVIEW_MAX = 140;

    private final JimMobileDeviceService deviceService;
    private final JimMobileFcmClient fcmClient;
    private final JimMobileFeatureService featureService;
    private final JimMobilePreferenceService preferenceService;
    private final JimEventExecutor eventExecutor;

    private final ConcurrentHashMap<String, Long> dedupeCache = new ConcurrentHashMap<>();
    private final AtomicLong sentCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();
    private final AtomicLong skippedFeatureCount = new AtomicLong();
    private final AtomicLong skippedPrefsCount = new AtomicLong();
    private volatile String lastSendOutcome = "none";
    private volatile long lastSendAt = 0L;

    public JimMobilePushServiceImpl(JimMobileDeviceService deviceService,
                                    JimMobileFcmClient fcmClient,
                                    JimMobileFeatureService featureService,
                                    JimMobilePreferenceService preferenceService,
                                    JimEventExecutor eventExecutor) {
        this.deviceService = deviceService;
        this.fcmClient = fcmClient;
        this.featureService = featureService;
        this.preferenceService = preferenceService;
        this.eventExecutor = eventExecutor;
    }

    @Override
    public void sendToUser(String userKey, MobilePushEvent event) {
        if (userKey == null || event == null) {
            return;
        }
        try {
            if (!this.fcmClient.isConfigured()) {
                // No credentials: do not fake success. Recorded in diagnostics.
                this.lastSendOutcome = "not_configured";
                return;
            }
            if (isDuplicate(event.getDedupeKey())) {
                log.debug("event=mobilepush stage=dedupe outcome=suppressed type={}", event.getEventType());
                return;
            }
            ApplicationUser user = userManager().getUserByKey(userKey);
            if (user == null || !user.isActive()) {
                return;
            }
            // Sprint 04G: never deliver a push for a feature the user cannot access.
            String feature = JimMobileFeatures.normalize(event.getFeature());
            if (feature != null && !this.featureService.isAllowed(user, feature)) {
                this.skippedFeatureCount.incrementAndGet();
                this.lastSendOutcome = "feature_blocked";
                log.debug("event=mobilepush stage=feature_gate outcome=blocked feature={}", feature);
                return;
            }
            // Sprint 05B: per-user notification preferences (category gating).
            MobilePreferences prefs = loadPrefs(userKey);
            if (!isCategoryEnabled(prefs, event.getCategory())) {
                this.skippedPrefsCount.incrementAndGet();
                this.lastSendOutcome = "prefs_disabled";
                log.debug("event=mobilepush stage=prefs_gate outcome=blocked category={}", event.getCategory());
                return;
            }
            List<JimMobileDevice> devices = this.deviceService.activeDevices(userKey);
            if (devices.isEmpty()) {
                return;
            }
            String notificationId = UUID.randomUUID().toString();
            // Render the safe, per-preference display payload (data-only).
            Map<String, String> data = renderData(event, prefs, notificationId);
            this.eventExecutor.submit("mobile_push", () -> deliver(userKey, devices, data));
        } catch (RuntimeException ex) {
            log.warn("event=mobilepush stage=enqueue outcome=error message={}", ex.getMessage());
        }
    }

    private void deliver(String userKey, List<JimMobileDevice> devices, Map<String, String> data) {
        for (JimMobileDevice device : devices) {
            String token = this.deviceService.decryptToken(device);
            if (token == null) {
                this.deviceService.recordSendResult(device, false, true);
                continue;
            }
            // Data-only: the app renders the rich local notification itself.
            JimMobileFcmClient.SendResult result = this.fcmClient.send(token, data, null, null, false);
            switch (result) {
                case SUCCESS:
                    this.sentCount.incrementAndGet();
                    this.lastSendOutcome = "success";
                    this.deviceService.recordSendResult(device, true, false);
                    break;
                case UNREGISTERED:
                    this.deviceService.recordSendResult(device, false, true);
                    this.lastSendOutcome = "unregistered";
                    break;
                case NOT_CONFIGURED:
                    this.lastSendOutcome = "not_configured";
                    break;
                default:
                    this.failedCount.incrementAndGet();
                    this.lastSendOutcome = result.name().toLowerCase();
                    this.deviceService.recordSendResult(device, false, false);
            }
            this.lastSendAt = System.currentTimeMillis();
        }
    }

    @Override
    public boolean isConfigured() {
        return this.fcmClient.isConfigured();
    }

    @Override
    public void configure(Map<String, Object> body) {
        this.fcmClient.configure(body);
    }

    @Override
    public void clearConfig() {
        this.fcmClient.clearConfig();
    }

    @Override
    public String sendTestToToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return "no_token";
        }
        if (!this.fcmClient.isConfigured()) {
            return "not_configured";
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("eventType", "test");
        data.put("ts", String.valueOf(System.currentTimeMillis()));
        JimMobileFcmClient.SendResult result = this.fcmClient.send(token.trim(), data, "CorbitHub", "Test push");
        this.lastSendOutcome = "test_" + result.name().toLowerCase();
        this.lastSendAt = System.currentTimeMillis();
        if (result == JimMobileFcmClient.SendResult.SUCCESS) {
            this.sentCount.incrementAndGet();
        }
        return result.name();
    }

    @Override
    public Map<String, Object> diagnostics() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("configured", this.fcmClient.isConfigured());
        map.put("projectId", this.fcmClient.getProjectId());
        map.put("activeDevices", this.deviceService.countActiveDevices());
        map.put("sentCount", this.sentCount.get());
        map.put("failedCount", this.failedCount.get());
        map.put("skippedFeatureBlocked", this.skippedFeatureCount.get());
        map.put("skippedPrefsDisabled", this.skippedPrefsCount.get());
        map.put("lastSendOutcome", this.lastSendOutcome);
        map.put("lastSendAt", this.lastSendAt);
        return map;
    }

    // ----- preferences + rendering -------------------------------------------

    private MobilePreferences loadPrefs(String userKey) {
        try {
            return this.preferenceService.getForUser(userKey);
        } catch (RuntimeException ex) {
            // Never block a push because prefs could not be read; use safe defaults.
            return MobilePreferences.defaults();
        }
    }

    private static boolean isCategoryEnabled(MobilePreferences prefs, String category) {
        if (prefs == null || !prefs.isPushEnabled()) {
            return false;
        }
        if (MobilePushEvent.CAT_CHAT.equals(category)) {
            return prefs.isChatPushEnabled();
        }
        if (MobilePushEvent.CAT_TASK.equals(category)) {
            return prefs.isTaskPushEnabled();
        }
        if (MobilePushEvent.CAT_MENTION.equals(category)) {
            return prefs.isMentionPushEnabled();
        }
        if (MobilePushEvent.CAT_REMINDER.equals(category)) {
            return prefs.isReminderPushEnabled();
        }
        // Unknown category: governed only by the master switch.
        return true;
    }

    /**
     * Builds the minimal, privacy-safe FCM {@code data} payload from the event
     * ingredients according to the recipient's detail level, preview toggle and
     * avatar toggle. Never emits issue summaries/descriptions/comments; a raw chat
     * preview is included only in {@code preview} mode with preview enabled.
     */
    private Map<String, String> renderData(MobilePushEvent event, MobilePreferences prefs, String notificationId) {
        Map<String, String> data = event.baseRoutingData(notificationId);
        String category = event.getCategory();
        if (category != null) {
            data.put("channel", category);
        }
        String detail = prefs.getDetailLevel();
        boolean generic = MobilePreferences.DETAIL_GENERIC.equals(detail);
        boolean keyOnly = MobilePreferences.DETAIL_KEY_ONLY.equals(detail);

        String title;
        String body;
        String sender = null;
        String avatarPath = null;

        if (MobilePushEvent.CAT_CHAT.equals(category)) {
            sender = trimToNull(event.getActorName());
            if (generic) {
                title = "CorbitHub";
                body = "New message";
            } else {
                title = sender != null ? sender : "New message";
                if (!keyOnly && prefs.isShowMessagePreview() && trimToNull(event.getChatPreview()) != null) {
                    body = truncate(event.getChatPreview(), PREVIEW_MAX);
                } else {
                    body = "New message";
                }
            }
            if (prefs.isShowSenderAvatar() && trimToNull(event.getActorUserKey()) != null) {
                avatarPath = JimMobileAvatars.userPath(event.getActorUserKey());
            }
        } else if (MobilePushEvent.CAT_REMINDER.equals(category)) {
            title = "CorbitHub";
            String fallback = trimToNull(event.getGenericBody()) != null ? event.getGenericBody() : "Task reminder";
            if (generic) {
                body = fallback;
            } else {
                body = trimToNull(event.getSummaryText()) != null ? event.getSummaryText() : fallback;
            }
        } else {
            // task / mention / other issue events
            boolean mention = MobilePushEvent.CAT_MENTION.equals(category);
            String key = trimToNull(event.getIssueKey());
            String actor = trimToNull(event.getActorName());
            String fallback = trimToNull(event.getGenericBody()) != null
                    ? event.getGenericBody() : (mention ? "New notification" : "Task update");
            if (generic) {
                title = "CorbitHub";
                body = fallback;
            } else if (keyOnly) {
                title = key != null ? key : (actor != null ? actor : "CorbitHub");
                body = mention ? "You were mentioned"
                        : (key != null ? key + " updated" : fallback);
            } else {
                title = key != null ? key : (actor != null ? actor : "CorbitHub");
                body = trimToNull(event.getSummaryText()) != null ? event.getSummaryText()
                        : (mention ? "You were mentioned" : fallback);
            }
            if (prefs.isShowSenderAvatar() && trimToNull(event.getActorUserKey()) != null) {
                avatarPath = JimMobileAvatars.userPath(event.getActorUserKey());
            }
        }

        data.put("title", title);
        data.put("body", body);
        if (sender != null) {
            data.put("sender", sender);
        }
        if (avatarPath != null) {
            data.put("avatarPath", avatarPath);
        }
        return data;
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String truncate(String v, int max) {
        String t = v.trim();
        return t.length() > max ? t.substring(0, max) + "\u2026" : t;
    }

    // ----- dedupe -------------------------------------------------------------

    private boolean isDuplicate(String dedupeKey) {
        if (dedupeKey == null || dedupeKey.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        pruneDedupe(now);
        Long previous = this.dedupeCache.putIfAbsent(dedupeKey, now);
        if (previous == null) {
            return false;
        }
        if (now - previous > DEDUPE_WINDOW_MS) {
            this.dedupeCache.put(dedupeKey, now);
            return false;
        }
        return true;
    }

    private void pruneDedupe(long now) {
        if (this.dedupeCache.size() < DEDUPE_MAX_ENTRIES) {
            return;
        }
        this.dedupeCache.entrySet().removeIf(e -> now - e.getValue() > DEDUPE_WINDOW_MS);
    }

    private static UserManager userManager() {
        return ComponentAccessor.getUserManager();
    }
}
