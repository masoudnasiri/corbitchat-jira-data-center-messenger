package com.corbitlogic.jira.internalmessenger.report;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.history.ChangeItemBean;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.issue.status.category.StatusCategory;
import com.atlassian.jira.timezone.TimeZoneManager;
import com.atlassian.jira.user.ApplicationUser;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link JimReportService}. Counting is issue-level and viewer-agnostic;
 * it reads every comment on the issue and classifies the marker-tagged ones via
 * {@link JimReportCalculator}. Writes go through {@code IssueManager.updateIssue}
 * (governance write, like schedule approval) and only when a value changes.
 */
public class JimReportServiceImpl implements JimReportService {
    private static final Logger log = LoggerFactory.getLogger(JimReportServiceImpl.class);

    /**
     * In-memory input fingerprint per issue (Phase 1). When the fingerprint is
     * unchanged since the last successful calc, {@link #recalculateWithOutcome}
     * skips the heavy compute + persist. Cleared on restart (Phase 2 persists it).
     */
    private final ConcurrentHashMap<Long, String> lastFingerprint = new ConcurrentHashMap<Long, String>();

    /**
     * Cached Done-transition time per issue so the full-changelog scan
     * ({@link #resolveDoneTime}) is not repeated on every burst event. Populated
     * when the issue is in a Done status category; cleared when it leaves Done.
     */
    private final ConcurrentHashMap<Long, Long> doneMsCache = new ConcurrentHashMap<Long, Long>();

    /**
     * Belt-and-suspenders re-entrancy guard: set true on the thread that is
     * writing report metrics so a (theoretical) re-entrant issue event on the
     * same thread can skip enqueuing another recalc. With DO_NOT_DISPATCH the
     * write no longer fires an event, so this should never actually trip.
     */
    private static final ThreadLocal<Boolean> WRITE_IN_PROGRESS = new ThreadLocal<Boolean>();

    /** Bumped when the calculation logic changes (persisted per row for migrations). */
    static final int CALCULATION_VERSION = 2;

    /**
     * AO ledger (Phase 2/3): persisted reporting-control lifecycle + bounded
     * per-window ledger. May be null in unit contexts; all use is null-guarded so
     * the feature degrades to Phase 1 in-memory behaviour when unavailable.
     */
    private final JimReportLedgerService ledgerService;

    @Inject
    public JimReportServiceImpl(JimReportLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /** True when the current thread is inside a report-metric write. */
    public static boolean isReportWriteInProgress() {
        return Boolean.TRUE.equals(WRITE_IN_PROGRESS.get());
    }

    @Override
    public boolean isReportComment(String body) {
        // A comment counts as a report ONLY when the marker appears OUTSIDE any
        // quoted segment. This is the critical parser rule: a report reply quotes
        // the parent report ({quote}...#گزارش...{quote}) and must never be counted
        // as a new report (nor a quoted marker in any ordinary comment).
        return JimReportConfig.isReportSubmissionBody(body);
    }

    @Override
    public JimReportSummary computeSummary(Issue issue) {
        long calcStart = System.currentTimeMillis();
        JimReportSummary s = new JimReportSummary();
        s.calculationVersion = CALCULATION_VERSION;
        if (issue == null) {
            return s;
        }
        s.featureEnabled = JimReportConfig.isFeatureEnabled();
        s.fieldsConfigured = JimReportConfig.fieldsConfigured();
        s.allowWithoutConfig = JimReportConfig.allowWithoutConfig();
        s.issueCreatedMs = issue.getCreated() != null ? issue.getCreated().getTime() : System.currentTimeMillis();

        if (!s.featureEnabled) {
            s.compliance = "not_configured";
            s.statusCode = "NOT_CONFIGURED";
            s.inactiveReason = "not_configured";
            s.submitActive = false;
            return s;
        }

        s.levelCode = JimReportConfig.levelCodeOf(issue);
        s.levelText = rawLevelText(issue);
        s.activeUntil = s.levelCode != null ? JimReportConfig.activeUntilForLevel(s.levelCode) : "earlier";
        s.countMode = s.levelCode != null ? JimReportConfig.countModeForLevel(s.levelCode) : "daily";

        JimReportConfig.ReportSchedule sch = s.levelCode != null
                ? JimReportConfig.scheduleForLevel(s.levelCode) : null;

        // Gather report comments (marker-tagged) — always, so the raw total and
        // list show even when the schedule/fields are not configured.
        List<Comment> reportComments = collectReportComments(issue);
        s.rawCount = reportComments.size();

        TimeZone tz = jiraTimeZone();
        s.tzOffsetMinutes = tz.getOffset(System.currentTimeMillis()) / 60000;
        boolean isDoneNow = isInDoneCategory(issue);
        s.doneMs = isDoneNow ? resolveDoneTimeCached(issue) : null;
        if (!isDoneNow && issue.getId() != null) {
            doneMsCache.remove(issue.getId()); // left Done → drop stale cache
        }
        s.dueEndMs = endOfDueDay(issue, tz);
        s.dueMissing = resolveEffectiveDueDate(issue) == null;

        // Report ENFORCEMENT policy (reportscope): enforcement bounds SCORING ONLY —
        // it never blocks report submission and never hides report history. When
        // enabled it derives a per-issue lower bound
        //   scoringPeriodStart = max(issueCreated, enforcementStartDate)
        // that is applied to required/missed bucket generation. An issue whose whole
        // period is before the start (or excluded by the date-basis filters) gets zero
        // required/missed (statusCode NOT_ENFORCED) but keeps submitActive + items.
        // The date basis uses the issue's SYSTEM due date so it matches the impact-scan
        // JQL (duedate). Done = statusCategory Done (isDoneNow).
        Long sysDueMs = issue.getDueDate() != null ? issue.getDueDate().getTime() : null;
        s.enforcementEnabled = JimReportConfig.applicabilityEnabled();
        s.enforcementStartDate = JimReportConfig.effectiveFromDate();
        s.enforcementDateBasis = JimReportConfig.applicabilityDateMode();
        s.fixedCounterStartMode = s.enforcementEnabled
                && "fixed_counter_start".equals(s.enforcementDateBasis);
        String scopeExclusion = s.enforcementEnabled
                ? JimReportConfig.applicabilityExclusion(s.issueCreatedMs, sysDueMs, isDoneNow, tz) : null;
        s.inEnforcementScope = (scopeExclusion == null);
        s.outOfScopeReason = scopeExclusion;
        s.applicable = s.inEnforcementScope;           // back-compat with existing clients
        s.applicabilityReason = scopeExclusion;
        Long enforcementStartMsObj = s.enforcementEnabled ? JimReportConfig.effectiveFromMs(tz) : null;
        long enforcementStartMs = enforcementStartMsObj != null ? enforcementStartMsObj.longValue() : 0L;
        if (!s.inEnforcementScope) {
            s.scoringPeriodStartMs = Long.MAX_VALUE;    // fully out of scope: zero scored buckets
        } else if (enforcementStartMs > 0L) {
            s.scoringPeriodStartMs = Math.max(s.issueCreatedMs, enforcementStartMs);
        } else {
            s.scoringPeriodStartMs = 0L;                // no date bound (policy off or empty date)
        }

        boolean fixedMin = "fixed_minimum".equals(s.countMode);
        if (sch != null) {
            s.intervalMinutes = sch.intervalMinutes;
            s.windowStartMinute = sch.windowStartMinute;
            s.windowEndMinute = sch.windowEndMinute;
        } else if (fixedMin && s.levelCode != null) {
            // Fixed-minimum mode has no interval; still resolve the window for the
            // out-of-window rule (per-level override → global).
            int[] w = JimReportConfig.windowForLevel(s.levelCode);
            s.windowStartMinute = w[0];
            s.windowEndMinute = w[1];
        }

        // Build report items (unclassified for now; classified from calc result).
        for (Comment c : reportComments) {
            JimReportSummary.Item it = new JimReportSummary.Item();
            it.commentId = c.getId();
            it.createdMs = c.getCreated() != null ? c.getCreated().getTime() : 0L;
            it.authorKey = c.getAuthorApplicationUser() != null ? c.getAuthorApplicationUser().getKey() : null;
            it.status = JimReportCalculator.STATUS_OUT_OF_WINDOW;
            s.items.add(it);
        }
        computeReportMetadata(s, reportComments);

        boolean configured = s.fieldsConfigured && s.levelCode != null && (fixedMin || sch != null);
        s.configured = configured;

        if (!configured) {
            s.compliance = "not_configured";
            // Everything is raw-only when we can't bucket.
            s.outOfWindowCount = s.rawCount;
            computeSubmitActive(s, isDoneNow, tz, false);
            s.statusCode = computeStatusCode(s, isDoneNow);
            return finalizeSummary(issue, s, isDoneNow, tz, calcStart);
        }

        // Fixed-minimum mode: "at least N reports until the lifecycle end" — no
        // interval buckets. Submit-active + reportingEndsMs come first so the
        // endpoint is known for counting.
        if (fixedMin) {
            computeSubmitActive(s, isDoneNow, tz, true);
            computeFixedMinimum(s, tz);
            computeValidReporterMetadata(s, reportComments);
            s.statusCode = computeStatusCode(s, isDoneNow);
            applyFixedMinimumAggregates(s);
            return finalizeSummary(issue, s, isDoneNow, tz, calcStart);
        }

        // Run the pure calculator.
        JimReportCalculator.Input in = new JimReportCalculator.Input();
        in.tz = tz;
        in.issueCreatedMs = s.issueCreatedMs;
        in.intervalMinutes = sch.intervalMinutes;
        in.windowStartMin = sch.windowStartMinute;
        in.windowEndMin = sch.windowEndMinute;
        in.workingDayInterval = sch.workingDayInterval; // Phase 8B: unit=days → N working days
        in.nowMs = System.currentTimeMillis();
        in.daily = "daily".equals(s.countMode);
        in.periodEndMs = countPeriodEnd(s, isDoneNow);
        // Working-day gate: non-working days (Jira days-per-week + weekday fallback +
        // holidays) generate no required buckets, so they never grow required/missed/
        // streak counters. Null classifier when the feature is off (calendar days).
        in.dayClassifier = ReportWorkingCalendarService.classifier(tz, issue);
        // Report-receiver pause: skip buckets while the issue is assigned to the
        // report receiver (approver), reconstructed from assignee change history.
        long pauseUpper = in.daily
                ? (startOfDay(in.nowMs, tz) + 24L * 60 * 60 * 1000L)
                : (in.periodEndMs != null ? Math.max(in.periodEndMs, in.nowMs) : in.nowMs);
        in.pausedIntervals = ReportResponsibilityService.pausedIntervals(issue, in.issueCreatedMs, pauseUpper);
        // Enforcement scoring lower bound — bounds required/missed only (submission +
        // history already flow normally above). 0 = no bound.
        in.scoredPeriodStartMs = s.scoringPeriodStartMs;
        long[] ts = new long[s.items.size()];
        for (int i = 0; i < s.items.size(); i++) {
            ts[i] = s.items.get(i).createdMs;
        }
        in.reports = ts;

        JimReportCalculator.Result r = JimReportCalculator.compute(in);
        s.requiredCount = r.requiredCount;
        s.validCount = r.validCount;
        s.outOfWindowCount = r.outOfWindowCount;
        s.outOfScheduleCount = r.outOfScheduleCount;
        s.outOfScopeReportCount = r.outOfScopeCount;
        s.duplicateCount = r.duplicateCount;
        s.missedCount = r.missedCount;
        s.requiredSoFar = r.requiredSoFar;
        s.validSoFar = r.validSoFar;
        s.nextExpectedMs = r.nextExpectedMs;
        s.compliance = r.compliance;
        for (int i = 0; i < s.items.size() && i < r.reportStatus.length; i++) {
            s.items.get(i).status = r.reportStatus[i];
        }
        computeValidReporterMetadata(s, reportComments);

        computeSubmitActive(s, isDoneNow, tz, true);
        s.statusCode = computeStatusCode(s, isDoneNow);

        // Phase 3/4: build the deterministic window ledger + aggregates first so
        // the lifecycle-close boundary can suppress post-closure missed windows.
        buildWindowLedger(s, r, tz);

        // Lazy persist on read when operational (feature + fields). Cheap + guarded.
        return finalizeSummary(issue, s, isDoneNow, tz, calcStart);
    }

    // --- Phase 2/3/4: lifecycle + window ledger finalization ------------------

    /**
     * Shared tail for every computeSummary path: derive the persisted reporting-
     * control state, fill responsible/exception fields, write the mapped custom
     * fields (Phase 1 loop-free), and upsert the AO control + window ledger.
     */
    private JimReportSummary finalizeSummary(Issue issue, JimReportSummary s,
                                             boolean isDoneNow, TimeZone tz, long calcStart) {
        applyLifecycleState(issue, s);
        enforceClosedContract(s, isDoneNow);
        applyCommonExtras(issue, s, tz);
        computeStreakMetrics(s);
        // Link report replies (real Jira comments) to their parent report for the UI/API.
        // Never affects counting — replies are excluded by the parser rule.
        try {
            JimReportReplies.enrichSummary(issue, s, collectReportComments(issue));
        } catch (Throwable ignore) {
        }
        s.calcDurationMs = System.currentTimeMillis() - calcStart;
        persistMetrics(issue, s, null);
        persistLedger(issue, s);
        return s;
    }

    /**
     * Report-metrics extension: compute {@code totalRequiredUntilEnd},
     * {@code currentMissedStreak} and {@code maxMissedStreak} deterministically
     * from the already-built window ledger (interval/daily) or the fixed-minimum
     * aggregates. Runs in {@link #finalizeSummary} AFTER the lifecycle/close state
     * is finalized (so CLOSED is known) and BEFORE {@link #persistMetrics} (so the
     * mapped custom-field writes see the values). Pure function of the summary —
     * idempotent, creates no ledger rows.
     *
     * <p>Streak rules: iterate the chronologically-ordered ledger; a SATISFIED
     * (or LATE = eventually-satisfied) window resets the running streak to 0; a
     * MISSED window increments it; OPEN / FUTURE / CLOSED_NOT_REQUIRED / EXEMPT
     * windows are ignored (never missed, never a reset). {@code currentMissedStreak}
     * is the trailing run; {@code maxMissedStreak} is the peak. Duplicate and
     * out-of-window reports never create a SATISFIED window, so they can never
     * reset the streak.</p>
     */
    private void computeStreakMetrics(JimReportSummary s) {
        s.totalRequiredUntilEnd = 0;
        s.currentMissedStreak = 0;
        s.maxMissedStreak = 0;
        if (!s.configured) {
            return;
        }
        if (!s.windowLedger.isEmpty()) {
            // Interval/daily: derive everything from the ordered ledger.
            int total = 0;
            int streak = 0;
            int max = 0;
            for (JimReportSummary.WindowInfo wi : s.windowLedger) {
                String st = wi.status;
                if ("CLOSED_NOT_REQUIRED".equals(st) || "EXEMPT".equals(st)) {
                    continue; // post-closure / exempt: not a real requirement
                }
                total++;
                if ("SATISFIED".equals(st) || "LATE".equals(st)) {
                    streak = 0;
                } else if ("MISSED".equals(st)) {
                    streak++;
                    if (streak > max) {
                        max = streak;
                    }
                }
                // OPEN / FUTURE: counted as a requirement (total) but neither
                // missed nor a reset — leave the running streak unchanged.
            }
            s.totalRequiredUntilEnd = total;
            s.currentMissedStreak = streak;
            s.maxMissedStreak = max;
        } else {
            // Fixed-minimum: a single "at least N until end" requirement. Total =
            // the configured minimum; the streak is the outstanding shortfall only
            // once the end condition is reached (never raised prematurely).
            s.totalRequiredUntilEnd = s.requiredCount;
            boolean ended = "CLOSED".equals(s.reportingControlState)
                    || "due_passed".equals(s.inactiveReason)
                    || "done".equals(s.inactiveReason)
                    || (s.reportingEndsMs != null && System.currentTimeMillis() > s.reportingEndsMs);
            if (s.validCount >= s.requiredCount) {
                s.currentMissedStreak = 0;
                s.maxMissedStreak = 0;
            } else if (ended) {
                int shortfall = Math.max(0, s.requiredCount - s.validCount);
                s.currentMissedStreak = shortfall;
                s.maxMissedStreak = shortfall;
            } else {
                s.currentMissedStreak = 0;
                s.maxMissedStreak = 0;
            }
        }
    }

    /**
     * Final-acceptance contract: once reporting control is CLOSED, no report is
     * required or encouraged — regardless of how it closed (Done status, due
     * date passed, manual) or of any reopen/active recomputation with the reopen
     * toggle OFF. This runs AFTER {@link #applyLifecycleState} (which owns the
     * state machine) so it can override the earlier submit-active / status-code /
     * current-window values that were computed before the state was known (e.g.
     * a reopened-but-not-reactivated issue whose raw submit-active came back
     * true). When the reopen toggle is ON the issue is set back to ACTIVE in
     * {@link #applyLifecycleState} and this is a no-op, so an active reactivated
     * issue keeps its correct submit-active requirement.
     */
    private void enforceClosedContract(JimReportSummary s, boolean isDoneNow) {
        if (!"CLOSED".equals(s.reportingControlState)) {
            return;
        }
        s.submitActive = false;
        if (s.inactiveReason == null) {
            s.inactiveReason = s.reportingClosedByCondition != null
                    ? s.reportingClosedByCondition : "closed";
        }
        // No future obligation: drop any next-due hint and mark the current
        // window as not-required so the UI/API is unambiguous.
        s.nextExpectedMs = null;
        s.nextReportDueMs = null;
        s.currentWindowStatus = "CLOSED_NOT_REQUIRED";
        // Recompute the rich status code now that submit-active is false so it
        // reports ENDED_DONE / ENDED_DUE_DATE instead of a stale ON_TRACK/OVERDUE.
        s.statusCode = computeStatusCode(s, isDoneNow);
    }

    /**
     * Derive the explicit reporting-control state machine from the (already
     * computed) submit-active state + prior persisted snapshot. Sets CLOSED when
     * a stop condition is met and does not reopen a CLOSED issue unless the admin
     * opted in via {@code report.reopenReactivation}.
     */
    private void applyLifecycleState(Issue issue, JimReportSummary s) {
        JimReportLedgerService.ControlSnapshot prev = null;
        try {
            if (ledgerService != null && issue.getId() != null) {
                prev = ledgerService.loadControl(issue.getId());
            }
        } catch (Throwable ignore) {
        }
        Long prevClosedAt = prev != null ? prev.reportingClosedAt : null;
        String prevState = prev != null ? prev.state : null;
        Long prevStartAt = prev != null ? prev.reportingStartAt : null;

        if (!s.featureEnabled) {
            s.reportingControlState = "NOT_REQUIRED";
            return;
        }
        if (s.levelCode == null) {
            s.reportingControlState = "NOT_REQUIRED";
            return;
        }
        if (!s.configured) {
            s.reportingControlState = "CONFIG_ERROR";
            return;
        }
        // Configured: start time is the first time it became trackable.
        s.reportingStartAt = prevStartAt != null ? prevStartAt : s.issueCreatedMs;

        boolean stopMet = !s.submitActive
                && ("done".equals(s.inactiveReason) || "due_passed".equals(s.inactiveReason));
        boolean wasClosed = "CLOSED".equals(prevState) || prevClosedAt != null;

        if (stopMet || wasClosed) {
            boolean reopenAllowed = JimReportConfig.reopenReactivation();
            String prevCondition = prev != null ? prev.reportingClosedByCondition : null;
            // Auto-reopen on a DEADLINE EXTENSION: a due-based closure whose
            // effective due has since moved into the future (e.g. an approved /
            // updated completion date) must reactivate — the effective reporting
            // end has NOT been reached, so reporting cannot stay globally closed.
            // This is an explicit deadline change, not flapping, so it does not
            // depend on the admin reopen toggle. A Done-based closure stays final
            // (kept behind the toggle) since Done is an acceptance decision.
            boolean deadlineExtended =
                    wasClosed && s.submitActive && !"done".equals(prevCondition);
            if (wasClosed && s.submitActive && (reopenAllowed || deadlineExtended)) {
                // Reactivate a previously closed issue (admin opt-in OR the
                // deadline was extended into the future).
                s.reportingControlState = "ACTIVE";
                s.reportingClosedAt = null;
                s.reportingClosedByCondition = null;
                s.reportingCloseReason = null;
                return;
            }
            s.reportingControlState = "CLOSED";
            s.reportingClosedAt = prevClosedAt != null ? prevClosedAt
                    : (s.reportingEndsMs != null ? s.reportingEndsMs : System.currentTimeMillis());
            s.reportingClosedByCondition = prev != null && prev.reportingClosedByCondition != null
                    ? prev.reportingClosedByCondition
                    : (s.inactiveReason != null ? s.inactiveReason : "closed");
            s.reportingCloseReason = closeReasonText(s.reportingClosedByCondition);
            return;
        }
        s.reportingControlState = s.submitActive ? "ACTIVE" : "PAUSED";
    }

    private static String closeReasonText(String condition) {
        if ("done".equals(condition)) {
            return "Issue reached a Done status";
        }
        if ("due_passed".equals(condition)) {
            return "Due date passed";
        }
        return "Reporting closed";
    }

    /** Responsible user + exception/quality + last-report-derived fields. */
    private void applyCommonExtras(Issue issue, JimReportSummary s, TimeZone tz) {
        try {
            ApplicationUser assignee = issue.getAssignee();
            if (assignee != null) {
                s.responsibleUserKey = assignee.getKey();
                s.responsibleUserName = assignee.getDisplayName();
            }
        } catch (Throwable ignore) {
        }
        s.reportLevelMissing = s.featureEnabled && s.levelCode == null;
        s.reportConfigError = "CONFIG_ERROR".equals(s.reportingControlState);
        long now = System.currentTimeMillis();
        if (s.lastValidReportMs != null) {
            s.minutesSinceLastValidReport = Math.max(0L, (now - s.lastValidReportMs) / 60000L);
        }
        // Reported today = a report (any) whose timestamp is within today's window.
        if (s.lastReportMs != null && isSameDay(s.lastReportMs, now, tz)) {
            s.reportedToday = true;
        }
        s.nextReportDueMs = s.nextExpectedMs;

        // --- Working-day + report-receiver pause state (for the UI/API) ---
        s.workingDaysEnabled = ReportWorkingCalendarService.isEnabled();
        try {
            ReportWorkingCalendarService.Resolved wc = ReportWorkingCalendarService.resolve(issue);
            s.workingDaysSource = wc.resolvedSource;
            s.workingCalendarSource = wc.source;
            s.workingCalendarResolvedSource = wc.resolvedSource;
            s.workingCalendarBoardId = wc.boardId > 0 ? wc.boardId : 0L;
            s.workingCalendarBoardName = wc.boardName;
            s.workingCalendarFallbackReason = wc.fallbackReason;
            s.workingWeekdays = new java.util.ArrayList<Integer>(wc.workingDaysOfWeek);
            s.nonWorkingDatesCount = wc.holidays.size();
        } catch (Throwable ignore) {
        }
        s.todayWorkingDay = ReportWorkingCalendarService.isWorkingDay(now, tz, issue);
        try {
            if (s.workingDaysEnabled && !s.todayWorkingDay) {
                s.todayNonWorkingReason = ReportWorkingCalendarService.nonWorkingReason(now, tz, issue);
                s.nextWorkingDay = ReportWorkingCalendarService.nextWorkingDay(now, tz, issue);
            }
        } catch (Throwable ignore) {
        }
        ReportResponsibilityService.PauseState ps = ReportResponsibilityService.pauseState(issue);
        s.receiverPauseEnabled = ps.enabled;
        s.reportReceiverConfigured = ps.receiverConfigured;
        s.reportReceiverKey = ps.receiverKey;
        s.reportReceiverName = ps.receiverName;
        s.pausedNow = ps.pausedNow;
        s.pauseReason = ps.pauseReason;

        // Explicit contract flag, evaluated after the CLOSED enforcement so a
        // closed issue is always reportRequired=false. When paused (assigned to the
        // report receiver) no report is required of the receiver. When the issue is
        // out of enforcement scoring scope no report is REQUIRED (but submission is
        // still allowed — submitActive is unchanged).
        s.reportRequired = s.submitActive && !s.pausedNow && s.inEnforcementScope;
        s.reportRequiredForScoring = s.reportRequired;
        // Out of scoring scope: never surface an overdue/missed state; submission and
        // history remain available. statusCode NOT_ENFORCED is unambiguous for the UI.
        if (s.enforcementEnabled && !s.inEnforcementScope) {
            if ("overdue".equals(s.compliance)) {
                s.compliance = "on_track";
            }
            s.statusCode = "NOT_ENFORCED";
        }
    }

    /** Compute + persist the input fingerprint into the AO control row (survives restart). */
    private void persistLedger(Issue issue, JimReportSummary s) {
        if (ledgerService == null || !s.featureEnabled) {
            return;
        }
        try {
            String fp = computeFingerprint(issue);
            ledgerService.persist(issue, s, fp);
        } catch (Throwable ex) {
            log.debug("event=report stage=ledger outcome=persist_skip issueKey={} message={}",
                    issue.getKey(), ex.getMessage());
        }
    }

    /**
     * Build the deterministic per-window ledger + Phase 4 window aggregates from
     * the pure calculator result. Windows whose start is at/after the reporting
     * close time are marked CLOSED_NOT_REQUIRED and never counted as missed.
     */
    private void buildWindowLedger(JimReportSummary s, JimReportCalculator.Result r, TimeZone tz) {
        s.windowLedger.clear();
        long now = System.currentTimeMillis();
        Long closeBoundary = "CLOSED".equals(s.reportingControlState) ? s.reportingClosedAt : null;
        int satisfied = 0, missed = 0, late = 0, open = 0, future = 0, overdueOpen = 0;
        int maxLate = 0;
        long lateSum = 0L;
        int lateCountForAvg = 0;
        Long lastRequiredEnd = null;
        long todayStart = startOfDay(now, tz);
        long todayEnd = todayStart + 24L * 60 * 60 * 1000L;
        int todayReq = 0, todayValid = 0, todayMissed = 0;
        Long curOverdueMin = null;

        for (JimReportCalculator.WindowDetail w : r.windows) {
            JimReportSummary.WindowInfo wi = new JimReportSummary.WindowInfo();
            wi.windowStart = w.start;
            wi.windowEnd = w.end;
            wi.validCount = w.validCount;
            wi.rawCount = w.rawCount;
            wi.duplicateCount = w.duplicateCount;
            wi.firstValidReportMs = w.firstValidMs;
            wi.lastReportMs = w.lastReportMs;
            for (Integer idx : w.reportIndexes) {
                if (idx != null && idx >= 0 && idx < s.items.size()) {
                    wi.commentIds.add(s.items.get(idx).commentId);
                }
            }
            String status;
            if (closeBoundary != null && w.start >= closeBoundary) {
                status = "CLOSED_NOT_REQUIRED";
            } else if (w.start > now) {
                status = "FUTURE";
                future++;
            } else if (w.filled) {
                status = "SATISFIED";
                satisfied++;
            } else if (w.end <= now) {
                status = "MISSED";
                missed++;
            } else {
                status = "OPEN";
                open++;
                overdueOpen++; // elapsed start, unsatisfied, still open
                curOverdueMin = Math.max(0L, (now - w.start) / 60000L);
            }
            wi.status = status;
            if (!"FUTURE".equals(status) && !"CLOSED_NOT_REQUIRED".equals(status)) {
                lastRequiredEnd = w.end;
            }
            // Today buckets.
            if (w.start >= todayStart && w.start < todayEnd) {
                todayReq++;
                if (w.filled) {
                    todayValid++;
                } else if (w.end <= now) {
                    todayMissed++;
                }
            }
            s.windowLedger.add(wi);
        }
        s.satisfiedWindowCount = satisfied;
        s.missedWindowCount = missed;
        s.lateWindowCount = late;
        s.openWindowCount = open;
        s.futureWindowCount = future;
        s.overdueOpenCount = overdueOpen;
        s.maxLateMinutes = maxLate;
        s.avgLateMinutes = lateCountForAvg > 0 ? (double) lateSum / lateCountForAvg : 0.0;
        s.lastRequiredWindowEndMs = lastRequiredEnd;
        s.todayRequiredCount = todayReq;
        s.todayValidCount = todayValid;
        s.todayMissedCount = todayMissed;
        s.currentlyOverdueMinutes = curOverdueMin;
        // Current window = the one containing "now".
        for (JimReportSummary.WindowInfo wi : s.windowLedger) {
            if (wi.windowStart <= now && now < wi.windowEnd) {
                s.currentWindowStartMs = wi.windowStart;
                s.currentWindowStatus = wi.status;
                break;
            }
        }
    }

    /** Simpler aggregates for fixed-minimum mode (no interval windows). */
    private void applyFixedMinimumAggregates(JimReportSummary s) {
        s.windowLedger.clear();
        s.satisfiedWindowCount = s.validCount;
        s.missedWindowCount = s.missedCount;
        s.todayValidCount = s.validCount;
        s.todayRequiredCount = s.requiredSoFar;
        s.todayMissedCount = s.missedCount;
    }

    private static boolean isSameDay(long a, long b, TimeZone tz) {
        return startOfDay(a, tz) == startOfDay(b, tz);
    }

    /** Deterministic window recompute for the API/export (full history from the summary). */
    @Override
    public List<JimReportSummary.WindowInfo> computeWindows(Issue issue) {
        JimReportSummary s = computeSummary(issue);
        return new ArrayList<JimReportSummary.WindowInfo>(s.windowLedger);
    }

    @Override
    public void recalculate(Issue issue, ApplicationUser actor) {
        recalculateWithOutcome(issue, actor, "direct");
    }

    @Override
    public JimReportRecalcOutcome recalculateWithOutcome(Issue issue, ApplicationUser actor, String reason) {
        JimReportRecalcOutcome out = new JimReportRecalcOutcome();
        out.issueKey = issue != null ? issue.getKey() : null;
        // Recalc whenever the feature is on (mapped-metric writes can apply even
        // when the schedule is unconfigured, e.g. totalRawReportCount).
        if (issue == null || !JimReportConfig.isFeatureEnabled()) {
            out.skipped = true;
            out.skipReason = "disabled";
            return out;
        }
        try {
            // In-memory input fingerprint gate: skip the heavy compute + persist
            // (and the full-changelog Done scan) when nothing that affects the
            // report has changed since the last successful calc for this issue.
            String fp = computeFingerprint(issue);
            String prev = lastFingerprint.get(issue.getId());
            if (prev == null && ledgerService != null) {
                // In-memory miss (e.g. after restart) → fall back to the persisted
                // fingerprint so a burst of events after restart does not force a
                // recompute storm across every issue.
                try {
                    prev = ledgerService.storedFingerprint(issue.getId());
                    if (prev != null) {
                        lastFingerprint.put(issue.getId(), prev);
                    }
                } catch (Throwable ignore) {
                }
            }
            if (fp != null && fp.equals(prev)) {
                out.skipped = true;
                out.skipReason = "fingerprint_unchanged";
                return out;
            }
            JimReportSummary s = computeSummary(issue); // computes + persists (loop-free)
            out.valuesChanged = s.valuesChanged;
            out.fieldsWritten = s.fieldsWritten;
            out.dispatched = s.dispatched;
            out.statusCode = s.statusCode;
            if (fp != null) {
                lastFingerprint.put(issue.getId(), fp);
            }
            return out;
        } catch (Throwable ex) {
            out.skipped = true;
            out.skipReason = "error";
            log.warn("event=report stage=recalc outcome=error issueKey={} message={}",
                    issue.getKey(), ex.getMessage());
            return out;
        }
    }

    /**
     * Cheap in-memory input fingerprint: report-comment count + max report comment
     * id + level code + due-date millis + status-category key + config marker/
     * window/mapping signature. When this is unchanged the report output cannot
     * have changed, so the calc is safely skipped. Returns null on failure (never
     * gate on an uncertain fingerprint).
     */
    private String computeFingerprint(Issue issue) {
        try {
            long count = 0L;
            long maxId = 0L;
            CommentManager cm = ComponentAccessor.getCommentManager();
            List<Comment> all = cm != null ? cm.getComments(issue) : null;
            if (all != null) {
                for (Comment c : all) {
                    if (c != null && isReportComment(c.getBody())) {
                        count++;
                        Long id = c.getId();
                        if (id != null && id > maxId) {
                            maxId = id;
                        }
                    }
                }
            }
            String level = JimReportConfig.levelCodeOf(issue);
            Long dueMs = issue.getDueDate() != null ? issue.getDueDate().getTime() : null;
            String statusCat = null;
            try {
                if (issue.getStatus() != null && issue.getStatus().getStatusCategory() != null) {
                    statusCat = issue.getStatus().getStatusCategory().getKey();
                }
            } catch (Throwable ignore) {
            }
            // Per-level counting semantics (so a countMode/interval/activeUntil change
            // invalidates the cached ledger — including the reportCountMode_high fix).
            String countMode = level != null ? JimReportConfig.countModeForLevel(level) : null;
            String activeUntil = level != null ? JimReportConfig.activeUntilForLevel(level) : null;
            JimReportConfig.ReportSchedule sch = level != null ? JimReportConfig.scheduleForLevel(level) : null;
            String schedSig = sch != null
                    ? (sch.intervalMinutes + "/" + sch.windowStartMinute + "-" + sch.windowEndMinute
                        + "/wd" + sch.workingDayInterval) : "none";
            // Include the fixed-minimum count so a minCount change for the issue's
            // (dynamic) level invalidates the cached ledger (Phase 8A).
            int minCount = level != null ? JimReportConfig.minCountForLevel(level) : 0;
            schedSig = schedSig + "/min" + minCount;
            String cfgSig = JimReportConfig.marker()
                    + "|" + JimReportConfig.globalWindowStartMinute()
                    + "|" + JimReportConfig.globalWindowEndMinute()
                    + "|" + JimReportConfig.allowWithoutConfig()
                    + "|" + JimReportConfig.fieldMappings().hashCode()
                    // Enforcement (report-scope) policy: a change to enabled/start-date/
                    // date-basis/non-done/missing-due must invalidate the cached ledger.
                    + "|enf:" + JimReportConfig.applicabilityEnabled()
                    + "|" + JimReportConfig.effectiveFromDate()
                    + "|" + JimReportConfig.applicabilityDateMode()
                    + "|" + JimReportConfig.applicabilityNonDoneOnly()
                    + "|" + JimReportConfig.applicabilityMissingDuePolicy();
            // Working-day source + report-receiver/assignee state so a working-week,
            // holiday, receiver or assignment change recomputes (pause/resume).
            String tzId = null;
            try {
                tzId = jiraTimeZone().getID();
            } catch (Throwable ignore) {
            }
            String semanticSig = countMode + "|" + activeUntil + "|" + schedSig + "|" + tzId
                    + "|" + ReportWorkingCalendarService.fingerprintSignature(issue)
                    + "|" + ReportResponsibilityService.fingerprintSignature(issue);
            return count + ":" + maxId + ":" + level + ":" + dueMs + ":" + statusCat
                    + ":" + cfgSig.hashCode() + ":" + semanticSig.hashCode();
        } catch (Throwable t) {
            return null;
        }
    }

    /** First/last report + latest reporter + comment id + short summary (config-independent). */
    private void computeReportMetadata(JimReportSummary s, List<Comment> reportComments) {
        Comment latest = null;
        for (Comment c : reportComments) {
            long ms = c.getCreated() != null ? c.getCreated().getTime() : 0L;
            if (s.firstReportMs == null || ms < s.firstReportMs) {
                s.firstReportMs = ms;
            }
            if (s.lastReportMs == null || ms >= s.lastReportMs) {
                s.lastReportMs = ms;
                latest = c;
            }
        }
        if (latest != null) {
            s.lastReportCommentId = latest.getId();
            if (latest.getAuthorApplicationUser() != null) {
                s.lastReporterKey = latest.getAuthorApplicationUser().getKey();
                s.lastReporterName = latest.getAuthorApplicationUser().getDisplayName();
            }
            s.lastReportSummary = shortSummary(latest.getBody());
        }
    }

    /** Latest VALID report metadata (requires classification, configured path). */
    private void computeValidReporterMetadata(JimReportSummary s, List<Comment> reportComments) {
        long best = -1L;
        for (int i = 0; i < s.items.size() && i < reportComments.size(); i++) {
            if (s.items.get(i).status != JimReportCalculator.STATUS_VALID) {
                continue;
            }
            Comment c = reportComments.get(i);
            long ms = c.getCreated() != null ? c.getCreated().getTime() : 0L;
            if (ms >= best) {
                best = ms;
                s.lastValidReportMs = ms;
                if (c.getAuthorApplicationUser() != null) {
                    s.lastValidReporterKey = c.getAuthorApplicationUser().getKey();
                    s.lastValidReporterName = c.getAuthorApplicationUser().getDisplayName();
                }
            }
        }
    }

    private String shortSummary(String body) {
        if (body == null) {
            return null;
        }
        String marker = JimReportConfig.marker();
        String cleaned = body.replace(marker, "").trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > 140 ? cleaned.substring(0, 140) + "\u2026" : cleaned;
    }

    /** Rich uppercase status for the generated metric + UI. */
    private String computeStatusCode(JimReportSummary s, boolean isDoneNow) {
        if (!s.featureEnabled) {
            return "NOT_CONFIGURED";
        }
        if (s.levelCode == null) {
            return "NO_FOLLOW_UP_LEVEL";
        }
        if (!s.configured) {
            return "NOT_CONFIGURED";
        }
        if (s.requiredCount > 0 && s.validCount >= s.requiredCount) {
            return "COMPLETE";
        }
        if (!s.submitActive) {
            if ("done".equals(s.inactiveReason)) {
                return "ENDED_DONE";
            }
            if ("due_passed".equals(s.inactiveReason)) {
                return "ENDED_DUE_DATE";
            }
            return "INACTIVE";
        }
        return "overdue".equals(s.compliance) ? "OVERDUE" : "ON_TRACK";
    }

    // --- Fixed-minimum mode ---------------------------------------------------

    /**
     * "At least N reports until the lifecycle end." No interval buckets: required
     * = the configured minimum; valid = report comments inside the reporting
     * window and at/before the endpoint (capped at required for compliance).
     * Out-of-window reports stay raw-only. Not-yet-met is on-track while still
     * active; {@code missedCount} stays 0 until the endpoint has passed.
     */
    private void computeFixedMinimum(JimReportSummary s, TimeZone tz) {
        // Fully out of enforcement scope → no scoring obligation (history still shown).
        boolean fullyOutOfScope = s.scoringPeriodStartMs == Long.MAX_VALUE;
        boolean hasBound = s.scoringPeriodStartMs > 0L && !fullyOutOfScope;
        int required = fullyOutOfScope ? 0 : JimReportConfig.minCountForLevel(s.levelCode);
        Long endpoint = s.reportingEndsMs; // due/done/earlier, or null = ongoing
        long now = System.currentTimeMillis();
        int validInWindow = 0;
        int outOfScope = 0;
        for (JimReportSummary.Item it : s.items) {
            long ts = it.createdMs;
            // Reports before the enforcement scoring start (or on a fully out-of-scope
            // issue) are unscored history — never valid, missed or out-of-window.
            if (fullyOutOfScope || (hasBound && ts < s.scoringPeriodStartMs)) {
                it.status = JimReportCalculator.STATUS_OUT_OF_SCOPE;
                outOfScope++;
                continue;
            }
            int mod = minuteOfDay(ts, tz);
            boolean inHours = mod >= s.windowStartMinute && mod < s.windowEndMinute;
            boolean beforeEnd = endpoint == null || ts <= endpoint;
            if (inHours && beforeEnd) {
                it.status = JimReportCalculator.STATUS_VALID;
                validInWindow++;
            } else {
                it.status = JimReportCalculator.STATUS_OUT_OF_WINDOW;
            }
        }
        s.outOfScopeReportCount = outOfScope;
        s.requiredCount = required;
        s.validCount = Math.min(validInWindow, required);
        s.duplicateCount = 0; // interval-duplicate logic does not apply
        s.outOfWindowCount = Math.max(0, s.rawCount - validInWindow - outOfScope);
        boolean met = s.validCount >= required;
        boolean ended = endpoint != null && now > endpoint;
        // Obligation is "N by the end". Until the due/done endpoint, nothing
        // has expired — not-yet-submitted is ON_TRACK, not missed.
        // Do not use (minimumRequired - valid) while the window is still open.
        s.requiredSoFar = (ended || met) ? required : 0;
        s.validSoFar = s.validCount;
        s.missedCount = (ended && !met) ? Math.max(required - s.validCount, 0) : 0;
        s.compliance = met ? "on_track" : (ended ? "overdue" : "on_track");
        s.nextExpectedMs = met ? null : endpoint;
    }

    private int minuteOfDay(long ts, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz != null ? tz : TimeZone.getDefault());
        c.setTimeInMillis(ts);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    // --- Counting period + lifecycle -----------------------------------------

    /** Cumulative counting endpoint from count-mode; null = ongoing (up to now). Ignored for daily. */
    private Long countPeriodEnd(JimReportSummary s, boolean isDoneNow) {
        if ("daily".equals(s.countMode)) {
            return null;
        }
        switch (s.countMode) {
            case "cumulative_due":
                return s.dueEndMs; // may be null when due missing → ongoing to now (dueMissing flagged)
            case "cumulative_done":
                return isDoneNow ? s.doneMs : null;
            case "cumulative_earlier":
                return earlier(s.dueEndMs, isDoneNow ? s.doneMs : null);
            default:
                return null;
        }
    }

    /** Whether Submit Report is still active, plus the reason when it is not. */
    private void computeSubmitActive(JimReportSummary s, boolean isDoneNow, TimeZone tz, boolean configured) {
        if (!configured) {
            // No follow-up level / fields: submit allowed only when the admin
            // opted in (report is still a plain #گزارش comment, never counted).
            s.submitActive = s.featureEnabled && s.allowWithoutConfig;
            s.inactiveReason = s.submitActive ? "allowed_without_config"
                    : (s.levelCode == null ? "no_follow_up_level" : "not_configured");
            return;
        }
        long now = System.currentTimeMillis();
        String au = s.activeUntil != null ? s.activeUntil : "earlier";
        boolean doneStops = "done".equals(au) || "earlier".equals(au);
        boolean dueStops = "due".equals(au) || "earlier".equals(au);
        // Effective reporting end (for the reportingEndsAt metric).
        Long endMs = null;
        if ("due".equals(au)) {
            endMs = s.dueEndMs;
        } else if ("done".equals(au)) {
            endMs = isDoneNow ? s.doneMs : null;
        } else {
            endMs = earlier(s.dueEndMs, isDoneNow ? s.doneMs : null);
        }
        s.reportingEndsMs = endMs;
        if (doneStops && isDoneNow) {
            s.submitActive = false;
            s.inactiveReason = "done";
            return;
        }
        if (dueStops && s.dueEndMs != null && now > s.dueEndMs) {
            s.submitActive = false;
            s.inactiveReason = "due_passed";
            return;
        }
        if (dueStops && s.dueMissing && !"earlier".equals(au)) {
            // "Until Due Date" but no due date set → can't determine end; keep
            // active but surface the missing-due-date state to the UI.
            s.submitActive = true;
            s.inactiveReason = "due_missing";
            return;
        }
        s.submitActive = true;
        s.inactiveReason = null;
    }

    // --- Persistence (safe, loop-free) ---------------------------------------

    /**
     * Persist the business count fields (when configured) plus any admin-mapped
     * generated-metric fields, in a single change-guarded update. Every field is
     * compared to its current value first, so a recompute that yields identical
     * values writes nothing and cannot loop the issue-updated event it fires.
     */
    private void persistMetrics(Issue issue, JimReportSummary s, ApplicationUser actor) {
        s.valuesChanged = 0;
        s.fieldsWritten = 0;
        s.dispatched = false;
        if (!s.featureEnabled) {
            return;
        }
        try {
            TimeZone tz = jiraTimeZone();
            MutableIssue mutable = ComponentAccessor.getIssueManager().getIssueObject(issue.getId());
            if (mutable == null) {
                return;
            }
            int changed = 0;

            // Business valid/required fields — only when fully configured (never
            // write misleading valid/required when the schedule can't be resolved).
            if (s.configured && JimReportConfig.isOperational()) {
                CustomField validCf = JimReportConfig.resolveValidCountField();
                CustomField requiredCf = JimReportConfig.resolveRequiredCountField();
                if (validCf != null && !eq(asDouble(validCf.getValue(issue)), (double) s.validCount)) {
                    mutable.setCustomFieldValue(validCf, (double) s.validCount);
                    changed++;
                }
                if (requiredCf != null && !eq(asDouble(requiredCf.getValue(issue)), (double) s.requiredCount)) {
                    mutable.setCustomFieldValue(requiredCf, (double) s.requiredCount);
                    changed++;
                }
            }

            // Optional admin-mapped generated metrics.
            Map<String, String> mappings = JimReportConfig.fieldMappings();
            CustomField updatedAtCf = null;
            for (Map.Entry<String, String> e : mappings.entrySet()) {
                String metricKey = e.getKey();
                String fieldId = e.getValue();
                if (fieldId == null || fieldId.isEmpty()) {
                    continue;
                }
                JimReportMetrics.Def def = JimReportMetrics.byKey(metricKey);
                if (def == null) {
                    continue;
                }
                if ("reportMetricsUpdatedAt".equals(metricKey)) {
                    updatedAtCf = resolveField(fieldId); // written last, only if other changes
                    continue;
                }
                if (!s.configured && !def.safeUnconfigured) {
                    continue; // schedule-derived metric while unconfigured → skip
                }
                CustomField cf = resolveField(fieldId);
                if (cf == null || cf.getCustomFieldType() == null) {
                    continue; // field missing on this env → skip (logged sparingly)
                }
                String typeKey = cf.getCustomFieldType().getKey();
                if (!JimReportMetrics.isCompatible(def.category, typeKey)) {
                    log.warn("event=report stage=metric outcome=skip reason=incompatible metric={} field={}",
                            metricKey, fieldId);
                    continue;
                }
                Object raw = JimReportMetrics.valueFor(metricKey, s);
                if (raw == null) {
                    continue; // no value → do not clear existing data
                }
                if (writeMetric(mutable, issue, cf, def.category, typeKey, raw, tz)) {
                    changed++;
                }
            }

            int fieldsWritten = changed;
            if (changed > 0 && updatedAtCf != null) {
                mutable.setCustomFieldValue(updatedAtCf, new java.sql.Timestamp(System.currentTimeMillis()));
                fieldsWritten++;
            }
            s.valuesChanged = changed;
            s.fieldsWritten = fieldsWritten;
            if (changed == 0) {
                return; // nothing to write → no event → no loop
            }
            ApplicationUser updater = actor != null ? actor
                    : ComponentAccessor.getUserManager().getUserByName("admin");
            if (updater == null && issue.getReporter() != null) {
                updater = issue.getReporter();
            }
            // Phase 1 hardening: internal metric write must NOT dispatch an
            // ISSUE_UPDATED event. That event fired the third-party Time-to-SLA
            // cascade and re-entered our own listener (a redundant ~60s recalc).
            // We write with DO_NOT_DISPATCH and instead reindex the issue
            // explicitly so JQL/gadgets stay current. A thread-local guard marks
            // the write in progress as belt-and-suspenders against re-entry.
            WRITE_IN_PROGRESS.set(Boolean.TRUE);
            Issue updated;
            try {
                updated = ComponentAccessor.getIssueManager()
                        .updateIssue(updater, mutable, EventDispatchOption.DO_NOT_DISPATCH, false);
            } finally {
                WRITE_IN_PROGRESS.remove();
            }
            s.dispatched = false;
            reIndexQuietly(updated != null ? updated : mutable);
            log.info("event=report stage=persist outcome=updated issueKey={} status={} valid={} required={} raw={} valuesChanged={} fieldsWritten={} dispatched=false reindexed=true",
                    new Object[]{issue.getKey(), s.statusCode, s.validCount, s.requiredCount, s.rawCount,
                            s.valuesChanged, s.fieldsWritten});
        } catch (Throwable ex) {
            log.warn("event=report stage=persist outcome=error issueKey={} message={}",
                    issue.getKey(), ex.getMessage());
        }
    }

    /**
     * Best-effort explicit reindex after a DO_NOT_DISPATCH write, so search /
     * gadgets reflect the new metric values without firing an ISSUE_UPDATED
     * event. Never throws to the caller (indexing is not critical to the write).
     */
    private void reIndexQuietly(Issue issue) {
        if (issue == null) {
            return;
        }
        try {
            com.atlassian.jira.issue.index.IssueIndexingService idx =
                    ComponentAccessor.getComponent(com.atlassian.jira.issue.index.IssueIndexingService.class);
            if (idx != null) {
                idx.reIndex(issue);
            }
        } catch (Throwable ex) {
            log.debug("event=report stage=reindex outcome=error issueKey={} message={}",
                    issue.getKey(), ex.getMessage());
        }
    }

    private CustomField resolveField(String fieldId) {
        try {
            return ComponentAccessor.getCustomFieldManager().getCustomFieldObject(fieldId);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Coerce + write a metric value to a field only when it differs. Returns true when written. */
    private boolean writeMetric(MutableIssue mutable, Issue issue, CustomField cf,
                                JimReportMetrics.Category cat, String typeKey, Object raw, TimeZone tz) {
        String tk = typeKey == null ? "" : typeKey.toLowerCase();
        switch (cat) {
            case NUMBER: {
                double v = ((Number) raw).doubleValue();
                if (eq(asDouble(cf.getValue(issue)), v)) {
                    return false;
                }
                mutable.setCustomFieldValue(cf, v);
                return true;
            }
            case DATE: {
                long ms = ((Number) raw).longValue();
                boolean dateTime = tk.contains("datetime");
                long target = dateTime ? ms : startOfDay(ms, tz);
                Object cur = cf.getValue(issue);
                long curMs = cur instanceof java.util.Date ? ((java.util.Date) cur).getTime() : Long.MIN_VALUE;
                long curCmp = (cur instanceof java.util.Date && !dateTime) ? startOfDay(curMs, tz) : curMs;
                if (curCmp == target) {
                    return false;
                }
                mutable.setCustomFieldValue(cf, new java.sql.Timestamp(target));
                return true;
            }
            case USER: {
                if (tk.contains("userpicker")) {
                    ApplicationUser u = ComponentAccessor.getUserManager().getUserByKey(String.valueOf(raw));
                    Object cur = cf.getValue(issue);
                    String curKey = cur instanceof ApplicationUser ? ((ApplicationUser) cur).getKey() : null;
                    String newKey = u != null ? u.getKey() : null;
                    if (eqStr(curKey, newKey)) {
                        return false;
                    }
                    mutable.setCustomFieldValue(cf, u);
                    return true;
                }
                // text target → write display name
                ApplicationUser u = ComponentAccessor.getUserManager().getUserByKey(String.valueOf(raw));
                String name = u != null ? u.getDisplayName() : String.valueOf(raw);
                return writeText(mutable, issue, cf, tk, name);
            }
            case TEXT:
            default:
                return writeText(mutable, issue, cf, tk, String.valueOf(raw));
        }
    }

    private boolean writeText(MutableIssue mutable, Issue issue, CustomField cf, String tk, String value) {
        if (tk.contains("select") || tk.contains("radiobuttons")) {
            // Best-effort option resolution; skip (no write) if the option is absent.
            try {
                com.atlassian.jira.issue.customfields.manager.OptionsManager om =
                        ComponentAccessor.getComponent(com.atlassian.jira.issue.customfields.manager.OptionsManager.class);
                com.atlassian.jira.issue.fields.config.FieldConfig fc = cf.getRelevantConfig(issue);
                com.atlassian.jira.issue.customfields.option.Options opts = om.getOptions(fc);
                com.atlassian.jira.issue.customfields.option.Option match = opts != null ? opts.getOptionForValue(value, null) : null;
                if (match == null) {
                    log.warn("event=report stage=metric outcome=skip reason=option_absent field={} value={}", cf.getId(), value);
                    return false;
                }
                Object cur = cf.getValue(issue);
                String curVal = cur instanceof com.atlassian.jira.issue.customfields.option.Option
                        ? ((com.atlassian.jira.issue.customfields.option.Option) cur).getValue() : null;
                if (eqStr(curVal, value)) {
                    return false;
                }
                mutable.setCustomFieldValue(cf, match);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
        Object cur = cf.getValue(issue);
        String curStr = cur != null ? String.valueOf(cur) : null;
        if (eqStr(curStr, value)) {
            return false;
        }
        mutable.setCustomFieldValue(cf, value);
        return true;
    }

    private static long startOfDay(long ms, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static boolean eqStr(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    // --- Helpers --------------------------------------------------------------

    private List<Comment> collectReportComments(Issue issue) {
        List<Comment> out = new ArrayList<Comment>();
        try {
            CommentManager cm = ComponentAccessor.getCommentManager();
            List<Comment> all = cm.getComments(issue);
            if (all != null) {
                for (Comment c : all) {
                    if (c != null && isReportComment(c.getBody())) {
                        out.add(c);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private String rawLevelText(Issue issue) {
        CustomField cf = JimReportConfig.resolveLevelField();
        if (cf == null) {
            return null;
        }
        try {
            Object v = issue.getCustomFieldValue(cf);
            if (v == null) {
                return null;
            }
            if (v instanceof com.atlassian.jira.issue.customfields.option.Option) {
                return ((com.atlassian.jira.issue.customfields.option.Option) v).getValue();
            }
            return String.valueOf(v);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isInDoneCategory(Issue issue) {
        try {
            Status st = issue.getStatus();
            if (st == null) {
                return false;
            }
            StatusCategory cat = st.getStatusCategory();
            return cat != null && StatusCategory.COMPLETE.equals(cat.getKey());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Done-transition time with an in-memory cache so a burst of events on an
     * already-Done issue does not repeat the full-changelog scan every time. The
     * Done time is stable once reached; the cache is dropped in
     * {@link #computeSummary} when the issue leaves the Done category.
     */
    private Long resolveDoneTimeCached(Issue issue) {
        Long id = issue.getId();
        if (id != null) {
            Long cached = doneMsCache.get(id);
            if (cached != null) {
                return cached;
            }
        }
        Long resolved = resolveDoneTime(issue);
        if (id != null && resolved != null) {
            doneMsCache.put(id, resolved);
        }
        return resolved;
    }

    /** Best-effort Done transition time from the changelog; null if unavailable. */
    private Long resolveDoneTime(Issue issue) {
        try {
            List<com.atlassian.jira.issue.changehistory.ChangeHistory> histories =
                    ComponentAccessor.getChangeHistoryManager().getChangeHistories(issue);
            if (histories == null) {
                return null;
            }
            Long doneMs = null;
            for (com.atlassian.jira.issue.changehistory.ChangeHistory h : histories) {
                if (h.getTimePerformed() == null) {
                    continue;
                }
                for (ChangeItemBean item : h.getChangeItemBeans()) {
                    if (!"status".equalsIgnoreCase(item.getField())) {
                        continue;
                    }
                    String toId = item.getTo();
                    if (toId == null) {
                        continue;
                    }
                    Status toStatus = ComponentAccessor.getConstantsManager().getStatus(toId);
                    if (toStatus != null && toStatus.getStatusCategory() != null
                            && StatusCategory.COMPLETE.equals(toStatus.getStatusCategory().getKey())) {
                        doneMs = h.getTimePerformed().getTime(); // keep the latest transition into done
                    }
                }
            }
            return doneMs;
        } catch (Throwable t) {
            return null; // history not available here → caller falls back
        }
    }

    /**
     * End-of-due-day as epoch millis, or null when no due date.
     *
     * <p>Due date is a date-only field. Jira anchors/reads such fields in the
     * SYSTEM default timezone (see {@link com.corbitlogic.jira.internalmessenger.mobile.JimMobileDates#toYmd}),
     * so we derive the calendar day the same way and take end-of-day in that same
     * timezone. Interpreting the raw Timestamp in the Jira "default user" timezone
     * instead shifted the day by ±1 (and by a stale-DST hour), which wrongly
     * reported {@code due_passed} and closed report submission.</p>
     */
    /**
     * Effective reporting-deadline date, in strict precedence:
     * <ol>
     *   <li>system Due Date;</li>
     *   <li>Approved Completion Date custom field (the approval workflow writes
     *       the approved date here);</li>
     *   <li>Proposed Completion Date custom field (fallback, pre-approval).</li>
     * </ol>
     * First Report Time is NEVER used as the end. Created date is not an end
     * either. Returns null only when none of the three are set. This keeps the
     * deadline correct even when the approval flow populated only the approved-
     * date custom field and not the system Due.
     */
    private java.util.Date resolveEffectiveDueDate(Issue issue) {
        if (issue == null) {
            return null;
        }
        java.sql.Timestamp due = issue.getDueDate();
        if (due != null) {
            return due;
        }
        try {
            java.util.Date approved =
                    com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalConfig.getApprovedDate(issue);
            if (approved != null) {
                return approved;
            }
            java.util.Date proposed =
                    com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalConfig.getProposedDate(issue);
            if (proposed != null) {
                return proposed;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private Long endOfDueDay(Issue issue, TimeZone tz) {
        java.util.Date due = resolveEffectiveDueDate(issue);
        if (due == null) {
            return null;
        }
        String ymd = com.corbitlogic.jira.internalmessenger.mobile.JimMobileDates.toYmd(
                due instanceof java.sql.Timestamp ? (java.sql.Timestamp) due
                        : new java.sql.Timestamp(due.getTime()));
        if (ymd == null) {
            return null;
        }
        try {
            String[] p = ymd.split("-");
            // Day derived via the system tz (how Jira stores date fields); the
            // wall-clock end-of-day is built in the Jira app tz so it matches the
            // reporting window + "now" reasoning (23:59:59 on the due day).
            Calendar c = Calendar.getInstance(tz != null ? tz : TimeZone.getDefault());
            c.clear();
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 23, 59, 59);
            c.set(Calendar.MILLISECOND, 999);
            return c.getTimeInMillis();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Effective timezone for reporting-window classification. Must match the tz
     * the report time is DISPLAYED in to the user, so a report at 07:50 shown to
     * a Tehran user is validated as 07:50 (not shifted into another tz). Priority
     * per spec: the viewer's Jira user timezone → Jira default → JVM default.
     *
     * <p>Bug fixed: previously this used the Jira DEFAULT timezone (Asia/Shanghai
     * on this instance's image) while users are Asia/Tehran, so a 07:50 Tehran
     * report became 12:20 Shanghai and was wrongly counted inside the window.</p>
     */
    private TimeZone jiraTimeZone() {
        // Admin override wins (fixed GMT offset bypasses stale JVM DST data).
        TimeZone override = JimReportConfig.reportTimeZone();
        if (override != null) {
            return override;
        }
        try {
            TimeZoneManager mgr = ComponentAccessor.getComponent(TimeZoneManager.class);
            if (mgr != null) {
                try {
                    TimeZone viewer = mgr.getLoggedInUserTimeZone();
                    if (viewer != null) {
                        return viewer;
                    }
                } catch (Throwable ignore) {
                }
                if (mgr.getDefaultTimezone() != null) {
                    return mgr.getDefaultTimezone();
                }
            }
        } catch (Throwable ignored) {
        }
        return TimeZone.getDefault();
    }

    private static Long earlier(Long a, Long b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Math.min(a, b);
    }

    private static Double asDouble(Object v) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        return null;
    }

    private static boolean eq(Double current, double next) {
        return current != null && Math.abs(current - next) < 0.0001;
    }
}
