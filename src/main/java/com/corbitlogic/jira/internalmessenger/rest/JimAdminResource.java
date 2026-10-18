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
import com.corbitlogic.jira.internalmessenger.report.JimReportBackfillManager;
import com.corbitlogic.jira.internalmessenger.report.JimReportConfig;
import com.corbitlogic.jira.internalmessenger.report.JimReportLedgerService;
import com.corbitlogic.jira.internalmessenger.report.JimReportMetrics;
import com.corbitlogic.jira.internalmessenger.report.JimReportRecalcCoordinator;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimAccessPolicyService;
import com.corbitlogic.jira.internalmessenger.service.JimAdminAuditService;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import com.corbitlogic.jira.internalmessenger.service.JimLicenseService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimMobileFeatureService;
import com.corbitlogic.jira.internalmessenger.service.JimMobilePushService;
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
    private final JimMobilePushService mobilePushService;
    private final JimReportLedgerService reportLedgerService;
    private final JimReportRecalcCoordinator reportRecalcCoordinator;
    private final JimReportBackfillManager reportBackfillManager;

    @Inject
    public JimAdminResource(JiraAuthenticationContext authenticationContext, GlobalPermissionManager globalPermissionManager, JimAdminSettingsService adminSettingsService, JimAccessPolicyService accessPolicyService, JimAdminAuditService auditService, JimPushService pushService, ActiveObjects activeObjects, ApplicationProperties applicationProperties, JimLicenseService licenseService, JimMobileFeatureService mobileFeatureService, JimMobilePushService mobilePushService, JimReportLedgerService reportLedgerService, JimReportRecalcCoordinator reportRecalcCoordinator, JimReportBackfillManager reportBackfillManager) {
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
        this.mobilePushService = mobilePushService;
        this.reportLedgerService = reportLedgerService;
        this.reportRecalcCoordinator = reportRecalcCoordinator;
        this.reportBackfillManager = reportBackfillManager;
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
    @Path(value="/mobile-push")
    public Response getMobilePushConfig() {
        try {
            this.requireSysAdmin();
            return JimRestResponses.okJson(this.mobilePushService.diagnostics());
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("GET /admin/mobile-push", ex);
        }
    }

    @POST
    @Path(value="/mobile-push")
    @Consumes(value={"application/json"})
    public Response configureMobilePush(Map<String, Object> request) {
        try {
            ApplicationUser admin = this.requireSysAdmin();
            if (!this.licenseService.canUseAdminSettings()) {
                return JimRestResponses.licenseBlocked();
            }
            if (request == null) {
                return JimRestResponses.errorJson(400, "bad_request", "Service-account configuration is required");
            }
            this.mobilePushService.configure(request);
            // Audit only non-secret metadata; never the private key.
            this.auditService.record(admin.getKey(), "mobilePush.configure", "configured=true");
            return JimRestResponses.okJson(this.mobilePushService.diagnostics());
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("POST /admin/mobile-push", ex);
        }
    }

    @POST
    @Path(value="/mobile-push/test")
    @Consumes(value={"application/json"})
    public Response testMobilePush(Map<String, Object> request) {
        try {
            this.requireSysAdmin();
            String token = request != null ? JimAdminResource.str(request, "token") : null;
            String result = this.mobilePushService.sendTestToToken(token);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("result", result);
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("POST /admin/mobile-push/test", ex);
        }
    }

    @DELETE
    @Path(value="/mobile-push")
    public Response clearMobilePush() {
        try {
            ApplicationUser admin = this.requireSysAdmin();
            if (!this.licenseService.canUseAdminSettings()) {
                return JimRestResponses.licenseBlocked();
            }
            this.mobilePushService.clearConfig();
            this.auditService.record(admin.getKey(), "mobilePush.clear", "configured=false");
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("cleared", true);
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("DELETE /admin/mobile-push", ex);
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
            try {
                body.put("mobilePush", this.mobilePushService.diagnostics());
            }
            catch (RuntimeException ex) {
                body.put("mobilePush", "unavailable");
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

    // ===== Reporting dashboard / diagnostics / export / backfill (Phase 6/7) =====

    @GET
    @Path(value="/reporting/levels")
    public Response getReportingLevels() {
        try {
            this.requireSysAdmin();
            return JimRestResponses.okJson(this.adminSettingsService.getReportLevelModel());
        } catch (Exception ex) {
            return this.internalError("GET /admin/reporting/levels", ex);
        }
    }

    @GET
    @Path(value="/reporting/diagnostics")
    public Response getReportingDiagnostics() {
        try {
            this.requireSysAdmin();
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("featureEnabled", JimReportConfig.isFeatureEnabled());
            body.put("queue", this.reportRecalcCoordinator.diagnostics());
            body.put("ledger", this.reportLedgerService.diagnostics());
            body.put("mappingHealth", this.reportMappingHealth());
            body.put("backfill", this.reportBackfillManager.status());
            // Working-day source + report-receiver pause diagnostics (Report Replies & Working-Days).
            body.put("workingDays",
                    com.corbitlogic.jira.internalmessenger.report.ReportWorkingCalendarService.diagnostics());
            // Dynamic follow-up level model (Phase 8A): field/options/config/orphans/warnings.
            body.put("followUpLevels", this.adminSettingsService.getReportLevelModel());
            LinkedHashMap<String, Object> receiver = new LinkedHashMap<String, Object>();
            receiver.put("pauseEnabled",
                    com.corbitlogic.jira.internalmessenger.report.ReportResponsibilityService.isPauseEnabled());
            receiver.put("source", "schedule_approval_approver");
            receiver.put("approverFieldConfigured",
                    com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalConfig.resolveApproverField() != null);
            body.put("reportReceiver", receiver);
            body.put("countModeInFingerprint", true);
            // Report ENFORCEMENT policy diagnostics: scope bounds SCORING only — it never
            // affects report submission or history display.
            LinkedHashMap<String, Object> enforcement = new LinkedHashMap<String, Object>();
            // Use the SAME timezone the report scoring engine uses (admin override →
            // Jira default), not the JVM/container default, so the interpreted start
            // instant matches real scoring.
            java.util.TimeZone rtz = JimReportConfig.reportTimeZone();
            if (rtz == null) {
                try {
                    com.atlassian.jira.timezone.TimeZoneManager tzm =
                            ComponentAccessor.getComponent(com.atlassian.jira.timezone.TimeZoneManager.class);
                    if (tzm != null && tzm.getDefaultTimezone() != null) {
                        rtz = tzm.getDefaultTimezone();
                    }
                } catch (Throwable ignore) {
                }
            }
            if (rtz == null) {
                rtz = java.util.TimeZone.getDefault();
            }
            String basis = JimReportConfig.applicabilityDateMode();
            boolean fixedStart = "fixed_counter_start".equals(basis);
            enforcement.put("enabled", JimReportConfig.applicabilityEnabled());
            enforcement.put("startDate", JimReportConfig.effectiveFromDate());
            Long efm = JimReportConfig.effectiveFromMs(rtz);
            enforcement.put("startInstantMs", efm);
            enforcement.put("reportTimezone", rtz.getID());
            enforcement.put("startInstantTimezone", rtz.getID());
            enforcement.put("dateBasis", basis);
            enforcement.put("fixedCounterStartMode", fixedStart);
            enforcement.put("nonDoneOnly", JimReportConfig.applicabilityNonDoneOnly());
            enforcement.put("missingDuePolicy", JimReportConfig.applicabilityMissingDuePolicy());
            enforcement.put("affectsSubmission", false);
            enforcement.put("affectsHistoryDisplay", false);
            enforcement.put("affectsCounters", true);
            enforcement.put("inFingerprint", true);
            enforcement.put("note", fixedStart
                    ? "created/due are not used as scope filters; the fixed start date is a "
                        + "scoring lower bound only. Submission and report history are always available."
                    : "Enforcement bounds required/missed/overdue scoring only; "
                        + "submission and report history are always available.");
            body.put("enforcement", enforcement);
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("GET /admin/reporting/diagnostics", ex);
        }
    }

    @GET
    @Path(value="/reporting/dashboard")
    public Response getReportingDashboard(@Context HttpServletRequest request) {
        try {
            this.requireSysAdmin();
            List<Map<String, Object>> rows = this.reportLedgerService.listControls(
                    param(request, "status"), param(request, "projectKey"), param(request, "userKey"),
                    longParam(request, "from"), longParam(request, "to"), intParam(request, "limit", 500));
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("issues", rows);
            body.put("count", rows.size());
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("GET /admin/reporting/dashboard", ex);
        }
    }

    @GET
    @Path(value="/reporting/export.csv")
    @Produces(value={"text/csv"})
    public Response exportReportingCsv(@Context HttpServletRequest request) {
        try {
            this.requireSysAdmin();
            List<Map<String, Object>> rows = this.reportLedgerService.listControls(
                    param(request, "status"), param(request, "projectKey"), param(request, "userKey"),
                    longParam(request, "from"), longParam(request, "to"), intParam(request, "limit", 5000));
            String[] cols = {"issueKey", "projectKey", "reportingControlState", "responsibleUserKey",
                    "responsibleUserName", "reportingStartAt", "reportingEndAt", "reportingClosedAt",
                    "reportingClosedByCondition", "totalRequiredReportsUntilEnd", "currentMissedReportStreak",
                    "maxMissedReportStreak", "calculatedAt", "calculationVersion"};
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cols.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(cols[i]);
            }
            sb.append("\r\n");
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < cols.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(csv(row.get(cols[i])));
                }
                sb.append("\r\n");
            }
            return Response.ok(sb.toString()).type("text/csv; charset=UTF-8")
                    .header("Content-Disposition", "attachment; filename=\"corbitchat-reporting.csv\"").build();
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("GET /admin/reporting/export.csv", ex);
        }
    }

    /**
     * Read-only report-applicability impact preview (reportscope). Bounded,
     * SYSTEM_ADMIN-gated. Uses server-owned JQL {@code searchCount}/sample for
     * the candidate/in-scope/excluded groups and a bounded AO-ledger cross-ref
     * for ACTIVE rows that would become out-of-scope. Never mutates anything.
     * Query params default to the currently-stored policy when omitted:
     * {@code effectiveFrom=YYYY-MM-DD, mode=..., nonDoneOnly=true|false,
     * missingDuePolicy=..., sample=15, ledgerCap=1000}.
     */
    @GET
    @Path(value="/report-applicability/impact")
    public Response getReportApplicabilityImpact(@Context HttpServletRequest request) {
        try {
            ApplicationUser admin = this.requireSysAdmin();
            String effectiveFrom = firstNonEmpty(param(request, "effectiveFrom"), JimReportConfig.effectiveFromDate());
            String mode = firstNonEmpty(param(request, "mode"), JimReportConfig.applicabilityDateMode());
            String ndStr = param(request, "nonDoneOnly");
            boolean nonDoneOnly = (ndStr == null || ndStr.isEmpty())
                    ? JimReportConfig.applicabilityNonDoneOnly() : "true".equalsIgnoreCase(ndStr);
            String missingDuePolicy = firstNonEmpty(param(request, "missingDuePolicy"),
                    JimReportConfig.applicabilityMissingDuePolicy());
            boolean includeMissing = "include_if_created_matches".equals(missingDuePolicy);
            int sample = clampInt(intParam(request, "sample", 15), 0, 50);
            int ledgerCap = clampInt(intParam(request, "ledgerCap", 1000), 0, 5000);

            java.util.TimeZone tz = JimReportConfig.reportTimeZone();
            Long effMs = JimReportConfig.parseYmdStartOfDay(effectiveFrom, tz);
            String levelFieldId = this.adminSettingsService.getReportLevelFieldId();
            String cfNum = levelFieldId != null ? levelFieldId.replaceAll("[^0-9]", "") : "";

            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            LinkedHashMap<String, Object> policy = new LinkedHashMap<String, Object>();
            policy.put("enabledNow", JimReportConfig.applicabilityEnabled());
            policy.put("effectiveFrom", effectiveFrom == null ? "" : effectiveFrom);
            policy.put("mode", mode);
            policy.put("nonDoneOnly", nonDoneOnly);
            policy.put("missingDuePolicy", missingDuePolicy);
            policy.put("reportTimezone", tz != null ? tz.getID() : "server-default");
            policy.put("levelField", levelFieldId);
            body.put("policy", policy);
            body.put("readOnly", true);

            if (cfNum.isEmpty()) {
                body.put("error", "report level field is not configured");
                return JimRestResponses.okJson(body);
            }
            String candidate = "cf[" + cfNum + "] IS NOT EMPTY";
            String notDone = "statusCategory != Done";
            String doneC = "statusCategory = Done";

            long totalCandidate = jqlCount(admin, candidate);
            LinkedHashMap<String, Object> counts = new LinkedHashMap<String, Object>();
            counts.put("totalCandidate", totalCandidate);

            if (effMs == null) {
                long inScope = jqlCount(admin, andJql(candidate, nonDoneOnly ? notDone : null));
                counts.put("inScope", inScope);
                counts.put("outOfScope", totalCandidate - inScope);
                counts.put("excludedDone", nonDoneOnly ? jqlCount(admin, andJql(candidate, doneC)) : 0L);
                counts.put("note", "effectiveFrom not set — only the nonDoneOnly rule applies");
            } else {
                String d = effectiveFrom;
                String createdGE = "created >= \"" + d + "\"";
                String createdLT = "created < \"" + d + "\"";
                String dueGE = "duedate >= \"" + d + "\"";
                String dueLT = "duedate < \"" + d + "\"";
                String dueEmpty = "duedate IS EMPTY";
                String dateScope = buildDateScope(mode, includeMissing, createdGE, dueGE, dueEmpty);
                String inScopeJql = andJql(candidate, nonDoneOnly ? notDone : null, dateScope);
                long inScope = jqlCount(admin, inScopeJql);
                counts.put("inScope", inScope);
                counts.put("outOfScope", totalCandidate - inScope);
                counts.put("excludedDone", nonDoneOnly ? jqlCount(admin, andJql(candidate, doneC)) : 0L);
                counts.put("excludedByCreatedDate", jqlCount(admin, andJql(candidate, createdLT)));
                counts.put("excludedByDueDate", jqlCount(admin, andJql(candidate, "duedate IS NOT EMPTY", dueLT)));
                counts.put("excludedMissingDue", jqlCount(admin, andJql(candidate, dueEmpty)));
                counts.put("note", "excluded* groups are independent diagnostics and may overlap; inScope/outOfScope are the authoritative totals.");
                LinkedHashMap<String, Object> samples = new LinkedHashMap<String, Object>();
                samples.put("inScope", jqlSample(admin, inScopeJql, sample));
                samples.put("excludedByCreatedDate", jqlSample(admin, andJql(candidate, createdLT), sample));
                samples.put("excludedByDueDate", jqlSample(admin, andJql(candidate, "duedate IS NOT EMPTY", dueLT), sample));
                samples.put("excludedMissingDue", jqlSample(admin, andJql(candidate, dueEmpty), sample));
                if (nonDoneOnly) {
                    samples.put("excludedDone", jqlSample(admin, andJql(candidate, doneC), sample));
                }
                body.put("samples", samples);
            }
            body.put("counts", counts);
            body.put("ledger", reportApplicabilityLedgerImpact(admin, effMs, mode, nonDoneOnly, includeMissing, ledgerCap, sample));
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("GET /admin/report-applicability/impact", ex);
        }
    }

    /** Bounded AO-ledger cross-ref: ACTIVE rows that would become out-of-scope + total CLOSED. Read-only. */
    private Map<String, Object> reportApplicabilityLedgerImpact(ApplicationUser admin, Long effMs, String mode,
            boolean nonDoneOnly, boolean includeMissing, int cap, int sample) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
        try {
            Map<String, Object> diag = this.reportLedgerService.diagnostics();
            out.put("closedLedgerCountAllLevels", diag.get("closedCount"));
            out.put("activeLedgerCountAllLevels", diag.get("activeCount"));
            List<Map<String, Object>> active = this.reportLedgerService.listControls(
                    "ACTIVE", null, null, null, null, cap);
            com.atlassian.jira.issue.IssueManager im = ComponentAccessor.getIssueManager();
            int scanned = 0, wouldBecomeOOS = 0;
            ArrayList<String> samples = new ArrayList<String>();
            for (Map<String, Object> row : active) {
                Object k = row.get("issueKey");
                if (k == null) continue;
                com.atlassian.jira.issue.Issue issue = im.getIssueObject(String.valueOf(k));
                if (issue == null) continue;
                scanned++;
                long created = issue.getCreated() != null ? issue.getCreated().getTime() : 0L;
                Long due = issue.getDueDate() != null ? issue.getDueDate().getTime() : null;
                boolean isDone = issue.getStatus() != null && issue.getStatus().getStatusCategory() != null
                        && com.atlassian.jira.issue.status.category.StatusCategory.COMPLETE
                                .equals(issue.getStatus().getStatusCategory().getKey());
                String reason = JimReportConfig.evaluateApplicability(created, due, isDone,
                        true, nonDoneOnly, effMs, mode, includeMissing);
                if (reason != null) {
                    wouldBecomeOOS++;
                    if (samples.size() < sample) samples.add(issue.getKey() + " (" + reason + ")");
                }
            }
            out.put("activeScanned", scanned);
            out.put("activeWouldBecomeOutOfScope", wouldBecomeOOS);
            out.put("activeSamples", samples);
            out.put("scanCap", cap);
            out.put("note", "ACTIVE ledger rows are left frozen (not mutated) by the policy; refreshing them is a separate, approval-gated Phase 4 cleanup.");
        }
        catch (Exception ex) {
            out.put("error", ex.getMessage());
        }
        return out;
    }

    private static String buildDateScope(String mode, boolean includeMissing,
            String createdGE, String dueGE, String dueEmpty) {
        switch (mode == null ? "" : mode) {
            case "created_on_or_after":
                return createdGE;
            case "due_on_or_after":
                return includeMissing ? "(" + dueGE + " OR (" + dueEmpty + " AND " + createdGE + "))" : dueGE;
            case "created_and_due_on_or_after":
                return includeMissing
                        ? "(" + createdGE + " AND (" + dueGE + " OR " + dueEmpty + "))"
                        : "(" + createdGE + " AND " + dueGE + ")";
            case "created_or_due_on_or_after":
                return "(" + createdGE + " OR " + dueGE + ")";
            default:
                return createdGE;
        }
    }

    private long jqlCount(ApplicationUser user, String jql) {
        try {
            com.atlassian.jira.bc.issue.search.SearchService ss =
                    ComponentAccessor.getComponent(com.atlassian.jira.bc.issue.search.SearchService.class);
            com.atlassian.jira.bc.issue.search.SearchService.ParseResult pr = ss.parseQuery(user, jql);
            if (!pr.isValid()) {
                return -1L;
            }
            return ss.searchCount(user, pr.getQuery());
        } catch (Exception ex) {
            log.warn("report-applicability impact jqlCount failed jql={} msg={}", jql, ex.getMessage());
            return -1L;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<String> jqlSample(ApplicationUser user, String jql, int n) {
        ArrayList<String> out = new ArrayList<String>();
        if (n <= 0) {
            return out;
        }
        try {
            com.atlassian.jira.bc.issue.search.SearchService ss =
                    ComponentAccessor.getComponent(com.atlassian.jira.bc.issue.search.SearchService.class);
            com.atlassian.jira.bc.issue.search.SearchService.ParseResult pr =
                    ss.parseQuery(user, jql + " ORDER BY key ASC");
            if (!pr.isValid()) {
                return out;
            }
            com.atlassian.jira.web.bean.PagerFilter pager = new com.atlassian.jira.web.bean.PagerFilter(n);
            com.atlassian.jira.issue.search.SearchResults results = ss.search(user, pr.getQuery(), pager);
            for (Object o : results.getResults()) {
                out.add(((com.atlassian.jira.issue.Issue) o).getKey());
            }
        } catch (Exception ex) {
            out.add("error:" + ex.getMessage());
        }
        return out;
    }

    private static String andJql(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" AND ");
            sb.append("(").append(p).append(")");
        }
        return sb.toString();
    }

    private static String firstNonEmpty(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : (b != null ? b : "");
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @POST
    @Path(value="/reporting/backfill")
    @Consumes(value={"application/json"})
    public Response startReportingBackfill(Map<String, Object> request) {
        try {
            ApplicationUser admin = this.requireSysAdmin();
            if (!this.licenseService.canUseAdminSettings()) {
                return JimRestResponses.licenseBlocked();
            }
            String jql = request != null ? JimAdminResource.str(request, "jql") : null;
            int max = request != null ? JimAdminResource.intValue(request, "maxIssues", 500) : 500;
            boolean started = this.reportBackfillManager.start(admin, jql, max);
            if (!started) {
                return JimRestResponses.errorJson(409, "backfill_busy",
                        "A backfill is already running or the feature is disabled.");
            }
            this.auditService.record(admin.getKey(), "reporting.backfill",
                    "maxIssues=" + max + " jql=" + (jql == null ? "" : jql));
            return JimRestResponses.okJson(this.reportBackfillManager.status());
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("POST /admin/reporting/backfill", ex);
        }
    }

    @GET
    @Path(value="/reporting/backfill")
    public Response getReportingBackfill() {
        try {
            this.requireSysAdmin();
            return JimRestResponses.okJson(this.reportBackfillManager.status());
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("GET /admin/reporting/backfill", ex);
        }
    }

    // ===== Schedule-approval field mapping / diagnostics / provisioning ======

    @GET
    @Path(value="/approval-fields/diagnostics")
    public Response getApprovalFieldDiagnostics() {
        try {
            this.requireSysAdmin();
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("fields", com.corbitlogic.jira.internalmessenger.schedule.JimApprovalFields.diagnosticsAll());
            body.put("minimallyConfigured",
                    com.corbitlogic.jira.internalmessenger.schedule.JimApprovalFields.minimallyConfigured());
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("GET /admin/approval-fields/diagnostics", ex);
        }
    }

    @PUT
    @Path(value="/approval-fields/mapping")
    @Consumes(value={"application/json"})
    public Response updateApprovalFieldMapping(Map<String, Object> request) {
        try {
            ApplicationUser admin = this.requireSysAdmin();
            if (!this.licenseService.canUseAdminSettings()) {
                return JimRestResponses.licenseBlocked();
            }
            if (request == null) {
                return JimRestResponses.errorJson(400, "bad_request", "Request body is required");
            }
            String logicalKey = JimAdminResource.str(request, "logicalKey");
            String fieldId = JimAdminResource.str(request, "fieldId");
            com.corbitlogic.jira.internalmessenger.schedule.JimApprovalFields.Key key =
                    com.corbitlogic.jira.internalmessenger.schedule.JimApprovalFields.Key.byLogicalKey(logicalKey);
            if (key == null) {
                return JimRestResponses.errorJson(400, "bad_request", "Unknown logicalKey: " + logicalKey);
            }
            String settingsKey = com.corbitlogic.jira.internalmessenger.schedule.JimApprovalFields.settingsKey(key);
            String normalizedId = fieldId != null ? fieldId.trim() : "";
            // Empty clears the mapping (resolver falls back to the production id).
            if (!normalizedId.isEmpty()) {
                String reason = com.corbitlogic.jira.internalmessenger.schedule.JimApprovalFields
                        .validateMappingCandidate(key, normalizedId);
                if (reason != null) {
                    return JimRestResponses.errorJson(400, "invalid_mapping", reason);
                }
            }
            LinkedHashMap<String, Object> change = new LinkedHashMap<String, Object>();
            change.put(settingsKey, normalizedId);
            this.adminSettingsService.updateSettings(change);
            this.auditService.record(admin.getKey(), "approvalField.mapping",
                    logicalKey + " -> " + (normalizedId.isEmpty() ? "(cleared)" : normalizedId));
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("fields", com.corbitlogic.jira.internalmessenger.schedule.JimApprovalFields.diagnosticsAll());
            body.put("minimallyConfigured",
                    com.corbitlogic.jira.internalmessenger.schedule.JimApprovalFields.minimallyConfigured());
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("PUT /admin/approval-fields/mapping", ex);
        }
    }

    @POST
    @Path(value="/approval-fields/provision")
    @Consumes(value={"application/json"})
    public Response provisionApprovalFields(Map<String, Object> request) {
        try {
            ApplicationUser admin = this.requireSysAdmin();
            if (!this.licenseService.canUseAdminSettings()) {
                return JimRestResponses.licenseBlocked();
            }
            boolean addToScreens = request != null && JimAdminResource.bool(request, "addToScreens", false);
            List<Long> screenIds = new ArrayList<Long>();
            if (request != null && request.get("screenIds") instanceof List) {
                for (Object o : (List<?>) request.get("screenIds")) {
                    if (o instanceof Number) {
                        screenIds.add(((Number) o).longValue());
                    } else if (o != null) {
                        try {
                            screenIds.add(Long.parseLong(String.valueOf(o).trim()));
                        } catch (NumberFormatException ignore) {
                        }
                    }
                }
            }
            Map<String, Object> result = com.corbitlogic.jira.internalmessenger.schedule.JimApprovalFieldProvisioning
                    .provision(addToScreens, screenIds, this.adminSettingsService, this.auditService, admin);
            this.auditService.record(admin.getKey(), "approvalField.provision",
                    "addToScreens=" + addToScreens + " screens=" + screenIds + " summary=" + result.get("summary"));
            // Include fresh diagnostics so the UI reflects the new state immediately.
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>(result);
            body.put("diagnostics",
                    com.corbitlogic.jira.internalmessenger.schedule.JimApprovalFields.diagnosticsAll());
            body.put("minimallyConfigured",
                    com.corbitlogic.jira.internalmessenger.schedule.JimApprovalFields.minimallyConfigured());
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return this.internalError("POST /admin/approval-fields/provision", ex);
        }
    }

    /** Per-metric mapping health: mapped field id + whether it resolves + type-compatible. */
    private List<Map<String, Object>> reportMappingHealth() {
        ArrayList<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        Map<String, String> mappings = JimReportConfig.fieldMappings();
        for (Map.Entry<String, String> e : mappings.entrySet()) {
            String fieldId = e.getValue();
            if (fieldId == null || fieldId.isEmpty()) {
                continue; // disabled
            }
            LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("metric", e.getKey());
            m.put("fieldId", fieldId);
            JimReportMetrics.Def def = JimReportMetrics.byKey(e.getKey());
            boolean resolves = false;
            boolean compatible = false;
            String typeKey = null;
            try {
                com.atlassian.jira.issue.fields.CustomField cf =
                        ComponentAccessor.getCustomFieldManager().getCustomFieldObject(fieldId);
                if (cf != null && cf.getCustomFieldType() != null) {
                    resolves = true;
                    typeKey = cf.getCustomFieldType().getKey();
                    compatible = def != null && JimReportMetrics.isCompatible(def.category, typeKey);
                }
            } catch (Throwable ignore) {
            }
            m.put("category", def != null ? def.category.name() : null);
            m.put("resolves", resolves);
            m.put("compatible", compatible);
            m.put("fieldType", typeKey);
            m.put("healthy", resolves && compatible);
            out.add(m);
        }
        return out;
    }

    private static String param(HttpServletRequest request, String name) {
        return request != null ? request.getParameter(name) : null;
    }

    private static Long longParam(HttpServletRequest request, String name) {
        String v = param(request, name);
        if (v == null || v.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int intParam(HttpServletRequest request, String name, int def) {
        String v = param(request, name);
        if (v == null || v.trim().isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
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

