# Phase 7H — Production PWA +107

Deployed: 2026-08-15 after owner asked to deploy Production.
**PWA only.** Plugin, Gateway, Workspace Agent, and report/calendar
logic were **not** changed. Hub was **not** deployed. Jira was **not**
restarted.

## What shipped

7G TECH mobile PWA `1.0.0+107 / figma-task-detail-person-spacing2-prod`.

Includes DEV-accepted mobile work since +102:

- multi-select compact toolbar
- attachment open without session/password wipe
- Tasks filter edge padding
- task-card footer swap and row spacing
- task-detail person rows as compact `name + 16px + avatar`

## Preflight

| Check | Result |
|-------|--------|
| Production PWA before | `1.0.0+102 / figma-phase7h-functional1-prod` |
| Production plugin | `figmachat7h-functional1` (left installed) |
| Gateway | `mcp-atlassian-prod` started `2026-07-31T23:35:19Z` — left running |
| Target PWA | `/root/jira-dev/releases/corbitchat-mobile-pwa-7gtech-1.0.0+107-figma-task-detail-person-spacing2-prod.tgz` |
| PWA sha256 | `87492330e09e9c6a0815613bbf293867eaad808adf89171cc440d7c208dcaeb9` |
| Brand | title/manifest **7G TECH**, no user-visible Hub |
| Mobile source | `78a6eeb0eed3deb23ba5e8437a32395da68a7022` |

## Backup / rollback (`corbit-prod`, TS `20260815-174930`)

| Item | Path |
|------|------|
| Live +102 PWA | `/root/jira-backups/prod-mobile-102-before-pwa107-20260815-174930.tgz` (sha256 `2a976490ecaf4e6b5a081a11e58f027e1daae33ce1b49f92c909ff308499a6d5`) |
| Rollback script | `/root/jira-backups/rollback-pwa-107-20260815-174930/ROLLBACK-pwa.sh` → +102 |

Do **not** run a plugin or Gateway rollback for this swap.

## Verify

Live `https://jira.7gtech.net/mobile/version.json`:

`build_number` 107, `build_label` `figma-task-detail-person-spacing2-prod`, `brand` `sevengtech`

`/mobile/` **200**. Title + apple-title **7G TECH**. Hub title leak **0**.
Plugin jar count **1**. Gateway `StartedAt` unchanged.

Hard-refresh Production `/mobile/` (`Ctrl+Shift+R`).
