/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.dto.ConversationSummaryDto;
import com.corbitlogic.jira.internalmessenger.dto.MessageDto;
import com.corbitlogic.jira.internalmessenger.dto.UserSearchResultDto;
import com.corbitlogic.jira.internalmessenger.service.JimApiService;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimDtoMapper;
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import com.corbitlogic.jira.internalmessenger.service.JimUserSearchService;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.util.ArrayList;
import java.util.List;

public class JimApiServiceImpl
implements JimApiService {
    private final JiraAuthenticationContext authenticationContext;
    private final JimPermissionService permissionService;
    private final JimConversationService conversationService;
    private final JimMessageService messageService;
    private final JimReadStateService readStateService;
    private final JimUserSearchService userSearchService;
    private final JimDtoMapper dtoMapper;

    public JimApiServiceImpl(JiraAuthenticationContext authenticationContext, JimPermissionService permissionService, JimConversationService conversationService, JimMessageService messageService, JimReadStateService readStateService, JimUserSearchService userSearchService, JimDtoMapper dtoMapper) {
        this.authenticationContext = authenticationContext;
        this.permissionService = permissionService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.readStateService = readStateService;
        this.userSearchService = userSearchService;
        this.dtoMapper = dtoMapper;
    }

    @Override
    public List<ConversationSummaryDto> listConversations() {
        String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        this.conversationService.getOrCreateSystemConversation(currentUserKey);
        List<JimConversation> conversations = this.conversationService.listConversationsForUser(currentUserKey);
        ArrayList<ConversationSummaryDto> summaries = new ArrayList<ConversationSummaryDto>();
        for (JimConversation conversation : conversations) {
            int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
            summaries.add(this.dtoMapper.toConversationSummary(conversation, currentUserKey, viewer, unreadCount));
        }
        return summaries;
    }

    @Override
    public ConversationSummaryDto createOrGetDirectConversation(String targetUserKey) {
        String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        String normalizedTarget = JimValidation.requireNonBlank(targetUserKey, "targetUserKey");
        JimConversation conversation = this.conversationService.getOrCreateDirectConversation(currentUserKey, normalizedTarget);
        this.permissionService.requireParticipant(conversation, currentUserKey);
        int unreadCount = this.readStateService.getUnreadCount(conversation.getID(), currentUserKey);
        return this.dtoMapper.toConversationSummary(conversation, currentUserKey, viewer, unreadCount);
    }

    @Override
    public List<MessageDto> listMessages(int conversationId, int limit, Integer beforeMessageId) {
        this.permissionService.requireAuthenticatedUserKey();
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        List<JimMessage> messages = this.messageService.listMessages(conversationId, limit, beforeMessageId);
        ArrayList<MessageDto> result = new ArrayList<MessageDto>();
        for (JimMessage message : messages) {
            result.add(this.dtoMapper.toMessageDto(message, viewer));
        }
        return result;
    }

    @Override
    public MessageDto sendMessage(int conversationId, String body) {
        String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        JimMessage message = this.messageService.sendUserMessage(conversationId, currentUserKey, body);
        return this.dtoMapper.toMessageDto(message, viewer);
    }

    @Override
    public void markConversationRead(int conversationId) {
        String currentUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.readStateService.markConversationRead(conversationId, currentUserKey);
    }

    @Override
    public List<UserSearchResultDto> searchUsers(String query) {
        return this.userSearchService.searchActiveUsers(query);
    }
}

