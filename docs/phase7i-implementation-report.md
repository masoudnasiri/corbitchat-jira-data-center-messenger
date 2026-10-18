# Phase 7I — Implementation report (DEV)

DEV-only. Production, Gateway, and Workspace Agent were not changed.

See `docs/phase7i-unified-report-metrics-audit.md` for the full audit and contract.

## Versions

| Surface | Before | After (DEV) |
|---|---|---|
| Plugin | `figmachat7h-functional1` | `figmachat7i-reportmetrics1` |
| Hub PWA | `1.0.0+111 / figma-task-detail-person-rtl1` | `1.0.0+112 / figma-phase7i-reportmetrics1` |
| Production plugin | `figmachat7h-functional1` | **unchanged** |
| Production PWA | `+107` | **unchanged** |
| Gateway | StartedAt `2026-07-31T23:35:19Z` | **unchanged** |

## What shipped

One service, `JimMobileReportMetricsService`, is the only report-counter source for:

- `GET /rest/corbit-mobile/1.0/dashboard` → `reportSummary.reportMetrics`
- `GET /rest/corbit-mobile/1.0/dashboard/report-status?period=`
- `GET /rest/corbit-mobile/1.0/issues/reports?period=`

Same JQL (assignee + unresolved + level not empty), same cap 25, same
`computeSummary` loop. Reports aggregate is the full scan, not the page.

Trusted fields: `valid`, `missed`, `rawTotal`, `outOfWindow`, `totalRequired`.
Periods: `all`, `today`, rolling 7-day `week`, calendar `month`, optional
`from`/`to`. Week/month use the engine window ledger (same SATISFIED/MISSED/OPEN
rules as today). Fixed-minimum issues contribute to `all`/`today` only.

Dashboard donut now shows Valid / Missed / Out of window. Tapping a number
opens the issue list behind it (same sheet as Reports). Reports gauge uses
the same four counters and the same period selector.

## DEV smoke (same user, same period)

Dashboard `/dashboard/report-status` and `/issues/reports` returned identical
`reportMetrics` for `all`, `today`, `week`, and `month`.

Example (`all`): `valid=0`, `missed=0`, `outOfWindow=1`, `rawTotal=19`,
`capped=false`, `source=report-engine`. Two configured issues in the scan.

## Artifacts (DEV)

| Item | Value |
|---|---|
| Plugin jar | `/root/jira-dev/releases/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics1.jar` |
| Plugin sha256 | `31d15cc09c5c36fd8b112e8931d31dd0003e1fe378a1fe321b8ba0ea765c100a` |
| Hub PWA tarball | `/root/jira-dev/releases/corbitchat-mobile-pwa-hub-1.0.0+112-figma-phase7i-reportmetrics1.tgz` |
| PWA sha256 | `013098e67d07b397943b03d59dc86ead75ec1ac34a704b1efe34f207eefa5db7` |
| Hub rollback | `/root/jira-backups/dev-hub-before-7i-20260816-143050` |

## Not changed

Submit/reply, working calendar, enforcement, Gateway, Workspace Agent,
task `openWorkCounts` / `statusCounts`, Production, GitHub.
