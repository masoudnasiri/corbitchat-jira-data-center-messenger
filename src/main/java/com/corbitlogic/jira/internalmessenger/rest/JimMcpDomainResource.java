package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.query.Query;

import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileDates;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileIssues;
import com.corbitlogic.jira.internalmessenger.report.JimReportService;
import com.corbitlogic.jira.internalmessenger.report.JimReportSummary;
import com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalActions;
import com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalConfig;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.service.JimMobileFeatureService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Canonical MCP-domain REST endpoints for reports, schedule/time approval, and Assistant.
 * DELEGATES to existing plugin services (no new business logic; no duplicated calculations).
 * Executes as the authenticated Jira user; Jira-native + plugin permissions apply.
 * Auto-discovered under /rest/jim/1.0/mcp-domain via the rest package scan.
 */
@Path("/mcp-domain")
@Produces({"application/json"})
public class JimMcpDomainResource {

    private static final Logger log = LoggerFactory.getLogger(JimMcpDomainResource.class);
    private static final int MAX_BATCH = 20;

    private final JiraAuthenticationContext authenticationContext;
    private final SearchService searchService;
    private final AvatarService avatarService;
    private final ApplicationProperties applicationProperties;
    private final JimMobileFeatureService featureService;
    private final JimReportService reportService;
    private final JimConversationService conversationService;
    private final JimMessageService messageService;
    private final JimReadStateService readStateService;
    private final JimRestJsonMapper restJsonMapper;

    @Inject
    public JimMcpDomainResource(JiraAuthenticationContext authenticationContext, SearchService searchService,
                                AvatarService avatarService, ApplicationProperties applicationProperties,
                                JimMobileFeatureService featureService, JimReportService reportService,
                                JimConversationService conversationService, JimMessageService messageService,
                                JimReadStateService readStateService, JimRestJsonMapper restJsonMapper) {
        this.authenticationContext = authenticationContext;
        this.searchService = searchService;
        this.avatarService = avatarService;
        this.applicationProperties = applicationProperties;
        this.featureService = featureService;
        this.reportService = reportService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.readStateService = readStateService;
        this.restJsonMapper = restJsonMapper;
    }

    private ApplicationUser viewer() {
        return this.authenticationContext.getLoggedInUser();
    }

    private MutableIssue loadBrowsable(String issueKey, ApplicationUser viewer) {
        if (issueKey == null) return null;
        MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(issueKey.trim());
        if (issue == null) return null;
        if (!ComponentAccessor.getPermissionManager().hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue) issue, viewer)) {
            return null;
        }
        return issue;
    }

    private static String projectOf(String issueKey) {
        int i = issueKey == null ? -1 : issueKey.indexOf('-');
        return i > 0 ? issueKey.substring(0, i) : null;
    }

    // ---------------------------------------------------------------- reports
    @GET
    @Path("/reports")
    public Response reportsList(@QueryParam("startAt") Integer startAt, @QueryParam("maxResults") Integer maxResults) {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.TASKS)) {
            return JimRestResponses.errorJson(403, "feature_disabled", "Reports feature not enabled for your account");
        }
        int start = startAt == null || startAt < 0 ? 0 : startAt;
        int max = maxResults == null || maxResults < 1 || maxResults > 50 ? 25 : maxResults;
        try {
            Query query = JimMobileIssues.buildQuery(viewer, this.searchService, "assigned");
            PagerFilter<Issue> pager = new PagerFilter<Issue>(max);
            pager.setStart(start);
            SearchResults<Issue> results = this.searchService.search(viewer, query, pager);
            List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
            int req = 0, submitted = 0, valid = 0, oow = 0, dup = 0, missing = 0, maxStreak = 0, configured = 0;
            Integer tzOff = null;
            for (Issue issue : results.getResults()) {
                JimReportSummary s = this.reportService.computeSummary(issue);
                if (s == null || !s.configured || s.levelCode == null || !s.applicable) continue;
                if (tzOff == null) tzOff = s.tzOffsetMinutes;
                Map<String, Object> it = new LinkedHashMap<String, Object>();
                it.put("scopeType", "issue_lifetime");
                it.put("issueKey", issue.getKey());
                it.put("projectKey", projectOf(issue.getKey()));
                it.put("summary", issue.getSummary());
                it.put("levelCode", s.levelCode);
                it.put("countMode", s.countMode);
                it.put("statusCode", s.statusCode);
                it.put("reportingControlState", s.reportingControlState);
                it.put("windowStartMinute", s.windowStartMinute);
                it.put("windowEndMinute", s.windowEndMinute);
                it.put("intervalMinutes", s.intervalMinutes);
                it.put("requiredCount", s.requiredCount);
                it.put("requiredSoFar", s.requiredSoFar);
                it.put("submittedCount", s.rawCount);
                it.put("validCount", s.validCount);
                it.put("validSoFar", s.validSoFar);
                it.put("outOfWindowCount", s.outOfWindowCount);
                it.put("duplicateCount", s.duplicateCount);
                it.put("missedCount", s.missedCount);
                it.put("maxConsecutiveMissed", s.maxMissedStreak);
                it.put("submitActive", s.submitActive);
                it.put("tzOffsetMinutes", s.tzOffsetMinutes);
                items.add(it);
                configured++;
                req += s.requiredSoFar; submitted += s.rawCount; valid += s.validSoFar;
                oow += s.outOfWindowCount; dup += s.duplicateCount; missing += s.missedCount;
                if (s.maxMissedStreak > maxStreak) maxStreak = s.maxMissedStreak;
            }
            Map<String, Object> summary = new LinkedHashMap<String, Object>();
            summary.put("scope", "issue_lifetime_sum");
            summary.put("requiredCount", req);
            summary.put("submittedCount", submitted);
            summary.put("validCount", valid);
            summary.put("outsideWindowCount", oow);
            summary.put("duplicateCount", dup);
            summary.put("invalidCount", oow + dup);
            summary.put("missingCount", missing);
            summary.put("maximumConsecutiveMissing", maxStreak);
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("scopeType", "issue_lifetime");
            body.put("scopeNote", "Per-issue LIFETIME compliance from JimReportService (not a weekly/user-period aggregate). Report the plugin numbers verbatim; do not re-classify reports or recompute from raw comments.");
            body.put("timezone", "GMT" + (tzOff == null ? "" : (tzOff >= 0 ? "+" : "-") + String.format("%02d:%02d", Math.abs(tzOff) / 60, Math.abs(tzOff) % 60)));
            body.put("tzOffsetMinutes", tzOff == null ? 0 : tzOff.intValue());
            body.put("reports", items);
            body.put("summary", summary);
            body.put("reportConfiguredIssues", configured);
            body.put("startAt", start);
            body.put("maxResults", max);
            body.put("total", results.getTotal());
            body.put("warnings", new ArrayList<String>());
            return JimRestResponses.okJson(body);
        } catch (Exception e) {
            log.error("mcp-domain reports list failed", e);
            return JimRestResponses.errorJson(500, "error", "Failed to list reports");
        }
    }

    @POST
    @Path("/reports/validate")
    @Consumes({"application/json"})
    public Response reportsValidate(Map<String, Object> body) {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        String issueKey = body == null ? null : String.valueOf(body.get("issue_key"));
        MutableIssue issue = loadBrowsable(issueKey, viewer);
        if (issue == null) return JimRestResponses.errorJson(404, "not_found", "Issue not found or not accessible");
        try {
            JimReportSummary s = this.reportService.computeSummary(issue);
            boolean canComment = ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.ADD_COMMENTS, (Issue) issue, viewer);
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("issueKey", issue.getKey());
            boolean configured = s != null && s.configured && s.levelCode != null && s.applicable;
            boolean canSubmit = configured && s.submitActive && canComment;
            out.put("configured", configured);
            out.put("applicable", s != null && s.applicable);
            out.put("submitActive", s != null && s.submitActive);
            out.put("canComment", canComment);
            out.put("canSubmit", canSubmit);
            out.put("levelCode", s == null ? null : s.levelCode);
            out.put("statusCode", s == null ? null : s.statusCode);
            out.put("reportingControlState", s == null ? null : s.reportingControlState);
            out.put("requiredSoFar", s == null ? 0 : s.requiredSoFar);
            out.put("validSoFar", s == null ? 0 : s.validSoFar);
            out.put("missedCount", s == null ? 0 : s.missedCount);
            List<String> msgs = new ArrayList<String>();
            if (!configured) msgs.add("Reporting is not configured/applicable for this issue.");
            if (configured && !s.submitActive) msgs.add("Reporting window is closed or the issue is inactive.");
            if (!canComment) msgs.add("You do not have permission to add reports on this issue.");
            out.put("validationMessages", msgs);
            out.put("sideEffects", false);
            return JimRestResponses.okJson(out);
        } catch (Exception e) {
            log.error("mcp-domain reports validate failed", e);
            return JimRestResponses.errorJson(500, "error", "Validation failed");
        }
    }

    // ---------------------------------------------------------------- schedule approval (by issue key)
    private Map<String, Object> scheduleProjection(Issue issue, ApplicationUser viewer) {
        Map<String, Object> p = JimScheduleApprovalConfig.projection(issue, viewer);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("issueKey", issue.getKey());
        out.put("projectKey", projectOf(issue.getKey()));
        out.put("summary", issue.getSummary());
        boolean configured = JimScheduleApprovalConfig.isConfigured();
        out.put("configured", configured);
        if (p != null) out.putAll(p);
        Date proposed = null, approved = null;
        ApplicationUser approver = null;
        String status = null;
        try { proposed = JimScheduleApprovalConfig.getProposedDate(issue); } catch (Exception ignore) { }
        try { approved = JimScheduleApprovalConfig.getApprovedDate(issue); } catch (Exception ignore) { }
        try { approver = JimScheduleApprovalConfig.getApprover(issue); } catch (Exception ignore) { }
        try { status = JimScheduleApprovalConfig.getStatusValue(issue); } catch (Exception ignore) { }
        out.put("proposedDateYmd", proposed == null ? null : JimMobileDates.toYmd(proposed));
        out.put("approvedDateYmd", approved == null ? null : JimMobileDates.toYmd(approved));
        boolean approverConfigured = approver != null;
        boolean isApprover = approver != null && approver.getKey().equals(viewer.getKey());
        boolean canEdit = ComponentAccessor.getPermissionManager()
                .hasPermission(ProjectPermissions.EDIT_ISSUES, (Issue) issue, viewer);
        boolean pending = status == null || status.isEmpty() || "Pending".equalsIgnoreCase(status);
        List<String> blocking = new ArrayList<String>();
        if (!configured) blocking.add("not_configured");
        if (configured && !approverConfigured) blocking.add("approver_not_configured");
        if (configured && JimMobileDates.findProposedCompletionField() == null) blocking.add("proposed_field_not_mapped");
        if (configured && !canEdit) blocking.add("no_edit_permission");
        boolean canSubmit = configured && canEdit && JimMobileDates.findProposedCompletionField() != null;
        List<String> decideBlocking = new ArrayList<String>();
        if (!configured) decideBlocking.add("not_configured");
        if (!isApprover) decideBlocking.add("not_approver");
        if (proposed == null) decideBlocking.add("no_proposed_value");
        if (!pending) decideBlocking.add("already_decided");
        boolean canDecide = configured && isApprover && proposed != null && pending;
        out.put("approverConfigured", approverConfigured);
        out.put("isApprover", isApprover);
        out.put("canSubmit", canSubmit);
        out.put("canDecide", canDecide);
        out.put("requiredFormat", "yyyy-MM-dd");
        out.put("blockingReasons", blocking);
        out.put("decisionBlockingReasons", decideBlocking);
        List<String> actions = new ArrayList<String>();
        if (canSubmit) actions.add("submit");
        if (canDecide) { actions.add("approve"); actions.add("reject"); }
        out.put("allowedActions", actions);
        out.put("domain", "corbit_schedule_approval");
        return out;
    }

    @GET
    @Path("/approvals/schedule/{issueKey}")
    public Response scheduleGet(@PathParam("issueKey") String issueKey) {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        MutableIssue issue = loadBrowsable(issueKey, viewer);
        if (issue == null) return JimRestResponses.errorJson(404, "not_found", "Issue not found or not accessible");
        if (!JimScheduleApprovalConfig.isConfigured()) {
            return JimRestResponses.errorJson(409, "not_configured", "Schedule approval is not configured");
        }
        return JimRestResponses.okJson(scheduleProjection(issue, viewer));
    }

    @POST
    @Path("/approvals/schedule/{issueKey}/submit")
    @Consumes({"application/json"})
    public Response scheduleSubmit(@PathParam("issueKey") String issueKey, Map<String, Object> body) {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        if (!JimScheduleApprovalConfig.isConfigured()) {
            return JimRestResponses.errorJson(409, "not_configured", "Schedule approval is not configured");
        }
        MutableIssue issue = loadBrowsable(issueKey, viewer);
        if (issue == null) return JimRestResponses.errorJson(404, "not_found", "Issue not found or not accessible");
        if (!ComponentAccessor.getPermissionManager().hasPermission(ProjectPermissions.EDIT_ISSUES, (Issue) issue, viewer)) {
            return JimRestResponses.errorJson(403, "no_permission", "You cannot submit a proposed schedule for this issue");
        }
        String proposedYmd = body == null ? null : (body.get("proposed_date") == null ? null : String.valueOf(body.get("proposed_date")));
        String approverName = body == null ? null : (body.get("approver_username") == null ? null : String.valueOf(body.get("approver_username")));
        boolean validateOnly = body != null && Boolean.TRUE.equals(body.get("validate_only"));
        try {
            Date proposed = null;
            boolean parseError = false;
            if (proposedYmd != null && !proposedYmd.trim().isEmpty() && !"null".equals(proposedYmd)) {
                try { proposed = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(proposedYmd.trim()); }
                catch (java.text.ParseException pe) { parseError = true; }
            }
            ApplicationUser approver = approverName == null ? null : ComponentAccessor.getUserManager().getUserByName(approverName);
            if (validateOnly) {
                Map<String, Object> v = new LinkedHashMap<String, Object>();
                Map<String, Object> proj = scheduleProjection(issue, viewer);
                List<String> reasons = new ArrayList<String>();
                Object pb = proj.get("blockingReasons");
                if (pb instanceof List) reasons.addAll((List<String>) pb);
                if (approver != null) reasons.remove("approver_not_configured");  // caller supplies approver
                if (parseError) reasons.add("bad_date_format");
                if (approverName != null && approver == null) reasons.add("approver_not_found");
                if (proposed == null && !parseError) reasons.add("no_proposed_value");
                boolean valid = reasons.isEmpty();
                v.put("issueKey", issue.getKey());
                v.put("validateOnly", true);
                v.put("valid", valid);
                v.put("canSubmit", Boolean.TRUE.equals(proj.get("canSubmit")) && !parseError);
                v.put("normalizedValue", proposed == null ? null : JimMobileDates.toYmd(proposed));
                v.put("requiredFormat", "yyyy-MM-dd");
                v.put("approverConfigured", proj.get("approverConfigured"));
                v.put("resultingApprovalState", "Pending");
                v.put("blockingReasons", reasons);
                v.put("warnings", new ArrayList<String>());
                v.put("sideEffects", false);
                v.put("domain", "corbit_schedule_approval");
                return JimRestResponses.okJson(v);
            }
            if (parseError) return JimRestResponses.errorJson(400, "bad_date", "proposed_date must be yyyy-MM-dd");
            ApplicationUser existingApprover = null;
            try { existingApprover = JimScheduleApprovalConfig.getApprover(issue); } catch (Exception ignore) { }
            if (approver == null && existingApprover == null) {
                return JimRestResponses.errorJson(409, "approver_not_configured",
                        "An approver must be provided (approver_username); none is configured on this issue.");
            }
            com.atlassian.jira.issue.fields.CustomField proposedCf = JimMobileDates.findProposedCompletionField();
            com.atlassian.jira.issue.fields.CustomField approverCf = JimScheduleApprovalConfig.resolveApproverField();
            if (proposedCf == null || approverCf == null) {
                return JimRestResponses.errorJson(409, "not_configured", "Schedule fields are not mapped");
            }
            issue.setCustomFieldValue(proposedCf, proposed == null ? null : new java.sql.Timestamp(proposed.getTime()));
            if (approver != null) issue.setCustomFieldValue(approverCf, approver);
            ComponentAccessor.getIssueManager().updateIssue(viewer, issue,
                    com.atlassian.jira.event.type.EventDispatchOption.ISSUE_UPDATED, false);
            MutableIssue refreshed = loadBrowsable(issueKey, viewer);
            return JimRestResponses.okJson(scheduleProjection(refreshed != null ? refreshed : issue, viewer));
        } catch (Exception e) {
            log.error("mcp-domain schedule submit failed", e);
            return JimRestResponses.errorJson(500, "error", "Failed to submit proposed schedule");
        }
    }

    @POST
    @Path("/approvals/schedule/{issueKey}/decision")
    @Consumes({"application/json"})
    public Response scheduleDecision(@PathParam("issueKey") String issueKey, Map<String, Object> body) {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        String decision = body == null ? null : (body.get("decision") == null ? null : String.valueOf(body.get("decision")));
        String reason = body == null ? null : (body.get("reason") == null ? null : String.valueOf(body.get("reason")));
        JimScheduleApprovalActions.Result result =
                JimScheduleApprovalActions.executeForIssue(issueKey, viewer, decision, reason);
        if (!result.isOk()) {
            return JimRestResponses.errorJson(result.statusCode, result.errorCode, result.errorMessage);
        }
        MutableIssue issue = loadBrowsable(issueKey, viewer);
        if (issue == null) {
            Map<String, Object> ok = new LinkedHashMap<String, Object>();
            ok.put("ok", true); ok.put("issueKey", issueKey); ok.put("decision", decision);
            return JimRestResponses.okJson(ok);
        }
        return JimRestResponses.okJson(scheduleProjection(issue, viewer));
    }

    @GET
    @Path("/approvals/schedule/pending")
    public Response schedulePending(@QueryParam("maxResults") Integer maxResults) {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        int count = 0;
        boolean configured = JimScheduleApprovalConfig.isConfigured();
        if (configured) {
            try {
                int max = maxResults == null || maxResults < 1 || maxResults > 50 ? 25 : maxResults;
                String approverClause = JimScheduleApprovalConfig.approverFieldJql();
                String statusClause = JimScheduleApprovalConfig.statusFieldJql();
                String proposedClause = JimMobileDates.proposedCompletionClause();
                String jql = approverClause + " = currentUser() AND (" + statusClause + " = \"Pending\" OR "
                        + statusClause + " is EMPTY) AND " + proposedClause + " is not EMPTY ORDER BY updated DESC";
                SearchService.ParseResult pr = this.searchService.parseQuery(viewer, jql);
                if (pr.isValid()) {
                    Query q = pr.getQuery();
                    count = (int) this.searchService.searchCount(viewer, q);
                    SearchResults<Issue> results = this.searchService.search(viewer, q, new PagerFilter<Issue>(max));
                    for (Issue issue : results.getResults()) {
                        Map<String, Object> it = new LinkedHashMap<String, Object>();
                        it.put("issueKey", issue.getKey());
                        it.put("projectKey", projectOf(issue.getKey()));
                        it.put("summary", issue.getSummary());
                        Date proposed = JimScheduleApprovalConfig.getProposedDate(issue);
                        it.put("proposedDateYmd", proposed == null ? null : JimMobileDates.toYmd(proposed));
                        it.put("status", JimScheduleApprovalConfig.getStatusValue(issue));
                        it.put("availableActions", java.util.Arrays.asList("approve", "reject"));
                        items.add(it);
                    }
                }
            } catch (Exception e) {
                log.error("mcp-domain pending approvals failed", e);
            }
        }
        out.put("configured", configured);
        out.put("count", count);
        out.put("pending", items);
        out.put("domain", "corbit_schedule_approval");
        return JimRestResponses.okJson(out);
    }

    // ---------------------------------------------------------------- assistant
    private JimConversation systemConversation(ApplicationUser viewer) {
        return this.conversationService.getOrCreateSystemConversation(viewer.getKey());
    }

    @GET
    @Path("/assistant/events")
    public Response assistantEvents(@QueryParam("eventType") String eventType,
                                    @QueryParam("issueKey") String issueKey,
                                    @QueryParam("limit") Integer limit) {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        try {
            JimConversation sys = systemConversation(viewer);
            int max = limit == null || limit < 1 || limit > 100 ? 30 : limit;
            List<JimMessage> messages = this.messageService.listMessages(sys.getID(), max, null);
            List<JimMessage> filtered = new ArrayList<JimMessage>();
            for (JimMessage m : messages) {
                if (eventType != null && !eventType.isEmpty()
                        && (m.getEventType() == null || !m.getEventType().equalsIgnoreCase(eventType))) continue;
                if (issueKey != null && !issueKey.isEmpty()) {
                    String body = m.getBody() == null ? "" : m.getBody();
                    if (!body.contains(issueKey)) continue;
                }
                filtered.add(m);
            }
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("events", this.restJsonMapper.toMessageMaps(filtered, viewer, sys, viewer.getKey()));
            body.put("count", filtered.size());
            body.put("domain", "corbit_assistant_event");
            return JimRestResponses.okJson(body);
        } catch (Exception e) {
            log.error("mcp-domain assistant events failed", e);
            return JimRestResponses.errorJson(500, "error", "Failed to list assistant events");
        }
    }

    @GET
    @Path("/assistant/events/{eventId}")
    public Response assistantEvent(@PathParam("eventId") int eventId) {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        try {
            JimConversation sys = systemConversation(viewer);
            JimMessage m = this.messageService.getMessageForParticipant(eventId, viewer.getKey());
            if (m == null) return JimRestResponses.errorJson(404, "not_found", "Event not found");
            List<Map<String, Object>> mapped = this.restJsonMapper.toMessageMaps(
                    java.util.Collections.singletonList(m), viewer, sys, viewer.getKey());
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("event", mapped.isEmpty() ? null : mapped.get(0));
            body.put("domain", "corbit_assistant_event");
            return JimRestResponses.okJson(body);
        } catch (Exception e) {
            return JimRestResponses.errorJson(404, "not_found", "Event not found or not accessible");
        }
    }

    @POST
    @Path("/assistant/events/mark-read")
    @Consumes({"application/json"})
    @SuppressWarnings("unchecked")
    public Response assistantMarkRead(Map<String, Object> body) {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        List<Object> ids = body != null && body.get("event_ids") instanceof List ? (List<Object>) body.get("event_ids") : null;
        if (ids == null || ids.isEmpty()) return JimRestResponses.errorJson(400, "bad_request", "event_ids required");
        if (ids.size() > MAX_BATCH) return JimRestResponses.errorJson(400, "too_many", "Max " + MAX_BATCH + " events per call");
        int marked = 0;
        for (Object idObj : ids) {
            try {
                int id = Integer.parseInt(String.valueOf(idObj));
                if (this.messageService.markActioned(id, viewer.getKey()) != null) marked++;
            } catch (Exception ignore) { }
        }
        // also advance the conversation read pointer for the user
        try {
            JimConversation sys = systemConversation(viewer);
            this.readStateService.markConversationRead(sys.getID(), viewer.getKey());
        } catch (Exception ignore) { }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("ok", true); out.put("marked", marked); out.put("domain", "corbit_assistant_event");
        return JimRestResponses.okJson(out);
    }
}
