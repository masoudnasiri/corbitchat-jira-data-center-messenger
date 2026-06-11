package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.upm.api.license.PluginLicenseManager;
import com.atlassian.upm.api.license.entity.PluginLicense;
import com.atlassian.upm.api.util.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * License enforcement backed by the Atlassian UPM licensing API.
 *
 * Marketplace/UPM owns purchase, trial, billing and user-tier matching; this
 * service only reflects and enforces the license state UPM reports. The raw
 * license key is never read, logged or exposed.
 */
public class JimLicenseServiceImpl implements JimLicenseService {

    private static final Logger log = LoggerFactory.getLogger(JimLicenseServiceImpl.class);

    /** Short cache so chat polling does not hammer the UPM API. */
    private static final long CACHE_TTL_MS = 15000L;

    private final PluginLicenseManager pluginLicenseManager;

    private volatile JimLicenseStatus cachedStatus;
    private volatile long cachedAt;

    public JimLicenseServiceImpl(PluginLicenseManager pluginLicenseManager) {
        this.pluginLicenseManager = pluginLicenseManager;
    }

    @Override
    public JimLicenseStatus getStatus() {
        JimLicenseStatus status = cachedStatus;
        long now = System.currentTimeMillis();
        if (status != null && now - cachedAt < CACHE_TTL_MS) {
            return status;
        }
        status = readStatus();
        cachedStatus = status;
        cachedAt = now;
        return status;
    }

    private JimLicenseStatus readStatus() {
        String pluginKey;
        try {
            pluginKey = pluginLicenseManager.getPluginKey();
        } catch (RuntimeException ex) {
            pluginKey = null;
        }
        try {
            Option<PluginLicense> licenseOption = pluginLicenseManager.getLicense();
            if (!licenseOption.isDefined()) {
                return JimLicenseStatus.missing(pluginKey);
            }
            PluginLicense license = licenseOption.get();
            Boolean dataCenter = safeDataCenter(license);
            Boolean evaluation = safeEvaluation(license);
            String expiryDate = safeExpiryDate(license);
            if (license.getError().isDefined()) {
                String errorKey = license.getError().get().name();
                return JimLicenseStatus.invalid(pluginKey, errorKey,
                        describeError(errorKey), dataCenter, evaluation, expiryDate);
            }
            if (!license.isValid()) {
                return JimLicenseStatus.invalid(pluginKey, "INVALID",
                        "The installed license is not valid.", dataCenter, evaluation, expiryDate);
            }
            return JimLicenseStatus.valid(pluginKey, dataCenter, evaluation, expiryDate);
        } catch (RuntimeException ex) {
            // Never log license contents; only the failure class/message.
            log.warn("License check failed: {} {}", ex.getClass().getSimpleName(), ex.getMessage());
            return JimLicenseStatus.unavailable(pluginKey, "License information is currently unavailable.");
        }
    }

    private static Boolean safeDataCenter(PluginLicense license) {
        try {
            return license.isDataCenter();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Boolean safeEvaluation(PluginLicense license) {
        try {
            return license.isEvaluation();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String safeExpiryDate(PluginLicense license) {
        try {
            return license.getExpiryZonedDate()
                    .map(date -> date.toLocalDate().toString())
                    .orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String describeError(String errorKey) {
        if ("EXPIRED".equals(errorKey)) {
            return "The license (or evaluation period) has expired.";
        }
        if ("TYPE_MISMATCH".equals(errorKey)) {
            return "The license type does not match this Jira instance.";
        }
        if ("USER_MISMATCH".equals(errorKey)) {
            return "The licensed user tier does not cover this Jira instance.";
        }
        if ("VERSION_MISMATCH".equals(errorKey)) {
            return "The license does not cover this app version (maintenance expired).";
        }
        if ("EDITION_MISMATCH".equals(errorKey)) {
            return "The license edition does not match this Jira instance.";
        }
        return "The installed license is not valid (" + errorKey + ").";
    }

    @Override
    public boolean isLicensed() {
        return getStatus().isLicensed();
    }

    @Override
    public boolean canUseMessaging() {
        return isLicensed();
    }

    @Override
    public boolean canUseAdminSettings() {
        return isLicensed();
    }

    @Override
    public boolean canUsePushNotifications() {
        return isLicensed();
    }

    @Override
    public boolean canUploadAttachments() {
        return isLicensed();
    }

    @Override
    public void requireMessaging() {
        if (!canUseMessaging()) {
            throw new JimLicenseBlockedException();
        }
    }

    @Override
    public void requireAdminSettings() {
        if (!canUseAdminSettings()) {
            throw new JimLicenseBlockedException();
        }
    }

    @Override
    public void requirePushNotifications() {
        if (!canUsePushNotifications()) {
            throw new JimLicenseBlockedException();
        }
    }

    @Override
    public void requireAttachments() {
        if (!canUploadAttachments()) {
            throw new JimLicenseBlockedException();
        }
    }
}
