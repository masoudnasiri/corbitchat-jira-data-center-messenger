# Phase 7F hotfix — Production PWA rollout (assignee align +99)

Deployed: 2026-08-15 after owner accepted DEV Hub
`1.0.0+99 / figma-task-assignee-align1`.
Plugin was **not** changed. Jira was **not** restarted. Gateway was
**not** touched. Phase 7G was **not** started. Hub was **not** deployed.

## Preflight

| Check | Result |
|-------|--------|
| Production PWA before | `1.0.0+98 / figma-phase7f-backend1-prod` |
| Production plugin | `figmachat7f-backend1` (exactly one jar) |
| Gateway | `mcp-atlassian-prod` started `2026-07-31T23:35:19Z` — left running |
| Artifact | `/root/jira-dev/releases/corbitchat-mobile-pwa-7gtech-1.0.0+99-figma-task-assignee-align1-prod.tgz` |
| sha256 | `d163f511a3e1e2baccc99fe77189961c806882ac9aff9f9ecc009505d63be326` (verified on Production host) |
| Brand | title/manifest **7G TECH**, Hub leak 0, label `figma-task-assignee-align1-prod` |
| Mobile source | `9ca87ec02c16bbdf05f6cc674c6a9012e5ad60bf` |

## Backup / rollback (`corbit-prod`, TS `20260814-232749`)

| Item | Path |
|------|------|
| Live +98 PWA | `/root/jira-backups/prod-mobile-98-before-assignee-align-20260814-232749.tgz` |
| Rollback script | `/root/jira-backups/ROLLBACK-pwa-assignee-align-20260814-232749.sh` |

Plugin backup was not taken because the plugin was not changed.

## Deploy

7G TECH tarball only. Stage → verify 99 / 7G TECH / no Hub → swap on
`nginx-jira`. No nginx restart. No `jira-srv` restart. No Gateway restart.

Live `https://jira.7gtech.net/mobile/version.json`:

`build_number` 99, `build_label` `figma-task-assignee-align1-prod`, `brand` `sevengtech`

`/mobile/` **200**. Title + manifest **7G TECH**. Hub leak **0**.
`main.dart.js` contains the prod label.

## Final Production

| Surface | Version |
|---------|---------|
| Plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7f-backend1` |
| PWA | `1.0.0+99 / figma-task-assignee-align1-prod` (7G TECH) |
| Gateway | `gateway-2.7-prod16` / `mcp-atlassian-prod` (not restarted) |

Owner APK: `/root/jira-dev/releases/corbitchat-mobile-7gtech-1.0.0+99-figma-task-assignee-align1-prod.apk`  
sha256 `eb97626015ed3f099523f73f63eb9732e862c46c92297182a614633dd11113a5`

Hard-refresh Production `/mobile/` (`Ctrl+Shift+R`) for on-device UI.
