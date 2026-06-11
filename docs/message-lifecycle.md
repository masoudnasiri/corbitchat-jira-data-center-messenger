# CorbitChat message lifecycle

Direct-chat message features: read receipts, reply, edit, soft delete, and inline image attachments.

## Seen / read receipts

Direct conversations have two participants. Read state is stored in `JimReadState.LAST_READ_MESSAGE_ID`.

- A message is **read by the current user** when `message.id <= currentUserLastReadMessageId`.
- A message is **seen by the other participant** (for the sender) when `message.id <= otherParticipantLastReadMessageId`.
- The sender UI shows:
  - `✓` (sent) when the message is not yet read by the recipient
  - `✓✓` (seen) when the recipient has read through that message
- System (Jira Assistant) cards do not show read checkmarks.
- No per-user read-receipt table is used for MVP direct chat.

## Reply model

- Send: `POST /rest/jim/1.0/conversations/{conversationId}/messages`
  - Body: `{ "body": "...", "replyToMessageId": 123 }` (`replyToMessageId` optional)
- `replyToMessageId` must belong to the same conversation.
- Replies to deleted messages are allowed; preview shows **Deleted message**.
- Message JSON includes `replyTo` preview:
  - `id`, `senderDisplayName`, `bodyPreview`, `deleted`, optional `attachmentPreview`
- Clicking a reply preview scrolls to the original message when it is loaded and briefly highlights it.

## Edit rules

- Endpoint: `PUT /rest/jim/1.0/messages/{messageId}` with `{ "body": "new text" }`
- Only the authenticated sender may edit.
- Only `USER` messages; not `SYSTEM` cards.
- Deleted messages cannot be edited.
- Body required unless the message has attachments.
- Sets `EDITED = 1`, `EDITED_AT = now`.
- Response includes `edited: true` and `editedAt`.
- UI shows **(edited)** near the timestamp.

### Unread after edit

If the other participant had already read the edited message (`LAST_READ_MESSAGE_ID >= messageId`), their read state is regressed to `messageId - 1` so the edited message becomes unread again. The sender's read state is unchanged.

## Delete rules (10-minute window)

- Endpoint: `DELETE /rest/jim/1.0/messages/{messageId}`
- Only the authenticated sender may delete.
- Only `USER` messages within **10 minutes** of `CREATED_AT`.
- After 10 minutes: HTTP **403** JSON:
  - `{ "error": "delete_window_expired", "message": "Messages can only be deleted within 10 minutes." }`
- Soft delete: `DELETED = 1`, `DELETED_AT`, `DELETED_BY_USER_KEY`; body cleared in storage and hidden from clients.
- Attachments are hidden in message JSON for deleted messages (files are not physically removed in MVP).
- Conversation preview updates to **Message deleted** when the deleted message was last.

## Attachments / inline images

- Upload: `POST /rest/jim/1.0/conversations/{conversationId}/attachments` (multipart)
- Images (`png`, `jpeg`, `gif`, `webp`) render inline with `previewUrl` and `downloadUrl`.
- SVG is not allowed for inline preview (blocked extension / disallowed MIME).
- Non-image files render as file cards with download link.
- URLs are permission-protected REST endpoints; storage paths are never exposed.

## Security / permissions

- All endpoints require an authenticated Jira user.
- Conversation access requires participant membership.
- Edit/delete require sender ownership.
- Attachment preview/download require conversation participation.
- REST returns JSON only (`Map<String, Object>`); errors do not expose stack traces or filesystem paths to the UI.

## Known limitations

- Polling (no WebSocket) for message and conversation updates.
- Delete is soft delete; no attachment file purge yet.
- No edit history / version diff.
- Single-file upload per message in MVP.
- No generated thumbnails (full image used for preview).
- Read receipts are conversation-level last-read pointer, not per-message multi-user receipts (sufficient for direct chat).
- Group chat not implemented.

cd /tmp && javac PushPayloadTest.java
PRIV='MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCCQgq6xONStDulViyK9xzb1WZIwuSE/C54iF7ZCarhLhQ=='
PUB='BFt9KEATRnQ2ycEHpRTgXwhuizBMkWC_ouO3O4Q_bqbTCXse4nfRIzl3w1mkuZxjUQYEqgjnXVnTrTrPgQakQ8Y'
echo "--- encrypted payload push to JIRAUSER10000 ---"
java PushPayloadTest "$PRIV" "$PUB" "$(cat /tmp/endpoint2.txt)" 'BOK3mljAXh9hsW-f2GbWhMuYHR2jw9wTCJM4m62viX5BsyPgTmL8g3DWZlGzfyn_vVm89h6pbmBH1Wlpn8dKpLA' 'K0ni7taOMrldUK9EjtmSQg' '{"title":"Diagnostic test","body":"Rich payload push works end-to-end"}'
echo "--- encrypted payload push to JIRAUSER10101 ---"
java PushPayloadTest "$PRIV" "$PUB" "$(cat /tmp/endpoint3.txt)" 'BCkb-1kCikcdgxV718RaPhTLNDB-WZW6qMUVi_tkCN0DARu1jc8cSQH9NO3PloCcS031tB8lUlp-6NZUUr-vmrc' 'bz103Esi143V2DBD75Fcgg' '{"title":"Diagnostic test","body":"Rich payload push works end-to-end"}'
