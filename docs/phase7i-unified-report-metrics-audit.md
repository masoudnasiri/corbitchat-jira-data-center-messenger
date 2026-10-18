# Phase 7I — Unified report metrics audit

Date: 2026-08-16. **DEV only.** Production stays on
`figmachat7h-functional1` / PWA `+107`. Do not deploy Production.

Official plugin: `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane`  
Official mobile: `/root/jira-dev/corbitchat-mobile`

## Versions at audit start

| Surface | Version |
|---|---|
| DEV plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7h-functional1` (one jar) |
| DEV Hub PWA | `1.0.0+111 / figma-task-detail-person-rtl1` / brand `hub` |
| Production plugin | `figmachat7h-functional1` (**unchanged**) |
| Production PWA | `1.0.0+107 / figma-task-detail-person-spacing2-prod` / `sevengtech` |
| Gateway | `mcp-atlassian-prod` StartedAt `2026-07-31T23:35:19Z` (**do not change**) |

## Principle

For the same user and the same time range, Dashboard report counters and
Reports section counters must show the **same numbers** from **one** backend
function. The Reports section meaning is the trusted one: valid/correct and
missed/lost come from `JimReportService.computeSummary` (the report engine).
Dashboard must reuse that source. Mobile must not calculate report stats.

---

## Engine (single per-issue source)

`JimReportService.computeSummary(issue)` → `JimReportSummary`.  
Calculator: `JimReportCalculator`. Working calendar / enforcement affect
**scoring only**; this audit does not change those rules.

| Field | Meaning |
|---|---|
| `validCount` | Filled required windows (SATISFIED). Trusted “correct/valid”. |
| `missedCount` | `max(0, requiredSoFar - validSoFar)`. Trusted “missed/lost”. |
| `rawCount` | In-window submitted reports (first valid + duplicates). |
| `outOfWindowCount` | Submitted reports whose timestamp missed the window. |
| `requiredCount` | All schedule windows (including future). |
| `requiredSoFar` / `validSoFar` | Elapsed windows / filled elapsed windows. |
| `statusCode` | Issue-level `COMPLETE` / `ON_TRACK` / `OVERDUE` (not a report-instance count). |
| `todayRequiredCount` / `todayValidCount` / `todayMissedCount` | Windows whose **start** is today (viewer/report TZ). Same SATISFIED/MISSED/OPEN rules. |
| `windowLedger` | Per-window `SATISFIED` / `MISSED` / `OPEN` / `FUTURE` / `CLOSED_NOT_REQUIRED`. Today buckets are derived from this ledger. |
| `configured` / `applicable` / `levelCode` | Issue is in report scope. |

Fixed-minimum levels have an empty ledger; `today*` is copied from lifetime
counts (`applyFixedMinimumAggregates`). Week/month cannot slice those issues
without inventing a formula.

---

## Visible numbers

### 1. Dashboard «گزارش‌ها» donut — Completed reports

| | |
|--|--|
| UI label | `Completed reports` / `گزارش‌های تکمیل‌شده` (`donutDone`) |
| Backend field | `reportSummary.complete` (period `all`) or `todayComplete` / `report-status.done` (period `day`) |
| Formula | **Issue count** where `statusCode == COMPLETE` (lifetime) or today-complete heuristic (`todayValid >= todayRequired`, or reported today with no requirement) |
| Time range | `all` = engine lifetime; `day` = today windows; `week`/`month` = honest `available=false` |
| User-specific | Yes (`assignee = currentUser()`) |
| Kind | **Issue-based** (not report-instance) |
| Match Reports? | **No** |
| Why | Different metric (issue status vs summed `validCount`). Different issue set and cap (below). Mobile `DashboardReportSummary` never parses `validSubmitted`. |
| Required correction | Show engine `valid` from the shared snapshot. Stop using COMPLETE issue-counts as the Dashboard “valid” number. |

### 2. Dashboard donut — Reports in progress

| | |
|--|--|
| UI label | `Reports in progress` / `گزارش‌های در جریان` (`donutInProgress`) |
| Backend field | `reportSummary.onTrack` / `todayOnTrack` / `report-status.inProgress` |
| Formula | **Issue count** where `statusCode == ON_TRACK`, or today “submit active and still short” |
| Time range | Same as (1) |
| User-specific | Yes |
| Kind | **Issue-based** |
| Match Reports? | **No** — Reports has no “in progress” tile |
| Required correction | Do not keep this as a separate formula. Replace with a shared field (`onTrack` = still-open required windows in the period, from the same ledger). |

### 3. Dashboard donut — Overdue reports

| | |
|--|--|
| UI label | `Overdue reports` / `گزارش‌های عقب‌افتاده` (`donutOverdue`) |
| Backend field | `reportSummary.overdue` / `todayOverdue` / `report-status.overdue` |
| Formula | **Issue count** where `statusCode == OVERDUE`, or `todayMissedCount > 0` |
| Time range | Same as (1) |
| User-specific | Yes |
| Kind | **Issue-based** |
| Match Reports? | **No** — Reports “missed” is `sum(missedCount)` (windows), not overdue issues |
| Required correction | Show engine `missed` from the shared snapshot. |

### 4. Dashboard (sent but unused) — validSubmitted / missed

| | |
|--|--|
| UI label | None (not parsed) |
| Backend field | `reportSummary.validSubmitted`, `reportSummary.missed` |
| Formula | `sum(validCount)`, `sum(missedCount)` over the dashboard scan |
| Time range | Engine lifetime |
| User-specific | Yes |
| Kind | **Report-instance / window** |
| Match Reports? | **Closer, but still no** — same engine fields, different issue set (level JQL + cap 25 vs assigned first 10) |
| Required correction | These are the trusted fields. Both surfaces must use the same scan + same period. |

### 5. Reports gauge — Total

| | |
|--|--|
| UI label | `Total` / `تعداد کل` (`reportTileTotal`) |
| Backend field | `aggregate.total` |
| Formula | `sum(rawCount)` over the **current page** of `/issues/reports` |
| Time range | Engine lifetime (no period param) |
| User-specific | Yes (`assigned` filter) |
| Kind | **Report-instance** (in-window submissions) |
| Match Dashboard? | **No** |
| Required correction | Same shared snapshot. Aggregate must be the full scan, not the page. |

### 6. Reports gauge — Valid

| | |
|--|--|
| UI label | `Valid` / `معتبر` (`reportTileValid`) |
| Backend field | `aggregate.valid` |
| Formula | `sum(validCount)` over the current page |
| Time range | Engine lifetime |
| User-specific | Yes |
| Kind | **Window / report-instance** (trusted) |
| Match Dashboard? | **No** (Dashboard shows COMPLETE issues; mobile ignores `validSubmitted`) |
| Required correction | Shared `reportMetrics.valid`. |

### 7. Reports gauge — Out of window

| | |
|--|--|
| UI label | `Out of window` / `خارج از بازه` (`reportTileOutOfWindow`) |
| Backend field | `aggregate.outOfWindow` |
| Formula | `sum(outOfWindowCount)` over the current page |
| Time range | Engine lifetime |
| User-specific | Yes |
| Kind | **Report-instance** |
| Match Dashboard? | **No** — Dashboard does not show this |
| Required correction | Include `outOfWindow` on the shared contract and on Dashboard. |

### 8. Reports aggregate — Missed (JSON only)

| | |
|--|--|
| UI label | None on the gauge (issue-detail uses `گزارش‌های عقب‌افتاده`) |
| Backend field | `aggregate.missed` |
| Formula | `sum(missedCount)` over the current page |
| Time range | Engine lifetime |
| User-specific | Yes |
| Kind | **Window** (trusted missed/lost) |
| Match Dashboard? | **No** |
| Required correction | Show as a tile on both surfaces from `reportMetrics.missed`. |

### 9. Reports per-card tiles

Same three fields as the gauge (`rawCount` / `validCount` / `outOfWindowCount`)
per issue. Tappable cards open issue detail. Gauge tiles already drill down via
`_openMetricSheet` (issues on the **loaded page** with that count > 0).

### 10. Issue-detail Report tab tiles

| UI | Field | Source |
|---|---|---|
| Total | `rawCount` | `GET /issues/{key}/report` → `computeSummary` |
| Valid | `validCount` | same |
| Out of window | `outOfWindowCount` | same |
| Missed (kv) | `missedCount` | same |

Per-issue, same engine. Not an aggregate. No change required unless a counter
bug is proven.

### 11. `/dashboard/report-status`

| Period | Behavior |
|---|---|
| `all` | Remaps `complete`/`onTrack`/`overdue` → `done`/`inProgress`/`overdue` (legacy names) |
| `day` | Remaps `todayComplete` / `todayOnTrack` / `todayOverdue` |
| `week` / `month` | `available=false`, zeros. Comment: engine has no precomputed week/month buckets |

---

## Why Dashboard ≠ Reports (do not paper over)

1. **Different metric.** Dashboard = issue `statusCode` buckets. Reports = summed engine counts (`valid` / `raw` / `outOfWindow` / `missed`).
2. **Different issue set.** Dashboard JQL: `assignee = currentUser() AND resolution is EMPTY` **and** follow-up level `is not EMPTY` (when the field exists), cap **25**. Reports: `JimMobileIssues` `assigned` (assignee + unresolved, **no** level clause), hard cap **10**, then skip `!configured` in Java. Aggregate is only over that page.
3. **Mobile drops** dashboard `validSubmitted` / `missed`.
4. **No shared period** on Reports (lifetime only). Dashboard week/month already honest-unavailable.
5. **No Dashboard drilldown.** Reports gauge tiles open `_openMetricSheet`.

This is a real counter-source bug, not a calendar bug. Do not change
submit/reply or working-calendar rules.

---

## Shared contract (implemented in this phase)

One service: `JimMobileReportMetricsService`.  
One scan: assigned + unresolved + level not empty (when configured), cap **25**.  
One `computeSummary` loop. Both `/dashboard` and `/issues/reports` call it.

```json
{
  "period": {
    "preset": "all|today|week|month|custom",
    "from": "YYYY-MM-DD",
    "to": "YYYY-MM-DD",
    "timezone": "Asia/Tehran"
  },
  "reportMetrics": {
    "available": true,
    "totalRequired": 0,
    "valid": 0,
    "missed": 0,
    "overdue": 0,
    "onTrack": 0,
    "outOfWindow": 0,
    "rawTotal": 0,
    "capped": false,
    "source": "report-engine"
  }
}
```

### Period support

| Preset | Safe? | How |
|---|---|---|
| `all` | **Yes** | Lifetime `validCount` / `missedCount` / `rawCount` / `outOfWindowCount` / `requiredSoFar`. Trusted Reports meaning. |
| `today` (`day`) | **Yes** | Engine `todayValidCount` / `todayMissedCount` / `todayRequiredCount`. Out-of-window / raw = report items whose `createdMs` falls on today. |
| `week` | **Yes, with documented range** | Same ledger classification as today. **Rolling last 7 calendar days** in the viewer Jira TZ (`today-6` … `today` inclusive). Not a Sat/Sun calendar week — first-day-of-week is locale-ambiguous. |
| `month` | **Yes** | Calendar month in viewer TZ: 1st of month … today inclusive. Ledger windows whose `windowStart` is in range. |
| `custom` `from`/`to` | **Yes** | Inclusive `YYYY-MM-DD` in viewer TZ, same ledger/item filter. |

Fixed-minimum issues have no ledger. They contribute to `all` and `today`
(engine already copies lifetime into `today*`). They contribute **0** to
week/month/custom window buckets (cannot slice without a new formula).
`capped=true` when Jira `issueTotal > 25`.

### Field mapping (trusted)

| Contract field | `all` | Ranged (`today`/`week`/`month`/`custom`) |
|---|---|---|
| `valid` | `sum(validCount)` | SATISFIED windows with start in range (`today*` for today) |
| `missed` | `sum(missedCount)` | MISSED windows with start in range |
| `totalRequired` | `sum(requiredSoFar)` | required windows in range (SATISFIED+MISSED+OPEN) |
| `overdue` | `sum(overdueOpenCount)` | OPEN windows with start in range |
| `onTrack` | `max(0, totalRequired - valid - missed - overdue)` | same |
| `outOfWindow` | `sum(outOfWindowCount)` | items with status `OUT_OF_WINDOW` and `createdMs` in range |
| `rawTotal` | `sum(rawCount)` | in-window items (`VALID`/`DUPLICATE`) with `createdMs` in range |

Dashboard UI shows **valid / missed / outOfWindow** (same meaning as Reports).  
Reports gauge shows **rawTotal / valid / missed / outOfWindow** from the same
object. Aggregate is the full scan, not the list page.

Legacy `complete` / `onTrack` / `overdue` issue-status fields remain on
`/dashboard` as unused extras from the **same scan** so old clients do not
invent task totals. New mobile ignores them for the donut.

### Drilldown

Dashboard legend taps open the same issue list pattern as Reports
`_openMetricSheet`: issues in the shared snapshot with that period count > 0.
Tap a row → issue detail Report tab. No second formula.

---

## Out of scope

- Report submit / reply / reactions
- Working calendar / enforcement rules
- Gateway, Workspace Agent
- Production deploy, GitHub push
- Task `openWorkCounts` / `statusCounts` (those stay task-based)
