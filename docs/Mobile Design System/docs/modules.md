# Feature Modules

Screen-level modules from the product spec that build on the base components. All are token-driven (light/dark) and RTL/LTR safe.

Styles: `css/modules.css`. Preview: `preview/index.html` (pages: Chats, Assistant, Group, Projects, Project, Gallery).

---

## 1. Chat List

The chat home with five filter tabs. Uses `.cc-tabs` + conversation rows.

Tabs: **All · Personal · Groups · Projects · Assistant** (`همه · شخصی · گروه‌ها · پروژه‌ها · دستیار`).

**Conversation row** (`.cc-conv-row`):

```
[avatar]  Name                     time
          last message preview…    [unread]
```

| Element | Class |
|---------|-------|
| Row | `.cc-conv-row` (`--unread` bolds name + snippet) |
| Name / time | `.cc-conv-row__name` / `.cc-conv-row__time` |
| Snippet | `.cc-conv-row__snippet` |
| Unread count | `.cc-count-badge` |

Avatar variants: `.cc-avatar-group` (stacked, for groups), `.cc-avatar--project` (square, project chat), `.cc-avatar--bot` (Assistant).

Presence dots via `.cc-avatar__presence--online|away|offline`.

---

## 2. Jira Assistant (activity center)

The Assistant is a special bot conversation that surfaces Jira activity as mobile events with quick actions. It is **not** a free-text chat — it's a feed.

**Header** (`.cc-assistant-header`): bot avatar + name + `دستیار` badge (`.cc-assistant-badge`) + subtitle (e.g. "۳ رویداد جدید").

**Feed** (`.cc-assistant-feed`): day separators (`.cc-assistant-day`) + event cards.

**Event card** (`.cc-assistant-event`):

```
(icon)  Title with ISSUE-KEY
        supporting text
        time            [Open] [Mark seen]
```

| Event type | Icon modifier | Deep link (spec) |
|------------|---------------|------------------|
| Mention | `--mention` | `corbitchat://issue/{key}?comment={id}` |
| Assignment | `--assign` | `corbitchat://issue/{key}` |
| Status change | `--status` | `corbitchat://issue/{key}` |
| Comment reply | `--reply` | `corbitchat://issue/{key}?comment={id}` |
| Overdue | `--overdue` | `corbitchat://issues?filter=overdue` |

- Unseen events: `.cc-assistant-event--unseen` (accent border on start edge).
- Quick actions: `.cc-assistant-action` — at minimum **Open issue** and **Mark as seen** (spec §2, §4.2).
- Issue keys use `.cc-issue-key` mono/link styling; keep `dir="ltr"` inside RTL text.

API: events come from the Assistant event model; "mark seen" posts back so badges clear across devices.

---

## 3. Group Chat

Extends the base chat bubbles for multi-party conversations.

| Feature | Class | Notes |
|---------|-------|-------|
| Group header | `.cc-top-bar` + stacked `.cc-avatar-group` | Subtitle = member count / typing |
| Sender name | `.cc-chat-bubble__sender` | Shown on incoming group bubbles only; color variants `--a…--d` |
| System message | `.cc-chat-system` | "X added Y", "Z left" — centered pill |
| Typing indicator | `.cc-typing` (3 `.cc-typing__dot`) | Animated; respects reduced-motion |
| Mentions | `.cc-mention` | `[~username]` Jira-compatible tokens |

**Member management** (Access Policy enforced): list of `.cc-list-item` rows with avatar, name, presence, and a `.cc-role-badge` (`--admin` for owners). Actions: message, remove (if permitted), add member.

Group creation and membership must respect `JimAccessPolicyService` (spec §4.3).

---

## 4. Projects

### Project list

`.cc-project-row` — accessible projects with avatar, name, key, lead, and quick stats.

```
[KEY]  Project name              [12]  [3]
       KEY · Lead name          open  unread
```

| Element | Class |
|---------|-------|
| Row | `.cc-project-row` |
| Avatar | `.cc-avatar--project` (key initials) |
| Name | `.cc-project-row__name` |
| Meta (key · lead) | `.cc-project-row__meta`, `.cc-project-row__key` |
| Stats | `.cc-project-stat` (open issues, unread project chat) |

### Project detail

Header (`.cc-project-header`) + tab bar (`.cc-tabs`): **Overview · Board · Issues · Chat · Members · Activity**.

**Overview** uses stat tiles (`.cc-stat-grid` / `.cc-stat`):

| Tile | Modifier |
|------|----------|
| Open issues | — |
| Overdue | `.cc-stat--warn` |
| Assigned to me | `.cc-stat--info` |
| Done this week | `.cc-stat--ok` |

Chat tab hosts the official **Project Chat** (`.cc-avatar--project`). Members tab reuses the group member list. Board tab links into Board / Board Gallery.

---

## 5. Board Gallery

Entry point that lists every board the user can access — the mobile equivalent of the desktop Board Gallery (spec §2, §4.5).

**Toolbar** (`.cc-gallery-toolbar`): search input (`.cc-input`) + filter chips (`.cc-filter-chip`): **All · Scrum · Kanban**.

**Grid** (`.cc-gallery`, 2-col phone / 3-col tablet) of **board tiles** (`.cc-board-tile`):

```
┌────────────────┐
│ mini columns   │  ← .cc-board-tile__preview (column thumbnail)
├────────────────┤
│ Board name     │
│ Project · SCRUM│  ← .cc-board-type--scrum | --kanban
└────────────────┘
```

| Element | Class |
|---------|-------|
| Tile | `.cc-board-tile` |
| Column preview | `.cc-board-tile__preview` / `.cc-board-tile__col` |
| Name | `.cc-board-tile__name` |
| Project | `.cc-board-tile__project` |
| Type badge | `.cc-board-type--scrum` / `--kanban` |

Tapping a tile opens the board (Trello-style columns or List-by-status — see `board-view.md`).

---

## Placement in navigation

- **Chat tab** → Chat List → (Direct / Group / Project chat / Assistant)
- **Projects tab** → Project list → Project detail (tabs incl. Chat + Board)
- **Board Gallery** → reachable from Projects tab and from a project's Board tab
- **Assistant** → a pinned conversation in the Chat List Assistant tab, and the target of Assistant push notifications
