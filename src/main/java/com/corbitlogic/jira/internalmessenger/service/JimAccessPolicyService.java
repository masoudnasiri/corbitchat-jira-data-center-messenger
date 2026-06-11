/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.ao.JimAccessPolicy;
import java.util.List;

public interface JimAccessPolicyService {
    public static final String SOURCE_USER = "USER";
    public static final String SOURCE_GROUP = "GROUP";
    public static final String TARGET_ANY = "ANY";
    public static final String ACTION_ALLOW = "ALLOW";
    public static final String ACTION_DENY = "DENY";

    public List<JimAccessPolicy> listPolicies();

    public JimAccessPolicy createPolicy(String var1, String var2, String var3, String var4, String var5, boolean var6, boolean var7, boolean var8, boolean var9, int var10, String var11);

    public JimAccessPolicy updatePolicy(int var1, String var2, String var3, String var4, String var5, String var6, boolean var7, boolean var8, boolean var9, boolean var10, int var11);

    public void deletePolicy(int var1);

    public boolean isChatEnabled();

    public boolean canSearch(String var1, String var2);

    public boolean canChatWith(String var1, String var2);

    public void requireCanChatWith(String var1, String var2);
}

