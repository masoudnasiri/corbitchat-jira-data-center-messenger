# Foundation

## Theming (light & dark)

Colors are consumed only through semantic CSS variables (`--cc-color-*`). Dark mode reassigns those variables under `html[data-theme="dark"]` and `@media (prefers-color-scheme: dark)`, so components never need theme-specific code.

- **Pin a theme**: `data-theme="light"` or `data-theme="dark"`.
- **Follow OS**: omit `data-theme`.
- **Flutter**: `theme: CorbitChatTheme.light(...)`, `darkTheme: CorbitChatTheme.dark(...)`.

Hex values below are the **light** theme. Dark equivalents live in `tokens.json → color.dark` and the `:root[data-theme="dark"]` block in `css/corbitchat.css`.

## Color System

### Brand

| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| `brand.primary` | `#0D6E6E` | `#2CB5B5` | Primary buttons, active nav, links, outgoing chat bubbles |
| `brand.primaryMuted` | `#E6F4F4` | `#12312F` | Selected tabs, subtle highlights |
| `brand.secondary` | `#3D4F7C` | `#97A6CC` | Section headers, mentions, project metadata |
| `brand.accent` | `#E07A2F` | `#F0965A` | Jira Assistant, unread counts, activity highlights |
| `brand.workflow` | `#0052CC` | `#4C9AFF` | Jira transition button, issue-type icon, links |

### Surfaces

| Token | Hex | Usage |
|-------|-----|-------|
| `surface.background` | `#F5F6F8` | App background, list backgrounds |
| `surface.backgroundElevated` | `#FFFFFF` | Cards, top bar, bottom nav, modals |
| `surface.border` | `#D8DCE4` | Input borders, card outlines |
| `surface.divider` | `#E8EBF0` | List separators |

### Text

| Token | Light | Contrast on white | Dark |
|-------|-------|-------------------|------|
| `text.primary` | `#1A2332` | ~14:1 ✓ | `#E7EBF0` |
| `text.secondary` | `#55627A` | ~5.9:1 ✓ | `#A6B0BD` |
| `text.tertiary` | `#6E7A8C` | ~4.5:1 ✓ (AA for ≥13px) | `#7E8A99` |
| `text.disabled` | `#A9B1BD` | decorative only | `#515C6B` |

### Semantic

| Role | Foreground | Background | Usage |
|------|------------|------------|-------|
| Success | `#1F8A4C` | `#E8F6EE` | Done status, online presence |
| Warning | `#C47A00` | `#FFF4E0` | Due-soon, away presence |
| Error | `#C62828` | `#FDECEC` | Overdue, blocked, validation errors |
| Info | `#1565C0` | `#E8F0FA` | In Progress, informational banners |

### Status Categories (Jira)

Maps to server `field-display-rules` API. Mobile uses these as defaults when no rule is returned.

| Category | Badge class | Color |
|----------|-------------|-------|
| To Do | `cc-badge--todo` | Gray |
| In Progress | `cc-badge--in-progress` | Blue |
| Done | `cc-badge--done` | Green |
| Blocked | `cc-badge--blocked` | Red |

### Priority

| Level | Badge class |
|-------|-------------|
| Highest | `cc-badge--highest` |
| High | `cc-badge--high` |
| Medium | `cc-badge--medium` |
| Low / Lowest | `cc-badge--low` |

### Dashboard Card Colors

Each dashboard card has a dedicated tint for quick scanning:

| Card | Tint | Semantic |
|------|------|----------|
| New messages | Teal | Unread conversations |
| Today's tasks | Blue | Due today |
| Overdue | Red | Warning — sort by duration |
| Follow-up | Purple | Watching / mentioned |
| Colleagues | Cyan | Active team members |
| Reports / Assistant | Orange | Activity feed |

---

## Typography

### Font Families

| Locale | Family | Weights |
|--------|--------|---------|
| Persian (`fa-IR`) | Vazirmatn | 400, 500, 600, 700 |
| English (`en-US`) | Inter | 400, 500, 600, 700 |
| Issue keys, code | JetBrains Mono | 400, 500 |

### Scale

| Token | Size | Line height | Usage |
|-------|------|-------------|-------|
| `xs` | 11px | 16px | Badges, timestamps, tab labels |
| `sm` | 13px | 18px | Secondary text, list subtitles |
| `base` | 15px | 22px | Body, chat messages, issue summary |
| `md` | 17px | 24px | Screen titles, section headers |
| `lg` | 20px | 28px | Dashboard greeting |
| `xl` | 24px | 32px | Large counts on dashboard cards |

### Weights

- **Regular (400)**: Body text, descriptions
- **Medium (500)**: Issue summaries, list titles, buttons
- **Semibold (600)**: Screen titles, section labels
- **Bold (700)**: Dashboard counts, emphasis

### Text Scaling

Support system font scaling up to 200%. At 150%+:
- Issue card summary may expand to 3 lines
- Bottom nav labels may hide (icon-only mode at 180%+)

---

## Spacing

Base unit: **4px**

| Token | Value | Usage |
|-------|-------|-------|
| `1` | 4px | Tight gaps (badge padding) |
| `2` | 8px | Inline icon gaps |
| `3` | 12px | List item internal padding |
| `4` | 16px | Screen padding, card padding |
| `6` | 24px | Section spacing |
| `touchMin` | 44px | Minimum interactive target (WCAG) |
| `bottomNavHeight` | 56px | Fixed bottom navigation |
| `topBarHeight` | 56px | App bar |

---

## Radius

| Token | Value | Usage |
|-------|-------|-------|
| `sm` | 6px | Badges, small chips |
| `md` | 10px | Buttons, inputs |
| `lg` | 14px | Cards, dashboard tiles |
| `xl` | 20px | Bottom sheets, modals |
| `bubble` | 16px | Chat message bubbles |
| `full` | 9999px | Avatars, pill tabs |

---

## Elevation

| Level | Shadow | Usage |
|-------|--------|-------|
| 0 | none | Flat lists |
| 1 | subtle | Cards on dashboard |
| 2 | medium | Floating action button, dropdowns |
| 3 | strong | Modals, bottom sheets |

---

## Motion

| Duration | Value | Usage |
|----------|-------|-------|
| Fast | 150ms | Button press, tab switch |
| Normal | 250ms | Screen transitions, expand/collapse |
| Slow | 400ms | Bottom sheet enter |

Respect `prefers-reduced-motion`: disable non-essential animations.

---

## Iconography

Use a consistent 24px stroke icon set (e.g. Material Symbols Outlined or Phosphor).

| Context | Size |
|---------|------|
| Bottom nav | 24px |
| List leading | 20px |
| Inline action | 16px |
| Empty state | 32px |

Icons must mirror horizontally in RTL when directional (back arrow, chevrons).
