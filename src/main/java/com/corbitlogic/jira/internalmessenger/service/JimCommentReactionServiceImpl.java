package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.corbitlogic.jira.internalmessenger.ao.JimCommentReaction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.java.ao.Query;

/**
 * Default {@link JimCommentReactionService} backed by the {@link JimCommentReaction}
 * Active Objects entity. Permission checks live at the REST layer.
 */
public class JimCommentReactionServiceImpl implements JimCommentReactionService {

    private static final int MAX_EMOJI_LEN = 64;
    private final ActiveObjects activeObjects;

    public JimCommentReactionServiceImpl(ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    @Override
    public List<Map<String, Object>> toggle(long commentId, String issueKey, String userKey, String emoji) {
        String e = emoji == null ? "" : emoji.trim();
        if (e.isEmpty() || userKey == null) {
            return listForComment(commentId, userKey);
        }
        if (e.length() > MAX_EMOJI_LEN) {
            e = e.substring(0, MAX_EMOJI_LEN);
        }
        JimCommentReaction[] existing = this.activeObjects.find(JimCommentReaction.class,
                Query.select().where("COMMENT_ID = ? AND USER_KEY = ? AND EMOJI = ?",
                        commentId, userKey, e));
        if (existing.length > 0) {
            this.activeObjects.delete(existing); // toggle off
        } else {
            JimCommentReaction row = this.activeObjects.create(JimCommentReaction.class);
            row.setCommentId(commentId);
            row.setIssueKey(issueKey);
            row.setUserKey(userKey);
            row.setEmoji(e);
            row.setCreatedAt(System.currentTimeMillis());
            row.save();
        }
        return listForComment(commentId, userKey);
    }

    @Override
    public List<Map<String, Object>> listForComment(long commentId, String viewerKey) {
        JimCommentReaction[] rows = this.activeObjects.find(JimCommentReaction.class,
                Query.select().where("COMMENT_ID = ?", commentId));
        // Preserve first-seen order of emojis.
        LinkedHashMap<String, int[]> counts = new LinkedHashMap<String, int[]>(); // emoji -> [count, mine]
        for (JimCommentReaction r : rows) {
            String emoji = r.getEmoji();
            if (emoji == null) {
                continue;
            }
            int[] agg = counts.get(emoji);
            if (agg == null) {
                agg = new int[]{0, 0};
                counts.put(emoji, agg);
            }
            agg[0]++;
            if (viewerKey != null && viewerKey.equals(r.getUserKey())) {
                agg[1] = 1;
            }
        }
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, int[]> en : counts.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("emoji", en.getKey());
            m.put("count", en.getValue()[0]);
            m.put("mine", en.getValue()[1] == 1);
            out.add(m);
        }
        return out;
    }
}
