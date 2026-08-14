# CorbitChat — Team Messenger for Jira — Agent Guide

P2 plugin for **Jira Data Center 9.17.x** running in the `jira-srv` Docker container.

- **Workspace (ONLY):** `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane`
- **Do not use:** `/root/jira-dev/jira-issue-chat-panel` (stale; caused the figmachat4 P0 regression)
- **Plugin key (never change):** `com.corbitlogic.corbitchat.jira.dc` (marketplace branch; internal branch uses `com.corbitlogic.jira.internalmessenger.jira-internal-messenger`)
- **Jira URL:** `http://185.83.181.194:8080`
- **Test user:** `m.nasiri` / `Man@782761`
- **Chat page:** `http://185.83.181.194:8080/plugins/servlet/jim/chat`
- **Production baseline:** `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat4e-regressionfix`
  (see `docs/production-baseline.md`)

## Production baseline (frozen 2026-08-14)

Official Production plugin. Restore these before any new phase:

- Assistant report filters (`گزارش‌ها`, `منشن گزارش`, `گزارش‌های دریافتی`)
- `openConversationAtMessage` chat search jump-to-message

Never reuse versions `figmachat4c`, `figmachat4d`, or `figmachat4e`.

Before every future `mvn ... package`, run the mandatory pre-package
checklist in `docs/deployment-production.md` §2.1.

## Build (offline Maven — always use these flags)

```bash
cd /root/jira-dev/jira-issue-chat-panel-mcp-control-plane
mvn -s /root/.m2/settings.xml -Dmaven.repo.local=/root/maven-repos/atlassian-jira-offline -o clean package
```

Plain `mvn clean package` will FAIL (no internet access to Maven Central / Atlassian repos).
Build takes ~20–30 s. Artifact: `target/corbitchat-jira-dc-1.0.0.jar`

## Install

```bash
docker exec -u 0 jira-srv bash -lc 'rm -f /var/jira/plugins/installed-plugins/jira-internal-messenger-*.jar /var/jira/plugins/installed-plugins/corbitchat-jira-dc-*.jar'
docker cp target/corbitchat-jira-dc-1.0.0.jar jira-srv:/var/jira/plugins/installed-plugins/
docker exec -u 0 jira-srv bash -lc 'chmod 644 /var/jira/plugins/installed-plugins/corbitchat-jira-dc-1.0.0.jar'
docker restart jira-srv
```

Do NOT use `-it` with `docker exec` (no TTY in this environment).

## Wait for Jira startup (~2–3 minutes)

Jira returns 503 while starting, then 401/200 when ready. Poll like this:

```bash
AUTH='m.nasiri:Man@782761'; BASE='http://185.83.181.194:8080'
for i in $(seq 1 50); do
  code=$(curl -s -u "$AUTH" -o /dev/null -w '%{http_code}' "$BASE/rest/jim/1.0/health" 2>/dev/null || echo 000)
  [ "$code" = "200" ] && echo ready && break
  sleep 5
done
```

Use a shell timeout (`block_until_ms`) of at least 250–300 s for this poll.

## Test after every build/install

```bash
AUTH='m.nasiri:Man@782761'; BASE='http://185.83.181.194:8080'
for p in /rest/jim/1.0/health /rest/jim/1.0/ao-health /rest/jim/1.0/conversations /plugins/servlet/jim/chat; do
  echo "$p => $(curl -s -u "$AUTH" -o /dev/null -w '%{http_code}' "$BASE$p")"
done
```

Expected: all **200**. `health` and `ao-health` return JSON (`{"ok":true,...}`).

For UI changes, additionally verify the deployed assets (Jira WRM serves a CSS/JS batch):

```bash
PAGE=$(curl -s -u "$AUTH" "$BASE/plugins/servlet/jim/chat")
CSS=$(echo "$PAGE" | grep -o '/s/[^"]*jim-messenger-resources\.css' | head -1)
JS=$(echo "$PAGE" | grep -o '/s/[^"]*jim-messenger-resources\.js' | head -1)
curl -s -u "$AUTH" "$BASE$CSS" | grep -c 'some-new-class'   # confirm new CSS is live
curl -s -u "$AUTH" "$BASE$JS"  | grep -c 'someNewFunction'  # confirm new JS is live
```

Never claim a UI fix is done based on source code only — check the live page HTML and
deployed CSS/JS bundle. The user must hard-refresh (Ctrl+Shift+R) after each deploy
because the WRM CDN hash changes.

## Regression checklist (must stay working)

- Direct chat send/receive (Enter sends, Shift+Enter newline)
- Jira Assistant (SYSTEM conversation, read-only composer bar)
- Assistant report filters + report event cards (`REPORT_SUBMITTED`,
  `REPORT_SUBMITTED_TO_ME`, `REPORT_REPLY`, `REPORT_MENTION`)
- Chat search result click jumps via `openConversationAtMessage`
- Assignment notification cards (eventType ASSIGNMENT)
- Mention notification cards (eventType MENTION)
- Compact sidebar, shared galleries, multi-forward, Saved Messages
- Copy / Share / Forward / Delete / Save
- Sidebar unread counts and selected state
- Attachments upload/download/preview (if present in build)
- MCP admin, report/calendar admin, Workspace Agent status

## Key files

| File | Role |
|------|------|
| `src/main/resources/templates/messenger.vm` | Chat page shell (servlet-rendered Velocity) |
| `src/main/resources/css/jim-messenger.css` | All UI styles, scoped under `#jim-messenger-app` |
| `src/main/resources/js/jim-messenger.js` | All client rendering (conversations, messages, composer) |
| `src/main/resources/js/jim-api.js` | REST client |
| `src/main/resources/jim/i18n.properties` | Labels/placeholders |
| `src/main/resources/atlassian-plugin.xml` | Web resources, servlets, web-items, filters |
| `src/main/java/.../web/JimChatServlet.java` | Renders `messenger.vm` |
| `src/main/java/.../mobile/JimMobileMenuInjectionFilter.java` | Mobile menu injection |

## Hard constraints

- Do not change plugin key, Active Objects schema, or REST endpoints for UI-only work.
- All CSS must be scoped under `#jim-messenger-app` / `.jim-chat-shell` — never global
  `body`, `html`, `button`, `input`, `textarea`, `#page`, `.aui-*`.
- Preserve element IDs used by JS (`jim-message-input`, `jim-send-button`,
  `jim-composer*`, `jim-chat-header`, `jim-message-list`, `jim-conversation-list`, ...).
- No fake/non-working controls (toolbar icons, tabs, reactions) unless backed by real logic.
- Do not modify Jira core files or force desktop mode on mobile.
