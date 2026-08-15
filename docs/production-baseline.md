# Production baseline — figmachat7g-dashboard-metrics1 + PWA +101

Updated: 2026-08-15. **Production status: Phase 7G deployed**
(dashboard `reportSummary` + 7G TECH PWA copy/RTL). Gateway was
**not** restarted. Workspace Agent and report/calendar logic were
**not** changed. Hub was **not** deployed. See
`docs/phase7g-production-rollout.md`.

| Surface | Version |
|---|---|
| Plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7g-dashboard-metrics1` |
| PWA | `1.0.0+101 / figma-phase7g-dashboard-copy-rtl1-prod` (7G TECH) |
| Gateway | `gateway-2.7-prod16` / `mcp-atlassian-prod` (**unchanged**, StartedAt `2026-07-31T23:35:19Z`) |

After `jira-srv` restart, localhost `/rest/jim/1.0/health` moved
503 → 401 in about seven minutes. That is normal startup, not a failure.

Rollback: plugin `figmachat7f-backend1`, PWA
`+99 / figma-task-assignee-align1-prod`.

## Auth-smoke limitation (not a Production failure)

A later public basic-auth check returned `403 AUTHENTICATION_DENIED`.
That is Jira blocking basic auth from that host/account (CAPTCHA /
failed-login lock / auth policy). It is **not** a plugin outage.

Do **not** keep retrying public basic-auth with the same credentials.
Use browser/session-based manual verification, localhost unauthenticated
401/302 behavior, or unlock/reset the test account before repeating
credential-based smokes. Owner UI test is the acceptance signal.

## Current Production plugin

| Field | Value |
|---|---|
| Version | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7g-dashboard-metrics1` |
| Plugin key | `com.corbitlogic.corbitchat.jira.dc` (never change) |
| Jar sha256 | `acd2929b4ec1c74d08e83df87ad0515ef5b0e02b95c368724168cd4032a941d2` |
| Release jar (build host) | `/root/jira-dev/releases/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7g-dashboard-metrics1.jar` |
| Installed on Production | `/var/jira/plugins/installed-plugins/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7g-dashboard-metrics1.jar` (inside `jira-srv`) |
| Plugin source commit | `773c762c801c7fb8c35711a713f0466fa1223148` |
| Production PWA | `1.0.0+101 / figma-phase7g-dashboard-copy-rtl1-prod` (7G TECH) |
| PWA sha256 | `f8436e69fb7b0ee2f700512f19f845ef93e164d86c87667544240d523d4d4987` |
| Mobile APK | `/root/jira-dev/releases/corbitchat-mobile-7gtech-1.0.0+101-figma-phase7g-dashboard-copy-rtl1-prod.apk` |
| APK sha256 | `1eb87ead1eb83064cf3a4d4a6b52e580e031cd1f7e3bd2c0e87740d95952e6d4` |
| Mobile source commit | `ad7268aee9f21f9957dc24521ae37c627c6a157a` |
| Gateway | `gateway-2.7-prod16` (commit `738009d0b6700fd2e960b4f9a29f87cd37890e3b`). MCP chat search **shipped** and **closed** after owner ChatGPT Scan Tools / Refresh. |
| Gateway contract hash | `102b6bfad85b083fbfffe20f5dae7695e646bd6d89a2b97f903bcfe64fe2e340` |

## Rollback targets

| Surface | Roll back to |
|---|---|
| Plugin | `figmachat7f-backend1` |
| PWA | `1.0.0+99 / figma-task-assignee-align1-prod` |
| Gateway | **Do not roll back** — Gateway was not changed |

On `corbit-prod` (TS `20260815-031631`):

- Plugin: `/root/jira-backups/rollback-figmachat7g-20260815-031631/ROLLBACK-plugin.sh`
- PWA: `/root/jira-backups/rollback-figmachat7g-20260815-031631/ROLLBACK-pwa.sh`

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
| Production (`jira.7gtech.net/mobile/`) | **7G TECH** | `+101 / figma-phase7g-dashboard-copy-rtl1-prod` |
| DEV (`jira.corbitlogic.com/mobile/`) | **Hub** | `+101 / figma-phase7g-dashboard-copy-rtl1` |

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
4. **Dashboard reports card** — `گزارش‌ها` reads additive
   `reportSummary` from the report engine (`complete` / `onTrack` /
   `overdue`). It must not use task `statusCounts`. Top numbers stay
   on `openWorkCounts`.
5. **RTL forward chevrons** — More / profile / settings / about /
   shared / chat-info rows use `ForwardChevron` (physical left in
   Persian, pointing left). Do not use Material `Icons.chevron_left`
   for those rows.

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
8. Gateway MCP chat search is live on Production (`corbit_chat_search`,
   `corbit_chat_search_messages`). Do not revert it unless rolling
   back the gateway to `gateway-2.7-prod15`.

## Phase 6 close (2026-08-14)

| Field | Value |
|---|---|
| Status | **Closed** by owner acceptance |
| Product code | Unchanged (plugin freeze `eaface7875fdc90af903501b6741372033287709`) |
| Versions | Unchanged (`figmachat5a-shared-open-hotfix` / `+88`) |
| Production | Unchanged — no Phase 6 deploy |
| Owner manual QA | Accepted as non-blocking / owner-signed |
| Phase 7 | **Not started.** Backlog source: `docs/phase6-figma-parity-delta.md` |
| Gateway MCP search | Not shipped **during Phase 6**. Shipped later the same day as a separate approved Gateway-only rollout (`gateway-2.7-prod16`). |

Phase 6 docs: `docs/phase6-hardening-plan.md`,
`docs/regression-checklist.md`, `docs/regression-checklist.yaml`,
`docs/manual-qa-phase6-chat.md`, `docs/phase6-figma-parity-delta.md`.

## Security follow-up (Option A — 2026-08-14)

- **DEV Jira password for `m.nasiri` was rotated** after GitHub/history
  exposure. The new value is **not** in git, docs, or scripts. Inject
  it with `JIM_PASS` from the environment or the host vault
  `/root/jira-dev/secrets/dev-jira-jim.env` (mode 600, not a git repo).
- Current HEAD is sanitized. `scripts/check-no-secrets.sh` is the
  permanent gate (`docs/deployment-production.md` §2.1).
- **History still contains the old exposed DEV password** (`501bc05`
  smokes; `AGENTS.md` on older commits including GitHub `origin/main`).
  **No history rewrite** was performed.
- GitHub push remains **blocked** until the owner chooses: keep private
  and accept residual history risk after rotation; rewrite history; or
  create a clean repo.
- Production credentials were **not** found in git. Production was
  **not** changed.
- Local DEV MySQL password from old handover docs is still active on
  `mysql-jira`. Not rotated (owner approval + downtime/config impact
  required). Remaining **local-only** risk.
- Set secrets through environment variables or secure server-side
  config only. Do not store a PAT in `~/.git-credentials`.

## Gateway MCP chat search close (2026-08-14)

Separate approved Gateway-only rollout. Closed after **owner ChatGPT
Scan Tools / Refresh**.

| Field | Value |
|---|---|
| Status | **Closed** by owner tool refresh |
| Release | `gateway-2.7-prod16` |
| Commit | `738009d0b6700fd2e960b4f9a29f87cd37890e3b` |
| Contract hash | `102b6bfad85b083fbfffe20f5dae7695e646bd6d89a2b97f903bcfe64fe2e340` |
| `corbit_chat_search` | Visible in ChatGPT; **read-only** |
| `corbit_chat_search_messages` | Visible in ChatGPT; **read-only** |
| Unintended write/destructive tools | None appeared |
| Safe read-only search test | **Passed** (owner) |
| Plugin / PWA | Unchanged (`figmachat5a-shared-open-hotfix` / `+88`) |
| Phase 7 | **Not started** at gateway close. Opened later as DEV-only Phase 7A. |

## Phase 7A DEV polish (2026-08-14)

DEV-only Figma polish. See `docs/phase7a-figma-polish.md`.

| Field | Value |
|---|---|
| Status | Corrective slice on DEV (`figmachat7a-correct1` / `+90`); Production unchanged |
| DEV plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7a-correct1` |
| DEV PWA | `1.0.0+93 / figma-chat-imageviewer-fix` (Hub) |
| Production plugin / PWA / Gateway | **Unchanged** (`figmachat5a` / `+88` / `gateway-2.7-prod16`) |

## Pending (non-blocking)

- Push plugin/mobile commits (and tags) to GitHub when credentials
  and network are available. Sanitize `AGENTS.md` and
  `docs/handover-mobile-app.md` first. Do not store a PAT in
  `~/.git-credentials`. Do not rewrite `501bc05` unless approved.
- Gateway rollback (gateway only):
  `/root/jira-backups/gateway-mcp-search-20260814-141223/ROLLBACK-gateway.sh`
  on `corbit-prod` (restores `gateway-2.7-prod15`).

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
