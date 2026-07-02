# Sprint 11 — Boards (Gallery, Board / List views, Sprints)

**Phase:** 3 — Board & Productivity · **Duration:** 2 weeks · **Goal:**
mobile board access — Board Gallery, a Trello-style column view, a
List-by-status view, sprint support and card quick actions.

> Spec: PDF §4.5 (project board). Design: `docs/board-view.md`,
> `docs/modules.md` §5 (Board Gallery), `docs/task-view.md`
> (`customfield_10903` label bars). Architecture §2.6 (Board Gallery),
> §12 (Agile REST).

## Objective

Convert desktop Board Gallery + board grid logic into API + mobile UI:
a searchable board directory, a horizontally scrollable Kanban board, a
phone-friendly List-by-status default, sprint filtering, and quick
actions from cards.

## In scope

- Board Gallery: searchable tiles with Scrum/Kanban filter and column
  thumbnails (`docs/modules.md` §5).
- Board view (Trello columns) + List-by-status view with toggle; List is
  default on phone (PDF §4.5, `docs/board-view.md`).
- Sprint support: active/future/closed sprints + filter by sprint.
- Card fields: summary, key, status, assignee avatars, priority, due
  date, attachment/comment counts, server-colored fields + task-type
  label bars (`customfield_10903`).
- Quick actions from a card: open, permitted transition, assign to me,
  quick comment, open project chat.

## Backend track (plugin BFF)

1. `GET /rest/corbit-mobile/1.0/boards` — accessible boards with type
   (Scrum/Kanban), project, and column preview data (Agile REST
   `/rest/agile/1.0/board`).
2. `GET /rest/corbit-mobile/1.0/boards/{id}/issues` — issues grouped by
   column (`{columns:[{id,name,count,issues:[card DTO]}]}`), paginated,
   with `customfield_10903` cssClass + field rules resolved server-side
   (`docs/board-view.md` API).
3. `GET /rest/corbit-mobile/1.0/boards/{id}/sprints` — active/future/
   closed; filter board issues by sprint.
4. Reuse transition/assign endpoints (Sprint 10) for quick actions.
5. Enforce Browse Project on all board data.

## Mobile track (Flutter)

1. Board Gallery grid (`.cc-gallery`, 2-col phone/3-col tablet) with
   `.cc-board-tile` previews + filter chips (`docs/modules.md` §5).
   Reachable from Projects tab and a project's Board tab.
2. Board (columns) view: `.cc-board` horizontal scroll, `.cc-board-column`
   (280px), header + count, vertically scrolling `.cc-board-card`s with
   label bars + task-type pills + due pill + assignee avatar
   (`docs/board-view.md`).
3. List-by-status view (default on phone): reuse `.cc-issue-card` grouped
   by status with sticky headers; toggle `Board | List` in top bar.
4. Sprint selector; pull-to-refresh; quick filters.
5. Card long-press quick actions sheet (assign, transition, comment,
   project chat) via Sprint 10 endpoints. (Drag-and-drop transition
   deferred — `docs/board-view.md` marks it Phase 3+/later.)
6. Cache last board for offline read.

## Design references

- `docs/board-view.md` — layout, column/card spec, modes, API, label bars.
- `docs/modules.md` §5 — Board Gallery tiles + toolbar.
- `docs/task-view.md` / `tokens/customfield_10903.json` — pill colors.

## API contract

```
GET /rest/corbit-mobile/1.0/boards
GET /rest/corbit-mobile/1.0/boards/{id}/issues     (grouped by column, paginated)
GET /rest/corbit-mobile/1.0/boards/{id}/sprints
```

## Dependencies

- Sprint 04 (project detail Board tab), Sprint 10 (transitions/assign +
  field rules), Sprint 08 (project chat quick action).

## Acceptance criteria (PDF §9 Phase 3, §4.5)

- [ ] Board Gallery lists accessible boards; search + Scrum/Kanban filter.
- [ ] Board columns view + List-by-status view both work; List default on
      phone, toggle available.
- [ ] Sprint filter shows active/future/closed and filters issues.
- [ ] Cards show correct fields incl. server-colored `customfield_10903`
      label bars.
- [ ] Card quick actions (open, transition, assign, comment, chat) work.

## Definition of Done

- All board data permission-bound; empty columns handled
  (`docs/board-view.md` Empty Column).
- Horizontal column scroll + RTL correctness verified on phone + tablet.
