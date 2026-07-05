package com.corbitlogic.jira.internalmessenger.mobile;

import com.atlassian.greenhopper.model.rapid.Column;
import com.atlassian.greenhopper.model.rapid.RapidView;
import com.atlassian.greenhopper.service.ServiceOutcome;
import com.atlassian.greenhopper.service.rapid.view.ColumnService;
import com.atlassian.greenhopper.service.rapid.view.RapidViewService;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchRequest;
import com.atlassian.jira.issue.search.SearchRequestManager;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.query.Query;
import com.corbitlogic.jira.internalmessenger.util.JimSanitizer;
import java.util.ArrayList;
import java.util.HashMap;
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
    /** Hard cap on issues fetched for a single mobile board view. */
    private static final int MAX_BOARD_ISSUES = 200;

    /**
     * Sprint scope tokens for Scrum boards. Each maps to a GreenHopper JQL
     * function; the mapping is validated at query time so a board/instance
     * without the {@code sprint} field simply falls back to "all".
     */
    private static final Map<String, String> SPRINT_SCOPES = buildSprintScopes();

    private static Map<String, String> buildSprintScopes() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("active", "sprint in openSprints()");
        m.put("future", "sprint in futureSprints()");
        m.put("closed", "sprint in closedSprints()");
        return m;
    }

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

    // ---------------------------------------------------------------------
    // Board detail (Sprint 11) — meta + issues grouped by status.
    // ---------------------------------------------------------------------

    /**
     * Board header/meta for {@code GET /boards/{id}}: name, type, project chip
     * and — for Scrum boards where the {@code sprint} JQL field resolves — the
     * available sprint scopes (active/future/closed). Returns {@code null} when
     * the board does not exist or the viewer cannot see it (handled by
     * {@link RapidViewService} which permission-filters per user).
     */
    public static Map<String, Object> boardMeta(long boardId,
                                                ApplicationUser viewer,
                                                SearchService searchService,
                                                ApplicationProperties applicationProperties) {
        RapidView view = loadView(boardId, viewer);
        if (view == null) {
            return null;
        }
        SearchRequestManager searchRequestManager =
                ComponentAccessor.getComponent(SearchRequestManager.class);
        PermissionManager permissionManager = ComponentAccessor.getPermissionManager();
        ProjectManager projectManager = ComponentAccessor.getProjectManager();

        Map<String, Object> card = toCard(view, viewer, searchService, searchRequestManager,
                permissionManager, projectManager, applicationProperties);

        String type = String.valueOf(card.get("type"));
        List<Map<String, Object>> scopes = new ArrayList<>();
        if ("scrum".equalsIgnoreCase(type)) {
            String boardJql = filterJql(view, viewer, searchRequestManager, searchService);
            for (Map.Entry<String, String> e : SPRINT_SCOPES.entrySet()) {
                if (sprintScopeUsable(boardJql, e.getValue(), viewer, searchService)) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("id", e.getKey());
                    scopes.add(s);
                }
            }
        }
        card.put("hasSprints", !scopes.isEmpty());
        card.put("sprintScopes", scopes);
        return card;
    }

    /**
     * Board issues grouped by status for
     * {@code GET /boards/{id}/issues?sprint=active|future|closed|all}.
     *
     * <p>Runs the board's own saved-filter JQL through
     * {@code SearchService.search} (which enforces the viewer's Browse-Project /
     * issue security), optionally AND-ed with a validated GreenHopper sprint
     * scope, then groups the results into ordered status columns (To&nbsp;Do →
     * In&nbsp;Progress → Done). Works for Kanban and Scrum boards alike and needs
     * no fragile column/swimlane configuration APIs.</p>
     */
    public static Map<String, Object> boardColumns(long boardId,
                                                   String sprintScope,
                                                   boolean mineOnly,
                                                   ApplicationUser viewer,
                                                   SearchService searchService,
                                                   AvatarService avatarService,
                                                   ApplicationProperties applicationProperties) {
        RapidView view = loadView(boardId, viewer);
        if (view == null) {
            return null;
        }
        SearchRequestManager searchRequestManager =
                ComponentAccessor.getComponent(SearchRequestManager.class);
        String boardJql = filterJql(view, viewer, searchRequestManager, searchService);
        String base = boardJql == null ? "" : ORDER_BY.matcher(boardJql).replaceFirst("").trim();

        String scopeClause = SPRINT_SCOPES.get(sprintScope);
        String effectiveScope = "all";
        String jql = base;
        if (scopeClause != null && sprintScopeUsable(boardJql, scopeClause, viewer, searchService)) {
            jql = base.isEmpty() ? scopeClause : "(" + base + ") AND " + scopeClause;
            effectiveScope = sprintScope;
        }
        // "My tasks" filter — scope the board to the caller's own issues.
        if (mineOnly) {
            jql = jql.isEmpty()
                    ? "assignee = currentUser()"
                    : "(" + jql + ") AND assignee = currentUser()";
        }

        List<Issue> issues = runSearch(jql, viewer, searchService);

        // Prefer the board's REAL configured columns (GreenHopper), so lane names
        // and order match Jira web and EMPTY lanes are still shown. Fall back to
        // status-grouping only when the column config is unavailable.
        String columnSource = "board";
        List<Map<String, Object>> columns =
                configuredColumns(view, issues, viewer, avatarService, applicationProperties);
        if (columns == null) {
            columns = statusFallbackColumns(issues, viewer, avatarService, applicationProperties);
            columnSource = "status";
        }
        int shown = 0;
        for (Map<String, Object> c : columns) {
            Object cnt = c.get("count");
            if (cnt instanceof Integer) {
                shown += (Integer) cnt;
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("boardId", boardId);
        body.put("name", JimSanitizer.sanitizeText(view.getName()));
        body.put("type", typeOf(view));
        body.put("sprint", effectiveScope);
        body.put("mine", mineOnly);
        body.put("total", issues.size());
        // Issues actually placed on a configured lane (mirrors Jira web, which
        // hides issues whose status is not mapped to any board column).
        body.put("shown", shown);
        body.put("columnSource", columnSource);
        body.put("truncated", issues.size() >= MAX_BOARD_ISSUES);
        body.put("columns", columns);
        return body;
    }

    /**
     * Build lanes from the board's configured GreenHopper columns
     * ({@link ColumnService#getVisibleColumns}). Returns ALL visible columns in
     * board order — including empty ones — with the real column name and the set
     * of statuses mapped to each column. Issues are placed by the board's
     * status→column mapping, exactly as Jira web does. Returns {@code null} when
     * the column configuration cannot be read, so the caller can fall back.
     */
    private static List<Map<String, Object>> configuredColumns(RapidView view,
                                                               List<Issue> issues,
                                                               ApplicationUser viewer,
                                                               AvatarService avatarService,
                                                               ApplicationProperties applicationProperties) {
        try {
            ColumnService columnService =
                    ComponentAccessor.getOSGiComponentInstanceOfType(ColumnService.class);
            if (columnService == null) {
                return null;
            }
            List<Column> cols = columnService.getVisibleColumns(view);
            if (cols == null || cols.isEmpty()) {
                return null;
            }
            ConstantsManager constantsManager = ComponentAccessor.getConstantsManager();

            // status id -> column index (first column that maps the status wins).
            Map<String, Integer> statusToCol = new HashMap<>();
            List<List<Issue>> buckets = new ArrayList<>();
            for (int i = 0; i < cols.size(); i++) {
                buckets.add(new ArrayList<>());
                List<String> statusIds = cols.get(i).getStatusIds();
                if (statusIds != null) {
                    for (String sid : statusIds) {
                        if (sid != null && !statusToCol.containsKey(sid)) {
                            statusToCol.put(sid, i);
                        }
                    }
                }
            }
            for (Issue issue : issues) {
                Status st = issue.getStatus();
                if (st == null) {
                    continue;
                }
                Integer idx = statusToCol.get(st.getId());
                if (idx != null) {
                    buckets.get(idx).add(issue);
                }
            }

            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = 0; i < cols.size(); i++) {
                Column col = cols.get(i);
                List<Map<String, Object>> cards = new ArrayList<>();
                for (Issue issue : buckets.get(i)) {
                    cards.add(JimMobileIssues.toIssueMap(issue, viewer, avatarService, applicationProperties));
                }
                List<Map<String, Object>> statuses = new ArrayList<>();
                String repCategory = null;
                List<String> statusIds = col.getStatusIds();
                if (statusIds != null) {
                    for (String sid : statusIds) {
                        Status st = constantsManager.getStatus(sid);
                        if (st == null) {
                            continue;
                        }
                        Map<String, Object> sm = new LinkedHashMap<>();
                        sm.put("id", st.getId());
                        sm.put("name", st.getName());
                        String cat = st.getStatusCategory() != null
                                ? st.getStatusCategory().getKey() : null;
                        sm.put("category", cat);
                        statuses.add(sm);
                        if (repCategory == null && cat != null) {
                            repCategory = cat;
                        }
                    }
                }
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("id", col.getId() != null ? String.valueOf(col.getId()) : ("col-" + i));
                c.put("name", JimSanitizer.sanitizeText(resolveColumnName(col.getName())));
                c.put("statusCategory", repCategory);
                c.put("statuses", statuses);
                c.put("count", cards.size());
                c.put("issues", cards);
                out.add(c);
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Fallback lane builder: group issues by their status name (the pre-Fix-3
     * behaviour). Used only when the board's real column configuration cannot be
     * read. Empty lanes cannot be shown here because there is no column config to
     * enumerate them — this is the documented degraded mode.
     */
    private static List<Map<String, Object>> statusFallbackColumns(List<Issue> issues,
                                                                   ApplicationUser viewer,
                                                                   AvatarService avatarService,
                                                                   ApplicationProperties applicationProperties) {
        Map<String, List<Issue>> byStatus = new LinkedHashMap<>();
        Map<String, String> statusCategory = new LinkedHashMap<>();
        Map<String, String> statusId = new LinkedHashMap<>();
        for (Issue issue : issues) {
            String status = issue.getStatus() != null ? issue.getStatus().getName() : "—";
            byStatus.computeIfAbsent(status, k -> new ArrayList<>()).add(issue);
            if (!statusCategory.containsKey(status)) {
                statusCategory.put(status, categoryKey(issue));
                statusId.put(status, issue.getStatus() != null ? issue.getStatus().getId() : status);
            }
        }
        List<String> ordered = new ArrayList<>(byStatus.keySet());
        List<String> insertion = new ArrayList<>(ordered);
        ordered.sort((a, b) -> {
            int ra = categoryRank(statusCategory.get(a));
            int rb = categoryRank(statusCategory.get(b));
            if (ra != rb) {
                return Integer.compare(ra, rb);
            }
            return Integer.compare(insertion.indexOf(a), insertion.indexOf(b));
        });

        List<Map<String, Object>> columns = new ArrayList<>();
        for (String status : ordered) {
            List<Issue> colIssues = byStatus.get(status);
            List<Map<String, Object>> cards = new ArrayList<>();
            for (Issue issue : colIssues) {
                cards.add(JimMobileIssues.toIssueMap(issue, viewer, avatarService, applicationProperties));
            }
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", statusId.get(status));
            sm.put("name", status);
            sm.put("category", statusCategory.get(status));
            List<Map<String, Object>> statuses = new ArrayList<>();
            statuses.add(sm);

            Map<String, Object> col = new LinkedHashMap<>();
            col.put("id", "status-" + statusId.get(status));
            col.put("name", status);
            col.put("statusCategory", statusCategory.get(status));
            col.put("statuses", statuses);
            col.put("count", cards.size());
            col.put("issues", cards);
            columns.add(col);
        }
        return columns;
    }

    /**
     * Board columns from a default GreenHopper preset store their name as an
     * i18n key (e.g. {@code gh.workflow.preset.todo}); Jira web renders these
     * localized. Resolve them via the viewer's {@link I18nHelper} so the mobile
     * lane name matches Jira web. A custom (admin-renamed) column name is not a
     * key, so {@code getText} returns it unchanged.
     */
    private static String resolveColumnName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        try {
            I18nHelper i18n = ComponentAccessor.getJiraAuthenticationContext().getI18nHelper();
            if (i18n != null) {
                String txt = i18n.getText(raw);
                if (txt != null && !txt.isEmpty()) {
                    return txt;
                }
            }
        } catch (Throwable ignore) {
            // fall through to the raw name
        }
        return raw;
    }

    private static RapidView loadView(long boardId, ApplicationUser viewer) {
        try {
            RapidViewService rapidViewService =
                    ComponentAccessor.getOSGiComponentInstanceOfType(RapidViewService.class);
            if (rapidViewService == null) {
                return null;
            }
            ServiceOutcome<RapidView> outcome = rapidViewService.getRapidView(viewer, boardId);
            if (outcome == null || !outcome.isValid()) {
                return null;
            }
            return outcome.getValue();
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<Issue> runSearch(String jql,
                                         ApplicationUser viewer,
                                         SearchService searchService) {
        List<Issue> out = new ArrayList<>();
        try {
            SearchService.ParseResult parsed = jql == null || jql.trim().isEmpty()
                    ? null : searchService.parseQuery(viewer, jql);
            Query query = parsed != null && parsed.isValid() ? parsed.getQuery() : null;
            if (query == null) {
                return out;
            }
            PagerFilter<Issue> pager = new PagerFilter<>(MAX_BOARD_ISSUES);
            pager.setStart(0);
            SearchResults<Issue> results = searchService.search(viewer, query, pager);
            if (results != null && results.getResults() != null) {
                out.addAll(results.getResults());
            }
        } catch (Throwable t) {
            // Bad board JQL etc. — return whatever we have (usually empty).
        }
        return out;
    }

    /** True when {@code (board) AND <scopeClause>} parses for this viewer. */
    private static boolean sprintScopeUsable(String boardJql,
                                             String scopeClause,
                                             ApplicationUser viewer,
                                             SearchService searchService) {
        try {
            String base = boardJql == null ? "" : ORDER_BY.matcher(boardJql).replaceFirst("").trim();
            String jql = base.isEmpty() ? scopeClause : "(" + base + ") AND " + scopeClause;
            SearchService.ParseResult parsed = searchService.parseQuery(viewer, jql);
            return parsed != null && parsed.isValid();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String categoryKey(Issue issue) {
        try {
            if (issue.getStatus() != null && issue.getStatus().getStatusCategory() != null) {
                return issue.getStatus().getStatusCategory().getKey();
            }
        } catch (Throwable ignore) {
            // fall through
        }
        return null;
    }

    private static int categoryRank(String categoryKey) {
        if (categoryKey == null) {
            return 1;
        }
        switch (categoryKey) {
            case "new":
            case "undefined":
                return 0;
            case "indeterminate":
                return 1;
            case "done":
                return 2;
            default:
                return 3;
        }
    }
}
