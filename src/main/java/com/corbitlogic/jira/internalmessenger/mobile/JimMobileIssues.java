package com.corbitlogic.jira.internalmessenger.mobile;

import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.util.JimIssueUrlBuilder;
import com.corbitlogic.jira.internalmessenger.util.JimSanitizer;
import com.atlassian.query.Query;
import com.atlassian.query.operator.Operator;
import com.atlassian.query.order.SortOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-owned, whitelisted issue queries + JSON projection for the mobile BFF
 * (Sprint 03). The mobile client can only pick a filter <em>token</em> — never
 * raw JQL — and each token maps to a JQL {@link Query} built here with
 * {@link JqlQueryBuilder}. All searches run through
 * {@code SearchService.search(user, ...)} at the call site, which enforces Jira
 * Browse-Project / issue-level security for that user, so no per-issue
 * permission check is required on the results.
 */
public final class JimMobileIssues {

    /** The only filter tokens the client may request. */
    public static final List<String> FILTERS =
            Arrays.asList("assigned", "today", "overdue", "followup");

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;

    private JimMobileIssues() {
    }

    public static boolean isValidFilter(String filter) {
        return filter != null && FILTERS.contains(filter);
    }

    public static int normalizePageSize(int requested) {
        if (requested <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    public static int normalizeStartAt(int requested) {
        return Math.max(requested, 0);
    }

    /**
     * Build the JQL {@link Query} for a whitelisted token. Uses current-user JQL
     * functions so the query is scoped to the caller. Throws
     * {@link IllegalArgumentException} for an unknown token.
     */
    public static Query buildQuery(String filter) {
        JqlQueryBuilder builder = JqlQueryBuilder.newBuilder();
        switch (filter) {
            case "assigned":
                builder.where().assigneeIsCurrentUser().and().unresolved().endWhere();
                builder.orderBy().updatedDate(SortOrder.DESC).endOrderBy();
                break;
            case "today":
                builder.where().assigneeIsCurrentUser().and().unresolved()
                        .and().dueBetween(JimMobileDates.startOfToday(),
                                JimMobileDates.endOfToday()).endWhere();
                builder.orderBy().dueDate(SortOrder.ASC).endOrderBy();
                break;
            case "overdue":
                builder.where().assigneeIsCurrentUser().and().unresolved()
                        .and().addDateCondition("due", Operator.LESS_THAN,
                                JimMobileDates.startOfToday())
                        .endWhere();
                builder.orderBy().dueDate(SortOrder.ASC).endOrderBy();
                break;
            case "followup":
                // Issues the user reported and that are still open — items they
                // are likely to be chasing / following up on.
                builder.where().reporterIsCurrentUser().and().unresolved().endWhere();
                builder.orderBy().updatedDate(SortOrder.DESC).endOrderBy();
                break;
            default:
                throw new IllegalArgumentException("Unsupported filter: " + filter);
        }
        return builder.buildQuery();
    }

    /**
     * Build the query for a whitelisted token, applying the effective-due-date
     * fallback for {@code today}/{@code overdue} when the "تاریخ انجام پیشنهادی"
     * custom field exists on this instance.
     *
     * <p>For the fallback we use a small, fully server-owned JQL string parsed
     * via {@link SearchService#parseQuery} rather than the builder: Jira's
     * {@code startOfDay()}/{@code endOfDay()} functions compare unreliably
     * against custom date-picker fields, whereas a literal {@code yyyy-MM-dd}
     * comparison is exact. The JQL is composed only from a validated custom-field
     * clause (e.g. {@code cf[11309]}) and a server-formatted date — never from
     * client input — so no raw JQL crosses the mobile boundary. Falls back to the
     * standard duedate-only builder when the field is absent or parsing fails.</p>
     */
    public static Query buildQuery(ApplicationUser user, SearchService searchService, String filter) {
        if (user != null && searchService != null
                && ("today".equals(filter) || "overdue".equals(filter))) {
            // "today" in the viewer's timezone — the single source of truth for
            // both the standard Due Date and the proposed-completion fallback, so
            // Today/Overdue classification is identical for both.
            String today = JimMobileDates.todayYmd(user);
            String clause = JimMobileDates.proposedCompletionClause(); // null when absent
            String jql = effectiveDueJql(filter, clause, today);
            try {
                SearchService.ParseResult parsed = searchService.parseQuery(user, jql);
                if (parsed != null && parsed.isValid()) {
                    return parsed.getQuery();
                }
            } catch (Exception ignored) {
                // Fall through to the standard duedate-only builder.
            }
        }
        return buildQuery(filter);
    }

    /**
     * Build the Today/Overdue JQL against the viewer's {@code today} (yyyy-MM-dd).
     * Semantics, identical for Due Date and the proposed-completion fallback:
     * <ul>
     *   <li><b>today</b>: effective date {@code = today}</li>
     *   <li><b>overdue</b>: effective date {@code < today} (strictly before)</li>
     * </ul>
     * Date-only literal comparison, so nothing shifts with timezone. The proposed
     * branch is added only when the field exists on this instance.
     */
    private static String effectiveDueJql(String filter, String clause, String today) {
        String op = "overdue".equals(filter) ? "<" : "=";
        StringBuilder sb = new StringBuilder(
                "assignee = currentUser() AND resolution is EMPTY AND (");
        sb.append("duedate ").append(op).append(" \"").append(today).append('"');
        if (clause != null) {
            sb.append(" OR (duedate is EMPTY AND ")
              .append(clause).append(' ').append(op)
              .append(" \"").append(today).append("\")");
        }
        sb.append(") ORDER BY duedate ASC");
        return sb.toString();
    }

    /**
     * Project a single {@link Issue} into a UI-ready, bounded JSON map. Never
     * includes description or other free-text bodies beyond the summary.
     */
    public static Map<String, Object> toIssueMap(Issue issue,
                                                 ApplicationUser viewer,
                                                 AvatarService avatarService,
                                                 ApplicationProperties applicationProperties) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", issue.getKey());
        map.put("summary", JimSanitizer.sanitizeText(issue.getSummary()));

        if (issue.getStatus() != null) {
            map.put("status", issue.getStatus().getName());
            if (issue.getStatus().getStatusCategory() != null) {
                map.put("statusCategory", issue.getStatus().getStatusCategory().getKey());
            } else {
                map.put("statusCategory", null);
            }
        } else {
            map.put("status", null);
            map.put("statusCategory", null);
        }

        map.put("priority", issue.getPriority() != null ? issue.getPriority().getName() : null);
        map.put("issueType", issue.getIssueType() != null ? issue.getIssueType().getName() : null);

        if (issue.getProjectObject() != null) {
            Map<String, Object> project = new LinkedHashMap<>();
            project.put("key", issue.getProjectObject().getKey());
            project.put("name", JimSanitizer.sanitizeText(issue.getProjectObject().getName()));
            map.put("project", project);
        } else {
            map.put("project", null);
        }

        ApplicationUser assignee = issue.getAssignee();
        if (assignee != null) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("displayName", assignee.getDisplayName());
            a.put("avatarUrl", JimMobileAvatars.userPath(assignee));
            map.put("assignee", a);
        } else {
            map.put("assignee", null);
        }

        // Calendar-day dates as yyyy-MM-dd (server zone) — never epoch millis, so
        // the client renders the exact day with no timezone drift. Effective due
        // date falls back to "تاریخ انجام پیشنهادی" when the standard Due Date is
        // empty (see JimMobileDates).
        String dueYmd = JimMobileDates.dueYmd(issue);
        String effectiveDue = JimMobileDates.effectiveDueYmd(issue);
        map.put("dueDate", dueYmd);
        map.put("effectiveDueDate", effectiveDue);
        map.put("effectiveDueDateSource", JimMobileDates.effectiveDueSource(issue));
        map.put("overdue", issue.getResolution() == null
                && JimMobileDates.isOverdueYmd(effectiveDue, viewer));
        map.put("updated", issue.getUpdated() != null ? issue.getUpdated().getTime() : null);
        // Task-type colored pill (customfield_10903) — null when absent/hidden.
        map.put("taskType", JimMobileTaskType.pillForIssue(issue));
        map.put("url", JimIssueUrlBuilder.buildBrowseUrl(applicationProperties, issue));
        return map;
    }
}
