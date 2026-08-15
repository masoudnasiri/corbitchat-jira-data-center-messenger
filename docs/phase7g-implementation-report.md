# Phase 7G — Dashboard metrics correctness (DEV)

DEV-only. Production, Gateway, and Workspace Agent were not changed.

## What shipped

Plugin `figmachat7g-dashboard-metrics1` adds additive `reportSummary` on
`GET /dashboard` and rewires `/dashboard/report-status` to the report
engine. Mobile `1.0.0+100` maps the `گزارش‌ها` donut to that block only.

Top `کارهای باز من` numbers stay on `openWorkCounts` (task JQL). They
were audited as correct.

## Root cause

`_ReportsDonutCard` labeled `گزارش‌ها` but read task `statusCounts`
(Done / In Progress) plus `tasks.overdue`. `/dashboard/report-status`
was also assignee + `statusCategory` + issue `updated` / `duedate`.

## DEV live check (`m.nasiri`)

| Metric | Value | Notes |
|--------|-------|--------|
| `openWorkCounts` | active 7, overdue 1, today 0, followup 57 | Matches `tasks.*` for those tokens |
| `statusCounts` | done 16, inProgress 5, todo 2 | Task categories — **not** used by the reports card |
| `reportSummary` | available, scanned 2, complete 0, onTrack 1, overdue 0 | Matches `/issues/reports`: TEST-27 `ON_TRACK`, TEST-28 `ENDED_DUE_DATE` (omitted) |
| Mixed with statusCounts? | **No** | |
| `report-status` week/month | `available: false` | Honest |
| Unauth `/dashboard` | 401 | |

## Artifacts

See the agent final report for sha256 paths after APK packaging.

## Not started

Phase 7G Production rollout. Week/month report buckets.
