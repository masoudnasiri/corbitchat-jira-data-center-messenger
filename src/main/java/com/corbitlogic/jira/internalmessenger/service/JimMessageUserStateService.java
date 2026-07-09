package com.corbitlogic.jira.internalmessenger.service;

import java.util.Collection;
import java.util.Set;

/**
 * Per-user message visibility overlay (Sprint 12): "Delete for me". Hiding a
 * message never removes it — it only marks it hidden for one user. The message
 * stays in the database, visible to everyone else and to the reviewer.
 */
public interface JimMessageUserStateService {

    /** Hide a single message for a user (idempotent). */
    void hide(int messageId, int conversationId, String userKey);

    /** Hide several messages for a user in one transaction. */
    void hideAll(Collection<Integer> messageIds, int conversationId, String userKey);

    /** Message ids hidden for this user in the given conversation. */
    Set<Integer> hiddenMessageIds(String userKey, int conversationId);

    boolean isHidden(int messageId, String userKey);
}
