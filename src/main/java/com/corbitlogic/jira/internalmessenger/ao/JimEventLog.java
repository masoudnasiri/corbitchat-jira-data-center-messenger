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
public interface JimEventLog
extends Entity {
    @Indexed
    @StringLength(value=255)
    public String getEventFingerprint();

    public void setEventFingerprint(String var1);

    @Indexed
    @StringLength(value=50)
    public String getEventType();

    public void setEventType(String var1);

    @Indexed
    @StringLength(value=64)
    public String getIssueKey();

    public void setIssueKey(String var1);

    @Indexed
    @StringLength(value=255)
    public String getTargetUserKey();

    public void setTargetUserKey(String var1);

    @Indexed
    public Long getCreatedAt();

    public void setCreatedAt(Long var1);
}

