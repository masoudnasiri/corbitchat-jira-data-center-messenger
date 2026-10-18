# Sprint 04C — UX Polish: Avatars, Text Contrast, Persian Jalali Dates

Status: **Done** — deployed to test (`http://185.83.181.194:8080`) and production
(`https://jira.7gtech.net`). Plugin version `1.0.0-mobile-s04c`.
Release APK: `releases/corbitchat-mobile-s04c-release.apk`.

A UX-foundation sprint (not Sprint 05 Push). Three root-cause fixes: avatar
loading, faint text, and mixed Gregorian/Jalali dates. No push notifications,
no create/edit, no redesign.

## 1. Avatars

### Root cause
Jira builds avatar URLs from its **configured base URL**, which the mobile client
cannot reach:

| Server | Example returned `avatarUrl` (before) | Problem |
|--------|----------------------------------------|---------|
| Test   | `https://185.83.181.194/secure/projectavatar?...` | `https` on a raw IP with no working TLS listener; app connects on `http://…:8080` |
| Test   | `https://jira.corbitlogic.com/secure/useravatar?...` | different host than the app is connected to |
| Test   | `https://www.gravatar.com/avatar/…` | external host (gravatar) |
| Prod   | `https://jira.7gtech.net/secure/useravatar?...` | reachable, but still built from server base URL |

`ChatAvatar`/`UserAvatar` loaded these with a bare `Image.network(url)` (no auth,
no base-URL normalization), so mismatched/broken hosts failed → initials for
(almost) every user.

### Fix — Mobile BFF avatar proxy + authenticated image loading
Chosen over client-side URL rewriting because it works regardless of Jira's base
URL, scheme, gravatar, or avatar-visibility policy, for **both** PAT and
mobile-session auth, with **no token in the URL**.

- **Backend proxy** `GET /rest/corbit-mobile/1.0/avatar/user/{key}` and
  `/avatar/project/{key}` (`CorbitMobileAvatarResource`): streams the Jira avatar
  bytes over the same authenticated connection. Permissions enforced by
  `AvatarService.getAvatar` (users) and `BROWSE_PROJECTS` (projects). Sits behind
  the existing mobile session filter, so unauthenticated requests get `401`.
  Bounded to 2 MB, `Cache-Control: private, max-age=86400`.
- **All mobile payloads** now return **relative** proxy paths
  (`JimMobileAvatars.userPath/projectPath`): bootstrap, issue detail
  (assignee/reporter/creator/comment/worklog authors + project), task cards,
  projects list (+ new `leadAvatarUrl`), and chat (conversations, messages,
  user-search) — the last three rewritten in `CorbitMobileChatResource` using the
  user keys it already holds, **without touching the shared `/rest/jim` mapper**
  (no web regression).
- **Client** `core/avatar_image.dart` resolves a proxy path against the session
  base URL and attaches the session's auth header (`Authorization: Bearer` for
  PAT, `X-CorbitChat-Session` for mobile session). Auth headers are attached
  **only** when the host matches our base URL — external URLs (gravatar) get no
  credential, so nothing leaks off-server. `ChatAvatar`, `UserAvatar`,
  `_ProjectAvatar`, and the project-overview avatar all use it.

### Verified
Proxy streams real PNGs on prod (self 5.8 KB, conversation 7.5 KB, assignee
5.8 KB) with both session and native/PAT auth; `401` without auth. On the test
server some Jira **default** user avatars are SVG (Flutter's image decoder can't
render SVG → clean initials fallback); uploaded avatars and project avatars are
PNG and render. Initials remain the fallback, not the default.

## 2. Text contrast / readability

### Root cause
`ColorScheme.outline` is the **border** color (`#D8DCE4`, near-invisible as text),
yet it was used as the **text/metadata** color for issue field labels, task-card
metadata, attachment metadata, comment/worklog labels, chat timestamps, and the
presence line.

### Fix — shared tokens first
- Theme (`corbitchat_theme.dart`): defined a **readable** `onSurfaceVariant`
  (design secondary `#55627A` light / `#A6B0BD` dark) plus `primaryContainer`,
  `onPrimaryContainer`, `surfaceContainerHighest`, `outlineVariant`, so secondary
  text has one correct token and `outline` stays a border color only.
- Switched real-content usages from `colorScheme.outline` → `onSurfaceVariant`
  in issue detail (labels, attachment/worklog metadata, muted text, schedule
  icon), task cards (issue-type, assignee/folder icons, due date), chat
  (conversation timestamp, day separator, presence line), and profile labels.
- Decorative large empty-state icons keep the lighter tone; hierarchy preserved,
  nothing over-darkened.

## 3. Persian / Jalali dates

### Root cause
`dateDisplayProvider` derived the calendar from a **server preference**, not the
UI language — so a Persian user whose server `calendar` was `gregorian` saw
Gregorian dates. Chat timestamps used a separate locale-agnostic `TimeFormat`
that only produced Gregorian `yyyy/mm/dd`.

### Fix — one centralized formatter, driven by UI language
- `dateDisplayProvider`: `fa` → Jalali + Persian digits; otherwise Gregorian.
  Switching language now updates every date surface consistently.
- `DateDisplay` extended with `time`, `listTime`, `dayLabel`, `isJalali`, so chat
  (list + thread) flows through the same Jalali/Gregorian + Persian-digit logic.
  Legacy `core/time_format.dart` deleted (no duplicated conversion).
- Surfaces routed through `DateDisplay`: Dashboard/Tasks cards, Issue Detail
  (created/updated/due/resolved, comments, worklogs, attachments), chat
  conversation-list timestamps, thread day separators + bubble times, profile
  last-sync. (Projects have no date fields.)
- **Future date-input foundation:** `Jalali.toGregorian()` (correct-by-
  construction, reuses the trusted forward conversion) + `core/date_input.dart`
  (`AppDateValue`) — the documented contract for later create/edit sprints:
  present a Jalali picker when `DateDisplay.isJalali`, always send Gregorian to
  Jira. No picker UI shipped this sprint.

## Files changed

Backend:
- `mobile/JimMobileAvatars.java` (new) — proxy path helper
- `mobile/rest/CorbitMobileAvatarResource.java` (new) — streaming proxy
- `mobile/JimMobileIssues.java`, `mobile/JimMobileIssueDetail.java`,
  `mobile/JimMobileProjects.java` (+`leadAvatarUrl`),
  `mobile/rest/CorbitMobileBootstrapResource.java`,
  `mobile/rest/CorbitMobileChatResource.java` — emit proxy paths
- `pom.xml` — version `1.0.0-mobile-s04c`

Mobile:
- `core/avatar_image.dart` (new), `core/date_input.dart` (new)
- `core/date_display.dart`, `core/jalali.dart`, `state/issue_providers.dart`
- `features/chat/widgets/chat_avatar.dart`, `features/shell/widgets/user_avatar.dart`
- `features/chat/chat_tab.dart`, `features/chat/conversation_screen.dart`
- `features/tasks/widgets/issue_card.dart`, `features/tasks/issue_detail_screen.dart`
- `features/projects/projects_tab.dart`, `features/projects/project_overview_screen.dart`
- `features/more/profile_screen.dart`, `models/project.dart`
- `theme/corbitchat_theme.dart`
- deleted `core/time_format.dart`

## Tests / deployment
- Backend `mvn … package` OK; `dart analyze` clean; `flutter test` passes; release
  APK built (54 MB).
- Deployed to test + prod (prod backup: `installed-plugins-*` retaining
  `…-s04b.jar`). Full mobile smoke `200` on both; `/rest/jim/1.0/health` `200`
  (web unchanged). Task-type pills verified (SUP-3 Urgent). No secrets in logs.

## Remaining risks
- Jira **default** user avatars served as SVG render as initials in Flutter (no
  SVG decoder; `flutter_svg` unavailable offline). Real uploaded avatars render.
- The date-input picker itself is not implemented (foundation only, by design).
