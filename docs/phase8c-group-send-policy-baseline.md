# Phase 8C — Group Send Policy: Live/Source Baseline

**Date (UTC):** 2026-09-11. No versions assumed from chat; read live.

## Plugin
| Item | Value |
|---|---|
| Source repo | `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane` |
| Branch / HEAD | `feature/mcp-control-plane` / `d098336` |
| Working tree | DIRTY (28) — prior reportmetrics2 / 8a-dynlevels1 / 8b-dayunit1 work + docs (snapshot: `docs/phase8c-preimplementation-source-snapshot.patch`) |
| Current version | `1.0.0-…-8a-dynlevels1-8b-dayunit1` |
| DEV installed plugin | `…-8a-dynlevels1-8b-dayunit1` (matches source) |
| **Production plugin (read-only)** | `…-8a-dynlevels1-8b-dayunit1`, enabled=true (operator deployed 8B) |

## Mobile
| Item | Value |
|---|---|
| Source repo | `/root/jira-dev/corbitchat-mobile` |
| Branch / HEAD | `main` / `78a6eeb` |
| Working tree | DIRTY (13) — prior reportmetrics mobile work (snapshot: `docs/phase8c-preimplementation-mobile-snapshot.patch`) |
| pubspec | `1.0.0+113` |
| **Production PWA (read-only)** | build 113 / `figma-composer-darktext1-prod` (brand sevengtech) |
| Highest release build | 113 |
| **Next unused mobile build** | **114** |

## Phase 8C target versions
- Plugin: `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2-8a-dynlevels1-8b-dayunit1-8c-groupsend1`
- Mobile: `1.0.0+114`; DEV Hub label `figma-phase8c-groupsend1`; 7G TECH label `figma-phase8c-groupsend1-prod`

## Source vs DEV baseline
- Plugin source matches DEV installed (`…8b-dayunit1`). Mobile source `+113` matches prod PWA `+113` (no `+114` yet).

Production is NOT changed in Phase 8C (DEV-first).
