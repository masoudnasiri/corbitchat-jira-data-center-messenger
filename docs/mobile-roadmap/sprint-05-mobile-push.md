# Sprint 05 — Mobile Push Notifications

Native push for CorbitHub Mobile: device registration, a safe minimal payload
contract, feature-aware delivery (respecting Sprint 04G), and deep-link routing.
This is a **separate native channel** from the existing browser Web Push (VAPID);
the two never share tokens or transport.

> Status: **LIVE** — backend + Android client implemented, deployed, and enabled.
> Firebase project `corbithub` is configured on the server (FCM HTTP v1) and the
> release APK is built with `google-services.json` active. The server↔FCM OAuth
> handshake is verified end-to-end (a send to a bogus token returns `UNREGISTERED`,
> which only happens after a valid access token is minted and FCM accepts the call).
> Real device delivery begins once the release APK is installed on a device and it
> registers its FCM token via the mobile BFF.

---

## Preflight results

| Check | Result |
|-------|--------|
| Jira → FCM outbound (`fcm.googleapis.com`, `oauth2.googleapis.com`) | **Reachable on test AND prod** (HTTP 404 to a GET = connected) |
| Existing web push | VAPID Web Push (`JimPushService`) — kept separate, not reused |
| Event sources | chat (`JimMessageServiceImpl`), assignment/status/mention (`JimIssueEventHandlerImpl` → `createSystemMessage`) |
| Scheduler for reminders | none existed → added `JimMobileReminderScheduler` |
| Sprint 04G feature access | `JimMobileFeatureService` reused for feature-aware delivery |
| Mobile router | none (imperative nav) → added global navigator key + deep-link router |
| Firebase in app | none (no deps, no `google-services.json`) → added, config-gated |
| CN mirror serves Firebase | pub packages + Android SDK: **yes**; plugins-DSL marker: no (used classpath) |
| iOS/APNs/macOS | **not available** (Linux host) — plan only |

## FCM / APNs configuration status

- **FCM Android:** infrastructure ready; **not configured** (no service account,
  no `google-services.json`). Push is a safe no-op until both are provided.
- **APNs / iOS:** **blocked** — no macOS/Xcode/Apple provisioning in this
  environment. iOS is scaffolded in the shared Dart layer (platform reported as
  `ios`) but not built or wired.

---

## Backend

### Device model & storage
- New AO entity `JimMobileDevice` (`@Table("JimMobileDevice")`), separate from
  `JimPushSubscription` (web push).
- Raw tokens are **AES-256-GCM encrypted at rest** (`TOKEN_ENC`) with a
  plugin-scoped key generated once and stored in plugin settings; a SHA-256
  `TOKEN_HASH` is stored for idempotent lookup/dedupe. **Raw tokens are never
  logged.**
- Max 10 devices/user (oldest pruned); dead tokens pruned after 5 failures or on
  an FCM `UNREGISTERED` response.

### Endpoints (Mobile BFF, `/rest/corbit-mobile/1.0`)
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/devices/register` | register/refresh token (`{token, platform, appVersion?, deviceModel?, locale?}`) → `{deviceId, pushConfigured}` |
| POST | `/devices/revoke` | revoke a token (`{token}`) |
| GET | `/devices` | list current user's devices (no raw tokens) + `pushConfigured` |

Registration is available to any authenticated mobile user; **feature gating is
applied at send time, not registration time.**

### Admin (`/rest/jim/1.0/admin`, sysadmin only)
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/mobile-push` | diagnostics (configured, projectId, activeDevices, counters, lastSendOutcome) |
| POST | `/mobile-push` | store FCM service account (accepts split fields or a raw service-account JSON body) |
| POST | `/mobile-push/test` | diagnostic: send a one-off push to a given `{"token": "..."}` and return the FCM result. `SUCCESS`/`UNREGISTERED` both prove the credential is valid |
| DELETE | `/mobile-push` | clear FCM config |

`/admin/diagnostics` now also includes a `mobilePush` node.

### Sender (`JimMobilePushService` + `JimMobileFcmClient`)
- FCM **HTTP v1** with service-account OAuth (RS256 JWT → access token, cached).
- For every send: **rechecks Sprint 04G feature access** for the recipient,
  confirms the user is active, applies **short-window dedupe** (45 s per
  `dedupeKey`), then delivers to each enabled device asynchronously.
- If FCM is not configured → records `not_configured` in diagnostics and returns
  (no fake success).

### Safe payload contract (FCM `data` + generic `notification`)
```
eventType       chat_message | issue_assignment | issue_status | issue_mention
                | reminder_today | reminder_overdue
feature         chat | issueDetail | tasks           (04G key required to open)
entityId        conversationId | issueKey | filter    (safe identifier only)
deepLink        corbithub://chat/conversation/{id}
                corbithub://issue/{issueKey}
                corbithub://tasks/{today|overdue}
notificationId  uuid
ts              epoch millis
(+ conversationId | issueKey | taskFilter as applicable)
```
Generic `notification.title/body` (e.g. sender display name + "New message") are
included so the OS can display the tray notification. **No message bodies, issue
summaries/descriptions, comment text, custom field values, or attachments are
ever sent.** Full data is loaded from the BFF after the tap (permissions
re-checked there).

### Event hooks
| Event | Hook | Feature | Deep link |
|-------|------|---------|-----------|
| Direct chat message | `JimMessageServiceImpl.notifyDirectRecipientPushSafely` | chat | conversation |
| Assignment / status / issue-mention | `JimMessageServiceImpl.createSystemMessage` (issueKey set) | issueDetail | issue |
| Chat/assistant mention (no issue) | same, issueKey null | chat | assistant conversation |
| Overdue / today | `JimMobileReminderScheduler` (daily 08:00 server time) | tasks | tasks filter |

Presence suppression is inherited from the existing web-push logic (no push when
the recipient is actively viewing the conversation).

---

## Mobile (Flutter / Android)

- `firebase_core` + `firebase_messaging` added, **config-gated**: the Google
  Services Gradle plugin is applied only when `android/app/google-services.json`
  exists (buildscript classpath, since the plugins-DSL marker is absent from the
  CN mirror). **The release APK builds today without any Firebase config**
  (verified) — push simply stays disabled.
- `PushService` (`lib/core/push/`): initialise Firebase (try/catch → disabled if
  no config), request permission, obtain token, register with the BFF, re-register
  on `onTokenRefresh`, and revoke on logout (`/devices/revoke` + `deleteToken`).
- Deep-link routing (`DeepLinkRouter` + global `appNavigatorKey`):
  - foreground: non-intrusive SnackBar with an "Open" action;
  - background tap: `onMessageOpenedApp`;
  - terminated tap: `getInitialMessage` on start.
  - Each route **re-checks 04G feature access**; a tap for a disabled feature
    shows the feature-disabled state instead of hidden content.
- `PushStatus` (`unknown/unsupported/permissionDenied/notConfigured/active`) is
  exposed via a provider for a "push unavailable" hint.

---

## Prerequisites to turn push ON

> **DONE (this deployment).** All steps below have been completed:
> - `android/app/google-services.json` supplied (project `corbithub`, package
>   `com.corbitlogic.corbitchat_mobile`, sender `822283232614`) and baked into the
>   release APK (`processReleaseGoogleServices` generated the resource values;
>   `firebase_messaging` is registered in `GeneratedPluginRegistrant`).
> - Service-account JSON posted to `/rest/jim/1.0/admin/mobile-push` →
>   `"configured": true`, `"projectId": "corbithub"`.
> - Credential validated end-to-end via `POST /mobile-push/test` (bogus token →
>   `UNREGISTERED`, proving OAuth + FCM round-trip).
> - Device register → list → revoke lifecycle verified through the mobile BFF.
> - Secrets are git-ignored in both repos (service-account JSON in the plugin repo,
>   `google-services.json` in the mobile repo). Neither is tracked.
>
> Remaining to see a notification on a phone: install the release APK on an Android
> device (with Google Play services), log in, grant the notification permission, and
> trigger a chat/assignment/mention event or the daily reminder.

Everything below is required before any real notification can be delivered.

### 1. Create a Firebase project
1. Go to <https://console.firebase.google.com> → **Add project** (or reuse one).
2. Name it (e.g. `corbithub`); Google Analytics is optional.

### 2. Add the Android app → `google-services.json`
1. In the project: **Add app → Android**.
2. **Android package name:** `com.corbitlogic.corbitchat_mobile` (must match exactly).
3. App nickname/SHA-1 optional (SHA-1 only needed for some Google services, not FCM).
4. **Download `google-services.json`** and place it at
   `android/app/google-services.json` in the CorbitHub repo (it is git-ignored;
   a template lives at `android/app/google-services.json.example`).
5. Rebuild: `flutter build apk --release`. The Gradle plugin is now applied
   automatically and the app obtains an FCM token.

### 3. Create a service account → server credentials
1. Firebase console → **Project settings → Service accounts**.
2. **Generate new private key** → downloads a service-account JSON.
3. In the CorbitHub **plugin admin** (as Jira sysadmin), POST that JSON to
   `/rest/jim/1.0/admin/mobile-push` (a UI field can be added later; for now use
   the REST endpoint). The plugin stores `project_id`, `client_email`,
   `private_key` in encrypted plugin settings. **Never commit or log this file.**
4. Verify: `GET /rest/jim/1.0/admin/mobile-push` → `"configured": true`.

### 4. (iOS, later) APNs
- Requires an Apple Developer account, an APNs key/cert uploaded to Firebase, a
  macOS build machine, and `GoogleService-Info.plist`. Out of scope for this
  environment.

### Network
- Jira server needs outbound HTTPS to `oauth2.googleapis.com` and
  `fcm.googleapis.com`. **Verified reachable on test and production.** Confirm any
  production firewall keeps these open.

---

## What was intentionally NOT implemented
- Real end-to-end delivery (blocked on the credentials above — not faked).
- iOS build/APNs (no macOS/Apple provisioning).
- Full notification preference center, quiet hours, rich/expanded push content.
- Comment/reply, project chat, group chat, full Assistant feed (future sprints).
- A dedicated admin UI tab for FCM config (REST endpoint provided; UI can follow).

## Files changed
**Plugin:** `ao/JimMobileDevice.java`, `mobile/push/MobilePushEvent.java`,
`service/JimMobileDeviceService(+Impl).java`, `service/JimMobileFcmClient.java`,
`service/JimMobilePushService(+Impl).java`, `service/JimMobileReminderScheduler.java`,
`mobile/rest/CorbitMobileDeviceResource.java`, edits to
`service/JimMessageServiceImpl.java`, `rest/JimAdminResource.java`,
`atlassian-plugin.xml`, `pom.xml` (→ `1.0.0-mobile-s05`).

**Mobile:** `core/app_navigator.dart`, `core/push/*` (backend/firebase/service/router),
`api/corbit_api.dart` (device APIs), edits to `app.dart`, `features/shell/app_shell.dart`,
`features/shell/tabs/more_tab.dart`, `android/` (manifest, gradle config-gate, template).

## Verification performed
- Plugin builds; deployed to **test + production**; `ao-health` 200 (new table created).
- Device register/list/revoke verified on test + prod; **no raw token exposed**.
- Diagnostics report `configured:false` / `not_configured` (safe, honest).
- Chat send still 200 with the hook firing (no crash; outcome `not_configured`).
- Regression green on test: dashboard, tasks (today/overdue), projects, boards, chat.
- **Release APK builds without `google-services.json`** (config-gated).
- `flutter analyze` clean.
