package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.issue.worklog.WorklogInputParameters;
import com.atlassian.jira.bc.issue.worklog.WorklogInputParametersImpl;
import com.atlassian.jira.bc.issue.worklog.WorklogResult;
import com.atlassian.jira.bc.issue.worklog.WorklogService;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.issue.attachment.CreateAttachmentParamsBean;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.AttachmentUtils;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.workflow.IssueWorkflowManager;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.query.Query;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentStorageService;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentStreaming;
import com.corbitlogic.jira.internalmessenger.attachment.JimMultipartParser;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileIssueDetail;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileIssues;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimMobileFeatureService;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mobile BFF issue search (Sprint 03). Mounted under
 * {@code /rest/corbit-mobile/1.0/issues}.
 *
 * <p>The client selects a whitelisted <em>filter token</em> only
 * ({@code assigned|today|overdue|followup}); the server maps it to a bounded
 * JQL query ({@link JimMobileIssues}) and runs it through
 * {@code SearchService.search(user, ...)}, which enforces the caller's Jira
 * Browse-Project / issue security. Raw JQL is never accepted and results are
 * always paginated. No issue fields beyond the projection are logged.</p>
 */
@Path("/issues")
@Produces({"application/json"})
@AnonymousAllowed
public class CorbitMobileIssueResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobileIssueResource.class);

    private final JiraAuthenticationContext authenticationContext;
    private final SearchService searchService;
    private final AvatarService avatarService;
    private final ApplicationProperties applicationProperties;
    private final JimMobileFeatureService featureService;
    private final JimAttachmentStorageService attachmentStorageService;

    @Inject
    public CorbitMobileIssueResource(JiraAuthenticationContext authenticationContext,
                                     SearchService searchService,
                                     AvatarService avatarService,
                                     ApplicationProperties applicationProperties,
                                     JimMobileFeatureService featureService,
                                     JimAttachmentStorageService attachmentStorageService) {
        this.authenticationContext = authenticationContext;
        this.searchService = searchService;
        this.avatarService = avatarService;
        this.applicationProperties = applicationProperties;
        this.featureService = featureService;
        this.attachmentStorageService = attachmentStorageService;
    }

    @GET
    @Path("/search")
    public Response search(@QueryParam("filter") String filter,
                           @QueryParam("startAt") @DefaultValue("0") int startAt,
                           @QueryParam("maxResults") @DefaultValue("20") int maxResults) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.TASKS)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.TASKS);
        }
        if (!JimMobileIssues.isValidFilter(filter)) {
            return JimRestResponses.errorJson(400, "bad_request",
                    "filter must be one of " + JimMobileIssues.FILTERS);
        }
        int start = JimMobileIssues.normalizeStartAt(startAt);
        int max = JimMobileIssues.normalizePageSize(maxResults);
        try {
            Query query = JimMobileIssues.buildQuery(viewer, this.searchService, filter);
            PagerFilter<Issue> pager = new PagerFilter<>(max);
            pager.setStart(start);
            SearchResults<Issue> results = this.searchService.search(viewer, query, pager);

            List<Map<String, Object>> issues = new ArrayList<>();
            for (Issue issue : results.getResults()) {
                issues.add(JimMobileIssues.toIssueMap(issue, viewer, this.avatarService, this.applicationProperties));
            }
            int total = results.getTotal();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("filter", filter);
            body.put("startAt", start);
            body.put("maxResults", max);
            body.put("total", total);
            body.put("isLast", start + issues.size() >= total);
            body.put("issues", issues);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/issues/search?filter=" + filter,
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while loading issues.");
        }
    }

    /**
     * Full, read-only issue detail —
     * {@code GET /rest/corbit-mobile/1.0/issues/{issueKey}}.
     *
     * <p>The issue is loaded by key and the viewer's Browse-Project permission
     * (and issue-level security) is enforced before any field is projected. A
     * missing issue and a permission failure return the SAME 404 so the endpoint
     * never reveals whether an issue key exists.</p>
     */
    @GET
    @Path("/{issueKey}")
    public Response getIssue(@PathParam("issueKey") String issueKey) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.ISSUE_DETAIL)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.ISSUE_DETAIL);
        }
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "issueKey is required.");
        }
        String key = issueKey.trim();
        try {
            MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(key);
            if (issue == null || !ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue) issue, viewer)) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            Map<String, Object> body = JimMobileIssueDetail.toDetailMap(
                    issue, viewer, this.avatarService, this.applicationProperties);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/issues/{issueKey}",
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while loading the issue.");
        }
    }

    /**
     * Add a new Jira comment to an issue —
     * {@code POST /rest/corbit-mobile/1.0/issues/{issueKey}/comments} with
     * body {@code {"body": "..."}}.
     *
     * <p>Creates a real, native Jira comment through {@link CommentManager} with
     * event dispatch on, so Jira's own notifications and the plugin's mention →
     * Assistant → push pipeline fire exactly as they do for a web comment. The
     * caller must be able to Browse the issue (404 otherwise, never leaking key
     * existence) AND have Add-Comments permission (403 otherwise).</p>
     */
    @POST
    @Path("/{issueKey}/comments")
    @Consumes({"application/json"})
    public Response addComment(@PathParam("issueKey") String issueKey,
                               Map<String, Object> requestBody) {
        return createComment(issueKey, null, requestBody);
    }

    /**
     * Reply to an existing Jira comment —
     * {@code POST /rest/corbit-mobile/1.0/issues/{issueKey}/comments/{commentId}/replies}
     * with body {@code {"body": "..."}}.
     *
     * <p>There is no native comment threading in this Jira version. Mirroring the
     * web reply composer ({@code jim-comment-reply.js}) exactly, this creates a
     * normal Jira comment whose wiki body is:</p>
     * <pre>
     *   [~parentAuthor]
     *
     *   {quote}&lt;parent excerpt&gt;{quote}
     *
     *   &lt;user reply text&gt;
     * </pre>
     * <p>The {@code [~mention]} is omitted on a self-reply (author replying to
     * their own comment), identical to the web behavior. This guarantees the
     * reply reads correctly in Jira web and re-uses the existing mention pipeline
     * for notifications — no new events or listeners are introduced.</p>
     */
    @POST
    @Path("/{issueKey}/comments/{commentId}/replies")
    @Consumes({"application/json"})
    public Response replyToComment(@PathParam("issueKey") String issueKey,
                                   @PathParam("commentId") long commentId,
                                   Map<String, Object> requestBody) {
        return createComment(issueKey, commentId, requestBody);
    }

    private Response createComment(String issueKey, Long parentCommentId,
                                   Map<String, Object> requestBody) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.ISSUE_DETAIL)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.ISSUE_DETAIL);
        }
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "issueKey is required.");
        }
        String text = trimToNull(requestBody != null ? requestBody.get("body") : null);
        if (text == null) {
            return JimRestResponses.errorJson(400, "bad_request", "Comment body is required.");
        }
        String key = issueKey.trim();
        try {
            MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(key);
            if (issue == null || !ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue) issue, viewer)) {
                // Same 404 as GET so we never reveal whether the key exists.
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            if (!ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.ADD_COMMENTS, (Issue) issue, viewer)) {
                return JimRestResponses.errorJson(403, "no_permission",
                        "You do not have permission to comment on this issue.");
            }
            return assembleAndCreate(issue, viewer, parentCommentId, text, Collections.emptyList());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/issues/{issueKey}/comments",
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while saving the comment.");
        }
    }

    // --- Multipart comment/reply (with a Jira issue attachment) ---------------

    /**
     * Add a comment WITH an attachment —
     * {@code POST /issues/{issueKey}/comments} as {@code multipart/form-data}
     * (fields {@code file} required, {@code body} optional). Mirrors the web
     * reply composer: the file is uploaded as a real <em>Jira issue attachment</em>
     * and linked into the comment via a wiki token ({@code !name|thumbnail!} for
     * images, {@code [^name]} otherwise), so the comment renders and round-trips
     * identically in Jira web. Selected by Content-Type — the JSON
     * {@link #addComment} handler is unchanged for text-only comments.
     */
    @POST
    @Path("/{issueKey}/comments")
    @Consumes({"multipart/form-data"})
    public Response addCommentMultipart(@PathParam("issueKey") String issueKey,
                                        @Context HttpServletRequest request) {
        return createCommentMultipart(issueKey, null, request);
    }

    /** Reply WITH an attachment — multipart variant of {@link #replyToComment}. */
    @POST
    @Path("/{issueKey}/comments/{commentId}/replies")
    @Consumes({"multipart/form-data"})
    public Response replyToCommentMultipart(@PathParam("issueKey") String issueKey,
                                            @PathParam("commentId") long commentId,
                                            @Context HttpServletRequest request) {
        return createCommentMultipart(issueKey, commentId, request);
    }

    private Response createCommentMultipart(String issueKey, Long parentCommentId,
                                            HttpServletRequest request) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.ISSUE_DETAIL)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.ISSUE_DETAIL);
        }
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "issueKey is required.");
        }
        String key = issueKey.trim();
        try {
            MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(key);
            if (issue == null || !ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue) issue, viewer)) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            if (!ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.ADD_COMMENTS, (Issue) issue, viewer)) {
                return JimRestResponses.errorJson(403, "no_permission",
                        "You do not have permission to comment on this issue.");
            }
            // A multipart request always carries a file → Create-Attachments is required.
            if (!ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.CREATE_ATTACHMENTS, (Issue) issue, viewer)) {
                return JimRestResponses.errorJson(403, "no_permission",
                        "You do not have permission to attach files to this issue.");
            }

            File tempDirectory = this.attachmentStorageService.createUploadTempDirectory();
            JimMultipartParser.ParsedMultipartForm form =
                    JimMultipartParser.parse(request, tempDirectory);
            String text = trimToNull(form.getBody());
            String token = uploadIssueAttachment(issue, viewer, form);
            List<String> tokens = token == null
                    ? Collections.<String>emptyList()
                    : Collections.singletonList(token);
            return assembleAndCreate(issue, viewer, parentCommentId,
                    text == null ? "" : text, tokens);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST (multipart) /rest/corbit-mobile/1.0/issues/{issueKey}/comments",
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while saving the comment.");
        }
    }

    /**
     * Shared body assembly + comment creation for both the JSON and multipart
     * paths. Builds the reply wiki (mention + quote) when replying, appends any
     * attachment wiki tokens, and creates a native Jira comment with event
     * dispatch on (so notifications + the Assistant mention pipeline fire).
     */
    private Response assembleAndCreate(Issue issue, ApplicationUser viewer,
                                       Long parentCommentId, String userText,
                                       List<String> attachmentTokens) {
        String base;
        if (parentCommentId != null) {
            CommentManager cm = ComponentAccessor.getCommentManager();
            Comment parent = findVisibleComment(cm, issue, viewer, parentCommentId);
            if (parent == null) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Comment not found or you do not have permission to view it.");
            }
            base = buildReplyBody(issue, parent, viewer, userText == null ? "" : userText);
        } else {
            base = userText == null ? "" : userText;
        }
        StringBuilder sb = new StringBuilder(base == null ? "" : base);
        if (attachmentTokens != null) {
            for (String t : attachmentTokens) {
                if (t == null || t.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(t);
            }
        }
        String finalBody = sb.toString();
        if (finalBody.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request",
                    "Comment body or an attachment is required.");
        }
        if (finalBody.length() > MAX_COMMENT_CHARS) {
            finalBody = finalBody.substring(0, MAX_COMMENT_CHARS);
        }
        Comment created = ComponentAccessor.getCommentManager()
                .create(issue, viewer, finalBody, true);
        return JimRestResponses.okJson(
                JimMobileIssueDetail.commentMap(issue, created, viewer, this.avatarService));
    }

    /**
     * Upload the parsed multipart file as a real Jira issue attachment and return
     * the wiki token that links it into the comment body. The SAME sanitized
     * filename is used for both the stored attachment and the token so Jira
     * resolves the token to the attachment.
     */
    private String uploadIssueAttachment(Issue issue, ApplicationUser viewer,
                                         JimMultipartParser.ParsedMultipartForm form) throws Exception {
        File file = form.getFile();
        if (file == null || !file.exists() || file.length() == 0L) {
            return null;
        }
        String filename = sanitizeAttachmentName(form.getOriginalFilename());
        String contentType = (form.getContentType() != null && !form.getContentType().trim().isEmpty())
                ? form.getContentType().trim()
                : "application/octet-stream";
        CreateAttachmentParamsBean bean = new CreateAttachmentParamsBean.Builder(
                file, filename, contentType, viewer, issue).build();
        ComponentAccessor.getAttachmentManager().createAttachment(bean);
        return wikiForAttachment(filename, contentType);
    }

    private static String sanitizeAttachmentName(String raw) {
        String name = (raw == null || raw.trim().isEmpty()) ? "attachment" : raw.trim();
        // Strip wiki-significant characters so the token matches the stored name.
        name = name.replaceAll("[!\\[\\]\\|\\\\{}\\r\\n]", "_");
        if (name.length() > 200) {
            name = name.substring(0, 200);
        }
        return name;
    }

    private static String wikiForAttachment(String filename, String contentType) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        return isImageAttachment(filename, contentType)
                ? "!" + filename + "|thumbnail!"
                : "[^" + filename + "]";
    }

    private static boolean isImageAttachment(String filename, String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (ct.matches("^image/(png|jpe?g|gif|webp|bmp|svg\\+xml|tiff?)$")) {
            return true;
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return name.matches(".*\\.(png|jpe?g|gif|webp|bmp|svg|tif|tiff)$");
    }

    // --- Issue attachment streaming (session-authed, for comment attachments) --

    /**
     * Stream a Jira issue attachment for download —
     * {@code GET /issues/{issueKey}/attachments/{attachmentId}/download}. Serves
     * attachments referenced by comment bodies over the session-authenticated
     * mobile path (a mobile session cannot use Jira's cookie-authed
     * {@code /secure/attachment/*}). The viewer must be able to browse the issue.
     */
    @GET
    @Path("/{issueKey}/attachments/{attachmentId}/download")
    public Response downloadIssueAttachment(@PathParam("issueKey") String issueKey,
                                            @PathParam("attachmentId") long attachmentId,
                                            @HeaderParam("Range") String rangeHeader) {
        return streamIssueAttachment(issueKey, attachmentId, false, rangeHeader);
    }

    /** Inline preview of an issue attachment (image/audio/video). */
    @GET
    @Path("/{issueKey}/attachments/{attachmentId}/preview")
    public Response previewIssueAttachment(@PathParam("issueKey") String issueKey,
                                           @PathParam("attachmentId") long attachmentId,
                                           @HeaderParam("Range") String rangeHeader) {
        return streamIssueAttachment(issueKey, attachmentId, true, rangeHeader);
    }

    private Response streamIssueAttachment(String issueKey, long attachmentId,
                                           boolean inlinePreview, String rangeHeader) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.ISSUE_DETAIL)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.ISSUE_DETAIL);
        }
        if (issueKey == null || issueKey.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "issueKey is required.");
        }
        String key = issueKey.trim();
        try {
            MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey(key);
            if (issue == null || !ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.BROWSE_PROJECTS, (Issue) issue, viewer)) {
                return JimRestResponses.errorJson(404, "not_found",
                        "Issue not found or you do not have permission to view it.");
            }
            Attachment attachment = ComponentAccessor.getAttachmentManager().getAttachment(attachmentId);
            if (attachment == null || attachment.getIssue() == null
                    || !issue.getId().equals(attachment.getIssue().getId())) {
                return JimRestResponses.errorJson(404, "not_found", "Attachment not found.");
            }
            File file = AttachmentUtils.getAttachmentFile(attachment);
            if (file == null || !file.exists()) {
                return JimRestResponses.errorJson(404, "not_found", "Attachment file not found.");
            }
            String contentType = attachment.getMimetype() != null
                    ? attachment.getMimetype() : "application/octet-stream";
            String disposition = (inlinePreview && isInlineType(contentType)) ? "inline" : "attachment";
            return JimAttachmentStreaming.stream(file, contentType,
                    attachment.getFilename(), disposition, rangeHeader);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/issues/{issueKey}/attachments/{id}",
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while loading the attachment.");
        }
    }

    private static boolean isInlineType(String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return ct.startsWith("image/") || ct.startsWith("audio/") || ct.startsWith("video/");
    }

    // ====================================================================
    //  Sprint 10 — issue actions (all run AS the mobile user, so Jira
    //  permission/workflow/field rules apply automatically).
    // ====================================================================

    /** Available workflow transitions for the viewer (condition + permission aware). */
    @GET
    @Path("/{issueKey}/transitions")
    public Response getTransitions(@PathParam("issueKey") String issueKey) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.ISSUE_DETAIL)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.ISSUE_DETAIL);
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return notFound();
            }
            List<Map<String, Object>> list = new ArrayList<>();
            IssueWorkflowManager iwm = ComponentAccessor.getComponentOfType(IssueWorkflowManager.class);
            Collection<ActionDescriptor> actions = iwm.getAvailableActions(issue, viewer);
            JiraWorkflow wf = null;
            try {
                wf = ComponentAccessor.getWorkflowManager().getWorkflow(issue);
            } catch (Exception ignored) {
            }
            List<ActionDescriptor> sorted = new ArrayList<>(
                    actions == null ? Collections.<ActionDescriptor>emptyList() : actions);
            sorted.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
            for (ActionDescriptor a : sorted) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", a.getId());
                m.put("name", a.getName());
                String view = a.getView();
                m.put("hasScreen", view != null && !view.trim().isEmpty());
                if (wf != null && a.getUnconditionalResult() != null) {
                    try {
                        int stepId = a.getUnconditionalResult().getStep();
                        if (stepId > 0) {
                            Status st = wf.getLinkedStatusObject(wf.getDescriptor().getStep(stepId));
                            if (st != null) {
                                Map<String, Object> to = new LinkedHashMap<>();
                                to.put("name", st.getName());
                                to.put("category", st.getStatusCategory() != null
                                        ? st.getStatusCategory().getKey() : null);
                                m.put("to", to);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                list.add(m);
            }
            return JimRestResponses.okJson(JimRestResponses.singleEntry("transitions", list));
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/issues/{issueKey}/transitions",
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while loading transitions.");
        }
    }

    /** Execute a workflow transition. Body: {"id":31,"comment":"...","resolution":"Done"}. */
    @POST
    @Path("/{issueKey}/transitions")
    @Consumes({"application/json"})
    public Response doTransition(@PathParam("issueKey") String issueKey,
                                 Map<String, Object> body) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.ISSUE_DETAIL)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.ISSUE_DETAIL);
        }
        Integer actionId = intOrNull(body != null ? body.get("id") : null);
        if (actionId == null) {
            return JimRestResponses.errorJson(400, "bad_request", "A transition id is required.");
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return notFound();
            }
            IssueService issueService = ComponentAccessor.getIssueService();
            IssueInputParameters iip = issueService.newIssueInputParameters();
            String comment = trimToNull(body.get("comment"));
            if (comment != null) {
                iip.setComment(comment);
            }
            String resolution = trimToNull(body.get("resolution"));
            if (resolution != null) {
                String resId = resolutionId(resolution);
                if (resId != null) {
                    iip.setResolutionId(resId);
                }
            }
            IssueService.TransitionValidationResult tvr =
                    issueService.validateTransition(viewer, issue.getId(), actionId, iip);
            if (!tvr.isValid()) {
                return validationError(tvr.getErrorCollection());
            }
            IssueService.IssueResult res = issueService.transition(viewer, tvr);
            if (!res.isValid()) {
                return validationError(res.getErrorCollection());
            }
            return detail(issue.getKey(), viewer);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/issues/{issueKey}/transitions",
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while transitioning the issue.");
        }
    }

    /** Field metadata the mobile edit form needs (allowed values Jira controls). */
    @GET
    @Path("/{issueKey}/editmeta")
    public Response getEditMeta(@PathParam("issueKey") String issueKey) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.ISSUE_DETAIL)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.ISSUE_DETAIL);
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return notFound();
            }
            ConstantsManager cm = ComponentAccessor.getConstantsManager();
            List<Map<String, Object>> priorities = new ArrayList<>();
            for (Priority p : cm.getPriorities()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.getId());
                m.put("name", p.getName());
                m.put("iconUrl", p.getIconUrl());
                priorities.add(m);
            }
            List<Map<String, Object>> resolutions = new ArrayList<>();
            for (Resolution r : cm.getResolutions()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.getId());
                m.put("name", r.getName());
                resolutions.add(m);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("priorities", priorities);
            out.put("resolutions", resolutions);
            return JimRestResponses.okJson(out);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/issues/{issueKey}/editmeta",
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while loading edit metadata.");
        }
    }

    /** Edit a curated set of fields. Body may contain summary/description/priority. */
    @PUT
    @Path("/{issueKey}")
    @Consumes({"application/json"})
    public Response editIssue(@PathParam("issueKey") String issueKey,
                              Map<String, Object> body) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.ISSUE_DETAIL)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.ISSUE_DETAIL);
        }
        if (body == null || body.isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "No fields to update.");
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return notFound();
            }
            if (!ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.EDIT_ISSUES, (Issue) issue, viewer)) {
                return JimRestResponses.errorJson(403, "no_permission",
                        "You do not have permission to edit this issue.");
            }
            IssueService issueService = ComponentAccessor.getIssueService();
            IssueInputParameters iip = issueService.newIssueInputParameters();
            boolean any = false;
            if (body.containsKey("summary")) {
                String summary = trimToNull(body.get("summary"));
                if (summary == null) {
                    return JimRestResponses.errorJson(400, "bad_request", "Summary cannot be empty.");
                }
                iip.setSummary(summary);
                any = true;
            }
            if (body.containsKey("description")) {
                String desc = body.get("description") == null ? "" : String.valueOf(body.get("description"));
                iip.setDescription(desc);
                any = true;
            }
            if (body.containsKey("priority")) {
                String pr = trimToNull(body.get("priority"));
                if (pr != null) {
                    iip.setPriorityId(pr);
                    any = true;
                }
            }
            if (!any) {
                return JimRestResponses.errorJson(400, "bad_request", "No editable fields provided.");
            }
            IssueService.UpdateValidationResult uvr =
                    issueService.validateUpdate(viewer, issue.getId(), iip);
            if (!uvr.isValid()) {
                return validationError(uvr.getErrorCollection());
            }
            issueService.update(viewer, uvr);
            return detail(issue.getKey(), viewer);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "PUT /rest/corbit-mobile/1.0/issues/{issueKey}",
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while updating the issue.");
        }
    }

    /** Assign / unassign. Body: {"name":"jdoe"} | {"name":null} (unassign) | {"name":"-1"} (default). */
    @POST
    @Path("/{issueKey}/assignee")
    @Consumes({"application/json"})
    public Response setAssignee(@PathParam("issueKey") String issueKey,
                                Map<String, Object> body) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.ISSUE_DETAIL)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.ISSUE_DETAIL);
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return notFound();
            }
            if (!ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.ASSIGN_ISSUES, (Issue) issue, viewer)) {
                return JimRestResponses.errorJson(403, "no_permission",
                        "You do not have permission to assign this issue.");
            }
            // null → unassigned; "-1" → automatic; otherwise a username.
            String assigneeId = body != null && body.containsKey("name")
                    ? trimToNull(body.get("name")) : null;
            IssueService issueService = ComponentAccessor.getIssueService();
            IssueService.AssignValidationResult avr =
                    issueService.validateAssign(viewer, issue.getId(), assigneeId);
            if (!avr.isValid()) {
                return validationError(avr.getErrorCollection());
            }
            issueService.assign(viewer, avr);
            return detail(issue.getKey(), viewer);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/issues/{issueKey}/assignee",
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while assigning the issue.");
        }
    }

    /** Start watching the issue (viewer only). */
    @PUT
    @Path("/{issueKey}/watch")
    public Response startWatching(@PathParam("issueKey") String issueKey) {
        return watch(issueKey, true);
    }

    /** Stop watching the issue (viewer only). */
    @DELETE
    @Path("/{issueKey}/watch")
    public Response stopWatching(@PathParam("issueKey") String issueKey) {
        return watch(issueKey, false);
    }

    private Response watch(String issueKey, boolean start) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.ISSUE_DETAIL)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.ISSUE_DETAIL);
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return notFound();
            }
            boolean watchingEnabled = ComponentAccessor.getApplicationProperties()
                    .getOption(com.atlassian.jira.config.properties.APKeys.JIRA_OPTION_WATCHING);
            if (!watchingEnabled) {
                return JimRestResponses.errorJson(400, "watching_disabled",
                        "Watching is disabled on this Jira instance.");
            }
            if (start) {
                ComponentAccessor.getWatcherManager().startWatching(viewer, issue);
            } else {
                ComponentAccessor.getWatcherManager().stopWatching(viewer, issue);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("watching", ComponentAccessor.getWatcherManager().isWatching(viewer, issue));
            out.put("watchCount", ComponentAccessor.getWatcherManager().getWatcherCount(issue));
            return JimRestResponses.okJson(out);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "PUT/DELETE /rest/corbit-mobile/1.0/issues/{issueKey}/watch",
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while updating your watch state.");
        }
    }

    /** Log work. Body: {"timeSpent":"2h 30m","comment":"...","startedEpochMs":123}. */
    @POST
    @Path("/{issueKey}/worklog")
    @Consumes({"application/json"})
    public Response addWorklog(@PathParam("issueKey") String issueKey,
                               Map<String, Object> body) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.ISSUE_DETAIL)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.ISSUE_DETAIL);
        }
        String timeSpent = trimToNull(body != null ? body.get("timeSpent") : null);
        if (timeSpent == null) {
            return JimRestResponses.errorJson(400, "bad_request", "timeSpent is required (e.g. \"2h 30m\").");
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return notFound();
            }
            if (!ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.WORK_ON_ISSUES, (Issue) issue, viewer)) {
                return JimRestResponses.errorJson(403, "no_permission",
                        "You do not have permission to log work on this issue.");
            }
            WorklogService worklogService = ComponentAccessor.getComponent(WorklogService.class);
            JiraServiceContext ctx = new JiraServiceContextImpl(viewer);
            String comment = trimToNull(body.get("comment"));
            Long startedMs = longOrNull(body.get("startedEpochMs"));
            WorklogInputParametersImpl.Builder builder = WorklogInputParametersImpl.issue(issue)
                    .timeSpent(timeSpent);
            if (comment != null) {
                builder.comment(comment);
            }
            // Jira's validateCreate requires an explicit start date; default to now.
            builder.startDate(startedMs != null ? new Date(startedMs) : new Date());
            WorklogInputParameters params = builder.build();
            WorklogResult wr = worklogService.validateCreate(ctx, params);
            if (wr == null || ctx.getErrorCollection().hasAnyErrors()) {
                return validationError(ctx.getErrorCollection());
            }
            worklogService.createAndAutoAdjustRemainingEstimate(ctx, wr, true);
            if (ctx.getErrorCollection().hasAnyErrors()) {
                return validationError(ctx.getErrorCollection());
            }
            return detail(issue.getKey(), viewer);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/issues/{issueKey}/worklog",
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while logging work.");
        }
    }

    /** Add an issue-level attachment (multipart, single file). */
    @POST
    @Path("/{issueKey}/attachments")
    @Consumes({"multipart/form-data"})
    public Response addIssueAttachment(@PathParam("issueKey") String issueKey,
                                       @Context HttpServletRequest request) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.ISSUE_DETAIL)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.ISSUE_DETAIL);
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return notFound();
            }
            if (!ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.CREATE_ATTACHMENTS, (Issue) issue, viewer)) {
                return JimRestResponses.errorJson(403, "no_permission",
                        "You do not have permission to attach files to this issue.");
            }
            File tempDirectory = this.attachmentStorageService.createUploadTempDirectory();
            JimMultipartParser.ParsedMultipartForm form =
                    JimMultipartParser.parse(request, tempDirectory);
            if (form.getFile() == null || !form.getFile().exists() || form.getFile().length() == 0L) {
                return JimRestResponses.errorJson(400, "bad_request", "A non-empty file is required.");
            }
            uploadIssueAttachment(issue, viewer, form);
            return detail(issue.getKey(), viewer);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST (multipart) /rest/corbit-mobile/1.0/issues/{issueKey}/attachments",
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while uploading the attachment.");
        }
    }

    /** Delete an issue attachment (delete-all, or delete-own if the viewer uploaded it). */
    @DELETE
    @Path("/{issueKey}/attachments/{attachmentId}")
    public Response deleteIssueAttachment(@PathParam("issueKey") String issueKey,
                                          @PathParam("attachmentId") long attachmentId) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.ISSUE_DETAIL)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.ISSUE_DETAIL);
        }
        try {
            MutableIssue issue = loadBrowsable(issueKey, viewer);
            if (issue == null) {
                return notFound();
            }
            Attachment attachment;
            try {
                attachment = ComponentAccessor.getAttachmentManager().getAttachment(attachmentId);
            } catch (Exception notFound) {
                attachment = null;
            }
            if (attachment == null || attachment.getIssueObject() == null
                    || !issue.getId().equals(attachment.getIssueObject().getId())) {
                return JimRestResponses.errorJson(404, "not_found", "Attachment not found on this issue.");
            }
            com.atlassian.jira.security.PermissionManager pm = ComponentAccessor.getPermissionManager();
            boolean own = attachment.getAuthorObject() != null
                    && viewer.getKey().equals(attachment.getAuthorObject().getKey());
            boolean allowed = pm.hasPermission(ProjectPermissions.DELETE_ALL_ATTACHMENTS, (Issue) issue, viewer)
                    || (own && pm.hasPermission(ProjectPermissions.DELETE_OWN_ATTACHMENTS, (Issue) issue, viewer));
            if (!allowed) {
                return JimRestResponses.errorJson(403, "no_permission",
                        "You do not have permission to delete this attachment.");
            }
            ComponentAccessor.getAttachmentManager().deleteAttachment(attachment);
            return detail(issue.getKey(), viewer);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "DELETE /rest/corbit-mobile/1.0/issues/{issueKey}/attachments/{id}",
                    viewer.getKey(), ex, "internal_error",
                    "An internal error occurred while deleting the attachment.");
        }
    }

    // --- Sprint 10 helpers ----------------------------------------------------

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

    private Response detail(String key, ApplicationUser viewer) {
        Issue fresh = ComponentAccessor.getIssueManager().getIssueByCurrentKey(key);
        if (fresh == null) {
            return notFound();
        }
        return JimRestResponses.okJson(JimMobileIssueDetail.toDetailMap(
                fresh, viewer, this.avatarService, this.applicationProperties));
    }

    private static Response notFound() {
        return JimRestResponses.errorJson(404, "not_found",
                "Issue not found or you do not have permission to view it.");
    }

    private static Response validationError(ErrorCollection ec) {
        List<String> parts = new ArrayList<>();
        if (ec != null) {
            if (ec.getErrorMessages() != null) {
                parts.addAll(ec.getErrorMessages());
            }
            if (ec.getErrors() != null) {
                for (Map.Entry<String, String> e : ec.getErrors().entrySet()) {
                    if (e.getValue() != null) {
                        parts.add(e.getValue());
                    }
                }
            }
        }
        String msg = parts.isEmpty() ? "The action could not be completed." : String.join(" ", parts);
        return JimRestResponses.errorJson(400, "validation_error", msg);
    }

    private static String resolutionId(String idOrName) {
        try {
            ConstantsManager cm = ComponentAccessor.getConstantsManager();
            for (Resolution r : cm.getResolutions()) {
                if (idOrName.equals(r.getId()) || idOrName.equalsIgnoreCase(r.getName())) {
                    return r.getId();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Integer intOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(v).trim()));
        } catch (Exception ex) {
            return null;
        }
    }

    private static Long longOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return (long) Math.floor(Double.parseDouble(String.valueOf(v).trim()));
        } catch (Exception ex) {
            return null;
        }
    }

    private static Comment findVisibleComment(CommentManager cm, Issue issue,
                                              ApplicationUser viewer, long commentId) {
        List<Comment> visible = cm.getCommentsForUser(issue, viewer);
        if (visible == null) {
            return null;
        }
        for (Comment c : visible) {
            if (c.getId() != null && c.getId() == commentId) {
                return c;
            }
        }
        return null;
    }

    /**
     * Assemble the reply wiki body, identical to the web composer:
     * {@code [~author]\n\n{quote}excerpt{quote}\n\ntext}, dropping the mention on
     * a self-reply.
     */
    private static String buildReplyBody(Issue issue, Comment parent,
                                         ApplicationUser viewer, String userText) {
        StringBuilder sb = new StringBuilder();
        ApplicationUser author = parent.getAuthorApplicationUser();
        boolean isSelf = author != null && viewer != null
                && viewer.getKey() != null && viewer.getKey().equals(author.getKey());
        if (!isSelf && author != null) {
            String username = author.getUsername();
            if (username != null && !username.trim().isEmpty()) {
                sb.append("[~").append(username.trim()).append("]\n\n");
            }
        }
        String excerpt = JimMobileIssueDetail.commentExcerpt(issue, parent, REPLY_EXCERPT_MAX);
        if (excerpt != null && !excerpt.isEmpty()) {
            sb.append("{quote}").append(excerpt).append("{quote}\n\n");
        }
        sb.append(userText.trim());
        return sb.toString();
    }

    private static String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static Response unauthenticated() {
        return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                "You must be signed in to use CorbitChat Mobile.");
    }

    private static final int MAX_COMMENT_CHARS = 32000;
    private static final int REPLY_EXCERPT_MAX = 240;
}
