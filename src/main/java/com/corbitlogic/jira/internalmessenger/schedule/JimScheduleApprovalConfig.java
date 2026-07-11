package com.corbitlogic.jira.internalmessenger.schedule;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileDates;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schedule-approval workflow field contract (Sprint 12 — proposed completion
 * date approval). All field ids/options live on the PRODUCTION instance only;
 * on any instance where they are missing the feature disables gracefully
 * ({@link #isConfigured()} returns false and every read returns null).
 *
 * <p>Fields (production):</p>
 * <ul>
 *   <li>Approver — {@code customfield_11305} (user picker) — تاييد كننده تاريخ انجام</li>
 *   <li>Approval status — {@code customfield_11503} (select: Approved/Pending/Rejected) — وضعيت تاييد تاريخ انجام</li>
 *   <li>Proposed completion date — {@code customfield_11309} (date; resolved by {@link JimMobileDates})</li>
 *   <li>Approved completion date — {@code customfield_11501} (date) — تاريخ انجام تاييد شده</li>
 *   <li>Rejection reason — {@code customfield_11502} (textarea) — دليل عدم تاييد</li>
 * </ul>
 *
 * <p>Types are validated at runtime — nothing is assumed. This class never logs
 * field values.</p>
 */
public final class JimScheduleApprovalConfig {
    private static final Logger log = LoggerFactory.getLogger(JimScheduleApprovalConfig.class);

    public static final String APPROVER_FIELD_ID = "customfield_11305";
    public static final String STATUS_FIELD_ID = "customfield_11503";
    public static final String REASON_FIELD_ID = "customfield_11502";
    public static final String APPROVED_DATE_FIELD_ID = "customfield_11501";

    public static final String STATUS_APPROVED = "Approved";
    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_REJECTED = "Rejected";

    public static final int MAX_REASON_LENGTH = 2000;

    private JimScheduleApprovalConfig() {
    }

    /** True only when the approver + status + proposed-date fields all resolve with the expected types. */
    public static boolean isConfigured() {
        return resolveApproverField() != null
                && resolveStatusField() != null
                && JimMobileDates.findProposedCompletionField() != null;
    }

    /** Display name of the approver field (for changelog matching), or null. */
    public static String approverFieldName() {
        CustomField cf = resolveApproverField();
        return cf != null ? cf.getFieldName() : null;
    }

    /** Display name of the proposed-completion-date field, or null. */
    public static String proposedDateFieldName() {
        CustomField cf = JimMobileDates.findProposedCompletionField();
        return cf != null ? cf.getFieldName() : null;
    }

    /** Display name of the approval-status field (for changelog matching), or null. */
    public static String statusFieldName() {
        CustomField cf = resolveStatusField();
        return cf != null ? cf.getFieldName() : null;
    }

    // --- Field resolution (id + type guard) ----------------------------------

    public static CustomField resolveApproverField() {
        return fieldOfType(APPROVER_FIELD_ID, "userpicker");
    }

    static CustomField resolveStatusField() {
        return fieldOfType(STATUS_FIELD_ID, "select");
    }

    static CustomField resolveReasonField() {
        CustomField cf = fieldOfType(REASON_FIELD_ID, "textarea");
        return cf != null ? cf : fieldOfType(REASON_FIELD_ID, "textfield");
    }

    static CustomField resolveApprovedDateField() {
        CustomField cf = fieldOfType(APPROVED_DATE_FIELD_ID, "datepicker");
        return cf != null ? cf : fieldOfType(APPROVED_DATE_FIELD_ID, "datetime");
    }

    private static CustomField fieldOfType(String id, String typeKeySubstring) {
        try {
            CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
            if (cfm == null) {
                return null;
            }
            CustomField cf = cfm.getCustomFieldObject(id);
            if (cf == null || cf.getCustomFieldType() == null) {
                return null;
            }
            String key = cf.getCustomFieldType().getKey();
            if (key != null && key.toLowerCase().contains(typeKeySubstring)) {
                return cf;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // --- Reads ----------------------------------------------------------------

    public static ApplicationUser getApprover(Issue issue) {
        CustomField cf = resolveApproverField();
        if (cf == null || issue == null) {
            return null;
        }
        try {
            Object value = cf.getValue(issue);
            if (value instanceof ApplicationUser) {
                return (ApplicationUser) value;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Date getProposedDate(Issue issue) {
        CustomField cf = JimMobileDates.findProposedCompletionField();
        if (cf == null || issue == null) {
            return null;
        }
        try {
            Object value = cf.getValue(issue);
            if (value instanceof Date) {
                return (Date) value;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Current approval-status display value ("Approved"/"Pending"/"Rejected"), or null. */
    public static String getStatusValue(Issue issue) {
        CustomField cf = resolveStatusField();
        if (cf == null || issue == null) {
            return null;
        }
        try {
            Object value = cf.getValue(issue);
            if (value instanceof Option) {
                return ((Option) value).getValue();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Current rejection reason (cf_11502), or null. */
    public static String getRejectionReason(Issue issue) {
        CustomField cf = resolveReasonField();
        if (cf == null || issue == null) {
            return null;
        }
        try {
            Object value = cf.getValue(issue);
            if (value != null) {
                String s = value.toString().trim();
                return s.isEmpty() ? null : s;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean isPending(Issue issue) {
        String v = getStatusValue(issue);
        // Treat a null/blank status on an issue that carries the fields as
        // effectively Pending (Jira default is Pending), so a freshly-set
        // proposed date still notifies before anyone touches the status.
        return v == null || v.trim().isEmpty() || STATUS_PENDING.equalsIgnoreCase(v.trim());
    }

    // --- Elevated writes ------------------------------------------------------
    // The approver's identity is enforced by the caller (resource layer). The
    // actual field write runs through IssueManager.updateIssue so the workflow
    // works even when the approver lacks EDIT_ISSUES permission (approval is a
    // governance action, not a general edit). History is attributed to the
    // approver. Never logs values.

    /**
     * Apply an approve/reject decision: set the status option, on approve copy
     * the proposed date into the approved-date field, on reject store the
     * reason, then persist and add an audit comment. Returns false if the
     * fields are not configured or the option cannot be resolved.
     */
    public static boolean applyDecision(Issue issue, ApplicationUser approver, boolean approved, String reason) {
        if (issue == null || approver == null || !isConfigured()) {
            return false;
        }
        CustomField statusCf = resolveStatusField();
        Option target = resolveOption(statusCf, issue, approved ? STATUS_APPROVED : STATUS_REJECTED);
        if (target == null) {
            log.warn("event=schedule stage=apply outcome=error reason=option_unresolved issueKey={} approved={}",
                    issue.getKey(), approved);
            return false;
        }
        try {
            MutableIssue mutable = ComponentAccessor.getIssueManager().getIssueObject(issue.getId());
            if (mutable == null) {
                return false;
            }
            mutable.setCustomFieldValue(statusCf, target);
            if (approved) {
                CustomField approvedDateCf = resolveApprovedDateField();
                Date proposed = getProposedDate(mutable);
                if (approvedDateCf != null && proposed != null) {
                    mutable.setCustomFieldValue(approvedDateCf, proposed);
                }
            } else {
                CustomField reasonCf = resolveReasonField();
                if (reasonCf != null) {
                    mutable.setCustomFieldValue(reasonCf, reason != null ? reason : "");
                }
            }
            ComponentAccessor.getIssueManager()
                    .updateIssue(approver, mutable, EventDispatchOption.ISSUE_UPDATED, false);
            return true;
        } catch (Throwable ex) {
            log.error("event=schedule stage=apply outcome=error issueKey={} approved={} message={}",
                    new Object[]{issue.getKey(), approved, ex.getMessage(), ex});
            return false;
        }
    }

    /**
     * Ensure the approval status is exactly "Pending" (elevated) when a request
     * is raised or the proposed date / approver changes. No-op only if it is
     * ALREADY exactly Pending — a null/empty status IS set to Pending so a fresh
     * request shows up in the approver's Dashboard pending list. The resulting
     * update only changes the status field, so it cannot loop the
     * proposed-date/approver change detector.
     */
    public static boolean resetToPending(Issue issue, ApplicationUser actor) {
        if (issue == null || !isConfigured()) {
            return false;
        }
        String current = getStatusValue(issue);
        if (current != null && STATUS_PENDING.equalsIgnoreCase(current.trim())) {
            return false;
        }
        CustomField statusCf = resolveStatusField();
        Option pending = resolveOption(statusCf, issue, STATUS_PENDING);
        if (pending == null) {
            return false;
        }
        try {
            MutableIssue mutable = ComponentAccessor.getIssueManager().getIssueObject(issue.getId());
            if (mutable == null) {
                return false;
            }
            mutable.setCustomFieldValue(statusCf, pending);
            ApplicationUser updater = actor != null ? actor
                    : ComponentAccessor.getUserManager().getUserByName("admin");
            ComponentAccessor.getIssueManager()
                    .updateIssue(updater, mutable, EventDispatchOption.ISSUE_UPDATED, false);
            return true;
        } catch (Throwable ex) {
            log.error("event=schedule stage=reset outcome=error issueKey={} message={}",
                    new Object[]{issue.getKey(), ex.getMessage(), ex});
            return false;
        }
    }

    public static void addDecisionComment(Issue issue, ApplicationUser approver, boolean approved, String reason) {
        try {
            String body = approved
                    ? "Proposed completion date approved."
                    : ("Proposed completion date rejected. Reason: " + (reason != null ? reason : ""));
            ComponentAccessor.getCommentManager().create(issue, approver, body, true);
        } catch (Throwable ex) {
            log.warn("event=schedule stage=comment outcome=error issueKey={} message={}",
                    issue != null ? issue.getKey() : "?", ex.getMessage());
        }
    }

    private static Option resolveOption(CustomField statusCf, Issue issue, String value) {
        if (statusCf == null || value == null) {
            return null;
        }
        try {
            OptionsManager optionsManager = ComponentAccessor.getComponent(OptionsManager.class);
            FieldConfig config = statusCf.getRelevantConfig(issue);
            if (optionsManager == null || config == null) {
                return null;
            }
            Options options = optionsManager.getOptions(config);
            if (options == null) {
                return null;
            }
            for (Option option : options) {
                if (option != null && value.equalsIgnoreCase(option.getValue())) {
                    return option;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
