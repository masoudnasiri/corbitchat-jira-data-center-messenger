# Phase 7B — Figma vs DEV visual evidence

Design lock: `docs/phase7b-design-lock-report.md`  
DEV PWA captured: `1.0.0+94 / figma-phase7b-design-lock1` (Hub)  
Do **not** claim pixel-perfect. Headless Chrome cannot long-press a Flutter canvas or open chat-info without a saved session. Overlay / gallery pixels must be judged on-device after a hard refresh.

## Folder

| Path | What |
|------|------|
| `figma-ref/07-pv-chat-more.png` | Figma `83:9983` overlay + tray |
| `figma-ref/08-profile-attachments.png` | Figma `92:10407` attachments |
| `figma-ref/10-profile-media.png` | Figma `92:10658` media |
| `figma-ref/12-profile-links.png` | Figma `102:11596` links |
| `dev-after/00-pwa-shell.png` | Live DEV Hub login after +94 swap (412×917) |
| `dev-after/00-pwa-login.png` | First-paint blank (discard; Flutter not ready) |

No +93 “before” captures of these exact frames were stored. Baseline is the accepted `+93` behavior, not a screenshot pair.

---

## 1. Message action overlay + reaction tray

| | Figma `83:9983` / `84:10312` / `83:10193` | DEV +94 implementation |
|--|-------------------------------------------|-------------------------|
| Overlay dim | `rgba(15,23,42,0.48)` | `HubFigmaTokens.overlay` = `#0F172A` @ 48% |
| Menu width | 256 | 256 |
| Menu radius | 14 | 14 |
| Menu shadow | elevation-3 `0 8px 24px rgba(15,23,42,.12)` | same |
| Primary rows | پاسخ، کپی، اشتراک‌گذاری، ارسال به، حذف برای من (red) | same labels; extras **below a divider** |
| Tray | 🌱 👍 👎 😃 🙏, white pill, downward notch | same five + **`+` full picker** (required preserve) |
| Icon side | RTL: icon on the right | `Row` first child = icon (RTL start) |

**Remaining (acceptable for this slice, not pixel-identical):**

- Extra product rows (Saved Messages, pin, edit, attachment open/save/share, select, info) make the card taller than 190px.
- `+` slot is not in the Figma tray.
- Material icons stand in for Nucleo instances (SVGs were not downloaded).
- Global theme primary is still `#01BA9E`; this chrome uses `#0D6E6E` locally.

**Verdict:** chrome locked to the measured tokens. Not pixel-perfect vs the 5-row-only mock.

---

## 2. Shared gallery / chat profile

| | Figma `8/10/12` | DEV +94 |
|--|-----------------|---------|
| Back | برگشت + arrow | `s.back` = برگشت |
| Tabs | one grey track; selected white + teal outline | same |
| Labels | پیوست‌ها / مدیا / لینک‌ها | same |
| Count | grey pill, honest | badge only when the list is complete (no fake totals) |
| Files | one card + dividers, download | same; Jalali date + size from API |
| Media | 3-col ~2px gutters | unchanged 3×2px grid + `باز کردن پیام` |
| Links | OG thumb + lorem title | **URL + real title only** — no fake OG |
| Job title | پروداکت دیزاین | **omitted** — API gap |
| Bottom nav | shown on frame | hidden on push (DESIGN-SYSTEM hide-on-push) |
| Selection mode | frames 9/11/13 | **deferred** |

**Verdict:** tab/list chrome matches the lock. Profile data and OG cards are intentionally incomplete vs Figma. Not pixel-perfect.

---

## 3. Measured tokens used (DESIGN-SYSTEM.md)

| Token | Value | Where |
|-------|-------|-------|
| primary | `#0D6E6E` | selected tab text/outline |
| overlay | `rgba(15,23,42,0.48)` | long-press dim |
| error | `#C62828` | حذف برای من |
| elevated | `#FFFFFF` | menu, tray, file card |
| surface bg | `#F5F6F8` | tab track |
| text primary / secondary | `#1A2332` / `#5A6578` | rows, meta |
| radius lg / pill | 14 / 9999 | menu / tray / track |
| menu width | 256 | action card |
| screen pad | 16 | gallery |

Not used (unsafe rebase): changing `CorbitChatTheme.primary` from `#01BA9E` app-wide.

---

## 4. On-device check (owner)

After hard-refresh of DEV Hub PWA (`Ctrl+Shift+R` / clear site data):

1. About shows `1.0.0 (94)` and `figma-phase7b-design-lock1`.
2. Long-press a bubble: five Figma emojis + notch + 256 menu; `+` still opens the full picker.
3. Header → اطلاعات گفتگو: segmented پیوست‌ها / مدیا / لینک‌ها; no job title; no invented link previews.
4. Image viewer, search jump, `باز کردن پیام` still work.
