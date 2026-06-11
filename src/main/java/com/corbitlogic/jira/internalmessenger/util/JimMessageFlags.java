/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.util;

import com.corbitlogic.jira.internalmessenger.ao.JimMessage;

public final class JimMessageFlags {
    private JimMessageFlags() {
    }

    public static boolean isDeleted(JimMessage message) {
        if (message == null) {
            return false;
        }
        Integer deleted = message.getDeleted();
        return deleted != null && deleted != 0;
    }

    public static boolean isEdited(JimMessage message) {
        if (message == null) {
            return false;
        }
        Integer edited = message.getEdited();
        return edited != null && edited != 0;
    }
}

