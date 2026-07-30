/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimGroupMember;
import java.io.File;
import java.util.List;

public interface JimGroupService {
    public JimConversation createGroup(String var1, String var2, List<String> var3);

    public void deleteGroup(int var1, String var2);

    public void addMember(int var1, String var2, String var3);

    public void removeMember(int var1, String var2, String var3);

    public List<JimGroupMember> listMembers(int var1, String var2);

    public int countMembers(int var1);

    public boolean isOwner(JimConversation var1, String var2);

    /** Effective role for a user, with legacy OWNER fallback (OWNER/ADMIN/MEMBER or null). */
    public String getRole(JimConversation conversation, String userKey);

    public boolean isAdmin(JimConversation conversation, String userKey);

    /** OWNER or ADMIN — may add/remove normal members, rename, change/reset avatar. */
    public boolean canManageMembers(JimConversation conversation, String userKey);

    /** OWNER only — may promote/demote admins. */
    public boolean canManageAdmins(JimConversation conversation, String userKey);

    public void promoteToAdmin(int conversationId, String actorUserKey, String targetUserKey);

    public void demoteToMember(int conversationId, String actorUserKey, String targetUserKey);

    public JimConversation renameGroup(int conversationId, String actorUserKey, String newName);

    /** Store a new custom avatar (OWNER/ADMIN). Returns the updated conversation. */
    public JimConversation uploadAvatar(int conversationId, String actorUserKey, File sourceFile,
                                        String contentType, String originalFilename);

    /** Reset (remove) the custom avatar (OWNER/ADMIN). Returns the updated conversation. */
    public JimConversation resetAvatar(int conversationId, String actorUserKey);
}

