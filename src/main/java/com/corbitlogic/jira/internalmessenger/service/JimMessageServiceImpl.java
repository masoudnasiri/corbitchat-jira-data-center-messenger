/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.activeobjects.external.ActiveObjects
 *  com.atlassian.jira.component.ComponentAccessor
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.jira.user.util.UserManager
 *  net.java.ao.DBParam
 *  net.java.ao.Query
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.corbitlogic.jira.internalmessenger.ao.JimAttachment;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimEventLog;
import com.corbitlogic.jira.internalmessenger.ao.JimGroupMember;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentPolicy;
import com.corbitlogic.jira.internalmessenger.model.JimBodyFormat;
import com.corbitlogic.jira.internalmessenger.model.JimConversationType;
import com.corbitlogic.jira.internalmessenger.model.JimEventType;
import com.corbitlogic.jira.internalmessenger.model.JimSenderType;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import com.corbitlogic.jira.internalmessenger.service.JimAttachmentService;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimPresenceService;
import com.corbitlogic.jira.internalmessenger.service.JimPushService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import com.corbitlogic.jira.internalmessenger.util.JimConversationKeys;
import com.corbitlogic.jira.internalmessenger.util.JimMessageFlags;
import com.corbitlogic.jira.internalmessenger.util.JimMessageLifecycle;
import com.corbitlogic.jira.internalmessenger.util.JimSanitizer;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.java.ao.DBParam;
import net.java.ao.Query;

public class JimMessageServiceImpl
implements JimMessageService {
    public static final String DELETE_WINDOW_EXPIRED_MESSAGE = "Messages can only be deleted within 10 minutes.";
    public static final String EDIT_WINDOW_EXPIRED_MESSAGE = "Messages can only be edited within 30 minutes.";
    private final ActiveObjects activeObjects;
    private final JimConversationService conversationService;
    private final JimPermissionService permissionService;
    private final JimReadStateService readStateService;
    private final JimAttachmentService attachmentService;
    private final JimPresenceService presenceService;
    private final JimPushService pushService;
    private final JimAdminSettingsService adminSettingsService;

    public JimMessageServiceImpl(ActiveObjects activeObjects, JimConversationService conversationService, JimPermissionService permissionService, JimReadStateService readStateService, JimAttachmentService attachmentService, JimPresenceService presenceService, JimPushService pushService, JimAdminSettingsService adminSettingsService) {
        this.activeObjects = activeObjects;
        this.conversationService = conversationService;
        this.permissionService = permissionService;
        this.readStateService = readStateService;
        this.attachmentService = attachmentService;
        this.presenceService = presenceService;
        this.pushService = pushService;
        this.adminSettingsService = adminSettingsService;
    }

    private boolean pushAllowedForEvent(JimEventType eventType) {
        if (eventType == JimEventType.MENTION) {
            return this.adminSettingsService.isMentionNotificationsEnabled();
        }
        if (eventType == JimEventType.ASSIGNMENT) {
            return this.adminSettingsService.isAssignmentNotificationsEnabled();
        }
        return true;
    }

    @Override
    public JimMessage sendUserMessage(int conversationId, String senderUserKey, String body) {
        return this.sendUserMessage(conversationId, senderUserKey, body, null);
    }

    @Override
    public JimMessage sendUserMessage(int conversationId, String senderUserKey, String body, Long replyToMessageId) {
        this.validateSendUserMessageRequest(conversationId, senderUserKey, body);
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.permissionService.requireSameUser(authenticatedUserKey, senderUserKey);
        this.conversationService.getConversationForUser(conversationId, senderUserKey);
        if (replyToMessageId != null) {
            this.validateReplyToMessage(conversationId, replyToMessageId);
        }
        String normalizedBody = JimValidation.validateMessageBody(body);
        long now = System.currentTimeMillis();
        JimMessage created = (JimMessage)this.activeObjects.executeInTransaction(() -> {
            JimMessage message = (JimMessage)this.activeObjects.create(JimMessage.class, new DBParam[0]);
            message.setConversationId(conversationId);
            message.setSenderType(JimSenderType.USER.name());
            message.setSenderUserKey(senderUserKey);
            message.setBody(normalizedBody);
            message.setBodyFormat(JimBodyFormat.TEXT.name());
            message.setEventType(JimEventType.NORMAL.name());
            message.setCreatedAt(now);
            message.setEdited(0);
            message.setDeleted(0);
            if (replyToMessageId != null) {
                message.setReplyToMessageId(replyToMessageId);
            }
            message.save();
            this.conversationService.touchConversation(conversationId, normalizedBody, senderUserKey);
            return message;
        });
        this.notifyGroupMentionsSafely(conversationId, senderUserKey, normalizedBody, created.getID());
        this.notifyDirectRecipientPushSafely(conversationId, senderUserKey, normalizedBody, created.getID());
        return created;
    }

    private void notifyDirectRecipientPushSafely(int conversationId, String senderUserKey, String preview, int messageId) {
        try {
            String recipient;
            JimConversation conversation = this.conversationService.getConversation(conversationId);
            if (!JimConversationType.DIRECT.name().equals(conversation.getConversationType())) {
                return;
            }
            String string = recipient = senderUserKey.equals(conversation.getUserAKey()) ? conversation.getUserBKey() : conversation.getUserAKey();
            if (recipient == null || recipient.equals(senderUserKey) || this.presenceService.isViewingConversation(recipient, conversationId)) {
                return;
            }
            ApplicationUser sender = ComponentAccessor.getUserManager().getUserByKey(senderUserKey);
            String senderName = sender != null ? sender.getDisplayName() : "New message";
            java.util.LinkedHashMap<String, String> chatPayload = new java.util.LinkedHashMap<String, String>();
            chatPayload.put("title", senderName);
            chatPayload.put("body", JimMessageServiceImpl.excerptForPush(preview));
            chatPayload.put("tag", "jim-conv-" + conversationId);
            chatPayload.put("type", "chat_message");
            chatPayload.put("url", "/plugins/servlet/jim/chat");
            chatPayload.put("conversationId", String.valueOf(conversationId));
            if (messageId > 0) {
                chatPayload.put("messageId", String.valueOf(messageId));
            }
            this.pushService.pushToUserAsync(recipient, chatPayload);
        }
        catch (RuntimeException runtimeException) {
            // empty catch block
        }
    }

    private static String excerptForPush(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "New message";
        }
        String trimmed = body.trim();
        return trimmed.length() > 140 ? trimmed.substring(0, 140) + "..." : trimmed;
    }

    private static String buildPushTitleForEvent(JimEventType eventType, String issueKey) {
        switch (eventType) {
            case ASSIGNMENT: {
                return issueKey != null ? issueKey + " assigned to you" : "New assignment";
            }
            case MENTION: {
                return issueKey != null ? "You were mentioned in " + issueKey : "You were mentioned";
            }
            case COMMENT: {
                return issueKey != null ? "New comment on " + issueKey : "New comment";
            }
            case STATUS_CHANGE: {
                return issueKey != null ? issueKey + " status changed" : "Status changed";
            }
        }
        return "Jira Assistant";
    }

    private void notifyGroupMentionsSafely(int conversationId, String senderUserKey, String body, int messageId) {
        try {
            JimGroupMember[] members;
            if (body == null || body.indexOf(64) < 0) {
                return;
            }
            JimConversation conversation = this.conversationService.getConversation(conversationId);
            String type = conversation.getConversationType();
            if (!JimConversationType.GROUP.name().equals(type) && !JimConversationType.PROJECT.name().equals(type)) {
                return;
            }
            String groupName = conversation.getGroupName() != null && !conversation.getGroupName().trim().isEmpty() ? conversation.getGroupName().trim() : "a group chat";
            UserManager jiraUserManager = ComponentAccessor.getUserManager();
            ApplicationUser sender = jiraUserManager.getUserByKey(senderUserKey);
            String senderName = sender != null ? sender.getDisplayName() : senderUserKey;
            String excerpt = body.length() > 120 ? body.substring(0, 120) + "..." : body;
            for (JimGroupMember member : members = (JimGroupMember[])this.activeObjects.find(JimGroupMember.class, Query.select().where("CONVERSATION_ID = ?", new Object[]{conversationId}))) {
                ApplicationUser target;
                String targetKey = member.getUserKey();
                if (targetKey == null || targetKey.equals(senderUserKey) || (target = jiraUserManager.getUserByKey(targetKey)) == null || !target.isActive() || target.getDisplayName() == null || !body.contains("@" + target.getDisplayName())) continue;
                try {
                    this.createSystemMessage(targetKey, JimEventType.MENTION, senderUserKey, senderName, null, null, null, senderName + " mentioned you in \"" + groupName + "\": " + excerpt, "MENTION|chat-message:" + messageId + "|" + targetKey);
                }
                catch (RuntimeException runtimeException) {
                    // empty catch block
                }
            }
        }
        catch (RuntimeException runtimeException) {
            // empty catch block
        }
    }

    @Override
    public JimMessage sendIssueLinkMessage(int conversationId, String senderUserKey, String issueKey, String issueSummary, String issueUrl, String body) {
        String normalizedBody;
        JimValidation.requirePositiveId(conversationId, "conversationId");
        JimValidation.requireNonBlank(senderUserKey, "senderUserKey");
        JimValidation.requireNonBlank(issueKey, "issueKey");
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.permissionService.requireSameUser(authenticatedUserKey, senderUserKey);
        this.conversationService.getConversationForUser(conversationId, senderUserKey);
        String string = normalizedBody = body != null ? body.trim() : "";
        if (!normalizedBody.isEmpty()) {
            normalizedBody = JimValidation.validateMessageBody(normalizedBody);
        }
        String storedBody = normalizedBody;
        String previewText = "Issue: " + issueKey.trim();
        long now = System.currentTimeMillis();
        JimMessage created = (JimMessage)this.activeObjects.executeInTransaction(() -> {
            JimMessage message = (JimMessage)this.activeObjects.create(JimMessage.class, new DBParam[0]);
            message.setConversationId(conversationId);
            message.setSenderType(JimSenderType.USER.name());
            message.setSenderUserKey(senderUserKey);
            message.setBody(storedBody);
            message.setBodyFormat(JimBodyFormat.TEXT.name());
            message.setEventType(JimEventType.ISSUE_LINK.name());
            message.setIssueKey(JimSanitizer.sanitizeText(issueKey.trim()));
            message.setIssueSummary(JimSanitizer.sanitizeText(issueSummary));
            message.setIssueUrl(issueUrl);
            message.setCreatedAt(now);
            message.setEdited(0);
            message.setDeleted(0);
            message.save();
            this.conversationService.touchConversation(conversationId, previewText, senderUserKey);
            return message;
        });
        this.notifyGroupMentionsSafely(conversationId, senderUserKey, storedBody, created.getID());
        this.notifyDirectRecipientPushSafely(conversationId, senderUserKey, "Shared issue " + issueKey.trim() + (String)(storedBody.isEmpty() ? "" : ": " + storedBody), created.getID());
        return created;
    }

    @Override
    public JimMessage editUserMessage(int messageId, String editorUserKey, String body) {
        JimValidation.requirePositiveId(messageId, "messageId");
        JimValidation.requireNonBlank(editorUserKey, "editorUserKey");
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.permissionService.requireSameUser(authenticatedUserKey, editorUserKey);
        JimMessage message = this.getMessageForParticipant(messageId, editorUserKey);
        this.requireUserOwnedMessage(message, editorUserKey);
        if (JimMessageFlags.isDeleted(message)) {
            throw JimMessengerException.badRequest("Deleted messages cannot be edited");
        }
        long now = System.currentTimeMillis();
        Long createdAt = message.getCreatedAt();
        if (createdAt == null || !JimMessageLifecycle.isWithinEditWindow(createdAt, now)) {
            throw JimMessengerException.forbidden(EDIT_WINDOW_EXPIRED_MESSAGE);
        }
        List<JimAttachment> attachments = this.attachmentService.listAttachmentsForMessage(messageId);
        String normalizedBody = this.normalizeEditableBody(body, attachments);
        int conversationId = message.getConversationId();
        return (JimMessage)this.activeObjects.executeInTransaction(() -> {
            message.setBody(normalizedBody);
            message.setEdited(1);
            message.setEditedAt(now);
            message.save();
            this.regressOtherParticipantReadState(conversationId, editorUserKey, messageId);
            this.refreshConversationPreviewIfNeeded(conversationId, messageId);
            return message;
        });
    }

    @Override
    public JimMessage deleteUserMessage(int messageId, String deleterUserKey) {
        JimValidation.requirePositiveId(messageId, "messageId");
        JimValidation.requireNonBlank(deleterUserKey, "deleterUserKey");
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.permissionService.requireSameUser(authenticatedUserKey, deleterUserKey);
        JimMessage message = this.getMessageForParticipant(messageId, deleterUserKey);
        this.requireUserOwnedMessage(message, deleterUserKey);
        if (JimMessageFlags.isDeleted(message)) {
            throw JimMessengerException.badRequest("Message is already deleted");
        }
        long now = System.currentTimeMillis();
        Long createdAt = message.getCreatedAt();
        if (createdAt == null || !JimMessageLifecycle.isWithinDeleteWindow(createdAt, now)) {
            throw JimMessengerException.forbidden(DELETE_WINDOW_EXPIRED_MESSAGE);
        }
        int conversationId = message.getConversationId();
        return (JimMessage)this.activeObjects.executeInTransaction(() -> {
            message.setDeleted(1);
            message.setDeletedAt(now);
            message.setDeletedByUserKey(deleterUserKey);
            message.setBody("");
            message.save();
            this.refreshConversationPreviewIfNeeded(conversationId, messageId);
            return message;
        });
    }

    @Override
    public JimMessage getMessageForParticipant(int messageId, String userKey) {
        JimValidation.requirePositiveId(messageId, "messageId");
        JimValidation.requireNonBlank(userKey, "userKey");
        JimMessage message = (JimMessage)this.activeObjects.get(JimMessage.class, messageId);
        if (message == null) {
            throw JimMessengerException.notFound("Message not found");
        }
        this.conversationService.getConversationForUser(message.getConversationId(), userKey);
        return message;
    }

    @Override
    public Map<Integer, JimMessage> findMessagesByIds(Collection<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        HashSet<Integer> normalizedIds = new HashSet<Integer>();
        for (Integer messageId : messageIds) {
            if (messageId == null || messageId <= 0) continue;
            normalizedIds.add(messageId);
        }
        if (normalizedIds.isEmpty()) {
            return Collections.emptyMap();
        }
        HashMap<Integer, JimMessage> result = new HashMap<Integer, JimMessage>();
        for (Integer messageId : normalizedIds) {
            JimMessage message = (JimMessage)this.activeObjects.get(JimMessage.class, messageId);
            if (message == null) continue;
            result.put(messageId, message);
        }
        return result;
    }

    @Override
    public JimMessage createSystemMessage(String targetUserKey, JimEventType eventType, String actorUserKey, String actorDisplayName, String issueKey, String issueSummary, String issueUrl, String body) {
        return this.createSystemMessage(targetUserKey, eventType, actorUserKey, actorDisplayName, issueKey, issueSummary, issueUrl, body, null);
    }

    @Override
    public JimMessage createSystemMessage(String targetUserKey, JimEventType eventType, String actorUserKey, String actorDisplayName, String issueKey, String issueSummary, String issueUrl, String body, String eventFingerprint) {
        String fingerprint;
        this.validateSystemMessageRequest(targetUserKey, eventType, actorUserKey, actorDisplayName, issueKey, issueSummary, issueUrl, body);
        this.permissionService.requireActiveUser(targetUserKey);
        String normalizedTarget = targetUserKey.trim();
        String normalizedBody = JimValidation.requireNonBlank(body, "body");
        JimEventType normalizedEventType = JimValidation.requireEventType(eventType);
        String string = fingerprint = eventFingerprint != null && !eventFingerprint.trim().isEmpty() ? eventFingerprint.trim() : JimConversationKeys.buildEventFingerprint(normalizedEventType.name(), normalizedTarget, this.trimToNull(issueKey), this.trimToNull(actorUserKey));
        if (this.isDuplicateEvent(fingerprint)) {
            return null;
        }
        JimConversation conversation = this.conversationService.getOrCreateSystemConversation(normalizedTarget);
        long now = System.currentTimeMillis();
        JimMessage created = (JimMessage)this.activeObjects.executeInTransaction(() -> this.createSystemMessageInTransaction(fingerprint, conversation, normalizedBody, normalizedEventType, issueKey, issueSummary, issueUrl, actorUserKey, actorDisplayName, now, normalizedTarget));
        if (created != null) {
            try {
                if (this.pushAllowedForEvent(normalizedEventType) && !this.presenceService.isViewingConversation(normalizedTarget, conversation.getID())) {
                    java.util.LinkedHashMap<String, String> assistantPayload = new java.util.LinkedHashMap<String, String>();
                    assistantPayload.put("title", JimMessageServiceImpl.buildPushTitleForEvent(normalizedEventType, this.trimToNull(issueKey)));
                    assistantPayload.put("body", JimMessageServiceImpl.excerptForPush(normalizedBody));
                    assistantPayload.put("tag", "jim-alarm-" + created.getID());
                    assistantPayload.put("type", "jira_assistant");
                    assistantPayload.put("url", "/plugins/servlet/jim/chat");
                    assistantPayload.put("conversationId", String.valueOf(conversation.getID()));
                    assistantPayload.put("messageId", String.valueOf(created.getID()));
                    this.pushService.pushToUserAsync(normalizedTarget, assistantPayload);
                }
            }
            catch (RuntimeException runtimeException) {
                // empty catch block
            }
        }
        return created;
    }

    @Override
    public List<JimMessage> listMessages(int conversationId, int limit, Integer beforeMessageId) {
        this.validateListMessagesRequest(conversationId, limit, beforeMessageId);
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.conversationService.getConversationForUser(conversationId, authenticatedUserKey);
        int normalizedLimit = JimValidation.normalizeListLimit(limit);
        Integer normalizedBeforeMessageId = JimValidation.normalizeBeforeMessageId(beforeMessageId);
        Query query = normalizedBeforeMessageId == null ? Query.select().where("CONVERSATION_ID = ?", new Object[]{conversationId}).order("ID DESC").limit(normalizedLimit) : Query.select().where("CONVERSATION_ID = ? AND ID < ?", new Object[]{conversationId, normalizedBeforeMessageId}).order("ID DESC").limit(normalizedLimit);
        JimMessage[] messages = (JimMessage[])this.activeObjects.find(JimMessage.class, query);
        ArrayList<JimMessage> result = new ArrayList<JimMessage>(Arrays.asList(messages));
        Collections.reverse(result);
        return result;
    }

    @Override
    public JimMessage setPinned(int messageId, String userKey, boolean pinned) {
        JimValidation.requirePositiveId(messageId, "messageId");
        JimValidation.requireNonBlank(userKey, "userKey");
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.permissionService.requireSameUser(authenticatedUserKey, userKey);
        JimMessage message = this.getMessageForParticipant(messageId, userKey);
        if (JimMessageFlags.isDeleted(message)) {
            throw JimMessengerException.badRequest("Deleted messages cannot be pinned");
        }
        int conversationId = message.getConversationId();
        return (JimMessage)this.activeObjects.executeInTransaction(() -> {
            if (pinned) {
                JimMessage[] currentlyPinned;
                for (JimMessage other : currentlyPinned = (JimMessage[])this.activeObjects.find(JimMessage.class, Query.select().where("CONVERSATION_ID = ? AND PINNED = 1", new Object[]{conversationId}))) {
                    if (other.getID() == messageId) continue;
                    other.setPinned(0);
                    other.save();
                }
            }
            message.setPinned(pinned ? 1 : 0);
            message.save();
            return message;
        });
    }

    @Override
    public JimMessage markActioned(int messageId, String userKey) {
        JimValidation.requirePositiveId(messageId, "messageId");
        JimValidation.requireNonBlank(userKey, "userKey");
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.permissionService.requireSameUser(authenticatedUserKey, userKey);
        JimMessage message = this.getMessageForParticipant(messageId, userKey);
        if (JimMessageFlags.isDeleted(message)) {
            throw JimMessengerException.badRequest("Deleted messages cannot be marked as actioned");
        }
        Integer current = message.getActioned();
        if (current != null && current != 0) {
            // Already actioned; preserve the original timestamp so the
            // operation is idempotent.
            return message;
        }
        long now = System.currentTimeMillis();
        return (JimMessage)this.activeObjects.executeInTransaction(() -> {
            message.setActioned(1);
            message.setActionedAt(now);
            message.save();
            return message;
        });
    }

    @Override
    public JimMessage getPinnedMessage(int conversationId, String userKey) {
        JimValidation.requirePositiveId(conversationId, "conversationId");
        JimValidation.requireNonBlank(userKey, "userKey");
        this.conversationService.getConversationForUser(conversationId, userKey);
        JimMessage[] pinned = (JimMessage[])this.activeObjects.find(JimMessage.class, Query.select().where("CONVERSATION_ID = ? AND PINNED = 1 AND (DELETED IS NULL OR DELETED = 0)", new Object[]{conversationId}).order("ID DESC").limit(1));
        return pinned.length == 0 ? null : pinned[0];
    }

    boolean isDuplicateEvent(String eventFingerprint) {
        JimValidation.requireNonBlank(eventFingerprint, "eventFingerprint");
        JimEventLog[] existing = (JimEventLog[])this.activeObjects.find(JimEventLog.class, Query.select().where("EVENT_FINGERPRINT = ?", new Object[]{eventFingerprint}).limit(1));
        return existing.length > 0;
    }

    void validateSendUserMessageRequest(int conversationId, String senderUserKey, String body) {
        JimValidation.requirePositiveId(conversationId, "conversationId");
        JimValidation.requireNonBlank(senderUserKey, "senderUserKey");
        JimValidation.validateMessageBody(body);
    }

    void validateSystemMessageRequest(String targetUserKey, JimEventType eventType, String actorUserKey, String actorDisplayName, String issueKey, String issueSummary, String issueUrl, String body) {
        JimValidation.requireNonBlank(targetUserKey, "targetUserKey");
        JimValidation.requireEventType(eventType);
        JimValidation.requireNonBlank(body, "body");
        JimValidation.requireMaxLength(actorUserKey, 255, "actorUserKey");
        JimValidation.requireMaxLength(actorDisplayName, 255, "actorDisplayName");
        JimValidation.requireMaxLength(issueKey, 64, "issueKey");
        JimValidation.requireMaxLength(issueSummary, 500, "issueSummary");
        JimValidation.requireMaxLength(issueUrl, 1024, "issueUrl");
        JimValidation.requireMaxLength(body, 4000, "body");
    }

    void validateListMessagesRequest(int conversationId, int limit, Integer beforeMessageId) {
        JimValidation.requirePositiveId(conversationId, "conversationId");
        if (limit < 0) {
            throw new JimMessengerException("limit must be zero or positive");
        }
        JimValidation.normalizeBeforeMessageId(beforeMessageId);
    }

    private void validateReplyToMessage(int conversationId, Long replyToMessageId) {
        if (replyToMessageId == null || replyToMessageId <= 0L) {
            throw JimMessengerException.badRequest("replyToMessageId is invalid");
        }
        JimMessage parent = (JimMessage)this.activeObjects.get(JimMessage.class, replyToMessageId.intValue());
        if (parent == null || parent.getConversationId() != conversationId) {
            throw JimMessengerException.badRequest("replyToMessageId must belong to the same conversation");
        }
    }

    private void requireUserOwnedMessage(JimMessage message, String userKey) {
        if (!JimSenderType.USER.name().equals(message.getSenderType())) {
            throw JimMessengerException.forbidden("System messages cannot be modified");
        }
        if (!userKey.equals(message.getSenderUserKey())) {
            throw JimMessengerException.forbidden("Only the sender can modify this message");
        }
    }

    private String normalizeEditableBody(String body, List<JimAttachment> attachments) {
        boolean hasAttachments;
        boolean bl = hasAttachments = attachments != null && !attachments.isEmpty();
        if (hasAttachments) {
            String sanitized = JimSanitizer.sanitizeText(body);
            String normalized = sanitized == null ? "" : JimValidation.normalizeWhitespace(sanitized);
            JimValidation.requireMaxLength(normalized, 4000, "body");
            return normalized;
        }
        return JimValidation.validateMessageBody(body);
    }

    private void regressOtherParticipantReadState(int conversationId, String editorUserKey, int messageId) {
        JimConversation conversation = this.conversationService.getConversationForUser(conversationId, editorUserKey);
        String otherUserKey = this.resolveOtherParticipantKey(conversation, editorUserKey);
        if (otherUserKey == null) {
            return;
        }
        this.readStateService.regressReadStateBeforeMessage(conversationId, otherUserKey, messageId);
    }

    private void refreshConversationPreviewIfNeeded(int conversationId, int messageId) {
        int latestMessageId = this.findLatestMessageId(conversationId);
        if (latestMessageId != messageId) {
            return;
        }
        JimMessage[] latest = (JimMessage[])this.activeObjects.find(JimMessage.class, Query.select().where("CONVERSATION_ID = ?", new Object[]{conversationId}).order("ID DESC").limit(1));
        String latestSender = latest.length > 0 ? latest[0].getSenderUserKey() : null;
        this.conversationService.touchConversation(conversationId, this.resolveLatestPreview(conversationId), latestSender);
    }

    private String resolveLatestPreview(int conversationId) {
        JimMessage[] messages = (JimMessage[])this.activeObjects.find(JimMessage.class, Query.select().where("CONVERSATION_ID = ?", new Object[]{conversationId}).order("ID DESC").limit(1));
        if (messages.length == 0) {
            return "";
        }
        JimMessage latest = messages[0];
        if (JimMessageFlags.isDeleted(latest)) {
            return "Message deleted";
        }
        String body = latest.getBody();
        if (body != null && !body.trim().isEmpty()) {
            return JimValidation.truncatePreview(body);
        }
        List<JimAttachment> attachments = this.attachmentService.listAttachmentsForMessage(latest.getID());
        if (attachments.isEmpty()) {
            return "";
        }
        JimAttachment first = attachments.get(0);
        return JimAttachmentPolicy.buildPreviewText(null, first.getFileKind(), first.getOriginalFilename());
    }

    private int findLatestMessageId(int conversationId) {
        JimMessage[] messages = (JimMessage[])this.activeObjects.find(JimMessage.class, Query.select().where("CONVERSATION_ID = ?", new Object[]{conversationId}).order("ID DESC").limit(1));
        return messages.length == 0 ? 0 : messages[0].getID();
    }

    private String resolveOtherParticipantKey(JimConversation conversation, String currentUserKey) {
        if (currentUserKey.equals(conversation.getUserAKey())) {
            return conversation.getUserBKey();
        }
        if (currentUserKey.equals(conversation.getUserBKey())) {
            return conversation.getUserAKey();
        }
        return null;
    }

    private String resolveSystemPreview(JimEventType eventType, String issueKey, String body) {
        if (eventType == JimEventType.ASSIGNMENT && issueKey != null && !issueKey.isEmpty()) {
            return "Assigned: " + issueKey;
        }
        if (eventType == JimEventType.MENTION && issueKey != null && !issueKey.isEmpty()) {
            return "Mention: " + issueKey;
        }
        if (eventType == JimEventType.STATUS_CHANGE && issueKey != null && !issueKey.isEmpty()) {
            return "Status: " + issueKey;
        }
        return JimValidation.truncatePreview(body);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private JimMessage createSystemMessageInTransaction(String fingerprint, JimConversation conversation, String normalizedBody, JimEventType normalizedEventType, String issueKey, String issueSummary, String issueUrl, String actorUserKey, String actorDisplayName, long now, String normalizedTarget) {
        if (this.isDuplicateEvent(fingerprint)) {
            return null;
        }
        JimMessage message = (JimMessage)this.activeObjects.create(JimMessage.class, new DBParam[0]);
        message.setConversationId(conversation.getID());
        message.setSenderType(JimSenderType.SYSTEM.name());
        message.setSenderUserKey("JIRA_ASSISTANT");
        message.setBody(normalizedBody);
        message.setBodyFormat(JimBodyFormat.SYSTEM_CARD.name());
        message.setEventType(normalizedEventType.name());
        message.setIssueKey(this.trimToNull(issueKey));
        message.setIssueSummary(this.trimToNull(issueSummary));
        message.setIssueUrl(this.trimToNull(issueUrl));
        message.setActorUserKey(this.trimToNull(actorUserKey));
        message.setActorDisplayName(this.trimToNull(actorDisplayName));
        message.setCreatedAt(now);
        message.setEdited(0);
        message.setDeleted(0);
        message.save();
        JimEventLog eventLog = (JimEventLog)this.activeObjects.create(JimEventLog.class, new DBParam[0]);
        eventLog.setEventFingerprint(fingerprint);
        eventLog.setEventType(normalizedEventType.name());
        eventLog.setIssueKey(this.trimToNull(issueKey));
        eventLog.setTargetUserKey(normalizedTarget);
        eventLog.setCreatedAt(now);
        eventLog.save();
        this.conversationService.touchConversation(conversation.getID(), this.resolveSystemPreview(normalizedEventType, this.trimToNull(issueKey), normalizedBody));
        return message;
    }
}

