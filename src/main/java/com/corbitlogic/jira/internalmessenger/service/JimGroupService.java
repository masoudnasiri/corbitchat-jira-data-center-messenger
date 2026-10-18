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

    // ---- Phase 8C: group send policy ----

    /** Effective send policy: "ALL_MEMBERS" (default) or "ADMINS_AND_ALLOWED". Never null. */
    public String getSendPolicy(JimConversation conversation);

    /**
     * Backend enforcement: can {@code userKey} send a message into this conversation?
     * True for non-group conversations and for ALL_MEMBERS groups; for ADMINS_AND_ALLOWED
     * only OWNER/ADMIN or an allowed member may send.
     */
    public boolean canSend(JimConversation conversation, String userKey);

    /** Throws 403 (Persian-friendly) when {@link #canSend} is false. Call before any insert. */
    public void requireCanSend(JimConversation conversation, String userKey);

    /** True if this member is explicitly flagged as an allowed sender. */
    public boolean isAllowedSender(JimConversation conversation, String userKey);

    /** OWNER/ADMIN only: set the group send policy ("ALL_MEMBERS" | "ADMINS_AND_ALLOWED"). */
    public JimConversation setSendPolicy(int conversationId, String actorUserKey, String policy);

    /** OWNER/ADMIN only: allow/disallow a specific non-admin participant to send. */
    public void setAllowedSender(int conversationId, String actorUserKey, String targetUserKey, boolean allowed);
}

