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
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimPresenceService;
import com.corbitlogic.jira.internalmessenger.service.JimReactionService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import com.corbitlogic.jira.internalmessenger.util.JimMessageFlags;
import com.corbitlogic.jira.internalmessenger.util.JimMessageLifecycle;
import com.corbitlogic.jira.internalmessenger.util.JimSanitizer;
import java.net.URI;
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

    public JimRestJsonMapper(UserManager userManager, AvatarService avatarService, JimPermissionService permissionService, JimAttachmentService attachmentService, JimReadStateService readStateService, JimMessageService messageService, JimReactionService reactionService, JimPresenceService presenceService, JimGroupService groupService, JimAdminSettingsService adminSettingsService) {
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
    }

    public Map<String, Object> toConversationMap(JimConversation conversation, String currentUserKey, ApplicationUser viewer, int unreadCount) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", conversation.getID());
        item.put("type", conversation.getConversationType());
        item.put("lastMessagePreview", JimSanitizer.sanitizeText(conversation.getLastMessagePreview()));
        item.put("lastMessageAt", conversation.getLastMessageAt());
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
            item.put("avatarUrl", null);
            item.put("otherUserActive", false);
            item.put("memberCount", this.groupService.countMembers(conversation.getID()));
            item.put("ownerUserKey", conversation.getCreatedByUserKey());
            item.put("canManage", currentUserKey != null && currentUserKey.equals(conversation.getCreatedByUserKey()));
            if (isProjectChat) {
                item.put("projectKey", conversation.getProjectKey());
            }
            return item;
        }
        String otherUserKey = this.resolveOtherParticipantKey(conversation, currentUserKey);
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
        item.put("issueSummary", JimSanitizer.sanitizeText(message.getIssueSummary()));
        item.put("issueUrl", message.getIssueUrl());
        this.enrichIssueDetails(item, message, viewer);
        item.put("actorUserKey", message.getActorUserKey());
        item.put("actorDisplayName", JimSanitizer.sanitizeText(message.getActorDisplayName()));
        item.put("createdAt", message.getCreatedAt());
        item.put("edited", edited);
        item.put("editedAt", edited ? message.getEditedAt() : null);
        item.put("deleted", deleted);
        item.put("deletedAt", deleted ? message.getDeletedAt() : null);
        item.put("pinned", !deleted && this.isPinned(message));
        if (message.getSenderUserKey() != null && (sender = this.userManager.getUserByKey(message.getSenderUserKey())) != null) {
            item.put("senderDisplayName", sender.getDisplayName());
        }
        int currentUserLastRead = this.readStateService.getLastReadMessageId(message.getConversationId(), currentUserKey);
        int otherParticipantLastRead = this.resolveOtherParticipantLastRead(conversation, currentUserKey);
        boolean seenByOther = ownMessage && this.isDirectConversation(conversation) && message.getID() <= otherParticipantLastRead;
        item.put("ownMessage", ownMessage);
        item.put("readByCurrentUser", message.getID() <= currentUserLastRead);
        item.put("seenByOther", seenByOther);
        item.put("seenAt", seenByOther ? this.resolveOtherParticipantLastReadAt(conversation, currentUserKey) : null);
        item.put("canEdit", ownMessage && isUserMessage && !deleted);
        item.put("canDelete", ownMessage && isUserMessage && !deleted && this.isWithinDeleteWindow(message));
        item.put("replyTo", this.buildReplyPreview(message, replyContext));
        item.put("attachments", deleted ? Collections.emptyList() : this.toAttachmentMaps(attachments != null ? attachments : Collections.emptyList()));
        item.put("reactions", deleted ? Collections.emptyList() : this.toReactionMaps(reactions, currentUserKey));
        return item;
    }

    private void enrichIssueDetails(Map<String, Object> item, JimMessage message, ApplicationUser viewer) {
        if (!JimEventType.ISSUE_LINK.name().equals(message.getEventType()) || message.getIssueKey() == null || message.getIssueKey().isEmpty()) {
            return;
        }
        try {
            MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(message.getIssueKey());
            if (issue == null || !ComponentAccessor.getPermissionManager().hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue)issue, viewer)) {
                return;
            }
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
        item.put("downloadUrl", this.buildAttachmentUrl(attachment.getID(), "download"));
        boolean bl = imagePreviewAllowed = !"IMAGE".equals(attachment.getFileKind()) || this.adminSettingsService.isImagePreviewEnabled();
        if (("IMAGE".equals(attachment.getFileKind()) || "AUDIO".equals(attachment.getFileKind())) && imagePreviewAllowed) {
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
        ArrayList<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        if (members == null) {
            return items;
        }
        for (JimGroupMember member : members) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("userKey", member.getUserKey());
            item.put("role", member.getRole());
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
        if ("AUDIO".equals(first.getFileKind())) {
            return "Voice message";
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

    private String buildAttachmentUrl(int attachmentId, String action) {
        return "/rest/jim/1.0/attachments/" + attachmentId + "/" + action;
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

