/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.util;

public final class JimMessageLifecycle {
    public static final long DELETE_WINDOW_MS = 600000L;        // 10 minutes
    public static final long EDIT_WINDOW_MS = 1800000L;         // 30 minutes
    public static final int REPLY_PREVIEW_MAX_LENGTH = 120;

    private JimMessageLifecycle() {
    }

    public static boolean isWithinDeleteWindow(long createdAt, long now) {
        return createdAt > 0L && now - createdAt <= DELETE_WINDOW_MS;
    }

    public static boolean isWithinEditWindow(long createdAt, long now) {
        return createdAt > 0L && now - createdAt <= EDIT_WINDOW_MS;
    }
}

