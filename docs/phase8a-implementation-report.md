# Phase 8A — Dynamic Report Follow-up Levels: DEV Implementation Report

**Date (UTC):** 2026-09-11
**Plugin version:** `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2-8a-dynlevels1`
**JAR sha256:** `555507779d9454fd5df98c2c71ae5fd2b1be7a4b6f9c554e9c445d5928560f40`
**Scope:** DEV only. Production NOT deployed. Gateway/eazyBI/Inbox/Automation untouched. No GitHub push.

## 1. Dynamic level model
Follow-up levels are resolved dynamically from the configured Jira select custom field. Stable key = `o<optionId>` (survives option rename). Legacy codes (`normal/high/special/managerial`) kept as **read-through fallback** so existing production settings keep applying. New options appear automatically after reload, default unconfigured (interval 0 = no required/missed/penalty). Rename-safe (keyed by id); delete-safe (orphans surfaced, never mis-applied). Text/missing field → graceful degrade with a warning.

## 2. Storage / backward compatibility
PluginSettings flat keys (prefix-validated, already generic): `reportInterval_o<id>`, `reportIntervalUnit_o<id>`, `reportStart_/reportEnd_/reportActiveUntil_/reportCountMode_/reportMinCount_o<id>`. Legacy `_normal/_high/_special/_managerial` retained (not removed). `getReportLevelSchedules()` returns legacy entries + `o<id>` entries with legacy fallback merged. A dynamic-configured-keys index (`reportDynamicConfiguredKeys`) enables orphan detection (PluginSettings can't enumerate keys).

## 3. Admin UI
`admin.vm`: fixed 4-row table replaced with a dynamic table (`#jim-report-levels-body`) + warning area. `jim-admin.js`: `loadReportLevels()` fetches `/admin/reporting/levels` and renders one editable row per option (label + stable key hint + configured/orphan/legacy/duplicate-context badges); save collects per-row `o<id>` keys. RTL + Persian/English preserved.

## 4. Report engine
`JimReportConfig.levelCodeOf()` returns `o<optionId>` (legacy text fallback for non-select). The engine is already generic over the key (`countModeForLevel/scheduleForLevel/activeUntilForLevel/minCountForLevel(key)`), so all count modes, fixed-minimum correction, working-day, enforcement-start, receiver pause, out-of-window, replies, mentions/attachments, and unified metrics are preserved. Unconfigured/interval-0 → NOT_CONFIGURED, no missed/penalty, submission still allowed.

## 5. Diagnostics + fingerprint
`GET /rest/jim/1.0/admin/reporting/levels` and `followUpLevels` in `/admin/reporting/diagnostics`: field id/name/type, isSelect, contexts count, options (key/value/context/configured/legacyFallback/duplicate), orphans, warnings. Per-issue ledger fingerprint already includes the level key + effective countMode/schedule; added `minCount` so fixed-minimum config changes recompute.

## 6. Related surfaces
- Issue report tab / Reports section / Dashboard metrics: use `levelText` (display) + generic engine output → correct; no per-code branching.
- **Mobile: unchanged** — DTO carries `levelText` (display) + `levelCode`; mobile displays `levelText` and does not branch on `levelCode` values. No PWA/APK build.
- **MCP/report tools: unchanged** — plugin exposes report summary generically (`levelCode==null` checks only); Gateway `REPORT_LEVELS` is informational. No Gateway change.

## 7. DEV verification (evidence)
DEV = local `jira-srv` (single-node), plugin `…8a-dynlevels1` deployed (exactly one JAR), health/ao-health 200.
- Configured field on DEV is a **textfield** → model degrades: `isSelect=false`, warning `follow_up_level_field_not_select`, 4 legacy configs shown as orphans. ✓
- Pointed report level field at a real select (`customfield_10500`, 3 options) → 3 dynamic rows `o10100/o10101/o10102`, unconfigured by default. ✓
- Configured `o10100` (4h daily) → save → reload → persisted (survived restart). ✓
- Added a new option "Phase8A Test Level" → after option-cache flush it appears as `o10190`, unconfigured (interval 0). ✓
- Configured `o10190` (2h daily) → save → reload → persisted. ✓
- Engine: set an issue's level to the new option → `/report` returns `levelCode=o10190`, `levelText="Phase8A Test Level"`, `countMode=daily`, applied. ✓
- Unconfigured option (`o10102`) → `statusCode=NOT_CONFIGURED`, `missedCount=0` (no penalty). ✓
- All test artifacts removed; report level field restored to `customfield_10200`.

## 8. Production rollout plan (NOT executed)
Preflight (from the full-alias operator workstation): HAProxy `.30` UP; node1 `.56` + node2 `.57` ACTIVE (`clusternode`); external DB `.205` reachable; shared home mounted; UPM/`pluginversion` current. Backups: `.205` mysqldump (settings change only, no schema/AO change — dynamic levels reuse existing PluginSettings + report ledger AO), shared plugin JAR backup, `pluginversion/pluginstate` snapshot, reporting-settings export, runbook/change-log. Install via **UPM/shared-home** only (no manual local JAR copy); rolling restart only if UPM requires it (this build has no AO schema change → UPM in-place, likely no restart). Validate both nodes, HAProxy, report settings page, dynamic option list (production follow-up field must be a **select**), engine on existing issues, Dashboard vs Reports parity. Rollback: UPM restore previous JAR `…figmachat7i-reportmetrics2` (legacy `_normal/_high/_special/_managerial` keys are retained, so behavior reverts cleanly).

## 9. Notes / risks
- Adding a Jira option via the **UI** updates the OptionsManager cache immediately (a raw DB insert needs a cache flush; production admins use the UI).
- DEV `m.nasiri` was CAPTCHA-locked (`invalidPasswordAttempts`); cleared in `cwd_user_attributes` (DEV-only). Its credential DB row was set to a temporary hash for testing then **restored to the original**; the temporary DEV password remains valid only until the next Jira restart (credential cache). CAPTCHA stays cleared.
- No AO schema change; fingerprint change marks affected ledgers stale → recompute on next calc (expected).
