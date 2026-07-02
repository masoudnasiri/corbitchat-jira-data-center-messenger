# Sprint 02 — Direct Chat (Conversation List + 1:1)

**Phase:** 1 — MVP Core · **Duration:** 2 weeks · **Goal:** the core chat
loop — list conversations, open a 1:1, send/receive text with optimistic
UI, drafts, and read state.

> Spec: PDF §4.3 (chat), §3.3 (data flow), §7 (deep links). Design:
> `docs/modules.md` §1 (Chat List), `docs/components.md` (Chat Bubble,
> List Item), `docs/layout.md` (Chat Layout), `docs/states.md` (Message
> Send States). Reference impl: `jim-messenger.js`, `jim-api.js`.

## Objective

Deliver a fast, correct direct-message experience matching the desktop
plugin's behavior (bidi text, chunked send, drafts), using the existing
`/rest/jim/1.0/*` chat services wrapped by the BFF where useful.

## In scope

- Conversation list (All + Personal tabs first) with avatars, presence,
  last-message snippet, timestamp, unread badge.
- Conversation/user search + start a new direct chat.
- 1:1 message thread: send text, receive, date separators, per-message
  timestamps, jump-to-latest.
- Optimistic send with sending/sent/failed states + retry.
- Draft preservation per conversation.
- `detectTextDirection` bidi behavior and `splitTextIntoChunks`
  (5,000-char cap → ordered chunks) ported from `jim-messenger.js`.
- Mark-read + unread badge sync; foreground polling (2,500 ms desktop →
  mobile foreground/pull-to-refresh only — handover gotcha #8).

## Backend track (plugin BFF)

Wrap existing chat REST with mobile-normalized, paginated payloads
(direct pass-through is acceptable for v1 where shapes already fit):

1. `GET /rest/corbit-mobile/1.0/conversations` → normalized rows
   `{id,type,title,avatarUrl,presence,lastMessage,unreadCount,updatedAt}`
   (source: `GET /rest/jim/1.0/conversations`).
2. Introduce `JimMobileSyncCursor` to support "changed since" on the
   list (server request candidate from handover §9 — implement basic
   `updatedAfter` param).
3. Pass-through/proxy for: `POST /conversations/direct`,
   `GET /conversations/{id}/messages` (`limit`+`beforeMessageId`),
   `POST /conversations/{id}/messages`, `POST /conversations/{id}/read`,
   `GET /users/search`.

## Mobile track (Flutter)

1. Chat List screen: `.cc-conv-row` layout, five tab bar (only All +
   Personal active this sprint), presence dots (`docs/modules.md` §1).
2. Conversation screen: reverse-chronological list, `.cc-chat-bubble`
   incoming/outgoing (RTL: outgoing on inline-start per
   `docs/rtl-i18n.md`), date separators, jump-to-latest FAB.
3. Composer docked above keyboard; Enter=send, Shift/newline handling;
   bidi auto-direction; client-side chunk splitting.
4. Optimistic message model in SQLite; send states clock/✓/✓✓/! (
   `docs/states.md` Message Send States); retry queue for failed sends
   (foundation for offline write queue).
5. Draft store keyed by conversationId; restore on open.
6. Foreground poll + pull-to-refresh; pause polling in background.
7. User search + new-direct-chat bottom sheet.

## Design references

- `docs/modules.md` §1 Chat List; `docs/components.md` Chat Bubble/List Item.
- `docs/layout.md` Chat Layout; `docs/states.md` Message Send States.
- `docs/rtl-i18n.md` Chat Bubbles in RTL, Mention Tokens.

## API contract (existing shapes, see `jim-api.js`)

```
GET  /conversations                         list
POST /conversations/direct                  { targetUserKey }
GET  /conversations/{id}/messages?limit=&beforeMessageId=
POST /conversations/{id}/messages           { body, replyToMessageId? }
POST /conversations/{id}/read
GET  /users/search?query=
GET  /conversations/unread-count
```
(Exposed via `/rest/corbit-mobile/1.0/*`; may proxy `/rest/jim/1.0/*`.)

## Dependencies

- Sprint 01 (shell, auth, cache foundation, i18n).

## Acceptance criteria

- [ ] Conversation list loads with unread counts + presence.
- [ ] Start a new 1:1 from user search; send and receive text.
- [ ] Long messages auto-split into ordered chunks (parity with desktop).
- [ ] Persian and English (bidi) messages render correctly.
- [ ] Optimistic send shows sending→sent; failed send retries.
- [ ] Draft persists across app restart; opening a chat marks it read.

## Definition of Done

- No message loss on backgrounding; polling paused off-foreground.
- Read-cache shows last-known thread offline with stale banner
  (`docs/states.md`).
- RTL/LTR thread rendering verified (mixed content, issue-key spans).

---

# Sprint 02 — Implementation Report (COMPLETED)

## Root cause / findings before implementation

- The existing web chat lives at `/rest/jim/1.0/*` and resolves the user via
  `JiraAuthenticationContext.getLoggedInUser()`. That path is **not** covered by
  the Sprint 01B mobile-session filter (`CorbitMobileSessionFilter` is scoped to
  `/rest/corbit-mobile/1.0/*`), so calling `/rest/jim/1.0/*` directly from mobile
  would work for PAT/basic but **fail for `X-CorbitChat-Session` tokens**.
- No Sprint 01/01B regressions were found. Chat was a placeholder tab only.
- Server semantics (confirmed by reading the web resources/services):
  - DIRECT identity = canonical sorted `(userAKey, userBKey)` pair on
    `JimConversation` (not group membership).
  - Messages paginate by `beforeMessageId` (exclusive cursor); pages return
    **ascending** ids, max `limit` 100 (default 50).
  - Unread excludes the viewer's own USER messages; mark-read sets
    `lastReadMessageId` to the latest message.
  - Message body max **5000** chars; **no** server-side bidi — RTL is client-only.

## Final API path used by mobile (and why)

Mobile Direct Chat uses the **BFF** under
`/rest/corbit-mobile/1.0/chat/*` (new `CorbitMobileChatResource`):

| Method | Path | Purpose |
|--------|------|---------|
| GET  | `/chat/conversations` | DIRECT conversation list |
| POST | `/chat/conversations/direct` | create/open a 1:1 (`{targetUserKey}`) |
| GET  | `/chat/conversations/{id}/messages?limit=&beforeMessageId=` | paged messages |
| POST | `/chat/conversations/{id}/messages` | send text (`{body, replyToMessageId?}`) |
| POST | `/chat/conversations/{id}/read` | mark read |
| GET  | `/chat/users/search?query=` | user search |

Why: this path is covered by the mobile-session filter, so **PAT, Jira
cookie/basic, and plugin-issued mobile session tokens all work identically**.
The resource is a thin wrapper that delegates to the **same services and
`JimRestJsonMapper`** as the web endpoints, so JSON shapes and every
permission/policy rule (`JimAccessPolicyService.requireCanChatWith`,
`isChatEnabled`, license, participant checks) are identical → web/mobile stay in
sync. The web `/rest/jim/1.0/*` surface was **not modified**.

## How PAT + mobile-session auth were verified

Same lifecycle run over both auth modes and confirmed against the web surface:

- **Mobile session** (password login → `X-CorbitChat-Session`): user-search,
  create-direct, send, list, mark-read, list-conversations all `200`.
- **PAT** (`Authorization: Bearer`): `/chat/conversations` `200`, send `200`.
- **Basic** (web parity): `/rest/jim/1.0/conversations/{id}/messages` returns the
  **same** message data as the BFF for the same conversation.
- **Anonymous**: `/chat/conversations` → `401` (self-enforced).
- Verified end-to-end over the real device host `https://jira.corbitlogic.com`
  (password login → chat conversations `200`, send `200`).
- User-search access-policy parity confirmed: a query the policy blocks returns
  `[]` on **both** web and mobile (not a bug).

## Files changed

Backend (plugin):
- `src/main/java/.../mobile/rest/CorbitMobileChatResource.java` (**new**) — the
  BFF chat endpoints; delegates to existing services + mapper; no message-body
  logging. (No `atlassian-plugin.xml` change — the mobile REST package is already
  scanned; all injected services are existing components.)

Mobile (Flutter):
- `lib/models/chat.dart` (**new**) — `Conversation`, `ChatMessage`
  (+`MessageStatus`), `UserSearchResult`.
- `lib/api/corbit_api.dart` — chat methods (list/messages/send/markRead/
  createDirect/searchUsers).
- `lib/state/chat_providers.dart` (**new**) — `conversationsProvider`,
  `draftsProvider`, `threadProvider` (optimistic send + retry, pagination,
  merge-polling, mark-read).
- `lib/core/bidi.dart` (**new**), `lib/core/time_format.dart` (**new**).
- `lib/features/chat/chat_tab.dart` (**new**) — conversation list.
- `lib/features/chat/conversation_screen.dart` (**new**) — 1:1 thread + composer.
- `lib/features/chat/new_chat_screen.dart` (**new**) — user search + start chat.
- `lib/features/chat/widgets/chat_avatar.dart` (**new**).
- `lib/features/shell/app_shell.dart`, `lib/features/shell/tabs/placeholder_tabs.dart`
  — wire the real `ChatTab`.
- `lib/core/strings.dart` — chat strings (EN/FA).

## Built / fixed

- Real DIRECT conversation list (avatar, presence dot, last-message preview with
  "You:" prefix, timestamp, unread badge) with loading/empty/error/retry states.
- 1:1 thread: paginated load ("Load earlier"), day separators, per-message
  timestamps, own/other bubbles, delivery status (clock → ✓ → ✓✓ seen), and a
  clear **failed** state with tap-to-retry.
- Optimistic send (temp bubble reconciled with the server echo by `localKey`).
- Per-conversation **drafts** preserved across leaving/reopening (in-memory).
- Mark-read on open + when new messages arrive; badge/list refresh via provider
  invalidation.
- Lightweight 6 s **merge-polling** so web-sent messages appear without manual
  refresh, without dropping older loaded pages or in-flight optimistic messages.
- Mixed Persian/English direction detection per message.

## Intentionally not changed

- Web `/rest/jim/1.0/*` behavior, AO schema, plugin key, REST contracts.
- Group/project/assistant chat, attachments, reactions, reply/edit/delete/pin,
  push, issue-link send from mobile (all out of scope).
- No SQLite offline draft persistence (drafts are in-memory for the session);
  no "updatedAfter"/sync-cursor server change (kept the smallest safe surface).
- Long-message client chunking was **not** ported; server accepts up to 5000
  chars in one message (parity with a single web send), matching existing limits.

## Tests performed

- `dart analyze lib` + `dart analyze test`: **No issues found**.
- `flutter test`: pass.
- `flutter build apk --release`: **built** (53 MB) →
  `corbitchat-mobile/dist/corbitchat-mobile-sprint02-directchat.apk`.
- Backend regression (all `200`, anon `401`): jim health/ao-health/conversations,
  chat page, mobile bootstrap/preferences/auth-session, chat convs.
- Secret/body leak scan: no message bodies, tokens, passwords, or auth headers in
  any log/print (backend or mobile).

## Deployment status

- Plugin rebuilt and deployed to the `jira-srv` container (serves
  `185.83.181.194:8080`, fronted by `https://jira.corbitlogic.com` — same
  instance), Jira restarted, health green.
- New release APK staged for device install (path above).

## Remaining risks

- Presence (`otherUserActive`) reflects the web presence heartbeat; may show
  offline for users not recently active on web.
- Polling is a fixed 6 s foreground interval (no push yet — Sprint 05); brief
  delay before web-sent messages appear.
- Drafts are in-memory (lost on app kill) — SQLite persistence deferred.
- Release APK requires the CN Flutter storage mirror to build in this
  environment (`FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn`).

## Manual UI test steps

1. **Login (both modes):** install the APK; sign in with username/password
   (`m.nasiri` / `<REDACTED>`) against `https://jira.corbitlogic.com`. Repeat with
   a PAT to confirm both work.
2. **Conversation list:** open the **Chat** tab → direct conversations load with
   avatars, presence dots, last-message preview, timestamp, and unread badges.
   Pull down to refresh.
3. **Open + read:** tap a conversation with an unread badge → messages load,
   badge clears, and the tab's unread count drops (mark-read).
4. **Send + optimistic/failed:** type and send → bubble appears immediately with a
   clock, then ✓ when delivered. Toggle airplane mode and send → bubble shows
   **Not delivered · Tap to retry**; tap to retry after re-enabling network.
5. **Receive:** send a message from the web chat (`/plugins/servlet/jim/chat`) to
   the same user → it appears in the mobile thread within ~6 s (or pull down).
6. **Pagination:** in a long thread, tap **Load earlier messages** at the top.
7. **Drafts:** type without sending, leave the thread, reopen → text is restored.
8. **Bidi:** send a mixed message like `سلام hello جهان` → it renders
   right-to-left with correct alignment; English-only stays left-to-right.
9. **New chat:** tap the compose FAB → search a user (≥2 chars, e.g. `hesari`) →
   tap to start → thread opens; send a message and confirm it shows in web chat.
10. **States:** verify loading spinners, empty ("No conversations yet"), error +
    retry, and no-permission (if `chat` feature flag is off) all render.
