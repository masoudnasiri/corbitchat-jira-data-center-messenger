package com.corbitlogic.jira.internalmessenger.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of generated report-compliance metrics that admins may optionally map
 * to Jira custom fields (Sprint 12 — Admin Enhancement). Each metric has a value
 * category used for field-type compatibility validation, and a flag marking
 * whether it is safe to write when the schedule cannot be resolved (no follow-up
 * level / fields) — schedule-derived metrics (valid/required/coverage/…) are
 * NOT written in that case so nothing misleading is stored.
 *
 * <p>{@link #valueFor} returns raw typed values (Double / epoch-Long / String /
 * user-key String); the service coerces them to the concrete field type when
 * writing. Nothing here touches Jira directly.</p>
 */
public final class JimReportMetrics {

    public enum Category { NUMBER, DATE, TEXT, USER }

    public static final class Def {
        public final String key;
        public final Category category;
        public final boolean safeUnconfigured;

        Def(String key, Category category, boolean safeUnconfigured) {
            this.key = key;
            this.category = category;
            this.safeUnconfigured = safeUnconfigured;
        }
    }

    private static final List<Def> DEFS = new ArrayList<Def>();
    private static final Map<String, Def> BY_KEY = new LinkedHashMap<String, Def>();

    private static void def(String key, Category cat, boolean safe) {
        Def d = new Def(key, cat, safe);
        DEFS.add(d);
        BY_KEY.put(key, d);
    }

    static {
        // Numeric
        def("validReportCount", Category.NUMBER, false);
        def("requiredReportCount", Category.NUMBER, false);
        def("totalRawReportCount", Category.NUMBER, true);
        def("duplicateReportCount", Category.NUMBER, false);
        def("outOfWindowReportCount", Category.NUMBER, false);
        def("missedReportCount", Category.NUMBER, false);
        def("requiredReportCountSoFar", Category.NUMBER, false);
        def("validReportCountSoFar", Category.NUMBER, false);
        def("reportCoveragePercent", Category.NUMBER, false);
        // Date/time
        def("firstReportAt", Category.DATE, true);
        def("lastReportAt", Category.DATE, true);
        def("lastValidReportAt", Category.DATE, true);
        def("nextExpectedReportAt", Category.DATE, false);
        def("reportingEndsAt", Category.DATE, false);
        def("reportMetricsUpdatedAt", Category.DATE, true);
        // Text/select
        def("reportComplianceStatus", Category.TEXT, true);
        def("reportingActiveState", Category.TEXT, true);
        def("reportingInactiveReason", Category.TEXT, true);
        def("effectiveFollowUpLevel", Category.TEXT, true);
        def("effectiveReportInterval", Category.TEXT, false);
        def("effectiveReportingWindow", Category.TEXT, false);
        def("reportCountMode", Category.TEXT, false);
        def("reportLifecycleMode", Category.TEXT, false);
        def("lastReportCommentId", Category.TEXT, true);
        def("lastReportSummary", Category.TEXT, true);
        // User
        def("lastReporter", Category.USER, true);
        def("lastValidReporter", Category.USER, true);

        // --- Phase 4: lifecycle / control (persisted in JimReportControl) -----
        def("reportingControlState", Category.TEXT, true);
        def("reportingActive", Category.TEXT, true);
        def("reportingCloseCondition", Category.TEXT, true);
        def("reportingCloseReason", Category.TEXT, true);
        def("reportingClosedAt", Category.DATE, true);
        def("reportingStartAt", Category.DATE, true);
        def("reportingEndAt", Category.DATE, false);
        def("nextReportDueAt", Category.DATE, false);
        def("currentWindowStartAt", Category.DATE, false);
        def("currentWindowStatus", Category.TEXT, false);

        // --- Phase 4: compliance / window aggregates -------------------------
        def("reportCompliancePercent", Category.NUMBER, false);
        def("satisfiedWindowCount", Category.NUMBER, false);
        def("missedWindowCount", Category.NUMBER, false);
        def("lateWindowCount", Category.NUMBER, false);
        def("openWindowCount", Category.NUMBER, false);
        def("futureWindowCount", Category.NUMBER, false);
        def("overdueOpenCount", Category.NUMBER, false);
        def("maxLateMinutes", Category.NUMBER, false);
        def("avgLateMinutes", Category.NUMBER, false);
        def("minutesSinceLastValidReport", Category.NUMBER, true);
        def("currentlyOverdueMinutes", Category.NUMBER, false);

        // --- Report-metrics extension: total-required + missed-streak ---------
        def("totalRequiredReportsUntilEnd", Category.NUMBER, false);
        def("currentMissedReportStreak", Category.NUMBER, false);
        def("maxMissedReportStreak", Category.NUMBER, false);

        // --- Phase 4: today / day-level --------------------------------------
        def("todayRequiredCount", Category.NUMBER, false);
        def("todayValidCount", Category.NUMBER, false);
        def("todayMissedCount", Category.NUMBER, false);
        def("reportedToday", Category.TEXT, false);

        // --- Phase 4: responsible + exception/quality ------------------------
        def("responsibleUser", Category.USER, true);
        def("reportLevelMissing", Category.TEXT, true);
        def("reportConfigError", Category.TEXT, true);
        def("calcDurationMs", Category.NUMBER, true);
        def("calculationVersion", Category.NUMBER, true);
    }

    private JimReportMetrics() {
    }

    public static List<Def> defs() {
        return DEFS;
    }

    public static Def byKey(String key) {
        return BY_KEY.get(key);
    }

    /** True when a Jira custom-field type key is compatible with a metric category. */
    public static boolean isCompatible(Category cat, String fieldTypeKeyLower) {
        if (fieldTypeKeyLower == null) {
            return false;
        }
        String k = fieldTypeKeyLower.toLowerCase();
        switch (cat) {
            case NUMBER:
                return k.contains("float") || k.contains("number");
            case DATE:
                return k.contains("datepicker") || k.contains("datetime");
            case TEXT:
                return k.contains("textfield") || k.contains("textarea")
                        || k.contains("select") || k.contains("radiobuttons") || k.contains("readonly");
            case USER:
                return k.contains("userpicker") || k.contains("textfield") || k.contains("textarea");
            default:
                return false;
        }
    }

    /**
     * Raw typed value for a metric from the computed summary. Returns null when
     * the metric is not applicable (e.g. schedule-derived while unconfigured, or
     * no data). Categories: NUMBER→Double, DATE→Long(epoch ms), TEXT→String,
     * USER→String(user key). {@code reportMetricsUpdatedAt} is handled by the
     * writer (set to "now" only when other metrics change), so it returns null here.
     */
    public static Object valueFor(String key, JimReportSummary s) {
        boolean cfg = s.configured;
        switch (key) {
            case "validReportCount": return cfg ? (Double) (double) s.validCount : null;
            case "requiredReportCount": return cfg ? (Double) (double) s.requiredCount : null;
            case "totalRawReportCount": return (Double) (double) s.rawCount;
            case "duplicateReportCount": return cfg ? (Double) (double) s.duplicateCount : null;
            case "outOfWindowReportCount": return cfg ? (Double) (double) s.outOfWindowCount : null;
            case "missedReportCount": return cfg ? (Double) (double) s.missedCount : null;
            case "requiredReportCountSoFar": return cfg ? (Double) (double) s.requiredSoFar : null;
            case "validReportCountSoFar": return cfg ? (Double) (double) s.validSoFar : null;
            case "reportCoveragePercent":
                if (!cfg) return null;
                return (Double) (s.requiredCount > 0 ? Math.round(100.0 * s.validCount / s.requiredCount) : 0.0);
            case "firstReportAt": return s.firstReportMs;
            case "lastReportAt": return s.lastReportMs;
            case "lastValidReportAt": return s.lastValidReportMs;
            case "nextExpectedReportAt": return cfg ? s.nextExpectedMs : null;
            case "reportingEndsAt": return s.reportingEndsMs;
            case "reportMetricsUpdatedAt": return null; // writer-managed
            case "reportComplianceStatus": return s.statusCode;
            case "reportingActiveState":
                return s.submitActive ? "ACTIVE" : (cfg ? "INACTIVE" : "NOT_CONFIGURED");
            case "reportingInactiveReason": return s.inactiveReason;
            case "effectiveFollowUpLevel": return s.levelCode;
            case "effectiveReportInterval": return cfg ? intervalText(s.intervalMinutes) : null;
            case "effectiveReportingWindow":
                return cfg ? (minute(s.windowStartMinute) + "-" + minute(s.windowEndMinute)) : null;
            case "reportCountMode": return cfg ? s.countMode : null;
            case "reportLifecycleMode": return cfg ? s.activeUntil : null;
            case "lastReportCommentId": return s.lastReportCommentId != null ? String.valueOf(s.lastReportCommentId) : null;
            case "lastReportSummary": return s.lastReportSummary;
            case "lastReporter": return s.lastReporterKey;
            case "lastValidReporter": return s.lastValidReporterKey;

            // --- Phase 4: lifecycle / control ---------------------------------
            case "reportingControlState": return s.reportingControlState;
            case "reportingActive": return s.submitActive ? "true" : "false";
            case "reportingCloseCondition": return s.reportingClosedByCondition;
            case "reportingCloseReason": return s.reportingCloseReason;
            case "reportingClosedAt": return s.reportingClosedAt;
            case "reportingStartAt": return s.reportingStartAt;
            case "reportingEndAt": return s.reportingEndsMs;
            case "nextReportDueAt": return cfg ? (s.nextReportDueMs != null ? s.nextReportDueMs : s.nextExpectedMs) : null;
            case "currentWindowStartAt": return cfg ? s.currentWindowStartMs : null;
            case "currentWindowStatus": return cfg ? s.currentWindowStatus : null;

            // --- Phase 4: compliance / window aggregates ----------------------
            case "reportCompliancePercent":
                if (!cfg) return null;
                return (Double) (s.requiredSoFar > 0 ? Math.round(100.0 * s.validSoFar / s.requiredSoFar) : 100.0);
            case "satisfiedWindowCount": return cfg ? (Double) (double) s.satisfiedWindowCount : null;
            case "missedWindowCount": return cfg ? (Double) (double) s.missedWindowCount : null;
            case "lateWindowCount": return cfg ? (Double) (double) s.lateWindowCount : null;
            case "openWindowCount": return cfg ? (Double) (double) s.openWindowCount : null;
            case "futureWindowCount": return cfg ? (Double) (double) s.futureWindowCount : null;
            case "overdueOpenCount": return cfg ? (Double) (double) s.overdueOpenCount : null;
            case "maxLateMinutes": return cfg ? (Double) (double) s.maxLateMinutes : null;
            case "avgLateMinutes": return cfg ? (Double) s.avgLateMinutes : null;
            case "minutesSinceLastValidReport": return s.minutesSinceLastValidReport != null ? (Double) (double) s.minutesSinceLastValidReport : null;
            case "currentlyOverdueMinutes": return cfg ? (s.currentlyOverdueMinutes != null ? (Double) (double) s.currentlyOverdueMinutes : null) : null;

            // --- Report-metrics extension: total-required + missed-streak -----
            case "totalRequiredReportsUntilEnd": return cfg ? (Double) (double) s.totalRequiredUntilEnd : null;
            case "currentMissedReportStreak": return cfg ? (Double) (double) s.currentMissedStreak : null;
            case "maxMissedReportStreak": return cfg ? (Double) (double) s.maxMissedStreak : null;

            // --- Phase 4: today / day-level -----------------------------------
            case "todayRequiredCount": return cfg ? (Double) (double) s.todayRequiredCount : null;
            case "todayValidCount": return cfg ? (Double) (double) s.todayValidCount : null;
            case "todayMissedCount": return cfg ? (Double) (double) s.todayMissedCount : null;
            case "reportedToday": return cfg ? (s.reportedToday ? "true" : "false") : null;

            // --- Phase 4: responsible + exception/quality ---------------------
            case "responsibleUser": return s.responsibleUserKey;
            case "reportLevelMissing": return s.reportLevelMissing ? "true" : "false";
            case "reportConfigError": return s.reportConfigError ? "true" : "false";
            case "calcDurationMs": return (Double) (double) s.calcDurationMs;
            case "calculationVersion": return (Double) (double) s.calculationVersion;
            default: return null;
        }
    }

    private static String intervalText(int minutes) {
        if (minutes <= 0) {
            return null;
        }
        return minutes % 60 == 0 ? (minutes / 60) + "h" : minutes + "m";
    }

    private static String minute(int m) {
        int h = m / 60, mm = m % 60;
        return (h < 10 ? "0" : "") + h + ":" + (mm < 10 ? "0" : "") + mm;
    }
}
