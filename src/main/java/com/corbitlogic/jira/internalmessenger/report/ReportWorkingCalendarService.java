package com.corbitlogic.jira.internalmessenger.report;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.database.ConnectionFunction;
import com.atlassian.jira.database.DatabaseAccessor;
import com.atlassian.jira.database.DatabaseConnection;
import com.atlassian.jira.issue.Issue;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves which calendar days are <b>working days</b> for the report counter.
 *
 * <p>This is <b>scoring-only</b>: it affects requiredCount / missedCount / streaks /
 * overdue status by generating required buckets only on working days. It never blocks
 * report submission and never hides report history.</p>
 *
 * <p>Calendar sources (admin-selectable via {@code reportWorkingCalendarSource}):</p>
 * <ul>
 *   <li>{@code jira_board_first} — read the Jira Software board Working Days
 *       (weekdays + non-working dates + timezone) for the issue's resolved board;
 *       fall back to the plugin calendar, then time-tracking, if no board resolves;</li>
 *   <li>{@code plugin_calendar_only} — use only the plugin working weekdays + plugin
 *       holidays;</li>
 *   <li>{@code plugin_calendar_plus_board_holidays} — plugin weekdays, but holidays =
 *       plugin holidays ∪ the resolved board's non-working dates;</li>
 *   <li>{@code time_tracking_fallback} — legacy behavior: derive the weekday set from
 *       {@code TimeTrackingConfiguration.getDaysPerWeek()} (default when unset, so
 *       existing installs keep their exact behavior).</li>
 * </ul>
 *
 * <p>Board Working Days are read read-only from the bundled Jira Software
 * (GreenHopper) tables {@code AO_60DB71_WORKINGDAYS} / {@code AO_60DB71_NONWORKINGDAY}
 * / {@code AO_60DB71_RAPIDVIEW} via {@link DatabaseAccessor}. We never mutate board
 * settings. Plugin holidays ({@code YYYY-MM-DD}) are always additionally non-working.
 * All reasoning is on raw epoch-millis + the report {@link TimeZone}; never on Jalali
 * text.</p>
 */
public final class ReportWorkingCalendarService {

    /** Iran-priority order for choosing non-working weekdays: Friday, Saturday, Thursday, ... */
    private static final int[] WEEKEND_PRIORITY =
            {Calendar.FRIDAY, Calendar.SATURDAY, Calendar.THURSDAY,
             Calendar.SUNDAY, Calendar.WEDNESDAY, Calendar.TUESDAY, Calendar.MONDAY};

    static final String SRC_BOARD_FIRST = "jira_board_first";
    static final String SRC_PLUGIN_ONLY = "plugin_calendar_only";
    static final String SRC_PLUGIN_PLUS_BOARD_HOLIDAYS = "plugin_calendar_plus_board_holidays";
    static final String SRC_TIME_TRACKING = "time_tracking_fallback";

    /** Short cache for board reads (boardId -> (loadedAtMs, BoardCalendar)); avoids per-issue SQL storms. */
    private static final Map<Long, long[]> BOARD_CACHE_TS = new ConcurrentHashMap<Long, long[]>();
    private static final Map<Long, BoardCalendar> BOARD_CACHE = new ConcurrentHashMap<Long, BoardCalendar>();
    private static final long BOARD_CACHE_TTL_MS = 60000L;

    private ReportWorkingCalendarService() {
    }

    private static JimAdminSettingsService admin() {
        return ComponentAccessor.getOSGiComponentInstanceOfType(JimAdminSettingsService.class);
    }

    /** Master toggle: when false, every day is a working day (fully back-compatible). */
    public static boolean isEnabled() {
        JimAdminSettingsService a = admin();
        return a != null && a.isReportWorkingDaysEnabled();
    }

    /** Selected calendar source mode (admin setting), never null. */
    public static String selectedSource() {
        JimAdminSettingsService a = admin();
        String s = a != null ? a.getReportWorkingCalendarSource() : null;
        return normalizeSource(s);
    }

    static String normalizeSource(String s) {
        if (s == null) {
            return SRC_TIME_TRACKING;
        }
        String t = s.trim();
        if (SRC_BOARD_FIRST.equals(t) || SRC_PLUGIN_ONLY.equals(t)
                || SRC_PLUGIN_PLUS_BOARD_HOLIDAYS.equals(t) || SRC_TIME_TRACKING.equals(t)) {
            return t;
        }
        return SRC_TIME_TRACKING;
    }

    // ------------------------------------------------------------------ board calendar
    /** Raw board working-days config read from Jira Software (GreenHopper) tables. */
    static final class BoardCalendar {
        final long boardId;
        final String boardName;
        final Set<Integer> workingDaysOfWeek;   // Calendar.DAY_OF_WEEK ints
        final Set<String> nonWorkingDates;       // YYYY-MM-DD
        final String timezoneId;                 // raw GreenHopper value ("defaultTimeZoneId" or a zone id)
        final boolean found;

        BoardCalendar(long boardId, String boardName, Set<Integer> dows, Set<String> nwd,
                      String tz, boolean found) {
            this.boardId = boardId;
            this.boardName = boardName;
            this.workingDaysOfWeek = dows;
            this.nonWorkingDates = nwd;
            this.timezoneId = tz;
            this.found = found;
        }
    }

    /** Read a board's Working Days config (read-only) from the GreenHopper AO tables; cached briefly. */
    static BoardCalendar readBoard(long boardId) {
        if (boardId <= 0) {
            return null;
        }
        long now = System.currentTimeMillis();
        long[] ts = BOARD_CACHE_TS.get(Long.valueOf(boardId));
        if (ts != null && (now - ts[0]) < BOARD_CACHE_TTL_MS) {
            return BOARD_CACHE.get(Long.valueOf(boardId));
        }
        BoardCalendar bc = readBoardUncached(boardId);
        BOARD_CACHE.put(Long.valueOf(boardId), bc);
        BOARD_CACHE_TS.put(Long.valueOf(boardId), new long[]{now});
        return bc;
    }

    private static BoardCalendar readBoardUncached(final long boardId) {
        try {
            DatabaseAccessor dba = ComponentAccessor.getComponent(DatabaseAccessor.class);
            if (dba == null) {
                return null;
            }
            return dba.executeQuery(new ConnectionFunction<BoardCalendar>() {
                @Override
                public BoardCalendar run(DatabaseConnection dc) {
                    Connection c = dc.getJdbcConnection();
                    long workingDaysId = -1L;
                    Set<Integer> dows = new LinkedHashSet<Integer>();
                    String tz = null;
                    // 1) weekday flags + timezone
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT ID, SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, TIMEZONE "
                                    + "FROM AO_60DB71_WORKINGDAYS WHERE RAPID_VIEW_ID = ?")) {
                        ps.setLong(1, boardId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                workingDaysId = rs.getLong("ID");
                                if (rs.getBoolean("SUNDAY")) dows.add(Calendar.SUNDAY);
                                if (rs.getBoolean("MONDAY")) dows.add(Calendar.MONDAY);
                                if (rs.getBoolean("TUESDAY")) dows.add(Calendar.TUESDAY);
                                if (rs.getBoolean("WEDNESDAY")) dows.add(Calendar.WEDNESDAY);
                                if (rs.getBoolean("THURSDAY")) dows.add(Calendar.THURSDAY);
                                if (rs.getBoolean("FRIDAY")) dows.add(Calendar.FRIDAY);
                                if (rs.getBoolean("SATURDAY")) dows.add(Calendar.SATURDAY);
                                tz = rs.getString("TIMEZONE");
                            }
                        }
                    } catch (Exception ex) {
                        return null;
                    }
                    if (workingDaysId < 0) {
                        return new BoardCalendar(boardId, boardName(c, boardId),
                                new LinkedHashSet<Integer>(), new LinkedHashSet<String>(), null, false);
                    }
                    // 2) non-working dates (holidays)
                    Set<String> nwd = new LinkedHashSet<String>();
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT ISO8601_DATE FROM AO_60DB71_NONWORKINGDAY WHERE WORKING_DAYS_ID = ?")) {
                        ps.setLong(1, workingDaysId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String d = rs.getString("ISO8601_DATE");
                                if (d != null && d.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
                                    nwd.add(d.trim());
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // holidays optional
                    }
                    return new BoardCalendar(boardId, boardName(c, boardId), dows, nwd, tz, true);
                }
            });
        } catch (Throwable t) {
            return null;
        }
    }

    private static String boardName(Connection c, long boardId) {
        try (PreparedStatement ps = c.prepareStatement("SELECT NAME FROM AO_60DB71_RAPIDVIEW WHERE ID = ?")) {
            ps.setLong(1, boardId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("NAME");
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Resolve which board id applies to an issue (explicit project mapping, then default board). */
    static long resolveBoardId(Issue issue) {
        JimAdminSettingsService a = admin();
        if (a == null) {
            return -1L;
        }
        String projectKey = null;
        try {
            if (issue != null && issue.getProjectObject() != null) {
                projectKey = issue.getProjectObject().getKey();
            }
        } catch (Throwable ignored) {
        }
        // 1) explicit project -> board mapping
        if (projectKey != null) {
            Map<String, Long> map = parseProjectBoardMapping(a.getReportProjectBoardMapping());
            Long b = map.get(projectKey);
            if (b != null && b.longValue() > 0) {
                return b.longValue();
            }
        }
        // 2) default board id
        long def = a.getReportDefaultBoardId();
        if (def > 0) {
            return def;
        }
        return -1L;
    }

    static Map<String, Long> parseProjectBoardMapping(String csv) {
        Map<String, Long> out = new LinkedHashMap<String, Long>();
        if (csv == null) {
            return out;
        }
        for (String tok : csv.split(",")) {
            String t = tok.trim();
            if (t.isEmpty() || t.indexOf('=') < 0) {
                continue;
            }
            String[] kv = t.split("=", 2);
            String pk = kv[0].trim();
            try {
                long b = Long.parseLong(kv[1].trim());
                if (!pk.isEmpty() && b > 0) {
                    out.put(pk, Long.valueOf(b));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ resolution
    /** Resolved working-day configuration for an issue (never null). */
    public static final class Resolved {
        public final Set<Integer> workingDaysOfWeek;
        public final Set<String> holidays;
        public final String source;            // selected mode
        public final String resolvedSource;    // actual effective source (e.g. board vs fallback)
        public final int daysPerWeek;
        public final long boardId;
        public final String boardName;
        public final String timezoneId;
        public final String fallbackReason;

        Resolved(Set<Integer> dows, Set<String> holidays, String source, String resolvedSource,
                 int daysPerWeek, long boardId, String boardName, String timezoneId, String fallbackReason) {
            this.workingDaysOfWeek = dows;
            this.holidays = holidays;
            this.source = source;
            this.resolvedSource = resolvedSource;
            this.daysPerWeek = daysPerWeek;
            this.boardId = boardId;
            this.boardName = boardName;
            this.timezoneId = timezoneId;
            this.fallbackReason = fallbackReason;
        }
    }

    /** Back-compatible no-issue resolution (uses plugin/time-tracking; no board). */
    public static Resolved resolve() {
        return resolve(null);
    }

    /** Resolve the effective working-day configuration for an issue (never null). */
    public static Resolved resolve(Issue issue) {
        JimAdminSettingsService a = admin();
        Set<String> pluginHolidays = parseHolidays(a != null ? a.getReportHolidaysCsv() : null);
        String source = selectedSource();

        // Board-based resolution when requested.
        if (SRC_BOARD_FIRST.equals(source) || SRC_PLUGIN_PLUS_BOARD_HOLIDAYS.equals(source)) {
            long boardId = resolveBoardId(issue);
            BoardCalendar bc = boardId > 0 ? readBoard(boardId) : null;
            if (bc != null && bc.found && !bc.workingDaysOfWeek.isEmpty()) {
                if (SRC_BOARD_FIRST.equals(source)) {
                    Set<String> holidays = new LinkedHashSet<String>();
                    holidays.addAll(bc.nonWorkingDates);
                    holidays.addAll(pluginHolidays); // org-wide plugin holidays always apply
                    return new Resolved(bc.workingDaysOfWeek, holidays, source, "jira_board(" + boardId + ")",
                            bc.workingDaysOfWeek.size(), boardId, bc.boardName, bc.timezoneId, null);
                }
                // plugin_calendar_plus_board_holidays: plugin weekdays + plugin∪board holidays
                PluginWeek pw = pluginWeekdays(a);
                Set<String> holidays = new LinkedHashSet<String>();
                holidays.addAll(pluginHolidays);
                holidays.addAll(bc.nonWorkingDates);
                return new Resolved(pw.dows, holidays, source, pw.source + "+board_holidays(" + boardId + ")",
                        pw.dows.size(), boardId, bc.boardName, bc.timezoneId, null);
            }
            // board could not be resolved/read → fall back to plugin calendar
            String reason = boardId <= 0 ? "no_board_mapped_for_issue"
                    : (bc == null ? "board_read_failed" : "board_has_no_working_days_config");
            PluginWeek pw = pluginWeekdays(a);
            return new Resolved(pw.dows, pluginHolidays, source, pw.source + "(fallback:" + reason + ")",
                    pw.dows.size(), -1L, null, null, reason);
        }

        if (SRC_PLUGIN_ONLY.equals(source)) {
            PluginWeek pw = pluginWeekdays(a);
            return new Resolved(pw.dows, pluginHolidays, source, pw.source, pw.dows.size(),
                    -1L, null, null, null);
        }

        // time_tracking_fallback (default; legacy behavior)
        PluginWeek pw = timeTrackingWeekdays();
        return new Resolved(pw.dows, pluginHolidays, source, pw.source, pw.dows.size(),
                -1L, null, null, null);
    }

    private static final class PluginWeek {
        final Set<Integer> dows;
        final String source;

        PluginWeek(Set<Integer> dows, String source) {
            this.dows = dows;
            this.source = source;
        }
    }

    /** Plugin working weekdays: explicit CSV wins, else time-tracking-derived. */
    private static PluginWeek pluginWeekdays(JimAdminSettingsService a) {
        Set<Integer> explicit = parseDowCsv(a != null ? a.getReportWorkingDaysCsv() : null);
        if (!explicit.isEmpty()) {
            return new PluginWeek(explicit, "plugin_working_days");
        }
        return timeTrackingWeekdays();
    }

    /** Time-tracking days-per-week → Iran-priority weekend fallback (legacy). */
    private static PluginWeek timeTrackingWeekdays() {
        Integer dpw = jiraDaysPerWeek();
        int n;
        String source;
        if (dpw != null) {
            n = Math.max(1, Math.min(7, dpw));
            source = "jira_time_tracking(daysPerWeek=" + n + ")";
        } else {
            n = 5;
            source = "fallback_default(daysPerWeek=5)";
        }
        return new PluginWeek(workingDaysForCount(n), source);
    }

    /**
     * Number of Jira platform working days per week from the time-tracking config
     * ({@code TimeTrackingConfiguration.getDaysPerWeek()}), or null when unavailable.
     */
    static Integer jiraDaysPerWeek() {
        try {
            com.atlassian.jira.bc.issue.worklog.TimeTrackingConfiguration ttc = ComponentAccessor.getComponent(
                    com.atlassian.jira.bc.issue.worklog.TimeTrackingConfiguration.class);
            if (ttc != null) {
                java.math.BigDecimal d = ttc.getDaysPerWeek();
                if (d != null) {
                    return Math.round(d.floatValue());
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Set<Integer> workingDaysForCount(int n) {
        Set<Integer> working = new LinkedHashSet<Integer>();
        for (int d = 1; d <= 7; d++) {
            working.add(d);
        }
        int toRemove = 7 - n;
        for (int i = 0; i < WEEKEND_PRIORITY.length && toRemove > 0; i++) {
            if (working.remove(Integer.valueOf(WEEKEND_PRIORITY[i]))) {
                toRemove--;
            }
        }
        return working;
    }

    private static Set<Integer> parseDowCsv(String csv) {
        Set<Integer> out = new LinkedHashSet<Integer>();
        if (csv == null) {
            return out;
        }
        for (String tok : csv.split(",")) {
            String t = tok.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                int v = Integer.parseInt(t);
                if (v >= 1 && v <= 7) {
                    out.add(v);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static Set<String> parseHolidays(String csv) {
        Set<String> out = new LinkedHashSet<String>();
        if (csv == null) {
            return out;
        }
        for (String tok : csv.split(",")) {
            String t = tok.trim();
            if (t.matches("\\d{4}-\\d{2}-\\d{2}")) {
                out.add(t);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ classifier
    /** Back-compatible classifier without issue context. */
    public static JimReportCalculator.DayClassifier classifier(TimeZone tz) {
        return classifier(tz, null);
    }

    /**
     * A {@link JimReportCalculator.DayClassifier} for the report timezone + issue, or
     * {@code null} when the working-days feature is disabled (calculator treats every
     * day as working — no behaviour change).
     */
    public static JimReportCalculator.DayClassifier classifier(final TimeZone tz, final Issue issue) {
        if (!isEnabled()) {
            return null;
        }
        final Resolved r = resolve(issue);
        final TimeZone zone = tz != null ? tz : TimeZone.getTimeZone("UTC");
        return new JimReportCalculator.DayClassifier() {
            @Override
            public boolean isWorkingDay(long dayStartMs) {
                Calendar c = Calendar.getInstance(zone);
                c.setTimeInMillis(dayStartMs);
                int dow = c.get(Calendar.DAY_OF_WEEK);
                if (!r.workingDaysOfWeek.contains(Integer.valueOf(dow))) {
                    return false;
                }
                if (!r.holidays.isEmpty()) {
                    String ymd = String.format("%04d-%02d-%02d",
                            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
                    if (r.holidays.contains(ymd)) {
                        return false;
                    }
                }
                return true;
            }
        };
    }

    /** True when the day containing {@code ms} is a working day (feature on); always true when off. */
    public static boolean isWorkingDay(long ms, TimeZone tz) {
        return isWorkingDay(ms, tz, null);
    }

    /** Issue-aware working-day check. */
    public static boolean isWorkingDay(long ms, TimeZone tz, Issue issue) {
        JimReportCalculator.DayClassifier c = classifier(tz, issue);
        if (c == null) {
            return true;
        }
        Calendar cal = Calendar.getInstance(tz != null ? tz : TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(ms);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return c.isWorkingDay(cal.getTimeInMillis());
    }

    /** Reason string for a non-working day (weekend vs holiday), or null when working. */
    public static String nonWorkingReason(long ms, TimeZone tz, Issue issue) {
        if (!isEnabled()) {
            return null;
        }
        Resolved r = resolve(issue);
        TimeZone zone = tz != null ? tz : TimeZone.getTimeZone("UTC");
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(ms);
        int dow = c.get(Calendar.DAY_OF_WEEK);
        String ymd = String.format("%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        if (r.holidays.contains(ymd)) {
            return "holiday";
        }
        if (!r.workingDaysOfWeek.contains(Integer.valueOf(dow))) {
            return "non_working_weekday";
        }
        return null;
    }

    /** Next working-day date (YYYY-MM-DD) at or after {@code ms}, bounded to 366 days; null if none/off. */
    public static String nextWorkingDay(long ms, TimeZone tz, Issue issue) {
        if (!isEnabled()) {
            return null;
        }
        TimeZone zone = tz != null ? tz : TimeZone.getTimeZone("UTC");
        JimReportCalculator.DayClassifier c = classifier(zone, issue);
        if (c == null) {
            return null;
        }
        Calendar cal = Calendar.getInstance(zone);
        cal.setTimeInMillis(ms);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 366; i++) {
            if (c.isWorkingDay(cal.getTimeInMillis())) {
                return String.format("%04d-%02d-%02d",
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return null;
    }

    // ------------------------------------------------------------------ diagnostics + fingerprint
    /** Diagnostics for the admin report-diagnostics endpoint (no issue context). */
    public static Map<String, Object> diagnostics() {
        return diagnostics(null);
    }

    /** Diagnostics for the admin report-diagnostics endpoint (optionally issue-scoped). */
    public static Map<String, Object> diagnostics(Issue issue) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        boolean enabled = isEnabled();
        m.put("enabled", enabled);
        m.put("selectedSource", selectedSource());
        Resolved r = resolve(issue);
        m.put("resolvedSource", r.resolvedSource);
        m.put("daysPerWeek", r.daysPerWeek);
        m.put("workingDaysOfWeek", r.workingDaysOfWeek);
        m.put("holidayCount", r.holidays.size());
        m.put("boardId", r.boardId > 0 ? Long.valueOf(r.boardId) : null);
        m.put("boardName", r.boardName);
        m.put("boardTimezone", r.timezoneId);
        m.put("fallbackReason", r.fallbackReason);
        Integer dpw = jiraDaysPerWeek();
        m.put("jiraDaysPerWeekAvailable", dpw != null);
        m.put("jiraDaysPerWeek", dpw);
        m.put("boardCalendarReadable", boardCalendarReadable());
        return m;
    }

    /** True when the plugin can read the Jira Software board Working Days tables. */
    static boolean boardCalendarReadable() {
        try {
            DatabaseAccessor dba = ComponentAccessor.getComponent(DatabaseAccessor.class);
            if (dba == null) {
                return false;
            }
            return Boolean.TRUE.equals(dba.executeQuery(new ConnectionFunction<Boolean>() {
                @Override
                public Boolean run(DatabaseConnection dc) {
                    try (PreparedStatement ps = dc.getJdbcConnection().prepareStatement(
                            "SELECT COUNT(*) FROM AO_60DB71_WORKINGDAYS");
                         ResultSet rs = ps.executeQuery()) {
                        return Boolean.valueOf(rs.next());
                    } catch (Exception ex) {
                        return Boolean.FALSE;
                    }
                }
            }));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Back-compatible fingerprint signature (no issue). */
    public static String fingerprintSignature() {
        return fingerprintSignature(null);
    }

    /**
     * Short stable signature for the ledger fingerprint. Includes calendar source, board
     * id (when used), working weekdays, holiday-list hash, timezone and fallback mode, so
     * that changing any of them invalidates/recomputes affected ledgers.
     */
    public static String fingerprintSignature(Issue issue) {
        if (!isEnabled()) {
            return "wd:off";
        }
        Resolved r = resolve(issue);
        return "wd:" + r.source + ":" + r.resolvedSource + ":board=" + r.boardId + ":"
                + r.workingDaysOfWeek + ":tz=" + (r.timezoneId == null ? "" : r.timezoneId)
                + ":h=" + r.holidays.hashCode();
    }
}
