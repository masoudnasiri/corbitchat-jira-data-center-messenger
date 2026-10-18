# Production hotfix — figmachat7i-reportmetrics2

Deployed: 2026-08-17 via Path B (plugin only). PWA, Gateway, and
Workspace Agent were not changed.

## What changed

`fixed_minimum` missed count no longer uses `minimumRequired − valid`
while the due/done endpoint is still open. COR-18 (min 1 by end, no
report, deadline not reached) is ON_TRACK with missed=0.

## Versions

| Surface | Version |
|---|---|
| Plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2` |
| Jar sha256 | `8102a1b931d7df0dcc7450f9514999555257c2ff4f040a68237284ffa29a880e` |
| PWA | `1.0.0+107 / figma-task-detail-person-spacing2-prod` (unchanged) |
| Gateway | `mcp-atlassian-prod` StartedAt `2026-07-31T23:35:19Z` (unchanged) |

## Backups on corbit-prod (TS `20260817-090409`)

- Plugin jar: `/root/jira-backups/corbitchat-jira-dc-figmachat7i-reportmetrics1-20260817-090409.jar`
- Listing: `/root/jira-backups/plugin-listing-20260817-090409.txt`
- Installed-plugins tree: `/root/jira-backups/installed-plugins-20260817-090409/`
- DB: `/root/jira-backups/jira-db-20260817-090409.sql`
- Rollback: `/root/jira-backups/rollback-figmachat7i-reportmetrics2-20260817-090409/ROLLBACK-plugin.sh`

## Rollback

Restore `figmachat7i-reportmetrics1` only. Do not restart Gateway.
Do not change PWA.
