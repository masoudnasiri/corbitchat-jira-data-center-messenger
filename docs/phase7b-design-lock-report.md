# Phase 7B — Design lock report

Opened: 2026-08-14. Written **before** any Phase 7B code.

Design source of truth (read-only stale tree):

`/root/jira-dev/jira-issue-chat-panel/figma`

Official implementation repos:

- Plugin: `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane`
- Mobile: `/root/jira-dev/corbitchat-mobile`

Hub file: [fyCTz4B2izGsUFSsFXV13K](https://www.figma.com/design/fyCTz4B2izGsUFSsFXV13K/Hub)  
Device: **412×917** (notch 44 + body + bottom nav **71px**). RTL-first, Vazirmatn, Jalali dates, Persian digits.

Current accepted DEV mobile test build (do not treat as Production):

`1.0.0+93 / figma-chat-imageviewer-fix-prod`

Production (unchanged unless separately approved):

- Plugin: `figmachat5a-shared-open-hotfix`
- PWA: `1.0.0+88 / figma-chat-shared-open-hotfix`
- Gateway: `gateway-2.7-prod16`

---

## 1. Files read

### Docs / machine inventory

| File | Role |
|------|------|
| `FIGMA-INVENTORY.md` | Frame list, node IDs, overlay nodes, empty-state gap |
| `DESIGN-SYSTEM.md` | Tokens (colors, type, spacing, radii, shadows, RTL, icons) |
| `IMPLEMENTATION-MATRIX.md` | Per-frame behavior, assets, route map |
| `figma-manifest.json` | Machine dump of frames, flows, instances |
| `tokens/` | **Missing.** DESIGN-SYSTEM cites `tokens/tokens.json`; folder is empty. Hex values are observed / implementation tokens, not live Figma variables (MCP quota blocked `get_variable_defs`). |

### Screenshots / exports (all PNGs in the folder root)

Inventory markdown still says many screenshots are “pending MCP quota.” Those PNGs are now in the folder root and **were read**. `figma/screenshots/` is empty.

**Chat / profile (Phase 7B candidates)**

- `1 - Chat - PV.png` (`12:2083`)
- `2 - Chat - Group.png` (`12:2942`)
- `3 - Chat - Search.png` (`33:3687`)
- `4 - Chat - New chat.png` (`35:1793`)
- `5 - Chat - PV chat.png` (`68:8226`)
- `6 - Chat - Group chat.png` (`82:9829`)
- `7 - Chat - PV chat - More.png` (`83:9983`) — overlay + tray
- `8 - Chat - Profile - Attachments.png` (`92:10407`)
- `9 - Chat - Profile - Attachments - selected.png` (`102:13947`)
- `10 - Chat - Profile - Media.png` (`92:10658`)
- `11 - Chat - Profile - Media - Selected.png` (`92:11238`)
- `12 - Chat - Profile - Links.png` (`102:11596`)
- `13 - Chat - Profile - Links - selected.png` (`102:11856`)
- `14 - Chat - PV chat - Voice.png` (`102:12031`)
- `15 - Chat - PV chat - Attachments.png` (`102:13472`)
- `16 - Chat - PV chat - Links.png` (`102:14179`)
- `17 - Chat - Select member.png` (`130:16349`)
- `18 - Chat - Select member - Selected.png` (`130:16787`)
- `19 - Chat - Profile group.png` (`131:16987`) — **export is create-group, not chat-info**
- `20 - Chat page.png` (`132:17390`)
- `Chat.png` (`29:1739`) — row component library

**Dashboard / boards / tasks / more (ingested, out of 7B slice)**

- `1 - Dashboard.png`, `1 - Dashboard-1.png`
- `2 - Dashboard - Recently activity.png`
- `3 - Dashboard - Open menu.png` plus `-1` … `-4`
- `1 - Board list.png`, `2 - Board list - Project.png`, `3 - Board list - Project page.png`
- `4 - Board list - Kanban.png`, `5 - Board list - Kanban.png`
- `6 - Board list - Issue detail.png` plus `-1` `-2`
- `7` / `8` / `9 - Board list - Issue detail.png`
- `10 - Board list - Issue detail - Report.png`
- `1 - Tasks.png`, `2 - Tasks - Approve request.png`
- `3 - Tasks - Reject.png` plus `-1` `-2`
- `1 - More.png`, `2 - More - Profile.png`, `2 - More - Profile-1.png`
- `3 - More - Notification.png` plus `-1` `-2`
- `Frame 3474435.png`, `Frame 3474437.png` (More overlay / confirm)
- `Section 1.png`, `Section 2.png`
- Nav / role / asset crops: `داشبورد.png`, `گفتگو.png`, `بوردها.png`, `تسک‌ها.png`, `بیشتر.png`, `مدیر.png`, `کارمند.png`, `User/Users_Group.png`, `logos copy-cut 2.png`, `Frame 172.png`, `image (3) 2.png`

SVG icon bytes were **not** downloaded (`download_assets` quota). Do not invent replacement icon sets.

---

## 2. Frames / screens discovered

Hub flows: داشبورد / گفتگو / بوردها / تسک‌ها / بیشتر. Bottom nav `110:15334`.

| Frame | Node | Platform | Purpose |
|-------|------|----------|---------|
| 1 - Chat - PV | `12:2083` | Mobile/PWA | Chat list, Personal tab, search, FAB |
| 2 - Chat - Group | `12:2942` | Mobile/PWA | Chat list, Groups tab |
| 3 - Chat - Search | `33:3687` | Mobile/PWA | Colleague-name search chrome |
| 4 - Chat - New chat | `35:1793` | Mobile/PWA | New chat + Saved Messages + new group |
| 17 / 18 Select member | `130:16349` / `130:16787` | Mobile/PWA | New group member pick |
| 5 - PV chat | `68:8226` | Mobile/PWA | 1:1 thread + composer |
| 6 - Group chat | `82:9829` | Mobile/PWA | Group thread |
| 20 - Chat page | `132:17390` | Mobile/PWA | Fresh group + system create message |
| 14 Voice / 15 Attachments / 16 Links | `102:12031` / `13472` / `14179` | Mobile/PWA | Thread media states |
| **7 - PV chat - More** | **`83:9983`** | **Mobile/PWA** | **Dim overlay + 5-emoji tray + 5-row menu** |
| 8 / 9 Profile Attachments | `92:10407` / `102:13947` | Mobile/PWA | Shared files + selection |
| 10 / 11 Profile Media | `92:10658` / `92:11238` | Mobile/PWA | Media grid + selection |
| 12 / 13 Profile Links | `102:11596` / `102:11856` | Mobile/PWA | Link cards + selection |
| 19 - Profile group | `131:16987` | Mobile/PWA | Manifest says group profile; **PNG is Create Group** |
| Dashboard / Boards / Kanban / Issue / Report / Tasks / More | see inventory | Mobile/PWA | Large Hub IA — unsafe for this slice |
| Contextual menu | `84:10312` | Mobile | 256×190 action card |
| Reactions | `83:10193` | Mobile | 5 emoji slots |
| Overlay dim | `83:10119` | Mobile | Full-screen 412×917 |

**Web/plugin:** this Figma folder is Hub **mobile** only (412×917). No web messenger frames. Web shared-gallery / right-click polish is **not** design-locked by these PNGs.

**Empty / loading / error:** no frames. Inventory: implement from existing `state_views`, do not invent Figma screens.

---

## 3. Mapping to current app

| Frame | Current route / screen | Status |
|-------|------------------------|--------|
| Chat list PV/Group | Mobile conversation list + All/Personal/Groups | **partial** — tabs exist; segmented chrome, unread subtitle, FAB, job-title subtitles differ |
| Chat search `33:3687` | Message search (Phase 5), not colleague-name filter | **wrong** vs this frame (keep Phase 5 search) |
| New chat / select member | New conversation + group create | **partial** |
| PV / group thread | `ConversationScreen` | **partial** — composer, bubbles, header exist; voice / OG link / Word file card differ |
| **More overlay `83:9983`** | `MessageActionOverlay` + long-press | **partial / visually different** — behavior accepted; chrome not pixel-locked |
| Profile attachments/media/links | `SharedGalleryScreen` (`showProfileHeader`) | **partial** — real data, chips, open-message; chrome and labels differ |
| Profile selected `9/11/13` | — | **missing** (bulk toolbar) |
| Frame 19 PNG | `GroupInfoScreen` vs create-group | **wrong mapping** — PNG is create-group (`ایجاد گروه`) |
| Image viewer | `ChatImageViewer` (+93 accepted) | **implemented** — **no Figma full-screen viewer frame** |
| Dashboard / Boards / Tasks / More | existing Hub tabs | **partial / large redesign** |
| Web right-click / Assistant filters | plugin `jim-messenger.js` | **implemented** — no new Figma web spec |

---

## 4. Frames still missing from product

- Profile **selection / bulk** toolbar (view / forward / delete)
- Voice-note bubbles (play/pause, waveform)
- In-thread **OG link preview** cards
- In-thread Word file card (glyph + download inside bubble)
- Create-group identity card as drawn in `19` (camera + `ایجاد گروه` CTA) if that export is the intended group-profile
- Job titles on people rows
- Empty / loading / error named frames
- Quiet hours schedule detail
- Full dashboard / Kanban / issue-report visual lock
- Web/plugin frames in this folder

---

## 5. Existing implementations that are visually different

### P0 — overlay + tray (`83:9983`) vs live +93

Figma (from PNG + DESIGN-SYSTEM):

- Dim: `rgba(15,23,42,0.48)` — live `0x7A0F172A` is already ~48% of `#0F172A` (**close**)
- Tray: white pill, **downward notch**, **five** slots: 🌱 👍 👎 😃 🙏
- Menu: **256×190**, elevation-3 `0 8px 24px rgba(15,23,42,.12)`, radius **14**
- Exactly five rows, RTL, icon on the **right**, text on the **left**:
  1. پاسخ
  2. کپی
  3. اشتراک‌گذاری
  4. ارسال به
  5. حذف برای من (red)

Live +93:

- Tray: **11** presets (👍😂🙏👏🔥✅🎉💡🚀🤔🌱) + `+` full picker, **no notch**
- Menu: unconstrained width, Material elevation 8, radius **16** (`radiusLg`)
- Many extra rows (Saved Messages, pin, edit, attachment open/save/share, select, info)
- Forward label is `ارسال به…` (ellipsis)
- Delete-for-everyone is `حذف` in addition to `حذف برای من`

### P0 — shared profile tabs (`8/10/12`) vs live

Figma:

- One **grey segmented track**, selected = **white pill**, teal text `#0D6E6E`, count in a **grey badge**
- Labels: **پیوست‌ها / مدیا / لینک‌ها**
- Attachments: **one** white card, dividers, Word glyph, `۱۴۰۳/۲/۲۴ ۱۶:۰۸ | ۱.۵ مگابایت`, download on inline-end
- Media: **3-column** tight grid (~1–2px)
- Links: OG card (thumb + title + description + URL) — **API does not provide this**
- Header: avatar + name + **job title** — **API does not provide job title**
- Back: **برگشت** + `Arrow_Right_LG`, no centered title
- Bottom nav still visible on these frames

Live +93:

- **Three separate** stadium pills; labels **فایل‌ها / مدیا / لینک‌ها**
- Count is bare text, only when the list is fully loaded (honest — keep)
- Each file is its **own** card
- Links: URL + copy, no fake OG (**correct gap handling**)
- Header: name + online/member count, no job title (**correct**)
- AppBar title `اطلاعات گفتگو`
- Bottom nav hidden on push (matches DESIGN-SYSTEM “hide on pushed threads”)

### P1 — thread chrome (`5/6/14/15/16`)

Composer placeholder `یک دیدگاه بنویسید`; mic + paperclip circles; incoming white / outgoing grey bubbles radius 16; Jalali date pill `۲۰ تیر`. Live composer/bubbles exist but do not match these pixels. Voice and OG cards are missing.

### P1 — theme token drift (app-wide)

| Token | DESIGN-SYSTEM.md | Live `CorbitChatTheme` |
|-------|------------------|------------------------|
| Primary | `#0D6E6E` | `#01BA9E` |
| Surface bg | `#F5F6F8` | `#FAFAFA` |
| Text primary | `#1A2332` | `#141414` |
| radius sm/md/lg/xl/bubble | 6 / 10 / 14 / 20 / 16 | 8 / 12 / 16 / 24 / 18 |
| Bottom nav | 71 | 56 |

Rebasing the whole theme is **unsafe** for this slice.

### P2

Chat-list job-title subtitles, More hub `اطلاعات تکمیلی`, notification quiet hours, dashboard doughnut, Kanban cards, issue report tab.

---

## 6. Priority

| ID | Item | Pri | Why |
|----|------|-----|-----|
| 7B-1 | Overlay menu + tray chrome to `83:9983` | **P0** | Clearest screenshot + measured tokens; UI-only |
| 7B-2 | Shared tab track + `پیوست‌ها` + grouped file list | **P0** | Frames 8/10/12 are explicit; UI-only |
| 7B-3 | Profile header job title | P1 | **API gap — do not fake** |
| 7B-4 | Link OG cards | P1 | **API gap — do not fake** |
| 7B-5 | Selection / bulk delete-forward | P1 | New UX; delete may need backend |
| 7B-6 | Voice bubbles | P1 | No voice product path in this slice |
| 7B-7 | Global token rebase `#01BA9E` → `#0D6E6E` | P1 | Touches every screen |
| 7B-8 | Frame 19 as group profile | P1 | PNG contradicts manifest; unsafe |
| 7B-9 | Bottom nav on pushed profile | P2 | Conflicts with DESIGN-SYSTEM hide-on-push |
| 7B-10 | Dashboard / boards / tasks / More | P2 | Owner: avoid unless approved |
| 7B-11 | Web gallery / right-click | P2 | No web frames in this folder |
| 7B-12 | Image viewer restyle | P2 | Accepted in +93; no Figma viewer frame |

---

## 7. Backend / API required

| Item | API today | Action |
|------|-----------|--------|
| Job title / role on profile | Not on conversation DTO | Document gap; **do not fake** |
| Link OG (title, description, thumb) | Shared links expose URL (+ optional title if server sent one) | Show real URL / real title only |
| Bulk delete / forward of shared items | No bulk shared-content API | Defer |
| Voice notes | No voice message type in this slice | Defer |
| Reaction persist | Existing `{emoji}` + DEV plugin `figmachat7a-correct1` | **Do not change** |
| Shared list + open-message | `listSharedContent` + `messageId` | **Do not change** |
| Attachment open/save/share | Existing authenticated download | **Do not change** |

---

## 8. UI-only (safe)

- Overlay dim, menu 256 width, elevation-3, radius 14, row padding, danger red on `حذف برای من`
- Tray: Figma five slots + notch; **keep** `+` full emoji picker (required preserve; not in Figma)
- Extra overlay actions **below a divider** (pin, edit, attachments, select, info) — required product, not in this frame
- Forward overlay label `ارسال به` (picker title may keep ellipsis)
- Shared segmented track, `پیوست‌ها`, grey count pills
- Grouped attachment card + dividers + download icon
- Media 3-column tight grid (already close)
- Profile AppBar `برگشت` (no fake title fields)
- Honest counts only when the list is complete

---

## 9. Unsafe for this slice

- Production deploy, gateway, report/calendar logic, Workspace Agent, GitHub push, history rewrite
- Building from `/root/jira-dev/jira-issue-chat-panel` (except reading `/figma`)
- AO / schema / reaction model changes
- Fake job titles, fake counts, fake OG previews, placeholder `پروداکت دیزاین`
- Removing extra overlay actions, image-viewer rewrite, search-jump rewrite
- Showing bottom nav on pushed profile (IA conflict)
- Treating frame 19 PNG as chat-info
- Dashboard / Kanban / Tasks / quiet-hours redesign
- Global theme token rebase
- Plugin JAR unless a web frame appears (none did)

---

## 10. Recommended Phase 7B implementation scope

**Mobile/PWA only. DEV only.**

Version: `1.0.0+94 / figma-phase7b-design-lock1`  
7G TECH owner APK/PWA: `1.0.0+94 / figma-phase7b-design-lock1-prod`  
Plugin: **no change** (stay `figmachat7a-correct1` on DEV).

### In scope

1. **Message action overlay + reaction tray** locked to `83:9983` / `84:10312` / `83:10193` chrome, without dropping Phase 5/6 actions or the full emoji picker.
2. **Shared gallery chrome** locked to frames `8/10/12`: segmented track, Figma labels, grouped file list, 3-col media, URL-only links, real profile name/presence.

### Out of scope (deferred)

Selection mode, job titles, OG cards, voice, frame 19 create-group, token rebase, dashboard/boards/tasks/More, web plugin polish, image viewer, Production, gateway.

### Verification after code

DEV Hub PWA only. Visual evidence under `docs/phase7b-visual-evidence/`. Regression: image viewer, overlay, reactions, `باز کردن پیام`, search jump, Assistant filters, no Production/gateway change.

---

## Design tokens used for the slice (from DESIGN-SYSTEM.md)

| Role | Value |
|------|-------|
| Overlay | `rgba(15,23,42,0.48)` = `#0F172A` @ 48% |
| Primary (selected tab / accent in this slice) | `#0D6E6E` |
| Error / delete | `#C62828` |
| Elevated / menu fill | `#FFFFFF` |
| Surface bg | `#F5F6F8` |
| Text primary / secondary | `#1A2332` / `#5A6578` |
| Menu | 256 wide, radius **14**, elevation-3 |
| Tray | pill 9999, elevation-2 |
| Screen pad | 16 |
| Chip height | 32–40 |
| Do not invent | new hex, radii, or icons |
