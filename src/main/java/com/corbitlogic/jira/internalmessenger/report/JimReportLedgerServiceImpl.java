package com.corbitlogic.jira.internalmessenger.report;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimReportControl;
import com.corbitlogic.jira.internalmessenger.ao.JimReportWindow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link JimReportLedgerService}. All writes are wrapped in an AO
 * transaction and change-guarded; all reads are defensive (never throw to the
 * caller). Window materialization is bounded to a recent retention window so the
 * table stays small; the full window history is recomputed deterministically by
 * the service for the API/export.
 */
public class JimReportLedgerServiceImpl implements JimReportLedgerService {

    private static final Logger log = LoggerFactory.getLogger(JimReportLedgerServiceImpl.class);

    /** Only windows newer than this are materialized (older history is recomputed on demand). */
    private static final long WINDOW_RETENTION_MS = 30L * 24 * 60 * 60 * 1000L;
    /** Hard cap on window rows written per recalc (defensive back-pressure). */
    private static final int MAX_WINDOWS_PER_PERSIST = 250;

    private final ActiveObjects activeObjects;

    @Inject
    public JimReportLedgerServiceImpl(ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    @Override
    public boolean isAvailable() {
        try {
            this.activeObjects.count(JimReportControl.class);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public ControlSnapshot loadControl(long issueId) {
        ControlSnapshot snap = new ControlSnapshot();
        try {
            JimReportControl row = findControl(issueId);
            if (row != null) {
                snap.exists = true;
                snap.state = row.getReportingControlState();
                snap.reportingClosedAt = row.getReportingClosedAt();
                snap.reportingClosedByCondition = row.getReportingClosedByCondition();
                snap.reportingCloseReason = row.getReportingCloseReason();
                snap.reportingStartAt = row.getReportingStartAt();
                snap.reportingEndAt = row.getReportingEndAt();
                snap.lastRequiredWindowEndAt = row.getLastRequiredWindowEndAt();
                snap.doneMs = row.getDoneMs();
                snap.fingerprint = row.getLastInputFingerprint();
                snap.calculatedAt = row.getCalculatedAt();
            }
        } catch (Throwable t) {
            log.debug("event=report stage=ledger outcome=load_error issueId={} message={}", issueId, t.getMessage());
        }
        return snap;
    }

    @Override
    public String storedFingerprint(long issueId) {
        try {
            JimReportControl row = findControl(issueId);
            return row != null ? row.getLastInputFingerprint() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void persist(final Issue issue, final JimReportSummary s, final String fingerprint) {
        if (issue == null || issue.getId() == null || s == null) {
            return;
        }
        final long issueId = issue.getId();
        final String issueKey = issue.getKey();
        try {
            this.activeObjects.executeInTransaction(() -> {
                persistControl(issueId, issueKey, s, fingerprint);
                persistWindows(issueId, issueKey, s);
                return null;
            });
        } catch (Throwable ex) {
            log.debug("event=report stage=ledger outcome=persist_error issueKey={} message={}",
                    issueKey, ex.getMessage());
        }
    }

    private void persistControl(long issueId, String issueKey, JimReportSummary s, String fingerprint) {
        JimReportControl existing = findControl(issueId);
        boolean created = false;
        if (existing == null) {
            existing = this.activeObjects.create(JimReportControl.class);
            existing.setIssueId(issueId);
            created = true;
        }
        final JimReportControl r = existing;
        boolean changed = created;
        changed |= setIfChanged(r.getIssueKey(), issueKey, r::setIssueKey);
        changed |= setIfChanged(r.getReportingControlState(), s.reportingControlState, r::setReportingControlState);
        changed |= setIfChanged(r.getReportingClosedAt(), s.reportingClosedAt, r::setReportingClosedAt);
        changed |= setIfChanged(r.getReportingClosedByCondition(), s.reportingClosedByCondition, r::setReportingClosedByCondition);
        changed |= setIfChanged(r.getReportingCloseReason(), s.reportingCloseReason, r::setReportingCloseReason);
        changed |= setIfChanged(r.getReportingStartAt(), s.reportingStartAt, r::setReportingStartAt);
        changed |= setIfChanged(r.getReportingEndAt(), s.reportingEndsMs, r::setReportingEndAt);
        changed |= setIfChanged(r.getLastRequiredWindowEndAt(), s.lastRequiredWindowEndMs, r::setLastRequiredWindowEndAt);
        changed |= setIfChanged(r.getDoneMs(), s.doneMs, r::setDoneMs);
        changed |= setIfChanged(r.getLastInputFingerprint(), fingerprint, r::setLastInputFingerprint);
        changed |= setIfChanged(r.getCalculationVersion(), (Integer) s.calculationVersion, r::setCalculationVersion);
        changed |= setIfChanged(r.getTotalRequiredUntilEnd(), (Integer) s.totalRequiredUntilEnd, r::setTotalRequiredUntilEnd);
        changed |= setIfChanged(r.getCurrentMissedStreak(), (Integer) s.currentMissedStreak, r::setCurrentMissedStreak);
        changed |= setIfChanged(r.getMaxMissedStreak(), (Integer) s.maxMissedStreak, r::setMaxMissedStreak);
        // calculatedAt always refreshed when anything else changed (or on create).
        if (changed) {
            r.setCalculatedAt(System.currentTimeMillis());
            r.save();
        }
    }

    private void persistWindows(long issueId, String issueKey, JimReportSummary s) {
        if (s.windowLedger == null || s.windowLedger.isEmpty()) {
            return;
        }
        long floor = System.currentTimeMillis() - WINDOW_RETENTION_MS;
        int written = 0;
        for (JimReportSummary.WindowInfo w : s.windowLedger) {
            if (w.windowStart < floor) {
                continue; // bounded: older windows recomputed on demand, never stored
            }
            if (written >= MAX_WINDOWS_PER_PERSIST) {
                break;
            }
            JimReportWindow row = findWindow(issueId, w.windowStart);
            boolean created = false;
            if (row == null) {
                row = this.activeObjects.create(JimReportWindow.class);
                row.setIssueId(issueId);
                row.setWindowStart(w.windowStart);
                created = true;
            }
            final JimReportWindow r = row;
            boolean changed = created;
            changed |= setIfChanged(r.getIssueKey(), issueKey, r::setIssueKey);
            changed |= setIfChanged(r.getWindowEnd(), (Long) w.windowEnd, r::setWindowEnd);
            changed |= setIfChanged(r.getResponsibleUserKey(), s.responsibleUserKey, r::setResponsibleUserKey);
            changed |= setIfChanged(r.getLevelCode(), s.levelCode, r::setLevelCode);
            changed |= setIfChanged(r.getIntervalMinutes(), (Integer) s.intervalMinutes, r::setIntervalMinutes);
            changed |= setIfChanged(r.getDueAt(), s.dueEndMs, r::setDueAt);
            changed |= setIfChanged(r.getStatus(), w.status, r::setStatus);
            changed |= setIfChanged(r.getFirstValidReportAt(), w.firstValidReportMs, r::setFirstValidReportAt);
            changed |= setIfChanged(r.getLastReportAt(), w.lastReportMs, r::setLastReportAt);
            changed |= setIfChanged(r.getValidCount(), (Integer) w.validCount, r::setValidCount);
            changed |= setIfChanged(r.getRawCount(), (Integer) w.rawCount, r::setRawCount);
            changed |= setIfChanged(r.getDuplicateCount(), (Integer) w.duplicateCount, r::setDuplicateCount);
            changed |= setIfChanged(r.getLateByMinutes(), (Integer) w.lateByMinutes, r::setLateByMinutes);
            changed |= setIfChanged(r.getSourceCommentIds(), joinIds(w.commentIds), r::setSourceCommentIds);
            if (changed) {
                r.setCalculationVersion(s.calculationVersion);
                r.setCalculatedAt(System.currentTimeMillis());
                r.save();
            }
            written++;
        }
    }

    @Override
    public List<Map<String, Object>> listControls(String stateFilter, String projectKey,
                                                  String userKey, Long fromMs, Long toMs, int limit) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        int cap = limit <= 0 ? 500 : Math.min(limit, 2000);
        try {
            Query q = Query.select().order("CALCULATED_AT DESC");
            List<Object> params = new ArrayList<Object>();
            StringBuilder where = new StringBuilder();
            if (stateFilter != null && !stateFilter.trim().isEmpty()) {
                where.append("REPORTING_CONTROL_STATE = ?");
                params.add(stateFilter.trim());
            }
            if (fromMs != null) {
                if (where.length() > 0) where.append(" AND ");
                where.append("CALCULATED_AT >= ?");
                params.add(fromMs);
            }
            if (toMs != null) {
                if (where.length() > 0) where.append(" AND ");
                where.append("CALCULATED_AT <= ?");
                params.add(toMs);
            }
            if (where.length() > 0) {
                q = q.where(where.toString(), params.toArray());
            }
            q = q.limit(cap);
            JimReportControl[] rows = this.activeObjects.find(JimReportControl.class, q);
            for (JimReportControl row : rows) {
                Map<String, Object> m = controlMap(row);
                // Optional project/user filters resolved against the live issue.
                if (projectKey != null && !projectKey.trim().isEmpty()) {
                    if (!projectKey.trim().equalsIgnoreCase(String.valueOf(m.get("projectKey")))) {
                        continue;
                    }
                }
                if (userKey != null && !userKey.trim().isEmpty()) {
                    if (!userKey.trim().equals(m.get("responsibleUserKey"))) {
                        continue;
                    }
                }
                out.add(m);
            }
        } catch (Throwable t) {
            log.debug("event=report stage=ledger outcome=list_error message={}", t.getMessage());
        }
        return out;
    }

    @Override
    public Map<String, Object> diagnostics() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        try {
            m.put("controlRowCount", this.activeObjects.count(JimReportControl.class));
            m.put("windowRowCount", this.activeObjects.count(JimReportWindow.class));
            m.put("activeCount", countState("ACTIVE"));
            m.put("closedCount", countState("CLOSED"));
            m.put("configErrorCount", countState("CONFIG_ERROR"));
            m.put("pausedCount", countState("PAUSED"));
            m.put("notRequiredCount", countState("NOT_REQUIRED"));
            m.put("ledgerAvailable", true);
        } catch (Throwable t) {
            m.put("ledgerAvailable", false);
            m.put("error", String.valueOf(t.getMessage()));
        }
        return m;
    }

    private int countState(String state) {
        try {
            return this.activeObjects.count(JimReportControl.class,
                    Query.select().where("REPORTING_CONTROL_STATE = ?", state));
        } catch (Throwable t) {
            return -1;
        }
    }

    private Map<String, Object> controlMap(JimReportControl row) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("issueId", row.getIssueId());
        m.put("issueKey", row.getIssueKey());
        m.put("reportingControlState", row.getReportingControlState());
        m.put("reportingClosedAt", row.getReportingClosedAt());
        m.put("reportingClosedByCondition", row.getReportingClosedByCondition());
        m.put("reportingCloseReason", row.getReportingCloseReason());
        m.put("reportingStartAt", row.getReportingStartAt());
        m.put("reportingEndAt", row.getReportingEndAt());
        m.put("doneMs", row.getDoneMs());
        m.put("calculatedAt", row.getCalculatedAt());
        m.put("calculationVersion", row.getCalculationVersion());
        m.put("totalRequiredReportsUntilEnd", row.getTotalRequiredUntilEnd());
        m.put("currentMissedReportStreak", row.getCurrentMissedStreak());
        m.put("maxMissedReportStreak", row.getMaxMissedStreak());
        // Enrich with live issue project + responsible user (best effort).
        String projectKey = null;
        String responsibleUserKey = null;
        String responsibleUserName = null;
        try {
            Issue issue = row.getIssueId() != null
                    ? ComponentAccessor.getIssueManager().getIssueObject(row.getIssueId()) : null;
            if (issue != null) {
                if (issue.getProjectObject() != null) {
                    projectKey = issue.getProjectObject().getKey();
                }
                ApplicationUser a = issue.getAssignee();
                if (a != null) {
                    responsibleUserKey = a.getKey();
                    responsibleUserName = a.getDisplayName();
                }
            }
        } catch (Throwable ignore) {
        }
        m.put("projectKey", projectKey);
        m.put("responsibleUserKey", responsibleUserKey);
        m.put("responsibleUserName", responsibleUserName);
        return m;
    }

    // --- helpers --------------------------------------------------------------

    private JimReportControl findControl(long issueId) {
        JimReportControl[] rows = this.activeObjects.find(JimReportControl.class,
                Query.select().where("ISSUE_ID = ?", issueId).limit(1));
        return rows.length > 0 ? rows[0] : null;
    }

    private JimReportWindow findWindow(long issueId, long windowStart) {
        JimReportWindow[] rows = this.activeObjects.find(JimReportWindow.class,
                Query.select().where("ISSUE_ID = ? AND WINDOW_START = ?", issueId, windowStart).limit(1));
        return rows.length > 0 ? rows[0] : null;
    }

    private static String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (id == null) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private interface Setter<T> {
        void set(T value);
    }

    private static <T> boolean setIfChanged(T current, T next, Setter<T> setter) {
        boolean same = current == null ? next == null : current.equals(next);
        if (same) {
            return false;
        }
        setter.set(next);
        return true;
    }
}
