# CorbitChat / Hub — Agent Handover (state as of 2026-07-04)

You are the new engineering agent taking over **CorbitChat** (the Jira Data
Center plugin) and **Hub** (the Flutter mobile app, internally "CorbitHub").
You have full access to both repos, the dev/test server (local Docker) and
the production server (SSH). This document is the single source of truth for
the CURRENT state: read it fully before touching anything.

---

## 1. Product in one paragraph

CorbitChat is a P2 plugin for **Jira Data Center 9.17.x** adding a team
messenger (direct/group/project chat, Jira Assistant, mentions, reactions,
attachments, boards, comment replies) rendered server-side. **Hub** is the
**Flutter mobile client** (Android first) talking to a **Mobile BFF** inside
the same plugin at `/rest/corbit-mobile/1.0/*`. The BFF wraps the same
services and JSON mapper as the web surface so web and mobile stay identical;
it is the only path that understands the mobile session token
(`X-CorbitChat-Session`). Never change the plugin key, the AO schema
destructively, or existing `/rest/jim/1.0/*` contracts.

---

## 2. Repositories

| Repo | Path | Remote | Branch |
|---|---|---|---|
| Plugin (CorbitChat) | `/root/jira-dev/jira-issue-chat-panel` | `github.com/masoudnasiri/corbitchatforjira` | `feature/reply-composer-attachments` |
| Mobile (Hub) | `/root/jira-dev/corbitchat-mobile` | `github.com/masoudnasiri/corbithub` | `main` |

- Plugin key (NEVER change): `com.corbitlogic.corbitchat.jira.dc`.
- Mobile BFF Java lives under `com.corbitlogic.jira.internalmessenger.mobile`.
- `AGENTS.md` (plugin repo root) is the always-applied build/install/test
  cheat-sheet.
- **Push is currently BLOCKED**: the build host has no GitHub credential
  (token-less HTTPS remotes, no GitHub SSH key). Local commits exist and are
  safe; ask the owner for a PAT/deploy key, or have them push.

### Current git state (IMPORTANT)

Committed (local, NOT pushed — GitHub push still blocked, see above):
- Plugin: `e70b36c` (Sprint 07 Fix-1..4 attachments/voice/video/vcard),
  `0190013` (branding endpoint + bootstrap block), `ebfb722` (Sprint 08
  +Fix-1..3 group/project chat, mentions, forward), `d0db225` (docs refresh).
- Mobile: `6e47a2e` (Sprint 07 media), `7b41c41` (branding + Hub rename),
  `d0bbfa2` (Sprint 08 +Fix-1..3 group/project chat, mentions, Assistant, polish).

Sprint 08 (+Fix-1..3) was accepted on-device (2026-07-04) and is now committed
locally in both repos; it still needs to be **pushed** once a GitHub credential
is available. `dist/` is gitignored in both repos; never commit APKs, jars,
secrets, google-services.json, Firebase admin JSON, or keystores.

---

## 3. Environments

Two single-node Docker deployments (each: `jira-srv` + `nginx-jira` +
`mysql-jira`, image `haxqer/jira:9.17.5`).

| | Dev / Test | Production |
|---|---|---|
| Public URL | `https://jira.corbitlogic.com` | `https://jira.7gtech.net` |
| Direct Jira | `http://185.83.181.194:8080` | `http://193.162.129.56:8080` |
| Access | local `docker` on this build host | SSH alias `corbit-prod` (key `~/.ssh/corbit_prod_193_162_129_56`) |
| Plugin dir | `/var/jira/plugins/installed-plugins/` in `jira-srv` | same |
| Plugin version NOW | `1.0.0-mobile-s08-fix3` | `1.0.0-mobile-s08-fix3` |
| Test users | `m.nasiri` (see AGENTS.md), `a.hesari`/`z.zarhoon` (pwd `123456`) | `m.nasiri` + `admin1` (owner provides passwords; NOT stored in repo) |

- Password login requires HTTPS → functional tests hit the public HTTPS URLs.
- Production has REAL users. Dev first, backup before prod, deploy prod only
  with explicit owner approval, never same-version redeploy.
- Full prod runbook: `docs/deployment-production.md` (boot can take
  **5–15 minutes** — poll patiently before assuming failure).
- Do NOT use `-it` with `docker exec` (no TTY).

## 4. Versions & artifacts (as of this handover)

- **Mobile app:** `1.0.0+17`, build label **`s08-fix3`** —
  `corbitchat-mobile/dist/corbithub-1.0.0+17-s08-fix3.apk`.
- **Plugin:** `1.0.0-mobile-s08-fix3` live on BOTH dev and prod (verified
  2026-07-04: smoke 7/7, mobile login, assistant endpoint, group list).
- Rollback jars for every prior version: `/root/jira-dev/releases/` and
  `corbit-prod:/root/jira-backups/installed-plugins-<TS>/` (+ DB dumps).

---

## 5. How development works (the loop)

1. Change code → build + static-check (see §6). Fix all analyzer errors.
2. Deploy to **dev** and verify live with authenticated HTTP calls (curl with
   a real mobile session token), never source-review only.
3. Mobile UI/native features additionally need the **physical device** —
   camera/mic/share/push cannot be driven from this host.
4. Backend changes: bump `pom.xml` to a traceable version (`s09`, `s09-fix1`,
   …), deploy dev, verify, then WAIT for owner approval before prod
   (backup → single jar → restart → verify per runbook).
5. Commit/push only when the owner accepts the sprint. One logical sprint per
   commit, style `feat(scope): Sprint NN — summary`.

Guardrails: plugin key + `/rest/jim/1.0` contracts stable; web CSS scoped
under `#jim-messenger-app`; preserve JS element IDs; no fake controls; never
log tokens/passwords; AO table names ≤ 30 chars; new AO columns must be
nullable (non-destructive upgrade).

## 6. Build commands

### Plugin (offline Maven — exact flags or it fails; ~25 s)
```bash
cd /root/jira-dev/jira-issue-chat-panel
mvn -s /root/.m2/settings.xml -Dmaven.repo.local=/root/maven-repos/atlassian-jira-offline -o clean package
```
Artifact: `target/corbitchat-jira-dc-<version>.jar` (never the `*-obr.jar`).

### Mobile (Flutter 3.44 at `/root/flutter/bin`; offline pub mirrors)
```bash
cd /root/jira-dev/corbitchat-mobile
export PATH=/root/flutter/bin:$PATH
export PUB_HOSTED_URL=https://pub.flutter-io.cn
export FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn
flutter analyze && flutter test && flutter build apk --release
```
**New Flutter/Gradle dependencies are generally NOT available offline** —
implement device capabilities in native Kotlin over the existing
`MethodChannel('corbitchat/share')` (that is how share/pick/compress/camera/
video-capture/voice/recorder/player/transcoder/contacts/location/SAF/share-out
are all done). Kotlin gotcha: block comments NEST — never put `/*` sequences
(e.g. MIME wildcards) inside comments.

## 7. Verify a deploy (every time)

```bash
BASE='https://jira.corbitlogic.com'; AUTH='m.nasiri:<pwd>'   # or prod URL
for p in /rest/jim/1.0/health /rest/jim/1.0/ao-health \
         /rest/corbit-mobile/1.0/health /rest/corbit-mobile/1.0/ao-health \
         /rest/corbit-mobile/1.0/bootstrap /rest/corbit-mobile/1.0/branding \
         /plugins/servlet/jim/chat; do
  echo "$p => $(curl -s -k -u "$AUTH" -o /dev/null -w '%{http_code}' "$BASE$p")"
done
```
All 200. For chat features, log in via
`POST /rest/corbit-mobile/1.0/auth/login {username,password}` → use the
`token` in `X-CorbitChat-Session` and exercise the real endpoints (see §8).
Clean up any test messages/groups you create (`DELETE .../chat/messages/{id}`,
`DELETE .../chat/conversations/{id}/group`).

## 8. Mobile BFF surface (all under `/rest/corbit-mobile/1.0`)

Auth: `auth/login|logout|session`. App: `bootstrap` (profile, features,
preferences, unread, branding), `preferences`, `branding` (anonymous),
`health`, `ao-health`, `dashboard`, `issues/*`, `projects/*`, `boards`,
`devices/*` (push), `avatars/*`.

Chat (`/chat/...`): `conversations` (DIRECT+GROUP+PROJECT),
`conversations/self|direct|group`, `conversations/{id}/group` (DELETE),
`conversations/{id}/members` (GET/POST/DELETE — owner rules, self-leave),
`conversations/{id}/messages` (GET/POST), `conversations/{id}/read`,
`conversations/{id}/pinned`, `conversations/{id}/attachments` (multipart:
`file`, `body`, `voice`), `conversations/{id}/messages/{mid}/receipts`
(group receipts, own messages), `attachments/{id}/download|preview` (Range
supported), `messages/{id}` (PUT/DELETE), `messages/{id}/pin|reactions|
forward|action`, `assistant` (SYSTEM conversation), `users/search`,
`projects/{key}/conversation` (project chat + membership).

Semantics to know: mentions are plain-text `@DisplayName` matched server-side
against members → MENTION assistant item + push; forwards clone attachments
(physical file copies) and chain attribution to the original author; group/
project messages push to all members preference-aware (mentioned members get
only the mention push); `voice` flag separates voice notes from audio files;
`fileKind` ∈ IMAGE/AUDIO/VIDEO/FILE.

## 9. Feature status (reality)

| Area | Status |
|---|---|
| Sprints 00–06 (+fixes) | Done, committed & on both servers |
| Sprint 07 + Fix-1..4 (attachments, voice+speed, video+compression, contact/location, crop editor, save/share, panel) | Done, accepted, committed locally (NOT pushed) |
| Branding polish 1+2 (icon, server branding, login UX, "Hub" rename) | Done, accepted, committed locally (NOT pushed) |
| Sprint 08 + Fix-1..3 (groups, project chat, filters, mentions, emoji panel, Assistant, attachment forward, push fan-out, notif icon, contrast, scroll-to-latest) | Done, accepted, deployed dev+prod, committed locally (NOT pushed) |
| Sprints 09–14 (roadmap: `docs/mobile-roadmap/`) | Planned docs only |

Known limitations (documented, deliberate): no in-app video player (system
player), no typing indicator (web has none), chat-mention assistant cards
don't deep-link into the conversation (no pointer stored — web parity),
emoji recents are session-only, OSM map tiles need internet on the device.

## 10. First actions for you

1. Read `AGENTS.md`, this file, `docs/deployment-production.md`, and
   `docs/mobile-roadmap/README.md`.
2. Run the §7 smoke against dev AND prod; confirm both report
   `1.0.0-mobile-s08-fix3`.
3. Sprint 08 (+Fix-1..3) is accepted and committed locally (2026-07-04).
   Remaining closure step: resolve the GitHub push credential with the owner
   and push both repos (plugin `feature/reply-composer-attachments`, mobile
   `main`) — several local commits are ahead of origin.
4. Then continue with the next roadmap sprint — Sprint 09 (issue comments &
   reply-to-comment), guide at `docs/mobile-roadmap/sprint-09-comments-replies.md`.
