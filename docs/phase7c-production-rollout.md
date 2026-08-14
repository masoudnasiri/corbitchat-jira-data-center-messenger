# Phase 7C — Production PWA rollout (PWA only)

Deployed: 2026-08-14 after owner accepted DEV Hub `+95`.
Plugin was **not** changed. Jira was **not** restarted. Gateway was
**not** touched. Phase 7D was **not** started. Hub was **not** deployed.

## Preflight

| Check | Result |
|-------|--------|
| Production PWA before | `1.0.0+94 / figma-phase7b-design-lock1-prod` |
| Production plugin | `figmachat7a-correct1` (exactly one jar) |
| Gateway | `mcp-atlassian-prod` Up 13 days — left running |
| Artifact | `/root/jira-dev/releases/corbitchat-mobile-pwa-7gtech-1.0.0+95-figma-phase7c-ui-pass1-prod.tgz` |
| sha256 | `75f8c2854e222152b0cb30efe55e1144399539ed05158ff041597b146ed6f512` |
| Brand | title/manifest **7G TECH**, Hub leak 0, label in `main.dart.js` |

## Backup / rollback (`corbit-prod`)

| Item | Path |
|------|------|
| Live +94 PWA | `/root/jira-backups/prod-mobile-94-before-phase7c-20260814-205123.tgz` |
| Rollback script | `/root/jira-backups/ROLLBACK-pwa-phase7c-20260814-205123.sh` |

Plugin backup was not taken because the plugin was not changed.

## Deploy

Stage → verify 95 / 7G TECH / no Hub → swap on `nginx-jira`.
No nginx restart required. No `jira-srv` restart. No Gateway restart.

## Final Production

| Surface | Version |
|---------|---------|
| Plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7a-correct1` |
| PWA | `1.0.0+95 / figma-phase7c-ui-pass1-prod` (7G TECH) |
| Gateway | `gateway-2.7-prod16` / `mcp-atlassian-prod` (not restarted) |

Owner APK: `/root/jira-dev/releases/corbitchat-mobile-7gtech-1.0.0+95-figma-phase7c-ui-pass1-prod.apk`  
sha256 `a5e5d88ac12da9321fc0a1f6e6960297e9bcad64747105488e51b45be35b72f0`

Hard-refresh Production `/mobile/` for on-device UI.