# Sprint 10 — Transitions, Assign, Worklog & Field Display Rules

**Phase:** 2 — Jira Actions · **Duration:** 2 weeks · **Goal:** make the
issue actionable (workflow transitions, assign/unassign, worklog display)
and introduce the server-driven Field Display Rules engine.

> Spec: PDF §4.5 (quick actions), §4.6 (worklog/header actions), §4.8
> (field formatting), §6.2. Design: `docs/task-view.md` (Action
> Toolbar), `docs/components.md` (Field Display, Badge, Progress Bar),
> `docs/foundation.md` (status/priority). Closes Phase 2.

## Objective

Users can perform permitted workflow transitions, assign to me/others,
and view worklogs; and the whole app starts rendering fields via the
`field-display-rules` API so colors/badges are controlled server-side,
without app releases (PDF §4.8).

## In scope

- Transitions: list allowed transitions + execute (with screen fields
  where required by the workflow — basic).
- Assign / unassign (incl. "assign to me").
- Worklog: display list + total time (create/edit deferred to a later
  release per PDF §4.6 "Phase 1 display, Phase 2 create/edit").
- Watch / unwatch.
- **Field Display Rules engine**: `JimFieldDisplayRule` AO +
  `GET /field-display-rules`; client applies value-mapping, status-
  category, date-threshold, numeric-range, text-pattern, user-field rules
  (PDF §4.8 table) with token fallbacks (`docs/foundation.md`).

## Backend track (plugin BFF)

1. `GET/POST /rest/corbit-mobile/1.0/issues/{key}/transitions` — list
   permitted transitions and execute (enforce TRANSITION permission).
2. `POST /rest/corbit-mobile/1.0/issues/{key}/assignee` — assign/unassign
   (ASSIGN_ISSUES).
3. `GET /rest/corbit-mobile/1.0/issues/{key}/worklogs` — worklog list +
   totals (display only this sprint).
4. `POST/DELETE /rest/corbit-mobile/1.0/issues/{key}/watchers` — watch/
   unwatch.
5. AO `JimFieldDisplayRule` (`fieldId, conditionType, conditionValue,
   color, icon, label, priority`) + `GET
   /rest/corbit-mobile/1.0/field-display-rules`; also resolve
   `cssClass`/formatting into the issue/board/card DTOs server-side
   (retrofit Sprint 04 issue DTO + Sprint 03 card DTO).
6. Minimal admin surface to manage rules (or seed defaults incl.
   `customfield_10903` mapping from `tokens/customfield_10903.json`).

## Mobile track (Flutter)

1. Action toolbar on issue detail (`docs/task-view.md`): Edit (opens
   sheet — basic), Comment (scroll), Assign (user picker), More (watch/
   share/link), Status (workflow bottom sheet, blue `brand.workflow`).
2. Transition bottom sheet: list transitions, confirm, optimistic status
   update, handle required-field screens minimally.
3. Assign picker (assign to me shortcut) + watch/unwatch toggle.
4. Worklog section (display): entries + total, locale time formatting.
5. Field renderer consumes `field-display-rules`: badges, colored dates
   (overdue red), numeric progress bars, VIP/text labels, card highlight
   for `assignee=me` (`docs/components.md` Field Display, Progress Bar).
   Cache rules; fall back to design tokens when a rule is absent.
6. Apply rules across Dashboard/Tasks/Issue cards retroactively.

## Design references

- `docs/task-view.md` — Action Toolbar, permissions per action.
- `docs/components.md` — Field Display (Server-Driven), Badge, Progress Bar.
- `docs/foundation.md` — Status Categories, Priority, semantic colors.

## API contract

```
GET  /rest/corbit-mobile/1.0/issues/{key}/transitions
POST /rest/corbit-mobile/1.0/issues/{key}/transitions   { transitionId, fields? }
POST /rest/corbit-mobile/1.0/issues/{key}/assignee      { userKey|null }
GET  /rest/corbit-mobile/1.0/issues/{key}/worklogs
POST /rest/corbit-mobile/1.0/issues/{key}/watchers  / DELETE
GET  /rest/corbit-mobile/1.0/field-display-rules
```

## Dependencies

- Sprint 04 (issue detail + permissions block + field renderer).

## Acceptance criteria (PDF §9 Phase 2, §4.8)

- [ ] Allowed transitions listed and executed; disallowed hidden.
- [ ] Assign/unassign + assign-to-me work; watch/unwatch works.
- [ ] Worklog list + total displayed with locale formatting.
- [ ] Field colors/badges come from `field-display-rules` (change a rule
      server-side → app reflects it with no release).
- [ ] `customfield_10903` colors served by rules (local map is fallback).
- [ ] Disallowed actions never shown as dead buttons.

## Definition of Done

- Transition/assign/worklog writes are permission-checked + audited
  (minimal record).
- Rules cached with token fallback; no org colors hard-coded in the app.
