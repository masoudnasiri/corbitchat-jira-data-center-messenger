package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.mobile.MobilePreferences;

/**
 * Read/write access to per-user CorbitChat Mobile preferences (Sprint 01).
 */
public interface JimMobilePreferenceService {

    /**
     * Returns the stored preferences for the user, or product defaults when no
     * row exists yet. Never returns null.
     */
    MobilePreferences getForUser(String userKey);

    /**
     * Upserts the user's preferences. Null fields in {@code requested} leave the
     * existing (or default) value untouched; provided values are validated and
     * normalized. Returns the effective, persisted preferences.
     */
    MobilePreferences saveForUser(String userKey, MobilePreferences requested);
}
