# Comprehensive Handover Prompt — CorbitChat Mobile App

The new agent has **direct access to this codebase**. Copy the block
between `----- PROMPT BEGIN -----` and `----- PROMPT END -----`
verbatim into the first message of the new agent's session.

Because the new agent can open every file, this prompt references
documents and source paths instead of inlining them.

---

----- PROMPT BEGIN -----

# Mission

You are the lead engineer for a **new mobile application** that
extends the existing **CorbitChat for Jira Data Center** plugin.
The mobile app must combine:

1. **Team chat** — full parity with the CorbitChat plugin's chat
   surface (direct / group / project chats, mentions, reactions,
   replies, attachments, voice, read receipts, Jira Assistant).
2. **Jira mobile for Data Center** — issue browsing, viewing,
   commenting, reply-to-comment with auto-mention, transitions,
   assignment, worklog, watchers, attachments, boards.

The **entire server side already exists** and is documented. You
own the mobile client only. No plugin changes are required for
phase 1.

# You have full access to this codebase

Workspace: `/root/jira-dev/jira-issue-chat-panel/` (the plugin
repo). You can read every source file. Use it as the authoritative
spec for request/response shapes — do not guess.

# Read these first, in this exact order

Do not write any code until you have read:

1. **`docs/handover-mobile-app.md`** — your primary handover.
   Product scope, test environment (no SSH — all local Docker),
   credentials, endpoint inventory, recommended stack, ten
   "things that will bite you", and the five-phase delivery plan.
2. **`docs/architecture.md`** — the plugin's definitive
   architecture reference (1,124 lines). Focus on:
   - §2 Complete feature list (what the chat client must match).
   - §4 Data model (the 10 Active Objects tables + fields).
   - §8 URL map + REST reference (every plugin endpoint you'll
     call).
   - §9 Data flows (send message, mention-in-comment, reply to
     comment, attachment upload, Web Push — annotated end to end).
   - §11 Security & permissions.
   - §12 External integrations (every native Jira REST endpoint).
   - §13 Configuration surface.
   - Appendix C (one-page URL cheatsheet).
3. **`AGENTS.md`** — plugin repo agent guide: build / install /
   restart recipes and the offline Maven flags.
4. **`docs/release-notes/1.0.0-internal-reply-attach.md`** —
   operator-facing summary of the current build's features and
   UX expectations.
5. **`src/main/resources/js/jim-api.js`** — the plugin's own REST
   client. **Model your mobile HTTP layer on this** — it enumerates
   every endpoint the current web client uses and the exact payload
   shapes.
6. **`src/main/resources/js/jim-messenger.js`** — the reference
   implementation for chat UX (message rendering, mentions, bidi
   text detection `detectTextDirection`, chunked send
   `splitTextIntoChunks`, drafts, edit-in-composer, reactions,
   receipts, lightbox). Mirror its behaviour so the two clients
   stay consistent.
7. **`src/main/resources/js/jim-comment-reply.js`** — the
   reference for reply-to-comment (auto-mention, `{quote}` markup,
   `expand=renderedBody` rendering in `injectRenderedComment`,
   attachment upload + wiki tokens). Mirror this in the mobile
   issue view.
8. **`src/main/java/com/corbitlogic/jira/internalmessenger/rest/`**
   — the server side of every endpoint. Read the DTOs in
   `src/main/java/.../dto/` for exact field names and types.

# Test environment (there is NO SSH)

Everything is Docker on the same host. Every "remote" op is
`docker exec` / `docker cp` on the local daemon — there is no SSH
key and no remote server to tunnel into.

- Test Jira (direct): `http://185.83.181.194:8080`
- Test Jira (HTTPS via nginx): `https://jira.corbitlogic.com`
- Test SysAdmin user: `m.nasiri` / `Man@782761`
- Other test users exist (`masoud`, `m.zeynali`, `z.zarhoon`,
  `a.hesari`, `m.moosavi`) — ask the operator for passwords.
- Containers: `jira-srv` (Jira 9.17.5), `mysql-jira`
  (`jira`/`123123`, DB `jira`, AO tables `AO_099FDF_JIM_*`),
  `nginx-jira`.
- Preserved rollback JARs: `/root/jira-dev/releases/`.
- Git remote already carries a PAT (do not change it); repo has
  moved to
  `github.com/masoudnasiri/corbitchat-jira-data-center-messenger`
  (pushes still work via redirect).
- Smoke test the API right now:
  `curl -su m.nasiri:Man@782761 http://185.83.181.194:8080/rest/api/2/myself`

# Product scope

**Chat (server exists — plugin REST `/rest/jim/1.0/*`):**
direct / group / project chats; text messages (5,000-char cap,
auto-split of longer text); mentions with `@` palette; emoji;
replies to messages; edit within a 30-minute window; delete;
forward; drafts; per-message timestamps; reactions; pinned
messages; read receipts (direct = seen flag, group = per-member
receipts endpoint); attachments (image / audio / file) + voice
messages; Jira Assistant bot conversation with `actioned` /
`actionedAt` acknowledged state; Web Push (map to FCM/APNs).

**Jira (server = standard Jira DC REST `/rest/api/2/*`,
`/rest/agile/1.0/*`):** issue search (JQL, assigned-to-me,
boards); full issue view (fields, comments, attachments,
activity, links); comment + reply-to-comment with auto-mention
(mirror `jim-comment-reply.js`); workflow transitions; assign /
unassign; worklog; watch / unwatch; attach files; Board Gallery +
Scrum / Kanban board views.

**Cross-cutting:** offline queue + retry for all writes;
deep-linking (`corbitchat://issue/<KEY>`, `.../chat/<id>`,
`.../comment/<id>`); biometric unlock; OS share-sheet + camera
integration; SSO customers via a WebView bootstrap that captures
the Jira session cookie.

# Recommended stack

- **React Native + TypeScript** (recommended — parity with the
  plugin's JS, best FCM/APNs + WebView ecosystem). Flutter is a
  valid alternative if you justify it.
- State: Redux Toolkit or Zustand.
- Offline cache: SQLite (WatermelonDB or expo-sqlite); auth token
  in the OS secure store (`react-native-mmkv` / Keychain /
  KeyStore).
- Networking: Axios (or fetch) with an auth interceptor; built-in
  retry + offline queue for outbound writes.
- Auth: **Personal Access Token** (user creates once in Jira,
  stored in the secure keystore). Fallback: cookie session from a
  WebView SSO bootstrap (`POST /rest/auth/1/session`).
- Push: FCM (Android) + APNs (iOS). W3C `PushSubscription` does
  NOT map to mobile tokens — you will likely need a new
  `MOBILE_FCM` / `MOBILE_APNS` push channel on the server (see
  "Server-side requests" below).

# Ten things that will bite you (details in
`docs/handover-mobile-app.md` §6)

1. Bidi text — mirror `detectTextDirection` for Persian / Arabic.
2. Wiki markup — always fetch comments with `expand=renderedBody`
   and render the HTML; don't reimplement Jira's wiki renderer.
3. RFC 5987 `Content-Disposition` filenames (already correct
   server-side; don't reparse).
4. 30-minute edit window (server-enforced) — show a "can no longer
   edit" state, not a dead button.
5. 5,000-char message cap with auto-split into ordered chunks —
   replicate `splitTextIntoChunks`.
6. Group read receipts come from
   `/conversations/{cid}/messages/{mid}/receipts` — never inferred
   client-side.
7. Assistant messages carry `actioned` / `actionedAt` — surface
   the acknowledge action.
8. Desktop polls chat at 2500 ms — on mobile use push as primary,
   poll only on foreground / pull-to-refresh.
9. Everything writeable (send, comment, transition, react) must be
   queueable offline.
10. Service Worker `jim-sw.js` is browser-only; do not reuse it —
    add a separate mobile push channel.

# Delivery plan (ship at any phase boundary)

1. Auth (PAT) + conversation list + 1-to-1 chat + FCM/APNs push +
   optimistic send + local cache. (2–3 w)
2. Group + project chat, attachments + image preview, reactions,
   replies, pins, edits, delete, Jira Assistant with ack. (2 w)
3. Jira issue browsing + full issue view + comment / reply /
   transition / assign / worklog / watch / attach. (3–4 w)
4. Board Gallery + Scrum / Kanban board views + deep-linking +
   biometrics + share sheet. (2 w)
5. WebView SSO for SAML customers. (as needed)

# Repo boundary

- Create a NEW sibling repo for the mobile app:
  `/root/jira-dev/corbitchat-mobile/`. Do NOT put the mobile app
  inside the plugin repo (different build systems + release
  cadence).
- Do NOT modify the plugin repo unless you need a new endpoint
  that you have already requested (see below). The plugin is on
  branch `feature/reply-composer-attachments` at commit `11c4fce`
  at handover time.

# Server-side requests (hand-off loop)

If you need a plugin change, follow `docs/handover-mobile-app.md`
§9: file an issue on the plugin repo describing the need with the
exact request/response shape. Likely candidates:
- `MOBILE_FCM` / `MOBILE_APNS` push channel on `JimPushService`.
- A "conversations delta since T" or "mark all read" endpoint on
  `JimConversationResource`.

# Consistency rule

Ask before diverging from the plugin's established UX:
auto-mention behaviour, wiki-markup handling, the 30-minute edit
window, chunked-send, and Assistant actioned-state semantics.
Both clients must behave the same way.

# Your first three actions

1. Read `docs/handover-mobile-app.md` end to end, then
   `docs/architecture.md` §2, §8, §9 and Appendix C.
2. Confirm connectivity:
   `curl -su m.nasiri:Man@782761 http://185.83.181.194:8080/rest/api/2/myself`
   should return 200 with the user profile.
3. Scaffold `/root/jira-dev/corbitchat-mobile/` as a React Native
   + TypeScript app; stub the auth screen and the API client
   (`src/api/client.ts`) pointed at the test Jira; make the
   "hello world" a real authenticated `GET /rest/api/2/myself`
   round-trip rendered on screen.

Then report back with a concrete phase-1 task breakdown before
building UI in bulk.

----- PROMPT END -----

---

## How to use

1. Open this file in the new agent's Cursor session (it shares the
   workspace, so the paths resolve).
2. Copy everything between the two markers.
3. Paste as the new agent's first message.

## Why this version exists alongside `handover-mobile-app-prompt.md`

- `handover-mobile-app-prompt.md` is the **short** (~3 KB) seed
  prompt.
- This file (`-full.md`) is the **comprehensive** version for when
  you want the new agent fully briefed in one shot — it enumerates
  the exact files to read (with the specific sections and the
  reference JS modules), the full product scope, the stack, the
  ten gotchas, the phase plan, the repo boundary, the server-side
  request loop, and the consistency rule. Use whichever fits how
  much you want to steer the first session.

Both point at the same source-of-truth documents; neither inlines
them, because the new agent can open them directly.
