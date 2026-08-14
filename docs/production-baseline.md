# Production baseline — figmachat7a-correct1 + PWA +94

Updated: 2026-08-14. Owner accepted Phase 7B on DEV (`+94`) and
approved this Production rollout. Plugin `figmachat7a-correct1` is
required so the full emoji picker can persist (5a still allowlisted
presets). PWA is 7G TECH `1.0.0+94 / figma-phase7b-design-lock1-prod`.
Gateway remains `gateway-2.7-prod16` / `mcp-atlassian-prod` (not
restarted). Phase 7C was **not** started.

Previous Production (`figmachat5a-shared-open-hotfix` / `+88`) is the
rollback target. See `docs/phase7b-production-rollout.md`.

Owner manual QA (`docs/manual-qa-phase6-chat.md`) is **accepted as
non-blocking / owner-signed**. Hardening produced docs and DEV-safe
scripts only: no product-code change, no version bump, no Production
deploy.

## Current Production plugin

| Field | Value |
|---|---|
| Version | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7a-correct1` |
| Plugin key | `com.corbitlogic.corbitchat.jira.dc` (never change) |
| Jar sha256 | `ccb048f61ef143eb09f06415595678eb58d13d7a547a0d33f2d6eca0ffe04df4` |
| Release jar (build host) | `/root/jira-dev/releases/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7a-correct1.jar` |
| Installed on Production | `/var/jira/plugins/installed-plugins/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7a-correct1.jar` (inside `jira-srv`) |
| Plugin source commit | `1dfd293925562182f6a5dc8bbe78ea9257747d6e` |
| Production PWA | `1.0.0+94 / figma-phase7b-design-lock1-prod` (7G TECH) |
| PWA sha256 | `de3769cdfc79bbf8daaedef45808e0f59199508355425bb04fd673bc833a2407` |
| Mobile source commit | `e0ea39f8d52d55b6137e27a00630c1cd47a8d4c8` |
| Gateway | `gateway-2.7-prod16` (commit `738009d0b6700fd2e960b4f9a29f87cd37890e3b`). MCP chat search **shipped** and **closed** after owner ChatGPT Scan Tools / Refresh. |
| Gateway contract hash | `102b6bfad85b083fbfffe20f5dae7695e646bd6d89a2b97f903bcfe64fe2e340` |

## Rollback targets

| Surface | Roll back to |
|---|---|
| Plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat5a-shared-open-hotfix` (sha256 `aebc17088461e9792e5f87cf9b5cb94493dc08a68aa25336d8ce52e53843b373`) |
| PWA | `1.0.0+88 / figma-chat-shared-open-hotfix` (sha256 `e2beaf936471ba6cac652243c1740caad2ea5f739698ea64a1b4a172036951db`) |
| Gateway | **Do not roll back** — Gateway was not changed in this rollout |

Backups and scripts: `/root/jira-backups/rollback-figmachat7b-20260814-194654/` on `corbit-prod`.

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
| Production (`jira.7gtech.net/mobile/`) | **7G TECH** | `+94 / figma-phase7b-design-lock1-prod` |
| DEV (`jira.corbitlogic.com/mobile/`) | **Hub** | `+94 / figma-phase7b-design-lock1` |

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
