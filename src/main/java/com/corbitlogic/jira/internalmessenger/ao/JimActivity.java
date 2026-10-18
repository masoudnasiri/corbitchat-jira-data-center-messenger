package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Ephemeral, cluster-safe chat activity state (Sprint 12): typing / recording /
 * uploading, scoped to a conversation and expiring on a short TTL. Stored in AO
 * (not in-memory) so presence is correct across Data Center nodes. This is NOT a
 * message and carries no draft text or file metadata — only the activity kind.
 * Rows are overwritten per (userKey, conversationId) and swept when expired.
 */
@Preload
@Table("JimActivity")
public interface JimActivity extends Entity {

    @Indexed
    @StringLength(255)
    String getUserKey();
    void setUserKey(String userKey);

    @Indexed
    int getConversationId();
    void setConversationId(int conversationId);

    /** typing | recording | uploading | uploading_media */
    @StringLength(32)
    String getActivity();
    void setActivity(String activity);

    Long getUpdatedAt();
    void setUpdatedAt(Long updatedAt);

    @Indexed
    Long getExpiresAt();
    void setExpiresAt(Long expiresAt);
}
