# UI States

Every screen in the CorbitChat app must implement these five states per the product spec.

---

## 1. Loading

**When**: Initial fetch, pagination, pull-to-refresh.

**Pattern**:
- First load: skeleton placeholders matching content layout
- Refresh: inline spinner in top bar or pull-to-refresh indicator
- Pagination: small spinner at list bottom

**Dashboard skeleton**: 4 card rectangles + 3 list row skeletons.

**Issue detail skeleton**: Header block + 4 field rows + 2 comment blocks.

**CSS**: `.cc-skeleton` with shimmer animation.

**Duration**: Show skeleton after 150ms delay (avoid flash on fast networks).

Respect `prefers-reduced-motion`: static gray blocks without shimmer.

---

## 2. Empty

**When**: No data exists (not an error).

| Screen | Icon | Title (fa) | Title (en) | Action |
|--------|------|------------|------------|--------|
| Chat list | message | گفتگویی وجود ندارد | No conversations | Start chat |
| Projects | folder | پروژه‌ای یافت نشد | No projects | — |
| Tasks (today) | checklist | taskی برای امروز ندارید | No tasks for today | — |
| Notifications | bell | اعلانی وجود ندارد | No notifications | — |
| Search | search | نتیجه‌ای یافت نشد | No results | Clear filters |
| Comments | comment | اولین نظر را بنویسید | Be the first to comment | — |

**Anatomy**: Centered icon (64px circle) + title + optional subtitle + primary action button.

**CSS**: `.cc-state`, `.cc-state__icon`, `.cc-state__title`, `.cc-state__message`

---

## 3. Error

**When**: Network failure, 5xx, timeout, unexpected response.

**Anatomy**:
- Error icon (red muted circle)
- Title: «خطا در بارگذاری» / «Failed to load»
- Message: Human-readable, non-technical when possible
- **Retry** button (primary)
- Optional **Go back** (ghost)

**Inline errors** (form): Red border on field + message below.

**Toast errors**: Short network failures (e.g. message send failed) with Retry action.

Do not expose stack traces or raw API errors to users.

---

## 4. No Permission

**When**: User lacks Jira browse/edit permission or chat access policy denies access.

**Distinct from empty** — data may exist but user cannot see it.

| Context | Message (fa) | Message (en) |
|---------|--------------|--------------|
| Issue | دسترسی به این مورد ندارید | You don't have access to this issue |
| Project | دسترسی به این پروژه ندارید | You don't have access to this project |
| Chat | عضو این گفتگو نیستید | You're not a member of this conversation |
| Comment | امکان ثبت نظر ندارید | You can't add comments |
| Transition | تغییر وضعیت مجاز نیست | Status change not allowed |

**Anatomy**: Lock icon + title + explanation + back navigation.

Hide action buttons that would fail (comment composer, transition menu).

---

## 5. Retry

Retry is the primary recovery action on error states.

**Behavior**:
- Exponential backoff for automatic retry (max 3 attempts) on bootstrap/dashboard
- Manual retry button always available
- Preserve user input on retry (draft message, comment text)

**Offline read cache** (v1.0): Show stale data with banner:

> «آفلاین — داده‌های ذخیره‌شده نمایش داده می‌شود»
> "Offline — showing cached data"

Banner uses `warningMuted` background; dismissible after reconnect.

---

## State Priority

When multiple conditions apply, show highest priority:

1. **Loading** (if actively fetching with no cached data)
2. **Error** (if fetch failed and no cache)
3. **No permission** (if 403 from API)
4. **Empty** (if 200 with zero items)
5. **Content**

---

## Pull to Refresh

Available on: Dashboard, Chat list, Projects, Issue list, Board.

Use platform-native refresh indicator tinted with `brand.primary`.

---

## Connection States (Login)

| State | UI |
|-------|-----|
| Idle | Connect button enabled |
| Testing | Spinner + «در حال اتصال…» |
| Success | Green check + redirect |
| Auth failed | Error message + retry |
| Cert error | Specific message for pinning/HTTPS issues |

---

## Message Send States

| State | Indicator |
|-------|-----------|
| Sending | Clock icon on bubble |
| Sent | Single check |
| Delivered | Double check (gray) |
| Read | Double check (primary) |
| Failed | Red ! + tap to retry |

---

## Sync Indicator (Dashboard)

Top bar trailing area:

| State | Icon |
|-------|------|
| Synced | None or subtle check |
| Syncing | Small spinner |
| Stale | Cloud-off icon + tap to sync |
| Error | Warning dot |

Show last sync time in profile/settings.
