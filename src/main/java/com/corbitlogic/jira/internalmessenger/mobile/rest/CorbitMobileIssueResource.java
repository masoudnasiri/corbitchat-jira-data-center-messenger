package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.issue.attachment.CreateAttachmentParamsBean;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.AttachmentUtils;
import com.atlassian.jira.web.bean.PagerFilter;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
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
