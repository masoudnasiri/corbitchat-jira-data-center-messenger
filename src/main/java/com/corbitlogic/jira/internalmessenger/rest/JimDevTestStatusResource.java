/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.permission.GlobalPermissionKey
 *  com.atlassian.jira.security.GlobalPermissionManager
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  javax.inject.Inject
 *  javax.ws.rs.Consumes
 *  javax.ws.rs.POST
 *  javax.ws.rs.Path
 *  javax.ws.rs.Produces
 *  javax.ws.rs.core.Response
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.dto.DevTestStatusRequestDto;
import com.corbitlogic.jira.internalmessenger.model.JimEventType;
import com.corbitlogic.jira.internalmessenger.rest.JimRestJsonMapper;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimMessageService;
import com.corbitlogic.jira.internalmessenger.util.JimConversationKeys;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.util.LinkedHashMap;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(value="/dev/test-status-card")
@Consumes(value={"application/json"})
@Produces(value={"application/json"})
public class JimDevTestStatusResource {
    private static final Logger log = LoggerFactory.getLogger(JimDevTestStatusResource.class);
    private final JiraAuthenticationContext authenticationContext;
    private final GlobalPermissionManager globalPermissionManager;
    private final JimMessageService messageService;
    private final JimRestJsonMapper restJsonMapper;

    @Inject
    public JimDevTestStatusResource(JiraAuthenticationContext authenticationContext, GlobalPermissionManager globalPermissionManager, JimMessageService messageService, JimRestJsonMapper restJsonMapper) {
        this.authenticationContext = authenticationContext;
        this.globalPermissionManager = globalPermissionManager;
        this.messageService = messageService;
        this.restJsonMapper = restJsonMapper;
    }

    @POST
    public Response createTestStatusCard(DevTestStatusRequestDto request) {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        if (!this.globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, user)) {
            return JimRestResponses.errorJson(403, "forbidden", "Jira administrator permission is required");
        }
        if (request == null || request.getTargetUserKey() == null) {
            return JimRestResponses.errorJson(400, "bad_request", "targetUserKey is required");
        }
        try {
            String targetUserKey = JimValidation.requireNonBlank(request.getTargetUserKey(), "targetUserKey");
            String issueKey = request.getIssueKey() != null ? request.getIssueKey().trim() : "TEST-1";
            String issueSummary = request.getIssueSummary() != null ? request.getIssueSummary().trim() : "Status change regression test";
            String actorDisplayName = request.getActorDisplayName() != null ? request.getActorDisplayName().trim() : "Test Actor";
            String fromStatus = request.getFromStatus() != null ? request.getFromStatus().trim() : "To Do";
            String toStatus = request.getToStatus() != null ? request.getToStatus().trim() : "In Progress";
            String issueUrl = request.getIssueUrl() != null && !request.getIssueUrl().trim().isEmpty() ? request.getIssueUrl().trim() : "/browse/" + issueKey;
            String body = actorDisplayName + " changed " + issueKey + " from " + fromStatus + " to " + toStatus + ".";
            String fingerprint = JimConversationKeys.buildEventFingerprint(JimEventType.STATUS_CHANGE.name(), targetUserKey, issueKey, "dev-test", "dev-status-" + fromStatus + "-" + toStatus + "-" + System.currentTimeMillis());
            JimMessage message = this.messageService.createSystemMessage(targetUserKey, JimEventType.STATUS_CHANGE, null, actorDisplayName, issueKey, issueSummary, issueUrl, body, fingerprint);
            if (message == null) {
                return JimRestResponses.errorJson(409, "duplicate_event", "A test status card with this fingerprint already exists.");
            }
            LinkedHashMap<String, Object> responseBody = new LinkedHashMap<String, Object>();
            responseBody.put("ok", true);
            responseBody.put("messageId", message.getID());
            responseBody.put("message", this.restJsonMapper.toMessageMap(message, user));
            return JimRestResponses.okJson(responseBody);
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/dev/test-status-card", user.getKey(), ex, "internal_error", "An internal error occurred while creating the test status card.");
        }
    }
}

