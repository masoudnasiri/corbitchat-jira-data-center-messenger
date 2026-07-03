# CorbitChat / CorbitHub — Agent Handover (state as of 2026‑07‑03)

You are the new engineering agent taking over **CorbitChat** (the Jira Data
Center plugin) and **CorbitHub** (the Flutter mobile app). You have full
access to both repos and to the dev/test and production servers. This document
is your single starting point: read it fully before touching anything.

---

## 1. Product in one paragraph

CorbitChat is a P2 plugin for **Jira Data Center 9.17.x** that adds a team
messenger (direct/group/project chat, Jira Assistant, notifications, boards,
comment replies) rendered server-side. CorbitHub is the **Flutter mobile
client** (Android first) that talks to a **Mobile BFF** built inside the same
plugin at `/rest/corbit-mobile/1.0/*`. The BFF wraps the same services and
JSON mapper the web surface uses, so web and mobile stay identical, and it is
the only path that understands the mobile session token. Do not change the
plugin key, the Active Objects schema, or existing `/rest/jim/1.0/*` contracts.

---

## 2. Repositories

| Repo | Path | Remote | Default branch |
|---|---|---|---|
| Plugin (CorbitChat) | `/root/jira-dev/jira-issue-chat-panel` | `github.com/masoudnasiri/corbitchatforjira` | work is on `feature/reply-composer-attachments` |
| Mobile (CorbitHub) | `/root/jira-dev/corbitchat-mobile` | `github.com/masoudnasiri/corbithub` | `main` |

- Plugin key (NEVER change): `com.corbitlogic.corbitchat.jira.dc`.
- Mobile BFF Java lives under `com.corbitlogic.jira.internalmessenger.mobile`.
- Read `AGENTS.md` (plugin repo root) first — it is the authoritative build/
  install/test cheat-sheet and is always applied.
- Remotes are stored **token-less**; never commit or echo credentials/PATs into
  a remote URL, a file, or logs.

### Key plugin files
| File | Role |
|---|---|
| `src/main/resources/atlassian-plugin.xml` | web-resources, servlets, REST modules, servlet-filters |
| `.../mobile/rest/CorbitMobileChatResource.java` | Mobile BFF direct-chat + attachments |
| `.../mobile/CorbitMobileSessionFilter.java` | resolves `X-CorbitChat-Session` → impersonates the Jira user |
| `.../mobile/MobileSessionSupport.java` | `SESSION_HEADER = "X-CorbitChat-Session"` |
| `.../rest/JimRestJsonMapper.java` | shared web/mobile JSON mapper (loads attachments/reactions/replies) |
| `.../attachment/JimAttachmentStreaming.java` | shared Range/RFC‑6266 file streaming (BFF-only use) |
| `.../attachment/JimMultipartParser.java` | multipart upload parser |
| `.../service/JimAttachmentService.java` | upload + permission-checked lookup |
| `src/main/resources/templates/messenger.vm`, `css/jim-messenger.css`, `js/jim-messenger.js` | web chat UI |

### Key mobile files
| File | Role |
|---|---|
| `lib/core/app_config.dart` | base URL, API paths, app version/build label |
| `lib/api/api_client.dart` | dio client, auth interceptor, multipart upload, byte download |
| `lib/api/corbit_api.dart` | typed BFF client |
| `lib/features/chat/conversation_screen.dart` | chat thread + composer (attach menu, mic/voice) |
| `lib/features/chat/widgets/attachment_view.dart` | in-thread image/file/audio cards, image send-size choice |
| `lib/features/chat/share_target_screen.dart` | Android share-sheet destination + send |
| `lib/core/share/attachment_channel.dart` | Dart↔native MethodChannel (`corbitchat/share`) |
| `android/app/src/main/kotlin/.../MainActivity.kt` | native share receive, file pick, image compress, camera, voice recorder, FileProvider open |
| `lib/core/strings.dart` | en/fa strings |

---

## 3. Environments

There are two single-node Docker deployments (each: `jira-srv` +
`nginx-jira` + `mysql-jira`, image `haxqer/jira:9.17.5`).

| | Dev / Test | Production |
|---|---|---|
| Public URL | `https://jira.corbitlogic.com` | `https://jira.7gtech.net` |
| Direct Jira | `http://185.83.181.194:8080` | `http://193.162.129.56:8080` |
| Access | local `docker` on this build host | SSH alias `corbit-prod` (key `~/.ssh/corbit_prod_193_162_129_56`) |
| Plugin dir | `/var/jira/plugins/installed-plugins/` inside `jira-srv` | same, inside prod `jira-srv` |
| Test login | `m.nasiri` / `Man@782761` (also `a.hesari`,`z.zarhoon` / `123456`) | `m.nasiri` (prod password provided by owner, **not stored in repo**); admin `admin1` |

- **Login endpoint requires HTTPS** (`POST /rest/corbit-mobile/1.0/auth/login`),
  so functional tests must hit the public HTTPS URL, not `http://IP:8080`.
- Do NOT use `-it` with `docker exec` (no TTY here).
- Production has **real users** — always test on dev first, back up before prod,
  and only deploy prod with a version bump + rollback artifact + approval.
- The full production runbook is `docs/deployment-production.md` — follow it.

---

## 4. How development works (the loop I use)

1. Make code changes in the relevant repo.
2. Build + static-check locally (see §5). Fix all analyzer/lint errors.
3. Deploy to **dev/test** and verify live with authenticated HTTP calls, not
   just source review or route pings (see §6–§7).
4. For mobile UI/native features, build the APK and have the physical device
   tested — native camera/mic/share can't be driven from here.
5. Only after live verification (or explicit approval) deploy to **production**
   with backup + rollback, then re-verify on prod.
6. Commit/push only when the work is verified or the owner approves closure.
   Keep plugin and mobile commits in their own repos; one logical sprint per
   commit, message style `feat(scope): Sprint NN — summary`.

Guardrails that matter: never change the plugin key or AO schema for UI work;
CSS scoped under `#jim-messenger-app`; preserve JS element IDs; no fake
controls; never log tokens/passwords/session headers; mobile must not rely on
Jira browser cookies; never store raw passwords.

---

## 5. Build commands

### Plugin (offline Maven — these exact flags, or it fails)
```bash
cd /root/jira-dev/jira-issue-chat-panel
mvn -s /root/.m2/settings.xml -Dmaven.repo.local=/root/maven-repos/atlassian-jira-offline -o clean package
```
Artifact: `target/corbitchat-jira-dc-<version>.jar` (deploy this, not `*-obr.jar`). ~25–30 s.

### Mobile (offline pub mirrors)
```bash
cd /root/jira-dev/corbitchat-mobile
export PUB_HOSTED_URL=https://pub.flutter-io.cn
export FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn
flutter analyze          # must be clean
flutter test             # must be green
flutter build apk --release   # build/app/outputs/flutter-apk/app-release.apk (~57MB)
```
Offline pub cache is limited: **new Flutter packages usually aren't available**.
Prefer native Kotlin over `MethodChannel('corbitchat/share')` for device
capabilities (that is how share/pick/compress/camera/voice are implemented).

---

## 6. Deploy

### Dev/Test (this host)
```bash
cd /root/jira-dev/jira-issue-chat-panel
docker exec -u 0 jira-srv bash -lc 'rm -f /var/jira/plugins/installed-plugins/corbitchat-jira-dc-*.jar /var/jira/plugins/installed-plugins/jira-internal-messenger-*.jar'
docker cp target/corbitchat-jira-dc-<version>.jar jira-srv:/var/jira/plugins/installed-plugins/
docker exec -u 0 jira-srv bash -lc 'chmod 644 /var/jira/plugins/installed-plugins/corbitchat-jira-dc-<version>.jar'
docker restart jira-srv     # ~2–3 min boot; health flips 503 → 200
```

### Production (summary of `docs/deployment-production.md`)
1. `ssh corbit-prod 'docker ps'` — confirm 3 containers up.
2. Bump `pom.xml` version; `mvn ... -o clean package`; copy jar to `/root/jira-dev/releases/`.
3. **Backup**: DB dump (creds from mysql container env) + copy of
   `installed-plugins/` into `/root/jira-backups/…`.
4. Install (Path B: `scp` jar → prod, remove old jars, `docker cp`, chmod) then
   `ssh corbit-prod 'docker restart jira-srv'`. (Path A = UPM upload, no restart.)
5. Keep exactly ONE `corbitchat-jira-dc-*.jar` in the plugin dir.
6. Rollback = restore the previous jar from the backup and restart.

Prod boot is slower (~5 min; larger DB). Poll health until 200 before testing.

---

## 7. How to verify a deploy (do this every time)

Endpoint smoke (expect all 200; `bootstrap`/`preferences` need an auth user):
```bash
BASE='https://jira.7gtech.net'; AUTH='USER:PASS'
for p in /rest/jim/1.0/health /rest/jim/1.0/ao-health \
         /rest/corbit-mobile/1.0/health /rest/corbit-mobile/1.0/ao-health \
         /rest/corbit-mobile/1.0/bootstrap ; do
  echo "$p => $(curl -s -k -u "$AUTH" -o /dev/null -w '%{http_code}' "$BASE$p")"
done
```

Attachment end-to-end (the Sprint 07 Fix-1 regression, run against HTTPS):
1. `POST /rest/corbit-mobile/1.0/auth/login` `{username,password}` → grab `token`.
2. `POST /rest/corbit-mobile/1.0/chat/conversations/self` with header
   `X-CorbitChat-Session: <token>` → get Saved Messages `id`.
3. `POST /rest/corbit-mobile/1.0/chat/conversations/<id>/attachments` multipart
   `file=@x.png` → expect 200; response `attachments[].downloadUrl/previewUrl`
   must be `/rest/corbit-mobile/1.0/chat/attachments/<id>/...`.
4. `GET` those download/preview URLs with the session header → expect 200.
5. Sanity: the same file against `/rest/jim/1.0/...` with only the session
   header must return **401** (that path is web-auth only — that was the bug).
6. Clean up: soft-delete the test message via
   `DELETE /rest/corbit-mobile/1.0/chat/messages/<messageId>`.

Confirm deployed version: `docker exec -u 0 jira-srv ls /var/jira/plugins/installed-plugins/corbitchat-jira-dc-*.jar`.

Never claim a UI fix works from source alone — check the live page/bundle and,
for mobile, the physical device.

---

## 8. Roadmap & sprint documents

Roadmap index: `docs/mobile-roadmap/README.md` (Phases 0–4, Sprints 00–14).
Each `docs/mobile-roadmap/sprint-NN-*.md` has scope, API contracts, acceptance
criteria and a DoD. Other must-reads: `docs/architecture.md` (features, data
model, REST, security), `docs/attachments.md`, `docs/Mobile Design System/`
(tokens, RTL/i18n, component specs), and the prior handovers
`docs/handover-mobile-app.md` / `docs/handover-mobile-app-prompt-full.md`.

**Implementation status (reality, not the plan):**

| Sprint | Topic | Status |
|---|---|---|
| 00–04G | Foundation, Auth/BFF, Direct Chat, Dashboard/Tasks, Issue Detail/Projects, avatars/dates, board gallery, feature access | Done, committed |
| 05 / 05B (+Fix‑1) | Push notifications, rich push UX, notification privacy | Done, committed |
| 06 (+Fix‑1/2/3) | Rich chat: reply/edit/delete/forward, Message Info, seen, swipe‑to‑reply, Saved Messages, multi‑recipient forward, Android text share | Done, committed |
| **07 (+Fix‑1)** | **Attachments, image size choice, Telegram composer, Android image/file share, camera, voice** | **Done + deployed (dev+prod) — NOT yet committed** |
| 08–14 | Group/Project + Assistant, comments/replies, transitions/worklog/field rules, boards, productivity/deeplinks/biometrics, hardening, release QA | **Planned docs only — not implemented** |

Production plugin version is currently **`1.0.0-mobile-s07-fix1`**; mobile app is
**`1.0.0+8`**, build label **`s07-fix1`**.

---

## 9. Current repo state (uncommitted)

Both repos have Sprint 07 + Fix‑1 changes **uncommitted** (the code is live on
both servers but not yet in git). Last commits: plugin =
"Sprint 06 Fix‑1/Fix‑2 backend"; mobile = "Sprint 06 + Fix‑1/2/3".

Plugin working tree (uncommitted):
- `M pom.xml` (version → `1.0.0-mobile-s07-fix1`)
- `M src/main/java/.../mobile/rest/CorbitMobileChatResource.java`
- `?? src/main/java/.../attachment/JimAttachmentStreaming.java`
- `M docs/mobile-roadmap/sprint-07-attachments-voice.md`
- `?? dist/` (built jar + rollback jar — do NOT commit binaries)

Mobile working tree (uncommitted): `AndroidManifest.xml`, `MainActivity.kt`,
`res/xml/file_paths.xml`, `api/api_client.dart`, `api/corbit_api.dart`,
`app.dart`, `core/app_config.dart`, `core/share/share_intent.dart`,
`core/share/attachment_channel.dart`, `core/strings.dart`,
`features/chat/conversation_screen.dart`,
`features/chat/widgets/attachment_view.dart`,
`features/chat/share_target_screen.dart`, `models/chat.dart`, `pubspec.yaml`
(version `1.0.0+8`), plus `dist/` (APK — do NOT commit).

Artifacts on disk: plugin jar
`target/corbitchat-jira-dc-1.0.0-mobile-s07-fix1.jar` (and copy in
`/root/jira-dev/releases/`); APK
`corbitchat-mobile/dist/corbithub-1.0.0+8-s07-fix1.apk`; prod backups in
`corbit-prod:/root/jira-backups/`.

**Immediate next step:** once the owner confirms on-device acceptance of Sprint
07 Fix‑1, create the closure commits in both repos (exclude `dist/`; keep
plugin/mobile changes in their own repos) and push. Do not commit before that.

---

## 10. The last sprint in full — Sprint 07 + Sprint 07 Fix‑1

### 10.1 Sprint 07 — Attachments, image size choice, Telegram composer, share
Goal: usable mobile attachment workflows. Delivered (mobile-only at the time):
- Android share‑sheet accepts images/files (`ACTION_SEND` intent filters +
  `FileProvider`, `res/xml/file_paths.xml`).
- `Attachment` model on `ChatMessage`; in‑thread rendering (image previews via
  authenticated `avatarImage`, file cards, full-screen image viewer).
- Composer attach button + pending-attachment preview + reactive send button.
- Image "Original vs Compressed" choice; compression done natively
  (`BitmapFactory`) via `MethodChannel('corbitchat/share')` because the
  relevant Flutter packages are not in the offline pub cache.
- File download/open via native download-to-cache + `FileProvider` `ACTION_VIEW`.
- Saved Messages supported through the same path.
All device capabilities are native Kotlin (offline constraint). Single-file
only; multi-file deferred.

### 10.2 Sprint 07 Fix‑1 — the critical bug + camera + voice
Physical-device testing showed **every** image/file upload (composer and share,
original and compressed) failing with "session expired", and re-login didn't
help. Also requested: camera capture and voice messages.

**Root cause (confirmed in code + live):** a BFF gap / endpoint‑auth mismatch.
Sprint 07 uploaded/downloaded via the legacy web endpoints `/rest/jim/1.0/*`.
The mobile session filter `CorbitMobileSessionFilter` is registered **only** for
`<url-pattern>/rest/corbit-mobile/1.0/*</url-pattern>` (see
`atlassian-plugin.xml`). So a username/password mobile session (which
authenticates via the `X-CorbitChat-Session` header) is **anonymous** on
`/rest/jim/1.0/*` → the resource returns 401 → the client mapped it to "session
expired". PAT logins would have worked there (Jira resolves PAT natively on
every path); username/password could not, and re‑login can never fix it. The
shared mapper also emits `/rest/jim/1.0/...` attachment URLs, so preview/download
failed for the same reason. Classification: **BFF gap + response mapping**, not
backend permission logic, not a UI-text issue.

**Fix — required backend changes (safe, additive):** a Mobile BFF attachment
proxy on the already session-authenticated path, reusing the same services and
policy — the web `/rest/jim/1.0/*` resource is untouched:
- `POST /rest/corbit-mobile/1.0/chat/conversations/{id}/attachments` — multipart
  upload via `JimMultipartParser` + `JimAttachmentService.uploadAttachment`,
  enforcing participant check, direct‑chat policy, allow‑list, size limit, and
  the `canUploadAttachments()` license gate.
- `GET /rest/corbit-mobile/1.0/chat/attachments/{id}/download` and `/preview` —
  permission-checked via `getAttachmentForUser`, streamed by the new
  `JimAttachmentStreaming` helper (verbatim extraction of the web streamer's
  Range + RFC‑6266/5987 logic; BFF-only, so web behavior is unchanged).
- BFF message payloads rewrite `downloadUrl`/`previewUrl` to the BFF paths
  (mirrors the existing avatar-proxy rewrite; the shared mapper is not changed,
  so the web surface keeps its own URLs). Applied in list/send/pinned/okMessage.

**Mobile client:** `corbit_api.uploadAttachment` now targets the BFF chat path;
download/preview URLs arrive already rewritten and load via the authenticated
client — everything now goes through the session-aware path.

**Camera:** native `ACTION_IMAGE_CAPTURE` + `FileProvider` output (no `CAMERA`
permission needed for this app). Composer attach → bottom sheet (Camera / Photo
or file); captured photo stages as a preview, supports original/compressed,
cancel/remove, uploads through the same real path.

**Voice:** native `MediaRecorder` (AAC/MP4 → `audio/mp4`, already in the
allow-list; the service skips extension checks for AUDIO). Mic button shows when
the composer is empty → runtime `RECORD_AUDIO` request → recording bar (timer,
discard, send). Uploads through the same attachment path; renders in-thread as a
"Voice message" audio card. **Limitation (documented):** no in-thread playback
yet — tapping downloads and opens with the Android system player. Permission
denial shows a clear, recoverable message.

**Files changed** — Plugin: `mobile/rest/CorbitMobileChatResource.java`, new
`attachment/JimAttachmentStreaming.java`, `pom.xml`, sprint doc. Mobile:
`api/corbit_api.dart`, `api/api_client.dart`, `core/app_config.dart`,
`core/share/attachment_channel.dart`, `core/share/share_intent.dart`, `app.dart`,
`core/strings.dart`, `features/chat/conversation_screen.dart`,
`features/chat/widgets/attachment_view.dart`,
`features/chat/share_target_screen.dart`, `models/chat.dart`, `pubspec.yaml`,
`android/.../MainActivity.kt`, `AndroidManifest.xml`, `res/xml/file_paths.xml`.

**Verification done:** `flutter analyze` clean; `flutter test` 8/8; APK built;
plugin `mvn -o clean package` BUILD SUCCESS; sensitive-data scan clean (no
secrets, no token/auth logging). Deployed to **dev** then **production**;
attachment end-to-end functional test (login → upload → download → preview,
plus the legacy-path‑401 proof) passed **9/9 on both**, with prod plugin
version confirmed `s07-fix1`. Smoke-test artifacts were cleaned up.

**Still needs on-device acceptance (native, can't be driven from here):**
camera capture, voice record/permission flows, Android share-sheet, composer UI,
in-thread rendering, and the full regression list (text/reply/edit/delete/
forward, dashboard unread, push). Install
`corbitchat-mobile/dist/corbithub-1.0.0+8-s07-fix1.apk` and confirm no false
"session expired" loop. If a device step fails now, it's a client/native issue
(the server contract is proven on prod), not the auth bug.

---

## 11. First actions for you (the new agent)
1. Read `AGENTS.md`, this file, `docs/deployment-production.md`, and
   `docs/mobile-roadmap/README.md`.
2. Confirm live state: run the §7 endpoint smoke + attachment e2e against dev
   and prod; confirm both report `s07-fix1`.
3. If Sprint 07 Fix‑1 is accepted on-device, do the closure commits/push for
   both repos (exclude `dist/`), then continue with the next planned sprint
   (08 — Group/Project Chat + Assistant) using its sprint doc.
