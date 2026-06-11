/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.avatar.Avatar$Size
 *  com.atlassian.jira.avatar.AvatarService
 *  com.atlassian.jira.avatar.AvatarsDisabledException
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.jira.user.util.UserManager
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.avatar.AvatarsDisabledException;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.dto.ConversationSummaryDto;
import com.corbitlogic.jira.internalmessenger.dto.MessageDto;
import com.corbitlogic.jira.internalmessenger.model.JimConversationType;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.util.JimSanitizer;
import java.net.URI;

public class JimDtoMapper {
    public static final String SYSTEM_CONVERSATION_DISPLAY_NAME = "Jira Assistant";
    private final UserManager userManager;
    private final AvatarService avatarService;
    private final JimPermissionService permissionService;

    public JimDtoMapper(UserManager userManager, AvatarService avatarService, JimPermissionService permissionService) {
        this.userManager = userManager;
        this.avatarService = avatarService;
        this.permissionService = permissionService;
    }

    public ConversationSummaryDto toConversationSummary(JimConversation conversation, String currentUserKey, ApplicationUser viewer, int unreadCount) {
        ConversationSummaryDto dto = new ConversationSummaryDto();
        dto.setId(conversation.getID());
        dto.setType(conversation.getConversationType());
        dto.setLastMessagePreview(JimSanitizer.sanitizeText(conversation.getLastMessagePreview()));
        dto.setLastMessageAt(conversation.getLastMessageAt());
        dto.setUnreadCount(unreadCount);
        JimConversationType type = this.permissionService.toConversationType(conversation);
        boolean isSystem = type == JimConversationType.SYSTEM;
        dto.setIsSystem(isSystem);
        if (isSystem) {
            dto.setDisplayName(SYSTEM_CONVERSATION_DISPLAY_NAME);
            dto.setAvatarUrl(null);
            return dto;
        }
        String otherUserKey = this.resolveOtherParticipantKey(conversation, currentUserKey);
        ApplicationUser otherUser = this.userManager.getUserByKey(otherUserKey);
        if (otherUser != null) {
            dto.setDisplayName(otherUser.getDisplayName());
            dto.setAvatarUrl(this.resolveAvatarUrl(viewer, otherUser));
        } else {
            dto.setDisplayName(otherUserKey);
            dto.setAvatarUrl(null);
        }
        return dto;
    }

    public MessageDto toMessageDto(JimMessage message, ApplicationUser viewer) {
        ApplicationUser sender;
        MessageDto dto = new MessageDto();
        dto.setId(message.getID());
        dto.setConversationId(message.getConversationId());
        dto.setSenderType(message.getSenderType());
        dto.setSenderUserKey(message.getSenderUserKey());
        dto.setBody(JimSanitizer.sanitizeText(message.getBody()));
        dto.setBodyFormat(message.getBodyFormat());
        dto.setEventType(message.getEventType());
        dto.setIssueKey(message.getIssueKey());
        dto.setIssueSummary(JimSanitizer.sanitizeText(message.getIssueSummary()));
        dto.setIssueUrl(message.getIssueUrl());
        dto.setActorUserKey(message.getActorUserKey());
        dto.setActorDisplayName(JimSanitizer.sanitizeText(message.getActorDisplayName()));
        dto.setCreatedAt(message.getCreatedAt());
        if (message.getSenderUserKey() != null && (sender = this.userManager.getUserByKey(message.getSenderUserKey())) != null) {
            dto.setSenderDisplayName(sender.getDisplayName());
        }
        return dto;
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
}

