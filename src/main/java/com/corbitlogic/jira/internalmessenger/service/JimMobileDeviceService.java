package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.ao.JimMobileDevice;
import java.util.List;

/**
 * Registration and lifecycle for native mobile push devices (Sprint 05).
 *
 * <p>Raw FCM/APNs tokens are encrypted at rest and never logged. Lookups and
 * deduplication use a SHA-256 hash of the token so callers never need to handle
 * the raw value. This service is intentionally free of any FCM/APNs
 * transport concern &mdash; sending lives in {@link JimMobilePushService}.</p>
 */
public interface JimMobileDeviceService {

    /** Platform identifiers persisted in {@link JimMobileDevice#getPlatform()}. */
    String PLATFORM_ANDROID = "android";
    String PLATFORM_IOS = "ios";

    /**
     * Register (or refresh) a device token for {@code userKey}. Idempotent by
     * token hash: an existing row for the same token is updated (and reassigned
     * to the current user if the token moved between accounts) rather than
     * duplicated. Returns the persisted row.
     */
    JimMobileDevice register(String userKey, String rawToken, String platform,
                             String appVersion, String deviceModel, String locale);

    /** Revoke a single device by its raw token for the given user. No-op if unknown. */
    void revoke(String userKey, String rawToken);

    /** Revoke every device belonging to {@code userKey} (e.g. account-wide sign-out). */
    void revokeAllForUser(String userKey);

    /** Devices currently registered for the user (for debugging; no raw tokens exposed). */
    List<JimMobileDevice> listDevices(String userKey);

    /** Enabled devices for the user (used by the sender). */
    List<JimMobileDevice> activeDevices(String userKey);

    /**
     * Decrypt a device's raw token for a send attempt. Returns null if the token
     * cannot be recovered (in which case the row should be pruned). The result
     * must never be logged.
     */
    String decryptToken(JimMobileDevice device);

    /** Record the outcome of a send so dead tokens can be pruned. */
    void recordSendResult(JimMobileDevice device, boolean success, boolean unregistered);

    /** Total number of enabled devices across all users (diagnostics). */
    int countActiveDevices();

    /** Distinct user keys with at least one enabled device (used by the reminder sweep). */
    List<String> distinctActiveDeviceUserKeys();
}
