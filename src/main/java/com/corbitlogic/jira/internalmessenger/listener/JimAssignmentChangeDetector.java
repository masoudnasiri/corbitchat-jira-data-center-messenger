/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.event.issue.IssueEvent
 *  com.atlassian.jira.event.type.EventType
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.jira.user.util.UserManager
 *  org.ofbiz.core.entity.GenericValue
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.listener;

import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import java.util.List;
import org.ofbiz.core.entity.GenericValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JimAssignmentChangeDetector {
    private static final Logger log = LoggerFactory.getLogger(JimAssignmentChangeDetector.class);
    private static final String ASSIGNEE_FIELD = "assignee";

    private JimAssignmentChangeDetector() {
    }

    public static boolean isAssigneeChange(IssueEvent issueEvent) {
        if (issueEvent == null) {
            return false;
        }
        GenericValue changeLog = issueEvent.getChangeLog();
        if (changeLog == null) {
            log.debug("event=assignment stage=detect outcome=skipped reason=no_changelog");
            return false;
        }
        try {
            List<GenericValue> changeItems = changeLog.getRelated("ChildChangeItem");
            if (changeItems == null || changeItems.isEmpty()) {
                log.debug("event=assignment stage=detect outcome=skipped reason=no_change_items");
                return false;
            }
            for (GenericValue item : changeItems) {
                String field = item.getString("field");
                if (field == null || !ASSIGNEE_FIELD.equalsIgnoreCase(field)) continue;
                return true;
            }
        }
        catch (Exception ex) {
            log.debug("event=assignment stage=detect outcome=error message={}", (Object)ex.getMessage());
        }
        return false;
    }

    public static ApplicationUser extractNewAssignee(IssueEvent issueEvent, UserManager userManager) {
        if (issueEvent == null || userManager == null) {
            return null;
        }
        GenericValue changeLog = issueEvent.getChangeLog();
        if (changeLog != null) {
            try {
                List<GenericValue> changeItems = changeLog.getRelated("ChildChangeItem");
                if (changeItems != null) {
                    for (GenericValue item : changeItems) {
                        ApplicationUser resolved;
                        String field = item.getString("field");
                        if (field == null || !ASSIGNEE_FIELD.equalsIgnoreCase(field) || (resolved = JimAssignmentChangeDetector.resolveUserFromChangeValue(userManager, item.getString("newvalue"), item.getString("newstring"))) == null) continue;
                        return resolved;
                    }
                }
            }
            catch (Exception ex) {
                log.debug("event=assignment stage=extract outcome=error message={}", (Object)ex.getMessage());
            }
        }
        if (EventType.ISSUE_ASSIGNED_ID.equals(issueEvent.getEventTypeId()) && issueEvent.getIssue() != null) {
            return issueEvent.getIssue().getAssignee();
        }
        return null;
    }

    public static String resolveChangeGroupSuffix(IssueEvent issueEvent) {
        if (issueEvent == null) {
            return "no-event";
        }
        GenericValue changeLog = issueEvent.getChangeLog();
        if (changeLog == null) {
            return "no-changelog";
        }
        Long groupId = changeLog.getLong("id");
        if (groupId != null) {
            return String.valueOf(groupId);
        }
        Object rawId = changeLog.get("id");
        return rawId != null ? String.valueOf(rawId) : "no-changelog";
    }

    private static ApplicationUser resolveUserFromChangeValue(UserManager userManager, String newValue, String newString) {
        ApplicationUser user = JimAssignmentChangeDetector.resolveUserReference(userManager, newValue);
        if (user != null) {
            return user;
        }
        return JimAssignmentChangeDetector.resolveUserReference(userManager, newString);
    }

    private static ApplicationUser resolveUserReference(UserManager userManager, String reference) {
        if (reference == null) {
            return null;
        }
        String trimmed = reference.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        ApplicationUser byKey = userManager.getUserByKey(trimmed);
        if (byKey != null) {
            return byKey;
        }
        return userManager.getUserByName(trimmed);
    }
}

