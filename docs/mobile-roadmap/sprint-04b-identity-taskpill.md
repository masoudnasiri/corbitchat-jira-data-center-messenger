# Sprint 04B — Product Identity (CorbitHub) & customfield_10903 Colored Pill

Status: **Done — deployed to test and production.**

Small polish sprint before Sprint 05. Two goals:

1. Align the user-facing product identity to **CorbitHub — Mobile Workspace
   for Jira Data Center**.
2. Render the "task type" custom field (`customfield_10903`,
   "نوع تسک (طبق دستورالعمل)") as a **colored pill** everywhere it appears in
   the mobile issue/task UI.

Plugin release: `corbitchat-jira-dc-1.0.0-mobile-s04b.jar`
APK: `releases/corbitchat-mobile-s04b-release.apk`

---

## 1. Product identity (CorbitHub)

Only **user-facing** labels changed. Internal identifiers were intentionally
left untouched (see below).

| Where | Before | After |
|---|---|---|
| `lib/core/strings.dart` `appName` | CorbitChat | **CorbitHub** |
| `lib/core/strings.dart` `appDescriptor` (new) | – | Mobile Workspace for Jira Data Center / فضای کاری موبایل برای Jira Data Center |
| `lib/core/strings.dart` `loginTagline` | Your Jira chat & tasks | Mobile Workspace for Jira Data Center |
| `lib/core/strings.dart` `aboutBody` | CorbitChat companion app… | CorbitHub is your mobile workspace… |
| `lib/core/strings.dart` `pluginMissing` | …CorbitChat mobile plugin… | …required CorbitHub mobile plugin… |
| `lib/features/more/about_screen.dart` | name only | name **+ descriptor line** |
| `lib/app.dart` `MaterialApp.title` | CorbitChat | CorbitHub |
| `lib/features/auth/login_screen.dart` device label | CorbitChat Mobile | CorbitHub Mobile |
| `android/app/src/main/AndroidManifest.xml` `android:label` | corbitchat_mobile | CorbitHub |

The login screen already shows `appName` + `loginTagline`, so it now reads
**CorbitHub / Mobile Workspace for Jira Data Center** with no further change.

### Intentionally NOT renamed (technical identifiers — zero-migration-risk rule)

- Plugin key `com.corbitlogic.corbitchat.jira.dc` (renaming orphans AO data).
- REST base path `/rest/corbit-mobile/1.0/*` and `/rest/jim/1.0/*`.
- Session header `X-CorbitChat-Session` (client/server contract + saved sessions).
- Android `applicationId` / package (`com.corbitlogic.corbitchat_mobile`) — a
  change is a new app install and breaks upgrades.
- Java packages, AO tables, secure-storage keys, `CorbitChatTheme` /
  `CorbitChatApp` class names, repo/dir names.

These are implementation details users never see; changing them is regression
risk with no user benefit.

---

## 2. customfield_10903 colored pill

### Investigation findings

- The field exists **only on production** (name on prod:
  "نوع تسک(طبق دستوالعمل)"). It is absent on the test server, so we key off the
  **field id**, never the name.
- Stored values mix **Arabic and Persian letter forms** and ZWNJ/space variants,
  e.g. `فوري` (Arabic yeh), `پروژه‌اي` (ZWNJ + Arabic yeh). Naive matching
  against the Persian spec values would miss every real value → normalization is
  mandatory.
- Sprint 04 issue detail already returned custom fields field-driven, but did
  not resolve this one into a pill. Sprint 03 issue cards did not include it.

### Backend (field-driven, preferred)

New helper `JimMobileTaskType.java`:

- `pillForIssue(Issue)` — reads `customfield_10903` respecting **field-layout
  visibility** (hidden ⇒ null), handles `Option` values, returns pill metadata
  or null.
- `resolve(String)` — normalizes + maps a raw value to pill metadata.
- Emits `{ value, key, cssClass, labelEn, labelFa, textColor, backgroundColor,
  render:"pill" }`.

Wired into:

- `JimMobileIssueDetail` custom-field projection → attaches `pill` to the
  `customfield_10903` entry (and `Option` values now stringify to their label).
- `JimMobileIssues.toIssueMap` → adds a bounded `taskType` pill to every issue
  **card** (search/dashboard preview). Null when the field is absent/hidden.

Nothing is logged; values are bounded (single option label).

### Mobile (generic renderer + local fallback)

- `lib/models/task_type_pill.dart` — `TaskTypePill` model with `fromJson`
  (backend metadata) **and** a local `resolve()` mirroring the backend
  normalization/mapping, so an older backend still renders a pill.
- `lib/features/common/task_type_pill_view.dart` — the single reusable pill
  widget (hex→Color, locale-aware label). No screen hard-codes colors.
- Rendered in **issue detail** details section and **task/issue cards**
  (`issue_card.dart`, used by Tasks lists and Dashboard today preview).

### Normalization (identical on both sides)

Trim → drop ZWNJ/ZWJ/LRM/RLM/space/NBSP → Arabic yeh `ي`/alef-maksura `ى` ⇒
Persian yeh `ی`, Arabic kaf `ك` ⇒ Persian kaf `ک` → lower-case latin. So
`پروژه ای` and `پروژه‌اي` and `پروژه‌ای` all collapse to one key. English
values (Critical, Urgent, …) are also accepted.

### Mapping

| Normalized value | key | English | Persian | Text | Background |
|---|---|---|---|---|---|
| بحرانی / critical | critical | Critical | بحرانی | #DE350B | #FFEBE6 |
| فوری / urgent | urgent | Urgent | فوری | #FF8B00 | #FFF0B3 |
| تعهدی / committed | committed | Committed | تعهدی | #FFAB00 | #FFF7D6 |
| جاری / current | current | Current | جاری | #0052CC | #DEEBFF |
| پروژه(‌)ای / project(-based) | project | Project-Based | پروژه‌ای | #6554C0 | #EAE6FF |
| توسعه(‌)ای / development | development | Development | توسعه‌ای | #00875A | #E3FCEF |
| راهبردی / strategic | strategic | Strategic | راهبردی | #172B4D | #DFE1E6 |

Unknown values → no pill; the field falls back to plain text (never crashes).

---

## 3. Verification (production data)

Real prod issues resolve correctly (stored Arabic forms):

| Issue | stored | pill |
|---|---|---|
| SUP-3 | فوري | Urgent #FF8B00/#FFF0B3 |
| SUP-8 | بحراني | Critical #DE350B/#FFEBE6 |
| CSI-9 | جاري | Current #0052CC/#DEEBFF |
| PMO-125 | پروژه‌اي | Project-Based #6554C0/#EAE6FF |
| PMO-124 | توسعه‌اي | Development #00875A/#E3FCEF |
| SDWAN-83 | تعهدي | Committed #FFAB00/#FFF7D6 |
| ICT-43 | راهبردي | Strategic #172B4D/#DFE1E6 |

Card projection confirmed (`/issues/search`): COR-5 → development, TEC-34 →
critical show `taskType`. Test server (field absent) returns `taskType:null`
with no regression.

Regression (both servers): bootstrap, preferences, dashboard, chat
conversations, issues/search, issue detail, projects all 200.
`dart analyze` clean, `flutter test` green, leak scan clean.

---

## 4. Manual UI verification steps

Install `releases/corbitchat-mobile-s04b-release.apk`.

Identity:
1. Login screen shows **CorbitHub** + **Mobile Workspace for Jira Data Center**.
2. More → About shows CorbitHub + descriptor + version/server.
3. Android launcher / recent-apps shows **CorbitHub**.

Custom-field pill (use the **production** server `https://jira.7gtech.net`,
since the field only exists there):
4. Open issue **SUP-8** → details shows a red **Critical / بحرانی** pill.
5. **SUP-3** → orange **Urgent / فوری**; **CSI-9** → blue **Current / جاری**.
6. **PMO-125** (پروژه‌اي) and **PMO-124** (توسعه‌اي) → Project-Based / Development.
7. **SDWAN-83** → Committed; **ICT-43** → Strategic.
8. Switch app language FA↔EN → pill label flips Persian↔English, colors stable.
9. Tasks tab / Dashboard today preview: issue cards with the field (e.g. COR-5,
   TEC-34 for m.nasiri) show the pill inline.
10. RTL (Persian) and LTR (English) both lay out correctly.

Regression: login (PAT + username/password), remembered login, chat
send/receive, dashboard counts, tasks lists, issue detail, projects.

## 5. Remaining risks

- The field only has data on production; test server cannot exercise the pill
  end-to-end (verified via prod REST + prod data instead).
- Any future task-type value outside the mapping renders as plain text by
  design; extend the map in `JimMobileTaskType` + `task_type_pill.dart` if new
  values are introduced.
