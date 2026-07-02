# Handover — CorbitChat Mobile App (Jira Data Center + Chat)

**For:** the new agent picking up mobile-app development.
**From:** the agent that built the CorbitChat plugin (this repository).
**Purpose:** everything you need to start the mobile app without
having to reverse-engineer the plugin or the test environment.

---

## 1. What you're building

A **mobile app** that is a companion to the CorbitChat Jira Data
Center plugin **plus** a Jira-mobile-for-DC-style client. Feature
scope suggested:

**Chat side (from CorbitChat plugin — already implemented server
side):**

- Direct / group / project chats.
- Messages with reactions, replies, pinned, edit (30-min window),
  delete, forward, drafts, per-message timestamps, read receipts.
- Attachments (image, audio, file); voice messages; drag-drop is
  desktop-only, mobile equivalent is the OS share sheet + camera.
- Mentions, emoji picker.
- Web Push → **native mobile push** (FCM on Android, APNs on iOS).
- Jira Assistant bot conversation (mentions, assignments, status
  changes, issue links) with actioned/acknowledged state.

**Jira side (equivalent to Jira mobile for Data Center):**

- Issue browsing (by project, board, JQL, "assigned to me",
  "reported by me", search).
- Full issue view (fields, comments, attachments, activity, links).
- Comment / reply to comment (with the auto-mention behaviour the
  plugin already implements).
- Transition issues (workflow actions).
- Assign / unassign.
- Log work.
- Watch / unwatch.
- Attach files.
- Board Gallery + Scrum / Kanban board views.

**Everything else the mobile app needs to be a serious app:**

- Offline queue / retry for outbound messages, comments and
  transitions.
- Deep-linking (`corbitchat://issue/<KEY>`, `corbitchat://chat/<id>`,
  `corbitchat://comment/<id>`).
- Biometric unlock (fingerprint / Face ID).
- SSO if the client uses it (SAML / OIDC via WebView bootstrap).

The good news: **no backend changes are required** for phase 1.
Every capability listed above is already exposed via Jira REST +
the plugin REST described in `docs/architecture.md`.

---

## 2. Where the source of truth lives — must-read documents

Read these in this order before writing any code. Every one of them
is inside this repo (`/root/jira-dev/jira-issue-chat-panel/`).

### 2.1 Architecture (read first, most important)

**`docs/architecture.md`** — 1,124-line complete reference:

- §2 Complete feature list (exhaustive, both surfaces).
- §4 Data model — all 10 Active Objects tables with fields.
- §5 Backend components — 21 services + 18 REST resources.
- §7 Wiring — Spring components + nav entries + URL patterns +
  WRM contexts.
- §8 URL map + REST reference — **every plugin REST endpoint the
  mobile app will consume**, grouped by resource.
- §9 Data flows — five annotated end-to-end journeys (send
  message, mention-in-comment, reply to Jira comment, attachment
  upload, Web Push).
- §11 Security & permissions.
- §12 External integrations — every Jira native REST endpoint the
  plugin (and therefore the mobile app) uses.
- §13 Configuration surface — PluginSettings keys, VAPID keys,
  compiled-in constants.
- Appendix C — one-page REST + servlet URL cheatsheet.

If you internalise §2, §8 and Appendix C you can start scaffolding
the mobile API client immediately.

### 2.2 Release notes

- **`docs/release-notes/1.0.0-internal-reply-attach.md`** — the
  operator-facing consolidated note for the current build. Read
  the *Highlights* section to understand what a user expects.
- **`docs/release-notes-internal.md`** — per-version change log
  (~750 lines). Useful when you need to understand *why* something
  is the way it is.
- **`docs/marketplace/release-notes.md`** — the marketplace-track
  release notes (public-facing wording).

### 2.3 Repo-level agent guide

**`AGENTS.md`** — the top-level Agent Guide for this repo. Covers:

- Workspace paths, plugin key, test Jira URL, test user, chat page
  URL.
- The **exact** Maven build command (offline flags — plain
  `mvn clean package` **will fail**).
- The install-and-restart recipe for the `jira-srv` container.
- Post-restart poll loop (Jira returns 503 for ~2–3 minutes).
- Standard smoke-test URLs.

### 2.4 Feature-specific docs

- **`docs/attachments.md`** — attachment policy, on-disk layout,
  RFC 5987 filename handling.
- **`docs/message-lifecycle.md`** — edit window, delete flags,
  soft-delete rules, chunked send.
- **`docs/dev-reset-active-objects.md`** — how to reset AO tables
  during development.

### 2.5 Marketplace-facing docs (`docs/marketplace/`)

- `README.md`, `marketplace-listing.md`, `admin-guide.md`,
  `user-guide.md`, `publishing-checklist.md`,
  `security-and-privacy.md`. These describe the plugin at the
  product level — useful for shaping the mobile UX to match user
  expectations.

### 2.6 In-code references

- `src/main/resources/atlassian-plugin.xml` — canonical list of
  every endpoint, servlet, web-resource, event listener, and AO
  entity.
- `src/main/resources/js/jim-api.js` — the plugin's own JS REST
  client. **Model the mobile app's HTTP layer on this.** It shows
  every endpoint the current web client uses and how it constructs
  the request.
- `src/main/java/com/corbitlogic/jira/internalmessenger/rest/*.java`
  — the server side of every endpoint. Read the DTOs for exact
  request / response shapes.

---

## 3. Test environment — how to connect

**Important:** there is **no SSH**. The whole test environment is
Docker containers on the same host as this workspace. The agent
runs shell commands on the host, which talks to Docker directly.

### 3.1 Host layout

- **This workspace:** `/root/jira-dev/jira-issue-chat-panel/`
  (the plugin repo).
- **Docker compose root:** `/root/jira/` (contains
  `docker-compose.yml`, `nginx/`, `letsencrypt/`, license SQLs).
- **Preserved release JARs (for rollback):**
  `/root/jira-dev/releases/`.
- **Offline Maven repo:**
  `/root/maven-repos/atlassian-jira-offline/`.
- **Maven settings:** `~/.m2/settings.xml` (points to the offline
  repo).

### 3.2 Running containers

| Container | Image | Ports | Purpose |
|---|---|---|---|
| `jira-srv` | `haxqer/jira:9.17.5` | `8080` | Jira Data Center 9.17.5 with the CorbitChat plugin. |
| `mysql-jira` | `mysql:8.0` | internal `3306` | Jira DB. Also holds the plugin's AO tables (`AO_099FDF_*`). |
| `nginx-jira` | `nginx:stable` | `80`, `443` | HTTPS reverse proxy for `jira.corbitlogic.com`. |

### 3.3 URLs

- **Direct Jira (test):** `http://185.83.181.194:8080`
- **HTTPS front (via nginx):** `https://jira.corbitlogic.com`
- **Mobile app dev target:** point the app at the direct URL for
  the fastest loop; move to the HTTPS front when you need the real
  service worker / push testing pipeline.

### 3.4 Credentials

**Jira test users** (SysAdmin unless noted):

- `m.nasiri` / `<REDACTED>` — primary, SysAdmin.
- `masoud`, `m.zeynali`, `z.zarhoon`, `a.hesari`, `m.moosavi` —
  additional test users on the test instance. Passwords are set
  by the operator; ask before assuming.

**GitHub** (already configured, don't touch):

- Personal Access Token is embedded in the git remote URL:
  `git remote get-url origin`
  will show it. Repo:
  `https://github.com/masoudnasiri/corbitchatforjira.git`
  (note: has moved to
  `https://github.com/masoudnasiri/corbitchat-jira-data-center-messenger.git`;
  pushes still work because GitHub redirects).
- Git identity: `masoudnasiri`, email
  `masoudnasiri@users.noreply.github.com`.

**MySQL** (only needed for deep debugging, not for the mobile app):

- Host: container `mysql-jira` (from other containers) or via
  `docker exec mysql-jira …` from the host.
- User / password: `jira` / `123123`.
- Database: `jira`.
- Plugin AO tables: `AO_099FDF_JIM_*` (see `docs/architecture.md`
  §4).

### 3.5 Common host commands (copy-paste)

```bash
# List containers
docker ps

# Health probe (via curl on the host)
curl -su m.nasiri:<REDACTED> http://185.83.181.194:8080/rest/jim/1.0/health

# Install a new plugin JAR + restart Jira
docker exec -u 0 jira-srv bash -lc \
  'rm -f /var/jira/plugins/installed-plugins/corbitchat-jira-dc-*.jar'
docker cp target/corbitchat-jira-dc-<VERSION>.jar \
  jira-srv:/var/jira/plugins/installed-plugins/
docker restart jira-srv

# Wait for startup (Jira returns 503 for ~2-3 min)
for i in $(seq 1 60); do
  code=$(curl -s -u m.nasiri:<REDACTED> -o /dev/null -w '%{http_code}' \
    http://185.83.181.194:8080/rest/jim/1.0/health)
  [ "$code" = 200 ] && echo READY && break
  sleep 5
done

# Inspect AO tables
docker exec mysql-jira mysql -ujira -p123123 jira \
  -e "SELECT * FROM AO_099FDF_JIM_CONVERSATION LIMIT 5;"

# Read Jira application log
docker exec jira-srv tail -n 200 /var/jira/log/atlassian-jira.log
```

### 3.6 SSH? (No)

You will **not** need SSH keys for anything in this workflow. The
plugin agent runs on the same host as the containers; every
"remote" operation is a `docker exec …` or `docker cp …`. Client
production servers (e.g. `jira.7gtech.net`) are reached only via
HTTPS to their public URL, not via SSH — the operator installs
built JARs there manually via the Jira UI's UPM.

If a specific SSH tunnel becomes needed later (e.g. because the
mobile team gets its own build server), the ops team will provision
it and hand over an `~/.ssh/id_ed25519` at that time. Today the
`~/.ssh` folder here is empty.

---

## 4. Suggested technology stack for the mobile app

### 4.1 Framework choice

Two viable paths:

- **React Native** (recommended).
  - Sharable JS knowledge with the plugin's own client
    (`jim-messenger.js`, `jim-comment-reply.js`).
  - Excellent FCM + APNs libraries: `@react-native-firebase/messaging`.
  - `react-native-webview` for the SSO bootstrap dance if the
    client uses SAML.
  - Codebase runs on both iOS and Android from one repo.
- **Flutter** (alternative).
  - Faster UI performance out of the box.
  - Slightly more work to bridge FCM/APNs plus WebView SSO.

I recommend **React Native + TypeScript** for parity with the
plugin's JS code and easier reuse of the wiki-markup renderers /
mention parsers.

### 4.2 State + persistence

- **Redux Toolkit** (or Zustand — simpler) for reactive UI state.
- **WatermelonDB** or **SQLite via `expo-sqlite`** for offline
  cache of conversations / messages / issues.
- **`react-native-mmkv`** for fast key-value storage of the auth
  token and settings.

### 4.3 Networking

- **Axios** or **fetch** with an interceptor that adds
  `Authorization: Basic <base64(user:token)>` or
  `Authorization: Bearer <PAT>`.
- Baked-in retry / offline queue for outbound writes
  (send-message, add-comment, transition, react, etc.).
- The plugin's `jim-api.js` is a good reference for endpoint URLs
  and payload shapes.

### 4.4 Push

- **FCM** for Android → registration token registered with the
  plugin's `POST /rest/jim/1.0/push/subscriptions` variant, OR a
  new mobile-specific endpoint you add.
- **APNs** for iOS → same idea.
- Consider adding a new push channel type `MOBILE_FCM` /
  `MOBILE_APNS` to `JimPushService` if the browser Web Push flow
  doesn't map cleanly (it likely doesn't — mobile push tokens are
  not W3C `PushSubscription` objects).

### 4.5 Auth

Jira Data Center supports:

- **Basic auth** (username + password). Simple but requires
  storing the password → do NOT do this in a mobile app.
- **Personal Access Tokens (PAT)**. User creates a PAT in their
  Jira profile once, mobile app stores it in the OS secure
  keystore (Keychain / KeyStore).
- **Cookie session** (post to `/rest/auth/1/session`, keep the
  `JSESSIONID` + `atlassian.xsrf.token` cookies). Ideal for the
  bootstrap SSO case.

Recommended: PAT for standalone auth + a "Sign in with SSO"
WebView flow that captures the session cookie for SSO customers.

---

## 5. Endpoints the mobile app will consume

All URLs relative to the Jira base URL. **Every one already
exists** — no server work needed for phase 1.

### 5.1 Chat surface — CorbitChat plugin REST (`/rest/jim/1.0/`)

Full authoritative list in `docs/architecture.md` §8. High-level:

- Conversations: `/conversations`,
  `/conversations/{id}/messages`,
  `/conversations/{id}/attachments`,
  `/conversations/{id}/read`,
  `/conversations/{id}/pinned`.
- Messages: `PUT /messages/{id}`, `DELETE /messages/{id}`,
  `/messages/{id}/pin`, `/messages/{id}/action`,
  `/messages/{id}/reactions`.
- Groups: `/groups`, `/groups/{id}/members`.
- Projects: `/projects/{key}/conversation`.
- Users: `/users/search`.
- Attachments (download / preview): `/attachments/{id}/download`,
  `/attachments/{id}/preview` (both support HTTP Range for
  streaming).
- Push: `/push/config`, `/push/subscriptions`.
- License: `/license/status` (marketplace build).

### 5.2 Jira native REST (used by every mobile-Jira feature)

- Issues: `GET /rest/api/2/issue/{key}` (with `expand`),
  `GET /rest/api/2/search?jql=…`,
  `POST /rest/api/2/issue`,
  `PUT /rest/api/2/issue/{key}`.
- Comments: `GET/POST /rest/api/2/issue/{key}/comment`,
  `PUT/DELETE /rest/api/2/issue/{key}/comment/{id}`.
- Attachments: `POST /rest/api/2/issue/{key}/attachments`,
  `DELETE /rest/api/2/attachment/{id}`.
- Transitions:
  `GET /rest/api/2/issue/{key}/transitions`,
  `POST /rest/api/2/issue/{key}/transitions`.
- Worklog: `GET/POST /rest/api/2/issue/{key}/worklog`.
- Users: `GET /rest/api/2/user`, `/rest/api/2/user/search`.
- Boards: `GET /rest/agile/1.0/board`,
  `/rest/agile/1.0/board/{id}/backlog`,
  `/rest/agile/1.0/board/{id}/sprint`,
  `/rest/agile/1.0/sprint/{id}/issue`.
- Projects: `GET /rest/api/2/project`,
  `/rest/api/2/project/{key}`.
- Auth: `POST /rest/auth/1/session`,
  `GET /rest/auth/1/session`,
  `DELETE /rest/auth/1/session`.

Everything above is standard Jira Data Center REST — no plugin
changes needed.

---

## 6. Things that will bite you (learned the hard way)

1. **HTTPS + Service Worker on Web Push.** Not relevant to mobile
   FCM / APNs, but the plugin's `jim-sw.js` currently hard-codes
   its scope to `/plugins/servlet/jim/sw.js`. Don't accidentally
   reuse that Service Worker for the mobile push channel; add a
   new push channel type instead.
2. **Persian / RTL text.** The plugin has a bidi detector that
   flips `direction: rtl` on messages that are majority-Persian /
   Arabic. Mirror that behavior in the mobile UI (React Native has
   `writingDirection` on `Text`).
3. **Wiki markup vs plain text.** Jira comments use wiki markup
   (`{quote}`, `[~user]`, `!file.png|thumbnail!`, `[^file.pdf]`).
   The plugin's Reply feature relies on Jira's own wiki renderer
   to expand these. In the mobile app you must either:
   a. Fetch comments with `expand=renderedBody` and render the HTML.
   b. Ship a wiki-markup renderer in the app.
   Option (a) is simpler and matches the desktop client's Reply
   flow — mirror what `jim-comment-reply.js` does in
   `injectRenderedComment()`.
4. **Attachment filenames.** Jira DC + this plugin now emit
   RFC 6266 / RFC 5987 `Content-Disposition` with
   `filename*=UTF-8''<percent-encoded>`. Every modern mobile HTTP
   client decodes this automatically — just don't reimplement the
   parsing yourself.
5. **Edit window.** Messages can only be edited within **30
   minutes** (server-enforced). Show a "you can no longer edit"
   state instead of a disabled button so the UX matches the
   desktop client.
6. **Message length.** 5,000 chars per message; longer text is
   auto-split into ordered chunks. The mobile app should replicate
   the split logic (`splitTextIntoChunks` in `jim-messenger.js`)
   or refuse >5k gracefully.
7. **Read-receipt privacy.** In direct chats a server-side flag
   `seenByOther` is per-user; group chats expose a per-member
   receipts endpoint (`/conversations/{cid}/messages/{mid}/receipts`).
   Don't infer "read by everyone" client-side.
8. **Assistant actioned state.** Every JimMessage row carries
   `actioned` + `actionedAt`. Mobile UI should surface this so
   users can acknowledge assistant events on the go.
9. **Rate limits.** Jira DC has no default rate limits; the
   plugin polls the chat at 2500 ms. On mobile that's too
   aggressive — use FCM/APNs push as the primary channel and
   poll only on foreground / pull-to-refresh.
10. **Offline first.** Anything that writes (send message, add
    comment, transition issue) must be queueable. The desktop
    client is online-only; the mobile client can't be.

---

## 7. Suggested phase plan

**Phase 1 — Chat parity (2–3 weeks).**

- Auth via PAT.
- Conversation list + one-to-one chat.
- Send / receive text messages.
- Push notifications (FCM + APNs).
- Local persistence + optimistic send.

**Phase 2 — Chat depth (2 weeks).**

- Group + project chats.
- Attachments (image + file), image preview.
- Reactions, replies, pins, edits, delete.
- Jira Assistant conversation with acknowledged state.

**Phase 3 — Jira issue features (3–4 weeks).**

- Issue browsing (search + filter + assigned-to-me).
- Full issue view.
- Comment / reply to comment (auto-mention like the plugin).
- Transitions, assign, worklog, watch, attach.

**Phase 4 — Boards + polish (2 weeks).**

- Board Gallery + Scrum / Kanban board views.
- Deep-linking + biometrics + share sheet integration.

**Phase 5 — SSO customers (as needed).**

- WebView-bootstrapped session cookie flow.

Server-side work only becomes necessary if:

- Push tokens need a `MOBILE_FCM` / `MOBILE_APNS` channel added
  to `JimPushService` (small change).
- The mobile app wants a "mark all read" or "conversations delta
  since T" endpoint that doesn't already exist (small addition
  to `JimConversationResource`).

---

## 8. Directory + repo hand-off

- **Plugin repo (this workspace):**
  `/root/jira-dev/jira-issue-chat-panel/`
- **Recommended mobile-app repo:** create a NEW repo under the
  same GitHub org, e.g.
  `github.com/masoudnasiri/corbitchat-mobile`. **Don't** put the
  mobile app inside the plugin repo — different build systems,
  different release cadence.
- **Cross-repo docs:** copy this handover into the mobile repo's
  `docs/` folder so the new agent has it locally too.

---

## 9. Contact / handoff loop

If the mobile agent needs a plugin change (new endpoint, extra
field, extra push channel), the pattern is:

1. Mobile agent files an issue on the plugin repo describing the
   need with the exact request/response shape.
2. Plugin agent adds the endpoint on a new branch (`feature/mobile-*`),
   follows the plugin's existing build / release cycle,
   ships a new internal JAR (`1.0.0-internal-<featurename>`).
3. Mobile agent points at the new endpoint; the mobile app
   version-gates the feature.

---

## 10. Ready-to-paste prompt for the new agent

See the companion file
**`docs/handover-mobile-app-prompt.md`** for a self-contained
prompt to seed the new agent's first session.

---

*Maintained in-tree. Keep in sync when new REST endpoints, tables
or auth flows are added to the plugin.*
