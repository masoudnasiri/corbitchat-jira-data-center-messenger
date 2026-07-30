package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.report.JimReportService;
import com.corbitlogic.jira.internalmessenger.report.JimReportSummary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-issue reporting API (Phase 6). Exposes the deterministic per-window ledger
 * and a lifecycle summary for a single issue. Web/admin surface only (no mobile
 * BFF). Every endpoint re-checks the caller's Jira browse permission server-side.
 */
@Path(value = "/reporting")
@Produces(value = {"application/json"})
public class JimReportingResource {
    private static final Logger log = LoggerFactory.getLogger(JimReportingResource.class);

    private final JiraAuthenticationContext authenticationContext;
    private final JimReportService reportService;

    @Inject
    public JimReportingResource(JiraAuthenticationContext authenticationContext, JimReportService reportService) {
        this.authenticationContext = authenticationContext;
        this.reportService = reportService;
    }

    @GET
    @Path(value = "/issues/{issueKey}/windows")
    public Response getIssueWindows(@PathParam(value = "issueKey") String issueKey) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            List<JimReportSummary.WindowInfo> windows = this.reportService.computeWindows(issue);
            List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
            for (JimReportSummary.WindowInfo w : windows) {
                items.add(windowJson(w));
            }
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("issueKey", issue.getKey());
            body.put("windows", items);
            body.put("count", items.size());
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/reporting/issues/" + issueKey + "/windows",
                    viewer.getKey(), ex, "internal_error", "An internal error occurred while loading windows.");
        }
    }

    @GET
    @Path(value = "/issues/{issueKey}/summary")
    public Response getIssueSummary(@PathParam(value = "issueKey") String issueKey) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            JimReportSummary s = this.reportService.computeSummary(issue);
            return JimRestResponses.okJson(lifecycleJson(issue.getKey(), s));
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/reporting/issues/" + issueKey + "/summary",
                    viewer.getKey(), ex, "internal_error", "An internal error occurred while loading the summary.");
        }
    }

    private Map<String, Object> windowJson(JimReportSummary.WindowInfo w) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("windowStart", w.windowStart);
        m.put("windowEnd", w.windowEnd);
        m.put("status", w.status);
        m.put("validCount", w.validCount);
        m.put("rawCount", w.rawCount);
        m.put("duplicateCount", w.duplicateCount);
        m.put("firstValidReportMs", w.firstValidReportMs);
        m.put("lastReportMs", w.lastReportMs);
        m.put("lateByMinutes", w.lateByMinutes);
        m.put("commentIds", w.commentIds);
        return m;
    }

    private Map<String, Object> lifecycleJson(String issueKey, JimReportSummary s) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("issueKey", issueKey);
        m.put("reportingControlState", s.reportingControlState);
        m.put("submitActive", s.submitActive);
        m.put("reportRequired", s.reportRequired);
        m.put("statusCode", s.statusCode);
        m.put("compliance", s.compliance);
        m.put("levelCode", s.levelCode);
        m.put("countMode", s.countMode);
        m.put("activeUntil", s.activeUntil);
        m.put("reportingStartAt", s.reportingStartAt);
        m.put("reportingEndAt", s.reportingEndsMs);
        m.put("reportingClosedAt", s.reportingClosedAt);
        m.put("reportingClosedByCondition", s.reportingClosedByCondition);
        m.put("reportingCloseReason", s.reportingCloseReason);
        m.put("nextReportDueAt", s.nextReportDueMs);
        m.put("requiredCount", s.requiredCount);
        m.put("validCount", s.validCount);
        m.put("missedCount", s.missedCount);
        m.put("satisfiedWindowCount", s.satisfiedWindowCount);
        m.put("missedWindowCount", s.missedWindowCount);
        m.put("openWindowCount", s.openWindowCount);
        m.put("futureWindowCount", s.futureWindowCount);
        m.put("overdueOpenCount", s.overdueOpenCount);
        m.put("totalRequiredReportsUntilEnd", s.totalRequiredUntilEnd);
        m.put("currentMissedReportStreak", s.currentMissedStreak);
        m.put("maxMissedReportStreak", s.maxMissedStreak);
        m.put("reportCompliancePercent", s.requiredSoFar > 0
                ? Math.round(100.0 * s.validSoFar / s.requiredSoFar) : 100L);
        m.put("todayRequiredCount", s.todayRequiredCount);
        m.put("todayValidCount", s.todayValidCount);
        m.put("todayMissedCount", s.todayMissedCount);
        m.put("reportedToday", s.reportedToday);
        m.put("currentWindowStartMs", s.currentWindowStartMs);
        m.put("currentWindowStatus", s.currentWindowStatus);
        m.put("minutesSinceLastValidReport", s.minutesSinceLastValidReport);
        m.put("currentlyOverdueMinutes", s.currentlyOverdueMinutes);
        m.put("responsibleUserKey", s.responsibleUserKey);
        m.put("responsibleUserName", s.responsibleUserName);
        m.put("calcDurationMs", s.calcDurationMs);
        m.put("calculationVersion", s.calculationVersion);
        return m;
    }

    private MutableIssue loadBrowsable(String issueKey, ApplicationUser viewer) {
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return null;
        }
        MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(issueKey.trim());
        if (issue == null) {
            return null;
        }
        if (!ComponentAccessor.getPermissionManager()
                .hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue) issue, viewer)) {
            return null;
        }
        return issue;
    }
}
