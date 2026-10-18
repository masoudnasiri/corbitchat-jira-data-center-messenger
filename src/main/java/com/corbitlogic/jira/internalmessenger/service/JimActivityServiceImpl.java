package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.corbitlogic.jira.internalmessenger.ao.JimActivity;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimActivityServiceImpl implements JimActivityService {

    private static final Logger log = LoggerFactory.getLogger(JimActivityServiceImpl.class);
    /** Activity is shown for this long after the last heartbeat, then expires. */
    private static final long TTL_MS = 8000L;

    private final ActiveObjects activeObjects;

    @Inject
    public JimActivityServiceImpl(ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    private static String normalize(String activity) {
        if (activity == null) {
            return TYPING;
        }
        switch (activity.trim().toLowerCase()) {
            case RECORDING: return RECORDING;
            case UPLOADING: return UPLOADING;
            case UPLOADING_MEDIA: return UPLOADING_MEDIA;
            default: return TYPING;
        }
    }

    @Override
    public void record(final String userKey, final int conversationId, final String activity) {
        if (userKey == null || userKey.trim().isEmpty() || conversationId <= 0) {
            return;
        }
        final String kind = normalize(activity);
        final long now = System.currentTimeMillis();
        try {
            this.activeObjects.executeInTransaction(() -> {
                JimActivity[] rows = this.activeObjects.find(JimActivity.class,
                        Query.select().where("USER_KEY = ? AND CONVERSATION_ID = ?", userKey, conversationId).limit(1));
                JimActivity row = rows.length > 0 ? rows[0] : this.activeObjects.create(JimActivity.class);
                row.setUserKey(userKey);
                row.setConversationId(conversationId);
                row.setActivity(kind);
                row.setUpdatedAt(now);
                row.setExpiresAt(now + TTL_MS);
                row.save();
                return null;
            });
        } catch (Exception ex) {
            log.debug("activity record failed: {}", ex.getClass().getSimpleName());
        }
    }

    @Override
    public void clear(final String userKey, final int conversationId) {
        if (userKey == null || conversationId <= 0) {
            return;
        }
        try {
            this.activeObjects.executeInTransaction(() -> {
                JimActivity[] rows = this.activeObjects.find(JimActivity.class,
                        Query.select().where("USER_KEY = ? AND CONVERSATION_ID = ?", userKey, conversationId));
                if (rows.length > 0) {
                    this.activeObjects.delete(rows);
                }
                return null;
            });
        } catch (Exception ex) {
            log.debug("activity clear failed: {}", ex.getClass().getSimpleName());
        }
    }

    @Override
    public List<JimActivity> activeFor(int conversationId, String excludeUserKey) {
        List<JimActivity> out = new ArrayList<>();
        if (conversationId <= 0) {
            return out;
        }
        long now = System.currentTimeMillis();
        try {
            JimActivity[] rows = this.activeObjects.find(JimActivity.class,
                    Query.select().where("CONVERSATION_ID = ? AND EXPIRES_AT > ?", conversationId, now));
            for (JimActivity r : rows) {
                if (excludeUserKey != null && excludeUserKey.equals(r.getUserKey())) {
                    continue;
                }
                out.add(r);
            }
        } catch (Exception ex) {
            log.debug("activity read failed: {}", ex.getClass().getSimpleName());
        }
        return out;
    }
}
