# Sprint 08 — Group & Project Chat + Jira Assistant

**Phase:** 2 — Jira Actions · **Duration:** 2 weeks · **Goal:** complete
the chat surface — group chats (with membership/Access Policy), the
official Project Chat, and the Jira Assistant activity feed.

> Spec: PDF §4.3 (group/project chat), §2.5-equiv Assistant. Design:
> `docs/modules.md` §2 (Assistant), §3 (Group Chat), §4 (Projects Chat).
> Architecture §2.5 (Assistant), §8.2 (groups/projects). Reference:
> `jim-messenger.js`, `jim-api.js`.

## Objective

Enable Groups tab, Projects/Assistant tabs in the chat list; group
creation + member management under `JimAccessPolicyService`; project chat
reachable from project pages; and the Assistant as a typed activity feed
with quick actions (Open, Mark seen).

## In scope

- Chat list Groups / Projects / Assistant tabs (complete the 5-tab bar).
- Group chat: sender labels on incoming bubbles, system messages, typing
  indicator, per-member read receipts (uses receipts endpoint — gotcha
  #6), group avatars.
- Group creation + member management (add/remove, role badges) enforcing
  Access Policy.
- Project Chat: official conversation per project; entry from Project
  Detail Chat tab (Sprint 04 scaffold) + from issue screens.
- Jira Assistant feed: typed event cards (mention, assignment, status,
  reply, overdue) with icons, day separators, unseen highlight, and
  quick actions Open / Mark seen (`actioned`/`actionedAt` — gotcha #7).

## Backend track (plugin BFF)

Existing group/project/assistant endpoints (`docs/architecture.md` §8.2):

```
POST   /groups                         { name, memberUserKeys }
DELETE /groups/{id}
GET    /groups/{id}/members
POST   /groups/{id}/members            { userKey }
DELETE /groups/{id}/members/{userKey}
GET    /projects/{key}/conversation
GET    /conversations/{id}/messages/{mid}/receipts   (group receipts)
POST   /messages/{id}/action           (Assistant mark actioned)
```
Add a mobile Assistant feed shape: `GET
/rest/corbit-mobile/1.0/assistant/events` returning typed events
`{type,title,issueKey?,supportingText,time,seen,deepLink}` derived from
the SYSTEM conversation / Assistant event model (Architecture §2.5).
Enforce `JimAccessPolicyService` on group create/membership (PDF §4.3).

## Mobile track (Flutter)

1. Complete chat tabs; group conversation rows use stacked
   `.cc-avatar-group`, project rows use `.cc-avatar--project`, Assistant
   uses `.cc-avatar--bot` (`docs/modules.md` §1).
2. Group thread: `.cc-chat-bubble__sender` colored labels, `.cc-chat-system`
   pills, `.cc-typing` indicator (respect reduced-motion), per-member
   receipts sheet (`docs/modules.md` §3).
3. Group create flow + member management list with `.cc-role-badge`
   (`--admin`); actions gated by Access Policy → hide/disable when not
   permitted (`docs/states.md` no-permission).
4. Project Chat screen reachable from Project Detail Chat tab and issue
   view; read-only composer rules identical to desktop.
5. Assistant feed screen (`docs/modules.md` §2): event cards, unseen
   accent border, Open (deep link) + Mark seen (POST action → clears
   badge across devices).

## Design references

- `docs/modules.md` §2 Assistant, §3 Group Chat, §4 Projects (Chat tab).
- `docs/components.md` — Chat Bubble sender, List Item, Bottom Sheet.

## Dependencies

- Sprint 02/06/07 (chat thread, actions, attachments), Sprint 04
  (project detail scaffold), Sprint 05 (Assistant push deep links).

## Acceptance criteria (PDF §4.3, consistency rule)

- [ ] Create a group, add/remove members respecting Access Policy.
- [ ] Group thread shows sender labels, system messages, typing, and
      per-member receipts from the receipts endpoint.
- [ ] Project Chat opens from a project and from an issue.
- [ ] Assistant feed lists typed events; Mark seen clears the badge and
      syncs across devices; Open deep-links to the issue/comment.

## Definition of Done

- Access Policy enforced server-side and reflected in UI (no dead
  controls).
- Assistant `actioned` semantics identical to desktop (gotcha #7).
- RTL verified for group labels, system pills, feed cards.
