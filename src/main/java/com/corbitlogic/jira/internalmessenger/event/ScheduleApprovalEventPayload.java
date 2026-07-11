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
 * Snapshot of a schedule-approval trigger (Sprint 12): an issue that has a
 * proposed completion date + a valid approver, on create OR when the proposed
 * date / approver actually changed. Carries everything the Assistant card needs
 * without re-reading Jira, plus a change-group suffix for dedup.
 */
public final class ScheduleApprovalEventPayload {
    private final String issueKey;
    private final String issueSummary;
    private final String description;
    private final Long proposedDateMillis;
    private final String proposedDateYmd;
    private final String approverUserKey;
    private final String approverDisplayName;
    private final String requesterUserKey;
    private final String requesterDisplayName;
    private final String statusValue;
    private final String changeGroupSuffix;

    private ScheduleApprovalEventPayload(String issueKey, String issueSummary, String description,
                                         Long proposedDateMillis, String proposedDateYmd,
                                         String approverUserKey, String approverDisplayName,
                                         String requesterUserKey, String requesterDisplayName,
                                         String statusValue, String changeGroupSuffix) {
        this.issueKey = issueKey;
        this.issueSummary = issueSummary;
        this.description = description;
        this.proposedDateMillis = proposedDateMillis;
        this.proposedDateYmd = proposedDateYmd;
        this.approverUserKey = approverUserKey;
        this.approverDisplayName = approverDisplayName;
        this.requesterUserKey = requesterUserKey;
        this.requesterDisplayName = requesterDisplayName;
        this.statusValue = statusValue;
        this.changeGroupSuffix = changeGroupSuffix;
    }

    /**
     * Build a payload if this event should raise/refresh a schedule-approval
     * request. Returns null when the feature is not configured, the fields are
     * absent, the approver is missing/inactive, or (for updates) neither the
     * proposed date nor the approver changed.
     */
    public static ScheduleApprovalEventPayload from(IssueEvent event, boolean isCreate) {
        if (event == null || !JimScheduleApprovalConfig.isConfigured()) {
            return null;
        }
        Issue issue = event.getIssue();
        if (issue == null) {
            return null;
        }
        Date proposed = JimScheduleApprovalConfig.getProposedDate(issue);
        if (proposed == null) {
            return null;
        }
        ApplicationUser approver = JimScheduleApprovalConfig.getApprover(issue);
        if (approver == null || !approver.isActive()) {
            return null;
        }
        if (!isCreate && !touchesProposedOrApprover(event)) {
            return null;
        }
        ApplicationUser actor = event.getUser();
        String requesterKey = actor != null ? actor.getKey()
                : (issue.getReporter() != null ? issue.getReporter().getKey() : null);
        String requesterName = actor != null && actor.getDisplayName() != null
                ? actor.getDisplayName()
                : (issue.getReporter() != null ? issue.getReporter().getDisplayName() : "Someone");
        String suffix = isCreate ? "created" : resolveChangeGroupSuffix(event);
        return new ScheduleApprovalEventPayload(
                issue.getKey(), issue.getSummary(), issue.getDescription(),
                proposed.getTime(), JimMobileDates.toYmd(proposed),
                approver.getKey(), approver.getDisplayName(),
                requesterKey, requesterName,
                JimScheduleApprovalConfig.getStatusValue(issue), suffix);
    }

    /** True if the change log touched the proposed-date or approver field. */
    public static boolean touchesProposedOrApprover(IssueEvent event) {
        if (event == null) {
            return false;
        }
        GenericValue changeLog = event.getChangeLog();
        if (changeLog == null) {
            return false;
        }
        String proposedName = JimScheduleApprovalConfig.proposedDateFieldName();
        String approverName = JimScheduleApprovalConfig.approverFieldName();
        try {
            List<GenericValue> items = changeLog.getRelated("ChildChangeItem");
            if (items == null) {
                return false;
            }
            for (GenericValue item : items) {
                String field = item.getString("field");
                if (field == null) {
                    continue;
                }
                if ((proposedName != null && proposedName.equalsIgnoreCase(field))
                        || (approverName != null && approverName.equalsIgnoreCase(field))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
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
        return "update";
    }

    public String getIssueKey() {
        return this.issueKey;
    }

    public String getIssueSummary() {
        return this.issueSummary;
    }

    public String getDescription() {
        return this.description;
    }

    public Long getProposedDateMillis() {
        return this.proposedDateMillis;
    }

    public String getProposedDateYmd() {
        return this.proposedDateYmd;
    }

    public String getApproverUserKey() {
        return this.approverUserKey;
    }

    public String getApproverDisplayName() {
        return this.approverDisplayName;
    }

    public String getRequesterUserKey() {
        return this.requesterUserKey;
    }

    public String getRequesterDisplayName() {
        return this.requesterDisplayName;
    }

    public String getStatusValue() {
        return this.statusValue;
    }

    public String getChangeGroupSuffix() {
        return this.changeGroupSuffix;
    }

    /**
     * Structured metadata persisted in {@code JimMessage.eventData} (one
     * key=value per line). Read back by the mapper + clients. Description is
     * carried in eventText (permission-gated), not here.
     */
    public String toEventData() {
        StringBuilder sb = new StringBuilder();
        sb.append("proposedDateMillis=").append(proposedDateMillis != null ? proposedDateMillis : "");
        sb.append('\n').append("proposedDateYmd=").append(proposedDateYmd != null ? proposedDateYmd : "");
        sb.append('\n').append("requesterKey=").append(requesterUserKey != null ? requesterUserKey : "");
        sb.append('\n').append("requesterName=").append(sanitize(requesterDisplayName));
        sb.append('\n').append("approverKey=").append(approverUserKey != null ? approverUserKey : "");
        sb.append('\n').append("status=").append(statusValue != null ? statusValue : "Pending");
        return sb.toString();
    }

    private static String sanitize(String v) {
        if (v == null) {
            return "";
        }
        return v.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
