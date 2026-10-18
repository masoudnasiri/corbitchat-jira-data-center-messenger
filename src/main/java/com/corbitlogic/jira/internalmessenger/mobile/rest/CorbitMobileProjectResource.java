package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.query.Query;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileIssues;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileProjects;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimMobileFeatureService;
import java.util.ArrayList;
import java.util.Collection;
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
 * Mobile BFF projects (Sprint 04). Mounted under
 * {@code /rest/corbit-mobile/1.0/projects}.
 *
 * <ul>
 *   <li>{@code GET /projects} — only projects the caller can Browse (via
 *       {@code PermissionManager.getProjects(BROWSE_PROJECTS, user)}).</li>
 *   <li>{@code GET /projects/{key}} — overview + basic stats. The project is
 *       permission-checked; stat counts run through {@code SearchService} which
 *       enforces the caller's issue-level security. No raw JQL is accepted.</li>
 * </ul>
 */
@Path("/projects")
@Produces({"application/json"})
@AnonymousAllowed
public class CorbitMobileProjectResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobileProjectResource.class);
    private static final int MAX_PROJECTS = 200;

    private static final int MAX_ISSUES_PAGE = 50;
    private static final int MAX_ACTIVITY = 30;

    private final JiraAuthenticationContext authenticationContext;
    private final SearchService searchService;
    private final AvatarService avatarService;
    private final ApplicationProperties applicationProperties;
    private final JimMobileFeatureService featureService;

    @Inject
    public CorbitMobileProjectResource(JiraAuthenticationContext authenticationContext,
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
    public Response list() {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.PROJECTS)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.PROJECTS);
        }
        try {
            Collection<Project> projects = ComponentAccessor.getPermissionManager()
                    .getProjects(ProjectPermissions.BROWSE_PROJECTS, viewer);
            List<Map<String, Object>> out = new ArrayList<>();
            if (projects != null) {
                for (Project p : projects) {
                    if (out.size() >= MAX_PROJECTS) {
                        break;
                    }
                    out.add(JimMobileProjects.toSummaryMap(p, this.applicationProperties));
                }
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("total", out.size());
            body.put("projects", out);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/projects", viewer.getKey(), ex,
                    "internal_error", "An internal error occurred while loading projects.");
        }
    }

    @GET
    @Path("/{projectKey}")
    public Response overview(@PathParam("projectKey") String projectKey) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.PROJECTS)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.PROJECTS);
        }
        if (projectKey == null || projectKey.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "projectKey is required.");
        }
        String key = projectKey.trim();
        try {
            Project project = ComponentAccessor.getProjectManager().getProjectObjByKey(key);
            if (project == null || !ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.BROWSE_PROJECTS, project, viewer)) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Project not found or you do not have permission to view it.");
            }
            Map<String, Object> body = JimMobileProjects.toOverviewMap(project, this.applicationProperties);

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("total", count(viewer, JimMobileProjects.totalQuery(project)));
            stats.put("open", count(viewer, JimMobileProjects.openQuery(project)));
            stats.put("assignedToMe", count(viewer, JimMobileProjects.assignedToMeQuery(project)));
            body.put("stats", stats);

            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/projects/{projectKey}", viewer.getKey(), ex,
                    "internal_error", "An internal error occurred while loading the project.");
        }
    }

    /**
     * Project issues for the Issues tab —
     * {@code GET /projects/{key}/issues?scope=open|all|mine&q=&startAt=&maxResults=}.
     * Scope is whitelisted and {@code q} is an optional bound text term; the
     * search runs through {@code SearchService} so issue security is enforced.
     */
    @GET
    @Path("/{projectKey}/issues")
    public Response issues(@PathParam("projectKey") String projectKey,
                           @QueryParam("scope") @DefaultValue("open") String scope,
                           @QueryParam("q") String q,
                           @QueryParam("startAt") @DefaultValue("0") int startAt,
                           @QueryParam("maxResults") @DefaultValue("20") int maxResults) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.PROJECTS)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.PROJECTS);
        }
        Project project = loadBrowsable(projectKey, viewer);
        if (project == null) {
            return notFound();
        }
        String useScope = JimMobileProjects.isValidScope(scope) ? scope : "open";
        int start = Math.max(startAt, 0);
        int max = maxResults <= 0 ? 20 : Math.min(maxResults, MAX_ISSUES_PAGE);
        try {
            Query query = JimMobileProjects.issuesQuery(project, useScope, q);
            PagerFilter<Issue> pager = new PagerFilter<>(max);
            pager.setStart(start);
            SearchResults<Issue> results = this.searchService.search(viewer, query, pager);
            List<Map<String, Object>> issues = new ArrayList<>();
            for (Issue issue : results.getResults()) {
                issues.add(JimMobileIssues.toIssueMap(issue, viewer, this.avatarService,
                        this.applicationProperties));
            }
            int total = results.getTotal();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("scope", useScope);
            body.put("startAt", start);
            body.put("maxResults", max);
            body.put("total", total);
            body.put("isLast", start + issues.size() >= total);
            body.put("issues", issues);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/projects/{projectKey}/issues", viewer.getKey(),
                    ex, "internal_error", "An internal error occurred while loading issues.");
        }
    }

    /**
     * Real project role membership for the Members tab —
     * {@code GET /projects/{key}/members}. Roles with no members are omitted.
     */
    @GET
    @Path("/{projectKey}/members")
    public Response members(@PathParam("projectKey") String projectKey) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.PROJECTS)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.PROJECTS);
        }
        Project project = loadBrowsable(projectKey, viewer);
        if (project == null) {
            return notFound();
        }
        try {
            ProjectRoleManager projectRoleManager =
                    ComponentAccessor.getComponent(ProjectRoleManager.class);
            List<Map<String, Object>> roles =
                    JimMobileProjects.roleMembers(project, projectRoleManager);
            Map<String, Object> body = new LinkedHashMap<>();
            ApplicationUser lead = project.getProjectLead();
            body.put("lead", lead != null ? lead.getDisplayName() : null);
            body.put("roles", roles);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/projects/{projectKey}/members", viewer.getKey(),
                    ex, "internal_error", "An internal error occurred while loading members.");
        }
    }

    /**
     * Recent project activity for the Activity tab —
     * {@code GET /projects/{key}/activity?limit=}. Backed by the project's most
     * recently updated issues (permission-safe via {@code SearchService}).
     */
    @GET
    @Path("/{projectKey}/activity")
    public Response activity(@PathParam("projectKey") String projectKey,
                             @QueryParam("limit") @DefaultValue("20") int limit) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.PROJECTS)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.PROJECTS);
        }
        Project project = loadBrowsable(projectKey, viewer);
        if (project == null) {
            return notFound();
        }
        int max = limit <= 0 ? 20 : Math.min(limit, MAX_ACTIVITY);
        try {
            Query query = JimMobileProjects.activityQuery(project);
            PagerFilter<Issue> pager = new PagerFilter<>(max);
            pager.setStart(0);
            SearchResults<Issue> results = this.searchService.search(viewer, query, pager);
            List<Map<String, Object>> items = new ArrayList<>();
            for (Issue issue : results.getResults()) {
                items.add(JimMobileIssues.toIssueMap(issue, viewer, this.avatarService,
                        this.applicationProperties));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("kind", "recentIssues");
            body.put("total", items.size());
            body.put("items", items);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/projects/{projectKey}/activity", viewer.getKey(),
                    ex, "internal_error", "An internal error occurred while loading activity.");
        }
    }

    private Project loadBrowsable(String projectKey, ApplicationUser viewer) {
        if (projectKey == null || projectKey.trim().isEmpty()) {
            return null;
        }
        Project project = ComponentAccessor.getProjectManager()
                .getProjectObjByKey(projectKey.trim());
        if (project == null || !ComponentAccessor.getPermissionManager()
                .hasPermission(ProjectPermissions.BROWSE_PROJECTS, project, viewer)) {
            return null;
        }
        return project;
    }

    private static Response notFound() {
        return JimRestResponses.errorJson(404, "not_found",
                "Project not found or you do not have permission to view it.");
    }

    private int count(ApplicationUser viewer, com.atlassian.query.Query query) {
        try {
            return (int) this.searchService.searchCount(viewer, query);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static Response unauthenticated() {
        return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                "You must be signed in to use CorbitChat Mobile.");
    }
}
