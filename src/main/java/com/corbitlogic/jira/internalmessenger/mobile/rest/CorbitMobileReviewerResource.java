package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.corbitlogic.jira.internalmessenger.ao.JimAttachment;
import com.corbitlogic.jira.internalmessenger.ao.JimChatAudit;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimGroupMember;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.ao.JimMessageUserState;
import com.corbitlogic.jira.internalmessenger.mobile.MobileSessionSupport;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimChatAuditService;
import com.corbitlogic.jira.internalmessenger.service.JimMobileSessionService;
import com.corbitlogic.jira.internalmessenger.service.JimReviewerService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hidden chat-reviewer READ-ONLY API (Sprint 12). Mounted under
 * {@code /rest/corbit-mobile/1.0/reviewer}. Authenticated ONLY by a reviewer
 * session (the plugin-owned {@code pluginuser}); a normal user's session key is
 * never the reviewer sentinel, so normal sessions get 403 here. The reviewer
 * can read every conversation, including soft-deleted messages (with their
 * retained body) and per-user "delete for me" state, for complaint review.
 *
 * <p>Strictly read-only: the only write is the reviewer's own password change.
 * The reviewer is never a participant, emits no presence/typing, triggers no
 * notifications, and never mutates read/unread state. Every access is audited.</p>
 */
@Path("/reviewer")
@Produces({"application/json"})
@AnonymousAllowed
public class CorbitMobileReviewerResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobileReviewerResource.class);

    private final ActiveObjects activeObjects;
    private final UserManager userManager;
    private final JimMobileSessionService sessionService;
    private final JimReviewerService reviewerService;
    private final JimChatAuditService auditService;

    @Inject
    public CorbitMobileReviewerResource(ActiveObjects activeObjects,
                                        UserManager userManager,
                                        JimMobileSessionService sessionService,
                                        JimReviewerService reviewerService,
                                        JimChatAuditService auditService) {
        this.activeObjects = activeObjects;
        this.userManager = userManager;
        this.sessionService = sessionService;
        this.reviewerService = reviewerService;
        this.auditService = auditService;
    }

    /** Quick check that the caller holds a valid reviewer session. */
    @GET
    @Path("/status")
    public Response status(@Context HttpServletRequest request) {
        if (!isReviewer(request)) {
            return forbidden();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reviewer", true);
        body.put("configured", this.reviewerService.isConfigured());
        return JimRestResponses.okJson(body);
    }

    /** All conversations across all users (governance view). */
    @GET
    @Path("/conversations")
    public Response conversations(@Context HttpServletRequest request) {
        if (!isReviewer(request)) {
            return forbidden();
        }
        this.auditService.record(JimReviewerService.SESSION_USER_KEY, "reviewer.access",
                null, null, null, "conversations");
        try {
            JimConversation[] rows = this.activeObjects.find(JimConversation.class,
                    Query.select().order("LAST_MESSAGE_AT DESC"));
            List<Map<String, Object>> items = new ArrayList<>();
            for (JimConversation c : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", c.getID());
                m.put("type", c.getConversationType());
                m.put("groupName", c.getGroupName());
                m.put("projectKey", c.getProjectKey());
                m.put("createdAt", c.getCreatedAt());
                m.put("lastMessageAt", c.getLastMessageAt());
                m.put("lastMessagePreview", c.getLastMessagePreview());
                boolean archived = c.getArchived() != null && c.getArchived() != 0;
                m.put("archived", archived);
                m.put("archivedAt", c.getArchivedAt());
                m.put("archivedByUserKey", c.getArchivedByUserKey());
                m.put("participants", participantsFor(c));
                items.add(m);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("conversations", items);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/reviewer/conversations",
                    JimReviewerService.SESSION_USER_KEY, ex, "internal_error",
                    "Unable to load conversations.");
        }
    }

    /** All messages in a conversation, including soft-deleted, with governance metadata. */
    @GET
    @Path("/conversations/{conversationId}/messages")
    public Response messages(@Context HttpServletRequest request,
                             @PathParam("conversationId") int conversationId) {
        if (!isReviewer(request)) {
            return forbidden();
        }
        this.auditService.record(JimReviewerService.SESSION_USER_KEY, "reviewer.access",
                null, conversationId, null, "messages");
        try {
            JimMessage[] rows = this.activeObjects.find(JimMessage.class,
                    Query.select().where("CONVERSATION_ID = ?", conversationId).order("CREATED_AT ASC"));
            List<Map<String, Object>> items = new ArrayList<>();
            for (JimMessage msg : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", msg.getID());
                m.put("senderType", msg.getSenderType());
                m.put("senderUserKey", msg.getSenderUserKey());
                m.put("senderDisplayName", displayName(msg.getSenderUserKey()));
                // Reviewer sees the RETAINED body even for soft-deleted messages.
                m.put("body", msg.getBody());
                m.put("bodyFormat", msg.getBodyFormat());
                m.put("eventType", msg.getEventType());
                m.put("createdAt", msg.getCreatedAt());
                m.put("edited", isTrue(msg.getEdited()));
                m.put("editedAt", msg.getEditedAt());
                boolean deleted = msg.getDeleted() != null && msg.getDeleted() != 0;
                m.put("deleted", deleted);
                m.put("deletedAt", msg.getDeletedAt());
                m.put("deletedByUserKey", msg.getDeletedByUserKey());
                m.put("replyToMessageId", msg.getReplyToMessageId());
                m.put("attachments", attachmentsFor(msg.getID()));
                m.put("hiddenForUsers", hiddenForUsers(msg.getID()));
                items.add(m);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("conversationId", conversationId);
            body.put("messages", items);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/reviewer/conversations/" + conversationId + "/messages",
                    JimReviewerService.SESSION_USER_KEY, ex, "internal_error",
                    "Unable to load messages.");
        }
    }

    /**
     * PERSON-CENTRIC landing (s12e-fix3): one row per real Jira user who is a
     * direct participant, a (current or retained) group/project member, or the
     * author of any message. Excludes the reviewer account. Sorted by latest
     * related chat activity. This is the reviewer's primary entry point so a
     * complaint review starts from a person, not scattered per-conversation rows.
     */
    @GET
    @Path("/people")
    public Response people(@Context HttpServletRequest request) {
        if (!isReviewer(request)) {
            return forbidden();
        }
        this.auditService.record(JimReviewerService.SESSION_USER_KEY, "reviewer.access",
                null, null, null, "people");
        try {
            JimConversation[] convs = this.activeObjects.find(JimConversation.class, Query.select());
            Map<Integer, JimConversation> convById = new HashMap<>();
            for (JimConversation c : convs) {
                convById.put(c.getID(), c);
            }
            // person userKey -> set of related conversation ids
            Map<String, Set<Integer>> rel = new LinkedHashMap<>();
            for (JimConversation c : convs) {
                if (isGroupOrProject(c.getConversationType())) {
                    JimGroupMember[] members = this.activeObjects.find(JimGroupMember.class,
                            Query.select().where("CONVERSATION_ID = ?", c.getID()));
                    for (JimGroupMember m : members) {
                        addRelation(rel, m.getUserKey(), c.getID());
                    }
                } else {
                    addRelation(rel, c.getUserAKey(), c.getID());
                    addRelation(rel, c.getUserBKey(), c.getID());
                }
            }
            // Authorship: covers people who sent messages even if later removed
            // from a group (historical participation).
            JimMessage[] userMsgs = this.activeObjects.find(JimMessage.class,
                    Query.select().where("SENDER_TYPE = ?", "USER"));
            for (JimMessage msg : userMsgs) {
                addRelation(rel, msg.getSenderUserKey(), msg.getConversationId());
            }

            List<Map<String, Object>> people = new ArrayList<>();
            for (Map.Entry<String, Set<Integer>> e : rel.entrySet()) {
                String key = e.getKey();
                if (key == null || this.reviewerService.isReviewerUserKey(key)) {
                    continue; // never list the hidden reviewer
                }
                ApplicationUser u = this.userManager.getUserByKey(key);
                if (u == null) {
                    continue; // only real Jira users/people
                }
                long last = 0L;
                int archived = 0;
                for (Integer cid : e.getValue()) {
                    JimConversation c = convById.get(cid);
                    if (c == null) continue;
                    if (c.getLastMessageAt() != null && c.getLastMessageAt() > last) {
                        last = c.getLastMessageAt();
                    }
                    if (isArchived(c)) archived++;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("userKey", key);
                row.put("username", u.getName());
                row.put("displayName", u.getDisplayName());
                row.put("conversationCount", e.getValue().size());
                row.put("lastActivityAt", last);
                row.put("archivedCount", archived);
                people.add(row);
            }
            people.sort(Comparator.comparingLong((Map<String, Object> m) ->
                    ((Number) m.getOrDefault("lastActivityAt", 0L)).longValue()).reversed());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("people", people);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/reviewer/people",
                    JimReviewerService.SESSION_USER_KEY, ex, "internal_error",
                    "Unable to load people.");
        }
    }

    /** All conversations related to one person (direct, group, project, archived, authored). */
    @GET
    @Path("/people/{userKey}/conversations")
    public Response personConversations(@Context HttpServletRequest request,
                                        @PathParam("userKey") String userKey) {
        if (!isReviewer(request)) {
            return forbidden();
        }
        this.auditService.record(JimReviewerService.SESSION_USER_KEY, "reviewer.access",
                null, null, userKey, "person.conversations");
        try {
            JimConversation[] convs = this.activeObjects.find(JimConversation.class, Query.select());
            Set<Integer> cids = new LinkedHashSet<>();
            for (JimConversation c : convs) {
                if (isGroupOrProject(c.getConversationType())) {
                    JimGroupMember[] members = this.activeObjects.find(JimGroupMember.class,
                            Query.select().where("CONVERSATION_ID = ? AND USER_KEY = ?", c.getID(), userKey));
                    if (members.length > 0) {
                        cids.add(c.getID());
                    }
                } else if (userKey.equals(c.getUserAKey()) || userKey.equals(c.getUserBKey())) {
                    cids.add(c.getID());
                }
            }
            // Authored conversations (historical participation after removal).
            JimMessage[] authored = this.activeObjects.find(JimMessage.class,
                    Query.select().where("SENDER_TYPE = ? AND SENDER_USER_KEY = ?", "USER", userKey));
            for (JimMessage msg : authored) {
                cids.add(msg.getConversationId());
            }

            List<Map<String, Object>> items = new ArrayList<>();
            for (JimConversation c : convs) {
                if (!cids.contains(c.getID())) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", c.getID());
                m.put("type", c.getConversationType());
                m.put("groupName", c.getGroupName());
                m.put("projectKey", c.getProjectKey());
                m.put("createdAt", c.getCreatedAt());
                m.put("lastMessageAt", c.getLastMessageAt());
                m.put("lastMessagePreview", c.getLastMessagePreview());
                m.put("archived", isArchived(c));
                m.put("archivedAt", c.getArchivedAt());
                m.put("participants", participantsFor(c));
                items.add(m);
            }
            items.sort(Comparator.comparingLong((Map<String, Object> m) -> {
                Object v = m.get("lastMessageAt");
                return v instanceof Number ? ((Number) v).longValue() : 0L;
            }).reversed());

            ApplicationUser u = this.userManager.getUserByKey(userKey);
            Map<String, Object> person = new LinkedHashMap<>();
            person.put("userKey", userKey);
            person.put("username", u != null ? u.getName() : null);
            person.put("displayName", u != null ? u.getDisplayName() : userKey);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("person", person);
            body.put("conversations", items);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/reviewer/people/" + userKey + "/conversations",
                    JimReviewerService.SESSION_USER_KEY, ex, "internal_error",
                    "Unable to load the person's conversations.");
        }
    }

    /** Recent governance audit entries. */
    @GET
    @Path("/audit")
    public Response audit(@Context HttpServletRequest request,
                          @QueryParam("limit") @DefaultValue("100") int limit) {
        if (!isReviewer(request)) {
            return forbidden();
        }
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            for (JimChatAudit a : this.auditService.recent(limit)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("actorUserKey", a.getActorUserKey());
                m.put("action", a.getAction());
                m.put("messageId", a.getMessageId());
                m.put("conversationId", a.getConversationId());
                m.put("targetUserKey", a.getTargetUserKey());
                m.put("details", a.getDetails());
                m.put("createdAt", a.getCreatedAt());
                items.add(m);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("audit", items);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/reviewer/audit",
                    JimReviewerService.SESSION_USER_KEY, ex, "internal_error",
                    "Unable to load audit log.");
        }
    }

    /** Reviewer changes their own password (through the app/PWA). */
    @POST
    @Path("/change-password")
    @Consumes({"application/json"})
    public Response changePassword(@Context HttpServletRequest request, Map<String, Object> body) {
        if (!isReviewer(request)) {
            return forbidden();
        }
        // s12e-fix2: the reviewer is now a REAL Jira user, so its password is
        // owned by Jira's directory (possibly LDAP/SSO). The plugin must not keep
        // a second internal password. Direct the reviewer to change it in Jira.
        return JimRestResponses.errorJson(400, "managed_in_jira",
                "The reviewer password is managed in Jira. Change it from your Jira account settings.");
    }

    // --- helpers --------------------------------------------------------------

    private boolean isReviewer(HttpServletRequest request) {
        String token = MobileSessionSupport.extractSessionToken(request);
        if (token == null) {
            token = MobileSessionSupport.extractCookieToken(request);
        }
        if (token == null) {
            return false;
        }
        try {
            String userKey = this.sessionService.validate(token);
            // s12e-fix2: reviewer is a real Jira user; authorize by user key →
            // username (legacy sentinel key still accepted for old sessions).
            return this.reviewerService.isReviewerUserKey(userKey);
        } catch (Exception ex) {
            return false;
        }
    }

    private static void addRelation(Map<String, Set<Integer>> rel, String userKey, int conversationId) {
        if (userKey == null || userKey.trim().isEmpty()) {
            return;
        }
        rel.computeIfAbsent(userKey, k -> new LinkedHashSet<>()).add(conversationId);
    }

    private static boolean isGroupOrProject(String type) {
        return type != null && ("GROUP".equalsIgnoreCase(type) || "PROJECT".equalsIgnoreCase(type));
    }

    private static boolean isArchived(JimConversation c) {
        return c.getArchived() != null && c.getArchived() != 0;
    }

    private List<String> participantsFor(JimConversation c) {
        List<String> keys = new ArrayList<>();
        String type = c.getConversationType();
        if (type != null && ("GROUP".equalsIgnoreCase(type) || "PROJECT".equalsIgnoreCase(type))) {
            JimGroupMember[] members = this.activeObjects.find(JimGroupMember.class,
                    Query.select().where("CONVERSATION_ID = ?", c.getID()));
            for (JimGroupMember member : members) {
                keys.add(describe(member.getUserKey()));
            }
        } else {
            if (c.getUserAKey() != null) keys.add(describe(c.getUserAKey()));
            if (c.getUserBKey() != null) keys.add(describe(c.getUserBKey()));
        }
        return keys;
    }

    private List<Map<String, Object>> attachmentsFor(int messageId) {
        List<Map<String, Object>> list = new ArrayList<>();
        JimAttachment[] rows = this.activeObjects.find(JimAttachment.class,
                Query.select().where("MESSAGE_ID = ?", messageId));
        for (JimAttachment a : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getID());
            m.put("fileName", a.getOriginalFilename());
            m.put("contentType", a.getContentType());
            m.put("fileSize", a.getFileSize());
            m.put("fileKind", a.getFileKind());
            m.put("voice", a.getVoice());
            list.add(m);
        }
        return list;
    }

    private List<String> hiddenForUsers(int messageId) {
        List<String> users = new ArrayList<>();
        JimMessageUserState[] rows = this.activeObjects.find(JimMessageUserState.class,
                Query.select().where("MESSAGE_ID = ? AND HIDDEN = ?", messageId, 1));
        for (JimMessageUserState row : rows) {
            users.add(describe(row.getUserKey()));
        }
        return users;
    }

    private String describe(String userKey) {
        String name = displayName(userKey);
        return name != null ? name + " (" + userKey + ")" : userKey;
    }

    private String displayName(String userKey) {
        if (userKey == null) {
            return null;
        }
        try {
            ApplicationUser user = this.userManager.getUserByKey(userKey);
            return user != null ? user.getDisplayName() : userKey;
        } catch (Exception ex) {
            return userKey;
        }
    }

    private static boolean isTrue(Integer v) {
        return v != null && v != 0;
    }

    private static Response forbidden() {
        return JimRestResponses.errorJson(403, "reviewer_required",
                "Reviewer authentication is required.");
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }
}
