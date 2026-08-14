# Production baseline freeze — figmachat4e-regressionfix

Frozen: 2026-08-14 after the accepted P0 regression restore.

This is the official Production plugin baseline. Do not start new
feature work from any other tree.

## Current Production plugin

| Field | Value |
|---|---|
| Version | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat4e-regressionfix` |
| Plugin key | `com.corbitlogic.corbitchat.jira.dc` (never change) |
| Jar sha256 | `8fade54586fe1679cc2e511b24ee64ec619a4d83bc191c99feb0783530492dc8` |
| Release jar (build host) | `/root/jira-dev/releases/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat4e-regressionfix.jar` |
| Installed on Production | `/var/jira/plugins/installed-plugins/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat4e-regressionfix.jar` (inside `jira-srv`) |

## Source of truth

| Field | Value |
|---|---|
| Source repo | `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane` |
| Branch | `feature/mcp-control-plane` |
| Freeze commit (source) | `68d3b33bcb62dffcdb2580db0f895d2c7b6d5bba` |
| Tag commit | `9e3b102e41b9183ec312f2c9fe1018024cf07404` |
| Release tag | `plugin-figmachat4e-regressionfix-prod` |

**Never build future plugin work from** `/root/jira-dev/jira-issue-chat-panel`
or any `reportreplies-workdays1` / `feature/reply-composer-attachments` lineage.
That tree is stale and caused the figmachat4 filter/jump regression.

## Production backups (host `vm-186356` / `corbit-prod`)

| Kind | Path |
|---|---|
| Previous live jar (`figmachat4d-forward-hotfix`) | `/root/jira-backups/installed-plugins-20260814-074500/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat4d-forward-hotfix.jar` |
| Previous jar sha256 | `89813048109f0bdc9b2071d9cf6883fe186dbd13477ca4938eeaf3eb4d0cf710` |
| Plugin listing | `/root/jira-backups/plugin-listing-20260814-074500.txt` |
| DB dump | `/root/jira-backups/jira-db-20260814-074500.sql` |
| Rollback script | `/root/jira-backups/rollback-figmachat4e-20260814-074500/ROLLBACK-plugin.sh` |

Rollback restores `figmachat4d-forward-hotfix` and restarts `jira-srv`.

## Restored P0 behaviors (must not regress)

1. **Assistant report filters** — chips and `EVENT_CARD_CONFIG` for
   `REPORT_SUBMITTED`, `REPORT_SUBMITTED_TO_ME`, `REPORT_REPLY`,
   `REPORT_MENTION`. Persian labels: `گزارش‌ها`, `منشن گزارش`,
   `گزارش‌های دریافتی`. Existing non-report filters stay.
2. **Chat search jump-to-message** — search result click calls
   `openConversationAtMessage(conversationId, messageId)`, loads older
   pages with a bounded limit if needed, scrolls, and applies
   `jim-message-highlight`.

Also preserved on this baseline: compact sidebar, shared galleries,
multi-forward, Saved Messages, Copy/Share/Forward/Delete/Save, MCP admin,
report/calendar admin, Workspace Agent.

## Build

```bash
cd /root/jira-dev/jira-issue-chat-panel-mcp-control-plane
mvn -s /root/.m2/settings.xml \
    -Dmaven.repo.local=/root/maven-repos/atlassian-jira-offline \
    -o clean package
```

Before packaging a future release, confirm in source:

- `ASSISTANT_FILTERS` still contains `reports`, `reportMentions`, `reportsReceived`
- `openConversationAtMessage` still exists and is the search-result click target
- `pom.xml` version is a **new** unique suffix (never reuse `figmachat4c` / `4d` / `4e`)
