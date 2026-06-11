/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.issue.Issue
 *  com.atlassian.jira.user.ApplicationUser
 */
package com.corbitlogic.jira.internalmessenger.util;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class JimNotificationRecipientResolver {
    private JimNotificationRecipientResolver() {
    }

    public static List<String> resolveStatusChangeRecipients(Issue issue) {
        if (issue == null) {
            return List.of();
        }
        LinkedHashSet<String> recipientKeys = new LinkedHashSet<String>();
        JimNotificationRecipientResolver.addActiveUserKey(recipientKeys, issue.getAssignee());
        JimNotificationRecipientResolver.addActiveUserKey(recipientKeys, issue.getReporter());
        return new ArrayList<String>(recipientKeys);
    }

    private static void addActiveUserKey(Set<String> recipientKeys, ApplicationUser user) {
        if (user == null || !user.isActive()) {
            return;
        }
        String userKey = user.getKey();
        if (userKey != null && !userKey.trim().isEmpty()) {
            recipientKeys.add(userKey.trim());
        }
    }
}

