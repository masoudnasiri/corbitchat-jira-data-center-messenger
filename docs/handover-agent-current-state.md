# CorbitChat / Hub — Agent Handover (state as of 2026-07-05)

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
- **Push works with an owner-supplied token.** The build host has no stored
  GitHub credential, but the owner provides a classic PAT on request. Use it
  inline via a transient credential helper (env-based `credential.helper`),
  NEVER write it to `~/.git-credentials`, and remind the owner to rotate it
  after. The plugin remote was **renamed on GitHub** to
  `corbitchat-jira-data-center-messenger`; the old `corbitchatforjira` URL
  still redirects, so pushes to the recorded remote succeed.

### Current git state (IMPORTANT)

Both repos are **pushed and in sync with origin** as of 2026-07-05:
- Plugin `feature/reply-composer-attachments` → tip `c5c6ac0`
  (Sprint 11 Fix-1/Fix-3 mobile BFF), preceded by `f9d7895` (Sprint 10),
  `68788a2` (Sprint 09 +Fix-1).
- Mobile `main` → tip `50ec47b` (Sprint 11 board fixes + 7G TECH build
  variant + Sprint 11.4 UI polish), preceded by `f321ed6` (Sprint 10),
  `3061c35` (Sprint 09 +Fix-1).

`dist/`, `releases/`, and `build/` are gitignored; never commit APKs, jars,
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
| Plugin version NOW | `1.0.0-mobile-s11-fix3` | `1.0.0-mobile-s11-fix3` |
| Test users | `m.nasiri` (see AGENTS.md), `a.hesari`/`z.zarhoon` (pwd `123456`) | `m.nasiri` + `admin1` (owner provides passwords; NOT stored in repo) |

- Password login requires HTTPS → functional tests hit the public HTTPS URLs.
- Production has REAL users. Dev first, backup before prod, deploy prod only
  with explicit owner approval, never same-version redeploy.
- Full prod runbook: `docs/deployment-production.md` (boot can take
  **5–15 minutes** — poll patiently before assuming failure).
- Do NOT use `-it` with `docker exec` (no TTY).

## 4. Versions & artifacts (as of this handover)

- **Mobile app:** `1.0.0+26`, build label **`s11.4-uipolish`**. Built in two
  brand flavors from one codebase (see §11): `corbitchat-mobile-hub-1.0.0+26-s11.4-uipolish.apk`
  and `corbitchat-mobile-7gtech-1.0.0+26-s11.4-uipolish.apk`, both under
  `/root/jira-dev/releases/`. The About screen shows `AppConfig.appBuild` +
  `AppConfig.buildLabel` (Dart constants baked into the binary) — the
  definitive check that a device is on the intended build.
- **Plugin:** `1.0.0-mobile-s11-fix3` live on BOTH dev and prod. Sprint 11.4
  is **mobile-only** (design tokens, shared badge components, card/detail
  polish) — it required **no backend change**, so prod was not redeployed.
- Rollback jars for every prior version: `/root/jira-dev/releases/` and
  `corbit-prod:/root/jira-backups/installed-plugins-<TS>/` (+ DB dumps).
- **Temporary APK hand-off endpoint (may still be live):** the DEV nginx
  (`nginx-jira`) serves signed APKs at `https://jira.corbitlogic.com/apk/`
  (a `location /apk/` block in `/root/jira/nginx/conf.d/jira.conf`, alias
  `/usr/share/nginx/apk/`, with `application/vnd.android.package-archive` +
  `Content-Disposition: attachment`). This is how a phone reliably installs a
  new build. Offer to tear it down (restore `jira.conf`, delete staged APKs)
  once the owner confirms the device is updated.

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

### Mobile (Flutter at `/root/flutter/bin`; offline pub mirrors; JDK 17)
```bash
cd /root/jira-dev/corbitchat-mobile
export PATH=/root/flutter/bin:$PATH
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PUB_HOSTED_URL=https://pub.flutter-io.cn
export FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn
flutter analyze && flutter test
```

**Two brand flavors, one codebase** (7G TECH variant, §11). Always build
release APKs via the helper so the Android launcher icon/label (`--flavor`)
and the in-app identity (`--dart-define=BRAND`) can never mismatch:
```bash
scripts/build_apks.sh both        # -> Hub + 7G TECH into ../releases
scripts/build_apks.sh sevengtech  # only 7G TECH   (flavor=sevengtech)
scripts/build_apks.sh hub         # only Hub        (flavor=hub)
```
If a device shows an OLD build after "installing", it is almost always a
stale device-side install, NOT a bad build. Do a `flutter clean` rebuild,
then verify the artifact three ways: `aapt2 dump badging` (versionCode +
label), grep the build label string inside `lib/arm64-v8a/libapp.so`, and
serve it via the `/apk/` HTTPS endpoint with a fresh cache-busting filename.
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

Issues (Sprint 09/10): `issues/search`, `issues/{key}` (detail incl.
comments, attachments, worklog, permissions, field-display rules),
`issues/{key}/comments` (GET/POST + reply-to-comment, comment attachments,
`@DisplayName` mentions rewritten to `[~user]`), `issues/{key}/transitions`
(GET + execute, screen prompts for comment/resolution),
`issues/{key}/assignee` (assign/unassign; "assign to me" uses the bootstrap
username, NEVER native `/myself`), `issues/{key}/watching`,
`issues/{key}/worklog`, `issues/{key}/attachments` (add/delete),
`issues/{key}/editmeta` + edit (summary/description/priority).

Boards & projects (Sprint 11 +Fix-1/Fix-3): `boards` (RapidView gallery),
board columns from GreenHopper `ColumnService` (real lanes incl. **empty**
lanes, localized names via `I18nHelper`, status-fallback when unavailable),
board issues with a `mine` filter and per-lane status metadata (drives
drag-to-transition); `projects/{key}` overview (stats total/open/assignedToMe,
component/version counts), `projects/{key}/members` (roles), project activity
(recent updated issues), and the inline project chat via
`projects/{key}/conversation`.

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
| Sprints 00–06 (+fixes) | Done, committed, pushed & on both servers |
| Sprint 07 + Fix-1..4 (attachments, voice+speed, video+compression, contact/location, crop editor, save/share, panel) | Done, accepted, pushed |
| Branding polish 1+2 (icon, server branding, login UX, "Hub" rename) | Done, accepted, pushed |
| Sprint 08 + Fix-1..3 (groups, project chat, filters, mentions, emoji panel, Assistant, attachment forward, push fan-out) | Done, accepted, on dev+prod, pushed |
| Sprint 09 + Fix-1 (issue comments, reply-to-comment, comment attachments, mentions) | Done, accepted, on dev+prod, pushed |
| Sprint 10 (workflow transitions, worklog, edit, assign incl. assign-to-me fix, watch, issue attachments, field rules) | Done, accepted, on dev+prod, pushed |
| Sprint 11 + Fix-1/Fix-3 (board gallery→real lanes incl. empty lanes, drag-to-transition, project workspace tabs, inline project chat, board card filters) | Done, accepted, on dev+prod, pushed |
| 7G TECH branded build variant (Android flavors + `--dart-define=BRAND`, icon-gen, `build_apks.sh`) | Done, accepted, pushed (mobile-only; §11) |
| Sprint 11.4 mobile UX polish (semantic design tokens, shared `StatusBadge`/`PriorityBadge`/`KeyChip`, title-first cards, operational issue-detail header, prioritized action sheet, dashboard subtext, project polish) | Done, `1.0.0+26`, pushed (mobile-only — no backend deploy) |
| Sprints 12–14 (roadmap: `docs/mobile-roadmap/`) | Planned docs only |

Known limitations (documented, deliberate): no in-app video player (system
player), no typing indicator (web has none), chat-mention assistant cards
don't deep-link into the conversation (no pointer stored — web parity),
emoji recents are session-only, OSM map tiles need internet on the device.
Recommended but NOT done (documented for a future, tested change): bottom-nav
reorder to Dashboard · Tasks · Boards · Chat · More (risky — feature-flag
landing priority + persisted index + deep-link handoff).

## 11. 7G TECH branded build variant

One codebase produces two APKs. An Android **product flavor** picks the
launcher icon + app label (`android/app/src/<flavor>/res/**`,
`values/strings.xml` `app_name`), and a matching `--dart-define=BRAND` picks
the in-app identity (About/login/nav name, push fallback title, bundled login
logo) via `lib/core/brand.dart`. Flavors: `hub` (default, "Hub") and
`sevengtech` ("7G TECH"), same `applicationId` so they are not co-installable.
Icons are regenerated with `flutter_launcher_icons` +
`tool/brand/gen_icon_sources.dart`. Always build via `scripts/build_apks.sh`
so icon and in-app brand can never mismatch. Do NOT commit generated APKs or
icon binaries beyond the checked-in source assets.

## 10. First actions for you

1. Read `AGENTS.md`, this file, `docs/deployment-production.md`, and
   `docs/mobile-roadmap/README.md`.
2. Run the §7 smoke against dev AND prod; confirm both report
   `1.0.0-mobile-s11-fix3`.
3. Report preflight: git state of both repos (currently pushed & in sync),
   plugin version, and mobile version (`1.0.0+26` / `s11.4-uipolish`). Check
   whether the temporary `/apk/` download endpoint (§4) is still live and
   offer to tear it down.
4. Ask the owner for the current priority. The next planned roadmap sprint is
   Sprint 12 (productivity, deep links, biometrics),
   `docs/mobile-roadmap/sprint-12-productivity-deeplinks-biometrics.md`; PWA
   work has also been flagged as upcoming.
