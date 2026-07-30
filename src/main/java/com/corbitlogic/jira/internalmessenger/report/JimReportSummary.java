package com.corbitlogic.jira.internalmessenger.report;

import java.util.ArrayList;
import java.util.List;

/**
 * Computed report-compliance state for a single issue. Viewer-independent
 * counting (issue-level governance); the Report tab/mobile section render this.
 * Never carries localized/Jalali text — timestamps are raw epoch millis and the
 * UI layer formats them per the viewer's locale.
 */
public final class JimReportSummary {

    // Feature / configuration state
    public boolean featureEnabled;
    public boolean fieldsConfigured;
    /** True when feature + fields + level + schedule are all resolved (counting is meaningful). */
    public boolean configured;

    // --- Report applicability policy (reportscope) ---
    /**
     * Whether the issue is in scope for report compliance under the current
     * applicability policy. Defaults true (policy disabled = always applicable).
     * When false the engine does not compute/persist counts and the issue is
     * treated as OUT_OF_SCOPE (ledger left untouched, no field writes).
     */
    public boolean applicable = true;
    /**
     * Stable exclusion reason when {@link #applicable} is false:
     * excluded_done_status | excluded_missing_due | excluded_created_before_effective_date
     * | excluded_due_before_effective_date | excluded_by_effective_date. Null when applicable.
     */
    public String applicabilityReason;

    // Follow-up level + schedule (echoed for the UI)
    public String levelCode;        // normal|high|special|managerial|null
    public String levelText;        // raw option value on the issue
    public int intervalMinutes;
    public int windowStartMinute;
    public int windowEndMinute;
    public String countMode;        // daily|cumulative_*
    public String activeUntil;      // due|done|earlier

    // Counters
    public int requiredCount;
    public int validCount;
    public int rawCount;
    public int outOfWindowCount;
    public int duplicateCount;
    public int missedCount;
    public int requiredSoFar;
    public int validSoFar;
    public Long nextExpectedMs;
    /** on_track | overdue | not_configured */
    public String compliance = "not_configured";
    /** Rich uppercase status for the generated metric: ON_TRACK|OVERDUE|COMPLETE|NOT_CONFIGURED|INACTIVE|NO_FOLLOW_UP_LEVEL|ENDED_DUE_DATE|ENDED_DONE. */
    public String statusCode = "NOT_CONFIGURED";

    // Report metadata (for generated metrics)
    public Long firstReportMs;
    public Long lastReportMs;
    public Long lastValidReportMs;
    public Long reportingEndsMs;
    public String lastReporterKey;
    public String lastReporterName;
    public String lastValidReporterKey;
    public String lastValidReporterName;
    public Long lastReportCommentId;
    public String lastReportSummary;
    /** Admin: allow submitting reports even when level/fields are not configured. */
    public boolean allowWithoutConfig;
    /** Offset (minutes from UTC) of the effective tz used for window + display, so client renders times identically. */
    public int tzOffsetMinutes;

    // Lifecycle / submit availability
    public boolean submitActive;
    /**
     * Explicit "a report is currently required from the user" flag. Always false
     * once {@link #reportingControlState} is CLOSED (final-acceptance contract:
     * a closed issue must never require/encourage a report). Mirrors
     * {@link #submitActive} in every other state.
     */
    public boolean reportRequired;
    /** done | due_passed | ended | due_missing | not_configured | null(active) */
    public String inactiveReason;
    public Long dueEndMs;
    public Long doneMs;
    public boolean dueMissing;
    public long issueCreatedMs;

    // --- Phase 2/4: reporting-control lifecycle (persisted in JimReportControl) ---
    /** NOT_REQUIRED | ACTIVE | PAUSED | CLOSED | CONFIG_ERROR */
    public String reportingControlState = "NOT_REQUIRED";
    public Long reportingClosedAt;
    /** done | due_passed | manual | null */
    public String reportingClosedByCondition;
    public String reportingCloseReason;
    public Long reportingStartAt;
    public Long lastRequiredWindowEndMs;

    // --- Phase 4: window-level aggregates (from the deterministic ledger compute) ---
    public int satisfiedWindowCount;
    public int missedWindowCount;
    public int lateWindowCount;
    public int openWindowCount;
    public int futureWindowCount;
    public int overdueOpenCount;
    public int maxLateMinutes;
    public double avgLateMinutes;
    public Long minutesSinceLastValidReport;
    public Long currentlyOverdueMinutes;
    /** Current window start/status for the dashboard (null when none active). */
    public Long currentWindowStartMs;
    public String currentWindowStatus;
    public Long nextReportDueMs;

    // --- Report-metrics extension: total-required + missed-streak (deterministic
    // from the window ledger; see JimReportServiceImpl#computeStreakMetrics) -----
    /**
     * Deterministic total number of required reports over the whole reporting-
     * control period up to the effective end/closure. Interval/daily = count of
     * ledger windows that are/were a real requirement (excludes
     * CLOSED_NOT_REQUIRED/EXEMPT); fixed-minimum = the configured minimum.
     */
    public int totalRequiredUntilEnd;
    /**
     * Consecutive MISSED required windows since the last valid report (or since
     * reporting start if no valid report exists). Resets to 0 on a valid report;
     * never counts OPEN/FUTURE/post-closure windows; not raised prematurely for
     * fixed-minimum before the end condition is reached.
     */
    public int currentMissedStreak;
    /** Highest {@link #currentMissedStreak} observed across the lifecycle. */
    public int maxMissedStreak;

    // --- Phase 4: today/day-level ---
    public int todayRequiredCount;
    public int todayValidCount;
    public int todayMissedCount;
    public boolean reportedToday;

    // --- Phase 4: responsible + exception/quality ---
    public String responsibleUserKey;
    public String responsibleUserName;
    public boolean reportLevelMissing;
    public boolean reportConfigError;
    public long calcDurationMs;
    public int calculationVersion = 1;

    // Persist diagnostics (populated by persistMetrics; not part of the wire DTO).
    /** Number of metric values that differed and were set on the last persist. */
    public transient int valuesChanged;
    /** Number of custom fields actually written on the last persist. */
    public transient int fieldsWritten;
    /** Whether the last persist dispatched a Jira event (false after Phase 1: DO_NOT_DISPATCH). */
    public transient boolean dispatched;

    public final List<Item> items = new ArrayList<Item>();

    /**
     * Deterministic per-window ledger for the API/export + AO materialization
     * (Phase 3). Ordered by windowStart; recomputed on every summary. Transient
     * (not serialized in the compliance-summary wire DTO).
     */
    public final transient List<WindowInfo> windowLedger = new ArrayList<WindowInfo>();

    /** One report comment (marker-tagged). Enriched further by the UI layer. */
    public static final class Item {
        public long commentId;
        public long createdMs;
        public String authorKey;
        /** 0=valid, 1=duplicate(same interval), 2=out-of-window (see JimReportCalculator). */
        public int status;
    }

    /** One computed reporting window (Phase 3 ledger detail). */
    public static final class WindowInfo {
        public long windowStart;
        public long windowEnd;
        /** FUTURE | OPEN | SATISFIED | MISSED | LATE | CLOSED_NOT_REQUIRED | EXEMPT */
        public String status;
        public int validCount;
        public int rawCount;
        public int duplicateCount;
        public Long firstValidReportMs;
        public Long lastReportMs;
        public int lateByMinutes;
        public final List<Long> commentIds = new ArrayList<Long>();
    }
}
