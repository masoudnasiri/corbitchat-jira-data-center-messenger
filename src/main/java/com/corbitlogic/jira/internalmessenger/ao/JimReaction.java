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
public interface JimReaction
extends Entity {
    @Indexed
    public int getMessageId();

    public void setMessageId(int var1);

    @Indexed
    @StringLength(value=255)
    public String getUserKey();

    public void setUserKey(String var1);

    @StringLength(value=64)
    public String getEmoji();

    public void setEmoji(String var1);

    public Long getCreatedAt();

    public void setCreatedAt(Long var1);
}

