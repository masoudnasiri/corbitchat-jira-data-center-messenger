/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.activeobjects.external.ActiveObjects
 *  com.atlassian.jira.component.ComponentAccessor
 *  com.atlassian.jira.user.ApplicationUser
 *  net.java.ao.DBParam
 *  net.java.ao.Query
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimAttachment;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentPolicy;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentStorageService;
import com.corbitlogic.jira.internalmessenger.model.JimBodyFormat;
import com.corbitlogic.jira.internalmessenger.model.JimConversationType;
import com.corbitlogic.jira.internalmessenger.model.JimEventType;
import com.corbitlogic.jira.internalmessenger.model.JimSenderType;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimPresenceService;
import com.corbitlogic.jira.internalmessenger.service.JimPushService;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.java.ao.DBParam;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimAttachmentService {
    private static final Logger log = LoggerFactory.getLogger(JimAttachmentService.class);
    private final ActiveObjects activeObjects;
    private final JimConversationService conversationService;
    private final JimPermissionService permissionService;
    private final JimAttachmentStorageService storageService;
    private final JimPresenceService presenceService;
    private final JimPushService pushService;
    private final JimAdminSettingsService adminSettingsService;

    public JimAttachmentService(ActiveObjects activeObjects, JimConversationService conversationService, JimPermissionService permissionService, JimAttachmentStorageService storageService, JimPresenceService presenceService, JimPushService pushService, JimAdminSettingsService adminSettingsService) {
        this.activeObjects = activeObjects;
        this.conversationService = conversationService;
        this.permissionService = permissionService;
        this.storageService = storageService;
        this.presenceService = presenceService;
        this.pushService = pushService;
        this.adminSettingsService = adminSettingsService;
    }

    private void enforceAdminAttachmentSettings(File uploadedFile, String contentType, String originalFilename) {
        if (!this.adminSettingsService.isAttachmentsEnabled()) {
            throw JimMessengerException.forbidden("Attachments have been disabled by the administrator");
        }
        long maxBytes = (long)this.adminSettingsService.getMaxAttachmentSizeMb() * 1024L * 1024L;
        if (uploadedFile != null && uploadedFile.length() > maxBytes) {
            throw JimMessengerException.badRequest("File exceeds the maximum allowed size of " + this.adminSettingsService.getMaxAttachmentSizeMb() + " MB");
        }
        // Voice messages are recorded by the browser's MediaRecorder, not
        // user-uploaded files. They have known browser-generated extensions
        // (webm/ogg/mp4/etc.) that admins won't typically include in the
        // extension allowlist, so we skip the allowlist for audio content
        // types. The hardcoded BLOCKED_EXTENSIONS in JimAttachmentPolicy
        // and the audio MIME safe list still apply.
        String kind = JimAttachmentPolicy.resolveFileKind(contentType);
        if ("AUDIO".equals(kind)) {
            return;
        }
        // Sprint 07 Fix-3: camera-captured videos likewise have device-generated
        // extensions (mp4/3gp) that admins won't typically allowlist. The video
        // MIME safe list and BLOCKED_EXTENSIONS still apply.
        if ("VIDEO".equals(kind)) {
            return;
        }
        // Sprint 07 Fix-3: app-generated single-contact vCards (.vcf) are a
        // built-in share feature, not user file uploads — skip the allowlist.
        if (JimAttachmentPolicy.isContactCardType(contentType)) {
            return;
        }
        String allowedExtensions = this.adminSettingsService.getAllowedExtensions();
        if (allowedExtensions != null && !allowedExtensions.isEmpty()) {
            String sanitized = JimAttachmentPolicy.sanitizeOriginalFilename(originalFilename);
            int dot = sanitized.lastIndexOf(46);
            String extension = dot >= 0 && dot < sanitized.length() - 1 ? sanitized.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
            boolean allowed = false;
            for (String candidate : allowedExtensions.split(",")) {
                if (!candidate.trim().equals(extension)) continue;
                allowed = true;
                break;
            }
            if (!allowed) {
                throw JimMessengerException.badRequest("File extension is not in the administrator's allowlist (" + allowedExtensions + ")");
            }
        }
    }

    public UploadResult uploadAttachment(int conversationId, String senderUserKey, String optionalBody, File uploadedFile, String contentType, String originalFilename) throws IOException {
        // Legacy call path (web + older mobile clients): no voice hint. Audio is
        // treated as a voice note, matching all behavior before Sprint 07 Fix-2
        // (the web only produces audio via its MediaRecorder voice composer).
        return this.uploadAttachment(conversationId, senderUserKey, optionalBody, uploadedFile, contentType, originalFilename, null);
    }

    /**
     * Sprint 07 Fix-2: {@code voiceHint} distinguishes a recorded voice note
     * (TRUE) from a picked/shared audio file (FALSE). Null means the client
     * did not say — treated as voice for AUDIO to preserve legacy behavior.
     * The hint is only meaningful for AUDIO content; other kinds store FALSE.
     */
    public UploadResult uploadAttachment(int conversationId, String senderUserKey, String optionalBody, File uploadedFile, String contentType, String originalFilename, Boolean voiceHint) throws IOException {
        JimValidation.requirePositiveId(conversationId, "conversationId");
        JimValidation.requireNonBlank(senderUserKey, "senderUserKey");
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.permissionService.requireSameUser(authenticatedUserKey, senderUserKey);
        this.conversationService.getConversationForUser(conversationId, senderUserKey);
        String normalizedBody = this.normalizeOptionalBody(optionalBody);
        if (normalizedBody.isEmpty() && (uploadedFile == null || !uploadedFile.exists())) {
            throw JimMessengerException.badRequest("A file is required");
        }
        this.enforceAdminAttachmentSettings(uploadedFile, contentType, originalFilename);
        this.storageService.ensureStorageRootExists();
        JimAttachmentStorageService.StoredAttachmentFile storedFile = this.storageService.storeUploadedFile(uploadedFile, contentType, originalFilename, this.adminSettingsService.getAllowedExtensions());
        boolean voice = "AUDIO".equals(storedFile.getFileKind())
                && (voiceHint == null || voiceHint.booleanValue());
        String preview = JimAttachmentPolicy.buildPreviewText(normalizedBody, storedFile.getFileKind(), storedFile.getOriginalFilename(), voice);
        long now = System.currentTimeMillis();
        UploadResult result = (UploadResult)this.activeObjects.executeInTransaction(() -> {
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
            message.save();
            JimAttachment attachment = (JimAttachment)this.activeObjects.create(JimAttachment.class, new DBParam[0]);
            attachment.setMessageId(message.getID());
            attachment.setConversationId(conversationId);
            attachment.setUploaderUserKey(senderUserKey);
            attachment.setOriginalFilename(storedFile.getOriginalFilename());
            attachment.setStoredFilename(storedFile.getStoredFilename());
            attachment.setStoragePath(storedFile.getStoragePath());
            attachment.setContentType(storedFile.getContentType());
            attachment.setFileSize(storedFile.getFileSize());
            attachment.setFileKind(storedFile.getFileKind());
            attachment.setVoice(voice ? Boolean.TRUE : Boolean.FALSE);
            attachment.setCreatedAt(now);
            attachment.setDeleted(0);
            attachment.save();
            this.conversationService.touchConversation(conversationId, preview, senderUserKey);
            log.info("event=attachment stage=create outcome=success conversationId={} messageId={} attachmentId={} fileKind={}", new Object[]{conversationId, message.getID(), attachment.getID(), storedFile.getFileKind()});
            return new UploadResult(message, attachment);
        });
        this.notifyDirectRecipientPushSafely(conversationId, senderUserKey, preview);
        return result;
    }

    private void notifyDirectRecipientPushSafely(int conversationId, String senderUserKey, String preview) {
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
            String body = preview != null && !preview.trim().isEmpty() ? preview.trim() : "Sent an attachment";
            java.util.LinkedHashMap<String, String> attachmentPayload = new java.util.LinkedHashMap<String, String>();
            attachmentPayload.put("title", senderName);
            attachmentPayload.put("body", body);
            attachmentPayload.put("tag", "jim-conv-" + conversationId);
            attachmentPayload.put("type", "chat_message");
            attachmentPayload.put("url", "/plugins/servlet/jim/chat");
            attachmentPayload.put("conversationId", String.valueOf(conversationId));
            this.pushService.pushToUserAsync(recipient, attachmentPayload);
        }
        catch (RuntimeException runtimeException) {
            // empty catch block
        }
    }

    /**
     * Sprint 08 Fix-2 (attachment forwarding): clone every live attachment of
     * [sourceMessageId] onto [targetMessage]. Each clone gets its OWN copy of
     * the stored file plus a fresh AO row carrying the same metadata
     * (filename, content type, kind, voice flag, dimensions), so the
     * forwarded message renders/opens/saves identically — voice notes stay
     * voice notes, videos stay videos, vCards stay contact cards. Access
     * control is untouched: forwarded attachments belong to the TARGET
     * conversation and are served only to its participants.
     *
     * <p>Callers verify read permission on the source message BEFORE calling
     * (the forward flow does). Per-attachment failures are logged and
     * skipped so one broken file cannot kill the whole forward.</p>
     */
    public List<JimAttachment> cloneAttachmentsForForward(int sourceMessageId, JimMessage targetMessage) {
        List<JimAttachment> cloned = new ArrayList<JimAttachment>();
        for (JimAttachment source : this.listAttachmentsForMessage(sourceMessageId)) {
            try {
                JimAttachmentStorageService.StoredAttachmentFile copy =
                        this.storageService.copyStoredAttachment(source);
                long now = System.currentTimeMillis();
                JimAttachment row = (JimAttachment)this.activeObjects.executeInTransaction(() -> {
                    JimAttachment attachment = (JimAttachment)this.activeObjects.create(JimAttachment.class, new DBParam[0]);
                    attachment.setMessageId(targetMessage.getID());
                    attachment.setConversationId(targetMessage.getConversationId());
                    attachment.setUploaderUserKey(targetMessage.getSenderUserKey());
                    attachment.setOriginalFilename(copy.getOriginalFilename());
                    attachment.setStoredFilename(copy.getStoredFilename());
                    attachment.setStoragePath(copy.getStoragePath());
                    attachment.setContentType(copy.getContentType());
                    attachment.setFileSize(copy.getFileSize());
                    attachment.setFileKind(copy.getFileKind());
                    attachment.setVoice(source.getVoice());
                    attachment.setWidth(source.getWidth());
                    attachment.setHeight(source.getHeight());
                    attachment.setCreatedAt(now);
                    attachment.setDeleted(0);
                    attachment.save();
                    return attachment;
                });
                cloned.add(row);
                log.info("event=attachment stage=forward outcome=success sourceAttachmentId={} newAttachmentId={} targetMessageId={}",
                        new Object[]{source.getID(), row.getID(), targetMessage.getID()});
            } catch (Exception ex) {
                log.warn("event=attachment stage=forward outcome=error sourceAttachmentId={} message={}",
                        new Object[]{source.getID(), ex.getMessage()});
            }
        }
        return cloned;
    }

    public JimAttachment getAttachmentForUser(int attachmentId, String userKey) {
        JimValidation.requirePositiveId(attachmentId, "attachmentId");
        JimValidation.requireNonBlank(userKey, "userKey");
        this.permissionService.requireAuthenticatedUserKey();
        JimAttachment attachment = this.getAttachment(attachmentId);
        this.conversationService.getConversationForUser(attachment.getConversationId(), userKey);
        return attachment;
    }

    public JimAttachment getAttachment(int attachmentId) {
        JimValidation.requirePositiveId(attachmentId, "attachmentId");
        JimAttachment attachment = (JimAttachment)this.activeObjects.get(JimAttachment.class, attachmentId);
        if (attachment == null || this.isDeleted(attachment)) {
            throw JimMessengerException.notFound("Attachment not found");
        }
        return attachment;
    }

    public List<JimAttachment> listAttachmentsForMessage(int messageId) {
        JimValidation.requirePositiveId(messageId, "messageId");
        JimAttachment[] attachments = (JimAttachment[])this.activeObjects.find(JimAttachment.class, Query.select().where("MESSAGE_ID = ? AND (DELETED IS NULL OR DELETED = 0)", new Object[]{messageId}).order("ID ASC"));
        return new ArrayList<JimAttachment>(Arrays.asList(attachments));
    }

    public Map<Integer, List<JimAttachment>> listAttachmentsForMessages(List<JimMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyMap();
        }
        ArrayList<Integer> messageIds = new ArrayList<Integer>();
        for (JimMessage message : messages) {
            if (message == null) continue;
            messageIds.add(message.getID());
        }
        if (messageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < messageIds.size(); ++i) {
            if (i > 0) {
                placeholders.append(',');
            }
            placeholders.append('?');
        }
        Object[] params = messageIds.toArray();
        JimAttachment[] attachments = (JimAttachment[])this.activeObjects.find(JimAttachment.class, Query.select().where("MESSAGE_ID IN (" + String.valueOf(placeholders) + ") AND (DELETED IS NULL OR DELETED = 0)", params).order("ID ASC"));
        HashMap<Integer, List<JimAttachment>> grouped = new HashMap<Integer, List<JimAttachment>>();
        for (JimAttachment attachment : attachments) {
            grouped.computeIfAbsent(attachment.getMessageId(), key -> new ArrayList()).add(attachment);
        }
        return grouped;
    }

    private String normalizeOptionalBody(String optionalBody) {
        if (optionalBody == null) {
            return "";
        }
        String trimmed = JimValidation.normalizeWhitespace(optionalBody);
        if (trimmed.isEmpty()) {
            return "";
        }
        return JimValidation.validateMessageBody(trimmed);
    }

    private boolean isDeleted(JimAttachment attachment) {
        Integer deleted = attachment.getDeleted();
        return deleted != null && deleted != 0;
    }

    public static final class UploadResult {
        private final JimMessage createdMessage;
        private final JimAttachment attachment;

        public UploadResult(JimMessage createdMessage, JimAttachment attachment) {
            this.createdMessage = createdMessage;
            this.attachment = attachment;
        }

        public JimMessage getCreatedMessage() {
            return this.createdMessage;
        }

        public JimAttachment getAttachment() {
            return this.attachment;
        }
    }
}

