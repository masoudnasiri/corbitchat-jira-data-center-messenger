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
import java.util.List;
import java.util.Map;
import net.java.ao.DBParam;
import net.java.ao.Query;
import net.java.ao.RawEntity;

public class JimReactionServiceImpl
implements JimReactionService {
    /**
     * Quick-preset palette (clients still show these first). Any other
     * well-formed emoji is accepted so the full picker can persist the same
     * {@code emoji} string the UI already sends. Length is capped to the
     * AO column ({@code JimReaction.emoji} 64). Existing rows remain readable.
     */
    private static final int MAX_EMOJI_CHARS = 64;
    private static final int MAX_EMOJI_CODEPOINTS = 10;
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
        if (!isUsableReactionEmoji(emoji)) {
            throw JimMessengerException.badRequest("Unsupported reaction emoji");
        }
        final String storedEmoji = emoji.trim();
        JimMessage message = this.messageService.getMessageForParticipant(messageId, userKey);
        if (JimMessageFlags.isDeleted(message)) {
            throw JimMessengerException.badRequest("Deleted messages cannot be reacted to");
        }
        long now = System.currentTimeMillis();
        return (Boolean)this.activeObjects.executeInTransaction(() -> {
            JimReaction[] existing = (JimReaction[])this.activeObjects.find(JimReaction.class, Query.select().where("MESSAGE_ID = ? AND USER_KEY = ? AND EMOJI = ?", new Object[]{messageId, userKey, storedEmoji}).limit(1));
            if (existing.length > 0) {
                this.activeObjects.delete((RawEntity[])existing);
                return false;
            }
            JimReaction reaction = (JimReaction)this.activeObjects.create(JimReaction.class, new DBParam[0]);
            reaction.setMessageId(messageId);
            reaction.setUserKey(userKey);
            reaction.setEmoji(storedEmoji);
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

    static boolean isUsableReactionEmoji(String raw) {
        if (raw == null) {
            return false;
        }
        String emoji = raw.trim();
        if (emoji.isEmpty() || emoji.length() > MAX_EMOJI_CHARS) {
            return false;
        }
        int[] cps = emoji.codePoints().toArray();
        if (cps.length == 0 || cps.length > MAX_EMOJI_CODEPOINTS) {
            return false;
        }
        for (int cp : cps) {
            if (Character.isISOControl(cp) || Character.isWhitespace(cp)) {
                return false;
            }
        }
        return true;
    }
}

