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
public interface JimConversation
extends Entity {
    @Indexed
    @StringLength(value=50)
    public String getConversationType();

    public void setConversationType(String var1);

    @Indexed
    @StringLength(value=255)
    @Accessor(value="USER_A_KEY")
    public String getUserAKey();

    @Mutator(value="USER_A_KEY")
    public void setUserAKey(String var1);

    @Indexed
    @StringLength(value=255)
    @Accessor(value="USER_B_KEY")
    public String getUserBKey();

    @Mutator(value="USER_B_KEY")
    public void setUserBKey(String var1);

    @Indexed
    @StringLength(value=100)
    public String getSystemKey();

    public void setSystemKey(String var1);

    public Long getCreatedAt();

    public void setCreatedAt(Long var1);

    public Long getUpdatedAt();

    public void setUpdatedAt(Long var1);

    public Long getLastMessageAt();

    public void setLastMessageAt(Long var1);

    @StringLength(value=-1)
    public String getLastMessagePreview();

    public void setLastMessagePreview(String var1);

    /**
     * Sender user key of the most recent non-system message in this
     * conversation. Used by the sidebar to colour the last-message
     * preview by ownership (light blue for the current user, light gray
     * for others). Nullable on legacy rows that pre-date this column;
     * the client renders the neutral style in that case.
     */
    @StringLength(value=255)
    @Accessor(value="LAST_MESSAGE_SENDER_USER_KEY")
    public String getLastMessageSenderUserKey();

    @Mutator(value="LAST_MESSAGE_SENDER_USER_KEY")
    public void setLastMessageSenderUserKey(String var1);

    @StringLength(value=255)
    @Accessor(value="GROUP_NAME")
    public String getGroupName();

    @Mutator(value="GROUP_NAME")
    public void setGroupName(String var1);

    @StringLength(value=255)
    @Accessor(value="CREATED_BY_USER_KEY")
    public String getCreatedByUserKey();

    @Mutator(value="CREATED_BY_USER_KEY")
    public void setCreatedByUserKey(String var1);

    @Indexed
    @StringLength(value=64)
    @Accessor(value="PROJECT_KEY")
    public String getProjectKey();

    @Mutator(value="PROJECT_KEY")
    public void setProjectKey(String var1);

    /**
     * Sprint 12 governance: 1 when a group has been soft-archived ("deleted" by
     * its owner). Archived groups are hidden from normal users but ALL history
     * (messages, members, attachments, reactions, audit) is retained for
     * complaint review. Additive/nullable — legacy rows read as not-archived.
     */
    @Accessor(value="ARCHIVED")
    public Integer getArchived();

    @Mutator(value="ARCHIVED")
    public void setArchived(Integer var1);

    @Accessor(value="ARCHIVED_AT")
    public Long getArchivedAt();

    @Mutator(value="ARCHIVED_AT")
    public void setArchivedAt(Long var1);

    @StringLength(value=255)
    @Accessor(value="ARCHIVED_BY_USER_KEY")
    public String getArchivedByUserKey();

    @Mutator(value="ARCHIVED_BY_USER_KEY")
    public void setArchivedByUserKey(String var1);
}

