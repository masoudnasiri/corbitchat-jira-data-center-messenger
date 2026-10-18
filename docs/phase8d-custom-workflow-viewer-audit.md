# Phase 8D — Custom Workflow Viewer: Audit

**Date (UTC):** 2026-10-06. No code written before this audit.

## 1. Web Jira issue-page integration
- The Corbit issue-page UI is delivered as native Jira **issue-tabpanels**, not a single button row:
  - `JimReportTabPanel extends AbstractIssueTabPanel3` (`web/JimReportTabPanel.java`) → "Report / گزارش" tab (`atlassian-plugin.xml` `<issue-tabpanel key="jim-report-tabpanel">`, order 60, `supports-ajax-load`). Its `getActions` renders a mount `<div data-issue-key=...>`; all rendering is client-side by `js/jim-report.js` against `/rest/jim/1.0/report/*`.
  - Comment-reply enhancement: `web-resource jim-comment-reply-resources` (context `jira.view.issue`) → `js/jim-comment-reply.js`.
- Web resource binding: `<web-resource key="jim-report-resources">` with `<context>jira.view.issue</context>` + `<context>jira.navigator</context>` (JS must be on the page because the tab AJAX fragment can't `requireResource`). Loads `css/jim-report.css` + `js/jim-report.js`.
- **Issue key detection:** the tab panel server-renders `data-issue-key` into the mount `<div>`; `jim-report.js` reads `root.getAttribute('data-issue-key')` (no reliance on `JIRA.Issue`/global `_`).
- **REST call pattern:** `js/jim-report.js` uses `BASE = (window.contextPath||'') + '/rest/jim/1.0/report'` + an XHR `req()` helper (AUI/AJS dependency `com.atlassian.auiplugin:ajs`). No lodash/underscore.
- **Chosen placement for 8D:** a NEW sibling `issue-tabpanel` "فرآیند / Workflow" (`JimWorkflowTabPanel`) + `web-resource jim-workflow-resources` (contexts `jira.view.issue` + `jira.navigator`) + `js/jim-workflow.js` + `css/jim-workflow.css`. This mirrors the proven Report tab exactly and does NOT touch `jim-report.js` (zero regression to Report/Views/Attachments/Comments). The tab shows the viewer; a "مشاهده فرآیند" control can also open an enlarged modal within the same JS.

## 2. Mobile issue detail
- `lib/features/tasks/issue_detail_screen.dart`: hero + status area near top; `_PillTabs` row (Details / Attachments / Comments + Report when applicable). Status category from `IssueCard.statusCategory` (`new|indeterminate|done`) in `lib/models/issue.dart`.
- API client `lib/api/corbit_api.dart` has issue methods (`/rest/corbit-mobile/1.0/issues/*`). I will add `getIssueWorkflow(issueKey)`.
- **Placement:** a "مشاهده فرآیند" button/chip near the top status area → navigates to a new `IssueWorkflowScreen` (full screen) that fetches `/rest/corbit-mobile/1.0/issues/{key}/workflow-view`. Existing tabs/chips untouched.

## 3. Plugin issue REST endpoints (reuse patterns)
- `rest/*` package auto-scanned by `<rest key="jim-rest" path="/jim" version="1.0">` → `/rest/jim/1.0/*`. Mobile: `<rest key="corbit-mobile-rest" path="/corbit-mobile" version="1.0">` → `/rest/corbit-mobile/1.0/*`. A new `@Path("/issues")` resource in each package auto-registers the new endpoints.
- Issue resolution + permission pattern (from `CorbitMobileIssueResource`): `ComponentAccessor.getIssueManager().getIssueByCurrentKey(key)`; `ComponentAccessor.getPermissionManager().hasPermission(ProjectPermissions.BROWSE_PROJECTS, issue, viewer)`; 404 when null/not visible, 403 for action perms. Issue security is enforced by `hasPermission(BROWSE_PROJECTS,...)` (Jira applies issue-level security).

## 4. Jira workflow data APIs available (confirmed on classpath + already used)
`CorbitMobileIssueResource` already imports/uses: `IssueWorkflowManager`, `JiraWorkflow`, `com.opensymphony.workflow.loader.ActionDescriptor`, `ConstantsManager`, `Status`. osworkflow `2.9.0-atlassian-1` jar present in the offline repo → `WorkflowDescriptor` (steps, actions, global/common/initial actions, `ConditionsDescriptor`, `ValidatorDescriptor`, `FunctionDescriptor`, `RestrictionDescriptor`).
- **Available transitions for current user** (existing pattern, lines ~1156-1181): `iwm.getAvailableActions(issue, viewer)` → `Collection<ActionDescriptor>`; target status = `wf.getLinkedStatusObject(wf.getDescriptor().getStep(action.getUnconditionalResult().getStep()))`.
- **All statuses:** `wf.getDescriptor().getSteps()` → each `StepDescriptor` → `wf.getLinkedStatusObject(step)` / `wf.getLinkedStatusId(step)`; status category via `Status.getStatusCategory().getKey()`, color via `getStatusCategory().getColorName()`.
- **All transitions:** per-step `step.getActions()` (normal) + `descriptor.getGlobalActions()` (global) + `descriptor.getCommonActions()` (common) + `descriptor.getInitialActions()` (initial). Target step via `action.getUnconditionalResult().getStep()` (step == source → loop; missing/−1 → warning).
- **Rules:** `action.getRestriction().getConditionsDescriptor()` (nested AND/OR `ConditionDescriptor` with `getType()`/`getArgs()`), `action.getValidators()` (`ValidatorDescriptor`), `action.getPostFunctions()` (`FunctionDescriptor`).
- **Initial status:** linked status of the step targeted by the single initial action.
- **Workflow name/draft:** `wf.getName()`, `wf.getDescription()`, `wf.isDraftWorkflow()`.
- **Layout:** Jira's stored layout is internal/fragile (and the native designer is the thing we're avoiding). 8D computes a deterministic category-bucketed layout (`layout.source="computed"`); DTO still carries nodes/edges + per-status x/y.

## 5. Workflow rule exposure / redaction
- **Safe for all issue viewers:** workflow name, statuses + categories/colors, transitions + names + from/to, available-now transitions, friendly rule TYPE labels (e.g. "Only Assignee", "Permission", "Field Required", "Fields Required On Transition", "Update Issue Field", "Fire Event", "Assign to Current User"). Built-in `com.atlassian.jira.workflow.*` conditions/validators/functions → friendly name + safe non-sensitive params (role/group/permission/field names, status), `visibility=public`.
- **Admin-only (project admin / jira admin):** raw `type`/`className`/`pluginKey` technical details → `visibility=adminOnly` (shown only to admins).
- **Redacted for everyone:** any rule whose type is `class`/script-bearing or from ScriptRunner/JMWE/JSU/custom plugins → show rule type + plugin/source + safe label, `visibility=redacted`, parameters dropped. Arg keys/values matching `script|groovy|code|condition|password|secret|token|cred|url|http|query|sql|beanshell|jython` are never emitted (redacted) regardless of plugin. "جزئیات محرمانه مخفی شده است".

## 6. Permission model
- Browse issue (`BROWSE_PROJECTS` via `hasPermission` which honors issue security) required; else 404 (issue invisible) per product convention. No Jira-admin required. Available transitions computed per current user via `IssueWorkflowManager.getAvailableActions`. Admin-enhanced rule detail gated by `PermissionManager` `ADMINISTER`/`ADMINISTER_PROJECTS`.

## 7. Design summary (feeds PART 2/3)
Shared `service/JimWorkflowViewService` builds the DTO from `WorkflowManager`/`JiraWorkflow`/`WorkflowDescriptor` + `IssueWorkflowManager` + `ConstantsManager`; redaction via a `JimWorkflowRuleRedactor` helper. Two thin REST resources (`rest/JimIssueWorkflowResource` `@Path("/issues")` and `mobile/rest/CorbitMobileWorkflowResource` `@Path("/issues")`) call the shared service after BROWSE check. Read-only; no mutation, no transition, no DB write; cluster-safe (pure reads).
