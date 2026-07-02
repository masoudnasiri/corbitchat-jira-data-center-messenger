# RTL, i18n & Localization

## Default Locale

- **Default**: Persian (`fa-IR`), RTL
- **Supported**: English (`en-US`), LTR
- Language switch in Settings; persisted via `/mobile/bootstrap` and `JimMobilePreference`

---

## Layout Direction

Set on root element:

```html
<!-- Persian (default) -->
<html lang="fa" dir="rtl">

<!-- English -->
<html lang="en" dir="ltr">
```

Flutter:

```dart
MaterialApp(
  locale: Locale('fa', 'IR'),
  supportedLocales: [Locale('fa', 'IR'), Locale('en', 'US')],
  localizationsDelegates: [...],
  builder: (context, child) {
    return Directionality(
      textDirection: _isRtl(context) ? TextDirection.rtl : TextDirection.ltr,
      child: child!,
    );
  },
)
```

---

## CSS Logical Properties

Always use logical properties instead of physical left/right:

| Avoid | Use |
|-------|-----|
| `margin-left` | `margin-inline-start` |
| `padding-right` | `padding-inline-end` |
| `text-align: left` | `text-align: start` |
| `border-left` | `border-inline-start` |
| `left: 0` | `inset-inline-start: 0` |

Issue card highlight accent uses `border-inline-start`.

Chat bubble tails use `border-end-end-radius` / `border-end-start-radius`.

---

## Icon Mirroring

Mirror these icons in RTL:

- Back / forward chevrons
- Navigation drawer open
- Reply arrow
- External link (optional)

Do **not** mirror:

- Play/pause, checkmarks, numbers
- Symmetric icons (search, close, add)
- Priority/status icons

---

## Typography per Locale

| Locale | Font | Notes |
|--------|------|-------|
| fa-IR | Vazirmatn | Full Persian glyph support |
| en-US | Inter | Latin UI |

Switch `font-family` on `html[lang]` — see `corbitchat.css`.

Mixed content (Persian message with English issue key) uses Unicode bidi algorithm; wrap issue keys in `<span dir="ltr">PROJ-123</span>` when embedded in RTL paragraphs.

---

## Date & Number Formatting

Controlled by user language + settings from bootstrap:

| Setting | Persian | English |
|---------|---------|---------|
| Calendar | Jalali (default) or Gregorian | Gregorian |
| Date format | ۱۴۰۴/۰۴/۱۰ or configurable | Jul 1, 2026 |
| Numbers | ۰۱۲۳… (optional) or 0123 | 0123 |
| Relative time | «۲ ساعت پیش» | «2 hours ago» |

Use ICU / `intl` package — never hard-code date strings.

---

## Copy & String Keys

All UI strings externalized:

```
dashboard.greeting          = "سلام، {name}" / "Hello, {name}"
dashboard.card.messages     = "پیام جدید" / "New messages"
dashboard.card.overdue      = "معوق" / "Overdue"
chat.tabs.all               = "همه" / "All"
nav.dashboard               = "داشبورد" / "Dashboard"
...
```

Plural rules:

```
issues.count = "{count} مورد" / "{count} items"
messages.unread = "{count} پیام خوانده‌نشده" / "{count} unread"
```

---

## Mention Tokens

Jira-compatible `[~username]` tokens:

- Render with `.cc-mention` styling
- Typeahead in composer shows avatars + display names
- RTL: mention chips flow naturally inside Persian text

---

## Form & Input

- Labels align to **start** edge
- Error messages below field, start-aligned
- Password/token fields: LTR text direction always (`dir="ltr"`) for PAT entry
- Jira URL input: LTR (`https://jira.company.com`)

---

## Chat Bubbles in RTL

Outgoing messages align to **inline-start** in RTL (visually left) — conventional for Persian chat apps where "self" is on the left.

Incoming messages align to **inline-end**.

Read receipts and timestamps on the trailing edge of each bubble.

---

## Testing Checklist

- [ ] All 5 main tabs render correctly in RTL and LTR
- [ ] Issue detail field order mirrors appropriately
- [ ] Board list view section headers align start
- [ ] Bottom sheet actions ordered start-to-end
- [ ] PAT/URL inputs remain LTR in RTL app
- [ ] Persian dates display on dashboard cards
- [ ] Mixed RTL/LTR comment threads render without overflow
- [ ] Push notification deep links open correct screen in both locales

---

## Accessibility + RTL

- Screen readers: set `lang` attribute on locale switch
- Focus order follows visual order in both directions
- Touch targets remain 44px minimum regardless of script width
