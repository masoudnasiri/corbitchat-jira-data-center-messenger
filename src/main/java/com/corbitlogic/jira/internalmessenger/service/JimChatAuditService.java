package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.ao.JimChatAudit;
import java.util.List;

/** Append-only governance audit for chat actions (Sprint 12). */
public interface JimChatAuditService {

    void record(String actorUserKey, String action, Integer messageId,
                Integer conversationId, String targetUserKey, String details);

    /** Most-recent audit entries (reviewer only). */
    List<JimChatAudit> recent(int limit);
}
