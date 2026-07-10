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

    /**
     * Render a raw Jira comment body into readable text for the Assistant:
     * mention tokens ([~accountid:x] / [~userkey:x] / [~name]) are replaced with
     * "@DisplayName" (falling back to the raw token when unresolved). Line
     * breaks are preserved; no HTML is produced (clients escape on render). The
     * result is capped to {@code maxLength} characters so a huge comment cannot
     * bloat storage — the "full text" the product asks for, within a sane bound.
     */
    public String renderReadable(String commentBody, int maxLength) {
        if (commentBody == null) {
            return "";
        }
        String rendered = commentBody;
        try {
            rendered = replaceMentions(ACCOUNT_ID_MENTION, rendered);
            rendered = replaceMentions(USER_KEY_MENTION, rendered);
            rendered = replaceMentions(USERNAME_MENTION, rendered);
        } catch (Exception ex) {
            log.debug("event=mention stage=render outcome=error message={}", (Object) ex.getMessage());
            rendered = commentBody;
        }
        // Normalize Windows newlines but keep line breaks.
        rendered = rendered.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (maxLength > 0 && rendered.length() > maxLength) {
            rendered = rendered.substring(0, Math.max(0, maxLength - 3)) + "...";
        }
        return rendered;
    }

    private String replaceMentions(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement;
            ApplicationUser user = token != null ? this.resolveMentionToken(token.trim()) : null;
            if (user != null && user.getDisplayName() != null) {
                replacement = "@" + user.getDisplayName();
            } else {
                // Unresolved: show a bare @name rather than the raw wiki token.
                replacement = token != null ? "@" + token.trim() : matcher.group(0);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
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

