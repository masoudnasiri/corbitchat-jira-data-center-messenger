# CorbitChat Design System

Mobile-first design system for the **CorbitChat Mobile Application for Jira Data Center**. **Tokens v1.1.0.**

This is an independent CorbitChat visual identity — not a copy of Jira or reference screenshots from the product spec. It is optimized for enterprise chat, Jira workflows, and bilingual Persian/English use, with full **light + dark** theming.

## Principles

| Principle | Implementation |
|-----------|----------------|
| **RTL-first** | Persian (`fa-IR`) is default; all layouts use logical properties (`inline-start`, `margin-inline`, etc.) |
| **Mobile-first** | 44px minimum touch targets, bottom navigation, dense but readable cards |
| **Themeable** | Every component reads semantic `--cc-*` tokens; light/dark switch via one attribute |
| **Server-driven UI** | Status/priority colors follow plugin field-display rules; tokens provide fallbacks |
| **Complete states** | Every screen supports loading, empty, error, no-permission, and retry |
| **Accessibility** | WCAG AA contrast, `:focus-visible` rings, `prefers-reduced-motion`, scalable text |

## Structure

```
design system/
├── tokens/tokens.json      # Source-of-truth design tokens
├── css/
│   ├── corbitchat.css      # CSS custom properties + typography
│   ├── components.css      # Component class library
│   ├── task-view.css       # Task view + board styles
│   └── modules.css         # Assistant, chat list, group chat, projects, gallery
├── flutter/
│   └── corbitchat_theme.dart   # Flutter ThemeData (recommended stack)
├── docs/
│   ├── foundation.md       # Colors, type, spacing, elevation
│   ├── components.md       # Component specifications
│   ├── task-view.md        # Issue detail fields & customfield_10903
│   ├── board-view.md       # Trello-style Kanban board
│   ├── modules.md          # Jira Assistant, group chat, projects, board gallery
│   ├── layout.md           # Navigation, screen structure
│   ├── rtl-i18n.md         # RTL/LTR and localization
│   └── states.md           # Loading, empty, error patterns
├── tokens/customfield_10903.json  # Task type field color mapping
├── css/task-view.css       # Task view + board styles
└── preview/index.html      # Interactive visual preview
```

## Quick Start

### Web / CSS

```html
<!-- lang controls font + direction; data-theme controls light/dark -->
<html lang="fa" dir="rtl" data-theme="light">
  <link rel="stylesheet" href="css/corbitchat.css" />
  <link rel="stylesheet" href="css/components.css" />
  <link rel="stylesheet" href="css/task-view.css" />
</html>
```

- Omit `data-theme` to follow the OS (`prefers-color-scheme`).
- Set `data-theme="dark"` / `"light"` to pin a theme.
- Switch language with `lang="en" dir="ltr"`.

### Flutter (recommended in spec)

```dart
MaterialApp(
  theme: CorbitChatTheme.light(locale: 'fa-IR'),
  darkTheme: CorbitChatTheme.dark(locale: 'fa-IR'),
  themeMode: ThemeMode.system,
  locale: const Locale('fa', 'IR'),
  // ...
)
```

Add fonts to `pubspec.yaml`:

```yaml
fonts:
  - family: Vazirmatn
    fonts:
      - asset: assets/fonts/Vazirmatn-Regular.ttf
      - asset: assets/fonts/Vazirmatn-Medium.ttf
        weight: 500
      - asset: assets/fonts/Vazirmatn-SemiBold.ttf
        weight: 600
      - asset: assets/fonts/Vazirmatn-Bold.ttf
        weight: 700
  - family: Inter
    fonts:
      - asset: assets/fonts/Inter-Regular.ttf
      - asset: assets/fonts/Inter-Medium.ttf
        weight: 500
      - asset: assets/fonts/Inter-SemiBold.ttf
        weight: 600
```

## Brand

| Role | Light | Dark |
|------|-------|------|
| **Primary** (actions, nav, outgoing chat) | `#0D6E6E` | `#2CB5B5` |
| **Secondary** (headers, mentions) | `#3D4F7C` | `#97A6CC` |
| **Accent** (Assistant, unread badges) | `#E07A2F` | `#F0965A` |
| **Workflow** (Jira transition/link) | `#0052CC` | `#4C9AFF` |

## Theming

The system uses a two-layer token model:

1. **Semantic tokens** (`--cc-color-*`, `--cc-space-*`, `--cc-shadow-*`, …) that every component consumes.
2. **Theme overrides** for dark mode that reassign the semantic colors under `html[data-theme="dark"]` (and `@media (prefers-color-scheme: dark)`).

Because components never hardcode hex values, one attribute re-colors the entire UI. Custom-field pills (`نوع تسک`) auto-tint in dark mode via `color-mix`.

## Accessibility

- **Focus**: global `:focus-visible` ring (`--cc-focus-ring`) on all interactive elements.
- **Motion**: honours `prefers-reduced-motion` (shimmer, transitions, spinners collapse).
- **Contrast**: text tokens tuned for WCAG AA on both themes.
- **Targets**: 44px minimum touch targets.
- **States**: `:disabled`, `.cc-input--error` + `.cc-field-error`, `.cc-btn--loading`, `.cc-spinner`.
- **Helpers**: `.cc-visually-hidden` for screen-reader-only labels.

## Primary Navigation

Bottom tabs (fixed, 5 items):

1. **Dashboard** — operational home (messages, tasks, colleagues)
2. **Chat** — conversations (All, Personal, Groups, Projects, Assistant)
3. **Projects** — accessible Jira projects
4. **Tasks** — filtered issue lists (today, overdue, follow-up)
5. **More** — settings, colleagues, notifications, logout

## Preview

Open `preview/index.html` in a browser. Pages: **Dashboard**, **Components**, **Chats**, **Chat**, **Group**, **Assistant**, **Projects**, **Project**, **Gallery**, **Task View**, **Board**.

### Feature modules

Beyond the base components, the system covers the spec's key modules (see `docs/modules.md`):

- **Jira Assistant** — activity-feed bot with typed events (mention, assignment, status, reply, overdue) and quick actions (Open, Mark seen).
- **Group chat** — sender labels, system messages, typing indicator, member list with role badges (Access Policy aware).
- **Projects** — project list with stats, and project detail with Overview / Board / Issues / Chat / Members / Activity tabs.
- **Board Gallery** — searchable Scrum/Kanban board tiles with column thumbnails.

### Task View fields

Matches Jira issue layout: Details (Type, Priority, Labels, Resolution, custom fields), People (Assignee, Reporter), Description, Attachments.

**نوع تسک (طبق دستورالعمل)** (`customfield_10903`) uses colored pills — see `tokens/customfield_10903.json`.

### Board

Trello-style horizontal Kanban columns with scrollable cards, label bars, task type pills, due dates, and assignee avatars.

## Related Spec

Based on: `CorbitChat_Jira_DC_Mobile_App_Product_and_Technical_Spec_EN.pdf`
