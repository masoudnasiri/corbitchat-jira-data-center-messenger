package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.dto.UserSearchResultDto;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileAvatars;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.model.JimConversationType;
import com.corbitlogic.jira.internalmessenger.rest.JimRestJsonMapper;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimAccessPolicyService;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimLicenseService;
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimMobileFeatureService;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimPresenceService;
import com.corbitlogic.jira.internalmessenger.service.JimReactionService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import com.corbitlogic.jira.internalmessenger.service.JimUserSearchService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
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
 * <p>Scope is 1:1 DIRECT chat only; group/project/system feeds are filtered out
 * of the conversation list and issue-link sends are not exposed here.</p>
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
                                    JimMobileFeatureService featureService) {
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

    // --- Conversation list (DIRECT only) --------------------------------------

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
            List<JimConversation> direct = new ArrayList<>();
            for (JimConversation conversation : all) {
                if (JimConversationType.DIRECT.name().equals(conversation.getConversationType())) {
                    direct.add(conversation);
                }
            }
            List<Map<String, Object>> items = this.restJsonMapper.toConversationMaps(
                    direct, currentUserKey, viewer, this.readStateService::getUnreadCount);
            rewriteConversationAvatars(direct, items, currentUserKey);
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

    // --- Messages: list (paginated) -------------------------------------------

    @GET
    @Path("/conversations/{conversationId}/messages")
    public Response listMessages(@PathParam("conversationId") int conversationId,
                                 @QueryParam("limit") @DefaultValue("50") int limit,
                                 @QueryParam("beforeMessageId") Integer beforeMessageId) {
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
                    this.messageService.listMessages(conversationId, limit, beforeMessageId);
            List<Map<String, Object>> messageMaps =
                    this.restJsonMapper.toMessageMaps(messages, viewer, conversation, currentUserKey);
            rewriteMessageAvatars(messageMaps);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("messages", messageMaps);
            return JimRestResponses.okJson(body);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/messages",
                    userKey, ex, "internal_error", "An internal error occurred while loading messages.");
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
        if (body == null || body.trim().isEmpty()) {
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
            JimMessage message =
                    this.messageService.sendUserMessage(conversationId, currentUserKey, body, replyToMessageId);
            Map<String, Object> messageMap =
                    this.restJsonMapper.toMessageMap(message, viewer, conversation, currentUserKey);
            rewriteMessageAvatar(messageMap);
            return JimRestResponses.okJson(messageMap);
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/chat/conversations/" + conversationId + "/messages",
                    userKey, ex, "internal_error", "An internal error occurred while sending the message.");
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
