# Sprint 04E — Date Correctness: Jalali Display & Proposed Completion Date Fallback

Status: **complete**, deployed to **test** and **production** (plugin
`1.0.0-mobile-s04e`, APK `releases/corbithub-s04e-dates.apk`).

Fixes three real, product-correctness date bugs found during mobile testing and
adds an "effective due date" fallback so issues that only carry the custom
"تاریخ انجام پیشنهادی" date are still surfaced with a deadline.

---

## Root causes

1. **Impossible Jalali years (e.g. 3358 instead of 1405).**
   `lib/core/jalali.dart` `Jalali.fromGregorian` used a corrupted variant of the
   Borkowski algorithm — it subtracted a bogus epoch (`226894`) and started the
   year at `979` with the wrong `jNp` scaling. For `2026-06-24` it produced
   `3358/01/18` instead of `1405/04/03`. **Client-side bug only.**

2. **Raw timestamp shown for "تاریخ انجام پیشنهادی" (`1782921600000`).**
   `JimMobileIssueDetail.stringifyValue` mapped any `Date` custom-field value to
   `String.valueOf(date.getTime())` — i.e. epoch milliseconds — and the client
   printed that string verbatim. **Backend contract bug.**

3. **No effective-due-date fallback.** Issues with an empty standard Due Date but
   a set proposed-completion date showed "No due date" and never appeared in
   Today/Overdue.

Contributing risk addressed: calendar-day fields (Due Date, date-only custom
fields) were serialized as epoch millis, which can shift ±1 day when the device
timezone differs from the server. They are now day-strings.

## Discovered field

| Property | Value |
|---|---|
| Name | `تاريخ انجام پيشنهادي` (Arabic yeh/kaf glyphs in Jira) |
| Id (production) | `customfield_11309` |
| Type | `com.atlassian.jira.plugin.system.customfieldtypes:datepicker` (date-only) |
| Present on | **production only** — the test instance has no such field |

The id is **not** hard-coded as a requirement. It is resolved by *normalized
field name* (Arabic ي/ی and ك/ک unified, zero-width marks dropped, whitespace
collapsed) among date-typed custom fields; `customfield_11309` is only a
documented fast-path. Related fields on prod: `customfield_11308`
"تاريخ انجام تاييد شده" (approved completion) and the 11305–11307 approval fields
— left untouched.

## Date serialization contract

Two kinds of dates, never mixed:

- **Calendar-day fields** — standard Due Date, `effectiveDueDate`, and date-only
  custom fields — are serialized as an ISO **`yyyy-MM-dd`** string computed in the
  Jira server zone. The client renders that exact day (Jalali or Gregorian) with
  no timezone math, so a due date can never shift by a day on the device.
- **Instant fields** — created / updated / resolved, comment / worklog /
  attachment times, `serverTime` — stay **epoch milliseconds** and render in the
  device's local zone (time is meaningful).

Date-typed custom fields now carry a typed shape in `customFields[]`:

```json
{ "id": "customfield_11309", "name": "…", "valueType": "date",
  "date": "2026-07-02", "value": "2026-07-02" }
```

`valueType` is `date` (with `date`) or `datetime` (with `epochMs`). `value` keeps
a readable ISO fallback so even an older client never shows raw milliseconds.

## Effective due date

Computed server-side in `JimMobileDates`:

```
effectiveDueDate       = dueDate ?? proposedCompletionDate   (yyyy-MM-dd)
effectiveDueDateSource = "dueDate" | "proposedCompletionDate" | null
overdue                = resolution == null && effectiveDueDate < today (server zone)
```

Emitted on both issue **cards** (`JimMobileIssues.toIssueMap`) and issue
**detail** (`JimMobileIssueDetail`). Because the dashboard and tasks list both
funnel through `toIssueMap` + `buildQuery`, one backend change covers cards,
lists, dashboard previews and counts.

## Today / Overdue with the fallback

`startOfDay()` / `endOfDay()` JQL functions compare **unreliably** against custom
date-picker fields (verified on prod: `cf[11309] < startOfDay()` returned 0 for a
clearly-overdue value, while `cf[11309] < "2026-07-03"` returned it correctly).
So the fallback filters use a small, **fully server-owned** JQL string parsed via
`SearchService.parseQuery`, built only from a validated `cf[<numericId>]` clause
and a server-formatted date (never client input):

```
overdue: assignee = currentUser() AND resolution is EMPTY AND
         (duedate < "<today>" OR (duedate is EMPTY AND cf[<id>] < "<today>"))
today:   assignee = currentUser() AND resolution is EMPTY AND
         (duedate = "<today>" OR (duedate is EMPTY AND cf[<id>] = "<today>"))
```

When the field is absent (e.g. test) or parsing fails, it falls back to the
original duedate-only `JqlQueryBuilder` path. The mobile app still sends only a
filter *token* — no raw JQL ever crosses the boundary.

Verified on production: overdue went **0 → 3** (`TEC-34`, `TEC-31` via Due Date;
`COR-6` via the proposed date), dashboard `overdue` count matches.

## Files changed

Backend:
- `mobile/JimMobileDates.java` **(new)** — date contract, name-normalized field
  resolution, `toYmd`, effective-due calc, `cf[id]` clause.
- `mobile/JimMobileIssues.java` — cards emit `dueDate`/`effectiveDueDate`/
  `effectiveDueDateSource` as ymd; `buildQuery(user, searchService, filter)`
  overload with the literal-date fallback JQL.
- `mobile/JimMobileIssueDetail.java` — ymd due dates + effective due date; typed
  date custom fields (no raw millis); defensive `stringifyValue` date branch.
- `mobile/rest/CorbitMobileIssueResource.java`,
  `mobile/rest/CorbitMobileDashboardResource.java` — use the fallback-aware
  `buildQuery`.
- `pom.xml` — version `1.0.0-mobile-s04e`.

Mobile:
- `lib/core/jalali.dart` — correct Borkowski `fromGregorian`.
- `lib/core/date_display.dart` — `dateYmd(String?)` calendar-day formatter.
- `lib/models/issue.dart`, `lib/models/issue_detail.dart` — `dueDate` → ymd
  string, `effectiveDueDate`/source, typed custom date fields; tolerant `asYmd`.
- `lib/features/tasks/widgets/issue_card.dart` — cards show effective due date
  with a subtle "proposed" hint.
- `lib/features/tasks/issue_detail_screen.dart` — Due row uses ymd; shows
  "Due (proposed)" when only the proposed date exists; custom date fields render
  as real localized dates.
- `lib/core/strings.dart` — `dueProposed`, `dueProposedLabel`.
- `test/date_display_test.dart` **(new)** — Jalali + formatter regression tests.

## Tests performed

- Flutter `analyze` clean; `test/date_display_test.dart` 7/7 pass (incl.
  `2026-06-24 → 1405/04/03`, Nowruz, no-impossible-year, round-trip).
- Backend `mvn -o clean package` green.
- Prod: `effectiveDueDate`/source correct across assigned/overdue; `customfield_11309`
  serialized as typed date `2026-07-02` (no millis); leak scan across 4 issues
  found **no** raw-timestamp values; overdue count 3.
- Test + prod smoke (health, ao-health, bootstrap, dashboard, chat/conversations,
  projects, boards, preferences, issues/search, web chat page) all **200**.

## Deployment

- Rollback preserved: `TEST` s04d jar in `/root/rollback-s04e/`; prod DB dump +
  full plugin dir in `corbit-prod:/root/jira-backups/…-20260702-171120`; previous
  jars in `/root/jira-dev/releases/`.
- Path B file install + restart on both `jira-srv` (test) and prod. Plugin key
  unchanged, no AO schema change.

## Remaining risks

- Field resolution is name-based; if an instance renames "تاریخ انجام پیشنهادی"
  the fallback silently stops (degrades to Due-Date-only). Documented.
- `toYmd` uses the JVM default zone (verified `+0800` on both servers via
  `serverInfo`), which matches how Jira stores datepicker midnights.
- Old installed APKs will read the new `dueDate` (ymd string) as "no due date"
  (graceful) — ship this sprint's APK.

## Manual verification steps

Persian Jalali dates:
1. Settings → Persian UI. Open Tasks → Overdue. `TEC-34`/`TEC-31` show a Jalali
   due date (~۴/۱۴۰۵), **not** year ۳۳۵۸, with Persian digits.

English Gregorian dates:
2. Switch to English UI → same issues show `2026/06/25` etc.

Proposed completion date:
3. Open `COR-6`. Details show "تاریخ انجام پیشنهادی" as a readable date
   (`2026-07-02` in EN, `۱۱ تیر ۱۴۰۵` in FA) — never `1782921600000`.

Due-date fallback:
4. `COR-6` has no standard Due Date; its card shows the proposed date (with a
   subtle "proposed"/"پیشنهادی" hint) instead of "No due date". Detail shows a
   "Due (proposed)" row.

Today / Overdue:
5. Overdue list includes `COR-6` (effective date before today); dashboard
   "overdue" count is 3.

Regression:
6. Login (password + PAT), remembered login, chat send/receive, dashboard,
   tasks, issue detail, projects, Board Gallery, avatars, `customfield_10903`
   colored pills all still work; backend smoke green on test + prod.
