# CorbitChat — Internal Build Release Notes

Release notes for the `internal-corbit` branch (on-prem internal distribution
build, Marketplace licensing disabled). For the marketplace build's notes
see `docs/marketplace/release-notes.md`.

---

## 1.0.0-internal-replies2 — 2026-06-30 (fix)

Fix for the previous `internal-replies` build. The first attempt drove
Jira's wiki editor by setting `#comment` textarea `.value` directly. The
wiki editor is a Visual/Source overlay on top of the textarea: setting
the underlying value only takes effect when the user happens to be in
Source mode, so in Visual mode the reply prefill silently disappeared
and the parent-comment context was never shown.

### What changed

* **Inline reply composer.** Clicking "Reply" no longer drives Jira's
  wiki editor. Instead an inline panel opens immediately below the
  parent comment containing:
    * a clear "Replying to <Author>" header with a Cancel (X) icon,
    * a quoted preview of the parent comment in a styled box,
    * an empty textarea (autofocused),
    * a small status line for inline error/success messages,
    * Cancel + **Send Reply** buttons.
* **Direct REST submit.** The composer's Send Reply button POSTs to
  Jira's standard `POST /rest/api/2/issue/{key}/comment` with body
  assembled as:

      [~parentAuthor]

      {quote}<parent excerpt, max 240 chars>{quote}

      <user-typed reply text>

  This is the same REST endpoint Jira's own Comment form uses, so:
    * The saved comment is a normal Jira comment (no shadow storage).
    * Jira applies native permissions / visibility / edit rules.
    * The `[~username]` is a real Jira-recognized mention token, which
      triggers Jira's native mention notification machinery (subject to
      the user's notification settings and the issue notification
      scheme).
    * The plugin's existing `CommentCreatedEvent` listener picks the
      same comment up and posts a Jira Assistant entry into the
      mentioned user's bot conversation.
* **Page refresh + scroll-to-new-comment.** On success the page is
  reloaded with `focusedCommentId=<new>` and `#comment-<new>` in the
  URL so Jira re-renders the activity feed with the reply included and
  the browser scrolls straight to it. This is intentionally simple — it
  is far more reliable than trying to surgically inject just the new
  comment HTML across the many Jira DC issue-view variants.
* **Self-reply.** If you reply to your own comment the `[~mention]`
  token is suppressed (no needless notification to yourself), but the
  `{quote}` excerpt is still embedded so the reply context is visible.
* **Keyboard.** Ctrl/Cmd+Enter sends the reply; Esc cancels.
* **Robust author resolution.** Reads `rel`/`data-username` from
  Jira's standard `.action-details a.user-hover`, ignoring non-user
  values like `rel="nofollow"`.
* **In-reply-to badge.** A small "↳ In reply to <user>" badge is
  injected above any comment whose body begins with a mention link
  followed by a blockquote — works for replies created by this plugin
  and for any user who manually quoted + mentioned someone.

### Files

* `src/main/resources/js/jim-comment-reply.js` — rewritten as an
  inline-composer + REST-POST flow.
* `src/main/resources/css/jim-comment-reply.css` — styles for the new
  inline composer + clearer badge.
* `pom.xml` — version bumped to `1.0.0-internal-replies2`.

### Verification

Live verification against the test Jira:

1. POST `/rest/api/2/issue/TEB-1/comment` with the composer's exact
   assembled body (`[~m.zeynali]\n\n{quote}…{quote}\n\n…`) returned
   HTTP 201 with new comment id `11600`.
2. `AO_099FDF_JIM_EVENT_LOG` recorded row `#72`:
   `EVENT_TYPE=MENTION ISSUE_KEY=TEB-1 TARGET_USER_KEY=JIRAUSER10100
   EVENT_FINGERPRINT=MENTION|JIRAUSER10100|TEB-1|JIRAUSER10000|11600`.
3. `AO_099FDF_JIM_MESSAGE` recorded row `#346` in conversation 3
   (m.zeynali's Jira Assistant): `EVENT_TYPE=MENTION ISSUE_KEY=TEB-1`
   body "masoud nasiri mentioned you in TEB-1. [~m.zeynali] {quote}…"
4. The minified JS is bundled into the `jira.view.issue` context batch
   served on every `/browse/<KEY>` and parses cleanly (8014 bytes,
   `Parsed OK`). All key flow strings survive minification
   (`jim-comment-reply-composer`, `ajs-issue-key`, `rest/api/2/issue`,
   `{quote}`, `Send Reply`, `X-Atlassian-Token`, …).
5. Test comments cleaned up via `DELETE
   /rest/api/2/issue/TEB-1/comment/{id}` → HTTP 204.

### How to test from the Jira UI

1. Open any issue (e.g. `/browse/TEB-1`).
2. Find an existing comment authored by another user. A "Reply" link
   now sits at the front of its action toolbar (next to Edit / Delete).
3. Click **Reply**. An inline panel appears immediately below that
   comment with:
   * a "↳ Replying to <Author>" header
   * a quoted preview of the parent comment
   * an empty textarea
   * Cancel and Send Reply buttons.
4. Type something and click **Send Reply** (or Ctrl/Cmd + Enter). The
   composer reports "Reply sent. Refreshing…" and the page reloads
   with the new comment focused.
5. Your new comment now shows a "↳ In reply to <Author>" badge above
   its body, with the wiki-rendered `{quote}` block showing what you
   were replying to.
6. The parent author receives:
   * Jira's standard mention email / in-app notification (subject to
     `ajs-outgoing-mail-enabled` and the user's notification settings),
     **and**
   * a Jira Assistant entry in their CorbitChat bot conversation with
     issue key, sender display name, preview, and a link to open the
     issue.
7. Esc inside the composer cancels. Clicking Cancel cancels. Only one
   composer is open at a time across the whole page.

### Edge cases

* **Self-reply**: mention is skipped (still quoted).
* **Inactive / deleted parent author**: the existing
  `JimMentionParser` already filters these out, so no Assistant noise.
* **Restricted parent comment**: native Jira permissions apply at every
  step — the reply itself goes through Jira's standard REST endpoint
  with the user's session, so a user who cannot see the comment cannot
  reply to it either. The Assistant entry is gated by the same checks.
* **Long parent comment**: truncated to 240 chars with an ellipsis.
* **Multiple replies to one parent**: each generates its own native
  comment with its own mention + quote + badge.
* **Many comments on the issue**: a `MutationObserver` attaches the
  Reply button to every native comment as Jira renders it — no
  pre-scan of the entire DOM beyond initialization.
* **`atl.outgoing-mail-enabled = false` in test instance**: this only
  silences email — the in-app mention notification and the Assistant
  entry both still fire (verified live).

### Branch & artifact

* Branch: `fix/jira-comment-replies` (off
  `feature/jira-comment-replies-with-notifications`).
* New artifact: `corbitchat-jira-dc-1.0.0-internal-replies2.jar`.
* Previous artifact `corbitchat-jira-dc-1.0.0-internal-replies.jar`
  preserved in `/root/jira-dev/releases/` for rollback.

---

## 1.0.0-internal-replies — 2026-06-30

Standalone Jira-platform feature: a **Reply to Jira comment** action on
every issue page. Built on the existing CorbitChat mention listener, so
no Jira-core change, no schema change, no new REST endpoint.

### What changed

- A small "Reply" link appears in the action toolbar of every comment on
  every issue page.
- Clicking it opens Jira's native add-comment editor and pre-fills it
  with:
  - A real Jira `[~username]` mention of the parent comment's author
  - A wiki `{quote}…{quote}` excerpt of the parent comment (max 200 chars)
- The user types their reply BELOW the quote and submits through Jira's
  normal "Save" button. The resulting comment is a standard Jira
  comment — same permissions, same editing rules, same visibility.
- Because the saved comment contains a real `[~username]` mention,
  **Jira's built-in mention notification fires automatically** for the
  parent comment author. We don't duplicate that pipeline.
- The plugin's existing `CommentCreatedEvent` listener
  (`JimPluginBootstrap` → `JimMentionParser` → `JimIssueEventHandlerImpl`)
  picks the same comment up, and a Jira Assistant entry lands in the
  mentioned user's bot conversation — including issue key, actor name,
  preview, and a link back to the issue.
- Every rendered comment whose body starts with our `[~name] {quote}…`
  pattern gets a small "↳ In reply to <name>" badge injected above the
  comment body so the reply relationship is visible at a glance.

### Edge cases handled

- **Self-reply** to your own comment: the `[~mention]` is suppressed (no
  notification to yourself), but the quote is still inserted.
- **Inactive / deleted** parent author: handled at the listener level —
  the existing `JimMentionParser` skips unresolved or inactive users.
- **Restricted comments**: native Jira permissions apply to both the
  reply (Jira's own permission checks) and the Assistant entry (the
  existing mention listener already filters on user visibility).
- **Duplicate triggers** (e.g. comment edited / replayed): the existing
  `JimEventLog` fingerprint deduplication prevents double Assistant
  entries.

### Why this approach

The cleanest and safest plugin pattern: ship a single web-resource
attached to the `jira.view.issue` context, prefill Jira's own editor,
and let Jira do the persistence, the permission check, and the
notification — exactly the way the Reply button on email clients works.
No Jira-core file is touched, no new REST endpoint is added, and the
reply relationship lives inside the comment body in a Jira-native form,
so it persists across page refresh, page reopen, mobile, activity
export, etc.

### Files

- `src/main/resources/js/jim-comment-reply.js` (new)
- `src/main/resources/css/jim-comment-reply.css` (new)
- `src/main/resources/atlassian-plugin.xml` (registered new web-resource
  with `<context>jira.view.issue</context>`)
- `pom.xml` (version bumped to `1.0.0-internal-replies`)

### Verification

- Web-resource is bundled into the `jira.view.issue` context batch (proven
  by inspecting the contextbatch JS served on `/browse/<key>` — it
  contains both the `jim-comment-reply` key and the
  `__jimCommentReplyLoaded` marker).
- Live test: reply comment id `11501` containing `[~m.zeynali]` produced
  EventLog row #71 (`MENTION`, target `JIRAUSER10100`, issue `TEB-1`)
  and JimMessage row #344 in conversation 3 (m.zeynali's Jira Assistant)
  with body "masoud nasiri mentioned you in TEB-1…".
- Jira's built-in mention notification is dispatched by Jira itself
  (no custom code in this branch touches it).

### Branch and artifact

- Branch: `feature/jira-comment-replies-with-notifications` (off
  `feature/board-gallery-page`).
- New JAR: `corbitchat-jira-dc-1.0.0-internal-replies.jar`.
- Previous artifact `corbitchat-jira-dc-1.0.0-internal-boards4.jar` is
  preserved in `/root/jira-dev/releases/` for rollback.

### How to test from the Jira UI

1. Open any issue (e.g. `/browse/TEB-1`).
2. Scroll to an existing comment authored by another user. A small
   "Reply" link is now present alongside Edit / Delete.
3. Click "Reply" → Jira's add-comment editor opens and is pre-filled
   with `[~author] {quote}<excerpt>{quote}`.
4. Type your reply under the quote and click "Save".
5. The new comment is persisted normally and shows a "↳ In reply to
   <author>" badge above its body.
6. The mentioned user (a) receives Jira's standard mention email /
   in-app notification and (b) sees a Jira Assistant message in their
   CorbitChat bot conversation linking back to the issue.

---

## 1.0.0-internal — 2026-06-28

Quality / stability batch covering seven follow-up requests after the
public 1.0.0-internal cut. No backend schema break, no UI redesign, no
weakened security; everything is additive or a narrow fix. The Atlassian
UPM Marketplace listing path is **not** affected.

### Headline changes

1. Notification status: text label instead of an ambiguous crossed-bell
2. Message editing: hard 30-minute window (server-enforced)
3. Chrome push: explicit same-origin service worker + diagnostics
4. Chrome push, take 2: payload-first SW, no `/push/summary` fetch
5. In-app delivery: faster polling, visibility wake-up, non-blocking push
6. Group-chat mention picker: single-click + multi-select + "Mention all"
7. Chat quality: per-chat drafts, sender-coloured previews, 5000-char
   messages with safe split, and a per-bubble timestamp

Commit range: `bcde651..5f0f312` on `internal-corbit`.

---

### 1. Sidebar footer notification status reads as plain text

**Why** — the previous crossed-bell icon in the bottom-left user area
overlapped visually with the per-conversation mute concept used by every
other modern messenger. Users couldn't tell whether the bell meant *push is
off* or *this chat is muted*.

**What** — replaced the bell button with a small text link sitting next
to `● Active`:

```
[avatar]  User Name
          ● Active · Enable notifications
```

Three states, controlled in JS:

| State | Display | Click behaviour |
|---|---|---|
| Push not enabled | `Enable notifications` (brand blue link) | Requests permission + subscribes |
| Push enabled | `Notifications on` (calm green) | Unsubscribes; re-enables in one click |
| Browser permission denied | `Notifications blocked` (warning amber, not clickable) | No-op; tooltip directs to browser settings |

Files: `templates/messenger-app.vm`, `js/jim-messenger.js`,
`css/jim-messenger.css`. The crossed-bell glyph (`U+1F56B`) is now
reserved for a future per-conversation mute feature.

---

### 2. Hard 30-minute edit window

**Why** — users could in principle edit messages of any age. Aligns
CorbitChat with chat-app conventions (Slack and others use 30 minutes by
default).

**What** — same enforcement pattern as the existing 10-min delete
window:

- `JimMessageLifecycle.EDIT_WINDOW_MS = 1_800_000` (30 minutes).
- `JimMessageServiceImpl.editUserMessage` throws
  `forbidden("Messages can only be edited within 30 minutes.")`
  for older messages.
- `JimRestJsonMapper` returns `canEdit=false` once the window lapses, so
  the Edit button disappears on the next poll.
- The JS message-action handler also enforces the window locally so the
  button disappears between polls and shows a friendly error if a stale
  DOM is clicked after the window expires.

System messages and Jira Assistant notifications stay non-editable as
before. The "(edited)" marker on previously-edited messages is unchanged
— this only blocks *new* edits.

Files: `JimMessageLifecycle.java`, `JimMessageServiceImpl.java`,
`JimRestJsonMapper.java`, `js/jim-messenger.js`.

---

### 3. Chrome push hardening: explicit same-origin SW + diagnostics

**Why** — Chrome was reporting
`Access to fetch at ... blocked by CORS policy: from origin 'null'`
when the service worker tried to fall back to `/rest/jim/1.0/push/summary`
for payloadless pushes. The SW was inheriting an opaque origin in some
Chrome configurations.

**What** —

- The service worker now builds every URL with
  `new URL(path, self.location.origin)` so origins are unambiguous.
- The `/push/summary` fetch uses an explicit `Request` with
  `mode: 'same-origin', credentials: 'same-origin', cache: 'no-cache'` —
  any URL-resolution mistake fails fast and visibly.
- The page-side push registration explicitly passes `scope: <ctx>/` and
  refuses to even attempt registration when `window.location.origin` is
  the string `'null'` (opaque-origin context), surfacing a clear toast:
  `Push setup failed because the request was not made from the Jira page origin.`
- Added `[CorbitChat Push]` and `[CorbitChat SW]` diagnostic logging at
  every stage (register / ready / blocked-opaque-origin / error / SW
  activate / SW fetch failure) so future bug reports include the
  relevant context without manual instrumentation.

**No CORS headers were added.** No `Access-Control-Allow-Origin: *`.
Push endpoints stay private same-origin.

Files: `js/jim-sw.js`, `js/jim-messenger.js`.

---

### 4. Payload-first push (no `/push/summary` fetch from SW)

**Why** — the SW still relied on a same-origin fetch as a fallback when
a push event arrived without an encrypted payload. Chrome's manual
DevTools "Push" button sends an empty event, which hit this fallback and
produced the recurring `[CorbitChat SW] /push/summary fetch failed`
error.

**What** — the service worker is now strictly payload-first:

- If `event.data` parses to JSON with a `title`, the SW shows that
  notification with `title`, `body`, `tag`, and stores
  `url`/`type`/`conversationId`/`messageId` in `notification.data`.
- If the payload is missing or invalid, the SW shows a generic fallback
  `CorbitChat / You have a new message` and **does not contact the
  server**.
- The shipped service worker now contains **zero** `fetch()` calls.

Server side, the push payload is enriched so all three real flows ship
content the SW can render directly:

| Source | Payload |
|---|---|
| Admin "Send test notification to me" | `title: "CorbitChat test", body: "Push notifications are working.", url: "/plugins/servlet/jim/chat", type: "test", tag: "jim-admin-test"` |
| Chat message | `title: senderName, body: excerpt, tag: "jim-conv-<id>", type: "chat_message", url: "/plugins/servlet/jim/chat", conversationId, messageId` |
| Jira Assistant | `title: eventTitle, body: excerpt, tag: "jim-alarm-<id>", type: "jira_assistant", url: "/plugins/servlet/jim/chat", conversationId, messageId` |
| Attachment | same as chat message |

New `JimPushService.pushToUserAsync(userKey, Map<String,String> payload)`
overload is the canonical entry point; the legacy 4-arg method now
delegates to it. JSON builder emits every non-blank field in the map so
the wire format is open to future fields without further changes.

Files: `js/jim-sw.js`, `JimPushService.java`, `JimPushServiceImpl.java`,
`JimMessageServiceImpl.java`, `JimAttachmentService.java`,
`JimAdminResource.java`.

---

### 5. Fast in-app message delivery (polling stabilisation)

**Why** — users were reporting 20+ second delays for in-app messages,
even when push was nominally working. The combination of a 5-second poll
interval, no visibility-change wake-up (Chrome heavily throttles
`setInterval` in background tabs), and a possible race where push setup
errors could prevent `startPolling()` from ever running, made the chat
feel intermittently broken.

**What** —

- `MESSAGE_POLL_MS` reduced from 5000 ms to **2500 ms**. Worst-case
  in-foreground receive latency drops to ~2.5 s + REST round-trip
  (~120 ms server-side) ≈ **2.7 s**.
- New `bindVisibilityRefresh()` fires an immediate `loadMessages` +
  `loadConversations` whenever the tab regains focus, so any backlog
  accumulated while the tab was hidden appears in <300 ms. Debounced to
  prevent burst floods.
- `init()` reordered: `loadConversations(true).then(startPolling)` runs
  **before** `setupPushNotifications()`, and the `.catch` branch also
  calls `startPolling()` so polling starts even if the initial
  conversations load fails. Push setup is wrapped in `setTimeout(0)` so
  no failure inside push code can possibly delay polling.
- Diagnostic logs added: `[CorbitChat poll] startPolling`,
  `[CorbitChat poll] visibility refresh`, `[CorbitChat send] POST start`,
  `[CorbitChat send] POST ok in N ms`.

Result: chat keeps working at full speed even if push is completely
unavailable on the user's browser/profile.

Files: `js/jim-messenger.js`.

---

### 6. Group-chat mention picker — single, multi, and Mention all

**Why** — the existing mention dropdown only supported one mention per
click; users had to re-open it for every name.

**What** — same dropdown, three flows that can be used together:

| Flow | How | What it inserts |
|---|---|---|
| Quick single mention (existing UX) | Click avatar/name area of a row | `@Name ` |
| Multi-select | Tick checkboxes, click footer **Insert N selected** | `@A @B @C ` |
| Mention all members | Click blue **Mention all members** header | Every other group member |

The header row shows the count of mentionable members. The Insert button
is disabled until at least one row is ticked and displays a live count.
A footer **Clear** button wipes the selection.

Server-side mention parsing is unchanged — `JimMentionParser` already
resolves multiple `@DisplayName` tokens in a single body, so multi-select
rides on existing logic. Mention rendering in bubbles, mention
highlighting and mention push notifications all work identically for
single and multi mentions.

Files: `js/jim-messenger.js`, `css/jim-messenger.css`.

---

### 7. Chat quality batch — drafts, sender-coloured previews, 5000-char, per-bubble time

Four user-facing quality fixes bundled together. All preserve existing
chat features (send, receive, unread, attachments, reactions, forwarding,
edit/delete, Jira Assistant).

#### 7a. Per-chat drafts

**Why** — typed text in Chat A previously bled into Chat B after a
conversation switch; users were at risk of sending a draft to the wrong
recipient.

**What** —

- `state.drafts` is a per-conversation map (in-memory) keyed by
  conversationId.
- `selectConversation()` saves the current draft *before* switching IDs
  and restores the destination conversation's draft after the new chat
  is rendered.
- All four send paths (text / issue-link / attachment queue / chunked
  text) clear the draft on successful send.
- Sidebar conversations with a draft show an amber **DRAFT** pill + the
  unsent text in italics, so the user can see at a glance which chats
  have unsent content.

Drafts are session-local — they live as long as the chat tab is open and
do not survive a page reload. Adding persistent drafts (sessionStorage)
is a small follow-up if desired.

#### 7b. Sender-coloured last-message previews

**Why** — users could not tell at a glance which sidebar previews were
their own last messages vs. someone else's.

**What** — new AO column `LAST_MESSAGE_SENDER_USER_KEY` on
`JimConversation` (auto-migrated, nullable for legacy rows).
`touchConversation` records the sender on every message send.
`JimRestJsonMapper` exposes `lastMessageSenderUserKey` and a precomputed
`lastMessageOwn` boolean. The client renders three preview states:

- `.jim-conversation-preview-own` → **light blue** background (own
  message)
- `.jim-conversation-preview-other` → **light gray** background
  (other user / group member)
- `.jim-conversation-preview-draft` → **amber** background (unsent
  draft, always wins over the other two)

Legacy conversations whose latest message predates the migration render
in the neutral style until they receive a new message; no destructive
re-write of old rows.

#### 7c. 5000-character messages with safe chunked send

**Why** — the 4000-character cap was too low and a hidden 500-char
preview validator was silently blocking *any* message longer than 500
chars from being sent.

**What** —

- `JimValidation.MAX_MESSAGE_BODY_LENGTH` 4000 → **5000**.
- Removed the legacy `requireMaxLength(preview, 500)` check in
  `validateTouchRequest`; `truncatePreview()` already caps storage at
  500 chars internally.
- Client `MAX_MESSAGE_LENGTH` 4000 → 5000. Textarea `maxlength` raised
  to 25000 so users can keep typing past one message worth.
- New `splitTextIntoChunks(body)` splits long input prefering paragraph
  breaks (`\n\n`), then line breaks, then sentence boundaries, then
  whitespace; hard 5000-char cut as last resort so input is never lost.
- New `sendChunkedTextMessage(body, replyToMessageId)` posts each chunk
  sequentially with a `[N/M]` continuation marker so receivers see them
  in order. Reply target attaches only to chunk 1. Per-chunk failures
  are reported without losing the remaining chunks.
- Attachments still cap at 5000 chars for the optional caption with a
  clear error suggesting the long text be sent first.

User experience: a brief composer notice reads
`Long message - sending as N parts...`, then chat continues normally.
No silent failures, no lost input.

#### 7d. Per-message timestamp on every bubble

**Why** — only the group-header showed time; individual messages in a
sender's run had no per-bubble timestamp.

**What** — every user-message bubble now renders a small
`.jim-message-bubble-time` (HH:MM) inside `.jim-message-meta`. Hover
shows the full date+time via the `title` attribute. Own (blue) bubbles
use a light/white timestamp; other-user bubbles use the muted gray. The
group header time and Jira Assistant card timestamps are unchanged.

Files: `JimConversation.java`, `JimConversationService.java`,
`JimConversationServiceImpl.java`, `JimMessageServiceImpl.java`,
`JimAttachmentService.java`, `JimRestJsonMapper.java`,
`JimValidation.java`, `js/jim-messenger.js`,
`templates/messenger-app.vm`, `css/jim-messenger.css`.

---

## Backend changes summary

| File | Change |
|---|---|
| `JimMessageLifecycle.java` | New `EDIT_WINDOW_MS = 30 min` + `isWithinEditWindow()` |
| `JimMessageServiceImpl.java` | Edit window enforcement; per-message push payload uses Map variant with `type=chat_message` / `jira_assistant` + `url` + `conversationId` + `messageId`; chat send + issue-link send + post-edit refresh pass sender to `touchConversation` |
| `JimValidation.java` | `MAX_MESSAGE_BODY_LENGTH` 4000 → 5000 |
| `JimConversation.java` (AO) | New `LAST_MESSAGE_SENDER_USER_KEY` column (nullable, auto-migrated) |
| `JimConversationService.java` | New 3-arg `touchConversation(int, String, String)` overload |
| `JimConversationServiceImpl.java` | Stores sender on save; **removed obsolete 500-char preview pre-validation** that blocked long messages |
| `JimAttachmentService.java` | Attachment push uses Map variant; passes sender to `touchConversation` |
| `JimRestJsonMapper.java` | DTO exposes `canEdit` window-aware, `lastMessageSenderUserKey`, `lastMessageOwn` |
| `JimPushService.java` | New `pushToUserAsync(userKey, Map<String,String> payload)` overload |
| `JimPushServiceImpl.java` | Map variant is the canonical impl; legacy 4-arg delegates; JSON builder emits any non-blank field |
| `JimAdminResource.java` | Admin test push sends `{type:"test", ...}` rich payload |

No REST endpoint paths changed. No AO entity besides `JimConversation`
got a new column. No security/permission/CORS change.

## Frontend changes summary

| File | Change |
|---|---|
| `js/jim-sw.js` | Rewritten as payload-first SW; zero `fetch()` calls; generic fallback for empty pushes; `new URL()` builds explicit same-origin URLs; `[CorbitChat SW]` diagnostic logs |
| `js/jim-messenger.js` | Notification status text link (3 states); 30-min edit-window UI guard; explicit same-origin SW registration; opaque-origin detection + clear error; `setTimeout(0)` push setup; `MESSAGE_POLL_MS` 5000 → 2500; visibility-change refresh; `init()` hard-guarantees polling; per-chat draft map + save/restore on conversation switch; sender-aware sidebar preview; new mention-picker (single / multi / all); long-message chunked sender; per-bubble HH:MM; diagnostic logs |
| `templates/messenger-app.vm` | Footer status link replaces bell button; textarea `maxlength` 4000 → 25000 |
| `css/jim-messenger.css` | Push-status link states; preview own/other/draft pills; bubble-time element; mention picker (Mention all header, checkbox column, footer with Insert/Clear) |

## How to test from the UI

After a hard refresh (Ctrl/Cmd + Shift + R) of the chat page:

### Notification status (1)
Look at the bottom-left of the sidebar — you should see
`● Active · Enable notifications` (or `Notifications on` /
`Notifications blocked` depending on browser state) instead of the old
crossed bell.

### 30-min edit window (2)
Send a message → wait 30+ minutes → hover the bubble → the **Edit**
button is gone. Server also rejects manual `PUT /messages/<id>` calls
on old messages with HTTP 403 *"Messages can only be edited within 30
minutes."*

### Chrome push (3, 4)
DevTools → Application → Service Workers → **Push** (no payload). The
SW shows the generic `CorbitChat / You have a new message` notification
and prints `push without payload - showing generic fallback`. **No**
`Failed to fetch` / `origin null` errors. Admin → CorbitChat
Configuration → Notifications → **Send test notification to me** shows
a Chrome notification reading `CorbitChat test` / `Push notifications
are working.`

### In-app delivery speed (5)
Open two browsers as different users. Send a message from A. B sees it
within ~2.5 s. Switch B's tab to another website for 30 s while A sends
another message; switch back to B's chat tab — the new message appears
within ~250 ms (visibility refresh). DevTools Console shows
`[CorbitChat poll] visibility refresh` on that wake-up.

### Mention picker (6)
In any group/project chat click the `@` icon. The dropdown now has:
- a blue **Mention all members** header (single click — mentions
  everyone),
- one row per member with a checkbox **and** a clickable name area
  (single-click name → mention only that one; tick multiple boxes →
  use footer **Insert N selected**).

### Drafts (7a)
Open Chat A, type `Hello from A`, click Chat B without sending. Chat B's
composer is empty; the sidebar shows Chat A with an amber `DRAFT` pill +
your unsent text. Click Chat A again — your text is restored. Send — the
draft pill disappears and is replaced by your sent message preview.

### Sender-coloured preview (7b)
Send a message to any conversation; sidebar preview for it sits on a
light **blue** background. When the other user replies, the preview
repaints in a light **gray** background.

### 5000-character + chunked send (7c)
Paste ~4500 chars → Send → arrives as one normal bubble.
Paste ~5000 chars → Send → arrives as one normal bubble (right at cap).
Paste ~12000 chars → Send → brief notice
`Long message - sending as 3 parts...` and the recipient sees three
ordered bubbles with `[1/3]` / `[2/3]` / `[3/3]` markers.

### Per-bubble timestamp (7d)
Send three messages in a row to the same chat. Each individual bubble
now shows its own `HH:MM` in the bottom-right corner, not just the
first. Hover any timestamp for the full date+time tooltip.

---

## Build / install

Build (offline Maven repo on this server):

```
cd /root/jira-dev/jira-issue-chat-panel
mvn -s /root/.m2/settings.xml \
    -Dmaven.repo.local=/root/maven-repos/atlassian-jira-offline \
    -o clean package -DskipTests
```

Artifact: `target/corbitchat-jira-dc-1.0.0-internal.jar` (~430 KB).

Install on the `jira-srv` test container (always remove old jars first
to avoid duplicate-plugin clashes):

```
docker exec -u 0 jira-srv bash -lc \
  'rm -f /var/jira/plugins/installed-plugins/jira-internal-messenger-*.jar \
         /var/jira/plugins/installed-plugins/corbitchat-jira-dc-*.jar'
docker cp target/corbitchat-jira-dc-1.0.0-internal.jar \
          jira-srv:/var/jira/plugins/installed-plugins/
docker exec -u 0 jira-srv bash -lc \
  'chmod 644 /var/jira/plugins/installed-plugins/corbitchat-jira-dc-1.0.0-internal.jar'
docker restart jira-srv
```

For UPM install on a different server use **Manage apps → Upload app →
pick the JAR** (no restart needed).

## Verified on jira-srv

```
plugin version              : 1.0.0-internal
POST /messages 4500 chars   : HTTP 200
POST /messages 5000 chars   : HTTP 200
POST /messages 5500 chars   : HTTP 400 "body exceeds maximum length of 5000"
POST /messages (12 KB)      : split client-side, 3x HTTP 200 with [1/3]..[3/3]
PUT  /messages/<old>        : HTTP 403 "Messages can only be edited within 30 minutes."
POST /admin/test-notification: {sent:true, subscriptions:1}
GET  /conversations          : carries lastMessageSenderUserKey + lastMessageOwn
served jim-sw.js             : 0 fetch() calls, 0 references to /push/summary
Polling                      : MESSAGE_POLL_MS=2500, visibility refresh wired
```

---

*Branch:* `internal-corbit`
*Commits in this release:* `bcde651`, `5165035`, `c6eb57d`, `be9a849`,
`5e23db1`, `39513db`, `5f0f312` (chronological).
