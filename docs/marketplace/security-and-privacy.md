# CorbitChat — Security, Privacy & Data Storage Statement

This document is the basis for the Marketplace "Privacy & Security" tab and
for customer security reviews.

## Summary

CorbitChat is fully self-hosted. All user content (messages, files, voice
notes, metadata) is stored inside the customer's Jira infrastructure. The app
introduces **no external services, no telemetry and no third-party data
processors**. The only outbound traffic is optional, end-to-end encrypted Web
Push delivery.

## Data the app stores

| Data | Location | Contents |
|---|---|---|
| Conversations | Jira DB (`AO_099FDF_JIM_CONVERSATION`) | type, participants, group/project name, last-message preview |
| Messages | Jira DB (`AO_099FDF_JIM_MESSAGE`) | author, body, timestamps, reply/issue references, edit/delete flags |
| Read states | Jira DB (`AO_099FDF_JIM_READ_STATE`) | last-read message per user per conversation |
| Reactions | Jira DB (`AO_099FDF_JIM_REACTION`) | emoji, user, message |
| Group members | Jira DB (`AO_099FDF_JIM_GROUP_MEMBER`) | conversation, user key, role |
| Event log | Jira DB (`AO_099FDF_JIM_EVENT_LOG`) | fingerprints of processed Jira events (deduplication) |
| Push subscriptions | Jira DB (`AO_099FDF_JIM_PUSH_SUB`) | per-browser push endpoint URL + public browser keys, user key |
| VAPID signing keys | Jira DB (plugin settings) | EC P-256 key pair generated on the instance |
| Attachments / voice notes | Jira shared home (`data/jim-attachments/`) | uploaded files |
| Presence | In-memory only | last-activity timestamps; lost on restart, never persisted |

User identification uses Jira **user keys**; display names and avatars are
resolved live from Jira's user management at render time.

## Data leaving the network (Web Push only)

When an administrator's network allows it and a user opts in:

- Jira sends push messages to the user's **browser push service**
  (`fcm.googleapis.com` for Chrome/Edge, `updates.push.services.mozilla.com`
  for Firefox).
- Payloads (sender name, message preview) are encrypted on the Jira server
  with the **browser's own public key** per **RFC 8291 (`aes128gcm`)** and
  authenticated with **VAPID (RFC 8292)**. Push relays transport an opaque
  ciphertext; only the destination browser can decrypt it.
- If a user never enables notifications, nothing is ever sent.
- Disabling notifications deletes the subscription server-side; expired or
  revoked subscriptions are removed automatically.

No other data leaves the instance. The app performs no update checks, no
analytics and no license "phone home" beyond Atlassian's standard Marketplace
licensing.

## Access control

- All REST endpoints (`/rest/jim/1.0/*`) require an authenticated, active Jira
  user and enforce per-conversation participant checks server-side.
- Direct chats: participants only. Group chats: members only; owner manages
  membership. Project chats: members + project lead; lead manages membership.
- Issue pickers/cards respect the viewer's Jira browse permissions.
- Attachment downloads enforce conversation membership; files are stored with
  randomized names and served only through the authenticated REST layer.
- All rendered content is sanitized; message bodies are escaped against XSS.

## Cryptography

| Use | Algorithm |
|---|---|
| VAPID JWT signing | ECDSA P-256 / SHA-256 (ES256) |
| Push payload encryption | ECDH (P-256) + HKDF-SHA-256 + AES-128-GCM per RFC 8291 |
| Implementation | Java JCA (JRE built-in); no bundled third-party crypto libraries |

## Data subject requests (GDPR)

- **Right of erasure**: deleting a message marks it deleted and hides its
  content; group/conversation deletion removes contained data. For full user
  off-boarding, rows for a user key can be removed from the `AO_099FDF_*`
  tables; attachments live under `data/jim-attachments/`.
- **Data portability**: all content is in the customer's own database and file
  system and can be exported with standard tooling.
- The vendor (CorbitLogic) has no access to any customer data.

## Security contact

Report vulnerabilities to **security@corbitlogic.com**. We aim to acknowledge
within 2 business days and provide a fix or mitigation plan within 14 days for
critical issues.
