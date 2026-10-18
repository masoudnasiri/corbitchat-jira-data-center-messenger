# Phase 7F — Production rollout (plugin + PWA)

Deployed: 2026-08-14 after owner accepted DEV
`figmachat7f-backend1` / Hub `+98`.
Gateway was **not** touched. Workspace Agent and report/calendar
logic were **not** changed. Hub was **not** deployed.

## Preflight

| Check | Result |
|-------|--------|
| Production plugin before | `figmachat7a-correct1` (exactly one jar) sha256 `ccb048f61ef143eb09f06415595678eb58d13d7a547a0d33f2d6eca0ffe04df4` |
| Production PWA before | `1.0.0+96 / figma-phase7d-main-surfaces1-prod` |
| Gateway | `mcp-atlassian-prod` started `2026-07-31T23:35:19Z` — left running |
| Target plugin | `/root/jira-dev/releases/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7f-backend1.jar` |
| Plugin sha256 | `fa69a697e95bdb6e2642a4fbec14cd52ea7ef421b5861afdb2f574aef6f10b46` (verified on Production host) |
| Target PWA | `/root/jira-dev/releases/corbitchat-mobile-pwa-7gtech-1.0.0+98-figma-phase7f-backend1-prod.tgz` |
| PWA sha256 | `2a9567357d77f90dc2ac6828988a34b377fc71cc5909a295dd8a24f29968d847` (verified on Production host) |
| Brand | title/manifest **7G TECH**, Hub leak 0, label `figma-phase7f-backend1-prod` |
| Plugin source | `f380f15f369f9afb1c2f136011d078280bf048cd` |
| Mobile source | `98fd45065974ccd7eed377b32849e119ff020b9d` |

## Backups (`corbit-prod`, TS `20260814-222910`)

| Item | Path |
|------|------|
| Plugin jar (7a) | `/root/jira-backups/corbitchat-jira-dc-figmachat7a-correct1-20260814-222910.jar` |
| Installed-plugins tree | `/root/jira-backups/installed-plugins-20260814-222910/` |
| Plugin listing | `/root/jira-backups/plugin-listing-20260814-222910.txt` |
| Jira DB dump | `/root/jira-backups/jira-db-20260814-222910.sql` (116 MB; mysqldump PROCESS warning only) |
| Live PWA +96 | `/root/jira-backups/prod-mobile-96-before-phase7f-20260814-222910.tgz` (sha256 `01e55b9112a890df2c27982f14c092ae3b4c1db9dbe38c63f0e9916ddb9fb4b6`) |

## Rollback scripts (`corbit-prod`)

`/root/jira-backups/rollback-figmachat7f-20260814-222910/ROLLBACK-plugin.sh` → 7a  
`/root/jira-backups/rollback-figmachat7f-20260814-222910/ROLLBACK-pwa.sh` → +96  

Do **not** run any gateway rollback.

## Plugin deploy

Path B: exactly one jar, stale transformed `corbitchat-jira-dc-*.jar`
moved aside, webresource cache cleared, `docker restart jira-srv` only.

- Installed sha256 `fa69a697e95bdb6e2642a4fbec14cd52ea7ef421b5861afdb2f574aef6f10b46`
- Localhost health 503 → **401** (~6 min). Chat servlet **302**.
- Gateway `StartedAt` unchanged.

Authenticated basic-auth (even localhost) returned
`403 AUTHENTICATION_DENIED` on the first try. **Stopped.** That is the
known auth-smoke limitation, not a plugin outage. Owner browser/session
test is the acceptance signal for live board totals / favorites /
activity / follow-up.

Installed jar contains: `JimMobileBoardFavorites`, `totalIssueCount`,
`recentActivity`, `followUpLevel`, `jim.board.favorites`, Assistant
filters, `باز کردن پیام`, `openConversationAtMessage` (4),
`forwardSelectedMessages`.

## PWA deploy

7G TECH tarball only. Stage → verify 98 / 7G TECH / no Hub → swap on
`nginx-jira`. No nginx restart. No Gateway restart.

Live `https://jira.7gtech.net/mobile/version.json`:

`build_number` 98, `build_label` `figma-phase7f-backend1-prod`, `brand` `sevengtech`

`/mobile/` **200**. Title + manifest **7G TECH**. Hub leak **0**.
`main.dart.js` contains the prod label.

## Final Production

| Surface | Version |
|---------|---------|
| Plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7f-backend1` |
| PWA | `1.0.0+98 / figma-phase7f-backend1-prod` (7G TECH) |
| Gateway | `gateway-2.7-prod16` / `mcp-atlassian-prod` (not restarted) |

Owner APK: `/root/jira-dev/releases/corbitchat-mobile-7gtech-1.0.0+98-figma-phase7f-backend1-prod.apk`  
sha256 `25f64e1e1a85e65a90ca2df3510986159ab58ab6af0452cb0fbbb6ae37287f85`

Hard-refresh Production `/mobile/` (`Ctrl+Shift+R`) for on-device UI.
