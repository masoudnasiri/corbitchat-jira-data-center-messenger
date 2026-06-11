/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.util;

public final class JimMessageLifecycle {
    public static final long DELETE_WINDOW_MS = 600000L;
    public static final int REPLY_PREVIEW_MAX_LENGTH = 120;

    private JimMessageLifecycle() {
    }

    public static boolean isWithinDeleteWindow(long createdAt, long now) {
        return createdAt > 0L && now - createdAt <= 600000L;
    }
}

