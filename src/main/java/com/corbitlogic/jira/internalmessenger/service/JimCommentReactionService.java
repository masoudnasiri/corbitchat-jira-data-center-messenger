package com.corbitlogic.jira.internalmessenger.service;

import java.util.List;
import java.util.Map;

/**
 * Emoji reactions attached to native Jira comments (issue comments / report
 * comments and their replies). Keyed by the Jira comment id so the comment body
 * is never touched. Distinct from chat-message reactions ({@code JimReaction}).
 */
public interface JimCommentReactionService {

    /**
     * Toggle a reaction for a user on a comment (add if absent, remove if the
     * same user+emoji already exists). Returns the updated grouped reactions.
     */
    List<Map<String, Object>> toggle(long commentId, String issueKey, String userKey, String emoji);

    /** Grouped reactions for one comment: [{emoji, count, mine}], viewer-aware. */
    List<Map<String, Object>> listForComment(long commentId, String viewerKey);
}
