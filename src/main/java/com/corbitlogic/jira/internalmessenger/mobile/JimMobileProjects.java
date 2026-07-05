package com.corbitlogic.jira.internalmessenger.mobile;

import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.jql.builder.JqlClauseBuilder;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleActors;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.query.Query;
import com.atlassian.query.operator.Operator;
import com.atlassian.query.order.SortOrder;
import com.corbitlogic.jira.internalmessenger.util.JimSanitizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-owned project projection + whitelisted project-scoped stat queries for
 * the mobile BFF (Sprint 04). The client never sends JQL: the only stat "kinds"
 * are fixed here and each maps to a bounded {@link Query} run through
 * {@code SearchService} for the caller, so Browse-Project / issue security is
 * always enforced.
 */
public final class JimMobileProjects {

    private static final int MAX_DESCRIPTION_CHARS = 2000;

    private JimMobileProjects() {
    }

    /** Compact summary for the accessible-projects list. */
    public static Map<String, Object> toSummaryMap(Project project,
                                                   ApplicationProperties applicationProperties) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", project.getKey());
        m.put("id", project.getId());
        m.put("name", JimSanitizer.sanitizeText(project.getName()));
        m.put("projectTypeKey", projectTypeKey(project));
        m.put("avatarUrl", JimMobileAvatars.projectPath(project.getKey()));
        ApplicationUser lead = project.getProjectLead();
        m.put("lead", lead != null ? lead.getDisplayName() : null);
        m.put("leadAvatarUrl", lead != null ? JimMobileAvatars.userPath(lead) : null);
        m.put("url", browseUrl(applicationProperties, project));
        return m;
    }

    /** Fuller overview (summary + description + counts filled in by the resource). */
    public static Map<String, Object> toOverviewMap(Project project,
                                                    ApplicationProperties applicationProperties) {
        Map<String, Object> m = toSummaryMap(project, applicationProperties);
        m.put("description", bound(JimSanitizer.sanitizeText(project.getDescription()),
                MAX_DESCRIPTION_CHARS));
        m.put("componentCount", project.getProjectComponents() != null
                ? project.getProjectComponents().size() : 0);
        m.put("versionCount", project.getVersions() != null
                ? project.getVersions().size() : 0);
        return m;
    }

    /** Total issues in a project the caller can see. */
    public static Query totalQuery(Project project) {
        JqlQueryBuilder b = JqlQueryBuilder.newBuilder();
        b.where().project(project.getId()).endWhere();
        return b.buildQuery();
    }

    /** Unresolved (open) issues in a project the caller can see. */
    public static Query openQuery(Project project) {
        JqlQueryBuilder b = JqlQueryBuilder.newBuilder();
        b.where().project(project.getId()).and().unresolved().endWhere();
        return b.buildQuery();
    }

    /** Unresolved issues assigned to the caller in this project. */
    public static Query assignedToMeQuery(Project project) {
        JqlQueryBuilder b = JqlQueryBuilder.newBuilder();
        b.where().project(project.getId()).and().unresolved()
                .and().assigneeIsCurrentUser().endWhere();
        return b.buildQuery();
    }

    /** The only issue-scope tokens the Issues tab may request. */
    public static boolean isValidScope(String scope) {
        return "open".equals(scope) || "all".equals(scope) || "mine".equals(scope);
    }

    /**
     * Project-scoped issue list query for the Issues tab (Sprint 11). Scope is a
     * whitelisted token ({@code open|all|mine}) and {@code q} is an optional free
     * text term applied via a bound {@code text ~} condition (never raw JQL — the
     * term crosses the boundary only as a {@link JqlClauseBuilder} string literal,
     * so it cannot alter the query structure). Always ordered by most-recently
     * updated. The search itself runs through {@code SearchService} for the
     * caller, enforcing Browse-Project / issue security.
     */
    public static Query issuesQuery(Project project, String scope, String q) {
        JqlQueryBuilder builder = JqlQueryBuilder.newBuilder();
        JqlClauseBuilder w = builder.where().project(project.getId());
        if ("mine".equals(scope)) {
            w.and().unresolved().and().assigneeIsCurrentUser();
        } else if (!"all".equals(scope)) {
            w.and().unresolved();
        }
        String text = boundTerm(q);
        if (text != null) {
            w.and().addStringCondition("text", Operator.LIKE, text);
        }
        w.endWhere();
        builder.orderBy().updatedDate(SortOrder.DESC).endOrderBy();
        return builder.buildQuery();
    }

    /** Recent-activity query: the project's most recently updated issues. */
    public static Query activityQuery(Project project) {
        JqlQueryBuilder builder = JqlQueryBuilder.newBuilder();
        builder.where().project(project.getId()).endWhere();
        builder.orderBy().updatedDate(SortOrder.DESC).endOrderBy();
        return builder.buildQuery();
    }

    /**
     * Real Jira project membership grouped by project role (Sprint 11). Only
     * non-empty roles are returned. This exposes actual project role members —
     * not chat members — and must be gated by Browse Project at the call site.
     */
    public static List<Map<String, Object>> roleMembers(Project project,
                                                        ProjectRoleManager projectRoleManager) {
        List<Map<String, Object>> roles = new ArrayList<>();
        if (projectRoleManager == null) {
            return roles;
        }
        try {
            for (ProjectRole role : projectRoleManager.getProjectRoles()) {
                if (role == null) {
                    continue;
                }
                List<Map<String, Object>> users = new ArrayList<>();
                try {
                    ProjectRoleActors actors = projectRoleManager.getProjectRoleActors(role, project);
                    if (actors != null && actors.getApplicationUsers() != null) {
                        for (ApplicationUser u : actors.getApplicationUsers()) {
                            if (u == null) {
                                continue;
                            }
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("name", u.getName());
                            m.put("displayName", u.getDisplayName());
                            m.put("avatarUrl", JimMobileAvatars.userPath(u));
                            m.put("active", u.isActive());
                            users.add(m);
                        }
                    }
                } catch (Exception ignore) {
                    // A single unreadable role must not drop the others.
                }
                if (!users.isEmpty()) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", role.getId());
                    r.put("name", JimSanitizer.sanitizeText(role.getName()));
                    r.put("count", users.size());
                    r.put("members", users);
                    roles.add(r);
                }
            }
        } catch (Throwable t) {
            return roles;
        }
        return roles;
    }

    private static String boundTerm(String q) {
        if (q == null) {
            return null;
        }
        String t = q.trim();
        if (t.isEmpty()) {
            return null;
        }
        // Keep it a plain term: drop characters that could confuse the text
        // index / clause literal. Length-bounded to avoid abuse.
        t = t.replaceAll("[\"'\\\\]", " ").trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > 100 ? t.substring(0, 100) : t;
    }

    private static String projectTypeKey(Project project) {
        try {
            return project.getProjectTypeKey() != null
                    ? project.getProjectTypeKey().getKey() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String browseUrl(ApplicationProperties applicationProperties, Project project) {
        String base = baseUrl(applicationProperties);
        return base != null ? base + "/browse/" + project.getKey() : "/browse/" + project.getKey();
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
}
