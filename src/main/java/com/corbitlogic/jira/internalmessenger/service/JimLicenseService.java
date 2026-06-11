package com.corbitlogic.jira.internalmessenger.service;

/**
 * Central Atlassian Marketplace license check for CorbitChat.
 *
 * All license decisions live here; other services and REST resources must not
 * talk to the UPM licensing API directly.
 */
public interface JimLicenseService {

    /** Current license snapshot. Never contains or logs the raw license key. */
    JimLicenseStatus getStatus();

    boolean isLicensed();

    boolean canUseMessaging();

    boolean canUseAdminSettings();

    boolean canUsePushNotifications();

    boolean canUploadAttachments();

    /** Throws {@link JimLicenseBlockedException} (HTTP 402) when messaging is not licensed. */
    void requireMessaging();

    void requireAdminSettings();

    void requirePushNotifications();

    void requireAttachments();
}
