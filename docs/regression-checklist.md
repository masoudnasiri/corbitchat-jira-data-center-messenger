# Chat release regression checklist

Machine-readable twin: `docs/regression-checklist.yaml`.  
Automation: `scripts/run-phase6-dev.sh` (DEV only).

Must never fail again after figmachat4 / shared-open regressions.

## A. Baseline / source safety

- [ ] Build only from `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane`
- [ ] Never build from `/root/jira-dev/jira-issue-chat-panel`
- [ ] Mobile only from `/root/jira-dev/corbitchat-mobile`
- [ ] No `reportreplies-workdays1` / `feature/reply-composer-attachments` lineage
- [ ] No jars, APKs, PWA tarballs, logs, backups, dumps, secrets, tokens, or Firebase admin JSON committed

## B. WRM / static asset safety

- [ ] No `jim-*-min.js/css` in `src/main/resources`
- [ ] Packaged JAR is built from current JS/CSS (AMPS may add min **inside the JAR only**)
- [ ] Live WRM batch URL contains the current plugin version
- [ ] Live batch / source contains:
  - `openConversationAtMessage`
  - `messagesAround`
  - shared-gallery `data-goto-message`
  - `jim-message-highlight`
  - `openForwardModalForMessages`
  - Assistant report filters
  - mention badge (`open-mention`)

## C. Assistant filters

- [ ] `گزارش‌ها`
- [ ] `منشن گزارش`
- [ ] `گزارش‌های دریافتی`
- [ ] `REPORT_SUBMITTED` / `REPORT_SUBMITTED_TO_ME` / `REPORT_REPLY` / `REPORT_MENTION`
- [ ] Existing non-report filters still present

## D. Chat search

- [ ] Search endpoint works
- [ ] Cursor pagination works (`nextCursor` / `cursor=`)
- [ ] Result includes `conversationId` + `messageId`
- [ ] Click jumps and highlights the exact message
- [ ] Old / paginated message jump works
- [ ] Project chat search works
- [ ] Unauthorized search is blocked
- [ ] Search listing does not mark the conversation read

## E. Shared galleries

- [ ] Files / Media / Links tabs work
- [ ] `باز کردن پیام` uses `openConversationAtMessage`
- [ ] Off-page source uses `messagesAround`
- [ ] Exact source message highlights
- [ ] Download / copy / open still work
- [ ] Profile/info entry and folder shortcut still work

## F. Message actions

- [ ] پاسخ / کپی / اشتراک‌گذاری / ارسال به… / ذخیره در پیام‌های ذخیره‌شده / حذف برای من
- [ ] Multi-forward works
- [ ] Selection does not mark read
- [ ] System / deleted messages do not expose unsafe actions

## G. Mobile / PWA

- [ ] Build label correct (`+88 / figma-chat-shared-open-hotfix` until a bump)
- [ ] Production artifact is 7G TECH
- [ ] No `Corbit Hub` on Production index/manifest
- [ ] Search screen, result jump, `نتایج قدیمی‌تر` sends `cursor=`
- [ ] `@` badge opens `latestMentionMessageId`
- [ ] Header/avatar → profile/info; Files/Media/Links chips
- [ ] Tabs: Boards, Chat, Dashboard, Tasks, More
- [ ] RTL / Jalali preserved

## H. Admin / health

- [ ] `/rest/corbit-mobile/1.0/health` and `ao-health`
- [ ] `/rest/jim/1.0/health` and `ao-health`
- [ ] Chat page, admin page, MCP admin page
- [ ] Workspace Agent status (read-only)
- [ ] Report/calendar admin (read-only, no setting changes)
- [ ] Exactly one CorbitChat jar
