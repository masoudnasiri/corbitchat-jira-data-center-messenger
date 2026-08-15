# Phase 7G corrective 2 — report wording + RTL chevrons (DEV)

Mobile-only relative to the 7G plugin. Plugin stays
`figmachat7g-dashboard-metrics1`. Production later received 7G TECH
PWA `+101`. Gateway / Workspace Agent were not changed. See
`docs/phase7g-production-rollout.md`.

## Reports card wording

Backend `reportSummary` was already report-engine only. The remaining
issue was **copy**: donut labels `تکمیل‌شده` / `جاری` / `عقب‌افتاده`
read as task status.

Final Persian labels:

| Segment | Field | Label |
|---------|-------|-------|
| Title | — | `گزارش‌ها` |
| Subtitle | — | `وضعیت گزارش‌های من` |
| Complete | `complete` / `todayComplete` | `گزارش‌های تکمیل‌شده` |
| On track | `onTrack` / `todayOnTrack` | `گزارش‌های در جریان` |
| Overdue | `overdue` / `todayOverdue` | `گزارش‌های عقب‌افتاده` |

Empty: `گزارشی برای نمایش وجود ندارد`. Unavailable unchanged.
The card still does not read `statusCounts` / `tasks.*` / `openWorkCounts`.

## Top Dashboard numbers (unchanged formulas)

Task chips were reworded so they cannot be read as report metrics:

- `تسک‌های عقب‌افتاده` / `سررسید امروز` / `پیگیری من`
- Headline stays `کارهای باز من` / `N کار فعال`

## RTL chevrons

Root cause: Material `Icons.chevron_left` has `matchTextDirection: true`,
so a hardcoded left chevron **flips to point right in RTL**.

Fix: reusable `ForwardChevron` (physical left in RTL, right in LTR,
`matchTextDirection: false`). Applied to More rows, profile header,
board cards, projects, report metric tiles, new-chat rows, reviewer
rows. Back / send / reply / share / download / logout were not flipped.
