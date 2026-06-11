# CorbitChat — Atlassian Marketplace Listing Copy

Ready-to-paste content for the Marketplace listing form
(https://marketplace.atlassian.com → Manage apps → Create new app listing).

---

## App name

**CorbitChat — Team Messenger for Jira**

## Tagline / Summary (max 130 characters)

> Slack-style team chat inside Jira Data Center: direct messages, group & project
> chats, mentions, voice notes and push notifications.

(129 characters — fits the limit.)

## Categories

- Primary: **Communication & collaboration**
- Secondary: **Project management**

## Search keywords

`chat`, `messenger`, `team chat`, `direct message`, `instant messaging`,
`notifications`, `push notifications`, `mentions`, `collaboration`, `slack`,
`group chat`, `project chat`, `voice message`

---

## Highlights (3 blocks, title ≤ 53 chars + paragraph + screenshot each)

### Highlight 1 — A full messenger, without leaving Jira

Stop switching between Jira and external chat tools. CorbitChat adds a complete,
modern messenger to Jira Data Center: direct messages, group chats and
auto-provisioned project chat rooms — with replies, reactions, pins, message
editing, file attachments and voice notes. Everything stays inside your Jira
instance: no external servers, no data leaves your infrastructure.

### Highlight 2 — Chat that understands Jira

Link any issue into a conversation as a rich issue card, straight from the
built-in issue picker. The Jira Assistant bot turns assignments, mentions,
comments and status changes into personal notification cards — with clear
read/unread states. Every project automatically gets its own chat room,
embedded right inside the project sidebar and managed by the project lead.

### Highlight 3 — Real-time push notifications, everywhere

Native, encrypted Web Push notifications on desktop and Android — even when the
Jira tab is closed. Notifications show who wrote what ("Maria: see PROJ-12"),
mentions and assignments arrive as separate persistent alerts, and nothing pops
up for the conversation you are already reading. Users opt in with one click
and can disable notifications at any time.

---

## More details (long description)

CorbitChat is a self-hosted team messenger built natively for Jira Data Center.

**Messaging**
- Two-pane messenger UI with conversation sidebar (All / Personal / Groups tabs)
- Direct (1:1) conversations with any active Jira user
- Group chats: create groups, add/remove members, leave groups; owners can
  delete groups
- Project chats: every project automatically gets a chat room, visible in the
  project sidebar and opened inside the project page; members are managed by
  the project lead
- Replies, message editing, deletion, emoji reactions, pinned messages
- File and image attachments with inline previews
- Voice messages with in-chat playback
- @mentions of group members with mention alarms
- Jira issue linking: search an issue and share it as a rich issue card
- Unread counters per conversation, in the sidebar and on the Jira top
  navigation bar
- Presence: see who is active right now
- Responsive UI for mobile browsers + entry in the Jira mobile menu

**Jira Assistant**
- Personal read-only conversation with notification cards for assignments,
  mentions (in issues and in chats), comments and status changes
- NEW badge for unread alarms

**Notifications**
- Encrypted Web Push (RFC 8291 / VAPID) on desktop browsers and Android —
  works even when the browser tab is closed
- Rich content: sender name + message preview, "You were mentioned in
  PROJ-12", "PROJ-12 assigned to you"
- Smart suppression: no popups for the conversation currently on screen
- In-Jira fallback notifications while any Jira page is open
- Per-user, per-browser enable/disable toggle

**Privacy & architecture**
- 100% self-hosted: all messages, files and metadata stay in your Jira
  database and Jira home directory
- Push payloads are end-to-end encrypted; Google/Mozilla push relays cannot
  read message content
- No external services, no telemetry, no third-party data processors

---

## Screenshot plan (1840×1040 px, PNG)

| # | Screenshot | Caption |
|---|------------|---------|
| 1 | Full messenger page with sidebar + active direct chat | A complete messenger inside Jira |
| 2 | Group chat with mentions, reactions and a pinned message | Group chats with mentions, reactions and pins |
| 3 | Project page with embedded project chat | Every project gets its own chat room |
| 4 | Issue card shared in a conversation | Share Jira issues as rich cards |
| 5 | Jira Assistant conversation with assignment/mention cards | Personal Jira notifications, in one place |
| 6 | Desktop push notification ("Maria: can you review PROJ-12?") | Real-time encrypted push notifications |
| 7 | Mobile view of a conversation | Works on mobile browsers |

## Other required assets

| Asset | Spec | Notes |
|---|---|---|
| App logo | 144×144 px PNG | Use the CorbitChat logo on transparent background |
| Banner | 1120×548 px PNG | Logo + tagline over product screenshot |
| Demo video (optional) | YouTube link | 60–90 s walkthrough strongly recommended |

## Listing links (fill before submitting)

| Field | Value |
|---|---|
| Documentation URL | link to hosted copy of `user-guide.md` / `admin-guide.md` |
| Support URL / email | https://corbitlogic.com/support, support@corbitlogic.com |
| Privacy policy URL | link to hosted copy of `security-and-privacy.md` + company policy |
| EULA | Atlassian standard EULA, or custom EULA URL |
| Source / issue tracker (optional) | — |

## Suggested pricing (Data Center, paid-via-Atlassian, per-year)

Anchor low against Slack/Teams licensing — the value story is "chat included
with Jira".

| Tier (users) | List price / year (USD) |
|---|---|
| 50 | 500 |
| 100 | 900 |
| 250 | 1,750 |
| 500 | 3,000 |
| 1,000 | 5,000 |
| 2,000 | 8,000 |
| 5,000+ | 12,000 |

Adjust freely; Marketplace takes 15–25% (15% for Bug Bounty participants /
cloud-security-participating partners, see current Atlassian terms).
