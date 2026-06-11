/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.model.JimConversationType;

public interface JimPermissionService {
    public String requireAuthenticatedUserKey();

    public void requireSameUser(String var1, String var2);

    public void requireActiveUser(String var1);

    public boolean isActiveUser(String var1);

    public boolean isParticipant(JimConversation var1, String var2);

    public void requireParticipant(JimConversation var1, String var2);

    public boolean isDirectConversation(JimConversation var1);

    public boolean isSystemConversation(JimConversation var1);

    public JimConversationType toConversationType(JimConversation var1);
}

