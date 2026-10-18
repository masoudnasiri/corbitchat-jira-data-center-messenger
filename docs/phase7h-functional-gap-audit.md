# Phase 7H — Functional gap audit (DEV)

DEV-only. Production / Gateway / Workspace Agent are not changed.

Confirmed live baseline before this phase:

| Surface | Version |
|---------|---------|
| DEV plugin | `figmachat7g-dashboard-metrics1` |
| DEV Hub PWA | `1.0.0+101 / figma-phase7g-dashboard-copy-rtl1` |
| Production plugin | `figmachat7g-dashboard-metrics1` (Phase 7G **already deployed**) |
| Production PWA | `1.0.0+101 / figma-phase7g-dashboard-copy-rtl1-prod` |
| Gateway | `gateway-2.7-prod16` / `mcp-atlassian-prod` StartedAt `2026-07-31T23:35:19Z` |

## 1. Dashboard follow-up count

| Item | Detail |
|------|--------|
| Current | `openWorkCounts.followup` = `reporter = currentUser() AND statusCategory != Done` (57 for `m.nasiri` on DEV). `tasks.followup` uses the same reporter list via `JimMobileIssues`. |
| Files | `CorbitMobileDashboardResource.openWorkCounts`, `JimMobileIssues.buildQuery` `followup` |
| Root cause | Follow-up was defined as “issues I reported”, not “issues I watch that are not mine”. |
| Fix | Plugin backend. Unify dashboard count + list on watcher JQL. |
| Risk | Medium — count will drop sharply (DEV 57 → 2 for `m.nasiri`). Correct, not a fake. |
| Plan | `watcher = currentUser() AND statusCategory != Done AND (assignee is EMPTY OR assignee != currentUser()) AND (reporter is EMPTY OR reporter != currentUser())` |
| Verify | DEV JQL already parses. Null-safe and simple forms both return **2** (`WEO-2`, `TEST-25`). Watching is enabled. |

Watcher JQL is available on DEV. Do not fall back to reporter counts.

## 2. Tasks tab follow-up filter

| Item | Detail |
|------|--------|
| Current | `GET /issues/search?filter=followup` → `reporterIsCurrentUser() AND unresolved()`. |
| Files | `JimMobileIssues`, `CorbitMobileIssueResource.search`, mobile `IssueListView` / `searchIssues` |
| Root cause | Same reporter definition. Mobile already sends the `followup` token. |
| Fix | Plugin backend (JQL). Mobile empty-state copy only. |
| Risk | Low once JQL is shared with the dashboard count. |
| Plan | Same watcher JQL + `ORDER BY updated DESC`. Exclude Done via `statusCategory != Done` (not only `resolution is EMPTY`). |
| Verify | List keys must match the dashboard count. Assigned/reported issues must not appear even if watched. |

## 3. Mobile chat multi-select

| Item | Detail |
|------|--------|
| Current | Toolbar: cancel, copy, forward (**exactly one** message), save, delete-for-me. Forward of N messages shows `forwardOneAtATime`. |
| Files | `conversation_screen.dart` `_bulkForward` / `_selectionMode` toolbar. Backend: per-message `POST .../messages/{id}/forward`. |
| Root cause | UI limited forward to one message. Backend already supports sequential forwards. Web already forwards a chronological selection. |
| Fix | Mobile-only. Loop forwardable selected messages in `createdAt`/`id` order to the existing destination picker. Add share of selected text via `AttachmentChannel.shareText`. |
| Risk | Low. Partial failure must be visible. |
| Plan | Enable Forward whenever ≥1 forwardable message is selected. Preserve order. Skip deleted/hidden. Persian success/partial/fail. |
| Verify | Select 2+ messages → Forward → picker → both arrive in order. |

## 4. Short-press reaction

| Item | Detail |
|------|--------|
| Current | Text bubble has `onLongPress` only. Tap on image/file already opens content. |
| Files | `_MessageBubble` `GestureDetector`, `FigmaReactionTray`, `toggleReaction` |
| Root cause | No short-press handler on the bubble. |
| Fix | Mobile-only. Tap bubble (not attachment/link/issue card) opens reaction tray. Long-press keeps full overlay. |
| Risk | Medium gesture conflict — do not steal attachment taps or selection-mode taps. |
| Plan | `onTap` → tray-only overlay using existing `toggleReaction` + full emoji picker `onMore`. |
| Verify | Tap text → tray. Long-press → overlay. Tap file/image → open, not react. |

## 5. File open/download persistence

| Item | Detail |
|------|--------|
| Current | `ensureAttachmentLocal` downloads via authenticated Dio to `Media/<cat>/att_<id>_<name>` or cache fallback, then `AttachmentChannel.openFile`. PWA opens a cookie-auth tab. |
| Files | `attachment_view.dart`, `api_client.downloadTo`, `AttachmentChannel` |
| Root cause | Filename sanitization is only `[/\\]`. No post-download size check. Failures surface as a generic snackbar with no retry. `mediaDir` null falls back to cache; a failed write looks like “nothing opened”. |
| Fix | Mobile. Harden sanitize (no `..`, control chars, length cap). Verify non-zero file. Progress + Persian retry. Reuse valid local file. |
| Risk | Medium on Android MIME/open; PWA cannot persist like a native app — keep browser download. |
| Plan | Same cache as auto-download. Do not log URLs/tokens. |
| Verify | Tap file → bytes on disk > 0 → opener. Second tap reuses file. |

## 6. Offline mode MVP

| Item | Detail |
|------|--------|
| Current | No conversation/message DB. Drafts are in-memory. Media files persist on Android only. |
| Files | `chat_providers.dart`, `secure_store.dart` |
| Root cause | Architecture is online-only. |
| Fix | Mobile local cache scoped by `server + user`. Persist last conversation list + last loaded messages (raw API JSON, no tokens). On network error, show cache + Persian offline banner. Disable send/react/forward/delete. On reconnect, refresh. On logout, clear that user’s cache. |
| Risk | High if over-built. Keep read-only. Do not fake server data. |
| Plan | File cache on native (`cacheDir/offline/<scope>`). Web: `localStorage` with size cap, or skip persist if unavailable. |
| Verify | Load chat online → disable network → list + messages still visible; composer blocked; downloaded files still open. |

## 7. Auto-download settings

| Item | Detail |
|------|--------|
| Current | Images prefetch on display. Files/voice download on tap. No user setting. |
| Files | `settings_screen.dart`, `attachment_view.dart` |
| Root cause | No preference or policy hook. |
| Fix | Mobile + local storage. Settings: images / videos / files / audio (+ optional Wi-Fi only). Defaults: images on, large types off. Use the same local file cache. Size cap. |
| Risk | Low. Wi-Fi detection may be conservative (skip auto-download when Wi-Fi-only is on and network type is unknown). |
| Plan | Persist in `SecureStore` (non-secret). After a successful online thread load, prefetch allowed attachments. |
| Verify | Toggle persists after restart. Manual open still works when auto is off. |

## 8. Awaiting / Rejected filters

| Item | Detail |
|------|--------|
| Current | `awaiting`: `cf[status] = "Pending" AND cf[approver] = currentUser() AND resolution is EMPTY`. `rejected`: `cf[status] = "Rejected" AND reporter = currentUser()` (no Done exclusion). DEV fields resolve to `customfield_10500` / `customfield_10501` (not hardcoded prod ids). Options are English `Pending`/`Approved`/`Rejected`. |
| Files | `JimMobileIssues.buildApprovalQuery`, `JimScheduleApprovalConfig`, `JimApprovalFields` |
| Root cause | Filters are role-narrow (approver-only / reporter-only). A user who submitted or is assignee but is not the approver sees an empty awaiting list. Rejected is empty on DEV because **no issue has status Rejected** (honest). Mobile already parses `issues`. |
| Fix | Plugin backend JQL. Mobile honest empty copy. Do not invent statuses. |
| Risk | Medium if option labels were Persian-only — DEV options are English and OK. |
| Plan | Pending: status Pending AND `statusCategory != Done` AND (approver OR assignee OR reporter = currentUser()). Rejected: status Rejected AND `statusCategory != Done` AND (approver OR assignee OR reporter). Reuse resolver JQL clauses — never hardcode user names or DEV/prod field ids. |
| Verify | DEV current awaiting: TEST-27 (Pending, `m.nasiri` is approver+assignee+reporter). Widened query still 1. Rejected widened query still 0 (no Rejected issues exist). |

## What must not change

Assigned / today / overdue JQL, `reportSummary`, board totals/favorites, report submission/reply/calendar, Workspace Agent, Gateway, Production.
