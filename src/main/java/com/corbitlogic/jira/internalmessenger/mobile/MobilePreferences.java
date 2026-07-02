package com.corbitlogic.jira.internalmessenger.mobile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable value object for a user's CorbitChat Mobile preferences. Decouples
 * REST/bootstrap code from the Active Objects entity lifecycle.
 */
public final class MobilePreferences {

    public static final String DEFAULT_LANGUAGE = "fa-IR";
    public static final String DEFAULT_THEME = "system";
    public static final String DEFAULT_CALENDAR = "gregorian";
    public static final String DEFAULT_NOTIFICATION_LEVEL = "ALL";

    private final String language;
    private final String theme;
    private final String calendar;
    private final String notificationLevel;
    private final String quietHours;
    private final long updatedAt;

    public MobilePreferences(String language, String theme, String calendar,
                             String notificationLevel, String quietHours, long updatedAt) {
        this.language = language;
        this.theme = theme;
        this.calendar = calendar;
        this.notificationLevel = notificationLevel;
        this.quietHours = quietHours;
        this.updatedAt = updatedAt;
    }

    public static MobilePreferences defaults() {
        return new MobilePreferences(DEFAULT_LANGUAGE, DEFAULT_THEME, DEFAULT_CALENDAR,
                DEFAULT_NOTIFICATION_LEVEL, null, 0L);
    }

    public String getLanguage() {
        return language;
    }

    public String getTheme() {
        return theme;
    }

    public String getCalendar() {
        return calendar;
    }

    public String getNotificationLevel() {
        return notificationLevel;
    }

    public String getQuietHours() {
        return quietHours;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("language", language);
        map.put("theme", theme);
        map.put("calendar", calendar);
        map.put("notificationLevel", notificationLevel);
        map.put("quietHours", quietHours);
        map.put("updatedAt", updatedAt);
        return map;
    }
}
