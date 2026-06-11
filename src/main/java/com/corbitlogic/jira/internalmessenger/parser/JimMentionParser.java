/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.jira.user.util.UserManager
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.parser;

import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimMentionParser {
    private static final Logger log = LoggerFactory.getLogger(JimMentionParser.class);
    private static final Pattern ACCOUNT_ID_MENTION = Pattern.compile("\\[~accountid:([^\\]]+)]");
    private static final Pattern USER_KEY_MENTION = Pattern.compile("\\[~userkey:([^\\]]+)]", 2);
    private static final Pattern USERNAME_MENTION = Pattern.compile("\\[~(?!accountid:|userkey:)([^\\]]+)]", 2);
    private final UserManager userManager;

    public JimMentionParser(UserManager userManager) {
        this.userManager = userManager;
    }

    public Set<ApplicationUser> parseMentionedUsers(String commentBody) {
        if (commentBody == null || commentBody.trim().isEmpty()) {
            return Set.of();
        }
        try {
            log.debug("event=mention stage=parse outcome=invoked bodyLength={}", (Object)commentBody.length());
            LinkedHashSet<String> tokens = new LinkedHashSet<String>();
            this.collectTokens(ACCOUNT_ID_MENTION, commentBody, tokens);
            this.collectTokens(USER_KEY_MENTION, commentBody, tokens);
            this.collectTokens(USERNAME_MENTION, commentBody, tokens);
            if (tokens.isEmpty()) {
                log.debug("event=mention stage=parse outcome=skipped reason=no_tokens");
                return Set.of();
            }
            log.debug("event=mention stage=parse outcome=tokens_found count={} tokens={}", (Object)tokens.size(), tokens);
            LinkedHashSet<ApplicationUser> mentionedUsers = new LinkedHashSet<ApplicationUser>();
            LinkedHashSet<String> resolvedKeys = new LinkedHashSet<String>();
            for (String token : tokens) {
                ApplicationUser user = this.resolveMentionToken(token);
                if (user == null) {
                    log.debug("event=mention stage=resolve outcome=skipped reason=unresolved token={}", (Object)token);
                    continue;
                }
                if (!user.isActive()) {
                    log.debug("event=mention stage=resolve outcome=skipped reason=inactive token={} userKey={}", (Object)token, (Object)user.getKey());
                    continue;
                }
                if (!resolvedKeys.add(user.getKey())) continue;
                mentionedUsers.add(user);
            }
            log.debug("event=mention stage=parse outcome=resolved count={}", (Object)mentionedUsers.size());
            return mentionedUsers;
        }
        catch (Exception ex) {
            log.debug("event=mention stage=parse outcome=error message={}", (Object)ex.getMessage());
            return Set.of();
        }
    }

    public List<ApplicationUser> parseMentionedUsersAsList(String commentBody) {
        return new ArrayList<ApplicationUser>(this.parseMentionedUsers(commentBody));
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

