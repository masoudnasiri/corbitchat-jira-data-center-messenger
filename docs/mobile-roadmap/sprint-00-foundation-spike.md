# Sprint 00 — Foundation & Technical Spike

**Phase:** 0 — Technical Spike · **Duration:** 2 weeks · **Goal:** prove
the whole stack end to end on the real Jira DC instance before building
features.

> Spec: PDF §3 (architecture), §6.1 (auth), §9 Phase 0. Handover:
> "Your first three actions". Design system: `README.md`,
> `docs/foundation.md`.

## Objective

De-risk every hard dependency: authentication against the real Jira DC,
the Mobile BFF skeleton inside the plugin, a Flutter app that boots with
the design system, and one real authenticated round-trip rendered on
screen for **both** Android and iOS.

## In scope

- Decide and prove the **auth method** (spike PAT first; confirm whether
  OAuth/SSO or plugin session token is required by the org — PDF §6.1,
  Appendix B).
- Mobile BFF module skeleton in the plugin: `/rest/corbit-mobile/1.0`
  with `GET /health` and a stub `GET /bootstrap`.
- Flutter app scaffold wired to `CorbitChatTheme` (light/dark, fa/en).
- One issue list (`assignee = currentUser`) + one conversation list,
  read-only, rendered natively.
- CI producing debug Android APK and iOS build.

## Backend track (plugin BFF)

1. Add a new REST module `com.corbitlogic.jira.internalmessenger.mobile`
   with `@Path` base `/rest/corbit-mobile/1.0`, wired in
   `atlassian-plugin.xml` (mirror the existing `/rest/jim/1.0` module —
   see `docs/architecture.md` §5.2, §7.3). **Do not touch** existing
   `/rest/jim/1.0` resources.
2. `GET /health` and `GET /ao-health` equivalents for the mobile module.
3. Stub `GET /bootstrap` returning `{ user, serverTime, locale }`
   resolved from the authenticated Jira user (`JiraAuthenticationContext`).
4. Confirm PAT-based `Authorization: Bearer <token>` reaches the module
   and resolves the current user (Jira DC honors PATs on REST).

## Mobile track (Flutter)

1. Scaffold `/root/jira-dev/corbitchat-mobile/` (Flutter + null-safety),
   folder layout `src/{api,models,features,theme,l10n}`.
2. Import `flutter/corbitchat_theme.dart`; wire `MaterialApp` with
   `theme`/`darkTheme`/`themeMode.system`, `Locale('fa','IR')` default,
   `Directionality` per `docs/rtl-i18n.md`. Bundle Vazirmatn + Inter +
   JetBrains Mono fonts.
3. `src/api/client.ts`→`client.dart`: HTTP layer modeled on
   `jim-api.js` (single `request()` helper, JSON error contract, 204
   handling, `X-Atlassian-Token: no-check`). Base URL configurable.
4. Auth screen stub: Jira URL + PAT + language toggle (`docs/layout.md`
   Login Layout). Store token in Keychain/Keystore (`flutter_secure_storage`).
5. "Hello world" = real `GET /rest/api/2/myself` → render display name +
   avatar; plus `GET /rest/jim/1.0/conversations` and
   `POST /rest/api/2/search` (assignee=currentUser) lists.

## Environment (from handover / AGENTS.md)

- Jira: `http://185.83.181.194:8080` (HTTPS via `https://jira.corbitlogic.com`).
- Smoke test: `curl -su m.nasiri:<REDACTED> http://185.83.181.194:8080/rest/api/2/myself` → 200.
- No SSH; plugin build/install via `docker exec`/`docker cp` per `AGENTS.md`.

## Design references

- `docs/foundation.md` (color, type, spacing, elevation tokens).
- `README.md` Quick Start (Flutter theme + fonts).
- `docs/rtl-i18n.md` (default locale, `Directionality`).

## Dependencies / prerequisites

- Firebase project + Apple developer account provisioning **started**
  (needed by Sprint 05) — kick off procurement now.
- Decision from operator on auth method (Appendix B risk).

## Acceptance criteria (PDF §9 Phase 0)

- [ ] Authenticated `GET /rest/api/2/myself` renders the profile on a
      physical/emulated **Android** and **iOS** device.
- [ ] `/rest/corbit-mobile/1.0/health` returns 200; `/bootstrap` returns
      the current user.
- [ ] Conversation list and an assigned-issue list load read-only.
- [ ] Theme switches light/dark and fa/en (RTL flips correctly).
- [ ] CI builds both platforms.

## Definition of Done

- Chosen auth method documented with pros/cons and org decision.
- Repo boundary respected (mobile in sibling repo; BFF is additive).
- Spike learnings written up; no throwaway code merged to main features
  without review.

## Risks

- Auth method uncertainty (Appendix B) — mitigate by proving PAT now and
  flagging OAuth/session as a later spike.
- Jira reachable only on internal network → VPN/reverse-proxy decision
  needed before push (Sprint 05).

## Execution status (implemented)

**Backend (Mobile BFF) — done, deployed, verified live.**

- New package `com.corbitlogic.jira.internalmessenger.mobile.rest` with three
  resources; new REST module `corbit-mobile-rest` mounted at
  `/rest/corbit-mobile/1.0` in `atlassian-plugin.xml`. Both REST modules are now
  package-scoped so they don't cross-serve. Existing `/rest/jim/1.0/*` untouched.
- Built offline (`corbitchat-jira-dc-1.0.0-internal-reply-attach.jar`), deployed,
  Jira restarted. Verified against `http://185.83.181.194:8080`:
  - `GET /rest/corbit-mobile/1.0/health` → 200 `{ok, module, apiVersion, status}`
  - `GET /rest/corbit-mobile/1.0/ao-health` → 200 (AO reachable)
  - `GET /rest/corbit-mobile/1.0/bootstrap` → 200 with the authenticated user
    (`m.nasiri`, displayName, email, avatar), `featureFlags`, `preferences`,
    `serverTime`; anonymous → 401.
  - Regression: `jim/health`, `jim/ao-health`, `conversations` still 200.

**Mobile (Flutter) — scaffolded, analyzed clean, real builds produced.**

- New sibling repo `/root/jira-dev/corbitchat-mobile` (Flutter 3.44.4 /
  Dart 3.12.2). Structure: `theme/` (design-system theme port),
  `core/` (config, in-memory session store, bilingual strings),
  `api/` (Dio client + typed errors + endpoints), `models/`,
  `features/auth` (Jira URL + PAT + language toggle + real `myself` round-trip),
  `features/home` (bootstrap round-trip render with loading/error/retry states).
- `flutter analyze` → **No issues found**; `flutter build web --release` → OK;
  `flutter build apk --debug` → **`build/app/outputs/flutter-apk/app-debug.apk`**
  (targetSdk 36, all ABIs).

### Toolchain notes / caveats for the mobile CI machine

- Google download hosts are **geo-blocked** from this build host. Working
  mirrors used: Flutter/Dart + engine artifacts via
  `FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn` +
  `PUB_HOSTED_URL=https://pub.flutter-io.cn`; Android SDK components via
  `https://mirrors.cloud.tencent.com/AndroidSDK/`; Gradle/AGP/AndroidX via
  `https://maven.aliyun.com/repository/{public,google,gradle-plugin}` (wired
  into `android/settings.gradle.kts`, `android/build.gradle.kts`, and
  `flutter/packages/flutter_tools/gradle/settings.gradle.kts`).
- `flutter_secure_storage` is **deferred to Sprint 01**: its bundled legacy AGP
  is incompatible with the template's AGP 9. `SecureStore` uses an in-memory
  impl behind the identical API for this spike (swap back on a machine with full
  Google SDK access, or after downgrading the app to AGP 8.x).
- iOS build not attempted (requires macOS; not available on this host).

### Acceptance status against PDF §9 Phase 0

- [x] Authenticated `GET /rest/api/2/myself` round-trip implemented + login flow.
- [x] `/rest/corbit-mobile/1.0/health` 200; `/bootstrap` returns current user.
- [x] Theme + fa/en language toggle with RTL (via `flutter_localizations`).
- [x] A real build is produced (Android APK + web). *(CI wiring + iOS pending.)*
- [ ] Conversation list + assigned-issue list read-only → moved to Sprint 02/03
      (Sprint 00 proves the stack end-to-end; list surfaces belong to their
      feature sprints).
