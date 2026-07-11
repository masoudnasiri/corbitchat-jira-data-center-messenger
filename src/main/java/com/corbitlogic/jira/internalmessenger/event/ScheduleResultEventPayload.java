package com.corbitlogic.jira.internalmessenger.event;

import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileDates;
import com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalConfig;
import java.util.Date;
import java.util.List;
import org.ofbiz.core.entity.GenericValue;

/**
 * Snapshot of an approval DECISION (Sprint 12 polish): the approval status
 * field (cf_11503) changed to Approved or Rejected. Drives the result
 * notification back to the original requester. The requester key itself is
 * resolved later (from the stored request card) since it isn't on the event.
 */
public final class ScheduleResultEventPayload {
    private final String issueKey;
    private final String issueSummary;
    private final boolean approved;
    private final String approverUserKey;
    private final String approverDisplayName;
    private final String rejectionReason;
    private final Long proposedDateMillis;
    private final String proposedDateYmd;
    private final Long decidedAt;
    private final String changeGroupSuffix;

    private ScheduleResultEventPayload(String issueKey, String issueSummary, boolean approved,
                                       String approverUserKey, String approverDisplayName,
                                       String rejectionReason, Long proposedDateMillis,
                                       String proposedDateYmd, Long decidedAt, String changeGroupSuffix) {
        this.issueKey = issueKey;
        this.issueSummary = issueSummary;
        this.approved = approved;
        this.approverUserKey = approverUserKey;
        this.approverDisplayName = approverDisplayName;
        this.rejectionReason = rejectionReason;
        this.proposedDateMillis = proposedDateMillis;
        this.proposedDateYmd = proposedDateYmd;
        this.decidedAt = decidedAt;
        this.changeGroupSuffix = changeGroupSuffix;
    }

    public static ScheduleResultEventPayload from(IssueEvent event) {
        if (event == null || !JimScheduleApprovalConfig.isConfigured()) {
            return null;
        }
        Issue issue = event.getIssue();
        if (issue == null) {
            return null;
        }
        String newStatus = statusChangeValue(event);
        if (newStatus == null) {
            return null;
        }
        boolean approved;
        if (JimScheduleApprovalConfig.STATUS_APPROVED.equalsIgnoreCase(newStatus)) {
            approved = true;
        } else if (JimScheduleApprovalConfig.STATUS_REJECTED.equalsIgnoreCase(newStatus)) {
            approved = false;
        } else {
            return null; // e.g. changed to Pending — not a decision
        }
        ApplicationUser actor = event.getUser();
        String approverName = actor != null && actor.getDisplayName() != null
                ? actor.getDisplayName() : "Someone";
        String reason = approved ? null : JimScheduleApprovalConfig.getRejectionReason(issue);
        Date proposed = JimScheduleApprovalConfig.getProposedDate(issue);
        Long decidedAt = event.getTime() != null ? event.getTime().getTime() : System.currentTimeMillis();
        return new ScheduleResultEventPayload(
                issue.getKey(), issue.getSummary(), approved,
                actor != null ? actor.getKey() : null, approverName, reason,
                proposed != null ? proposed.getTime() : null,
                proposed != null ? JimMobileDates.toYmd(proposed) : null,
                decidedAt, resolveChangeGroupSuffix(event));
    }

    /** The new value of the approval-status field in this event's changelog, or null. */
    private static String statusChangeValue(IssueEvent event) {
        GenericValue changeLog = event.getChangeLog();
        if (changeLog == null) {
            return null;
        }
        String statusName = JimScheduleApprovalConfig.statusFieldName();
        if (statusName == null) {
            return null;
        }
        try {
            List<GenericValue> items = changeLog.getRelated("ChildChangeItem");
            if (items == null) {
                return null;
            }
            for (GenericValue item : items) {
                String field = item.getString("field");
                if (field == null || !statusName.equalsIgnoreCase(field)) {
                    continue;
                }
                String newString = item.getString("newstring");
                if (newString != null && !newString.trim().isEmpty()) {
                    return newString.trim();
                }
                return item.getString("newvalue");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String resolveChangeGroupSuffix(IssueEvent event) {
        try {
            GenericValue changeLog = event.getChangeLog();
            if (changeLog != null) {
                Object id = changeLog.get("id");
                if (id != null) {
                    return String.valueOf(id);
                }
            }
        } catch (Exception ignored) {
        }
        return "decision";
    }

    public String getIssueKey() {
        return this.issueKey;
    }

    public String getIssueSummary() {
        return this.issueSummary;
    }

    public boolean isApproved() {
        return this.approved;
    }

    public String getApproverUserKey() {
        return this.approverUserKey;
    }

    public String getApproverDisplayName() {
        return this.approverDisplayName;
    }

    public String getRejectionReason() {
        return this.rejectionReason;
    }

    public Long getProposedDateMillis() {
        return this.proposedDateMillis;
    }

    public String getProposedDateYmd() {
        return this.proposedDateYmd;
    }

    public Long getDecidedAt() {
        return this.decidedAt;
    }

    public String getChangeGroupSuffix() {
        return this.changeGroupSuffix;
    }

    /** Structured metadata for the requester's result card (key=value lines). */
    public String toEventData() {
        StringBuilder sb = new StringBuilder();
        sb.append("approverName=").append(sanitize(approverDisplayName));
        sb.append('\n').append("approverKey=").append(approverUserKey != null ? approverUserKey : "");
        sb.append('\n').append("status=").append(approved ? "Approved" : "Rejected");
        sb.append('\n').append("proposedDateMillis=").append(proposedDateMillis != null ? proposedDateMillis : "");
        sb.append('\n').append("proposedDateYmd=").append(proposedDateYmd != null ? proposedDateYmd : "");
        sb.append('\n').append("decidedAt=").append(decidedAt != null ? decidedAt : "");
        return sb.toString();
    }

    private static String sanitize(String v) {
        if (v == null) {
            return "";
        }
        return v.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
