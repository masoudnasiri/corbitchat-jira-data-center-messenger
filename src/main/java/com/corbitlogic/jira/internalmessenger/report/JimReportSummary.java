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

    // --- Report ENFORCEMENT policy (scoring scope only; never blocks submit/display) ---
    /** Whether the enforcement (report-scope) policy is enabled by the admin. */
    public boolean enforcementEnabled;
    /** Configured enforcement start date (Gregorian ISO YYYY-MM-DD) or "" = none. */
    public String enforcementStartDate = "";
    /** Enforcement date-basis mode (created_on_or_after | due_on_or_after | fixed_counter_start | ...). */
    public String enforcementDateBasis;
    /** True when the basis is fixed_counter_start (created/due are NOT scope filters). */
    public boolean fixedCounterStartMode;
    /** True when this issue participates in compliance scoring (required/missed). */
    public boolean inEnforcementScope = true;
    /** Stable reason the issue is out of scoring scope (mirrors applicabilityReason). */
    public String outOfScopeReason;
    /** Lower bound (epoch ms, report tz start-of-day) for scored required/missed buckets. 0 = none, Long.MAX_VALUE = fully out of scope. */
    public long scoringPeriodStartMs;
    /** True when a report is required for SCORING now (false when out of scope or paused). */
    public boolean reportRequiredForScoring;
    /** Reports that predate the scoring start / are out of enforcement scope (raw history only, unscored). */
    public int outOfScopeReportCount;

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
    /** Reports submitted on a non-working day or during a paused interval (raw only, out-of-schedule). */
    public int outOfScheduleCount;
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

    // --- Working-day counter + report-receiver pause (Report Replies & Working-Days) ---
    /** True when the working-days feature is on (counter counts only working days). */
    public boolean workingDaysEnabled;
    /** Resolved working-day source, e.g. jira_time_tracking(daysPerWeek=5) | admin_working_days | fallback_default(...). */
    public String workingDaysSource;
    /** Whether "today" (in the report tz) is a working day; drives the "امروز روز کاری نیست" state. */
    public boolean todayWorkingDay = true;
    // --- Working calendar (board vs plugin) exposure (Board Working-Days feature) ---
    /** Selected calendar source mode (admin setting): jira_board_first | plugin_calendar_only | plugin_calendar_plus_board_holidays | time_tracking_fallback. */
    public String workingCalendarSource;
    /** Effective source actually used (e.g. jira_board(6) | plugin_working_days | ...(fallback:reason)). */
    public String workingCalendarResolvedSource;
    /** Resolved board (rapidView) id used for the calendar, or 0 when none. */
    public long workingCalendarBoardId;
    /** Resolved board name, when a board calendar was used. */
    public String workingCalendarBoardName;
    /** Reason the board could not be used (when falling back), or null. */
    public String workingCalendarFallbackReason;
    /** Working weekdays as Calendar.DAY_OF_WEEK ints (1=Sun..7=Sat). */
    public java.util.List<Integer> workingWeekdays;
    /** Count of non-working dates (plugin holidays ∪ board non-working dates) in effect. */
    public int nonWorkingDatesCount;
    /** Reason today is non-working ("holiday" | "non_working_weekday"), or null. */
    public String todayNonWorkingReason;
    /** Next working day (YYYY-MM-DD) when today is non-working, else null. */
    public String nextWorkingDay;
    /** Whether the report-receiver pause rule is enabled. */
    public boolean receiverPauseEnabled;
    /** True when a report receiver (schedule-approval approver) is configured on the issue. */
    public boolean reportReceiverConfigured;
    public String reportReceiverKey;
    public String reportReceiverName;
    /** True when counting is paused right now because the current assignee is the report receiver. */
    public boolean pausedNow;
    /** Pause reason code (ASSIGNED_TO_REPORT_RECEIVER) or null when not paused. */
    public String pauseReason;

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
        /** 0=valid, 1=duplicate(same interval), 2=out-of-window, 3=out-of-schedule (see JimReportCalculator). */
        public int status;
        /** Number of replies linked to this report comment (never counted as reports). */
        public int repliesCount;
        /** Cheap preview of the latest reply (plain text, truncated) or null. */
        public String latestReplyPreview;
        /** Timestamp of the latest reply, or null. */
        public Long latestReplyMs;
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
