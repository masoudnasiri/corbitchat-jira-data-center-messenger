/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.activeobjects.external.ActiveObjects
 *  net.java.ao.DBParam
 *  net.java.ao.Query
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.ao.JimReadState;
import com.corbitlogic.jira.internalmessenger.model.JimSenderType;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import net.java.ao.DBParam;
import net.java.ao.Query;

public class JimReadStateServiceImpl
implements JimReadStateService {
    private final ActiveObjects activeObjects;
    private final JimConversationService conversationService;
    private final JimPermissionService permissionService;

    public JimReadStateServiceImpl(ActiveObjects activeObjects, JimConversationService conversationService, JimPermissionService permissionService) {
        this.activeObjects = activeObjects;
        this.conversationService = conversationService;
        this.permissionService = permissionService;
    }

    @Override
    public int getUnreadCount(int conversationId, String userKey) {
        JimValidation.requirePositiveId(conversationId, "conversationId");
        JimValidation.requireNonBlank(userKey, "userKey");
        try {
            this.conversationService.getConversationForUser(conversationId, userKey);
        }
        catch (JimMessengerException ex) {
            return 0;
        }
        int lastReadMessageId = this.getLastReadMessageId(conversationId, userKey);
        return this.activeObjects.count(JimMessage.class, Query.select().where("CONVERSATION_ID = ? AND ID > ? AND NOT (SENDER_TYPE = ? AND SENDER_USER_KEY = ?)", new Object[]{conversationId, lastReadMessageId, JimSenderType.USER.name(), userKey}));
    }

    @Override
    public void markConversationRead(int conversationId, String userKey) {
        this.validateMarkReadRequest(conversationId, userKey);
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.permissionService.requireSameUser(authenticatedUserKey, userKey);
        this.conversationService.getConversationForUser(conversationId, userKey);
        int latestMessageId = this.findLatestMessageId(conversationId);
        long now = System.currentTimeMillis();
        this.activeObjects.executeInTransaction(() -> {
            JimReadState readState = this.findReadState(conversationId, userKey);
            if (readState == null) {
                readState = (JimReadState)this.activeObjects.create(JimReadState.class, new DBParam[0]);
                readState.setConversationId(conversationId);
                readState.setUserKey(userKey);
            }
            readState.setLastReadMessageId(latestMessageId);
            readState.setLastReadAt(now);
            readState.save();
            return null;
        });
    }

    @Override
    public int getLastReadMessageId(int conversationId, String userKey) {
        JimValidation.requirePositiveId(conversationId, "conversationId");
        JimValidation.requireNonBlank(userKey, "userKey");
        return this.readLastMessageId(conversationId, userKey);
    }

    @Override
    public Long getLastReadAt(int conversationId, String userKey) {
        JimValidation.requirePositiveId(conversationId, "conversationId");
        JimValidation.requireNonBlank(userKey, "userKey");
        JimReadState readState = this.findReadState(conversationId, userKey);
        return readState == null ? null : readState.getLastReadAt();
    }

    @Override
    public void regressReadStateBeforeMessage(int conversationId, String userKey, int messageId) {
        JimValidation.requirePositiveId(conversationId, "conversationId");
        JimValidation.requirePositiveId(messageId, "messageId");
        JimValidation.requireNonBlank(userKey, "userKey");
        int currentLastRead = this.readLastMessageId(conversationId, userKey);
        if (currentLastRead < messageId) {
            return;
        }
        int newLastRead = Math.max(0, messageId - 1);
        long now = System.currentTimeMillis();
        this.activeObjects.executeInTransaction(() -> {
            JimReadState readState = this.findReadState(conversationId, userKey);
            if (readState == null) {
                readState = (JimReadState)this.activeObjects.create(JimReadState.class, new DBParam[0]);
                readState.setConversationId(conversationId);
                readState.setUserKey(userKey);
            }
            readState.setLastReadMessageId(newLastRead);
            readState.setLastReadAt(now);
            readState.save();
            return null;
        });
    }

    void validateMarkReadRequest(int conversationId, String userKey) {
        JimValidation.requirePositiveId(conversationId, "conversationId");
        JimValidation.requireNonBlank(userKey, "userKey");
    }

    private int readLastMessageId(int conversationId, String userKey) {
        JimReadState readState = this.findReadState(conversationId, userKey);
        if (readState == null || readState.getLastReadMessageId() == null) {
            return 0;
        }
        return readState.getLastReadMessageId();
    }

    private JimReadState findReadState(int conversationId, String userKey) {
        JimReadState[] states = (JimReadState[])this.activeObjects.find(JimReadState.class, Query.select().where("CONVERSATION_ID = ? AND USER_KEY = ?", new Object[]{conversationId, userKey}).limit(1));
        return states.length == 0 ? null : states[0];
    }

    private int findLatestMessageId(int conversationId) {
        JimMessage[] messages = (JimMessage[])this.activeObjects.find(JimMessage.class, Query.select().where("CONVERSATION_ID = ?", new Object[]{conversationId}).order("ID DESC").limit(1));
        return messages.length == 0 ? 0 : messages[0].getID();
    }
}

