/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.util;

import com.corbitlogic.jira.internalmessenger.model.JimEventType;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.util.JimSanitizer;

public final class JimValidation {
    public static final int MAX_MESSAGE_BODY_LENGTH = 4000;
    public static final int MAX_LIST_LIMIT = 100;
    public static final int DEFAULT_LIST_LIMIT = 50;

    private JimValidation() {
    }

    public static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new JimMessengerException(fieldName + " is required");
        }
        return value.trim();
    }

    public static void requireDistinctUsers(String firstUserKey, String secondUserKey) {
        if (firstUserKey.equals(secondUserKey)) {
            throw new JimMessengerException("Direct conversations require two different users");
        }
    }

    public static void requirePositiveId(int id, String fieldName) {
        if (id <= 0) {
            throw new JimMessengerException(fieldName + " must be positive");
        }
    }

    public static void requireMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new JimMessengerException(fieldName + " exceeds maximum length of " + maxLength);
        }
    }

    public static String validateMessageBody(String body) {
        String sanitized = JimSanitizer.sanitizeText(body);
        if (sanitized == null || sanitized.isEmpty()) {
            throw new JimMessengerException("body is required");
        }
        String normalized = JimValidation.normalizeWhitespace(sanitized);
        if (normalized.isEmpty()) {
            throw new JimMessengerException("body is required");
        }
        JimValidation.requireMaxLength(normalized, 4000, "body");
        return normalized;
    }

    public static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', '\n').replaceAll("[ \\t\\x0B\\f]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    public static int normalizeListLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.min(limit, 100);
    }

    public static Integer normalizeBeforeMessageId(Integer beforeMessageId) {
        if (beforeMessageId == null) {
            return null;
        }
        if (beforeMessageId <= 0) {
            throw new JimMessengerException("beforeMessageId must be positive when provided");
        }
        return beforeMessageId;
    }

    public static JimEventType requireEventType(JimEventType eventType) {
        if (eventType == null) {
            throw new JimMessengerException("eventType is required");
        }
        return eventType;
    }

    public static String truncatePreview(String preview) {
        if (preview == null) {
            return null;
        }
        String trimmed = preview.trim();
        if (trimmed.length() <= 500) {
            return trimmed;
        }
        return trimmed.substring(0, 497) + "...";
    }
}

