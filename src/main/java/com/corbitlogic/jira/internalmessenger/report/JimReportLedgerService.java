package com.corbitlogic.jira.internalmessenger.report;

import com.atlassian.jira.issue.Issue;
import java.util.List;
import java.util.Map;

/**
 * Active-Objects persistence for the reporting-control lifecycle
 * ({@code JimReportControl}) and the bounded per-window ledger
 * ({@code JimReportWindow}). All access is additive + idempotent; historical
 * window rows are never destroyed and no missed window is created after closure.
 *
 * <p>Everything degrades gracefully when Active Objects is unavailable — the
 * report feature then falls back to the Phase 1 in-memory behaviour and the
 * chat/Assistant features are unaffected.</p>
 */
public interface JimReportLedgerService {

    /** Lightweight snapshot of a persisted control row (null fields when absent). */
    final class ControlSnapshot {
        public boolean exists;
        public String state;
        public Long reportingClosedAt;
        public String reportingClosedByCondition;
        public String reportingCloseReason;
        public Long reportingStartAt;
        public Long reportingEndAt;
        public Long lastRequiredWindowEndAt;
        public Long doneMs;
        public String fingerprint;
        public Long calculatedAt;
    }

    /** True when Active Objects is initialized and usable on this node. */
    boolean isAvailable();

    /** Load the persisted control snapshot for an issue (never throws). */
    ControlSnapshot loadControl(long issueId);

    /**
     * Idempotently persist the control row + bounded recent windows for an issue
     * from a freshly-computed summary. Change-guarded (writes only when values
     * differ). Never throws to the caller.
     */
    void persist(Issue issue, JimReportSummary summary, String fingerprint);

    /** Stored input fingerprint for an issue, or null. */
    String storedFingerprint(long issueId);

    /**
     * Admin dashboard listing over persisted control rows, with optional filters
     * (state, projectKey, responsible userKey, from/to on calculatedAt). Capped
     * at {@code limit}.
     */
    List<Map<String, Object>> listControls(String stateFilter, String projectKey,
                                           String userKey, Long fromMs, Long toMs, int limit);

    /** Queue/ledger diagnostics (control/window counts by state) for the admin API. */
    Map<String, Object> diagnostics();
}
