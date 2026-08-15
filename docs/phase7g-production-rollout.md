# Phase 7G — Production rollout (plugin + PWA)

Deployed: 2026-08-15 after owner accepted DEV
`figmachat7g-dashboard-metrics1` / Hub `+101`.
Gateway was **not** touched. Workspace Agent and report/calendar
logic were **not** changed. Hub was **not** deployed.

## Preflight

| Check | Result |
|-------|--------|
| Production plugin before | `figmachat7f-backend1` (exactly one jar) sha256 `fa69a697e95bdb6e2642a4fbec14cd52ea7ef421b5861afdb2f574aef6f10b46` |
| Production PWA before | `1.0.0+99 / figma-task-assignee-align1-prod` |
| Gateway | `mcp-atlassian-prod` started `2026-07-31T23:35:19Z` — left running |
| Target plugin | `/root/jira-dev/releases/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7g-dashboard-metrics1.jar` |
| Plugin sha256 | `acd2929b4ec1c74d08e83df87ad0515ef5b0e02b95c368724168cd4032a941d2` (verified on Production host) |
| Target PWA | `/root/jira-dev/releases/corbitchat-mobile-pwa-7gtech-1.0.0+101-figma-phase7g-dashboard-copy-rtl1-prod.tgz` |
| PWA sha256 | `f8436e69fb7b0ee2f700512f19f845ef93e164d86c87667544240d523d4d4987` (verified on Production host) |
| Brand | title/manifest **7G TECH**, no user-visible Hub, label `figma-phase7g-dashboard-copy-rtl1-prod`, build 101 |
| Plugin source | `773c762c801c7fb8c35711a713f0466fa1223148` |
| Mobile source | `ad7268aee9f21f9957dc24521ae37c627c6a157a` |

## Backups (`corbit-prod`, TS `20260815-031631`)

| Item | Path |
|------|------|
| Plugin jar (7f) | `/root/jira-backups/corbitchat-jira-dc-figmachat7f-backend1-20260815-031631.jar` |
| Installed-plugins tree | `/root/jira-backups/installed-plugins-20260815-031631/` |
| Plugin listing | `/root/jira-backups/plugin-listing-20260815-031631.txt` |
| Jira DB dump | `/root/jira-backups/jira-db-20260815-031631.sql` (117 MB; mysqldump PROCESS warning only) |
| Live PWA +99 | `/root/jira-backups/prod-mobile-99-before-phase7g-20260815-031631.tgz` (sha256 `d163f511a3e1e2baccc99fe77189961c806882ac9aff9f9ecc009505d63be326`) |

## Rollback scripts (`corbit-prod`)

`/root/jira-backups/rollback-figmachat7g-20260815-031631/ROLLBACK-plugin.sh` → 7f  
`/root/jira-backups/rollback-figmachat7g-20260815-031631/ROLLBACK-pwa.sh` → +99  

Do **not** run any gateway rollback.

## Plugin deploy

Path B: exactly one jar, stale transformed `figmachat7f-backend1_*.jar`
moved aside, webresource cache cleared, `docker restart jira-srv` only.

- Installed sha256 `acd2929b4ec1c74d08e83df87ad0515ef5b0e02b95c368724168cd4032a941d2`
- Localhost health 503 → **401** (~7 min). Chat / admin / mcp-admin **302**.
- Gateway `StartedAt` unchanged.

Localhost authenticated `/dashboard` **200** for `m.nasiri`:

- `reportSummary.source` = `report-engine`
- `reportSummary` complete 1 / onTrack 1 / overdue 1 (scanned 7, configured 7)
- `statusCounts` done 0 / inProgress 6 / todo 1 / total 7 — **not equal**
- `openWorkCounts` active 7 / overdue 4 / today 0 / followup 5
- `/dashboard/report-status` `source: reports` done 1 / inProgress 1 / overdue 1

`/rest/corbit-mobile/1.0/health` version is
`figmachat7g-dashboard-metrics1`. Boards still return
`totalIssueCount` and `favorite`. Assistant filter strings remain in
installed `jim-messenger.js`.

`GET /rest/jim/1.0/workspace-agent/status` as `m.nasiri` is **403**
(`Jira System Administrator permission is required`). That is the
existing permission gate, not `AUTHENTICATION_DENIED`. Agent settings
were not changed.

## PWA deploy

7G TECH tarball only. Stage → verify 101 / 7G TECH / no Hub → swap on
`nginx-jira`. No nginx restart. No Gateway restart.

Live `https://jira.7gtech.net/mobile/version.json`:

`build_number` 101, `build_label` `figma-phase7g-dashboard-copy-rtl1-prod`, `brand` `sevengtech`

`/mobile/` **200**. Title + manifest **7G TECH**. User-visible Hub leak **0**.
`main.dart.js` contains the prod label and the Phase 7G report/task copy
(English keys + decoded Persian). Chevron codepoints `57694` / `57695`
are present (`ForwardChevron`).

## Final Production

| Surface | Version |
|---------|---------|
| Plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7g-dashboard-metrics1` |
| PWA | `1.0.0+101 / figma-phase7g-dashboard-copy-rtl1-prod` (7G TECH) |
| Gateway | `gateway-2.7-prod16` / `mcp-atlassian-prod` (not restarted) |

Owner APK: `/root/jira-dev/releases/corbitchat-mobile-7gtech-1.0.0+101-figma-phase7g-dashboard-copy-rtl1-prod.apk`  
sha256 `1eb87ead1eb83064cf3a4d4a6b52e580e031cd1f7e3bd2c0e87740d95952e6d4`

Hard-refresh Production `/mobile/` (`Ctrl+Shift+R`) for on-device UI
(About, reports wording, RTL chevrons, chat smoke).
