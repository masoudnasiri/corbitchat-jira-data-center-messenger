# Sprint 09 — Issue Comments & Replies

**Phase:** 2 — Jira Actions · **Duration:** 2 weeks · **Goal:** create
native Jira comments and desktop-compatible replies with automatic
parent-author mention, plus reply attachments — from the issue view.

> Spec: PDF §4.6 (comments section), §4.7 (comments & replies). Design:
> `docs/task-view.md`, `docs/components.md` (Input/comment composer).
> Reference impl: `jim-comment-reply.js` (mirror exactly). Architecture
> §2.7, §9.3.

## Objective

Users comment and reply on issues so that Jira's own notifications/audit
fire and the reply is **fully compatible** with the desktop web UI:
`{quote}` wiki markup contract, auto-mention of the parent author,
`expand=renderedBody` rendering, and reply attachments with wiki tokens.

## In scope

- Add native Jira comment (create as real Jira comment so Jira
  notifications/audit stay active — PDF §4.7).
- Reply-to-comment: preserve parent context, auto-mention parent author,
  store the same recognizable quote/preview contract as desktop
  (`jim-comment-reply.js`).
- Render comments/replies via `renderedBody` (gotcha #2), reply badges.
- Reply attachments (upload + wiki tokens) mirroring
  `jim-comment-reply.js`.
- After reply, Jira Assistant generates the event + push for the
  mentioned user (PDF §4.7; ties to Sprint 05/08).

## Backend track (plugin BFF)

1. `POST /rest/corbit-mobile/1.0/issues/{key}/comments` — create native
   Jira comment (server-side via Jira REST/Java API), return
   `renderedBody`.
2. `POST /rest/corbit-mobile/1.0/issues/{key}/comments/{id}/replies` —
   build the **same** body contract the desktop uses: `{quote}` of the
   parent + `[~parentAuthor]` auto-mention + reply text; attach files
   with the same wiki-token approach as `jim-comment-reply.js`. Verify
   against `jim-comment-reply.js` and Architecture §9.3 so web+mobile
   round-trip identically.
3. Reply attachment upload: reuse Jira native
   `POST /rest/api/2/issue/{key}/attachments` server-side and embed wiki
   tokens (Architecture §9.3).
4. Ensure the reply triggers the existing Assistant event + push path.

## Mobile track (Flutter)

1. Comment composer docked above keyboard (`docs/layout.md` Issue Detail
   Layout; `docs/components.md` Input). Mention typeahead + `[~username]`.
2. Reply action on a comment → composer pre-fills quoted preview + parent
   mention; show the quote block with accent border.
3. Render comments with `renderedBody` HTML (links/images/mentions);
   reply badges + threaded display.
4. Reply attachment picker (image/file) with upload progress; insert
   wiki tokens as the server contract dictates.
5. Optimistic comment/reply with sending/failed/retry; preserve draft on
   failure (PDF §1.3 draft preservation).
6. Permission-aware: hide composer when user lacks COMMENT permission
   (`docs/states.md` no-permission).

## Design references

- `docs/task-view.md` — Comments section, People (mention targets).
- `docs/components.md` — Input / comment composer, Notification item.
- `docs/rtl-i18n.md` — mentions + bidi in comment threads.

## API contract

```
POST /rest/corbit-mobile/1.0/issues/{key}/comments                    { body }
POST /rest/corbit-mobile/1.0/issues/{key}/comments/{id}/replies       { body, attachments? }
GET  /rest/corbit-mobile/1.0/issues/{key}   (comments expand=renderedBody, from Sprint 04)
```

## Dependencies

- Sprint 04 (issue detail + comments read), Sprint 07 (attachment upload
  patterns), Sprint 05/08 (Assistant event + push).

## Acceptance criteria (PDF §9 Phase 2, §9.1, consistency rule)

- [ ] Comment created appears as a **native Jira comment** in the web UI.
- [ ] Reply auto-mentions the parent author and shows the quote contract
      identically on web and mobile.
- [ ] Reply with attachment renders correctly on both clients.
- [ ] Reply generates a Jira Assistant event + push to the mentioned user.
- [ ] `renderedBody` used everywhere; no raw wiki shown.
- [ ] Composer hidden when user can't comment.

## Definition of Done

- Web↔mobile reply round-trip verified against `jim-comment-reply.js`.
- Draft preserved on send failure; optimistic + retry.
- Do not manipulate Jira web DOM (mobile uses REST/services only — PDF §2).
