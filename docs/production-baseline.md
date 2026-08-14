# Production baseline — figmachat5a-shared-open-hotfix

Updated: 2026-08-14. Phase 5 and the shared-gallery open-message hotfix
are **accepted**. This is the official working baseline for any future
phase. Do not deploy further Phase 5 changes. Do not start Phase 6
until the owner opens that phase. Do not change Production from this
close-out.

## Current Production plugin

| Field | Value |
|---|---|
| Version | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat5a-shared-open-hotfix` |
| Plugin key | `com.corbitlogic.corbitchat.jira.dc` (never change) |
| Jar sha256 | `aebc17088461e9792e5f87cf9b5cb94493dc08a68aa25336d8ce52e53843b373` |
| Release jar (build host) | `/root/jira-dev/releases/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat5a-shared-open-hotfix.jar` |
| Installed on Production | `/var/jira/plugins/installed-plugins/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat5a-shared-open-hotfix.jar` (inside `jira-srv`) |
| Plugin freeze commit | `eaface7875fdc90af903501b6741372033287709` |
| Production PWA | `1.0.0+88 / figma-chat-shared-open-hotfix` |
| PWA sha256 | `e2beaf936471ba6cac652243c1740caad2ea5f739698ea64a1b4a172036951db` |
| Mobile freeze commit | `38d2991b209634bfd5e2c72344998fa2c2c6dcbb` |
| Gateway | **unchanged** (`gateway-2.7-prod15`). MCP search remains DEV-only. |

## Rollback targets

| Surface | Roll back to |
|---|---|
| Plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat5-search-mentions` (sha256 `f6618ae08478f0218ada56afc3bd38d5497c80b7b4270e9277a8fa736632f1b6`) |
| PWA | `1.0.0+87 / figma-chat-design-reconcile1` (sha256 `b54e3dca3edb88d24a1f8bb4f6601657addafcc504490f188b40d0c3dddda1d3`) |

Backups and scripts: `/root/jira-backups/*-20260814-125535*` and
`/root/jira-backups/rollback-figmachat5a-20260814-125535/` on `corbit-prod`.

## Root cause

Shared gallery “Open message” / `باز کردن پیام` used a local
`querySelector` + `scrollIntoView` on `#jim-message-list`. That only
worked when the source message was already in the loaded page. On
Production (longer threads) the click closed the modal and did nothing:
no `openConversationAtMessage`, no `/messages/around/{messageId}`, no
highlight, no snackbar. The shared API DTO already had `conversationId`
and `messageId`. This was not a cache-only issue.

The hotfix routes Files / Media / Links through the same Phase 5 search
jump: `openConversationAtMessage(conversationId, messageId)` on web, and
`ConversationScreen` + `loadAroundAsInitial` / `messagesAround` on PWA.

## Branding

| Environment | PWA brand | Build |
|---|---|---|
| Production (`jira.7gtech.net/mobile/`) | **7G TECH** | `+88 / figma-chat-shared-open-hotfix` |
| DEV (`jira.corbitlogic.com/mobile/`) | **Hub** | `+88 / figma-chat-shared-open-hotfix` |

DEV Hub is the DEV brand, not a Production leak. Production index/manifest
title is `7G TECH`. There is no user-visible `Corbit Hub` string on
Production `/mobile/`. Do not replace DEV Hub with 7G TECH unless the
owner asks for customer-brand parity on the DEV host.

## Source of truth

| Field | Value |
|---|---|
| Source repo | `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane` |
| Branch | `feature/mcp-control-plane` |
| Freeze commit (source) | `68d3b33bcb62dffcdb2580db0f895d2c7b6d5bba` |
| Tag commit | `d8199a97a417906db691df3275d1079fa4cce57a` (contains freeze `68d3b33`) |
| Release tag | `plugin-figmachat4e-regressionfix-prod` (local only) |
| Remote tag | **Pending** — this host has no GitHub push credentials and sometimes cannot resolve `github.com`. Do not store a PAT in `~/.git-credentials`. |

When the owner provides temporary GitHub authentication, push **only** the existing local tag (do not retag, do not force-push):

```bash
cd /root/jira-dev/jira-issue-chat-panel-mcp-control-plane
git push origin plugin-figmachat4e-regressionfix-prod
```

**Never build future plugin work from** `/root/jira-dev/jira-issue-chat-panel`
or any `reportreplies-workdays1` / `feature/reply-composer-attachments` lineage.
That tree is stale and caused the figmachat4 filter/jump regression.

## Production backups (host `vm-186356` / `corbit-prod`)

| Kind | Path |
|---|---|
| Previous live jar (`figmachat5-search-mentions`) | `/root/jira-backups/corbitchat-jira-dc-figmachat5-search-mentions-20260814-125535.jar` |
| Previous jar sha256 | `f6618ae08478f0218ada56afc3bd38d5497c80b7b4270e9277a8fa736632f1b6` |
| Previous PWA (+87) | `/root/jira-backups/prod-mobile-87-before-figmachat5a-20260814-125535.tgz` |
| Plugin listing | `/root/jira-backups/plugin-listing-20260814-125535.txt` |
| DB dump | `/root/jira-backups/jira-db-20260814-125535.sql` |
| Rollback scripts | `/root/jira-backups/rollback-figmachat5a-20260814-125535/ROLLBACK-plugin.sh` and `ROLLBACK-pwa.sh` |

Rollback restores plugin `figmachat5-search-mentions` and/or PWA
`+87 / figma-chat-design-reconcile1`, then restarts `jira-srv` if the
plugin is rolled back.

## Restored P0 behaviors (must not regress)

1. **Assistant report filters** — chips and `EVENT_CARD_CONFIG` for
   `REPORT_SUBMITTED`, `REPORT_SUBMITTED_TO_ME`, `REPORT_REPLY`,
   `REPORT_MENTION`. Persian labels: `گزارش‌ها`, `منشن گزارش`,
   `گزارش‌های دریافتی`. Existing non-report filters stay.
2. **Chat search jump-to-message** — search result click calls
   `openConversationAtMessage(conversationId, messageId)`, loads older
   pages with a bounded limit if needed, scrolls, and applies
   `jim-message-highlight`.
3. **Shared gallery Open message** — Files / Media / Links `باز کردن پیام`
   must call the same `openConversationAtMessage` path (web) or
   `loadAroundAsInitial` / `messagesAround` (PWA). Do not use
   querySelector-only scroll.

Also preserved on this baseline: compact sidebar, shared galleries,
multi-forward, Saved Messages, Copy/Share/Forward/Delete/Save, MCP admin,
report/calendar admin, Workspace Agent.

## Before any future phase

1. Build plugin only from `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane`.
2. Do mobile/PWA work only from `/root/jira-dev/corbitchat-mobile`.
3. Never use `/root/jira-dev/jira-issue-chat-panel` (stale).
4. Keep Assistant report filters (`گزارش‌ها`, `منشن گزارش`, `گزارش‌های دریافتی`).
5. Keep search jump via `openConversationAtMessage`.
6. Keep shared-gallery Open message on `openConversationAtMessage`.
7. Keep Production PWA branding as **7G TECH**.
8. Do not ship Gateway MCP search to Production unless the owner
   decides that separately.

## Pending (non-blocking)

- Owner hard-refresh and confirm the final UI click/highlight.
- Push plugin/mobile commits (and tags) to GitHub when credentials
  and network are available. Do not store a PAT in `~/.git-credentials`.
- Decide separately whether to ship Gateway MCP search to Production.

## Build

```bash
cd /root/jira-dev/jira-issue-chat-panel-mcp-control-plane
mvn -s /root/.m2/settings.xml \
    -Dmaven.repo.local=/root/maven-repos/atlassian-jira-offline \
    -o clean package
```

Before packaging a future release, run the mandatory pre-package
checklist in `docs/deployment-production.md` §2.1. Do not package
if any item fails.
