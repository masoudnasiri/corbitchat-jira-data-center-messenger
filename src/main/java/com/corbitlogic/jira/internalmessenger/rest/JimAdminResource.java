/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.activeobjects.external.ActiveObjects
 *  com.atlassian.jira.component.ComponentAccessor
 *  com.atlassian.jira.config.properties.ApplicationProperties
 *  com.atlassian.jira.permission.GlobalPermissionKey
 *  com.atlassian.jira.security.GlobalPermissionManager
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.plugin.Plugin
 *  javax.inject.Inject
 *  javax.servlet.http.HttpServletRequest
 *  javax.ws.rs.Consumes
 *  javax.ws.rs.DELETE
 *  javax.ws.rs.GET
 *  javax.ws.rs.POST
 *  javax.ws.rs.PUT
 *  javax.ws.rs.Path
 *  javax.ws.rs.PathParam
 *  javax.ws.rs.Produces
 *  javax.ws.rs.core.Context
 *  javax.ws.rs.core.Response
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.Plugin;
import com.corbitlogic.jira.internalmessenger.ao.JimAccessPolicy;
import com.corbitlogic.jira.internalmessenger.ao.JimAdminAudit;
import com.corbitlogic.jira.internalmessenger.ao.JimMobileFeatureRule;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimAccessPolicyService;
import com.corbitlogic.jira.internalmessenger.service.JimAdminAuditService;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import com.corbitlogic.jira.internalmessenger.service.JimLicenseService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimMobileFeatureService;
import com.corbitlogic.jira.internalmessenger.service.JimPushService;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(value="/admin")
@Produces(value={"application/json"})
public class JimAdminResource {
    private static final Logger log = LoggerFactory.getLogger(JimAdminResource.class);
    private static final String PLUGIN_KEY = "com.corbitlogic.corbitchat.jira.dc";
    private final JiraAuthenticationContext authenticationContext;
    private final GlobalPermissionManager globalPermissionManager;
    private final JimAdminSettingsService adminSettingsService;
    private final JimAccessPolicyService accessPolicyService;
    private final JimAdminAuditService auditService;
    private final JimPushService pushService;
    private final ActiveObjects activeObjects;
    private final ApplicationProperties applicationProperties;
    private final JimLicenseService licenseService;
    private final JimMobileFeatureService mobileFeatureService;

    @Inject
    public JimAdminResource(JiraAuthenticationContext authenticationContext, GlobalPermissionManager globalPermissionManager, JimAdminSettingsService adminSettingsService, JimAccessPolicyService accessPolicyService, JimAdminAuditService auditService, JimPushService pushService, ActiveObjects activeObjects, ApplicationProperties applicationProperties, JimLicenseService licenseService, JimMobileFeatureService mobileFeatureService) {
        this.authenticationContext = authenticationContext;
        this.globalPermissionManager = globalPermissionManager;
        this.adminSettingsService = adminSettingsService;
        this.accessPolicyService = accessPolicyService;
        this.auditService = auditService;
        this.pushService = pushService;
        this.activeObjects = activeObjects;
        this.applicationProperties = applicationProperties;
        this.licenseService = licenseService;
        this.mobileFeatureService = mobileFeatureService;
    }

    @GET
    @Path(value="/license")
    public Response getLicenseStatus() {
        try {
            this.requireSysAdmin();
            return JimRestResponses.okJson(this.licenseService.getStatus().toAdminMap());
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("GET /admin/license", ex);
        }
    }

    @GET
    @Path(value="/settings")
    public Response getSettings() {
        try {
            this.requireSysAdmin();
            return JimRestResponses.okJson(this.adminSettingsService.getAllSettings());
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("GET /admin/settings", ex);
        }
    }

    @PUT
    @Path(value="/settings")
    @Consumes(value={"application/json"})
    public Response updateSettings(Map<String, Object> changes) {
        try {
            ApplicationUser admin = this.requireSysAdmin();
            if (!this.licenseService.canUseAdminSettings()) {
                return JimRestResponses.licenseBlocked();
            }
            List<String> descriptions = this.adminSettingsService.updateSettings(changes);
            this.auditService.record(admin.getKey(), "settings.update", String.join((CharSequence)"; ", descriptions));
            return JimRestResponses.okJson(this.adminSettingsService.getAllSettings());
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("PUT /admin/settings", ex);
        }
    }

    @GET
    @Path(value="/policies")
    public Response listPolicies() {
        try {
            this.requireSysAdmin();
            ArrayList<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
            for (JimAccessPolicy policy : this.accessPolicyService.listPolicies()) {
                items.add(this.toPolicyMap(policy));
            }
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("policies", items);
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("GET /admin/policies", ex);
        }
    }

    @POST
    @Path(value="/policies")
    @Consumes(value={"application/json"})
    public Response createPolicy(Map<String, Object> request) {
        try {
            ApplicationUser admin = this.requireSysAdmin();
            if (!this.licenseService.canUseAdminSettings()) {
                return JimRestResponses.licenseBlocked();
            }
            if (request == null) {
                return JimRestResponses.errorJson(400, "bad_request", "Request body is required");
            }
            JimAccessPolicy policy = this.accessPolicyService.createPolicy(JimAdminResource.str(request, "sourceType"), JimAdminResource.str(request, "sourceValue"), JimAdminResource.str(request, "targetType"), JimAdminResource.str(request, "targetValue"), JimAdminResource.str(request, "action"), JimAdminResource.bool(request, "canSearch", true), JimAdminResource.bool(request, "canStartChat", true), JimAdminResource.bool(request, "canReceiveChat", true), JimAdminResource.bool(request, "enabled", true), JimAdminResource.intValue(request, "priority", 0), admin.getKey());
            this.auditService.record(admin.getKey(), "policy.create", JimAdminResource.describePolicy(policy));
            return JimRestResponses.okJson(this.toPolicyMap(policy));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("POST /admin/policies", ex);
        }
    }

    @PUT
    @Path(value="/policies/{policyId}")
    @Consumes(value={"application/json"})
    public Response updatePolicy(@PathParam(value="policyId") int policyId, Map<String, Object> request) {
        try {
            ApplicationUser admin = this.requireSysAdmin();
            if (!this.licenseService.canUseAdminSettings()) {
                return JimRestResponses.licenseBlocked();
            }
            if (request == null) {
                return JimRestResponses.errorJson(400, "bad_request", "Request body is required");
            }
            JimAccessPolicy policy = this.accessPolicyService.updatePolicy(policyId, JimAdminResource.str(request, "sourceType"), JimAdminResource.str(request, "sourceValue"), JimAdminResource.str(request, "targetType"), JimAdminResource.str(request, "targetValue"), JimAdminResource.str(request, "action"), JimAdminResource.bool(request, "canSearch", true), JimAdminResource.bool(request, "canStartChat", true), JimAdminResource.bool(request, "canReceiveChat", true), JimAdminResource.bool(request, "enabled", true), JimAdminResource.intValue(request, "priority", 0));
            this.auditService.record(admin.getKey(), "policy.update", JimAdminResource.describePolicy(policy));
            return JimRestResponses.okJson(this.toPolicyMap(policy));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("PUT /admin/policies/" + policyId, ex);
        }
    }

    @DELETE
    @Path(value="/policies/{policyId}")
    public Response deletePolicy(@PathParam(value="policyId") int policyId) {
        try {
            ApplicationUser admin = this.requireSysAdmin();
            if (!this.licenseService.canUseAdminSettings()) {
                return JimRestResponses.licenseBlocked();
            }
            this.accessPolicyService.deletePolicy(policyId);
            this.auditService.record(admin.getKey(), "policy.delete", "policyId=" + policyId);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("deleted", true);
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("DELETE /admin/policies/" + policyId, ex);
        }
    }

    // ===== Mobile feature access rules (Sprint 04G) ==========================

    @GET
    @Path(value="/mobile-features")
    public Response listMobileFeatureKeys() {
        try {
            this.requireSysAdmin();
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("features", JimMobileFeatures.all());
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("GET /admin/mobile-features", ex);
        }
    }

    @GET
    @Path(value="/mobile-feature-rules")
    public Response listMobileFeatureRules() {
        try {
            this.requireSysAdmin();
            ArrayList<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
            for (JimMobileFeatureRule rule : this.mobileFeatureService.listRules()) {
                items.add(this.toMobileRuleMap(rule));
            }
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("rules", items);
            body.put("features", JimMobileFeatures.all());
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("GET /admin/mobile-feature-rules", ex);
        }
    }

    @POST
    @Path(value="/mobile-feature-rules")
    @Consumes(value={"application/json"})
    public Response createMobileFeatureRule(Map<String, Object> request) {
        try {
            ApplicationUser admin = this.requireSysAdmin();
            if (!this.licenseService.canUseAdminSettings()) {
                return JimRestResponses.licenseBlocked();
            }
            if (request == null) {
                return JimRestResponses.errorJson(400, "bad_request", "Request body is required");
            }
            JimMobileFeatureRule rule = this.mobileFeatureService.createRule(
                    JimAdminResource.str(request, "subjectType"),
                    JimAdminResource.str(request, "subjectValue"),
                    JimAdminResource.featuresCsv(request),
                    JimAdminResource.bool(request, "enabled", true),
                    JimAdminResource.intValue(request, "priority", 0),
                    admin.getKey());
            this.auditService.record(admin.getKey(), "mobileFeatureRule.create", JimAdminResource.describeMobileRule(rule));
            return JimRestResponses.okJson(this.toMobileRuleMap(rule));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("POST /admin/mobile-feature-rules", ex);
        }
    }

    @PUT
    @Path(value="/mobile-feature-rules/{ruleId}")
    @Consumes(value={"application/json"})
    public Response updateMobileFeatureRule(@PathParam(value="ruleId") int ruleId, Map<String, Object> request) {
        try {
            ApplicationUser admin = this.requireSysAdmin();
            if (!this.licenseService.canUseAdminSettings()) {
                return JimRestResponses.licenseBlocked();
            }
            if (request == null) {
                return JimRestResponses.errorJson(400, "bad_request", "Request body is required");
            }
            JimMobileFeatureRule rule = this.mobileFeatureService.updateRule(
                    ruleId,
                    JimAdminResource.str(request, "subjectType"),
                    JimAdminResource.str(request, "subjectValue"),
                    JimAdminResource.featuresCsv(request),
                    JimAdminResource.bool(request, "enabled", true),
                    JimAdminResource.intValue(request, "priority", 0));
            this.auditService.record(admin.getKey(), "mobileFeatureRule.update", JimAdminResource.describeMobileRule(rule));
            return JimRestResponses.okJson(this.toMobileRuleMap(rule));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("PUT /admin/mobile-feature-rules/" + ruleId, ex);
        }
    }

    @DELETE
    @Path(value="/mobile-feature-rules/{ruleId}")
    public Response deleteMobileFeatureRule(@PathParam(value="ruleId") int ruleId) {
        try {
            ApplicationUser admin = this.requireSysAdmin();
            if (!this.licenseService.canUseAdminSettings()) {
                return JimRestResponses.licenseBlocked();
            }
            this.mobileFeatureService.deleteRule(ruleId);
            this.auditService.record(admin.getKey(), "mobileFeatureRule.delete", "ruleId=" + ruleId);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("deleted", true);
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("DELETE /admin/mobile-feature-rules/" + ruleId, ex);
        }
    }

    @GET
    @Path(value="/diagnostics")
    public Response getDiagnostics(@Context HttpServletRequest request) {
        try {
            boolean vapidConfigured;
            boolean aoHealthy;
            this.requireSysAdmin();
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("pluginVersion", this.resolvePluginVersion());
            body.put("restHealthy", true);
            try {
                this.activeObjects.count(JimAccessPolicy.class);
                aoHealthy = true;
            }
            catch (RuntimeException ex) {
                aoHealthy = false;
            }
            body.put("aoHealthy", aoHealthy);
            String baseUrl = this.applicationProperties.getString("jira.baseurl");
            body.put("jiraBaseUrl", baseUrl);
            String requestBaseUrl = JimAdminResource.buildRequestBaseUrl(request);
            body.put("requestBaseUrl", requestBaseUrl);
            String forwardedProto = request != null ? request.getHeader("X-Forwarded-Proto") : null;
            boolean https = request != null && request.isSecure() || "https".equalsIgnoreCase(forwardedProto) || baseUrl != null && baseUrl.toLowerCase().startsWith("https://");
            body.put("httpsDetected", https);
            try {
                String key = this.pushService.getVapidPublicKey();
                vapidConfigured = key != null && !key.isEmpty();
            }
            catch (RuntimeException ex) {
                vapidConfigured = false;
            }
            body.put("vapidConfigured", vapidConfigured);
            body.put("pushEnabled", this.adminSettingsService.isWebPushEnabled());
            body.put("pushSubscriptionCount", this.pushService.countSubscriptions());
            body.put("failedPushCount", this.pushService.getFailedPushCount());
            body.put("chatMode", this.adminSettingsService.getChatMode());
            body.put("policyCount", this.accessPolicyService.listPolicies().size());
            try {
                body.put("mobileFeatureRuleCount", this.mobileFeatureService.listRules().size());
            }
            catch (RuntimeException ex) {
                body.put("mobileFeatureRuleCount", 0);
            }
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("GET /admin/diagnostics", ex);
        }
    }

    @GET
    @Path(value="/audit")
    public Response listAudit() {
        try {
            this.requireSysAdmin();
            ArrayList items = new ArrayList();
            for (JimAdminAudit entry : this.auditService.listRecent(50)) {
                LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("id", entry.getID());
                item.put("userKey", entry.getUserKey());
                item.put("userDisplayName", this.resolveDisplayName(entry.getUserKey()));
                item.put("action", entry.getAction());
                item.put("details", entry.getDetails());
                item.put("createdAt", entry.getCreatedAt());
                items.add(item);
            }
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("entries", items);
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("GET /admin/audit", ex);
        }
    }

    @POST
    @Path(value="/test-notification")
    public Response sendTestNotification() {
        try {
            ApplicationUser admin = this.requireSysAdmin();
            if (!this.licenseService.canUsePushNotifications()) {
                return JimRestResponses.licenseBlocked();
            }
            if (!this.adminSettingsService.isWebPushEnabled()) {
                return JimRestResponses.errorJson(400, "bad_request", "Web push is disabled. Enable it in Notification Settings first.");
            }
            int subscriptions = this.pushService.listSubscriptions(admin.getKey()).size();
            if (subscriptions == 0) {
                return JimRestResponses.errorJson(400, "bad_request", "You have no push subscriptions. Open the chat page and enable notifications first.");
            }
            java.util.LinkedHashMap<String, String> testPayload = new java.util.LinkedHashMap<String, String>();
            testPayload.put("title", "CorbitChat test");
            testPayload.put("body", "Push notifications are working.");
            testPayload.put("url", "/plugins/servlet/jim/chat");
            testPayload.put("type", "test");
            testPayload.put("tag", "jim-admin-test");
            this.pushService.pushToUserAsync(admin.getKey(), testPayload);
            this.auditService.record(admin.getKey(), "test.notification", "subscriptions=" + subscriptions);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("sent", true);
            body.put("subscriptions", subscriptions);
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("POST /admin/test-notification", ex);
        }
    }

    private ApplicationUser requireSysAdmin() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            throw JimMessengerException.unauthorized("User is not authenticated");
        }
        if (!this.globalPermissionManager.hasPermission(GlobalPermissionKey.SYSTEM_ADMIN, user)) {
            throw JimMessengerException.forbidden("Jira System Administrator permission is required");
        }
        return user;
    }

    private Map<String, Object> toPolicyMap(JimAccessPolicy policy) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", policy.getID());
        item.put("sourceType", policy.getSourceType());
        item.put("sourceValue", policy.getSourceValue());
        item.put("targetType", policy.getTargetType());
        item.put("targetValue", policy.getTargetValue());
        item.put("action", policy.getAction());
        item.put("canSearch", Boolean.TRUE.equals(policy.getCanSearch()));
        item.put("canStartChat", Boolean.TRUE.equals(policy.getCanStartChat()));
        item.put("canReceiveChat", Boolean.TRUE.equals(policy.getCanReceiveChat()));
        item.put("enabled", Boolean.TRUE.equals(policy.getEnabled()));
        item.put("priority", policy.getPriority() != null ? policy.getPriority() : 0);
        item.put("createdBy", policy.getCreatedBy());
        item.put("createdAt", policy.getCreatedAt());
        item.put("updatedAt", policy.getUpdatedAt());
        return item;
    }

    private Map<String, Object> toMobileRuleMap(JimMobileFeatureRule rule) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", rule.getID());
        item.put("subjectType", rule.getSubjectType());
        item.put("subjectValue", rule.getSubjectValue());
        item.put("features", new ArrayList<String>(JimMobileFeatures.parse(rule.getFeatures())));
        item.put("enabled", Boolean.TRUE.equals(rule.getEnabled()));
        item.put("priority", rule.getPriority() != null ? rule.getPriority() : 0);
        item.put("createdBy", rule.getCreatedBy());
        item.put("createdAt", rule.getCreatedAt());
        item.put("updatedAt", rule.getUpdatedAt());
        return item;
    }

    private static String describeMobileRule(JimMobileFeatureRule rule) {
        return "id=" + rule.getID() + " " + rule.getSubjectType() + ":" + rule.getSubjectValue()
                + " features=[" + JimMobileFeatures.toCsv(JimMobileFeatures.parse(rule.getFeatures())) + "]"
                + " enabled=" + Boolean.TRUE.equals(rule.getEnabled())
                + " priority=" + rule.getPriority();
    }

    /**
     * Accepts the allowed feature keys either as a JSON array ({@code features})
     * or a comma-separated string, and returns a normalised CSV. Unknown keys
     * are dropped by the service layer.
     */
    private static String featuresCsv(Map<String, Object> request) {
        Object value = request.get("features");
        if (value instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Object item : (List<?>) value) {
                if (item == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(String.valueOf(item));
            }
            return sb.toString();
        }
        return value != null ? String.valueOf(value) : "";
    }

    private static String describePolicy(JimAccessPolicy policy) {
        return "id=" + policy.getID() + " " + policy.getAction() + " " + policy.getSourceType() + ":" + policy.getSourceValue() + " -> " + policy.getTargetType() + (String)(policy.getTargetValue() != null && !policy.getTargetValue().isEmpty() ? ":" + policy.getTargetValue() : "") + " search=" + Boolean.TRUE.equals(policy.getCanSearch()) + " start=" + Boolean.TRUE.equals(policy.getCanStartChat()) + " receive=" + Boolean.TRUE.equals(policy.getCanReceiveChat()) + " enabled=" + Boolean.TRUE.equals(policy.getEnabled()) + " priority=" + policy.getPriority();
    }

    private String resolvePluginVersion() {
        try {
            Plugin plugin = ComponentAccessor.getPluginAccessor().getPlugin(PLUGIN_KEY);
            return plugin != null ? plugin.getPluginInformation().getVersion() : "unknown";
        }
        catch (RuntimeException ex) {
            return "unknown";
        }
    }

    private String resolveDisplayName(String userKey) {
        if (userKey == null) {
            return null;
        }
        ApplicationUser user = ComponentAccessor.getUserManager().getUserByKey(userKey);
        return user != null ? user.getDisplayName() : userKey;
    }

    private static String buildRequestBaseUrl(HttpServletRequest request) {
        Object host;
        if (request == null) {
            return null;
        }
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isEmpty()) {
            scheme = request.getScheme();
        }
        if ((host = request.getHeader("X-Forwarded-Host")) == null || ((String)host).isEmpty()) {
            boolean defaultPort;
            host = request.getServerName();
            int port = request.getServerPort();
            boolean bl = defaultPort = "http".equalsIgnoreCase(scheme) && port == 80 || "https".equalsIgnoreCase(scheme) && port == 443;
            if (!defaultPort && port > 0) {
                host = (String)host + ":" + port;
            }
        }
        return scheme + "://" + (String)host + request.getContextPath();
    }

    private static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean)value;
        }
        if (value instanceof String) {
            return "true".equalsIgnoreCase((String)value);
        }
        return defaultValue;
    }

    private static int intValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number)value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String)value);
            }
            catch (NumberFormatException ex) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Response internalError(String route, Exception ex) {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        return JimRestResponses.internalError(log, route, user != null ? user.getKey() : null, ex, "internal_error", "An internal error occurred in the admin API.");
    }
}

