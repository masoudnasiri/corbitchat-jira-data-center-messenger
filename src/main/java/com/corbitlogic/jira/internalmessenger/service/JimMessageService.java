/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.model.JimEventType;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface JimMessageService {
    public JimMessage sendUserMessage(int var1, String var2, String var3);

    public JimMessage sendUserMessage(int var1, String var2, String var3, Long var4);

    /**
     * Forward an existing message into another conversation, preserving the
     * original author's attribution (Sprint 06 Fix-1). Permission-safe: the
     * forwarder must be a participant of both the source message's conversation
     * and the target conversation. Args: targetConversationId, forwarderUserKey,
     * sourceMessageId.
     */
    public JimMessage forwardUserMessage(int var1, String var2, int var3);

    public JimMessage sendIssueLinkMessage(int var1, String var2, String var3, String var4, String var5, String var6);

    public JimMessage editUserMessage(int var1, String var2, String var3);

    public JimMessage deleteUserMessage(int var1, String var2);

    public JimMessage getMessageForParticipant(int var1, String var2);

    public Map<Integer, JimMessage> findMessagesByIds(Collection<Integer> var1);

    public JimMessage createSystemMessage(String var1, JimEventType var2, String var3, String var4, String var5, String var6, String var7, String var8);

    public JimMessage createSystemMessage(String var1, JimEventType var2, String var3, String var4, String var5, String var6, String var7, String var8, String var9);

    public JimMessage createSystemMessage(String var1, JimEventType var2, String var3, String var4, String var5, String var6, String var7, String var8, String eventText, String eventData, String var11);

    public java.util.Set<String> attachFilesToRecentMentions(String issueKey, String eventDataJson);

    public void reconcileMentionWithStandaloneAttachment(String targetUserKey, String issueKey);

    public String findScheduleRequesterKey(String issueKey);

    public List<JimMessage> listMessages(int var1, int var2, Integer var3);

    public List<JimMessage> listMessages(int var1, int var2, Integer var3, Long sinceMillis);

    /**
     * Chat message search for the authenticated user. When {@code conversationId} is
     * non-null the search is limited to that one conversation (membership enforced);
     * otherwise it is a GLOBAL search across every conversation the user participates
     * in (direct/group/project/system). Matches message body text and attachment
     * filenames (case-insensitive, MySQL {@code LIKE}), skips deleted/hidden/pluginuser
     * messages, and NEVER changes read state. Offset-based pagination. Returns a wire
     * map: {@code query, scope, results[], nextCursor}. Each result carries
     * conversationId/type/title, messageId, sender, createdAt, snippet, attachment
     * indicator and open target.
     */
    public java.util.Map<String, Object> searchMessages(String query, Integer conversationId, int limit, int offset);

    /**
     * Same as {@link #searchMessages(String, Integer, int, int)} with an optional
     * conversation-type filter ({@code DIRECT}/{@code GROUP}/{@code PROJECT}/{@code SYSTEM}/{@code SELF}).
     */
    public java.util.Map<String, Object> searchMessages(String query, Integer conversationId, String type, int limit, int offset);

    /**
     * Bounded page around a target message for search jump. Membership and
     * delete-for-me are enforced. Does not change read state.
     */
    public java.util.Map<String, Object> listMessagesAround(int conversationId, int messageId, int before, int after);

    /**
     * Unread chat-mention projection for a conversation row. SYSTEM / Assistant
     * report mentions are excluded (those stay on Assistant filter chips).
     * Does not change read state. Bounded unread-window scan.
     */
    public java.util.Map<String, Object> mentionBadge(int conversationId, String userKey);

    public boolean hasMessagesBefore(int conversationId, int messageId);

    public JimMessage setPinned(int var1, String var2, boolean var3);

    public JimMessage getPinnedMessage(int var1, String var2);

    /**
     * Marks a Jira Assistant / system message as acknowledged or acted-on
     * by the given user. Idempotent: if the message is already actioned,
     * the existing actionedAt timestamp is preserved. Permission is
     * verified via {@link #getMessageForParticipant} so users cannot mark
     * messages from a conversation they do not participate in.
     */
    public JimMessage markActioned(int var1, String var2);
}

