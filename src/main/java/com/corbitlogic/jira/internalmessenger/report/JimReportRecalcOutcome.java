package com.corbitlogic.jira.internalmessenger.report;

/**
 * Diagnostic result of a single {@code report_recalc} run. Carries just enough
 * to emit the structured timing log line (Phase 1 hardening) and to let the
 * coalescing coordinator report whether the calc was skipped by the in-memory
 * input-fingerprint gate. Never touches Jira.
 */
public final class JimReportRecalcOutcome {

    /** True when the calc was short-circuited (feature off / fingerprint unchanged / issue gone). */
    public boolean skipped;
    /** Why it was skipped ({@code disabled}, {@code fingerprint_unchanged}, {@code no_issue}, {@code error}). */
    public String skipReason;
    /** Count of metric values that differed from the stored value this run. */
    public int valuesChanged;
    /** Count of custom fields actually written to the issue this run. */
    public int fieldsWritten;
    /** Whether the metric write dispatched a Jira event (always false after Phase 1). */
    public boolean dispatched;
    /** Computed compliance status code (for the log line), or null when skipped. */
    public String statusCode;
    /** Issue key (for the log line). */
    public String issueKey;
}
