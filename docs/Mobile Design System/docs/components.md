# Components

Component specifications for CorbitChat mobile. All components must work in RTL and LTR.

---

## Button

**Purpose**: Primary actions — login, send message, retry, transition.

| Variant | Background | Text | Usage |
|---------|------------|------|-------|
| Primary | `brand.primary` | white | Main CTA |
| Secondary | `secondaryMuted` | `secondary` | Cancel, secondary actions |
| Ghost | transparent | `primary` | Toolbar actions |
| Danger | `errorMuted` | `error` | Delete, logout confirm |

**Sizes**: Default (44px min height), Small (36px)

**Anatomy**: `[icon?] Label [icon?]`

**States**: default, hover, pressed (scale 0.98), disabled (40% opacity), loading (spinner replaces label)

**CSS**: `.cc-btn`, `.cc-btn--primary`, `.cc-btn--secondary`, `.cc-btn--ghost`, `.cc-btn--danger`, `.cc-btn--sm`, `.cc-btn--icon`

---

## Badge / Chip

**Purpose**: Issue status, priority, overdue indicator, reply count.

**Anatomy**: `[icon?] Label`

**Variants**: status (todo, in-progress, done, blocked), priority (highest–low), overdue, count

Server-driven rules from `/mobile/field-display-rules` override default token colors.

**CSS**: `.cc-badge`, `.cc-badge--{variant}`, `.cc-count-badge`

---

## Issue Card

**Purpose**: Dense task row in lists, board list view, search results.

**Anatomy**:
```
┌─────────────────────────────────────┐
│ PROJ-123              [Status badge]│
│ Two-line summary text that truncates│
│ [Priority] [Due date] [Avatar] [💬3]│
└─────────────────────────────────────┘
```

**Fields shown** (configurable via projection):
- Issue key (mono, xs, secondary)
- Summary (2 lines max, medium weight)
- Status badge
- Priority badge (optional)
- Due date (red if overdue)
- Assignee avatar
- Comment/attachment counts

**Highlight**: When `assignee = currentUser`, apply left/right border accent (`cc-issue-card--highlight`).

**Interaction**: Tap → Issue Detail. Long press → quick actions sheet.

---

## Dashboard Card

**Purpose**: Operational summary tiles on home screen.

**Anatomy**:
```
┌──────────────┐
│ 12           │  ← large count
│ پیام جدید    │  ← label
└──────────────┘
```

**Variants**: messages, today, overdue, followup, colleagues, reports

Grid: 2 columns on phone, 3 on tablet.

Tap navigates to filtered list or chat.

---

## Avatar

**Sizes**: sm (32px), md (40px), lg (48px)

**Anatomy**: Initials or image + optional presence dot

**Presence**: online (green), away (amber), offline (gray)

Used in: chat list, issue assignee, project members, colleagues row.

---

## Chat Bubble

**Outgoing**: Teal background, white text, tail on bottom-end (RTL-aware)

**Incoming**: White background, border, tail on bottom-start

**Anatomy**:
```
[Reply preview?]
Message text with @mention support
[Attachments?]
          10:32 ✓✓  ← meta (time + read receipt)
```

**Reply preview**: Quoted block with 3px accent border on start side.

**Mentions**: `.cc-mention` — highlighted `[~username]` tokens.

---

## List Item

**Purpose**: Conversation row, project row, notification row, settings row.

**Anatomy**:
```
[Avatar/Icon]  Title                    [Badge/time]
               Subtitle preview...
```

**Min height**: 44px (can expand for 2-line subtitle)

**Unread**: Bold title + count badge on trailing edge.

---

## Top Bar

**Height**: 56px

**Anatomy**: `[Back/menu] Title [Actions...]`

**Variants**:
- Dashboard: greeting + sync indicator + notifications
- Detail screens: back + entity title + overflow menu
- Chat: avatar + name + presence

---

## Bottom Navigation

**5 tabs**: Dashboard, Chat, Projects, Tasks, More

**Anatomy**: Icon (24px) + label (11px)

**Active**: Primary color icon + label

**Badges**: Chat and Tasks tabs show unread/issue counts

Safe area inset respected on iOS.

---

## Tabs (Segmented / Pill)

**Purpose**: Chat filters (All, Personal, Groups, Projects, Assistant), Project detail tabs.

**Style**: Horizontal scroll, pill shape, active = primaryMuted background.

**CSS**: `.cc-tabs`, `.cc-tab`, `.cc-tab--active`

---

## Input / Text Field

**Min height**: 44px

**Variants**: single line, multiline (comment composer), search

**Anatomy**: `[Leading icon?] Input [Trailing action?]`

**Comment composer**: Fixed above keyboard with attach, mention, send buttons.

---

## Empty / Error / Loading States

See [states.md](./states.md).

**CSS**: `.cc-state`, `.cc-skeleton`

---

## Toast / Snackbar

**Position**: Above bottom nav, full width with horizontal margin.

**Duration**: 3s default, 5s for errors with action.

**Anatomy**: Message + optional action button (Retry, Undo).

---

## Modal / Bottom Sheet

**Bottom sheet** preferred on mobile for:
- Status transitions
- Quick actions on issue
- Filter picker
- Member selection

**Radius**: xl (20px) top corners only.

**Max height**: 90vh with scroll.

---

## Field Display (Server-Driven)

Mobile renders fields using rules from the plugin API:

| Rule type | Example | Component |
|-----------|---------|-----------|
| Value mapping | Priority = Highest | Badge with icon |
| Status category | Done | Status badge color |
| Date threshold | dueDate < now | Red date + overdue badge |
| Numeric range | progress >= 80 | Green progress bar |
| Text pattern | contains VIP | Custom label chip |
| User field | assignee = me | Card highlight border |

Do not hard-code organization-specific colors in the app.

---

## Notification List Item

**Anatomy**:
```
[Type icon]  Title (issue key or sender)
             Preview text (based on privacy setting)
             Relative time
```

**Unread**: Bold + dot indicator on start side.

**Swipe**: Mark as read (start), mute (end) — optional Phase 3.

---

## Board Column Header (Board View)

**Anatomy**: Status name + count pill + optional WIP limit warning

Horizontally scrollable columns on tablet; List View default on phone.

---

## Progress Bar

Used for numeric custom fields (e.g. story points, % complete).

**Height**: 6px, radius full.

Colors from server rules or semantic success/warning thresholds.
