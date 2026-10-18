package com.corbitlogic.jira.internalmessenger.report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Pure report-compliance bucket calculator (Sprint 12 — Phase B).
 *
 * <p>Deliberately has <b>no Jira dependency</b> so the algorithm can be unit
 * tested / run standalone. All reasoning is on raw epoch-millis + a
 * {@link TimeZone}; never on localized/Jalali display text.</p>
 *
 * <h3>Model</h3>
 * <ul>
 *   <li>An interval "bucket" is a slot of {@code intervalMinutes} inside a day's
 *       reporting window {@code [windowStart, windowEnd)} (minute-of-day).</li>
 *   <li>The first report whose timestamp falls inside a bucket makes that bucket
 *       <b>valid</b>. Additional reports in the same bucket are <b>duplicates</b>
 *       (counted in the raw total only). Reports in no bucket (before window
 *       start, after window end, outside the active period) are
 *       <b>out-of-window</b> (raw only).</li>
 *   <li>On the issue's creation day the first bucket starts at
 *       {@code max(windowStart, creation)}; if the issue was created after the
 *       window end that day contributes no buckets.</li>
 *   <li><b>Daily</b> mode counts only the current day's window (of {@code now}).
 *       <b>Cumulative</b> modes count every active day from creation to the
 *       period end ({@code periodEndMs}; {@code null} = ongoing → up to now).</li>
 *   <li>{@code requiredCount} = buckets in the period (to {@code periodEndMs},
 *       which may be a future due date). Compliance/overdue uses only buckets
 *       whose start is at/before {@code now} ("elapsed").</li>
 * </ul>
 */
public final class JimReportCalculator {

    public static final int STATUS_VALID = 0;
    public static final int STATUS_DUPLICATE = 1;
    public static final int STATUS_OUT_OF_WINDOW = 2;
    /**
     * Report submitted on a non-working day or during a paused (assigned-to-report-receiver)
     * interval. Shown in raw history as "extra / out-of-schedule" but never creates a required
     * bucket and never counts as valid/missed. (Report Replies &amp; Working-Days feature.)
     */
    public static final int STATUS_OUT_OF_SCHEDULE = 3;
    /**
     * Report submitted before the enforcement scoring start date (or on an issue that is
     * out of enforcement scope). Shown in raw history as "out of scope / unscored" but
     * never creates a required bucket and never counts as valid/missed/out-of-window.
     * (Report Enforcement Start Date feature.)
     */
    public static final int STATUS_OUT_OF_SCOPE = 4;

    public static final String COMPLIANCE_ON_TRACK = "on_track";
    public static final String COMPLIANCE_OVERDUE = "overdue";

    private JimReportCalculator() {
    }

    /**
     * Classifies a day (given its start-of-day millis in the report timezone) as a Jira
     * working day. Pure — the resolution of the real Jira source lives in
     * {@code ReportWorkingCalendarService}. When {@code null} every day is a working day
     * (fully back-compatible with the pre-working-days engine).
     */
    public interface DayClassifier {
        boolean isWorkingDay(long dayStartMs);
    }

    public static final class Input {
        public long issueCreatedMs;
        public int intervalMinutes;
        public int windowStartMin;
        public int windowEndMin;
        public boolean daily;
        /** Cumulative period end (due/done/earlier). {@code null} = ongoing (use now). Ignored for daily. */
        public Long periodEndMs;
        public long nowMs;
        public TimeZone tz;
        public long[] reports = new long[0];
        /**
         * Working-day gate. When set, days classified non-working generate no buckets, so
         * they never increase required/missed/streaks. {@code null} = every day works.
         */
        public DayClassifier dayClassifier;
        /**
         * Half-open [start,end) intervals during which report counting is paused because the
         * issue is assigned to the report receiver / team lead. Buckets whose start falls in
         * any interval are not generated (no required/missed) and resume automatically after.
         */
        public long[][] pausedIntervals = new long[0][];
        /**
         * Enforcement scoring lower bound (epoch ms). Required/missed buckets are only
         * generated at/after this instant, so nothing before it is required or missed.
         * Reports before it are classified {@link #STATUS_OUT_OF_SCOPE} (visible history,
         * never scored). {@code 0} = no bound. {@code Long.MAX_VALUE} = fully out of scope
         * (zero scored buckets). Composes with the working-day + pause gates.
         */
        public long scoredPeriodStartMs;
        /**
         * Phase 8B working-day interval (unit=days). When {@code > 0}, ONE required bucket is
         * generated per {@code workingDayInterval} WORKING days (non-working days/holidays do
         * not advance the counter), instead of intra-day {@code intervalMinutes} buckets. The
         * working-day set comes from {@link #dayClassifier}. {@code 0} = disabled (minutes/hours).
         */
        public int workingDayInterval;
    }

    public static final class Result {
        public int requiredCount;
        public int validCount;
        public int rawCount;
        public int outOfWindowCount;
        public int duplicateCount;
        public int missedCount;
        /** Reports that fell on a non-working day or in a paused interval (raw only, out-of-schedule). */
        public int outOfScheduleCount;
        /** Reports before the enforcement scoring start / out of scope (raw only, unscored). */
        public int outOfScopeCount;
        /** Buckets whose start is at/before now (required "so far"). */
        public int requiredSoFar;
        /** Valid buckets whose start is at/before now (valid "so far"). */
        public int validSoFar;
        public Long nextExpectedMs;
        public String compliance = COMPLIANCE_ON_TRACK;
        /** Per-input-report classification, parallel to {@link Input#reports}. */
        public int[] reportStatus = new int[0];
        /** Deterministic per-window ledger detail (Phase 3), ordered by windowStart. */
        public List<WindowDetail> windows = new ArrayList<WindowDetail>();
    }

    /**
     * Deterministic detail for a single window/bucket (Phase 3). Ordered by
     * {@link #start}. Used both to materialize the bounded {@code JimReportWindow}
     * ledger and to recompute the full window history for the API/export.
     */
    public static final class WindowDetail {
        public long start;
        public long end;
        public boolean filled;
        public int validCount;
        public int rawCount;
        public int duplicateCount;
        public Long firstValidMs;
        public Long lastReportMs;
        /** Indexes into {@link Input#reports} whose timestamps fell in this window. */
        public final List<Integer> reportIndexes = new ArrayList<Integer>();
    }

    private static final class Bucket {
        final long start;
        final long end;
        /**
         * Point from which this bucket counts toward requiredSoFar/missed. Null = use
         * {@link #start} (legacy intra-day buckets). For Phase 8B working-day-interval
         * buckets this is the interval COMPLETION (end of the Nth working day), so the
         * report is required only after N working days elapse — while {@code [start,end)}
         * still spans the whole interval so an early report is valid.
         */
        Long requiredSinceMs;
        boolean filled;
        int rawCount;
        int duplicateCount;
        Long firstValidMs;
        Long lastReportMs;
        final List<Integer> reportIndexes = new ArrayList<Integer>();

        Bucket(long start, long end) {
            this.start = start;
            this.end = end;
        }

        long requiredSince() {
            return requiredSinceMs != null ? requiredSinceMs.longValue() : start;
        }
    }

    public static Result compute(Input in) {
        Result r = new Result();
        TimeZone tz = in.tz != null ? in.tz : TimeZone.getTimeZone("UTC");
        r.rawCount = in.reports != null ? in.reports.length : 0;
        r.reportStatus = new int[r.rawCount];
        // Everything defaults to out-of-window until mapped to a bucket.
        Arrays.fill(r.reportStatus, STATUS_OUT_OF_WINDOW);

        boolean workingDayMode = in.workingDayInterval > 0;
        if ((in.intervalMinutes <= 0 && !workingDayMode) || in.windowEndMin <= in.windowStartMin) {
            r.outOfWindowCount = r.rawCount;
            return r; // schedule not usable → everything raw only
        }

        long genEnd;
        long firstDayAnchor;
        if (in.daily && !workingDayMode) {
            // Current active day only.
            firstDayAnchor = in.nowMs;
            genEnd = endOfDayWindow(in.nowMs, in.windowEndMin, tz);
        } else {
            // Cumulative (and working-day-interval): from creation to the period end / now.
            firstDayAnchor = in.issueCreatedMs;
            genEnd = in.periodEndMs != null ? in.periodEndMs : in.nowMs;
        }

        List<Bucket> buckets = workingDayMode
                ? buildWorkingDayBuckets(in, tz, genEnd)
                : buildBuckets(in, tz, firstDayAnchor, genEnd);

        // Classify reports into buckets (sorted by time so the earliest report in
        // a bucket is the valid one; later ones are duplicates).
        long[] reports = in.reports != null ? in.reports.clone() : new long[0];
        Integer[] order = new Integer[reports.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Long.compare(reports[a], reports[b]));
        for (int idx : order) {
            long ts = reports[idx];
            Bucket b = findBucket(buckets, ts);
            if (b == null) {
                // No required bucket. Distinguish so the UI can label it correctly; none of
                // these ever count toward valid/missed.
                if (in.scoredPeriodStartMs > 0L && ts < in.scoredPeriodStartMs) {
                    // Before the enforcement scoring start (or fully out of scope): unscored
                    // history, NOT out-of-window and NOT invalid.
                    r.reportStatus[idx] = STATUS_OUT_OF_SCOPE;
                } else {
                    boolean offSchedule = isPaused(ts, in.pausedIntervals)
                            || (in.dayClassifier != null && !in.dayClassifier.isWorkingDay(startOfDay(ts, tz)));
                    r.reportStatus[idx] = offSchedule ? STATUS_OUT_OF_SCHEDULE : STATUS_OUT_OF_WINDOW;
                }
            } else {
                b.rawCount++;
                b.reportIndexes.add(idx);
                if (b.lastReportMs == null || ts > b.lastReportMs) {
                    b.lastReportMs = ts;
                }
                if (!b.filled) {
                    b.filled = true;
                    b.firstValidMs = ts;
                    r.reportStatus[idx] = STATUS_VALID;
                } else {
                    b.duplicateCount++;
                    r.reportStatus[idx] = STATUS_DUPLICATE;
                }
            }
        }

        int elapsed = 0;
        int validElapsed = 0;
        Long nextExpected = null;
        for (Bucket b : buckets) {
            if (b.filled) {
                r.validCount++;
            }
            // requiredSince() == start for legacy buckets; == interval completion for
            // Phase 8B working-day buckets (required only after N working days elapse).
            if (b.requiredSince() <= in.nowMs) {
                elapsed++;
                if (b.filled) {
                    validElapsed++;
                }
            } else if (!b.filled && nextExpected == null) {
                nextExpected = b.requiredSince();
            }
        }
        r.requiredCount = buckets.size();
        r.requiredSoFar = elapsed;
        r.validSoFar = validElapsed;
        r.missedCount = Math.max(0, elapsed - validElapsed);
        r.nextExpectedMs = nextExpected;
        r.compliance = r.missedCount == 0 ? COMPLIANCE_ON_TRACK : COMPLIANCE_OVERDUE;

        for (Bucket b : buckets) {
            WindowDetail w = new WindowDetail();
            w.start = b.start;
            w.end = b.end;
            w.filled = b.filled;
            w.validCount = b.filled ? 1 : 0;
            w.rawCount = b.rawCount;
            w.duplicateCount = b.duplicateCount;
            w.firstValidMs = b.firstValidMs;
            w.lastReportMs = b.lastReportMs;
            w.reportIndexes.addAll(b.reportIndexes);
            r.windows.add(w);
        }

        for (int s : r.reportStatus) {
            if (s == STATUS_DUPLICATE) {
                r.duplicateCount++;
            } else if (s == STATUS_OUT_OF_WINDOW) {
                r.outOfWindowCount++;
            } else if (s == STATUS_OUT_OF_SCHEDULE) {
                r.outOfScheduleCount++;
            } else if (s == STATUS_OUT_OF_SCOPE) {
                r.outOfScopeCount++;
            }
        }
        return r;
    }

    /** True when {@code ts} falls inside any half-open [start,end) paused interval. */
    private static boolean isPaused(long ts, long[][] pausedIntervals) {
        if (pausedIntervals == null) {
            return false;
        }
        for (long[] iv : pausedIntervals) {
            if (iv != null && iv.length == 2 && ts >= iv[0] && ts < iv[1]) {
                return true;
            }
        }
        return false;
    }

    private static List<Bucket> buildBuckets(Input in, TimeZone tz, long firstDayAnchor, long genEnd) {
        List<Bucket> buckets = new ArrayList<>();
        // Fully out of enforcement scope → zero scored buckets (all reports become
        // unscored OUT_OF_SCOPE history; requiredCount/missedCount stay 0).
        if (in.scoredPeriodStartMs == Long.MAX_VALUE) {
            return buckets;
        }
        long creationDayStart = startOfDay(in.issueCreatedMs, tz);
        long cursorDay = in.daily ? startOfDay(in.nowMs, tz) : creationDayStart;
        // Enforcement scoring lower bound: advance the first scored day so no required
        // bucket is generated before the enforcement start (out-of-scope issues use
        // Long.MAX_VALUE → zero scored buckets). Composes with the per-bucket guard below.
        if (!in.daily && in.scoredPeriodStartMs > 0L && in.scoredPeriodStartMs != Long.MAX_VALUE) {
            long boundDay = startOfDay(in.scoredPeriodStartMs, tz);
            if (boundDay > cursorDay) {
                cursorDay = boundDay;
            }
        }
        long lastDay = startOfDay(genEnd, tz);
        int guard = 0;
        while (cursorDay <= lastDay && guard++ < 4000) {
            // Working-day gate: non-working days generate no required buckets, so they never
            // increase required/missed/streak counters (Jira working-days rule).
            if (in.dayClassifier != null && !in.dayClassifier.isWorkingDay(cursorDay)) {
                cursorDay = nextDayStart(cursorDay, tz);
                continue;
            }
            long dayWindowStart = atMinuteOfDay(cursorDay, in.windowStartMin, tz);
            long dayWindowEnd = atMinuteOfDay(cursorDay, in.windowEndMin, tz);
            long segStart = dayWindowStart;
            // On the creation day, don't count buckets before the issue existed.
            if (sameDay(cursorDay, in.issueCreatedMs, tz)) {
                segStart = Math.max(dayWindowStart, in.issueCreatedMs);
            }
            // Enforcement scoring lower bound on the first scored day.
            if (in.scoredPeriodStartMs > 0L && in.scoredPeriodStartMs != Long.MAX_VALUE
                    && sameDay(cursorDay, in.scoredPeriodStartMs, tz)) {
                segStart = Math.max(segStart, in.scoredPeriodStartMs);
            }
            long segEnd = dayWindowEnd;
            if (!in.daily && genEnd < segEnd) {
                segEnd = genEnd; // cap the final day at the period end
            }
            long intervalMs = (long) in.intervalMinutes * 60000L;
            for (long t = segStart; t < segEnd; t += intervalMs) {
                // Enforcement scoring lower bound (covers daily mode + future start dates).
                if (in.scoredPeriodStartMs > 0L && t < in.scoredPeriodStartMs) {
                    continue;
                }
                // Pause gate: skip buckets whose start falls in an assigned-to-receiver interval.
                if (isPaused(t, in.pausedIntervals)) {
                    continue;
                }
                buckets.add(new Bucket(t, Math.min(t + intervalMs, dayWindowEnd)));
            }
            cursorDay = nextDayStart(cursorDay, tz);
        }
        return buckets;
    }

    /**
     * Phase 8B: one required bucket per {@code workingDayInterval} WORKING days.
     * Walks working days (skipping non-working days/holidays via {@link Input#dayClassifier}),
     * groups them into N‑working‑day intervals, and emits one bucket per interval spanning
     * {@code [firstWorkingDayWindowStart, NthWorkingDayWindowEnd)} with
     * {@code requiredSinceMs = completion} — so a report anywhere in the interval is valid, but
     * the bucket is required (counts toward requiredSoFar/missed) only after N working days
     * elapse. Non-working days never advance the counter.
     */
    private static List<Bucket> buildWorkingDayBuckets(Input in, TimeZone tz, long genEnd) {
        List<Bucket> buckets = new ArrayList<>();
        int n = in.workingDayInterval;
        if (n <= 0 || in.scoredPeriodStartMs == Long.MAX_VALUE) {
            return buckets;
        }
        long cursorDay = startOfDay(in.issueCreatedMs, tz);
        if (in.scoredPeriodStartMs > 0L && in.scoredPeriodStartMs != Long.MAX_VALUE) {
            long boundDay = startOfDay(in.scoredPeriodStartMs, tz);
            if (boundDay > cursorDay) {
                cursorDay = boundDay;
            }
        }
        long lastDay = startOfDay(genEnd, tz);
        List<long[]> workDays = new ArrayList<>(); // each: {segStart, windowEnd}
        int guard = 0;
        // Phase 1: collect working days up to genEnd (adjusted for creation / enforcement start).
        while (cursorDay <= lastDay && guard++ < 8000) {
            if (in.dayClassifier != null && !in.dayClassifier.isWorkingDay(cursorDay)) {
                cursorDay = nextDayStart(cursorDay, tz);
                continue;
            }
            long ws = atMinuteOfDay(cursorDay, in.windowStartMin, tz);
            long we = atMinuteOfDay(cursorDay, in.windowEndMin, tz);
            long segStart = ws;
            if (sameDay(cursorDay, in.issueCreatedMs, tz)) {
                segStart = Math.max(ws, in.issueCreatedMs);
            }
            if (in.scoredPeriodStartMs > 0L && in.scoredPeriodStartMs != Long.MAX_VALUE
                    && sameDay(cursorDay, in.scoredPeriodStartMs, tz)) {
                segStart = Math.max(segStart, in.scoredPeriodStartMs);
            }
            workDays.add(new long[]{segStart, we});
            cursorDay = nextDayStart(cursorDay, tz);
        }
        // Phase 2: project forward to COMPLETE the final in-progress interval so it has a real
        // completion instant (may be in the future → required only once it passes).
        while (workDays.size() % n != 0 && guard++ < 8000) {
            if (in.dayClassifier == null || in.dayClassifier.isWorkingDay(cursorDay)) {
                long ws = atMinuteOfDay(cursorDay, in.windowStartMin, tz);
                long we = atMinuteOfDay(cursorDay, in.windowEndMin, tz);
                workDays.add(new long[]{ws, we});
            }
            cursorDay = nextDayStart(cursorDay, tz);
        }
        // Group into intervals of N working days → one bucket each.
        for (int i = 0; i + n <= workDays.size(); i += n) {
            long bStart = workDays.get(i)[0];
            long completion = workDays.get(i + n - 1)[1];
            if (isPaused(bStart, in.pausedIntervals)) {
                continue; // assigned-to-receiver pause: no required/missed for this interval
            }
            Bucket b = new Bucket(bStart, completion);
            b.requiredSinceMs = Long.valueOf(completion);
            buckets.add(b);
        }
        return buckets;
    }

    private static Bucket findBucket(List<Bucket> buckets, long ts) {
        for (Bucket b : buckets) {
            if (ts >= b.start && ts < b.end) {
                return b;
            }
        }
        return null;
    }

    // --- Calendar helpers (timezone-aware; no display formatting) -------------

    private static long startOfDay(long ms, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long atMinuteOfDay(long dayStartMs, int minuteOfDay, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(dayStartMs);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.MINUTE, minuteOfDay);
        return c.getTimeInMillis();
    }

    private static long nextDayStart(long dayStartMs, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(dayStartMs);
        c.add(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long endOfDayWindow(long ms, int windowEndMin, TimeZone tz) {
        return atMinuteOfDay(startOfDay(ms, tz), windowEndMin, tz);
    }

    private static boolean sameDay(long dayStartMs, long other, TimeZone tz) {
        return startOfDay(dayStartMs, tz) == startOfDay(other, tz);
    }
}
