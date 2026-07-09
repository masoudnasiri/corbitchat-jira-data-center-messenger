package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.corbitlogic.jira.internalmessenger.ao.JimPushSubscription;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimPushService;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.ws.rs.core.Response;

/**
 * Web Push for the Flutter Web PWA (Sprint 11.5 / PWA-01 Fix-3). Mounted under
 * {@code /rest/corbit-mobile/1.0/push}. The user is resolved by the mobile
 * session cookie/header filter, so every endpoint is authenticated; anonymous
 * callers get 401.
 *
 * <p>This reuses the plugin's existing Web Push stack ({@link JimPushService},
 * VAPID keys, {@code JimPushSubscription} storage and the payload-first push
 * sender). A PWA subscription is stored exactly like a web-chat subscription, so
 * the existing event fan-out (direct message / mention / Assistant item) already
 * delivers to the PWA — no change to the send pipeline is required. Native
 * Android FCM (JimMobileDevice) is a completely separate channel and is
 * unaffected.</p>
 */
@Path("/push")
@Produces({"application/json"})
@AnonymousAllowed
public class CorbitMobilePushResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobilePushResource.class);

    private final JiraAuthenticationContext authenticationContext;
    private final JimPushService pushService;

    @Inject
    public CorbitMobilePushResource(JiraAuthenticationContext authenticationContext,
                                    JimPushService pushService) {
        this.authenticationContext = authenticationContext;
        this.pushService = pushService;
    }

    /** VAPID public key the browser needs as {@code applicationServerKey}. */
    @GET
    @Path("/vapid-key")
    public Response vapidKey() {
        String userKey = currentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("publicKey", this.pushService.getVapidPublicKey());
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/push/vapid-key", userKey, ex,
                    "internal_error", "Unable to load push configuration.");
        }
    }

    /** Whether push is configured server-side and how many subscriptions this user has. */
    @GET
    @Path("/status")
    public Response status() {
        String userKey = currentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        try {
            boolean configured;
            try {
                configured = this.pushService.getVapidPublicKey() != null;
            } catch (Exception ex) {
                configured = false;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("configured", configured);
            body.put("subscriptions", this.pushService.listSubscriptions(userKey).size());
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/push/status", userKey, ex,
                    "internal_error", "Unable to load push status.");
        }
    }

    /** Store a browser Push API subscription against the authenticated user. */
    @POST
    @Path("/subscribe")
    @Consumes({"application/json"})
    public Response subscribe(Map<String, Object> body) {
        String userKey = currentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        String endpoint = str(body, "endpoint");
        Map<String, Object> keys = map(body, "keys");
        String p256dh = str(keys, "p256dh");
        String auth = str(keys, "auth");
        if (endpoint == null || p256dh == null || auth == null) {
            return JimRestResponses.errorJson(400, "bad_request",
                    "endpoint, keys.p256dh and keys.auth are required");
        }
        try {
            this.pushService.subscribe(userKey, endpoint, p256dh, auth);
            return JimRestResponses.okJson(JimRestResponses.singleEntry("ok", true));
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/push/subscribe", userKey, ex,
                    "internal_error", "Unable to save push subscription.");
        }
    }

    /** Remove a browser Push API subscription for the authenticated user. */
    @POST
    @Path("/unsubscribe")
    @Consumes({"application/json"})
    public Response unsubscribe(Map<String, Object> body) {
        String userKey = currentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        String endpoint = str(body, "endpoint");
        if (endpoint == null) {
            return JimRestResponses.errorJson(400, "bad_request", "endpoint is required");
        }
        try {
            this.pushService.unsubscribe(userKey, endpoint);
            return JimRestResponses.okJson(JimRestResponses.singleEntry("ok", true));
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/push/unsubscribe", userKey, ex,
                    "internal_error", "Unable to remove push subscription.");
        }
    }

    /** Send a test notification to all of the authenticated user's subscriptions. */
    @POST
    @Path("/test")
    public Response test() {
        String userKey = currentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("title", "CorbitChat");
            payload.put("body", "Test notification — push is working.");
            payload.put("tag", "corbit-pwa-test");
            payload.put("url", "/mobile/");
            payload.put("type", "test");
            this.pushService.pushToUserAsync(userKey, payload);
            return JimRestResponses.okJson(JimRestResponses.singleEntry("ok", true));
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/push/test", userKey, ex,
                    "internal_error", "Unable to send test push.");
        }
    }

    private String currentUserKey() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        return user != null ? user.getKey() : null;
    }

    private static Response unauthenticated() {
        return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                "You must be signed in to use CorbitChat Mobile.");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> src, String key) {
        if (src == null) {
            return null;
        }
        Object value = src.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private static String str(Map<String, Object> src, String key) {
        if (src == null) {
            return null;
        }
        Object value = src.get(key);
        if (!(value instanceof String)) {
            return null;
        }
        String s = ((String) value).trim();
        return s.isEmpty() ? null : s;
    }
}
