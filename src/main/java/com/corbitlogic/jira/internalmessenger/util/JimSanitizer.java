/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.util;

public final class JimSanitizer {
    private JimSanitizer() {
    }

    public static String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); ++i) {
            char character = value.charAt(i);
            if (character != '\n' && character != '\r' && character != '\t' && character < ' ') continue;
            builder.append(character);
        }
        return builder.toString().trim();
    }
}

