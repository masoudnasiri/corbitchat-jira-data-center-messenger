/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.config.properties.ApplicationProperties
 *  com.atlassian.jira.issue.Issue
 */
package com.corbitlogic.jira.internalmessenger.util;

import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.Issue;

public final class JimIssueUrlBuilder {
    private JimIssueUrlBuilder() {
    }

    public static String buildBrowseUrl(ApplicationProperties applicationProperties, Issue issue) {
        if (issue == null) {
            return null;
        }
        return JimIssueUrlBuilder.buildBrowseUrl(applicationProperties, issue.getKey());
    }

    public static String buildBrowseUrl(ApplicationProperties applicationProperties, String issueKey) {
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return null;
        }
        String normalizedKey = issueKey.trim();
        String path = "/browse/" + normalizedKey;
        if (applicationProperties == null) {
            return path;
        }
        String baseUrl = applicationProperties.getString("jira.baseurl");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return path;
        }
        String trimmedBase = baseUrl.trim();
        if (trimmedBase.endsWith("/")) {
            return trimmedBase.substring(0, trimmedBase.length() - 1) + path;
        }
        return trimmedBase + path;
    }
}

