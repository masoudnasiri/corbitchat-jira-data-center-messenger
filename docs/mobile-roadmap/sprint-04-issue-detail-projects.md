# Sprint 04 — Read-only Issue Detail & Projects

**Phase:** 1 — MVP Core · **Duration:** 2 weeks · **Goal:** the complete
field-driven issue detail (read-only) and the Projects tab (list +
detail overview). Completes the "view everything" MVP.

> Spec: PDF §4.4 (projects), §4.6 (complete issue detail). Design:
> `docs/task-view.md`, `docs/modules.md` §4 (Projects), `docs/layout.md`
> (Issue Detail Layout), `tokens/customfield_10903.json`.

## Objective

Users can open any permitted issue and see **all** fields (system +
custom), rendered description, comments (read), attachments (list),
people and dates — driven by the API so org-specific custom fields
appear without an app release. Projects tab lists accessible projects and
opens a project overview.

## In scope

- Issue Detail (read-only): header, people, dates, rendered description,
  field-driven Details section, comments (read), attachments (list),
  activity (basic). Editing/actions come in Sprints 09–10.
- `customfield_10903` "نوع تسک (طبق دستورالعمل)" colored pill via the
  local mapping fallback (`tokens/customfield_10903.json`); server rules
  arrive Sprint 10.
- Projects tab: project list with stats; project detail with Overview
  tab (stat tiles) + tab bar scaffold (Board/Issues/Chat/Members/
  Activity tabs are placeholders wired in later sprints).

## Backend track (plugin BFF)

1. `GET /rest/corbit-mobile/1.0/issues/{key}` — field-driven payload:
   `{key, summary, project, issuetype, status(+category), priority,
   people:{assignee,reporter,watchers}, dates, renderedDescription,
   fields:[{id,label,value,render,cssClass?}], comments[], attachments[],
   transitions[](read-only list), permissions:{comment,edit,transition,
   assign,worklog}}`. Fetch comments with `expand=renderedBody`
   (handover gotcha #2) and return safe HTML. Resolve
   `customfield_10903` cssClass server-side (`docs/task-view.md` API
   Mapping).
2. `GET /rest/corbit-mobile/1.0/projects` — accessible projects with
   `{key,name,avatarUrl,lead,openIssueCount,unreadProjectChat}`.
3. `GET /rest/corbit-mobile/1.0/projects/{key}` — details + overview
   stats (open, overdue, assigned-to-me, done-this-week), members,
   boards refs.
4. Enforce Browse Project / issue-level permissions → 403 → mobile
   "no-permission" state (`docs/states.md`).

## Mobile track (Flutter)

1. Issue Detail screen with collapsible sections (all expanded default)
   per `docs/task-view.md`: Details / People / Description / Attachments /
   Comments / Activity. Compact top bar with issue key.
2. Field renderer engine: switch on `render` type (issueType, priority,
   pill, plainText, userField, date, labels…) → components from
   `docs/components.md` / `docs/task-view.md`. Pill for `customfield_10903`.
3. Rendered description: safe HTML/wiki display with links/images (do not
   reimplement wiki — handover gotcha #2).
4. People rows (avatar+name, tap→profile/chat), locale dates.
5. Projects list (`.cc-project-row`) + Project Detail with
   `.cc-project-header` + `.cc-tabs` and Overview stat tiles
   (`docs/modules.md` §4).
6. Deep link `corbitchat://issue/{key}` opens Issue Detail with loading
   skeleton.
7. Cache last-opened issues + projects for offline read.

## Design references

- `docs/task-view.md` — structure, fields, `customfield_10903`, API map.
- `docs/layout.md` — Issue Detail Layout.
- `docs/modules.md` §4 — Projects list + detail tabs + stat tiles.
- `tokens/customfield_10903.json` — pill color mapping fallback.

## API contract

```
GET /rest/corbit-mobile/1.0/issues/{key}         (comments expand=renderedBody)
GET /rest/corbit-mobile/1.0/projects
GET /rest/corbit-mobile/1.0/projects/{key}
```

## Dependencies

- Sprint 03 (issue cards/lists deep-link into this detail; search API).

## Acceptance criteria (PDF §9 Phase 1, §4.6)

- [ ] Issue detail shows ALL visible system + custom fields (full
      visibility mandatory even if not editable — PDF §1.3).
- [ ] `customfield_10903` shows correct colored pill (fa + en labels).
- [ ] Description renders with links/images; no raw wiki markup shown.
- [ ] Comments render (read) with renderedBody; attachments listed.
- [ ] Projects list + project overview stats correct.
- [ ] No-permission issue/project shows lock state, not empty.
- [ ] Issue detail loads in <3s with paginated comments (PDF §8).

## Definition of Done

- Field-driven rendering: adding an org custom field requires no app
  change (label/value/render come from API).
- RTL field rows (label at start) verified; issue keys wrapped `dir=ltr`.
