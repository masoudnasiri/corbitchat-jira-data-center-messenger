# Phase 7B — Production rollout

Deployed: 2026-08-14 after owner accepted DEV `+94`.
Gateway was **not** touched. Phase 7C was **not** started.

## 1. Preflight (before change)

| Check | Result |
|-------|--------|
| Production plugin | `figmachat5a-shared-open-hotfix` sha256 `aebc17088461e9792e5f87cf9b5cb94493dc08a68aa25336d8ce52e53843b373` |
| Production PWA | `build_number` **88**, title/manifest **7G TECH** |
| Target PWA | `1.0.0+94 / figma-phase7b-design-lock1-prod` sha256 `de3769cdfc79bbf8daaedef45808e0f59199508355425bb04fd673bc833a2407` |
| Target APK | `1.0.0+94 / figma-phase7b-design-lock1-prod` sha256 `1c076e7b10863623b505229bce85683015f2a6b7a92a7f38acf42b1d1e8a99e0` |
| Plugin required? | **Yes.** HEAD 5a source still has `ALLOWED_EMOJI`. Full picker persist needs `figmachat7a-correct1` (`isUsableReactionEmoji`). No mobile API / AO schema change. |
| Gateway | `mcp-atlassian-prod` Up 13 days — left running |

## 2. Source commits (before deploy)

| Repo | Commit |
|------|--------|
| `/root/jira-dev/corbitchat-mobile` | `e0ea39f8d52d55b6137e27a00630c1cd47a8d4c8` |
| `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane` | `1dfd293925562182f6a5dc8bbe78ea9257747d6e` |

Not committed: APK/PWA/jars, backups, secrets, `docs/redesign/`, `dev-tests/`.
`web/` brand-swap dirt was restored to HEAD (7G TECH) before the mobile commit.

## 3. Backups (`corbit-prod`, TS `20260814-194654`)

| Item | Path |
|------|------|
| Plugin jar (5a) | `/root/jira-backups/corbitchat-jira-dc-figmachat5a-shared-open-hotfix-20260814-194654.jar` |
| Installed-plugins tree | `/root/jira-backups/installed-plugins-20260814-194654/` |
| Plugin listing | `/root/jira-backups/plugin-listing-20260814-194654.txt` |
| Jira DB dump | `/root/jira-backups/jira-db-20260814-194654.sql` (117 MB; mysqldump PROCESS warning only) |
| Live PWA +88 | `/root/jira-backups/prod-mobile-88-before-figmachat7b-20260814-194654.tgz` |
| +88 rollback tarball | `/root/jira-backups/prod-mobile-88-figma-chat-shared-open-hotfix.tgz` |

## 4. Plugin deploy

**Decision: deploy `figmachat7a-correct1`.** Required for arbitrary emoji persist. Not required for other +94 UI.

- Exactly one jar after swap
- sha256 `ccb048f61ef143eb09f06415595678eb58d13d7a547a0d33f2d6eca0ffe04df4`
- `docker restart jira-srv` only (gateway not restarted)
- Mobile health version: `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7a-correct1`

| Endpoint | Code |
|----------|------|
| `/rest/corbit-mobile/1.0/health` | 200 |
| `/rest/corbit-mobile/1.0/ao-health` | 200 |
| `/rest/jim/1.0/health` | 200 |
| `/rest/jim/1.0/ao-health` | 200 |
| `/plugins/servlet/jim/chat` | 200 |
| `/plugins/servlet/jim/admin` | 200 |
| `/plugins/servlet/jim/mcp-admin` | 200 |
| `/rest/jim/1.0/workspace-agent/status` | 200 (`enabled` true) |

Live WRM batch is `figmachat7a-correct1`. Assistant filters `گزارش‌ها` / `منشن گزارش` / `گزارش‌های دریافتی` present. Live minified JS has `messages/around`, `باز کردن پیام`, Saved Messages. Jar source has `function openConversationAtMessage` (4 refs).

## 5. PWA deploy

7G TECH tarball only (not Hub). Stage → verify → swap on `nginx-jira`.

Live `https://jira.7gtech.net/mobile/version.json`:

`build_number` 94, `build_label` `figma-phase7b-design-lock1-prod`, `brand` `sevengtech`

`/mobile/` **200**. Title + manifest **7G TECH**. Hub / Corbit Hub leak **0**. `main.dart.js` contains the prod label.

## 6. Final Production versions

| Surface | Version |
|---------|---------|
| Plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7a-correct1` |
| PWA | `1.0.0+94 / figma-phase7b-design-lock1-prod` (7G TECH) |
| Gateway | `gateway-2.7-prod16` / `mcp-atlassian-prod` (created 2026-07-31, not restarted) |

## 7. Verification

Automated: endpoints above, WRM filters, open-message strings, PWA brand/label, exactly one jar, gateway uptime unchanged.

Owner on-device (hard-refresh `/mobile/`): About `1.0.0 (94)` / `figma-phase7b-design-lock1-prod`; overlay; tray; full picker persist; image viewer; پیوست‌ها/مدیا/لینک‌ها; `باز کردن پیام`; search/@ jump; RTL/Jalali. Web right-click / multi-forward / Saved Messages unchanged in source+WRM.

## 8. Owner APK

`/root/jira-dev/releases/corbitchat-mobile-7gtech-1.0.0+94-figma-phase7b-design-lock1-prod.apk`  
sha256 `1c076e7b10863623b505229bce85683015f2a6b7a92a7f38acf42b1d1e8a99e0`

## 9. Rollback (`corbit-prod`)

`/root/jira-backups/rollback-figmachat7b-20260814-194654/ROLLBACK-plugin.sh` → 5a  
`/root/jira-backups/rollback-figmachat7b-20260814-194654/ROLLBACK-pwa.sh` → +88  

Do **not** run any gateway rollback.

## 10–11

Gateway unchanged. Phase 7C not started.
