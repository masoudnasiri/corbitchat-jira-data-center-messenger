/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.jira.user.util.UserManager
 */
package com.corbitlogic.jira.internalmessenger.util;

import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MentionParser {
    private static final Pattern ACCOUNT_ID_MENTION = Pattern.compile("\\[~accountid:([^\\]]+)]");
    private static final Pattern USER_KEY_MENTION = Pattern.compile("\\[~userkey:([^\\]]+)]", 2);
    private static final Pattern USERNAME_MENTION = Pattern.compile("\\[~(?!accountid:|userkey:)([^\\]]+)]", 2);
    private final UserManager userManager;

    public MentionParser(UserManager userManager) {
        this.userManager = userManager;
    }

    public List<ApplicationUser> parseMentionedUsers(String commentBody) {
        if (commentBody == null || commentBody.trim().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<String>();
        this.collectTokens(ACCOUNT_ID_MENTION, commentBody, tokens);
        this.collectTokens(USER_KEY_MENTION, commentBody, tokens);
        this.collectTokens(USERNAME_MENTION, commentBody, tokens);
        ArrayList<ApplicationUser> mentionedUsers = new ArrayList<ApplicationUser>();
        LinkedHashSet<String> resolvedKeys = new LinkedHashSet<String>();
        for (String token : tokens) {
            ApplicationUser user = this.resolveMentionToken(token);
            if (user == null || !user.isActive() || !resolvedKeys.add(user.getKey())) continue;
            mentionedUsers.add(user);
        }
        return mentionedUsers;
    }

    public String excerpt(String commentBody, int maxLength) {
        if (commentBody == null) {
            return "";
        }
        String normalized = commentBody.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void collectTokens(Pattern pattern, String commentBody, Set<String> tokens) {
        Matcher matcher = pattern.matcher(commentBody);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token == null || token.trim().isEmpty()) continue;
            tokens.add(token.trim());
        }
    }

    private ApplicationUser resolveMentionToken(String token) {
        ApplicationUser byKey = this.userManager.getUserByKey(token);
        if (byKey != null) {
            return byKey;
        }
        return this.userManager.getUserByName(token);
    }
}

