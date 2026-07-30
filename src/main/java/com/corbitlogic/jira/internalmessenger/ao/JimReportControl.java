package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Persisted reporting-control lifecycle state for a single Jira issue (Phase 2).
 *
 * <p>One row per issue (keyed by {@link #getIssueId()}). It formalizes the
 * report lifecycle that previously lived only in derived/in-memory state:
 * whether reporting is required/active/closed for the issue, when and why it was
 * closed, and the cached inputs (Done time, input fingerprint) that let the
 * recalc coordinator skip unchanged work across restarts.</p>
 *
 * <p>Additive-only schema: this table is created on first use and never
 * destructively migrated. When Active Objects is unavailable the report feature
 * degrades to the Phase 1 in-memory behaviour (nothing here is required for the
 * chat/Assistant features).</p>
 */
@Preload
@Table("JimReportCtl")
public interface JimReportControl extends Entity {

    /** Jira issue id (one control row per issue). */
    @Indexed
    Long getIssueId();
    void setIssueId(Long issueId);

    @Indexed
    @StringLength(64)
    String getIssueKey();
    void setIssueKey(String issueKey);

    /** NOT_REQUIRED | ACTIVE | PAUSED | CLOSED | CONFIG_ERROR */
    @Indexed
    @StringLength(32)
    String getReportingControlState();
    void setReportingControlState(String reportingControlState);

    /** Epoch ms the reporting lifecycle was closed (stop condition met), or null. */
    Long getReportingClosedAt();
    void setReportingClosedAt(Long reportingClosedAt);

    /** Which stop condition closed it: done | due_passed | manual | null. */
    @StringLength(32)
    String getReportingClosedByCondition();
    void setReportingClosedByCondition(String reportingClosedByCondition);

    @StringLength(255)
    String getReportingCloseReason();
    void setReportingCloseReason(String reportingCloseReason);

    /** End of the last required window (used so no missed window is created past closure). */
    Long getLastRequiredWindowEndAt();
    void setLastRequiredWindowEndAt(Long lastRequiredWindowEndAt);

    Long getReportingStartAt();
    void setReportingStartAt(Long reportingStartAt);

    /** Effective reporting end (due/done/earlier), or null when ongoing. */
    Long getReportingEndAt();
    void setReportingEndAt(Long reportingEndAt);

    /** Cached Done-transition time so the full-changelog scan is not repeated. */
    Long getDoneMs();
    void setDoneMs(Long doneMs);

    /** Last input fingerprint (survives restart so recalc can be skipped when unchanged). */
    @StringLength(255)
    String getLastInputFingerprint();
    void setLastInputFingerprint(String lastInputFingerprint);

    Long getCalculatedAt();
    void setCalculatedAt(Long calculatedAt);

    Integer getCalculationVersion();
    void setCalculationVersion(Integer calculationVersion);

    // --- Report-metrics extension (additive): total-required + missed-streak ---
    /** Deterministic total required reports over the whole control period. */
    Integer getTotalRequiredUntilEnd();
    void setTotalRequiredUntilEnd(Integer totalRequiredUntilEnd);

    /** Consecutive missed windows since the last valid report (resets on valid). */
    Integer getCurrentMissedStreak();
    void setCurrentMissedStreak(Integer currentMissedStreak);

    /** Highest consecutive missed streak observed across the lifecycle. */
    Integer getMaxMissedStreak();
    void setMaxMissedStreak(Integer maxMissedStreak);
}
