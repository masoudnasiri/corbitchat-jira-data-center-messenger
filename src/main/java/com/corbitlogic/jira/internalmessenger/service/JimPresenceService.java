/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

public interface JimPresenceService {
    public void heartbeat(String var1);

    public void heartbeatConversation(String var1, int var2);

    public boolean isActive(String var1);

    public boolean isViewingConversation(String var1, int var2);
}

