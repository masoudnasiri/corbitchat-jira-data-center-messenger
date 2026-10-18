/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  javax.inject.Inject
 *  javax.ws.rs.Consumes
 *  javax.ws.rs.DELETE
 *  javax.ws.rs.POST
 *  javax.ws.rs.PUT
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
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.dto.EditMessageRequestDto;
import com.corbitlogic.jira.internalmessenger.dto.ReactionRequestDto;
import com.corbitlogic.jira.internalmessenger.rest.JimRestJsonMapper;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimChatAuditService;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimGroupService;
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.service.JimMessageUserStateService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimReactionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(value="/messages")
@Produces(value={"application/json"})
public class JimMessageResource {
    private static final Logger log = LoggerFactory.getLogger(JimMessageResource.class);
    private final JiraAuthenticationContext authenticationContext;
    private final JimPermissionService permissionService;
    private final JimConversationService conversationService;
    private final JimMessageService messageService;
    private final JimReactionService reactionService;
    private final JimRestJsonMapper restJsonMapper;
    private final JimMessageUserStateService userStateService;
    private final JimChatAuditService auditService;
    private final JimGroupService groupService;

    @Inject
    public JimMessageResource(JiraAuthenticationContext authenticationContext, JimPermissionService permissionService, JimConversationService conversationService, JimMessageService messageService, JimReactionService reactionService, JimRestJsonMapper restJsonMapper, JimMessageUserStateService userStateService, JimChatAuditService auditService, JimGroupService groupService) {
        this.authenticationContext = authenticationContext;
        this.permissionService = permissionService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.reactionService = reactionService;
        this.restJsonMapper = restJsonMapper;
        this.userStateService = userStateService;
        this.auditService = auditService;
        this.groupService = groupService;
    }

    /** Delete-for-me: hide a single message for the current user only (Sprint 12). */
    @POST
    @Path(value="/{messageId}/hide")
    public Response hideMessage(@PathParam(value="messageId") int messageId) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            JimMessage message = this.messageService.getMessageForParticipant(messageId, userKey);
            this.userStateService.hide(messageId, message.getConversationId(), userKey);
            this.auditService.record(userKey, "message.hideForMe", messageId,
                    message.getConversationId(), null, null);
            return JimRestResponses.okJson(JimRestResponses.singleEntry("ok", true));
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/messages/" + messageId + "/hide", userKey, ex, "internal_error", "An internal error occurred while hiding the message.");
        }
    }

    /** Delete-for-me in bulk: hide several messages for the current user only. */
    @POST
    @Path(value="/hide")
    @Consumes(value={"application/json"})
    public Response hideMessages(Map<String, Object> request) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        Object idsRaw = request == null ? null : request.get("ids");
        if (!(idsRaw instanceof List)) {
            return JimRestResponses.errorJson(400, "bad_request", "ids array is required");
        }
        try {
            List<Integer> ids = new ArrayList<>();
            int conversationId = -1;
            for (Object o : (List<?>) idsRaw) {
                int id = o instanceof Number ? ((Number) o).intValue() : Integer.parseInt(String.valueOf(o));
                // Participant check per message; all must be in one conversation.
                JimMessage message = this.messageService.getMessageForParticipant(id, userKey);
                conversationId = message.getConversationId();
                ids.add(id);
            }
            if (conversationId >= 0) {
                this.userStateService.hideAll(ids, conversationId, userKey);
                this.auditService.record(userKey, "message.hideForMe.bulk", null,
                        conversationId, null, "count=" + ids.size());
            }
            return JimRestResponses.okJson(JimRestResponses.singleEntry("ok", true));
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/messages/hide", userKey, ex, "internal_error", "An internal error occurred while hiding messages.");
        }
    }

    @PUT
    @Path(value="/{messageId}")
    @Consumes(value={"application/json"})
    public Response editMessage(@PathParam(value="messageId") int messageId, EditMessageRequestDto request) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        if (request == null || request.getBody() == null) {
            return JimRestResponses.errorJson(400, "bad_request", "body is required");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimMessage message = this.messageService.editUserMessage(messageId, currentUserKey, request.getBody());
            JimConversation conversation = this.conversationService.getConversationForUser(message.getConversationId(), currentUserKey);
            return JimRestResponses.okJson(this.restJsonMapper.toMessageMap(message, viewer, conversation, currentUserKey));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "PUT /rest/jim/1.0/messages/" + messageId, userKey, ex, "internal_error", "An internal error occurred while editing the message.");
        }
    }

    @DELETE
    @Path(value="/{messageId}")
    public Response deleteMessage(@PathParam(value="messageId") int messageId) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimMessage message = this.messageService.deleteUserMessage(messageId, currentUserKey);
            JimConversation conversation = this.conversationService.getConversationForUser(message.getConversationId(), currentUserKey);
            return JimRestResponses.okJson(this.restJsonMapper.toMessageMap(message, viewer, conversation, currentUserKey));
        }
        catch (JimMessengerException ex) {
            if (ex.getStatusCode() == 403 && "Messages can only be deleted within 10 minutes.".equals(ex.getMessage())) {
                return JimRestResponses.errorJson(403, "delete_window_expired", ex.getMessage());
            }
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "DELETE /rest/jim/1.0/messages/" + messageId, userKey, ex, "internal_error", "An internal error occurred while deleting the message.");
        }
    }

    @POST
    @Path(value="/{messageId}/pin")
    public Response pinMessage(@PathParam(value="messageId") int messageId) {
        return this.setPinned(messageId, true);
    }

    @DELETE
    @Path(value="/{messageId}/pin")
    public Response unpinMessage(@PathParam(value="messageId") int messageId) {
        return this.setPinned(messageId, false);
    }

    private Response setPinned(int messageId, boolean pinned) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimMessage message = this.messageService.setPinned(messageId, currentUserKey, pinned);
            JimConversation conversation = this.conversationService.getConversationForUser(message.getConversationId(), currentUserKey);
            return JimRestResponses.okJson(this.restJsonMapper.toMessageMap(message, viewer, conversation, currentUserKey));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, (pinned ? "POST" : "DELETE") + " /rest/jim/1.0/messages/" + messageId + "/pin", userKey, ex, "internal_error", "An internal error occurred while updating the pinned message.");
        }
    }

    @POST
    @Path(value="/{messageId}/forward")
    @Consumes(value={"application/json"})
    public Response forwardMessage(@PathParam(value="messageId") int messageId, java.util.Map<String, Object> request) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        Integer targetConversationId = asInt(request, "targetConversationId");
        if (targetConversationId == null || targetConversationId <= 0) {
            return JimRestResponses.errorJson(400, "bad_request", "targetConversationId is required");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            // Phase 8C: forwarding creates a message in the TARGET group — enforce its send policy.
            JimConversation target = this.conversationService.getConversationForUser(targetConversationId, currentUserKey);
            this.groupService.requireCanSend(target, currentUserKey);
            JimMessage message = this.messageService.forwardUserMessage(targetConversationId, currentUserKey, messageId);
            JimConversation conversation = this.conversationService.getConversationForUser(message.getConversationId(), currentUserKey);
            return JimRestResponses.okJson(this.restJsonMapper.toMessageMap(message, viewer, conversation, currentUserKey));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/messages/" + messageId + "/forward", userKey, ex, "internal_error", "An internal error occurred while forwarding the message.");
        }
    }

    private static Integer asInt(java.util.Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.valueOf(Integer.parseInt(value.toString().trim()));
        }
        catch (NumberFormatException ex) {
            return null;
        }
    }

    @POST
    @Path(value="/{messageId}/action")
    public Response markActioned(@PathParam(value="messageId") int messageId) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimMessage message = this.messageService.markActioned(messageId, currentUserKey);
            JimConversation conversation = this.conversationService.getConversationForUser(message.getConversationId(), currentUserKey);
            return JimRestResponses.okJson(this.restJsonMapper.toMessageMap(message, viewer, conversation, currentUserKey));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/messages/" + messageId + "/action", userKey, ex, "internal_error", "An internal error occurred while marking the message as actioned.");
        }
    }

    @POST
    @Path(value="/{messageId}/schedule-approval")
    @Consumes(value={"application/json"})
    public Response scheduleApprovalDecision(@PathParam(value="messageId") int messageId, Map<String, Object> body) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        String action = body != null && body.get("action") != null ? String.valueOf(body.get("action")) : null;
        String reason = body != null && body.get("reason") != null ? String.valueOf(body.get("reason")) : null;
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimMessage message = this.messageService.getMessageForParticipant(messageId, currentUserKey);
            com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalActions.Result result =
                    com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalActions.execute(message, viewer, action, reason);
            if (!result.isOk()) {
                return JimRestResponses.errorJson(result.statusCode, result.errorCode, result.errorMessage);
            }
            JimMessage actioned = this.messageService.markActioned(messageId, currentUserKey);
            JimConversation conversation = this.conversationService.getConversationForUser(actioned.getConversationId(), currentUserKey);
            return JimRestResponses.okJson(this.restJsonMapper.toMessageMap(actioned, viewer, conversation, currentUserKey));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/messages/" + messageId + "/schedule-approval", userKey, ex, "internal_error", "An internal error occurred while processing the approval.");
        }
    }

    @POST
    @Path(value="/{messageId}/reactions")
    @Consumes(value={"application/json"})
    public Response toggleReaction(@PathParam(value="messageId") int messageId, ReactionRequestDto request) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        if (request == null || request.getEmoji() == null || request.getEmoji().trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "emoji is required");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            this.reactionService.toggleReaction(messageId, currentUserKey, request.getEmoji().trim());
            JimMessage message = this.messageService.getMessageForParticipant(messageId, currentUserKey);
            JimConversation conversation = this.conversationService.getConversationForUser(message.getConversationId(), currentUserKey);
            return JimRestResponses.okJson(this.restJsonMapper.toMessageMap(message, viewer, conversation, currentUserKey));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/messages/" + messageId + "/reactions", userKey, ex, "internal_error", "An internal error occurred while updating the reaction.");
        }
    }

    private String resolveCurrentUserKey() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        return user != null ? user.getKey() : null;
    }
}

