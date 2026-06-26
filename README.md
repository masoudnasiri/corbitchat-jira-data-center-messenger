# CorbitChat

**A self-hosted team messenger for Jira Data Center.**

CorbitChat adds a modern team messenger directly inside Jira Data Center, including direct messages, group chats, automatic project chat rooms, Jira-aware notifications, file sharing, voice messages, issue cards, mentions, unread tracking, and encrypted Web Push notifications.

It is designed for teams that want Jira-related collaboration to happen inside Jira, without depending on external chat platforms.

---

## Badges

![Jira Data Center](https://img.shields.io/badge/Jira%20Data%20Center-9.x-blue)
![Tested on Jira](https://img.shields.io/badge/Tested%20on%20Jira-9.17-brightgreen)
![Release](https://img.shields.io/badge/release-1.0.0-orange)
![Self Hosted](https://img.shields.io/badge/self--hosted-yes-success)
![Free License](https://img.shields.io/badge/license%20key-free%20on%20request-brightgreen)

---

## Overview

CorbitChat is a complete team messenger for **Jira Data Center**.

It allows Jira users to communicate through direct conversations, group chats, and project-specific chat rooms without leaving Jira. The plugin is built for self-hosted Jira environments where teams want messaging, notifications, issue discussions, and project collaboration to stay close to their Jira workflows.

All application data remains inside the customer’s Jira infrastructure:

* Messages and metadata are stored in the Jira database.
* Attachments and voice messages are stored in Jira shared home.
* No external application server is required.
* No telemetry or analytics are sent by the plugin.
* Optional Web Push notifications are encrypted before being sent to browser push services.

---

## Key Features

### Messaging

* Direct 1:1 conversations between Jira users
* Group chats with full member management
* Automatic project chat rooms for Jira projects
* Project chat embedded inside the Jira project page
* Replies
* Message editing and deletion
* Emoji reactions
* Pinned messages
* File and image attachments with inline previews
* Voice messages with in-chat playback
* `@mentions` in group and project conversations
* Jira issue linking with rich issue cards
* Read/unread tracking
* Per-conversation unread counters
* Top navigation unread badge
* Presence indicator: `active now`
* Responsive mobile UI
* Jira mobile menu entry

---

## Jira Assistant

CorbitChat includes a personal Jira Assistant conversation that turns Jira activity into chat-style notification cards.

Supported assistant events include:

* Issue assignments
* Issue mentions
* Chat mentions
* Issue comments
* Status changes
* Read/unread distinction
* `NEW` badges for unread assistant cards

This makes Jira notifications easier to follow without switching between multiple Jira screens.

---

## Notifications

CorbitChat supports browser-based Web Push notifications.

Features include:

* Encrypted Web Push using VAPID / RFC 8291
* Desktop browser support
* Android browser support
* Notifications can be delivered even when the Jira tab is closed
* Rich notification content with sender and message preview
* Mention and assignment notification titles
* Smart suppression for the conversation currently open
* In-Jira fallback notifications while Jira is open
* Per-user and per-browser enable/disable control

> HTTPS is required for Web Push notifications, service workers, and voice recording because modern browsers require secure contexts for these capabilities.

---

## Platform Support

| Component      | Supported                                                   |
| -------------- | ----------------------------------------------------------- |
| Jira           | Jira Data Center 9.x                                        |
| Tested version | Jira 9.17                                                   |
| Database       | Any Jira-supported database                                 |
| Storage        | Jira database + Jira shared home                            |
| REST API       | `/rest/jim/1.0/*`                                           |
| HTTPS          | Required for Web Push, service workers, and voice recording |

The plugin can run without HTTPS, but browser push notifications and voice recording will not be available.

---

## Installation

A pre-built JAR file is available in the repository releases.

To install CorbitChat:

1. Download the latest `jira-internal-messenger-<version>.jar` from the **Releases** section.
2. Log in to Jira as a System Administrator.
3. Go to:

```text
Jira Administration → Manage apps → Upload app
```

4. Upload the JAR file.
5. Wait for Jira to install and enable the plugin.
6. Open Jira and use the new **Chat** / **CorbitChat** entry.

On first startup, CorbitChat automatically:

* Creates its Active Objects database tables.
* Creates project chat rooms for existing projects.
* Registers Jira event listeners.
* Enables the REST API under `/rest/jim/1.0`.

---

## Free License Key

The pre-built Jira plugin requires a license key because it was prepared with Marketplace-style licensing support.

The license key is currently provided **free of charge**.

If you want to install and use the built JAR version, please request a free license key from the repository owner.

When requesting a license, please include:

* Your name
* Organization name, if applicable
* Jira version
* Jira user tier
* Intended use: testing, internal use, or production

The license key is only required for activating the packaged Jira plugin. It does not mean the license is paid.

---

## Recommended Evaluation Checklist

After installation, you can test the plugin with this checklist:

* Confirm that the **Chat** menu appears in Jira.
* Start a direct conversation with another Jira user.
* Create a group chat.
* Add and remove group members.
* Open a Jira project and test the embedded project chat.
* Send a message with a Jira issue card.
* Send a reply.
* Edit and delete a message.
* Add an emoji reaction.
* Pin a message.
* Upload an image or file attachment.
* Record and play a voice message.
* Enable browser notifications.
* Test unread counters and top navigation badge.
* Check Jira Assistant cards for assignments, mentions, comments, and status changes.

---

## Data Storage

| Data               | Location             |
| ------------------ | -------------------- |
| Conversations      | Jira database        |
| Messages           | Jira database        |
| Read states        | Jira database        |
| Reactions          | Jira database        |
| Group members      | Jira database        |
| Event log          | Jira database        |
| Push subscriptions | Jira database        |
| VAPID keys         | Jira plugin settings |
| Attachments        | Jira shared home     |
| Voice messages     | Jira shared home     |
| Presence           | In-memory only       |

User identification uses Jira user keys. Display names and avatars are resolved from Jira.

---

## Web Push Notes

If users enable notifications, Jira sends encrypted push messages to browser push services.

Common endpoints:

* Chrome / Edge: `fcm.googleapis.com`
* Firefox: `updates.push.services.mozilla.com`

Push payloads are encrypted before delivery. Browser push services transport encrypted payloads and cannot read the message content.

If notifications are not enabled by the user, no push subscription is created.

---

## REST API

CorbitChat exposes its API under:

```text
/rest/jim/1.0/*
```

Admin endpoints are available under:

```text
/rest/jim/1.0/admin/*
```

Useful health endpoints:

```text
GET /rest/jim/1.0/health
GET /rest/jim/1.0/ao-health
```

---

## Documentation

This repository may include the following documentation files:

* `user-guide.md` — End-user guide
* `admin-guide.md` — Installation, configuration, and troubleshooting
* `security-and-privacy.md` — Security, privacy, and data storage statement
* `release-notes.md` — Version release notes

---

## Release Notes

### 1.0.0

First public release.

Includes:

* Direct messages
* Group chats
* Project chats
* Jira Assistant
* Issue cards
* Mentions
* File attachments
* Voice messages
* Reactions
* Pinned messages
* Read/unread tracking
* Web Push notifications
* Mobile responsive UI
* Admin configuration
* Access control policies
* Self-hosted Jira database and shared-home storage

---

## License

CorbitChat source code is published in this repository.

The packaged Jira plugin may require a free license key for activation because the build was prepared for Marketplace-style licensing.

Please check the `LICENSE` file in this repository for the exact source code license terms.

---

## About

CorbitChat is developed by **CorbitLogic**.
