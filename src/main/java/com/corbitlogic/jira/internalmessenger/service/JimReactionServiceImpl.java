/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.activeobjects.external.ActiveObjects
 *  net.java.ao.DBParam
 *  net.java.ao.Query
 *  net.java.ao.RawEntity
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.ao.JimReaction;
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimReactionService;
import com.corbitlogic.jira.internalmessenger.util.JimMessageFlags;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.java.ao.DBParam;
import net.java.ao.Query;
import net.java.ao.RawEntity;

public class JimReactionServiceImpl
implements JimReactionService {
    private static final Set<String> ALLOWED_EMOJI = new HashSet<String>(Arrays.asList("\ud83d\udc4d", "\u2705", "\ud83d\ude04", "\ud83c\udf89", "\u2764\ufe0f", "\ud83d\udc40"));
    private final ActiveObjects activeObjects;
    private final JimMessageService messageService;
    private final JimPermissionService permissionService;

    public JimReactionServiceImpl(ActiveObjects activeObjects, JimMessageService messageService, JimPermissionService permissionService) {
        this.activeObjects = activeObjects;
        this.messageService = messageService;
        this.permissionService = permissionService;
    }

    @Override
    public boolean toggleReaction(int messageId, String userKey, String emoji) {
        JimValidation.requirePositiveId(messageId, "messageId");
        JimValidation.requireNonBlank(userKey, "userKey");
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.permissionService.requireSameUser(authenticatedUserKey, userKey);
        if (emoji == null || !ALLOWED_EMOJI.contains(emoji)) {
            throw JimMessengerException.badRequest("Unsupported reaction emoji");
        }
        JimMessage message = this.messageService.getMessageForParticipant(messageId, userKey);
        if (JimMessageFlags.isDeleted(message)) {
            throw JimMessengerException.badRequest("Deleted messages cannot be reacted to");
        }
        long now = System.currentTimeMillis();
        return (Boolean)this.activeObjects.executeInTransaction(() -> {
            JimReaction[] existing = (JimReaction[])this.activeObjects.find(JimReaction.class, Query.select().where("MESSAGE_ID = ? AND USER_KEY = ? AND EMOJI = ?", new Object[]{messageId, userKey, emoji}).limit(1));
            if (existing.length > 0) {
                this.activeObjects.delete((RawEntity[])existing);
                return false;
            }
            JimReaction reaction = (JimReaction)this.activeObjects.create(JimReaction.class, new DBParam[0]);
            reaction.setMessageId(messageId);
            reaction.setUserKey(userKey);
            reaction.setEmoji(emoji);
            reaction.setCreatedAt(now);
            reaction.save();
            return true;
        });
    }

    @Override
    public List<JimReaction> listReactionsForMessage(int messageId) {
        JimValidation.requirePositiveId(messageId, "messageId");
        JimReaction[] reactions = (JimReaction[])this.activeObjects.find(JimReaction.class, Query.select().where("MESSAGE_ID = ?", new Object[]{messageId}).order("ID ASC"));
        return new ArrayList<JimReaction>(Arrays.asList(reactions));
    }

    @Override
    public Map<Integer, List<JimReaction>> listReactionsForMessages(List<JimMessage> messages) {
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
        JimReaction[] reactions = (JimReaction[])this.activeObjects.find(JimReaction.class, Query.select().where("MESSAGE_ID IN (" + String.valueOf(placeholders) + ")", params).order("ID ASC"));
        HashMap<Integer, List<JimReaction>> grouped = new HashMap<Integer, List<JimReaction>>();
        for (JimReaction reaction : reactions) {
            grouped.computeIfAbsent(reaction.getMessageId(), key -> new ArrayList()).add(reaction);
        }
        return grouped;
    }
}

