/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.event.issue.IssueEvent
 *  com.atlassian.jira.issue.Issue
 *  com.atlassian.jira.user.ApplicationUser
 */
package com.corbitlogic.jira.internalmessenger.event;

import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.listener.JimStatusChangeDetector;
import com.corbitlogic.jira.internalmessenger.util.JimNotificationRecipientResolver;
import java.util.List;

public final class StatusChangeEventPayload {
    private final String issueKey;
    private final Long issueId;
    private final String issueSummary;
    private final String actorUserKey;
    private final String actorDisplayName;
    private final String fromStatus;
    private final String toStatus;
    private final Long eventTime;
    private final String changeGroupId;
    private final List<String> recipientUserKeys;

    private StatusChangeEventPayload(String issueKey, Long issueId, String issueSummary, String actorUserKey, String actorDisplayName, String fromStatus, String toStatus, Long eventTime, String changeGroupId, List<String> recipientUserKeys) {
        this.issueKey = issueKey;
        this.issueId = issueId;
        this.issueSummary = issueSummary;
        this.actorUserKey = actorUserKey;
        this.actorDisplayName = actorDisplayName;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.eventTime = eventTime;
        this.changeGroupId = changeGroupId;
        this.recipientUserKeys = recipientUserKeys;
    }

    public static StatusChangeEventPayload from(IssueEvent issueEvent) {
        if (issueEvent == null) {
            return null;
        }
        Issue issue = issueEvent.getIssue();
        if (issue == null) {
            return null;
        }
        JimStatusChangeDetector.StatusChangeDetails details = JimStatusChangeDetector.detect(issueEvent);
        if (details == null) {
            return null;
        }
        ApplicationUser actor = issueEvent.getUser();
        String actorDisplayName = actor != null && actor.getDisplayName() != null ? actor.getDisplayName() : "Jira";
        List<String> recipientUserKeys = JimNotificationRecipientResolver.resolveStatusChangeRecipients(issue);
        if (recipientUserKeys.isEmpty()) {
            return null;
        }
        Long eventTime = issueEvent.getTime() != null ? issueEvent.getTime().getTime() : System.currentTimeMillis();
        return new StatusChangeEventPayload(issue.getKey(), issue.getId(), issue.getSummary(), actor != null ? actor.getKey() : null, actorDisplayName, details.getFromStatus(), details.getToStatus(), eventTime, details.getChangeGroupSuffix(), recipientUserKeys);
    }

    public String getIssueKey() {
        return this.issueKey;
    }

    public Long getIssueId() {
        return this.issueId;
    }

    public String getIssueSummary() {
        return this.issueSummary;
    }

    public String getActorUserKey() {
        return this.actorUserKey;
    }

    public String getActorDisplayName() {
        return this.actorDisplayName;
    }

    public String getFromStatus() {
        return this.fromStatus;
    }

    public String getToStatus() {
        return this.toStatus;
    }

    public Long getEventTime() {
        return this.eventTime;
    }

    public String getChangeGroupId() {
        return this.changeGroupId;
    }

    public List<String> getRecipientUserKeys() {
        return this.recipientUserKeys;
    }
}

