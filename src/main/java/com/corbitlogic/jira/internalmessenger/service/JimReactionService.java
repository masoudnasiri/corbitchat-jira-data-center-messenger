/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.ao.JimReaction;
import java.util.List;
import java.util.Map;

public interface JimReactionService {
    public boolean toggleReaction(int var1, String var2, String var3);

    public List<JimReaction> listReactionsForMessage(int var1);

    public Map<Integer, List<JimReaction>> listReactionsForMessages(List<JimMessage> var1);
}

