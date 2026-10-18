package com.corbitlogic.jira.internalmessenger.service;

/**
 * Internal-build license service.
 *
 * This implementation is used for the on-prem internal-distribution build of
 * CorbitChat (the {@code internal-corbit} branch). Marketplace licensing is
 * intentionally disabled here — every capability check returns true so chat is
 * always fully available. The Atlassian UPM PluginLicenseManager is not
 * consulted, never injected, and never logs anything about a license.
 *
 * For the paid Marketplace build see the {@code marketplace} branch, which
 * replaces this implementation with one that delegates to {@code PluginLicenseManager}.
 */
public class JimLicenseServiceImpl implements JimLicenseService {

    private static final String PLUGIN_KEY = "com.corbitlogic.corbitchat.jira.dc";

    private final JimLicenseStatus alwaysValid =
            JimLicenseStatus.valid(PLUGIN_KEY, /* dataCenter */ true, /* evaluation */ false, /* expiryDate */ null);

    @Override
    public JimLicenseStatus getStatus() {
        return alwaysValid;
    }

    @Override
    public boolean isLicensed() {
        return true;
    }

    @Override
    public boolean canUseMessaging() {
        return true;
    }

    @Override
    public boolean canUseAdminSettings() {
        return true;
    }

    @Override
    public boolean canUsePushNotifications() {
        return true;
    }

    @Override
    public boolean canUploadAttachments() {
        return true;
    }

    @Override
    public void requireMessaging() {
        // always permitted in the internal build
    }

    @Override
    public void requireAdminSettings() {
        // always permitted in the internal build
    }

    @Override
    public void requirePushNotifications() {
        // always permitted in the internal build
    }

    @Override
    public void requireAttachments() {
        // always permitted in the internal build
    }
}
