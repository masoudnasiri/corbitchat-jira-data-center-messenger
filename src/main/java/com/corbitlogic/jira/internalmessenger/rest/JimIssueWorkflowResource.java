package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.service.JimWorkflowViewService;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 8D — read-only custom Workflow Viewer endpoint for the web issue page.
 * {@code GET /rest/jim/1.0/issues/{issueKey}/workflow-view}. Authenticated + Browse
 * required; honors issue security via {@code hasPermission(BROWSE_PROJECTS, ...)}.
 * Never mutates/transitions the issue; no DB write; no Jira native Workflow Designer.
 */
@Path(value = "/issues")
@Produces(value = {"application/json"})
public class JimIssueWorkflowResource {

    private static final Logger log = LoggerFactory.getLogger(JimIssueWorkflowResource.class);
    private final JiraAuthenticationContext authenticationContext;

    @Inject
    public JimIssueWorkflowResource(JiraAuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    @GET
    @Path(value = "/{issueKey}/workflow-view")
    public Response workflowView(@PathParam(value = "issueKey") String issueKey) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            MutableIssue issue = ComponentAccessor.getIssueManager()
                    .getIssueByCurrentKey(issueKey == null ? null : issueKey.trim());
            if (issue == null || !ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue) issue, viewer)) {
                // Do not reveal existence of inaccessible issues (product convention).
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            return JimRestResponses.okJson(JimWorkflowViewService.buildWorkflowView(issue, viewer));
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/jim/1.0/issues/" + issueKey + "/workflow-view",
                    viewer.getKey(), ex, "internal_error", "Unable to load the workflow view.");
        }
    }
}
