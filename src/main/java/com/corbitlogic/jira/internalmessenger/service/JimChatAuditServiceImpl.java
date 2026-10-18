package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.corbitlogic.jira.internalmessenger.ao.JimChatAudit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimChatAuditServiceImpl implements JimChatAuditService {

    private static final Logger log = LoggerFactory.getLogger(JimChatAuditServiceImpl.class);
    private static final int DETAILS_MAX = 4000;

    private final ActiveObjects activeObjects;

    @Inject
    public JimChatAuditServiceImpl(ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    @Override
    public void record(String actorUserKey, String action, Integer messageId,
                       Integer conversationId, String targetUserKey, String details) {
        if (action == null) {
            return;
        }
        try {
            this.activeObjects.executeInTransaction(() -> {
                JimChatAudit row = this.activeObjects.create(JimChatAudit.class);
                row.setActorUserKey(truncate(actorUserKey, 255));
                row.setAction(truncate(action, 64));
                row.setMessageId(messageId);
                row.setConversationId(conversationId);
                row.setTargetUserKey(truncate(targetUserKey, 255));
                row.setDetails(truncate(details, DETAILS_MAX));
                row.setCreatedAt(System.currentTimeMillis());
                row.save();
                return row;
            });
        } catch (Exception ex) {
            // Audit must never break the user action; log the failure only.
            log.warn("chat audit record failed action={} : {}", action, ex.getMessage());
        }
    }

    @Override
    public List<JimChatAudit> recent(int limit) {
        int capped = limit <= 0 ? 50 : Math.min(limit, 500);
        try {
            JimChatAudit[] rows = this.activeObjects.find(JimChatAudit.class,
                    Query.select().order("CREATED_AT DESC").limit(capped));
            return Arrays.asList(rows);
        } catch (Exception ex) {
            log.warn("chat audit query failed: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
