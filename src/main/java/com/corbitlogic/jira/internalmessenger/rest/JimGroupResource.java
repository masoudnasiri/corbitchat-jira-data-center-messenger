/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  javax.inject.Inject
 *  javax.ws.rs.Consumes
 *  javax.ws.rs.DELETE
 *  javax.ws.rs.GET
 *  javax.ws.rs.POST
 *  javax.ws.rs.Path
 *  javax.ws.rs.PathParam
 *  javax.ws.rs.Produces
 *  javax.ws.rs.core.Response
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimGroupMember;
import com.corbitlogic.jira.internalmessenger.dto.CreateGroupRequestDto;
import com.corbitlogic.jira.internalmessenger.dto.GroupMemberRequestDto;
import com.corbitlogic.jira.internalmessenger.rest.JimRestJsonMapper;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimGroupService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import java.util.LinkedHashMap;
import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(value="/groups")
@Produces(value={"application/json"})
public class JimGroupResource {
    private static final Logger log = LoggerFactory.getLogger(JimGroupResource.class);
    private final JiraAuthenticationContext authenticationContext;
    private final JimPermissionService permissionService;
    private final JimGroupService groupService;
    private final JimReadStateService readStateService;
    private final JimRestJsonMapper restJsonMapper;

    @Inject
    public JimGroupResource(JiraAuthenticationContext authenticationContext, JimPermissionService permissionService, JimGroupService groupService, JimReadStateService readStateService, JimRestJsonMapper restJsonMapper) {
        this.authenticationContext = authenticationContext;
        this.permissionService = permissionService;
        this.groupService = groupService;
        this.readStateService = readStateService;
        this.restJsonMapper = restJsonMapper;
    }

    @POST
    @Consumes(value={"application/json"})
    public Response createGroup(CreateGroupRequestDto request) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "name is required");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation conversation = this.groupService.createGroup(currentUserKey, request.getName(), request.getMemberUserKeys());
            int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            return JimRestResponses.okJson(this.restJsonMapper.toConversationMap(conversation, currentUserKey, viewer, unreadCount));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/groups", userKey, ex, "internal_error", "An internal error occurred while creating the group.");
        }
    }

    @DELETE
    @Path(value="/{conversationId}")
    public Response deleteGroup(@PathParam(value="conversationId") int conversationId) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            this.groupService.deleteGroup(conversationId, currentUserKey);
            return Response.noContent().build();
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "DELETE /rest/jim/1.0/groups/" + conversationId, userKey, ex, "internal_error", "An internal error occurred while deleting the group.");
        }
    }

    @GET
    @Path(value="/{conversationId}/members")
    public Response listMembers(@PathParam(value="conversationId") int conversationId) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            List<JimGroupMember> members = this.groupService.listMembers(conversationId, currentUserKey);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("members", this.restJsonMapper.toGroupMemberMaps(members, viewer));
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/groups/" + conversationId + "/members", userKey, ex, "internal_error", "An internal error occurred while loading group members.");
        }
    }

    @POST
    @Path(value="/{conversationId}/members")
    @Consumes(value={"application/json"})
    public Response addMember(@PathParam(value="conversationId") int conversationId, GroupMemberRequestDto request) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        if (request == null || request.getUserKey() == null || request.getUserKey().trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "userKey is required");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            this.groupService.addMember(conversationId, currentUserKey, request.getUserKey());
            List<JimGroupMember> members = this.groupService.listMembers(conversationId, currentUserKey);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("members", this.restJsonMapper.toGroupMemberMaps(members, viewer));
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/groups/" + conversationId + "/members", userKey, ex, "internal_error", "An internal error occurred while adding the group member.");
        }
    }

    @DELETE
    @Path(value="/{conversationId}/members/{memberUserKey}")
    public Response removeMember(@PathParam(value="conversationId") int conversationId, @PathParam(value="memberUserKey") String memberUserKey) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            this.groupService.removeMember(conversationId, currentUserKey, memberUserKey);
            return Response.noContent().build();
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "DELETE /rest/jim/1.0/groups/" + conversationId + "/members/" + memberUserKey, userKey, ex, "internal_error", "An internal error occurred while removing the group member.");
        }
    }

    private String resolveCurrentUserKey() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        return user != null ? user.getKey() : null;
    }
}

