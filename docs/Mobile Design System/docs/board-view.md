# Board View (Trello-style)

Horizontal Kanban board for mobile and tablet. Inspired by Trello column layout while using CorbitChat/Jira data.

## Layout

```
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│ To Do  3 │ │ In Prog 2│ │ Review 1 │ │ Done   5 │  ← column headers
├──────────┤ ├──────────┤ ├──────────┤ ├──────────┤
│ ┌──────┐ │ │ ┌──────┐ │ │ ┌──────┐ │ │ ┌──────┐ │
│ │ card │ │ │ │ card │ │ │ │ card │ │ │ │ card │ │
│ └──────┘ │ │ └──────┘ │ │ └──────┘ │ │ └──────┘ │
│ ┌──────┐ │ │          │ │          │ │          │
│ │ card │ │ │ + Add    │ │          │ │          │
│ └──────┘ │ │   card   │ │          │ │          │
└──────────┘ └──────────┘ └──────────┘ └──────────┘
     ← horizontal scroll →
```

## Column Spec

| Property | Value |
|----------|-------|
| Width | 280px fixed |
| Background | `#EBECF0` (Trello gray) |
| Header text | `#5E6C84`, semibold 13px |
| Count badge | Subtle pill next to title |
| Max height | Viewport minus top bar; cards scroll vertically inside column |

## Card Spec

| Element | Description |
|---------|-------------|
| Label bars | Colored strips from **نوع تسک (طبق دستورالعمل)** (`customfield_10903`) or labels (height 8px) |
| Summary | Up to 4 lines, 13px |
| Issue key | Mono, xs, tertiary (e.g. TEC-34) |
| Task type pill | Mini version of `cc-field-pill` when space allows |
| Due date | Red pill if overdue, gray if ok |
| Assignee | 28px avatar, bottom-end corner |

**Interaction**: Tap → Issue detail. Long press → quick actions (assign, transition).

**Drag & drop**: Phase 3 — transition on drop when permitted.

## Modes

| Mode | Default on | Description |
|------|------------|-------------|
| **Board** (Trello) | Tablet / landscape | Horizontal columns |
| **List by status** | Phone portrait | Vertical grouped list (existing `cc-issue-card`) |

Toggle in board top bar: `Board | List`

## CSS Classes

| Class | Usage |
|-------|-------|
| `.cc-board` | Horizontal scroll container |
| `.cc-board-column` | Single status column |
| `.cc-board-column__header` | Title + count |
| `.cc-board-column__cards` | Scrollable card stack |
| `.cc-board-card` | Individual issue card |
| `.cc-board-add-card` | "+ Add a card" footer action |

## Card Label Colors

Use **نوع تسک (طبق دستورالعمل)** colors as top label bars on board cards:

```html
<div class="cc-board-card__labels">
  <span class="cc-board-card__label-bar" style="background:#DE350B"></span>
</div>
```

Or render full pill inside card footer for clarity on mobile.

## API

`GET /mobile/boards/{id}/issues` returns issues grouped by column:

```json
{
  "columns": [
    {
      "id": "todo",
      "name": "برای انجام",
      "count": 3,
      "issues": [
        {
          "key": "COR-5",
          "summary": "پیگیری اقدامات...",
          "assignee": { "avatarUrl": "...", "displayName": "..." },
          "customfield_10903": { "value": "توسعه‌ای", "cssClass": "development" },
          "dueDate": "2026-07-01",
          "overdue": false
        }
      ]
    }
  ]
}
```

## Empty Column

Show column with count `0` and muted "+ Add a card" if user has create permission; otherwise hide empty columns (configurable).
