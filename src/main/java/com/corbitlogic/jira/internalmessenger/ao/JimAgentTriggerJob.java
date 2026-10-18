package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Durable trigger job + idempotency ledger for the ChatGPT Workspace Agent
 * two-way chat integration. One row per eligible incoming CorbitChat message
 * (idempotency key {@code corbit-message-{messageId}}). The row is the single
 * source of truth for whether a message has been triggered, so both the
 * near-real-time path and the hourly fallback scan share it and never double-fire.
 * Secrets are NEVER stored here.
 */
@Preload
@Table("JimAgentJob")
public interface JimAgentTriggerJob extends Entity {

    /** Unique idempotency key: {@code corbit-message-{messageId}}. */
    @Indexed
    @StringLength(255)
    String getIdempotencyKey();
    void setIdempotencyKey(String v);

    @Indexed
    int getMessageId();
    void setMessageId(int v);

    @Indexed
    int getConversationId();
    void setConversationId(int v);

    @StringLength(255)
    String getSenderUserKey();
    void setSenderUserKey(String v);

    /** Stable per-conversation thread key: {@code corbit:{tenantId}:{conversationId}}. */
    @StringLength(255)
    String getThreadKey();
    void setThreadKey(String v);

    /** pending | running | succeeded | failed | dead_letter. */
    @Indexed
    @StringLength(20)
    String getStatus();
    void setStatus(String v);

    int getAttempts();
    void setAttempts(int v);

    @StringLength(StringLength.UNLIMITED)
    String getLastError();
    void setLastError(String v);

    /** Workspace Agent run id, when the trigger response returns one. */
    @StringLength(255)
    String getAgentRunId();
    void setAgentRunId(String v);

    /** Source of the enqueue: realtime | fallback_scan | test. */
    @StringLength(20)
    String getSource();
    void setSource(String v);

    @Indexed
    Long getCreatedAt();
    void setCreatedAt(Long v);

    Long getUpdatedAt();
    void setUpdatedAt(Long v);

    Long getCompletedAt();
    void setCompletedAt(Long v);

    /** Next earliest attempt time (backoff), epoch ms. */
    @Indexed
    Long getNextAttemptAt();
    void setNextAttemptAt(Long v);
}
