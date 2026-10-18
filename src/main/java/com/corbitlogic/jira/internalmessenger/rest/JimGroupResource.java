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
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimGroupService;
import com.corbitlogic.jira.internalmessenger.service.JimLicenseService;
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
    private final JimLicenseService licenseService;
    private final JimConversationService conversationService;

    @Inject
    public JimGroupResource(JiraAuthenticationContext authenticationContext, JimPermissionService permissionService, JimGroupService groupService, JimReadStateService readStateService, JimRestJsonMapper restJsonMapper, JimLicenseService licenseService, JimConversationService conversationService) {
        this.authenticationContext = authenticationContext;
        this.permissionService = permissionService;
        this.groupService = groupService;
        this.readStateService = readStateService;
        this.restJsonMapper = restJsonMapper;
        this.licenseService = licenseService;
        this.conversationService = conversationService;
    }

    @POST
    @Consumes(value={"application/json"})
    public Response createGroup(CreateGroupRequestDto request) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        if (!this.licenseService.canUseMessaging()) {
            return JimRestResponses.licenseBlocked();
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
            JimConversation conversation = this.conversationService.getConversationForUser(conversationId, currentUserKey);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("members", this.restJsonMapper.toGroupMemberMaps(members, viewer, conversation)); // Phase 8C: incl. allowedSender/canSend
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

    @POST
    @Path(value="/{conversationId}/members/{memberUserKey}/role")
    @Consumes(value={"application/json"})
    public Response setMemberRole(@PathParam(value="conversationId") int conversationId, @PathParam(value="memberUserKey") String memberUserKey, java.util.Map<String, Object> request) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        String role = request != null && request.get("role") != null ? String.valueOf(request.get("role")).trim().toUpperCase() : "";
        if (!"ADMIN".equals(role) && !"MEMBER".equals(role)) {
            return JimRestResponses.errorJson(400, "bad_request", "role must be ADMIN or MEMBER");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            if ("ADMIN".equals(role)) {
                this.groupService.promoteToAdmin(conversationId, currentUserKey, memberUserKey);
            } else {
                this.groupService.demoteToMember(conversationId, currentUserKey, memberUserKey);
            }
            List<JimGroupMember> members = this.groupService.listMembers(conversationId, currentUserKey);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("members", this.restJsonMapper.toGroupMemberMaps(members, viewer));
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/groups/" + conversationId + "/members/" + memberUserKey + "/role", userKey, ex, "internal_error", "An internal error occurred while updating the member role.");
        }
    }

    @POST
    @Path(value="/{conversationId}/rename")
    @Consumes(value={"application/json"})
    public Response renameGroup(@PathParam(value="conversationId") int conversationId, java.util.Map<String, Object> request) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        String name = request != null && request.get("name") != null ? String.valueOf(request.get("name")) : null;
        if (name == null || name.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "name is required");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation conversation = this.groupService.renameGroup(conversationId, currentUserKey, name);
            int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            return JimRestResponses.okJson(this.restJsonMapper.toConversationMap(conversation, currentUserKey, viewer, unreadCount));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/groups/" + conversationId + "/rename", userKey, ex, "internal_error", "An internal error occurred while renaming the group.");
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

    // ---- Phase 8C: group send policy ----

    /** OWNER/ADMIN: set the group send policy ("ALL_MEMBERS" | "ADMINS_AND_ALLOWED"). */
    @POST
    @Path(value="/{conversationId}/send-policy")
    @Consumes(value={"application/json"})
    public Response setSendPolicy(@PathParam(value="conversationId") int conversationId, java.util.Map<String, Object> request) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        String policy = request != null && request.get("sendPolicy") != null ? String.valueOf(request.get("sendPolicy")) : null;
        if (policy == null || policy.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "sendPolicy is required");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation conversation = this.groupService.setSendPolicy(conversationId, currentUserKey, policy);
            int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            return JimRestResponses.okJson(this.restJsonMapper.toConversationMap(conversation, currentUserKey, viewer, unreadCount));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/groups/" + conversationId + "/send-policy", userKey, ex, "internal_error", "An internal error occurred while updating the send policy.");
        }
    }

    /** OWNER/ADMIN: allow/disallow a specific non-admin member to send. Body: {userKey, allowed}. */
    @POST
    @Path(value="/{conversationId}/allowed-senders")
    @Consumes(value={"application/json"})
    public Response setAllowedSender(@PathParam(value="conversationId") int conversationId, java.util.Map<String, Object> request) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        String targetUserKey = request != null && request.get("userKey") != null ? String.valueOf(request.get("userKey")) : null;
        if (targetUserKey == null || targetUserKey.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "userKey is required");
        }
        boolean allowed = request != null && Boolean.TRUE.equals(request.get("allowed"));
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            this.groupService.setAllowedSender(conversationId, currentUserKey, targetUserKey, allowed);
            List<JimGroupMember> members = this.groupService.listMembers(conversationId, currentUserKey);
            JimConversation conversation = this.conversationService.getConversationForUser(conversationId, currentUserKey);
            java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<String, Object>();
            body.put("members", this.restJsonMapper.toGroupMemberMaps(members, viewer, conversation));
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/groups/" + conversationId + "/allowed-senders", userKey, ex, "internal_error", "An internal error occurred while updating allowed senders.");
        }
    }

    private String resolveCurrentUserKey() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        return user != null ? user.getKey() : null;
    }
}

