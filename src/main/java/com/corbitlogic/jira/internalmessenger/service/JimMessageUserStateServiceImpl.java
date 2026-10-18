package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.corbitlogic.jira.internalmessenger.ao.JimMessageUserState;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Inject;
import net.java.ao.Query;

public class JimMessageUserStateServiceImpl implements JimMessageUserStateService {

    private final ActiveObjects activeObjects;

    @Inject
    public JimMessageUserStateServiceImpl(ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    @Override
    public void hide(int messageId, int conversationId, String userKey) {
        if (userKey == null || userKey.isEmpty()) {
            return;
        }
        this.activeObjects.executeInTransaction(() -> {
            upsertHidden(messageId, conversationId, userKey);
            return null;
        });
    }

    @Override
    public void hideAll(Collection<Integer> messageIds, int conversationId, String userKey) {
        if (userKey == null || userKey.isEmpty() || messageIds == null || messageIds.isEmpty()) {
            return;
        }
        final Set<Integer> unique = new LinkedHashSet<>(messageIds);
        this.activeObjects.executeInTransaction(() -> {
            for (Integer id : unique) {
                if (id != null) {
                    upsertHidden(id, conversationId, userKey);
                }
            }
            return null;
        });
    }

    private void upsertHidden(int messageId, int conversationId, String userKey) {
        JimMessageUserState[] existing = this.activeObjects.find(JimMessageUserState.class,
                Query.select().where("MESSAGE_ID = ? AND USER_KEY = ?", messageId, userKey));
        JimMessageUserState row = existing.length > 0
                ? existing[0]
                : this.activeObjects.create(JimMessageUserState.class);
        row.setMessageId(messageId);
        row.setConversationId(conversationId);
        row.setUserKey(userKey);
        row.setHidden(1);
        row.setHiddenAt(System.currentTimeMillis());
        row.save();
    }

    @Override
    public Set<Integer> hiddenMessageIds(String userKey, int conversationId) {
        Set<Integer> ids = new HashSet<>();
        if (userKey == null || userKey.isEmpty()) {
            return ids;
        }
        JimMessageUserState[] rows = this.activeObjects.find(JimMessageUserState.class,
                Query.select().where("USER_KEY = ? AND CONVERSATION_ID = ? AND HIDDEN = ?",
                        userKey, conversationId, 1));
        for (JimMessageUserState row : rows) {
            ids.add(row.getMessageId());
        }
        return ids;
    }

    @Override
    public boolean isHidden(int messageId, String userKey) {
        if (userKey == null || userKey.isEmpty()) {
            return false;
        }
        JimMessageUserState[] rows = this.activeObjects.find(JimMessageUserState.class,
                Query.select().where("MESSAGE_ID = ? AND USER_KEY = ? AND HIDDEN = ?",
                        messageId, userKey, 1));
        return rows.length > 0;
    }
}
