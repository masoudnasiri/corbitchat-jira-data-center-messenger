/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.java.ao.Entity
 *  net.java.ao.Preload
 *  net.java.ao.schema.Indexed
 *  net.java.ao.schema.StringLength
 */
package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;

@Preload
public interface JimGroupMember
extends Entity {
    @Indexed
    public int getConversationId();

    public void setConversationId(int var1);

    @Indexed
    @StringLength(value=255)
    public String getUserKey();

    public void setUserKey(String var1);

    @StringLength(value=50)
    public String getRole();

    public void setRole(String var1);

    public Long getJoinedAt();

    public void setJoinedAt(Long var1);

    /**
     * Phase 8C: when the conversation's sendPolicy is ADMINS_AND_ALLOWED, a non-admin member
     * with {@code allowedSender=true} may still send. {@code null}/false = not allowed.
     * Lives on the member row so removing the member automatically clears the allowance.
     * Nullable → AO auto-migration adds the column; existing rows read as null (harmless).
     */
    public Boolean getAllowedSender();

    public void setAllowedSender(Boolean var1);
}

