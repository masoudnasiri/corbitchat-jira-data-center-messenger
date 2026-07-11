package com.corbitlogic.jira.internalmessenger.schedule;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.model.JimEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side orchestration for a schedule-approval decision from an Assistant
 * card (web + mobile share this). Enforces: the card is a SCHEDULE_APPROVAL
 * card, the viewer can browse the issue, and the viewer IS the selected
 * approver (customfield_11305). Only then does it write the status + reason +
 * audit comment. Never trusts the client button alone.
 */
public final class JimScheduleApprovalActions {
    private static final Logger log = LoggerFactory.getLogger(JimScheduleApprovalActions.class);

    public static final class Result {
        public final int statusCode;      // 0 = success
        public final String errorCode;
        public final String errorMessage;

        private Result(int statusCode, String errorCode, String errorMessage) {
            this.statusCode = statusCode;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public boolean isOk() {
            return this.statusCode == 0;
        }

        static Result ok() {
            return new Result(0, null, null);
        }

        static Result error(int code, String errorCode, String message) {
            return new Result(code, errorCode, message);
        }
    }

    private JimScheduleApprovalActions() {
    }

    public static Result execute(JimMessage message, ApplicationUser viewer, String action, String reason) {
        if (message == null || !JimEventType.SCHEDULE_APPROVAL.name().equals(message.getEventType())
                || message.getIssueKey() == null) {
            return Result.error(400, "bad_request", "Not a schedule-approval notification.");
        }
        return executeForIssue(message.getIssueKey(), viewer, action, reason);
    }

    /** Issue-key based decision (used by the Dashboard pending list). */
    public static Result executeForIssue(String issueKey, ApplicationUser viewer, String action, String reason) {
        if (viewer == null) {
            return Result.error(401, "unauthorized", "User is not authenticated.");
        }
        if (!JimScheduleApprovalConfig.isConfigured()) {
            return Result.error(409, "not_configured", "Schedule approval fields are not configured on this environment.");
        }
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return Result.error(400, "bad_request", "issueKey is required.");
        }
        boolean approved;
        if ("approve".equalsIgnoreCase(action)) {
            approved = true;
        } else if ("reject".equalsIgnoreCase(action)) {
            approved = false;
        } else {
            return Result.error(400, "bad_request", "action must be 'approve' or 'reject'.");
        }
        String cleanReason = reason != null ? reason.trim() : "";
        if (!approved) {
            if (cleanReason.isEmpty()) {
                return Result.error(400, "reason_required", "A rejection reason is required.");
            }
            if (cleanReason.length() > JimScheduleApprovalConfig.MAX_REASON_LENGTH) {
                cleanReason = cleanReason.substring(0, JimScheduleApprovalConfig.MAX_REASON_LENGTH);
            }
        }
        try {
            MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(issueKey.trim());
            if (issue == null || !ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue) issue, viewer)) {
                return Result.error(404, "not_found", "Issue not found or you do not have permission to view it.");
            }
            ApplicationUser approver = JimScheduleApprovalConfig.getApprover(issue);
            if (approver == null || !approver.getKey().equals(viewer.getKey())) {
                return Result.error(403, "not_approver", "Only the selected approver can approve or reject this request.");
            }
            boolean ok = JimScheduleApprovalConfig.applyDecision(issue, viewer, approved, cleanReason);
            if (!ok) {
                return Result.error(500, "update_failed", "Could not update the approval status.");
            }
            JimScheduleApprovalConfig.addDecisionComment(issue, viewer, approved, cleanReason);
            log.info("event=schedule stage=decision outcome=success issueKey={} approverKey={} approved={}",
                    new Object[]{issueKey, viewer.getKey(), approved});
            return Result.ok();
        } catch (Exception ex) {
            log.error("event=schedule stage=decision outcome=error issueKey={} message={}",
                    new Object[]{issueKey, ex.getMessage(), ex});
            return Result.error(500, "internal_error", "An internal error occurred while processing the decision.");
        }
    }
}
