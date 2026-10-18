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
import com.corbitlogic.jira.internalmessenger.report.JimReportConfig;
import com.corbitlogic.jira.internalmessenger.report.JimReportService;
import com.corbitlogic.jira.internalmessenger.report.JimReportSummary;
import com.atlassian.jira.permission.GlobalPermissionKey;
import java.util.Calendar;
import java.util.TimeZone;
import com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalActions;
import com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalConfig;
import com.corbitlogic.jira.internalmessenger.ao.JimGroupMember;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimGroupService;
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.service.JimMobileFeatureService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import com.corbitlogic.jira.internalmessenger.service.JimSharedContentService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
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
    private final JimSharedContentService sharedContentService;

    @Inject
    public JimMcpDomainResource(JiraAuthenticationContext authenticationContext, SearchService searchService,
                                AvatarService avatarService, ApplicationProperties applicationProperties,
                                JimMobileFeatureService featureService, JimReportService reportService,
                                JimConversationService conversationService, JimMessageService messageService,
                                JimReadStateService readStateService, JimRestJsonMapper restJsonMapper,
                                JimSharedContentService sharedContentService) {
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
        this.sharedContentService = sharedContentService;
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
    private static final java.util.Set<String> PERIOD_EXCLUDE =
            new java.util.HashSet<String>(java.util.Arrays.asList("CLOSED_NOT_REQUIRED", "EXEMPT", "FUTURE"));

    private static String tzLabel(int off) {
        return "GMT" + (off >= 0 ? "+" : "-") + String.format("%02d:%02d", Math.abs(off) / 60, Math.abs(off) % 60);
    }

    private TimeZone reportTz() {
        try { TimeZone tz = JimReportConfig.reportTimeZone(); if (tz != null) return tz; } catch (Exception ignore) { }
        return TimeZone.getDefault();
    }

    private String ymdInTz(long ms, TimeZone tz) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd");
        f.setTimeZone(tz);
        return f.format(new Date(ms));
    }

    private long parseYmd(String ymd, TimeZone tz, long dflt, boolean endOfDay) {
        if (ymd == null || ymd.trim().isEmpty()) return dflt;
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd");
            f.setTimeZone(tz);
            Calendar c = Calendar.getInstance(tz);
            c.setTime(f.parse(ymd.trim()));
            if (endOfDay) { c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999); }
            else { c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); }
            return c.getTimeInMillis();
        } catch (Exception e) { return dflt; }
    }

    /** [fromMs, toMs] in the canonical report timezone. Iranian week starts Saturday. */
    private long[] periodBounds(String period, String dateFrom, String dateTo, TimeZone tz) {
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        long startToday = c.getTimeInMillis();
        String p = period == null ? "" : period.trim().toLowerCase();
        boolean hasDates = (dateFrom != null && !dateFrom.isEmpty()) || (dateTo != null && !dateTo.isEmpty());
        if (hasDates) p = "custom";
        if (p.isEmpty()) p = "current_week";
        long from, to = now;
        if (p.equals("today")) {
            from = startToday;
        } else if (p.equals("last_3_days")) {
            from = startToday - 2L * 86400000L;
        } else if (p.equals("current_month")) {
            Calendar m = Calendar.getInstance(tz); m.setTimeInMillis(startToday); m.set(Calendar.DAY_OF_MONTH, 1);
            from = m.getTimeInMillis();
        } else if (p.equals("custom")) {
            from = parseYmd(dateFrom, tz, startToday, false);
            to = parseYmd(dateTo, tz, now, true);
        } else { // current_week (Saturday-based)
            Calendar w = Calendar.getInstance(tz); w.setTimeInMillis(startToday); w.setFirstDayOfWeek(Calendar.SATURDAY);
            int dow = w.get(Calendar.DAY_OF_WEEK);
            int diff = (dow - Calendar.SATURDAY + 7) % 7;
            w.add(Calendar.DAY_OF_MONTH, -diff);
            from = w.getTimeInMillis();
        }
        return new long[]{from, to};
    }

    /** Canonical period metrics for one issue: windows (interval/daily) + items (all modes). No gateway recompute. */
    private Map<String, Object> periodMetrics(Issue issue, long from, long to) {
        JimReportSummary s = this.reportService.computeSummary(issue);
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("issueKey", issue.getKey());
        m.put("projectKey", projectOf(issue.getKey()));
        m.put("summary", issue.getSummary());
        if (s == null || !s.configured || s.levelCode == null || !s.applicable) { m.put("configured", false); return m; }
        m.put("configured", true);
        m.put("levelCode", s.levelCode);
        m.put("countMode", s.countMode);
        m.put("deadlineState", s.statusCode);
        m.put("reportingControlState", s.reportingControlState);
        m.put("tzOffsetMinutes", s.tzOffsetMinutes);
        m.put("responsibleUserKey", s.responsibleUserKey);
        m.put("responsibleUserName", s.responsibleUserName);
        int submitted = 0, valid = 0, oow = 0, dup = 0;
        if (s.items != null) for (JimReportSummary.Item it : s.items) {
            if (it.createdMs < from || it.createdMs >= to) continue;
            submitted++;
            if (it.status == 0) valid++; else if (it.status == 1) dup++; else oow++;
        }
        boolean hasWindows = s.windowLedger != null && !s.windowLedger.isEmpty();
        Integer required = null; int missed = 0, maxStreak = 0, streak = 0;
        if (hasWindows) {
            java.util.List<JimReportSummary.WindowInfo> ws = new ArrayList<JimReportSummary.WindowInfo>();
            for (JimReportSummary.WindowInfo w : s.windowLedger) {
                if (w.windowStart < from || w.windowStart >= to) continue;
                if (PERIOD_EXCLUDE.contains(w.status)) continue;
                ws.add(w);
            }
            java.util.Collections.sort(ws, new java.util.Comparator<JimReportSummary.WindowInfo>() {
                public int compare(JimReportSummary.WindowInfo a, JimReportSummary.WindowInfo b) { return Long.compare(a.windowStart, b.windowStart); }
            });
            required = 0;
            for (JimReportSummary.WindowInfo w : ws) {
                required++;
                if ("MISSED".equals(w.status)) { missed++; streak++; if (streak > maxStreak) maxStreak = streak; }
                else if ("SATISFIED".equals(w.status) || "LATE".equals(w.status)) { streak = 0; }
            }
        } else {
            m.put("note", "fixed_minimum: period required-count is not window-scoped; counts are report-item based");
        }
        m.put("requiredCount", required);
        m.put("submittedCount", submitted);
        m.put("validCount", valid);
        m.put("outOfWindowCount", oow);
        m.put("duplicateCount", dup);
        m.put("missedCount", hasWindows ? missed : 0);
        m.put("maximumConsecutiveMissed", hasWindows ? maxStreak : 0);
        return m;
    }

    private static int i(Object o) { return o instanceof Number ? ((Number) o).intValue() : 0; }

    @GET
    @Path("/reports")
    public Response reportsList(@QueryParam("scope_type") String scopeType,
                               @QueryParam("issue_key") String issueKey,
                               @QueryParam("date_from") String dateFrom,
                               @QueryParam("date_to") String dateTo,
                               @QueryParam("period") String period,
                               @QueryParam("user_key") String userKey,
                               @QueryParam("project_keys") String projectKeys,
                               @QueryParam("include_items") Boolean includeItems,
                               @QueryParam("startAt") Integer startAt,
                               @QueryParam("maxResults") Integer maxResults) {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.TASKS)) {
            return JimRestResponses.errorJson(403, "feature_disabled", "Reports feature not enabled for your account");
        }
        String requested = scopeType == null ? "" : scopeType.trim();
        String applied = requested;
        if (applied.isEmpty()) {
            if (issueKey != null && !issueKey.isEmpty()) applied = "issue_lifetime";
            else if (period != null || dateFrom != null || dateTo != null) applied = "self_period";
            else applied = "issue_lifetime";
        }
        try {
            if (applied.equals("manager_team_period")) return managerTeamPeriod(viewer, requested, period, dateFrom, dateTo, userKey, projectKeys, Boolean.TRUE.equals(includeItems));
            if (applied.equals("self_period")) return selfPeriod(viewer, requested, period, dateFrom, dateTo, Boolean.TRUE.equals(includeItems));
            return issueLifetime(viewer, requested, issueKey, startAt, maxResults);
        } catch (Exception e) {
            log.error("mcp-domain reports failed", e);
            return JimRestResponses.errorJson(500, "error", "Failed to list reports");
        }
    }

    private Response issueLifetime(ApplicationUser viewer, String requested, String issueKey, Integer startAt, Integer maxResults) throws com.atlassian.jira.issue.search.SearchException {
        int start = startAt == null || startAt < 0 ? 0 : startAt;
        int max = maxResults == null || maxResults < 1 || maxResults > 50 ? 25 : maxResults;
        List<Issue> issues = new ArrayList<Issue>();
        int total;
        if (issueKey != null && !issueKey.isEmpty()) {
            MutableIssue one = loadBrowsable(issueKey, viewer);
            if (one == null) return JimRestResponses.errorJson(404, "not_found", "Issue not found or not accessible");
            issues.add(one); total = 1;
        } else {
            Query query = JimMobileIssues.buildQuery(viewer, this.searchService, "assigned");
            PagerFilter<Issue> pager = new PagerFilter<Issue>(max); pager.setStart(start);
            SearchResults<Issue> results = this.searchService.search(viewer, query, pager);
            issues.addAll(results.getResults()); total = results.getTotal();
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        int req = 0, submitted = 0, valid = 0, oow = 0, dup = 0, missing = 0, maxStreak = 0, configured = 0;
        Integer tzOff = null;
        for (Issue issue : issues) {
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
        int off = tzOff == null ? reportTz().getOffset(System.currentTimeMillis()) / 60000 : tzOff.intValue();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("domain", "corbit_report");
        body.put("requestedScopeType", requested.isEmpty() ? "issue_lifetime" : requested);
        body.put("appliedScopeType", "issue_lifetime");
        body.put("scopeType", "issue_lifetime");
        body.put("scopeNote", "Per-issue LIFETIME compliance from JimReportService. Report numbers verbatim; do not re-classify or recompute from raw comments; do not present a lifetime total as a weekly/user-period total.");
        body.put("authenticatedUser", viewer.getKey());
        body.put("timezone", tzLabel(off));
        body.put("tzOffsetMinutes", off);
        body.put("reports", items);
        body.put("summary", summary);
        body.put("issueCount", configured);
        body.put("reportConfiguredIssues", configured);
        body.put("startAt", start);
        body.put("maxResults", max);
        body.put("total", total);
        body.put("generatedAt", System.currentTimeMillis());
        body.put("warnings", new ArrayList<String>());
        return JimRestResponses.okJson(body);
    }

    private Map<String, Object> aggregatePeriod(List<Map<String, Object>> perIssue) {
        int req = 0, submitted = 0, valid = 0, oow = 0, dup = 0, missed = 0, maxStreak = 0; String maxKey = null;
        boolean anyRequired = false;
        for (Map<String, Object> pm : perIssue) {
            if (!Boolean.TRUE.equals(pm.get("configured"))) continue;
            Object r = pm.get("requiredCount");
            if (r instanceof Number) { req += ((Number) r).intValue(); anyRequired = true; }
            submitted += i(pm.get("submittedCount")); valid += i(pm.get("validCount"));
            oow += i(pm.get("outOfWindowCount")); dup += i(pm.get("duplicateCount")); missed += i(pm.get("missedCount"));
            int ms = i(pm.get("maximumConsecutiveMissed"));
            if (ms > maxStreak) { maxStreak = ms; maxKey = String.valueOf(pm.get("issueKey")); }
        }
        Map<String, Object> a = new LinkedHashMap<String, Object>();
        a.put("requiredCount", anyRequired ? (Object) req : null);
        a.put("submittedCount", submitted);
        a.put("validCount", valid);
        a.put("outOfWindowCount", oow);
        a.put("duplicateCount", dup);
        a.put("invalidCount", oow + dup);
        a.put("missedCount", missed);
        a.put("maximumConsecutiveMissedPerIssue", maxStreak);
        a.put("maximumConsecutiveMissedIssueKey", maxKey);
        return a;
    }

    private void putScopeMeta(Map<String, Object> body, ApplicationUser viewer, String requested, String applied,
                              long from, long to, TimeZone tz, List<String> targetUsers, String projectKeys,
                              int issueCount, java.util.Set<String> modes) {
        int off = tz.getOffset(System.currentTimeMillis()) / 60000;
        body.put("domain", "corbit_report");
        body.put("requestedScopeType", requested.isEmpty() ? applied : requested);
        body.put("appliedScopeType", applied);
        body.put("scopeType", applied);
        body.put("authenticatedUser", viewer.getKey());
        body.put("targetUsers", targetUsers);
        body.put("targetUserCount", targetUsers.size());
        body.put("projectKeys", projectKeys == null || projectKeys.isEmpty() ? new ArrayList<String>() : java.util.Arrays.asList(projectKeys.split(",")));
        body.put("issueCount", issueCount);
        body.put("periodStartMs", from);
        body.put("periodEndMs", to);
        body.put("periodStart", ymdInTz(from, tz));
        body.put("periodEnd", ymdInTz(to, tz));
        body.put("timezone", tzLabel(off));
        body.put("tzOffsetMinutes", off);
        body.put("calculationModes", new ArrayList<String>(modes));
        body.put("generatedAt", System.currentTimeMillis());
    }

    private Response selfPeriod(ApplicationUser viewer, String requested, String period, String dateFrom, String dateTo, boolean includeItems) throws com.atlassian.jira.issue.search.SearchException {
        TimeZone tz = reportTz();
        long[] pb = periodBounds(period, dateFrom, dateTo, tz);
        Query query = JimMobileIssues.buildQuery(viewer, this.searchService, "assigned");
        SearchResults<Issue> results = this.searchService.search(viewer, query, new PagerFilter<Issue>(200));
        List<Map<String, Object>> perIssue = new ArrayList<Map<String, Object>>();
        java.util.Set<String> modes = new java.util.LinkedHashSet<String>();
        for (Issue issue : results.getResults()) {
            Map<String, Object> pm = periodMetrics(issue, pb[0], pb[1]);
            if (!Boolean.TRUE.equals(pm.get("configured"))) continue;
            if (pm.get("countMode") != null) modes.add(String.valueOf(pm.get("countMode")));
            perIssue.add(pm);
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        putScopeMeta(body, viewer, requested, "self_period", pb[0], pb[1], tz,
                new ArrayList<String>(java.util.Arrays.asList(viewer.getKey())), null, perIssue.size(), modes);
        body.put("scopeNote", "Reports the authenticated user submitted/were required within the period, computed from canonical plugin windows (interval/daily) or report items (fixed_minimum). Not a lifetime total. Consecutive-missing is per-issue only.");
        body.put("summary", aggregatePeriod(perIssue));
        if (includeItems) body.put("perIssue", perIssue);
        body.put("warnings", new ArrayList<String>());
        return JimRestResponses.okJson(body);
    }

    private Response managerTeamPeriod(ApplicationUser viewer, String requested, String period, String dateFrom,
                                       String dateTo, String userKey, String projectKeys, boolean includeItems) throws com.atlassian.jira.issue.search.SearchException {
        // Non-admin: bounded by the user's own Jira BROWSE permission (SearchService runs as the user),
        // consistent with current-coverage. A project lead / authorized manager sees their legitimate
        // scope without SYSTEM_ADMIN; a user never sees issues they cannot already browse.
        TimeZone tz = reportTz();
        long[] pb = periodBounds(period, dateFrom, dateTo, tz);
        // canonical set: issues with a report-level custom field, run as the admin (Jira security still applies)
        String jql;
        try {
            com.atlassian.jira.issue.fields.CustomField lvl = JimReportConfig.resolveLevelField();
            String cfNum = lvl == null ? null : lvl.getId().replaceAll("[^0-9]", "");
            StringBuilder sb = new StringBuilder();
            if (cfNum != null && !cfNum.isEmpty()) sb.append("cf[").append(cfNum).append("] IS NOT EMPTY");
            else sb.append("resolution IS NOT NULL OR resolution IS EMPTY"); // fallback: all issues
            if (userKey != null && !userKey.isEmpty()) {
                ApplicationUser tgt = ComponentAccessor.getUserManager().getUserByKey(userKey);
                if (tgt == null) tgt = ComponentAccessor.getUserManager().getUserByName(userKey);
                if (tgt != null) sb.append(" AND assignee = \"").append(tgt.getUsername()).append("\"");
            }
            if (projectKeys != null && !projectKeys.isEmpty()) sb.append(" AND project in (").append(projectKeys).append(")");
            sb.append(" ORDER BY assignee ASC");
            jql = sb.toString();
        } catch (Exception e) { return JimRestResponses.errorJson(500, "error", "Failed to build team query"); }
        SearchService.ParseResult pr = this.searchService.parseQuery(viewer, jql);
        if (!pr.isValid()) return JimRestResponses.errorJson(400, "bad_query", "Team query could not be parsed");
        SearchResults<Issue> results = this.searchService.search(viewer, pr.getQuery(), new PagerFilter<Issue>(500));
        Map<String, List<Map<String, Object>>> byUser = new java.util.LinkedHashMap<String, List<Map<String, Object>>>();
        Map<String, String> userNames = new java.util.HashMap<String, String>();
        List<Map<String, Object>> allIssues = new ArrayList<Map<String, Object>>();
        java.util.Set<String> modes = new java.util.LinkedHashSet<String>();
        for (Issue issue : results.getResults()) {
            Map<String, Object> pm = periodMetrics(issue, pb[0], pb[1]);
            if (!Boolean.TRUE.equals(pm.get("configured"))) continue;
            String uk = pm.get("responsibleUserKey") == null ? "(unassigned)" : String.valueOf(pm.get("responsibleUserKey"));
            userNames.put(uk, pm.get("responsibleUserName") == null ? uk : String.valueOf(pm.get("responsibleUserName")));
            if (pm.get("countMode") != null) modes.add(String.valueOf(pm.get("countMode")));
            if (!byUser.containsKey(uk)) byUser.put(uk, new ArrayList<Map<String, Object>>());
            byUser.get(uk).add(pm);
            allIssues.add(pm);
        }
        List<Map<String, Object>> perUser = new ArrayList<Map<String, Object>>();
        List<String> targetUsers = new ArrayList<String>();
        for (Map.Entry<String, List<Map<String, Object>>> e : byUser.entrySet()) {
            Map<String, Object> agg = aggregatePeriod(e.getValue());
            Map<String, Object> u = new LinkedHashMap<String, Object>();
            u.put("jiraUserKey", e.getKey());
            u.put("displayName", userNames.get(e.getKey()));
            u.putAll(agg);
            u.put("highestRiskIssue", agg.get("maximumConsecutiveMissedIssueKey"));
            if (includeItems) u.put("perIssue", e.getValue());
            perUser.add(u);
            targetUsers.add(e.getKey());
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        putScopeMeta(body, viewer, requested, "manager_team_period", pb[0], pb[1], tz, targetUsers, projectKeys, allIssues.size(), modes);
        Map<String, Object> hsrc = new LinkedHashMap<String, Object>();
        hsrc.put("type", (projectKeys != null && !projectKeys.isEmpty()) ? "project_browse" : "jira_browse");
        hsrc.put("value", (projectKeys != null && !projectKeys.isEmpty()) ? projectKeys : "browsable report-configured issues");
        body.put("scopeSource", hsrc);
        body.put("byUser", perUser);
        body.put("scopeNote", "Cross-user report compliance for the period, bounded by the user's own Jira BROWSE permission (no SYSTEM_ADMIN; no invented hierarchy). Counts exclude issues the user cannot browse.");
        body.put("summary", aggregatePeriod(allIssues));
        body.put("perUser", perUser);
        body.put("warnings", new ArrayList<String>());
        return JimRestResponses.okJson(body);
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
            // Submission is NOT gated by enforcement scope — an out-of-scope issue can
            // still receive an informational (unscored) report if the schedule is active
            // and the caller can comment. Enforcement scope only affects scoring.
            boolean configured = s != null && s.configured && s.levelCode != null;
            boolean inScope = s != null && s.inEnforcementScope;
            boolean canSubmit = configured && s.submitActive && canComment;
            out.put("configured", configured);
            out.put("applicable", inScope);
            out.put("inEnforcementScope", inScope);
            out.put("enforcementEnabled", s != null && s.enforcementEnabled);
            out.put("enforcementStartDate", s == null ? "" : s.enforcementStartDate);
            out.put("enforcementDateBasis", s == null ? null : s.enforcementDateBasis);
            out.put("fixedCounterStartMode", s != null && s.fixedCounterStartMode);
            out.put("outOfScopeReason", s == null ? null : s.outOfScopeReason);
            out.put("scoringPeriodStartMs", (s == null || s.scoringPeriodStartMs <= 0L
                    || s.scoringPeriodStartMs == Long.MAX_VALUE) ? null : Long.valueOf(s.scoringPeriodStartMs));
            out.put("reportRequiredForScoring", s != null && s.reportRequiredForScoring);
            out.put("submitActive", s != null && s.submitActive);
            out.put("canComment", canComment);
            out.put("canSubmit", canSubmit);
            out.put("canSubmitReport", canSubmit);
            out.put("informationalReportAllowed", canSubmit && !inScope);
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

    // ---------------------------------------------------------------- current-window reporting
    private int minuteOfDay(long ms, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz); c.setTimeInMillis(ms);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }
    private long startOfDay(long ms, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz); c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
    private String localWall(Long ms, int offMin) {
        if (ms == null) return null;
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        f.setTimeZone(TimeZone.getTimeZone("GMT"));
        return f.format(new Date(ms + offMin * 60000L));
    }

    /** Canonical CURRENT-window projection for one issue. Historical misses never set current state. */
    private Map<String, Object> currentWindowMap(Issue issue) {
        JimReportSummary s = this.reportService.computeSummary(issue);
        TimeZone tz = reportTz();
        long now = System.currentTimeMillis();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("issueKey", issue.getKey());
        m.put("projectKey", projectOf(issue.getKey()));
        m.put("summary", issue.getSummary());
        int off = s != null ? s.tzOffsetMinutes : tz.getOffset(now) / 60000;
        m.put("tzOffsetMinutes", off);
        m.put("reportingEnabled", s != null && s.configured);
        m.put("reportLevel", s == null ? null : s.levelCode);
        m.put("countMode", s == null ? null : s.countMode);
        m.put("responsibleUserKey", s == null ? null : s.responsibleUserKey);
        m.put("responsibleUserName", s == null ? null : s.responsibleUserName);
        String state; boolean actionRequired = false, hasValid = false, currentExists = false;
        Integer subCur = null, valCur = null, dupCur = null; Long winStart = null, winEnd = null;
        if (s == null || !s.configured || s.levelCode == null || !s.applicable) {
            state = "not_applicable";
        } else if (!s.fieldsConfigured || s.reportConfigError) {
            state = "configuration_incomplete";
        } else if ("CLOSED".equals(s.reportingControlState)) {
            state = "closed";
        } else if ("fixed_minimum".equals(s.countMode)) {
            long dayStart = startOfDay(now, tz);
            long dayEnd = dayStart + 86400000L;
            int sub = 0, val = 0, dup = 0;
            if (s.items != null) for (JimReportSummary.Item it : s.items) {
                if (it.createdMs < dayStart || it.createdMs >= dayEnd) continue;
                sub++; if (it.status == 0) val++; else if (it.status == 1) dup++;
            }
            subCur = sub; valCur = val; dupCur = dup;
            int mod = minuteOfDay(now, tz);
            boolean inHours = mod >= s.windowStartMinute && mod < s.windowEndMinute;
            currentExists = inHours;
            winStart = dayStart + s.windowStartMinute * 60000L;
            winEnd = dayStart + s.windowEndMinute * 60000L;
            if (val > 0) { state = "covered"; hasValid = true; }
            else if (sub > 0) { state = "invalid_only"; actionRequired = true; }
            else if (inHours && s.submitActive) { state = "missing"; actionRequired = true; }
            else { state = "not_due"; }
        } else {
            JimReportSummary.WindowInfo cur = null;
            if (s.windowLedger != null) for (JimReportSummary.WindowInfo w : s.windowLedger) {
                if (w.windowStart <= now && now < w.windowEnd) { cur = w; break; }
            }
            if (cur == null) {
                state = "not_due";
            } else {
                currentExists = true; winStart = cur.windowStart; winEnd = cur.windowEnd;
                subCur = cur.rawCount; valCur = cur.validCount; dupCur = cur.duplicateCount;
                if (cur.validCount > 0) { state = "covered"; hasValid = true; }
                else if (cur.rawCount > 0) { state = "invalid_only"; actionRequired = true; }
                else { state = "missing"; actionRequired = true; }
            }
        }
        m.put("currentWindowState", state);
        m.put("actionRequiredNow", actionRequired);
        m.put("reportRequiredNow", actionRequired);
        m.put("hasValidReportInCurrentWindow", hasValid);
        m.put("currentWindowExists", currentExists);
        m.put("currentWindowStartMs", winStart);
        m.put("currentWindowStartLocal", localWall(winStart, off));
        m.put("currentWindowEndMs", winEnd);
        m.put("currentWindowEndLocal", localWall(winEnd, off));
        m.put("currentWindowSubmittedCount", subCur);
        m.put("currentWindowValidCount", valCur);
        m.put("currentWindowDuplicateCount", dupCur);
        Long nextDue = s == null ? null : (s.nextReportDueMs != null ? s.nextReportDueMs : s.nextExpectedMs);
        m.put("nextDeadlineMs", nextDue);
        m.put("nextDeadlineLocal", localWall(nextDue, off));
        Map<String, Object> hist = new LinkedHashMap<String, Object>();
        hist.put("requiredCount", s == null ? 0 : s.requiredCount);
        hist.put("validCount", s == null ? 0 : s.validCount);
        hist.put("missedCount", s == null ? 0 : s.missedCount);
        hist.put("maximumConsecutiveMissed", s == null ? 0 : s.maxMissedStreak);
        m.put("historicalContext", hist);
        return m;
    }

    @GET
    @Path("/reports/current-window/{issueKey}")
    public Response reportsCurrentWindow(@PathParam("issueKey") String issueKey) {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        MutableIssue issue = loadBrowsable(issueKey, viewer);
        if (issue == null) return JimRestResponses.errorJson(404, "not_found", "Issue not found or not accessible");
        Map<String, Object> m = currentWindowMap(issue);
        m.put("scopeType", "issue_current_window");
        m.put("timezone", tzLabel(i(m.get("tzOffsetMinutes"))));
        m.put("domain", "corbit_report");
        return JimRestResponses.okJson(m);
    }

    @GET
    @Path("/reports/current-coverage")
    public Response reportsCurrentCoverage(@QueryParam("scope") String scope,
                                           @QueryParam("project_key") String projectKey,
                                           @QueryParam("user_key") String userKey,
                                           @QueryParam("include_covered") Boolean includeCovered,
                                           @QueryParam("maxResults") Integer maxResults) throws com.atlassian.jira.issue.search.SearchException {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.TASKS)) {
            return JimRestResponses.errorJson(403, "feature_disabled", "Reports feature not enabled for your account");
        }
        String sc = scope == null || scope.trim().isEmpty() ? "my_current_window" : scope.trim();
        int max = maxResults == null || maxResults < 1 || maxResults > 200 ? 100 : maxResults;
        boolean team = sc.startsWith("team") || sc.startsWith("project") || sc.startsWith("configured_supervisor") || sc.startsWith("schedule_approver");
        List<Issue> issues = new ArrayList<Issue>();
        Map<String, Object> scopeSource = new LinkedHashMap<String, Object>();
        if (team) {
            // Oversight scope is bounded by the user's own Jira BROWSE permission (SearchService runs
            // as the user), so a project lead / authorized manager sees their legitimate scope WITHOUT
            // SYSTEM_ADMIN, and a user never sees issues they cannot already browse. No invented hierarchy.
            if ((sc.startsWith("project") || sc.startsWith("schedule_approver")) && (projectKey == null || projectKey.isEmpty())
                    && (userKey == null || userKey.isEmpty())) {
                return JimRestResponses.errorJson(400, "project_key_required", "project_key (or user_key) is required for project/approver scope.");
            }
            com.atlassian.jira.issue.fields.CustomField lvl = JimReportConfig.resolveLevelField();
            String cfNum = lvl == null ? null : lvl.getId().replaceAll("[^0-9]", "");
            StringBuilder sb = new StringBuilder();
            sb.append(cfNum != null && !cfNum.isEmpty() ? "cf[" + cfNum + "] IS NOT EMPTY" : "created is not EMPTY");
            if (userKey != null && !userKey.isEmpty()) {
                ApplicationUser tgt = ComponentAccessor.getUserManager().getUserByKey(userKey);
                if (tgt == null) tgt = ComponentAccessor.getUserManager().getUserByName(userKey);
                if (tgt != null) sb.append(" AND assignee = \"").append(tgt.getUsername()).append("\"");
            }
            if (sc.startsWith("schedule_approver")) {
                String approverJql = JimScheduleApprovalConfig.approverFieldJql();
                if (approverJql != null) sb.append(" AND ").append(approverJql).append(" = currentUser()");
            }
            if (projectKey != null && !projectKey.isEmpty()) sb.append(" AND project = \"").append(projectKey).append("\"");
            SearchService.ParseResult pr = this.searchService.parseQuery(viewer, sb.toString());
            if (!pr.isValid()) return JimRestResponses.errorJson(400, "bad_query", "Coverage query could not be parsed");
            issues.addAll(this.searchService.search(viewer, pr.getQuery(), new PagerFilter<Issue>(max)).getResults());
            // scopeSource reflects the canonical basis for the visibility
            if (sc.startsWith("schedule_approver")) { scopeSource.put("type", "schedule_approver"); scopeSource.put("value", viewer.getKey()); }
            else if (projectKey != null && !projectKey.isEmpty()) {
                boolean lead = false;
                try {
                    com.atlassian.jira.project.Project p = ComponentAccessor.getProjectManager().getProjectObjByKey(projectKey);
                    lead = p != null && p.getProjectLead() != null && p.getProjectLead().getKey().equals(viewer.getKey());
                } catch (Exception ignore) { }
                scopeSource.put("type", lead ? "project_lead" : "project_browse");
                scopeSource.put("value", projectKey);
            } else { scopeSource.put("type", "jira_browse"); scopeSource.put("value", "browsable report-configured issues"); }
        } else {
            Query q = JimMobileIssues.buildQuery(viewer, this.searchService, "assigned");
            issues.addAll(this.searchService.search(viewer, q, new PagerFilter<Issue>(max)).getResults());
            scopeSource.put("type", "assignee"); scopeSource.put("value", viewer.getKey());
        }
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (String k : new String[]{"currentlyRequired", "covered", "missing", "invalidOnly", "notDue", "closed", "notApplicable", "configurationIncomplete"}) counts.put(k, 0);
        List<Map<String, Object>> missing = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> invalidOnly = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> covered = new ArrayList<Map<String, Object>>();
        boolean incCov = Boolean.TRUE.equals(includeCovered);
        for (Issue issue : issues) {
            Map<String, Object> cw = currentWindowMap(issue);
            String st = String.valueOf(cw.get("currentWindowState"));
            if ("covered".equals(st)) { counts.put("covered", counts.get("covered") + 1); counts.put("currentlyRequired", counts.get("currentlyRequired") + 1); if (incCov) covered.add(cw); }
            else if ("missing".equals(st)) { counts.put("missing", counts.get("missing") + 1); counts.put("currentlyRequired", counts.get("currentlyRequired") + 1); missing.add(cw); }
            else if ("invalid_only".equals(st)) { counts.put("invalidOnly", counts.get("invalidOnly") + 1); counts.put("currentlyRequired", counts.get("currentlyRequired") + 1); invalidOnly.add(cw); }
            else if ("not_due".equals(st)) counts.put("notDue", counts.get("notDue") + 1);
            else if ("closed".equals(st)) counts.put("closed", counts.get("closed") + 1);
            else if ("configuration_incomplete".equals(st)) counts.put("configurationIncomplete", counts.get("configurationIncomplete") + 1);
            else counts.put("notApplicable", counts.get("notApplicable") + 1);
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("scopeType", team ? "manager_team_current_window" : "my_current_window");
        body.put("scopeSource", scopeSource);
        body.put("summary", counts);
        body.put("missingCurrentReports", missing);
        body.put("invalidOnlyCurrentReports", invalidOnly);
        if (incCov) body.put("coveredCurrentReports", covered);
        body.put("timezone", tzLabel(reportTz().getOffset(System.currentTimeMillis()) / 60000));
        body.put("issueCount", issues.size());
        body.put("domain", "corbit_report");
        body.put("warnings", new ArrayList<String>());
        return JimRestResponses.okJson(body);
    }

    // ---------------------------------------------------------------- managed (Project Lead) projects
    /** Canonical Project Lead resolution: projects whose Jira Lead == the authenticated user, browsable.
     *  NEVER inferred from assignee/reporter/membership/browse/admin/schedule-approver/chat/feature. */
    private List<com.atlassian.jira.project.Project> resolveLeadProjects(ApplicationUser viewer) {
        List<com.atlassian.jira.project.Project> out = new ArrayList<com.atlassian.jira.project.Project>();
        com.atlassian.jira.security.PermissionManager pmg = ComponentAccessor.getPermissionManager();
        for (com.atlassian.jira.project.Project p : ComponentAccessor.getProjectManager().getProjectObjects()) {
            ApplicationUser lead = p.getProjectLead();
            if (lead == null || !lead.getKey().equals(viewer.getKey())) continue;   // canonical Jira lead identity only
            if (!pmg.hasPermission(ProjectPermissions.BROWSE_PROJECTS, p, viewer)) continue;
            out.add(p);
        }
        return out;
    }

    private Map<String, Object> leadProjectDto(com.atlassian.jira.project.Project p) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        ApplicationUser lead = p.getProjectLead();
        m.put("projectKey", p.getKey());
        m.put("projectId", String.valueOf(p.getId()));
        m.put("projectName", p.getName());
        m.put("leadUserKey", lead == null ? null : lead.getKey());
        m.put("leadDisplayName", lead == null ? null : lead.getDisplayName());
        m.put("browsable", true);
        m.put("archived", false);
        return m;
    }

    @GET
    @Path("/projects/managed")
    public Response projectsManaged() {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        List<com.atlassian.jira.project.Project> lead = resolveLeadProjects(viewer);
        List<Map<String, Object>> projects = new ArrayList<Map<String, Object>>();
        List<String> keys = new ArrayList<String>();
        for (com.atlassian.jira.project.Project p : lead) { projects.add(leadProjectDto(p)); keys.add(p.getKey()); }
        Map<String, Object> ju = new LinkedHashMap<String, Object>();
        ju.put("jiraUserKey", viewer.getKey());
        ju.put("username", viewer.getUsername());
        ju.put("displayName", viewer.getDisplayName());
        Map<String, Object> ss = new LinkedHashMap<String, Object>();
        ss.put("type", "jira_project_lead"); ss.put("jiraUserKey", viewer.getKey());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("domain", "projects");
        body.put("scopeType", "project_lead_projects");
        body.put("scopeSource", ss);
        body.put("scopeProjectCount", projects.size());
        body.put("scopeProjectKeys", keys);
        body.put("jiraUser", ju);
        body.put("projectCount", projects.size());
        body.put("projects", projects);
        body.put("complete", true);
        body.put("partial", false);
        body.put("truncated", false);
        body.put("fallbackUsed", false);
        body.put("warnings", new ArrayList<Object>());
        return JimRestResponses.okJson(body);
    }

    private Map<String, Object> histSummary(Map<String, Object> agg) {
        Map<String, Object> h = new LinkedHashMap<String, Object>();
        h.put("requiredCount", agg.get("requiredCount"));
        h.put("submittedCount", agg.get("submittedCount"));
        h.put("validCount", agg.get("validCount"));
        h.put("duplicateCount", agg.get("duplicateCount"));
        h.put("outOfWindowCount", agg.get("outOfWindowCount"));
        h.put("missedCount", agg.get("missedCount"));
        h.put("maximumConsecutiveMissed", agg.get("maximumConsecutiveMissedPerIssue"));
        return h;
    }

    private Map<String, Object> warnObj(String code, String message) {
        Map<String, Object> w = new LinkedHashMap<String, Object>();
        w.put("code", code); w.put("message", message);
        return w;
    }

    /** Timeout-safe, per-project bounded portfolio for the authenticated user's canonical Project Lead projects.
     *  Current-window and historical-period results are kept strictly separate. Never widens to browse. */
    @GET
    @Path("/reports/managed-overview")
    public Response managedOverview(@QueryParam("period") String period,
                                    @QueryParam("date_from") String dateFrom,
                                    @QueryParam("date_to") String dateTo,
                                    @QueryParam("includeCurrentWindow") Boolean includeCurrentWindow,
                                    @QueryParam("includeHistorical") Boolean includeHistorical,
                                    @QueryParam("includeUsers") Boolean includeUsers,
                                    @QueryParam("projectLimit") Integer projectLimit,
                                    @QueryParam("issuePageSize") Integer issuePageSize,
                                    @QueryParam("continuationCursor") Integer continuationCursor)
            throws com.atlassian.jira.issue.search.SearchException {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.TASKS))
            return JimRestResponses.errorJson(403, "feature_disabled", "Reports feature not enabled for your account");
        boolean incCur = includeCurrentWindow == null || includeCurrentWindow.booleanValue();
        boolean incHist = includeHistorical == null || includeHistorical.booleanValue();
        boolean incUsers = includeUsers != null && includeUsers.booleanValue();
        int projLimit = projectLimit == null || projectLimit < 1 || projectLimit > 100 ? 50 : projectLimit;
        int perProjectCap = issuePageSize == null || issuePageSize < 1 || issuePageSize > 1000 ? 500 : (issuePageSize < 50 ? 50 : issuePageSize);
        long budgetMs = 20000L;                 // overall interactive budget, safely under the 30s Gateway timeout
        long startNs = System.nanoTime();
        int cursor = continuationCursor == null || continuationCursor < 0 ? 0 : continuationCursor.intValue();
        TimeZone tz = reportTz();
        long[] pb = periodBounds(period, dateFrom, dateTo, tz);

        List<com.atlassian.jira.project.Project> lead = resolveLeadProjects(viewer);   // canonical, single resolution
        List<String> allLeadKeys = new ArrayList<String>();
        for (com.atlassian.jira.project.Project p : lead) allLeadKeys.add(p.getKey());
        com.atlassian.jira.issue.fields.CustomField lvl = JimReportConfig.resolveLevelField();
        String cfNum = lvl == null ? null : lvl.getId().replaceAll("[^0-9]", "");

        Map<String, Integer> curAgg = new LinkedHashMap<String, Integer>();
        for (String k : new String[]{"currentlyRequired","covered","missing","invalidOnly","notDue","closed","notApplicable","configurationIncomplete"}) curAgg.put(k, 0);
        List<Map<String, Object>> histAll = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> projectResults = new ArrayList<Map<String, Object>>();
        List<Object> warnings = new ArrayList<Object>();
        int issuesEvaluated = 0, projectsEvaluated = 0;
        boolean truncated = false;
        Integer nextCursor = null;
        int end = Math.min(lead.size(), cursor + projLimit);
        int i = cursor;
        for (; i < end; i++) {
            if (i > cursor && (System.nanoTime() - startNs) / 1000000L > budgetMs) break;   // budget reached -> continue later
            com.atlassian.jira.project.Project p = lead.get(i);
            long pStartNs = System.nanoTime();
            String jql = (cfNum != null && !cfNum.isEmpty() ? "cf[" + cfNum + "] IS NOT EMPTY" : "created is not EMPTY")
                    + " AND project = \"" + p.getKey() + "\" ORDER BY assignee ASC";
            Map<String, Object> prow = new LinkedHashMap<String, Object>();
            prow.put("projectKey", p.getKey());
            prow.put("projectName", p.getName());
            SearchService.ParseResult pr = this.searchService.parseQuery(viewer, jql);
            if (!pr.isValid()) { prow.put("complete", false); prow.put("partial", false); prow.put("error", "bad_query"); projectResults.add(prow); continue; }
            SearchResults<Issue> sr = this.searchService.search(viewer, pr.getQuery(), new PagerFilter<Issue>(perProjectCap + 1));
            List<Issue> issues = sr.getResults();
            boolean projTrunc = issues.size() > perProjectCap;
            if (projTrunc) issues = issues.subList(0, perProjectCap);
            Map<String, Integer> pcur = new LinkedHashMap<String, Integer>();
            for (String k : new String[]{"currentlyRequired","covered","missing","invalidOnly","notDue","closed","notApplicable","configurationIncomplete"}) pcur.put(k, 0);
            List<Map<String, Object>> pHist = new ArrayList<Map<String, Object>>();
            Map<String, String> users = new LinkedHashMap<String, String>();
            int evaluated = 0;
            for (Issue issue : issues) {
                if (incCur) {
                    String st = String.valueOf(currentWindowMap(issue).get("currentWindowState"));
                    if ("covered".equals(st)) { pcur.put("covered", pcur.get("covered")+1); pcur.put("currentlyRequired", pcur.get("currentlyRequired")+1); }
                    else if ("missing".equals(st)) { pcur.put("missing", pcur.get("missing")+1); pcur.put("currentlyRequired", pcur.get("currentlyRequired")+1); }
                    else if ("invalid_only".equals(st)) { pcur.put("invalidOnly", pcur.get("invalidOnly")+1); pcur.put("currentlyRequired", pcur.get("currentlyRequired")+1); }
                    else if ("not_due".equals(st)) pcur.put("notDue", pcur.get("notDue")+1);
                    else if ("closed".equals(st)) pcur.put("closed", pcur.get("closed")+1);
                    else if ("configuration_incomplete".equals(st)) pcur.put("configurationIncomplete", pcur.get("configurationIncomplete")+1);
                    else pcur.put("notApplicable", pcur.get("notApplicable")+1);
                }
                if (incHist) {
                    Map<String, Object> pm = periodMetrics(issue, pb[0], pb[1]);
                    if (Boolean.TRUE.equals(pm.get("configured"))) {
                        pHist.add(pm);
                        if (incUsers) { Object uk = pm.get("responsibleUserKey"); if (uk != null) users.put(String.valueOf(uk), String.valueOf(pm.get("responsibleUserName"))); }
                    }
                }
                evaluated++;
            }
            for (String k : pcur.keySet()) curAgg.put(k, curAgg.get(k) + pcur.get(k));
            histAll.addAll(pHist);
            issuesEvaluated += evaluated; projectsEvaluated++;
            prow.put("issueCountEvaluated", evaluated);
            prow.put("issueCountTotal", projTrunc ? (perProjectCap + "+") : String.valueOf(evaluated));
            prow.put("complete", !projTrunc);
            prow.put("partial", projTrunc);
            if (incCur) prow.put("current", pcur);
            if (incHist) prow.put("historical", histSummary(aggregatePeriod(pHist)));
            if (incUsers) {
                List<Map<String, Object>> us = new ArrayList<Map<String, Object>>();
                for (Map.Entry<String, String> e : users.entrySet()) { Map<String, Object> u = new LinkedHashMap<String, Object>(); u.put("jiraUserKey", e.getKey()); u.put("displayName", e.getValue()); us.add(u); }
                prow.put("users", us);
            }
            prow.put("durationMs", (System.nanoTime() - pStartNs) / 1000000L);
            if (projTrunc) { truncated = true; warnings.add(warnObj("project_issue_cap_reached", "Project " + p.getKey() + " exceeded " + perProjectCap + " report issues; showing first " + perProjectCap + ".")); }
            projectResults.add(prow);
        }
        boolean complete = (i >= lead.size());
        boolean partial = !complete;
        if (!complete) { nextCursor = Integer.valueOf(i); warnings.add(warnObj("portfolio_partial_timeout", projectsEvaluated + " of " + lead.size() + " managed projects were completed.")); }

        Map<String, Object> scope = new LinkedHashMap<String, Object>();
        scope.put("scopeType", "project_lead_portfolio");
        scope.put("scopeSource", "jira_project_lead");
        scope.put("projects", allLeadKeys);
        scope.put("projectCount", lead.size());
        Map<String, Object> psum = new LinkedHashMap<String, Object>();
        psum.put("projectsEvaluated", projectsEvaluated);
        psum.put("issuesEvaluated", issuesEvaluated);
        if (incCur) psum.put("current", curAgg);
        if (incHist) psum.put("historical", histSummary(aggregatePeriod(histAll)));

        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("domain", "corbit_report");
        body.put("scopeType", "project_lead_portfolio");
        body.put("scope", scope);
        body.put("scopeSource", "jira_project_lead");
        body.put("authenticatedUser", viewer.getKey());
        body.put("period", period == null || period.isEmpty() ? "this_month" : period);
        body.put("timezone", tzLabel(tz.getOffset(System.currentTimeMillis()) / 60000));
        body.put("periodStartMs", pb[0]);
        body.put("periodEndMs", pb[1]);
        body.put("scopeProjectKeys", allLeadKeys);
        body.put("scopeProjectCount", lead.size());
        body.put("portfolioSummary", psum);
        body.put("projects", projectResults);
        body.put("projectsEvaluated", projectsEvaluated);
        body.put("projectsTotal", lead.size());
        body.put("issuesEvaluated", issuesEvaluated);
        body.put("complete", complete);
        body.put("partial", partial);
        body.put("truncated", truncated);
        body.put("continuationCursor", nextCursor);
        body.put("fallbackUsed", false);
        body.put("warnings", warnings);
        return JimRestResponses.okJson(body);
    }

    // ---------------------------------------------------------------- chat: read conversation / members / search
    private Map<String, Object> conversationDto(JimConversation c) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("conversationId", c.getID());
        m.put("conversationType", c.getConversationType());
        m.put("groupName", c.getGroupName());
        m.put("projectKey", c.getProjectKey());
        m.put("userAKey", c.getUserAKey());
        m.put("userBKey", c.getUserBKey());
        m.put("createdByUserKey", c.getCreatedByUserKey());
        m.put("archived", c.getArchived() != null && c.getArchived().intValue() == 1);
        m.put("lastMessageAt", c.getLastMessageAt());
        m.put("lastMessagePreview", c.getLastMessagePreview());
        return m;
    }

    private Map<String, Object> memberDto(String userKey, String role, Long joinedAt) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        ApplicationUser u = userKey == null ? null : ComponentAccessor.getUserManager().getUserByKey(userKey);
        m.put("jiraUserKey", userKey);
        m.put("username", u == null ? null : u.getUsername());
        m.put("displayName", u == null ? userKey : u.getDisplayName());
        m.put("role", role);
        m.put("joinedAt", joinedAt);
        return m;
    }

    @GET
    @Path("/chat/conversations/{id}")
    public Response chatGetConversation(@PathParam("id") int id) {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        JimConversation c = this.conversationService.getConversationForUser(id, viewer.getKey());
        if (c == null) return JimRestResponses.errorJson(404, "not_found", "Conversation not found or you are not a member");
        return JimRestResponses.okJson(conversationDto(c));
    }

    @GET
    @Path("/chat/conversations/{id}/members")
    public Response chatGetMembers(@PathParam("id") int id) {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        JimConversation c = this.conversationService.getConversationForUser(id, viewer.getKey());
        if (c == null) return JimRestResponses.errorJson(404, "not_found", "Conversation not found or you are not a member");
        String type = c.getConversationType() == null ? "" : c.getConversationType();
        List<Map<String, Object>> members = new ArrayList<Map<String, Object>>();
        if ("GROUP".equalsIgnoreCase(type)) {
            try {
                JimGroupService gs = ComponentAccessor.getOSGiComponentInstanceOfType(JimGroupService.class);
                for (JimGroupMember gm : gs.listMembers(id, viewer.getKey())) {
                    members.add(memberDto(gm.getUserKey(), gm.getRole(), gm.getJoinedAt()));
                }
            } catch (Exception e) { return JimRestResponses.errorJson(403, "forbidden", "Cannot list members: " + e.getMessage()); }
        } else if ("DIRECT".equalsIgnoreCase(type)) {
            members.add(memberDto(c.getUserAKey(), "member", null));
            members.add(memberDto(c.getUserBKey(), "member", null));
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("conversationId", id);
        body.put("conversationType", type);
        body.put("memberCount", members.size());
        body.put("members", members);
        if (!"GROUP".equalsIgnoreCase(type) && !"DIRECT".equalsIgnoreCase(type)) {
            body.put("note", "Membership for " + type + " conversations is derived from project/system scope, not an explicit member list.");
        }
        return JimRestResponses.okJson(body);
    }

    @GET
    @Path("/chat/search")
    public Response chatSearchGlobal(@QueryParam("q") String q,
                                     @QueryParam("conversationId") Integer conversationId,
                                     @QueryParam("type") String type,
                                     @QueryParam("limit") Integer limit,
                                     @QueryParam("cursor") Integer cursor) {
        ApplicationUser viewer = viewer();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        }
        try {
            return JimRestResponses.okJson(this.messageService.searchMessages(
                    q, conversationId, type, limit == null ? 25 : limit.intValue(),
                    cursor == null ? 0 : cursor.intValue()));
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/mcp-domain/chat/search",
                    viewer.getKey(), ex, "internal_error", "An internal error occurred during search.");
        }
    }

    @GET
    @Path("/chat/conversations/{id}/messages/search")
    public Response chatSearchMessages(@PathParam("id") int id, @QueryParam("q") String q, @QueryParam("limit") Integer limit) {
        ApplicationUser viewer = viewer();
        if (viewer == null) return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        if (q == null || q.trim().isEmpty()) return JimRestResponses.errorJson(400, "bad_request", "q (search text) is required");
        JimConversation c = this.conversationService.getConversationForUser(id, viewer.getKey());
        if (c == null) return JimRestResponses.errorJson(404, "not_found", "Conversation not found or you are not a member");
        int scan = 500;   // bounded scan window (newest messages)
        List<JimMessage> msgs = this.messageService.listMessages(id, scan, null);
        String needle = q.trim().toLowerCase();
        int max = limit == null || limit < 1 || limit > 100 ? 50 : limit.intValue();
        List<JimMessage> hits = new ArrayList<JimMessage>();
        for (JimMessage m : msgs) {
            if (m.getDeleted() != null && m.getDeleted().intValue() == 1) continue;
            String b = m.getBody();
            if (b != null && b.toLowerCase().contains(needle)) {
                hits.add(m);
                if (hits.size() >= max) break;
            }
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("conversationId", id);
        body.put("query", q.trim());
        body.put("matchCount", hits.size());
        body.put("scannedWindow", Math.min(scan, msgs.size()));
        body.put("truncated", msgs.size() >= scan);
        body.put("messages", this.restJsonMapper.toMessageMaps(hits, viewer, c, viewer.getKey()));
        return JimRestResponses.okJson(body);
    }

    @GET
    @Path("/chat/conversations/{id}/shared")
    public Response chatListShared(@PathParam("id") int id,
                                   @QueryParam("type") @DefaultValue("files") String type,
                                   @QueryParam("cursor") Integer cursor,
                                   @QueryParam("limit") Integer limit) {
        ApplicationUser viewer = viewer();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "Not authenticated");
        }
        try {
            return JimRestResponses.okJson(this.sharedContentService.listShared(
                    id, viewer.getKey(), type, cursor, limit));
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            log.error("MCP shared content failed conversationId={}", id, ex);
            return JimRestResponses.errorJson(500, "internal_error", "Unable to list shared content");
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
        boolean baseSubmit = configured && canEdit && JimMobileDates.findProposedCompletionField() != null;
        boolean canSubmit = baseSubmit && approverConfigured;
        List<String> decideBlocking = new ArrayList<String>();
        if (!configured) decideBlocking.add("not_configured");
        if (!isApprover) decideBlocking.add("not_approver");
        if (proposed == null) decideBlocking.add("no_proposed_value");
        if (!pending) decideBlocking.add("already_decided");
        boolean canDecide = configured && isApprover && proposed != null && pending;
        out.put("approverConfigured", approverConfigured);
        out.put("isApprover", isApprover);
        out.put("canSubmit", canSubmit);
        out.put("canSubmitWithApprover", baseSubmit);
        out.put("canDecide", canDecide);
        out.put("requiredFormat", "yyyy-MM-dd");
        out.put("blockingReasons", blocking);
        out.put("decisionBlockingReasons", decideBlocking);
        List<String> actions = new ArrayList<String>();
        if (baseSubmit) actions.add("submit");
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
                v.put("canSubmit", valid);
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
