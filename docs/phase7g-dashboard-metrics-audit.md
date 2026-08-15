# Phase 7G — Dashboard metrics audit

DEV-only. Production stays on `figmachat7f-backend1` / PWA `+99`.
Source: plugin `CorbitMobileDashboardResource`, mobile `dashboard_tab.dart`,
`/issues/reports`, `JimMobileIssues` filters, Figma Dashboard frame.

## Contract (stable)

Task numbers and report numbers must not be mixed.

| Kind | Allowed sources | Forbidden |
|------|-----------------|-----------|
| Task cards / chips | Permission-aware issue JQL (`openWorkCounts`, `tasks.*`) | Report engine, chat unread |
| `گزارش‌ها` card | Report engine (`JimReportSummary.statusCode` and today buckets) | Jira `statusCategory`, task due-date JQL |
| Chat preview | Conversation unread / last message | Issues or reports |

If a report metric cannot be computed safely, return `available: false` or
omit the field. Do not copy a task count into the reports card.

---

## Top Dashboard numbers (`کارهای باز من`)

### 1. `N کار فعال` (active tasks)

| | |
|--|--|
| UI label | `کارهای باز من` / `$n کار فعال` |
| Backend field | `openWorkCounts.active` |
| Mobile mapping | `DashboardData.openActive` → `owActive` |
| Intended meaning | Issues assigned to the current user that are not Done |
| Actual calculation | `assignee = currentUser() AND statusCategory != Done` via `searchCount` |
| Kind | Task-based |
| JQL / source | Server-owned JQL; issue security enforced |
| Verdict | **Correct.** Keep. |
| Verify | Compare with Jira issue navigator for the same JQL as the signed-in user |

Fallback (older plugin, unused on current DEV): `statusInProgress + statusTodo`,
then `tasks.assigned` (`resolution IS EMPTY`). Documented; not shown when
`openWorkCounts` is present.

### 2. Chip `عقب‌افتاده`

| | |
|--|--|
| UI label | `عقب‌افتاده` |
| Backend field | `openWorkCounts.overdue` |
| Mobile mapping | `DashboardData.openOverdue` |
| Intended meaning | Assigned, not-Done issues whose effective due date is before today |
| Actual calculation | `assignee = currentUser() AND statusCategory != Done AND (duedate < today OR (duedate is EMPTY AND proposed-completion < today))` |
| Kind | Task-based |
| Verdict | **Correct.** Keep. |
| Verify | Navigator: assigned + not Done + due before today (plus proposed-date fallback) |

Tap opens Tasks filter `overdue`, which still uses `resolution IS EMPTY` rather
than `statusCategory != Done`. Counts and list can differ by Done-unresolved
issues. That list-filter mismatch is **noted, not changed** (task filters stay).

### 3. Chip `امروز`

| | |
|--|--|
| UI label | `امروز` |
| Backend field | `openWorkCounts.today` |
| Mobile mapping | `DashboardData.openToday` |
| Intended meaning | Assigned, not-Done issues due today (Due Date, else proposed completion) |
| Actual calculation | Same as overdue with `=` today |
| Kind | Task-based |
| Verdict | **Correct.** Keep. |
| Verify | Navigator: assigned + not Done + due = today |

Same list-filter caveat as overdue (`resolution IS EMPTY` on the Tasks screen).

### 4. Chip `پیگیری`

| | |
|--|--|
| UI label | `پیگیری` |
| Backend field | `openWorkCounts.followup` |
| Mobile mapping | `DashboardData.openFollowup` |
| Intended meaning | Open issues the current user **reported** (follow-up / chasing) |
| Actual calculation | `reporter = currentUser() AND statusCategory != Done` |
| Kind | Task-based (reporter, not assignee) |
| Verdict | **Correct** as the existing follow-up filter. Not a subset of active assigned work; chips must not be summed to `کار فعال`. |
| Verify | Navigator: reporter = currentUser, statusCategory != Done |

This is **not** follow-up *level* (`followUpLevel` on task cards). Different
concept; both are real.

### 5. Legacy `tasks.*` (not shown as headline)

| Token | JQL (via `JimMobileIssues`) | Shown on Dashboard? |
|-------|-----------------------------|---------------------|
| `assigned` | assignee + `resolution IS EMPTY` | Fallback only |
| `today` / `overdue` | assignee + unresolved + effective due | Fallback only |
| `followup` | reporter + unresolved | Fallback only |
| `awaiting` / `rejected` | schedule-approval custom fields | Counted in `tasks` map; **not** rendered on the Dashboard home cards |

`awaiting` / `rejected` are schedule-approval queues, not report statuses.
They appear on the Tasks tab, not as top Dashboard numbers.

---

## Other Dashboard blocks (not top numbers)

| UI | Backend | Kind | Verdict |
|----|---------|------|---------|
| هویت / برند | bootstrap user | identity | Correct |
| `اولویت‌های امروز` | `todayPreview` (filter `today`, page 5) | Task | Correct; empty list is honest |
| `فعالیت اخیر` | `recentActivity` (assignee OR reporter, `updated >= -14d`, page 8) | Task | Correct; omitted when empty |
| `تاییدهای امروز` | `scheduleApprovals` | Schedule-approval | Correct; hidden when count 0 |
| Recent chats | conversations provider | Chat | Correct; not from `/dashboard` counts |
| Unread (payload) | `unread.total` / `byType` | Chat | Present on `/dashboard`; not a top numeric card |

`statusCounts` (`done` / `inProgress` / `todo` / `total`) is a **task**
statusCategory breakdown for assigned issues. It remains on the payload for
compatibility. It must **not** feed `گزارش‌ها`.

---

## Card `گزارش‌ها` — root cause

**Yes: this card currently used task data by mistake.**

| | Before 7G | After 7G |
|--|-----------|----------|
| UI label | `گزارش‌ها` | unchanged |
| Backend fields used | `statusCounts.done`, `statusCounts.inProgress`, **`tasks.overdue`** (all-time); `/dashboard/report-status` (day/week/month) | `reportSummary.complete` / `onTrack` / `overdue` (+ today* for روزانه) |
| Mobile mapping | `_ReportsDonutCard`: `statusDone`, `statusInProgress`, `data.overdue` | `DashboardReportSummary` only |
| Intended meaning | Current user's **report** status | Same |
| Actual calculation (before) | Jira `statusCategory` = Done / In Progress for assigned issues; overdue = **task** due-date filter. `/report-status` was also assignee + statusCategory + `updated` window + task `duedate`. | Report engine `statusCode` COMPLETE / ON_TRACK / OVERDUE on assigned, report-configured issues |
| Kind (before) | **Task-based** | **Report-based** |
| Verdict (before) | **Wrong.** Label says reports; numbers are task statuses. All-time overdue even used `tasks.overdue`, not `statusCounts`. | Fixed on DEV |

Figma legend text (`انجام شده` / `در حال انجام` / `عقب افتاده`) looks like
task language. Product intent (owner): the card is report status. Mobile
labels are now `تکمیل‌شده` / `جاری` / `عقب‌افتاده`.

### Correct `گزارش‌ها` definition

Source: `JimReportService.computeSummary` on a **bounded** search:

```
assignee = currentUser() AND resolution is EMPTY
  AND cf[level] is not EMPTY   (when the follow-up field exists)
ORDER BY updated DESC
```

Cap: 25 issues (`REPORT_SCAN_CAP`). Same permission path as
`GET /issues/reports`. Issues that are not configured / not applicable are
skipped (same as the Reports tab).

| Donut segment | Engine field | Meaning |
|---------------|--------------|---------|
| تکمیل‌شده | `statusCode == COMPLETE` | Required valid reports met |
| جاری | `statusCode == ON_TRACK` | Submit still active, not overdue |
| عقب‌افتاده | `statusCode == OVERDUE` | Active reporting window overdue |

`ENDED_*`, `INACTIVE`, `NOT_CONFIGURED` are omitted (not invented as a fourth
slice).

**روزانه:** `todayComplete` / `todayOnTrack` / `todayOverdue` from
`todayValidCount`, `todayRequiredCount`, `todayMissedCount`, `reportedToday`,
`submitActive`.

**هفتگی / ماهانه:** `available: false`. The report engine has no week/month
buckets. UI shows an honest unavailable line. Not faked from `updated >= startOfWeek()`.

If the feature is off or no configured issues are in the scan:
`reportSummary.available = false` and counts are omitted.

### How to verify with real data

1. `GET /rest/corbit-mobile/1.0/dashboard` → `reportSummary.source` is
   `report-engine`; `statusCounts` must not equal `reportSummary` except by
   coincidence.
2. `GET /rest/corbit-mobile/1.0/issues/reports` → per-issue `statusCode`
   tallies should match `complete` / `onTrack` / `overdue` for the same
   bounded assigned set.
3. Open Tasks → Reports: current/overdue cards should agree with the donut
   for issues in the scan.
4. Unauthorized `/dashboard` → 401.

---

## Deferred (honest)

| Item | Why |
|------|-----|
| Week/month report donut | No engine buckets; would require inventing a date window on comments |
| Reports received / requested-from-me / awaiting-my-action as extra slices | No dashboard JQL for “reports received by me” that is distinct from the existing assigned+engine scan without new report-engine APIs |
| Uncapped exact totals for users with >25 report-configured assigned issues | `capped: true` is returned; do not pretend the scan is complete |
| Changing Tasks `overdue`/`today`/`followup` JQL to `statusCategory != Done` | Dashboard counts are already consistent; changing filters is out of scope unless those filters are proven wrong |
| Chat unread as a top number | Not on the Figma top row; payload already has `unread` |
