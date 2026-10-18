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
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.mobile.push.MobilePushEvent;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import com.corbitlogic.jira.internalmessenger.service.JimAttachmentService;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimPresenceService;
import com.corbitlogic.jira.internalmessenger.service.JimPushService;
import com.corbitlogic.jira.internalmessenger.service.JimMessageUserStateService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import com.corbitlogic.jira.internalmessenger.service.JimReviewerService;
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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JimMessageServiceImpl.class);
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
    private final JimMobilePushService mobilePushService;
    private final JimMessageUserStateService userStateService;

    public JimMessageServiceImpl(ActiveObjects activeObjects, JimConversationService conversationService, JimPermissionService permissionService, JimReadStateService readStateService, JimAttachmentService attachmentService, JimPresenceService presenceService, JimPushService pushService, JimAdminSettingsService adminSettingsService, JimMobilePushService mobilePushService, JimMessageUserStateService userStateService) {
        this.activeObjects = activeObjects;
        this.conversationService = conversationService;
        this.permissionService = permissionService;
        this.readStateService = readStateService;
        this.attachmentService = attachmentService;
        this.presenceService = presenceService;
        this.pushService = pushService;
        this.adminSettingsService = adminSettingsService;
        this.mobilePushService = mobilePushService;
        this.userStateService = userStateService;
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
        this.triggerWorkspaceAgentSafely(conversationId, created);
        return created;
    }

    /**
     * Best-effort post-commit hook for the ChatGPT Workspace Agent integration.
     * Enqueues a trigger job when the message is eligible (direct-to-bot or bot
     * mention). Never throws and never blocks message creation. Resolved lazily via
     * ComponentAccessor so message creation has no hard dependency on the integration.
     */
    private void triggerWorkspaceAgentSafely(int conversationId, JimMessage created) {
        try {
            JimWorkspaceAgentService agent = JimWorkspaceAgentServiceImpl.instance();
            if (agent != null) {
                agent.onUserMessage(conversationId, created);
            }
        } catch (Throwable ignore) {
            // Integration is optional; message creation must never fail because of it.
        }
    }

    @Override
    public JimMessage forwardUserMessage(int targetConversationId, String forwarderUserKey, int sourceMessageId) {
        JimValidation.requirePositiveId(targetConversationId, "conversationId");
        JimValidation.requirePositiveId(sourceMessageId, "sourceMessageId");
        JimValidation.requireNonBlank(forwarderUserKey, "forwarderUserKey");
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.permissionService.requireSameUser(authenticatedUserKey, forwarderUserKey);
        // Forwarder must participate in the TARGET conversation...
        this.conversationService.getConversationForUser(targetConversationId, forwarderUserKey);
        // ...and must be allowed to read the SOURCE message (participant of its
        // conversation). getMessageForParticipant throws otherwise → forward is
        // permission-safe on both ends.
        JimMessage source = this.getMessageForParticipant(sourceMessageId, forwarderUserKey);
        if (JimMessageFlags.isDeleted(source)) {
            throw JimMessengerException.badRequest("Deleted messages cannot be forwarded");
        }
        if (!JimSenderType.USER.name().equals(source.getSenderType())) {
            throw JimMessengerException.badRequest("Only user messages can be forwarded");
        }
        // Sprint 08 Fix-2: forwarding carries the FULL message — attachments
        // included. Attachment-only messages have an empty body, which is
        // valid to forward as long as there is something to carry.
        List<JimAttachment> sourceAttachments = this.attachmentService.listAttachmentsForMessage(sourceMessageId);
        String rawBody = source.getBody() != null ? source.getBody().trim() : "";
        if (rawBody.isEmpty() && sourceAttachments.isEmpty()) {
            throw JimMessengerException.badRequest("This message has no content to forward");
        }
        String normalizedBody = rawBody.isEmpty() ? "" : JimValidation.validateMessageBody(rawBody);
        // Conversation preview mirrors the send/upload paths: body when
        // present, otherwise the first attachment's kind-aware label.
        String preview = normalizedBody;
        if (preview.isEmpty()) {
            JimAttachment first = sourceAttachments.get(0);
            preview = com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentPolicy.buildPreviewText(
                    null, first.getFileKind(), first.getOriginalFilename(),
                    com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentPolicy.isVoiceAttachment(
                            first.getFileKind(), first.getVoice()));
        }
        final String touchPreview = preview;
        // Chain to the ULTIMATE original author so re-forwarding preserves the
        // true author rather than the last relayer.
        final long originMessageId = source.getForwardedFromMessageId() != null
                ? source.getForwardedFromMessageId()
                : (long) source.getID();
        final String originUserKey = source.getForwardedFromUserKey() != null
                ? source.getForwardedFromUserKey()
                : source.getSenderUserKey();
        final String originDisplayName = source.getForwardedFromDisplayName() != null
                ? source.getForwardedFromDisplayName()
                : this.resolveDisplayName(source.getSenderUserKey());
        long now = System.currentTimeMillis();
        JimMessage created = (JimMessage)this.activeObjects.executeInTransaction(() -> {
            JimMessage message = (JimMessage)this.activeObjects.create(JimMessage.class, new DBParam[0]);
            message.setConversationId(targetConversationId);
            message.setSenderType(JimSenderType.USER.name());
            message.setSenderUserKey(forwarderUserKey);
            message.setBody(normalizedBody);
            message.setBodyFormat(JimBodyFormat.TEXT.name());
            message.setEventType(JimEventType.NORMAL.name());
            message.setCreatedAt(now);
            message.setEdited(0);
            message.setDeleted(0);
            message.setForwardedFromMessageId(originMessageId);
            message.setForwardedFromUserKey(originUserKey);
            message.setForwardedFromDisplayName(originDisplayName);
            message.setForwardedAt(now);
            message.save();
            this.conversationService.touchConversation(targetConversationId, touchPreview, forwarderUserKey);
            return message;
        });
        // Sprint 08 Fix-2: clone attachments (own file copies + rows) onto the
        // forwarded message so images/files/videos/voice/contacts survive.
        if (!sourceAttachments.isEmpty()) {
            this.attachmentService.cloneAttachmentsForForward(sourceMessageId, created);
        }
        this.notifyGroupMentionsSafely(targetConversationId, forwarderUserKey, normalizedBody, created.getID());
        this.notifyDirectRecipientPushSafely(targetConversationId, forwarderUserKey, touchPreview, created.getID());
        return created;
    }

    private String resolveDisplayName(String userKey) {
        if (userKey == null) {
            return null;
        }
        ApplicationUser user = ComponentAccessor.getUserManager().getUserByKey(userKey);
        return user != null ? user.getDisplayName() : userKey;
    }

    private void notifyDirectRecipientPushSafely(int conversationId, String senderUserKey, String preview, int messageId) {
        try {
            JimConversation conversation = this.conversationService.getConversation(conversationId);
            String type = conversation.getConversationType();
            // Sprint 08 Fix-3: GROUP/PROJECT chat messages fan out to every
            // other member with the same preference-aware preview pipeline as
            // direct messages (the recipient's detail level / preview toggle
            // decides what is actually shown). Mentioned members are skipped
            // here — they receive the dedicated MENTION notification instead,
            // so nobody gets double-notified for one message.
            if (JimConversationType.GROUP.name().equals(type)
                    || JimConversationType.PROJECT.name().equals(type)) {
                this.notifyGroupRecipientsPushSafely(conversation, senderUserKey, preview, messageId);
                return;
            }
            if (!JimConversationType.DIRECT.name().equals(type)) {
                return;
            }
            String recipient = senderUserKey.equals(conversation.getUserAKey())
                    ? conversation.getUserBKey() : conversation.getUserAKey();
            if (recipient == null || recipient.equals(senderUserKey) || this.presenceService.isViewingConversation(recipient, conversationId)) {
                return;
            }
            ApplicationUser sender = ComponentAccessor.getUserManager().getUserByKey(senderUserKey);
            String senderName = sender != null ? sender.getDisplayName() : "New message";
            this.sendWebChatPush(recipient, senderName, JimMessageServiceImpl.excerptForPush(preview), conversationId, messageId);
            // Native mobile push (Sprint 05/05B): a SEPARATE channel from web push.
            // The raw preview is passed to the sender, which decides — per the
            // recipient's notification preferences — how much (if any) to include.
            this.sendMobileChatPush(recipient, senderUserKey, senderName, preview, conversationId, messageId);
        }
        catch (RuntimeException runtimeException) {
            // empty catch block
        }
    }

    /**
     * Group/project message push (Sprint 08 Fix-3). Title is the group name;
     * the candidate preview is "Sender: text" — whether the text is actually
     * shown is decided per-recipient by the mobile push sender (detail level +
     * message-preview toggle), so disabled previews stay private ("New
     * message" only). Skips the sender, members currently viewing the
     * conversation, and members mentioned in the body (they get the MENTION
     * notification instead).
     */
    private void notifyGroupRecipientsPushSafely(JimConversation conversation, String senderUserKey,
                                                 String preview, int messageId) {
        try {
            int conversationId = conversation.getID();
            String groupName = conversation.getGroupName() != null && !conversation.getGroupName().trim().isEmpty()
                    ? conversation.getGroupName().trim() : "Group chat";
            UserManager jiraUserManager = ComponentAccessor.getUserManager();
            ApplicationUser sender = jiraUserManager.getUserByKey(senderUserKey);
            String senderName = sender != null ? sender.getDisplayName() : "New message";
            String candidatePreview = senderName + ": " + (preview != null ? preview : "");
            String body = preview != null ? preview : "";
            JimGroupMember[] members = (JimGroupMember[])this.activeObjects.find(JimGroupMember.class,
                    Query.select().where("CONVERSATION_ID = ?", new Object[]{conversationId}));
            for (JimGroupMember member : members) {
                String targetKey = member.getUserKey();
                if (targetKey == null || targetKey.equals(senderUserKey)
                        || this.presenceService.isViewingConversation(targetKey, conversationId)) {
                    continue;
                }
                ApplicationUser target = jiraUserManager.getUserByKey(targetKey);
                if (target == null || !target.isActive()) {
                    continue;
                }
                // Mentioned members get the dedicated MENTION push instead.
                if (target.getDisplayName() != null && body.contains("@" + target.getDisplayName())) {
                    continue;
                }
                this.sendWebChatPush(targetKey, groupName,
                        JimMessageServiceImpl.excerptForPush(candidatePreview), conversationId, messageId);
                this.sendMobileChatPush(targetKey, senderUserKey, groupName, candidatePreview,
                        conversationId, messageId);
            }
        }
        catch (RuntimeException runtimeException) {
            // empty catch block
        }
    }

    private void sendWebChatPush(String recipient, String title, String excerpt, int conversationId, int messageId) {
        java.util.LinkedHashMap<String, String> chatPayload = new java.util.LinkedHashMap<String, String>();
        chatPayload.put("title", title);
        chatPayload.put("body", excerpt);
        chatPayload.put("tag", "jim-conv-" + conversationId);
        chatPayload.put("type", "chat_message");
        chatPayload.put("url", "/plugins/servlet/jim/chat");
        chatPayload.put("conversationId", String.valueOf(conversationId));
        if (messageId > 0) {
            chatPayload.put("messageId", String.valueOf(messageId));
        }
        this.pushService.pushToUserAsync(recipient, chatPayload);
    }

    /**
     * Enqueue a native mobile push for a direct message. The message body is
     * passed to the push sender as a candidate preview only; whether it is
     * actually included is decided by the recipient's notification preferences
     * (detail level + message-preview toggle). The client always re-fetches full
     * data from the BFF (which rechecks permissions) after the notification tap.
     */
    private void sendMobileChatPush(String recipient, String senderUserKey, String senderName,
                                    String preview, int conversationId, int messageId) {
        try {
            if (this.mobilePushService == null) {
                return;
            }
            MobilePushEvent event = MobilePushEvent.builder(MobilePushEvent.CHAT_MESSAGE, JimMobileFeatures.CHAT)
                    .category(MobilePushEvent.CAT_CHAT)
                    .entityId(String.valueOf(conversationId))
                    .deepLink("corbithub://chat/conversation/" + conversationId)
                    .actorName(senderName)
                    .actorUserKey(senderUserKey)
                    .chatPreview(preview)
                    .genericBody("New message")
                    .dedupeKey("chat|" + conversationId + "|" + messageId + "|" + recipient)
                    .extra("conversationId", String.valueOf(conversationId))
                    .extra("messageId", String.valueOf(messageId))
                    .build();
            this.mobilePushService.sendToUser(recipient, event);
        } catch (RuntimeException ex) {
            // Never let mobile push failures affect message delivery.
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
            case ISSUE_ATTACHMENT: {
                return issueKey != null ? "New attachment on " + issueKey : "New attachment";
            }
            case REPORT_SUBMITTED:
            case REPORT_SUBMITTED_TO_ME: {
                return issueKey != null ? "New report on " + issueKey : "New report submitted";
            }
            case REPORT_REPLY: {
                return issueKey != null ? "New reply to a report on " + issueKey : "New report reply";
            }
            case REPORT_MENTION: {
                return issueKey != null ? "You were mentioned in a report on " + issueKey : "You were mentioned in a report";
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
            // Sprint 12 governance: the body is RETAINED in the row for audit /
            // hidden-reviewer complaint review. Normal participants never see it
            // (JimRestJsonMapper returns "" and the conversation preview shows
            // "Message deleted", both driven by the deleted flag, not the body).
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
        return this.createSystemMessage(targetUserKey, eventType, actorUserKey, actorDisplayName, issueKey, issueSummary, issueUrl, body, null, null, eventFingerprint);
    }

    @Override
    public JimMessage createSystemMessage(String targetUserKey, JimEventType eventType, String actorUserKey, String actorDisplayName, String issueKey, String issueSummary, String issueUrl, String body, String eventText, String eventData, String eventFingerprint) {
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
        JimMessage created = (JimMessage)this.activeObjects.executeInTransaction(() -> this.createSystemMessageInTransaction(fingerprint, conversation, normalizedBody, normalizedEventType, issueKey, issueSummary, issueUrl, actorUserKey, actorDisplayName, this.trimToNull(eventText), this.trimToNull(eventData), now, normalizedTarget));
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
                    // Native mobile push (Sprint 05/05B), feature- and preference-aware.
                    this.sendMobileSystemPush(normalizedTarget, normalizedEventType, actorUserKey,
                            actorDisplayName, this.trimToNull(issueKey), conversation.getID(),
                            created.getID(), fingerprint);
                }
            }
            catch (RuntimeException runtimeException) {
                // empty catch block
            }
        }
        return created;
    }

    // Window within which a Jira comment (mention) and an attachment-add on the
    // same issue are treated as ONE combined action (Assistant enrichment).
    private static final long COMBINE_WINDOW_MS = 15L * 60L * 1000L;

    @Override
    public java.util.Set<String> attachFilesToRecentMentions(String issueKey, String eventDataJson) {
        java.util.LinkedHashSet<String> users = new java.util.LinkedHashSet<String>();
        String key = this.trimToNull(issueKey);
        String data = this.trimToNull(eventDataJson);
        if (key == null || data == null) {
            return users;
        }
        long cutoff = System.currentTimeMillis() - COMBINE_WINDOW_MS;
        JimMessage[] mentions = (JimMessage[]) this.activeObjects.find(JimMessage.class,
                Query.select().where("EVENT_TYPE = ? AND ISSUE_KEY = ? AND CREATED_AT >= ? AND DELETED = 0",
                        new Object[]{JimEventType.MENTION.name(), key, cutoff}).order("ID DESC"));
        if (mentions.length == 0) {
            return users;
        }
        this.activeObjects.executeInTransaction(() -> {
            for (JimMessage m : mentions) {
                String uk = this.resolveSystemConversationUserKey(m.getConversationId());
                if (uk != null) {
                    users.add(uk);
                }
                String existing = m.getEventData();
                if (existing == null || existing.trim().isEmpty()) {
                    m.setEventData(data);
                    m.save();
                }
            }
            return null;
        });
        return users;
    }

    @Override
    public void reconcileMentionWithStandaloneAttachment(String targetUserKey, String issueKey) {
        String uk = this.trimToNull(targetUserKey);
        String key = this.trimToNull(issueKey);
        if (uk == null || key == null) {
            return;
        }
        JimConversation conversation = this.conversationService.getOrCreateSystemConversation(uk);
        long cutoff = System.currentTimeMillis() - COMBINE_WINDOW_MS;
        JimMessage[] mentions = (JimMessage[]) this.activeObjects.find(JimMessage.class,
                Query.select().where("CONVERSATION_ID = ? AND EVENT_TYPE = ? AND ISSUE_KEY = ? AND CREATED_AT >= ? AND DELETED = 0",
                        new Object[]{conversation.getID(), JimEventType.MENTION.name(), key, cutoff}).order("ID DESC").limit(1));
        JimMessage[] attachmentCards = (JimMessage[]) this.activeObjects.find(JimMessage.class,
                Query.select().where("CONVERSATION_ID = ? AND EVENT_TYPE = ? AND ISSUE_KEY = ? AND CREATED_AT >= ? AND DELETED = 0",
                        new Object[]{conversation.getID(), JimEventType.ISSUE_ATTACHMENT.name(), key, cutoff}).order("ID DESC"));
        if (mentions.length == 0 || attachmentCards.length == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        this.activeObjects.executeInTransaction(() -> {
            JimMessage mention = mentions[0];
            String mentionData = mention.getEventData();
            if (mentionData == null || mentionData.trim().isEmpty()) {
                mention.setEventData(attachmentCards[0].getEventData());
                mention.save();
            }
            // Remove the now-redundant standalone attachment card(s): the files
            // are shown on the combined mention card instead.
            for (JimMessage card : attachmentCards) {
                card.setDeleted(1);
                card.setDeletedAt(now);
                card.save();
            }
            return null;
        });
    }

    @Override
    public String findScheduleRequesterKey(String issueKey) {
        String key = this.trimToNull(issueKey);
        if (key == null) {
            return null;
        }
        JimMessage[] cards = (JimMessage[]) this.activeObjects.find(JimMessage.class,
                Query.select().where("EVENT_TYPE = ? AND ISSUE_KEY = ?",
                        new Object[]{JimEventType.SCHEDULE_APPROVAL.name(), key}).order("ID DESC").limit(1));
        if (cards.length == 0) {
            return null;
        }
        String data = cards[0].getEventData();
        if (data == null) {
            return null;
        }
        for (String line : data.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0 && "requesterKey".equals(line.substring(0, eq).trim())) {
                String v = line.substring(eq + 1).trim();
                return v.isEmpty() ? null : v;
            }
        }
        return null;
    }

    private String resolveSystemConversationUserKey(int conversationId) {
        try {
            JimConversation conversation = (JimConversation) this.activeObjects.get(JimConversation.class, conversationId);
            return conversation != null ? conversation.getUserAKey() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Enqueue a privacy-safe native mobile push for a system/assistant message.
     * Issue-related events (with an issue key) deep-link to Issue Detail and are
     * gated on the {@code issueDetail} mobile feature; chat/assistant mentions
     * without an issue key deep-link to the assistant conversation and are gated
     * on {@code chat}. No issue summary or body is ever included.
     */
    private void sendMobileSystemPush(String targetUserKey, JimEventType eventType, String actorUserKey,
                                      String actorDisplayName, String issueKey, int conversationId,
                                      int messageId, String fingerprint) {
        try {
            if (this.mobilePushService == null) {
                return;
            }
            MobilePushEvent event;
            // A safe, high-level summary (no issue description/comments/fields).
            String summary = JimMessageServiceImpl.buildPushTitleForEvent(eventType, issueKey);
            String actor = this.trimToNull(actorUserKey);
            String actorName = this.trimToNull(actorDisplayName);
            String dedupe = fingerprint != null ? fingerprint
                    : (eventType.name() + "|" + issueKey + "|" + targetUserKey + "|" + messageId);
            boolean isMention = eventType == JimEventType.MENTION;
            if (issueKey != null) {
                event = MobilePushEvent.builder(mobileEventTypeForIssue(eventType), JimMobileFeatures.ISSUE_DETAIL)
                        .category(isMention ? MobilePushEvent.CAT_MENTION : MobilePushEvent.CAT_TASK)
                        .entityId(issueKey)
                        .deepLink("corbithub://issue/" + issueKey)
                        .issueKey(issueKey)
                        .actorName(actorName)
                        .actorUserKey(actor)
                        .summaryText(summary)
                        .genericBody(isMention ? "You were mentioned" : "Task update")
                        .dedupeKey(dedupe)
                        .extra("issueKey", issueKey)
                        .build();
            } else {
                event = MobilePushEvent.builder(MobilePushEvent.CHAT_MESSAGE,
                                isMention ? JimMobileFeatures.CHAT : JimMobileFeatures.CHAT)
                        .category(isMention ? MobilePushEvent.CAT_MENTION : MobilePushEvent.CAT_CHAT)
                        .entityId(String.valueOf(conversationId))
                        .deepLink("corbithub://chat/conversation/" + conversationId)
                        .actorName(actorName)
                        .actorUserKey(actor)
                        .summaryText(summary)
                        .genericBody(isMention ? "You were mentioned" : "New message")
                        .dedupeKey(dedupe)
                        .extra("conversationId", String.valueOf(conversationId))
                        .build();
            }
            this.mobilePushService.sendToUser(targetUserKey, event);
        } catch (RuntimeException ex) {
            // Never let mobile push failures affect message delivery.
        }
    }

    private static String mobileEventTypeForIssue(JimEventType eventType) {
        switch (eventType) {
            case ASSIGNMENT:
                return MobilePushEvent.ISSUE_ASSIGNMENT;
            case STATUS_CHANGE:
                return MobilePushEvent.ISSUE_STATUS;
            case MENTION:
            case REPORT_MENTION:
                return MobilePushEvent.ISSUE_MENTION;
            case REPORT_SUBMITTED:
            case REPORT_SUBMITTED_TO_ME:
            case REPORT_REPLY:
                return MobilePushEvent.ISSUE_MENTION;
            default:
                return MobilePushEvent.ISSUE_STATUS;
        }
    }

    @Override
    public List<JimMessage> listMessages(int conversationId, int limit, Integer beforeMessageId) {
        return this.listMessages(conversationId, limit, beforeMessageId, null);
    }

    @Override
    public List<JimMessage> listMessages(int conversationId, int limit, Integer beforeMessageId, Long sinceMillis) {
        this.validateListMessagesRequest(conversationId, limit, beforeMessageId);
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        JimConversation conversation = this.conversationService.getConversationForUser(conversationId, authenticatedUserKey);
        int normalizedLimit = JimValidation.normalizeListLimit(limit);
        Integer normalizedBeforeMessageId = JimValidation.normalizeBeforeMessageId(beforeMessageId);
        // Dedicated Jira Assistant (SYSTEM) feed policy: NEVER apply an age-based
        // time window to the Assistant feed. Assistant items are notification
        // cards that are frequently older than the chat "recent" window. When any
        // client path (poll / refresh / resume / tab-switch / deep-link) sent
        // `since`, the Assistant query returned an empty page — and an empty page
        // also collapsed hasOlder to false in the mobile BFF — so the whole feed
        // disappeared. Root-cause fix at the service layer so EVERY caller (web,
        // mobile BFF, future) is safe. Only an explicit user-requested older
        // cursor (beforeMessageId) pages the Assistant; `since` is ignored for it.
        boolean systemConversation = conversation != null
                && JimConversationType.SYSTEM.name().equals(conversation.getConversationType());
        Long effectiveSince = systemConversation ? null : sinceMillis;
        if (systemConversation && sinceMillis != null && sinceMillis > 0L) {
            log.debug("Assistant feed: ignoring age window since={} for SYSTEM conversation {} "
                    + "(recurring-empty guard)", sinceMillis, conversationId);
        }
        Query query;
        if (normalizedBeforeMessageId != null) {
            // Older page (infinite scroll): messages before a cursor id.
            query = Query.select().where("CONVERSATION_ID = ? AND ID < ?",
                    new Object[]{conversationId, normalizedBeforeMessageId}).order("ID DESC").limit(normalizedLimit);
        } else if (effectiveSince != null && effectiveSince > 0L) {
            // Initial window (~last N days) for NORMAL chats only: messages at/after
            // `since`, capped by limit so a very active recent window can't blow up
            // the payload. Never reached for SYSTEM/Assistant conversations.
            query = Query.select().where("CONVERSATION_ID = ? AND CREATED_AT >= ?",
                    new Object[]{conversationId, effectiveSince}).order("ID DESC").limit(normalizedLimit);
        } else {
            query = Query.select().where("CONVERSATION_ID = ?",
                    new Object[]{conversationId}).order("ID DESC").limit(normalizedLimit);
        }
        JimMessage[] messages = (JimMessage[])this.activeObjects.find(JimMessage.class, query);
        ArrayList<JimMessage> result = new ArrayList<JimMessage>(Arrays.asList(messages));
        Collections.reverse(result);
        return result;
    }

    // ------------------------------------------------------------------ chat search
    private static final int SEARCH_MIN_QUERY = 2;
    private static final int SEARCH_MAX_LIMIT = 50;
    private static final int SEARCH_CONVERSATION_CAP = 300;   // bounded global scan
    private static final int SEARCH_SNIPPET_RADIUS = 60;

    /** Escape LIKE wildcards so user input can't broaden the match (values are parameterized). */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public java.util.Map<String, Object> searchMessages(String query, Integer conversationId, int limit, int offset) {
        return this.searchMessages(query, conversationId, null, limit, offset);
    }

    @Override
    public java.util.Map<String, Object> searchMessages(String query, Integer conversationId, String type, int limit, int offset) {
        String userKey = this.permissionService.requireAuthenticatedUserKey();
        String q = query == null ? "" : query.trim();
        if (q.length() < SEARCH_MIN_QUERY) {
            throw JimMessengerException.badRequest("Search query must be at least " + SEARCH_MIN_QUERY + " characters");
        }
        int max = limit < 1 ? 25 : Math.min(limit, SEARCH_MAX_LIMIT);
        int off = Math.max(0, offset);
        String needle = q.toLowerCase();
        String like = "%" + escapeLike(q) + "%";
        String typeFilter = type == null ? "" : type.trim().toUpperCase();

        // Scope: one membership-checked conversation, or all the user's conversations.
        List<JimConversation> convs = new ArrayList<JimConversation>();
        String scope;
        if (conversationId != null) {
            JimConversation c = this.conversationService.getConversationForUser(conversationId.intValue(), userKey);
            if (c != null) {
                convs.add(c);
            }
            scope = "conversation";
        } else {
            List<JimConversation> all = this.conversationService.listConversationsForUser(userKey);
            if (all != null) {
                convs.addAll(all);
            }
            scope = "global";
        }
        if (!typeFilter.isEmpty()) {
            List<JimConversation> filtered = new ArrayList<JimConversation>();
            for (JimConversation c : convs) {
                if (conversationMatchesType(c, userKey, typeFilter)) {
                    filtered.add(c);
                }
            }
            convs = filtered;
        }
        // Project chats are opened from the Jira project page and are not in
        // listConversationsForUser (sidebar is DIRECT/GROUP/SYSTEM only).
        // Global / type=PROJECT search must still cover PROJECT conversations
        // the caller is a participant of (explicit member or current lead).
        if (conversationId == null && (typeFilter.isEmpty() || "PROJECT".equals(typeFilter))) {
            java.util.Set<Integer> have = new java.util.HashSet<Integer>();
            for (JimConversation c : convs) {
                have.add(Integer.valueOf(c.getID()));
            }
            JimConversation[] projects = (JimConversation[]) this.activeObjects.find(JimConversation.class,
                    Query.select().where("CONVERSATION_TYPE = ?",
                            new Object[]{JimConversationType.PROJECT.name()}));
            for (JimConversation c : projects) {
                if (c == null || have.contains(Integer.valueOf(c.getID()))) {
                    continue;
                }
                if (c.getArchived() != null && c.getArchived().intValue() != 0) {
                    continue;
                }
                if (this.permissionService.isParticipant(c, userKey)) {
                    convs.add(c);
                    have.add(Integer.valueOf(c.getID()));
                }
            }
        }

        JimReviewerService reviewer = component(JimReviewerService.class);
        JimMessageUserStateService userState = this.userStateService;
        UserManager userManager = ComponentAccessor.getUserManager();
        // Per-conversation fetch cap: enough to paginate for a single conversation;
        // small for global so no conversation dominates and no unbounded scan occurs.
        int perConv = conversationId != null ? Math.min(off + max + 1, 200) : 25;

        // messageId -> result row (dedupe; body match wins over filename-only match).
        java.util.LinkedHashMap<Integer, Object[]> byId = new java.util.LinkedHashMap<Integer, Object[]>();
        int scannedConvs = 0;
        for (JimConversation c : convs) {
            if (scannedConvs++ >= SEARCH_CONVERSATION_CAP) {
                break;
            }
            java.util.Set<Integer> hidden;
            try {
                hidden = userState != null ? userState.hiddenMessageIds(userKey, c.getID())
                        : java.util.Collections.<Integer>emptySet();
            } catch (Throwable t) {
                hidden = java.util.Collections.<Integer>emptySet();
            }
            // Body matches (indexed by CONVERSATION_ID; LIKE is case-insensitive on MySQL).
            JimMessage[] byBody = (JimMessage[]) this.activeObjects.find(JimMessage.class,
                    Query.select().where("CONVERSATION_ID = ? AND BODY LIKE ? AND (DELETED IS NULL OR DELETED = 0)",
                            new Object[]{c.getID(), like}).order("ID DESC").limit(perConv));
            for (JimMessage m : byBody) {
                if (skipMessage(m, hidden, reviewer)) {
                    continue;
                }
                byId.put(Integer.valueOf(m.getID()), new Object[]{m, c, null});
            }
            // Attachment filename matches (join back to the owning message).
            try {
                JimAttachment[] byFile = (JimAttachment[]) this.activeObjects.find(JimAttachment.class,
                        Query.select().where("CONVERSATION_ID = ? AND ORIGINAL_FILENAME LIKE ? AND (DELETED IS NULL OR DELETED = 0)",
                                new Object[]{c.getID(), like}).order("ID DESC").limit(perConv));
                for (JimAttachment a : byFile) {
                    Integer mid = Integer.valueOf(a.getMessageId());
                    if (byId.containsKey(mid)) {
                        continue; // already a body hit
                    }
                    JimMessage m = this.activeObjects.get(JimMessage.class, mid);
                    if (m == null || m.getConversationId() != c.getID() || skipMessage(m, hidden, reviewer)) {
                        continue;
                    }
                    byId.put(mid, new Object[]{m, c, a.getOriginalFilename()});
                }
            } catch (Throwable ignore) {
                // attachment table optional; body results still returned
            }
        }

        // Sort newest-first by created time, then paginate by offset/limit.
        List<Object[]> rows = new ArrayList<Object[]>(byId.values());
        rows.sort((x, y) -> Long.compare(createdMillis((JimMessage) y[0]), createdMillis((JimMessage) x[0])));
        int total = rows.size();
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (int i = off; i < total && results.size() < max; i++) {
            Object[] row = rows.get(i);
            JimMessage m = (JimMessage) row[0];
            JimConversation c = (JimConversation) row[1];
            String matchedFilename = (String) row[2];
            String sourceText = matchedFilename != null ? matchedFilename : (m.getBody() != null ? m.getBody() : "");
            String snip = snippet(sourceText, needle);
            String matchedField = matchedFilename != null ? "filename"
                    : (looksLikeIssueKey(q) && sourceText.toUpperCase().contains(q.toUpperCase()) ? "issue" : "body");
            Map<String, Object> sender = new HashMap<String, Object>();
            sender.put("userKey", m.getSenderUserKey());
            sender.put("displayName", senderDisplayName(m, userManager));
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("conversationId", c.getID());
            item.put("conversationType", c.getConversationType());
            item.put("conversationTitle", conversationTitle(c, userKey, userManager));
            item.put("messageId", m.getID());
            item.put("sender", sender);
            item.put("senderUserKey", m.getSenderUserKey());
            item.put("senderDisplayName", senderDisplayName(m, userManager));
            item.put("createdAt", createdMillis(m));
            item.put("snippet", snip);
            item.put("highlightRanges", highlightRanges(snip, needle));
            item.put("attachmentIndicator", matchedFilename != null);
            item.put("attachmentMatched", matchedFilename != null);
            item.put("matchedField", matchedField);
            if (matchedFilename != null) {
                item.put("matchedFilename", matchedFilename);
            }
            item.put("eventType", m.getEventType());
            item.put("deepLink", "corbithub://chat/" + c.getID() + "?message=" + m.getID());
            item.put("canOpen", true);
            results.add(item);
        }
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("query", q);
        out.put("scope", scope);
        out.put("results", results);
        out.put("matchCount", total);
        Integer nextCursor = (off + results.size() < total) ? Integer.valueOf(off + results.size()) : null;
        out.put("nextCursor", nextCursor);
        return out;
    }

    @Override
    public java.util.Map<String, Object> listMessagesAround(int conversationId, int messageId, int before, int after) {
        String userKey = this.permissionService.requireAuthenticatedUserKey();
        this.conversationService.getConversationForUser(conversationId, userKey);
        int beforeN = before < 0 ? 0 : Math.min(before, 50);
        int afterN = after < 0 ? 0 : Math.min(after, 50);
        if (beforeN == 0 && afterN == 0) {
            beforeN = 25;
            afterN = 25;
        }
        JimMessageUserStateService userState = this.userStateService;
        java.util.Set<Integer> hidden;
        try {
            hidden = userState != null ? userState.hiddenMessageIds(userKey, conversationId)
                    : java.util.Collections.<Integer>emptySet();
        } catch (Throwable t) {
            hidden = java.util.Collections.<Integer>emptySet();
        }
        JimMessage target = this.activeObjects.get(JimMessage.class, messageId);
        if (target == null || target.getConversationId() != conversationId
                || skipMessage(target, hidden, component(JimReviewerService.class))) {
            throw JimMessengerException.notFound("Message not found");
        }
        JimMessage[] older = (JimMessage[]) this.activeObjects.find(JimMessage.class,
                Query.select().where("CONVERSATION_ID = ? AND ID < ? AND (DELETED IS NULL OR DELETED = 0)",
                        new Object[]{conversationId, messageId}).order("ID DESC").limit(beforeN + 8));
        JimMessage[] newer = (JimMessage[]) this.activeObjects.find(JimMessage.class,
                Query.select().where("CONVERSATION_ID = ? AND ID > ? AND (DELETED IS NULL OR DELETED = 0)",
                        new Object[]{conversationId, messageId}).order("ID ASC").limit(afterN + 8));
        JimReviewerService reviewer = component(JimReviewerService.class);
        List<JimMessage> beforeList = new ArrayList<JimMessage>();
        for (JimMessage m : older) {
            if (skipMessage(m, hidden, reviewer)) {
                continue;
            }
            beforeList.add(m);
            if (beforeList.size() >= beforeN) {
                break;
            }
        }
        Collections.reverse(beforeList);
        List<JimMessage> page = new ArrayList<JimMessage>(beforeList);
        page.add(target);
        int afterCount = 0;
        for (JimMessage m : newer) {
            if (skipMessage(m, hidden, reviewer)) {
                continue;
            }
            page.add(m);
            if (++afterCount >= afterN) {
                break;
            }
        }
        boolean hasMoreOlder = this.hasMessagesBefore(conversationId, page.isEmpty() ? messageId : page.get(0).getID());
        JimMessage[] oneNewer = (JimMessage[]) this.activeObjects.find(JimMessage.class,
                Query.select().where("CONVERSATION_ID = ? AND ID > ?",
                        new Object[]{conversationId, page.get(page.size() - 1).getID()}).order("ID ASC").limit(1));
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("_messages", page);
        out.put("targetMessageId", messageId);
        out.put("targetFound", true);
        out.put("hasMoreOlder", hasMoreOlder);
        out.put("hasMoreNewer", oneNewer.length > 0);
        return out;
    }

    @Override
    public java.util.Map<String, Object> mentionBadge(int conversationId, String userKey) {
        Map<String, Object> empty = emptyMentionBadge();
        if (userKey == null) {
            return empty;
        }
        JimConversation c;
        try {
            c = this.conversationService.getConversationForUser(conversationId, userKey);
        } catch (Throwable t) {
            return empty;
        }
        if (c == null || JimConversationType.SYSTEM.name().equals(c.getConversationType())) {
            return empty;
        }
        boolean self = userKey.equals(c.getUserAKey()) && userKey.equals(c.getUserBKey());
        if (self) {
            return empty;
        }
        ApplicationUser viewer = ComponentAccessor.getUserManager().getUserByKey(userKey);
        if (viewer == null) {
            return empty;
        }
        int lastRead = 0;
        try {
            lastRead = this.readStateService.getLastReadMessageId(conversationId, userKey);
        } catch (Throwable ignore) {
            lastRead = 0;
        }
        JimMessageUserStateService userState = this.userStateService;
        java.util.Set<Integer> hidden;
        try {
            hidden = userState != null ? userState.hiddenMessageIds(userKey, conversationId)
                    : java.util.Collections.<Integer>emptySet();
        } catch (Throwable t) {
            hidden = java.util.Collections.<Integer>emptySet();
        }
        JimMessage[] unread = (JimMessage[]) this.activeObjects.find(JimMessage.class,
                Query.select().where("CONVERSATION_ID = ? AND ID > ? AND (DELETED IS NULL OR DELETED = 0)",
                        new Object[]{conversationId, lastRead}).order("ID DESC").limit(80));
        int count = 0;
        Integer latest = null;
        for (JimMessage m : unread) {
            if (skipMessage(m, hidden, component(JimReviewerService.class))) {
                continue;
            }
            if (userKey.equals(m.getSenderUserKey())) {
                continue;
            }
            if (JimSenderType.SYSTEM.name().equals(m.getSenderType())) {
                continue;
            }
            if (!messageMentionsUser(m.getBody(), viewer)) {
                continue;
            }
            count++;
            if (latest == null || m.getID() > latest.intValue()) {
                latest = Integer.valueOf(m.getID());
            }
        }
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("mentionCount", count);
        out.put("unreadMentionCount", count);
        out.put("hasMention", count > 0);
        out.put("latestMentionMessageId", latest);
        return out;
    }

    private static Map<String, Object> emptyMentionBadge() {
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("mentionCount", 0);
        out.put("unreadMentionCount", 0);
        out.put("hasMention", false);
        out.put("latestMentionMessageId", null);
        return out;
    }

    private boolean conversationMatchesType(JimConversation c, String userKey, String typeFilter) {
        String actual = c.getConversationType() == null ? "" : c.getConversationType().toUpperCase();
        if ("SELF".equals(typeFilter)) {
            return JimConversationType.DIRECT.name().equals(actual)
                    && userKey != null && userKey.equals(c.getUserAKey()) && userKey.equals(c.getUserBKey());
        }
        return typeFilter.equals(actual);
    }

    private static boolean looksLikeIssueKey(String q) {
        return q != null && q.matches("[A-Za-z][A-Za-z0-9]+-\\d+");
    }

    private static List<Map<String, Object>> highlightRanges(String snippet, String needleLower) {
        List<Map<String, Object>> ranges = new ArrayList<Map<String, Object>>();
        if (snippet == null || needleLower == null || needleLower.isEmpty()) {
            return ranges;
        }
        String lower = snippet.toLowerCase();
        int idx = lower.indexOf(needleLower);
        if (idx >= 0) {
            Map<String, Object> r = new HashMap<String, Object>();
            r.put("start", idx);
            r.put("end", idx + needleLower.length());
            ranges.add(r);
        }
        return ranges;
    }

    private boolean messageMentionsUser(String body, ApplicationUser user) {
        if (body == null || body.isEmpty() || user == null) {
            return false;
        }
        String display = user.getDisplayName();
        if (display != null && !display.trim().isEmpty() && body.contains("@" + display.trim())) {
            return true;
        }
        String name = user.getName();
        if (name != null && body.contains("[~" + name + "]")) {
            return true;
        }
        if (user.getKey() != null && body.toLowerCase().contains("[~userkey:" + user.getKey().toLowerCase() + "]")) {
            return true;
        }
        try {
            java.lang.reflect.Method m = user.getClass().getMethod("getAccountId");
            Object accountId = m.invoke(user);
            if (accountId != null && body.contains("[~accountid:" + accountId + "]")) {
                return true;
            }
        } catch (Throwable ignore) {
            // Jira DC users may not have accountId
        }
        return false;
    }

    /** Skip deleted, per-user hidden, and hidden-system-account (pluginuser) messages. */
    private boolean skipMessage(JimMessage m, java.util.Set<Integer> hidden, JimReviewerService reviewer) {
        if (m == null) {
            return true;
        }
        if (m.getDeleted() != null && m.getDeleted().intValue() == 1) {
            return true;
        }
        if (hidden != null && hidden.contains(Integer.valueOf(m.getID()))) {
            return true;
        }
        if (reviewer != null && m.getSenderUserKey() != null
                && reviewer.isHiddenSystemAccountUserKey(m.getSenderUserKey())) {
            return true;
        }
        return false;
    }

    private static long createdMillis(JimMessage m) {
        try {
            return m.getCreatedAt() != null ? m.getCreatedAt().longValue() : 0L;
        } catch (Throwable t) {
            return 0L;
        }
    }

    private String senderDisplayName(JimMessage m, UserManager userManager) {
        if (JimSenderType.SYSTEM.name().equals(m.getSenderType())) {
            return "Jira Assistant";
        }
        String key = m.getSenderUserKey();
        if (key != null && userManager != null) {
            ApplicationUser u = userManager.getUserByKey(key);
            if (u != null) {
                return u.getDisplayName();
            }
        }
        return key != null ? key : "";
    }

    /** Human title for a conversation from the viewer's perspective (never leaks pluginuser). */
    private String conversationTitle(JimConversation c, String viewerKey, UserManager userManager) {
        String type = c.getConversationType();
        if (JimConversationType.SYSTEM.name().equals(type)) {
            return "Jira Assistant";
        }
        if (JimConversationType.GROUP.name().equals(type) || JimConversationType.PROJECT.name().equals(type)) {
            String name = c.getGroupName();
            if (name != null && !name.trim().isEmpty()) {
                return name;
            }
            return JimConversationType.PROJECT.name().equals(type) && c.getProjectKey() != null
                    ? c.getProjectKey() + " chat" : "Group";
        }
        // DIRECT: the other participant.
        String other = viewerKey != null && viewerKey.equals(c.getUserAKey()) ? c.getUserBKey() : c.getUserAKey();
        if (other != null && userManager != null) {
            ApplicationUser u = userManager.getUserByKey(other);
            if (u != null) {
                return u.getDisplayName();
            }
        }
        return other != null ? other : "Direct message";
    }

    /** Build a short snippet around the first case-insensitive match; mention tokens stripped. */
    private String snippet(String body, String needleLower) {
        if (body == null) {
            return "";
        }
        String clean = body.replaceAll("\\[~[^\\]]+\\]", "").replaceAll("\\s+", " ").trim();
        String lower = clean.toLowerCase();
        int idx = lower.indexOf(needleLower);
        if (idx < 0) {
            return clean.length() > 160 ? clean.substring(0, 160) + "\u2026" : clean;
        }
        int start = Math.max(0, idx - SEARCH_SNIPPET_RADIUS);
        int end = Math.min(clean.length(), idx + needleLower.length() + SEARCH_SNIPPET_RADIUS);
        String snip = clean.substring(start, end);
        if (start > 0) {
            snip = "\u2026" + snip;
        }
        if (end < clean.length()) {
            snip = snip + "\u2026";
        }
        return snip;
    }

    private static <T> T component(Class<T> type) {
        try {
            return ComponentAccessor.getOSGiComponentInstanceOfType(type);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean hasMessagesBefore(int conversationId, int messageId) {
        if (messageId <= 0) {
            return false;
        }
        this.permissionService.requireAuthenticatedUserKey();
        JimMessage[] older = (JimMessage[]) this.activeObjects.find(JimMessage.class,
                Query.select().where("CONVERSATION_ID = ? AND ID < ?",
                        new Object[]{conversationId, messageId}).order("ID DESC").limit(1));
        return older.length > 0;
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
        return JimAttachmentPolicy.buildPreviewText(null, first.getFileKind(), first.getOriginalFilename(),
                JimAttachmentPolicy.isVoiceAttachment(first.getFileKind(), first.getVoice()));
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

    private JimMessage createSystemMessageInTransaction(String fingerprint, JimConversation conversation, String normalizedBody, JimEventType normalizedEventType, String issueKey, String issueSummary, String issueUrl, String actorUserKey, String actorDisplayName, String eventText, String eventData, long now, String normalizedTarget) {
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
        message.setEventText(eventText);
        message.setEventData(eventData);
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

