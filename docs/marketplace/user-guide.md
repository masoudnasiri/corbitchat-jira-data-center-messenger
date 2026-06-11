# CorbitChat — User Guide

CorbitChat is a team messenger built into Jira. This guide covers everything an
end user needs.

## Opening the chat

- Click **Chat** in the Jira top navigation bar, or
- open your avatar menu → **CorbitChat**, or
- on mobile browsers, use the Jira mobile menu → **CorbitChat**.

The unread badge on the **Chat** menu shows your total unread messages from
anywhere in Jira.

## The messenger layout

- **Left sidebar** — your conversations, with search, unread counters and tabs:
  - **All** — everything
  - **Personal** — direct chats and the Jira Assistant
  - **Groups** — group chats
- **Right panel** — the selected conversation: header, messages, composer.

A green dot on an avatar means the person is active in the messenger right now.

## Starting a conversation

1. Click **+ New conversation**.
2. Search for a colleague by name.
3. Select them — the direct conversation opens immediately.

## Sending messages

- Type and press **Enter** to send, **Shift+Enter** for a new line.
- Hover over any message for actions: **Reply**, **Edit**, **Delete**, **Pin**,
  and emoji **reactions**.
- Pinned messages appear in a banner at the top of the conversation.

### Attachments

Click the 📎 icon to attach a file or image. Images show inline previews;
other files appear as downloadable cards.

### Voice messages

Click the microphone icon, record, then send. Voice notes play directly in the
conversation. (Your browser will ask for microphone permission the first time.)

### Emoji

Click the emoji icon next to the composer to insert emoji.

### Mentions (group chats)

Click the **@** icon (or type `@`) to mention a group member. Mentioned users
get a Jira Assistant alarm and a push notification.

### Linking Jira issues

Click the issue icon in the composer, search for an issue exactly like in
Jira's issue picker, and select it. The issue is shared as a rich card with
key, summary and status. You can add a message (and mentions) along with it.

## Group chats

- **Create**: sidebar → **+ New group**, name it, add members.
- **Manage members**: open the group → members panel; owners can add/remove
  members and delete the group.
- **Leave**: any member (except the owner) can leave a group.

## Project chats

Every Jira project has its own chat room:

- Open it from the **Project Chat** entry in the project sidebar — it opens
  inside the project page.
- Members are managed by the **project lead**; members cannot leave.
- Mention teammates with **@** to send them an alarm.

## Jira Assistant

The **Jira Assistant** conversation collects your personal Jira notifications
as cards:

- Issues **assigned to you**
- **Mentions** — in issue comments and in chats
- **Comments** and **status changes** on issues you're involved in

Unread cards show a blue **NEW** badge until you have seen them.

## Notifications

### Enabling push notifications

1. Open the chat page.
2. Click **🔔 Enable notifications** at the bottom of the sidebar.
3. Accept the browser permission prompt.

You will now receive native notifications — on desktop and Android, even when
the Jira tab is closed:

- "**Maria Kim** — *can you review the deploy plan?*"
- "**You were mentioned in PROJ-12**"
- "**PROJ-12 assigned to you**"

Notes:

- No popup is shown for the conversation you are currently reading.
- Notifications for the same conversation update a single alert instead of
  flooding your notification tray; mentions and assignments arrive as separate
  persistent alerts.
- Click a notification to jump to the chat.

### Disabling

Click **🔕 Disable notifications** in the sidebar. The setting is per browser.

### Supported platforms

| Platform | Push (tab closed) | In-page notifications |
|---|---|---|
| Chrome / Edge / Firefox desktop | ✔ | ✔ |
| Chrome / Firefox on Android | ✔ | ✔ |
| Safari on iOS | only as installed Home-Screen app (iOS 16.4+) | ✔ |

## Mobile

The messenger UI is responsive: on small screens it becomes a single-panel
flow (conversation list → chat) with a back button.

## Tips

- **Ctrl+Shift+R** (hard refresh) fixes most UI glitches after an upgrade.
- Voice recording requires HTTPS (your admin's responsibility) and microphone
  permission.
