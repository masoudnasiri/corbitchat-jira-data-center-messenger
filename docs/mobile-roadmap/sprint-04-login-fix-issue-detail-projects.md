# Sprint 04 — Fix Login Regression, Login Remembering, Read-only Issue Detail, and Projects

Status: **DONE** — deployed to test (`http://185.83.181.194:8080`) and production
(`https://jira.7gtech.net`). Plugin version `1.0.0-mobile-s04`.

Credentials are never written here; use the operational values from your secure
notes / AGENTS.md.

---

## 1. Login regression — root cause

The Sprint 03 **release** APK could not reach either server ("could not reach the
server") while the Jira web UI worked. The backend was healthy the whole time.

Root cause was purely **client-side, Android packaging**, in two parts:

1. **Missing `INTERNET` permission in the release APK.** The permission was only
   declared in `android/app/src/debug/AndroidManifest.xml` and
   `.../profile/AndroidManifest.xml`. Those manifests are **not** merged into a
   `--release` build, so the release APK shipped with **no network permission at
   all** → every request failed with a generic connection error, on every server.
   Debug builds worked, which masked the problem during development.
2. **Cleartext HTTP blocked.** `targetSdk` follows the Flutter default (API ≥ 28),
   where cleartext (`http://`) is disabled by default. Even after fixing (1), the
   test server (`http://185.83.181.194:8080`) would still be unreachable for PAT
   login over http.

Neither was a plugin-version mismatch, TLS, URL-normalization or error-mapping bug
— though error mapping was *also* too coarse and is now fixed (see §3).

### Fix

- Added `<uses-permission android:name="android.permission.INTERNET"/>` to the
  **main** manifest (`android/app/src/main/AndroidManifest.xml`).
- Added `android/app/src/main/res/xml/network_security_config.xml`: cleartext is
  **disabled by default** (production is https-only) with a small allow-list for
  known dev/test hosts (`185.83.181.194`, `10.0.2.2`, `localhost`, `127.0.0.1`).
  Referenced via `android:networkSecurityConfig` + `android:usesCleartextTraffic="false"`.

Verified in the **shipped** merged manifest
(`build/app/intermediates/packaged_manifests/release/.../AndroidManifest.xml`):
`android.permission.INTERNET`, `networkSecurityConfig` and
`usesCleartextTraffic="false"` are all present.

---

## 2. Login remembering

- **First launch:** Jira URL field is empty (removed the hard-coded
  `AppConfig.defaultBaseUrl`).
- **After a successful login:** the last Jira URL is remembered; the **username**
  is remembered for password logins; the login **method** (password/PAT) is
  remembered.
- **Save-password checkbox:** off by default, shown only when the URL is secure
  (https / local dev). When enabled the password is stored **only** in secure
  storage (`flutter_secure_storage`, Keystore/Keychain). When disabled, any saved
  password is deleted immediately.
- **Logout:** revokes the mobile session server-side and clears the active
  session (token/authKind/session base URL), but **keeps** the remembered
  URL/username so the next login is quick.
- **Clear saved login:** a button on the login screen wipes remembered URL,
  username, method and any saved password.

Storage keys (all in secure storage): `cc.login.baseUrl`, `cc.login.username`,
`cc.login.method`, `cc.login.savePassword`, `cc.login.password`. These survive
logout; only "Clear saved login" removes them.

---

## 3. Error mapping

`ApiClient` now classifies failures into `ApiErrorKind` and shows distinct copy:

| Situation | Kind | Message |
|---|---|---|
| DNS / host not found | `dns` | "Server address not found. Check the URL…" |
| TLS/cert failure | `tls` | "The secure (TLS) connection failed…" |
| http blocked by device | `cleartextBlocked` | "This is a plain http:// server, which the device blocks…" |
| unreachable / refused / timeout | `network` | "Could not reach the server…" / timeout copy |
| 401/403 | `unauthorized` | permission message |
| **404 (missing endpoint)** | `endpointMissing` | plugin-not-installed / old-version message |
| 5xx | `http` | server-problem message |

The login screen turns `endpointMissing` into a clear
"mobile plugin not available / older version" message for both password and PAT
login. PAT login additionally probes `GET /health` after validating the token so a
valid PAT against a plugin-less server still reports a compatibility error rather
than "logged in then everything 404s".

---

## 4. Backend BFF — new read-only endpoints

All under `/rest/corbit-mobile/1.0/*`, `@AnonymousAllowed` + manual auth gate,
permission-enforced, bounded, no raw JQL from client.

### `GET /issues/{issueKey}` — full issue detail
`CorbitMobileIssueResource#getIssue` + `JimMobileIssueDetail`.

- Loads the issue via `IssueManager.getIssueByCurrentKey` and enforces
  `ProjectPermissions.BROWSE_PROJECTS` (issue security too). Missing issue and
  permission failure return the **same 404** so existence is never leaked.
- Fields: key, id, summary, **rendered** description + environment (wiki→HTML via
  `RendererManager` using the field's configured renderer, plain-text fallback),
  issue type (+icon), status (+category), priority (+icon), resolution, project
  (+avatar), assignee/reporter/creator (+avatars), created/updated/due/resolution
  dates, overdue flag, labels, components, fix/affects versions, aggregate time
  tracking.
- **Custom fields:** field-driven. Only field-configuration-visible (non-hidden)
  fields; values stringified and length-bounded (≤1000 chars, ≤50 list elements)
  so no huge/unsafe object is returned; string fields with a renderer are rendered
  to HTML. Well-known non-display types are skipped (`gh-lexo-rank`,
  `jira-development-integration`/`devsummary`).
- **Comments:** `CommentManager.getCommentsForUser` (respects role/group
  visibility), latest 50, rendered body where available.
- **Attachments:** metadata list (filename, mime, size, created, author, url).
- **Work log:** aggregate time spent + only **unrestricted** entries (role/group
  restricted worklogs skipped to avoid leaks), latest 50.
- **`permissions`** flags for FUTURE sprints (read-only this sprint): canComment,
  canEdit, canTransition, canAssign, canLogWork, canAddAttachment, canDelete.

### `GET /projects` — accessible projects
`CorbitMobileProjectResource#list` — uses
`PermissionManager.getProjects(BROWSE_PROJECTS, user)` (only browseable projects).
Fields: key, id, name, projectTypeKey, avatar, lead, url. Capped at 200.

### `GET /projects/{projectKey}` — overview + stats
`CorbitMobileProjectResource#overview` + `JimMobileProjects`. Permission-checked
project; description (bounded), component/version counts; `stats` = total / open /
assignedToMe computed via `SearchService.searchCount` on server-owned project-scoped
JQL (enforces the caller's issue security).

No new `<component-import>` was needed: manager types are obtained via
`ComponentAccessor` (matching the existing web-side pattern).

---

## 5. Mobile UI

- **Issue Detail** (`features/tasks/issue_detail_screen.dart`): header pills
  (type/status/priority/resolution), summary, rendered description/environment,
  Details (system + field-driven custom fields), time tracking, attachments,
  comments (rendered), work log. Loading / error+retry / **404 lock (no-permission)**
  states. RTL/LTR via per-string `directionOf`; dates via the Sprint 03
  Jalali/Gregorian `DateDisplay` (+ new `dateTime`/`duration`).
- **HTML rendering** (`core/html_view.dart`): dependency-free converter that turns
  the server-rendered HTML into clean, safe text (strips tags incl. script/style,
  keeps paragraphs/bullets/entities). No raw wiki markup is ever shown.
- **Navigation:** Dashboard "today" preview and all Tasks lists open the real
  Issue Detail (replaced the "coming soon" snackbars).
- **Projects tab** (`features/projects/projects_tab.dart`): accessible project
  list with avatars/lead; loading/empty/error states; pull-to-refresh.
- **Project overview** (`features/projects/project_overview_screen.dart`): header,
  stats (total/open/assigned-to-me), description, component/version counts, and a
  6-tab scaffold (Overview + honest "coming soon" placeholders for Board / Issues /
  Chat / Members / Activity — no fabricated data). 404 → no-permission state.

---

## 6. Files changed

**Mobile (`/root/jira-dev/corbitchat-mobile`)**
- `android/app/src/main/AndroidManifest.xml` — INTERNET perm + net-security config ref
- `android/app/src/main/res/xml/network_security_config.xml` — NEW
- `lib/core/app_config.dart` — removed default URL; remembered-login keys
- `lib/core/secure_store.dart` — remembered-login read/write/clear
- `lib/core/strings.dart` — issue-detail / projects / save-password copy
- `lib/core/date_display.dart` — `dateTime` + `duration`
- `lib/core/html_view.dart` — NEW HTML→text renderer
- `lib/api/api_client.dart` — `ApiErrorKind` + granular error mapping
- `lib/api/corbit_api.dart` — `checkMobileCompat`, `issueDetail`, `projects`, `projectOverview`
- `lib/features/auth/login_screen.dart` — remembered fields, save-password, clear, compat errors
- `lib/models/issue_detail.dart` — NEW
- `lib/models/project.dart` — NEW
- `lib/state/issue_providers.dart` — issueDetail / projects / projectOverview providers
- `lib/features/tasks/issue_detail_screen.dart` — NEW
- `lib/features/tasks/issue_list_view.dart` — open Issue Detail
- `lib/features/shell/tabs/dashboard_tab.dart` — open Issue Detail
- `lib/features/projects/projects_tab.dart` — NEW
- `lib/features/projects/project_overview_screen.dart` — NEW
- `lib/features/shell/app_shell.dart` — use real ProjectsTab
- `lib/features/shell/tabs/placeholder_tabs.dart` — DELETED

**Backend (`/root/jira-dev/jira-issue-chat-panel`)**
- `pom.xml` — version → `1.0.0-mobile-s04`
- `.../mobile/JimMobileIssueDetail.java` — NEW
- `.../mobile/JimMobileProjects.java` — NEW
- `.../mobile/rest/CorbitMobileIssueResource.java` — `GET /issues/{key}`
- `.../mobile/rest/CorbitMobileProjectResource.java` — NEW

---

## 7. What was intentionally NOT changed

No write actions (comment/edit/transition/assign/worklog/attachment upload), no
boards, no project chat/members/activity implementations, no `/rest/jim/1.0/*`
behavior changes, no AO schema / plugin-key changes, no new `<component-import>`.

---

## 8. Verification

- Release APK: merged/packaged manifest contains INTERNET + net-security config.
- Test + prod: `jim/health`, `jim/ao-health`, mobile `health/ao-health/bootstrap/
  preferences/dashboard/issues.search/chat.conversations/projects/projects/{key}/
  issues/{key}` all 200.
- Security: anonymous → 401; unknown issue/project → 404; session-token flow works
  on the new endpoints; custom-field skip verified (noisy fields filtered out).
- `dart analyze` clean; `flutter test` (offline) green; leak scan found no logging
  of secrets/descriptions/comments/values (new code logs nothing sensitive).
- Prod log confirms `1.0.0-mobile-s03 → 1.0.0-mobile-s04` upgrade + clean startup.

Rollback: previous `corbitchat-jira-dc-1.0.0-mobile-s03.jar` is preserved in
`/root/jira-dev/releases/` and in the prod backup `installed-plugins-<ts>` folder.

---

## 9. Manual UI test steps

See the "How to test" section in the sprint completion report.
