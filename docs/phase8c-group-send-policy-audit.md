# Phase 8C — Group Send Policy: Current-Model Audit

**Date (UTC):** 2026-09-11. No code written before this audit.

## 1. Conversation types
`JimConversationType` (`model/JimConversationType.java`): **DIRECT, SYSTEM, GROUP, PROJECT**. Stored as string on `JimConversation.conversationType`.

## 2. Group data model
- **Conversation AO:** `ao/JimConversation.java` — `createdByUserKey` = permanent owner (group creator / project lead); `groupName`, `projectKey`, `archived`.
- **Participant AO:** `ao/JimGroupMember.java` — `conversationId` (indexed), `userKey` (indexed), **`role`** (String), `joinedAt`.
- **Roles (strings, not enum):** `JimGroupServiceImpl` `ROLE_OWNER="OWNER"`, `ROLE_ADMIN="ADMIN"`, `ROLE_MEMBER="MEMBER"`.
- **Admin determination:** `JimGroupServiceImpl.getRole()` → OWNER if `userKey==createdByUserKey` (`isOwner()`), else the member row's role, else MEMBER. Helpers: `isAdmin()`, `canManageMembers()` (OWNER/ADMIN), `canManageAdmins()` (OWNER only).
- **Web/mobile know admin via DTO:** `JimRestJsonMapper.toConversationMap` exposes `myRole`, `isOwner`, `canManage`, `canManageAdmins`. Mobile `Conversation` mirrors `myRole`/`canManage`/`canManageAdmins` (+ getters `iAmOwner`/`iAmAdmin`).

## 3. Message-send endpoints (all user-content insert paths)
- **Web REST** (`JimConversationResource`, `/rest/jim/1.0`): `POST /conversations/{id}/messages` (text/reply/issue-link), `POST /conversations/{id}/attachments`. Forward: `JimMessageResource POST /messages/{id}/forward`.
- **Mobile BFF** (`CorbitMobileChatResource`, `/rest/corbit-mobile/1.0/chat`): `POST /conversations/{id}/messages`, `POST /conversations/{id}/attachments` (with `voice`), `POST /messages/{id}/forward`.
- **Service choke points (AO insert):** `JimMessageServiceImpl.sendUserMessage`, `.sendIssueLinkMessage`, `.forwardUserMessage`; `JimAttachmentService.uploadAttachment`. Voice = attachment with `voice=true`.
- **System/assistant/group-event inserts** (`createSystemMessageInTransaction`, `JimGroupServiceImpl.addGroupEventMessage`): SYSTEM sender — **must NOT be gated** by send policy.
- **MCP/domain:** chat tools are **read/search-only** (`JimMcpDomainResource`); no in-plugin `corbit_chat_send`. Workspace Agent (`mcp.bootstrap`) replies **externally via the same REST send endpoints** → automatically covered by REST-layer enforcement.

## 4. Mobile group info/settings UI
`lib/features/chat/group_info_screen.dart` — add/remove member, promote/demote admin (`setMemberRole`), rename, avatar, leave, delete, shared files. Admin detected from `Conversation.canManage`/`canManageAdmins`/`iAmOwner`. Members via `listGroupMembers`.

## 5. Web group UI
`templates/messenger-app.vm` `#jim-members-panel`; rendered by `js/jim-messenger.js` `renderMembersPanel()` (gated by `conversation.canManage`). API client `js/jim-api.js`. This panel is where the send-policy controls belong.

## 6. Permission checks today
- `JimPermissionServiceImpl.isParticipant/requireParticipant`; enforced in `JimConversationServiceImpl.getConversationForUser` (also rejects archived).
- DIRECT-only global policy: `enforceDirectChatPolicy`. GROUP/PROJECT sends only check participant today — **no per-sender restriction exists** (this is the gap 8C fills).
- SYSTEM composer read-only enforced client-side only.

## 7. DB/AO schema safety
- No `UpgradeTask` classes; AO auto-migrates new **nullable** columns; new entities registered in `atlassian-plugin.xml` `<ao key="jim-active-objects">`.
- **Adding fields to `JimConversation` is safe** (auto-migrate). Adding a boolean to `JimGroupMember` is safe.
- **Chosen model (smallest safe, no new table, cluster-safe):**
  - `JimConversation.sendPolicy` (String, nullable). `null`/empty/`ALL_MEMBERS` = current behavior; `ADMINS_AND_ALLOWED` = restricted.
  - `JimGroupMember.allowedSender` (Boolean, nullable). Per-member allow flag. Because it lives on the member row, **removing a member auto-clears the allowance** (requirement satisfied for free); cluster-safe; no manual DB writes; handles inactive users (row-scoped).
- Audit via existing `JimChatAuditService.record(actor, action, messageId, conversationId, targetUserKey, details)`.

## 8. Design — permission rules (PART 2)
Central enforcement: `JimGroupService.canSend(conversation, userKey)` + `requireCanSend(...)`:
- Non-GROUP/PROJECT (DIRECT/SYSTEM): return true (existing rules apply; send policy is group-only).
- `sendPolicy` null/`ALL_MEMBERS`: true (default; existing groups unchanged).
- `ADMINS_AND_ALLOWED`: true iff OWNER or ADMIN, OR member row `allowedSender==true`. Else false.
- SYSTEM/plugin-internal inserts do not pass through this (separate methods) — Assistant/system messages unaffected.
- Reactions: NOT gated (kept allowed; documented). Read/list/search/shared-files/avatar/group-info: NOT gated.
Enforced at REST send resources (web + mobile + forward) right after the participant check → 403 with a Persian-friendly message, before any message/attachment row or upload side effect. Manage endpoints (set policy, add/remove allowed sender) are OWNER/ADMIN-only and GROUP/PROJECT-only.

## 9. DTO additions
`JimRestJsonMapper.toConversationMap` (web+mobile) adds: `sendPolicy`, `canSendMessage` (per viewer), `canManageSendPolicy` (OWNER/ADMIN), `memberSendRestricted` (UI flag = policy restricted & !canSend). Group member map adds `allowedSender` + `canSend` (for admin management UI). Mobile `Conversation` parses `sendPolicy`, `canSendMessage`, `canManageSendPolicy`, `memberSendRestricted`.
