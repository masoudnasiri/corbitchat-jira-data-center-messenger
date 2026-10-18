package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.corbitlogic.jira.internalmessenger.ao.JimMobilePreference;
import com.corbitlogic.jira.internalmessenger.mobile.MobilePreferences;
import java.util.Locale;
import net.java.ao.DBParam;
import net.java.ao.Query;

public class JimMobilePreferenceServiceImpl implements JimMobilePreferenceService {

    private static final int QUIET_HOURS_MAX = 64;

    private final ActiveObjects activeObjects;

    public JimMobilePreferenceServiceImpl(ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    @Override
    public MobilePreferences getForUser(String userKey) {
        requireUserKey(userKey);
        JimMobilePreference row = findForUser(userKey);
        if (row == null) {
            return MobilePreferences.defaults();
        }
        return toValue(row);
    }

    @Override
    public MobilePreferences saveForUser(String userKey, MobilePreferences requested) {
        requireUserKey(userKey);
        MobilePreferences current = getForUser(userKey);

        final String language = normalizeLanguage(
                firstNonNull(requested.getLanguage(), current.getLanguage()));
        final String theme = normalizeEnum(
                firstNonNull(requested.getTheme(), current.getTheme()),
                current.getTheme(), "system", "light", "dark");
        final String calendar = normalizeEnum(
                firstNonNull(requested.getCalendar(), current.getCalendar()),
                current.getCalendar(), "gregorian", "jalali");
        final String notificationLevel = normalizeEnum(
                firstNonNull(requested.getNotificationLevel(), current.getNotificationLevel()),
                current.getNotificationLevel(), "ALL", "MENTIONS", "NONE");
        final String quietHours = truncate(
                requested.getQuietHours() != null ? requested.getQuietHours() : current.getQuietHours(),
                QUIET_HOURS_MAX);

        // Push preferences: null in the patch means "keep current".
        final boolean pushEnabled = firstNonNull(requested.getPushEnabled(), current.getPushEnabled(), Boolean.TRUE);
        final boolean chatPush = firstNonNull(requested.getChatPushEnabled(), current.getChatPushEnabled(), Boolean.TRUE);
        final boolean taskPush = firstNonNull(requested.getTaskPushEnabled(), current.getTaskPushEnabled(), Boolean.TRUE);
        final boolean mentionPush = firstNonNull(requested.getMentionPushEnabled(), current.getMentionPushEnabled(), Boolean.TRUE);
        final boolean reminderPush = firstNonNull(requested.getReminderPushEnabled(), current.getReminderPushEnabled(), Boolean.TRUE);
        final String detailLevel = normalizeEnum(
                firstNonNull(requested.getDetailLevel(), current.getDetailLevel()),
                current.getDetailLevel(),
                MobilePreferences.DETAIL_GENERIC, MobilePreferences.DETAIL_KEY_ONLY, MobilePreferences.DETAIL_PREVIEW);
        final boolean showPreview = firstNonNull(requested.getShowMessagePreview(), current.getShowMessagePreview(), Boolean.TRUE);
        final boolean showAvatar = firstNonNull(requested.getShowSenderAvatar(), current.getShowSenderAvatar(), Boolean.TRUE);

        final long now = System.currentTimeMillis();

        this.activeObjects.executeInTransaction(() -> {
            JimMobilePreference row = findForUser(userKey);
            if (row == null) {
                row = this.activeObjects.create(JimMobilePreference.class, new DBParam[0]);
                row.setUserKey(userKey);
            }
            row.setLanguage(language);
            row.setTheme(theme);
            row.setCalendar(calendar);
            row.setNotificationLevel(notificationLevel);
            row.setQuietHours(quietHours);
            row.setPushEnabled(pushEnabled);
            row.setChatPushEnabled(chatPush);
            row.setTaskPushEnabled(taskPush);
            row.setMentionPushEnabled(mentionPush);
            row.setReminderPushEnabled(reminderPush);
            row.setDetailLevel(detailLevel);
            row.setShowMessagePreview(showPreview);
            row.setShowSenderAvatar(showAvatar);
            row.setUpdatedAt(now);
            row.save();
            return null;
        });

        return MobilePreferences.builder()
                .language(language)
                .theme(theme)
                .calendar(calendar)
                .notificationLevel(notificationLevel)
                .quietHours(quietHours)
                .pushEnabled(pushEnabled)
                .chatPushEnabled(chatPush)
                .taskPushEnabled(taskPush)
                .mentionPushEnabled(mentionPush)
                .reminderPushEnabled(reminderPush)
                .detailLevel(detailLevel)
                .showMessagePreview(showPreview)
                .showSenderAvatar(showAvatar)
                .updatedAt(now)
                .build();
    }

    private JimMobilePreference findForUser(String userKey) {
        JimMobilePreference[] rows = this.activeObjects.find(JimMobilePreference.class,
                Query.select().where("USER_KEY = ?", new Object[]{userKey}).limit(1));
        return rows.length == 0 ? null : rows[0];
    }

    private MobilePreferences toValue(JimMobilePreference row) {
        String detailLevel = normalizeEnum(row.getDetailLevel(), MobilePreferences.DEFAULT_DETAIL_LEVEL,
                MobilePreferences.DETAIL_GENERIC, MobilePreferences.DETAIL_KEY_ONLY, MobilePreferences.DETAIL_PREVIEW);
        return MobilePreferences.builder()
                .language(orDefault(row.getLanguage(), MobilePreferences.DEFAULT_LANGUAGE))
                .theme(orDefault(row.getTheme(), MobilePreferences.DEFAULT_THEME))
                .calendar(orDefault(row.getCalendar(), MobilePreferences.DEFAULT_CALENDAR))
                .notificationLevel(orDefault(row.getNotificationLevel(), MobilePreferences.DEFAULT_NOTIFICATION_LEVEL))
                .quietHours(row.getQuietHours())
                // Null (legacy rows) → enabled default.
                .pushEnabled(row.getPushEnabled() == null ? Boolean.TRUE : row.getPushEnabled())
                .chatPushEnabled(row.getChatPushEnabled() == null ? Boolean.TRUE : row.getChatPushEnabled())
                .taskPushEnabled(row.getTaskPushEnabled() == null ? Boolean.TRUE : row.getTaskPushEnabled())
                .mentionPushEnabled(row.getMentionPushEnabled() == null ? Boolean.TRUE : row.getMentionPushEnabled())
                .reminderPushEnabled(row.getReminderPushEnabled() == null ? Boolean.TRUE : row.getReminderPushEnabled())
                .detailLevel(detailLevel)
                .showMessagePreview(row.getShowMessagePreview() == null ? Boolean.TRUE : row.getShowMessagePreview())
                .showSenderAvatar(row.getShowSenderAvatar() == null ? Boolean.TRUE : row.getShowSenderAvatar())
                .updatedAt(row.getUpdatedAt() == null ? 0L : row.getUpdatedAt())
                .build();
    }

    private static void requireUserKey(String userKey) {
        if (userKey == null || userKey.trim().isEmpty()) {
            throw new IllegalArgumentException("userKey must not be blank");
        }
    }

    // Accept only fa / en language tags; fall back to the product default.
    private static String normalizeLanguage(String value) {
        if (value == null) {
            return MobilePreferences.DEFAULT_LANGUAGE;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("fa")) {
            return "fa-IR";
        }
        if (lower.startsWith("en")) {
            return "en-US";
        }
        return MobilePreferences.DEFAULT_LANGUAGE;
    }

    private static String normalizeEnum(String value, String fallback, String... allowed) {
        if (value == null) {
            return fallback != null ? fallback : allowed[0];
        }
        String trimmed = value.trim();
        for (String option : allowed) {
            if (option.equalsIgnoreCase(trimmed)) {
                return option;
            }
        }
        return fallback != null ? fallback : allowed[0];
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value;
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static boolean firstNonNull(Boolean a, Boolean b, Boolean fallback) {
        if (a != null) {
            return a;
        }
        if (b != null) {
            return b;
        }
        return fallback != null && fallback;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
