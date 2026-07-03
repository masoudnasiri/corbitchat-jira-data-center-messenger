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

    public List<JimMessage> listMessages(int var1, int var2, Integer var3);

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

