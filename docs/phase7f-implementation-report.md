# Phase 7F — Implementation report (DEV only)

Opened after `docs/phase7f-backend-gap-inventory.md`. No Production,
Gateway, or Workspace Agent change. No GitHub push. No fake data.

## 1. Selected slice

**A + C + honest B activity.** Today-priority was already real
(`todayPreview`) and was not rebuilt.

| In | Out |
|---|---|
| Board `totalIssueCount` via permission-aware `searchCount` | OG fetch / SSRF |
| Per-user board favorites (UserProperty, no AO) | Waveform extraction |
| Dashboard `recentActivity` (≤8, 14 days, current-state cards) | Job title / colleagues / extra profile |
| Issue `followUpLevel` / `followUpLevelCode` from report field | Gallery bulk/destructive |
| Mobile +98 consume + honest nulls | Gateway, Workspace Agent, Production |

## 2. What was implemented

### Plugin `figmachat7f-backend1`

- `GET /rest/corbit-mobile/1.0/boards` cards now include:
  - `totalIssueCount` (integer or `null` — never an unscoped Jira-wide count)
  - `favorite` (boolean)
- `PUT /boards/{id}/favorite` and `DELETE /boards/{id}/favorite`
  - 401 anonymous
  - 404 if the board is missing or the viewer cannot see it
  - 400 at 40-favorite cap
  - Stored in user property `jim.board.favorites` (no AO/schema)
- `GET /dashboard` additive `recentActivity` (same issue DTO as cards)
- `toIssueMap` / issue detail: optional `followUpLevel` + `followUpLevelCode`
  from `JimReportConfig.levelCodeOf` (admin report field). Null if unset.

### Mobile `1.0.0+98 / figma-phase7f-backend1`

- Board card shows the real all-tasks total, or `—` when null
- Star toggle; Favorites segment lists `favorite == true` only
- Dashboard shows **فعالیت اخیر** only when `recentActivity` is non-empty
- Today priorities still use real `todayPreview` (empty = honest empty)
- Task card shows `سطح پیگیری` only when `followUpLevel` is non-null
- Phase 7E chrome preserved

## 3. What was deferred

See inventory. OG, waveform, job title, colleagues, extra profile fields,
gallery bulk, Nucleo icons. Today-priority needed no new backend.

## 4. Plugin files changed

- `pom.xml` → `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7f-backend1`
- `src/main/java/.../mobile/JimMobileBoardFavorites.java` (new)
- `src/main/java/.../mobile/JimMobileBoards.java`
- `src/main/java/.../mobile/rest/CorbitMobileBoardResource.java`
- `src/main/java/.../mobile/JimMobileIssues.java`
- `src/main/java/.../mobile/JimMobileIssueDetail.java`
- `src/main/java/.../mobile/rest/CorbitMobileDashboardResource.java`
- `docs/phase7f-backend-gap-inventory.md`
- `docs/phase7f-implementation-report.md` (this file)

## 5. Mobile files changed

- `lib/core/app_config.dart`, `pubspec.yaml`, `scripts/build_web.sh`, `scripts/build_apks.sh`
- `lib/models/board.dart`, `lib/models/issue.dart`
- `lib/api/corbit_api.dart`
- `lib/features/boards/widgets/board_card.dart`
- `lib/features/boards/board_gallery_screen.dart`
- `lib/features/tasks/widgets/issue_card.dart`
- `lib/features/shell/tabs/dashboard_tab.dart`
- `lib/core/strings.dart`

## 6. Versions and artifacts

| Surface | Version | Where |
|---------|---------|-------|
| DEV plugin | `figmachat7f-backend1` | DEV `jira-srv` only |
| DEV Hub PWA | `1.0.0+98 / figma-phase7f-backend1` | local `nginx-jira` `/mobile/` |
| 7G TECH PWA | `1.0.0+98 / figma-phase7f-backend1-prod` | artifact only, **not deployed** |
| Hub APK | `1.0.0+98 / figma-phase7f-backend1` | `releases/` |
| 7G TECH APK | `1.0.0+98 / figma-phase7f-backend1-prod` | `releases/` |
| Production plugin | `figmachat7a-correct1` | **unchanged** |
| Production PWA | `1.0.0+96 / figma-phase7d-main-surfaces1-prod` | **unchanged** |
| Gateway | `mcp-atlassian` / `mcp-atlassian-prod` Up 13 days | **unchanged** |

### Hashes

| Artifact | sha256 |
|---|---|
| Plugin jar | `fa69a697e95bdb6e2642a4fbec14cd52ea7ef421b5861afdb2f574aef6f10b46` |
| Hub PWA tgz | `009f0c491519a2bb3c43a864b6f93645d5e734531911388d6104a50ddbefc8f7` |
| 7G TECH PWA tgz | `2a9567357d77f90dc2ac6828988a34b377fc71cc5909a295dd8a24f29968d847` |
| Hub APK | `7e8773b1f9c05845bfc29002c5038c22fe3f61b46c4c25afdb70a9a28e93a104` |
| 7G TECH APK | `25f64e1e1a85e65a90ca2df3510986159ab58ab6af0452cb0fbbb6ae37287f85` |

Jar path: `/root/jira-dev/releases/corbitchat-jira-dc-1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7f-backend1.jar`  
(also `target/` in the official plugin repo)

APKs are artifacts only (not committed). Reviewer APK was also produced by
`build_apks.sh` and is not part of this Hub/7G TECH review slice.

## 7. DEV deployment

- Exactly one CorbitChat jar on DEV: `...figmachat7f-backend1.jar`
- Jira restarted; health 503 → 200
- Hub PWA stage-verify-swap on local `nginx-jira` only
- 7G TECH PWA/APK packaged, not copied to nginx
- Production not touched

## 8. Backend verification (DEV, `m.nasiri`)

| Check | Result |
|---|---|
| health / ao-health / chat / admin / MCP admin | 200 |
| workspace-agent/status | 200, `enabled: false` (unchanged) |
| boards / dashboard / bootstrap | 200 |
| issues/search?filter=assigned | 200 |
| issues/reports | 200 |
| issues/search without filter | 400 (existing contract) |
| boards unauthenticated | 401 |
| PUT favorite unauthenticated | 401 |
| PUT favorite hidden id `999999` | 404, no leak |
| PUT/DELETE favorite board 1 | 200; flag persisted then cleared |
| Real totals | TEST 27, WEO 18, RPS 39, … (not faked) |
| recentActivity | 8 real issues (e.g. TEST-28) |
| followUpLevel | TEST-28 `ویژه/special`, TEST-27 `بالا/high`; others null |
| todayPreview | `[]` (honest empty) |
| Assistant filters + `باز کردن پیام` | present in live JS batch |
| Gateway | local `mcp-atlassian` Up 13 days |

## 9. Mobile verification

- Live `/mobile/version.json`: `1.0.0` / `98` / `figma-phase7f-backend1` / `hub`
- Index title: `Hub — Jira Workspace`
- 7G TECH artifact: `figma-phase7f-backend1-prod` / `sevengtech`; no Hub title leak
- Boards/dashboard/tasks consume the new optional fields; null → `—` or omit
- Phase 7E chat chrome was not edited

## 10. Security / performance

- All new reads go through Jira `SearchService` / `RapidViewService` for the viewer
- Favorite toggle 404s hidden boards
- No AO schema change
- No external URL fetch
- Board totals: one extra `searchCount` per visible board (cap 120); empty JQL → null
- Activity: pager 8, `updated >= -14d`
- `pluginuser` not added to any new people list
- No secrets or issue bodies in new log lines

## 11. Confirmation

- Production unchanged (`figmachat7a-correct1`, PWA +96, Gateway Up 13 days)
- Gateway unchanged
- Workspace Agent unchanged
- No GitHub push

## 12. Owner review

DEV `+98` / plugin `figmachat7f-backend1` is ready for owner review.
Hard-refresh the Hub PWA (`Ctrl+Shift+R`). Production must not be
updated until the owner accepts this slice.
