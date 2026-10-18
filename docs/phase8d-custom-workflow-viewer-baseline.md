# Phase 8D — Custom Workflow Viewer: Live/Source Baseline

**Date (UTC):** 2026-10-06. No versions assumed from chat; read live.

## Plugin
| Item | Value |
|---|---|
| Source repo | `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane` |
| Branch / HEAD | `feature/mcp-control-plane` / `d098336` |
| Working tree | DIRTY (47) — prior reportmetrics2 / 8a / 8b / 8c-groupsend1 / chatlimit15000 work + docs (snapshot: `docs/phase8d-preimplementation-source-snapshot.patch`) |
| Current version (pom) | `1.0.0-…-8c-groupsend1-chatlimit15000-1` |
| DEV installed plugin | `…-8c-groupsend1-chatlimit15000-1` (matches source) |
| **Production plugin (read-only)** | UPM read unavailable this session; last ops-confirmed suffix `8c-groupsend1` (chatlimit15000 is staged, not yet prod) |
| 8C group-send source present | Yes (`JimGroupServiceImpl` `ADMINS_AND_ALLOWED`/`allowedSender`, etc.) |

## Mobile
| Item | Value |
|---|---|
| Source repo | `/root/jira-dev/corbitchat-mobile` |
| Branch / HEAD | `main` / `78a6eeb` |
| Working tree | DIRTY (16) — prior reportmetrics / 8c / chatlimit mobile work (snapshot: `docs/phase8d-preimplementation-mobile-snapshot.patch`) |
| pubspec | `1.0.0+114` |
| **Production PWA (read-only)** | build 114 / `figma-phase8c-groupsend1-prod` |
| Highest release build | 114 |
| **Next unused mobile build** | **115** |

## Phase 8D target versions
- Plugin: `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2-8a-dynlevels1-8b-dayunit1-8c-groupsend1-chatlimit15000-1-8d-wfview1` (short `-8d-wfview1` suffix due to length).
- Mobile: `1.0.0+115`; DEV Hub label `figma-phase8d-workflowview1`; 7G TECH label `figma-phase8d-workflowview1-prod`.

## Baseline composition note
The current DEV source tree accumulates prior in-place DEV work (reportmetrics2/8a/8b/8c/chatlimit15000); Production is behind at `8c-groupsend1` (chatlimit15000 staged separately). The 8D build layers the workflow viewer on top of the current DEV tree (a superset of prod) — consistent with prior phases. Production is NOT changed in this phase.
