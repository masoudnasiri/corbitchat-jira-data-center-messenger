/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.component.ComponentAccessor
 *  com.atlassian.jira.issue.Issue
 *  com.atlassian.jira.issue.MutableIssue
 *  com.atlassian.jira.permission.ProjectPermissions
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  javax.inject.Inject
 *  javax.servlet.http.HttpServletRequest
 *  javax.ws.rs.Consumes
 *  javax.ws.rs.DefaultValue
 *  javax.ws.rs.GET
 *  javax.ws.rs.POST
 *  javax.ws.rs.Path
 *  javax.ws.rs.PathParam
 *  javax.ws.rs.Produces
 *  javax.ws.rs.QueryParam
 *  javax.ws.rs.core.Context
 *  javax.ws.rs.core.Response
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentStorageService;
import com.corbitlogic.jira.internalmessenger.attachment.JimAvatarStorageService;
import com.corbitlogic.jira.internalmessenger.attachment.JimMultipartParser;
import com.corbitlogic.jira.internalmessenger.bootstrap.JimPluginBootstrap;
import com.corbitlogic.jira.internalmessenger.service.JimGroupService;
import com.corbitlogic.jira.internalmessenger.dto.CreateDirectConversationRequestDto;
import com.corbitlogic.jira.internalmessenger.dto.SendMessageRequestDto;
import com.corbitlogic.jira.internalmessenger.model.JimConversationType;
import com.corbitlogic.jira.internalmessenger.rest.JimRestJsonMapper;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimAccessPolicyService;
import com.corbitlogic.jira.internalmessenger.service.JimLicenseService;
import com.corbitlogic.jira.internalmessenger.service.JimAttachmentService;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.ao.JimActivity;
import com.corbitlogic.jira.internalmessenger.service.JimActivityService;
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.service.JimMessageUserStateService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimPresenceService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import com.corbitlogic.jira.internalmessenger.service.JimSharedContentService;
import com.corbitlogic.jira.internalmessenger.util.JimIssueUrlBuilder;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(value="/conversations")
@Produces(value={"application/json"})
public class JimConversationResource {
    private static final Logger log = LoggerFactory.getLogger(JimConversationResource.class);
    private final JiraAuthenticationContext authenticationContext;
    private final JimPermissionService permissionService;
    private final JimConversationService conversationService;
    private final JimMessageService messageService;
    private final JimReadStateService readStateService;
    private final JimAttachmentService attachmentService;
    private final JimAttachmentStorageService attachmentStorageService;
    private final JimPresenceService presenceService;
    private final JimRestJsonMapper restJsonMapper;
    private final JimAccessPolicyService accessPolicyService;
    private final JimLicenseService licenseService;
    private final JimMessageUserStateService userStateService;
    private final JimActivityService activityService;
    private final JimGroupService groupService;
    private final JimAvatarStorageService avatarStorageService;
    private final JimSharedContentService sharedContentService;

    @Inject
    public JimConversationResource(JiraAuthenticationContext authenticationContext, JimPermissionService permissionService, JimConversationService conversationService, JimMessageService messageService, JimReadStateService readStateService, JimAttachmentService attachmentService, JimAttachmentStorageService attachmentStorageService, JimPresenceService presenceService, JimRestJsonMapper restJsonMapper, JimAccessPolicyService accessPolicyService, JimLicenseService licenseService, JimMessageUserStateService userStateService, JimActivityService activityService, JimGroupService groupService, JimAvatarStorageService avatarStorageService, JimSharedContentService sharedContentService, JimPluginBootstrap pluginBootstrap) {
        this.authenticationContext = authenticationContext;
        this.permissionService = permissionService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.readStateService = readStateService;
        this.attachmentService = attachmentService;
        this.attachmentStorageService = attachmentStorageService;
        this.presenceService = presenceService;
        this.restJsonMapper = restJsonMapper;
        this.accessPolicyService = accessPolicyService;
        this.licenseService = licenseService;
        this.userStateService = userStateService;
        this.activityService = activityService;
        this.groupService = groupService;
        this.avatarStorageService = avatarStorageService;
        this.sharedContentService = sharedContentService;
    }

    private java.util.List<java.util.Map<String, Object>> activitiesFor(int conversationId, String viewerKey) {
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (JimActivity a : this.activityService.activeFor(conversationId, viewerKey)) {
            java.util.Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("userKey", a.getUserKey());
            String name = a.getUserKey();
            try {
                com.atlassian.jira.user.ApplicationUser u = com.atlassian.jira.component.ComponentAccessor.getUserManager().getUserByKey(a.getUserKey());
                if (u != null) name = u.getDisplayName();
            } catch (Exception ignored) {}
            m.put("displayName", name);
            m.put("activity", a.getActivity());
            out.add(m);
        }
        return out;
    }

    private void enforceDirectChatPolicy(JimConversation conversation, String currentUserKey) {
        String otherKey;
        if (!this.accessPolicyService.isChatEnabled()) {
            throw JimMessengerException.forbidden("Chat has been disabled by the administrator");
        }
        if (conversation == null || !this.permissionService.isDirectConversation(conversation)) {
            return;
        }
        String string = otherKey = currentUserKey.equals(conversation.getUserAKey()) ? conversation.getUserBKey() : conversation.getUserAKey();
        if (otherKey != null && !otherKey.equals(currentUserKey)) {
            this.accessPolicyService.requireCanChatWith(currentUserKey, otherKey);
        }
    }

    @GET
    public Response listConversations() {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            this.presenceService.heartbeat(currentUserKey);
            this.conversationService.getOrCreateSystemConversation(currentUserKey);
            List<JimConversation> conversations = this.conversationService.listConversationsForUser(currentUserKey);
            List<Map<String, Object>> items = this.restJsonMapper.toConversationMaps(conversations, currentUserKey, viewer, this.readStateService::getUnreadCount);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("conversations", items);
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/conversations", userKey, ex, "internal_error", "An internal error occurred while loading conversations.");
        }
    }

    /**
     * Chat message search (Chat Search feature). Global across the user's conversations
     * when {@code conversationId} is omitted, or scoped to one membership-checked
     * conversation. Server-side permission enforced; never changes read state.
     * {@code GET /rest/jim/1.0/conversations/search?q=&conversationId=&limit=&cursor=}
     */
    @GET
    @Path(value="/search")
    public Response searchMessages(@javax.ws.rs.QueryParam("q") String q,
                                   @javax.ws.rs.QueryParam("conversationId") Integer conversationId,
                                   @javax.ws.rs.QueryParam("type") String type,
                                   @javax.ws.rs.QueryParam("limit") Integer limit,
                                   @javax.ws.rs.QueryParam("cursor") Integer cursor) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            java.util.Map<String, Object> body = this.messageService.searchMessages(
                    q, conversationId, type, limit == null ? 25 : limit.intValue(), cursor == null ? 0 : cursor.intValue());
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/conversations/search",
                    userKey, ex, "internal_error", "An internal error occurred during search.");
        }
    }

    @GET
    @Path(value="/unread-count")
    public Response getUnreadCount() {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            List<JimConversation> conversations = this.conversationService.listConversationsForUser(currentUserKey);
            int total = 0;
            for (JimConversation conversation : conversations) {
                total += this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            }
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("unreadCount", total);
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/conversations/unread-count", userKey, ex, "internal_error", "An internal error occurred while loading the unread count.");
        }
    }

    @GET
    @Path(value="/{conversationId}/messages/{messageId}/receipts")
    public Response getMessageReceipts(@PathParam(value="conversationId") int conversationId, @PathParam(value="messageId") int messageId) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            JimConversation conversation = this.conversationService.getConversationForUser(conversationId, currentUserKey);
            String type = conversation.getConversationType();
            if (!JimConversationType.GROUP.name().equals(type) && !JimConversationType.PROJECT.name().equals(type)) {
                return JimRestResponses.errorJson(400, "bad_request", "Message info is only available in group conversations");
            }
            JimMessage message = this.messageService.getMessageForParticipant(messageId, currentUserKey);
            if (message.getConversationId() != conversationId) {
                return JimRestResponses.errorJson(400, "bad_request", "Message does not belong to this conversation");
            }
            if (!currentUserKey.equals(message.getSenderUserKey())) {
                return JimRestResponses.errorJson(403, "forbidden", "Message info is only available for your own messages");
            }
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("receipts", this.restJsonMapper.toMessageReceiptMaps(conversation, message, currentUserKey));
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/conversations/{id}/messages/{messageId}/receipts", userKey, ex, "internal_error", "An internal error occurred while loading message info.");
        }
    }

    @POST
    @Path(value="/direct")
    @Consumes(value={"application/json"})
    public Response createDirectConversation(CreateDirectConversationRequestDto request) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        if (request == null || request.getTargetUserKey() == null) {
            return JimRestResponses.errorJson(400, "bad_request", "targetUserKey is required");
        }
        if (!this.licenseService.canUseMessaging()) {
            return JimRestResponses.licenseBlocked();
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            String normalizedTarget = JimValidation.requireNonBlank(request.getTargetUserKey(), "targetUserKey");
            this.accessPolicyService.requireCanChatWith(currentUserKey, normalizedTarget);
            JimConversation conversation = this.conversationService.getOrCreateDirectConversation(currentUserKey, normalizedTarget);
            this.permissionService.requireParticipant(conversation, currentUserKey);
            int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            return JimRestResponses.okJson(this.restJsonMapper.toConversationMap(conversation, currentUserKey, viewer, unreadCount));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/conversations/direct", userKey, ex, "internal_error", "An internal error occurred while creating the conversation.");
        }
    }

    @GET
    @Path(value="/{conversationId}/shared")
    public Response listSharedContent(@PathParam(value="conversationId") int conversationId,
                                      @QueryParam(value="type") @DefaultValue(value="files") String type,
                                      @QueryParam(value="cursor") Integer cursor,
                                      @QueryParam(value="limit") @DefaultValue(value="25") int limit) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            return JimRestResponses.okJson(this.sharedContentService.listShared(
                    conversationId, currentUserKey, type, cursor, Integer.valueOf(limit)));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/conversations/" + conversationId + "/shared",
                    userKey, ex, "internal_error", "An internal error occurred while loading shared content.");
        }
    }

    @GET
    @Path(value="/{conversationId}/messages")
    public Response listMessages(@PathParam(value="conversationId") int conversationId, @QueryParam(value="limit") @DefaultValue(value="50") int limit, @QueryParam(value="beforeMessageId") Integer beforeMessageId) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            this.presenceService.heartbeatConversation(currentUserKey, conversationId);
            JimConversation conversation = this.conversationService.getConversationForUser(conversationId, currentUserKey);
            List<JimMessage> messages = this.messageService.listMessages(conversationId, limit, beforeMessageId);
            // Sprint 12 hotfix: exclude messages the viewer chose "Delete for me"
            // (per-user hidden overlay). This web endpoint builds the list
            // directly, so the filter must be applied here too (matches mobile).
            java.util.Set<Integer> hidden = this.userStateService.hiddenMessageIds(currentUserKey, conversationId);
            if (!hidden.isEmpty()) {
                java.util.List<JimMessage> visible = new java.util.ArrayList<JimMessage>();
                for (JimMessage m : messages) {
                    if (!hidden.contains(m.getID())) {
                        visible.add(m);
                    }
                }
                messages = visible;
            }
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("messages", this.restJsonMapper.toMessageMaps(messages, viewer, conversation, currentUserKey));
            body.put("activities", this.activitiesFor(conversationId, currentUserKey));
            // Server clock so the client can anchor edit-window countdowns to the
            // server instead of a possibly-skewed browser clock.
            body.put("serverNowMs", System.currentTimeMillis());
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/conversations/" + conversationId + "/messages", userKey, ex, "internal_error", "An internal error occurred while loading messages.");
        }
    }

    @GET
    @Path(value="/{conversationId}/messages/around/{messageId}")
    public Response messagesAround(@PathParam(value="conversationId") int conversationId,
                                   @PathParam(value="messageId") int messageId,
                                   @QueryParam(value="before") @DefaultValue(value="25") int before,
                                   @QueryParam(value="after") @DefaultValue(value="25") int after) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation conversation = this.conversationService.getConversationForUser(conversationId, currentUserKey);
            Map<String, Object> page = this.messageService.listMessagesAround(conversationId, messageId, before, after);
            @SuppressWarnings("unchecked")
            List<JimMessage> messages = (List<JimMessage>) page.get("_messages");
            page.remove("_messages");
            page.put("messages", this.restJsonMapper.toMessageMaps(messages, viewer, conversation, currentUserKey));
            page.put("conversationId", conversationId);
            return JimRestResponses.okJson(page);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/jim/1.0/conversations/" + conversationId + "/messages/around/" + messageId,
                    userKey, ex, "internal_error", "An internal error occurred while loading messages.");
        }
    }

    @GET
    @Path(value="/{conversationId}/activity")
    public Response getActivity(@PathParam(value="conversationId") int conversationId) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            this.conversationService.getConversationForUser(conversationId, currentUserKey);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("activities", this.activitiesFor(conversationId, currentUserKey));
            return JimRestResponses.okJson(body);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/conversations/" + conversationId + "/activity", userKey, ex, "internal_error", "Unable to load activity.");
        }
    }

    @POST
    @Path(value="/{conversationId}/activity")
    @Consumes(value={"application/json"})
    public Response postActivity(@PathParam(value="conversationId") int conversationId, Map<String, Object> requestBody) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            this.conversationService.getConversationForUser(conversationId, currentUserKey);
            Object act = requestBody == null ? null : requestBody.get("activity");
            boolean clear = requestBody != null && Boolean.TRUE.equals(requestBody.get("clear"));
            String activity = act == null ? null : String.valueOf(act);
            if (clear || activity == null || activity.trim().isEmpty()) {
                this.activityService.clear(currentUserKey, conversationId);
            } else {
                this.activityService.record(currentUserKey, conversationId, activity);
            }
            return JimRestResponses.okJson(JimRestResponses.singleEntry("ok", true));
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/conversations/" + conversationId + "/activity", userKey, ex, "internal_error", "Unable to update activity.");
        }
    }

    @POST
    @Path(value="/{conversationId}/messages")
    @Consumes(value={"application/json"})
    public Response sendMessage(@PathParam(value="conversationId") int conversationId, SendMessageRequestDto request) {
        String requestedIssueKey;
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        String string = requestedIssueKey = request != null && request.getIssueKey() != null ? request.getIssueKey().trim() : "";
        if (request == null || request.getBody() == null && requestedIssueKey.isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "body is required");
        }
        if (!this.licenseService.canUseMessaging()) {
            return JimRestResponses.licenseBlocked();
        }
        try {
            JimMessage message;
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation conversation = this.conversationService.getConversationForUser(conversationId, currentUserKey);
            this.enforceDirectChatPolicy(conversation, currentUserKey);
            this.groupService.requireCanSend(conversation, currentUserKey); // Phase 8C group send policy
            if (!requestedIssueKey.isEmpty()) {
                MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(requestedIssueKey);
                if (issue == null || !ComponentAccessor.getPermissionManager().hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue)issue, viewer)) {
                    return JimRestResponses.errorJson(404, "not_found", "Issue not found or you do not have permission to view it");
                }
                String issueUrl = JimIssueUrlBuilder.buildBrowseUrl(ComponentAccessor.getApplicationProperties(), (Issue)issue);
                message = this.messageService.sendIssueLinkMessage(conversationId, currentUserKey, issue.getKey(), issue.getSummary(), issueUrl, request.getBody());
            } else {
                message = this.messageService.sendUserMessage(conversationId, currentUserKey, request.getBody(), request.getReplyToMessageId());
            }
            return JimRestResponses.okJson(this.restJsonMapper.toMessageMap(message, viewer, conversation, currentUserKey));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/conversations/" + conversationId + "/messages", userKey, ex, "internal_error", "An internal error occurred while sending the message.");
        }
    }

    @POST
    @Path(value="/{conversationId}/attachments")
    @Produces(value={"application/json"})
    public Response uploadAttachment(@PathParam(value="conversationId") int conversationId, @Context HttpServletRequest request) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        if (!this.licenseService.canUploadAttachments()) {
            return JimRestResponses.licenseBlocked();
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation uploadConversation = this.conversationService.getConversationForUser(conversationId, currentUserKey);
            this.enforceDirectChatPolicy(uploadConversation, currentUserKey);
            this.groupService.requireCanSend(uploadConversation, currentUserKey); // Phase 8C: block before any upload side effect
            File tempDirectory = this.attachmentStorageService.createUploadTempDirectory();
            JimMultipartParser.ParsedMultipartForm form = JimMultipartParser.parse(request, tempDirectory);
            JimAttachmentService.UploadResult result = this.attachmentService.uploadAttachment(conversationId, currentUserKey, form.getBody(), form.getFile(), form.getContentType(), form.getOriginalFilename());
            JimConversation conversation = this.conversationService.getConversationForUser(conversationId, currentUserKey);
            return JimRestResponses.okJson(this.restJsonMapper.toMessageMap(result.getCreatedMessage(), viewer, conversation, currentUserKey));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/conversations/" + conversationId + "/attachments", userKey, ex, "internal_error", "An internal error occurred while uploading the attachment.");
        }
    }

    @GET
    @Path(value="/{conversationId}/pinned")
    public Response getPinnedMessage(@PathParam(value="conversationId") int conversationId) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation conversation = this.conversationService.getConversationForUser(conversationId, currentUserKey);
            JimMessage pinned = this.messageService.getPinnedMessage(conversationId, currentUserKey);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("message", pinned == null ? null : this.restJsonMapper.toMessageMap(pinned, viewer, conversation, currentUserKey));
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/conversations/" + conversationId + "/pinned", userKey, ex, "internal_error", "An internal error occurred while loading the pinned message.");
        }
    }

    @POST
    @Path(value="/{conversationId}/read")
    @Consumes(value={"application/json"})
    public Response markConversationRead(@PathParam(value="conversationId") int conversationId) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            this.readStateService.markConversationRead(conversationId, currentUserKey);
            return Response.noContent().build();
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/conversations/" + conversationId + "/read", userKey, ex, "internal_error", "An internal error occurred while marking the conversation as read.");
        }
    }

    // --- Group/Project custom chat avatar (Phase 1) ---------------------------

    @POST
    @Path(value="/{conversationId}/avatar")
    @Produces(value={"application/json"})
    public Response uploadAvatar(@PathParam(value="conversationId") int conversationId, @Context HttpServletRequest request) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            File tempDirectory = this.avatarStorageService.createUploadTempDirectory();
            JimMultipartParser.ParsedMultipartForm form = JimMultipartParser.parse(request, tempDirectory);
            JimConversation conversation = this.groupService.uploadAvatar(conversationId, currentUserKey, form.getFile(), form.getContentType(), form.getOriginalFilename());
            int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            return JimRestResponses.okJson(this.restJsonMapper.toConversationMap(conversation, currentUserKey, viewer, unreadCount));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/conversations/" + conversationId + "/avatar", userKey, ex, "internal_error", "An internal error occurred while uploading the avatar.");
        }
    }

    @DELETE
    @Path(value="/{conversationId}/avatar")
    @Produces(value={"application/json"})
    public Response deleteAvatar(@PathParam(value="conversationId") int conversationId) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation conversation = this.groupService.resetAvatar(conversationId, currentUserKey);
            int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            return JimRestResponses.okJson(this.restJsonMapper.toConversationMap(conversation, currentUserKey, viewer, unreadCount));
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "DELETE /rest/jim/1.0/conversations/" + conversationId + "/avatar", userKey, ex, "internal_error", "An internal error occurred while resetting the avatar.");
        }
    }

    @GET
    @Path(value="/{conversationId}/avatar")
    public Response getAvatar(@PathParam(value="conversationId") int conversationId) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            // Participant-checked + archived groups become 404 (client falls back).
            JimConversation conversation = this.conversationService.getConversationForUser(conversationId, currentUserKey);
            String storagePath = conversation.getAvatarStoragePath();
            if (storagePath == null || storagePath.trim().isEmpty()) {
                return Response.status(404).build();
            }
            final File file = this.avatarStorageService.resolveAvatarFile(storagePath);
            String contentType = conversation.getAvatarContentType();
            if (contentType == null || contentType.trim().isEmpty()) {
                contentType = "image/png";
            }
            long version = conversation.getAvatarVersion() == null ? 0L : conversation.getAvatarVersion();
            String checksum = conversation.getAvatarChecksum();
            EntityTag etag = new EntityTag((checksum != null && !checksum.isEmpty() ? checksum : ("v" + version)));
            CacheControl cc = new CacheControl();
            cc.setPrivate(true);
            cc.setMaxAge(31536000);
            // Versioned + immutable: the ?v= query changes on every avatar update.
            cc.getCacheExtension().put("immutable", "");
            StreamingOutput stream = new StreamingOutput() {
                @Override
                public void write(java.io.OutputStream output) throws java.io.IOException {
                    byte[] buffer = new byte[8192];
                    try (InputStream input = new FileInputStream(file)) {
                        int read;
                        while ((read = input.read(buffer)) >= 0) {
                            output.write(buffer, 0, read);
                        }
                        output.flush();
                    }
                }
            };
            return Response.ok(stream).type(contentType).tag(etag).cacheControl(cc)
                    .header("Content-Length", file.length()).build();
        }
        catch (JimMessengerException ex) {
            if (ex.getStatusCode() == 404) {
                return Response.status(404).build();
            }
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/conversations/" + conversationId + "/avatar", userKey, ex, "internal_error", "An internal error occurred while loading the avatar.");
        }
    }

    private String resolveCurrentUserKey() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        return user != null ? user.getKey() : null;
    }
}

