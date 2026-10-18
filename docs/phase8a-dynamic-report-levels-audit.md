# Phase 8A — Dynamic Report Follow-up Levels: Current Implementation Audit

**Date (UTC):** 2026-09-11
**Baseline:** plugin `…report-calendar1-figmachat7i-reportmetrics2` (see `phase8a-live-baseline-discovery.md`).
**Rule:** No coding before this audit. Live source read from the canonical worktree `feature/mcp-control-plane`.

---

## 1. Where the four hard-coded levels live

| Layer | Location | What is hard-coded |
|---|---|---|
| Settings service | `JimAdminSettingsServiceImpl` line 54: `REPORT_LEVEL_CODES = Arrays.asList("normal","high","special","managerial")` | The 4 codes; iterated in `getReportLevelSchedules()` (≈368), `getAllSettings()` (≈407), and `updateSettings`/validation (≈850) |
| Config resolver | `JimReportConfig.codeForLevelText()` (≈337) | Persian labels عادی/بالا/ویژه/مدیریتی → `normal/high/special/managerial` |
| Config resolver | `JimReportConfig.levelLabelFa(code)` (≈356) | code → Persian label |
| Admin template | `src/main/resources/templates/admin.vm` (report schedule section) | Fixed 4-row level table |
| Admin JS | `src/main/resources/js/jim-admin.js` (`REPORT_LEVEL_CODES`, per-code load ≈281 / save ≈391) | 4-code load/save loop |
| Report engine | `JimReportServiceImpl` (≈106–112, 747–761, 886) | **Not** hard-coded per level — consumes a generic `code` string via config-map lookups |
| Gateway (OUT OF SCOPE) | `corbit-logic-mcp-gateway/src/domain.py` `REPORT_LEVELS` | Informational capabilities list only; not touched |

**Key architectural finding:** the report **engine is already generic** over a `code` string. `JimReportServiceImpl` calls `JimReportConfig.levelCodeOf(issue)` then `activeUntilForLevel(code)`, `countModeForLevel(code)`, `scheduleForLevel(code)`, `minCountForLevel(code)` — all map lookups keyed by the code. There is **no `switch(level)`** in the calculation path. So dynamic levels require making (a) the level list and (b) `levelCodeOf` dynamic; the engine itself needs no per-level branching change.

## 2. How the configured Follow-up level field is read
`JimReportConfig.resolveLevelField()` → `fieldOfType(getReportLevelFieldId(), "select","radiobuttons","textfield")` (type-guarded, graceful disable if missing/incompatible). Configured id is admin setting `reportLevelFieldId` (DEV/prod value observed historically `customfield_10200` on DEV).

## 3. How issue values are resolved today
`levelCodeOf(issue)`: `issue.getCustomFieldValue(cf)` → if `Option`, use `Option.getValue()` (text); else `String.valueOf(raw)` → `codeForLevelText(normalizePersian(text))` → one of the 4 codes or `null`.

## 4. Are Jira option IDs available (or only display text)?
**Option IDs are available.** The raw value is a `com.atlassian.jira.issue.customfields.option.Option`, which exposes `getOptionId()` (Long). For enumerating options without an issue: `OptionsManager.getOptions(cf.getRelevantConfig(issue))` (issue context, already used in `JimReportServiceImpl.writeText` ≈1196) and `cf.getConfigurationSchemes()` → `FieldConfigScheme`/`FieldConfig` → `OptionsManager.getOptions(fieldConfig)` (admin/no-issue context). Today `levelCodeOf` uses only the **text**, discarding the available option ID — this is the main gap to close.

## 5. How report level settings are stored
Flat **PluginSettings** keys (prefix `KEY_PREFIX`), per code:
`reportInterval_<code>`, `reportIntervalUnit_<code>`, `reportStart_<code>`, `reportEnd_<code>`, `reportActiveUntil_<code>`, `reportCountMode_<code>`, `reportMinCount_<code>`.
`getReportLevelSchedules()` returns `Map<code, Map<field,value>>`. **No AO table** for level config. Legacy keys are the 4 codes.

## 6. Code paths that assume exactly normal/high/special/managerial
- `REPORT_LEVEL_CODES` iterations (settings load/save/getAll/validation).
- `codeForLevelText` / `levelLabelFa` (label↔code).
- `admin.vm` fixed table + `jim-admin.js` fixed loop.

## 7. Code paths that must become dynamic
- Settings service level enumeration (union of: Jira options, legacy 4 codes, any persisted config keys).
- `levelCodeOf(issue)` → return a **stable option key** (option id based), with legacy text fallback.
- `levelLabelFa`/label resolution → derive from the option/config.
- Admin UI (template + JS) → dynamic rows from resolved options.
- Diagnostics → expose field/options/config state.

## 8. Code paths that can remain display-only (no change needed)
- `JimReportSummary.levelText` (raw issue label) and the issue report tab / reports section / dashboard metrics — they display `levelText` and consume engine output; unaffected by the key change.
- **Mobile:** report DTO carries `levelText` (display) + `levelCode`. Mobile shows the label; it does not configure levels. Provided we keep `levelText` populated and don't remove `levelCode`, **mobile is unchanged**. (Confirmed no mobile 4-level config UI exists.) → **Mobile unchanged — no PWA/APK build** (final confirmation during implementation).
- **MCP/report tools:** plugin exposes report summary via `/report/{key}`; the gateway `REPORT_LEVELS` is an informational capabilities list. No tool keys on the 4 codes for calculation. No Gateway change required.

## 9. Multiple field contexts
A Jira select CF can have multiple `FieldConfigScheme`s (contexts), each with its own option set (and option IDs are unique per option row). The dynamic model must enumerate all active contexts and de-duplicate/label by context to avoid display-name ambiguity → stable key `contextId:optionId` where contexts matter; `optionId` alone is globally unique in `customfieldoption`, so `o<optionId>` is a safe primary key with `contextId` shown for disambiguation.

## 10. Safe option retrieval
- With issue: `OptionsManager.getOptions(cf.getRelevantConfig(issue))`.
- Admin (no issue): iterate `cf.getConfigurationSchemes()` → for each `FieldConfigScheme.getOneAndOnlyConfig()`/configs → `OptionsManager.getOptions(fieldConfig)`; collect `optionId`, `value`, `disabled`, `contextName`. Guard every call (graceful disable if field missing / not a select).

---

## Chosen dynamic model (design)

- **Stable level key:** `o<optionId>` (globally-unique Jira option id). `contextId` retained for display disambiguation.
- **`levelCodeOf(issue)`** returns `o<optionId>` from `Option.getOptionId()`; falls back to legacy `codeForLevelText()` only when no option id is available (text field) — documented risk.
- **Settings service** enumerates levels dynamically = active Jira options (each → `levelKey=o<optionId>`, label, contextId, configured?) ∪ persisted orphan configs ∪ legacy 4 codes (shown only if still mappable). Reads **dynamic config first**, then **legacy fallback**: for an option whose normalized value maps to a legacy code (عادی→normal, …), if no `o<optionId>` config exists, fall back to `reportInterval_normal`… so **existing production settings keep applying** and are not overwritten. Legacy keys are **not removed** this release.
- **Persistence:** new keys `reportInterval_o<id>`, `reportIntervalUnit_o<id>`, `reportStart_o<id>`, `reportEnd_o<id>`, `reportActiveUntil_o<id>`, `reportCountMode_o<id>`, `reportMinCount_o<id>`. Legacy `_normal/_high/_special/_managerial` retained.
- **Unconfigured default:** interval 0 → `scheduleForLevel` already returns null → engine treats as not configured (no required/missed) — matches requirement 6.
- **Rename safe:** keyed by option id → label change keeps config. **Delete safe:** orphan config shown in diagnostics, never auto-applied to another option; settings/engine guard nulls.
- **Fingerprint:** include the resolved dynamic level config (keys+values) + the field id + option-id set in the report ledger fingerprint so recalculation notices changes (extends the existing `computeFingerprint`).
- **Diagnostics:** add field id/name/type, contexts count, options count, per-option key/value/context/configured, orphaned configs, legacy-fallback-used flags.

## Risk register
- Text-only follow-up field (no option id) → falls back to legacy label mapping (documented; only affects non-select configs).
- Multiple contexts with duplicate display names → mitigated by `o<optionId>` key + context label in UI.
- Legacy fallback must never auto-write dynamic keys (read-through only) to avoid overwriting production settings.
- Fingerprint change will mark existing ledgers stale → recomputation on next calc (expected; no data change, matches current settings-change behavior).
