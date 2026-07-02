package com.corbitlogic.jira.internalmessenger.mobile;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Canonical catalogue of CorbitHub mobile feature keys (Sprint 04G).
 *
 * <p>These keys are the vocabulary shared by the admin rules, the backend
 * feature gate, the bootstrap payload and the mobile navigation. Keeping them in
 * one place means the admin UI, enforcement and app never drift.</p>
 *
 * <p>{@code profile}/{@code settings}/{@code about}/logout are intentionally NOT
 * gated: a user must always be able to change their settings or sign out even
 * when every work feature is restricted.</p>
 */
public final class JimMobileFeatures {

    public static final String DASHBOARD = "dashboard";
    public static final String CHAT = "chat";
    public static final String BOARDS = "boards";
    public static final String PROJECTS = "projects";
    public static final String TASKS = "tasks";
    public static final String ISSUE_DETAIL = "issueDetail";

    /**
     * All gateable feature keys, in the app's navigation-priority order. The
     * default-landing logic on the client walks this order to pick the first
     * allowed section.
     */
    private static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            BOARDS, DASHBOARD, CHAT, TASKS, PROJECTS, ISSUE_DETAIL));

    private JimMobileFeatures() {
    }

    /** Immutable list of every gateable feature key, in navigation priority. */
    public static List<String> all() {
        return ALL;
    }

    /** A fresh, mutable set containing every feature key (the default grant). */
    public static Set<String> allSet() {
        return new LinkedHashSet<>(ALL);
    }

    public static boolean isKnown(String key) {
        return key != null && ALL.contains(key.trim());
    }

    /**
     * Parse a stored/admin-supplied CSV into the subset of <em>known</em> keys,
     * preserving canonical ordering and dropping blanks/unknowns. Never null.
     */
    public static Set<String> parse(String csv) {
        Set<String> out = new LinkedHashSet<>();
        if (csv == null) {
            return out;
        }
        Set<String> requested = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String key = normalize(part);
            if (key != null) {
                requested.add(key);
            }
        }
        // Emit in canonical order so serialisation is stable.
        for (String key : ALL) {
            if (requested.contains(key)) {
                out.add(key);
            }
        }
        return out;
    }

    /** Canonical CSV for a set of keys (canonical order, known keys only). */
    public static String toCsv(Set<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String key : ALL) {
            if (keys != null && keys.contains(key)) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(key);
            }
        }
        return sb.toString();
    }

    /**
     * Normalise a single token to its canonical key, or null if unknown/blank.
     * Tolerates a couple of legacy/alias spellings so admins and older clients
     * cannot accidentally lose access through a harmless spelling difference.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        String lower = v.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "dashboard":
                return DASHBOARD;
            case "chat":
            case "messages":
            case "messaging":
                return CHAT;
            case "boards":
            case "board":
                return BOARDS;
            case "projects":
            case "project":
                return PROJECTS;
            case "tasks":
            case "issues":
            case "task":
                return TASKS;
            case "issuedetail":
            case "issue_detail":
            case "issue-detail":
                return ISSUE_DETAIL;
            default:
                return null;
        }
    }
}
