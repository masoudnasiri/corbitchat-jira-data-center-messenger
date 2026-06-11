/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.java.ao.Accessor
 *  net.java.ao.Entity
 *  net.java.ao.Mutator
 *  net.java.ao.Preload
 *  net.java.ao.schema.Indexed
 *  net.java.ao.schema.StringLength
 */
package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Accessor;
import net.java.ao.Entity;
import net.java.ao.Mutator;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;

@Preload
public interface JimAdminAudit
extends Entity {
    @Indexed
    @StringLength(value=255)
    @Accessor(value="USER_KEY")
    public String getUserKey();

    @Mutator(value="USER_KEY")
    public void setUserKey(String var1);

    @StringLength(value=100)
    public String getAction();

    public void setAction(String var1);

    @StringLength(value=-1)
    public String getDetails();

    public void setDetails(String var1);

    @Indexed
    @Accessor(value="CREATED_AT")
    public Long getCreatedAt();

    @Mutator(value="CREATED_AT")
    public void setCreatedAt(Long var1);
}

