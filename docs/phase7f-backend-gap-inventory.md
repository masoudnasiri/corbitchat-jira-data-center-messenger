# Phase 7F — Backend gap inventory

DEV-only review of items deferred in Phase 7D / 7E because they needed
real API or data. Design reference: `/root/jira-dev/jira-issue-chat-panel/figma`
(Hub mobile file). No fake data. No Production.

| # | Item | Action |
|---|---|---|
| 1 | Board favorites | **implement now** |
| 2 | Board all-tasks total | **implement now** |
| 3 | Dashboard activity timeline | **implement now** (honest recent issues, not invented changelog verbs) |
| 4 | Dashboard today-priority carousel | **already implemented** (`todayPreview`) |
| 5 | Real waveform samples | **defer** |
| 6 | OG link previews | **do not implement without separate approval** |
| 7 | Job title / organization role | **defer** |
| 8 | Colleagues list | **defer** |
| 9 | Task follow-up-level row | **implement now** |
| 10 | Shared-gallery multi-select / bulk | **do not implement without separate approval** |
| 11 | Profile fields missing from API | **defer** (same as 7) |
| 12 | Nucleo icons / leftover assets | **defer** (not a backend gap) |

---

## 1. Board favorites

1. **User-visible:** Board gallery Favorites segment; star a board.
2. **Figma:** Board list frames (`علاقه‌مندی` segment).
3. **Current UI:** Segment exists; empty state says favorites are not available. No star control.
4. **Missing:** Per-user favorite flag and toggle API.
5. **Existing APIs:** No. Boards DTO has `id/name/type/assignedCount` only. No AO favorite entity.
6. **Requires:** Plugin REST (additive `favorite` + PUT/DELETE). **No AO/schema.** UserProperty text `jim.board.favorites`. Jira RapidView visibility check. Mobile consume. No external network. Light security review (must 404 hidden boards).
7. **Risk:** Low.
8. **Action:** implement now.

## 2. Board all-tasks total

1. **User-visible:** Board card `همهٔ کارها` count.
2. **Figma:** Board card footer (my tasks / all tasks).
3. **Current UI:** Honest `—`. `assignedCount` is already real.
4. **Missing:** `totalIssueCount` on the board card.
5. **Existing APIs:** Same board saved-filter JQL already used for `assignedCount` via `SearchService.searchCount`.
6. **Requires:** Plugin REST additive field. Jira `searchCount` on board JQL (strip `ORDER BY`). Empty/unparseable JQL → `null`. No AO. Mobile consume. No external network.
7. **Risk:** Low–Medium (one extra `searchCount` per visible board, cap 120). Not unbounded issue fetch.
8. **Action:** implement now. If count fails, return `null` and keep `—`.

## 3. Dashboard activity timeline

1. **User-visible:** Dashboard “فعالیت اخیر”.
2. **Figma:** Dashboard activity list.
3. **Current UI:** Section omitted / not faked. Recent **chats** already use the conversation list.
4. **Missing:** Issue activity feed. No changelog API on the mobile BFF.
5. **Existing APIs:** Permission-aware JQL search already used by dashboard counts/preview.
6. **Requires:** Additive `recentActivity` on `GET /dashboard`. Bounded JQL (`assignee OR reporter`, `updated >= -14d`, page ≤ 8). Same issue DTO as cards. **Do not invent** “marked as Done” verbs. Mobile show only when non-empty. No AO. No external network.
7. **Risk:** Low.
8. **Action:** implement now (honest current-state cards, not a fake changelog).

## 4. Dashboard today-priority carousel

1. **User-visible:** “اولویت‌های امروز”.
2. **Figma:** Dashboard today-priority block.
3. **Current UI:** Already rendered from real `todayPreview` (`preview(viewer, "today")`, size 5). Empty state is honest.
4. **Missing:** Nothing for a first real slice. A visual carousel (vs stacked cards) is UI-only.
5. **Existing APIs:** Yes — `GET /dashboard` → `todayPreview`.
6. **Requires:** None for data. Optional later mobile-only carousel restyle.
7. **Risk:** n/a.
8. **Action:** already implemented. No new backend in 7F.

## 5. Real waveform samples for voice/audio

1. **User-visible:** Voice bubble waveform.
2. **Figma:** Voice/audio message frames.
3. **Current UI (7E):** Decorative progress bars. Honest (not claimed as samples).
4. **Missing:** Decoded peak samples stored or computed from attachment bytes.
5. **Existing APIs:** Attachment download exists; no waveform field.
6. **Requires:** Decode/storage or on-read ffmpeg. CPU/disk. Security review of media processing. Not a small REST field.
7. **Risk:** High.
8. **Action:** defer.

## 6. OG link previews

1. **User-visible:** Rich title/image cards for URLs.
2. **Figma:** Link preview cards.
3. **Current UI (7E):** `HonestLinkCard` (URL/domain only).
4. **Missing:** External HTTP fetch of target pages.
5. **Existing APIs:** No OG fields.
6. **Requires:** External network. **SSRF** controls (block RFC1918/metadata, allowlist schemes, timeouts, size limits, no credential forward). Security review **before** any fetch.
7. **Risk:** High.
8. **Action:** do not implement without separate approval. Stop here.

## 7. Job title / organization role

1. **User-visible:** Title under the display name (profile / identity).
2. **Figma:** Profile header.
3. **Current UI:** `@username` only. No fake title.
4. **Missing:** Job title on the user DTO.
5. **Existing APIs:** Bootstrap user has `displayName` / `name` / avatar. Jira DC user directory does not expose a reliable title on this instance.
6. **Requires:** Directory/custom field mapping + security review if a new user field is chosen. Not present today.
7. **Risk:** Medium (wrong field = misleading identity).
8. **Action:** defer.

## 8. Colleagues list

1. **User-visible:** More → همکاران.
2. **Figma:** Colleagues / people list.
3. **Current UI:** Row with `به‌زودی`.
4. **Missing:** A permission-safe “people I work with” source.
5. **Existing APIs:** Project role members exist per project; no global colleagues endpoint. User search hides `pluginuser`.
6. **Requires:** New aggregation (shared projects / recent assignees) + pagination + hide `pluginuser`. Easy to over-share.
7. **Risk:** Medium–High (directory leak).
8. **Action:** defer.

## 9. Task follow-up-level row

1. **User-visible:** Task card row `سطح پیگیری`.
2. **Figma:** Task card dotted-leader rows.
3. **Current UI:** Row omitted (comment in `IssueCardTile`).
4. **Missing:** Field not projected on the mobile issue card DTO.
5. **Existing APIs:** Admin `getReportLevelFieldId()` (default `customfield_10802`). `JimReportConfig.levelCodeOf` already reads it for reports.
6. **Requires:** Additive `followUpLevel` / `followUpLevelCode` on `toIssueMap`. Null if unset/unmapped. No AO. No new JQL. Mobile show only when non-null.
7. **Risk:** Low.
8. **Action:** implement now.

## 10. Shared-gallery multi-select / bulk actions

1. **User-visible:** Select many attachments; bulk download/delete.
2. **Figma:** Gallery selection mode.
3. **Current UI:** Single-item actions only.
4. **Missing:** Bulk delete/download APIs and destructive confirmation.
5. **Existing APIs:** Per-item attachment delete/download.
6. **Requires:** New destructive endpoints or client loops. Security review. Easy to delete-for-everyone by mistake.
7. **Risk:** High.
8. **Action:** do not implement without separate approval.

## 11. Profile fields missing from real API

1. **User-visible:** Extra profile rows (title, org, phone, …).
2. **Figma:** Profile tabs.
3. **Current UI:** Real Jira identity only; extras omitted.
4. **Missing:** Same as item 7 plus fields Jira does not return on bootstrap.
5. **Existing APIs:** Bootstrap user is name/displayName/avatar/locale.
6. **Requires:** Directory mapping. Do not invent fields.
7. **Risk:** Medium.
8. **Action:** defer.

## 12. Nucleo icons / leftover assets

1. **User-visible:** Exact Figma icon set (nav, boards, chat).
2. **Figma:** Nucleo / design-system icons.
3. **Current UI (7E):** Material stand-ins (`grid_view`, `chat_bubble`, …).
4. **Missing:** Licensed Nucleo pack or exported SVG set — **not** an API gap.
5. **Existing APIs:** n/a.
6. **Requires:** Asset/license work only.
7. **Risk:** Low (visual).
8. **Action:** defer (not backend).

---

## Phase 7F implementation slice (selected)

**A + C + honest B activity. Not B today-priority (already live).**

| In 7F | Out of 7F |
|---|---|
| `totalIssueCount` (null if unavailable) | OG fetch / SSRF |
| Per-user board favorites (UserProperty, no AO) | Waveform extraction |
| `recentActivity` bounded recent issues | Job title / colleagues / extra profile |
| `followUpLevel` from existing report field | Gallery bulk/destructive |
| Mobile +98 consume + honest nulls | Gateway, Workspace Agent, Production |

Plugin version: `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7f-backend1`  
Mobile: `1.0.0+98 / figma-phase7f-backend1` (Hub DEV). 7G TECH artifact only.
