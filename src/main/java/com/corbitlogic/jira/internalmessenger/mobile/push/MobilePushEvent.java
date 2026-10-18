package com.corbitlogic.jira.internalmessenger.mobile.push;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A privacy-aware mobile push event (Sprint 05 / 05B).
 *
 * <p>The event carries the <em>semantic ingredients</em> of a notification —
 * sender/actor display name, an optional raw chat preview, a safe non-chat
 * summary, an issue key, routing identifiers — plus a {@link #getCategory()
 * category} used for per-user category gating. The push sender
 * ({@code JimMobilePushService}) renders the final, minimal FCM {@code data}
 * payload from these ingredients <b>according to the recipient's notification
 * preferences</b> (detail level, message-preview toggle, avatar toggle).</p>
 *
 * <p>The payload delivered to a device never contains issue summaries,
 * descriptions, comments, custom field values, or attachments. A raw chat
 * {@link #getChatPreview() preview} is included only when the recipient has
 * enabled preview mode for chat.</p>
 */
public final class MobilePushEvent {

    // Canonical event types (kept in sync with the Flutter deep-link router).
    public static final String CHAT_MESSAGE = "chat_message";
    public static final String ISSUE_ASSIGNMENT = "issue_assignment";
    public static final String ISSUE_STATUS = "issue_status";
    public static final String ISSUE_MENTION = "issue_mention";
    public static final String REMINDER_TODAY = "reminder_today";
    public static final String REMINDER_OVERDUE = "reminder_overdue";

    // Categories used for per-user push category gating + notification channels.
    public static final String CAT_CHAT = "chat";
    public static final String CAT_TASK = "task";
    public static final String CAT_MENTION = "mention";
    public static final String CAT_REMINDER = "reminder";

    private final String eventType;
    private final String feature;
    private final String category;
    private final String entityId;
    private final String deepLink;
    private final String dedupeKey;

    private final String actorName;
    private final String actorUserKey;
    private final String chatPreview;
    private final String issueKey;
    private final String summaryText;
    private final String genericBody;

    private final Map<String, String> extra;

    private MobilePushEvent(Builder b) {
        this.eventType = b.eventType;
        this.feature = b.feature;
        this.category = b.category;
        this.entityId = b.entityId;
        this.deepLink = b.deepLink;
        this.dedupeKey = b.dedupeKey;
        this.actorName = b.actorName;
        this.actorUserKey = b.actorUserKey;
        this.chatPreview = b.chatPreview;
        this.issueKey = b.issueKey;
        this.summaryText = b.summaryText;
        this.genericBody = b.genericBody;
        this.extra = b.extra;
    }

    public String getEventType() { return eventType; }
    public String getFeature() { return feature; }
    public String getCategory() { return category; }
    public String getEntityId() { return entityId; }
    public String getDeepLink() { return deepLink; }
    public String getDedupeKey() { return dedupeKey; }
    public String getActorName() { return actorName; }
    public String getActorUserKey() { return actorUserKey; }
    public String getChatPreview() { return chatPreview; }
    public String getIssueKey() { return issueKey; }
    public String getSummaryText() { return summaryText; }
    public String getGenericBody() { return genericBody; }
    public Map<String, String> getExtra() { return extra; }

    /**
     * The always-present routing fields (no display content). Display fields
     * (title/body/sender/avatarPath) are added by the sender per user prefs.
     */
    public Map<String, String> baseRoutingData(String notificationId) {
        LinkedHashMap<String, String> data = new LinkedHashMap<>();
        put(data, "eventType", eventType);
        put(data, "feature", feature);
        put(data, "category", category);
        put(data, "entityId", entityId);
        put(data, "deepLink", deepLink);
        put(data, "notificationId", notificationId);
        put(data, "ts", String.valueOf(System.currentTimeMillis()));
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                put(data, e.getKey(), e.getValue());
            }
        }
        return data;
    }

    private static void put(Map<String, String> m, String k, String v) {
        if (k != null && !k.isEmpty() && v != null && !v.isEmpty()) {
            m.put(k, v);
        }
    }

    public static Builder builder(String eventType, String feature) {
        return new Builder(eventType, feature);
    }

    public static final class Builder {
        private final String eventType;
        private final String feature;
        private String category;
        private String entityId;
        private String deepLink;
        private String dedupeKey;
        private String actorName;
        private String actorUserKey;
        private String chatPreview;
        private String issueKey;
        private String summaryText;
        private String genericBody;
        private Map<String, String> extra;

        private Builder(String eventType, String feature) {
            this.eventType = eventType;
            this.feature = feature;
        }

        public Builder category(String v) { this.category = v; return this; }
        public Builder entityId(String v) { this.entityId = v; return this; }
        public Builder deepLink(String v) { this.deepLink = v; return this; }
        public Builder dedupeKey(String v) { this.dedupeKey = v; return this; }
        public Builder actorName(String v) { this.actorName = v; return this; }
        public Builder actorUserKey(String v) { this.actorUserKey = v; return this; }
        public Builder chatPreview(String v) { this.chatPreview = v; return this; }
        public Builder issueKey(String v) { this.issueKey = v; return this; }
        public Builder summaryText(String v) { this.summaryText = v; return this; }
        public Builder genericBody(String v) { this.genericBody = v; return this; }

        public Builder extra(String k, String v) {
            if (v == null || v.isEmpty()) {
                return this;
            }
            if (this.extra == null) {
                this.extra = new LinkedHashMap<>();
            }
            this.extra.put(k, v);
            return this;
        }

        public MobilePushEvent build() {
            return new MobilePushEvent(this);
        }
    }
}
