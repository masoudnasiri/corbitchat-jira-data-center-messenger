# Phase 7A corrective slice — Production rollout package

Prepared: 2026-08-14  
**Do not deploy Production until the owner explicitly approves.**

This package is the Production-branded (7G TECH) counterpart of the
DEV-only corrective slice (`figmachat7a-correct1` / Hub `+90`).
Gateway is out of scope. Report / calendar / Workspace Agent are
unchanged. Do not build from `/root/jira-dev/jira-issue-chat-panel`.

## Why the owner still saw the old mobile UX

The corrective slice was installed on **DEV only**. Production is still:

| Surface | Live now |
|---|---|
| Production plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat5a-shared-open-hotfix` |
| Production PWA | `1.0.0+88 / figma-chat-shared-open-hotfix` (7G TECH) |
| Gateway | `gateway-2.7-prod16` / `mcp-atlassian` (unchanged) |

There was **no** 7G TECH APK for `+88`, `+89`, or `+90`. The newest
customer APK in releases before this package is `+87`. Any phone that
was not sideloading the DEV Hub `+90` APK is still on the old client.

## Current state (confirmed, not deployed)

| Surface | Version / label | Brand |
|---|---|---|
| DEV plugin | `figmachat7a-correct1` | n/a |
| DEV PWA | `1.0.0+90 / figma-chat-correct1` | Hub |
| DEV APK (already built) | `1.0.0+90 / figma-chat-correct1` | Hub |
| Production PWA | `1.0.0+88` | 7G TECH |
| Production plugin | `figmachat5a-shared-open-hotfix` | n/a |

Production does **not** include the Phase 7A corrective mobile fixes.

## Fresh Production mobile package

Clean Flutter rebuild (`flutter clean` + deleted `build/`) from
`/root/jira-dev/corbitchat-mobile`.

| Field | Value |
|---|---|
| versionName | `1.0.0` |
| versionCode / build | `91` |
| Label | `figma-chat-correct1-prod` |
| Brand | 7G TECH (`--flavor sevengtech` + `--dart-define=BRAND=sevengtech`) |
| Default server | `https://jira.7gtech.net` |

Verified in this package:

- PWA `version.json` build `91`
- PWA `main.dart.js` contains `figma-chat-correct1-prod`
- PWA title + manifest name/short_name = `7G TECH`
- No Hub / Corbit Hub leak in index or manifest
- APK `AndroidManifest` `versionCode=91`, `versionName=1.0.0`
- APK `string/app_name` = `7G TECH`
- APK `libapp.so` contains `figma-chat-correct1-prod`, `7G TECH`, `jira.7gtech.net`
- APK `libapp.so` does not contain `Corbit Hub`

## Artifacts (do not deploy yet)

| Piece | Path | sha256 |
|---|---|---|
| Plugin | `/root/jira-dev/releases/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7a-correct1.jar` | `ccb048f61ef143eb09f06415595678eb58d13d7a547a0d33f2d6eca0ffe04df4` |
| 7G TECH PWA +91 | `/root/jira-dev/releases/corbitchat-mobile-pwa-7gtech-1.0.0+91-figma-chat-correct1-prod.tgz` | `caf47e61d0134d6ee73bcfe9d3365b77b613eda250bececea503be02e662ef69` |
| 7G TECH APK +91 | `/root/jira-dev/releases/corbitchat-mobile-7gtech-1.0.0+91-figma-chat-correct1-prod.apk` | `a8ffbde6f1cd0e399832274547477a9641abed12143b240ef1b6f78099ba49bf` |

### Plugin jar checks

- Version in `atlassian-plugin.xml`: `figmachat7a-correct1`
- Plugin key unchanged: `com.corbitlogic.corbitchat.jira.dc`
- `JimReactionServiceImpl` has `isUsableReactionEmoji`; **no** `ALLOWED_EMOJI`
- Still present: Assistant filters (`گزارش‌ها` / `منشن گزارش` / `گزارش‌های دریافتی`), `openConversationAtMessage`, `/messages/around/`, `باز کردن پیام`, Saved Messages, `forwardSelectedMessages`
- No gateway deploy. Existing MCP admin resources in the plugin are unchanged product surface, not a gateway rollout.

Full-emoji reactions on Production **require this plugin**. Other mobile UX fixes are client-only and work against the current Production plugin once the +91 client is installed.

## Owner APK test (before or after Production PWA/plugin approval)

1. Uninstall the old Android app completely (`com.corbitlogic.corbitchat_mobile`).
2. If testing PWA instead: clear site data for `https://jira.7gtech.net/mobile/` (or wait for an approved PWA swap + hard refresh).
3. Install:
   `/root/jira-dev/releases/corbitchat-mobile-7gtech-1.0.0+91-figma-chat-correct1-prod.apk`
4. Open **More → About** and confirm:
   - version `1.0.0 (91)`
   - build label `figma-chat-correct1-prod`
   - app name **7G TECH**
   - server `https://jira.7gtech.net` (or the saved Production URL)
5. If About is not build **91** / `figma-chat-correct1-prod`, **stop**. The old app is still installed.

Sideloading the APK against Production Jira can validate header/media/tabs/long-press/open-message **now**. Full emoji persist will fail until the matching plugin is approved and installed on Production.

## Backup plan (on `corbit-prod` / `193.162.129.56`, only after approval)

Follow `docs/deployment-production.md` §4:

1. Confirm live Production is still plugin `figmachat5a-shared-open-hotfix` and PWA `version.json` build `88`.
2. Timestamped mysqldump → `/root/jira-backups/jira-db-$TS.sql`
3. Copy `/var/jira/plugins/installed-plugins/` → `/root/jira-backups/installed-plugins-$TS`
4. Tar current `/usr/share/nginx/mobile` → `/root/jira-backups/prod-mobile-88-before-figmachat7a-correct1-$TS.tgz`
5. Then Path B plugin swap (exactly one jar) + nginx PWA swap. Do **not** restart gateway.

## Rollback

| Surface | Restore |
|---|---|
| Plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat5a-shared-open-hotfix` sha256 `aebc17088461e9792e5f87cf9b5cb94493dc08a68aa25336d8ce52e53843b373` |
| PWA | `1.0.0+88 / figma-chat-shared-open-hotfix` tarball `/root/jira-dev/releases/corbitchat-mobile-pwa-7gtech-1.0.0+88-figma-chat-shared-open-hotfix.tgz` sha256 `e2beaf936471ba6cac652243c1740caad2ea5f739698ea64a1b4a172036951db` |
| Gateway | Do not touch |

Also the §4 timestamped backups taken immediately before the swap.

## Verification checklist (after a future approved deploy — not now)

### Mobile/PWA

1. `https://jira.7gtech.net/mobile/version.json` build `91`
2. About shows `figma-chat-correct1-prod`
3. 7G TECH branding; no Hub
4. Direct chat: extra top shared/folder icon gone
5. Header/avatar opens `اطلاعات گفتگو`
6. Shared media loads real items
7. Shared tab selected state: green text only, not a filled-green pill
8. Long-press is floating/anchored (not the old full sheet)
9. Quick reactions still work
10. Full emoji picker applies a non-preset emoji (needs plugin)
11. `باز کردن پیام` from media → exact source message
12. `باز کردن پیام` from files → exact source message
13. `باز کردن پیام` from links → exact source message
14. Search jump still works
15. `@` badge jump still works if a badge is present
16. No Hub leak

### Web

1. Right-click / message menu still works
2. Assistant report filters remain
3. Shared-gallery Open message still uses `openConversationAtMessage`
4. Search jump still works
5. Multi-forward and Saved Messages still work
