# CorbitChat — Internal Build Release Notes

Release notes for the `internal-corbit` branch (on-prem internal distribution
build, Marketplace licensing disabled). For the marketplace build's notes
see `docs/marketplace/release-notes.md`.

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
