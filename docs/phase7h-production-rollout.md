# Phase 7H — Production rollout (plugin + PWA)

Deployed: 2026-08-15 after owner accepted DEV
`figmachat7h-functional1` / Hub `+102`.
Gateway was **not** touched. Workspace Agent and report/calendar
logic were **not** changed. Hub was **not** deployed.

## Preflight

| Check | Result |
|-------|--------|
| Production plugin before | `figmachat7g-dashboard-metrics1` (exactly one jar) sha256 `acd2929b4ec1c74d08e83df87ad0515ef5b0e02b95c368724168cd4032a941d2` |
| Production PWA before | `1.0.0+101 / figma-phase7g-dashboard-copy-rtl1-prod` |
| Gateway | `mcp-atlassian-prod` started `2026-07-31T23:35:19Z` — left running |
| Target plugin | `/root/jira-dev/releases/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7h-functional1.jar` |
| Plugin sha256 | `b3933dfed2ad986661c725fcaae13397a33f118175d7a7b80445239668a54949` (verified on Production host) |
| Target PWA | `/root/jira-dev/releases/corbitchat-mobile-pwa-7gtech-1.0.0+102-figma-phase7h-functional1-prod.tgz` |
| PWA sha256 | `673d654c318c6d4364c6db3ddfab7212dc5f2559c1537be33e61ee2437736cf5` (verified on Production host) |
| Brand | title/manifest **7G TECH**, no user-visible Hub, label `figma-phase7h-functional1-prod`, build 102 |
| Plugin source | `95f50c6ff9e0b02f83856a7f107b300ddb6e749b` |
| Mobile source | `18b0777b51161bc3fd0d3bba5c0c90c29c178df3` |

## Backups (`corbit-prod`, TS `20260815-110909`)

| Item | Path |
|------|------|
| Plugin jar (7g) | `/root/jira-backups/corbitchat-jira-dc-figmachat7g-dashboard-metrics1-20260815-110909.jar` |
| Installed-plugins tree | `/root/jira-backups/installed-plugins-20260815-110909/` |
| Plugin listing | `/root/jira-backups/plugin-listing-20260815-110909.txt` |
| Jira DB dump | `/root/jira-backups/jira-db-20260815-110909.sql` (122 MB; mysqldump PROCESS warning only) |
| Live PWA +101 | `/root/jira-backups/prod-mobile-101-before-phase7h-20260815-110909.tgz` (sha256 `65105d3bec1cd3a2dafe895b44bcb15587b58063ef002c388cbab4385279056a`) |

## Rollback scripts (`corbit-prod`)

`/root/jira-backups/rollback-figmachat7h-20260815-110909/ROLLBACK-plugin.sh` → 7g  
`/root/jira-backups/rollback-figmachat7h-20260815-110909/ROLLBACK-pwa.sh` → +101  

Do **not** run any gateway rollback.

## Plugin deploy

Path B: exactly one jar, stale transformed `figmachat7g-dashboard-metrics1_*.jar`
moved aside, webresource cache cleared, `docker restart jira-srv` only.

- Installed sha256 `b3933dfed2ad986661c725fcaae13397a33f118175d7a7b80445239668a54949`
- Localhost health 503 → **401** (~6.5 min). Chat servlet **302**.
- Gateway `StartedAt` unchanged.

Localhost authenticated `/dashboard` **200** for `m.nasiri`:

- `openWorkCounts` active 6 / overdue 2 / today 0 / followup **6**
- `filter=followup` total **6** (`COR-16`, `CM-7`, `CM-4`, `CM-5`, `CM-6`, `CM-3`) — assignees are other users; no overlap with assigned
- `filter=assigned` total **6**
- `filter=awaiting` total **1** (`COR-18`, `scheduleApproval.status=Pending`)
- `filter=rejected` total **0** (honest empty)
- `reportSummary.source` = `report-engine` (scanned 6, complete 1, onTrack 2, overdue 1)
- Boards still return `totalIssueCount` and `favorite`
- Unauthenticated search **401**

`GET /rest/jim/1.0/workspace-agent/status` as `m.nasiri` is **403**
(`Jira System Administrator permission is required`). That is the
existing permission gate, not `AUTHENTICATION_DENIED`. Agent settings
were not changed. Admin / MCP admin servlets exist (unauth **302**).

Installed jar contains Assistant filters, `باز کردن پیام`,
`openConversationAtMessage` (4), `openForwardModalForMessages`.

## PWA deploy

7G TECH tarball only. Stage → verify 102 / 7G TECH / no Hub → swap on
`nginx-jira`. No nginx restart. No Gateway restart.

Live `https://jira.7gtech.net/mobile/version.json`:

`build_number` 102, `build_label` `figma-phase7h-functional1-prod`, `brand` `sevengtech`

`/mobile/` **200**. Title + manifest **7G TECH**. User-visible Hub leak **0**.
`main.dart.js` contains the prod label.

## Final Production

| Surface | Version |
|---------|---------|
| Plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7h-functional1` |
| PWA | `1.0.0+102 / figma-phase7h-functional1-prod` (7G TECH) |
| Gateway | `gateway-2.7-prod16` / `mcp-atlassian-prod` (not restarted) |

Owner APK: `/root/jira-dev/releases/corbitchat-mobile-7gtech-1.0.0+102-figma-phase7h-functional1-prod.apk`  
sha256 `98237f9b1025e010c1585bcc3b87c3f9d01d3f32ee6f20062670d77f5b045774`

Hard-refresh Production `/mobile/` (`Ctrl+Shift+R`) for on-device UI
(About, follow-up, approval filters, multi-select, short-press reaction,
file persist/open, offline, auto-download).
