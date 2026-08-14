package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.corbitlogic.jira.internalmessenger.ao.JimAttachment;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentStorageService;
import com.corbitlogic.jira.internalmessenger.attachment.JimAvatarStorageService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.util.AttachmentUtils;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentStreaming;
import com.corbitlogic.jira.internalmessenger.attachment.JimMultipartParser;
import com.corbitlogic.jira.internalmessenger.dto.UserSearchResultDto;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileAvatars;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.model.JimConversationType;
import com.corbitlogic.jira.internalmessenger.rest.JimRestJsonMapper;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.ao.JimGroupMember;
import com.corbitlogic.jira.internalmessenger.ao.JimActivity;
import com.corbitlogic.jira.internalmessenger.service.JimAccessPolicyService;
import com.corbitlogic.jira.internalmessenger.service.JimActivityService;
import com.corbitlogic.jira.internalmessenger.service.JimAttachmentService;
import com.corbitlogic.jira.internalmessenger.service.JimChatAuditService;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimMessageUserStateService;
import com.corbitlogic.jira.internalmessenger.service.JimGroupService;
import com.corbitlogic.jira.internalmessenger.service.JimLicenseService;
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimMobileFeatureService;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimPresenceService;
import com.corbitlogic.jira.internalmessenger.service.JimProjectChatService;
import com.corbitlogic.jira.internalmessenger.service.JimReactionService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import com.corbitlogic.jira.internalmessenger.service.JimSharedContentService;
import com.corbitlogic.jira.internalmessenger.service.JimUserSearchService;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mobile BFF Direct Chat (Sprint 02). Mounted under
 * {@code /rest/corbit-mobile/1.0/chat}.
 *
 * <p>Thin wrappers over the SAME services and {@link JimRestJsonMapper} used by
 * the web surface at {@code /rest/jim/1.0/*}, so the JSON shapes and all
 * permission/policy rules are identical and web/mobile stay in sync. The only
 * reason these live under the mobile module is authentication: the mobile
 * session filter resolves the real Jira user for {@code X-CorbitChat-Session}
 * tokens on this path, so PAT, Jira cookie/basic, and mobile-session auth all
 * work consistently. The web {@code /rest/jim/1.0/*} endpoints are unchanged.</p>
 *
 * <p>Scope is DIRECT + GROUP chat (Sprint 08). Project chat and the SYSTEM
 * (Assistant) feed are separate concepts and stay out of the conversation
 * list; issue-link sends are not exposed here.</p>
 *
 * <p>{@code @AnonymousAllowed} lets the request past Jira's anonymous-REST gate;
 * each method still enforces {@code getLoggedInUser() != null} and returns 401
 * otherwise. Message bodies are never logged.</p>
 */
@Path("/chat")
@Produces({"application/json"})
@AnonymousAllowed
public class CorbitMobileChatResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobileChatResource.class);

    private final JiraAuthenticationContext authenticationContext;
    private final JimPermissionService permissionService;
    private final JimConversationService conversationService;
    private final JimMessageService messageService;
    private final JimReactionService reactionService;
    private final JimReadStateService readStateService;
    private final JimPresenceService presenceService;
    private final JimRestJsonMapper restJsonMapper;
    private final JimAccessPolicyService accessPolicyService;
    private final JimLicenseService licenseService;
    private final JimUserSearchService userSearchService;
    private final JimMobileFeatureService featureService;
    private final JimAttachmentService attachmentService;
    private final JimAttachmentStorageService attachmentStorageService;
    private final JimAvatarStorageService avatarStorageService;
    private final JimGroupService groupService;
    private final JimProjectChatService projectChatService;
    private final JimMessageUserStateService userStateService;
    private final JimChatAuditService auditService;
    private final JimActivityService activityService;
    private final JimSharedContentService sharedContentService;

    @Inject
    public CorbitMobileChatResource(JiraAuthenticationContext authenticationContext,
                                    JimPermissionService permissionService,
                                    JimConversationService conversationService,
                                    JimMessageService messageService,
                                    JimReactionService reactionService,
                                    JimReadStateService readStateService,
                                    JimPresenceService presenceService,
                                    JimRestJsonMapper restJsonMapper,
                                    JimAccessPolicyService accessPolicyService,
                                    JimLicenseService licenseService,
                                    JimUserSearchService userSearchService,
                                    JimMobileFeatureService featureService,
                                    JimAttachmentService attachmentService,
                                    JimAttachmentStorageService attachmentStorageService,
                                    JimAvatarStorageService avatarStorageService,
                                    JimGroupService groupService,
                                    JimProjectChatService projectChatService,
                                    JimMessageUserStateService userStateService,
                                    JimChatAuditService auditService,
                                    JimActivityService activityService,
                                    JimSharedContentService sharedContentService) {
        this.authenticationContext = authenticationContext;
        this.permissionService = permissionService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.reactionService = reactionService;
        this.readStateService = readStateService;
        this.presenceService = presenceService;
        this.restJsonMapper = restJsonMapper;
        this.accessPolicyService = accessPolicyService;
        this.licenseService = licenseService;
        this.userSearchService = userSearchService;
        this.featureService = featureService;
        this.attachmentService = attachmentService;
        this.attachmentStorageService = attachmentStorageService;
        this.avatarStorageService = avatarStorageService;
        this.groupService = groupService;
        this.projectChatService = projectChatService;
        this.userStateService = userStateService;
        this.auditService = auditService;
        this.activityService = activityService;
        this.sharedContentService = sharedContentService;
    }

    /** Serialize non-expired activities of OTHER participants for the poll response. */
    private List<Map<String, Object>> activitiesFor(int conversationId, String viewerKey) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JimActivity a : this.activityService.activeFor(conversationId, viewerKey)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userKey", a.getUserKey());
            m.put("displayName", displayNameFor(a.getUserKey()));
            m.put("activity", a.getActivity());
            out.add(m);
        }
        return out;
    }

    private String displayNameFor(String userKey) {
        if (userKey == null) {
            return null;
        }
        try {
            ApplicationUser u = com.atlassian.jira.component.ComponentAccessor.getUserManager().getUserByKey(userKey);
            return u != null ? u.getDisplayName() : userKey;
        } catch (Exception ex) {
            return userKey;
        }
    }

    /**
     * Sprint 04G mobile feature gate for the whole chat section. Returns a 403
     * {@code feature_disabled} response when the current user is not allowed the
     * {@code chat} feature, or null when access is permitted. Callers invoke this
     * right after confirming the user is authenticated and before any data is
     * loaded, so no chat data leaks before the check.
     */
    private Response chatFeatureBlocked() {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer != null && !this.featureService.isAllowed(viewer, JimMobileFeatures.CHAT)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.CHAT);
        }
        return null;
    }

    // --- Conversation list (DIRECT + GROUP, Sprint 08) ------------------------

    /**
     * Chat message search (mobile BFF). Global across the user's conversations when
     * {@code conversationId} is omitted, or scoped to one membership-checked
     * conversation. Server-side permission enforced; never changes read state.
     * {@code GET /rest/corbit-mobile/1.0/chat/search?q=&conversationId=&limit=&cursor=}
     */
    @GET
    @Path("/search")
    public Response searchMessages(@javax.ws.rs.QueryParam("q") String q,
                                   @javax.ws.rs.QueryParam("conversationId") Integer conversationId,
                                   @javax.ws.rs.QueryParam("limit") Integer limit,
                                   @javax.ws.rs.QueryParam("cursor") Integer cursor) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            Map<String, Object> body = this.messageService.searchMessages(
                    q, conversationId, limit == null ? 25 : limit.intValue(), cursor == null ? 0 : cursor.intValue());
            return JimRestResponses.okJson(body);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/chat/search", userKey, ex,
                    "internal_error", "An internal error occurred during search.");
        }
    }

    @GET
    @Path("/conversations")
    public Response listConversations() {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            this.presenceService.heartbeat(currentUserKey);
            List<JimConversation> all = this.conversationService.listConversationsForUser(currentUserKey);
            // Sprint 08 Fix-1: DIRECT + GROUP + PROJECT — the same set the web
            // sidebar shows (project chats the user is a member of are
            // group-type rows there). Only the SYSTEM/Assistant feed stays out.
            List<JimConversation> visible = new ArrayList<>();
            for (JimConversation conversation : all) {
                String type = conversation.getConversationType();
                if (JimConversationType.DIRECT.name().equals(type)
                        || JimConversationType.GROUP.name().equals(type)
                        || JimConversationType.PROJECT.name().equals(type)) {
                    visible.add(conversation);
                }
            }
            List<Map<String, Object>> items = this.restJsonMapper.toConversationMaps(
                    visible, currentUserKey, viewer, this.readStateService::getUnreadCount);
            rewriteConversationAvatars(visible, items, currentUserKey);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("conversations", items);
            return JimRestResponses.okJson(body);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/chat/conversations", userKey, ex,
                    "internal_error", "An internal error occurred while loading conversations.");
        }
    }

    // --- Create/open a DIRECT conversation ------------------------------------

    @POST
    @Path("/conversations/self")
    public Response getOrCreateSelfConversation() {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        if (!this.licenseService.canUseMessaging()) {
            return JimRestResponses.licenseBlocked();
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            // "Saved Messages": a direct conversation whose two sides are both
            // the current user. Allowed by policy (self short-circuit) and by
            // the conversation service (distinct-user rule relaxed).
            this.accessPolicyService.requireCanChatWith(currentUserKey, currentUserKey);
            JimConversation conversation =
                    this.conversationService.getOrCreateDirectConversation(currentUserKey, currentUserKey);
            this.permissionService.requireParticipant(conversation, currentUserKey);
            int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            Map<String, Object> convMap =
                    this.restJsonMapper.toConversationMap(conversation, currentUserKey, viewer, unreadCount);
            rewriteConversationAvatar(conversation, convMap, currentUserKey);
            return JimRestResponses.okJson(convMap);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/conversations/self", userKey, ex,
                    "internal_error", "An internal error occurred while opening Saved Messages.");
        }
    }

    @POST
    @Path("/conversations/direct")
    @Consumes({"application/json"})
    public Response createDirect(Map<String, Object> requestBody) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        String targetUserKey = str(requestBody, "targetUserKey");
        if (targetUserKey == null || targetUserKey.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "targetUserKey is required");
        }
        if (!this.licenseService.canUseMessaging()) {
            return JimRestResponses.licenseBlocked();
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            String normalizedTarget = targetUserKey.trim();
            this.accessPolicyService.requireCanChatWith(currentUserKey, normalizedTarget);
            JimConversation conversation =
                    this.conversationService.getOrCreateDirectConversation(currentUserKey, normalizedTarget);
            this.permissionService.requireParticipant(conversation, currentUserKey);
            int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            Map<String, Object> convMap =
                    this.restJsonMapper.toConversationMap(conversation, currentUserKey, viewer, unreadCount);
            rewriteConversationAvatar(conversation, convMap, currentUserKey);
            return JimRestResponses.okJson(convMap);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/conversations/direct", userKey, ex,
                    "internal_error", "An internal error occurred while creating the conversation.");
        }
    }

    // --- Jira Assistant (Sprint 08 Fix-2) --------------------------------------
    // The Assistant is the per-user SYSTEM conversation the web sidebar pins:
    // typed event cards (MENTION/ASSIGNMENT/…) with issue metadata and the
    // acknowledged ("actioned") state. Same services as the web; messages are
    // listed/marked-read through the existing conversation endpoints.

    @GET
    @Path("/assistant")
    public Response getAssistantConversation() {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation conversation =
                    this.conversationService.getOrCreateSystemConversation(currentUserKey);
            int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            Map<String, Object> convMap =
                    this.restJsonMapper.toConversationMap(conversation, currentUserKey, viewer, unreadCount);
            return JimRestResponses.okJson(convMap);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/chat/assistant", userKey, ex,
                    "internal_error", "An internal error occurred while loading the Jira Assistant.");
        }
    }

    /** Mark an Assistant card as acknowledged — mirrors POST /rest/jim/1.0/messages/{id}/action. */
    @POST
    @Path("/messages/{messageId}/action")
    public Response markMessageActioned(@PathParam("messageId") int messageId) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimMessage message = this.messageService.markActioned(messageId, currentUserKey);
            return okMessage(message, viewer, currentUserKey);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/messages/" + messageId + "/action",
                    userKey, ex, "internal_error", "An internal error occurred while marking the message as seen.");
        }
    }

    @POST
    @Path("/messages/{messageId}/schedule-approval")
    @Consumes({"application/json"})
    public Response scheduleApprovalDecision(@PathParam("messageId") int messageId, Map<String, Object> body) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
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
            return okMessage(actioned, viewer, currentUserKey);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/messages/" + messageId + "/schedule-approval",
                    userKey, ex, "internal_error", "An internal error occurred while processing the approval.");
        }
    }

    // --- Groups (Sprint 08) ----------------------------------------------------
    // Thin wrappers over the SAME JimGroupService the web /rest/jim/1.0/groups
    // resource uses: owner-only management, member limits, group event messages
    // and Access Policy rules are all enforced in the service layer.

    @POST
    @Path("/conversations/group")
    @Consumes({"application/json"})
    public Response createGroup(Map<String, Object> requestBody) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        if (!this.licenseService.canUseMessaging()) {
            return JimRestResponses.licenseBlocked();
        }
        String name = str(requestBody, "name");
        if (name == null || name.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "name is required");
        }
        List<String> memberKeys = strList(requestBody, "memberUserKeys");
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation conversation = this.groupService.createGroup(currentUserKey, name, memberKeys);
            int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            Map<String, Object> convMap =
                    this.restJsonMapper.toConversationMap(conversation, currentUserKey, viewer, unreadCount);
            return JimRestResponses.okJson(convMap);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/conversations/group", userKey, ex,
                    "internal_error", "An internal error occurred while creating the group.");
        }
    }

    @DELETE
    @Path("/conversations/{conversationId}/group")
    public Response deleteGroup(@PathParam("conversationId") int conversationId) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            this.groupService.deleteGroup(conversationId, currentUserKey);
            return JimRestResponses.okJson(JimRestResponses.singleEntry("ok", true));
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "DELETE /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/group",
                    userKey, ex, "internal_error", "An internal error occurred while deleting the group.");
        }
    }

    @GET
    @Path("/conversations/{conversationId}/members")
    public Response listGroupMembers(@PathParam("conversationId") int conversationId) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            List<JimGroupMember> members = this.groupService.listMembers(conversationId, currentUserKey);
            List<Map<String, Object>> memberMaps = this.restJsonMapper.toGroupMemberMaps(members, viewer);
            rewriteGroupMemberAvatars(memberMaps);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("members", memberMaps);
            return JimRestResponses.okJson(body);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/members",
                    userKey, ex, "internal_error", "An internal error occurred while loading group members.");
        }
    }

    @POST
    @Path("/conversations/{conversationId}/members")
    @Consumes({"application/json"})
    public Response addGroupMember(@PathParam("conversationId") int conversationId,
                                   Map<String, Object> requestBody) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        String targetUserKey = str(requestBody, "userKey");
        if (targetUserKey == null || targetUserKey.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "userKey is required");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            this.groupService.addMember(conversationId, currentUserKey, targetUserKey.trim());
            List<JimGroupMember> members = this.groupService.listMembers(conversationId, currentUserKey);
            List<Map<String, Object>> memberMaps = this.restJsonMapper.toGroupMemberMaps(members, viewer);
            rewriteGroupMemberAvatars(memberMaps);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("members", memberMaps);
            return JimRestResponses.okJson(body);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/members",
                    userKey, ex, "internal_error", "An internal error occurred while adding the group member.");
        }
    }

    @DELETE
    @Path("/conversations/{conversationId}/members/{memberUserKey}")
    public Response removeGroupMember(@PathParam("conversationId") int conversationId,
                                      @PathParam("memberUserKey") String memberUserKey) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            // Self-removal = leave group; owner-removal rules enforced in service.
            this.groupService.removeMember(conversationId, currentUserKey, memberUserKey);
            return JimRestResponses.okJson(JimRestResponses.singleEntry("ok", true));
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "DELETE /rest/corbit-mobile/1.0/chat/conversations/" + conversationId
                            + "/members/" + memberUserKey,
                    userKey, ex, "internal_error", "An internal error occurred while removing the group member.");
        }
    }

    /**
     * Promote/demote a group member (Phase 1). Body {@code {"role":"ADMIN"|"MEMBER"}}.
     * OWNER-only, enforced in the service. Mirrors
     * {@code POST /rest/jim/1.0/groups/{id}/members/{userKey}/role}.
     */
    @POST
    @Path("/conversations/{conversationId}/members/{memberUserKey}/role")
    @Consumes({"application/json"})
    public Response setMemberRole(@PathParam("conversationId") int conversationId,
                                  @PathParam("memberUserKey") String memberUserKey,
                                  Map<String, Object> requestBody) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        String role = str(requestBody, "role");
        role = role == null ? "" : role.trim().toUpperCase();
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
            List<Map<String, Object>> memberMaps = this.restJsonMapper.toGroupMemberMaps(members, viewer);
            rewriteGroupMemberAvatars(memberMaps);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("members", memberMaps);
            return JimRestResponses.okJson(body);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/conversations/" + conversationId
                            + "/members/" + memberUserKey + "/role",
                    userKey, ex, "internal_error", "An internal error occurred while updating the member role.");
        }
    }

    /** Rename a group/project chat (OWNER/ADMIN). Mirrors {@code POST /rest/jim/1.0/groups/{id}/rename}. */
    @POST
    @Path("/conversations/{conversationId}/rename")
    @Consumes({"application/json"})
    public Response renameGroup(@PathParam("conversationId") int conversationId,
                                Map<String, Object> requestBody) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        String name = str(requestBody, "name");
        if (name == null || name.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "name is required");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation conversation = this.groupService.renameGroup(conversationId, currentUserKey, name);
            int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            Map<String, Object> convMap =
                    this.restJsonMapper.toConversationMap(conversation, currentUserKey, viewer, unreadCount);
            rewriteConversationAvatar(conversation, convMap, currentUserKey);
            return JimRestResponses.okJson(convMap);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/rename",
                    userKey, ex, "internal_error", "An internal error occurred while renaming the group.");
        }
    }

    /** Upload a group/project custom avatar (OWNER/ADMIN). Multipart field {@code file}. */
    @POST
    @Path("/conversations/{conversationId}/avatar")
    @Produces({"application/json"})
    public Response uploadAvatar(@PathParam("conversationId") int conversationId,
                                 @Context HttpServletRequest request) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            File tempDirectory = this.avatarStorageService.createUploadTempDirectory();
            JimMultipartParser.ParsedMultipartForm form = JimMultipartParser.parse(request, tempDirectory);
            JimConversation conversation = this.groupService.uploadAvatar(conversationId, currentUserKey,
                    form.getFile(), form.getContentType(), form.getOriginalFilename());
            int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            Map<String, Object> convMap =
                    this.restJsonMapper.toConversationMap(conversation, currentUserKey, viewer, unreadCount);
            rewriteConversationAvatar(conversation, convMap, currentUserKey);
            return JimRestResponses.okJson(convMap);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/avatar",
                    userKey, ex, "internal_error", "An internal error occurred while uploading the avatar.");
        }
    }

    /** Reset a group/project custom avatar (OWNER/ADMIN). */
    @DELETE
    @Path("/conversations/{conversationId}/avatar")
    @Produces({"application/json"})
    public Response deleteAvatar(@PathParam("conversationId") int conversationId) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation conversation = this.groupService.resetAvatar(conversationId, currentUserKey);
            int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            Map<String, Object> convMap =
                    this.restJsonMapper.toConversationMap(conversation, currentUserKey, viewer, unreadCount);
            rewriteConversationAvatar(conversation, convMap, currentUserKey);
            return JimRestResponses.okJson(convMap);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "DELETE /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/avatar",
                    userKey, ex, "internal_error", "An internal error occurred while resetting the avatar.");
        }
    }

    /**
     * The official project chat for a project (Sprint 08 Fix-1) — mirrors
     * {@code GET /rest/jim/1.0/projects/{key}/conversation}: Browse-Project
     * gated, ensures the PROJECT conversation exists (lead = OWNER member),
     * and reports membership so the client can show the same access notice
     * the web shows to non-members. Never exposes messages to non-members —
     * the message endpoints all run their own participant checks.
     */
    @GET
    @Path("/projects/{projectKey}/conversation")
    public Response getProjectConversation(@PathParam("projectKey") String projectKey) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            com.atlassian.jira.project.Project project =
                    com.atlassian.jira.component.ComponentAccessor.getProjectManager()
                            .getProjectObjByKey(projectKey != null ? projectKey.trim() : null);
            if (project == null
                    || !com.atlassian.jira.component.ComponentAccessor.getPermissionManager().hasPermission(
                            com.atlassian.jira.permission.ProjectPermissions.BROWSE_PROJECTS, project, viewer)) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Project not found or you do not have permission to view it");
            }
            JimConversation conversation = this.projectChatService.ensureProjectConversation(project);
            if (conversation == null) {
                return JimRestResponses.errorJson(500, "internal_error",
                        "Unable to load the project chat");
            }
            boolean isMember = this.permissionService.isParticipant(conversation, currentUserKey);
            boolean isLead = this.projectChatService.isProjectLead(conversation, currentUserKey);
            int unreadCount = isMember
                    ? this.readStateService.getUnreadCount(conversation.getID(), currentUserKey)
                    : 0;
            ApplicationUser lead = project.getProjectLead();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("conversation",
                    this.restJsonMapper.toConversationMap(conversation, currentUserKey, viewer, unreadCount));
            payload.put("isMember", isMember);
            payload.put("isLead", isLead);
            payload.put("leadDisplayName", lead != null ? lead.getDisplayName() : null);
            return JimRestResponses.okJson(payload);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/chat/projects/" + projectKey + "/conversation",
                    userKey, ex, "internal_error", "An internal error occurred while loading the project chat.");
        }
    }

    /**
     * Per-member read receipts for the sender's own message in a group —
     * mirrors {@code GET /rest/jim/1.0/conversations/{id}/messages/{mid}/receipts}
     * including its rules (groups only, own messages only).
     */
    @GET
    @Path("/conversations/{conversationId}/messages/{messageId}/receipts")
    public Response getMessageReceipts(@PathParam("conversationId") int conversationId,
                                       @PathParam("messageId") int messageId) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            JimConversation conversation =
                    this.conversationService.getConversationForUser(conversationId, currentUserKey);
            String type = conversation.getConversationType();
            if (!JimConversationType.GROUP.name().equals(type)
                    && !JimConversationType.PROJECT.name().equals(type)) {
                return JimRestResponses.errorJson(400, "bad_request",
                        "Message info is only available in group conversations");
            }
            JimMessage message = this.messageService.getMessageForParticipant(messageId, currentUserKey);
            if (message.getConversationId() != conversationId) {
                return JimRestResponses.errorJson(400, "bad_request",
                        "Message does not belong to this conversation");
            }
            if (!currentUserKey.equals(message.getSenderUserKey())) {
                return JimRestResponses.errorJson(403, "forbidden",
                        "Message info is only available for your own messages");
            }
            List<Map<String, Object>> receipts =
                    this.restJsonMapper.toMessageReceiptMaps(conversation, message, currentUserKey);
            rewriteGroupMemberAvatars(receipts);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("receipts", receipts);
            return JimRestResponses.okJson(body);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/chat/conversations/" + conversationId
                            + "/messages/" + messageId + "/receipts",
                    userKey, ex, "internal_error", "An internal error occurred while loading message info.");
        }
    }

    @GET
    @Path("/conversations/{conversationId}/shared")
    public Response listSharedContent(@PathParam("conversationId") int conversationId,
                                      @QueryParam("type") @DefaultValue("files") String type,
                                      @QueryParam("cursor") Integer cursor,
                                      @QueryParam("limit") @DefaultValue("25") int limit) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            Map<String, Object> body = this.sharedContentService.listShared(
                    conversationId, currentUserKey, type, cursor, Integer.valueOf(limit));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
            if (items != null) {
                for (Map<String, Object> item : items) {
                    Object preview = item.get("previewUrl");
                    Object download = item.get("downloadUrl");
                    if (preview instanceof String) {
                        item.put("previewUrl", ((String) preview).replace(
                                "/rest/jim/1.0/attachments/",
                                "/rest/corbit-mobile/1.0/chat/attachments/"));
                    }
                    if (download instanceof String) {
                        item.put("downloadUrl", ((String) download).replace(
                                "/rest/jim/1.0/attachments/",
                                "/rest/corbit-mobile/1.0/chat/attachments/"));
                    }
                }
            }
            return JimRestResponses.okJson(body);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/shared",
                    userKey, ex, "internal_error", "An internal error occurred while loading shared content.");
        }
    }

    // --- Messages: list (paginated) -------------------------------------------

    @GET
    @Path("/conversations/{conversationId}/messages")
    public Response listMessages(@PathParam("conversationId") int conversationId,
                                 @QueryParam("limit") @DefaultValue("50") int limit,
                                 @QueryParam("beforeMessageId") Integer beforeMessageId,
                                 @QueryParam("since") Long since) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            this.presenceService.heartbeatConversation(currentUserKey, conversationId);
            JimConversation conversation =
                    this.conversationService.getConversationForUser(conversationId, currentUserKey);
            List<JimMessage> messages =
                    this.messageService.listMessages(conversationId, limit, beforeMessageId, since);
            // hasOlder must be derived from the RAW fetched batch (before the
            // per-user "Delete for me" hidden filter below). Otherwise, if every
            // item on this page is hidden for the viewer, the visible list would
            // be empty and hasOlder would collapse to false — leaving the client
            // unable to auto-load older pages and the feed stuck empty. Capturing
            // the raw oldest id here keeps the "older exists" signal correct so
            // the client can page back to real visible items.
            Integer rawOldestId = messages.isEmpty() ? null : Integer.valueOf(messages.get(0).getID());
            // Sprint 12: drop messages the viewer chose "Delete for me".
            java.util.Set<Integer> hidden =
                    this.userStateService.hiddenMessageIds(currentUserKey, conversationId);
            if (!hidden.isEmpty()) {
                List<JimMessage> visible = new ArrayList<>();
                for (JimMessage m : messages) {
                    if (!hidden.contains(m.getID())) {
                        visible.add(m);
                    }
                }
                messages = visible;
            }
            List<Map<String, Object>> messageMaps =
                    this.restJsonMapper.toMessageMaps(messages, viewer, conversation, currentUserKey);
            rewriteMessageAvatars(messageMaps);
            rewriteMessageAttachments(messageMaps);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messages", messageMaps);
            // Whether older messages exist before the earliest RAW-returned one,
            // so the client can keep infinite-scroll / auto-load-older enabled
            // even when this page produced no visible items after hidden filtering.
            boolean hasOlder = rawOldestId != null
                    && this.messageService.hasMessagesBefore(conversationId, rawOldestId.intValue());
            body.put("hasOlder", hasOlder);
            // Ephemeral typing/recording/uploading of other participants (TTL).
            body.put("activities", activitiesFor(conversationId, currentUserKey));
            // Server clock for client-side edit-window anchoring (no wall-clock reliance).
            body.put("serverNowMs", System.currentTimeMillis());
            return JimRestResponses.okJson(body);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/messages",
                    userKey, ex, "internal_error", "An internal error occurred while loading messages.");
        }
    }

    /** Poll other participants' current ephemeral activity (typing/recording/uploading). */
    @GET
    @Path("/conversations/{conversationId}/activity")
    public Response getActivity(@PathParam("conversationId") int conversationId) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            this.conversationService.getConversationForUser(conversationId, currentUserKey);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("activities", activitiesFor(conversationId, currentUserKey));
            return JimRestResponses.okJson(body);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/activity",
                    userKey, ex, "internal_error", "Unable to load activity.");
        }
    }

    /**
     * Record the caller's ephemeral activity (typing/recording/uploading) in a
     * conversation. Refreshes a short TTL; not a message. Only participants may
     * post. An empty/absent activity clears it (on send/leave/blur).
     */
    @POST
    @Path("/conversations/{conversationId}/activity")
    @Consumes({"application/json"})
    public Response postActivity(@PathParam("conversationId") int conversationId,
                                 Map<String, Object> requestBody) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            // Participant check (throws if not a participant).
            this.conversationService.getConversationForUser(conversationId, currentUserKey);
            String activity = requestBody == null ? null : String.valueOf(requestBody.get("activity"));
            boolean clear = requestBody != null && Boolean.TRUE.equals(requestBody.get("clear"));
            if (clear || activity == null || "null".equals(activity) || activity.trim().isEmpty()) {
                this.activityService.clear(currentUserKey, conversationId);
            } else {
                this.activityService.record(currentUserKey, conversationId, activity);
            }
            return JimRestResponses.okJson(JimRestResponses.singleEntry("ok", true));
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/activity",
                    userKey, ex, "internal_error", "Unable to update activity.");
        }
    }

    // --- Messages: send text --------------------------------------------------

    @POST
    @Path("/conversations/{conversationId}/messages")
    @Consumes({"application/json"})
    public Response sendMessage(@PathParam("conversationId") int conversationId,
                                Map<String, Object> requestBody) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        String body = str(requestBody, "body");
        String issueKey = str(requestBody, "issueKey");
        issueKey = issueKey == null ? "" : issueKey.trim();
        // Parity with web: an issue-link message may have no text body.
        if ((body == null || body.trim().isEmpty()) && issueKey.isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "body is required");
        }
        if (!this.licenseService.canUseMessaging()) {
            return JimRestResponses.licenseBlocked();
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation conversation =
                    this.conversationService.getConversationForUser(conversationId, currentUserKey);
            enforceDirectChatPolicy(conversation, currentUserKey);
            Long replyToMessageId = asLong(requestBody, "replyToMessageId");
            JimMessage message;
            if (!issueKey.isEmpty()) {
                // Attach a Jira issue as a card. Permission-checked against the
                // SENDER; the linked issue is re-checked per VIEWER at render time.
                com.atlassian.jira.issue.MutableIssue issue =
                        com.atlassian.jira.component.ComponentAccessor.getIssueManager().getIssueByCurrentKey(issueKey);
                if (issue == null || !com.atlassian.jira.component.ComponentAccessor.getPermissionManager()
                        .hasPermission(com.atlassian.jira.permission.ProjectPermissions.BROWSE_PROJECTS,
                                (com.atlassian.jira.issue.Issue) issue, viewer)) {
                    return JimRestResponses.errorJson(404, "not_found",
                            "Issue not found or you do not have permission to view it");
                }
                String issueUrl = com.corbitlogic.jira.internalmessenger.util.JimIssueUrlBuilder.buildBrowseUrl(
                        com.atlassian.jira.component.ComponentAccessor.getApplicationProperties(),
                        (com.atlassian.jira.issue.Issue) issue);
                message = this.messageService.sendIssueLinkMessage(conversationId, currentUserKey,
                        issue.getKey(), issue.getSummary(), issueUrl, body);
            } else {
                message = this.messageService.sendUserMessage(conversationId, currentUserKey, body, replyToMessageId);
            }
            Map<String, Object> messageMap =
                    this.restJsonMapper.toMessageMap(message, viewer, conversation, currentUserKey);
            rewriteMessageAvatar(messageMap);
            rewriteMessageAttachment(messageMap);
            return JimRestResponses.okJson(messageMap);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/messages",
                    userKey, ex, "internal_error", "An internal error occurred while sending the message.");
        }
    }

    // --- Attachments (Sprint 07 Fix-1) ----------------------------------------
    // Upload/download/preview through the BFF so the mobile session filter (which
    // only covers /rest/corbit-mobile/1.0/*) authenticates them. The legacy web
    // endpoints at /rest/jim/1.0/* require Jira-native auth (PAT/cookie) and 401
    // for username/password mobile sessions, which the client surfaced as a false
    // "session expired". Same services, participant checks, direct-chat policy,
    // allow-list, size limit and license gate as the web surface.

    @POST
    @Path("/conversations/{conversationId}/attachments")
    @Produces({"application/json"})
    public Response uploadAttachment(@PathParam("conversationId") int conversationId,
                                     @Context HttpServletRequest request) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        if (!this.licenseService.canUploadAttachments()) {
            return JimRestResponses.licenseBlocked();
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation conversation =
                    this.conversationService.getConversationForUser(conversationId, currentUserKey);
            enforceDirectChatPolicy(conversation, currentUserKey);
            File tempDirectory = this.attachmentStorageService.createUploadTempDirectory();
            JimMultipartParser.ParsedMultipartForm form = JimMultipartParser.parse(request, tempDirectory);
            JimAttachmentService.UploadResult result = this.attachmentService.uploadAttachment(
                    conversationId, currentUserKey, form.getBody(), form.getFile(),
                    form.getContentType(), form.getOriginalFilename(), form.getVoice());
            return okMessage(result.getCreatedMessage(), viewer, currentUserKey);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/attachments",
                    userKey, ex, "internal_error", "An internal error occurred while uploading the attachment.");
        }
    }

    @GET
    @Path("/attachments/{attachmentId}/download")
    public Response downloadAttachment(@PathParam("attachmentId") int attachmentId,
                                       @HeaderParam("Range") String rangeHeader) {
        return streamAttachment(attachmentId, false, rangeHeader);
    }

    @GET
    @Path("/attachments/{attachmentId}/preview")
    public Response previewAttachment(@PathParam("attachmentId") int attachmentId,
                                      @HeaderParam("Range") String rangeHeader) {
        return streamAttachment(attachmentId, true, rangeHeader);
    }

    private Response streamAttachment(int attachmentId, boolean inlinePreview, String rangeHeader) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            this.permissionService.requireAuthenticatedUserKey();
            // getAttachmentForUser enforces the participant permission check.
            JimAttachment attachment = this.attachmentService.getAttachmentForUser(attachmentId, userKey);
            File file = this.attachmentStorageService.resolveAttachmentFile(attachment);
            return JimAttachmentStreaming.stream(attachment, file, inlinePreview, rangeHeader);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/chat/attachments/" + attachmentId
                            + (inlinePreview ? "/preview" : "/download"),
                    userKey, ex, "internal_error", "An internal error occurred while loading the attachment.");
        }
    }

    // --- Jira Assistant issue-attachment download (Sprint 12 enrichment) ------
    // Serves a Jira issue attachment referenced by an Assistant mention/
    // attachment card over the session-authenticated chat path. Gated on the
    // CHAT feature (not issueDetail): a user mentioned on an issue can open the
    // attached file as long as they can BROWSE the issue. Jira browse +
    // attachment-belongs-to-issue are enforced server-side on every request.

    @GET
    @Path("/issue-attachments/{issueKey}/{attachmentId}/download")
    public Response downloadAssistantIssueAttachment(@PathParam("issueKey") String issueKey,
                                                     @PathParam("attachmentId") long attachmentId,
                                                     @HeaderParam("Range") String rangeHeader) {
        return streamAssistantIssueAttachment(issueKey, attachmentId, false, rangeHeader);
    }

    @GET
    @Path("/issue-attachments/{issueKey}/{attachmentId}/preview")
    public Response previewAssistantIssueAttachment(@PathParam("issueKey") String issueKey,
                                                    @PathParam("attachmentId") long attachmentId,
                                                    @HeaderParam("Range") String rangeHeader) {
        return streamAssistantIssueAttachment(issueKey, attachmentId, true, rangeHeader);
    }

    private Response streamAssistantIssueAttachment(String issueKey, long attachmentId,
                                                    boolean inlinePreview, String rangeHeader) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "issueKey is required.");
        }
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        try {
            MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(issueKey.trim());
            if (issue == null || !ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue) issue, viewer)) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            Attachment attachment = ComponentAccessor.getAttachmentManager().getAttachment(attachmentId);
            if (attachment == null || attachment.getIssue() == null
                    || !issue.getId().equals(attachment.getIssue().getId())) {
                return JimRestResponses.errorJson(404, "not_found", "Attachment not found.");
            }
            File file = AttachmentUtils.getAttachmentFile(attachment);
            if (file == null || !file.exists()) {
                return JimRestResponses.errorJson(404, "not_found", "Attachment file not found.");
            }
            String contentType = attachment.getMimetype() != null
                    ? attachment.getMimetype() : "application/octet-stream";
            String ctl = contentType.toLowerCase(java.util.Locale.ROOT);
            boolean inlineOk = ctl.startsWith("image/") || ctl.startsWith("audio/") || ctl.startsWith("video/");
            String disposition = (inlinePreview && inlineOk) ? "inline" : "attachment";
            return JimAttachmentStreaming.stream(file, contentType,
                    attachment.getFilename(), disposition, rangeHeader);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/chat/issue-attachments/" + issueKey + "/" + attachmentId,
                    userKey, ex, "internal_error",
                    "An internal error occurred while loading the attachment.");
        }
    }

    // --- Mark read ------------------------------------------------------------

    @POST
    @Path("/conversations/{conversationId}/read")
    @Consumes({"application/json"})
    public Response markRead(@PathParam("conversationId") int conversationId) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            this.readStateService.markConversationRead(conversationId, currentUserKey);
            return JimRestResponses.okJson(JimRestResponses.singleEntry("ok", true));
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/read",
                    userKey, ex, "internal_error", "An internal error occurred while marking the conversation as read.");
        }
    }

    // --- Message actions (Sprint 06): edit / delete / pin / react -------------
    // Thin wrappers over the SAME services the web surface uses, so every rule
    // (edit 30-min window, delete 10-min soft-delete, pin = one per conversation,
    // reaction allowlist, participant/ownership checks) is enforced server-side.
    // The mobile session token only authenticates /rest/corbit-mobile/1.0/*, so
    // these must live here rather than the client calling /rest/jim/1.0/messages.

    @PUT
    @Path("/messages/{messageId}")
    @Consumes({"application/json"})
    public Response editMessage(@PathParam("messageId") int messageId, Map<String, Object> requestBody) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        String body = str(requestBody, "body");
        if (body == null || body.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "body is required");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimMessage message = this.messageService.editUserMessage(messageId, currentUserKey, body);
            return okMessage(message, viewer, currentUserKey);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "PUT /rest/corbit-mobile/1.0/chat/messages/" + messageId, userKey, ex,
                    "internal_error", "An internal error occurred while editing the message.");
        }
    }

    @DELETE
    @Path("/messages/{messageId}")
    public Response deleteMessage(@PathParam("messageId") int messageId) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimMessage message = this.messageService.deleteUserMessage(messageId, currentUserKey);
            return okMessage(message, viewer, currentUserKey);
        } catch (JimMessengerException ex) {
            if (ex.getStatusCode() == 403 && "Messages can only be deleted within 10 minutes.".equals(ex.getMessage())) {
                return JimRestResponses.errorJson(403, "delete_window_expired", ex.getMessage());
            }
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "DELETE /rest/corbit-mobile/1.0/chat/messages/" + messageId, userKey, ex,
                    "internal_error", "An internal error occurred while deleting the message.");
        }
    }

    /** Delete-for-me: hide a single message for the current user only (Sprint 12). */
    @POST
    @Path("/messages/{messageId}/hide")
    public Response hideMessage(@PathParam("messageId") int messageId) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
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
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/messages/" + messageId + "/hide", userKey, ex,
                    "internal_error", "An internal error occurred while hiding the message.");
        }
    }

    /** Delete-for-me in bulk: hide several messages for the current user only. */
    @POST
    @Path("/messages/hide")
    @Consumes({"application/json"})
    public Response hideMessages(Map<String, Object> requestBody) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Object idsRaw = requestBody == null ? null : requestBody.get("ids");
        if (!(idsRaw instanceof List)) {
            return JimRestResponses.errorJson(400, "bad_request", "ids array is required");
        }
        try {
            List<Integer> ids = new ArrayList<>();
            int conversationId = -1;
            for (Object o : (List<?>) idsRaw) {
                int id = o instanceof Number ? ((Number) o).intValue()
                        : Integer.parseInt(String.valueOf(o));
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
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/messages/hide", userKey, ex,
                    "internal_error", "An internal error occurred while hiding messages.");
        }
    }

    @POST
    @Path("/messages/{messageId}/pin")
    public Response pinMessage(@PathParam("messageId") int messageId) {
        return setPinned(messageId, true);
    }

    @DELETE
    @Path("/messages/{messageId}/pin")
    public Response unpinMessage(@PathParam("messageId") int messageId) {
        return setPinned(messageId, false);
    }

    private Response setPinned(int messageId, boolean pinned) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimMessage message = this.messageService.setPinned(messageId, currentUserKey, pinned);
            return okMessage(message, viewer, currentUserKey);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    (pinned ? "POST" : "DELETE") + " /rest/corbit-mobile/1.0/chat/messages/" + messageId + "/pin",
                    userKey, ex, "internal_error", "An internal error occurred while updating the pinned message.");
        }
    }

    @GET
    @Path("/conversations/{conversationId}/pinned")
    public Response getPinnedMessage(@PathParam("conversationId") int conversationId) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation conversation =
                    this.conversationService.getConversationForUser(conversationId, currentUserKey);
            JimMessage pinned = this.messageService.getPinnedMessage(conversationId, currentUserKey);
            Map<String, Object> body = new LinkedHashMap<>();
            if (pinned == null) {
                body.put("message", null);
            } else {
                Map<String, Object> messageMap =
                        this.restJsonMapper.toMessageMap(pinned, viewer, conversation, currentUserKey);
                rewriteMessageAvatar(messageMap);
                rewriteMessageAttachment(messageMap);
                body.put("message", messageMap);
            }
            return JimRestResponses.okJson(body);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/pinned",
                    userKey, ex, "internal_error", "An internal error occurred while loading the pinned message.");
        }
    }

    @POST
    @Path("/messages/{messageId}/forward")
    @Consumes({"application/json"})
    public Response forwardMessage(@PathParam("messageId") int messageId, Map<String, Object> requestBody) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        Long targetConversationId = asLong(requestBody, "targetConversationId");
        if (targetConversationId == null || targetConversationId <= 0) {
            return JimRestResponses.errorJson(400, "bad_request", "targetConversationId is required");
        }
        if (!this.licenseService.canUseMessaging()) {
            return JimRestResponses.licenseBlocked();
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            JimConversation target = this.conversationService.getConversationForUser(
                    targetConversationId.intValue(), currentUserKey);
            enforceDirectChatPolicy(target, currentUserKey);
            JimMessage message = this.messageService.forwardUserMessage(
                    targetConversationId.intValue(), currentUserKey, messageId);
            return okMessage(message, viewer, currentUserKey);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/messages/" + messageId + "/forward",
                    userKey, ex, "internal_error", "An internal error occurred while forwarding the message.");
        }
    }

    @POST
    @Path("/messages/{messageId}/reactions")
    @Consumes({"application/json"})
    public Response toggleReaction(@PathParam("messageId") int messageId, Map<String, Object> requestBody) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        String emoji = str(requestBody, "emoji");
        if (emoji == null || emoji.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "emoji is required");
        }
        try {
            String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
            ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
            this.reactionService.toggleReaction(messageId, currentUserKey, emoji.trim());
            JimMessage message = this.messageService.getMessageForParticipant(messageId, currentUserKey);
            return okMessage(message, viewer, currentUserKey);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/messages/" + messageId + "/reactions",
                    userKey, ex, "internal_error", "An internal error occurred while updating the reaction.");
        }
    }

    /**
     * Loads the message's conversation (participant-checked) and returns the
     * shared message JSON with the mobile avatar-proxy rewrite applied.
     */
    private Response okMessage(JimMessage message, ApplicationUser viewer, String currentUserKey) {
        JimConversation conversation =
                this.conversationService.getConversationForUser(message.getConversationId(), currentUserKey);
        Map<String, Object> messageMap =
                this.restJsonMapper.toMessageMap(message, viewer, conversation, currentUserKey);
        rewriteMessageAvatar(messageMap);
        rewriteMessageAttachment(messageMap);
        return JimRestResponses.okJson(messageMap);
    }

    // --- User search (start a chat) -------------------------------------------

    @GET
    @Path("/users/search")
    public Response searchUsers(@QueryParam("query") String query) {
        String userKey = resolveCurrentUserKey();
        if (userKey == null) {
            return unauthenticated();
        }
        Response featureBlocked = chatFeatureBlocked();
        if (featureBlocked != null) {
            return featureBlocked;
        }
        if (query == null) {
            return JimRestResponses.errorJson(400, "bad_request", "query is required");
        }
        if (!this.licenseService.canUseMessaging()) {
            return JimRestResponses.licenseBlocked();
        }
        try {
            this.permissionService.requireAuthenticatedUserKey();
            List<UserSearchResultDto> users = this.userSearchService.searchActiveUsers(query);
            List<Map<String, Object>> userMaps = this.restJsonMapper.toUserSearchMaps(users);
            rewriteUserSearchAvatars(userMaps);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("users", userMaps);
            return JimRestResponses.okJson(body);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/chat/users/search", userKey, ex,
                    "internal_error", "An internal error occurred while searching users.");
        }
    }

    // --- Avatar proxy rewriting (Sprint 04C) ----------------------------------
    // The shared web mapper emits absolute Jira/gravatar avatar URLs built from
    // Jira's configured base URL, which the mobile client often cannot reach.
    // We rewrite them to relative BFF avatar-proxy paths using the user keys the
    // resource already holds — WITHOUT touching the shared /rest/jim mapper.

    private void rewriteConversationAvatars(List<JimConversation> conversations,
                                            List<Map<String, Object>> items,
                                            String currentUserKey) {
        if (conversations == null || items == null) {
            return;
        }
        int n = Math.min(conversations.size(), items.size());
        for (int i = 0; i < n; i++) {
            rewriteConversationAvatar(conversations.get(i), items.get(i), currentUserKey);
        }
    }

    private void rewriteConversationAvatar(JimConversation conversation,
                                           Map<String, Object> item,
                                           String currentUserKey) {
        if (conversation == null || item == null) {
            return;
        }
        String type = conversation.getConversationType();
        // GROUP/PROJECT: when a custom chat avatar is set, point the mobile client
        // at the session-authenticated BFF conversation-avatar proxy (Phase 1).
        // Otherwise leave avatarUrl null so the client renders a group glyph.
        if (JimConversationType.GROUP.name().equals(type)
                || JimConversationType.PROJECT.name().equals(type)) {
            if (conversation.getAvatarStoragePath() != null
                    && !conversation.getAvatarStoragePath().trim().isEmpty()) {
                long version = conversation.getAvatarVersion() == null ? 0L : conversation.getAvatarVersion();
                item.put("avatarUrl", JimMobileAvatars.conversationPath(conversation.getID()) + "?v=" + version);
            } else {
                item.put("avatarUrl", null);
            }
            return;
        }
        if (!JimConversationType.DIRECT.name().equals(type)) {
            return;
        }
        String other = currentUserKey != null && currentUserKey.equals(conversation.getUserAKey())
                ? conversation.getUserBKey()
                : conversation.getUserAKey();
        item.put("avatarUrl", JimMobileAvatars.userPath(other));
    }

    private void rewriteMessageAvatars(List<Map<String, Object>> messages) {
        if (messages == null) {
            return;
        }
        for (Map<String, Object> m : messages) {
            rewriteMessageAvatar(m);
        }
    }

    private void rewriteMessageAvatar(Map<String, Object> message) {
        if (message == null) {
            return;
        }
        Object type = message.get("senderType");
        Object key = message.get("senderUserKey");
        if ("USER".equals(String.valueOf(type)) && key != null) {
            message.put("senderAvatarUrl", JimMobileAvatars.userPath(key.toString()));
        }
    }

    // --- Attachment URL rewriting (Sprint 07 Fix-1) ---------------------------
    // The shared mapper emits web attachment URLs (/rest/jim/1.0/attachments/..)
    // which the mobile session token does NOT authenticate. Rewrite them to the
    // session-aware BFF paths in mobile responses only — the shared /rest/jim
    // mapper is untouched, so the web surface keeps its own URLs.

    private void rewriteMessageAttachments(List<Map<String, Object>> messages) {
        if (messages == null) {
            return;
        }
        for (Map<String, Object> m : messages) {
            rewriteMessageAttachment(m);
        }
    }

    @SuppressWarnings("unchecked")
    private void rewriteMessageAttachment(Map<String, Object> message) {
        if (message == null) {
            return;
        }
        Object atts = message.get("attachments");
        if (atts instanceof List) {
            for (Object o : (List<Object>) atts) {
                if (!(o instanceof Map)) {
                    continue;
                }
                Map<String, Object> a = (Map<String, Object>) o;
                Object id = a.get("id");
                if (id == null) {
                    continue;
                }
                if (a.get("downloadUrl") != null) {
                    a.put("downloadUrl", "/rest/corbit-mobile/1.0/chat/attachments/" + id + "/download");
                }
                if (a.get("previewUrl") != null) {
                    a.put("previewUrl", "/rest/corbit-mobile/1.0/chat/attachments/" + id + "/preview");
                }
            }
        }
        rewriteIssueAttachments(message);
    }

    /**
     * Rewrite Jira Assistant issue-attachment links (from the shared mapper's
     * {@code /secure/attachment/*} form, which needs a Jira cookie) to the
     * session-authenticated mobile proxy the app can actually reach. The proxy
     * re-checks browse + attachment permission server-side.
     */
    @SuppressWarnings("unchecked")
    private void rewriteIssueAttachments(Map<String, Object> message) {
        if (message == null) {
            return;
        }
        Object issueAtts = message.get("issueAttachments");
        if (!(issueAtts instanceof List)) {
            return;
        }
        Object issueKeyObj = message.get("issueKey");
        if (issueKeyObj == null) {
            return;
        }
        String base = "/rest/corbit-mobile/1.0/chat/issue-attachments/" + issueKeyObj + "/";
        for (Object o : (List<Object>) issueAtts) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> a = (Map<String, Object>) o;
            Object id = a.get("id");
            if (id == null) {
                continue;
            }
            if (a.get("downloadUrl") != null) {
                a.put("downloadUrl", base + id + "/download");
            }
            if (a.get("previewUrl") != null) {
                a.put("previewUrl", base + id + "/preview");
            }
        }
    }

    private void rewriteUserSearchAvatars(List<Map<String, Object>> users) {
        if (users == null) {
            return;
        }
        for (Map<String, Object> u : users) {
            if (u == null) {
                continue;
            }
            Object key = u.get("userKey");
            if (key != null) {
                u.put("avatarUrl", JimMobileAvatars.userPath(key.toString()));
            }
        }
    }

    /** Group member / receipt maps carry a userKey — same avatar-proxy rewrite. */
    private void rewriteGroupMemberAvatars(List<Map<String, Object>> members) {
        rewriteUserSearchAvatars(members);
    }

    // --- Helpers (mirror JimConversationResource semantics) -------------------

    private void enforceDirectChatPolicy(JimConversation conversation, String currentUserKey) {
        if (!this.accessPolicyService.isChatEnabled()) {
            throw JimMessengerException.forbidden("Chat has been disabled by the administrator");
        }
        if (conversation == null || !this.permissionService.isDirectConversation(conversation)) {
            return;
        }
        String otherKey = currentUserKey.equals(conversation.getUserAKey())
                ? conversation.getUserBKey()
                : conversation.getUserAKey();
        if (otherKey != null && !otherKey.equals(currentUserKey)) {
            this.accessPolicyService.requireCanChatWith(currentUserKey, otherKey);
        }
    }

    private String resolveCurrentUserKey() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        return user != null ? user.getKey() : null;
    }

    private static Response unauthenticated() {
        return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                "You must be signed in to use CorbitChat Mobile.");
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private static List<String> strList(Map<String, Object> map, String key) {
        List<String> out = new ArrayList<>();
        if (map == null) {
            return out;
        }
        Object value = map.get(key);
        if (value instanceof List) {
            for (Object o : (List<?>) value) {
                if (o != null && !o.toString().trim().isEmpty()) {
                    out.add(o.toString().trim());
                }
            }
        }
        return out;
    }

    private static Long asLong(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
