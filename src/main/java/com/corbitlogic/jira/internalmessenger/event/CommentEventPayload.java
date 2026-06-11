/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.event.comment.CommentCreatedEvent
 *  com.atlassian.jira.event.issue.IssueEvent
 *  com.atlassian.jira.issue.Issue
 *  com.atlassian.jira.issue.comments.Comment
 *  com.atlassian.jira.user.ApplicationUser
 */
package com.corbitlogic.jira.internalmessenger.event;

import com.atlassian.jira.event.comment.CommentCreatedEvent;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.user.ApplicationUser;

public final class CommentEventPayload {
    private final String issueKey;
    private final String issueSummary;
    private final String commentBody;
    private final Long commentId;
    private final String actorUserKey;
    private final String actorDisplayName;

    private CommentEventPayload(String issueKey, String issueSummary, String commentBody, Long commentId, String actorUserKey, String actorDisplayName) {
        this.issueKey = issueKey;
        this.issueSummary = issueSummary;
        this.commentBody = commentBody;
        this.commentId = commentId;
        this.actorUserKey = actorUserKey;
        this.actorDisplayName = actorDisplayName;
    }

    public static CommentEventPayload from(CommentCreatedEvent commentCreatedEvent) {
        if (commentCreatedEvent == null) {
            return null;
        }
        return CommentEventPayload.from(commentCreatedEvent.getComment());
    }

    public static CommentEventPayload from(IssueEvent issueEvent) {
        if (issueEvent == null) {
            return null;
        }
        return CommentEventPayload.from(issueEvent.getComment());
    }

    private static CommentEventPayload from(Comment comment) {
        if (comment == null) {
            return null;
        }
        Issue issue = comment.getIssue();
        if (issue == null) {
            return null;
        }
        String commentBody = comment.getBody();
        if (commentBody == null || commentBody.trim().isEmpty()) {
            return null;
        }
        ApplicationUser author = comment.getAuthorApplicationUser();
        if (author == null) {
            author = comment.getAuthorUser();
        }
        String actorDisplayName = author != null && author.getDisplayName() != null ? author.getDisplayName() : "Someone";
        return new CommentEventPayload(issue.getKey(), issue.getSummary(), commentBody, comment.getId(), author != null ? author.getKey() : null, actorDisplayName);
    }

    public String getIssueKey() {
        return this.issueKey;
    }

    public String getIssueSummary() {
        return this.issueSummary;
    }

    public String getCommentBody() {
        return this.commentBody;
    }

    public Long getCommentId() {
        return this.commentId;
    }

    public String getActorUserKey() {
        return this.actorUserKey;
    }

    public String getActorDisplayName() {
        return this.actorDisplayName;
    }
}

