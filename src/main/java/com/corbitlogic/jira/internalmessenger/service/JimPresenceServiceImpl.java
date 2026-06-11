/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.service.JimPresenceService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JimPresenceServiceImpl
implements JimPresenceService {
    private static final long ACTIVE_WINDOW_MS = 70000L;
    private static final long VIEWING_WINDOW_MS = 20000L;
    private final Map<String, Long> lastSeenByUserKey = new ConcurrentHashMap<String, Long>();
    private final Map<String, long[]> viewedConversationByUserKey = new ConcurrentHashMap<String, long[]>();

    @Override
    public void heartbeat(String userKey) {
        if (userKey == null || userKey.isEmpty()) {
            return;
        }
        this.lastSeenByUserKey.put(userKey, System.currentTimeMillis());
    }

    @Override
    public void heartbeatConversation(String userKey, int conversationId) {
        if (userKey == null || userKey.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        this.lastSeenByUserKey.put(userKey, now);
        this.viewedConversationByUserKey.put(userKey, new long[]{conversationId, now});
    }

    @Override
    public boolean isActive(String userKey) {
        if (userKey == null || userKey.isEmpty()) {
            return false;
        }
        Long lastSeen = this.lastSeenByUserKey.get(userKey);
        return lastSeen != null && System.currentTimeMillis() - lastSeen <= 70000L;
    }

    @Override
    public boolean isViewingConversation(String userKey, int conversationId) {
        if (userKey == null || userKey.isEmpty()) {
            return false;
        }
        long[] viewed = this.viewedConversationByUserKey.get(userKey);
        return viewed != null && viewed[0] == (long)conversationId && System.currentTimeMillis() - viewed[1] <= 20000L;
    }
}

