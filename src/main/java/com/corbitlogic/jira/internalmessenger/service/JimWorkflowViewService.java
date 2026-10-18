package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.IssueWorkflowManager;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.ConditionDescriptor;
import com.opensymphony.workflow.loader.ConditionsDescriptor;
import com.opensymphony.workflow.loader.FunctionDescriptor;
import com.opensymphony.workflow.loader.RestrictionDescriptor;
import com.opensymphony.workflow.loader.StepDescriptor;
import com.opensymphony.workflow.loader.ValidatorDescriptor;
import com.opensymphony.workflow.loader.WorkflowDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Phase 8D — read-only Workflow Viewer service. Builds a safe, redacted DTO for an
 * issue's workflow (statuses, transitions, conditions/validators/post-functions,
 * available-now transitions, computed layout) from Jira backend APIs ONLY
 * (WorkflowManager / JiraWorkflow / OSWorkflow descriptor / IssueWorkflowManager).
 *
 * Never mutates the issue, never transitions, never writes DB, never touches Jira's
 * native Workflow Designer frontend. Cluster-safe (pure reads). Rule parameters that
 * could carry scripts/secrets are redacted; technical class/plugin detail is gated to
 * admins. Failures become warnings, not crashes.
 */
public final class JimWorkflowViewService {

    private JimWorkflowViewService() {}

    /** Arg keys that are safe to surface for built-in Jira rules. */
    private static final Set<String> SAFE_ARG_KEYS = new LinkedHashSet<>(java.util.Arrays.asList(
            "group", "groupname", "jira.permission", "permissionkey", "permission",
            "role", "projectrole", "jira.projectrole.id", "fieldid", "field", "fieldsrequired",
            "status", "statusid", "event", "eventtype", "eventtypeid", "resolution", "assignee",
            "monitor", "comparisonvalue", "fieldvalue"));

    /** Arg keys/values that must never be emitted (scripts/secrets/urls/queries). */
    private static final Pattern SENSITIVE = Pattern.compile(
            "script|groovy|beanshell|jython|code|password|secret|token|cred|apikey|api_key|"
            + "url|http|https|sql|query|jql|expression|condition\\.script|email|webhook",
            Pattern.CASE_INSENSITIVE);

    /** Friendly names for common built-in Jira workflow rule classes. */
    private static final Map<String, String> FRIENDLY = new LinkedHashMap<>();
    static {
        FRIENDLY.put("AllowOnlyAssignee", "Only the assignee");
        FRIENDLY.put("AllowOnlyReporter", "Only the reporter");
        FRIENDLY.put("PermissionCondition", "Permission required");
        FRIENDLY.put("InProjectRoleCondition", "In project role");
        FRIENDLY.put("UserInGroupCondition", "In group");
        FRIENDLY.put("SubTaskBlockingCondition", "Sub-tasks must be in status");
        FRIENDLY.put("PermissionValidator", "Permission required");
        FRIENDLY.put("FieldRequiredValidator", "Field(s) required");
        FRIENDLY.put("PreviousStatusCondition", "Previously in status");
        FRIENDLY.put("SeparationOfDutiesCondition", "Separation of duties");
        FRIENDLY.put("UpdateIssueFieldFunction", "Update issue field");
        FRIENDLY.put("FireIssueEventFunction", "Fire event");
        FRIENDLY.put("AssignToCurrentUserFunction", "Assign to current user");
        FRIENDLY.put("IssueCreateFunction", "Create issue");
        FRIENDLY.put("IssueReindexFunction", "Re-index issue");
        FRIENDLY.put("GenerateChangeHistoryFunction", "Record change history");
        FRIENDLY.put("IssueStoreFunction", "Store issue");
        FRIENDLY.put("UpdateIssueStatusFunction", "Set status");
        FRIENDLY.put("CreateCommentFunction", "Add comment");
        FRIENDLY.put("SetIssueSecurityFromLevelFunction", "Set issue security");
    }

    // ----------------------------------------------------------------------- //
    public static Map<String, Object> buildWorkflowView(Issue issue, ApplicationUser viewer) {
        Map<String, Object> dto = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        boolean isAdmin = isAdmin(issue, viewer);

        dto.put("issueKey", issue.getKey());
        dto.put("issueId", issue.getId() != null ? String.valueOf(issue.getId()) : null);
        dto.put("summary", issue.getSummary());
        dto.put("projectKey", issue.getProjectObject() != null ? issue.getProjectObject().getKey() : null);
        dto.put("projectName", issue.getProjectObject() != null ? issue.getProjectObject().getName() : null);
        Map<String, Object> itype = new LinkedHashMap<>();
        if (issue.getIssueType() != null) {
            itype.put("id", issue.getIssueType().getId());
            itype.put("name", issue.getIssueType().getName());
            itype.put("iconUrl", issue.getIssueType().getCompleteIconUrl());
        }
        dto.put("issueType", itype);
        dto.put("viewerIsAdmin", isAdmin);

        JiraWorkflow wf;
        try {
            wf = ComponentAccessor.getWorkflowManager().getWorkflow(issue);
        } catch (Exception ex) {
            wf = null;
        }
        if (wf == null) {
            warnings.add("WORKFLOW_UNRESOLVED: could not resolve the workflow for this issue");
            dto.put("warnings", warnings);
            return dto;
        }
        Map<String, Object> wfMap = new LinkedHashMap<>();
        wfMap.put("name", safe(wf.getName()));
        try { wfMap.put("description", safe(wf.getDescription())); } catch (Exception ignored) {}
        try { wfMap.put("isDraft", wf.isDraftWorkflow()); } catch (Exception ignored) { wfMap.put("isDraft", false); }
        dto.put("workflow", wfMap);

        WorkflowDescriptor desc = wf.getDescriptor();
        Status currentStatus = issue.getStatus();
        String currentStatusId = currentStatus != null ? currentStatus.getId() : null;

        // ---- initial status ----
        String initialStatusId = null;
        try {
            List<?> inits = desc.getInitialActions();
            if (inits != null && !inits.isEmpty()) {
                ActionDescriptor init = (ActionDescriptor) inits.get(0);
                Status st = targetStatus(wf, init);
                if (st != null) initialStatusId = st.getId();
            }
        } catch (Exception ex) {
            warnings.add("INITIAL_STATUS_UNRESOLVED");
        }
        dto.put("initialStatusId", initialStatusId);

        // ---- statuses (one per workflow step) ----
        List<Map<String, Object>> statuses = new ArrayList<>();
        Map<Integer, String> stepIdToStatusId = new LinkedHashMap<>();
        Map<String, Integer> statusOutgoing = new LinkedHashMap<>();
        int order = 0;
        try {
            @SuppressWarnings("unchecked")
            List<StepDescriptor> steps = desc.getSteps();
            for (StepDescriptor step : steps) {
                order++;
                Status st = null;
                try { st = wf.getLinkedStatusObject(step); } catch (Exception ignored) {}
                if (st == null) {
                    warnings.add("STEP_WITHOUT_STATUS: step " + step.getId());
                    continue;
                }
                stepIdToStatusId.put(step.getId(), st.getId());
                int outgoing = 0;
                try { outgoing = step.getActions() != null ? step.getActions().size() : 0; } catch (Exception ignored) {}
                statusOutgoing.put(st.getId(), outgoing);
                Map<String, Object> sm = new LinkedHashMap<>();
                String cat = st.getStatusCategory() != null ? st.getStatusCategory().getKey() : null;
                sm.put("id", st.getId());
                sm.put("name", st.getName());
                sm.put("category", cat);
                sm.put("color", st.getStatusCategory() != null ? st.getStatusCategory().getColorName() : null);
                sm.put("isCurrent", st.getId().equals(currentStatusId));
                sm.put("isInitial", st.getId().equals(initialStatusId));
                sm.put("isTerminal", "done".equals(cat));
                sm.put("stepId", step.getId());
                sm.put("order", order);
                statuses.add(sm);
            }
        } catch (Exception ex) {
            warnings.add("STATUS_ENUMERATION_FAILED: " + ex.getClass().getSimpleName());
        }
        // Terminal heuristic: a non-done status with zero outgoing transitions is terminal
        // ONLY when the workflow has no global actions (with globals, every status can move,
        // so "done" category is the sole reliable terminal signal).
        boolean hasGlobals = false;
        try {
            List<?> g = desc.getGlobalActions();
            hasGlobals = g != null && !g.isEmpty();
        } catch (Exception ignored) {}
        if (!hasGlobals) {
            for (Map<String, Object> sm : statuses) {
                if (Boolean.FALSE.equals(sm.get("isTerminal"))
                        && statusOutgoing.getOrDefault((String) sm.get("id"), 1) == 0) {
                    sm.put("isTerminal", true);
                }
            }
        }

        // current status block
        Map<String, Object> cur = new LinkedHashMap<>();
        if (currentStatus != null) {
            cur.put("id", currentStatus.getId());
            cur.put("name", currentStatus.getName());
            String cc = currentStatus.getStatusCategory() != null ? currentStatus.getStatusCategory().getKey() : null;
            cur.put("category", cc);
            cur.put("color", currentStatus.getStatusCategory() != null ? currentStatus.getStatusCategory().getColorName() : null);
            cur.put("isCurrent", true);
        }
        dto.put("currentStatus", cur);
        dto.put("statuses", statuses);

        // ---- available-now transitions for the viewer ----
        Set<Integer> availableIds = new LinkedHashSet<>();
        List<Map<String, Object>> availableTransitions = new ArrayList<>();
        try {
            IssueWorkflowManager iwm = ComponentAccessor.getComponentOfType(IssueWorkflowManager.class);
            Collection<ActionDescriptor> avail = iwm.getAvailableActions(issue, viewer);
            if (avail != null) {
                for (ActionDescriptor a : avail) {
                    availableIds.add(a.getId());
                    Status to = targetStatus(wf, a);
                    Map<String, Object> am = new LinkedHashMap<>();
                    am.put("id", String.valueOf(a.getId()));
                    am.put("name", a.getName());
                    am.put("toStatusId", to != null ? to.getId() : null);
                    availableTransitions.add(am);
                }
            }
        } catch (Exception ex) {
            warnings.add("AVAILABLE_TRANSITIONS_FAILED: " + ex.getClass().getSimpleName());
        }

        // ---- all transitions ----
        List<Map<String, Object>> transitions = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<Integer> commonIds = commonActionIds(desc);
        try {
            @SuppressWarnings("unchecked")
            List<StepDescriptor> steps = desc.getSteps();
            for (StepDescriptor step : steps) {
                String fromId = stepIdToStatusId.get(step.getId());
                @SuppressWarnings("unchecked")
                List<ActionDescriptor> acts = step.getActions();
                if (acts == null) continue;
                for (ActionDescriptor a : acts) {
                    String type = commonIds.contains(a.getId()) ? "common" : "normal";
                    transitions.add(transition(wf, a, fromId, currentStatusId, availableIds, type, warnings, isAdmin, seen));
                }
            }
            // global actions (from any status)
            @SuppressWarnings("unchecked")
            List<ActionDescriptor> globals = desc.getGlobalActions();
            if (globals != null) {
                for (ActionDescriptor a : globals) {
                    transitions.add(transition(wf, a, null, currentStatusId, availableIds, "global", warnings, isAdmin, seen));
                }
            }
        } catch (Exception ex) {
            warnings.add("TRANSITION_ENUMERATION_FAILED: " + ex.getClass().getSimpleName());
        }
        dto.put("transitions", transitions);
        dto.put("availableTransitions", availableTransitions);

        // ---- computed layout (category buckets; no Jira designer, no Raphael) ----
        dto.put("layout", computeLayout(statuses, transitions));
        dto.put("dense", statuses.size() > 12 || transitions.size() > 40);
        dto.put("warnings", warnings);
        return dto;
    }

    // ----------------------------------------------------------------------- //
    private static Map<String, Object> transition(JiraWorkflow wf, ActionDescriptor a, String fromId,
                                                  String currentStatusId, Set<Integer> availableIds,
                                                  String type, List<String> warnings, boolean isAdmin,
                                                  Set<String> seen) {
        Map<String, Object> m = new LinkedHashMap<>();
        Status to = targetStatus(wf, a);
        String toId = to != null ? to.getId() : null;
        String effType = type;
        if (toId != null && toId.equals(fromId)) {
            effType = "loop";
        }
        if (to == null) {
            warnings.add("TRANSITION_TARGET_UNRESOLVED: action " + a.getId() + " (" + a.getName() + ")");
        }
        m.put("id", String.valueOf(a.getId()));
        m.put("name", a.getName());
        m.put("fromStatusId", fromId);
        m.put("toStatusId", toId);
        m.put("type", effType);
        boolean fromCurrent = (fromId != null && fromId.equals(currentStatusId)) || "global".equals(type);
        m.put("isFromCurrentStatus", fromCurrent);
        m.put("isAvailableNow", availableIds.contains(a.getId()));
        String screen = null;
        try { screen = a.getView(); } catch (Exception ignored) {}
        m.put("hasScreen", screen != null && !screen.trim().isEmpty());

        List<Map<String, Object>> conditions = new ArrayList<>();
        List<Map<String, Object>> validators = new ArrayList<>();
        List<Map<String, Object>> postFns = new ArrayList<>();
        List<String> restr = new ArrayList<>();
        try {
            RestrictionDescriptor r = a.getRestriction();
            if (r != null && r.getConditionsDescriptor() != null) {
                collectConditions(r.getConditionsDescriptor(), conditions, isAdmin);
            }
        } catch (Exception ex) { warnings.add("CONDITIONS_UNREADABLE: action " + a.getId()); }
        try {
            @SuppressWarnings("unchecked")
            List<ValidatorDescriptor> vs = a.getValidators();
            if (vs != null) for (ValidatorDescriptor v : vs) validators.add(rule("validator", v.getType(), v.getArgs(), isAdmin));
        } catch (Exception ex) { warnings.add("VALIDATORS_UNREADABLE: action " + a.getId()); }
        try {
            @SuppressWarnings("unchecked")
            List<FunctionDescriptor> fs = a.getPostFunctions();
            if (fs != null) for (FunctionDescriptor f : fs) postFns.add(rule("postFunction", f.getType(), f.getArgs(), isAdmin));
        } catch (Exception ex) { warnings.add("POSTFUNCTIONS_UNREADABLE: action " + a.getId()); }

        for (Map<String, Object> c : conditions) restr.add(String.valueOf(c.get("name")));
        m.put("conditions", conditions);
        m.put("validators", validators);
        m.put("postFunctions", postFns);
        m.put("restrictionsSummary", restr);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static void collectConditions(ConditionsDescriptor cd, List<Map<String, Object>> out, boolean isAdmin) {
        List<?> conds = cd.getConditions();
        if (conds == null) return;
        for (Object o : conds) {
            if (o instanceof ConditionsDescriptor) {
                collectConditions((ConditionsDescriptor) o, out, isAdmin);
            } else if (o instanceof ConditionDescriptor) {
                ConditionDescriptor c = (ConditionDescriptor) o;
                out.add(rule("condition", c.getType(), c.getArgs(), isAdmin));
            }
        }
    }

    /** Summarize + redact one rule. type is the osworkflow type ("class", "beanshell", ...). */
    private static Map<String, Object> rule(String kind, String osType, Map<?, ?> args, boolean isAdmin) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<String> w = new ArrayList<>();
        String className = args != null ? str(args.get("class.name")) : null;
        String pluginKey = detectPlugin(className);
        String simple = className != null && className.contains(".")
                ? className.substring(className.lastIndexOf('.') + 1) : className;
        boolean scripty = "beanshell".equalsIgnoreCase(osType) || "jython".equalsIgnoreCase(osType)
                || "script".equalsIgnoreCase(osType)
                || (className != null && (className.toLowerCase(Locale.ROOT).contains("script")
                    || className.toLowerCase(Locale.ROOT).contains("groovy")));
        boolean builtIn = className != null && className.startsWith("com.atlassian.jira.workflow");
        String friendly = simple != null ? FRIENDLY.getOrDefault(simple, null) : null;

        m.put("type", kind);
        if (scripty || (!builtIn && className != null)) {
            // Third-party / script-bearing rule: label only, details redacted.
            m.put("name", friendly != null ? friendly : (humanize(simple) != null ? humanize(simple) : "Custom rule"));
            m.put("pluginKey", isAdmin ? pluginKey : null);
            m.put("className", isAdmin ? className : null);
            m.put("visibility", "redacted");
            m.put("parameters", Collections.emptyList());
            w.add("جزئیات محرمانه مخفی شده است");
        } else {
            m.put("name", friendly != null ? friendly : humanize(simple));
            m.put("pluginKey", isAdmin ? pluginKey : null);
            m.put("className", isAdmin ? className : null);
            m.put("visibility", builtIn ? "public" : "adminOnly");
            m.put("parameters", safeParams(args));
        }
        m.put("description", null);
        m.put("warnings", w);
        return m;
    }

    private static List<Map<String, Object>> safeParams(Map<?, ?> args) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (args == null) return out;
        for (Map.Entry<?, ?> e : args.entrySet()) {
            String k = str(e.getKey());
            if (k == null) continue;
            String kl = k.toLowerCase(Locale.ROOT);
            if (kl.equals("class.name")) continue;
            if (SENSITIVE.matcher(kl).find()) continue;
            if (!SAFE_ARG_KEYS.contains(kl)) continue;
            String v = str(e.getValue());
            if (v == null || SENSITIVE.matcher(v).find()) continue;
            if (v.length() > 120) v = v.substring(0, 117) + "...";
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("key", k);
            p.put("value", v);
            out.add(p);
        }
        return out;
    }

    private static Map<String, Object> computeLayout(List<Map<String, Object>> statuses, List<Map<String, Object>> transitions) {
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("source", "computed");
        int[] colCount = new int[3];
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map<String, Object> s : statuses) {
            String cat = (String) s.get("category");
            int col = "new".equals(cat) ? 0 : ("done".equals(cat) ? 2 : 1);
            int row = colCount[col]++;
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", s.get("id"));
            n.put("x", col * 260);
            n.put("y", row * 120);
            n.put("col", col);
            nodes.add(n);
            s.put("x", col * 260);
            s.put("y", row * 120);
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> t : transitions) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", t.get("id"));
            e.put("from", t.get("fromStatusId"));
            e.put("to", t.get("toStatusId"));
            e.put("type", t.get("type"));
            edges.add(e);
        }
        layout.put("nodes", nodes);
        layout.put("edges", edges);
        return layout;
    }

    // ----------------------------------------------------------------------- //
    private static Status targetStatus(JiraWorkflow wf, ActionDescriptor a) {
        try {
            if (a.getUnconditionalResult() == null) return null;
            int stepId = a.getUnconditionalResult().getStep();
            if (stepId <= 0) return null; // -1 = "same step" (loop); caller treats null gracefully
            StepDescriptor step = wf.getDescriptor().getStep(stepId);
            return step != null ? wf.getLinkedStatusObject(step) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<Integer> commonActionIds(WorkflowDescriptor desc) {
        Set<Integer> ids = new LinkedHashSet<>();
        try {
            Map<Integer, ActionDescriptor> common = desc.getCommonActions();
            if (common != null) ids.addAll(common.keySet());
        } catch (Exception ignored) {}
        return ids;
    }

    private static boolean isAdmin(Issue issue, ApplicationUser viewer) {
        try {
            if (viewer == null) return false;
            if (ComponentAccessor.getGlobalPermissionManager().hasPermission(
                    com.atlassian.jira.permission.GlobalPermissionKey.ADMINISTER, viewer)) {
                return true;
            }
            return issue.getProjectObject() != null && ComponentAccessor.getPermissionManager()
                    .hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, issue.getProjectObject(), viewer);
        } catch (Exception ex) {
            return false;
        }
    }

    private static String detectPlugin(String className) {
        if (className == null) return null;
        String c = className.toLowerCase(Locale.ROOT);
        if (c.contains("onresolve") || c.contains("scriptrunner") || c.contains("canned")) return "ScriptRunner";
        if (c.contains("innovalog") || c.contains("jmwe")) return "JMWE";
        if (c.contains("suite") || c.contains("jsutils") || c.contains("beecom")) return "JSU";
        if (className.startsWith("com.atlassian.jira")) return "jira-core";
        int i = className.indexOf('.', className.indexOf('.') + 1);
        return i > 0 ? className.substring(0, i) : className;
    }

    private static String humanize(String simple) {
        if (simple == null) return null;
        String s = simple.replaceAll("(Condition|Validator|Function)$", "")
                .replaceAll("([a-z])([A-Z])", "$1 $2").trim();
        return s.isEmpty() ? simple : s;
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static String safe(String s) { return s == null ? null : s; }
}
