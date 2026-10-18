# Sprint 04F — Board-first Navigation & Today/Overdue Timezone Fix

Status: **complete**, deployed to **test** and **production** (plugin
`1.0.0-mobile-s04f`, APK `releases/corbithub-s04f-boards-today.apk`).

Small correction sprint before Sprint 05 (Push Notifications): make Boards the
default navigation slot, and fix a Today/Overdue misclassification caused by
using the server timezone instead of the user's.

---

## 1. Board-first navigation

**Change:** the third bottom-nav slot now opens the **Board Gallery** by default
instead of the Projects list.

- The gallery body was extracted into a body-only `BoardGalleryBody`
  (`lib/features/boards/board_gallery_screen.dart`) so the shell can embed it
  (the shell already provides the AppBar). `BoardGalleryScreen` remains as a thin
  Scaffold wrapper for standalone use.
- The shell (`lib/features/shell/app_shell.dart`) slot 2 is now
  `BoardGalleryBody()`; label → `tabBoards` ("Boards" / "بوردها"); icon →
  `view_kanban` (outlined/filled for unselected/selected).
- **Projects stays fully accessible**: a top app-bar action (folder icon,
  tooltip = "Projects"/"پروژه‌ها") on the Boards tab pushes a new
  `ProjectsScreen` (`lib/features/projects/projects_tab.dart`) — its own
  Scaffold + AppBar wrapping the existing `ProjectsTab`. Project overview and its
  per-project **Board** tab (`ProjectBoardsView`) are unchanged.

**Label decision:** the label **was changed** to "Boards"/"بوردها". The slot's
default content is now the gallery, so keeping "Projects" would mislabel it.
Projects is one tap away via the top action, and its own screen is titled
"Projects"/"پروژه‌ها", so discoverability is preserved. No sixth tab was added.

## 2. Today / Overdue — root cause and fix

**Root cause: timezone, not JQL or display.** The Jira server runs in **+0800
(Asia/Shanghai)** while users are in **Asia/Tehran (+03:30)**. Sprint 04E
computed "today" with `TimeZone.getDefault()` (the *server* zone). During the
window when the server has rolled to the next day but Tehran has not
(server 00:00–03:30 = Tehran 20:30–24:00 previous day), a task whose effective
date is the user's *today* satisfies `date < server-today` and was wrongly listed
as **Overdue**.

Reproduced live on production (server `2026-07-03 02:11 +0800`, Tehran
`2026-07-02 21:41`): COR-6 with proposed date `2026-07-02` (Tehran today) landed
in **Overdue**. After the fix it correctly lands in **Today**.

**Fix (backend, one place):** compute "today" in the **viewer's** Jira timezone.

- `JimMobileDates.todayYmd(ApplicationUser)` formats `new Date()` with
  `TimeZoneManager.getTimeZoneforUser(user)` (falls back to server default).
- `isOverdueYmd(ymd, user)` / `isTodayYmd(ymd, user)` compare against the viewer's
  today. Card/detail `overdue` flags now pass the viewer.
- Date-field **values** are still formatted in the server zone (`toYmd`) because
  date-picker/Due-Date values are stored as midnight there — that recovers the
  exact calendar day the user picked. Only "today" is user-zone. Both are
  absolute calendar days, so the string comparison is correct.

**Final semantics (identical for Due Date and proposed-completion fallback):**

| effective date vs viewer's today | bucket |
|---|---|
| `== today` | Today |
| `< today` (strictly before) | Overdue |
| `> today` | neither |

Effective date = `dueDate ?? proposedCompletionDate` (unchanged from 04E).

**Unified query:** `JimMobileIssues.buildQuery(user, searchService, filter)` now
builds the Today/Overdue JQL for **both** the standard Due Date and the proposed
fallback from the same viewer-`today` literal:

```
today:   assignee = currentUser() AND resolution is EMPTY AND
         (duedate = "<today>"  [OR (duedate is EMPTY AND cf[id] = "<today>")])
overdue: assignee = currentUser() AND resolution is EMPTY AND
         (duedate < "<today>"  [OR (duedate is EMPTY AND cf[id] < "<today>")])
```

The `cf[...]` branch is added only when the field exists on the instance. JQL is
fully server-owned (mobile sends only a filter token). Dashboard counts and
previews use the same `buildQuery` + `toIssueMap`, so counts, previews, tasks
lists and card flags are always consistent.

### Verified matrix (production, Tehran today = 2026-07-02)

| COR-6 field | value | Today | Overdue |
|---|---|---|---|
| proposed | 2026-07-01 | no | **yes** |
| proposed | 2026-07-02 | **yes** | no |
| proposed | 2026-07-03 | no | no |
| dueDate | 2026-07-02 | **yes** | no |

Dashboard `today=1, overdue=2`, `todayPreview=[COR-6]` — matches the filters.

## Files changed

Backend:
- `mobile/JimMobileDates.java` — `todayYmd(user)`, `isOverdueYmd/ isTodayYmd(ymd,user)`,
  `userTimeZone(user)` via `TimeZoneManager`.
- `mobile/JimMobileIssues.java` — viewer-tz overdue flag; unified viewer-`today`
  literal JQL for Due Date + proposed fallback.
- `mobile/JimMobileIssueDetail.java` — viewer-tz overdue flag.
- `pom.xml` → `1.0.0-mobile-s04f`.

Mobile:
- `lib/features/boards/board_gallery_screen.dart` — extracted `BoardGalleryBody`.
- `lib/features/projects/projects_tab.dart` — new `ProjectsScreen` wrapper.
- `lib/features/shell/app_shell.dart` — Boards is slot 2; label/icon; top action
  → Projects.
- `lib/core/strings.dart` — `tabBoards` ("Boards"/"بوردها").

## Tests performed

- Flutter `analyze` clean; `flutter test` 8/8 pass.
- Backend `mvn -o clean package` green.
- **Bug reproduced then fixed** on prod (COR-6 overdue → today); boundary matrix
  above; dashboard counts match filters.
- Test + prod smoke (health, ao-health, bootstrap, dashboard, chat/conversations,
  projects, boards, all 4 issue filters, web chat) all **200**; both on s04f.
- Release APK built (54.5 MB).

## Deployment

- Rollback: previous jars in `/root/jira-dev/releases/`
  (`…-mobile-s04e.jar`, `…-mobile-s04d.jar`); prod DB dump + plugin dir from
  Sprint 04E at `corbit-prod:/root/jira-backups/…-20260702-171120`. Plugin key
  unchanged, no AO schema change.
- Path B file install + restart on both servers.

## Remaining risks

- "Today" follows the user's **Jira profile** timezone. A user whose device zone
  differs from their Jira profile zone will see classification by the profile
  zone (the correct, consistent Jira behaviour).
- Board-first is a behaviour change; Projects moved to a top action (documented).
- Old installed APKs still work but keep the Projects-first layout — ship this
  APK.

## Manual verification steps

Board-first navigation:
1. Open the app → tap the 3rd bottom-nav item ("Boards"/"بوردها", kanban icon):
   the **Board Gallery** opens with real boards, search and Scrum/Kanban filters.
2. Tap the folder icon (top-right / top-left in RTL): the **Projects** list opens
   with real projects. Open a project → overview works; its **Board** tab works.
3. Switch English ⇄ Persian: labels and RTL/LTR are correct; selected state on
   the Boards tab is correct.

Today / Overdue:
4. Ensure an assigned, unresolved issue has **no** Due Date but "تاریخ انجام
   پیشنهادی" = **today** → it appears in **Today**, not Overdue, with the
   "proposed"/"پیشنهادی" hint and correct Jalali (FA) / Gregorian (EN) date.
5. Same field = **yesterday** → **Overdue** (red emphasis). = **tomorrow** →
   neither list.
6. An issue with standard Due Date = today → Today; before today → Overdue.
7. Dashboard Today/Overdue counts equal the Tasks filter lists; the Today
   preview shows the same issues.

Regression:
8. Login (password + PAT), remembered login, chat send/receive, dashboard,
   tasks pagination/refresh, issue detail, projects, Board Gallery, avatars,
   `customfield_10903` colored pills all still work; backend smoke green on test
   and prod.
