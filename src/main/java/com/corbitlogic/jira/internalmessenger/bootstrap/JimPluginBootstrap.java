/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.event.api.EventListener
 *  com.atlassian.event.api.EventPublisher
 *  com.atlassian.jira.event.ProjectCreatedEvent
 *  com.atlassian.jira.event.comment.CommentCreatedEvent
 *  com.atlassian.jira.event.issue.IssueEvent
 *  com.atlassian.jira.event.type.EventType
 *  com.atlassian.jira.project.Project
 *  com.atlassian.jira.user.util.UserManager
 *  com.atlassian.sal.api.lifecycle.LifecycleAware
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.bootstrap;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.event.ProjectCreatedEvent;
import com.atlassian.jira.event.comment.CommentCreatedEvent;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.corbitlogic.jira.internalmessenger.event.AssignmentEventPayload;
import com.corbitlogic.jira.internalmessenger.event.AttachmentEventPayload;
import com.corbitlogic.jira.internalmessenger.event.CommentEventPayload;
import com.corbitlogic.jira.internalmessenger.event.JimEventExecutor;
import com.corbitlogic.jira.internalmessenger.event.ScheduleApprovalEventPayload;
import com.corbitlogic.jira.internalmessenger.event.ScheduleResultEventPayload;
import com.corbitlogic.jira.internalmessenger.event.StatusChangeEventPayload;
import com.corbitlogic.jira.internalmessenger.listener.JimAssignmentChangeDetector;
import com.corbitlogic.jira.internalmessenger.listener.JimAttachmentChangeDetector;
import com.corbitlogic.jira.internalmessenger.listener.JimStatusChangeDetector;
import com.corbitlogic.jira.internalmessenger.report.JimReportConfig;
import com.corbitlogic.jira.internalmessenger.report.JimReportRecalcCoordinator;
import com.corbitlogic.jira.internalmessenger.report.JimReportService;
import com.corbitlogic.jira.internalmessenger.report.JimReportServiceImpl;
import com.corbitlogic.jira.internalmessenger.service.JimIssueEventHandler;
import com.corbitlogic.jira.internalmessenger.service.JimProjectChatService;
import java.util.List;
import org.ofbiz.core.entity.GenericValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimPluginBootstrap
implements LifecycleAware {
    private static final Logger log = LoggerFactory.getLogger(JimPluginBootstrap.class);
    private final EventPublisher eventPublisher;
    private final JimIssueEventHandler issueEventHandler;
    private final JimEventExecutor eventExecutor;
    private final UserManager userManager;
    private final JimProjectChatService projectChatService;
    private final JimReportService reportService;
    private final JimReportRecalcCoordinator reportRecalcCoordinator;
    private volatile boolean registered;

    public JimPluginBootstrap(EventPublisher eventPublisher, JimIssueEventHandler issueEventHandler, JimEventExecutor eventExecutor, UserManager userManager, JimProjectChatService projectChatService, JimReportService reportService, JimReportRecalcCoordinator reportRecalcCoordinator) {
        this.eventPublisher = eventPublisher;
        this.issueEventHandler = issueEventHandler;
        this.eventExecutor = eventExecutor;
        this.userManager = userManager;
        this.projectChatService = projectChatService;
        this.reportService = reportService;
        this.reportRecalcCoordinator = reportRecalcCoordinator;
        this.registerListener();
    }

    public void onStart() {
        this.registerListener();
        this.eventExecutor.submit("project_chat_sync", this.projectChatService::ensureConversationsForAllProjects);
    }

    public void onStop() {
        this.unregisterListener();
        if (this.reportRecalcCoordinator != null) {
            this.reportRecalcCoordinator.shutdown();
        }
    }

    public boolean isAssignmentListenerRegistered() {
        return this.registered;
    }

    public boolean isMentionListenerRegistered() {
        return this.registered;
    }

    public boolean isStatusChangeListenerRegistered() {
        return this.registered;
    }

    private void registerListener() {
        if (this.registered) {
            return;
        }
        this.eventPublisher.register((Object)this);
        this.registered = true;
        log.info("event=plugin stage=listener outcome=registered handlers=assignment,mention,status");
    }

    private void unregisterListener() {
        if (!this.registered) {
            return;
        }
        this.eventPublisher.unregister((Object)this);
        this.registered = false;
        log.info("event=plugin stage=listener outcome=unregistered");
    }

    @EventListener
    public void onIssueEvent(IssueEvent issueEvent) {
        try {
            boolean updatedWithAssigneeChange;
            if (issueEvent == null || issueEvent.getEventTypeId() == null) {
                return;
            }
            Long eventTypeId = issueEvent.getEventTypeId();
            // A comment + attachment added in one action can surface either on
            // the same IssueEvent or as adjacent events; detect attachment adds
            // on ANY issue event so the Assistant can build a combined card.
            boolean attachmentAdd = JimAttachmentChangeDetector.hasAttachmentAdd(issueEvent);
            if (EventType.ISSUE_COMMENTED_ID.equals(eventTypeId)) {
                this.handleMentionCommentEvent(CommentEventPayload.from(issueEvent), "issue_event");
                if (attachmentAdd) {
                    this.handleAttachmentEvent(issueEvent);
                }
                // A new comment may be a report → recompute compliance counters.
                this.handleReportRecalc(issueEvent);
                return;
            }
            boolean explicitAssignment = EventType.ISSUE_ASSIGNED_ID.equals(eventTypeId);
            boolean bl = updatedWithAssigneeChange = EventType.ISSUE_UPDATED_ID.equals(eventTypeId) && JimAssignmentChangeDetector.isAssigneeChange(issueEvent);
            if (explicitAssignment || updatedWithAssigneeChange) {
                this.handleAssignmentEvent(issueEvent, eventTypeId, explicitAssignment, updatedWithAssigneeChange);
            }
            if (JimPluginBootstrap.isStatusChangeCandidateEvent(eventTypeId) && JimStatusChangeDetector.isStatusChange(issueEvent)) {
                this.handleStatusChangeEvent(issueEvent);
            }
            if (attachmentAdd) {
                this.handleAttachmentEvent(issueEvent);
            }
            // Schedule-approval workflow (proposed completion date). On create,
            // or when the proposed date / approver changed on an update, notify
            // the approver. No-op unless the workflow fields are configured.
            if (EventType.ISSUE_CREATED_ID.equals(eventTypeId)) {
                this.handleScheduleApprovalEvent(issueEvent, true);
                this.handleReportRecalc(issueEvent);
            } else if (EventType.ISSUE_UPDATED_ID.equals(eventTypeId)
                    || EventType.ISSUE_GENERICEVENT_ID.equals(eventTypeId)) {
                this.handleScheduleApprovalEvent(issueEvent, false);
                // Approval decision (status → Approved/Rejected): notify requester.
                this.handleScheduleResultEvent(issueEvent);
                // Recompute report compliance (follow-up level, due date or Done
                // status may have changed). No-op unless operational; the write
                // is change-guarded so it cannot loop this event.
                this.handleReportRecalc(issueEvent);
            }
        }
        catch (Exception ex) {
            log.error("event=issue stage=listener outcome=error message={}", (Object)ex.getMessage(), (Object)ex);
        }
    }

    @EventListener
    public void onProjectCreated(ProjectCreatedEvent projectCreatedEvent) {
        try {
            Project project;
            Project project2 = project = projectCreatedEvent != null ? projectCreatedEvent.getProject() : null;
            if (project == null) {
                return;
            }
            log.info("event=project_chat stage=listener projectKey={}", (Object)project.getKey());
            this.eventExecutor.submit("project_chat_create", () -> this.projectChatService.ensureProjectConversation(project));
        }
        catch (Exception ex) {
            log.error("event=project_chat stage=listener outcome=error message={}", (Object)ex.getMessage(), (Object)ex);
        }
    }

    @EventListener
    public void onCommentCreated(CommentCreatedEvent commentCreatedEvent) {
        try {
            this.handleMentionCommentEvent(CommentEventPayload.from(commentCreatedEvent), "comment_created");
        }
        catch (Exception ex) {
            log.error("event=mention stage=listener outcome=error message={}", (Object)ex.getMessage(), (Object)ex);
        }
    }

    private static boolean isStatusChangeCandidateEvent(Long eventTypeId) {
        return EventType.ISSUE_UPDATED_ID.equals(eventTypeId) || EventType.ISSUE_GENERICEVENT_ID.equals(eventTypeId);
    }

    private void handleAssignmentEvent(IssueEvent issueEvent, Long eventTypeId, boolean explicitAssignment, boolean updatedWithAssigneeChange) {
        log.debug("event=assignment stage=receive issueKey={} eventTypeId={} explicitAssignment={} updatedWithAssigneeChange={}", new Object[]{issueEvent.getIssue() != null ? issueEvent.getIssue().getKey() : "unknown", eventTypeId, explicitAssignment, updatedWithAssigneeChange});
        AssignmentEventPayload payload = AssignmentEventPayload.from(issueEvent, this.userManager);
        if (payload == null) {
            log.debug("event=assignment stage=extract outcome=skipped reason=invalid_payload");
            return;
        }
        log.info("event=assignment stage=detect outcome=matched issueKey={} assigneeUserKey={}", (Object)payload.getIssueKey(), (Object)payload.getAssigneeUserKey());
        this.eventExecutor.submit("assignment", () -> this.issueEventHandler.handleAssignment(payload));
    }

    private void handleStatusChangeEvent(IssueEvent issueEvent) {
        log.debug("event=status stage=receive issueKey={}", (Object)(issueEvent.getIssue() != null ? issueEvent.getIssue().getKey() : "unknown"));
        StatusChangeEventPayload payload = StatusChangeEventPayload.from(issueEvent);
        if (payload == null) {
            log.debug("event=status stage=extract outcome=skipped reason=invalid_payload");
            return;
        }
        log.info("event=status stage=detect outcome=matched issueKey={} fromStatus={} toStatus={} recipientCount={}", new Object[]{payload.getIssueKey(), payload.getFromStatus(), payload.getToStatus(), payload.getRecipientUserKeys().size()});
        this.eventExecutor.submit("status", () -> this.issueEventHandler.handleStatusChange(payload));
    }

    private void handleScheduleApprovalEvent(IssueEvent issueEvent, boolean isCreate) {
        try {
            ScheduleApprovalEventPayload payload = ScheduleApprovalEventPayload.from(issueEvent, isCreate);
            if (payload == null) {
                return;
            }
            // Ensure the status is Pending for a fresh/changed request (elevated,
            // loop-safe) so the card and Dashboard pending list reflect it even
            // when the field had no value yet.
            try {
                com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalConfig
                        .resetToPending(issueEvent.getIssue(), issueEvent.getUser());
            } catch (Exception ignore) {
                // best-effort; notification still proceeds
            }
            log.info("event=schedule stage=detect outcome=matched issueKey={} approverKey={} isCreate={}",
                    new Object[]{payload.getIssueKey(), payload.getApproverUserKey(), isCreate});
            this.eventExecutor.submit("schedule_approval",
                    () -> this.issueEventHandler.handleScheduleApproval(payload));
        } catch (Exception ex) {
            log.debug("event=schedule stage=receive outcome=error message={}", (Object) ex.getMessage());
        }
    }

    private void handleScheduleResultEvent(IssueEvent issueEvent) {
        try {
            ScheduleResultEventPayload payload = ScheduleResultEventPayload.from(issueEvent);
            if (payload == null) {
                return;
            }
            log.info("event=schedule_result stage=detect outcome=matched issueKey={} approved={}",
                    new Object[]{payload.getIssueKey(), payload.isApproved()});
            this.eventExecutor.submit("schedule_result",
                    () -> this.issueEventHandler.handleScheduleResult(payload));
        } catch (Exception ex) {
            log.debug("event=schedule_result stage=receive outcome=error message={}", (Object) ex.getMessage());
        }
    }

    private void handleAttachmentEvent(IssueEvent issueEvent) {
        AttachmentEventPayload payload = AttachmentEventPayload.from(issueEvent);
        if (payload == null) {
            log.debug("event=attachment stage=extract outcome=skipped reason=invalid_payload");
            return;
        }
        log.info("event=attachment stage=detect outcome=matched issueKey={} fileCount={} recipientCount={}",
                new Object[]{payload.getIssueKey(), payload.getFiles().size(), payload.getRecipientUserKeys().size()});
        this.eventExecutor.submit("attachment", () -> this.issueEventHandler.handleAttachment(payload));
    }

    /**
     * Relevance-filtered, coalesced report recalculation. Only a comment add, an
     * issue create, or an update whose change items touch status / due date / the
     * report level field enqueues work; everything else is skipped (debug log).
     * Enqueue goes through the coordinator, which coalesces bursts and submits the
     * real work onto the maintenance lane of {@link JimEventExecutor}.
     */
    private void handleReportRecalc(IssueEvent issueEvent) {
        try {
            if (issueEvent == null || issueEvent.getIssue() == null) {
                return;
            }
            // Belt-and-suspenders: ignore events that surface on the same thread
            // that is currently writing report metrics (should not happen with
            // DO_NOT_DISPATCH, but keeps us loop-proof).
            if (JimReportServiceImpl.isReportWriteInProgress()) {
                log.debug("event=report stage=filter outcome=skip reason=self_write_in_progress");
                return;
            }
            String reason = reportRecalcReason(issueEvent);
            if (reason == null) {
                log.debug("event=report stage=filter outcome=skip issueKey={} reason=irrelevant_change",
                        (Object) issueEvent.getIssue().getKey());
                return;
            }
            this.reportRecalcCoordinator.enqueue(issueEvent.getIssue(), issueEvent.getUser(), reason);
        } catch (Exception ex) {
            log.debug("event=report stage=receive outcome=error message={}", (Object) ex.getMessage());
        }
    }

    /**
     * Trigger reason when the event is report-relevant, else null (skip). Comment
     * adds and creates always qualify; updates/generic events qualify only when a
     * change item touches status, due date, or the report level custom field.
     */
    private static String reportRecalcReason(IssueEvent issueEvent) {
        Long typeId = issueEvent.getEventTypeId();
        if (EventType.ISSUE_COMMENTED_ID.equals(typeId)) {
            return "comment_add";
        }
        if (EventType.ISSUE_CREATED_ID.equals(typeId)) {
            return "issue_create";
        }
        if (EventType.ISSUE_UPDATED_ID.equals(typeId) || EventType.ISSUE_GENERICEVENT_ID.equals(typeId)) {
            String fields = relevantChangedFields(issueEvent);
            return fields != null ? "field_update:" + fields : null;
        }
        return null;
    }

    /**
     * Comma-joined list of report-relevant fields changed by this event
     * (status / duedate / report level field), or null when none. On an
     * unexpected changelog error we return {@code "unknown"} so recalc still runs
     * (fail-open — the fingerprint gate will drop it if nothing really changed).
     */
    private static String relevantChangedFields(IssueEvent issueEvent) {
        GenericValue changeLog = issueEvent.getChangeLog();
        if (changeLog == null) {
            return null;
        }
        try {
            List<GenericValue> items = changeLog.getRelated("ChildChangeItem");
            if (items == null || items.isEmpty()) {
                return null;
            }
            String levelFieldName = reportLevelFieldName();
            StringBuilder hits = new StringBuilder();
            for (GenericValue item : items) {
                String field = item.getString("field");
                if (field == null) {
                    continue;
                }
                boolean relevant = "status".equalsIgnoreCase(field)
                        || "duedate".equalsIgnoreCase(field)
                        || (levelFieldName != null && levelFieldName.equalsIgnoreCase(field));
                if (relevant) {
                    if (hits.length() > 0) {
                        hits.append(",");
                    }
                    hits.append(field.toLowerCase());
                }
            }
            return hits.length() > 0 ? hits.toString() : null;
        } catch (Exception ex) {
            log.debug("event=report stage=filter outcome=changelog_error message={}", (Object) ex.getMessage());
            return "unknown";
        }
    }

    private static String reportLevelFieldName() {
        try {
            com.atlassian.jira.issue.fields.CustomField cf = JimReportConfig.resolveLevelField();
            return cf != null ? cf.getName() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private void handleMentionCommentEvent(CommentEventPayload payload, String source) {
        if (payload == null) {
            log.debug("event=mention stage=extract outcome=skipped reason=invalid_payload source={}", (Object)source);
            return;
        }
        log.debug("event=mention stage=receive source={} issueKey={} commentId={}", new Object[]{source, payload.getIssueKey(), payload.getCommentId()});
        this.eventExecutor.submit("mention", () -> this.issueEventHandler.handleComment(payload));
    }
}

