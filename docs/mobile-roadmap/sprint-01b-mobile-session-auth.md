# Sprint 01B — Mobile Username/Password Login with Plugin-Issued Session Token

Status: **Completed & deployed** to the live Jira DC instance.

Turns the PAT-only mobile foundation from Sprint 01 into a user-friendly,
secure login model: the user signs in with **Jira URL + username + password**,
the plugin validates the credentials against Jira and issues a **plugin-owned
mobile session token**. The app stores only that token (never the password),
and every later Mobile BFF call is authenticated as the **real Jira user**.

---

## 1. Selected authentication approach (and why)

**Plugin-issued opaque session token**, sent in a dedicated request header, with
the plugin impersonating the real Jira user for the request.

Why not the alternatives:

| Option | Verdict | Reason |
|---|---|---|
| **Plugin-issued session token** (chosen) | ✅ | Mobile-appropriate long-lived sessions (30 days), server-side revocation, hashed at rest, decoupled from Jira's short web-session timeouts. Matches the sprint's expected behavior. |
| Jira native cookie (`/rest/auth/1/session`) | ❌ | Inherits Jira's short web-session timeout (poor mobile UX), cookie-jar/XSRF handling on the client, and no app-owned revocation model. More "native" but worse for a mobile app. |
| Shared plugin/service token | ❌ (forbidden) | Would bypass user identity, Jira permissions, and audit correctness. Never used for user-level operations. |

### Why the token is NOT in the `Authorization` header
Jira Data Center rejects unknown `Authorization: Bearer <x>` values with **401
before the request reaches the plugin** (verified). Reusing that header would
break the flow and collide with PAT auth. The mobile session token therefore
travels in a custom header:

```
X-CorbitChat-Session: <opaque token>
```

This keeps the existing **PAT path (`Authorization: Bearer <PAT>`) completely
untouched**.

### Why a shared plugin-level access token was not used
User-level operations (bootstrap, preferences, and — in later sprints — sending
messages, comments, transitions, assignments) must resolve to the current user
so Jira permissions and audit stay correct. A shared token has a single fixed
identity and would break all of that. Credentials are validated per user via
`CrowdService`, and each request resolves to that specific user.

---

## 2. How the real Jira user is resolved per request

A servlet filter (`CorbitMobileSessionFilter`, scoped to
`/rest/corbit-mobile/1.0/*`, `before-dispatch`):

1. If the request is **already authenticated** (PAT / Jira cookie) → pass through
   untouched.
2. Else if it carries a valid `X-CorbitChat-Session` token → look up the owning
   user and call `JiraAuthenticationContext.setLoggedInUser(user)` for the
   duration of the request, restoring the previous value in a `finally` block.

Because the filter sets the **real** logged-in user, every existing resource
keeps using `getLoggedInUser()` and Jira permission checks unchanged. This was
verified live: a session-token `bootstrap` returns the correct user
(`JIRAUSER10000 / m.nasiri`).

> Note: this Jira blocks anonymous REST entirely, so the mobile endpoints that
> must be reachable pre-/via-session (`/auth/*`, `/bootstrap`, `/preferences`)
> are annotated `@AnonymousAllowed`; they still enforce their own
> `getLoggedInUser() != null` check, so anonymous requests get a clean 401.

---

## 3. Mobile session token lifecycle

- **Create** — `POST /rest/corbit-mobile/1.0/auth/login` with `{username,
  password, device?}`. The plugin validates via `CrowdService.authenticate(...)`,
  resolves the `ApplicationUser`, generates a 256-bit random token, stores only
  its **SHA-256 hash** (AO entity `JimMobileSession`), and returns the raw token
  once with an `expiresAt`.
- **Validate** — per request, the token is hashed and looked up; rejected if
  missing, revoked, or expired. `lastUsedAt` is refreshed (best-effort).
- **Expire** — 30 days from issue (`TTL_MILLIS`).
- **Revoke** — `POST /auth/logout` marks the row revoked (idempotent). Also
  `revokeAllForUser(...)` is available (e.g. for future password-change hooks).

Storage: only token **hashes** live in AO (`AO_099FDF_JIM_MOBILE_SESS`); the raw
token exists only on the device (OS Keychain/Keystore via
`flutter_secure_storage`).

---

## 4. HTTPS / HTTP handling

- **Backend**: `POST /auth/login` requires a secure transport —
  `request.isSecure()` OR `X-Forwarded-Proto: https` OR `X-Forwarded-Ssl: on`
  (the standard reverse-proxy model). A JVM override
  `-Dcorbitchat.mobile.allowInsecureLogin=true` exists for local/dev HTTP only
  (off by default). Otherwise → `400 INSECURE_TRANSPORT`.
- **Client (app)**: password login is **blocked** unless the URL is `https://`
  (or a local dev host: `localhost`, `127.0.0.1`, `10.0.2.2`). PAT login over
  `http://` is allowed but shows an advisory insecure-connection banner.
- **Test-instance caveat**: this deployment's Tomcat connector reports
  `secure=true` (it fronts the `https://jira.corbitlogic.com` TLS proxy), so the
  server treats all requests as HTTPS. The live HTTPS negative case
  (`400 INSECURE_TRANSPORT`) could not be reproduced from the build host; the
  client-side block is the enforced, user-facing control. See §8.

---

## 5. Files changed

### Backend (plugin)
- `ao/JimMobileSession.java` **(new)** — hashed-token session entity
  (`@Table("JimMobileSess")`).
- `service/JimMobileSessionService.java` + `…Impl.java` **(new)** —
  create/validate/revoke/expiry; SHA-256 hashing; secure-random tokens.
- `mobile/rest/CorbitMobileAuthResource.java` **(new)** — `login` / `logout` /
  `session` (`@AnonymousAllowed`), Crowd validation, HTTPS enforcement.
- `mobile/MobileSessionSupport.java` **(new)** — header name, HTTPS detection,
  insecure-login override.
- `mobile/CorbitMobileSessionFilter.java` **(new)** — resolves & impersonates the
  real user from the session header.
- `mobile/rest/CorbitMobileBootstrapResource.java`,
  `mobile/rest/CorbitMobilePreferencesResource.java` — added `@AnonymousAllowed`
  (self-enforced 401).
- `atlassian-plugin.xml` — `crowdService` import, `JimMobileSession` AO entity,
  `jimMobileSessionService` component, `corbit-mobile-session-filter`.

### Mobile (Flutter)
- `models/session.dart` — `AuthKind {pat, session}` on `Session`.
- `core/app_config.dart` — `sessionHeader`, `kAuthKind` key.
- `core/secure_store.dart` — persist/clear `authKind`.
- `api/api_client.dart` — session header vs bearer; `postJson`; `onUnauthorized`
  (401) hook.
- `api/corbit_api.dart` — `login(...)`, `logout()`, `MobileLoginResult`.
- `state/providers.dart` — `login(authKind)`, `clear()`, `handleExpired()`;
  auto-drop session on 401.
- `features/auth/login_screen.dart` — method selector (password / access token),
  username+password fields, HTTP block for password login.
- `features/shell/tabs/more_tab.dart` — logout now revokes server-side then wipes
  local storage.
- `main.dart`, `core/strings.dart`, `test/widget_test.dart` — restore authKind,
  new copy (fa/en), updated test.

---

## 6. What was fixed / intentionally not changed

**Fixed / addressed**
- PAT-only assumption removed without breaking PAT.
- Anonymous-REST gate (this Jira blocks all anon REST) handled via
  `@AnonymousAllowed` + self-enforced auth checks.
- Username no longer logged on the login error path.

**Intentionally not changed**
- Existing `/rest/jim/1.0/*` web endpoints, AO schema of other entities, plugin
  key, and all Sprint 02+ features (Direct Chat, dashboard tasks, push, issue
  detail, comments, boards) — out of scope.
- Sliding-expiry sessions and a user-facing "active sessions" screen — deferred
  (foundation is in place via `revokeAllForUser`).

---

## 7. Tests performed

- **Backend regression suite (15/15 PASS)** against the live instance: missing
  creds→400, wrong password→401, bogus session→401, session bootstrap/auth-
  session/prefs GET+PUT→200, bootstrap-after-logout→401, PAT bearer→200, basic-
  auth→200, jim health/ao-health/conversations→200, anon jim→401, chat page→200.
- Session token stored is the plugin token (login response returns
  `token`+`expiresAt`+`user`; password never returned/stored).
- **Flutter**: `flutter analyze` (no issues), `flutter test` (pass),
  `flutter build web --release` (ok), `flutter build apk --debug` (ok).
- **Leak scan**: no `print`/logging of password/token in the app; no secret in
  backend logs.

---

## 8. Deployment status

Built with offline Maven, deployed
`corbitchat-jira-dc-1.0.0-internal-mobile-bff.jar`, restarted Jira, waited for
readiness, and ran the regression suite (all green). AO module healthy
(`ao-health` 200) with the new `JimMobileSession` entity.

---

## 9. Remaining risks

- **HTTPS negative path not live-tested** here (connector reports secure; the
  `jira.corbitlogic.com` HTTPS host is unreachable from the build host). Verify
  on an HTTPS-capable host: password login over plain `http://` (no
  `X-Forwarded-Proto`) must return `400 INSECURE_TRANSPORT`.
- **Reverse-proxy trust**: HTTPS detection trusts `X-Forwarded-Proto`; the origin
  HTTP connector must not be publicly exposed (standard Jira proxy posture).
- **No automatic session cleanup job** for expired rows yet (validation already
  rejects them; a periodic purge can be added later).
- Impersonation relies on `setLoggedInUser`; verified working for current
  endpoints and the intended pattern for Sprint 02 user-level actions.

---

## 10. Exact UI steps to test

Point the app at an **HTTPS** Jira for password login (e.g.
`https://jira.corbitlogic.com`). User: `m.nasiri` / `<REDACTED>`.

**A. Username/password login (happy path)**
1. Launch app → Login screen, method **"Username & password"** selected.
2. Jira URL = `https://<your-jira>`, Username = `m.nasiri`, Password = `<REDACTED>`.
3. Tap **Sign in** → "Connected successfully" → lands in the app shell
   (Dashboard shows your profile). Only the session token is stored.

**B. Invalid credentials**
1. Same as A but a wrong password → clear error **"Incorrect username or
   password."**, stays on Login.

**C. Insecure HTTP block (password)**
1. Set Jira URL to an `http://` address, method "Username & password" → a red
   **"Insecure connection"** banner appears and **Sign in** is blocked.

**D. PAT login still works**
1. Switch method to **"Access token"**, enter URL + a valid PAT → **Sign in** →
   app shell. (`http://` allowed here, with an advisory banner.)

**E. Restart persistence**
1. After A (or D), fully close and reopen the app → you remain logged in and land
   directly in the shell (session restored from secure storage).

**F. Logout (server revoke + local wipe)**
1. **More → Logout → confirm** → returns to Login. The server session is revoked
   and the token is removed from the device.

**G. Expired / revoked session → back to login**
1. While logged in via password, revoke the session server-side (Logout on
   another device, or admin), then trigger any action (pull-to-refresh / reopen a
   tab) → the app detects the 401, drops the session, and returns to Login with
   the session-expired message.
