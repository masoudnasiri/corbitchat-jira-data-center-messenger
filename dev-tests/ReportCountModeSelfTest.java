import com.corbitlogic.jira.internalmessenger.report.JimReportCalculator;
import com.corbitlogic.jira.internalmessenger.report.JimReportCalculator.Input;
import com.corbitlogic.jira.internalmessenger.report.JimReportCalculator.Result;
import java.util.TimeZone;

/**
 * Pure-logic verification of the report count-mode fix (high: daily -> cumulative_due).
 * Uses the REAL JimReportCalculator (no Jira dependency) with the exact PM-68/PM-74
 * production inputs. Not part of the Maven build; compiled + run standalone.
 */
public class ReportCountModeSelfTest {

    static final TimeZone TZ = TimeZone.getTimeZone("GMT+03:30"); // reportTimezone (prod)

    // now = 2026-07-29 20:00 Tehran (a representative "today evening" on the due day of PM-68)
    static final long NOW = 1785339000000L; // 2026-07-29T20:00:00+03:30

    // Production values (from /report + issue REST):
    static final long PM68_CREATED = 1782389891000L; // 2026-06-25 15:48 Tehran
    static final long PM68_DUE_EOD = 1785356999999L; // 2026-07-29 23:59:59.999 Tehran
    static final long[] PM68 = {
        1784123882000L,1784124386000L,1784370216000L,1784447088000L,1784647380000L,
        1784733185000L,1784980851000L,1785082122000L,1785231452000L,1785331274000L };

    static final long PM74_CREATED = 1782389893000L; // 2026-06-25 15:48 Tehran
    static final long PM74_DUE_EOD = 1786566599999L; // 2026-08-12 23:59:59.999 Tehran
    static final long[] PM74 = {
        1784124556000L,1784363585000L,1784553979000L,1784726311000L,1784980775000L,
        1785081564000L,1785246875000L,1785331260000L };

    static Result run(boolean daily, long created, Long periodEnd, long[] reports) {
        Input in = new Input();
        in.tz = TZ;
        in.issueCreatedMs = created;
        in.intervalMinutes = 1440;   // 24h
        in.windowStartMin = 540;     // 09:00
        in.windowEndMin = 1260;      // 21:00
        in.nowMs = NOW;
        in.daily = daily;
        in.periodEndMs = daily ? null : periodEnd; // cumulative_due => due EOD
        in.reports = reports;
        return JimReportCalculator.compute(in);
    }

    static int count(int[] status, int want) {
        int n = 0; for (int s : status) if (s == want) n++; return n;
    }

    static void show(String name, boolean daily, long created, Long periodEnd, long[] reports) {
        Result r = run(daily, created, periodEnd, reports);
        int valid = count(r.reportStatus, JimReportCalculator.STATUS_VALID);
        int dup   = count(r.reportStatus, JimReportCalculator.STATUS_DUPLICATE);
        int oow   = count(r.reportStatus, JimReportCalculator.STATUS_OUT_OF_WINDOW);
        System.out.printf("%-8s mode=%-14s | windows=%-3d required=%-3d validCount=%-3d outOfWindow=%-3d duplicate=%-2d missed=%-3d requiredSoFar=%-3d validSoFar=%-3d compliance=%s%n",
            name, daily ? "daily" : "cumulative_due", r.windows.size(), r.requiredCount, r.validCount,
            oow, dup, r.missedCount, r.requiredSoFar, r.validSoFar, r.compliance);
        System.out.print("         per-report status (V/D/O): ");
        for (int s : r.reportStatus) System.out.print(s == 0 ? "V " : s == 1 ? "D " : "O ");
        System.out.println();
    }

    // ---- Working-days + pause + parser (Report Replies & Working-Days feature) ----

    static int failures = 0;

    static void check(String name, boolean ok) {
        System.out.printf("  [%s] %s%n", ok ? "PASS" : "FAIL", name);
        if (!ok) failures++;
    }

    /** Fri+Sat off working-day classifier in the report tz (Iran default fallback). */
    static JimReportCalculator.DayClassifier friSatOff() {
        return dayStart -> {
            java.util.Calendar c = java.util.Calendar.getInstance(TZ);
            c.setTimeInMillis(dayStart);
            int dow = c.get(java.util.Calendar.DAY_OF_WEEK);
            return dow != java.util.Calendar.FRIDAY && dow != java.util.Calendar.SATURDAY;
        };
    }

    static Result runFull(long created, Long periodEnd, long[] reports,
                          JimReportCalculator.DayClassifier dc, long[][] paused) {
        Input in = new Input();
        in.tz = TZ;
        in.issueCreatedMs = created;
        in.intervalMinutes = 1440;
        in.windowStartMin = 540;
        in.windowEndMin = 1260;
        in.nowMs = NOW;
        in.daily = false;
        in.periodEndMs = periodEnd;
        in.reports = reports;
        in.dayClassifier = dc;
        in.pausedIntervals = paused == null ? new long[0][] : paused;
        return JimReportCalculator.compute(in);
    }

    static int count(int[] status, int want, boolean b) { return count(status, want); }

    static void workingDayAndPauseTests() {
        System.out.println("== Working-days + pause ==");
        // Baseline: PM-68 cumulative_due over ALL calendar days.
        Result calendar = runFull(PM68_CREATED, PM68_DUE_EOD, PM68, null, null);
        // Same but only Sun-Thu are working (Fri+Sat off).
        Result working = runFull(PM68_CREATED, PM68_DUE_EOD, PM68, friSatOff(), null);
        System.out.printf("  calendar-day required=%d ; working-day required=%d%n",
                calendar.requiredCount, working.requiredCount);
        check("working-day required < calendar-day required (Fri+Sat skipped)",
                working.requiredCount < calendar.requiredCount);
        // Non-working days never add missed windows.
        check("working-day missed <= calendar-day missed",
                working.missedCount <= calendar.missedCount);

        // A report landing on a non-working day is out-of-schedule, never valid/missed.
        // 2026-07-24 is a Friday in Tehran → craft a report at Fri 12:00.
        long friReport = friNoonMs();
        long[] withFri = java.util.Arrays.copyOf(PM68, PM68.length + 1);
        withFri[PM68.length] = friReport;
        Result r = runFull(PM68_CREATED, PM68_DUE_EOD, withFri, friSatOff(), null);
        int offSched = count(r.reportStatus, JimReportCalculator.STATUS_OUT_OF_SCHEDULE);
        check("report on non-working day classified OUT_OF_SCHEDULE", offSched >= 1);
        check("out-of-schedule report does not raise validCount vs no-extra",
                r.validCount == working.validCount);

        // Pause: pause the whole period → zero required windows (assigned to receiver).
        long[][] pauseAll = {{PM68_CREATED, PM68_DUE_EOD}};
        Result paused = runFull(PM68_CREATED, PM68_DUE_EOD, PM68, friSatOff(), pauseAll);
        check("fully-paused period generates zero required windows", paused.requiredCount == 0);
        check("fully-paused period generates zero missed windows", paused.missedCount == 0);
    }

    static long friNoonMs() {
        java.util.Calendar c = java.util.Calendar.getInstance(TZ);
        c.set(2026, java.util.Calendar.JULY, 24, 12, 0, 0); // 2026-07-24 is a Friday
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** Replicates isReportSubmissionBody without ComponentAccessor (pure). */
    static boolean isReport(String body) {
        String marker = com.corbitlogic.jira.internalmessenger.report.JimReportConfig
                .normalizePersian("#\u06af\u0632\u0627\u0631\u0634");
        String outside = com.corbitlogic.jira.internalmessenger.report.JimReportConfig
                .normalizePersian(com.corbitlogic.jira.internalmessenger.report.JimReportConfig
                        .stripQuotedSegments(body));
        return outside.contains(marker);
    }

    static void parserTests() {
        System.out.println("== Parser (report vs reply/quote) ==");
        String marker = "#\u06af\u0632\u0627\u0631\u0634"; // #گزارش
        check("original report (marker outside quote) counts",
                isReport(marker + "\n\nProgress today: done 3 tasks."));
        check("reply quoting a report (marker only inside quote) NOT counted",
                !isReport("[~m.nasiri]\n\n{quote}" + marker + "\nyesterday report{quote}\n\nThanks, looks good."));
        check("ordinary comment quoting a report NOT counted",
                !isReport("{quote}" + marker + " old report{quote}\n\nplease update the estimate"));
        check("ordinary comment (no marker) NOT counted",
                !isReport("Just a normal comment with no marker."));
        check("reply that ALSO writes the marker in its own free text counts",
                isReport("[~x]\n\n{quote}old{quote}\n\n" + marker + " here is my new report"));
    }

    public static void main(String[] args) {
        System.out.println("== PM-68 (due today 2026-07-29) ==");
        show("PM-68", true,  PM68_CREATED, PM68_DUE_EOD, PM68);   // current prod
        show("PM-68", false, PM68_CREATED, PM68_DUE_EOD, PM68);   // fixed
        System.out.println();
        System.out.println("== PM-74 (due future 2026-08-12) ==");
        show("PM-74", true,  PM74_CREATED, PM74_DUE_EOD, PM74);   // current prod
        show("PM-74", false, PM74_CREATED, PM74_DUE_EOD, PM74);   // fixed
        System.out.println();

        // PM-68/PM-74 non-regression: cumulative_due valid counts unchanged (no working-day gate).
        Result p68 = run(false, PM68_CREATED, PM68_DUE_EOD, PM68);
        // 10 report comments = 9 valid + 1 duplicate (same-bucket) — unchanged by this feature.
        check("PM-68 cumulative_due validCount == 9 + 1 dup (non-regression)",
                p68.validCount == 9 && p68.duplicateCount == 1);
        Result p68d = run(true, PM68_CREATED, PM68_DUE_EOD, PM68);
        check("PM-68 daily required == 1 (non-regression)", p68d.requiredCount == 1);
        System.out.println();

        workingDayAndPauseTests();
        System.out.println();
        parserTests();
        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : (failures + " CHECK(S) FAILED"));
        System.exit(failures == 0 ? 0 : 1);
    }
}
