# Phase 8B — Working-Day "days" Interval: Live/Source Baseline

**Date (UTC):** 2026-09-11
**Rule:** No version assumed from chat; values read live.

| Item | Value |
|---|---|
| Source repo | `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane` |
| Branch / HEAD | `feature/mcp-control-plane` / `d098336` |
| Working tree | DIRTY (22 files: expected reportmetrics2 + Phase 8A work; snapshot at `docs/phase8b-preimplementation-source-snapshot.patch`) |
| DEV installed plugin | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2-8a-dynlevels1` (matches source) |
| Phase 8A code present | Yes (`resolveLevelOptions`, `getReportLevelModel`, dynamic `o<optionId>` keys) |
| Source matches DEV | Yes |
| **Production plugin (read-only)** | **`…report-calendar1-figmachat7i-reportmetrics2-8a-dynlevels1`** (Phase 8A now live in prod — operator completed the earlier rollout) |
| Production PWA | build 113 / `figma-composer-darktext1-prod` |
| Mobile repo/pubspec | `1.0.0+113` |
| Mobile change needed? | Preliminary NO (interval unit is admin-only; not shown/parsed in mobile). Confirm in audit §10. |

**Chosen Phase 8B plugin version:** `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2-8a-dynlevels1-8b-dayunit1`.
Production is NOT touched in this phase (DEV-first).
