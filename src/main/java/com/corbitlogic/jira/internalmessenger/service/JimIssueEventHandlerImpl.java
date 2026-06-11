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

import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.event.AssignmentEventPayload;
import com.corbitlogic.jira.internalmessenger.event.CommentEventPayload;
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
            List<ApplicationUser> mentionedUsers = this.mentionParser.parseMentionedUsersAsList(payload.getCommentBody());
            if (mentionedUsers.isEmpty()) {
                log.debug("event=mention stage=process outcome=skipped reason=no_mentions issueKey={} commentId={}", (Object)payload.getIssueKey(), (Object)payload.getCommentId());
                return;
            }
            String excerpt = this.mentionParser.excerpt(payload.getCommentBody(), 200);
            String commentSuffix = payload.getCommentId() != null ? String.valueOf(payload.getCommentId()) : "unknown";
            for (ApplicationUser mentionedUser : mentionedUsers) {
                if (mentionedUser == null || !this.permissionService.isActiveUser(mentionedUser.getKey())) {
                    log.debug("event=mention stage=process outcome=skipped reason=inactive_user issueKey={} commentId={} userKey={}", new Object[]{payload.getIssueKey(), payload.getCommentId(), mentionedUser != null ? mentionedUser.getKey() : "null"});
                    continue;
                }
                if (payload.getActorUserKey() != null && payload.getActorUserKey().equals(mentionedUser.getKey())) {
                    log.debug("event=mention stage=process outcome=skipped reason=self_mention issueKey={} commentId={} userKey={}", new Object[]{payload.getIssueKey(), payload.getCommentId(), mentionedUser.getKey()});
                    continue;
                }
                String body = payload.getActorDisplayName() + " mentioned you in " + payload.getIssueKey() + ".";
                if (!excerpt.isEmpty()) {
                    body = body + " \"" + excerpt + "\"";
                }
                String fingerprint = JimConversationKeys.buildEventFingerprint(JimEventType.MENTION.name(), mentionedUser.getKey(), payload.getIssueKey(), payload.getActorUserKey(), commentSuffix);
                this.createAssistantMessageSafely(mentionedUser.getKey(), JimEventType.MENTION, payload.getActorUserKey(), payload.getActorDisplayName(), payload.getIssueKey(), payload.getIssueSummary(), JimIssueUrlBuilder.buildBrowseUrl(this.applicationProperties, payload.getIssueKey()), JimValidation.truncatePreview(body), fingerprint);
            }
        }
        catch (Exception ex) {
            log.error("event=mention stage=process outcome=error issueKey={} commentId={} message={}", new Object[]{payload.getIssueKey(), payload.getCommentId(), ex.getMessage(), ex});
        }
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

    private void createAssistantMessageSafely(String targetUserKey, JimEventType eventType, String actorUserKey, String actorDisplayName, String issueKey, String issueSummary, String issueUrl, String body, String fingerprint) {
        try {
            if (!this.permissionService.isActiveUser(targetUserKey)) {
                log.debug("event={} stage=create outcome=skipped reason=inactive_user targetUserKey={} issueKey={}", new Object[]{eventType.name(), targetUserKey, issueKey});
                return;
            }
            JimMessage message = this.messageService.createSystemMessage(targetUserKey, eventType, actorUserKey, actorDisplayName, issueKey, issueSummary, issueUrl, body, fingerprint);
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

