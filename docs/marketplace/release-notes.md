# CorbitChat — Release Notes

## 1.0.0 (first public release)

A complete team messenger for Jira Data Center.

### Messaging

- Direct (1:1) conversations between Jira users
- Group chats with full member management (create, add/remove, leave, delete)
- Automatic per-project chat rooms, embedded in the project page and managed
  by the project lead
- Replies, message editing and deletion, emoji reactions, pinned messages
- File and image attachments with inline previews
- Voice messages with in-chat playback (HTTP range streaming)
- @mentions of group members with assistant alarms
- Jira issue linking with rich issue cards (native issue picker)
- Read/unread tracking, per-conversation unread counters, top-navigation badge
- Presence indicator ("active now")
- Responsive mobile UI + Jira mobile menu entry

### Jira Assistant

- Personal notification feed as chat cards: assignments, mentions (issues and
  chats), comments, status changes
- Read/unread distinction with NEW badges

### Notifications

- Encrypted Web Push (RFC 8291 / VAPID, built-in crypto — no external
  libraries) for desktop browsers and Android, delivered even when the
  browser tab is closed
- Rich notification content: sender + preview, mention and assignment titles
- Per-conversation smart suppression (no popup for the chat on screen)
- In-Jira fallback notifications on every Jira page
- Per-user, per-browser enable/disable

### Platform

- Jira Data Center 9.x (tested on 9.17)
- All data self-hosted in the Jira database and shared home
- REST API under `/rest/jim/1.0`
