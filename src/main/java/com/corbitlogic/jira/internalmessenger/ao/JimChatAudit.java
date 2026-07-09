package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Governance audit trail for chat actions (Sprint 12): soft-delete,
 * delete-for-me, and hidden-reviewer access. Append-only; never exposed to
 * normal users. Message BODY content is not stored here — only metadata — to
 * avoid duplicating message content into application logs/audit unless the
 * retained message row itself is the audit source.
 */
@Preload
@Table("JimChatAudit")
public interface JimChatAudit extends Entity {

    @Indexed
    @StringLength(255)
    String getActorUserKey();
    void setActorUserKey(String actorUserKey);

    /** e.g. message.softDelete, message.hideForMe, reviewer.access, reviewer.login. */
    @StringLength(64)
    String getAction();
    void setAction(String action);

    Integer getMessageId();
    void setMessageId(Integer messageId);

    Integer getConversationId();
    void setConversationId(Integer conversationId);

    @StringLength(255)
    String getTargetUserKey();
    void setTargetUserKey(String targetUserKey);

    @StringLength(StringLength.UNLIMITED)
    String getDetails();
    void setDetails(String details);

    @Indexed
    Long getCreatedAt();
    void setCreatedAt(Long createdAt);
}
