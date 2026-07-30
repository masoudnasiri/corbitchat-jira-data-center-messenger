/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.avatar.Avatar$Size
 *  com.atlassian.jira.avatar.AvatarService
 *  com.atlassian.jira.avatar.AvatarsDisabledException
 *  com.atlassian.jira.component.ComponentAccessor
 *  com.atlassian.jira.issue.Issue
 *  com.atlassian.jira.issue.MutableIssue
 *  com.atlassian.jira.permission.ProjectPermissions
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.jira.user.util.UserManager
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.avatar.AvatarsDisabledException;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.corbitlogic.jira.internalmessenger.ao.JimAttachment;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimGroupMember;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.ao.JimReaction;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentPolicy;
import com.corbitlogic.jira.internalmessenger.dto.UserSearchResultDto;
import com.corbitlogic.jira.internalmessenger.model.JimConversationType;
import com.corbitlogic.jira.internalmessenger.model.JimEventType;
import com.corbitlogic.jira.internalmessenger.model.JimSenderType;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import com.corbitlogic.jira.internalmessenger.service.JimAttachmentService;
import com.corbitlogic.jira.internalmessenger.service.JimGroupService;
import com.corbitlogic.jira.internalmessenger.service.JimGroupServiceImpl;
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimPresenceService;
import com.corbitlogic.jira.internalmessenger.service.JimReactionService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import com.corbitlogic.jira.internalmessenger.util.JimMessageFlags;
import com.corbitlogic.jira.internalmessenger.util.JimMessageLifecycle;
import com.corbitlogic.jira.internalmessenger.util.JimSanitizer;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimRestJsonMapper {
    private static final Logger log = LoggerFactory.getLogger(JimRestJsonMapper.class);
    public static final String SYSTEM_CONVERSATION_DISPLAY_NAME = "Jira Assistant";
    private final UserManager userManager;
    private final AvatarService avatarService;
    private final JimPermissionService permissionService;
    private final JimAttachmentService attachmentService;
    private final JimReadStateService readStateService;
    private final JimMessageService messageService;
    private final JimReactionService reactionService;
    private final JimPresenceService presenceService;
    private final JimGroupService groupService;
    private final JimAdminSettingsService adminSettingsService;
    private final com.corbitlogic.jira.internalmessenger.service.JimReviewerService reviewerService;

    public JimRestJsonMapper(UserManager userManager, AvatarService avatarService, JimPermissionService permissionService, JimAttachmentService attachmentService, JimReadStateService readStateService, JimMessageService messageService, JimReactionService reactionService, JimPresenceService presenceService, JimGroupService groupService, JimAdminSettingsService adminSettingsService, com.corbitlogic.jira.internalmessenger.service.JimReviewerService reviewerService) {
        this.userManager = userManager;
        this.avatarService = avatarService;
        this.permissionService = permissionService;
        this.attachmentService = attachmentService;
        this.readStateService = readStateService;
        this.messageService = messageService;
        this.reactionService = reactionService;
        this.presenceService = presenceService;
        this.groupService = groupService;
        this.adminSettingsService = adminSettingsService;
        this.reviewerService = reviewerService;
    }

    public Map<String, Object> toConversationMap(JimConversation conversation, String currentUserKey, ApplicationUser viewer, int unreadCount) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", conversation.getID());
        item.put("type", conversation.getConversationType());
        item.put("lastMessagePreview", JimSanitizer.sanitizeText(conversation.getLastMessagePreview()));
        item.put("lastMessageAt", conversation.getLastMessageAt());
        // Sender of the most recent message so the sidebar can colour-code
        // the preview by ownership. Null on legacy rows (pre-migration) and
        // on system messages; the client renders the neutral style then.
        item.put("lastMessageSenderUserKey", conversation.getLastMessageSenderUserKey());
        item.put("lastMessageOwn", currentUserKey != null
                && currentUserKey.equals(conversation.getLastMessageSenderUserKey()));
        item.put("unreadCount", unreadCount);
        JimConversationType type = this.permissionService.toConversationType(conversation);
        boolean isSystem = type == JimConversationType.SYSTEM;
        boolean isProjectChat = type == JimConversationType.PROJECT;
        boolean isGroup = type == JimConversationType.GROUP || isProjectChat;
        item.put("isSystem", isSystem);
        item.put("isGroup", isGroup);
        item.put("isProjectChat", isProjectChat);
        if (isSystem) {
            item.put("displayName", SYSTEM_CONVERSATION_DISPLAY_NAME);
            item.put("avatarUrl", null);
            item.put("otherUserActive", false);
            return item;
        }
        if (isGroup) {
            item.put("displayName", JimSanitizer.sanitizeText(conversation.getGroupName()));
            item.put("avatarUrl", this.buildConversationAvatarUrl(conversation));
            item.put("otherUserActive", false);
            item.put("memberCount", this.groupService.countMembers(conversation.getID()));
            item.put("ownerUserKey", conversation.getCreatedByUserKey());
            String myRole = this.groupService.getRole(conversation, currentUserKey);
            boolean isOwner = JimGroupServiceImpl.ROLE_OWNER.equals(myRole);
            item.put("myRole", myRole);
            item.put("isOwner", isOwner);
            item.put("canManage", this.groupService.canManageMembers(conversation, currentUserKey));
            item.put("canManageAdmins", this.groupService.canManageAdmins(conversation, currentUserKey));
            if (isProjectChat) {
                item.put("projectKey", conversation.getProjectKey());
            }
            return item;
        }
        String otherUserKey = this.resolveOtherParticipantKey(conversation, currentUserKey);
        // Self "Saved Messages" conversation (Sprint 06 Fix-2): both sides are
        // the viewer. Flag it so the client can label it distinctly.
        boolean self = currentUserKey != null && currentUserKey.equals(conversation.getUserAKey())
                && currentUserKey.equals(conversation.getUserBKey());
        item.put("self", self);
        ApplicationUser otherUser = this.userManager.getUserByKey(otherUserKey);
        if (otherUser != null) {
            item.put("displayName", otherUser.getDisplayName());
            item.put("avatarUrl", this.resolveAvatarUrl(viewer, otherUser));
        } else {
            item.put("displayName", otherUserKey);
            item.put("avatarUrl", null);
        }
        item.put("otherUserActive", this.presenceService.isActive(otherUserKey));
        return item;
    }

    public Map<String, Object> toMessageMap(JimMessage message, ApplicationUser viewer) {
        String currentUserKey = viewer != null ? viewer.getKey() : null;
        return this.toMessageMap(message, viewer, null, currentUserKey);
    }

    public Map<String, Object> toMessageMap(JimMessage message, ApplicationUser viewer, JimConversation conversation, String currentUserKey) {
        List<JimAttachment> attachments = JimMessageFlags.isDeleted(message) ? Collections.emptyList() : this.attachmentService.listAttachmentsForMessage(message.getID());
        List<JimReaction> reactions = JimMessageFlags.isDeleted(message) ? Collections.emptyList() : this.reactionService.listReactionsForMessage(message.getID());
        return this.toMessageMap(message, viewer, attachments, conversation, currentUserKey, this.buildReplyContext(Collections.singletonList(message)), reactions);
    }

    public Map<String, Object> toMessageMap(JimMessage message, ApplicationUser viewer, List<JimAttachment> attachments, JimConversation conversation, String currentUserKey, ReplyContext replyContext, List<JimReaction> reactions) {
        ApplicationUser sender;
        LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
        boolean deleted = JimMessageFlags.isDeleted(message);
        boolean edited = JimMessageFlags.isEdited(message);
        boolean isUserMessage = JimSenderType.USER.name().equals(message.getSenderType());
        boolean ownMessage = isUserMessage && currentUserKey != null && currentUserKey.equals(message.getSenderUserKey());
        item.put("id", message.getID());
        item.put("conversationId", message.getConversationId());
        item.put("senderType", message.getSenderType());
        item.put("senderUserKey", message.getSenderUserKey());
        item.put("body", deleted ? "" : JimSanitizer.sanitizeText(message.getBody()));
        item.put("bodyFormat", message.getBodyFormat());
        item.put("eventType", message.getEventType());
        item.put("issueKey", message.getIssueKey());
        item.put("issueUrl", message.getIssueUrl());
        // Jira Assistant / system issue cards (MENTION / ASSIGNMENT /
        // STATUS_CHANGE / COMMENT / ISSUE_ATTACHMENT that reference an issue)
        // are now re-gated per-viewer at read time (enrichSystemIssueCard): the
        // summary, full mention text and attachment links are ONLY returned to a
        // viewer who can still browse the issue. Non-issue system cards (e.g. a
        // group @mention) keep their stored summary. USER "attach issue"
        // (ISSUE_LINK) cards remain gated in enrichIssueDetails.
        boolean systemIssueCard = JimRestJsonMapper.isEnrichableSystemIssueCard(message);
        if (!JimEventType.ISSUE_LINK.name().equals(message.getEventType()) && !systemIssueCard) {
            item.put("issueSummary", JimSanitizer.sanitizeText(message.getIssueSummary()));
        }
        this.enrichIssueDetails(item, message, viewer);
        this.enrichSystemIssueCard(item, message, viewer, systemIssueCard);
        item.put("actorUserKey", message.getActorUserKey());
        item.put("actorDisplayName", JimSanitizer.sanitizeText(message.getActorDisplayName()));
        item.put("createdAt", message.getCreatedAt());
        item.put("edited", edited);
        item.put("editedAt", edited ? message.getEditedAt() : null);
        item.put("deleted", deleted);
        item.put("deletedAt", deleted ? message.getDeletedAt() : null);
        item.put("pinned", !deleted && this.isPinned(message));
        // Actioned/acknowledged state for Jira Assistant / system messages.
        // The field exists on every JimMessage row, but the client only
        // renders it for system messages.
        Integer actionedFlag = message.getActioned();
        item.put("actioned", actionedFlag != null && actionedFlag != 0);
        item.put("actionedAt", actionedFlag != null && actionedFlag != 0 ? message.getActionedAt() : null);
        if (message.getSenderUserKey() != null && (sender = this.userManager.getUserByKey(message.getSenderUserKey())) != null) {
                item.put("senderDisplayName", sender.getDisplayName());
            // Expose the sender's Jira avatar URL on every message so
            // the client can show the real face next to each bubble
            // in group / project chats. Previously the client fell back
            // to the conversation's own avatar, which is meaningful only
            // for 1-on-1 chats - in group chats every bubble ended up
            // rendering identical initials.
            item.put("senderAvatarUrl", this.resolveAvatarUrl(viewer, sender));
        }
        int currentUserLastRead = this.readStateService.getLastReadMessageId(message.getConversationId(), currentUserKey);
        int otherParticipantLastRead = this.resolveOtherParticipantLastRead(conversation, currentUserKey);
        boolean seenByOther = ownMessage && this.isDirectConversation(conversation) && message.getID() <= otherParticipantLastRead;
        item.put("ownMessage", ownMessage);
        item.put("readByCurrentUser", message.getID() <= currentUserLastRead);
        item.put("seenByOther", seenByOther);
        item.put("seenAt", seenByOther ? this.resolveOtherParticipantLastReadAt(conversation, currentUserKey) : null);
        boolean editEligibleType = ownMessage && isUserMessage && !deleted;
        item.put("canEdit", editEligibleType && this.isWithinEditWindow(message));
        item.put("canDelete", ownMessage && isUserMessage && !deleted && this.isWithinDeleteWindow(message));
        // Server-anchored edit-window fields (epoch millis) so clients never rely
        // on browser/device wall-clock or localized strings to decide editability.
        // editableUntilMs is null for messages that are not edit-eligible by type
        // (others', system/issue-link, deleted) regardless of time.
        item.put("editWindowMinutes", (int) (JimMessageLifecycle.EDIT_WINDOW_MS / 60000L));
        Long createdAtMs = message.getCreatedAt();
        item.put("editableUntilMs",
                (editEligibleType && createdAtMs != null)
                        ? Long.valueOf(createdAtMs + JimMessageLifecycle.EDIT_WINDOW_MS)
                        : null);
        item.put("replyTo", this.buildReplyPreview(message, replyContext));
        item.put("forwardedFrom", this.buildForwardedFrom(message));
        item.put("attachments", deleted ? Collections.emptyList() : this.toAttachmentMaps(attachments != null ? attachments : Collections.emptyList()));
        item.put("reactions", deleted ? Collections.emptyList() : this.toReactionMaps(reactions, currentUserKey));
        return item;
    }

    private void enrichIssueDetails(Map<String, Object> item, JimMessage message, ApplicationUser viewer) {
        if (!JimEventType.ISSUE_LINK.name().equals(message.getEventType()) || message.getIssueKey() == null || message.getIssueKey().isEmpty()) {
            return;
        }
        // Default: not accessible → clients render a safe "no permission / unavailable"
        // card with just the key, never the stored summary.
        item.put("issueAccessible", false);
        try {
            MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(message.getIssueKey());
            if (issue == null || !ComponentAccessor.getPermissionManager().hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue)issue, viewer)) {
                return;
            }
            item.put("issueAccessible", true);
            if (issue.getIssueType() != null) {
                item.put("issueTypeName", issue.getIssueType().getName());
            }
            if (issue.getPriority() != null) {
                item.put("issuePriorityName", issue.getPriority().getName());
            }
            if (issue.getStatus() != null) {
                item.put("issueStatusName", issue.getStatus().getName());
                if (issue.getStatus().getStatusCategory() != null) {
                    item.put("issueStatusCategory", issue.getStatus().getStatusCategory().getKey());
                }
            }
            item.put("issueSummary", JimSanitizer.sanitizeText(issue.getSummary()));
        }
        catch (Exception ex) {
            log.debug("Unable to enrich issue details for message {}: {}", (Object)message.getID(), (Object)ex.getMessage());
        }
    }

    /**
     * A SYSTEM Jira Assistant card that references an issue and therefore must
     * have its sensitive fields (summary / full mention text / attachment links)
     * re-gated by a live browse-permission check at read time.
     */
    private static boolean isEnrichableSystemIssueCard(JimMessage message) {
        if (message == null || !JimSenderType.SYSTEM.name().equals(message.getSenderType())) {
            return false;
        }
        String key = message.getIssueKey();
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        String et = message.getEventType();
        return JimEventType.MENTION.name().equals(et)
                || JimEventType.ASSIGNMENT.name().equals(et)
                || JimEventType.STATUS_CHANGE.name().equals(et)
                || JimEventType.COMMENT.name().equals(et)
                || JimEventType.ISSUE_ATTACHMENT.name().equals(et)
                || JimEventType.SCHEDULE_APPROVAL.name().equals(et)
                || JimEventType.SCHEDULE_APPROVAL_APPROVED.name().equals(et)
                || JimEventType.SCHEDULE_APPROVAL_REJECTED.name().equals(et);
    }

    private static boolean isScheduleCard(JimMessage message) {
        String et = message.getEventType();
        return JimEventType.SCHEDULE_APPROVAL.name().equals(et)
                || JimEventType.SCHEDULE_APPROVAL_APPROVED.name().equals(et)
                || JimEventType.SCHEDULE_APPROVAL_REJECTED.name().equals(et);
    }

    /**
     * Enrich (and permission-gate) a Jira Assistant issue card. When the viewer
     * can browse the issue we expose a FRESH summary, the full mention/comment
     * text and any attached files with secure, Jira-authenticated download
     * links. When they cannot (never had access, or lost it after the
     * notification was created) we redact everything sensitive and emit a safe
     * "unavailable" card carrying only a generic label + the issue key.
     */
    private void enrichSystemIssueCard(Map<String, Object> item, JimMessage message, ApplicationUser viewer, boolean systemIssueCard) {
        if (!systemIssueCard) {
            return;
        }
        boolean accessible = false;
        try {
            MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(message.getIssueKey());
            if (issue != null && ComponentAccessor.getPermissionManager().hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue) issue, viewer)) {
                accessible = true;
                item.put("issueSummary", JimSanitizer.sanitizeText(issue.getSummary()));
                if (issue.getIssueType() != null) {
                    item.put("issueTypeName", issue.getIssueType().getName());
                }
                if (issue.getStatus() != null) {
                    item.put("issueStatusName", issue.getStatus().getName());
                    if (issue.getStatus().getStatusCategory() != null) {
                        item.put("issueStatusCategory", issue.getStatus().getStatusCategory().getKey());
                    }
                }
                String eventText = message.getEventText();
                if (eventText != null && !eventText.trim().isEmpty()) {
                    // Line breaks preserved; clients HTML-escape on render.
                    item.put("eventText", JimSanitizer.sanitizeText(eventText));
                }
                List<Map<String, Object>> files = this.buildIssueAttachments(message.getEventData(), issue, message.getIssueKey());
                if (!files.isEmpty()) {
                    item.put("issueAttachments", files);
                }
                if (isScheduleCard(message)) {
                    this.enrichScheduleApproval(item, message, issue, viewer);
                }
            }
        }
        catch (Exception ex) {
            log.debug("Unable to enrich system issue card for message {}: {}", (Object) message.getID(), (Object) ex.getMessage());
            accessible = false;
        }
        item.put("issueAccessible", accessible);
        if (!accessible) {
            // Redact: never leak summary / full text / file names / links to a
            // viewer without browse permission (or if the issue is gone).
            item.remove("issueSummary");
            item.remove("eventText");
            item.remove("issueAttachments");
            item.remove("issueTypeName");
            item.remove("issueStatusName");
            item.remove("issueStatusCategory");
            item.remove("scheduleApproval");
            item.put("issueUnavailable", true);
            item.put("body", JimSanitizer.sanitizeText(this.safeSystemBody(message)));
        }
    }

    /**
     * Schedule-approval card fields (permission-gated; only reached for a viewer
     * who can browse the issue). Status is read LIVE from Jira so approve/reject
     * reflects immediately; proposed date is read live; requester/approver come
     * from stored event metadata. {@code isApprover} is true only for the
     * selected approver — the client shows Approve/Reject only then.
     */
    private void enrichScheduleApproval(Map<String, Object> item, JimMessage message, Issue issue, ApplicationUser viewer) {
        LinkedHashMap<String, Object> sa = new LinkedHashMap<String, Object>();
        Map<String, String> data = parseEventDataKv(message.getEventData());
        // Proposed date — live from the issue, fall back to stored snapshot.
        java.util.Date proposed = com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalConfig.getProposedDate(issue);
        if (proposed != null) {
            sa.put("proposedDateMillis", proposed.getTime());
            sa.put("proposedDateYmd", com.corbitlogic.jira.internalmessenger.mobile.JimMobileDates.toYmd(proposed));
        } else {
            if (data.get("proposedDateMillis") != null && !data.get("proposedDateMillis").isEmpty()) {
                try { sa.put("proposedDateMillis", Long.parseLong(data.get("proposedDateMillis"))); } catch (NumberFormatException ignored) {}
            }
            if (data.get("proposedDateYmd") != null) {
                sa.put("proposedDateYmd", data.get("proposedDateYmd"));
            }
        }
        boolean resultCard = JimEventType.SCHEDULE_APPROVAL_APPROVED.name().equals(message.getEventType())
                || JimEventType.SCHEDULE_APPROVAL_REJECTED.name().equals(message.getEventType());
        // Live approval status (request card) or the decided status (result card).
        String status;
        if (resultCard) {
            status = JimEventType.SCHEDULE_APPROVAL_APPROVED.name().equals(message.getEventType())
                    ? "Approved" : "Rejected";
        } else {
            status = com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalConfig.getStatusValue(issue);
            if (status == null || status.trim().isEmpty()) {
                status = data.getOrDefault("status", "Pending");
            }
        }
        sa.put("status", status);
        // Requester (request card) or approver (result card) identity.
        if (data.get("requesterName") != null && !data.get("requesterName").isEmpty()) {
            sa.put("requesterName", JimSanitizer.sanitizeText(data.get("requesterName")));
        }
        if (data.get("approverName") != null && !data.get("approverName").isEmpty()) {
            sa.put("approverName", JimSanitizer.sanitizeText(data.get("approverName")));
        }
        if (resultCard) {
            sa.put("resultType", JimEventType.SCHEDULE_APPROVAL_APPROVED.name().equals(message.getEventType())
                    ? "approved" : "rejected");
            // Rejection reason (stored in eventText, permission-gated by being
            // reached only inside the accessible branch).
            String reason = message.getEventText();
            if (reason != null && !reason.trim().isEmpty()) {
                sa.put("reason", JimSanitizer.sanitizeText(reason));
            }
            if (data.get("decidedAt") != null && !data.get("decidedAt").isEmpty()) {
                try { sa.put("decidedAt", Long.parseLong(data.get("decidedAt"))); } catch (NumberFormatException ignored) {}
            }
            sa.put("isApprover", false);
        } else {
            String approverKey = data.get("approverKey");
            boolean isApprover = viewer != null && approverKey != null && !approverKey.isEmpty()
                    && approverKey.equals(viewer.getKey());
            sa.put("isApprover", isApprover);
        }
        item.put("scheduleApproval", sa);
    }

    private static Map<String, String> parseEventDataKv(String eventData) {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        if (eventData == null || eventData.trim().isEmpty()) {
            return map;
        }
        for (String line : eventData.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                map.put(line.substring(0, eq).trim(), line.substring(eq + 1));
            }
        }
        return map;
    }

    /**
     * Resolve the stored attachment ids (one {@code id<TAB>name} per line) into
     * per-viewer JSON with a secure download link. Metadata (size/type) and file
     * existence are re-checked LIVE against Jira so a deleted file surfaces as
     * unavailable and nothing stale is ever returned. The {@code downloadUrl} is
     * Jira's own {@code /secure/attachment/*} path (permission-enforced by Jira
     * on the web); the mobile BFF rewrites it to a session-authed proxy path.
     */
    private List<Map<String, Object>> buildIssueAttachments(String eventData, Issue issue, String issueKey) {
        ArrayList<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (eventData == null || eventData.trim().isEmpty() || issue == null) {
            return out;
        }
        String[] lines = eventData.split("\n");
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            String idPart;
            String storedName;
            int tab = line.indexOf(9);
            if (tab >= 0) {
                idPart = line.substring(0, tab).trim();
                storedName = line.substring(tab + 1).trim();
            } else {
                idPart = line.trim();
                storedName = null;
            }
            long attachmentId;
            try {
                attachmentId = Long.parseLong(idPart);
            } catch (NumberFormatException nfe) {
                continue;
            }
            LinkedHashMap<String, Object> file = new LinkedHashMap<String, Object>();
            file.put("id", attachmentId);
            boolean available = false;
            String fileName = storedName;
            try {
                Attachment att = ComponentAccessor.getAttachmentManager().getAttachment(attachmentId);
                if (att != null && att.getIssue() != null && issue.getId() != null
                        && issue.getId().equals(att.getIssue().getId())) {
                    available = true;
                    if (att.getFilename() != null) {
                        fileName = att.getFilename();
                    }
                    if (att.getFilesize() != null) {
                        file.put("fileSize", att.getFilesize());
                    }
                    if (att.getMimetype() != null) {
                        file.put("contentType", att.getMimetype());
                    }
                    String secure = "/secure/attachment/" + attachmentId + "/"
                            + JimRestJsonMapper.encodePathSegment(att.getFilename());
                    file.put("downloadUrl", secure);
                    file.put("previewUrl", secure);
                }
            }
            catch (Exception ex) {
                available = false;
            }
            file.put("fileName", JimSanitizer.sanitizeText(fileName != null ? fileName : "attachment"));
            file.put("available", available);
            out.add(file);
        }
        return out;
    }

    private static String encodePathSegment(String name) {
        if (name == null || name.isEmpty()) {
            return "file";
        }
        try {
            return URLEncoder.encode(name, "UTF-8").replace("+", "%20");
        }
        catch (UnsupportedEncodingException ex) {
            return "file";
        }
    }

    private String safeSystemBody(JimMessage message) {
        String actor = message.getActorDisplayName();
        String who = actor != null && !actor.trim().isEmpty() ? actor.trim() : "Someone";
        String key = message.getIssueKey() != null ? message.getIssueKey() : "an issue";
        String et = message.getEventType();
        if (JimEventType.MENTION.name().equals(et)) {
            return who + " mentioned you in " + key + ".";
        }
        if (JimEventType.ASSIGNMENT.name().equals(et)) {
            return who + " assigned " + key + " to you.";
        }
        if (JimEventType.STATUS_CHANGE.name().equals(et)) {
            return who + " updated the status of " + key + ".";
        }
        if (JimEventType.ISSUE_ATTACHMENT.name().equals(et)) {
            return who + " attached a file to " + key + ".";
        }
        return "Update on " + key + ".";
    }

    public List<Map<String, Object>> toReactionMaps(List<JimReaction> reactions, String currentUserKey) {
        if (reactions == null || reactions.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashMap<String, Map<String, Object>> byEmoji = new LinkedHashMap<String, Map<String, Object>>();
        for (JimReaction reaction : reactions) {
            Map<String, Object> aggregate = byEmoji.computeIfAbsent(reaction.getEmoji(), emoji -> {
                LinkedHashMap<String, Object> entry = new LinkedHashMap<String, Object>();
                entry.put("emoji", emoji);
                entry.put("count", 0);
                entry.put("reactedByMe", false);
                return entry;
            });
            aggregate.put("count", (Integer)aggregate.get("count") + 1);
            if (currentUserKey == null || !currentUserKey.equals(reaction.getUserKey())) continue;
            aggregate.put("reactedByMe", true);
        }
        return new ArrayList<Map<String, Object>>(byEmoji.values());
    }

    public List<Map<String, Object>> toMessageMaps(List<JimMessage> messages, ApplicationUser viewer, JimConversation conversation, String currentUserKey) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Integer, List<JimAttachment>> attachmentsByMessage = this.attachmentService.listAttachmentsForMessages(messages);
        Map<Integer, List<JimReaction>> reactionsByMessage = this.reactionService.listReactionsForMessages(messages);
        ReplyContext replyContext = this.buildReplyContext(messages);
        ArrayList<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (JimMessage message : messages) {
            List<JimAttachment> attachments = attachmentsByMessage.getOrDefault(message.getID(), Collections.emptyList());
            List<JimReaction> reactions = reactionsByMessage.getOrDefault(message.getID(), Collections.emptyList());
            if (JimMessageFlags.isDeleted(message)) {
                attachments = Collections.emptyList();
                reactions = Collections.emptyList();
            }
            items.add(this.toMessageMap(message, viewer, attachments, conversation, currentUserKey, replyContext, reactions));
        }
        return items;
    }

    public Map<String, Object> toAttachmentMap(JimAttachment attachment) {
        boolean imagePreviewAllowed;
        LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", attachment.getID());
        item.put("fileName", JimSanitizer.sanitizeText(attachment.getOriginalFilename()));
        item.put("contentType", attachment.getContentType());
        item.put("fileSize", attachment.getFileSize());
        item.put("fileKind", attachment.getFileKind());
        // Sprint 07 Fix-2: recorded voice note vs picked/shared audio file.
        // Additive field; fileKind stays AUDIO so existing web rendering
        // (inline <audio> player) is unaffected.
        item.put("voice", JimAttachmentPolicy.isVoiceAttachment(attachment.getFileKind(), attachment.getVoice()));
        item.put("downloadUrl", this.buildAttachmentUrl(attachment.getID(), "download"));
        boolean bl = imagePreviewAllowed = !"IMAGE".equals(attachment.getFileKind()) || this.adminSettingsService.isImagePreviewEnabled();
        if (("IMAGE".equals(attachment.getFileKind()) || "AUDIO".equals(attachment.getFileKind())
                || "VIDEO".equals(attachment.getFileKind())) && imagePreviewAllowed) {
            item.put("previewUrl", this.buildAttachmentUrl(attachment.getID(), "preview"));
        }
        return item;
    }

    public List<Map<String, Object>> toAttachmentMaps(List<JimAttachment> attachments) {
        ArrayList<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (JimAttachment attachment : attachments) {
            items.add(this.toAttachmentMap(attachment));
        }
        return items;
    }

    public List<Map<String, Object>> toGroupMemberMaps(List<JimGroupMember> members, ApplicationUser viewer) {
        return this.toGroupMemberMaps(members, viewer, null);
    }

    public List<Map<String, Object>> toGroupMemberMaps(List<JimGroupMember> members, ApplicationUser viewer, JimConversation conversation) {
        ArrayList<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        if (members == null) {
            return items;
        }
        String ownerKey = conversation != null && conversation.getCreatedByUserKey() != null
                ? conversation.getCreatedByUserKey().trim() : null;
        for (JimGroupMember member : members) {
            // Only the dedicated hidden system account (pluginuser) is suppressed
            // from member/admin lists — never an ordinary user such as the
            // configured reviewer user (m.nasiri).
            if (this.reviewerService != null && this.reviewerService.isHiddenSystemAccountUserKey(member.getUserKey())) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("userKey", member.getUserKey());
            // Normalise role with the legacy OWNER fallback: the permanent owner
            // (createdByUserKey) reads as OWNER even if the row's role is null.
            String role = member.getRole();
            if (ownerKey != null && ownerKey.equals(member.getUserKey())) {
                role = JimGroupServiceImpl.ROLE_OWNER;
            } else if (role == null || role.trim().isEmpty()) {
                role = JimGroupServiceImpl.ROLE_MEMBER;
            }
            item.put("role", role);
            item.put("joinedAt", member.getJoinedAt());
            ApplicationUser user = this.userManager.getUserByKey(member.getUserKey());
            if (user != null) {
                item.put("displayName", user.getDisplayName());
                item.put("avatarUrl", this.resolveAvatarUrl(viewer, user));
                item.put("active", user.isActive());
            } else {
                item.put("displayName", member.getUserKey());
                item.put("avatarUrl", null);
                item.put("active", false);
            }
            items.add(item);
        }
        return items;
    }

    public Map<String, Object> toUserSearchMap(UserSearchResultDto user) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("userKey", user.getUserKey());
        item.put("username", user.getUsername());
        item.put("displayName", user.getDisplayName());
        item.put("avatarUrl", user.getAvatarUrl());
        return item;
    }

    public List<Map<String, Object>> toConversationMaps(List<JimConversation> conversations, String currentUserKey, ApplicationUser viewer, BiFunction<Integer, String, Integer> unreadCountResolver) {
        ArrayList<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (JimConversation conversation : conversations) {
            int unreadCount = unreadCountResolver.apply(conversation.getID(), currentUserKey);
            items.add(this.toConversationMap(conversation, currentUserKey, viewer, unreadCount));
        }
        return items;
    }

    public List<Map<String, Object>> toUserSearchMaps(List<UserSearchResultDto> users) {
        ArrayList<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (UserSearchResultDto user : users) {
            items.add(this.toUserSearchMap(user));
        }
        return items;
    }

    private ReplyContext buildReplyContext(List<JimMessage> messages) {
        HashSet<Integer> replyIds = new HashSet<Integer>();
        for (JimMessage message : messages) {
            Long replyToMessageId = message.getReplyToMessageId();
            if (replyToMessageId == null || replyToMessageId <= 0L) continue;
                replyIds.add(replyToMessageId.intValue());
        }
        Map<Integer, JimMessage> replyMessages = this.messageService.findMessagesByIds(replyIds);
        Map<Integer, List<JimAttachment>> replyAttachments = Collections.emptyMap();
        if (!replyMessages.isEmpty()) {
            replyAttachments = this.attachmentService.listAttachmentsForMessages(new ArrayList<JimMessage>(replyMessages.values()));
        }
        return new ReplyContext(replyMessages, replyAttachments);
    }

    /**
     * Forward attribution block (Sprint 06 Fix-1), or null for non-forwarded
     * messages. Carries the original author so the client can render
     * "Forwarded from &lt;name&gt;". The display name is a stored snapshot taken
     * at forward time; fall back to a live lookup if it is missing.
     */
    private Map<String, Object> buildForwardedFrom(JimMessage message) {
        Long forwardedFromMessageId = message.getForwardedFromMessageId();
        String forwardedFromUserKey = message.getForwardedFromUserKey();
        if ((forwardedFromMessageId == null || forwardedFromMessageId <= 0L)
                && (forwardedFromUserKey == null || forwardedFromUserKey.isEmpty())) {
            return null;
        }
        LinkedHashMap<String, Object> info = new LinkedHashMap<String, Object>();
        info.put("messageId", forwardedFromMessageId);
        info.put("userKey", forwardedFromUserKey);
        String displayName = message.getForwardedFromDisplayName();
        if ((displayName == null || displayName.isEmpty()) && forwardedFromUserKey != null) {
            ApplicationUser original = this.userManager.getUserByKey(forwardedFromUserKey);
            displayName = original != null ? original.getDisplayName() : forwardedFromUserKey;
        }
        info.put("displayName", displayName);
        info.put("at", message.getForwardedAt());
        return info;
    }

    private Map<String, Object> buildReplyPreview(JimMessage message, ReplyContext replyContext) {
        Long replyToMessageId = message.getReplyToMessageId();
        if (replyToMessageId == null || replyToMessageId <= 0L) {
            return null;
        }
        JimMessage parent = replyContext.replyMessages.get(replyToMessageId.intValue());
        if (parent == null) {
            return null;
        }
        LinkedHashMap<String, Object> preview = new LinkedHashMap<String, Object>();
        preview.put("id", parent.getID());
        preview.put("deleted", JimMessageFlags.isDeleted(parent));
        String senderDisplayName = this.resolveSenderDisplayName(parent);
        preview.put("senderDisplayName", senderDisplayName);
        if (JimMessageFlags.isDeleted(parent)) {
            preview.put("bodyPreview", "Deleted message");
            preview.put("attachmentPreview", null);
            return preview;
        }
        String bodyPreview = this.buildBodyPreview(parent.getBody());
        List<JimAttachment> attachments = replyContext.replyAttachments.getOrDefault(parent.getID(), Collections.emptyList());
        String attachmentPreview = this.buildAttachmentPreview(attachments);
        if ((bodyPreview == null || bodyPreview.isEmpty()) && attachmentPreview != null) {
            preview.put("bodyPreview", attachmentPreview);
            preview.put("attachmentPreview", attachmentPreview);
        } else {
            preview.put("bodyPreview", bodyPreview != null ? bodyPreview : "");
            preview.put("attachmentPreview", attachmentPreview);
        }
        return preview;
    }

    private String buildBodyPreview(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() <= 120) {
            return JimSanitizer.sanitizeText(trimmed);
        }
        return JimSanitizer.sanitizeText(trimmed.substring(0, 120)) + "...";
    }

    private String buildAttachmentPreview(List<JimAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        JimAttachment first = attachments.get(0);
        if ("IMAGE".equals(first.getFileKind())) {
            return "Image";
        }
        if (JimAttachmentPolicy.isVoiceAttachment(first.getFileKind(), first.getVoice())) {
            return "Voice message";
        }
        if ("VIDEO".equals(first.getFileKind())) {
            return "Video";
        }
        return "File: " + JimAttachmentPolicy.sanitizeOriginalFilename(first.getOriginalFilename());
    }

    private String resolveSenderDisplayName(JimMessage message) {
        if (JimSenderType.SYSTEM.name().equals(message.getSenderType())) {
            return SYSTEM_CONVERSATION_DISPLAY_NAME;
        }
        if (message.getSenderUserKey() != null) {
            ApplicationUser sender = this.userManager.getUserByKey(message.getSenderUserKey());
            if (sender != null) {
                return sender.getDisplayName();
            }
            return message.getSenderUserKey();
        }
        return "User";
    }

    private int resolveOtherParticipantLastRead(JimConversation conversation, String currentUserKey) {
        if (conversation == null || !this.isDirectConversation(conversation)) {
            return 0;
        }
        String otherUserKey = this.resolveOtherParticipantKey(conversation, currentUserKey);
        if (otherUserKey == null) {
            return 0;
        }
        return this.readStateService.getLastReadMessageId(conversation.getID(), otherUserKey);
    }

    private Long resolveOtherParticipantLastReadAt(JimConversation conversation, String currentUserKey) {
        if (conversation == null || !this.isDirectConversation(conversation)) {
            return null;
        }
        String otherUserKey = this.resolveOtherParticipantKey(conversation, currentUserKey);
        if (otherUserKey == null) {
            return null;
        }
        return this.readStateService.getLastReadAt(conversation.getID(), otherUserKey);
    }

    public List<Map<String, Object>> toMessageReceiptMaps(JimConversation conversation, JimMessage message, String currentUserKey) {
        ApplicationUser viewer = this.userManager.getUserByKey(currentUserKey);
        ArrayList<Map<String, Object>> receipts = new ArrayList<Map<String, Object>>();
        for (JimGroupMember member : this.groupService.listMembers(conversation.getID(), currentUserKey)) {
            String memberKey = member.getUserKey();
            if (memberKey == null || memberKey.equals(message.getSenderUserKey())) continue;
            ApplicationUser user = this.userManager.getUserByKey(memberKey);
            int lastRead = this.readStateService.getLastReadMessageId(conversation.getID(), memberKey);
            boolean read = message.getID() <= lastRead;
            LinkedHashMap<String, Object> receipt = new LinkedHashMap<String, Object>();
            receipt.put("userKey", memberKey);
            receipt.put("displayName", user != null ? user.getDisplayName() : memberKey);
            receipt.put("avatarUrl", this.resolveAvatarUrl(viewer, user));
            receipt.put("read", read);
            receipt.put("readAt", read ? this.readStateService.getLastReadAt(conversation.getID(), memberKey) : null);
            receipts.add(receipt);
        }
        receipts.sort((a, b) -> {
            boolean readB;
            boolean readA = Boolean.TRUE.equals(a.get("read"));
            if (readA != (readB = Boolean.TRUE.equals(b.get("read")))) {
                return readA ? -1 : 1;
            }
            return String.valueOf(a.get("displayName")).compareToIgnoreCase(String.valueOf(b.get("displayName")));
        });
        return receipts;
    }

    private boolean isDirectConversation(JimConversation conversation) {
        return conversation != null && JimConversationType.DIRECT.name().equals(conversation.getConversationType());
    }

    private boolean isPinned(JimMessage message) {
        Integer pinned = message.getPinned();
        return pinned != null && pinned != 0;
    }

    private boolean isWithinDeleteWindow(JimMessage message) {
        Long createdAt = message.getCreatedAt();
        if (createdAt == null) {
            return false;
        }
        return JimMessageLifecycle.isWithinDeleteWindow(createdAt, System.currentTimeMillis());
    }

    private boolean isWithinEditWindow(JimMessage message) {
        Long createdAt = message.getCreatedAt();
        if (createdAt == null) {
            return false;
        }
        return JimMessageLifecycle.isWithinEditWindow(createdAt, System.currentTimeMillis());
    }

    private String buildAttachmentUrl(int attachmentId, String action) {
        return "/rest/jim/1.0/attachments/" + attachmentId + "/" + action;
    }

    /**
     * Authenticated avatar-serve URL for a GROUP/PROJECT conversation with a
     * custom avatar, versioned for cache-busting. Null when no custom avatar is
     * set (the client then falls back to initials / group glyph). Relative path
     * mirrors {@link #buildAttachmentUrl}.
     */
    private String buildConversationAvatarUrl(JimConversation conversation) {
        if (conversation == null || conversation.getAvatarStoragePath() == null
                || conversation.getAvatarStoragePath().trim().isEmpty()) {
            return null;
        }
        long version = conversation.getAvatarVersion() == null ? 0L : conversation.getAvatarVersion();
        return "/rest/jim/1.0/conversations/" + conversation.getID() + "/avatar?v=" + version;
    }

    private String resolveOtherParticipantKey(JimConversation conversation, String currentUserKey) {
        if (currentUserKey.equals(conversation.getUserAKey())) {
            return conversation.getUserBKey();
        }
        return conversation.getUserAKey();
    }

    private String resolveAvatarUrl(ApplicationUser viewer, ApplicationUser target) {
        if (viewer == null || target == null) {
            return null;
        }
        try {
            URI uri = this.avatarService.getAvatarURL(viewer, target, Avatar.Size.XXLARGE);
            return uri == null ? null : uri.toString();
        }
        catch (AvatarsDisabledException ex) {
            return null;
        }
        catch (RuntimeException ex) {
            return null;
        }
    }

    private static final class ReplyContext {
        private final Map<Integer, JimMessage> replyMessages;
        private final Map<Integer, List<JimAttachment>> replyAttachments;

        private ReplyContext(Map<Integer, JimMessage> replyMessages, Map<Integer, List<JimAttachment>> replyAttachments) {
            this.replyMessages = replyMessages;
            this.replyAttachments = replyAttachments;
        }
    }
}

