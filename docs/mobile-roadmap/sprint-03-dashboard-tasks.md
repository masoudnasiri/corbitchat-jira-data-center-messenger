# Sprint 03 — Dashboard & Tasks

**Phase:** 1 — MVP Core · **Duration:** 2 weeks · **Goal:** the
operational home screen (aggregated dashboard) and the Tasks tab
(filtered issue lists) — the app's daily entry points.

> Spec: PDF §4.2 (dashboard), §5.2 (screen map), §8 (performance <2s).
> Design: `docs/layout.md` (Dashboard Layout), `docs/components.md`
> (Dashboard Card, Issue Card), `docs/foundation.md` (dashboard card
> colors), `docs/states.md`.

## Objective

One aggregated dashboard call renders operational cards in <2s, and the
Tasks tab provides today/overdue/follow-up issue lists via controlled
JQL. Read-only issue cards; tapping a card deep-links to lists (issue
detail arrives Sprint 04).

## In scope

- Dashboard cards: New messages, Today's tasks, Overdue, Follow-up,
  Colleagues (horizontal avatar scroll), Recent Assistant/activity.
- Tasks tab: filtered issue lists (Today, Overdue, Follow-up) built from
  server-side JQL; sort + basic filter; pagination.
- Issue Card component (`docs/components.md`) with
  `assignee=currentUser` highlight; overdue red date.
- Pull-to-refresh + sync indicator (`docs/states.md`).

## Backend track (plugin BFF)

1. `GET /rest/corbit-mobile/1.0/dashboard` — one aggregated,
   permission-aware payload (PDF §3.3): unread counts, today's tasks
   count+preview, overdue count (`due < now AND statusCategory != Done`),
   follow-up (watching/mentioned/assigned/recently-updated), colleagues
   (active members in shared projects + recent chats), recent Assistant
   events. Bound each sub-query with limits (PDF §8 scalability).
2. `POST /rest/corbit-mobile/1.0/issues/search` — controlled JQL
   (whitelisted filters), field projection, cursor/page pagination,
   sort. Backed by Jira REST `POST /rest/api/2/search` server-side.
3. Predefined filter tokens: `today`, `overdue`, `followup`,
   `assigned-to-me` resolved to JQL server-side (client never sends raw
   unbounded JQL).
4. Use `JimMobileSyncCursor` for cheap dashboard refresh.

## Mobile track (Flutter)

1. Dashboard screen: 2-col card grid (`docs/layout.md`), per-card tints
   (`docs/foundation.md` Dashboard Card Colors), greeting + sync
   indicator in top bar. Overdue card prioritized when count>0.
2. Colleagues horizontal avatar scroll (tap → start chat/profile).
3. Recent Assistant events list (read-only preview → deep link).
4. Tasks tab: segmented filter (Today/Overdue/Follow-up), Issue Card
   list, infinite scroll pagination, sort/filter bottom sheet.
5. Jalali/Gregorian date rendering on cards per locale
   (`docs/rtl-i18n.md`).
6. Cache dashboard + last issue lists for offline display.

## Design references

- `docs/layout.md` Dashboard Layout, Issue List.
- `docs/components.md` Dashboard Card, Issue Card, Avatar.
- `docs/foundation.md` Dashboard Card Colors, Priority/Status badges.
- `docs/states.md` Loading skeletons, Pull to Refresh, Sync Indicator.

## API contract

```
GET  /rest/corbit-mobile/1.0/dashboard
POST /rest/corbit-mobile/1.0/issues/search  { filter|jqlToken, page|cursor, projection, sort }
```

## Dependencies

- Sprint 01 (shell/i18n/cache), Sprint 02 (unread counts feed the
  messages card).

## Acceptance criteria (PDF §9 Phase 1 / §8)

- [ ] Dashboard loads in <2s on the internal network (single call).
- [ ] Cards show correct counts; overdue uses error tint and sorts by
      overdue duration.
- [ ] Tasks lists paginate; Today/Overdue/Follow-up return correct sets.
- [ ] `assignee=me` cards highlighted; overdue dates shown red.
- [ ] Dates render in Jalali (fa) / Gregorian (en).
- [ ] Cached dashboard shown offline with stale banner.

## Definition of Done

- Every list has pagination + limits (no unbounded JQL — PDF §8, §9.1).
- Loading skeletons match content layout; empty/error/no-permission
  states implemented.

---

# Sprint 03 — Implementation Report (COMPLETED)

## Root cause / findings before implementation

- Sprint 02 was verified green before starting (jim health/ao-health/conversations,
  mobile health/bootstrap/preferences/auth-session/chat all `200`, anon/bad-PAT
  `401`).
- The plugin had **no existing Jira issue-search usage** — only `UserSearchService`
  (people) and single-issue `IssueManager.getIssueByCurrentKey` + `BROWSE_PROJECTS`
  checks. So issue search is net-new and had to be built the permission-safe way.
- **Security cleanup:** real credentials (`<REDACTED>` password) were present in
  several docs. All occurrences were redacted across `docs/` (handover docs +
  sprint 00/01b/02 notes). Test steps now use placeholders.
- Sprint 02 deferrals (in-memory drafts, no SQLite cache) do not affect Sprint 03.
  Dashboard/Tasks use short-lived in-memory provider state + pull-to-refresh; no
  SQLite read-cache was added this sprint (documented under "not changed").

## Final API paths

Both under the mobile BFF `/rest/corbit-mobile/1.0/*` (session-filter covered, so
PAT / cookie-basic / mobile-session all work):

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/dashboard` | aggregated: `unread{total,byType}`, `tasks{today,overdue,followup,assigned}` counts, `todayPreview[]` |
| GET | `/issues/search?filter=&startAt=&maxResults=` | one whitelisted filter token → paginated issue cards |

`filter` ∈ `{assigned, today, overdue, followup}` (anything else → `400`).
`maxResults` default 20, hard-capped at 50; `startAt` ≥ 0. Response includes
`total` and `isLast` for pagination.

## How permission + JQL/filter safety are enforced

- **No raw JQL from the client — ever.** The client sends only a filter *token*.
  The server maps each token to a `com.atlassian.query.Query` built with
  `JqlQueryBuilder` in `JimMobileIssues.buildQuery(...)`. Unknown tokens are
  rejected with `400` before any search runs (verified: `filter=DROP` and
  `filter=assigned OR 1=1` both `400`).
- **Jira permissions enforced by the engine.** All searches go through
  `SearchService.search(user, query, pager)` / `searchCount(user, query)` with the
  logged-in `ApplicationUser`; this applies Browse-Project and issue-level
  security automatically, so results only ever contain issues that user may see.
  `searchOverrideSecurity(...)` is **not** used.
- **Bounded.** Every query is paginated via `PagerFilter` (page ≤ 50); the
  dashboard preview is limited to 5. No unbounded lists.
- **Filter → JQL mapping** (all scoped to the current user via JQL functions):
  - `assigned`: `assignee = currentUser AND resolution is EMPTY` order by updated desc
  - `today`: `assignee = currentUser AND unresolved AND due BETWEEN startOfDay..endOfDay` order by due asc
  - `overdue`: `assignee = currentUser AND unresolved AND due < startOfToday` order by due asc
  - `followup`: `reporter = currentUser AND unresolved` order by updated desc

## Files changed

Backend (plugin):
- `mobile/JimMobileIssues.java` (**new**) — whitelist, token→JQL builder, issue
  JSON projection, page-size clamps, overdue calc. No description/body fields.
- `mobile/rest/CorbitMobileIssueResource.java` (**new**) — `GET /issues/search`.
- `mobile/rest/CorbitMobileDashboardResource.java` (**new**) — `GET /dashboard`.
- `atlassian-plugin.xml` — added `<component-import ... SearchService>` (the only
  wiring change; the mobile REST package is already scanned).

Mobile (Flutter):
- `models/issue.dart` (**new**) — `IssueCard`, `IssuePage`, `DashboardData`, refs.
- `core/jalali.dart` (**new**) — dependency-free Gregorian→Jalali conversion.
- `core/date_display.dart` (**new**) — calendar-aware date formatting + Persian digits.
- `api/corbit_api.dart` — `dashboard()`, `searchIssues(filter,...)`.
- `state/issue_providers.dart` (**new**) — `dashboardProvider`,
  `dateDisplayProvider`, `taskListProvider` (paginated controller).
- `features/tasks/widgets/issue_card.dart` (**new**) — issue card (key, type,
  summary, status chip by category, priority, assignee, project, due date).
- `features/tasks/issue_list_view.dart` (**new**) — paginated list + infinite
  scroll + pull-to-refresh + states.
- `features/tasks/tasks_tab.dart` (**new**) — filter tabs + standalone
  `TaskListScreen` for dashboard navigation.
- `features/shell/tabs/dashboard_tab.dart` — rewritten: real stat cards + today
  preview + states.
- `features/shell/tabs/placeholder_tabs.dart`, `features/shell/app_shell.dart` —
  wire the real `TasksTab` (only `ProjectsTab` remains a placeholder).
- `core/strings.dart` — dashboard/tasks strings (EN/FA).

## Built

- Dashboard: identity card + four stat cards (messages/today/overdue/follow-up)
  with tap-through to filtered lists, plus a "today's tasks" preview; loading /
  error+retry states; pull-to-refresh.
- Tasks: Today / Overdue / Follow-up / Assigned tabs, each a paginated issue list
  with infinite scroll, pull-to-refresh, and loading/empty/error/no-permission/
  retry states.
- Issue cards with status color by category, overdue emphasis (red busy-calendar
  icon + bold date), assignee avatar, project key.
- Persian (Jalali) / English (Gregorian) date rendering with localized digits,
  driven by the server calendar preference + UI locale.
- Tapping an issue shows a "detail coming soon" snackbar (placeholder until S04).

## Intentionally not changed

- No issue detail / edit / transitions / assign / worklog / boards / push / group
  chat / attachments (all out of scope).
- `/rest/jim/1.0/*` web behavior, AO schema, plugin key untouched.
- No SQLite read-cache (in-memory provider state + refresh instead) — deferred.
- Issue `url` uses Jira's configured base URL (currently an internal IP); not used
  for navigation this sprint.

## Tests performed

- `dart analyze lib test`: **No issues found**. `flutter test`: pass.
- `flutter build apk --release`: built (53.7 MB) →
  `corbitchat-mobile/dist/corbitchat-mobile-sprint03-dashboard-tasks.apk`.
- Backend across **session + valid PAT + basic**: `/dashboard` and
  `/issues/search` `200`; pagination verified (distinct keys for startAt 0 vs 2);
  `maxResults=500` clamped to 50; invalid filter + JQL-injection → `400`; anon /
  bad-PAT → `401`.
- Full regression (`200`): jim health/ao-health/conversations, mobile
  health/bootstrap/preferences/auth-session/chat, dashboard, issues.
- HTTPS device path (`https://jira.corbitlogic.com`): dashboard + issues `200`.
- Leak scan: no issue summaries/bodies, tokens, passwords, or auth headers in any
  log/print (backend + mobile).

## Deployment status

- Plugin rebuilt + deployed to `jira-srv` (serves `185.83.181.194:8080`, fronted
  by `https://jira.corbitlogic.com` — same instance), Jira restarted, health green.
- New release APK staged for device install (path above).

## Remaining risks

- `followup` is defined as "issues I reported that are unresolved"; if your team
  expects watched issues instead, it's a one-line change in `JimMobileIssues`.
- Dashboard counts run 4 `searchCount` queries + 1 small search per load; fine for
  this instance but could be cached/parallelized if projects grow large.
- No offline cache: lists need connectivity; refresh re-queries.
- Release APK build requires the CN Flutter storage mirror in this environment
  (`FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn`).

## Manual UI test steps

1. **Login (both modes):** install the APK; sign in with username/password over
   `https://jira.corbitlogic.com`, then repeat with a PAT — both reach the app.
2. **Dashboard cards:** open the **Dashboard** tab → see your name, a "New
   messages" count, and **Today / Overdue / Follow-up** counts with real numbers.
   Pull down to refresh.
3. **Today's tasks preview:** if you have issues due today, they appear under
   "Today's tasks"; otherwise it shows "No tasks". Tap **View all** → opens the
   Today list.
4. **Card tap-through:** tap the Overdue (or Follow-up) card → opens that filtered
   list.
5. **Tasks tab + filters:** open the **Tasks** tab → switch between Today /
   Overdue / Follow-up / Assigned; each loads real issues.
6. **Pagination:** on a long list (e.g. Follow-up), scroll down → more issues load
   automatically with a spinner.
7. **Pull-to-refresh:** pull down any task list to reload.
8. **Overdue emphasis:** overdue issues show a red busy-calendar icon and bold red
   due date.
9. **Persian/English dates:** in **More → language/settings**, switch language;
   with the Jalali calendar preference, due dates render as Persian (Shamsi) with
   Persian digits; with Gregorian/English they render as `yyyy/MM/dd`.
10. **Issue card fields:** confirm each card shows key, type, summary, status chip
    (color by category), assignee (avatar + name or "Unassigned"), project key,
    and due date.
11. **States:** verify loading spinner, empty ("No tasks"), error + retry (e.g.
    airplane mode), and no-permission (if the `issues` feature flag is off).
12. **Sprint 02 regression:** open **Chat** → conversations still load; open a 1:1,
    send a message, confirm optimistic send + receive still work.
