/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.model;

public final class JimAttachmentFileKind {
    public static final String IMAGE = "IMAGE";
    public static final String FILE = "FILE";
    public static final String AUDIO = "AUDIO";
    // Sprint 07 Fix-3: videos get their own kind so clients can render a
    // player/preview; unknown kinds fall back to a plain file card everywhere.
    public static final String VIDEO = "VIDEO";

    private JimAttachmentFileKind() {
    }
}

