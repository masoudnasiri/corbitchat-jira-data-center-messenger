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
public interface JimMessage
extends Entity {
    @Indexed
    public int getConversationId();

    public void setConversationId(int var1);

    @StringLength(value=50)
    public String getSenderType();

    public void setSenderType(String var1);

    @Indexed
    @StringLength(value=255)
    public String getSenderUserKey();

    public void setSenderUserKey(String var1);

    @StringLength(value=-1)
    public String getBody();

    public void setBody(String var1);

    @StringLength(value=50)
    public String getBodyFormat();

    public void setBodyFormat(String var1);

    @StringLength(value=50)
    public String getEventType();

    public void setEventType(String var1);

    @Indexed
    @StringLength(value=64)
    public String getIssueKey();

    public void setIssueKey(String var1);

    @StringLength(value=-1)
    public String getIssueSummary();

    public void setIssueSummary(String var1);

    @StringLength(value=-1)
    public String getIssueUrl();

    public void setIssueUrl(String var1);

    @StringLength(value=255)
    public String getActorUserKey();

    public void setActorUserKey(String var1);

    @StringLength(value=255)
    public String getActorDisplayName();

    public void setActorDisplayName(String var1);

    @Indexed
    public Long getCreatedAt();

    public void setCreatedAt(Long var1);

    @Indexed
    @Accessor(value="REPLY_TO_MESSAGE_ID")
    public Long getReplyToMessageId();

    @Mutator(value="REPLY_TO_MESSAGE_ID")
    public void setReplyToMessageId(Long var1);

    @Accessor(value="EDITED")
    public Integer getEdited();

    @Mutator(value="EDITED")
    public void setEdited(Integer var1);

    @Accessor(value="EDITED_AT")
    public Long getEditedAt();

    @Mutator(value="EDITED_AT")
    public void setEditedAt(Long var1);

    @Accessor(value="DELETED")
    public Integer getDeleted();

    @Mutator(value="DELETED")
    public void setDeleted(Integer var1);

    @Accessor(value="DELETED_AT")
    public Long getDeletedAt();

    @Mutator(value="DELETED_AT")
    public void setDeletedAt(Long var1);

    @StringLength(value=255)
    @Accessor(value="DELETED_BY_USER_KEY")
    public String getDeletedByUserKey();

    @Mutator(value="DELETED_BY_USER_KEY")
    public void setDeletedByUserKey(String var1);

    @Accessor(value="PINNED")
    public Integer getPinned();

    @Mutator(value="PINNED")
    public void setPinned(Integer var1);

    /**
     * 1 when the recipient of a Jira Assistant / system message has
     * acknowledged or acted on it (clicked the issue link, clicked
     * 'Mark as seen', etc.), otherwise 0/null. Used only for system
     * messages, which are 1:1 per user (each user has their own system
     * conversation), so storing this on the message row is effectively
     * per-user. Auto-migrated as nullable; legacy rows render in the
     * default (not-actioned) state.
     */
    @Accessor(value="ACTIONED")
    public Integer getActioned();

    @Mutator(value="ACTIONED")
    public void setActioned(Integer var1);

    @Accessor(value="ACTIONED_AT")
    public Long getActionedAt();

    @Mutator(value="ACTIONED_AT")
    public void setActionedAt(Long var1);
}

