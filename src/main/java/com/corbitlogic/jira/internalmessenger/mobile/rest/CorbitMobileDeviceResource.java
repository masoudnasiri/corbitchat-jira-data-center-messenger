package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.corbitlogic.jira.internalmessenger.ao.JimMobileDevice;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimMobileDeviceService;
import com.corbitlogic.jira.internalmessenger.service.JimMobilePushService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mobile BFF device registration for native push (Sprint 05). Mounted under
 * {@code /rest/corbit-mobile/1.0/devices}.
 *
 * <p>Registration is infrastructure and is available to any authenticated
 * mobile user regardless of Sprint 04G feature access — feature gating is
 * applied at <em>send</em> time, not registration time. Raw push tokens are
 * accepted only in the request body, are never returned by the list endpoint,
 * and are never logged.</p>
 */
@Path("/devices")
@Produces({"application/json"})
@AnonymousAllowed
public class CorbitMobileDeviceResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobileDeviceResource.class);

    private final JiraAuthenticationContext authenticationContext;
    private final JimMobileDeviceService deviceService;
    private final JimMobilePushService pushService;

    @Inject
    public CorbitMobileDeviceResource(JiraAuthenticationContext authenticationContext,
                                      JimMobileDeviceService deviceService,
                                      JimMobilePushService pushService) {
        this.authenticationContext = authenticationContext;
        this.deviceService = deviceService;
        this.pushService = pushService;
    }

    @POST
    @Path("/register")
    @Consumes({"application/json"})
    public Response register(Map<String, Object> body) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        String token = str(body, "token");
        if (token == null || token.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "token is required");
        }
        try {
            JimMobileDevice device = this.deviceService.register(
                    userKey,
                    token,
                    str(body, "platform"),
                    str(body, "appVersion"),
                    str(body, "deviceModel"),
                    str(body, "locale"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deviceId", device.getID());
            result.put("pushConfigured", this.pushService.isConfigured());
            return JimRestResponses.okJson(result);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/devices/register", userKey, ex,
                    "internal_error", "An internal error occurred while registering the device.");
        }
    }

    @POST
    @Path("/revoke")
    @Consumes({"application/json"})
    public Response revoke(Map<String, Object> body) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        String token = str(body, "token");
        if (token == null || token.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "token is required");
        }
        try {
            this.deviceService.revoke(userKey, token);
            return JimRestResponses.okJson(JimRestResponses.singleEntry("ok", true));
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/devices/revoke", userKey, ex,
                    "internal_error", "An internal error occurred while revoking the device.");
        }
    }

    @GET
    public Response list() {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            for (JimMobileDevice device : this.deviceService.listDevices(userKey)) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("deviceId", device.getID());
                map.put("platform", device.getPlatform());
                map.put("appVersion", device.getAppVersion());
                map.put("deviceModel", device.getDeviceModel());
                map.put("enabled", device.getEnabled());
                map.put("lastSeenAt", device.getLastSeenAt());
                items.add(map);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("devices", items);
            result.put("pushConfigured", this.pushService.isConfigured());
            return JimRestResponses.okJson(result);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/devices", userKey, ex,
                    "internal_error", "An internal error occurred while listing devices.");
        }
    }

    private String resolveCurrentUserKey() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        return user != null ? user.getKey() : null;
    }

    private static Response unauthenticated() {
        return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                "You must be signed in to use CorbitHub Mobile.");
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }
}
