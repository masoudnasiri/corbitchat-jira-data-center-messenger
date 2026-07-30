package com.corbitlogic.jira.internalmessenger.report;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import java.util.List;

/**
 * Issue report-compliance computation + safe custom-field persistence.
 *
 * <p>Everything degrades gracefully when the feature is disabled or the report
 * custom fields are absent on the instance ({@link JimReportSummary#configured}
 * = false, compliance = {@code not_configured}); nothing is written in that case.</p>
 */
public interface JimReportService {

    /** Compute (and, when operational, lazily persist) the report state for an issue. */
    JimReportSummary computeSummary(Issue issue);

    /**
     * Recalculate + persist the valid/required count fields for an issue (event
     * driven). No-op when not operational. Writes only when a value actually
     * changes, so it can never loop the issue-updated event it triggers.
     */
    void recalculate(Issue issue, ApplicationUser actor);

    /**
     * Recalculate + persist (like {@link #recalculate}) but returns a diagnostic
     * outcome and applies the in-memory input-fingerprint gate: when the report
     * inputs are unchanged since the last successful calc for this issue, the
     * heavy compute + persist are skipped ({@code outcome.skipped=true}). Used by
     * the coalescing recalc coordinator on the maintenance lane.
     *
     * @param reason free-form trigger reason for the structured timing log
     */
    JimReportRecalcOutcome recalculateWithOutcome(Issue issue, ApplicationUser actor, String reason);

    /** True when a comment body is a report (contains the configured marker). */
    boolean isReportComment(String body);

    /**
     * Deterministic per-window ledger for an issue (Phase 3), recomputed from the
     * schedule for the API/export. Empty when the feature/schedule is not
     * configured or the level uses fixed-minimum mode (no interval windows).
     */
    List<JimReportSummary.WindowInfo> computeWindows(Issue issue);
}
