package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Per-user, per-message visibility overlay (Sprint 12). A row means the message
 * is hidden ("Delete for me") for {@code userKey}: the message row itself is
 * never removed and stays visible to everyone else. Additive, non-destructive.
 *
 * <p>AO prefixes tables with {@code AO_xxxxxx_} and caps the total at 30 chars,
 * so the table name is shortened.</p>
 */
@Preload
@Table("JimMsgUserState")
public interface JimMessageUserState extends Entity {

    @Indexed
    int getMessageId();
    void setMessageId(int messageId);

    @Indexed
    int getConversationId();
    void setConversationId(int conversationId);

    @Indexed
    @StringLength(255)
    String getUserKey();
    void setUserKey(String userKey);

    /** 1 = hidden for this user ("delete for me"). */
    Integer getHidden();
    void setHidden(Integer hidden);

    Long getHiddenAt();
    void setHiddenAt(Long hiddenAt);
}
