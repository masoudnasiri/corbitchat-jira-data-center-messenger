package com.corbitlogic.jira.internalmessenger.mobile;

import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.util.JimSanitizer;
import com.atlassian.query.Query;
import java.util.LinkedHashMap;
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
