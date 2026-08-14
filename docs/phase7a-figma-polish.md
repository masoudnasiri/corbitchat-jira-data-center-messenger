# Phase 7A — Low-risk Figma polish (DEV only)

Opened: 2026-08-14. Owner opened Phase 7 as **7A**: visual polish
only. Production stays on `figmachat5a-shared-open-hotfix` / `+88` /
`gateway-2.7-prod16`.

Design source (do not reinvent):

- `docs/phase6-figma-parity-delta.md`
- `/root/jira-dev/jira-issue-chat-panel/figma/FIGMA-INVENTORY.md`
- `/root/jira-dev/jira-issue-chat-panel/figma/DESIGN-SYSTEM.md`
- `/root/jira-dev/jira-issue-chat-panel/figma/IMPLEMENTATION-MATRIX.md`

## Scope shipped on DEV

| Item | Platform | Notes |
|---|---|---|
| Chat profile header card + pill Files/Media/Links tabs | Mobile/PWA | Honest count badges only when the list is complete |
| Shared file rows (type glyph, Jalali stamp, size) | Both | No fake filenames or sizes |
| Shared media grid (2px gutters, 6px radius) | Both | `باز کردن پیام` still jumps via existing around-message path |
| Shared link rows (URL in workflow blue, no OG card) | Both | No invented preview title/description |
| Group info card + camera badge + pill chips + role pills | Mobile/PWA | Existing avatar/rename APIs only |
| Message action sheet / menu chrome | Both | Same actions; delete/hide is danger-colored |
| Empty / loading / error illustrated states | Both | Shared gallery + generic state views |

## Intentionally deferred

From `docs/phase6-figma-parity-delta.md`:

1. Chat-list search filter chrome (`33:3687`) — Phase 5 search stays message search
2. Reaction tray pixel overlay (`83:9983`) — existing reaction API kept; no new tray UX
3. Profile selection mode + bulk toolbar — may need backend
4. Link OG preview cards — no preview API
5. Voice bubble pixel match
6. Dashboard / Kanban / Tasks / More quiet-hours fidelity
7. Fake counters or profile job-title fields

## Versions (DEV)

| Surface | Version | Artifact | sha256 |
|---|---|---|---|
| Plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7a-polish1` | `/root/jira-dev/releases/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7a-polish1.jar` | `7f6a1119cac23a972984b1b4fa34acd129db00fc300ae547864f208a8cb33db7` |
| PWA | `1.0.0+89 / figma-chat-polish1` (Hub) | `/root/jira-dev/releases/corbitchat-mobile-pwa-hub-1.0.0+89-figma-chat-polish1.tgz` | `dcb5751afe3409076aec5e21f3c5d580e366ddc00397a19598ebaf59a320dd3d` |
| APK | `1.0.0+89 / figma-chat-polish1` (Hub) | `/root/jira-dev/releases/corbitchat-mobile-hub-1.0.0+89-figma-chat-polish1.apk` | `79cd0eb69d7351d647cecf813f7ca38c8d9e73fa8fcc5afa755e5edae66df25d` |
| Production | unchanged (`figmachat5a` / `+88` / `gateway-2.7-prod16`) | — | — |

DEV PWA backup before replace:
`/root/jira-backups/dev-mobile-88-before-figmachat7a-20260814-151115.tgz`

## Guards

- No new REST endpoint, AO migration, or gateway change
- Assistant report filters unchanged
- `openConversationAtMessage` / `messagesAround` unchanged
- Shared-gallery `باز کردن پیام` still uses conversationId + messageId
- DEV brand remains Hub; Production 7G TECH rule unchanged

## Corrective slice (DEV — 2026-08-14)

Owner-reported UX bugs after polish1. Deployed to DEV only.

| Surface | Version | Artifact | sha256 |
|---|---|---|---|
| Plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7a-correct1` | `/root/jira-dev/releases/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7a-correct1.jar` | `ccb048f61ef143eb09f06415595678eb58d13d7a547a0d33f2d6eca0ffe04df4` |
| PWA | `1.0.0+90 / figma-chat-correct1` (Hub) | `/root/jira-dev/releases/corbitchat-mobile-pwa-hub-1.0.0+90-figma-chat-correct1.tgz` | `371e90fac2876f1606898ecad58719616118944a32dfb92451bb399460642326` |
| APK | `1.0.0+90 / figma-chat-correct1` (Hub) | `/root/jira-dev/releases/corbitchat-mobile-hub-1.0.0+90-figma-chat-correct1.apk` | `70c3dd1dbcf7e914393efb7bf46bffd3b8196594c994d970292f8f97fe6fc3f3` |
| Production | unchanged (`figmachat5a` / `+88` / `gateway-2.7-prod16`) | — | — |

DEV PWA backup before this swap:
`/root/jira-backups/dev-mobile-89-before-figmachat7a-correct1-20260814-163720.tgz`

Plugin change required only for full emoji reactions: `JimReactionServiceImpl` no longer allowlists presets. Same `{emoji}` payload; AO `emoji` still max 64. Gateway untouched.

Production-branded package (not deployed):
`docs/phase7a-correct1-production-rollout-package.md`
(`1.0.0+91 / figma-chat-correct1-prod`, 7G TECH).

## Corrective 2 (DEV — 2026-08-14)

Mobile-only. Plugin unchanged (`figmachat7a-correct1`). Production unchanged.

| Surface | Version | Artifact | sha256 |
|---|---|---|---|
| DEV PWA | `1.0.0+92 / figma-chat-correct2` (Hub) | `/root/jira-dev/releases/corbitchat-mobile-pwa-hub-1.0.0+92-figma-chat-correct2.tgz` | `e0af927263614677c39cc97b7a91c3e8c3a4ddc99a1a9b7e34a5cb91b057a7ef` |
| DEV APK | `1.0.0+92 / figma-chat-correct2` (Hub) | `/root/jira-dev/releases/corbitchat-mobile-hub-1.0.0+92-figma-chat-correct2.apk` | `00bba316d12a8adba4c5f843d9760ff7773fdff4de23cc2f44efb49fdb99df16` |
| Owner APK | `1.0.0+92 / figma-chat-correct2-prod` (7G TECH) | `/root/jira-dev/releases/corbitchat-mobile-7gtech-1.0.0+92-figma-chat-correct2-prod.apk` | `be4b9d9e0f3938df4d92ff44f4124a5f8d87cf25c9d8cbc323d38f691f6f7702` |
| 7G PWA (not deployed) | `1.0.0+92 / figma-chat-correct2-prod` | `/root/jira-dev/releases/corbitchat-mobile-pwa-7gtech-1.0.0+92-figma-chat-correct2-prod.tgz` | `affc132d265815c16286ed85dd849b57a93de8b0ad8a197041023f0d3a665b59` |

## Corrective 3 — full-screen image viewer (DEV — 2026-08-14)

Mobile-only. Plugin unchanged. Production unchanged.

`ChatImageViewer` now sizes the image to the full viewport (`LayoutBuilder` +
`SizedBox` + `BoxFit.contain`), with pinch/pan and double-tap zoom. Authenticated
load path unchanged.

| Surface | Version | Artifact | sha256 |
|---|---|---|---|
| DEV PWA | `1.0.0+93 / figma-chat-imageviewer-fix` (Hub) | `/root/jira-dev/releases/corbitchat-mobile-pwa-hub-1.0.0+93-figma-chat-imageviewer-fix.tgz` | `1039a54132bf1b4d10857c5287b7350493269ebae58fa8dc79b9718b2a01842f` |
| Hub APK | `1.0.0+93 / figma-chat-imageviewer-fix` | `/root/jira-dev/releases/corbitchat-mobile-hub-1.0.0+93-figma-chat-imageviewer-fix.apk` | `abef686e95d294cc165e671fbe6493a9acda532b902261d1ce05a37358ab880f` |
| Owner APK | `1.0.0+93 / figma-chat-imageviewer-fix-prod` (7G TECH) | `/root/jira-dev/releases/corbitchat-mobile-7gtech-1.0.0+93-figma-chat-imageviewer-fix-prod.apk` | `da6c7cc3b2e258105a2ec522ea221c5250cfd840acef70bea03006d94f1131cd` |
| 7G PWA (not deployed) | `1.0.0+93 / figma-chat-imageviewer-fix-prod` | `/root/jira-dev/releases/corbitchat-mobile-pwa-7gtech-1.0.0+93-figma-chat-imageviewer-fix-prod.tgz` | `daf3170fc53dad62fe190973c208849be9379fecbacd4149a73e3ec9b04d9e76` |
