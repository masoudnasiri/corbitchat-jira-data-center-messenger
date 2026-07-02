package com.corbitlogic.jira.internalmessenger.mobile;

import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueFieldConstants;
import com.atlassian.jira.issue.RendererManager;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.layout.field.FieldLayout;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutItem;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutManager;
import com.atlassian.jira.issue.label.Label;
import com.atlassian.jira.issue.worklog.Worklog;
import com.atlassian.jira.issue.worklog.WorklogManager;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.util.JimIssueUrlBuilder;
import com.corbitlogic.jira.internalmessenger.util.JimSanitizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only, permission-safe projection of a full Jira {@link Issue} for the
 * mobile BFF (Sprint 04). The caller MUST have already verified the viewer can
 * browse the issue (see {@code CorbitMobileIssueResource#getIssue}); this class
 * only shapes the JSON.
 *
 * <p>Guarantees:</p>
 * <ul>
 *   <li>Rendered wiki/HTML for description, environment and comment bodies where
 *       Jira can render them (falls back to sanitized plain text otherwise).</li>
 *   <li>Only field-configuration-visible (non-hidden) custom fields are emitted,
 *       and their values are stringified + length-bounded so no huge/unsafe
 *       object is ever returned.</li>
 *   <li>Only comments the viewer is allowed to see (role/group visibility) are
 *       included; only unrestricted worklogs are included.</li>
 *   <li>All free text is bounded and control-char sanitized. Nothing here logs
 *       field values, descriptions or comment bodies.</li>
 * </ul>
 */
public final class JimMobileIssueDetail {

    private static final int MAX_COMMENTS = 50;
    private static final int MAX_ATTACHMENTS = 100;
    private static final int MAX_WORKLOGS = 50;
    private static final int MAX_CUSTOM_FIELDS = 100;
    private static final int MAX_RENDERED_CHARS = 20000;
    private static final int MAX_TEXT_CHARS = 10000;
    private static final int MAX_FIELD_VALUE_CHARS = 1000;
    private static final int MAX_LIST_ELEMENTS = 50;

    /**
     * Custom-field type keys that are internal/noisy and never useful to render
     * as text on mobile (LexoRank ordering, dev-tool summary blobs, etc.). These
     * are standard Jira/GreenHopper field types, not customer-specific fields.
     */
    private static final List<String> SKIP_CF_TYPE_SUBSTRINGS = java.util.Arrays.asList(
            "gh-lexo-rank",
            "jira-development-integration",
            "devsummary");

    private JimMobileIssueDetail() {
    }

    public static Map<String, Object> toDetailMap(Issue issue,
                                                  ApplicationUser viewer,
                                                  AvatarService avatarService,
                                                  ApplicationProperties applicationProperties) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", issue.getKey());
        map.put("id", issue.getId());
        map.put("summary", JimSanitizer.sanitizeText(issue.getSummary()));

        RendererManager rendererManager = ComponentAccessor.getComponent(RendererManager.class);
        FieldLayout fieldLayout = safeFieldLayout(issue);

        map.put("description", renderedField(issue, rendererManager, fieldLayout,
                IssueFieldConstants.DESCRIPTION, issue.getDescription()));
        map.put("environment", renderedField(issue, rendererManager, fieldLayout,
                IssueFieldConstants.ENVIRONMENT, issue.getEnvironment()));

        // Issue type
        if (issue.getIssueType() != null) {
            Map<String, Object> type = new LinkedHashMap<>();
            type.put("name", issue.getIssueType().getName());
            type.put("iconUrl", issue.getIssueType().getCompleteIconUrl());
            type.put("subtask", issue.getIssueType().isSubTask());
            map.put("issueType", type);
        } else {
            map.put("issueType", null);
        }

        // Status + category
        if (issue.getStatus() != null) {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("name", issue.getStatus().getName());
            status.put("category", issue.getStatus().getStatusCategory() != null
                    ? issue.getStatus().getStatusCategory().getKey() : null);
            map.put("status", status);
        } else {
            map.put("status", null);
        }

        if (issue.getPriority() != null) {
            Map<String, Object> pr = new LinkedHashMap<>();
            pr.put("name", issue.getPriority().getName());
            pr.put("iconUrl", issue.getPriority().getIconUrl());
            map.put("priority", pr);
        } else {
            map.put("priority", null);
        }

        map.put("resolution", issue.getResolution() != null
                ? JimRestResponsesLite.single("name", issue.getResolution().getName()) : null);

        // Project
        Project project = issue.getProjectObject();
        if (project != null) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("key", project.getKey());
            p.put("name", JimSanitizer.sanitizeText(project.getName()));
            p.put("avatarUrl", JimMobileAvatars.projectPath(project.getKey()));
            map.put("project", p);
        } else {
            map.put("project", null);
        }

        // People
        map.put("assignee", personMap(issue.getAssignee(), viewer, avatarService));
        map.put("reporter", personMap(issue.getReporter(), viewer, avatarService));
        map.put("creator", personMap(issue.getCreator(), viewer, avatarService));

        // Dates. Instants (created/updated/resolved) stay epoch millis; the Due
        // Date is a calendar day (yyyy-MM-dd, server zone) so it never shifts by
        // a day on the device. Effective due date falls back to the proposed
        // completion custom field when the standard Due Date is empty.
        String dueYmd = JimMobileDates.dueYmd(issue);
        String effectiveDue = JimMobileDates.effectiveDueYmd(issue);
        map.put("created", issue.getCreated() != null ? issue.getCreated().getTime() : null);
        map.put("updated", issue.getUpdated() != null ? issue.getUpdated().getTime() : null);
        map.put("dueDate", dueYmd);
        map.put("effectiveDueDate", effectiveDue);
        map.put("effectiveDueDateSource", JimMobileDates.effectiveDueSource(issue));
        map.put("resolutionDate", issue.getResolutionDate() != null
                ? issue.getResolutionDate().getTime() : null);
        map.put("overdue", issue.getResolution() == null
                && JimMobileDates.isOverdueYmd(effectiveDue, viewer));

        // Simple lists
        map.put("labels", labelList(issue));
        map.put("components", namedList(issue.getComponents()));
        map.put("fixVersions", versionList(issue.getFixVersions()));
        map.put("affectsVersions", versionList(issue.getAffectedVersions()));

        // Time tracking (aggregate seconds — safe, non-restricted)
        Map<String, Object> tt = new LinkedHashMap<>();
        tt.put("originalEstimateSeconds", issue.getOriginalEstimate());
        tt.put("remainingEstimateSeconds", issue.getEstimate());
        tt.put("timeSpentSeconds", issue.getTimeSpent());
        map.put("timeTracking", tt);

        // Custom fields (visible only, bounded)
        map.put("customFields", customFields(issue, viewer, avatarService,
                rendererManager, fieldLayout));

        // Comments (viewer-visible only)
        map.put("comments", comments(issue, viewer, avatarService, rendererManager, fieldLayout));

        // Attachments (metadata list)
        map.put("attachments", attachments(issue, applicationProperties));

        // Worklog (aggregate + unrestricted entries)
        map.put("worklog", worklog(issue, viewer, avatarService));

        // Permission flags for FUTURE write actions (this sprint is read-only)
        map.put("permissions", permissions(issue, viewer));

        map.put("url", JimIssueUrlBuilder.buildBrowseUrl(applicationProperties, issue));
        return map;
    }

    // --- Rendered fields ------------------------------------------------------

    private static Map<String, Object> renderedField(Issue issue,
                                                     RendererManager rendererManager,
                                                     FieldLayout fieldLayout,
                                                     String fieldId,
                                                     String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        String text = bound(JimSanitizer.sanitizeText(rawValue), MAX_TEXT_CHARS);
        String rendered = null;
        try {
            String rendererType = rendererType(fieldLayout, fieldId);
            if (rendererManager != null && rendererType != null) {
                String html = rendererManager.getRenderedContent(rendererType, rawValue,
                        issue.getIssueRenderContext());
                if (html != null && !html.isEmpty()) {
                    rendered = bound(html, MAX_RENDERED_CHARS);
                }
            }
        } catch (Exception ignored) {
            // Fall back to plain text below.
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rendered", rendered);
        m.put("text", text);
        return m;
    }

    private static String rendererType(FieldLayout fieldLayout, String fieldId) {
        if (fieldLayout == null) {
            return null;
        }
        try {
            FieldLayoutItem item = fieldLayout.getFieldLayoutItem(fieldId);
            return item != null ? item.getRendererType() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static FieldLayout safeFieldLayout(Issue issue) {
        try {
            FieldLayoutManager mgr = ComponentAccessor.getFieldLayoutManager();
            return mgr != null ? mgr.getFieldLayout(issue) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    // --- Custom fields --------------------------------------------------------

    private static List<Map<String, Object>> customFields(Issue issue,
                                                          ApplicationUser viewer,
                                                          AvatarService avatarService,
                                                          RendererManager rendererManager,
                                                          FieldLayout fieldLayout) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
            List<CustomField> fields = cfm.getCustomFieldObjects(issue);
            if (fields == null) {
                return out;
            }
            for (CustomField cf : fields) {
                if (out.size() >= MAX_CUSTOM_FIELDS) {
                    break;
                }
                // Respect field-configuration visibility (hidden fields excluded).
                FieldLayoutItem item = null;
                if (fieldLayout != null) {
                    try {
                        item = fieldLayout.getFieldLayoutItem(cf);
                    } catch (Exception ignored) {
                    }
                }
                if (item != null && item.isHidden()) {
                    continue;
                }
                if (isSkippedCustomFieldType(cf)) {
                    continue;
                }
                Object value = cf.getValue(issue);
                if (value == null) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", cf.getId());
                m.put("name", JimSanitizer.sanitizeText(cf.getFieldName()));
                m.put("type", "custom");

                // A field with a renderer + string value can be rendered to HTML.
                String rendererType = item != null ? item.getRendererType() : null;
                String rendered = null;
                if (rendererType != null && value instanceof String) {
                    try {
                        String html = rendererManager != null
                                ? rendererManager.getRenderedContent(rendererType, (String) value,
                                        issue.getIssueRenderContext())
                                : null;
                        if (html != null && !html.isEmpty()) {
                            rendered = bound(html, MAX_RENDERED_CHARS);
                        }
                    } catch (Exception ignored) {
                    }
                }
                m.put("rendered", rendered);

                // Date/date-time custom fields get a typed contract so the client
                // renders them as real dates (Jalali/Gregorian) instead of a raw
                // epoch value. `value` still carries a readable ISO fallback so
                // any older client never shows raw milliseconds.
                if (value instanceof Date) {
                    Date d = (Date) value;
                    if (JimMobileDates.isDateTimeCustomField(cf)) {
                        m.put("valueType", "datetime");
                        m.put("epochMs", d.getTime());
                        m.put("value", JimMobileDates.toYmd(d));
                    } else {
                        String ymd = JimMobileDates.toYmd(d);
                        m.put("valueType", "date");
                        m.put("date", ymd);
                        m.put("value", ymd);
                    }
                } else {
                    m.put("value", stringifyValue(value, viewer, avatarService));
                }
                // Task-type field → attach colored-pill metadata so the client
                // renders it generically (see JimMobileTaskType).
                if (JimMobileTaskType.FIELD_ID.equals(cf.getId())) {
                    Map<String, Object> pill = JimMobileTaskType.resolve(
                            JimMobileTaskType.rawValue(value));
                    if (pill != null) {
                        m.put("pill", pill);
                    }
                }
                out.add(m);
            }
        } catch (Exception ignored) {
            // Custom fields are best-effort; never fail the whole issue for them.
        }
        return out;
    }

    private static String stringifyValue(Object value, ApplicationUser viewer, AvatarService avatarService) {
        if (value == null) {
            return null;
        }
        if (value instanceof ApplicationUser) {
            return ((ApplicationUser) value).getDisplayName();
        }
        if (value instanceof Date) {
            // Defensive: date custom fields are handled with a typed contract in
            // customFields(); never emit raw epoch millis as a "value".
            return JimMobileDates.toYmd((Date) value);
        }
        if (value instanceof Collection) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (Object o : (Collection<?>) value) {
                if (i >= MAX_LIST_ELEMENTS) {
                    sb.append(", …");
                    break;
                }
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(elementLabel(o));
                i++;
            }
            return bound(JimSanitizer.sanitizeText(sb.toString()), MAX_FIELD_VALUE_CHARS);
        }
        return bound(JimSanitizer.sanitizeText(elementLabel(value)), MAX_FIELD_VALUE_CHARS);
    }

    private static boolean isSkippedCustomFieldType(CustomField cf) {
        try {
            if (cf.getCustomFieldType() == null || cf.getCustomFieldType().getKey() == null) {
                return false;
            }
            String key = cf.getCustomFieldType().getKey().toLowerCase();
            for (String needle : SKIP_CF_TYPE_SUBSTRINGS) {
                if (key.contains(needle)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static String elementLabel(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof ApplicationUser) {
            return ((ApplicationUser) o).getDisplayName();
        }
        if (o instanceof com.atlassian.jira.issue.customfields.option.Option) {
            return ((com.atlassian.jira.issue.customfields.option.Option) o).getValue();
        }
        return String.valueOf(o);
    }

    // --- Comments -------------------------------------------------------------

    private static Map<String, Object> comments(Issue issue,
                                                ApplicationUser viewer,
                                                AvatarService avatarService,
                                                RendererManager rendererManager,
                                                FieldLayout fieldLayout) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        int total = 0;
        try {
            CommentManager cm = ComponentAccessor.getCommentManager();
            // getCommentsForUser respects comment-level role/group visibility.
            List<Comment> visible = cm.getCommentsForUser(issue, viewer);
            if (visible != null) {
                total = visible.size();
                String rendererType = rendererType(fieldLayout, IssueFieldConstants.COMMENT);
                int start = Math.max(0, visible.size() - MAX_COMMENTS);
                for (int i = start; i < visible.size(); i++) {
                    Comment c = visible.get(i);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("author", personMap(c.getAuthorApplicationUser(), viewer, avatarService));
                    m.put("created", c.getCreated() != null ? c.getCreated().getTime() : null);
                    m.put("updated", c.getUpdated() != null ? c.getUpdated().getTime() : null);
                    String rendered = null;
                    try {
                        if (rendererManager != null && rendererType != null && c.getBody() != null) {
                            String html = rendererManager.getRenderedContent(rendererType,
                                    c.getBody(), issue.getIssueRenderContext());
                            if (html != null && !html.isEmpty()) {
                                rendered = bound(html, MAX_RENDERED_CHARS);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    m.put("renderedBody", rendered);
                    m.put("body", bound(JimSanitizer.sanitizeText(c.getBody()), MAX_TEXT_CHARS));
                    items.add(m);
                }
            }
        } catch (Exception ignored) {
        }
        out.put("total", total);
        out.put("items", items);
        return out;
    }

    // --- Attachments ----------------------------------------------------------

    private static List<Map<String, Object>> attachments(Issue issue,
                                                         ApplicationProperties applicationProperties) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<Attachment> list = ComponentAccessor.getAttachmentManager().getAttachments(issue);
            if (list == null) {
                return out;
            }
            String base = baseUrl(applicationProperties);
            for (Attachment a : list) {
                if (out.size() >= MAX_ATTACHMENTS) {
                    break;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", a.getId());
                m.put("filename", JimSanitizer.sanitizeText(a.getFilename()));
                m.put("mimeType", a.getMimetype());
                m.put("size", a.getFilesize());
                m.put("created", a.getCreated() != null ? a.getCreated().getTime() : null);
                m.put("author", a.getAuthorObject() != null
                        ? a.getAuthorObject().getDisplayName() : null);
                if (base != null && a.getFilename() != null) {
                    m.put("url", base + "/secure/attachment/" + a.getId() + "/"
                            + a.getFilename());
                } else {
                    m.put("url", null);
                }
                out.add(m);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // --- Worklog --------------------------------------------------------------

    private static Map<String, Object> worklog(Issue issue,
                                               ApplicationUser viewer,
                                               AvatarService avatarService) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        long totalSeconds = issue.getTimeSpent() != null ? issue.getTimeSpent() : 0L;
        int total = 0;
        try {
            WorklogManager wm = ComponentAccessor.getComponent(WorklogManager.class);
            if (wm != null) {
                List<Worklog> logs = wm.getByIssue(issue);
                if (logs != null) {
                    for (Worklog w : logs) {
                        // Skip role/group-restricted worklogs — do not risk a leak.
                        if (w.getRoleLevelId() != null || w.getGroupLevel() != null) {
                            continue;
                        }
                        total++;
                        if (items.size() >= MAX_WORKLOGS) {
                            continue;
                        }
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("author", personMap(w.getAuthorObject(), viewer, avatarService));
                        m.put("timeSpentSeconds", w.getTimeSpent());
                        m.put("started", w.getStartDate() != null ? w.getStartDate().getTime() : null);
                        m.put("comment", bound(JimSanitizer.sanitizeText(w.getComment()), MAX_TEXT_CHARS));
                        items.add(m);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        out.put("timeSpentSeconds", totalSeconds);
        out.put("total", total);
        out.put("items", items);
        return out;
    }

    // --- Permission flags -----------------------------------------------------

    private static Map<String, Object> permissions(Issue issue, ApplicationUser viewer) {
        Map<String, Object> m = new LinkedHashMap<>();
        PermissionManager pm = ComponentAccessor.getPermissionManager();
        m.put("canComment", has(pm, ProjectPermissions.ADD_COMMENTS, issue, viewer));
        m.put("canEdit", has(pm, ProjectPermissions.EDIT_ISSUES, issue, viewer));
        m.put("canTransition", has(pm, ProjectPermissions.TRANSITION_ISSUES, issue, viewer));
        m.put("canAssign", has(pm, ProjectPermissions.ASSIGN_ISSUES, issue, viewer));
        m.put("canLogWork", has(pm, ProjectPermissions.WORK_ON_ISSUES, issue, viewer));
        m.put("canAddAttachment", has(pm, ProjectPermissions.CREATE_ATTACHMENTS, issue, viewer));
        m.put("canDelete", has(pm, ProjectPermissions.DELETE_ISSUES, issue, viewer));
        return m;
    }

    private static boolean has(PermissionManager pm,
                               com.atlassian.jira.security.plugin.ProjectPermissionKey key,
                               Issue issue, ApplicationUser viewer) {
        try {
            return pm.hasPermission(key, issue, viewer);
        } catch (Exception ex) {
            return false;
        }
    }

    // --- Small helpers --------------------------------------------------------

    private static Map<String, Object> personMap(ApplicationUser user,
                                                 ApplicationUser viewer,
                                                 AvatarService avatarService) {
        if (user == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", user.getName());
        m.put("displayName", user.getDisplayName());
        m.put("avatarUrl", JimMobileAvatars.userPath(user));
        return m;
    }

    private static List<String> labelList(Issue issue) {
        List<String> out = new ArrayList<>();
        try {
            if (issue.getLabels() != null) {
                for (Label l : issue.getLabels()) {
                    if (out.size() >= MAX_LIST_ELEMENTS) {
                        break;
                    }
                    out.add(l.getLabel());
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static List<String> namedList(Collection<?> values) {
        List<String> out = new ArrayList<>();
        if (values == null) {
            return out;
        }
        for (Object v : values) {
            if (out.size() >= MAX_LIST_ELEMENTS) {
                break;
            }
            try {
                out.add(JimSanitizer.sanitizeText(String.valueOf(
                        v.getClass().getMethod("getName").invoke(v))));
            } catch (Exception ex) {
                out.add(JimSanitizer.sanitizeText(String.valueOf(v)));
            }
        }
        return out;
    }

    private static List<String> versionList(Collection<Version> versions) {
        List<String> out = new ArrayList<>();
        if (versions == null) {
            return out;
        }
        for (Version v : versions) {
            if (out.size() >= MAX_LIST_ELEMENTS) {
                break;
            }
            out.add(JimSanitizer.sanitizeText(v.getName()));
        }
        return out;
    }

    private static String baseUrl(ApplicationProperties applicationProperties) {
        if (applicationProperties == null) {
            return null;
        }
        String base = applicationProperties.getString("jira.baseurl");
        if (base == null || base.trim().isEmpty()) {
            return null;
        }
        base = base.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String bound(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    /** Tiny local helper to avoid a dependency cycle for a single-entry map. */
    private static final class JimRestResponsesLite {
        static Map<String, Object> single(String key, Object value) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(key, value);
            return m;
        }
    }
}
