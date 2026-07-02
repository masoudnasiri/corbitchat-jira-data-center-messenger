package com.corbitlogic.jira.internalmessenger.mobile;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.timezone.TimeZoneManager;
import com.atlassian.jira.user.ApplicationUser;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Centralized date contract for the mobile BFF (Sprint 04E).
 *
 * <p><b>Wire contract</b> — two kinds of dates, never mixed:</p>
 * <ul>
 *   <li><b>Calendar-day fields</b> (Jira Due Date, date-only custom fields) are
 *       serialized as an ISO {@code yyyy-MM-dd} string computed in the Jira
 *       server timezone. The client renders that exact day (Jalali or Gregorian)
 *       with no timezone math, so a due date can never shift by ±1 day on the
 *       device.</li>
 *   <li><b>Instant fields</b> (created/updated/resolved, comment/worklog times)
 *       stay as epoch milliseconds and are rendered in the device's local zone.</li>
 * </ul>
 *
 * <p><b>Proposed completion date</b> — the custom date field "تاریخ انجام
 * پیشنهادی". On the reference production instance this is
 * {@value #KNOWN_PROPOSED_COMPLETION_ID} (type <em>datepicker</em>), but the id
 * differs between instances, so it is resolved by <em>normalized field name</em>
 * among date-typed custom fields (tolerating Arabic vs Persian ي/ی and ك/ک and
 * stray whitespace). The known id is only a documented expectation, never a hard
 * requirement.</p>
 *
 * <p>This class never logs field values.</p>
 */
public final class JimMobileDates {

    /** Known id on the reference production instance (may differ elsewhere). */
    public static final String KNOWN_PROPOSED_COMPLETION_ID = "customfield_11309";

    public static final String SOURCE_DUE = "dueDate";
    public static final String SOURCE_PROPOSED = "proposedCompletionDate";

    /** Normalized target name for "تاریخ انجام پیشنهادی". */
    private static final String PROPOSED_NAME_NORMALIZED = normalizeFa("تاریخ انجام پیشنهادی");

    private JimMobileDates() {
    }

    // --- Calendar-day formatting ---------------------------------------------

    /**
     * Format an instant as an ISO {@code yyyy-MM-dd} in the Jira <em>server</em>
     * zone. Date-picker / Due-Date values are stored as midnight in the server
     * default zone, so this recovers the exact calendar day that was entered.
     */
    public static String toYmd(Date date) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        fmt.setTimeZone(TimeZone.getDefault());
        return fmt.format(date);
    }

    /**
     * Today's calendar day as {@code yyyy-MM-dd} in the <em>viewer's</em> Jira
     * timezone. Today/Overdue must be judged in the user's day, not the server's
     * — the server may run in a zone hours ahead of the user (here +0800 vs
     * Asia/Tehran), which would otherwise mark a task due <em>today</em> as
     * overdue during the small hours.
     */
    public static String todayYmd(ApplicationUser user) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        fmt.setTimeZone(userTimeZone(user));
        return fmt.format(new Date());
    }

    /** True when {@code ymd} is a valid day strictly before the viewer's today. */
    public static boolean isOverdueYmd(String ymd, ApplicationUser user) {
        if (ymd == null || ymd.isEmpty()) {
            return false;
        }
        return ymd.compareTo(todayYmd(user)) < 0;
    }

    /** True when {@code ymd} equals the viewer's today. */
    public static boolean isTodayYmd(String ymd, ApplicationUser user) {
        if (ymd == null || ymd.isEmpty()) {
            return false;
        }
        return ymd.equals(todayYmd(user));
    }

    /** The viewer's Jira timezone, falling back to the server default. */
    public static TimeZone userTimeZone(ApplicationUser user) {
        try {
            TimeZoneManager mgr = ComponentAccessor.getComponent(TimeZoneManager.class);
            if (mgr != null && user != null) {
                TimeZone tz = mgr.getTimeZoneforUser(user);
                if (tz != null) {
                    return tz;
                }
            }
        } catch (Throwable ignored) {
        }
        return TimeZone.getDefault();
    }

    // --- Due date + proposed-completion fallback -----------------------------

    /** Standard Jira Due Date as {@code yyyy-MM-dd}, or null. */
    public static String dueYmd(Issue issue) {
        return issue != null ? toYmd(issue.getDueDate()) : null;
    }

    /**
     * Value of the "تاریخ انجام پیشنهادی" custom field for this issue as
     * {@code yyyy-MM-dd}, or null when absent/empty/not present on this instance.
     */
    public static String proposedCompletionYmd(Issue issue) {
        if (issue == null) {
            return null;
        }
        try {
            CustomField cf = findProposedCompletionField();
            if (cf == null) {
                return null;
            }
            Object v = cf.getValue(issue);
            if (v instanceof Date) {
                return toYmd((Date) v);
            }
        } catch (Throwable ignored) {
            // Never fail an issue over an optional fallback field.
        }
        return null;
    }

    /**
     * Effective due day: the standard Due Date if present, else the proposed
     * completion date. Returns null when neither is set.
     */
    public static String effectiveDueYmd(Issue issue) {
        String due = dueYmd(issue);
        if (due != null && !due.isEmpty()) {
            return due;
        }
        return proposedCompletionYmd(issue);
    }

    /** Which field the effective due date came from, or null when none. */
    public static String effectiveDueSource(Issue issue) {
        String due = dueYmd(issue);
        if (due != null && !due.isEmpty()) {
            return SOURCE_DUE;
        }
        return proposedCompletionYmd(issue) != null ? SOURCE_PROPOSED : null;
    }

    // --- Custom-field date classification ------------------------------------

    /** True for a custom field whose type is a Jira datetime picker. */
    public static boolean isDateTimeCustomField(CustomField cf) {
        String key = typeKey(cf);
        return key != null && key.contains("datetime");
    }

    /** True for any date/datetime custom field (datepicker, datetime, JPO date). */
    public static boolean isDateCustomField(CustomField cf) {
        String key = typeKey(cf);
        if (key == null) {
            return false;
        }
        return key.contains("datepicker") || key.contains("datetime")
                || key.contains("baseline-start") || key.contains("baseline-end")
                || key.contains(":date");
    }

    // --- Proposed-completion field resolution + JQL clause -------------------

    /**
     * Resolve the "تاریخ انجام پیشنهادی" custom field by normalized name among
     * date-typed custom fields. Returns null on instances that do not have it.
     */
    public static CustomField findProposedCompletionField() {
        try {
            CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
            if (cfm == null) {
                return null;
            }
            // Fast path: the known id, but only if it is still a date field with
            // the expected name (guards against id reuse on other instances).
            CustomField byId = cfm.getCustomFieldObject(KNOWN_PROPOSED_COMPLETION_ID);
            if (byId != null && isDateCustomField(byId) && nameMatches(byId)) {
                return byId;
            }
            List<CustomField> all = cfm.getCustomFieldObjects();
            if (all != null) {
                for (CustomField cf : all) {
                    if (isDateCustomField(cf) && nameMatches(cf)) {
                        return cf;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * JQL clause name for the proposed-completion field (e.g. {@code cf[11309]}),
     * or null when the field is not present on this instance.
     */
    public static String proposedCompletionClause() {
        CustomField cf = findProposedCompletionField();
        if (cf == null) {
            return null;
        }
        Long id = cf.getIdAsLong();
        return id != null ? "cf[" + id + "]" : null;
    }

    private static boolean nameMatches(CustomField cf) {
        try {
            return PROPOSED_NAME_NORMALIZED.equals(normalizeFa(cf.getFieldName()));
        } catch (Throwable t) {
            return false;
        }
    }

    private static String typeKey(CustomField cf) {
        try {
            if (cf == null || cf.getCustomFieldType() == null) {
                return null;
            }
            String key = cf.getCustomFieldType().getKey();
            return key != null ? key.toLowerCase() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // --- Start-of-day helper (kept here so callers share one definition) ------

    public static Date startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    public static Date endOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        return c.getTime();
    }

    /**
     * Normalize a Persian/Arabic field name for robust matching: unify Arabic
     * yeh/kaf/alef-maksura to their Persian forms, drop zero-width marks, and
     * collapse whitespace.
     */
    static String normalizeFa(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\u064A': // Arabic yeh
                case '\u0649': // Arabic alef maksura
                    c = '\u06CC'; // Persian yeh
                    break;
                case '\u0643': // Arabic kaf
                    c = '\u06A9'; // Persian kaf
                    break;
                case '\u200C': // ZWNJ
                case '\u200D': // ZWJ
                case '\u200E': // LRM
                case '\u200F': // RLM
                    continue;
                default:
                    break;
            }
            if (Character.isWhitespace(c)) {
                if (b.length() > 0 && b.charAt(b.length() - 1) != ' ') {
                    b.append(' ');
                }
            } else {
                b.append(c);
            }
        }
        return b.toString().trim();
    }
}
