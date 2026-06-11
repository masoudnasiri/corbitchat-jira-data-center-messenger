/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.event.issue.IssueEvent
 *  com.atlassian.jira.event.type.EventType
 *  com.atlassian.jira.issue.Issue
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.jira.user.util.UserManager
 */
package com.corbitlogic.jira.internalmessenger.event;

import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.corbitlogic.jira.internalmessenger.listener.JimAssignmentChangeDetector;

public final class AssignmentEventPayload {
    private final String issueKey;
    private final String issueSummary;
    private final String assigneeUserKey;
    private final String actorUserKey;
    private final String actorDisplayName;
    private final String changeGroupSuffix;

    private AssignmentEventPayload(String issueKey, String issueSummary, String assigneeUserKey, String actorUserKey, String actorDisplayName, String changeGroupSuffix) {
        this.issueKey = issueKey;
        this.issueSummary = issueSummary;
        this.assigneeUserKey = assigneeUserKey;
        this.actorUserKey = actorUserKey;
        this.actorDisplayName = actorDisplayName;
        this.changeGroupSuffix = changeGroupSuffix;
    }

    public static AssignmentEventPayload from(IssueEvent issueEvent, UserManager userManager) {
        if (issueEvent == null || userManager == null) {
            return null;
        }
        Issue issue = issueEvent.getIssue();
        if (issue == null) {
            return null;
        }
        boolean assigneeChanged = JimAssignmentChangeDetector.isAssigneeChange(issueEvent);
        boolean explicitAssignmentEvent = EventType.ISSUE_ASSIGNED_ID.equals(issueEvent.getEventTypeId());
        if (!assigneeChanged && !explicitAssignmentEvent) {
            return null;
        }
        ApplicationUser assignee = JimAssignmentChangeDetector.extractNewAssignee(issueEvent, userManager);
        if (assignee == null || !assignee.isActive()) {
            return null;
        }
        ApplicationUser actor = issueEvent.getUser();
        String actorDisplayName = actor != null && actor.getDisplayName() != null ? actor.getDisplayName() : "Jira";
        return new AssignmentEventPayload(issue.getKey(), issue.getSummary(), assignee.getKey(), actor != null ? actor.getKey() : null, actorDisplayName, JimAssignmentChangeDetector.resolveChangeGroupSuffix(issueEvent));
    }

    public String getIssueKey() {
        return this.issueKey;
    }

    public String getIssueSummary() {
        return this.issueSummary;
    }

    public String getAssigneeUserKey() {
        return this.assigneeUserKey;
    }

    public String getActorUserKey() {
        return this.actorUserKey;
    }

    public String getActorDisplayName() {
        return this.actorDisplayName;
    }

    public String getChangeGroupSuffix() {
        return this.changeGroupSuffix;
    }
}

