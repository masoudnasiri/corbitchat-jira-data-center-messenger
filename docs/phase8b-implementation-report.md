# Phase 8B — Working-Day "days" Interval Unit — Implementation Report

**Date (UTC):** 2026-09-11 · **Scope:** DEV-first, plugin-only. Production NOT deployed. No GitHub push.
**Plugin version:** `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2-8a-dynlevels1-8b-dayunit1`

## What was added
A new interval unit **`days` = WORKING days** in Report Settings. `interval=7, unit=days` means "one required report per **7 working days**" — weekends/holidays are skipped (they do not advance the counter), using the **existing** `ReportWorkingCalendarService` (Jira board working-days + holidays) and the report timezone. Not 24h, not calendar days. Phase 8A dynamic levels and the fixed-minimum correction are preserved.

## Changed files (all in the plugin)
| File | Change |
|---|---|
| `report/JimReportCalculator.java` | `Input.workingDayInterval`; `Bucket.requiredSinceMs` (+`requiredSince()`); guard bypass when working-day mode; elapsed/nextExpected use `requiredSince()`; new `buildWorkingDayBuckets()` (one bucket per N working days, span = interval, required-since = completion). |
| `report/JimReportConfig.java` | `ReportSchedule.workingDayInterval` (+4-arg ctor); `scheduleForLevel()` maps `unit=days` → `workingDayInterval=N, intervalMinutes=0`. |
| `report/JimReportServiceImpl.java` | Pass `sch.workingDayInterval` into `Input`; fingerprint includes `/wd<N>`. |
| `service/JimAdminSettingsServiceImpl.java` | `INTERVAL_UNITS += "days"`; clearer invalid-unit error message. |
| `resources/js/jim-admin.js` | Unit `<select>` adds `days / روز` (with working-day tooltip). |
| `resources/templates/admin.vm` | Bilingual help note: days = working days. |

## Semantics (PART 2)
- Walk working days from creation (or enforcement start) via `dayClassifier`; non-working days do not count.
- Group working days into intervals of N. Bucket k spans `[firstWorkingDayWindowStart, NthWorkingDayWindowEnd)` and becomes **required** only at completion (Nth working day) — so an early report during the interval is valid, and no "missed" is created before N working days elapse.
- `requiredSoFar = floor(elapsedWorkingDays / N)`; `missed = requiredSoFar − validSoFar`.
- Composes with pause (assigned-to-receiver), enforcement scoring start, and `cumulative_*` period ends. `fixed_minimum` is unchanged (interval-agnostic). `daily`+days falls back to cumulative-style working-day intervals.

## Verification
### Offline calculator harness (the real production class) — 8/8 OK
- N=7 all-working: 6 elapsed→req 0/missed 0; 7 elapsed→1/1; 14 elapsed→2/2; early report in interval0→valid, not missed.
- N=5 weekends-skipped: 1 working week→1/1; weekend does not advance the counter; 2 weeks→2/2; weekend report does not satisfy an interval.

### Live DEV (node `jira-srv`, `com.corbitlogic.corbitchat.jira.dc`)
- Deployed `…8b-dayunit1`; health/ao-health/chat = 200; deployed `jim-admin.js` bundle contains `days / روز`.
- Settings REST: `unit=days` **accepted + persisted**; bogus `unit=weeks` **rejected (400)**; diagnostics `followUpLevels` reflects `unit=days`.
- Working-day source live: `jira_board`, `daysPerWeek=5`, Mon–Fri, `holidayCount=1`.
- **End-to-end engine:** set `normal` → `cumulative_due, unit=days, interval=7`, recalc. Issue **TEST-38** (created 2026-08-17, due 2027-12-31): `totalRequiredReportsUntilEnd=52` (≈364 working days ÷ 7 ✓) and `currentMissedReportStreak=2` (≈18–19 working days elapsed ÷ 7 = 2 ✓), weekends excluded. DEV `normal` level restored to its original config afterward.

## Not changed (per constraints)
Mobile/PWA (interval unit is admin-only, not exposed to mobile/MCP DTOs), MCP Gateway, eazyBI, Inbox, Automation, `/mobile` route, Active Objects schema, plugin key, REST endpoints. No JARs committed; no GitHub push; no secrets in docs.
