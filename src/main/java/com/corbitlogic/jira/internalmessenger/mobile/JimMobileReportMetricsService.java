package com.corbitlogic.jira.internalmessenger.mobile;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.query.Query;
import com.corbitlogic.jira.internalmessenger.report.JimReportCalculator;
import com.corbitlogic.jira.internalmessenger.report.JimReportConfig;
import com.corbitlogic.jira.internalmessenger.report.JimReportService;
import com.corbitlogic.jira.internalmessenger.report.JimReportSummary;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single report-metrics source for Dashboard and the Reports section.
 *
 * <p>One JQL, one scan cap, one {@link JimReportService#computeSummary} loop.
 * Period math reuses the engine window ledger (same SATISFIED/MISSED/OPEN
 * rules as {@code today*}). Does not change submit/reply or calendar rules.</p>
 */
public class JimMobileReportMetricsService {

    private static final Logger log = LoggerFactory.getLogger(JimMobileReportMetricsService.class);

    /** Shared scan cap — Dashboard and Reports must use the same bound. */
    public static final int SCAN_CAP = 25;

    private final SearchService searchService;
    private final JimReportService reportService;

    public JimMobileReportMetricsService(SearchService searchService,
                                         JimReportService reportService) {
        this.searchService = searchService;
        this.reportService = reportService;
    }

    public Snapshot snapshot(ApplicationUser viewer, String preset, String fromYmd, String toYmd) {
        Period period = resolvePeriod(viewer, preset, fromYmd, toYmd);
        Snapshot out = new Snapshot();
        out.period = period;
        out.metrics.source = "report-engine";
        out.metrics.available = false;
        if (viewer == null || !JimReportConfig.isOperational()) {
            return out;
        }
        try {
            SearchService.ParseResult pr = this.searchService.parseQuery(viewer, assignedReportJql());
            if (pr == null || !pr.isValid()) {
                return out;
            }
            Query q = pr.getQuery();
            PagerFilter<Issue> pager = new PagerFilter<>(SCAN_CAP);
            SearchResults<Issue> results = this.searchService.search(viewer, q, pager);
            if (results == null || results.getResults() == null) {
                return out;
            }
            out.scanned = results.getResults().size();
            out.issueTotal = results.getTotal();
            out.metrics.capped = out.issueTotal > SCAN_CAP;

            int complete = 0;
            int issueOnTrack = 0;
            int issueOverdue = 0;
            int todayComplete = 0;
            int todayOnTrack = 0;
            int todayOverdue = 0;

            for (Issue issue : results.getResults()) {
                JimReportSummary s = this.reportService.computeSummary(issue);
                if (s == null || !s.configured || s.levelCode == null || !s.applicable) {
                    continue;
                }
                Row row = countsFor(s, period);
                out.configuredCount++;
                out.metrics.totalRequired += row.required;
                out.metrics.valid += row.valid;
                out.metrics.missed += row.missed;
                out.metrics.overdue += row.overdue;
                out.metrics.outOfWindow += row.outOfWindow;
                out.metrics.rawTotal += row.raw;
                out.rows.add(new IssueRow(issue, s, row));

                String code = s.statusCode == null ? "" : s.statusCode;
                if ("COMPLETE".equals(code)) {
                    complete++;
                } else if ("OVERDUE".equals(code)) {
                    issueOverdue++;
                } else if ("ON_TRACK".equals(code)) {
                    issueOnTrack++;
                }
                if (s.todayMissedCount > 0) {
                    todayOverdue++;
                } else if (s.todayRequiredCount > 0
                        && s.todayValidCount >= s.todayRequiredCount) {
                    todayComplete++;
                } else if (s.reportedToday && s.todayRequiredCount <= 0) {
                    todayComplete++;
                } else if (s.submitActive && s.todayRequiredCount > s.todayValidCount) {
                    todayOnTrack++;
                }
            }
            out.legacyComplete = complete;
            out.legacyOnTrack = issueOnTrack;
            out.legacyOverdue = issueOverdue;
            out.legacyTodayComplete = todayComplete;
            out.legacyTodayOnTrack = todayOnTrack;
            out.legacyTodayOverdue = todayOverdue;
            out.metrics.onTrack = Math.max(0,
                    out.metrics.totalRequired - out.metrics.valid
                            - out.metrics.missed - out.metrics.overdue);
            out.metrics.available = out.configuredCount > 0;
            return out;
        } catch (Exception ex) {
            log.warn("stage=reportMetrics outcome=degraded message={}", ex.getMessage());
            return out;
        }
    }

    public Map<String, Object> periodMap(Period period) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("preset", period.preset);
        m.put("from", period.fromYmd);
        m.put("to", period.toYmd);
        m.put("timezone", period.timezoneId);
        return m;
    }

    public Map<String, Object> metricsMap(Snapshot snap) {
        Metrics x = snap.metrics;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", x.available);
        m.put("totalRequired", x.totalRequired);
        m.put("valid", x.valid);
        m.put("missed", x.missed);
        m.put("overdue", x.overdue);
        m.put("onTrack", x.onTrack);
        m.put("outOfWindow", x.outOfWindow);
        m.put("rawTotal", x.rawTotal);
        m.put("capped", x.capped);
        m.put("source", x.source);
        return m;
    }

    /**
     * Dashboard {@code reportSummary}: shared metrics plus same-scan legacy
     * issue-status fields so older clients do not invent task totals.
     */
    public Map<String, Object> dashboardSummary(Snapshot snap) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "report-engine");
        out.put("available", snap.metrics.available);
        out.put("scanned", snap.scanned);
        out.put("configuredCount", snap.configuredCount);
        out.put("issueTotal", snap.issueTotal);
        out.put("capped", snap.metrics.capped);
        out.put("period", periodMap(snap.period));
        out.put("reportMetrics", metricsMap(snap));
        out.put("items", itemMaps(snap));
        if (snap.metrics.available) {
            out.put("complete", snap.legacyComplete);
            out.put("onTrack", snap.legacyOnTrack);
            out.put("overdue", snap.legacyOverdue);
            out.put("todayComplete", snap.legacyTodayComplete);
            out.put("todayOnTrack", snap.legacyTodayOnTrack);
            out.put("todayOverdue", snap.legacyTodayOverdue);
            out.put("validSubmitted", snap.metrics.valid);
            out.put("missed", snap.metrics.missed);
        }
        return out;
    }

    public List<Map<String, Object>> itemMaps(Snapshot snap) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (IssueRow row : snap.rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("issueKey", row.issue.getKey());
            m.put("summary", com.corbitlogic.jira.internalmessenger.util.JimSanitizer
                    .sanitizeText(row.issue.getSummary()));
            m.put("validCount", row.counts.valid);
            m.put("missedCount", row.counts.missed);
            m.put("rawCount", row.counts.raw);
            m.put("outOfWindowCount", row.counts.outOfWindow);
            m.put("requiredCount", row.counts.required);
            m.put("overdueCount", row.counts.overdue);
            items.add(m);
        }
        return items;
    }

    public static String assignedReportJql() {
        String level = JimReportConfig.levelFieldJql();
        StringBuilder sb = new StringBuilder(
                "assignee = currentUser() AND resolution is EMPTY");
        if (level != null) {
            sb.append(" AND ").append(level).append(" is not EMPTY");
        }
        sb.append(" ORDER BY updated DESC");
        return sb.toString();
    }

    private static Row countsFor(JimReportSummary s, Period period) {
        Row r = new Row();
        if ("all".equals(period.preset)) {
            r.valid = Math.max(0, s.validCount);
            r.missed = expiredMissed(s, s.missedCount);
            r.required = Math.max(0, s.requiredSoFar);
            r.overdue = Math.max(0, s.overdueOpenCount);
            r.outOfWindow = Math.max(0, s.outOfWindowCount);
            r.raw = Math.max(0, s.rawCount);
            return r;
        }
        if ("today".equals(period.preset)) {
            r.valid = Math.max(0, s.todayValidCount);
            r.missed = expiredMissed(s, s.todayMissedCount);
            r.required = Math.max(0, s.todayRequiredCount);
            r.overdue = Math.max(0, openWindowsInRange(s, period.fromMs, period.toExclusiveMs));
            countItemsInRange(s, period.fromMs, period.toExclusiveMs, r);
            return r;
        }
        // week / month / custom — same ledger classification as today*, different range
        if (s.windowLedger == null || s.windowLedger.isEmpty()) {
            return r;
        }
        for (JimReportSummary.WindowInfo w : s.windowLedger) {
            if (w.windowStart < period.fromMs || w.windowStart >= period.toExclusiveMs) {
                continue;
            }
            if ("FUTURE".equals(w.status) || "CLOSED_NOT_REQUIRED".equals(w.status)
                    || "EXEMPT".equals(w.status)) {
                continue;
            }
            r.required++;
            if ("SATISFIED".equals(w.status) || "LATE".equals(w.status)) {
                r.valid++;
            } else if ("MISSED".equals(w.status)) {
                r.missed++;
            } else if ("OPEN".equals(w.status)) {
                r.overdue++;
            }
        }
        countItemsInRange(s, period.fromMs, period.toExclusiveMs, r);
        return r;
    }

    /**
     * Missed = expired unfilled obligation only. For {@code fixed_minimum},
     * the Report tab stays ON_TRACK until due/done; do not treat
     * (minimum − valid) as missed while submit is still active.
     */
    private static int expiredMissed(JimReportSummary s, int candidate) {
        if (s != null && "fixed_minimum".equals(s.countMode)
                && s.submitActive
                && !"OVERDUE".equals(s.statusCode)) {
            return 0;
        }
        return Math.max(0, candidate);
    }

    private static int openWindowsInRange(JimReportSummary s, long fromMs, long toEx) {
        if (s.windowLedger == null) {
            return 0;
        }
        int n = 0;
        for (JimReportSummary.WindowInfo w : s.windowLedger) {
            if (w.windowStart >= fromMs && w.windowStart < toEx && "OPEN".equals(w.status)) {
                n++;
            }
        }
        return n;
    }

    private static void countItemsInRange(JimReportSummary s, long fromMs, long toEx, Row r) {
        if (s.items == null) {
            return;
        }
        int raw = 0;
        int oow = 0;
        for (JimReportSummary.Item it : s.items) {
            if (it.createdMs < fromMs || it.createdMs >= toEx) {
                continue;
            }
            if (it.status == JimReportCalculator.STATUS_OUT_OF_WINDOW) {
                oow++;
            } else if (it.status == JimReportCalculator.STATUS_VALID
                    || it.status == JimReportCalculator.STATUS_DUPLICATE) {
                raw++;
            }
        }
        r.raw = raw;
        r.outOfWindow = oow;
    }

    static Period resolvePeriod(ApplicationUser viewer, String preset, String fromYmd, String toYmd) {
        TimeZone tz = JimMobileDates.userTimeZone(viewer);
        String p = preset == null ? "all" : preset.trim().toLowerCase();
        if ("day".equals(p)) {
            p = "today";
        }
        Period out = new Period();
        out.timezoneId = tz.getID();
        Calendar today = startOfDay(tz);
        String todayYmd = ymd(today, tz);
        if (fromYmd != null && !fromYmd.trim().isEmpty()
                && toYmd != null && !toYmd.trim().isEmpty()
                && ("custom".equals(p) || parseYmd(fromYmd.trim(), tz) != null)) {
            Long from = parseYmd(fromYmd.trim(), tz);
            Long to = parseYmd(toYmd.trim(), tz);
            if (from != null && to != null && from <= to) {
                out.preset = "custom".equals(p) ? "custom" : p;
                if (!"today".equals(p) && !"week".equals(p) && !"month".equals(p) && !"all".equals(p)) {
                    out.preset = "custom";
                }
                if ("custom".equals(p)) {
                    out.fromMs = from;
                    out.toExclusiveMs = to + 24L * 60 * 60 * 1000L;
                    out.fromYmd = fromYmd.trim();
                    out.toYmd = toYmd.trim();
                    return out;
                }
            }
        }
        if ("today".equals(p)) {
            out.preset = "today";
            out.fromMs = today.getTimeInMillis();
            out.toExclusiveMs = out.fromMs + 24L * 60 * 60 * 1000L;
            out.fromYmd = todayYmd;
            out.toYmd = todayYmd;
            return out;
        }
        if ("week".equals(p)) {
            Calendar from = startOfDay(tz);
            from.add(Calendar.DATE, -6);
            out.preset = "week";
            out.fromMs = from.getTimeInMillis();
            out.toExclusiveMs = today.getTimeInMillis() + 24L * 60 * 60 * 1000L;
            out.fromYmd = ymd(from, tz);
            out.toYmd = todayYmd;
            return out;
        }
        if ("month".equals(p)) {
            Calendar from = startOfDay(tz);
            from.set(Calendar.DAY_OF_MONTH, 1);
            out.preset = "month";
            out.fromMs = from.getTimeInMillis();
            out.toExclusiveMs = today.getTimeInMillis() + 24L * 60 * 60 * 1000L;
            out.fromYmd = ymd(from, tz);
            out.toYmd = todayYmd;
            return out;
        }
        out.preset = "all";
        out.fromYmd = todayYmd;
        out.toYmd = todayYmd;
        out.fromMs = 0L;
        out.toExclusiveMs = Long.MAX_VALUE;
        return out;
    }

    private static Calendar startOfDay(TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    private static String ymd(Calendar cal, TimeZone tz) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        fmt.setTimeZone(tz);
        return fmt.format(cal.getTime());
    }

    private static Long parseYmd(String ymd, TimeZone tz) {
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
            fmt.setTimeZone(tz);
            fmt.setLenient(false);
            return fmt.parse(ymd).getTime();
        } catch (ParseException ex) {
            return null;
        }
    }

    public static final class Period {
        public String preset = "all";
        public String fromYmd;
        public String toYmd;
        public String timezoneId;
        public long fromMs;
        public long toExclusiveMs;
    }

    public static final class Metrics {
        public boolean available;
        public int totalRequired;
        public int valid;
        public int missed;
        public int overdue;
        public int onTrack;
        public int outOfWindow;
        public int rawTotal;
        public boolean capped;
        public String source = "report-engine";
    }

    public static final class Row {
        public int valid;
        public int missed;
        public int required;
        public int overdue;
        public int outOfWindow;
        public int raw;
    }

    public static final class IssueRow {
        public final Issue issue;
        public final JimReportSummary summary;
        public final Row counts;

        IssueRow(Issue issue, JimReportSummary summary, Row counts) {
            this.issue = issue;
            this.summary = summary;
            this.counts = counts;
        }
    }

    public static final class Snapshot {
        public Period period = new Period();
        public final Metrics metrics = new Metrics();
        public final List<IssueRow> rows = new ArrayList<>();
        public int scanned;
        public int issueTotal;
        public int configuredCount;
        public int legacyComplete;
        public int legacyOnTrack;
        public int legacyOverdue;
        public int legacyTodayComplete;
        public int legacyTodayOnTrack;
        public int legacyTodayOverdue;
    }
}
