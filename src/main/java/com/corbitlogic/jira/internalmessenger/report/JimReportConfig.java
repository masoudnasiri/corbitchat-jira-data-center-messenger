package com.corbitlogic.jira.internalmessenger.report;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.fields.CustomField;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import java.util.Map;

/**
 * Issue Report-Compliance field + schedule contract (Sprint 12).
 *
 * <p>All configuration is admin-driven (see the "Report Schedule" admin tab):
 * the three custom-field ids, the report marker, the global reporting window and
 * the per follow-up level interval/window overrides. Production defaults are the
 * confirmed field ids ({@code customfield_10802} level, {@code customfield_11801}
 * valid-count, {@code customfield_11900} required-count) but everything is
 * overridable so the feature also works on DEV where the field ids differ.</p>
 *
 * <p>On any instance where the fields are missing/incompatible the feature
 * disables gracefully: {@link #fieldsConfigured()} returns false and the Report
 * tab shows a clear "not configured" notice instead of crashing. Types are
 * validated at runtime; nothing is assumed. This class never logs field values.</p>
 *
 * <p>All time reasoning uses raw timestamps / minute-of-day — never localized or
 * Jalali display text.</p>
 */
public final class JimReportConfig {

    private JimReportConfig() {
    }

    private static JimAdminSettingsService admin() {
        return ComponentAccessor.getOSGiComponentInstanceOfType(JimAdminSettingsService.class);
    }

    // --- Feature state --------------------------------------------------------

    /** Admin master toggle for the report-compliance feature. */
    public static boolean isFeatureEnabled() {
        JimAdminSettingsService a = admin();
        return a != null && a.isReportComplianceEnabled();
    }

    /** The configured report marker/hashtag (e.g. {@code #گزارش}). */
    public static String marker() {
        JimAdminSettingsService a = admin();
        return a != null ? a.getReportMarker() : "#\u06af\u0632\u0627\u0631\u0634";
    }

    /** Admin: allow submitting reports even when level/fields are not configured. */
    public static boolean allowWithoutConfig() {
        JimAdminSettingsService a = admin();
        return a != null && a.isReportAllowWithoutConfig();
    }

    /**
     * Admin: when true, a previously CLOSED issue that becomes active again
     * (reopened / due date moved out) reactivates reporting. Conservative default
     * (false) keeps a closed issue closed (Phase 2).
     */
    public static boolean reopenReactivation() {
        JimAdminSettingsService a = admin();
        return a != null && a.isReportReopenReactivation();
    }

    /** Admin: Jira-wide Jalali date display is enabled (issue view shows Jalali). */
    public static boolean jiraWideJalali() {
        JimAdminSettingsService a = admin();
        return a != null && a.isJiraWideJalaliEnabled();
    }

    /**
     * Admin reporting-timezone override (e.g. {@code GMT+03:30}), or null when
     * unset. A fixed GMT offset bypasses stale JVM DST rules so window
     * classification matches the user's real local time.
     */
    public static java.util.TimeZone reportTimeZone() {
        JimAdminSettingsService a = admin();
        if (a == null) {
            return null;
        }
        String v = a.getReportTimezone();
        if (v == null || v.trim().isEmpty()) {
            return null;
        }
        try {
            return java.util.TimeZone.getTimeZone(v.trim());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Optional generated-metric → custom-field mappings (metricKey → fieldId; "" = disabled). */
    public static Map<String, String> fieldMappings() {
        JimAdminSettingsService a = admin();
        return a != null ? a.getReportFieldMappings() : java.util.Collections.<String, String>emptyMap();
    }

    // --- Report Applicability Policy (reportscope) ---------------------------
    // Additive scope gate. When disabled (default) every method here is a no-op:
    // applicabilityExclusion(...) always returns null → the engine is unchanged.

    /** Master on/off for the report applicability policy. */
    public static boolean applicabilityEnabled() {
        JimAdminSettingsService a = admin();
        return a != null && a.isReportApplicabilityEnabled();
    }

    /** created_on_or_after | due_on_or_after | created_and_due_on_or_after | created_or_due_on_or_after. */
    public static String applicabilityDateMode() {
        JimAdminSettingsService a = admin();
        return a != null ? a.getReportApplicabilityDateMode() : "created_on_or_after";
    }

    /** When true, only non-Done issues are report-applicable. */
    public static boolean applicabilityNonDoneOnly() {
        JimAdminSettingsService a = admin();
        return a == null || a.isReportApplicabilityNonDoneOnly();
    }

    /** exclude_missing_due | include_if_created_matches. */
    public static String applicabilityMissingDuePolicy() {
        JimAdminSettingsService a = admin();
        return a != null ? a.getReportApplicabilityMissingDuePolicy() : "exclude_missing_due";
    }

    /** Raw configured effective-from date ({@code YYYY-MM-DD}) or "" when unset. */
    public static String effectiveFromDate() {
        JimAdminSettingsService a = admin();
        return a != null ? a.getReportEffectiveFromDate() : "";
    }

    /**
     * Effective-from date parsed to the start-of-day epoch millis in {@code tz},
     * or null when unset/unparseable. Start-of-day so "on or after" includes the
     * whole effective day.
     */
    public static Long effectiveFromMs(java.util.TimeZone tz) {
        return parseYmdStartOfDay(effectiveFromDate(), tz);
    }

    /** Parse {@code YYYY-MM-DD} to start-of-day epoch millis in {@code tz}; null if blank/invalid. */
    public static Long parseYmdStartOfDay(String ymd, java.util.TimeZone tz) {
        if (ymd == null || !ymd.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            String[] p = ymd.split("-");
            java.util.Calendar c = java.util.Calendar.getInstance(
                    tz != null ? tz : java.util.TimeZone.getTimeZone("GMT"));
            c.clear();
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 0, 0, 0);
            return c.getTimeInMillis();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Pure applicability evaluator. Returns {@code null} when the issue is
     * in-scope for report compliance, or a stable exclusion-reason code when the
     * enabled policy excludes it. When the policy is disabled this always returns
     * {@code null} (no behaviour change).
     *
     * <p>Done is decided by {@code isDone} (caller passes statusCategory==Done,
     * never resolution). The date basis uses the issue's system created/due
     * millis so it matches the impact-scan JQL ({@code created}/{@code duedate}).</p>
     *
     * @param createdMs issue created epoch millis
     * @param dueMs     issue system due-date epoch millis, or null when no due date
     * @param isDone    true when the issue's statusCategory is Done
     * @param tz        effective report timezone
     */
    public static String applicabilityExclusion(long createdMs, Long dueMs, boolean isDone, java.util.TimeZone tz) {
        return evaluateApplicability(createdMs, dueMs, isDone,
                applicabilityEnabled(), applicabilityNonDoneOnly(), effectiveFromMs(tz),
                applicabilityDateMode(),
                "include_if_created_matches".equals(applicabilityMissingDuePolicy()));
    }

    /**
     * Pure applicability evaluator with all policy inputs supplied explicitly —
     * used by {@link #applicabilityExclusion} (stored settings) and the admin
     * impact-preview (proposed, not-yet-saved params). Returns {@code null} when
     * in-scope, else a stable exclusion-reason code. No Jira/settings access.
     *
     * @param effMs effective-from start-of-day epoch millis, or null when unset
     */
    public static String evaluateApplicability(long createdMs, Long dueMs, boolean isDone,
            boolean enabled, boolean nonDoneOnly, Long effMs, String mode, boolean includeMissingIfCreated) {
        if (!enabled) {
            return null;
        }
        // fixed_counter_start: created/due (and Done status) are NOT used as scope
        // filters. Every issue with a report level is in scope; the enforcement start
        // date is applied purely as a scoring lower bound (in computeSummary).
        if ("fixed_counter_start".equals(mode)) {
            return null;
        }
        if (nonDoneOnly && isDone) {
            return "excluded_done_status";
        }
        if (effMs == null) {
            return null; // policy on but no date → only the non-Done rule applies
        }
        long eff = effMs;
        boolean createdOk = createdMs >= eff;
        boolean dueMissing = (dueMs == null);
        boolean dueOk = !dueMissing && dueMs >= eff;
        switch (mode == null ? "" : mode) {
            case "created_on_or_after":
                return createdOk ? null : "excluded_created_before_effective_date";
            case "due_on_or_after":
                if (dueMissing) {
                    return (includeMissingIfCreated && createdOk) ? null : "excluded_missing_due";
                }
                return dueOk ? null : "excluded_due_before_effective_date";
            case "created_and_due_on_or_after":
                if (dueMissing) {
                    return (includeMissingIfCreated && createdOk) ? null : "excluded_missing_due";
                }
                if (!createdOk) {
                    return "excluded_created_before_effective_date";
                }
                return dueOk ? null : "excluded_due_before_effective_date";
            case "created_or_due_on_or_after":
                if (createdOk || dueOk) {
                    return null;
                }
                return dueMissing ? "excluded_created_before_effective_date" : "excluded_by_effective_date";
            default:
                return null;
        }
    }

    /**
     * True when all three report custom fields resolve with compatible types on
     * this instance. Used to gate the feature per environment (graceful disable).
     */
    public static boolean fieldsConfigured() {
        return resolveLevelField() != null
                && resolveValidCountField() != null
                && resolveRequiredCountField() != null;
    }

    /** Feature is enabled AND the fields exist on this instance. */
    public static boolean isOperational() {
        return isFeatureEnabled() && fieldsConfigured();
    }

    /**
     * JQL clause for the follow-up level field (e.g. {@code cf[10802]}).
     * Null when the field is missing on this instance. Server-owned — never
     * built from client input.
     */
    public static String levelFieldJql() {
        CustomField cf = resolveLevelField();
        return cf != null ? "cf[" + cf.getIdAsLong() + "]" : null;
    }

    // --- Field resolution (configurable id + type guard) ----------------------

    public static CustomField resolveLevelField() {
        JimAdminSettingsService a = admin();
        return a == null ? null : fieldOfType(a.getReportLevelFieldId(), "select", "radiobuttons", "textfield");
    }

    public static CustomField resolveValidCountField() {
        JimAdminSettingsService a = admin();
        return a == null ? null : fieldOfType(a.getReportValidCountFieldId(), "float", "number");
    }

    public static CustomField resolveRequiredCountField() {
        JimAdminSettingsService a = admin();
        return a == null ? null : fieldOfType(a.getReportRequiredCountFieldId(), "float", "number");
    }

    private static CustomField fieldOfType(String id, String... typeKeySubstrings) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
            if (cfm == null) {
                return null;
            }
            CustomField cf = cfm.getCustomFieldObject(id);
            if (cf == null || cf.getCustomFieldType() == null) {
                return null;
            }
            String key = cf.getCustomFieldType().getKey();
            if (key == null) {
                return null;
            }
            String lower = key.toLowerCase();
            for (String sub : typeKeySubstrings) {
                if (lower.contains(sub)) {
                    return cf;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // --- Follow-up level ------------------------------------------------------

    /**
     * Read the issue's follow-up level and map it to an internal level code
     * ({@code normal|high|special|managerial}), or null when unset/unknown.
     * Tolerates select options and plain text, and normalizes Arabic/Persian
     * yeh/kaf so option text differences never break matching.
     */
    public static String levelCodeOf(Issue issue) {
        CustomField cf = resolveLevelField();
        if (cf == null || issue == null) {
            return null;
        }
        Object raw;
        try {
            raw = issue.getCustomFieldValue(cf);
        } catch (Throwable t) {
            return null;
        }
        String text = null;
        if (raw instanceof Option) {
            text = ((Option) raw).getValue();
        } else if (raw != null) {
            text = String.valueOf(raw);
        }
        return codeForLevelText(text);
    }

    /** Map a Persian follow-up-level label to an internal code (null if unknown). */
    public static String codeForLevelText(String text) {
        if (text == null) {
            return null;
        }
        String n = normalizePersian(text);
        switch (n) {
            case "\u0639\u0627\u062f\u06cc":               // عادی
                return "normal";
            case "\u0628\u0627\u0644\u0627":               // بالا
                return "high";
            case "\u0648\u06cc\u0698\u0647":               // ویژه
                return "special";
            case "\u0645\u062f\u06cc\u0631\u06cc\u062a\u06cc": // مدیریتی
                return "managerial";
            default:
                return null;
        }
    }

    /** Persian display label for a level code (for UI), or the code itself if unknown. */
    public static String levelLabelFa(String code) {
        if (code == null) {
            return "";
        }
        switch (code) {
            case "normal":     return "\u0639\u0627\u062f\u06cc";               // عادی
            case "high":       return "\u0628\u0627\u0644\u0627";               // بالا
            case "special":    return "\u0648\u06cc\u0698\u0647";               // ویژه
            case "managerial": return "\u0645\u062f\u06cc\u0631\u06cc\u062a\u06cc"; // مدیریتی
            default:           return code;
        }
    }

    /**
     * Remove Jira wiki quoted segments ({@code {quote}...{quote}} blocks and
     * {@code bq.} line-quotes) from a comment body. Used by report detection so a
     * <b>reply that quotes a report</b> (the parent report text — including the
     * {@code #گزارش} marker — is inside a {@code {quote}} block) is NOT miscounted
     * as a new report. Only a marker appearing OUTSIDE any quote makes a comment a
     * report submission. Never used for display; purely for classification.
     */
    public static String stripQuotedSegments(String body) {
        if (body == null) {
            return "";
        }
        String out = body;
        // {quote}...{quote} blocks (case-insensitive, across newlines). Handles an
        // unbalanced trailing {quote} by removing to end-of-text.
        try {
            out = out.replaceAll("(?is)\\{quote\\}.*?\\{quote\\}", " ");
            out = out.replaceAll("(?is)\\{quote\\}.*$", " ");
        } catch (Throwable ignored) {
        }
        // bq. line quotes (a single quoted line).
        StringBuilder sb = new StringBuilder();
        for (String line : out.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("bq.")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * True when {@code body} is an original report submission: the report marker
     * appears outside any quoted segment. Replies/quotes/ordinary comments that
     * only contain the marker inside a {@code {quote}} return false.
     */
    public static boolean isReportSubmissionBody(String body) {
        if (body == null) {
            return false;
        }
        String marker = normalizePersian(marker());
        if (marker.isEmpty()) {
            return false;
        }
        return normalizePersian(stripQuotedSegments(body)).contains(marker);
    }

    /** Normalize Arabic yeh/kaf to Persian and strip zero-width chars + trim. */
    public static String normalizePersian(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\u064a', '\u06cc')  // Arabic yeh -> Persian yeh
                .replace('\u0643', '\u06a9')   // Arabic kaf -> Persian kaf
                .replace("\u200c", "")         // ZWNJ
                .replace("\u200f", "")
                .replace("\u200e", "")
                .trim();
    }

    // --- Schedule resolution --------------------------------------------------

    /** Effective interval + reporting window (minute-of-day) for a follow-up level. */
    public static final class ReportSchedule {
        public final int intervalMinutes;
        public final int windowStartMinute;
        public final int windowEndMinute;

        ReportSchedule(int intervalMinutes, int windowStartMinute, int windowEndMinute) {
            this.intervalMinutes = intervalMinutes;
            this.windowStartMinute = windowStartMinute;
            this.windowEndMinute = windowEndMinute;
        }
    }

    /** Global reporting-window start as minute-of-day (default 08:00). */
    public static int globalWindowStartMinute() {
        JimAdminSettingsService a = admin();
        return parseHHmm(a != null ? a.getReportWindowStart() : null, 8 * 60);
    }

    /** Global reporting-window end as minute-of-day (default 21:00). */
    public static int globalWindowEndMinute() {
        JimAdminSettingsService a = admin();
        return parseHHmm(a != null ? a.getReportWindowEnd() : null, 21 * 60);
    }

    /**
     * Resolve the effective schedule for a follow-up level code, or null when the
     * level has no configured interval (schedule "not configured" for that level).
     * Per-level start/end override the global window when set.
     */
    public static ReportSchedule scheduleForLevel(String code) {
        JimAdminSettingsService a = admin();
        if (a == null || code == null) {
            return null;
        }
        Map<String, Object> level = a.getReportLevelSchedules().get(code);
        if (level == null) {
            return null;
        }
        int intervalValue = level.get("interval") instanceof Number
                ? ((Number) level.get("interval")).intValue() : 0;
        if (intervalValue <= 0) {
            return null;
        }
        String unit = String.valueOf(level.get("unit"));
        int intervalMinutes = "hours".equals(unit) ? intervalValue * 60 : intervalValue;
        int start = parseHHmm(String.valueOf(level.get("start")), globalWindowStartMinute());
        int end = parseHHmm(String.valueOf(level.get("end")), globalWindowEndMinute());
        return new ReportSchedule(intervalMinutes, start, end);
    }

    /**
     * Per-level reporting lifecycle / end condition:
     * {@code due} | {@code done} | {@code earlier} (default {@code earlier}).
     * Controls whether report submission is still active for an issue.
     */
    public static String activeUntilForLevel(String code) {
        return levelStringField(code, "activeUntil", "earlier");
    }

    /**
     * Per-level required-count counting mode:
     * {@code daily} | {@code cumulative_due} | {@code cumulative_done} |
     * {@code cumulative_earlier} (default {@code daily}). Controls how the
     * required report count and compliance are computed.
     */
    public static String countModeForLevel(String code) {
        return levelStringField(code, "countMode", "daily");
    }

    /** Minimum required reports for the {@code fixed_minimum} count mode (default 1). */
    public static int minCountForLevel(String code) {
        JimAdminSettingsService a = admin();
        if (a == null || code == null) {
            return 1;
        }
        Map<String, Object> level = a.getReportLevelSchedules().get(code);
        if (level == null || !(level.get("minCount") instanceof Number)) {
            return 1;
        }
        int v = ((Number) level.get("minCount")).intValue();
        return v < 1 ? 1 : v;
    }

    /**
     * Effective reporting window (minute-of-day) for a level, independent of the
     * interval — used by the {@code fixed_minimum} mode which has no buckets but
     * still applies the out-of-window rule. Per-level override → global default.
     */
    public static int[] windowForLevel(String code) {
        JimAdminSettingsService a = admin();
        int gs = globalWindowStartMinute();
        int ge = globalWindowEndMinute();
        if (a == null || code == null) {
            return new int[]{gs, ge};
        }
        Map<String, Object> level = a.getReportLevelSchedules().get(code);
        if (level == null) {
            return new int[]{gs, ge};
        }
        int start = parseHHmm(String.valueOf(level.get("start")), gs);
        int end = parseHHmm(String.valueOf(level.get("end")), ge);
        return new int[]{start, end};
    }

    private static String levelStringField(String code, String field, String fallback) {
        JimAdminSettingsService a = admin();
        if (a == null || code == null) {
            return fallback;
        }
        Map<String, Object> level = a.getReportLevelSchedules().get(code);
        if (level == null || level.get(field) == null) {
            return fallback;
        }
        return String.valueOf(level.get(field));
    }

    /** Parse {@code HH:mm} to minute-of-day; returns {@code fallback} when blank/invalid. */
    public static int parseHHmm(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        String v = value.trim();
        if (v.isEmpty() || "null".equals(v) || !v.matches("\\d{1,2}:\\d{2}")) {
            return fallback;
        }
        String[] parts = v.split(":");
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        if (h < 0 || h > 23 || m < 0 || m > 59) {
            return fallback;
        }
        return h * 60 + m;
    }
}
