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
public interface JimAccessPolicy
extends Entity {
    @Indexed
    @StringLength(value=20)
    @Accessor(value="SOURCE_TYPE")
    public String getSourceType();

    @Mutator(value="SOURCE_TYPE")
    public void setSourceType(String var1);

    @StringLength(value=255)
    @Accessor(value="SOURCE_VALUE")
    public String getSourceValue();

    @Mutator(value="SOURCE_VALUE")
    public void setSourceValue(String var1);

    @StringLength(value=20)
    @Accessor(value="TARGET_TYPE")
    public String getTargetType();

    @Mutator(value="TARGET_TYPE")
    public void setTargetType(String var1);

    @StringLength(value=255)
    @Accessor(value="TARGET_VALUE")
    public String getTargetValue();

    @Mutator(value="TARGET_VALUE")
    public void setTargetValue(String var1);

    @StringLength(value=10)
    public String getAction();

    public void setAction(String var1);

    @Accessor(value="CAN_SEARCH")
    public Boolean getCanSearch();

    @Mutator(value="CAN_SEARCH")
    public void setCanSearch(Boolean var1);

    @Accessor(value="CAN_START_CHAT")
    public Boolean getCanStartChat();

    @Mutator(value="CAN_START_CHAT")
    public void setCanStartChat(Boolean var1);

    @Accessor(value="CAN_RECEIVE_CHAT")
    public Boolean getCanReceiveChat();

    @Mutator(value="CAN_RECEIVE_CHAT")
    public void setCanReceiveChat(Boolean var1);

    public Boolean getEnabled();

    public void setEnabled(Boolean var1);

    public Integer getPriority();

    public void setPriority(Integer var1);

    @StringLength(value=255)
    @Accessor(value="CREATED_BY")
    public String getCreatedBy();

    @Mutator(value="CREATED_BY")
    public void setCreatedBy(String var1);

    @Accessor(value="CREATED_AT")
    public Long getCreatedAt();

    @Mutator(value="CREATED_AT")
    public void setCreatedAt(Long var1);

    @Accessor(value="UPDATED_AT")
    public Long getUpdatedAt();

    @Mutator(value="UPDATED_AT")
    public void setUpdatedAt(Long var1);
}

