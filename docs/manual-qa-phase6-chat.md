# Manual QA — Phase 6 chat

For a human tester after a **hard refresh** (Ctrl+Shift+R).
Do not change report/calendar or Workspace Agent settings.
Do not deploy Production from this script.

Tester: ________  Date: ________  Env: DEV / Production

## Web

1. Hard-refresh `/plugins/servlet/jim/chat`.
2. Open a **direct** chat. Confirm messages and composer.
3. Open a **group** chat.
4. Open **Saved Messages**.
5. Search a known word (at least 2 characters).
6. Click a search result.
7. Confirm the conversation opens, the list scrolls, and the message has `jim-message-highlight`.
8. Search / open a message that is **not** in the latest page. Confirm `/messages/around/{id}` (or older-page load) and highlight.
9. Open shared **Files**.
10. Click `باز کردن پیام`. Confirm exact source message + highlight.
11. Open shared **Media**.
12. Click `باز کردن پیام`. Confirm exact source + highlight.
13. Open shared **Links**.
14. Click `باز کردن پیام`. Confirm exact source + highlight.
15. On Jira Assistant, confirm filters: `گزارش‌ها`, `منشن گزارش`, `گزارش‌های دریافتی`.
16. Select two messages and **ارسال به…** (multi-forward). Confirm both arrive.
17. Confirm Persian labels: پاسخ، کپی، اشتراک‌گذاری، ارسال به…، ذخیره در پیام‌های ذخیره‌شده، حذف برای من.

Pass / fail / notes:

| Step | Result | Notes |
|------|--------|-------|
| 1–4 shell | | |
| 5–8 search jump | | |
| 9–14 shared open | | |
| 15 filters | | |
| 16–17 actions | | |

## Mobile / PWA

1. Open `/mobile/`.
2. About / `version.json`: build **88**, label `figma-chat-shared-open-hotfix`.
3. Branding: Production = **7G TECH**. DEV = Hub. No `Corbit Hub` on Production.
4. Open **Chat**.
5. Search a message.
6. Tap a result.
7. Confirm jump + highlight.
8. Tap `نتایج قدیمی‌تر` if shown. Confirm `cursor=` is sent.
9. If an `@` badge exists, tap it. Confirm `latestMentionMessageId`.
10. Tap header / avatar → profile / info.
11. Open Files / Media / Links chips.
12. Tap `باز کردن پیام` (or the open-message control). Confirm exact source.
13. Long-press: پاسخ، کپی، اشتراک‌گذاری، ارسال به…، ذخیره، حذف برای من.
14. Bottom nav: Boards, Chat, Dashboard, Tasks, More.

| Step | Result | Notes |
|------|--------|-------|
| 1–3 version/brand | | |
| 4–9 search / @ | | |
| 10–12 profile / shared | | |
| 13–14 actions / tabs | | |

## Admin (read-only)

1. `/rest/jim/1.0/health` → 200
2. `/rest/jim/1.0/ao-health` → 200
3. `/rest/corbit-mobile/1.0/health` and `ao-health` → 200
4. `/plugins/servlet/jim/admin` opens
5. `/plugins/servlet/jim/mcp-admin` opens
6. Workspace Agent status visible — **do not change**
7. Report/calendar admin visible — **do not change**
8. Exactly one `corbitchat-jira-dc-*.jar`

| Step | Result | Notes |
|------|--------|-------|
| Health | | |
| Admin pages | | |
| One jar | | |
