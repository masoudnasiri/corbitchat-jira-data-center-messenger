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
 *  net.java.ao.schema.Table
 */
package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Accessor;
import net.java.ao.Entity;
import net.java.ao.Mutator;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

@Preload
@Table(value="JimPushSub")
public interface JimPushSubscription
extends Entity {
    @Indexed
    @StringLength(value=255)
    @Accessor(value="USER_KEY")
    public String getUserKey();

    @Mutator(value="USER_KEY")
    public void setUserKey(String var1);

    @StringLength(value=-1)
    public String getEndpoint();

    public void setEndpoint(String var1);

    @Indexed
    @StringLength(value=64)
    @Accessor(value="ENDPOINT_HASH")
    public String getEndpointHash();

    @Mutator(value="ENDPOINT_HASH")
    public void setEndpointHash(String var1);

    @StringLength(value=255)
    @Accessor(value="P256DH_KEY")
    public String getP256dhKey();

    @Mutator(value="P256DH_KEY")
    public void setP256dhKey(String var1);

    @StringLength(value=255)
    @Accessor(value="AUTH_KEY")
    public String getAuthKey();

    @Mutator(value="AUTH_KEY")
    public void setAuthKey(String var1);

    public Long getCreatedAt();

    public void setCreatedAt(Long var1);
}

