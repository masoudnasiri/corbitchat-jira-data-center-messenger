package com.corbitlogic.jira.internalmessenger.mobile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable value object for a user's CorbitChat Mobile preferences. Decouples
 * REST/bootstrap code from the Active Objects entity lifecycle.
 *
 * <p>Sprint 05B adds granular push-notification preferences. The push booleans
 * are nullable {@link Boolean} so a partial PATCH can distinguish "not provided"
 * (null → keep current) from an explicit {@code false}. Fully-resolved instances
 * returned by the service always have non-null values (safe defaults applied).</p>
 */
public final class MobilePreferences {

    public static final String DEFAULT_LANGUAGE = "fa-IR";
    public static final String DEFAULT_THEME = "system";
    public static final String DEFAULT_CALENDAR = "gregorian";
    public static final String DEFAULT_NOTIFICATION_LEVEL = "ALL";

    // Detail level for push notification content.
    public static final String DETAIL_GENERIC = "generic";
    public static final String DETAIL_KEY_ONLY = "keyOnly";
    public static final String DETAIL_PREVIEW = "preview";
    public static final String DEFAULT_DETAIL_LEVEL = DETAIL_PREVIEW;

    private final String language;
    private final String theme;
    private final String calendar;
    private final String notificationLevel;
    private final String quietHours;

    private final Boolean pushEnabled;
    private final Boolean chatPushEnabled;
    private final Boolean taskPushEnabled;
    private final Boolean mentionPushEnabled;
    private final Boolean reminderPushEnabled;
    private final String detailLevel;
    private final Boolean showMessagePreview;
    private final Boolean showSenderAvatar;

    private final long updatedAt;

    private MobilePreferences(Builder b) {
        this.language = b.language;
        this.theme = b.theme;
        this.calendar = b.calendar;
        this.notificationLevel = b.notificationLevel;
        this.quietHours = b.quietHours;
        this.pushEnabled = b.pushEnabled;
        this.chatPushEnabled = b.chatPushEnabled;
        this.taskPushEnabled = b.taskPushEnabled;
        this.mentionPushEnabled = b.mentionPushEnabled;
        this.reminderPushEnabled = b.reminderPushEnabled;
        this.detailLevel = b.detailLevel;
        this.showMessagePreview = b.showMessagePreview;
        this.showSenderAvatar = b.showSenderAvatar;
        this.updatedAt = b.updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fully-resolved default preferences (all push categories enabled, preview on). */
    public static MobilePreferences defaults() {
        return builder()
                .language(DEFAULT_LANGUAGE)
                .theme(DEFAULT_THEME)
                .calendar(DEFAULT_CALENDAR)
                .notificationLevel(DEFAULT_NOTIFICATION_LEVEL)
                .quietHours(null)
                .pushEnabled(Boolean.TRUE)
                .chatPushEnabled(Boolean.TRUE)
                .taskPushEnabled(Boolean.TRUE)
                .mentionPushEnabled(Boolean.TRUE)
                .reminderPushEnabled(Boolean.TRUE)
                .detailLevel(DEFAULT_DETAIL_LEVEL)
                .showMessagePreview(Boolean.TRUE)
                .showSenderAvatar(Boolean.TRUE)
                .updatedAt(0L)
                .build();
    }

    public String getLanguage() { return language; }
    public String getTheme() { return theme; }
    public String getCalendar() { return calendar; }
    public String getNotificationLevel() { return notificationLevel; }
    public String getQuietHours() { return quietHours; }
    public Boolean getPushEnabled() { return pushEnabled; }
    public Boolean getChatPushEnabled() { return chatPushEnabled; }
    public Boolean getTaskPushEnabled() { return taskPushEnabled; }
    public Boolean getMentionPushEnabled() { return mentionPushEnabled; }
    public Boolean getReminderPushEnabled() { return reminderPushEnabled; }
    public String getDetailLevel() { return detailLevel; }
    public Boolean getShowMessagePreview() { return showMessagePreview; }
    public Boolean getShowSenderAvatar() { return showSenderAvatar; }
    public long getUpdatedAt() { return updatedAt; }

    /** True unless the value is explicitly {@code Boolean.FALSE} (null → true). */
    private static boolean flag(Boolean value) {
        return !Boolean.FALSE.equals(value);
    }

    public boolean isPushEnabled() { return flag(pushEnabled); }
    public boolean isChatPushEnabled() { return flag(chatPushEnabled); }
    public boolean isTaskPushEnabled() { return flag(taskPushEnabled); }
    public boolean isMentionPushEnabled() { return flag(mentionPushEnabled); }
    public boolean isReminderPushEnabled() { return flag(reminderPushEnabled); }
    public boolean isShowMessagePreview() { return flag(showMessagePreview); }
    public boolean isShowSenderAvatar() { return flag(showSenderAvatar); }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("language", language);
        map.put("theme", theme);
        map.put("calendar", calendar);
        map.put("notificationLevel", notificationLevel);
        map.put("quietHours", quietHours);
        map.put("pushEnabled", isPushEnabled());
        map.put("chatPushEnabled", isChatPushEnabled());
        map.put("taskPushEnabled", isTaskPushEnabled());
        map.put("mentionPushEnabled", isMentionPushEnabled());
        map.put("reminderPushEnabled", isReminderPushEnabled());
        map.put("detailLevel", detailLevel != null ? detailLevel : DEFAULT_DETAIL_LEVEL);
        map.put("showMessagePreview", isShowMessagePreview());
        map.put("showSenderAvatar", isShowSenderAvatar());
        map.put("updatedAt", updatedAt);
        return map;
    }

    public static final class Builder {
        private String language;
        private String theme;
        private String calendar;
        private String notificationLevel;
        private String quietHours;
        private Boolean pushEnabled;
        private Boolean chatPushEnabled;
        private Boolean taskPushEnabled;
        private Boolean mentionPushEnabled;
        private Boolean reminderPushEnabled;
        private String detailLevel;
        private Boolean showMessagePreview;
        private Boolean showSenderAvatar;
        private long updatedAt;

        public Builder language(String v) { this.language = v; return this; }
        public Builder theme(String v) { this.theme = v; return this; }
        public Builder calendar(String v) { this.calendar = v; return this; }
        public Builder notificationLevel(String v) { this.notificationLevel = v; return this; }
        public Builder quietHours(String v) { this.quietHours = v; return this; }
        public Builder pushEnabled(Boolean v) { this.pushEnabled = v; return this; }
        public Builder chatPushEnabled(Boolean v) { this.chatPushEnabled = v; return this; }
        public Builder taskPushEnabled(Boolean v) { this.taskPushEnabled = v; return this; }
        public Builder mentionPushEnabled(Boolean v) { this.mentionPushEnabled = v; return this; }
        public Builder reminderPushEnabled(Boolean v) { this.reminderPushEnabled = v; return this; }
        public Builder detailLevel(String v) { this.detailLevel = v; return this; }
        public Builder showMessagePreview(Boolean v) { this.showMessagePreview = v; return this; }
        public Builder showSenderAvatar(Boolean v) { this.showSenderAvatar = v; return this; }
        public Builder updatedAt(long v) { this.updatedAt = v; return this; }

        public MobilePreferences build() {
            return new MobilePreferences(this);
        }
    }
}
