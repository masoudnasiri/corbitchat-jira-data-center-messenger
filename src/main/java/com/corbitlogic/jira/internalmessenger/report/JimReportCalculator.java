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

    public static final String COMPLIANCE_ON_TRACK = "on_track";
    public static final String COMPLIANCE_OVERDUE = "overdue";

    private JimReportCalculator() {
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
    }

    public static final class Result {
        public int requiredCount;
        public int validCount;
        public int rawCount;
        public int outOfWindowCount;
        public int duplicateCount;
        public int missedCount;
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
    }

    public static Result compute(Input in) {
        Result r = new Result();
        TimeZone tz = in.tz != null ? in.tz : TimeZone.getTimeZone("UTC");
        r.rawCount = in.reports != null ? in.reports.length : 0;
        r.reportStatus = new int[r.rawCount];
        // Everything defaults to out-of-window until mapped to a bucket.
        Arrays.fill(r.reportStatus, STATUS_OUT_OF_WINDOW);

        if (in.intervalMinutes <= 0 || in.windowEndMin <= in.windowStartMin) {
            r.outOfWindowCount = r.rawCount;
            return r; // schedule not usable → everything raw only
        }

        long genEnd;
        long firstDayAnchor;
        if (in.daily) {
            // Current active day only.
            firstDayAnchor = in.nowMs;
            genEnd = endOfDayWindow(in.nowMs, in.windowEndMin, tz);
        } else {
            firstDayAnchor = in.issueCreatedMs;
            genEnd = in.periodEndMs != null ? in.periodEndMs : in.nowMs;
        }

        List<Bucket> buckets = buildBuckets(in, tz, firstDayAnchor, genEnd);

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
                r.reportStatus[idx] = STATUS_OUT_OF_WINDOW;
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
            if (b.start <= in.nowMs) {
                elapsed++;
                if (b.filled) {
                    validElapsed++;
                }
            } else if (!b.filled && nextExpected == null) {
                nextExpected = b.start;
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
            }
        }
        return r;
    }

    private static List<Bucket> buildBuckets(Input in, TimeZone tz, long firstDayAnchor, long genEnd) {
        List<Bucket> buckets = new ArrayList<>();
        long creationDayStart = startOfDay(in.issueCreatedMs, tz);
        long cursorDay = in.daily ? startOfDay(in.nowMs, tz) : creationDayStart;
        long lastDay = startOfDay(genEnd, tz);
        int guard = 0;
        while (cursorDay <= lastDay && guard++ < 4000) {
            long dayWindowStart = atMinuteOfDay(cursorDay, in.windowStartMin, tz);
            long dayWindowEnd = atMinuteOfDay(cursorDay, in.windowEndMin, tz);
            long segStart = dayWindowStart;
            // On the creation day, don't count buckets before the issue existed.
            if (sameDay(cursorDay, in.issueCreatedMs, tz)) {
                segStart = Math.max(dayWindowStart, in.issueCreatedMs);
            }
            long segEnd = dayWindowEnd;
            if (!in.daily && genEnd < segEnd) {
                segEnd = genEnd; // cap the final day at the period end
            }
            long intervalMs = (long) in.intervalMinutes * 60000L;
            for (long t = segStart; t < segEnd; t += intervalMs) {
                buckets.add(new Bucket(t, Math.min(t + intervalMs, dayWindowEnd)));
            }
            cursorDay = nextDayStart(cursorDay, tz);
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
