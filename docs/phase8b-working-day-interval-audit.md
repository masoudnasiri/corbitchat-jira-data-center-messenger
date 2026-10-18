# Phase 8B — Interval-Unit Implementation Audit

**Date (UTC):** 2026-09-11. No coding before this audit.

## 1. Where interval units are defined
- **Java constant:** `JimAdminSettingsServiceImpl.INTERVAL_UNITS = Arrays.asList("minutes","hours")` (line ~55). Used by validation (`reportIntervalUnit_*` → must be in INTERVAL_UNITS, ~1037) and `unitValue()` fallback→`hours` (~1149).
- **Admin UI:** `admin.vm` dynamic level rows (Phase 8A) build the unit `<select>` in `jim-admin.js` `levelRowHtml()` with `hours`/`minutes` options.
- **Settings storage:** flat key `reportIntervalUnit_<levelKey>` (levelKey = legacy code or `o<optionId>`).
- **Config resolver:** `JimReportConfig.scheduleForLevel(code)` converts to minutes: `intervalMinutes = "hours".equals(unit) ? intervalValue*60 : intervalValue` (~637). `days` currently would fall through and be mis-treated as minutes → must be handled.

## 2. Current supported units
`minutes`, `hours`. No hidden/legacy units.

## 3. Interval→time conversion
`ReportSchedule.intervalMinutes` drives bucket generation. minutes→value, hours→value*60.

## 4/5. Working-day handling + calendar source (already exists — Phase: report-calendar)
`ReportWorkingCalendarService.classifier(tz, issue)` returns a `JimReportCalculator.DayClassifier` (`isWorkingDay(dayStartMs)`), resolved from: `jira_board_first` (GreenHopper board weekdays + non-working dates), `plugin_calendar_only`, `plugin_calendar_plus_board_holidays`, or `time_tracking_fallback`. Holidays/non-working days ARE already excluded. Timezone = report timezone (`JimReportConfig.reportTimeZone()` / Jira default). **This is the working-day source Phase 8B will reuse — no new calendar service needed.**

## 6/7. Count modes + interval usage
`JimReportCalculator.buildBuckets` walks day-by-day from creation/scored-start to `genEnd`, **skipping non-working days** (`dayClassifier.isWorkingDay(cursorDay)`), and within each working day emits buckets every `intervalMinutes` inside `[windowStart,windowEnd]`. Modes:
- `daily`: `genEnd`=end of today's window, `firstDayAnchor`=now (current active day only).
- `cumulative_due`/`cumulative_done`/`cumulative_earlier`: `genEnd`=periodEnd (due/done/earlier) or now; buckets across all working days.
- `fixed_minimum`: handled in `JimReportServiceImpl.computeFixedMinimum` (uses `minCountForLevel`, not intra-day buckets); Phase 7/8 correction = no missed before due/end. Interval unit is largely irrelevant to fixed_minimum.

## 8. Out-of-window
Reports not matching any bucket → `OUT_OF_WINDOW`, or `OUT_OF_SCHEDULE` (non-working day / paused), or `OUT_OF_SCOPE` (before enforcement start). Buckets are `[start,end)` matched by report timestamp (`findBucket`). Earliest report in a bucket = valid; later = duplicate.

## 9. Metrics
Dashboard/Reports use unified engine fields (`JimReportSummary`) — generic; no unit-specific branching (`levelCode==null` checks only).

## 10. Mobile/MCP/dashboard DTOs
Report DTOs expose `levelText`/`levelCode` + counters; **interval unit is NOT exposed** to mobile/MCP for display or parsing. → **Mobile unchanged**; Gateway unchanged (interval unit is a plugin-internal admin setting).

## Design for unit=days (working days)
- `days` means **working days** (not 24h, not calendar). Reuse `ReportWorkingCalendarService` classifier.
- Add `int workingDayInterval` to `JimReportCalculator.Input` and `ReportSchedule`; `scheduleForLevel` sets `workingDayInterval=N`, `intervalMinutes=0` when `unit=days`.
- Calculator: when `workingDayInterval>0`, use a **separate** `buildWorkingDayBuckets` that walks working days, groups them into N‑working‑day intervals, and emits ONE bucket per interval. Decouple validity span from the required anchor via a new `Bucket.requiredSinceMs`:
  - `bucket.start` = window start of the interval's 1st working day (validity span start; early reports count).
  - `bucket.end` = window end of the interval's Nth working day = **completion** (validity span end).
  - `bucket.requiredSinceMs` = completion end → the bucket counts toward requiredSoFar/missed only after N working days have elapsed (matches: 6 elapsed→0 required, 7 elapsed→1). Forward-walk (bounded) resolves the completion day even when it is in the near future so an in-progress interval is fillable but not yet missed.
- `compute()` elapsed/nextExpected use `requiredSinceMs ?? start`; bypass the `intervalMinutes<=0` guard when `workingDayInterval>0`. All existing paths unchanged when `workingDayInterval==0`.
- `fixed_minimum` unchanged (no missed before due/end). `daily`+days is not meaningful → days applies to interval-based (cumulative) modes; daily keeps current behavior.
- Fingerprint already includes the level's schedule signature; extend to include the unit/workingDayInterval so a unit change recomputes.
- Validation: add `days` to `INTERVAL_UNITS`; integer ≥1 for days.
