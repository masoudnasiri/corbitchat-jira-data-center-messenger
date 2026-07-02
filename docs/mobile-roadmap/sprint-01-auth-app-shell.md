# Sprint 01 — Auth, Bootstrap & App Shell

**Phase:** 1 — MVP Core · **Duration:** 2 weeks · **Goal:** a real,
production-shaped login → bootstrap → navigable app shell with theming,
RTL/i18n and secure storage.

> Spec: PDF §4.1 (login/profile), §5 (UX/screen model), §6.1 (auth),
> §6.3 (`JimMobilePreference`). Design: `docs/layout.md`,
> `docs/rtl-i18n.md`, `docs/states.md`, `docs/components.md` (Button,
> Input, Top Bar, Bottom Navigation).

## Objective

Turn the spike into the app's permanent foundation: secure login, the
`/bootstrap` contract, the 5-tab shell, the theming/i18n engine, and the
More/Settings menu with a language switch persisted server-side.

## In scope

- Login / server-selection screen with connection test (`docs/states.md`
  Connection States).
- Secure token/session storage (Keychain/Keystore); full logout with
  device-token revocation hook (revocation wired in Sprint 05).
- `GET /bootstrap` → profile, language, feature flags, unread counts,
  mobile settings.
- Bottom-nav shell (Dashboard, Chat, Projects, Tasks, More) with empty
  placeholder screens; More menu (Colleagues, Notifications, Settings,
  About, Logout).
- Settings: language (fa/en), theme, notification level scaffold; persist
  via `JimMobilePreference`.
- Profile view (display name, avatar, presence, accessible projects
  count, last sync time).

## Backend track (plugin BFF)

1. `GET /rest/corbit-mobile/1.0/bootstrap` — return
   `{ user:{key,displayName,avatarUrl,presence}, locale, calendar,
   featureFlags, unread:{total,byType}, preferences }`. Resolve unread
   from existing chat services (`GET /conversations/unread-count`).
2. Add AO entity `JimMobilePreference` (`userKey, language, calendar,
   quietHours, notificationLevel`) + `GET/PUT
   /rest/corbit-mobile/1.0/preferences`.
3. Confirm auth interceptor resolves current user for PAT and cookie
   session; standard error contract `{status,message}` (mirror
   `jim-api.js` error handling).

## Mobile track (Flutter)

1. Auth flow + `flutter_secure_storage`; PAT/URL inputs forced `dir=ltr`
   (`docs/rtl-i18n.md` Form & Input). Connection-test states.
2. `AppShell` with persistent bottom nav (`docs/layout.md` Primary
   Navigation); route table incl. deep-link placeholders (`corbitchat://`).
3. Theming + i18n engine: `intl`/ICU, Jalali+Gregorian formatting,
   fa/en string tables, runtime locale switch re-renders + flips
   direction. Externalize all strings (`docs/rtl-i18n.md` Copy & String
   Keys) — no hard-coded UI text.
4. State management (Riverpod/Bloc — pick one, document it) + secure
   `Dio` client with auth interceptor and typed error mapping.
5. Settings + Profile screens; language change calls `PUT /preferences`.
6. Local read-cache foundation (SQLite via `drift`/`isar`) — schema for
   bootstrap + conversations for later sprints.

## Design references

- `docs/layout.md` — Login Layout, Screen Shell, Bottom Nav, More menu.
- `docs/components.md` — Button, Input, Top Bar, Bottom Navigation, List Item.
- `docs/states.md` — Connection States, Sync Indicator.
- `docs/rtl-i18n.md` — locale, dates, form direction, testing checklist.

## API contract

```
GET  /rest/corbit-mobile/1.0/bootstrap      → profile, locale, flags, unread, prefs
GET  /rest/corbit-mobile/1.0/preferences
PUT  /rest/corbit-mobile/1.0/preferences    { language, calendar, notificationLevel, quietHours }
POST /rest/auth/1/session                   (fallback cookie bootstrap for SSO — later)
```

## Dependencies

- Sprint 00 (BFF module, theme wiring, auth method decision).

## Acceptance criteria

- [ ] User logs in with PAT, token stored securely, survives app restart.
- [ ] Bootstrap populates profile + unread badge on the Chat tab.
- [ ] All 5 tabs reachable; More menu + Settings + Profile render.
- [ ] Language switch (fa↔en) persists to server and flips RTL/LTR live.
- [ ] Logout clears secure storage and returns to login.
- [ ] Every screen shows loading/empty/error states.

## Definition of Done (aligns to PDF §9.1)

- No raw token in logs/crash reports.
- Consistent error contract + retry with input preserved.
- RTL + LTR verified on all shell screens (`docs/rtl-i18n.md` checklist).

---

## Execution status (implemented)

**Backend (Mobile BFF) — done, deployed, verified live.**

- New AO entity `JimMobilePreference` (`@Table("JimMobilePref")` — the full name
  overflows AO's 30-char table-name limit; see root cause below), registered in
  `atlassian-plugin.xml` `<ao>`.
- New `JimMobilePreferenceService` / `…Impl` (upsert-by-user, enum/language
  normalization, product defaults) + component registration.
- New `CorbitMobilePreferencesResource` → `GET`/`PUT
  /rest/corbit-mobile/1.0/preferences` (partial-update merge, 400 on invalid,
  401 anonymous).
- `CorbitMobileBootstrapResource` enriched: effective `locale` from stored prefs,
  `jiraLocale` hint, `calendar`, `preferences`, `presence`, and `unread`
  (`{total, byType}`) aggregated from the existing chat services (best-effort,
  degrades to 0 rather than failing launch).
- Built offline, deployed to `http://185.83.181.194:8080`, Jira restarted.
  Verified: `corbit-mobile/ao-health` `initialized`; `bootstrap` returns full
  profile + `unread.total` (13, split SYSTEM/DIRECT/GROUP) + prefs; `preferences`
  GET/PUT persist and round-trip; invalid language normalizes to `fa-IR`;
  anonymous → 401. **Regression:** `jim/ao-health`, `jim/conversations` back to
  200 (see root cause).

**Mobile (Flutter) — implemented, analyzed clean, real builds produced.**

- Real secure storage: `flutter_secure_storage` (Android EncryptedSharedPrefs /
  iOS Keychain) replaces the Sprint 00 in-memory spike. Session persists across
  restart; logout wipes credentials. Token never logged (`Session.toString`
  redacts it; no Dio log interceptor).
- State management: **Riverpod** (`flutter_riverpod`, pure Dart — no native build
  impact). `sessionProvider`, `settingsProvider`, `apiProvider`,
  `bootstrapProvider`.
- App shell: 5-tab bottom nav (Dashboard, Chat, Projects, Tasks, More) with a
  live unread badge on Chat. Dashboard renders real bootstrap; Chat/Projects/Tasks
  are feature-flag-gated placeholders. More → Profile, Settings, About, Logout
  (Colleagues/Notifications shown as "coming soon").
- i18n/RTL: fa/en with runtime switch that flips direction live; all copy
  externalized in `core/strings.dart`; credential inputs forced `dir=ltr`.
- Settings: language + theme persist locally **and** to the server via
  `PUT /preferences`; notification-level scaffold (disabled, Sprint 05).
- Every shell surface has loading / empty / error+retry / no-permission states
  (`features/common/state_views.dart`, `bootstrap_builder.dart`).
- `flutter analyze` → **No issues found**; `flutter test` → pass;
  `flutter build web --release` → OK; `flutter build apk --debug` →
  `build/app/outputs/flutter-apk/app-debug.apk`.

### AGP / secure-storage decision

`flutter_secure_storage` 9.2.4 ships a **legacy** `android/build.gradle`
(`buildscript` DSL + pinned `com.android.tools.build:gradle:8.5.1`). AGP 9 (the
Flutter 3.44 scaffold default) reads only the new DSL, so the plugin fails to
configure. Decision: **pin the app to the last AGP 8.x line** —
**AGP 8.11.1 / Gradle 8.14 / Kotlin 2.2.20** (also the Flutter tool's supported
floor, so no deprecation warnings), keeping `compileSdk 36`. This preserves a
stable plugin ecosystem and reproducible builds over chasing AGP 9. Full
rationale + the environment-specific mirror toggle (`systemProp.corbitchat.cnMirror`
/ `CORBITCHAT_CN_MIRROR`) are documented in `corbitchat-mobile/android/README.md`.

### Root cause of the deploy regression (found & fixed during this sprint)

First deploy of the new AO entity took the **whole plugin's `<ao>` module down**
(`jim/conversations` → 500), because AO caps generated table names at 30 chars
and `AO_099FDF_JIM_MOBILE_PREFERENCE` is 31. Fixed with an explicit short
`@Table("JimMobilePref")`, rebuilt, redeployed; AO re-initialized and all `jim`
endpoints returned to 200.

### Intentionally not changed / deferred

- Local SQLite read-cache (drift/isar): deferred to the first list-bearing sprint
  (conversations/issues) to avoid adding an unused native DB dependency now.
- Push notifications / device-token revocation: Sprint 05 (only the logout hook
  point exists).
- iOS build: not attempted — requires macOS; not available on this host.
- `flutter_secure_storage` web backend differs from mobile; restart-persistence
  must be verified on an Android device/emulator (no emulator on this host).
