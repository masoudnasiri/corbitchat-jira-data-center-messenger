package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.query.Query;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileIssues;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.atlassian.jira.user.util.UserManager;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileDates;
import com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalConfig;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.service.JimMobileFeatureService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import com.corbitlogic.jira.internalmessenger.util.JimSanitizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mobile BFF dashboard (Sprint 03). Mounted under
 * {@code /rest/corbit-mobile/1.0/dashboard}.
 *
 * <p>One aggregated, permission-aware, bounded payload for the home screen:
 * unread chat totals (reusing the chat read-state) plus counts for the
 * whitelisted task filters and a small "today" preview. Counts come from
 * {@code SearchService.searchCount(user, ...)} and the preview from a bounded
 * {@code SearchService.search(...)}, so Jira issue security is enforced per
 * user. Every sub-query is best-effort: a failure degrades that section to a
 * safe default rather than failing the whole dashboard.</p>
 */
@Path("/dashboard")
@Produces({"application/json"})
@AnonymousAllowed
public class CorbitMobileDashboardResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobileDashboardResource.class);
    private static final int PREVIEW_SIZE = 5;

    private final JiraAuthenticationContext authenticationContext;
    private final SearchService searchService;
    private final AvatarService avatarService;
    private final ApplicationProperties applicationProperties;
    private final JimConversationService conversationService;
    private final JimReadStateService readStateService;
    private final JimMobileFeatureService featureService;
    private final JimMessageService messageService;
    private final UserManager userManager;

    @Inject
    public CorbitMobileDashboardResource(JiraAuthenticationContext authenticationContext,
                                         SearchService searchService,
                                         AvatarService avatarService,
                                         ApplicationProperties applicationProperties,
                                         JimConversationService conversationService,
                                         JimReadStateService readStateService,
                                         JimMobileFeatureService featureService,
                                         JimMessageService messageService,
                                         UserManager userManager) {
        this.authenticationContext = authenticationContext;
        this.searchService = searchService;
        this.avatarService = avatarService;
        this.applicationProperties = applicationProperties;
        this.conversationService = conversationService;
        this.readStateService = readStateService;
        this.featureService = featureService;
        this.messageService = messageService;
        this.userManager = userManager;
    }

    @GET
    public Response dashboard() {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                    "You must be signed in to use CorbitChat Mobile.");
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.DASHBOARD)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.DASHBOARD);
        }
        try {
            Map<String, Object> tasks = new LinkedHashMap<>();
            for (String filter : JimMobileIssues.FILTERS) {
                tasks.put(filter, count(viewer, filter));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("serverTime", System.currentTimeMillis());
            body.put("unread", computeUnread(viewer.getKey()));
            body.put("tasks", tasks);
            // Status-category counts for the Dashboard donut (R4). Additive and
            // backward-compatible: older clients ignore it. Bounded — three
            // permission-aware searchCount() queries, no per-issue work.
            body.put("statusCounts", statusCounts(viewer));
            // Open-work counts that EXCLUDE statusCategory=Done (dashboard-final):
            // the headline + overdue/today/followup chips are all consistent and
            // never include Done-status issues (the old resolution-only filters
            // could). Bounded — four permission-aware searchCount() queries.
            body.put("openWorkCounts", openWorkCounts(viewer));
            body.put("todayPreview", preview(viewer, "today"));
            // Schedule approvals pending for the current user (proposed
            // completion date awaiting their approval). Only present when the
            // workflow fields are configured on this instance.
            body.put("scheduleApprovals", scheduleApprovals(viewer));
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/dashboard", viewer.getKey(), ex,
                    "internal_error", "An internal error occurred while loading the dashboard.");
        }
    }

    /**
     * Pending schedule approvals assigned to the current user: {@code count} and
     * a bounded {@code preview}. {@code configured} is false (count 0) on any
     * instance missing the workflow fields, so the client hides the card there.
     */
    private Map<String, Object> scheduleApprovals(ApplicationUser viewer) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean configured = false;
        int count = 0;
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            if (com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalConfig.isConfigured()) {
                configured = true;
                // Resolve the JQL clauses from the environment-specific field ids
                // (configured -> production fallback) instead of hardcoding the
                // production ids, so awaiting-approval works on DEV/Server-2 too.
                String approverClause = com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalConfig.approverFieldJql();
                String statusClause = com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalConfig.statusFieldJql();
                String proposedClause = com.corbitlogic.jira.internalmessenger.mobile.JimMobileDates.proposedCompletionClause();
                if (approverClause == null || statusClause == null || proposedClause == null) {
                    out.put("configured", true);
                    out.put("count", 0);
                    out.put("preview", items);
                    return out;
                }
                String jql = approverClause + " = currentUser() "
                        + "AND (" + statusClause + " = \"Pending\" OR " + statusClause + " is EMPTY) "
                        + "AND " + proposedClause + " is not EMPTY ORDER BY updated DESC";
                SearchService.ParseResult pr = this.searchService.parseQuery(viewer, jql);
                if (pr.isValid()) {
                    Query q = pr.getQuery();
                    count = (int) this.searchService.searchCount(viewer, q);
                    SearchResults<Issue> results =
                            this.searchService.search(viewer, q, new PagerFilter<>(PREVIEW_SIZE));
                    for (Issue issue : results.getResults()) {
                        Map<String, Object> item =
                                JimMobileIssues.toIssueMap(issue, viewer, this.avatarService, this.applicationProperties);
                        enrichScheduleItem(item, issue);
                        items.add(item);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("endpoint=GET /rest/corbit-mobile/1.0/dashboard stage=scheduleApprovals outcome=degraded message={}",
                    ex.getMessage());
        }
        out.put("configured", configured);
        out.put("count", count);
        out.put("preview", items);
        return out;
    }

    /**
     * Add the same schedule DTO the Assistant card uses to a Dashboard pending
     * item, so the Dashboard is as complete as Assistant: proposed date,
     * status, requester and a description preview.
     */
    private void enrichScheduleItem(Map<String, Object> item, Issue issue) {
        try {
            Map<String, Object> sa = new LinkedHashMap<>();
            java.util.Date pd = JimScheduleApprovalConfig.getProposedDate(issue);
            if (pd != null) {
                sa.put("proposedDateMillis", pd.getTime());
                sa.put("proposedDateYmd", JimMobileDates.toYmd(pd));
            }
            String status = JimScheduleApprovalConfig.getStatusValue(issue);
            sa.put("status", (status == null || status.trim().isEmpty()) ? "Pending" : status);
            sa.put("isApprover", true); // the JQL already restricts to the current-user approver
            String requesterKey = this.messageService.findScheduleRequesterKey(issue.getKey());
            if (requesterKey != null) {
                com.atlassian.jira.user.ApplicationUser req = this.userManager.getUserByKey(requesterKey);
                if (req != null && req.getDisplayName() != null) {
                    sa.put("requesterName", JimSanitizer.sanitizeText(req.getDisplayName()));
                }
            }
            item.put("scheduleApproval", sa);
            String desc = issue.getDescription();
            if (desc != null && !desc.trim().isEmpty()) {
                String t = desc.trim();
                if (t.length() > 500) {
                    t = t.substring(0, 497) + "...";
                }
                item.put("descriptionPreview", JimSanitizer.sanitizeText(t));
            }
        } catch (Exception ex) {
            log.warn("dashboard scheduleApprovals enrich failed for {}: {}", issue.getKey(), ex.getMessage());
        }
    }

    /**
     * Status-category counts for the current user's assigned issues, used by the
     * Dashboard donut ({@code done} / {@code inProgress} / {@code todo}), plus
     * {@code total}. Bounded and permission-aware ({@code searchCount} enforces
     * issue security); each sub-query degrades to 0 on failure. Server-owned JQL
     * — no client JQL crosses the boundary.
     */
    private Map<String, Object> statusCounts(ApplicationUser viewer) {
        Map<String, Object> out = new LinkedHashMap<>();
        int done = statusCategoryCount(viewer, "Done");
        int inProgress = statusCategoryCount(viewer, "In Progress");
        int todo = statusCategoryCount(viewer, "To Do");
        out.put("done", done);
        out.put("inProgress", inProgress);
        out.put("todo", todo);
        out.put("total", done + inProgress + todo);
        return out;
    }

    private int statusCategoryCount(ApplicationUser viewer, String category) {
        try {
            String jql = "assignee = currentUser() AND statusCategory = \"" + category + "\"";
            SearchService.ParseResult pr = this.searchService.parseQuery(viewer, jql);
            if (!pr.isValid()) {
                return 0;
            }
            return (int) this.searchService.searchCount(viewer, pr.getQuery());
        } catch (Exception ex) {
            log.warn("endpoint=GET /rest/corbit-mobile/1.0/dashboard stage=statusCounts category={} outcome=degraded message={}",
                    category, ex.getMessage());
            return 0;
        }
    }

    /**
     * Open-work counts that exclude {@code statusCategory = Done}, so the
     * headline "active" and the overdue/today/followup chips are all consistent
     * (the legacy {@code resolution IS EMPTY} filters could still include a
     * Done-status issue with no resolution). Server-owned JQL, permission-aware
     * {@code searchCount} (issue security enforced), bounded, no client JQL.
     */
    private Map<String, Object> openWorkCounts(ApplicationUser viewer) {
        Map<String, Object> out = new LinkedHashMap<>();
        String today = JimMobileDates.todayYmd(viewer);
        String clause = JimMobileDates.proposedCompletionClause();
        out.put("active", jqlCount(viewer,
                "assignee = currentUser() AND statusCategory != Done"));
        out.put("overdue", jqlCount(viewer, openDueJql("<", clause, today)));
        out.put("today", jqlCount(viewer, openDueJql("=", clause, today)));
        // Follow-up mirrors the existing filter (issues the user reported and
        // still open) but excludes Done as well.
        out.put("followup", jqlCount(viewer,
                "reporter = currentUser() AND statusCategory != Done"));
        return out;
    }

    /** Assignee + not-Done + effective-due (Due Date, with proposed-date fallback). */
    private String openDueJql(String op, String clause, String today) {
        StringBuilder sb = new StringBuilder(
                "assignee = currentUser() AND statusCategory != Done AND (");
        sb.append("duedate ").append(op).append(" \"").append(today).append('"');
        if (clause != null) {
            sb.append(" OR (duedate is EMPTY AND ")
              .append(clause).append(' ').append(op)
              .append(" \"").append(today).append("\")");
        }
        sb.append(')');
        return sb.toString();
    }

    private int jqlCount(ApplicationUser viewer, String jql) {
        try {
            SearchService.ParseResult pr = this.searchService.parseQuery(viewer, jql);
            if (!pr.isValid()) {
                return 0;
            }
            return (int) this.searchService.searchCount(viewer, pr.getQuery());
        } catch (Exception ex) {
            log.warn("endpoint=GET /rest/corbit-mobile/1.0/dashboard stage=openWork outcome=degraded message={}",
                    ex.getMessage());
            return 0;
        }
    }

    /**
     * Period-filtered status donut counts (dashboard-final). Bounded, additive,
     * permission-aware. Period windows are on the issue's last-updated date:
     * <ul>
     *   <li>{@code day} → updated {@code >= startOfDay()}</li>
     *   <li>{@code week} → updated {@code >= startOfWeek()} (current week)</li>
     *   <li>{@code month} → updated {@code >= startOfMonth()} (current month)</li>
     *   <li>{@code all} → no date window</li>
     * </ul>
     * Segments match the donut legend: done / inProgress / overdue.
     */
    @GET
    @Path("/report-status")
    public Response reportStatus(@QueryParam("period") @DefaultValue("all") String period) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                    "You must be signed in to use CorbitChat Mobile.");
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.DASHBOARD)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.DASHBOARD);
        }
        String p = period == null ? "all" : period.trim().toLowerCase();
        String window;
        switch (p) {
            case "day":
                window = " AND updated >= startOfDay()";
                break;
            case "week":
                window = " AND updated >= startOfWeek()";
                break;
            case "month":
                window = " AND updated >= startOfMonth()";
                break;
            default:
                p = "all";
                window = "";
        }
        int done = jqlCount(viewer,
                "assignee = currentUser() AND statusCategory = Done" + window);
        int inProgress = jqlCount(viewer,
                "assignee = currentUser() AND statusCategory = \"In Progress\"" + window);
        int overdue = jqlCount(viewer,
                "assignee = currentUser() AND statusCategory != Done AND duedate < startOfDay()" + window);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("period", p);
        body.put("done", done);
        body.put("inProgress", inProgress);
        body.put("overdue", overdue);
        body.put("total", done + inProgress + overdue);
        return JimRestResponses.okJson(body);
    }

    private int count(ApplicationUser viewer, String filter) {
        try {
            Query query = JimMobileIssues.buildQuery(viewer, this.searchService, filter);
            return (int) this.searchService.searchCount(viewer, query);
        } catch (Exception ex) {
            log.warn("endpoint=GET /rest/corbit-mobile/1.0/dashboard stage=count filter={} outcome=degraded message={}",
                    filter, ex.getMessage());
            return 0;
        }
    }

    private List<Map<String, Object>> preview(ApplicationUser viewer, String filter) {
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            Query query = JimMobileIssues.buildQuery(viewer, this.searchService, filter);
            PagerFilter<Issue> pager = new PagerFilter<>(PREVIEW_SIZE);
            SearchResults<Issue> results = this.searchService.search(viewer, query, pager);
            for (Issue issue : results.getResults()) {
                items.add(JimMobileIssues.toIssueMap(issue, viewer, this.avatarService, this.applicationProperties));
            }
        } catch (Exception ex) {
            log.warn("endpoint=GET /rest/corbit-mobile/1.0/dashboard stage=preview filter={} outcome=degraded message={}",
                    filter, ex.getMessage());
        }
        return items;
    }

    /**
     * Aggregate unread across the user's conversations, grouped by type — same
     * shape as bootstrap's {@code unread}. Best-effort: degrades to zero.
     */
    private Map<String, Object> computeUnread(String userKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> byType = new LinkedHashMap<>();
        int total = 0;
        try {
            List<JimConversation> conversations =
                    this.conversationService.listConversationsForUser(userKey);
            for (JimConversation conversation : conversations) {
                int unread = this.readStateService.getUnreadCount(conversation.getID(), userKey);
                if (unread <= 0) {
                    continue;
                }
                total += unread;
                String type = conversation.getConversationType();
                String bucket = (type == null || type.isEmpty()) ? "OTHER" : type;
                byType.merge(bucket, unread, Integer::sum);
            }
        } catch (Exception ex) {
            log.warn("endpoint=GET /rest/corbit-mobile/1.0/dashboard stage=unread outcome=degraded message={}",
                    ex.getMessage());
        }
        result.put("total", total);
        result.put("byType", byType);
        return result;
    }
}
