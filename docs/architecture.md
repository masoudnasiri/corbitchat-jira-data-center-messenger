# CorbitChat for Jira — Architecture & Capabilities

**Document scope.** A complete, definitive reference for what
CorbitChat is, what it does, how the pieces fit together and what it
can do. Written for engineers who will maintain, extend or diagnose
the plugin. Feature lists here are exhaustive (not marketing-style
highlights).

**Applies to release line.** `1.0.0-internal-*` (internal on-prem
distribution). The Marketplace distribution uses the same modules
plus the `JimLicenseService` gate.

**Plugin key.** `com.corbitlogic.corbitchat.jira.dc`  
**Host.** Atlassian Jira Data Center 9.x (tested against 9.17.5).

---

## Table of contents

1. [Executive summary](#1-executive-summary)
2. [Complete feature list](#2-complete-feature-list)
3. [Architecture at a glance](#3-architecture-at-a-glance)
4. [Data model (Active Objects tables)](#4-data-model-active-objects-tables)
5. [Backend components](#5-backend-components)
6. [Frontend components](#6-frontend-components)
7. [Wiring — how everything is connected](#7-wiring--how-everything-is-connected)
8. [URL map & REST reference](#8-url-map--rest-reference)
9. [Data flows for major features](#9-data-flows-for-major-features)
10. [Admin console](#10-admin-console)
11. [Security & permissions](#11-security--permissions)
12. [External integrations](#12-external-integrations)
13. [Configuration surface](#13-configuration-surface)
14. [Extension points](#14-extension-points)
15. [Operational notes](#15-operational-notes)

---

## 1. Executive summary

CorbitChat is a Jira Data Center P2 plugin that adds three
independent-but-integrated capabilities to a Jira instance:

1. **Team messenger.** A Slack/Telegram-style chat surface inside
   Jira: direct chats, group chats, project chats, mentions,
   reactions, replies, pinned messages, drafts, presence, edit /
   delete windows, drag-and-drop attachments, voice messages, image
   preview lightbox, message forwarding, per-message read receipts,
   Web Push notifications, license gating (in the marketplace
   build).

2. **Jira Assistant.** An automatic bot conversation that surfaces
   Jira issue events (mentions in comments, assignments, status
   changes, issue links) as chat messages the user can act on — so
   users see and acknowledge Jira activity inside CorbitChat.

3. **Jira-side enhancements** that live outside the chat surface:
   - **Board Gallery** — a visual card directory of every Jira
     board the current user can access, added to the top nav.
   - **Comment Reply** — a *Reply* action on every native Jira
     issue comment that opens an inline composer, auto-mentions
     the parent author, quotes the parent, submits as a real
     native Jira comment (so Jira's native mention notification +
     the plugin's Assistant listener both fire automatically), and
     supports drag-and-drop file attachments.

Everything is server-rendered Velocity + vanilla JS + AUI CSS. No
React / Atlaskit. Persistence is Jira Active Objects for structured
data; PluginSettings for simple key/value admin toggles. All
Jira-facing writes go through the standard Jira REST / event / AO
paths — no direct DB writes to Jira's own tables, no Jira-core
patch.

---

## 2. Complete feature list

### 2.1 Messenger — conversations

- **Direct 1-on-1 chats.** Auto-provisioned on first message to a
  user; user-A/user-B key pair keyed uniquely.
- **Group chats.** Named group with a member list, roles
  (`OWNER`, `MEMBER`), admin controls (add / remove members,
  delete group). Group modal supports typeahead search and
  multi-select.
- **Project chats.** One auto-provisioned group conversation per
  Jira project (`JimProjectChatService.ensureProjectConversation`),
  members auto-added on first access if the user can browse the
  project. Rendered in a Jira project side-panel *and* on the
  standalone `/jim/project-chat` page.
- **System conversations.** Special conversation type used by the
  Jira Assistant bot (system-generated messages, no user composer).
- **Sidebar tabs.** All / Personal / Groups filter tabs; recent
  activity ordering; unread badge counts per conversation and a
  page-wide unread count.
- **Search** across accessible users (excluded by policy respected).

### 2.2 Messenger — messages

- **Text messages** up to **5,000 characters**. Longer text is
  **auto-split** into ordered chunks at paragraph → line → word
  → char boundaries and sent sequentially without user
  intervention.
- **RTL / LTR detection** per-message; Persian / Arabic / Hebrew
  text is bidi-correctly rendered inside LTR bubble chrome.
- **Mentions.** `@username` typeahead palette (group / project
  chats only). Single-click select or multi-select with checkboxes.
  "Mention all" option inserts every group member. Backend parses
  `[~username]` tokens and drives the notification / read-state
  flow.
- **Emoji picker** with the 10 approved emoji + 🌱; picker inserts
  at caret; used both in text and in reactions.
- **Replies to specific messages.** Reply chip above the composer
  before send; sender + one-line preview on the resulting bubble;
  click-to-jump-and-highlight the original.
- **Edit** own messages inside a **30-minute window**
  (server-enforced via `JimMessageLifecycle.EDIT_WINDOW_MS`).
  Client edits in the **main composer** at the bottom of the chat
  (amber "Editing message — Esc to cancel" chip; **Esc** restores
  the pre-edit draft). Rejected past window with a friendly error.
- **Delete** own messages: soft-delete flag preserves audit
  trail, bubble replaced by "This message was deleted."
- **Forward** any of the user's own or others' messages (including
  files) to another conversation via a search-picker modal. Files
  are re-uploaded to the target conversation.
- **Reactions.** Up to 10 emoji per message per user; aggregate
  counts + who-reacted-what visible.
- **Pinned messages.** One pinned message per conversation; sticky
  banner above the message list; pin / unpin from the message
  action menu.
- **Per-message timestamps.** Every bubble shows its exact send
  time; the group header shows a relative timestamp.
- **Read receipts.**
  - Direct chats: single tick on send, double-tick when the other
    user's `last_read_message_id` catches up; exact "seen at" time.
  - Group / project chats: per-message **Info** button opens a
    modal listing delivery + read status per member.
- **Message actions menu** (per bubble): React · Reply · Forward
  · Pin/Unpin · Info · Edit · Delete (gated by permissions +
  edit-window).
- **Draft preservation.** Per-conversation drafts persisted in
  memory; switching conversations preserves the outgoing draft and
  restores the incoming one; sidebar shows a "Draft: …" preview
  for conversations with unsent text.

### 2.3 Messenger — attachments

- **Multi-file upload** (drag-and-drop, paste from clipboard, file
  picker). Multiple queued files upload sequentially; caption text
  attached to the first upload only.
- **Attachment types.**
  - Images (thumbnail preview in bubble; **click to open lightbox**;
    Download link inside the lightbox).
  - Audio (inline `<audio>` element with playback restoration when
    the chat re-renders around it).
  - Voice messages: browser `MediaRecorder`-based recording with a
    timer, cancel, and send buttons; encoded as WebM/Opus; sent as
    a normal audio attachment.
  - Generic files (card with icon, filename, size, Download).
- **Size limit.** Configurable per admin (default 150 MB, max
  200 MB).
- **Type gating.** Admin-configured extension allowlist + a hard
  denylist enforced server-side (`JimAttachmentPolicy`). Audio
  files (voice messages) bypass the admin extension allowlist so
  admins don't have to whitelist `.webm`.
- **UTF-8 filenames.** Multipart upload path decodes RFC 5987
  `filename*=UTF-8''…` and recovers UTF-8 from legacy ISO-8859-1
  `filename="…"`. Download path emits RFC 6266/5987
  `Content-Disposition` with both an ASCII fallback and
  `filename*=UTF-8''<percent-encoded>` so browsers save the
  original name (Persian / Arabic / CJK).
- **HTTP range** support for streaming; images and audio use
  `inline` content-disposition (for previews), other files use
  `attachment`.

### 2.4 Messenger — notifications

- **Web Push (RFC 8030 + VAPID RFC 8292).**
  - Per-user push subscription registered via the browser's
    `PushManager.subscribe`.
  - **Payload-first Service Worker** (`jim-sw.js`): all metadata
    for the notification arrives inside the push payload; the SW
    does not make network calls to render a notification.
  - Server sends **encrypted** payloads to the subscription
    endpoint with the P-256 ECDH + HKDF + aes128gcm content
    encoding.
  - Falls back to a generic "You have a new message" title if the
    payload can't be decrypted.
- **Push scenarios.** Direct chat messages, group / project mentions,
  Jira Assistant events, admin "test notification" button.
- **Push gating.** Admin toggle `enableWebPush`, per-user preference
  (disable in the sidebar footer), permission state
  (default/granted/denied) surfaced as text (never a mystery icon).
- **In-app unread notification.** Top-nav badge across all Jira
  pages; browser `Notification` fallback if push is unavailable.
- **License gating** (marketplace build): push subscribe + push
  test both return HTTP 402 when the license is invalid.

### 2.5 Jira Assistant (bot conversation)

Automatic per-user system conversation that ingests Jira events:

| Event | Trigger | Bubble content |
|---|---|---|
| Comment mention | `[~user]` in comment body (via `CommentCreatedEvent` + `IssueEvent`) | `<Actor> mentioned you in <KEY>. "<preview>"` with issue link |
| Assignment | `IssueEvent` with an assignee change | `<Actor> assigned <KEY> to you. Status: <status>` |
| Status change | `IssueEvent` with a status transition | `<Actor> changed <KEY> from <old> to <new>` |
| Issue link | Direct call to `sendIssueLink` from the chat composer | Card with issue key + summary + status + priority + type |

- **Deduplication.** Every event carries a fingerprint
  (`EVENT|target|issue|actor|commentId|from|to`); duplicates are
  swallowed via `JimEventLog`.
- **Self-mention skip.** The mention listener does not create an
  Assistant entry for the actor themselves.
- **Actioned / acknowledged state.** Assistant messages carry an
  `ACTIONED`/`ACTIONED_AT` flag. Users can click "Mark as seen" (or
  the built-in issue link) to acknowledge; the bubble muters and
  shows an "Acknowledged" pill. State is per-user, persisted.
- **Assistant push.** Every Assistant event fires a Web Push
  notification unless the admin's per-event push gate is off.

### 2.6 Board Gallery (`/plugins/servlet/jim/boards`)

- **Card grid** of every Jira board the current user can browse.
- **Per-card data**: board name + type (Scrum / Kanban) + board id,
  real project avatar (fetched from
  `/rest/api/2/project/<key>` via Jira REST), project name (link),
  project lead name, "My tasks" count (issues assigned to the
  current user, via a JQL search), "All project tasks" link,
  "Project chat" link (opens the CorbitChat project chat panel).
- **Client-side search** + Scrum / Kanban filter tabs.
- **Page-level "Open Chat"** button in the header.
- **Same-origin URL rewriting.** Project avatar URLs coming back
  from Jira as absolute `https://<canonical>/secure/projectavatar…`
  are stripped to `path+query` so they resolve against the
  browser's current origin (fixes cross-origin image blocking on
  IP-based / test hosts).
- **Empty state / error state / long-name handling.**

### 2.7 Reply to Jira comments

- **Reply link** injected into every native Jira comment's action
  toolbar (at the front, next to Edit / Delete / Pin / 🙂).
- **Inline composer** opens directly below the parent comment with
  a `↳ Replying to <Author>` header, quoted preview of the parent,
  autofocused textarea, **Attach** button (paperclip icon),
  Cancel and Send Reply buttons.
- **Drag-and-drop** files anywhere onto the composer (panel tints
  blue while dragging). Multiple files supported.
- **Per-file upload chips** with icon, filename, "(uploading…)",
  red "Upload failed" line on error, ✕ to remove (which DELETEs the
  uploaded file from the issue).
- **Assembled body.** `[~author]\n\n{quote}<excerpt>{quote}\n\n<text>\n\n<attachment wiki tokens>`
  where each attachment becomes `!filename.png|thumbnail!` (image)
  or `[^filename.ext]` (file). Jira's wiki renderer resolves these
  into inline thumbnails / native attachment links.
- **Native comment.** Posted via
  `POST /rest/api/2/issue/<keyOrId>/comment` — a real Jira
  comment. Native mention notification + CorbitChat Assistant
  entry both fire automatically.
- **In-place insert.** On success we fetch
  `GET /rest/api/2/issue/<key>/comment/<id>?expand=renderedBody`
  and inject a comment DOM block (matching Jira's own
  `system-comment-issue-page-view.vm` template) in the composer's
  slot — no page reload, brief blue-highlight animation, auto
  scroll into view.
- **"In reply to" badge.** Every comment whose body starts with
  a mention followed by a blockquote gets an "↳ In reply to
  <user>" badge above it.
- **Lifecycle.** Success keeps attachments; cancel / Esc / ✕ /
  removing a chip DELETEs orphan uploads.
- **Edge cases.** Self-reply skips mention. Restricted /
  permission-scoped comments respected by native Jira. Inactive /
  deleted authors filtered by `JimMentionParser`. Multiple replies
  to one parent all work. Long parent quoted to 240 chars.
- **View-variant support.** `/browse/<KEY>`,
  `/projects/<PROJ>/issues/?selectedIssue=<KEY>`,
  `/issues/?selectedIssue=<KEY>`, Agile board side-panel view.
  Issue key resolved from 6 fallback sources including the parent
  comment's own DOM (`jira-comment-pins`, edit-comment link,
  ancestor `data-issue-key`, URL parsing).
- **Diagnostics.** `window.JimCommentReply.diagnose()` reports
  version, counts, hook availability. Verbose logging togglable
  via `?jimReplyDebug=1` or `localStorage.jimReplyDebug=1`.
- **Robustness.** Tolerant author resolver (5 strategies); button
  injects even when author unresolvable; MutationObserver +
  tapered periodic backstop scan (750 ms × 30 s then 5 s × 4.5 min)
  + AJS `ajaxStop` + `JIRA.Events.NEW_CONTENT_ADDED` hooks.

### 2.8 Admin console (`/plugins/servlet/jim/admin`)

Jira System-Administrator-only. Tabs:

- **Overview.** Plugin version, license (marketplace build), chat
  mode, policy count, quick-glance health.
- **Access Control.** Chat mode (Allow everyone / Restricted /
  Disabled); policy table (source: USER|GROUP × target:
  USER|GROUP|ANY × action: ALLOW|DENY; capabilities:
  `can_search` / `can_start_chat` / `can_receive_chat`; enabled
  flag; priority). DENY overrides ALLOW; server-enforced in user
  search, conversation creation and send.
- **Notification Settings.** `enable_web_push`,
  `notification_detail_level`, `aggregate_notifications`,
  `mention_notifications`, `assignment_notifications`, plus a
  **Test notification** button that sends a real Web Push to the
  current admin.
- **Attachment Settings.** `attachments_enabled`,
  `max_attachment_size_mb`, `allowed_extensions`,
  `image_preview_enabled`.
- **Branding.** Custom chat title and logo (file picker with
  data-URL preview; 512 KB URL cap).
- **Diagnostics.** Plugin version, REST health, AO health, Jira
  base URL, current request base URL, HTTPS detected, VAPID
  configured, push enabled, push subscription count, failed push
  count.
- **Audit trail.** Every admin write is recorded to
  `JimAdminAudit` (userKey / action / details / createdAt).
- **License section** (marketplace build): status, plugin key,
  Data Center flag, error message.

### 2.9 Mobile support

- **Mobile UI adjustments.** Responsive CSS breakpoint;
  conversation sidebar collapses; back-button pattern.
- **Mobile fly-out menu injection.**
  `JimMobileMenuInjectionFilter` rewrites Jira's
  `/plugins/servlet/mobile*` HTML to inject a CorbitChat entry
  into the mobile fly-out menu.

---

## 3. Architecture at a glance

```
                           ┌────────────────────────────────────────────┐
                           │              Jira Data Center               │
                           │                                             │
    Browser (Jira page)    │        Plugin OSGi bundle                   │
    ┌───────────────┐      │   ┌──────────────────────────────────┐      │
    │ jim-messenger │──────┼──▶│  Servlets  ─▶  ContextProvider   │      │
    │ jim-comment-  │      │   │            ↓                      │      │
    │  reply        │──────┼──▶│  REST /rest/jim/1.0/*             │      │
    │ jim-board-    │      │   │            ↓                      │      │
    │  gallery      │──────┼──▶│  Services (Spring-wired)          │      │
    │ jim-admin     │      │   │            ↓                      │      │
    │ jim-nav-badge │      │   │  Active Objects (MySQL/Postgres)  │      │
    │ jim-sw.js     │◀─────┼── │            ↑                      │      │
    │  (SW push)    │  push│   │  JimIssueEventHandler ◀── Jira    │      │
    │               │      │   │  JimPluginBootstrap  ◀── Events   │      │
    │               │      │   │  (Comment/Issue/Assignment)       │      │
    └───────────────┘      │   └──────────────────────────────────┘      │
                           │            ↑                                │
                           │  Jira REST (/rest/api/2/*, /rest/agile/1.0) │
                           └────────────────────────────────────────────┘
```

Three data planes:

- **User traffic.** Browser ↔ plugin REST + servlets. Same-origin
  session cookies.
- **Event plane.** Jira dispatches `IssueEvent` /
  `CommentCreatedEvent`; `JimPluginBootstrap` listens; async work
  through `JimEventExecutor` writes to AO and triggers push.
- **Push plane.** Server-side VAPID push out to each subscribed
  browser's endpoint; the browser's Service Worker (`jim-sw.js`)
  decrypts + displays the notification.

---

## 4. Data model (Active Objects tables)

Namespace `com.corbitlogic.jira.internalmessenger.ao`, prefix
`AO_099FDF_*` in the physical schema.

| AO entity | Physical table | Purpose |
|---|---|---|
| `JimConversation` | `AO_099FDF_JIM_CONVERSATION` | Every chat (direct, group, project, system). |
| `JimMessage` | `AO_099FDF_JIM_MESSAGE` | Every message. |
| `JimReadState` | `AO_099FDF_JIM_READ_STATE` | Per-user last-read message + last-read time per conversation. |
| `JimAttachment` | `AO_099FDF_JIM_ATTACHMENT` | Message attachments metadata (files stored on disk). |
| `JimReaction` | `AO_099FDF_JIM_REACTION` | Emoji reactions on messages. |
| `JimGroupMember` | `AO_099FDF_JIM_GROUP_MEMBER` | Group / project chat membership + role. |
| `JimPushSubscription` | `AO_099FDF_JIM_PUSH_SUB` | Per-user Web Push endpoints. |
| `JimEventLog` | `AO_099FDF_JIM_EVENT_LOG` | Deduplication fingerprints + audit for Assistant events. |
| `JimAccessPolicy` | `AO_099FDF_JIM_ACCESS_POLICY` | Admin-configured ALLOW/DENY policies. |
| `JimAdminAudit` | `AO_099FDF_JIM_ADMIN_AUDIT` | Admin-console change audit trail. |

**JimConversation** fields (highlights): `conversationType`
(`DIRECT`|`GROUP`|`PROJECT`|`SYSTEM`), `userAKey`/`userBKey` for
direct, `groupName` / `createdByUserKey`, `projectKey`,
`lastMessageAt` / `lastMessagePreview` /
`lastMessageSenderUserKey`, `systemKey` for bot conversations.

**JimMessage** fields (highlights): `conversationId`, `senderType`
(`USER`|`JIRA_ASSISTANT`), `senderUserKey`, `body`, `bodyFormat`,
`eventType` (`NORMAL`|`MENTION`|`ASSIGNMENT`|`COMMENT`|
`STATUS_CHANGE`|`GROUP_EVENT`|`ISSUE_LINK`), issue-context fields
(`issueKey`, `issueSummary`, `issueUrl`, `actorUserKey`,
`actorDisplayName`), lifecycle flags (`edited`+`editedAt`,
`deleted`+`deletedAt`+`deletedByUserKey`, `pinned`,
`actioned`+`actionedAt`), `replyToMessageId`.

**JimAttachment** fields: `messageId`, `conversationId`,
`uploaderUserKey`, `originalFilename`, `storedFilename`,
`storagePath`, `contentType`, `fileSize`, `fileKind`
(`IMAGE`|`AUDIO`|`FILE`|…), `thumbnailPath`, `width`/`height`,
soft-`deleted` flag.

**JimAccessPolicy** fields: `sourceType`+`sourceValue`,
`targetType`+`targetValue`, `action` (`ALLOW`|`DENY`),
`canSearch`, `canStartChat`, `canReceiveChat`, `enabled`,
`priority`, audit fields.

**JimPushSubscription** fields: `userKey`, `endpoint` (full URL),
`endpointHash` (for dedup / lookup), `p256dhKey`, `authKey`.

**Attachment files on disk.** Stored under
`<jira-home>/data/corbitchat/attachments/<yyyy>/<mm>/<uuid>-<safe-name>`
by `JimAttachmentStorageService`.

**No Jira-core writes.** The plugin never writes to
`jiraissue`, `jiraaction`, `fileattachment` etc. All Jira-side
writes go through Jira's own REST or event APIs.

---

## 5. Backend components

### 5.1 Services (business logic, `service/`)

| Service | Responsibility |
|---|---|
| `MessengerService(Impl)` | Thin facade for cross-service orchestration (used by servlets). |
| `JimConversationService(Impl)` | Create / lookup conversations, touch `lastMessage*`. |
| `JimMessageService(Impl)` | Create user & system messages, edit / delete, chunking, `markActioned`, edit-window check, push fan-out. |
| `JimGroupService(Impl)` | Group create / delete, member add / remove, role checks. |
| `JimProjectChatService(Impl)` | Provision per-project group conversation; sync membership with project access. |
| `JimUserSearchService(Impl)` | Typeahead user search over Jira; filtered by `JimAccessPolicyService`. |
| `JimReactionService(Impl)` | Toggle / list emoji reactions; enforces the 10-emoji allowlist. |
| `JimReadStateService(Impl)` | Per-user last-read pointers; unread counters. |
| `JimPresenceService(Impl)` | In-memory presence (Active / Away / Offline). |
| `JimAttachmentService` (+ `JimAttachmentStorageService`) | Attachment metadata + on-disk storage; enforces admin size / extension policies. |
| `JimPushService(Impl)` | VAPID key management, subscription CRUD, encrypted push send, per-event push gating. |
| `JimMentionParser`, `MentionParser` | Parse `[~username]` tokens; resolve to `ApplicationUser`. |
| `JimIssueEventHandler(Impl)` | Translate `IssueEvent` / `CommentCreatedEvent` payloads into Assistant messages; dedup via `JimEventLog`; self-mention skip; recipient resolution via `JimNotificationRecipientResolver`. |
| `JimPermissionService(Impl)` | "Can this user see this conversation / message / group?" |
| `JimAccessPolicyService(Impl)` | Load + evaluate `JimAccessPolicy` rows. Public API: `isChatEnabled`, `canSearch`, `canChatWith`, `requireCanChatWith`. DENY overrides ALLOW. |
| `JimAdminSettingsService(Impl)` | Typed access to admin toggles stored in PluginSettings. |
| `JimAdminAuditService(Impl)` | Record admin actions + list recent audit rows. |
| `JimLicenseService(Impl)` | (Marketplace build) wraps `PluginLicenseManager`; caches status; `canUseMessaging` / `canUseAdminSettings` / `canUsePushNotifications` / `canUploadAttachments`. In internal build always returns "licensed". |
| `JimApiService(Impl)` | REST-facing orchestration; keeps REST resources thin. |
| `JimDtoMapper` (+ `JimRestJsonMapper`) | DTO + Map-based JSON serialization. |
| `JimAoSchemaDiagnostics` | Dev-only AO schema inspection. |

### 5.2 REST resources (`rest/`, base path `/rest/jim/1.0`)

Each `@Path`-annotated resource is a thin controller that
delegates to services. Full list in §8.

### 5.3 Servlets (`web/`)

| Servlet | URL | Purpose |
|---|---|---|
| `JimChatServlet` | `/plugins/servlet/jim/chat` | Main chat page. Renders `templates/messenger.vm` (which includes `messenger-app.vm`). |
| `JimProjectChatServlet` | `/plugins/servlet/jim/project-chat` | Standalone project-chat page. Renders `messenger-app.vm` with `projectChatMode=true`. |
| `JimAdminServlet` | `/plugins/servlet/jim/admin` | Admin console. Renders `templates/admin.vm`. SysAdmin-gated. |
| `JimBoardGalleryServlet` | `/plugins/servlet/jim/boards` | Board Gallery. Renders `templates/board-gallery.vm`. |
| `JimServiceWorkerServlet` | `/plugins/servlet/jim/sw.js` | Serves the push Service Worker at a same-origin path (required by Web Push spec for scope). |

Servlet filter: `JimMobileMenuInjectionFilter` rewrites Jira's
mobile menu HTML to add a CorbitChat entry.

### 5.4 Event listeners (`bootstrap/JimPluginBootstrap`)

Registers three `@EventListener` methods on the Atlassian event
publisher:

- `onIssueEvent(IssueEvent)` — dispatches to mention / assignment
  / status handlers based on event type + comment presence.
- `onCommentCreated(CommentCreatedEvent)` — mention handler on
  the modern comment-added event.
- (Third `@EventListener` reserved for future use — currently
  wired to the same mention flow for defensive symmetry.)

All actual work is queued through `JimEventExecutor` (a shared
executor named `jim-assistant-event-processor`) so the event bus
returns quickly.

### 5.5 Utilities (`util/`)

| Utility | Purpose |
|---|---|
| `JimConversationKeys` | Canonical (userA, userB) direct-key ordering. |
| `JimIssueUrlBuilder` | Base-URL-aware `/browse/<key>` link builder. |
| `JimMessageFlags` | Deleted / edited helpers. |
| `JimMessageLifecycle` | Edit window (30 min) and delete window helpers. |
| `JimNotificationRecipientResolver` | Which users should get a mention/assignment/status notification (permissions + access-policy aware). |
| `JimSanitizer` | HTML / text sanitization for stored bodies. |
| `JimValidation` | Length caps, mandatory-field checks (max body length = **5000**). |

### 5.6 Model enums (`model/`)

- `JimAttachmentFileKind` — `IMAGE` | `AUDIO` | `FILE` | …
- `JimBodyFormat` — `TEXT` | `MARKDOWN` | …
- `JimConversationType` — `DIRECT` | `GROUP` | `PROJECT` | `SYSTEM`
- `JimEventType` — `NORMAL` | `MENTION` | `ASSIGNMENT` |
  `COMMENT` | `STATUS_CHANGE` | `GROUP_EVENT` | `ISSUE_LINK`
- `JimSenderType` — `USER` | `JIRA_ASSISTANT`

### 5.7 Development-only resources (`rest/JimDev*`)

Under `/rest/jim/1.0/dev/*`. Gated by log level in production; used
by developers to seed test Assistant cards (`test-assignment-card`,
`test-mention-card`, `test-status-card`), inspect the AO schema
(`ao-schema`) and debug (`debug`).

---

## 6. Frontend components

Everything is vanilla JS + AUI CSS. No framework runtime.

### 6.1 JS modules (`src/main/resources/js/`)

| File | Loaded on | Role |
|---|---|---|
| `jim-messenger.js` | Chat pages + project chat panel | Complete chat SPA-ish surface: conversation list, message rendering, composer, replies, edits, drafts, reactions, mentions, emoji, voice, file attachments, forwarding, receipts, lightbox, push setup, polling. |
| `jim-api.js` | Chat surface | Thin REST client with a single `request()` helper; every method returns a promise. |
| `jim-comment-reply.js` | Every `/browse/<KEY>` and issue-view variant (`jira.view.issue`) | The Reply-to-comment feature (button injection, inline composer, attachment upload, in-place insert, badge, diagnostics surface). |
| `jim-board-gallery.js` | Board Gallery page | Card grid loader, per-card project + tasks queries, search + filter, same-origin avatar rewriting. |
| `jim-admin.js` | Admin console | Tabbed UI, settings loader / saver, policy CRUD + edit, license status, diagnostics, branding preview. |
| `jim-nav-badge.js` | Every Jira page (`atl.general`) | Top-nav unread badge + browser notification fallback. |
| `jim-sw.js` | Registered as Service Worker at `/plugins/servlet/jim/sw.js` | Push receive / show; payload-first with generic fallback. No network calls. |

### 6.2 CSS modules (`src/main/resources/css/`)

All scoped under `#jim-messenger-app`, `#jim-admin-page`,
`#jim-board-gallery`, or a per-injection class, so nothing bleeds
into surrounding Jira UI.

- `jim-messenger.css` — main chat surface + edit-in-composer chip.
- `jim-comment-reply.css` — inline composer + attach chips + badge.
- `jim-board-gallery.css` — board card grid.
- `jim-admin.css` — admin tabbed page.
- `jim-nav-badge.css` — top-nav badge.

### 6.3 Velocity templates (`src/main/resources/templates/`)

- `messenger.vm` — main chat page wrapper (parses `messenger-app.vm`).
- `messenger-app.vm` — the actual chat shell: sidebar, header,
  message list, composer, group modal, message-info modal, forward
  modal. Reused by both the main chat page and the project-chat
  web-panel.
- `admin.vm` — admin console.
- `board-gallery.vm` — Board Gallery page.

### 6.4 Web-resource bundles + WRM contexts

| Bundle key | Auto-loaded in | Contents |
|---|---|---|
| `jim-messenger-resources` | `jim.messenger` context (chat page + project chat panel) | `jim-messenger.css`, `jim-messenger.js`, `jim-api.js`, `corbit-app-logo.png` |
| `jim-nav-badge-resources` | `atl.general` context (every Jira page) | `jim-nav-badge.css`, `jim-nav-badge.js` |
| `jim-comment-reply-resources` | `jira.view.issue` context (every issue view) | `jim-comment-reply.css`, `jim-comment-reply.js` |
| `jim-board-gallery-resources` | Explicit `webResourceManager.requireResource` from `JimBoardGalleryServlet` | `jim-board-gallery.css`, `jim-board-gallery.js` |
| `jim-admin-resources` | Explicit require from `JimAdminServlet` | `jim-admin.css`, `jim-admin.js` |

---

## 7. Wiring — how everything is connected

### 7.1 Component wiring (`atlassian-plugin.xml`)

Every backend service, servlet, mapper, REST helper, listener and
utility class is declared as an `<component>` (or
`<component-import>` for Jira-provided beans) and gets constructor-
injected wherever it's needed. The plugin uses
Atlassian Spring Scanner-style import + component registration.

Notable component imports (Jira-supplied dependencies):

`ActiveObjects`, `EventPublisher`, `JiraAuthenticationContext`,
`UserManager`, `ApplicationProperties`, `JiraHome`,
`UserSearchService`, `AvatarService`, `TemplateRenderer`,
`WebResourceManager`, `GlobalPermissionManager`,
`TransactionalExecutorFactory`, `PluginSettingsFactory`.

Notable plugin components:

`jimConversationService`, `jimMessageService`,
`jimAttachmentService`, `jimGroupService`,
`jimProjectChatService`, `jimPresenceService`, `jimPushService`,
`jimAdminSettingsService`, `jimAccessPolicyService`,
`jimAdminAuditService`, `jimLicenseService`, `jimReactionService`,
`jimReadStateService`, `jimUserSearchService`, `jimApiService`,
`jimMentionParser`, `jimIssueEventHandler`, `jimEventExecutor`,
`jimPluginBootstrap` (LifecycleAware), `messengerService`,
`jimAdminServlet`, `jimChatServlet`, `jimProjectChatServlet`,
`jimBoardGalleryServlet`, `jimDtoMapper`, `jimRestJsonMapper`,
`jimAoSchemaDiagnostics`, `jimPermissionService`.

### 7.2 Navigation entries

| Location | Item | URL |
|---|---|---|
| `system.top.navigation.bar` (weight 60) | **Chat** | `/plugins/servlet/jim/chat` |
| `system.top.navigation.bar` (weight 65) | **Board Gallery** | `/plugins/servlet/jim/boards` |
| `system.user.options/personal` | **CorbitChat** (user menu) | `/plugins/servlet/jim/chat` |
| `admin_plugins_menu/jim-admin-section` | **CorbitChat Configuration** | `/plugins/servlet/jim/admin` |
| `jira.project.sidebar.plugins.navigation` | **Project Chat** | `/projects/<KEY>?selectedItem=…:jim-project-chat-link` |

Web-panel: `jim-project-chat-panel` renders `messenger-app.vm`
inside the project sidebar location via
`JimProjectChatPanelContextProvider`.

### 7.3 REST plugin module

`<rest key="jim-rest" path="/jim" version="1.0"/>` mounts every
`@Path`-annotated resource under `/rest/jim/1.0/…`.

### 7.4 Servlet filter

`JimMobileMenuInjectionFilter` — mapped on
`/plugins/servlet/mobile`, `/plugins/servlet/mobile/*`. Captures
the response HTML via `JimCapturingHttpServletResponse` and
injects a CorbitChat menu item via `JimMobileMenuHtmlInjector`.

### 7.5 Event listener registration

`JimPluginBootstrap` implements `LifecycleAware`; on
`onStart` it registers itself with the `EventPublisher`. Every
`@EventListener` method is invoked synchronously by Jira but
schedules the actual work on `JimEventExecutor` so the event bus
is never blocked.

---

## 8. URL map & REST reference

### 8.1 Servlets (HTML pages)

- `GET /plugins/servlet/jim/chat` — main chat page.
- `GET /plugins/servlet/jim/project-chat?projectKey=<KEY>` —
  standalone project chat page.
- `GET /plugins/servlet/jim/admin` — admin console (SysAdmin).
- `GET /plugins/servlet/jim/boards` — Board Gallery.
- `GET /plugins/servlet/jim/sw.js` — Service Worker script.

### 8.2 REST endpoints (all under `/rest/jim/1.0/`)

#### Health & metadata

- `GET /health` — REST alive check.
- `GET /ao-health` — Active Objects reachability.

#### Conversations (`/conversations`)

- `GET /conversations` — list current user's conversations.
- `GET /conversations/unread-count` — global unread count.
- `POST /conversations/direct` — create/get a direct conversation
  with a target user.
- `GET /conversations/{conversationId}/messages` — list messages
  (paginated via `limit` + `beforeMessageId`).
- `POST /conversations/{conversationId}/messages` — send a message
  (body / replyToMessageId / issueLink).
- `POST /conversations/{conversationId}/attachments` — upload an
  attachment (multipart).
- `GET /conversations/{conversationId}/pinned` — get pinned
  message.
- `POST /conversations/{conversationId}/read` — mark read.
- `GET /conversations/{conversationId}/messages/{messageId}/receipts`
  — group receipts (Info modal).

#### Messages (`/messages`)

- `PUT /messages/{messageId}` — edit body (server-enforces the 30 min window).
- `DELETE /messages/{messageId}` — soft delete.
- `POST /messages/{messageId}/pin` / `DELETE .../pin`.
- `POST /messages/{messageId}/action` — mark actioned (Assistant).
- `POST /messages/{messageId}/reactions` — toggle emoji reaction.

#### Groups (`/groups`)

- `POST /groups` — create group.
- `DELETE /groups/{conversationId}` — delete group.
- `GET /groups/{conversationId}/members` — list members.
- `POST /groups/{conversationId}/members` — add member.
- `DELETE /groups/{conversationId}/members/{memberUserKey}` —
  remove member.

#### Projects (`/projects`)

- `GET /projects/{projectKey}/conversation` — get/provision the
  project chat conversation for a project.

#### Users (`/users`)

- `GET /users/search?query=<q>` — typeahead search (policy-filtered).

#### Attachments (`/attachments`)

- `GET /attachments/{id}/download` — download with RFC 5987
  `Content-Disposition`.
- `GET /attachments/{id}/preview` — inline preview for images /
  audio.

#### Push (`/push`)

- `GET /push/config` — VAPID public key + settings.
- `POST /push/subscriptions` — save a browser subscription.
- `DELETE /push/subscriptions?endpoint=<url>` — remove one.
- `GET /push/summary` — kept for backwards compatibility.

#### License (`/license`)

- `GET /license/status` — minimal `{licensed: bool}` for regular
  users (marketplace build).

#### Admin (`/admin`, SysAdmin-only)

- `GET /admin/license` — full license status.
- `GET /admin/settings`, `PUT /admin/settings` — get / save admin
  toggles.
- `GET /admin/policies`, `POST /admin/policies`,
  `PUT /admin/policies/{id}`, `DELETE /admin/policies/{id}` —
  access-policy CRUD.
- `GET /admin/diagnostics` — full diagnostics.
- `GET /admin/audit` — recent admin audit entries.
- `POST /admin/test-notification` — send a real push to the
  current admin.

#### Dev-only (`/dev/*`)

`/dev/ao-schema`, `/dev/debug`, `/dev/test-assignment-card`,
`/dev/test-mention-card`, `/dev/test-status-card`.

---

## 9. Data flows for major features

### 9.1 Send a chat message

```
Browser (jim-messenger.js)
   │ typing / Enter
   ▼
POST /rest/jim/1.0/conversations/{id}/messages    (JimConversationResource)
   ▼
JimAccessPolicyService.requireCanChatWith(...)     (blocks if policy DENY)
JimLicenseService.canUseMessaging()               (marketplace build only)
   ▼
JimMessageService.createUserMessage(...)
   ├─▶ JimMessage AO row (edited=false, deleted=false)
   ├─▶ JimConversationService.touchConversation(lastMessage*)
   └─▶ JimPushService.pushToUserAsync(recipientKey, payload)  (async)
   ▼
Response 201 with message JSON
   ▼
Browser polls /conversations/{id}/messages (2500 ms) OR receives push
   ▼
Recipient's browser SW shows the push; app renders new bubble on next poll
```

### 9.2 Jira Assistant — mention-in-comment

```
Jira user posts a comment containing [~user2]
   ▼
Atlassian CommentCreatedEvent  ─────────────────▶  JimPluginBootstrap.onCommentCreated
                                                        │
                                                        ▼
                                                  JimEventExecutor.submit("mention",...)
                                                        │
                                                        ▼
                             JimIssueEventHandlerImpl.handleComment(payload)
                                                        │
                                                        ▼
                             JimMentionParser.parse(body) → [user2]
                                                        │
                                                        ▼
                             skip if actor == user2 (self-mention)
                             skip if user2 inactive / not visible
                                                        │
                                                        ▼
                             JimEventLog check fingerprint (dedup)
                                                        │
                                                        ▼
                             JimMessageService.createSystemMessage(
                                target=user2, event=MENTION,
                                body="<actor> mentioned you in <KEY>. \"...\"",
                                issueKey, issueSummary, issueUrl,
                                actorUserKey, actorDisplayName)
                                                        │
                                                        ▼
                             Assistant bubble appears in user2's bot conversation
                             + JimPushService push to user2's browser (if enabled)
```

### 9.3 Reply to a Jira comment

```
Browser (jim-comment-reply.js) — user clicks Reply
   ▼
openComposer(commentEl, author, parentExcerpt)
   ▼
[optional] user drags files → uploadAttachmentForReply()
   POST /rest/api/2/issue/{key}/attachments  (Jira native REST)
   → attachment id + filename in staged list
   ▼
User clicks Send Reply
   ▼
POST /rest/api/2/issue/{key}/comment   { body: "[~author]\n\n{quote}...{quote}\n\n<text>\n\n[^file.pdf]" }
   ▼
Jira native mention notification dispatched to <author>  ┐
Jira CommentCreatedEvent fires ─▶ JimPluginBootstrap    ┘  ← same code path as 9.2
                                                                │
                                                                ▼
                                                Assistant bubble appears in <author>'s bot conversation
   ▼
GET /rest/api/2/issue/{key}/comment/{newId}?expand=renderedBody
   ▼
buildCommentDom(commentJson) → in-place insert in composer's slot
Blue-highlight fade-in animation
"In reply to" badge injected above the new comment
```

### 9.4 Attachment upload (chat surface)

```
Browser (drag / paste / picker)
   ▼
POST /rest/jim/1.0/conversations/{id}/attachments  (multipart/form-data)
   ▼
JimMultipartParser
   ├─▶ RFC 5987 filename* decode
   └─▶ ISO-8859-1 → UTF-8 recovery for legacy filename
   ▼
JimAttachmentPolicy.validateUpload(size, ext, mime)
   ├─▶ admin allowlist (bypassed for AUDIO/voice)
   ├─▶ hard denylist
   └─▶ MAX_FILE_SIZE_BYTES (200 MB cap; admin default 150 MB)
   ▼
JimAttachmentStorageService.store(bytes) → <jira-home>/data/corbitchat/attachments/…
   ▼
JimAttachment AO row
   ▼
JimMessage AO row (attachments linked via messageId)
   ▼
touchConversation → push → response
```

### 9.5 Web Push

```
User grants permission
   ▼
Browser subscribes to PushManager with VAPID public key
   ▼
POST /rest/jim/1.0/push/subscriptions { endpoint, p256dh, auth }
   ▼
JimPushSubscription AO row
   ▼
Later, message arrives → JimPushService.pushToUserAsync(userKey, payload)
   ├─▶ build encrypted payload (P-256 ECDH + HKDF + aes128gcm)
   ├─▶ POST endpoint with Authorization: vapid t=<JWT>, k=<base64url>
   └─▶ browser SW receives push
   ▼
jim-sw.js decrypts payload, calls Notification API
   ▼
User taps → focus / open chat page with the right conversation
```

---

## 10. Admin console

Rendered by `JimAdminServlet` from `admin.vm` +
`js/jim-admin.js` + `css/jim-admin.css`. SysAdmin-gated.

Six tabs:

1. **Overview.**
2. **Access Control** — policy CRUD table with typeahead pickers
   for USER / GROUP source and target; edit-in-place; DENY overrides
   ALLOW; server-enforced.
3. **Notifications** — push + per-event toggles + Test notification
   button.
4. **Attachments** — enable / size / extensions / image preview.
5. **Branding** — chat title + logo (file → data URL).
6. **Diagnostics** — plugin health / version / VAPID / base URL /
   HTTPS / push counts.

Every write goes through `JimAdminAuditService.record(userKey,
action, details)` and appears in the Audit tab.

---

## 11. Security & permissions

- **Authentication.** Every servlet + REST endpoint requires an
  authenticated Jira user (`JiraAuthenticationContext.getLoggedInUser()
  != null`). Anonymous requests get 401.
- **SysAdmin gate.** Admin servlet + all `/admin/*` REST endpoints
  require `GlobalPermissionManager.hasPermission(SYSTEM_ADMIN, user)`.
- **Access policies.** `JimAccessPolicyService` evaluates DENY-
  overrides-ALLOW rules for `canSearch` / `canStartChat` /
  `canReceiveChat`. Enforced in user search, conversation creation
  and message send.
- **Native Jira permissions.** Board Gallery + Reply flow rely on
  Jira's own permission checks (`/rest/agile`, `/rest/api/2/*`,
  `/rest/api/2/issue/*/comment`). We never bypass native
  permissions; we never expose data the user isn't allowed to see.
- **XSRF.** All state-changing calls send
  `X-Atlassian-Token: no-check` and use same-origin session cookies.
- **XSS.** `JimSanitizer` scrubs stored bodies. Frontend renders
  through `escapeHtml`; `renderedBody` from Jira's comment REST is
  the only HTML we inject verbatim — that HTML has already been
  server-side sanitized by Jira's wiki renderer.
- **License.** Marketplace build gates messaging / admin / push /
  attachments via `JimLicenseService.can*()`; blocked calls return
  HTTP 402 with `error=LICENSE_INVALID`. Raw license is never
  logged or exposed.
- **Web Push.** VAPID subject / keys stored in PluginSettings.
  Subscription endpoints are per-user; deleting a subscription
  removes it from AO.

---

## 12. External integrations

- **Jira native REST** (via user's session cookies):
  - `/rest/api/2/issue/{key}/comment` — post native comments.
  - `/rest/api/2/issue/{key}/attachments` — attach files for
    inline reply.
  - `/rest/api/2/attachment/{id}` — delete orphan attachments.
  - `/rest/api/2/project/{key}` — project info for Board Gallery.
  - `/rest/api/2/search` (JQL) — "My tasks" counts.
  - `/rest/api/2/user` / `/rest/api/2/user/search` — user lookups.
  - `/rest/agile/1.0/board` — board list.
  - `/rest/greenhopper/1.0/rapidviews/list` — legacy board data
    (fallback).
- **Atlassian event bus** — `IssueEvent`, `CommentCreatedEvent`.
- **Atlassian Web Resource Manager** — bundles + contexts.
- **Atlassian PluginSettings** — admin toggles.
- **Atlassian Active Objects** — structured persistence.
- **Atlassian UPM `PluginLicenseManager`** — marketplace build
  only.
- **Web Push (RFC 8030 + VAPID RFC 8292)** — outbound HTTPS to
  each subscribed browser's push endpoint (Google FCM, Mozilla
  autopush, etc.).

---

## 13. Configuration surface

### 13.1 PluginSettings keys (namespace `com.corbitlogic.jim.admin.`)

- `chatMode` — `EVERYONE` | `RESTRICTED` | `DISABLED`
- `enableWebPush` — bool
- `notificationDetailLevel` — `FULL_MESSAGE` | `SENDER_ONLY` |
  `GENERIC_ONLY`
- `aggregateNotifications` — bool
- `mentionNotifications` — bool
- `assignmentNotifications` — bool
- `attachmentsEnabled` — bool
- `maxAttachmentSizeMb` — int (10..200, default 150)
- `allowedExtensions` — CSV
- `imagePreviewEnabled` — bool
- `brandingTitle` — string (max 100)
- `brandingLogoUrl` — string (max 524,288 for data-URLs)

### 13.2 VAPID keys (auto-generated)

Stored in PluginSettings via `JimPushServiceImpl`:
`vapidPublicKeyUncompressed`, `vapidPrivateKeyPkcs`, `VAPID_SUBJECT`
(defaults to `mailto:admin@<jira-host>`).

### 13.3 Constants (compiled in)

- `JimMessageLifecycle.EDIT_WINDOW_MS` — 30 min.
- `JimValidation.MAX_MESSAGE_BODY_LENGTH` — 5000.
- `JimAttachmentPolicy.MAX_FILE_SIZE_BYTES` — 200 MB (hard cap).
- Chat polling interval — 2500 ms.
- Reply-composer per-file attachment upload — no client-side cap
  (Jira's own attachment settings apply).

---

## 14. Extension points

### 14.1 New Assistant event type

1. Add a value to `JimEventType`.
2. Add an `EventPayload` DTO in `event/`.
3. Wire an `@EventListener` in `JimPluginBootstrap` (or hook into
   an existing one).
4. Add a handler method on `JimIssueEventHandler(Impl)` that
   builds a system message + dedup fingerprint.
5. (Optional) Add a "test-*-card" dev REST for reproducing the
   event locally.

### 14.2 New chat feature

1. Add server-side logic in the appropriate service; expose via
   REST resource; wire in `atlassian-plugin.xml`.
2. Add client-side function in `jim-messenger.js`; add UI in
   `messenger-app.vm`; add styles in `jim-messenger.css`.
3. Because `messenger-app.vm` is shared between the main chat
   page and the project chat panel, the feature ships to both
   surfaces automatically.

### 14.3 New admin toggle

1. Add a `KEY_*` + getter/setter in
   `JimAdminSettingsService(Impl)`.
2. Add a form field in `admin.vm` + wire in `jim-admin.js`.
3. Add the toggle key to
   `JimAdminResource.updateSettings()` handler.
4. Consume the toggle where relevant; record admin writes via
   `JimAdminAuditService`.

### 14.4 New page

1. Add a servlet class in `web/` extending `HttpServlet`.
2. Register it in `atlassian-plugin.xml` as `<component>` +
   `<servlet>`.
3. Add its web-resource bundle (CSS/JS) + require it from the
   servlet's context provider.
4. Add a `<web-item>` for the nav entry.

---

## 15. Operational notes

- **Rollback.** Every artifact is preserved in
  `/root/jira-dev/releases/`. Rollback = replace the JAR in
  `<jira-home>/plugins/installed-plugins/` + restart. No DB
  fix-up.
- **Hard refresh.** Users must hard-refresh after a plugin
  upgrade (`Ctrl+Shift+R`) so the WRM context batch loads new JS.
- **Push troubleshooting.**
  - Chrome/Edge require HTTPS on the Jira page. Set
    `atlassian-data-center-compatible` HTTPS via a proxy if
    needed.
  - The Service Worker MUST be served from the same origin as the
    Jira page — that's why we ship it via a plugin servlet
    (`/plugins/servlet/jim/sw.js`) rather than as a static
    resource.
- **Reply diagnostics.** `window.JimCommentReply.diagnose()` in
  the browser console; `window.JimCommentReply.enableDebug()` for
  verbose logging.
- **AO schema.** `JimAoSchemaDiagnostics` exposes the current
  schema via `GET /rest/jim/1.0/dev/ao-schema` for validation.
- **Logs.** All plugin log lines follow the `event=<name>
  stage=<phase> outcome=<result>` structured key/value convention
  for grep-ability. Example: `event=mention stage=process
  outcome=created targetUserKey=… issueKey=…`.

---

## Appendix A. File tree (backend)

```
src/main/java/com/corbitlogic/jira/internalmessenger/
├── action/           MessengerPageAction
├── ao/               10 AO entity interfaces + JimAoConstants
├── attachment/       JimAttachmentPolicy · JimAttachmentStorageService · JimMultipartParser
├── bootstrap/        JimPluginBootstrap  (event listeners)
├── dto/              REST request/response DTOs
├── event/            AssignmentEventPayload · CommentEventPayload · StatusChangeEventPayload · JimEventExecutor
├── listener/         JimAssignmentChangeDetector · JimStatusChangeDetector
├── mobile/           JimCapturingHttpServletResponse · JimMobileMenuHtmlInjector · JimMobileMenuInjectionFilter
├── model/            JimAttachmentFileKind · JimBodyFormat · JimConversationType · JimEventType · JimSenderType
├── parser/           JimMentionParser
├── rest/             18 REST resources + JimRestJsonMapper · JimRestResponses · exception mappers
├── service/          21 services + impls  (see §5.1)
├── util/             JimConversationKeys · JimIssueUrlBuilder · JimMessageFlags · JimMessageLifecycle · JimNotificationRecipientResolver · JimSanitizer · JimValidation · MentionParser
└── web/              Servlets + JimProjectChatPanelContextProvider
```

## Appendix B. File tree (frontend)

```
src/main/resources/
├── atlassian-plugin.xml
├── css/           jim-messenger.css · jim-comment-reply.css · jim-board-gallery.css · jim-admin.css · jim-nav-badge.css
├── images/        corbit-app-logo.png · (icons)
├── js/            jim-messenger.js · jim-api.js · jim-comment-reply.js · jim-board-gallery.js · jim-admin.js · jim-nav-badge.js · jim-sw.js
├── jim/           i18n bundles
└── templates/     messenger.vm · messenger-app.vm · admin.vm · board-gallery.vm
```

## Appendix C. Complete servlet + REST URL cheatsheet

Servlets (`/plugins/servlet/`):

- `jim/chat`, `jim/project-chat`, `jim/admin`, `jim/boards`, `jim/sw.js`

REST (`/rest/jim/1.0/`):

- **Health:** `/health`, `/ao-health`
- **Conversations:** `/conversations`, `/conversations/unread-count`,
  `/conversations/direct`, `/conversations/{id}/messages`,
  `/conversations/{id}/attachments`, `/conversations/{id}/pinned`,
  `/conversations/{id}/read`,
  `/conversations/{id}/messages/{mid}/receipts`
- **Messages:** `/messages/{id}` (PUT/DELETE), `/messages/{id}/pin`,
  `/messages/{id}/action`, `/messages/{id}/reactions`
- **Groups:** `/groups`, `/groups/{id}`, `/groups/{id}/members`,
  `/groups/{id}/members/{userKey}`
- **Projects:** `/projects/{key}/conversation`
- **Users:** `/users/search`
- **Attachments:** `/attachments/{id}/download`,
  `/attachments/{id}/preview`
- **Push:** `/push/config`, `/push/subscriptions`, `/push/summary`
- **License:** `/license/status`
- **Admin (sysadmin):** `/admin/license`, `/admin/settings`,
  `/admin/policies`, `/admin/policies/{id}`, `/admin/diagnostics`,
  `/admin/audit`, `/admin/test-notification`
- **Dev-only:** `/dev/ao-schema`, `/dev/debug`,
  `/dev/test-*-card`

---

*This document is maintained in-tree; keep it in sync when adding
new features, tables or REST endpoints.*
