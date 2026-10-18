# Sprint 11.5 / PWA-01 — Flutter Web PWA Access for iPhone Users

**Phase:** 3 — Board & Productivity (side track) · **Goal:** an iOS-friendly
web/PWA access path for the existing Hub app, reusing the Flutter codebase,
same-origin under the Jira domain, with a browser-safe cookie session. Not a
rewrite, not full native parity.

Plugin version: `1.0.0-mobile-s11.5-pwa1` · App build: `1.0.0+27` /
`s11.5-pwa1`.

> **Fix-1 (2026-07-05):** deployed to **production**
> (`https://jira.7gtech.net/mobile/`, plugin `1.0.0-mobile-s11.5-pwa1`) and
> enabled **browser file/image attachment upload** in PWA chat (paperclip is
> shown on web again). See §3 and §10.
>
> **Fix-2 (2026-07-05, app `1.0.0+28` / `s11.5-pwa2`, web-only — no plugin
> change):** production PWA is now the **7G TECH branded build**; added
> **service-worker cache-busting**; implemented **web voice recording
> (MediaRecorder)**, **image crop/rotate + Original vs Compressed (canvas)**.
> **Chat data parity** confirmed (cookie auth returns identical conversations to
> native header auth). **Web Push is deferred** (needs a backend BFF endpoint —
> see §12). See §11–§12.

---

## 1. Architecture decision

**Reuse the existing Flutter app, compiled with Flutter Web.** No new frontend.
The same `lib/` produces Android (unchanged) and a Web PWA. Web-specific
behavior is gated with `kIsWeb`, so there is a single codebase and the native
build is untouched.

**Same-origin deployment (Option A):** the Flutter web bundle is served by the
existing Jira nginx at `https://<jira-host>/mobile/`, and the Mobile BFF stays
at `https://<jira-host>/rest/corbit-mobile/1.0/*` (already proxied). One origin
means **no CORS, no preflight, and a first-party cookie** — the simplest, safest
option. Option B (serving the web bundle from the plugin as static resources)
was rejected: WRM/plugin static hosting is awkward for a hashed SPA bundle and
would couple every web redeploy to a plugin release. nginx static hosting mirrors
the already-proven `/apk/` handoff pattern.

- **DEV:** `https://jira.corbitlogic.com/mobile/`
- **PROD (after approval):** `https://jira.7gtech.net/mobile/`

```
Browser (/mobile/)  ──GET──▶  nginx  ──▶  /usr/share/nginx/mobile/  (static Flutter web)
      │
      └── XHR /rest/corbit-mobile/1.0/*  (withCredentials, HttpOnly cookie)
                     │
                   nginx (location /) ──▶ jira-srv:8080 ──▶ CorbitMobileSessionFilter
```

## 2. Flutter Web compatibility findings

`flutter build web` **compiles and runs offline** on this host (cached
`flutter_web_sdk` + CanvasKit). Key points:

- `dart:io` imports in 7 files (`api_client.dart`, `local_notifications.dart`,
  and five chat/issue screens) **compile under dart2js** — the types exist and
  only throw if actually exercised at runtime, which happens exclusively in
  native-only paths that are hidden on web.
- All device I/O funnels through one `MethodChannel('corbitchat/share')` and is
  wrapped in try/catch, so it degrades safely; we additionally hide its UI on
  web so nothing looks wired-but-dead.
- Push (`firebase_*`, `flutter_local_notifications`) starts only post-login and
  is now skipped entirely on web via `kIsWeb`.
- Theme, localization (fa/en), RTL/LTR, brand system, Riverpod, and Dio are all
  pure-Dart and web-safe.
- Routing is Navigator 1.0 (`MaterialApp.home`). On web a refresh reloads to the
  root (login or shell landing) — it does not break; deep-link URLs are a
  documented follow-up (would need path-based routing / go_router).

## 3. Native-only features and their web fallback

| Feature | Web behavior (PWA-01) |
|---|---|
| **Chat file/image upload (paperclip)** | **Supported on web (Fix-1)** via a browser file picker → in-memory bytes → same multipart BFF endpoint. Paperclip offers Photo/Image and File. |
| Camera capture / gallery-vs-camera | Camera is native only; on web the picker uses the browser (gallery/Files) |
| **Image crop/rotate + Original vs Compressed** | **Supported on web (Fix-2)** via HTML canvas: the same crop/rotate editor runs on the picked bytes; compression resizes/re-encodes to JPEG and only replaces the original when actually smaller (otherwise offers Send original / Cancel). |
| **Voice recording** | **Supported on web (Fix-2)** where the browser exposes MediaRecorder (feature-detected; iOS Safari 14.5+). Mic prompt only on tap; record/duration/cancel/send; uploaded with `voice=true` so it renders as a voice bubble. Hidden where MediaRecorder is unavailable. |
| Contact / location share | Native only; not offered on web |
| Android inbound share intent | Not started on web |
| FCM push + local notifications | Skipped on web; "Notifications" settings tile hidden |
| Existing attachments: view/open/download | Images render via same-origin cookie `<img>`; open/download opens the authenticated same-origin URL in a new browser tab (native SAF save / share-out are hidden) |
| Issue/comment attachment upload | Still native-only on web in Fix-1 (deferred — needs the same bytes plumbing on the issue-comment endpoint); chat covers the core requirement |
| Biometric unlock | Out of scope (PWA-01 non-goal) |

No fake/non-functional controls are shown on web.

### Web attachment implementation (Fix-1)

- Browser picker: `lib/core/web/web_files.dart` (conditional import;
  `dart:html` on web, no-op stub on native) returns picked bytes + name + mime.
- `LocalAttachment` gained an in-memory `bytes` field (web); `ApiClient.uploadFile`
  / `CorbitApi.uploadAttachment` accept `bytes` and use
  `MultipartFile.fromBytes` when present (native still streams from a file path).
- Composer preview uses `Image.memory` for web-staged images; upload goes through
  the same authenticated same-origin cookie session and the same BFF security
  checks; upload progress/errors use the existing UI.
- Open/download of existing attachments on web opens the authenticated
  same-origin URL in a new tab (`web_files.dart` `openUrlInNewTab`).

## 4. Auth / session strategy (the core security change)

The native app authenticates with the `X-CorbitChat-Session` header. The PWA must
not hold a token in JavaScript-readable storage, so PWA-01 adds a **cookie
session that reuses the existing `JimMobileSession` token** (no new AO schema, no
new token type):

- `POST /auth/login` now also returns
  `Set-Cookie: corbitchat-pwa-session=<token>; Path=/; Max-Age=<ttl>; HttpOnly; SameSite=Lax; Secure`.
  Native clients ignore it; the browser stores it.
- `CorbitMobileSessionFilter` resolves the user from the **header first, then the
  cookie**. Header path (native) is unchanged.
- `POST /auth/logout` revokes the token (via header or cookie) and clears the
  cookie (`Max-Age=0`).
- The Flutter web client sets `AuthKind.cookie`: **no token stored** (only a
  non-sensitive "resume" marker), same-origin base URL (`Uri.base.origin`), and
  `withCredentials=true` on Dio so the cookie is sent.
- Expired/revoked cookie → BFF returns JSON `401` → app drops the session and
  returns to login (no Jira HTML redirect for API calls).

### CSRF decision

- Session cookie is `HttpOnly; Secure; SameSite=Lax`. `SameSite=Lax` keeps the
  cookie off cross-site subrequests (XHR/fetch/form POST), which is where CSRF
  lives.
- Defense-in-depth: cookie-authenticated **state-changing** requests
  (POST/PUT/DELETE) must also carry the custom header `X-CorbitChat-PWA: 1`. A
  cross-site page cannot set a custom header on a "simple" request, and any
  attempt triggers a CORS preflight this same-origin-only API never answers. If
  the marker is missing on a mutation, the filter leaves the request anonymous
  (→ 401). GET/HEAD are exempt (not state-changing); the native header path is
  exempt (not cookie auth).
- No Jira permission or backend auth check is weakened; the filter still
  impersonates the real user and every resource keeps its server-side checks.

### Cache policy for sensitive responses

`CorbitMobileSessionFilter` sets `Cache-Control: no-store` on all
`/rest/corbit-mobile/1.0/*` responses; login/logout set it explicitly too.

## 5. Service worker / cache policy

- The PWA uses **Flutter's own `flutter_service_worker.js`**, which precaches
  only the hashed app shell (`main.dart.js`, CanvasKit, fonts, icons, asset
  manifests) by content hash. It has **no rules for `/rest/*`**, so it never
  caches Jira data (chat, issues, comments, attachments, boards, projects,
  profile, preferences, or authenticated downloads).
- nginx serves `/mobile/index.html` and `/mobile/flutter_service_worker.js` with
  `Cache-Control: no-cache` so a new deploy is picked up promptly; hashed assets
  cache normally.
- On logout the app state is cleared and the cookie is dropped; the app-shell
  cache holds no user data so it is safe to keep.

## 6. Deployment (DEV done; PROD needs approval)

Static bundle lives inside the `nginx-jira` container at
`/usr/share/nginx/mobile/` (same pattern as `/apk/`); nginx config is the
host-mounted `/root/jira/nginx/conf.d/jira.conf`.

```bash
# 1. Build (same-origin base href, default Hub brand)
cd /root/jira-dev/corbitchat-mobile
flutter build web --release --base-href /mobile/ --dart-define=BRAND=hub

# 2. Stage into the nginx container
docker exec nginx-jira sh -c 'rm -rf /usr/share/nginx/mobile/*'
docker cp build/web/. nginx-jira:/usr/share/nginx/mobile/

# 3. nginx location (added to jira.conf), then reload
#   location /mobile/ { root /usr/share/nginx; try_files $uri $uri/ /mobile/index.html; }
#   location = /mobile/index.html            { root /usr/share/nginx; add_header Cache-Control "no-cache" always; }
#   location = /mobile/flutter_service_worker.js { root /usr/share/nginx; add_header Cache-Control "no-cache" always; }
docker exec nginx-jira nginx -t && docker exec nginx-jira nginx -s reload
```

Reverse-proxy headers already forwarded by the existing `location /`:
`Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto: https`,
`X-Forwarded-Host`, `X-Forwarded-Port: 443` — these let the plugin see the
request as secure so it sets the `Secure` cookie and allows password login.

**Production (done — Fix-1, 2026-07-05):** `https://jira.7gtech.net/mobile/` is
live. Plugin `1.0.0-mobile-s11.5-pwa1` deployed (Path B: single jar, restart);
web bundle in `nginx-jira:/usr/share/nginx/mobile/`; same `/mobile/` nginx
location added to the canonical HTTPS block. Backups taken first:
- DB dump: `corbit-prod:/root/jira-backups/jira-db-20260705-031736.sql`
- Previous jar: `corbit-prod:/root/jira-backups/installed-plugins-20260705-031613/corbitchat-jira-dc-1.0.0-mobile-s11-fix3.jar`
- nginx config: `corbit-prod:/root/jira/nginx/conf.d/jira.conf.bak-before-mobile-20260705-031613`

Rollback: restore the previous jar + restart (plugin key/AO schema unchanged, so
data is preserved), and restore the nginx `.bak` + reload. See
`docs/deployment-production.md`.

## 7. Add to Home Screen — iPhone user guide

1. Open **Safari** on iPhone and go to `https://jira.7gtech.net/mobile/`
   (DEV: `https://jira.corbitlogic.com/mobile/`).
2. Sign in with your Jira username and password (no server field — it is
   fixed to this site).
3. Tap the **Share** button → **Add to Home Screen** → **Add**.
4. Launch **Hub** from the Home Screen: it opens full-screen (standalone),
   app-like, portrait.
5. To sign out: **More → Log out** (clears the session cookie and returns to
   login).

## 8. Known limitations (PWA-01)

- No camera/mic/voice/file attachments, no native share, no push/local
  notifications, no biometric unlock, no offline Jira data cache (all deferred
  per PWA-01 non-goals).
- No URL deep links / scroll-to-target on web; refresh returns to app root.
- Avatars/branding images render via same-origin authenticated requests; where
  an image requires Jira's own web session they fall back to initials.
- Single static manifest (Hub brand). A 7G TECH web manifest/build is a
  follow-up.
- iOS Safari standalone PWAs have platform limits (background push, storage
  eviction) — expected, out of scope.

## 9. Verification (DEV, 2026-07-05)

Backend (curl, real user `m.nasiri` over HTTPS):
- Login → `200` + `Set-Cookie corbitchat-pwa-session` (HttpOnly, SameSite=Lax, Secure).
- Bootstrap with cookie → `200`; without cookie → `401` JSON.
- Mutation (PUT /preferences) with cookie but no marker → `401`; with marker → `200`.
- Logout → clears cookie (`Max-Age=0`); post-logout bootstrap → `401`.
- Native header auth regression → bootstrap `200`, mutation without marker `200`.
- `Cache-Control: no-store` present on BFF responses.

Frontend/build:
- `flutter analyze` clean; `flutter test` 14/14 pass (incl. login render).
- `flutter build web` OK; served at `/mobile/` — index, main.dart.js, canvaskit,
  manifest, service worker, asset manifests all `200`; SPA fallback OK.
- Android Hub release APK builds: `corbitchat-mobile-hub-1.0.0+27-s11.5-pwa1.apk`.

Production (curl, real user `m.nasiri`, 2026-07-05):
- Installed jar = `corbitchat-jira-dc-1.0.0-mobile-s11.5-pwa1.jar` (exactly one).
- `/mobile/` + assets (main.dart.js, manifest, AssetManifest.bin.json,
  canvaskit.wasm, icons) all `200`; SPA fallback `200`; index/SW `no-cache`;
  SW contains zero `/rest/` references.
- Cookie login `200` + Set-Cookie (HttpOnly, SameSite=Lax, Secure);
  bootstrap/dashboard/boards/chat conversations `200`; `no-store` present.
- CSRF: mutation without marker `401`, with marker `200`.
- PWA attachment upload (multipart bytes) `200` (message + attachment created),
  without marker `401`; test message deleted (cleanup).
- Logout clears cookie; post-logout bootstrap `401`.
- Native header auth regression: bootstrap `200`, mutation without marker `200`,
  logout `200`.

**Why production showed nothing before Fix-1:** the plugin and web bundle had
only ever been deployed to DEV. Production still ran `1.0.0-mobile-s11-fix3`
(no cookie-auth) and had no `/mobile/` nginx location or web bundle, so
`https://jira.7gtech.net/mobile/` returned a redirect/404. Fix-1 deployed both.

Owner steps (need a real device/browser): open in iPhone Safari, log in through
the UI, Add to Home Screen, launch standalone, navigate tabs, upload a chat
attachment, open/download an attachment, log out.

## 11. Fix-2 — branding, media, chat parity

### Branded builds (one codebase, two brands)
- `web/brand/hub/` and `web/brand/sevengtech/` each hold a brand `manifest.json`,
  `index.html`, and `icons/` (generated from the brand logo by
  `tool/brand/gen_web_icons.dart`). `scripts/build_web.sh <hub|sevengtech>`
  copies the brand shell into `web/` and builds with `--dart-define=BRAND=<brand>`
  so the Home Screen name/icon can never mismatch the in-app identity.
- 7G TECH icons are generated from the customer logo
  (`jira-issue-chat-panel/logos copy-cut-s.png`); the branded login/About logo is
  the bundled `assets/branding/app_logo_7gtech.png` selected by `BRAND=sevengtech`.
- **DEV serves Hub** (`jira.corbitlogic.com/mobile/`), **PROD serves 7G TECH**
  (`jira.7gtech.net/mobile/`). Server-provided Jira branding remains a separate
  concern and does not override the app identity.

### Chat data parity — root cause
Not a backend bug: on production, cookie-auth and native header-auth return the
**identical** conversation list for the same user (verified: 21 conversations
each, same bootstrap user `m.nasiri`). The reported "no chats" was a client
app-shell issue; the fix is **service-worker cache-busting** — `index.html`,
`flutter_service_worker.js`, and `flutter_bootstrap.js` are all served
`Cache-Control: no-cache`, so a redeploy (or the branded build) is picked up on
the next load instead of a stale shell being served. Chat errors are surfaced by
the UI (`ErrorView`), never a silent empty list. The service worker still caches
only the app shell (zero `/rest/` references).

### Media (Fix-2)
- Voice: `lib/core/web/web_files_web.dart` `WebAudioRecorder` (MediaRecorder +
  getUserMedia), feature-detected via `webVoiceRecordingSupported()`. Uploaded as
  a `voice=true` attachment through the existing multipart endpoint.
- Image: `webTransformImage` (rotate/crop via canvas, wired into the existing
  `ImageEditorScreen`) and `webCompressImage` (canvas resize → JPEG). Cancel
  stages/sends nothing; the edited/compressed bytes are what upload.

## 12. Web Push — status (deferred)

Not implemented in Fix-2, and **not shown as a fake control** (the push-settings
tile stays hidden on web). It is a real, supported capability (modern browsers;
iOS 16.4+ requires the PWA installed to Home Screen), so it is scoped as the next
increment rather than "unsupported":

- Reuse the plugin's existing VAPID + `JimPushService` / `JimPushSubscription`.
- Add cookie-authenticated BFF endpoints under `/rest/corbit-mobile/1.0/push/*`
  (`vapid-key`, `subscribe`, `unsubscribe`, `test`) that store the browser
  `PushSubscription` against the authenticated user and reuse the push sender.
- Add a dedicated push service worker (separate from Flutter's app-shell SW; the
  app-shell SW must keep caching zero API data) and a Flutter web
  subscribe/unsubscribe flow triggered by a direct user gesture, feature-detected.
- Requires a plugin version bump + DEV-then-PROD plugin redeploy (with backups).

## 13. Fix-2 verification (2026-07-05)

DEV (`jira.corbitlogic.com`): Hub manifest/title; `flutter analyze` clean;
`flutter test` 14/14; both brand bundles build; `/mobile/` + assets 200;
`flutter_bootstrap.js` + SW `no-cache`.

PROD (`jira.7gtech.net`): manifest/short_name/title/apple-title all **7G TECH**;
branded icons served; `flutter_bootstrap.js` `no-cache`; SW zero `/rest/` refs;
cookie login → 21 conversations (== native header auth); native header
regression bootstrap 200 / 21 conversations; exactly one plugin jar
(`1.0.0-mobile-s11.5-pwa1`, unchanged — Fix-2 is web-only).

Android regression: `corbitchat-mobile-hub-1.0.0+28-s11.5-pwa2.apk` builds green.

Owner device steps (need a real iPhone/browser): install to Home Screen from
`jira.7gtech.net/mobile/` and confirm the **7G TECH** name/icon; log in and
confirm existing chats appear; record a voice note; pick an image, crop/rotate,
send compressed and original; open/download an attachment; log out.
Nginx backups: `corbit-prod:/root/jira/nginx/conf.d/jira.conf.bak-before-7gbrand-*`.
