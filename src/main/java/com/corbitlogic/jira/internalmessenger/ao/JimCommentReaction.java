package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Emoji reaction on a Jira issue comment / comment-reply (Sprint 12). Keyed by
 * the native Jira comment id so it never touches the comment body. One row per
 * (commentId, userKey, emoji). Permission (Browse/Comment) is enforced at the
 * REST layer, not here.
 */
@Preload
@Table("JimCommentReact")
public interface JimCommentReaction extends Entity {

    @Indexed
    long getCommentId();
    void setCommentId(long commentId);

    /** The issue key that owns the comment — used for permission checks + listing. */
    @Indexed
    @StringLength(255)
    String getIssueKey();
    void setIssueKey(String issueKey);

    @Indexed
    @StringLength(255)
    String getUserKey();
    void setUserKey(String userKey);

    @StringLength(64)
    String getEmoji();
    void setEmoji(String emoji);

    Long getCreatedAt();
    void setCreatedAt(Long createdAt);
}
