/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

public interface JimReadStateService {
    public int getUnreadCount(int var1, String var2);

    public void markConversationRead(int var1, String var2);

    public int getLastReadMessageId(int var1, String var2);

    public Long getLastReadAt(int var1, String var2);

    public void regressReadStateBeforeMessage(int var1, String var2, int var3);
}

