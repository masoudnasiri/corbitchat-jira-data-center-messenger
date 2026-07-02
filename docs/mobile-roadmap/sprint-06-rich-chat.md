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
