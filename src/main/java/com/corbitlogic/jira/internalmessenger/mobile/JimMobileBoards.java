package com.corbitlogic.jira.internalmessenger.mobile;

import com.atlassian.greenhopper.model.rapid.RapidView;
import com.atlassian.greenhopper.service.ServiceOutcome;
import com.atlassian.greenhopper.service.rapid.view.RapidViewService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.search.SearchRequest;
import com.atlassian.jira.issue.search.SearchRequestManager;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.query.Query;
import com.corbitlogic.jira.internalmessenger.util.JimSanitizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mobile Board Gallery data (Sprint 04D).
 *
 * <p><b>Isolation:</b> every Jira Software / GreenHopper reference lives ONLY in
 * this class. The endpoint calls it inside a {@code try/catch(Throwable)}, so if
 * Jira Software is absent or its API changes (the OSGi import is optional per the
 * pom's {@code *;resolution:=optional} rule) the board endpoint degrades to
 * "unavailable" and nothing else in the plugin is affected.</p>
 *
 * <p>Mirrors the web Board Gallery card exactly: board name + type, an avatar
 * seed (client draws initials/colour like the web), the primary project chip
 * (key + name, permission-checked), project lead, a "Multi" indication for
 * multi-project boards, and a best-effort "my tasks" count — all resolved from
 * the board's saved filter JQL, the same source the web gallery uses. Boards are
 * returned already permission-filtered by {@link RapidViewService}.</p>
 */
public final class JimMobileBoards {

    private static final int MAX_BOARDS = 120;

    // project in (A, B, "C")  /  project = KEY  /  project = "Key"
    private static final Pattern PROJECT_IN =
            Pattern.compile("project\\s+in\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_EQ =
            Pattern.compile("project\\s*=\\s*(\"[^\"]+\"|'[^']+'|[A-Za-z][A-Za-z0-9_]*)",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDER_BY =
            Pattern.compile("\\s+ORDER\\s+BY.*", Pattern.CASE_INSENSITIVE);

    private JimMobileBoards() {
    }

    /** True when Jira Software (GreenHopper) board services are wired at runtime. */
    public static boolean isAvailable() {
        try {
            return ComponentAccessor.getOSGiComponentInstanceOfType(RapidViewService.class) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Boards the {@code viewer} can see, as mobile-card maps. Never throws for a
     * single bad board — each enrichment step degrades independently, exactly
     * like the web gallery.
     */
    public static List<Map<String, Object>> listBoards(ApplicationUser viewer,
                                                       SearchService searchService,
                                                       ApplicationProperties applicationProperties) {
        List<Map<String, Object>> out = new ArrayList<>();
        RapidViewService rapidViewService =
                ComponentAccessor.getOSGiComponentInstanceOfType(RapidViewService.class);
        if (rapidViewService == null) {
            return out;
        }
        ServiceOutcome<List<RapidView>> outcome = rapidViewService.getRapidViews(viewer);
        if (outcome == null || !outcome.isValid() || outcome.getValue() == null) {
            return out;
        }
        SearchRequestManager searchRequestManager =
                ComponentAccessor.getComponent(SearchRequestManager.class);
        PermissionManager permissionManager = ComponentAccessor.getPermissionManager();
        ProjectManager projectManager = ComponentAccessor.getProjectManager();

        for (RapidView view : outcome.getValue()) {
            if (out.size() >= MAX_BOARDS) {
                break;
            }
            if (view == null) {
                continue;
            }
            try {
                out.add(toCard(view, viewer, searchService, searchRequestManager,
                        permissionManager, projectManager, applicationProperties));
            } catch (Throwable ignore) {
                // One board failing must never drop the whole gallery.
            }
        }
        return out;
    }

    private static Map<String, Object> toCard(RapidView view,
                                              ApplicationUser viewer,
                                              SearchService searchService,
                                              SearchRequestManager searchRequestManager,
                                              PermissionManager permissionManager,
                                              ProjectManager projectManager,
                                              ApplicationProperties applicationProperties) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", view.getId());
        card.put("name", JimSanitizer.sanitizeText(view.getName()));
        card.put("type", typeOf(view));

        String boardJql = filterJql(view, viewer, searchRequestManager, searchService);
        List<String> keys = parseProjectKeys(boardJql);
        card.put("projectKeys", keys);
        card.put("multipleProjects", keys.size() > 1);

        // Primary project chip (single-project boards only, permission-checked).
        Map<String, Object> project = null;
        if (keys.size() == 1) {
            Project p = projectManager.getProjectObjByKeyIgnoreCase(keys.get(0));
            if (p != null && permissionManager.hasPermission(
                    ProjectPermissions.BROWSE_PROJECTS, p, viewer)) {
                project = JimMobileProjects.toSummaryMap(p, applicationProperties);
            }
        }
        card.put("project", project);

        // "My tasks" count — best effort, scoped to the board's own JQL.
        card.put("assignedCount", assignedCount(boardJql, viewer, searchService));
        return card;
    }

    private static String typeOf(RapidView view) {
        try {
            RapidView.Type t = view.getType();
            return t != null ? t.name().toLowerCase() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static String filterJql(RapidView view,
                                    ApplicationUser viewer,
                                    SearchRequestManager searchRequestManager,
                                    SearchService searchService) {
        try {
            Long filterId = view.getSavedFilterId();
            if (filterId == null || searchRequestManager == null) {
                return "";
            }
            SearchRequest sr = searchRequestManager.getSearchRequestById(viewer, filterId);
            if (sr == null) {
                sr = searchRequestManager.getSearchRequestById(filterId);
            }
            if (sr == null) {
                return "";
            }
            Query q = sr.getQuery();
            if (q == null) {
                return "";
            }
            String jql = searchService.getJqlString(q);
            return jql != null ? jql : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static List<String> parseProjectKeys(String jql) {
        List<String> keys = new ArrayList<>();
        if (jql == null || jql.trim().isEmpty()) {
            return keys;
        }
        Set<String> seen = new LinkedHashSet<>();
        Matcher in = PROJECT_IN.matcher(jql);
        if (in.find()) {
            for (String raw : in.group(1).split(",")) {
                addKey(seen, raw);
            }
            keys.addAll(seen);
            return keys;
        }
        Matcher eq = PROJECT_EQ.matcher(jql);
        if (eq.find()) {
            addKey(seen, eq.group(1));
        }
        keys.addAll(seen);
        return keys;
    }

    private static void addKey(Set<String> seen, String raw) {
        if (raw == null) {
            return;
        }
        String k = raw.replaceAll("['\"\\s]", "").toUpperCase();
        if (!k.isEmpty()) {
            seen.add(k);
        }
    }

    private static Integer assignedCount(String boardJql,
                                         ApplicationUser viewer,
                                         SearchService searchService) {
        try {
            String base = boardJql == null ? "" : ORDER_BY.matcher(boardJql).replaceFirst("").trim();
            String jql = base.isEmpty()
                    ? "assignee = currentUser()"
                    : "(" + base + ") AND assignee = currentUser()";
            SearchService.ParseResult parsed = searchService.parseQuery(viewer, jql);
            if (parsed == null || !parsed.isValid()) {
                return null;
            }
            return (int) searchService.searchCount(viewer, parsed.getQuery());
        } catch (Throwable t) {
            return null;
        }
    }
}
