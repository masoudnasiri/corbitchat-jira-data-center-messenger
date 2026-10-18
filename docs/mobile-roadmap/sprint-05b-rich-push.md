# Sprint 05B — Rich Push UX, Avatar Notifications, and Notification Privacy

Refines Sprint 05 push. Notifications now show sender name, an optional message
preview, and the sender avatar — all under user control — while keeping payloads
privacy-safe and respecting Sprint 04G mobile feature access.

> Status: **DONE (fix1)** — backend + Android client implemented, built,
> deployed and verified. Plugin version now `1.0.0-mobile-s05b-fix1`.
> On-device visual verification (avatar/preview) requires installing the release
> APK on a physical Android device (steps at the end).
>
> See the **Fix-1 addendum** at the bottom for the production root cause
> (production was still running the old `1.0.0-mobile-s05` jar).

---

## Root cause of the generic push UX

Sprint 05 sent an FCM message with a generic `notification` block
(`title="CorbitHub"/sender`, `body="New message"`) plus a routing `data` block.
The OS rendered the generic `notification` block directly, so:

- message text was deliberately never included (privacy, but too minimal);
- the sender avatar could not be shown (FCM auto-downloads notification images
  without auth headers, and our avatars require authentication — no token may be
  placed in a URL);
- there was no per-user control over notification content.

## Fix — data-only push + client-rendered local notifications

The plugin now sends **data-only** FCM messages (no `notification` block). The
Flutter app always receives them (foreground/background/terminated on Android)
and renders a **local notification** itself via `flutter_local_notifications`,
which allows a per-notification large icon (avatar) and full text control.

Privacy is enforced **server-side** (single source of truth): the sender reads
the recipient's preferences and only emits the fields the user allows, so the
payload never contains more than permitted — even for an old client.

---

## Source drift / version

- Old deployed jar and source both read `1.0.0-mobile-s05`, so they were
  indistinguishable. Bumped to **`1.0.0-mobile-s05b`**, rebuilt, and redeployed.
- The `POST /admin/mobile-push/test` diagnostic is included in s05b (verified
  live: bogus token → `UNREGISTERED`, proving the OAuth→FCM path).
- Rollback artifact: `/root/jira-dev/rollback/corbitchat-jira-dc-1.0.0-mobile-s05.ROLLBACK.jar`.
- This environment has a single Jira instance (`jira-srv`), so test == prod here.
  For a separate production server: deploy the s05b jar and the FCM config is
  already persisted in plugin settings (survives restart — verified).

---

## Notification preference model

Stored per user in the existing `JimMobilePref` AO table (additive nullable
columns; legacy rows read as the safe default = enabled). Exposed and updated via
the existing BFF `GET/PUT /rest/corbit-mobile/1.0/preferences`.

| Field | Type | Default | Meaning |
|-------|------|---------|---------|
| `pushEnabled` | bool | true | Master switch |
| `chatPushEnabled` | bool | true | Chat category |
| `taskPushEnabled` | bool | true | Task/assignment/status category |
| `mentionPushEnabled` | bool | true | Mention/assistant category |
| `reminderPushEnabled` | bool | true | Overdue/today reminder category |
| `detailLevel` | enum | `preview` | `generic` \| `keyOnly` \| `preview` |
| `showMessagePreview` | bool | true | Include chat message text |
| `showSenderAvatar` | bool | true | Include sender avatar |

## Push payload contract (data-only)

Routing (always): `eventType`, `feature`, `category`, `entityId`, `deepLink`,
`notificationId`, `ts`, `channel`, plus `conversationId` / `issueKey` /
`taskFilter` as applicable.

Display (computed per prefs): `title`, `body`, optional `sender`, optional
`avatarPath` (a **relative, token-free** BFF path
`/rest/corbit-mobile/1.0/avatar/user/<key>`).

Never included: issue summary/description, comments, custom field values,
attachments, tokens, or auth material.

### Detail-level behaviour

| Category | generic | keyOnly | preview |
|----------|---------|---------|---------|
| Chat | "New message" | sender name, no text | sender name + message text* |
| Task/Status | "Task update" | `TEC-34 updated` | safe summary (e.g. "TEC-34 assigned to you") |
| Mention | "New notification" | "You were mentioned" | safe summary |
| Reminder | "Task reminder" | "N tasks due today" | "N tasks due today" |

\* chat text is included only when `detailLevel=preview` **and**
`showMessagePreview=true`. Avatar is attached only when `showSenderAvatar=true`.

## Feature access (Sprint 04G) — unchanged and still enforced

Before rendering/sending, the sender rechecks the recipient's mobile feature
access for the event's feature (`chat` / `issueDetail` / `tasks`). A blocked
feature is never delivered (diagnostics: `skippedFeatureBlocked`). Category-gated
skips are counted as `skippedPrefsDisabled`.

---

## How the avatar is implemented

The app fetches `avatarPath` over the **authenticated** connection (reads the
stored session from secure storage — works in the background isolate too), with
the credential attached as a header only when the host matches the stored base
URL. The bytes become the notification large icon; if the fetch fails or avatar
is disabled, it falls back to the app icon. No token ever appears in a URL.

### Limitation (documented, not faked)

Data-only delivery in background/terminated state relies on high-priority FCM
data messages waking the app isolate. On Android with Google Play services this
works; some aggressive OEM battery managers may delay/suppress background
delivery. iOS is out of scope in this environment (no macOS/APNs).

---

## Files changed

### Plugin (backend)
- `ao/JimMobilePreference.java` — +8 nullable notification columns.
- `mobile/MobilePreferences.java` — rebuilt with builder + push fields + `toMap`.
- `service/JimMobilePreferenceServiceImpl.java` — merge/normalize/persist push prefs.
- `mobile/rest/CorbitMobilePreferencesResource.java` — accept push fields (nullable bool PATCH).
- `mobile/push/MobilePushEvent.java` — carries semantic ingredients + category.
- `service/JimMobilePushServiceImpl.java` — reads prefs, category gate, renders safe per-pref data-only payload.
- `service/JimMobileFcmClient.java` — data-only send overload.
- `service/JimMessageServiceImpl.java` — chat/system hooks pass sender/actor/preview + category.
- `service/JimMobileReminderScheduler.java` — reminder category + safe summary.
- `pom.xml` — version `1.0.0-mobile-s05b`.
- `.gitignore` — ignore Firebase service-account JSON.

### Mobile (Flutter)
- `core/push/local_notifications.dart` — NEW: renders rich local notifications, authed avatar fetch, tap payload.
- `core/push/firebase_push_backend.dart` — background handler renders local notification.
- `core/push/push_service.dart` — init local notifications, foreground render, cold-start tap via launch payload.
- `models/preferences.dart` — push fields + `copyWith`.
- `api/corbit_api.dart` — `putPreferences` push fields.
- `features/more/push_settings_screen.dart` — NEW: settings UI.
- `features/shell/tabs/more_tab.dart` — Notifications tile opens the screen.
- `core/strings.dart` — bilingual push-settings copy.
- `android/app/build.gradle.kts` — core-library desugaring for local notifications.
- `pubspec.yaml` — `flutter_local_notifications ^19.0.0`.
- `.gitignore` — ignore Firebase service-account JSON.

## Tests performed (automated / server)
- Plugin builds clean (`s05b`), deployed with rollback backup, Jira healthy.
- `flutter analyze`: no issues. Release APK built (55.8 MB) with Firebase + local notifications.
- FCM config persisted across restart (`configured:true`, `projectId:corbithub`).
- `POST /admin/mobile-push/test` → `UNREGISTERED` (OAuth→FCM valid).
- `GET /preferences` exposes all new fields with safe defaults; `PUT` round-trip
  persists `chatPushEnabled/detailLevel/showMessagePreview/showSenderAvatar`.
- Regression smoke (health, ao-health, conversations, chat page, BFF health,
  preferences, devices) all `200`.

## Remaining risks
- Background/terminated delivery subject to OEM battery restrictions (Android).
- On-device avatar/preview appearance not verifiable without a physical device.
- iOS push not built (no macOS/APNs in this environment).

---

## On-device / admin verification steps

Install `build/app/outputs/flutter-apk/app-release.apk` on an Android device.

1. **Settings visible**: More → Notifications → Push notifications screen shows
   master switch, four category toggles, detail-level segmented control, and the
   message-text / sender-photo switches.
2. **Persistence**: toggle options, kill and relaunch the app — choices persist
   (they are stored server-side; verify by signing in on another device).
3. **Chat push (preview)**: with detail=Full preview + message text ON, have
   another user DM you while the app is backgrounded → notification shows sender
   name, message text, and sender avatar. Tap → opens the conversation.
4. **Preview OFF**: turn message text off → next chat notification shows the
   sender name but body "New message" (no text).
5. **Generic**: set detail=Generic → notification shows "CorbitHub / New
   message" only.
6. **Category off**: turn Chat off → no chat notifications arrive; a task
   assignment still notifies (Tasks on).
7. **Task/issue**: get assigned an issue → keyOnly shows "TEC-34 updated";
   preview shows "TEC-34 assigned to you". Tap → Issue Detail (if feature
   allowed), else the feature-disabled screen.
8. **Feature access**: with the admin disabling your Chat mobile feature, chat
   push stops (server-side), independent of preferences.
9. **Logout**: log out → device token revoked; no further push.
10. **Diagnostics** (admin): `GET /rest/jim/1.0/admin/mobile-push` →
    `configured:true`, `activeDevices>=1` after registering, counters advancing;
    `skippedPrefsDisabled` / `skippedFeatureBlocked` increment on gated events.
11. **Credential test** (admin): `POST /rest/jim/1.0/admin/mobile-push/test`
    with `{"token":"<real device token>"}` → `SUCCESS`.

---

## Fix-1 addendum (production runtime failure)

Reported: on a physical Android phone connected to **production**
(`https://jira.7gtech.net` / `193.162.129.56`), (1) the Push Notification
settings toggles did not stick, and (2) push still looked generic.

### Root cause — one cause, two symptoms

**Production was still running the old `1.0.0-mobile-s05` jar.** Sprint 05 and
05B were only ever deployed to the **dev/test** box (`185.83.181.194` /
`jira.corbitlogic.com`). Production is a *separate* server (see
`docs/deployment-production.md`) that never received s05b.

Confirmed from the production server:

- Installed jar was `corbitchat-jira-dc-1.0.0-mobile-s05.jar`.
- The prod `AO_099FDF_JIM_MOBILE_PREF` table had **no push columns**
  (`THEME_MODE, NOTIF_LEVEL, CALENDAR_SYS, LANG_CODE, QUIET_HOURS, UPDATED_AT`
  only) — so `PUT /preferences` could not persist push settings and the echo
  re-defaulted every field → **toggles reverted** (not a UI bug).
- The old s05 sender attaches an FCM `notification` block → Android OS-renders
  the **generic** notification, and an old APK never runs the local-notification
  renderer.

The mobile settings screen and push client code were already correct; both
symptoms were pure **deployment drift** (prod jar + phone APK behind source).

### Fix (smallest safe change)

- Bumped plugin `1.0.0-mobile-s05b` → **`1.0.0-mobile-s05b-fix1`** (traceable,
  not a same-version redeploy — prod had s05).
- Added a live plugin **version** to `GET /rest/corbit-mobile/1.0/health`
  (read via `PluginAccessor`, zero drift).
- Mobile **About** screen now shows: app version+build, **Build** label
  (`s05b-fix1`), server URL, and **Server plugin** (live from health) — so a
  client/server mismatch is obvious on-device.
- Device registration now reports `appVersion` (build label) so prod
  diagnostics show which APK is registered.

### Deployment (both servers, single jar each)

| Server | Before | After |
|---|---|---|
| dev/test (`185.83.181.194`) | `1.0.0-mobile-s05b` | `1.0.0-mobile-s05b-fix1` |
| production (`193.162.129.56`) | `1.0.0-mobile-s05` | `1.0.0-mobile-s05b-fix1` |

Production deploy used the runbook Path B (file install + restart) with a DB +
plugin-jar backup first. Rollback artifacts:

- DB dump: `corbit-prod:/root/jira-backups/jira-db-20260702-223131.sql`
- Previous jar:
  `corbit-prod:/root/jira-backups/installed-plugins-20260702-223131/corbitchat-jira-dc-1.0.0-mobile-s05.jar`

### Production verification (no Jira admin creds needed)

- `docker` shows a single `corbitchat-jira-dc-1.0.0-mobile-s05b-fix1.jar`.
- Log: `Plugin ... version '1.0.0-mobile-s05' is being updated to version
  1.0.0-mobile-s05b-fix1.` AO re-initialised, no exceptions.
- `AO_099FDF_JIM_MOBILE_PREF` now has all 8 push columns
  (`PUSH_ENABLED, CHAT_PUSH, TASK_PUSH, MENTION_PUSH, REMINDER_PUSH,
  DETAIL_LEVEL, SHOW_PREVIEW, SHOW_AVATAR`).
- FCM config survived the upgrade (`fcmProjectId/fcmClientEmail/fcmPrivateKeyPem/
  fcmTokenUri` present in plugin settings — names only, never values).
- All BFF + admin endpoints return `401` (present, auth-required), not `404`.
- One enabled android device registered (`JIRAUSER10000`).

### Dev round-trip (authenticated) — proves the settings fix

`PUT /preferences {chatPushEnabled:false, detailLevel:"keyOnly",
showMessagePreview:false, showSenderAvatar:false}` → echo reflects the change →
a fresh `GET` returns the same values (persisted). Push diagnostics:
`configured:true, projectId:"corbithub"`, with `skippedPrefsDisabled` counter.

### FCM payload shape (code-proven)

`deliver()` calls `send(token, data, null, null, false)`; `buildMessageJson`
only adds a `notification` block `if (title != null || body != null)`, so the
real push is **data-only** (`message.data` + `android.priority=high`, no
`notification` block). The app's background/foreground handlers render the local
notification via `flutter_local_notifications`.

### Artifacts

- Plugin: `/root/jira-dev/releases/corbitchat-jira-dc-1.0.0-mobile-s05b-fix1.jar`
- APK: `/root/jira-dev/releases/corbithub-s05b-fix1-release.apk` (app `1.0.0+2`,
  build label `s05b-fix1`)

### On-device verification (phone on production)

0. Install `corbithub-s05b-fix1-release.apk`, log in, open **More → About** and
   confirm **Build = s05b-fix1** and **Server plugin = 1.0.0-mobile-s05b-fix1**.
Then run steps 1–11 above.
