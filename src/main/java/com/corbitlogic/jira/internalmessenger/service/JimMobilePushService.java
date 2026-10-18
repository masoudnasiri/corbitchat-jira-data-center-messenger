package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.mobile.push.MobilePushEvent;
import java.util.Map;

/**
 * Feature- and permission-aware sender for the native mobile push channel
 * (Sprint 05). This is a separate channel from the browser Web Push
 * ({@link JimPushService}); the two never share tokens or transport.
 *
 * <p>Every send rechecks Sprint 04G mobile feature access for the recipient and
 * applies short-window deduplication so the same logical event does not spam a
 * device. When FCM is not configured, sends are no-ops that surface in
 * diagnostics rather than failing silently or faking success.</p>
 */
public interface JimMobilePushService {

    /** Enqueue a push to every enabled device of {@code userKey}, subject to feature access. */
    void sendToUser(String userKey, MobilePushEvent event);

    /** True when FCM credentials are configured and sends will be attempted. */
    boolean isConfigured();

    /** Persist FCM service-account configuration (admin only). */
    void configure(Map<String, Object> body);

    /** Remove FCM configuration (admin only). */
    void clearConfig();

    /** Operational snapshot for the admin diagnostics endpoint. */
    Map<String, Object> diagnostics();

    /**
     * Send a one-off diagnostic push directly to a device token, bypassing
     * feature/dedupe gating. Returns the FCM result name (e.g. {@code SUCCESS},
     * {@code UNREGISTERED}, {@code NOT_CONFIGURED}). Both {@code SUCCESS} and
     * {@code UNREGISTERED} confirm the server↔FCM credential is valid.
     */
    String sendTestToToken(String token);
}
