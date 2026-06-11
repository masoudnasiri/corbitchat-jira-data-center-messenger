/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  javax.inject.Inject
 *  javax.ws.rs.Consumes
 *  javax.ws.rs.DELETE
 *  javax.ws.rs.GET
 *  javax.ws.rs.POST
 *  javax.ws.rs.Path
 *  javax.ws.rs.Produces
 *  javax.ws.rs.QueryParam
 *  javax.ws.rs.core.Response
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimLicenseService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimPushService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(value="/push")
@Produces(value={"application/json"})
public class JimPushResource {
    private static final Logger log = LoggerFactory.getLogger(JimPushResource.class);
    private final JiraAuthenticationContext authenticationContext;
    private final JimPermissionService permissionService;
    private final JimPushService pushService;
    private final JimConversationService conversationService;
    private final JimReadStateService readStateService;
    private final JimLicenseService licenseService;

    @Inject
    public JimPushResource(JiraAuthenticationContext authenticationContext, JimPermissionService permissionService, JimPushService pushService, JimConversationService conversationService, JimReadStateService readStateService, JimLicenseService licenseService) {
        this.authenticationContext = authenticationContext;
        this.permissionService = permissionService;
        this.pushService = pushService;
        this.conversationService = conversationService;
        this.readStateService = readStateService;
        this.licenseService = licenseService;
    }

    @GET
    @Path(value="/config")
    public Response getConfig() {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("publicKey", this.pushService.getVapidPublicKey());
            return JimRestResponses.okJson(body);
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/push/config", viewer.getKey(), ex, "internal_error", "Unable to load push configuration.");
        }
    }

    @POST
    @Path(value="/subscriptions")
    @Consumes(value={"application/json"})
    public Response saveSubscription(Map<String, Object> request) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        if (!this.licenseService.canUsePushNotifications()) {
            return JimRestResponses.licenseBlocked();
        }
        try {
            String userKey = this.permissionService.requireAuthenticatedUserKey();
            String endpoint = JimPushResource.stringValue(request, "endpoint");
            Map<String, Object> keys = JimPushResource.mapValue(request, "keys");
            String p256dh = JimPushResource.stringValue(keys, "p256dh");
            String auth = JimPushResource.stringValue(keys, "auth");
            if (endpoint == null || p256dh == null || auth == null) {
                return JimRestResponses.errorJson(400, "bad_request", "endpoint, keys.p256dh and keys.auth are required");
            }
            this.pushService.subscribe(userKey, endpoint, p256dh, auth);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("ok", true);
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/push/subscriptions", viewer.getKey(), ex, "internal_error", "Unable to save push subscription.");
        }
    }

    @DELETE
    @Path(value="/subscriptions")
    public Response deleteSubscription(@QueryParam(value="endpoint") String endpoint) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String userKey = this.permissionService.requireAuthenticatedUserKey();
            this.pushService.unsubscribe(userKey, endpoint);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("ok", true);
            return JimRestResponses.okJson(body);
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "DELETE /rest/jim/1.0/push/subscriptions", viewer.getKey(), ex, "internal_error", "Unable to delete push subscription.");
        }
    }

    @GET
    @Path(value="/summary")
    public Response getSummary() {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String userKey = this.permissionService.requireAuthenticatedUserKey();
            List<JimConversation> conversations = this.conversationService.listConversationsForUser(userKey);
            int total = 0;
            String latestPreview = null;
            long latestAt = 0L;
            for (JimConversation conversation : conversations) {
                int unread = this.readStateService.getUnreadCount(conversation.getID(), userKey);
                total += unread;
                if (unread <= 0 || conversation.getLastMessageAt() == null || conversation.getLastMessageAt() <= latestAt) continue;
                latestAt = conversation.getLastMessageAt();
                latestPreview = conversation.getLastMessagePreview();
            }
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("unreadCount", total);
            body.put("title", "CorbitChat");
            body.put("body", total > 0 ? (total == 1 && latestPreview != null ? latestPreview : "You have " + total + " unread message" + (total == 1 ? "" : "s")) : "You have new activity");
            return JimRestResponses.okJson(body);
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/push/summary", viewer.getKey(), ex, "internal_error", "Unable to load push summary.");
        }
    }

    private static Map<String, Object> mapValue(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value instanceof Map ? (Map)value : null;
    }

    private static String stringValue(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value instanceof String && !((String)value).trim().isEmpty() ? ((String)value).trim() : null;
    }
}

