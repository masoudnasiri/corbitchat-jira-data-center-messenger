/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.util;

public final class JimConversationKeys {
    private JimConversationKeys() {
    }

    public static String[] canonicalDirectPair(String firstUserKey, String secondUserKey) {
        if (firstUserKey.compareTo(secondUserKey) <= 0) {
            return new String[]{firstUserKey, secondUserKey};
        }
        return new String[]{secondUserKey, firstUserKey};
    }

    public static String systemConversationKey() {
        return "JIRA_ASSISTANT";
    }

    public static String buildEventFingerprint(String eventType, String targetUserKey, String issueKey, String actorUserKey) {
        return JimConversationKeys.buildEventFingerprint(eventType, targetUserKey, issueKey, actorUserKey, null);
    }

    public static String buildEventFingerprint(String eventType, String targetUserKey, String issueKey, String actorUserKey, String uniqueSuffix) {
        return String.join((CharSequence)"|", JimConversationKeys.nullSafe(eventType), JimConversationKeys.nullSafe(targetUserKey), JimConversationKeys.nullSafe(issueKey), JimConversationKeys.nullSafe(actorUserKey), JimConversationKeys.nullSafe(uniqueSuffix));
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}

