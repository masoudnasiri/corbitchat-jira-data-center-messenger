# Phase 7B — Implementation report (DEV only)

Opened after `docs/phase7b-design-lock-report.md`. No Production, gateway, or plugin change.

## 1. Design files read

All of `/root/jira-dev/jira-issue-chat-panel/figma` (docs + every PNG). See the lock report §1.

## 2. Design lock summary

Hub mobile file `fyCTz4B2izGsUFSsFXV13K`, 412×917, RTL. Clearest lockable chrome: overlay `83:9983` and profile tabs `8/10/12`. Frame 19 PNG is create-group, not chat-info. Empty/loading/error frames are missing. Web/plugin frames are absent. Job titles and OG link cards are API gaps — do not fake.

## 3. Selected Phase 7B scope (shipped)

Mobile/PWA UI-only:

1. Long-press overlay + reaction tray locked to DESIGN-SYSTEM tokens (256 menu, radius 14, elevation-3, dim 48%, Figma five emojis + notch, labels پاسخ / کپی / اشتراک‌گذاری / ارسال به / حذف برای من). Extra product actions kept **below a divider**. Full emoji picker kept on `+`.
2. Shared gallery chrome: one segmented track, labels **پیوست‌ها / مدیا / لینک‌ها**, honest count pills, grouped file card, `برگشت` app bar. No fake job title. No fake OG previews.

## 4. Intentionally deferred

Selection/bulk mode, job titles, OG cards, voice bubbles, frame 19 as profile, global `#01BA9E` → `#0D6E6E` theme rebase, dashboard/boards/tasks/More, web plugin polish, image-viewer restyle, Production, gateway, plugin JAR.

## 5. Files changed

Official mobile repo only (`/root/jira-dev/corbitchat-mobile`):

- `lib/theme/hub_figma_tokens.dart` (new)
- `lib/features/chat/widgets/message_action_overlay.dart`
- `lib/features/chat/conversation_screen.dart`
- `lib/features/chat/shared_gallery_screen.dart`
- `lib/core/strings.dart`
- `lib/core/app_config.dart`
- `pubspec.yaml` → `1.0.0+94`

Official plugin repo docs:

- `docs/phase7b-design-lock-report.md`
- `docs/phase7b-visual-evidence/`
- `docs/phase7b-implementation-report.md` (this file)

Plugin Java/JS/CSS: **unchanged**.

## 6. Versions

| Surface | Version | Where |
|---------|---------|-------|
| DEV Hub PWA (live) | `1.0.0+94 / figma-phase7b-design-lock1` | `http://185.83.181.194/mobile/` |
| 7G TECH PWA (artifact only) | `1.0.0+94 / figma-phase7b-design-lock1-prod` | **not deployed** |
| Hub APK | `1.0.0+94 / figma-phase7b-design-lock1` | releases/ |
| 7G TECH APK | `1.0.0+94 / figma-phase7b-design-lock1-prod` | releases/ |
| DEV plugin | `figmachat7a-correct1` | unchanged |
| Production plugin | `figmachat5a-shared-open-hotfix` | unchanged |
| Production PWA | `1.0.0+88` | `https://jira.7gtech.net/mobile/version.json` |
| Gateway | `mcp-atlassian` / `gateway-2.7-prod16` | unchanged |

## 7. Artifacts and sha256

| Artifact | sha256 |
|----------|--------|
| `/root/jira-dev/releases/corbitchat-mobile-pwa-hub-1.0.0+94-figma-phase7b-design-lock1.tgz` | `a2fd6a3e036d0149bfe3a22f37ee025695731810e8997285353b2a324b08ca75` |
| `/root/jira-dev/releases/corbitchat-mobile-pwa-7gtech-1.0.0+94-figma-phase7b-design-lock1-prod.tgz` | `de3769cdfc79bbf8daaedef45808e0f59199508355425bb04fd673bc833a2407` |
| `/root/jira-dev/releases/corbitchat-mobile-hub-1.0.0+94-figma-phase7b-design-lock1.apk` | `d20c02f9d0e8f0915afb027d27e2708fbbb840e86f1e1389b584e53fb743e6ca` |
| `/root/jira-dev/releases/corbitchat-mobile-7gtech-1.0.0+94-figma-phase7b-design-lock1-prod.apk` | `1c076e7b10863623b505229bce85683015f2a6b7a92a7f38acf42b1d1e8a99e0` |

DEV PWA backup before swap: `/root/jira-backups/dev-mobile-93-before-figmachat7b-20260814-192616.tgz`

## 8. DEV deployment result

Stage → verify Hub title + `build_number` 94 + label → swap on `nginx-jira:/usr/share/nginx/mobile/`.

Live: `{"build_number":"94","build_label":"figma-phase7b-design-lock1","brand":"hub"}`  
Index title: `Hub — Jira Workspace`  
7G TECH PWA was **not** copied to nginx.

## 9. Visual evidence path

`/root/jira-dev/jira-issue-chat-panel-mcp-control-plane/docs/phase7b-visual-evidence/`

## 10–11. Figma vs DEV / remaining differences

See `docs/phase7b-visual-evidence/README.md`. Not pixel-perfect. Extra overlay rows, `+` picker, no job title, no OG cards, no selection mode, no bottom nav on push, Material icons vs missing Nucleo SVGs, app-wide primary still `#01BA9E`.

## 12. Regression checklist

| Check | Result |
|-------|--------|
| Design slice (overlay + gallery chrome) | Implemented; owner on-device review needed |
| Figma comparison captured | Refs + measured tokens + Hub login shell |
| Image viewer | Source preserved (`ChatImageViewer`) |
| Long-press overlay | Still `showGeneralDialog` + anchored overlay |
| Reactions | Five Figma slots + full picker; plugin allowlist still off on DEV |
| Shared open-message | `_openMessage` pop unchanged |
| Search jump | Untouched |
| Assistant filters گزارش‌ها / منشن گزارش / گزارش‌های دریافتی | Present in live WRM `figmachat7a-correct1` JS |
| Production | `+88` |
| Gateway | `mcp-atlassian` up, not rebuilt |

DEV plugin health: `health` / `ao-health` / `conversations` / chat servlet all **200**. Exactly one jar.

## 13. Production unchanged

`https://jira.7gtech.net/mobile/version.json` → `build_number` **88**. Plugin baseline remains `figmachat5a-shared-open-hotfix`.

## 14. Gateway unchanged

`mcp-atlassian` container not rebuilt (up 13 days at check time). No gateway files touched.

## 15. Ready for owner review?

**Yes, as a DEV design-lock slice — not as a Production rollout.**

Owner should hard-refresh DEV Hub PWA, confirm About `1.0.0 (94)` / `figma-phase7b-design-lock1`, then compare overlay + profile tabs to the Figma refs. 7G TECH APK/PWA are packaged for a later owner test install only.
