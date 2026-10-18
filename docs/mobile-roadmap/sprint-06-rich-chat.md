# Sprint 06 — Rich Chat (Reactions, Replies, Edit/Delete, Forward, Pins, Receipts)

**Phase:** 2 — Jira Actions · **Duration:** 2 weeks · **Goal:** bring the
1:1/thread experience to full parity with the desktop plugin's message
features.

> Spec: PDF §4.3. Design: `docs/components.md` (Chat Bubble), `docs/modules.md`.
> Reference impl: `jim-messenger.js`, `jim-api.js`. Architecture §2.2.

## Objective

Add reactions, reply-to-message, in-composer edit (30-min window),
delete, forward, pinned message, and read receipts — matching the exact
server semantics already used by the web client so both clients behave
identically (consistency rule).

## In scope

- Emoji reactions (toggle) + reaction summary on bubbles.
- Reply-to-message with quoted preview (`docs/components.md` Chat Bubble
  reply preview).
- Edit within the **server-enforced 30-minute window** — show a "can no
  longer edit" state, not a dead button (gotcha #4).
- Delete (soft) with tombstone rendering.
- Forward message to another conversation.
- Pin/unpin; pinned-message banner in thread header.
- Read receipts: direct = seen flag; group = per-member receipts endpoint
  (never inferred client-side — gotcha #6; group UI lands Sprint 08).
- Mention typeahead + `[~username]` tokens in composer.

## Backend track (plugin BFF)

Proxy/normalize existing message endpoints (already implemented in
`/rest/jim/1.0/*` — `docs/architecture.md` §8.2):

```
POST   /messages/{id}/reactions      toggle emoji
PUT    /messages/{id}                 edit (30-min enforced server-side)
DELETE /messages/{id}                 soft delete
POST   /messages/{id}/pin  / DELETE .../pin
GET    /conversations/{id}/pinned
GET    /conversations/{id}/messages/{mid}/receipts
```
Forward = `POST /conversations/{id}/messages` with copied body/issueLink.
Expose these under `/rest/corbit-mobile/1.0/*`; keep response fields
(editedAt, deletedAt, reactions[], replyTo, pinned) intact.

## Mobile track (Flutter)

1. Message action sheet (long-press): react, reply, edit, delete,
   forward, pin, copy (`docs/components.md` Modal/Bottom Sheet).
2. Reaction picker + inline reaction chips; optimistic toggle.
3. Reply composer state with quoted preview; tapping quote scrolls to
   source message.
4. Edit-in-composer; client computes remaining edit window from
   `createdAt` and hides edit past 30 min (server remains source of
   truth — handle 403 gracefully).
5. Delete → tombstone; forward → conversation picker sheet.
6. Pinned banner in thread header (`GET /pinned`), tap to jump.
7. Direct-chat read receipt (✓✓ primary when seen); mention typeahead
   using `GET /users/search` with avatars.

## Design references

- `docs/components.md` — Chat Bubble (reply preview, mentions), Modal/
  Bottom Sheet, Toast/Snackbar.
- `docs/rtl-i18n.md` — Mention Tokens, bidi.
- `docs/states.md` — Message Send States (failed/retry for actions).

## Dependencies

- Sprint 02 (direct chat thread + composer).

## Acceptance criteria (PDF §4.3, consistency rule)

- [ ] React/reply/edit/delete/forward/pin all work and match desktop.
- [ ] Edit disabled after 30 min with a clear state (not a dead button).
- [ ] Reactions and edits made on web appear on mobile and vice-versa.
- [ ] Direct read receipt reflects seen state.
- [ ] Mention typeahead inserts `[~username]` tokens; renders as chips.

## Definition of Done

- All actions optimistic with retry on failure (`docs/states.md`).
- No client-side inference of receipts; server is source of truth.
- RTL rendering of reply previews, reactions, tombstones verified.

---

## Implementation addendum (delivered 2026-07-03)

Plugin `1.0.0-mobile-s06` · App `1.0.0+3 (s06)` — APK
`releases/corbithub-mobile-1.0.0-s06.apk`.

### Backend (mobile BFF `/rest/corbit-mobile/1.0/chat/*`)

The mobile session token (`X-CorbitChat-Session`) only authenticates
`/rest/corbit-mobile/1.0/*`, so the rich actions were added as thin
BFF wrappers over the **same** services the web surface uses (windows,
soft-delete, one-pin-per-conversation and the reaction allowlist are all
enforced server-side). New routes on `CorbitMobileChatResource`:

| Method | Path | Delegates to |
|---|---|---|
| `PUT` | `/chat/messages/{id}` | `editUserMessage` (30-min window) |
| `DELETE` | `/chat/messages/{id}` | `deleteUserMessage` (10-min soft delete; `delete_window_expired`) |
| `POST` / `DELETE` | `/chat/messages/{id}/pin` | `setPinned` |
| `GET` | `/chat/conversations/{id}/pinned` | `getPinnedMessage` |
| `POST` | `/chat/messages/{id}/reactions` | `JimReactionService.toggleReaction` |

Message JSON was already rich (shared `JimRestJsonMapper`): `replyTo`,
`reactions`, `edited`/`editedAt`, `pinned`, `canEdit`, `canDelete`,
`deleted`. No AO/schema changes; plugin key unchanged.

**Forward:** there is no native server forward, so mobile forward
re-sends the message body into the chosen conversation via the existing
`sendMessage` (text only) — a real action, not a stub.

### Mobile (Flutter)

- `ChatMessage` extended with `replyTo`, `reactions`, `edited`/`editedAt`,
  `pinned`, `canEdit`, `canDelete`; `CorbitApi` gained
  `editMessage/deleteMessage/pinMessage/unpinMessage/getPinnedMessage/
  toggleReaction/forwardText` and a `deleteJson` client helper.
- `ThreadController`: reply/edit composer modes, optimistic reply send,
  edit/delete/pin/react with server reconcile; derived pinned message.
- Long-press action sheet (react · reply · copy · forward · pin/unpin ·
  edit · delete) with permission-aware visibility; reply preview inside
  bubbles; inline reaction chips (toggle); pinned banner; edited marker;
  tombstone rendering; delete confirm; 403 → clear window-expired copy.
- Bilingual (en/fa) strings + RTL for all new UI.

### Android share sheet (inbound)

- Manifest `ACTION_SEND` (`text/plain`) intent-filter on `MainActivity`.
- Native bridge `MainActivity.kt` ↔ Flutter over MethodChannel
  `corbitchat/share` (cold-start `getInitialSharedText` + running-app
  `onSharedText`), no third-party package (offline-friendly).
- `ShareIntentService` parks the text in `pendingShareProvider`; the root
  widget routes to `ShareTargetScreen` once a session exists — a share on
  the login screen resumes automatically after sign-in.
- `ShareTargetScreen` reuses the `DestinationPickerScreen` (recent direct
  chats + user search), editable caption, then sends.

### Scope decisions (honouring "don't fake / don't pull S07 forward")

- **Image/file share + attachment rendering → deferred to Sprint 07.**
  The mobile thread has no attachment display and the BFF has no
  attachment endpoints; upload-only would ship shared images that never
  render in-app.
- **Group/project targets → deferred.** The BFF lists DIRECT only; the
  forward/share picker is limited to direct conversations + user search.
- **Mention typeahead** (`[~username]`) not included in this cut.

### Verification

- Dev (`185.83.181.194:8080`, plugin `s06`): full action lifecycle via
  BFF — send → edit (`edited:true`) → react (👍) → pin → get-pinned →
  unpin → soft-delete (`deleted:true`); reply preview + rich fields
  present; regression smoke (health/ao-health/bootstrap/conversations/
  chat page) all `200`.
- Prod (`193.162.129.56`, `https://jira.7gtech.net`): DB + jar backup
  taken; `s06` installed as the only plugin jar; plugin enabled, no
  errors; new routes registered (401/500, not 404; bogus paths 404).
  Endpoints are auth-gated on prod, so functional correctness was
  validated on dev with real credentials.
- `flutter analyze`: **No issues found**; release APK built (56.2 MB).

### Deferred / open

- On-device confirmation on the physical production device (share sheet
  entry, action sheet, reactions/pin round-trip).
- Sprint 07 will add attachment upload/download BFF + in-thread
  attachment rendering, unlocking image/file share.

---

## Fix-1 addendum (Message Info, delivery/seen, forward attribution, Dashboard unread)

Plugin `1.0.0-mobile-s06-fix1`; mobile `1.0.0+4` (`buildLabel s06-fix1`).

### Root causes

- **Missing sent/seen timing & Message Info.** The data already existed —
  the shared web mapper emits `createdAt` and, for direct chats, `seenAt`
  (the other participant's `lastReadAt`) plus `seenByOther` — but the mobile
  `ChatMessage` model never parsed `seenAt` and there was no detail surface.
  No separate *delivered* timestamp is stored server-side (delivery ≈ stored
  = `createdAt`), so we never fabricate one.
- **No forward attribution.** Sprint 06 forwarded by re-sending the body
  through `sendMessage`, so the receiver saw a normal message from the
  forwarder. There was no forward model in the backend.
- **Wrong Dashboard unread count.** `bootstrap`/`dashboard` `unread.total`
  sums **all** conversation types (DIRECT + SYSTEM + GROUP), but the mobile
  Chat tab lists **DIRECT only**. Example (dev): `total 14` = DIRECT 4 +
  SYSTEM 2 + GROUP 8, while the chat list showed 4. The card also had no tap
  action and rendered ASCII digits in Persian UI.

### Backend (real forward metadata — the "preferred" path)

- `JimMessage` AO: four nullable, auto-migrated columns —
  `FORWARDED_FROM_MESSAGE_ID`, `FORWARDED_FROM_USER_KEY`,
  `FORWARDED_FROM_NAME`, `FORWARDED_AT`. Legacy/non-forward rows stay null.
- `JimMessageService.forwardUserMessage(targetConvId, forwarderKey, srcMsgId)`:
  permission-safe (forwarder must participate in the target **and** be able
  to read the source via `getMessageForParticipant`); creates a new message
  carrying the **ultimate original** author (re-forwards chain to the true
  author, not the last relayer); refuses deleted/system sources.
- Mapper emits `forwardedFrom {messageId,userKey,displayName,at}` (or null).
- REST: web `POST /rest/jim/1.0/messages/{id}/forward` and mobile BFF
  `POST /rest/corbit-mobile/1.0/chat/messages/{id}/forward` (both take
  `{targetConversationId}`; the BFF enforces the direct-chat policy + license).
- No schema change was needed for seen time — `seenAt` was already exposed.

### Mobile

- `ChatMessage` now parses `seenAt`, `readByCurrentUser`, `deletedAt`, and
  `forwardedFrom` (new `ForwardInfo`).
- **Message Info**: new action in the long-press sheet → `MessageInfoSheet`
  showing sender, conversation, sent time, delivery/seen state (truthful:
  `Seen · <time>`, `Seen · time not available`, or `Delivered · not seen
  yet`), edited/deleted/pinned, reply-to, forwarded-from, and the technical
  message id (id left in ASCII; all times via the central `DateDisplay` so
  Persian UI gets Jalali + Persian digits).
- **Forwarded marker**: bubble shows `Forwarded from <name>` (or `Forwarded`
  when self-forwarding), driven by backend metadata; forward action now calls
  `forwardMessage(sourceId, targetId)` instead of re-sending the body.
- **Dashboard**: "New messages" now uses the DIRECT bucket
  (`unread.byType['DIRECT']`), matching the Chat list + bottom-nav badge
  exactly; tapping it switches to the Chat tab (new `shellTabRequestProvider`)
  and refreshes conversations; unread conversations sort first in the list.
  Counters are localized via `DateDisplay.number` / `.badge` (Persian digits
  in fa UI; issue/project keys, usernames, versions and the message id are
  never converted).

### Verification (authenticated, real credentials)

- **Dev** (`185.83.181.194:8080`, m.nasiri): all 7 endpoints `200`; version
  `s06-fix1`. Forward of another user's message attributes the original
  author; re-forward preserves the true author; reply/react/pin/edit/unpin
  all green; `seenAt` present with real timestamps; unread `byType.DIRECT`
  (4) equals the conversation-list sum (4).
- **Prod** (`193.162.129.56` / `https://jira.7gtech.net`, m.nasiri): DB dump
  (41 MB) + rollback jar backed up; installed as the only plugin jar; all 7
  endpoints `200`; version `s06-fix1`; forward attributes the original author
  (`Admin1`), unauthorized forward returns `404`; unread agreement holds.
  Test messages created during verification were deleted afterwards.
- `flutter analyze`: **No issues found**. Release APK:
  `/root/jira-dev/releases/corbitchat-mobile-1.0.0+4-s06-fix1.apk`.

---

## Fix-2 addendum (swipe-to-reply + self chat / Saved Messages)

Plugin `1.0.0-mobile-s06-fix2`; app `1.0.0+5` (`s06-fix2`).

### Root causes

- **Swipe-to-reply missing**: purely a mobile gap — reply existed only via the
  long-press action sheet. **No backend change was needed** for the gesture.
- **Self chat missing**: two backend guards blocked it —
  `JimConversationServiceImpl.validateDirectConversationRequest` called
  `requireDistinctUsers` (rejecting `current == target`), and in `RESTRICTED`
  chat mode `JimAccessPolicyServiceImpl.canChatWith` evaluated the self→self
  pair against the policy graph (which governs chatting with *others*).

### Backend changes

- `validateDirectConversationRequest`: dropped the distinct-user requirement.
  The canonical direct pair already collapses to one row when
  `USER_A_KEY == USER_B_KEY`, so a user has exactly one self conversation.
- `canChatWith`: self short-circuit — a user may always chat with themselves
  whenever chat is not fully `DISABLED`.
- New BFF endpoint `POST /rest/corbit-mobile/1.0/chat/conversations/self`
  (get-or-create the caller's self conversation; no client needs its own key).
- `toConversationMap`: added a `self` boolean (true when a DIRECT conversation's
  both sides are the viewer) so the client can label it "Saved Messages".
- **No AO/schema change.** Existing behavior is reused for the rest:
  self messages never count as unread (`getUnreadCount` already excludes the
  viewer's own messages) and never push (`notifyDirectRecipientPush` already
  returns early when recipient == sender).

### Behavior decisions

- **Unread for self messages**: never unread. The sender authored them, and the
  unread query already excludes own messages — Dashboard/badge stay correct.
- **Push for self messages**: never sent. Avoids noisy self-push on the same
  device; the existing recipient==sender guard covers all channels.
- **Naming**: "Saved Messages" / "پیام‌های ذخیره‌شده", bookmark avatar, no
  online/offline line, appears exactly once (pinned entry in new-chat + picker;
  `self` conversations are filtered out of the picker's recent list).

### Mobile changes

- `_SwipeToReply` wrapper (horizontal-drag only, threshold 52 px, animated
  snap-back, haptic on cross, reply icon reveal). Direction-agnostic so it works
  in LTR and RTL; disabled for system/deleted/not-yet-sent messages; leaves
  scroll, long-press, taps, links, reactions and selection untouched. Reuses the
  existing `startReply` composer flow.
- `SavedMessagesTile` shared widget in new-chat and destination picker;
  `openSelfConversation()` API; `Conversation.isSelf`; self relabel + bookmark
  avatar in the chat list, thread header and pickers.

### Verification (authenticated, real credentials)

- **Dev** (`185.83.181.194:8080`, m.nasiri): version `s06-fix2`; self create is
  idempotent (same id 21); send-to-self works with unread `0`; list shows
  `self:true`; forward of another user's message (Zana Zarhoon) into self
  preserves `forwardedFrom`; normal direct chat still returns `self:false`; core
  endpoints `200`.
- **Prod** (`193.162.129.56`, m.nasiri): DB dump
  (`jira-db-20260703-100325.sql`) + rollback jar
  (`installed-plugins-20260703-100325`, s06-fix1) backed up; installed as the
  only plugin jar; version `s06-fix2`; self create/idempotent/send verified,
  unread `0`; 16 existing directs `self:false`, 1 `self:true`; normal thread
  loads `200`.
- `flutter analyze`: **No issues found**. Release APK:
  `/root/jira-dev/releases/corbitchat-mobile-1.0.0+5-s06-fix2.apk`.

---

## Fix-3 addendum (multi-recipient forward)

App `1.0.0+6` (`s06-fix3`). **Mobile-only — no backend/plugin change**, so the
production plugin stays at `1.0.0-mobile-s06-fix2` (no redeploy).

### Root cause

Single-recipient forward was a client limitation: `DestinationPickerScreen`
popped one `Conversation` and `_forward` called `forwardMessage(id, targetId)`
once. The backend forward endpoint is already per-destination, so multi-forward
is just "call it once per selected destination".

### Approach (mobile-only)

- `DestinationPickerScreen` gained a `multiSelect` flag. In multi mode, tapping a
  row (Saved Messages, existing chat, or search result) toggles selection;
  selections show as chips with an inline remove; a bottom "Send to N" bar
  confirms and pops a deduped `List<ForwardDestination>`. Empty selection can't
  be sent. Single mode (Android share) is unchanged.
- `_forward` resolves each `ForwardDestination` to a canonical conversation id
  (`openSelfConversation` / existing id / `createDirect` — all idempotent
  get-or-create), **dedups by that id**, then forwards once per unique id via the
  existing per-destination endpoint (attribution preserved server-side).

### Duplicate prevention

Two layers: (1) picker selection is keyed by a stable token
(`c:<id>` / `self` / `u:<userKey>`) so the same row can't be added twice;
(2) at send time selections are resolved to conversation ids and deduped again,
so the same person chosen via both an existing chat and a search result receives
the message exactly once.

### Partial failures

Per-destination try/catch tallies `ok`/`fail`. Result snackbar is truthful:
all-success → "Forwarded"/"Forwarded to N chats"; some fail →
"Forwarded to ok of total chats"; all fail → "Forward failed". No false success.

### Android share

Intentionally left single-destination. Share uses a different path (composed
text + optional caption, not a message forward); multi-target there is a larger,
separate change and out of this fix's low-risk scope.

### Saved Messages in multi-forward

Selectable as one destination among others (pinned "Saved Messages" row, bookmark
chip). Forwarding to self preserves `forwardedFrom` exactly like any other target.

### Verification

- **Dev** (`185.83.181.194`, m.nasiri): simulated the client loop — forwarded one
  message (authored by Zana Zarhoon) to conv 11 **and** self (21); both carried
  `forwardedFrom` = Zana Zarhoon. Invalid destination returns "Conversation not
  found" (drives the partial-failure path). No plugin change; version still
  `s06-fix2`.
- `flutter analyze`: **No issues found**. Release APK:
  `/root/jira-dev/releases/corbitchat-mobile-1.0.0+6-s06-fix3.apk`.
