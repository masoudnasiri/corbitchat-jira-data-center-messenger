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
import com.corbitlogic.jira.internalmessenger.event.CommentEventPayload;
import com.corbitlogic.jira.internalmessenger.event.JimEventExecutor;
import com.corbitlogic.jira.internalmessenger.event.StatusChangeEventPayload;
import com.corbitlogic.jira.internalmessenger.listener.JimAssignmentChangeDetector;
import com.corbitlogic.jira.internalmessenger.listener.JimStatusChangeDetector;
import com.corbitlogic.jira.internalmessenger.service.JimIssueEventHandler;
import com.corbitlogic.jira.internalmessenger.service.JimProjectChatService;
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
    private volatile boolean registered;

    public JimPluginBootstrap(EventPublisher eventPublisher, JimIssueEventHandler issueEventHandler, JimEventExecutor eventExecutor, UserManager userManager, JimProjectChatService projectChatService) {
        this.eventPublisher = eventPublisher;
        this.issueEventHandler = issueEventHandler;
        this.eventExecutor = eventExecutor;
        this.userManager = userManager;
        this.projectChatService = projectChatService;
        this.registerListener();
    }

    public void onStart() {
        this.registerListener();
        this.eventExecutor.submit("project_chat_sync", this.projectChatService::ensureConversationsForAllProjects);
    }

    public void onStop() {
        this.unregisterListener();
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
            if (EventType.ISSUE_COMMENTED_ID.equals(eventTypeId)) {
                this.handleMentionCommentEvent(CommentEventPayload.from(issueEvent), "issue_event");
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

    private void handleMentionCommentEvent(CommentEventPayload payload, String source) {
        if (payload == null) {
            log.debug("event=mention stage=extract outcome=skipped reason=invalid_payload source={}", (Object)source);
            return;
        }
        log.debug("event=mention stage=receive source={} issueKey={} commentId={}", new Object[]{source, payload.getIssueKey(), payload.getCommentId()});
        this.eventExecutor.submit("mention", () -> this.issueEventHandler.handleComment(payload));
    }
}

