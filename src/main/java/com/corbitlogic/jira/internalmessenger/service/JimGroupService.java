/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimGroupMember;
import java.util.List;

public interface JimGroupService {
    public JimConversation createGroup(String var1, String var2, List<String> var3);

    public void deleteGroup(int var1, String var2);

    public void addMember(int var1, String var2, String var3);

    public void removeMember(int var1, String var2, String var3);

    public List<JimGroupMember> listMembers(int var1, String var2);

    public int countMembers(int var1);

    public boolean isOwner(JimConversation var1, String var2);
}

