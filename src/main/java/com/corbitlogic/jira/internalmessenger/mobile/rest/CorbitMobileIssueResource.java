package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.query.Query;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileIssueDetail;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileIssues;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimMobileFeatureService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mobile BFF issue search (Sprint 03). Mounted under
 * {@code /rest/corbit-mobile/1.0/issues}.
 *
 * <p>The client selects a whitelisted <em>filter token</em> only
 * ({@code assigned|today|overdue|followup}); the server maps it to a bounded
 * JQL query ({@link JimMobileIssues}) and runs it through
 * {@code SearchService.search(user, ...)}, which enforces the caller's Jira
 * Browse-Project / issue security. Raw JQL is never accepted and results are
 * always paginated. No issue fields beyond the projection are logged.</p>
 */
@Path("/issues")
@Produces({"application/json"})
@AnonymousAllowed
public class CorbitMobileIssueResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobileIssueResource.class);

    private final JiraAuthenticationContext authenticationContext;
    private final SearchService searchService;
    private final AvatarService avatarService;
    private final ApplicationProperties applicationProperties;
    private final JimMobileFeatureService featureService;

    @Inject
    public CorbitMobileIssueResource(JiraAuthenticationContext authenticationContext,
                                     SearchService searchService,
                                     AvatarService avatarService,
                                     ApplicationProperties applicationProperties,
                                     JimMobileFeatureService featureService) {
        this.authenticationContext = authenticationContext;
        this.searchService = searchService;
        this.avatarService = avatarService;
        this.applicationProperties = applicationProperties;
        this.featureService = featureService;
    }

    @GET
    @Path("/search")
    public Response search(@QueryParam("filter") String filter,
                           @QueryParam("startAt") @DefaultValue("0") int startAt,
                           @QueryParam("maxResults") @DefaultValue("20") int maxResults) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.TASKS)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.TASKS);
        }
        if (!JimMobileIssues.isValidFilter(filter)) {
            return JimRestResponses.errorJson(400, "bad_request",
                    "filter must be one of " + JimMobileIssues.FILTERS);
        }
        int start = JimMobileIssues.normalizeStartAt(startAt);
        int max = JimMobileIssues.normalizePageSize(maxResults);
        try {
            Query query = JimMobileIssues.buildQuery(viewer, this.searchService, filter);
            PagerFilter<Issue> pager = new PagerFilter<>(max);
            pager.setStart(start);
            SearchResults<Issue> results = this.searchService.search(viewer, query, pager);

            List<Map<String, Object>> issues = new ArrayList<>();
            for (Issue issue : results.getResults()) {
                issues.add(JimMobileIssues.toIssueMap(issue, viewer, this.avatarService, this.applicationProperties));
            }
            int total = results.getTotal();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("filter", filter);
            body.put("startAt", start);
            body.put("maxResults", max);
            body.put("total", total);
            body.put("isLast", start + issues.size() >= total);
            body.put("issues", issues);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/issues/search?filter=" + filter,
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while loading issues.");
        }
    }

    /**
     * Full, read-only issue detail —
     * {@code GET /rest/corbit-mobile/1.0/issues/{issueKey}}.
     *
     * <p>The issue is loaded by key and the viewer's Browse-Project permission
     * (and issue-level security) is enforced before any field is projected. A
     * missing issue and a permission failure return the SAME 404 so the endpoint
     * never reveals whether an issue key exists.</p>
     */
    @GET
    @Path("/{issueKey}")
    public Response getIssue(@PathParam("issueKey") String issueKey) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.ISSUE_DETAIL)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.ISSUE_DETAIL);
        }
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "issueKey is required.");
        }
        String key = issueKey.trim();
        try {
            MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(key);
            if (issue == null || !ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue) issue, viewer)) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            Map<String, Object> body = JimMobileIssueDetail.toDetailMap(
                    issue, viewer, this.avatarService, this.applicationProperties);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/issues/{issueKey}",
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while loading the issue.");
        }
    }

    private static Response unauthenticated() {
        return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                "You must be signed in to use CorbitChat Mobile.");
    }
}
