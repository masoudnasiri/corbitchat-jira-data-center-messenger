/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import java.util.List;
import java.util.Map;

public interface JimAdminSettingsService {
    public static final String CHAT_MODE_ALLOW_ALL = "ALLOW_ALL";
    public static final String CHAT_MODE_RESTRICTED = "RESTRICTED";
    public static final String CHAT_MODE_DISABLED = "DISABLED";
    public static final String DETAIL_FULL_MESSAGE = "FULL_MESSAGE";
    public static final String DETAIL_SENDER_ONLY = "SENDER_ONLY";
    public static final String DETAIL_GENERIC_ONLY = "GENERIC_ONLY";

    public String getChatMode();

    public boolean isWebPushEnabled();

    public String getNotificationDetailLevel();

    public boolean isAggregateNotifications();

    public boolean isMentionNotificationsEnabled();

    public boolean isAssignmentNotificationsEnabled();

    public boolean isAttachmentsEnabled();

    public int getMaxAttachmentSizeMb();

    public String getAllowedExtensions();

    public boolean isImagePreviewEnabled();

    public String getBrandingTitle();

    public String getBrandingLogoUrl();

    /** Admin default: show the Jira web chat UI in Persian for users on "admin default". */
    public boolean isWebPersianUiDefault();

    /** Admin default: show chat dates in Jalali/Shamsi for users on "admin default". */
    public boolean isWebJalaliDatesDefault();

    /**
     * Global admin toggle: display Jira web UI dates as Jalali/Shamsi
     * (display-layer only; never changes stored values, JQL, API or exports).
     * Separate concept from the per-user chat date preference above.
     */
    public boolean isJiraWideJalaliEnabled();

    // --- Issue Report Compliance (Report Schedule) ---------------------------

    /** Master on/off for the issue report-compliance feature. */
    public boolean isReportComplianceEnabled();

    /** Marker/hashtag that identifies a comment as a report (default {@code #گزارش}). */
    public String getReportMarker();

    /** Global reporting-window start, {@code HH:mm} (default {@code 08:00}). */
    public String getReportWindowStart();

    /** Global reporting-window end, {@code HH:mm} (default {@code 21:00}). */
    public String getReportWindowEnd();

    /** Jira field id of the follow-up level select (default {@code customfield_10802}). */
    public String getReportLevelFieldId();

    /** Jira field id of the valid submitted-report count number (default {@code customfield_11801}). */
    public String getReportValidCountFieldId();

    /** Jira field id of the required report count number (default {@code customfield_11900}). */
    public String getReportRequiredCountFieldId();

    /**
     * Optional reporting-timezone override for window classification + display
     * (e.g. {@code GMT+03:30}). Empty = use the viewer's Jira timezone. Useful
     * when the server JVM has stale tz data (e.g. abolished Iran DST) — a fixed
     * GMT offset bypasses it so validation matches the user's real local time.
     */
    public String getReportTimezone();

    /**
     * Per follow-up level schedule config, keyed by internal level code
     * ({@code normal|high|special|managerial}). Each value is a map with
     * {@code interval} (int, in {@code unit}), {@code unit} ({@code minutes|hours}),
     * {@code start} ({@code ""}=use global or {@code HH:mm}) and {@code end}.
     */
    public Map<String, Map<String, Object>> getReportLevelSchedules();

    /**
     * Allow submitting report comments even when the follow-up level or report
     * fields are not configured (report still created as a {@code #گزارش}
     * comment; never counted as a valid scheduled report). Default false.
     */
    public boolean isReportAllowWithoutConfig();

    /**
     * When true, reopening a closed issue (or extending its due date) reactivates
     * reporting. Conservative default false keeps a closed issue closed (Phase 2).
     */
    public boolean isReportReopenReactivation();

    // --- Report Applicability Policy (Sprint 12 reportscope) -----------------
    // Additive, backward-compatible scope gate. When disabled (default) the
    // report engine behaves exactly as before. When enabled it restricts which
    // issues are report-applicable by an effective date (created/due basis) and
    // an optional non-Done-only rule (Done = statusCategory Done).

    /** Master on/off for the applicability policy. Default false (no behaviour change). */
    public boolean isReportApplicabilityEnabled();

    /** Effective-from date as ISO {@code YYYY-MM-DD}, or "" when unset. Interpreted in the report timezone. */
    public String getReportEffectiveFromDate();

    /** Date basis: {@code created_on_or_after|due_on_or_after|created_and_due_on_or_after|created_or_due_on_or_after}. */
    public String getReportApplicabilityDateMode();

    /** When true, only non-Done issues (statusCategory != Done) are report-applicable. Default true. */
    public boolean isReportApplicabilityNonDoneOnly();

    /** Missing-due handling: {@code exclude_missing_due|include_if_created_matches}. Default {@code exclude_missing_due}. */
    public String getReportApplicabilityMissingDuePolicy();

    /**
     * Optional generated-metric → Jira custom-field mappings, keyed by metric
     * key (see {@code JimReportMetrics}). Value is a {@code customfield_<id>} or
     * {@code ""} (disabled/not mapped).
     */
    public Map<String, String> getReportFieldMappings();

    // --- Schedule-approval field mapping (environment-specific) --------------
    // Each returns the admin-configured Jira custom-field id for the logical
    // approval field on THIS instance, or {@code ""} when unset. Empty means
    // "fall back to the production id" (see {@code JimApprovalFields}); an
    // explicit value overrides the fallback. Values look like
    // {@code customfield_12345}.

    /** Configured field id for logical key {@code corbit.approval.status}, or "". */
    public String getApprovalStatusFieldId();

    /** Configured field id for logical key {@code corbit.approval.approver}, or "". */
    public String getApprovalApproverFieldId();

    /** Configured field id for logical key {@code corbit.approval.proposedDate}, or "". */
    public String getApprovalProposedDateFieldId();

    /** Configured field id for logical key {@code corbit.approval.approvedDate}, or "". */
    public String getApprovalApprovedDateFieldId();

    /** Configured field id for logical key {@code corbit.approval.rejectionReason}, or "". */
    public String getApprovalRejectionReasonFieldId();

    public Map<String, Object> getAllSettings();

    public List<String> updateSettings(Map<String, Object> var1);
}

