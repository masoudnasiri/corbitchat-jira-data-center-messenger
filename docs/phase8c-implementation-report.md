# Phase 8C — Group Send Policy — Implementation Report

**Date (UTC):** 2026-09-11 · **Scope:** DEV-first (plugin + mobile). Production NOT deployed. No GitHub push.
**Plugin version:** `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2-8a-dynlevels1-8b-dayunit1-8c-groupsend1`
**Mobile:** `1.0.0+114`, DEV label `figma-phase8c-groupsend1`, 7G label `figma-phase8c-groupsend1-prod`.

## Feature
A group admin (OWNER/ADMIN) can restrict who may send in a group chat. Default is unchanged (all members send). When restricted (`ADMINS_AND_ALLOWED`), only admins/owner and explicitly-allowed non-admin members can send; everyone else has a read-only composer. Read/search/shared-files/reactions are never blocked. Backend-enforced (not client-only).

## Data model (PART 3 — smallest safe, cluster-safe, no new table)
- `JimConversation.sendPolicy` (String, nullable) — `null`/`ALL_MEMBERS` (default/legacy) | `ADMINS_AND_ALLOWED`.
- `JimGroupMember.allowedSender` (Boolean, nullable) — per-member allow flag. Living on the member row means **removing a member auto-clears the allowance**. Both are additive nullable columns → AO auto-migration; **no existing group becomes restricted after upgrade**.

## Permission model (PART 2)
`JimGroupService.canSend(conversation, userKey)` / `requireCanSend(...)`:
- Non-GROUP/PROJECT (DIRECT/SYSTEM) → allowed (existing rules apply).
- `ALL_MEMBERS`/null → allowed.
- `ADMINS_AND_ALLOWED` → allowed iff OWNER/ADMIN (`canManageMembers`) or member `allowedSender==true`; else 403 with Persian message `ارسال پیام در این گروه توسط مدیران محدود شده است`.
- System/assistant/group-event inserts use separate methods (`createSystemMessage*`, `addGroupEventMessage`) and are **not** gated. Reactions are **not** gated (documented).

## Backend/API (PART 4)
- Enforcement wired into every user-content send path (403 before any row/upload): web `JimConversationResource` (messages, attachments), `JimMessageResource` (forward → target group), mobile `CorbitMobileChatResource` (messages, attachments, forward).
- Manage endpoints (OWNER/ADMIN, GROUP/PROJECT only):
  - `POST /rest/jim/1.0/groups/{id}/send-policy` `{sendPolicy}` (+ mobile mirror `/chat/conversations/{id}/send-policy`).
  - `POST /rest/jim/1.0/groups/{id}/allowed-senders` `{userKey,allowed}` (+ mobile mirror). Target must be a participant; non-members rejected.
- DTO (`JimRestJsonMapper`): conversation now exposes `sendPolicy`, `canSendMessage`, `canManageSendPolicy`, `memberSendRestricted`; group member exposes `allowedSender`, `canSend`.
- Audit via `JimChatAuditService`: `group.sendPolicy.set`, `group.allowedSender.set` (+ projectChat.* variants) with actor/group/target/details.

## Web UI (PART 5)
`jim-messenger.js`: composer becomes read-only with `ارسال پیام در این گروه توسط مدیران محدود شده است` when `canSendMessage==false`. Group settings panel (admins) gains a send-policy radio (`همه اعضا…` / `محدود کردن…`) and per-member `اجازه ارسال`/`مجاز به ارسال` toggles. `jim-api.js`: `setGroupSendPolicy`, `setGroupAllowedSender`. Scoped CSS added. Forward-to-group respects policy (backend 403).

## Mobile UI (PART 6)
`models/chat.dart`: `Conversation.{sendPolicy,canSendMessage,canManageSendPolicy,memberSendRestricted}`, `GroupMember.{allowedSender,canSend}`. `corbit_api.dart`: `setGroupSendPolicy`, `setGroupAllowedSender`. `conversation_screen.dart`: restricted composer replaced by a lock banner `ارسال پیام در این گروه محدود شده است`; fail-safe guards on text/attachment/voice sends (prevents offline optimistic queueing into a restricted group; cached permission respected). `group_info_screen.dart`: admin send-policy switch + per-member allow/revoke menu + allowed indicator. Long-press/multi-select/reactions/file open/image viewer/search jump/shared open/RTL untouched.

## MCP / Workspace Agent (PART 7)
Chat MCP/domain tools are **read/search-only**; there is **no in-plugin chat-send tool**. The Workspace Agent (`mcp.bootstrap`) posts replies via the same REST send endpoints, which now enforce `canSend` — but Agent/Assistant replies go to the user's SYSTEM/assistant conversation or DM (non-group → not gated), so Assistant/System behavior is preserved. **No Gateway/registry/schema change → Gateway not restarted/changed.**

## Not changed
Report engine/settings, eazyBI, Inbox, Automation, WebPush/FCM, Jira cluster/HAProxy/nginx config, plugin key, REST base, AO schema version mechanism. Mobile: no change to existing chat UX surfaces beyond the composer gate + group settings.
