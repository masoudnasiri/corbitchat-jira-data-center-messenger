/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import java.util.List;

public interface JimConversationService {
    public JimConversation getOrCreateDirectConversation(String var1, String var2);

    public JimConversation getOrCreateSystemConversation(String var1);

    public List<JimConversation> listConversationsForUser(String var1);

    public JimConversation touchConversation(int var1, String var2);

    public JimConversation getConversation(int var1);

    public JimConversation getConversationForUser(int var1, String var2);
}

