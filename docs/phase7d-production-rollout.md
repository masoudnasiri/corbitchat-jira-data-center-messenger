# Phase 7D — Production PWA rollout (PWA only)

Deployed: 2026-08-14 after owner accepted DEV Hub `+96`.
Plugin was **not** changed. Jira was **not** restarted. Gateway was
**not** touched. Phase 7E was **not** started. Hub was **not** deployed.

## Preflight

| Check | Result |
|-------|--------|
| Production PWA before | `1.0.0+95 / figma-phase7c-ui-pass1-prod` |
| Production plugin | `figmachat7a-correct1` (exactly one jar) |
| Gateway | `mcp-atlassian-prod` Up 13 days — left running |
| Artifact | `/root/jira-dev/releases/corbitchat-mobile-pwa-7gtech-1.0.0+96-figma-phase7d-main-surfaces1-prod.tgz` |
| sha256 | `01e55b9112a890df2c27982f14c092ae3b4c1db9dbe38c63f0e9916ddb9fb4b6` |
| Brand | title/manifest **7G TECH**, Hub leak 0, label in `main.dart.js` |
| Mobile source | `e64ee0389fa11cb0551ae058abd354fc68641c77` |

## Backup / rollback (`corbit-prod`)

| Item | Path |
|------|------|
| Live +95 PWA | `/root/jira-backups/prod-mobile-95-before-phase7d-20260814-213435.tgz` |
| Rollback script | `/root/jira-backups/ROLLBACK-pwa-phase7d-20260814-213435.sh` |

Plugin backup was not taken because the plugin was not changed.

## Deploy

Stage → verify 96 / 7G TECH / no Hub → swap on `nginx-jira`.
No nginx restart required. No `jira-srv` restart. No Gateway restart.

## Final Production

| Surface | Version |
|---------|---------|
| Plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7a-correct1` |
| PWA | `1.0.0+96 / figma-phase7d-main-surfaces1-prod` (7G TECH) |
| Gateway | `gateway-2.7-prod16` / `mcp-atlassian-prod` (not restarted) |

Owner APK: `/root/jira-dev/releases/corbitchat-mobile-7gtech-1.0.0+96-figma-phase7d-main-surfaces1-prod.apk`  
sha256 `3cd02f1a65ecfe2a68e126d4b0b960a9f1a28bd063bbe434dff2dae9bfc6b835`

Hard-refresh Production `/mobile/` for on-device UI.
