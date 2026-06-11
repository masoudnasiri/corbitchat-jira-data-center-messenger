/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.component.ComponentAccessor
 *  com.atlassian.jira.permission.ProjectPermissions
 *  com.atlassian.jira.project.Project
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  javax.inject.Inject
 *  javax.ws.rs.GET
 *  javax.ws.rs.Path
 *  javax.ws.rs.PathParam
 *  javax.ws.rs.Produces
 *  javax.ws.rs.core.Response
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.rest.JimRestJsonMapper;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimProjectChatService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import java.util.LinkedHashMap;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(value="/projects")
@Produces(value={"application/json"})
public class JimProjectResource {
    private static final Logger log = LoggerFactory.getLogger(JimProjectResource.class);
    private final JiraAuthenticationContext authenticationContext;
    private final JimPermissionService permissionService;
    private final JimProjectChatService projectChatService;
    private final JimReadStateService readStateService;
    private final JimRestJsonMapper restJsonMapper;

    @Inject
    public JimProjectResource(JiraAuthenticationContext authenticationContext, JimPermissionService permissionService, JimProjectChatService projectChatService, JimReadStateService readStateService, JimRestJsonMapper restJsonMapper) {
        this.authenticationContext = authenticationContext;
        this.permissionService = permissionService;
        this.projectChatService = projectChatService;
        this.readStateService = readStateService;
        this.restJsonMapper = restJsonMapper;
    }

    @GET
    @Path(value="/{projectKey}/conversation")
    public Response getProjectConversation(@PathParam(value="projectKey") String projectKey) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String userKey = this.permissionService.requireAuthenticatedUserKey();
            Project project = ComponentAccessor.getProjectManager().getProjectObjByKey(projectKey != null ? projectKey.trim() : null);
            if (project == null || !ComponentAccessor.getPermissionManager().hasPermission(ProjectPermissions.BROWSE_PROJECTS, project, viewer)) {
                return JimRestResponses.errorJson(404, "not_found", "Project not found or you do not have permission to view it");
            }
            JimConversation conversation = this.projectChatService.ensureProjectConversation(project);
            if (conversation == null) {
                return JimRestResponses.errorJson(500, "internal_error", "Unable to load the project chat");
            }
            boolean isMember = this.permissionService.isParticipant(conversation, userKey);
            boolean isLead = this.projectChatService.isProjectLead(conversation, userKey);
            int unreadCount = isMember ? this.readStateService.getUnreadCount(conversation.getID(), userKey) : 0;
            ApplicationUser lead = project.getProjectLead();
            LinkedHashMap<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("conversation", this.restJsonMapper.toConversationMap(conversation, userKey, viewer, unreadCount));
            payload.put("isMember", isMember);
            payload.put("isLead", isLead);
            payload.put("leadDisplayName", lead != null ? lead.getDisplayName() : null);
            return JimRestResponses.okJson(payload);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/projects/" + projectKey + "/conversation", viewer.getKey(), ex, "internal_error", "An internal error occurred while loading the project chat.");
        }
    }
}

