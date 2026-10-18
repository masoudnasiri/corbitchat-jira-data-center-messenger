package com.corbitlogic.jira.internalmessenger.service;

import java.util.Map;

/**
 * Per-user localization preferences for the Jira WEB chat surfaces only
 * (Persian UI + Jalali dates). Web-only: does not touch the mobile
 * preferences, Jira system locale, or stored dates.
 *
 * <p>Precedence: an explicit user choice wins; "default" defers to the admin
 * global default; with no admin default the behavior is the legacy one
 * (English UI, Gregorian dates).</p>
 */
public interface JimWebLocaleService {
    String LANG_DEFAULT = "default";
    String LANG_FA = "fa";
    String LANG_EN = "en";

    String CAL_DEFAULT = "default";
    String CAL_JALALI = "jalali";
    String CAL_GREGORIAN = "gregorian";

    /** Raw stored user language choice: default | fa | en. */
    String getUserLanguage(String userKey);

    /** Raw stored user calendar choice: default | jalali | gregorian. */
    String getUserCalendar(String userKey);

    /** Persist the user's web language + calendar choices (validated). */
    void saveUserPreferences(String userKey, String language, String calendar);

    /** Effective language after precedence: {@code fa} | {@code en}. */
    String effectiveLanguage(String userKey);

    /** Effective calendar after precedence: {@code jalali} | {@code gregorian}. */
    String effectiveCalendar(String userKey);

    /**
     * Full bootstrap map for the web client: admin defaults, the user's raw
     * choices, and the resolved effective values.
     */
    Map<String, Object> bootstrap(String userKey);
}
