/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.activeobjects.external.ActiveObjects
 *  net.java.ao.DBParam
 *  net.java.ao.Query
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimGroupMember;
import com.corbitlogic.jira.internalmessenger.model.JimConversationType;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.util.JimConversationKeys;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.java.ao.DBParam;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimConversationServiceImpl
implements JimConversationService {
    private static final Logger log = LoggerFactory.getLogger(JimConversationServiceImpl.class);
    private final ActiveObjects activeObjects;
    private final JimPermissionService permissionService;

    public JimConversationServiceImpl(ActiveObjects activeObjects, JimPermissionService permissionService) {
        this.activeObjects = activeObjects;
        this.permissionService = permissionService;
    }

    @Override
    public JimConversation getOrCreateDirectConversation(String currentUserKey, String targetUserKey) {
        this.validateDirectConversationRequest(currentUserKey, targetUserKey);
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.permissionService.requireSameUser(authenticatedUserKey, currentUserKey);
        this.permissionService.requireActiveUser(currentUserKey);
        this.permissionService.requireActiveUser(targetUserKey);
        String[] pair = JimConversationKeys.canonicalDirectPair(currentUserKey, targetUserKey);
        JimConversation[] existing = (JimConversation[])this.activeObjects.find(JimConversation.class, Query.select().where("CONVERSATION_TYPE = ? AND USER_A_KEY = ? AND USER_B_KEY = ?", new Object[]{JimConversationType.DIRECT.name(), pair[0], pair[1]}));
        if (existing.length > 0) {
            return existing[0];
        }
        return (JimConversation)this.activeObjects.executeInTransaction(() -> {
            JimConversation[] rechecked = (JimConversation[])this.activeObjects.find(JimConversation.class, Query.select().where("CONVERSATION_TYPE = ? AND USER_A_KEY = ? AND USER_B_KEY = ?", new Object[]{JimConversationType.DIRECT.name(), pair[0], pair[1]}));
            if (rechecked.length > 0) {
                return rechecked[0];
            }
            long now = System.currentTimeMillis();
            JimConversation conversation = (JimConversation)this.activeObjects.create(JimConversation.class, new DBParam[0]);
            conversation.setConversationType(JimConversationType.DIRECT.name());
            conversation.setUserAKey(pair[0]);
            conversation.setUserBKey(pair[1]);
            conversation.setCreatedAt(now);
            conversation.setUpdatedAt(now);
            conversation.save();
            return conversation;
        });
    }

    @Override
    public JimConversation getOrCreateSystemConversation(String userKey) {
        this.validateSystemConversationRequest(userKey);
        this.permissionService.requireActiveUser(userKey);
        JimConversation existing = this.findSystemConversationForUser(userKey);
        if (existing != null) {
            return existing;
        }
        return (JimConversation)this.activeObjects.executeInTransaction(() -> {
            JimConversation rechecked = this.findSystemConversationForUser(userKey);
            if (rechecked != null) {
                return rechecked;
            }
            long now = System.currentTimeMillis();
            JimConversation conversation = (JimConversation)this.activeObjects.create(JimConversation.class, new DBParam[0]);
            conversation.setConversationType(JimConversationType.SYSTEM.name());
            conversation.setSystemKey(JimConversationKeys.systemConversationKey());
            conversation.setUserAKey(userKey);
            conversation.setCreatedAt(now);
            conversation.setUpdatedAt(now);
            conversation.setLastMessageAt(now);
            conversation.setLastMessagePreview("Jira Assistant");
            conversation.save();
            log.info("event=system_conversation stage=create outcome=success userKey={}", (Object)userKey);
            return conversation;
        });
    }

    @Override
    public List<JimConversation> listConversationsForUser(String userKey) {
        JimGroupMember[] memberships;
        this.validateSystemConversationRequest(userKey);
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.permissionService.requireSameUser(authenticatedUserKey, userKey);
        JimConversation[] directConversations = (JimConversation[])this.activeObjects.find(JimConversation.class, Query.select().where("CONVERSATION_TYPE = ? AND (USER_A_KEY = ? OR USER_B_KEY = ?)", new Object[]{JimConversationType.DIRECT.name(), userKey, userKey}));
        JimConversation[] systemConversations = (JimConversation[])this.activeObjects.find(JimConversation.class, Query.select().where("CONVERSATION_TYPE = ? AND USER_A_KEY = ? AND SYSTEM_KEY = ?", new Object[]{JimConversationType.SYSTEM.name(), userKey, "JIRA_ASSISTANT"}));
        ArrayList<JimConversation> conversations = new ArrayList<JimConversation>();
        for (JimConversation conversation : directConversations) {
            if (!this.permissionService.isParticipant(conversation, userKey)) continue;
            conversations.add(conversation);
        }
        for (JimConversation conversation : systemConversations) {
            if (!this.permissionService.isParticipant(conversation, userKey)) continue;
            conversations.add(conversation);
        }
        for (JimGroupMember membership : memberships = (JimGroupMember[])this.activeObjects.find(JimGroupMember.class, Query.select().where("USER_KEY = ?", new Object[]{userKey}))) {
            JimConversation conversation = (JimConversation)this.activeObjects.get(JimConversation.class, membership.getConversationId());
            if (conversation == null || !JimConversationType.GROUP.name().equals(conversation.getConversationType())) continue;
            conversations.add(conversation);
        }
        conversations.sort(Comparator.comparing(JimConversation::getLastMessageAt, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(JimConversation::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(JimConversation::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return conversations;
    }

    @Override
    public JimConversation touchConversation(int conversationId, String preview) {
        return this.touchConversation(conversationId, preview, null);
    }

    @Override
    public JimConversation touchConversation(int conversationId, String preview, String senderUserKey) {
        this.validateTouchRequest(conversationId, preview);
        JimConversation conversation = this.getConversation(conversationId);
        long now = System.currentTimeMillis();
        conversation.setUpdatedAt(now);
        conversation.setLastMessageAt(now);
        conversation.setLastMessagePreview(JimValidation.truncatePreview(preview));
        conversation.setLastMessageSenderUserKey(senderUserKey);
        conversation.save();
        return conversation;
    }

    @Override
    public JimConversation getConversation(int conversationId) {
        JimValidation.requirePositiveId(conversationId, "conversationId");
        JimConversation conversation = (JimConversation)this.activeObjects.get(JimConversation.class, conversationId);
        if (conversation == null) {
            throw JimMessengerException.notFound("Conversation not found");
        }
        return conversation;
    }

    @Override
    public JimConversation getConversationForUser(int conversationId, String userKey) {
        JimValidation.requireNonBlank(userKey, "userKey");
        JimConversation conversation = this.getConversation(conversationId);
        this.permissionService.requireParticipant(conversation, userKey);
        return conversation;
    }

    private JimConversation findSystemConversationForUser(String userKey) {
        JimConversation[] conversations = (JimConversation[])this.activeObjects.find(JimConversation.class, Query.select().where("CONVERSATION_TYPE = ? AND USER_A_KEY = ? AND SYSTEM_KEY = ?", new Object[]{JimConversationType.SYSTEM.name(), userKey, "JIRA_ASSISTANT"}).limit(1));
        return conversations.length > 0 ? conversations[0] : null;
    }

    void validateDirectConversationRequest(String currentUserKey, String targetUserKey) {
        // Self-direct conversations ("Saved Messages", Sprint 06 Fix-2) are
        // allowed: current == target is intentional and produces a single
        // canonical row (USER_A_KEY == USER_B_KEY). We therefore no longer
        // require the two keys to be distinct here.
        JimValidation.requireNonBlank(currentUserKey, "currentUserKey");
        JimValidation.requireNonBlank(targetUserKey, "targetUserKey");
    }

    void validateSystemConversationRequest(String userKey) {
        JimValidation.requireNonBlank(userKey, "userKey");
    }

    void validateTouchRequest(int conversationId, String preview) {
        JimValidation.requirePositiveId(conversationId, "conversationId");
        // The 500-char ceiling is enforced by JimValidation.truncatePreview()
        // at the storage call site, so we let any preview length through
        // here. Previously, this used requireMaxLength which prevented long
        // messages (>500 chars body) from being sent at all because the
        // message body itself was passed as the preview.
    }
}

