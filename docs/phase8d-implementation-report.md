# Phase 8D — Custom Workflow Viewer — Implementation Report

**Date (UTC):** 2026-10-06 · **Scope:** DEV-first (plugin + mobile). Production NOT deployed. No GitHub push.
**Plugin version:** `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2-8a-dynlevels1-8b-dayunit1-8c-groupsend1-chatlimit15000-1-8d-wfview1`
**Mobile:** `1.0.0+115`; DEV label `figma-phase8d-workflowview1`; 7G label `figma-phase8d-workflowview1-prod`.

## Feature
A read-only, professional **custom Workflow Viewer** inside the CorbitChat/JIM plugin (web issue page) and mobile app — replacing attempts to fix Jira's native Workflow Designer. Shows statuses, current/initial/terminal, all transitions (normal/global/common/loop/initial), available-now transitions for the current user, and safe condition/validator/post-function summaries. Never edits/transitions the issue; never calls/iframes the native Workflow Designer; no Raphaël; no global lodash/underscore.

## Backend (PART 2/3)
- **Shared service** `service/JimWorkflowViewService` (static, read-only) builds the DTO from Jira backend APIs only: `WorkflowManager.getWorkflow(issue)` → `JiraWorkflow` → OSWorkflow `WorkflowDescriptor` (steps/actions/global/common/initial; `RestrictionDescriptor`→`ConditionsDescriptor`, `ValidatorDescriptor`, `FunctionDescriptor`), `IssueWorkflowManager.getAvailableActions(issue,viewer)` for available-now, `ConstantsManager`/`Status` for category/color. Target status resolved via `action.getUnconditionalResult().getStep()` → `wf.getLinkedStatusObject(descriptor.getStep(stepId))`.
- **Endpoints:** web `GET /rest/jim/1.0/issues/{issueKey}/workflow-view` (`rest/JimIssueWorkflowResource`); mobile `GET /rest/corbit-mobile/1.0/issues/{issueKey}/workflow-view` (added to `CorbitMobileIssueResource`). Both: authenticated + `hasPermission(BROWSE_PROJECTS, issue, viewer)` (honors issue security) → 404 if invisible; no Jira-admin required; no mutation/DB write; cluster-safe (pure reads).
- **DTO:** issueKey/id/summary/project, issueType, workflow{name,description,isDraft}, currentStatus, initialStatusId, statuses[] (id,name,category,color,isCurrent,isInitial,isTerminal,order,x,y), transitions[] (id,name,from/toStatusId,type,isAvailableNow,isFromCurrentStatus,hasScreen,conditions/validators/postFunctions,restrictionsSummary), availableTransitions[], layout{source:"computed",nodes,edges}, dense, warnings[].
- **Terminal heuristic:** `category==done`; also zero-outgoing-when-no-global-actions (so all-global Simplified workflows don't mis-flag in-progress as terminal). Missing targets/draft/loops/duplicate names → warnings, never crash.

## Redaction/security (PART 2)
`JimWorkflowViewService` rule summarizer:
- Built-in `com.atlassian.jira.workflow.*` rules → friendly name + safe allow-listed params (role/group/permission/field/status/event/resolution), `visibility=public`.
- Script-bearing / ScriptRunner / JMWE / JSU / custom (non-builtin or beanshell/jython/groovy/"script") → label + plugin source only, `parameters=[]`, `visibility=redacted`, warning "جزئیات محرمانه مخفی شده است".
- Any arg key/value matching `script|groovy|beanshell|jython|code|password|secret|token|cred|apikey|url|http|sql|query|jql|expression|email|webhook` is never emitted.
- `className`/`pluginKey` returned only to Jira/project admins (`viewerIsAdmin`), and still never for script/secret bodies.

## Layout (PART 3)
Deterministic computed layout: category buckets (new→col0, indeterminate→col1, done→col2), rows within bucket; normalized x/y per node; edges carry type. Web renders hand-built **SVG** (rounded-rect nodes, arrowhead markers, dashed global edges, self-loop path) with a list fallback when `dense`. No Jira layout store, no Raphaël.

## Web UI (PART 4)
New issue-tabpanel `JimWorkflowTabPanel` ("فرآیند / Workflow", order 62) mirroring the Report tab; `web-resource jim-workflow-resources` (contexts `jira.view.issue` + `jira.navigator`) → `js/jim-workflow.js` + `css/jim-workflow.css`. The tab renders inline; a "مشاهده فرآیند" button opens an enlarged modal. Loading/error/empty states; graph/list toggle; current status teal, done green, available highlighted, redaction badges, expandable rule sections; RTL. Persian labels per spec. `jim-report.js`/Report/Views/Attachments/Comments untouched (new isolated files).

## Mobile UI (PART 5)
`corbit_api.getIssueWorkflow(key)`; new `IssueWorkflowScreen` (full screen, RTL, rounded cards, teal current / green done / neutral unavailable, expandable transition rule cards, warnings, offline/error states, no transition actions). Entry: a "مشاهده فرآیند" `TextButton.icon` in the issue-detail status strip → pushes the screen. Existing tabs/chips/detail untouched.

## Not changed
Native Jira Workflow Designer, workflow DB/layout, validators/conditions/post-functions, Gateway/MCP, eazyBI, Inbox, Automation, WebPush/FCM, report settings/engine, HAProxy/nginx/cluster. No DB migration (read-only feature; no AO changes).
