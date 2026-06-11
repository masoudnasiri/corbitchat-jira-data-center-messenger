# CorbitChat — Administration & Installation Guide

## Requirements

| Requirement | Value |
|---|---|
| Jira | Jira Software / Jira Core **Data Center 9.x** (developed and tested on 9.17) |
| Database | Any Jira-supported database (storage via Active Objects) |
| HTTPS | **Required** for push notifications, voice recording and service workers. Terminate TLS on a reverse proxy (nginx, Apache, Caddy) or on Tomcat and set the Jira Base URL to the `https://` address |
| Outbound network | For push notifications the Jira node(s) must reach the browser push services over HTTPS (443): `fcm.googleapis.com` (Chrome/Edge), `updates.push.services.mozilla.com` (Firefox) |

The app works without HTTPS/outbound access — only Web Push and voice
recording are unavailable in that case.

## Installation

1. Jira administration → **Manage apps** → **Upload app** (or install from the
   Marketplace listing).
2. Upload `jira-internal-messenger-<version>.jar`.
3. On first start the app automatically:
   - creates its database tables (Active Objects, prefix `AO_099FDF_`),
   - creates a **project chat** for every existing project,
   - registers event listeners for issue events and project creation.

No manual configuration is required.

## What the app adds to Jira

| Module | Location |
|---|---|
| **Chat** item + unread badge | Top navigation bar |
| **CorbitChat** item | User avatar menu and Jira mobile menu |
| **Project Chat** item | Project sidebar (opens embedded in the project page) |
| **CorbitChat Configuration** | Jira Administration → Manage apps section (System Administrators only) |
| Chat page | `/plugins/servlet/jim/chat` |
| Admin console | `/plugins/servlet/jim/admin` |
| Service worker (push) | `/plugins/servlet/jim/sw.js` |
| REST API | `/rest/jim/1.0/*` (admin endpoints under `/rest/jim/1.0/admin/*`) |

## Admin console

Jira **System Administrators** get a CorbitChat configuration area under
**Jira Administration → Manage apps → CorbitChat → CorbitChat Configuration**
(direct URL: `/plugins/servlet/jim/admin`). All admin REST endpoints require
the System Administrator global permission, and every change is recorded in
an audit trail (visible on the Overview tab).

An in-app **Help** tab on the same page documents all options; the highlights:

### Overview

Status cards (chat mode, push, attachments, policy count, push subscriptions,
plugin version) and the recent admin audit trail.

### Access Control

**Global chat mode**:

| Mode | Effect |
|---|---|
| Allow everyone (default) | Any active Jira user can search and chat with anyone |
| Restricted by policy | Only the policy table below decides who can search / start / receive chats. **No matching rule = no access** (whitelist model) |
| Disabled | Chat is off: user search returns nothing, starting and sending direct messages is rejected server-side |

**Policy table** — each rule has: source (`USER`/`GROUP` + value), target
(`USER`/`GROUP`/`ANY` + value), action (`ALLOW`/`DENY`), capability flags
(search / start chat / receive chat), enabled flag and priority.

Evaluation: the **highest priority** matching rule wins; on a priority tie
**DENY overrides ALLOW**. Starting a chat also requires the recipient to be
allowed to *receive* chat from the initiator. Enforcement is server-side in
the REST API (user search, conversation creation, message send, attachment
upload) — not just UI hiding.

### Notification Settings

- Enable/disable Web Push globally.
- **Detail level**: `FULL_MESSAGE` (sender + excerpt), `SENDER_ONLY`
  (sender name, generic body), `GENERIC_ONLY` (no sender, no content) — use
  the reduced levels in privacy-sensitive environments since push payloads
  transit Google/Mozilla relays (encrypted, but metadata-conscious admins may
  still prefer less content).
- Aggregate notifications (one notification replaces the previous).
- Per-event toggles for mention and assignment pushes.
- **Send test notification** button (pushes to your own browser subscriptions).

### Attachments

- Enable/disable attachments (also disables voice messages).
- Maximum size (1–100 MB; enforced at upload).
- Extension allowlist (further restricts the built-in safe-type list; cannot
  re-enable blocked executable/script types).
- Inline image preview toggle.

### Diagnostics

Plugin version, REST/AO health, Jira base URL vs. request base URL, HTTPS
detection, VAPID/push status, push subscription count and failed push count —
the first place to look when push notifications misbehave.

## Push notifications

### How it works

- On first use the app generates a **VAPID key pair** (stored in plugin
  settings in the Jira database).
- Users opt in per browser ("Enable notifications" in the chat sidebar). The
  subscription (push endpoint + browser keys) is stored in the app's tables.
- When a user receives a direct message, a mention, an assignment or another
  assistant alarm — and is not currently looking at that conversation — Jira
  sends an **encrypted** push message (RFC 8291, `aes128gcm`) to the browser's
  push service. The relay (Google/Mozilla) cannot read the content.
- Expired subscriptions are cleaned up automatically.
- Notifications are suppressed only for the conversation the user is actively
  viewing; the in-app UI covers that case.

### Reverse proxy notes

- The service worker is served from
  `https://<jira>/plugins/servlet/jim/sw.js` — make sure the proxy passes
  `/plugins/servlet/jim/*` through unmodified (no caching of `sw.js`; the app
  already sends `Cache-Control: no-cache`).
- WebSockets are **not** used; no special proxy configuration is needed beyond
  standard Jira reverse-proxy setup.

### Firewall checklist

Allow outbound HTTPS (443) from all Jira nodes to:

- `fcm.googleapis.com`
- `updates.push.services.mozilla.com`

(Plus any region-specific endpoints those services use; allowing
`*.googleapis.com` and `*.push.services.mozilla.com` is simplest.)

## Data storage

| Data | Where |
|---|---|
| Conversations, messages, reactions, read states, group members, mention/event log, push subscriptions | Jira database, Active Objects tables `AO_099FDF_*` |
| Attachments & voice messages | Jira shared home, `data/jim-attachments/` |
| VAPID keys | Plugin settings (`propertyentry` / `propertystring`) |
| Presence ("active now") | In-memory only, per node |

See `security-and-privacy.md` for the complete statement.

## Permissions model

- Only logged-in, active Jira users can use the messenger.
- Direct conversations: only the two participants can read/write.
- Group chats: only members; the owner manages membership and can delete the
  group.
- Project chats: members + the project lead; only the lead manages members.
- All REST endpoints enforce participant checks server-side.
- Issue cards: the issue picker and issue enrichment respect the viewer's Jira
  browse permissions.

## Upgrading

Upload the new JAR via Manage apps (or `installed-plugins` + restart for
manual installs). Database schema upgrades run automatically. Users should
hard-refresh (Ctrl+Shift+R) after an upgrade; the push service worker updates
itself on the next chat page visit.

## Uninstalling

Removing the app stops all UI modules immediately. App tables (`AO_099FDF_*`)
and the attachments folder are left in place (standard Atlassian behaviour) —
drop them manually if you want a full cleanup.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| "Enable notifications" button missing | Page not served over HTTPS, or the browser does not support Web Push (e.g. iOS Safari outside a Home-Screen app) |
| No push when browser closed | Check outbound access to the push services from the Jira node; check the browser's OS-level notification permission |
| Generic notification text instead of message preview | Stale service worker — reload the chat page; the worker self-updates |
| Voice recording unavailable | HTTPS required by browsers for microphone access |
| UI looks broken after upgrade | Hard refresh (Ctrl+Shift+R) to bypass the old web-resource batch |
| Chat health check | `GET /rest/jim/1.0/health` and `GET /rest/jim/1.0/ao-health` should return 200 with `{"ok":true}` |

### Logging

App log lines are prefixed `event=` (e.g. `event=push stage=send`). To see
INFO-level app logs, add a logger for `com.corbitlogic.jira.internalmessenger`
at INFO level (Administration → System → Logging and profiling).
