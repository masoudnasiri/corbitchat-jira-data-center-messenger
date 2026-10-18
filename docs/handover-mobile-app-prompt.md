# Handover Prompt for the New Mobile-App Agent

Copy the block between the `----- PROMPT BEGIN -----` and
`----- PROMPT END -----` markers verbatim into the first message
of the new agent's session. Nothing above or below the markers
needs to be sent.

The prompt is self-contained: it points the new agent at the
handover doc, the architecture doc, and the exact starter
instructions, without repeating their contents.

---

----- PROMPT BEGIN -----

You are picking up a new engineering task: build a cross-platform
**mobile app** for the CorbitChat plugin (Jira Data Center) plus
Jira-mobile-for-DC-style issue features. The server side is already
built; your job is the mobile client.

## Your starting context

- You are working in a Cursor workspace that is **shared with the
  server-side plugin repo**, so you can read every plugin source
  file directly.
- **Plugin repo:** `/root/jira-dev/jira-issue-chat-panel/`
- **Recommended mobile repo:** create a new sibling repo (e.g.
  `/root/jira-dev/corbitchat-mobile/`). Do NOT put the mobile app
  inside the plugin repo.

## Read these before writing any code (in this order)

1. `/root/jira-dev/jira-issue-chat-panel/docs/handover-mobile-app.md`
   — comprehensive handover written specifically for you. Covers
   scope, environment, credentials, endpoints, stack recommendations,
   gotchas, phase plan.
2. `/root/jira-dev/jira-issue-chat-panel/docs/architecture.md` —
   the plugin's definitive architecture reference (§2 features, §8
   URL map + REST reference, §9 data flows, Appendix C URL
   cheatsheet).
3. `/root/jira-dev/jira-issue-chat-panel/AGENTS.md` — the plugin
   repo's own agent guide (build / install / restart recipes).
4. `/root/jira-dev/jira-issue-chat-panel/docs/release-notes/1.0.0-internal-reply-attach.md`
   — operator-facing feature summary of the current build.
5. `/root/jira-dev/jira-issue-chat-panel/src/main/resources/js/jim-api.js`
   — the plugin's own REST client. Model your mobile HTTP layer on
   this.

## Test environment (no SSH; all local Docker)

- Test Jira: `http://185.83.181.194:8080`
  (HTTPS front: `https://jira.corbitlogic.com`).
- Test user (SysAdmin): `m.nasiri` / `<REDACTED>`.
- Docker containers on the same host: `jira-srv`, `mysql-jira`,
  `nginx-jira`.
- Preserved release JARs for rollback: `/root/jira-dev/releases/`.
- Git remote already configured with a PAT (don't change it):
  `github.com/masoudnasiri/corbitchat-jira-data-center-messenger`.
- There is **no SSH key** to a remote server; every "remote"
  operation is `docker exec`/`docker cp` on the local Docker
  daemon. Client production Jira instances are reached only via
  HTTPS to their public URL.

## Product scope (summary)

**Chat side (server already exists — CorbitChat plugin REST at
`/rest/jim/1.0/*`):**
- Direct / group / project chats.
- Messages: text, reactions, replies, pinned, edit (30-min
  window), delete, forward, drafts, per-message timestamps,
  read receipts.
- Attachments (image, audio, file), voice messages.
- Mentions, emoji picker.
- Web Push → native FCM (Android) + APNs (iOS).
- Jira Assistant bot conversation with acknowledged state.

**Jira side (server is standard Jira DC 9.x REST at
`/rest/api/2/*` and `/rest/agile/1.0/*`):**
- Issue browsing (JQL, boards, assigned-to-me).
- Full issue view with comments, attachments, activity.
- Comment + reply to comment (auto-mention like the plugin).
- Transitions, assignment, worklog, watchers, attach files.
- Board Gallery + Scrum / Kanban board views.

**Cross-cutting:**
- Offline queue for sends / comments / transitions.
- Deep-linking, biometric unlock, share-sheet integration.
- SSO customers via WebView bootstrap that captures the Jira
  session cookie.

## Recommended stack

- **React Native + TypeScript** (parity with plugin JS, best
  library ecosystem for FCM/APNs and Jira integrations).
  Flutter is a valid alternative.
- Redux Toolkit or Zustand for state.
- SQLite (Watermelon or expo-sqlite) for offline cache; MMKV for
  auth token.
- Axios with an auth interceptor.
- Personal Access Tokens for auth (fallback: cookie session from
  a WebView SSO bootstrap).

## Suggested phase plan

Delivered incrementally so you can ship at any phase boundary:

1. Auth + conversation list + 1-to-1 chat + FCM/APNs push (2–3 w).
2. Group + project chat, attachments, reactions, replies, pins,
   edits, delete, Jira Assistant (2 w).
3. Jira issue browsing + full issue view + comment/reply/transition/
   assign/worklog/watch/attach (3–4 w).
4. Board Gallery + Scrum/Kanban board views + deep-linking +
   biometrics (2 w).
5. WebView SSO for SAML customers (as needed).

## Things that will bite you (read
`docs/handover-mobile-app.md` §6 for details)

- Bidi text (Persian / Arabic majority-char detection).
- Wiki markup — always fetch comments with `expand=renderedBody`;
  don't reimplement Jira's wiki renderer.
- RFC 5987 filenames on downloads (already correct on the server).
- 30-minute edit window; 5,000-char message length; auto-split of
  longer text.
- Group read-receipts are exposed via
  `/conversations/{cid}/messages/{mid}/receipts`, not inferred.
- Assistant messages have an `actioned`/`actionedAt` flag; surface
  it in the mobile UI.
- Rate: chat polls at 2500 ms on desktop; on mobile use push as
  the primary channel and poll only on foreground / pull-to-
  refresh.
- Everything writeable must be queueable offline.

## Server-side work you might request

The plugin agent can add:

- A `MOBILE_FCM` / `MOBILE_APNS` push channel to
  `JimPushService` (needed because W3C `PushSubscription` doesn't
  fit mobile).
- A "conversations delta since T" or "mark all read" endpoint if
  you need it.

Follow the loop in `docs/handover-mobile-app.md` §9 — file an
issue on the plugin repo with the exact request/response shape.

## Your first three actions

1. Read `docs/handover-mobile-app.md` end to end.
2. Read `docs/architecture.md` §2, §8, §9, and Appendix C.
3. Scaffold a new React Native + TypeScript repo at
   `/root/jira-dev/corbitchat-mobile/` and stub the auth screen +
   the API client (`src/api/client.ts`), point it at the test
   Jira `http://185.83.181.194:8080`, and verify a plain
   `GET /rest/api/2/myself` with Basic auth (`m.nasiri` /
   `<REDACTED>`) returns 200 with the user's profile. That's your
   "hello world".

Do NOT modify the plugin repo unless the plugin needs a new
endpoint you have already requested via the hand-off loop. The
plugin is on branch `feature/reply-composer-attachments` at
commit `b730699` at the time of this handover.

Ask before making any decision that would meaningfully diverge
from the plugin's UX (auto-mention behaviour, wiki markup
handling, edit-window enforcement, actioned-state semantics) so
we keep the two clients consistent.

----- PROMPT END -----

---

## Notes on using this prompt

- **Model choice for the new agent.** Anything with strong
  TypeScript + React Native training data (GPT-5 Codex, Sonnet
  4.6/5) works well.
- **Length.** The prompt is intentionally ~3 KB — small enough to
  fit any context window with plenty of room for the agent's own
  tool calls. It intentionally *does not* inline the full
  architecture doc; it points at it.
- **Determinism.** The three explicit "first actions" prevent
  the new agent from starting with a wall of clarifying questions
  or hallucinating endpoints.
- **Boundary enforcement.** The last two paragraphs establish the
  "don't modify the plugin unless requested" rule so you don't
  end up with parallel divergent implementations.

## Version discipline

If you update the handover doc after this, remember to also update
the four line-item paths inside the "Read these before writing any
code" block if any of them move.
