# Layout & Navigation

## Screen Shell

Every screen shares a common structure:

```
┌─────────────────────────────┐
│ Top Bar (56px)              │
├─────────────────────────────┤
│ Optional Tabs (48px)        │
├─────────────────────────────┤
│                             │
│ Scrollable Content          │
│                             │
│                             │
├─────────────────────────────┤
│ Bottom Nav (56px + safe)    │  ← hidden on modal/full-screen flows
└─────────────────────────────┘
```

**Content padding**: 16px horizontal unless full-bleed list.

**Safe areas**: Respect `safe-area-inset-top` and `safe-area-inset-bottom` on iOS.

---

## Primary Navigation (Bottom Tabs)

| Tab | Icon | Badge | Destination |
|-----|------|-------|-------------|
| Dashboard | home | — | `/dashboard` |
| Chat | message | unread conversations | `/conversations` |
| Projects | folder | — | `/projects` |
| Tasks | checklist | overdue count (optional) | `/tasks` |
| More | menu | — | drawer / `/more` |

Tabs persist across main sections. Detail screens (issue, conversation) hide bottom nav or show contextual actions.

---

## Secondary Navigation

### More Menu / Drawer

Accessible from **More** tab:

- Colleagues
- Notifications
- Settings (language, notifications, cache)
- About / version
- Logout

### Project Detail Tabs

Horizontal pills below top bar:

`Overview | Board | Issues | Chat | Members | Activity`

Default tab: **Overview** on first open; deep links may target Board or Chat.

### Chat Filter Tabs

`All | Personal | Groups | Projects | Assistant`

Persist last selected tab per session.

---

## Screen Map

| Screen | Top bar title | Primary action |
|--------|---------------|----------------|
| Login | CorbitChat | Connect |
| Dashboard | Greeting + user name | Sync |
| Chat list | Chats | New chat FAB |
| Conversation | Contact / group name | Attach, send |
| Projects | Projects | Search |
| Project detail | Project name + key | Project chat shortcut |
| Board | Board name | Toggle List/Board view |
| Issue list | Filter name (Today, Overdue…) | Filter/sort |
| Issue detail | Issue key | Transition, comment |
| Notifications | Notifications | Mark all read |
| Settings | Settings | — |

---

## Dashboard Layout

```
Greeting + sync status
┌─────────┐ ┌─────────┐
│ Messages│ │ Today   │
└─────────┘ └─────────┘
┌─────────┐ ┌─────────┐
│ Overdue │ │ Follow  │
└─────────┘ └─────────┘
Colleagues (horizontal avatar scroll)
Recent Assistant events (list)
```

Cards use 2-column grid with 12px gap.

Overdue card uses error tint — visually prioritized when count > 0.

---

## Issue Detail Layout

Vertical scroll, grouped sections:

1. **Header** — key, summary, type icon, status badge, transition button
2. **People** — assignee, reporter (avatar rows)
3. **Dates** — created, updated, due (locale-formatted)
4. **Description** — rendered HTML/wiki
5. **Fields** — collapsible groups, searchable in Phase 2
6. **Comments** — threaded with reply badges
7. **Attachments** — grid preview
8. **Activity** — filterable timeline

Fixed comment composer at bottom when user has comment permission.

---

## Board Layouts

### List View (default on phone)

Issues grouped by status column name. Section headers sticky.

### Column View (tablet or toggle)

Horizontal scroll of columns. Each column min-width 280px.

Card fields match issue card spec + server-colored custom fields.

---

## Chat Layout

**Conversation list**: Full-bleed list items, search bar pinned below tabs.

**Conversation screen**:
- Messages reverse-chronological scroll
- Date separators between days
- Jump-to-latest FAB when scrolled up
- Composer docked above keyboard

---

## Login Layout

Centered form, max-width 400px:

1. CorbitChat logo + tagline
2. Jira URL input
3. Auth method selector (PAT / SSO / session)
4. Language toggle (fa/en)
5. Connect button
6. Connection test result

---

## Responsive Breakpoints

| Breakpoint | Width | Behavior |
|------------|-------|----------|
| sm | 360px | Minimum supported phone |
| md | 414px | Standard phone (preview frame) |
| lg | 768px | Tablet — 3-col dashboard, board columns |
| xl | 1024px | Large tablet — optional side panel |

---

## Deep Link Handling

Push notifications and URLs use scheme `corbitchat://`:

| Path | Screen |
|------|--------|
| `conversation/{id}` | Chat thread |
| `issue/{key}` | Issue detail |
| `issue/{key}?comment={id}` | Issue detail, scroll to comment |
| `issues?filter=overdue` | Overdue task list |

Show loading skeleton while fetching deep-link target.

---

## Z-Index Stack

| Layer | z-index |
|-------|---------|
| Base content | 0 |
| Sticky headers | 10 |
| Bottom nav | 10 |
| Dropdowns | 100 |
| Overlay scrim | 200 |
| Modals / sheets | 300 |
| Toasts | 400 |
