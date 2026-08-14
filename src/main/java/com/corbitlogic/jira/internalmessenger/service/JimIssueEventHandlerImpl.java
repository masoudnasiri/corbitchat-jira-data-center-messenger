/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.config.properties.ApplicationProperties
 *  com.atlassian.jira.user.ApplicationUser
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.report.JimReportConfig;
import com.corbitlogic.jira.internalmessenger.report.JimReportReplies;
import com.corbitlogic.jira.internalmessenger.report.ReportResponsibilityService;
import com.corbitlogic.jira.internalmessenger.event.AssignmentEventPayload;
import com.corbitlogic.jira.internalmessenger.event.AttachmentEventPayload;
import com.corbitlogic.jira.internalmessenger.event.CommentEventPayload;
import com.corbitlogic.jira.internalmessenger.event.ScheduleApprovalEventPayload;
import com.corbitlogic.jira.internalmessenger.event.ScheduleResultEventPayload;
import com.corbitlogic.jira.internalmessenger.event.StatusChangeEventPayload;
import com.corbitlogic.jira.internalmessenger.model.JimEventType;
import com.corbitlogic.jira.internalmessenger.parser.JimMentionParser;
import com.corbitlogic.jira.internalmessenger.service.JimIssueEventHandler;
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.util.JimConversationKeys;
import com.corbitlogic.jira.internalmessenger.util.JimIssueUrlBuilder;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimIssueEventHandlerImpl
implements JimIssueEventHandler {
    private static final Logger log = LoggerFactory.getLogger(JimIssueEventHandlerImpl.class);
    private static final int COMMENT_EXCERPT_LENGTH = 200;
    private static final int MENTION_TEXT_LENGTH = 10000;
    private final JimMessageService messageService;
    private final JimPermissionService permissionService;
    private final JimMentionParser mentionParser;
    private final ApplicationProperties applicationProperties;

    public JimIssueEventHandlerImpl(JimMessageService messageService, JimPermissionService permissionService, JimMentionParser mentionParser, ApplicationProperties applicationProperties) {
        this.messageService = messageService;
        this.permissionService = permissionService;
        this.mentionParser = mentionParser;
        this.applicationProperties = applicationProperties;
    }

    @Override
    public void handleAssignment(AssignmentEventPayload payload) {
        if (payload == null) {
            return;
        }
        try {
            String actorName = payload.getActorDisplayName();
            String body = actorName != null && !actorName.trim().isEmpty() ? actorName + " assigned " + payload.getIssueKey() + " to you." : "Jira assigned " + payload.getIssueKey() + " to you.";
            String fingerprint = JimConversationKeys.buildEventFingerprint(JimEventType.ASSIGNMENT.name(), payload.getAssigneeUserKey(), payload.getIssueKey(), payload.getActorUserKey(), payload.getChangeGroupSuffix());
            this.createAssistantMessageSafely(payload.getAssigneeUserKey(), JimEventType.ASSIGNMENT, payload.getActorUserKey(), actorName != null ? actorName : "Jira", payload.getIssueKey(), payload.getIssueSummary(), JimIssueUrlBuilder.buildBrowseUrl(this.applicationProperties, payload.getIssueKey()), body, fingerprint);
        }
        catch (Exception ex) {
            log.error("event=assignment stage=process outcome=error issueKey={} assigneeUserKey={} message={}", new Object[]{payload.getIssueKey(), payload.getAssigneeUserKey(), ex.getMessage(), ex});
        }
    }

    @Override
    public void handleComment(CommentEventPayload payload) {
        if (payload == null) {
            return;
        }
        try {
            String body = payload.getCommentBody();
            String issueKey = payload.getIssueKey();
            String actorKey = payload.getActorUserKey();
            String actorName = payload.getActorDisplayName() != null ? payload.getActorDisplayName() : "Someone";
            Long commentId = payload.getCommentId();
            String commentSuffix = commentId != null ? String.valueOf(commentId) : "unknown";
            String issueUrl = JimIssueUrlBuilder.buildBrowseUrl(this.applicationProperties, issueKey);
            // FULL comment body stored as eventText (permission re-gated at read); card
            // body stays a short non-sensitive label.
            String fullText = this.mentionParser.renderReadable(body, MENTION_TEXT_LENGTH);

            // --- Report context (submission / reply / none) ---
            Issue issue = loadIssue(issueKey);
            boolean isSubmission = JimReportConfig.isReportSubmissionBody(body);
            Comment parentReport = (!isSubmission && issue != null && commentId != null)
                    ? JimReportReplies.parentReportFor(issue, commentId, body) : null;
            boolean isReply = parentReport != null;
            boolean reportContext = isSubmission || isReply;
            boolean hasAttachment = bodyHasAttachment(body);

            List<ApplicationUser> mentionedUsers = this.mentionParser.parseMentionedUsersAsList(body);
            if (mentionedUsers.isEmpty() && !reportContext) {
                log.debug("event=comment stage=process outcome=skipped reason=no_mentions_no_report issueKey={} commentId={}",
                        (Object) issueKey, (Object) commentId);
                return;
            }

            // Users already notified for THIS comment (dedupe: one card per person per action).
            java.util.Set<String> notified = new java.util.HashSet<String>();

            // Report receiver = schedule-approval approver (same source as the report pause rule).
            ApplicationUser receiver = issue != null ? ReportResponsibilityService.receiver(issue) : null;
            String receiverKey = receiver != null ? receiver.getKey() : null;

            // 1) Report SUBMISSION -> notify the report receiver/team lead (REPORT_SUBMITTED_TO_ME).
            //    Recipient is resolved from the approver field only (never guessed from assignee).
            if (isSubmission && receiverKey != null && this.permissionService.isActiveUser(receiverKey)
                    && !receiverKey.equals(actorKey)) {
                String label = actorName + " submitted a report on " + issueKey + " for you.";
                String eventData = "recipientReason=report_receiver\nreportId=" + commentSuffix
                        + (hasAttachment ? "\nhasAttachment=true" : "");
                String fp = JimConversationKeys.buildEventFingerprint(
                        JimEventType.REPORT_SUBMITTED_TO_ME.name(), receiverKey, issueKey, actorKey, commentSuffix);
                this.createAssistantMessageSafely(receiverKey, JimEventType.REPORT_SUBMITTED_TO_ME, actorKey, actorName,
                        issueKey, payload.getIssueSummary(), issueUrl, JimValidation.truncatePreview(label), fullText, eventData, fp);
                notified.add(receiverKey);
            }

            // 2) Report REPLY -> notify the parent report author and the report receiver (REPORT_REPLY).
            if (isReply) {
                String parentReportId = parentReport.getId() != null ? String.valueOf(parentReport.getId()) : "";
                java.util.LinkedHashMap<String, String> replyTargets = new java.util.LinkedHashMap<String, String>();
                ApplicationUser parentAuthor = parentReport.getAuthorApplicationUser();
                if (parentAuthor != null && parentAuthor.getKey() != null) {
                    replyTargets.put(parentAuthor.getKey(), "parent_author");
                }
                if (receiverKey != null && !replyTargets.containsKey(receiverKey)) {
                    replyTargets.put(receiverKey, "report_receiver");
                }
                for (java.util.Map.Entry<String, String> e : replyTargets.entrySet()) {
                    String tk = e.getKey();
                    if (tk == null || tk.equals(actorKey) || notified.contains(tk)
                            || !this.permissionService.isActiveUser(tk)) {
                        continue;
                    }
                    String label = actorName + " replied to a report on " + issueKey + ".";
                    String eventData = "recipientReason=" + e.getValue() + "\nreportId=" + commentSuffix
                            + "\nparentReportId=" + parentReportId + (hasAttachment ? "\nhasAttachment=true" : "");
                    String fp = JimConversationKeys.buildEventFingerprint(
                            JimEventType.REPORT_REPLY.name(), tk, issueKey, actorKey, commentSuffix);
                    this.createAssistantMessageSafely(tk, JimEventType.REPORT_REPLY, actorKey, actorName,
                            issueKey, payload.getIssueSummary(), issueUrl, JimValidation.truncatePreview(label), fullText, eventData, fp);
                    notified.add(tk);
                }
            }

            // 3) Mentions -> REPORT_MENTION inside a report/reply, else MENTION. Skip anyone already
            //    notified for this comment (avoids duplicate cards for the receiver/parent author).
            for (ApplicationUser mentionedUser : mentionedUsers) {
                if (mentionedUser == null || !this.permissionService.isActiveUser(mentionedUser.getKey())) {
                    continue;
                }
                String mk = mentionedUser.getKey();
                if (actorKey != null && actorKey.equals(mk)) {
                    continue; // self-mention
                }
                if (notified.contains(mk)) {
                    continue; // already got a receiver/reply card for this comment
                }
                JimEventType type = reportContext ? JimEventType.REPORT_MENTION : JimEventType.MENTION;
                String label = reportContext
                        ? (actorName + " mentioned you in a report on " + issueKey + ".")
                        : (actorName + " mentioned you in " + issueKey + ".");
                String eventData = reportContext
                        ? ("recipientReason=mentioned\nreportContext=" + (isSubmission ? "report" : "reply")
                            + (isReply ? "\nparentReportId=" + parentReport.getId() : "")
                            + (hasAttachment ? "\nhasAttachment=true" : ""))
                        : null;
                String fp = JimConversationKeys.buildEventFingerprint(type.name(), mk, issueKey, actorKey, commentSuffix);
                this.createAssistantMessageSafely(mk, type, actorKey, actorName, issueKey, payload.getIssueSummary(),
                        issueUrl, JimValidation.truncatePreview(label), fullText, eventData, fp);
                notified.add(mk);
                try {
                    this.messageService.reconcileMentionWithStandaloneAttachment(mk, issueKey);
                } catch (Exception ignore) {
                    // best-effort
                }
            }
        }
        catch (Exception ex) {
            log.error("event=comment stage=process outcome=error issueKey={} commentId={} message={}", new Object[]{payload.getIssueKey(), payload.getCommentId(), ex.getMessage(), ex});
        }
    }

    /** Load the Jira issue by key, or null on any failure (never throws to the caller). */
    private Issue loadIssue(String issueKey) {
        try {
            return issueKey == null ? null : ComponentAccessor.getIssueManager().getIssueByCurrentKey(issueKey);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Cheap check whether a comment body embeds a Jira attachment wiki token. */
    private static boolean bodyHasAttachment(String body) {
        if (body == null) {
            return false;
        }
        return body.contains("[^") || body.matches("(?s).*!\\S+\\|thumbnail!.*");
    }

    @Override
    public void handleStatusChange(StatusChangeEventPayload payload) {
        if (payload == null) {
            return;
        }
        try {
            String actorName = payload.getActorDisplayName();
            String body = actorName != null && !actorName.trim().isEmpty() ? actorName + " changed " + payload.getIssueKey() + " from " + payload.getFromStatus() + " to " + payload.getToStatus() + "." : "Jira changed " + payload.getIssueKey() + " from " + payload.getFromStatus() + " to " + payload.getToStatus() + ".";
            String fingerprintSuffix = payload.getChangeGroupId() + "|" + payload.getFromStatus() + "|" + payload.getToStatus();
            log.debug("event=status stage=process outcome=recipients issueKey={} recipientCount={} fromStatus={} toStatus={}", new Object[]{payload.getIssueKey(), payload.getRecipientUserKeys().size(), payload.getFromStatus(), payload.getToStatus()});
            for (String targetUserKey : payload.getRecipientUserKeys()) {
                String fingerprint = JimConversationKeys.buildEventFingerprint(JimEventType.STATUS_CHANGE.name(), targetUserKey, payload.getIssueKey(), payload.getActorUserKey(), fingerprintSuffix);
                this.createAssistantMessageSafely(targetUserKey, JimEventType.STATUS_CHANGE, payload.getActorUserKey(), actorName != null ? actorName : "Jira", payload.getIssueKey(), payload.getIssueSummary(), JimIssueUrlBuilder.buildBrowseUrl(this.applicationProperties, payload.getIssueKey()), body, fingerprint);
            }
        }
        catch (Exception ex) {
            log.error("event=status stage=process outcome=error issueKey={} message={}", new Object[]{payload.getIssueKey(), ex.getMessage(), ex});
        }
    }

    @Override
    public void handleAttachment(AttachmentEventPayload payload) {
        if (payload == null) {
            return;
        }
        try {
            String issueKey = payload.getIssueKey();
            String eventDataJson = payload.toEventData();
            int fileCount = payload.getFiles().size();
            String actorName = payload.getActorDisplayName();
            // (1) Fold the attachments into any recent mention card for this
            // issue (same comment/update): those users get the COMBINED card
            // rather than a separate attachment-only notification.
            java.util.Set<String> mentionRecipients =
                    this.messageService.attachFilesToRecentMentions(issueKey, eventDataJson);
            // (2) Standalone "file attached" cards for the issue's assignee /
            // reporter who were NOT mentioned (and are not the uploader).
            String issueUrl = JimIssueUrlBuilder.buildBrowseUrl(this.applicationProperties, issueKey);
            String body = (actorName != null && !actorName.trim().isEmpty() ? actorName : "Someone")
                    + (fileCount > 1
                        ? " attached " + fileCount + " files to " + issueKey + "."
                        : " attached a file to " + issueKey + ".");
            for (String targetUserKey : payload.getRecipientUserKeys()) {
                if (targetUserKey == null || targetUserKey.trim().isEmpty()) {
                    continue;
                }
                if (payload.getActorUserKey() != null && payload.getActorUserKey().equals(targetUserKey)) {
                    continue; // never notify the uploader
                }
                if (mentionRecipients.contains(targetUserKey)) {
                    continue; // already delivered as a combined mention card
                }
                String fingerprint = JimConversationKeys.buildEventFingerprint(
                        JimEventType.ISSUE_ATTACHMENT.name(), targetUserKey, issueKey,
                        payload.getActorUserKey(), payload.getChangeGroupSuffix());
                this.createAssistantMessageSafely(targetUserKey, JimEventType.ISSUE_ATTACHMENT,
                        payload.getActorUserKey(), actorName, issueKey, payload.getIssueSummary(),
                        issueUrl, body, null, eventDataJson, fingerprint);
            }
        } catch (Exception ex) {
            log.error("event=attachment stage=process outcome=error issueKey={} message={}",
                    new Object[]{payload.getIssueKey(), ex.getMessage(), ex});
        }
    }

    @Override
    public void handleScheduleApproval(ScheduleApprovalEventPayload payload) {
        if (payload == null) {
            return;
        }
        try {
            String approverKey = payload.getApproverUserKey();
            if (approverKey == null || !this.permissionService.isActiveUser(approverKey)) {
                return;
            }
            // Never notify a requester who selected themselves as approver in a
            // way that would be pointless — but self-approval IS allowed by the
            // business, so we still notify (they must act as approver).
            String body = payload.getRequesterDisplayName() + " requested your approval of the proposed completion date for "
                    + payload.getIssueKey() + ".";
            // Full description is carried in eventText (permission-gated at read).
            String description = payload.getDescription();
            String fullText = null;
            if (description != null && !description.trim().isEmpty()) {
                String trimmed = description.trim();
                fullText = trimmed.length() > MENTION_TEXT_LENGTH
                        ? trimmed.substring(0, MENTION_TEXT_LENGTH - 3) + "..." : trimmed;
            }
            String fingerprint = JimConversationKeys.buildEventFingerprint(
                    JimEventType.SCHEDULE_APPROVAL.name(), approverKey, payload.getIssueKey(),
                    payload.getRequesterUserKey(), payload.getChangeGroupSuffix());
            this.createAssistantMessageSafely(approverKey, JimEventType.SCHEDULE_APPROVAL,
                    payload.getRequesterUserKey(), payload.getRequesterDisplayName(), payload.getIssueKey(),
                    payload.getIssueSummary(), JimIssueUrlBuilder.buildBrowseUrl(this.applicationProperties, payload.getIssueKey()),
                    JimValidation.truncatePreview(body), fullText, payload.toEventData(), fingerprint);
        } catch (Exception ex) {
            log.error("event=schedule stage=process outcome=error issueKey={} message={}",
                    new Object[]{payload.getIssueKey(), ex.getMessage(), ex});
        }
    }

    @Override
    public void handleScheduleResult(ScheduleResultEventPayload payload) {
        if (payload == null) {
            return;
        }
        try {
            String requesterKey = this.messageService.findScheduleRequesterKey(payload.getIssueKey());
            if (requesterKey == null || !this.permissionService.isActiveUser(requesterKey)) {
                return;
            }
            // Don't notify the approver about their own decision.
            if (payload.getApproverUserKey() != null && payload.getApproverUserKey().equals(requesterKey)) {
                return;
            }
            JimEventType type = payload.isApproved()
                    ? JimEventType.SCHEDULE_APPROVAL_APPROVED : JimEventType.SCHEDULE_APPROVAL_REJECTED;
            String body = payload.isApproved()
                    ? "Your proposed completion date for " + payload.getIssueKey() + " was approved by " + payload.getApproverDisplayName() + "."
                    : "Your proposed completion date for " + payload.getIssueKey() + " was rejected by " + payload.getApproverDisplayName() + ".";
            // Rejection reason travels in eventText (permission-gated at read).
            String eventText = payload.isApproved() ? null : payload.getRejectionReason();
            String fingerprint = JimConversationKeys.buildEventFingerprint(
                    type.name(), requesterKey, payload.getIssueKey(),
                    payload.getApproverUserKey(), payload.getChangeGroupSuffix());
            this.createAssistantMessageSafely(requesterKey, type,
                    payload.getApproverUserKey(), payload.getApproverDisplayName(), payload.getIssueKey(),
                    payload.getIssueSummary(), JimIssueUrlBuilder.buildBrowseUrl(this.applicationProperties, payload.getIssueKey()),
                    JimValidation.truncatePreview(body), eventText, payload.toEventData(), fingerprint);
        } catch (Exception ex) {
            log.error("event=schedule_result stage=process outcome=error issueKey={} message={}",
                    new Object[]{payload.getIssueKey(), ex.getMessage(), ex});
        }
    }

    private void createAssistantMessageSafely(String targetUserKey, JimEventType eventType, String actorUserKey, String actorDisplayName, String issueKey, String issueSummary, String issueUrl, String body, String fingerprint) {
        this.createAssistantMessageSafely(targetUserKey, eventType, actorUserKey, actorDisplayName, issueKey, issueSummary, issueUrl, body, null, null, fingerprint);
    }

    private void createAssistantMessageSafely(String targetUserKey, JimEventType eventType, String actorUserKey, String actorDisplayName, String issueKey, String issueSummary, String issueUrl, String body, String eventText, String eventData, String fingerprint) {
        try {
            if (!this.permissionService.isActiveUser(targetUserKey)) {
                log.debug("event={} stage=create outcome=skipped reason=inactive_user targetUserKey={} issueKey={}", new Object[]{eventType.name(), targetUserKey, issueKey});
                return;
            }
            JimMessage message = this.messageService.createSystemMessage(targetUserKey, eventType, actorUserKey, actorDisplayName, issueKey, issueSummary, issueUrl, body, eventText, eventData, fingerprint);
            if (message == null) {
                log.debug("event={} stage=create outcome=skipped reason=duplicate targetUserKey={} issueKey={} fingerprint={}", new Object[]{eventType.name(), targetUserKey, issueKey, fingerprint});
                return;
            }
            log.info("event={} stage=create outcome=success targetUserKey={} issueKey={} messageId={} fingerprint={}", new Object[]{eventType.name(), targetUserKey, issueKey, message.getID(), fingerprint});
        }
        catch (Exception ex) {
            log.error("event={} stage=create outcome=error targetUserKey={} issueKey={} message={}", new Object[]{eventType.name(), targetUserKey, issueKey, ex.getMessage(), ex});
        }
    }
}

