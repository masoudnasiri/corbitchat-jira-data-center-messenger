package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Accessor;
import net.java.ao.Entity;
import net.java.ao.Mutator;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Per-user preferences for the CorbitChat Mobile app (Sprint 01, PDF §6.3).
 *
 * <p>One row per Jira user (keyed by {@code USER_KEY}). Additive to the existing
 * CorbitChat AO schema; not used by the web surface. Column names are chosen to
 * avoid reserved words across HSQLDB/MySQL/Postgres.</p>
 *
 * <p>An explicit short {@code @Table} name is required: AO prefixes the table
 * with {@code AO_xxxxxx_} and caps the total at 30 chars, which the full class
 * name ({@code JIM_MOBILE_PREFERENCE}) would exceed.</p>
 */
@Preload
@Table("JimMobilePref")
public interface JimMobilePreference extends Entity {

    @Indexed
    @StringLength(255)
    @Accessor("USER_KEY")
    String getUserKey();

    @Mutator("USER_KEY")
    void setUserKey(String userKey);

    /** BCP-47 language tag, e.g. {@code fa-IR} or {@code en-US}. */
    @StringLength(16)
    @Accessor("LANG_CODE")
    String getLanguage();

    @Mutator("LANG_CODE")
    void setLanguage(String language);

    /** {@code system} | {@code light} | {@code dark}. */
    @StringLength(16)
    @Accessor("THEME_MODE")
    String getTheme();

    @Mutator("THEME_MODE")
    void setTheme(String theme);

    /** {@code gregorian} | {@code jalali}. */
    @StringLength(16)
    @Accessor("CALENDAR_SYS")
    String getCalendar();

    @Mutator("CALENDAR_SYS")
    void setCalendar(String calendar);

    /** {@code ALL} | {@code MENTIONS} | {@code NONE}. */
    @StringLength(16)
    @Accessor("NOTIF_LEVEL")
    String getNotificationLevel();

    @Mutator("NOTIF_LEVEL")
    void setNotificationLevel(String notificationLevel);

    /** Optional quiet-hours window, e.g. {@code 22:00-07:00}; null = disabled. */
    @StringLength(64)
    @Accessor("QUIET_HOURS")
    String getQuietHours();

    @Mutator("QUIET_HOURS")
    void setQuietHours(String quietHours);

    @Accessor("UPDATED_AT")
    Long getUpdatedAt();

    @Mutator("UPDATED_AT")
    void setUpdatedAt(Long updatedAt);
}
