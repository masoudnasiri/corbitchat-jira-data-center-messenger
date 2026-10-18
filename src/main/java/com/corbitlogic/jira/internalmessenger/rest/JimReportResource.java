package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.attachment.CreateAttachmentParamsBean;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.attachment.JimMultipartParser;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileIssueDetail;
import com.corbitlogic.jira.internalmessenger.report.JimReportConfig;
import com.corbitlogic.jira.internalmessenger.report.JimReportService;
import com.corbitlogic.jira.internalmessenger.report.JimReportSummary;
import com.corbitlogic.jira.internalmessenger.service.JimCommentReactionService;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web Report-tab REST (Sprint 12 — Phase C). Backs the issue-view "Report /
 * گزارش" tab: compliance summary, the separated list of report comments, and
 * Submit-report / reply actions. Reports remain ordinary Jira comments carrying
 * the configured marker; this resource never stores report text in plugin
 * tables. All endpoints re-check Jira browse/comment permissions server-side.
 */
@Path(value = "/report")
@Produces(value = {"application/json"})
public class JimReportResource {
    private static final Logger log = LoggerFactory.getLogger(JimReportResource.class);

    private final JiraAuthenticationContext authenticationContext;
    private final JimReportService reportService;
    private final JimCommentReactionService reactionService;

    @Inject
    public JimReportResource(JiraAuthenticationContext authenticationContext, JimReportService reportService,
                             JimCommentReactionService reactionService) {
        this.authenticationContext = authenticationContext;
        this.reportService = reportService;
        this.reactionService = reactionService;
    }

    /** Compliance summary + report list for an issue (browse-gated). */
    @GET
    @Path(value = "/{issueKey}")
    public Response getReport(@PathParam(value = "issueKey") String issueKey) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            JimReportSummary summary = this.reportService.computeSummary(issue);
            Map<String, Object> body = summaryJson(summary);
            body.put("issueKey", issue.getKey());
            body.put("canComment", ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.ADD_COMMENTS, (Issue) issue, viewer));
            body.put("items", reportItemsJson(issue, viewer, summary));
            body.put("marker", JimReportConfig.marker());
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/report/" + issueKey,
                    viewer.getKey(), ex, "internal_error", "An internal error occurred while loading the report.");
        }
    }

    /**
     * List the replies linked to a report comment (Report Replies feature). Replies
     * are real Jira comments that quote the parent report; they are never counted as
     * reports (parser rule). Browse-gated.
     */
    @GET
    @Path(value = "/{issueKey}/replies/{commentId}")
    public Response getReplies(@PathParam(value = "issueKey") String issueKey,
                               @PathParam(value = "commentId") long commentId) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            CommentManager cm = ComponentAccessor.getCommentManager();
            Comment parent = null;
            try {
                parent = cm.getCommentById(commentId);
            } catch (Throwable ignored) {
            }
            if (parent == null || !this.reportService.isReportComment(parent.getBody())) {
                return JimRestResponses.errorJson(404, "not_found", "Report comment not found.");
            }
            Map<Long, List<Comment>> byReport =
                    com.corbitlogic.jira.internalmessenger.report.JimReportReplies.repliesByReport(
                            issue, Collections.singletonList(parent));
            List<Comment> replies = byReport.get(commentId);
            AvatarService avatarService = ComponentAccessor.getComponent(AvatarService.class);
            List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
            if (replies != null) {
                for (Comment c : replies) {
                    Map<String, Object> rm;
                    try {
                        rm = JimMobileIssueDetail.commentMap(issue, c, viewer, avatarService);
                    } catch (Throwable t) {
                        rm = new LinkedHashMap<String, Object>();
                        rm.put("id", c.getId());
                        rm.put("body", c.getBody());
                    }
                    rm.put("parentReportId", commentId);
                    out.add(rm);
                }
            }
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("issueKey", issue.getKey());
            body.put("reportId", commentId);
            body.put("count", out.size());
            body.put("canComment", ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.ADD_COMMENTS, (Issue) issue, viewer));
            body.put("replies", out);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/report/" + issueKey + "/replies/" + commentId,
                    viewer.getKey(), ex, "internal_error", "An internal error occurred while loading replies.");
        }
    }

    /** Submit a report (text only): creates a Jira comment carrying the marker. */
    @POST
    @Path(value = "/{issueKey}/submit")
    @Consumes(value = {"application/json"})
    public Response submitReport(@PathParam(value = "issueKey") String issueKey, Map<String, Object> requestBody) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        String text = requestBody != null && requestBody.get("text") != null
                ? String.valueOf(requestBody.get("text")).trim() : "";
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            return doSubmit(issue, viewer, text, Collections.<String>emptyList());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/report/" + issueKey + "/submit",
                    viewer.getKey(), ex, "internal_error", "An internal error occurred while submitting the report.");
        }
    }

    /** Submit a report WITH a file attachment (multipart: fields file + optional body). */
    @POST
    @Path(value = "/{issueKey}/submit")
    @Consumes(value = {"multipart/form-data"})
    public Response submitReportMultipart(@PathParam(value = "issueKey") String issueKey,
                                          @Context HttpServletRequest request) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            if (!hasAttachPermission(issue, viewer)) {
                return JimRestResponses.errorJson(403, "no_permission",
                        "You do not have permission to attach files to this issue.");
            }
            AttachmentUpload up = parseAndUpload(issue, viewer, request);
            return doSubmit(issue, viewer, up.text, up.tokens);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST(multipart) /rest/jim/1.0/report/" + issueKey + "/submit",
                    viewer.getKey(), ex, "internal_error", "An internal error occurred while submitting the report.");
        }
    }

    /** Reply to a report (text only). Reply is an ordinary comment; not counted. */
    @POST
    @Path(value = "/{issueKey}/reply/{commentId}")
    @Consumes(value = {"application/json"})
    public Response replyToReport(@PathParam(value = "issueKey") String issueKey,
                                  @PathParam(value = "commentId") long commentId,
                                  Map<String, Object> requestBody) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        String text = requestBody != null && requestBody.get("text") != null
                ? String.valueOf(requestBody.get("text")).trim() : "";
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            return doReply(issue, viewer, commentId, text, Collections.<String>emptyList());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/report/" + issueKey + "/reply/" + commentId,
                    viewer.getKey(), ex, "internal_error", "An internal error occurred while posting the reply.");
        }
    }

    /** Reply to a report WITH a file attachment (multipart). */
    @POST
    @Path(value = "/{issueKey}/reply/{commentId}")
    @Consumes(value = {"multipart/form-data"})
    public Response replyToReportMultipart(@PathParam(value = "issueKey") String issueKey,
                                           @PathParam(value = "commentId") long commentId,
                                           @Context HttpServletRequest request) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            if (!hasAttachPermission(issue, viewer)) {
                return JimRestResponses.errorJson(403, "no_permission",
                        "You do not have permission to attach files to this issue.");
            }
            AttachmentUpload up = parseAndUpload(issue, viewer, request);
            return doReply(issue, viewer, commentId, up.text, up.tokens);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST(multipart) /rest/jim/1.0/report/" + issueKey + "/reply/" + commentId,
                    viewer.getKey(), ex, "internal_error", "An internal error occurred while posting the reply.");
        }
    }

    /** Toggle an emoji reaction on a report comment / reply. */
    @POST
    @Path(value = "/{issueKey}/react/{commentId}")
    @Consumes(value = {"application/json"})
    public Response reactToReport(@PathParam(value = "issueKey") String issueKey,
                                  @PathParam(value = "commentId") long commentId,
                                  Map<String, Object> requestBody) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        String emoji = requestBody != null && requestBody.get("emoji") != null
                ? String.valueOf(requestBody.get("emoji")).trim() : "";
        if (emoji.isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "emoji is required");
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            Comment c = ComponentAccessor.getCommentManager().getCommentById(commentId);
            if (c == null || c.getIssue() == null || !c.getIssue().getId().equals(issue.getId())) {
                return JimRestResponses.errorJson(404, "not_found", "Comment not found.");
            }
            List<Map<String, Object>> reactions =
                    this.reactionService.toggle(commentId, issue.getKey(), viewer.getKey(), emoji);
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("commentId", commentId);
            out.put("reactions", reactions);
            return JimRestResponses.okJson(out);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "POST /rest/jim/1.0/report/" + issueKey + "/react/" + commentId,
                    viewer.getKey(), ex, "internal_error", "An internal error occurred while updating the reaction.");
        }
    }

    // --- Shared create paths --------------------------------------------------

    private Response doSubmit(MutableIssue issue, ApplicationUser viewer, String text, List<String> tokens) {
        if (!ComponentAccessor.getPermissionManager()
                .hasPermission(ProjectPermissions.ADD_COMMENTS, (Issue) issue, viewer)) {
            return JimRestResponses.errorJson(403, "no_permission",
                    "You do not have permission to comment on this issue.");
        }
        if (!JimReportConfig.isFeatureEnabled()) {
            return JimRestResponses.errorJson(409, "not_enabled", "Report compliance is not enabled.");
        }
        JimReportSummary pre = this.reportService.computeSummary(issue);
        if (!pre.submitActive) {
            return JimRestResponses.errorJson(409, "reporting_closed", reasonMessage(pre.inactiveReason));
        }
        StringBuilder body = new StringBuilder(JimReportConfig.marker());
        if (text != null && !text.isEmpty()) {
            body.append("\n\n").append(text);
        }
        appendTokens(body, tokens);
        ComponentAccessor.getCommentManager().create(issue, viewer, body.toString(), true);
        return okSummary(issue, viewer);
    }

    private Response doReply(MutableIssue issue, ApplicationUser viewer, long commentId,
                             String text, List<String> tokens) {
        if (!ComponentAccessor.getPermissionManager()
                .hasPermission(ProjectPermissions.ADD_COMMENTS, (Issue) issue, viewer)) {
            return JimRestResponses.errorJson(403, "no_permission",
                    "You do not have permission to comment on this issue.");
        }
        Comment parent = ComponentAccessor.getCommentManager().getCommentById(commentId);
        if (parent == null || parent.getIssue() == null || !parent.getIssue().getId().equals(issue.getId())) {
            return JimRestResponses.errorJson(404, "not_found", "Report comment not found.");
        }
        StringBuilder body = new StringBuilder(buildReplyBody(parent, viewer, text == null ? "" : text));
        appendTokens(body, tokens);
        if (body.toString().trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "Reply text or an attachment is required.");
        }
        ComponentAccessor.getCommentManager().create(issue, viewer, body.toString(), true);
        return okSummary(issue, viewer);
    }

    private Response okSummary(MutableIssue issue, ApplicationUser viewer) {
        JimReportSummary summary = this.reportService.computeSummary(issue);
        Map<String, Object> out = summaryJson(summary);
        out.put("issueKey", issue.getKey());
        out.put("canComment", ComponentAccessor.getPermissionManager()
                .hasPermission(ProjectPermissions.ADD_COMMENTS, (Issue) issue, viewer));
        out.put("items", reportItemsJson(issue, viewer, summary));
        return JimRestResponses.okJson(out);
    }

    private void appendTokens(StringBuilder body, List<String> tokens) {
        if (tokens == null) {
            return;
        }
        for (String t : tokens) {
            if (t != null && !t.isEmpty()) {
                if (body.length() > 0) {
                    body.append("\n\n");
                }
                body.append(t);
            }
        }
    }

    // --- JSON shaping ---------------------------------------------------------

    private Map<String, Object> summaryJson(JimReportSummary s) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("featureEnabled", s.featureEnabled);
        m.put("fieldsConfigured", s.fieldsConfigured);
        m.put("configured", s.configured);
        m.put("applicable", s.applicable);
        m.put("applicabilityReason", s.applicabilityReason);
        // Report ENFORCEMENT policy (scoring scope only; submission/display never blocked).
        m.put("enforcementEnabled", s.enforcementEnabled);
        m.put("enforcementStartDate", s.enforcementStartDate);
        m.put("enforcementDateBasis", s.enforcementDateBasis);
        m.put("fixedCounterStartMode", s.fixedCounterStartMode);
        m.put("inEnforcementScope", s.inEnforcementScope);
        m.put("outOfScopeReason", s.outOfScopeReason);
        m.put("scoringPeriodStartMs",
                (s.scoringPeriodStartMs != Long.MAX_VALUE && s.scoringPeriodStartMs > s.issueCreatedMs)
                        ? Long.valueOf(s.scoringPeriodStartMs) : null);
        m.put("reportRequiredForScoring", s.reportRequiredForScoring);
        m.put("outOfScopeReportCount", s.outOfScopeReportCount);
        m.put("levelCode", s.levelCode);
        m.put("levelText", s.levelText);
        m.put("levelLabelFa", s.levelCode != null ? JimReportConfig.levelLabelFa(s.levelCode) : null);
        m.put("intervalMinutes", s.intervalMinutes);
        m.put("windowStartMinute", s.windowStartMinute);
        m.put("windowEndMinute", s.windowEndMinute);
        m.put("countMode", s.countMode);
        m.put("activeUntil", s.activeUntil);
        m.put("requiredCount", s.requiredCount);
        m.put("validCount", s.validCount);
        m.put("rawCount", s.rawCount);
        m.put("outOfWindowCount", s.outOfWindowCount);
        m.put("outOfScheduleCount", s.outOfScheduleCount);
        m.put("duplicateCount", s.duplicateCount);
        m.put("missedCount", s.missedCount);
        m.put("requiredSoFar", s.requiredSoFar);
        m.put("validSoFar", s.validSoFar);
        m.put("totalRequiredReportsUntilEnd", s.totalRequiredUntilEnd);
        m.put("currentMissedReportStreak", s.currentMissedStreak);
        m.put("maxMissedReportStreak", s.maxMissedStreak);
        m.put("nextExpectedMs", s.nextExpectedMs);
        m.put("compliance", s.compliance);
        m.put("statusCode", s.statusCode);
        m.put("submitActive", s.submitActive);
        m.put("reportRequired", s.reportRequired);
        m.put("reportingControlState", s.reportingControlState);
        m.put("reportingCloseReason", s.reportingCloseReason);
        m.put("inactiveReason", s.inactiveReason);
        m.put("allowWithoutConfig", s.allowWithoutConfig);
        m.put("tzOffsetMinutes", s.tzOffsetMinutes);
        m.put("dueEndMs", s.dueEndMs);
        m.put("doneMs", s.doneMs);
        m.put("dueMissing", s.dueMissing);
        m.put("issueCreatedMs", s.issueCreatedMs);
        m.put("firstReportMs", s.firstReportMs);
        m.put("lastReportMs", s.lastReportMs);
        m.put("lastValidReportMs", s.lastValidReportMs);
        m.put("reportingEndsMs", s.reportingEndsMs);
        m.put("lastReporterName", s.lastReporterName);
        m.put("lastReportSummary", s.lastReportSummary);
        // Working-day + report-receiver pause state (Report Replies & Working-Days).
        m.put("workingDaysEnabled", s.workingDaysEnabled);
        m.put("workingDaysSource", s.workingDaysSource);
        m.put("todayWorkingDay", s.todayWorkingDay);
        // Working calendar (board vs plugin) exposure (Board Working-Days feature).
        m.put("workingCalendarSource", s.workingCalendarSource);
        m.put("workingCalendarResolvedSource", s.workingCalendarResolvedSource);
        m.put("workingCalendarBoardId", s.workingCalendarBoardId > 0 ? Long.valueOf(s.workingCalendarBoardId) : null);
        m.put("workingCalendarBoardName", s.workingCalendarBoardName);
        m.put("workingCalendarFallbackReason", s.workingCalendarFallbackReason);
        m.put("workingWeekdays", s.workingWeekdays);
        m.put("nonWorkingDatesCount", s.nonWorkingDatesCount);
        m.put("todayNonWorkingReason", s.todayNonWorkingReason);
        m.put("nextWorkingDay", s.nextWorkingDay);
        m.put("receiverPauseEnabled", s.receiverPauseEnabled);
        m.put("reportReceiverConfigured", s.reportReceiverConfigured);
        m.put("reportReceiverName", s.reportReceiverName);
        m.put("pausedNow", s.pausedNow);
        m.put("pauseReason", s.pauseReason);
        return m;
    }

    private List<Map<String, Object>> reportItemsJson(Issue issue, ApplicationUser viewer, JimReportSummary s) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        AvatarService avatarService = ComponentAccessor.getComponent(AvatarService.class);
        CommentManager cm = ComponentAccessor.getCommentManager();
        for (JimReportSummary.Item it : s.items) {
            Comment c = null;
            try {
                c = cm.getCommentById(it.commentId);
            } catch (Throwable ignored) {
            }
            if (c == null) {
                continue;
            }
            Map<String, Object> m;
            try {
                m = JimMobileIssueDetail.commentMap(issue, c, viewer, avatarService);
            } catch (Throwable t) {
                m = new LinkedHashMap<String, Object>();
                m.put("id", it.commentId);
                m.put("body", c.getBody());
            }
            m.put("status", it.status); // 0=valid,1=duplicate,2=out-of-window,3=out-of-schedule
            m.put("createdMs", it.createdMs);
            m.put("repliesCount", it.repliesCount);
            m.put("latestReplyPreview", it.latestReplyPreview);
            m.put("latestReplyMs", it.latestReplyMs);
            try {
                m.put("reactions", this.reactionService.listForComment(it.commentId,
                        viewer != null ? viewer.getKey() : null));
            } catch (Throwable ignored) {
                m.put("reactions", new ArrayList<Object>());
            }
            out.add(m);
        }
        return out;
    }

    // --- Helpers --------------------------------------------------------------

    private MutableIssue loadBrowsable(String issueKey, ApplicationUser viewer) {
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return null;
        }
        MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(issueKey.trim());
        if (issue == null) {
            return null;
        }
        if (!ComponentAccessor.getPermissionManager()
                .hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue) issue, viewer)) {
            return null;
        }
        return issue;
    }

    // --- Attachment upload (reuses the shared multipart + Jira attachment API) --

    private static final class AttachmentUpload {
        String text;
        List<String> tokens = Collections.emptyList();
    }

    private boolean hasAttachPermission(Issue issue, ApplicationUser viewer) {
        return ComponentAccessor.getPermissionManager()
                .hasPermission(ProjectPermissions.CREATE_ATTACHMENTS, issue, viewer);
    }

    /** Parse the multipart body, upload the file as a real Jira issue attachment, return text + wiki token. */
    private AttachmentUpload parseAndUpload(Issue issue, ApplicationUser viewer, HttpServletRequest request)
            throws Exception {
        AttachmentUpload up = new AttachmentUpload();
        File tempDir = Files.createTempDirectory("jim-report-upload").toFile();
        try {
            JimMultipartParser.ParsedMultipartForm form = JimMultipartParser.parse(request, tempDir);
            up.text = form.getBody() != null ? form.getBody().trim() : "";
            String token = uploadIssueAttachment(issue, viewer, form);
            up.tokens = token == null ? Collections.<String>emptyList() : Collections.singletonList(token);
            return up;
        } finally {
            try {
                File[] files = tempDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete();
                    }
                }
                tempDir.delete();
            } catch (Throwable ignored) {
            }
        }
    }

    private String uploadIssueAttachment(Issue issue, ApplicationUser viewer,
                                         JimMultipartParser.ParsedMultipartForm form) throws Exception {
        File file = form.getFile();
        if (file == null || !file.exists() || file.length() == 0L) {
            return null;
        }
        String filename = sanitizeAttachmentName(form.getOriginalFilename());
        String contentType = (form.getContentType() != null && !form.getContentType().trim().isEmpty())
                ? form.getContentType().trim() : "application/octet-stream";
        CreateAttachmentParamsBean bean = new CreateAttachmentParamsBean.Builder(
                file, filename, contentType, viewer, issue).build();
        ComponentAccessor.getAttachmentManager().createAttachment(bean);
        return wikiForAttachment(filename, contentType);
    }

    private static String sanitizeAttachmentName(String raw) {
        String name = (raw == null || raw.trim().isEmpty()) ? "attachment" : raw.trim();
        name = name.replaceAll("[!\\[\\]\\|\\\\{}\\r\\n]", "_");
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private static String wikiForAttachment(String filename, String contentType) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        return isImageAttachment(filename, contentType) ? "!" + filename + "|thumbnail!" : "[^" + filename + "]";
    }

    private static boolean isImageAttachment(String filename, String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (ct.matches("^image/(png|jpe?g|gif|webp|bmp|svg\\+xml|tiff?)$")) {
            return true;
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return name.matches(".*\\.(png|jpe?g|gif|webp|bmp|svg|tif|tiff)$");
    }

    /** {@code [~author]\n\n{quote}excerpt{quote}\n\ntext} — same convention as chat/mobile replies. */
    private String buildReplyBody(Comment parent, ApplicationUser viewer, String userText) {
        StringBuilder sb = new StringBuilder();
        ApplicationUser author = parent.getAuthorApplicationUser();
        boolean selfReply = author != null && viewer != null && viewer.getKey().equals(author.getKey());
        if (author != null && !selfReply) {
            sb.append("[~").append(author.getName()).append("]\n\n");
        }
        String excerpt = JimMobileIssueDetail.commentExcerpt(parent.getIssue(), parent, 280);
        if (excerpt != null && !excerpt.isEmpty()) {
            sb.append("{quote}").append(excerpt).append("{quote}\n\n");
        }
        sb.append(userText);
        return sb.toString();
    }

    private String reasonMessage(String reason) {
        if ("done".equals(reason)) {
            return "\u062a\u0633\u06a9 Done \u0634\u062f\u0647 \u0648 \u062b\u0628\u062a \u06af\u0632\u0627\u0631\u0634 \u0641\u0639\u0627\u0644 \u0646\u06cc\u0633\u062a"; // تسک Done شده و ثبت گزارش فعال نیست
        }
        if ("due_passed".equals(reason)) {
            return "\u0645\u0647\u0644\u062a Due Date \u0628\u0647 \u067e\u0627\u06cc\u0627\u0646 \u0631\u0633\u06cc\u062f\u0647 \u0627\u0633\u062a"; // مهلت Due Date به پایان رسیده است
        }
        if ("not_configured".equals(reason) || "no_follow_up_level".equals(reason)) {
            // ثبت گزارش فعال نیست، چون سطح پیگیری یا تنظیمات گزارش‌دهی کامل نیست.
            return "\u062b\u0628\u062a \u06af\u0632\u0627\u0631\u0634 \u0641\u0639\u0627\u0644 \u0646\u06cc\u0633\u062a\u060c \u0686\u0648\u0646 \u0633\u0637\u062d \u067e\u06cc\u06af\u06cc\u0631\u06cc \u06cc\u0627 \u062a\u0646\u0638\u06cc\u0645\u0627\u062a \u06af\u0632\u0627\u0631\u0634\u200c\u062f\u0647\u06cc \u06a9\u0627\u0645\u0644 \u0646\u06cc\u0633\u062a.";
        }
        return "\u0645\u0647\u0644\u062a \u062b\u0628\u062a \u06af\u0632\u0627\u0631\u0634 \u0628\u0647 \u067e\u0627\u06cc\u0627\u0646 \u0631\u0633\u06cc\u062f\u0647 \u0627\u0633\u062a"; // مهلت ثبت گزارش به پایان رسیده است
    }
}
