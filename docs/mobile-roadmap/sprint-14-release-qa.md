# Sprint 14 — Release & QA (Offline Cache, Accessibility, Store Submission)

**Phase:** 4 — Hardening · **Duration:** 2 weeks · **Goal:** ship v1.0 —
consolidate offline read-cache + drafts, finish accessibility + RTL/LTR
QA, complete documentation, and submit to the stores.

> Spec: PDF §1.3 (v1 offline scope), §8 (accessibility/localization),
> §9 Phase 4 + §9.1 (Definition of Done). Design: `docs/states.md`,
> `docs/rtl-i18n.md`, `docs/foundation.md` (accessibility).

## Objective

Reach a releasable v1.0: consistent offline read-cache and draft
preservation everywhere (not full offline write — PDF §1.3), full
accessibility + bilingual QA across all screens, release notes/API docs
updated, and Android + iOS store submissions prepared.

## In scope

- Consolidate offline read-cache across dashboard, chat, projects,
  issues, boards; unified stale banner (`docs/states.md`).
- Draft preservation for messages and comments across restarts.
- Accessibility pass: WCAG AA contrast, 44px targets, text scaling to
  200%, `:focus`/screen-reader labels, reduced-motion (`docs/foundation.md`,
  `README.md` Accessibility).
- Full RTL + LTR QA on all main screens (`docs/rtl-i18n.md` checklist).
- Push QA matrix: Android + iOS × foreground/background/terminated for
  every event type (PDF §9.1).
- Comment/reply web↔mobile compatibility regression (PDF §9.1).
- Release notes + API documentation updated (PDF §9.1).
- App store assets, privacy disclosures, versioning, crash reporting,
  and store submission.

## Backend track (plugin)

1. Finalize/version the `/rest/corbit-mobile/1.0` contract; publish API
   docs + a mobile release note (mirror `docs/release-notes/`).
2. Ensure feature flags allow phased rollout; confirm diagnostics report
   mobile health.
3. Any final DTO/pagination consistency fixes surfaced by QA.

## Mobile track (Flutter)

1. Cache-layer consolidation + eviction policy; stale-data banners.
2. Draft store audit (chat + comment) for all entry points.
3. Accessibility remediation from the audit; dynamic-type layouts (issue
   card 3-line at 150%+, icon-only nav at 180%+ per `docs/foundation.md`).
4. Localization completeness check — no hard-coded strings; Jalali/
   Gregorian everywhere.
5. Build signing, store metadata (fa + en), screenshots, privacy labels,
   crash/analytics wiring; staged rollout config.

## QA / test matrix

- [ ] RTL + LTR on all 5 tabs and every detail screen
      (`docs/rtl-i18n.md` Testing Checklist).
- [ ] Push on Android + iOS in all 3 states, all event types.
- [ ] Comment/reply verified in Jira web UI + Assistant events fire.
- [ ] Offline: cached data shown with banner; drafts survive restart.
- [ ] Every screen: loading/empty/error/no-permission/retry.
- [ ] Accessibility: contrast, targets, scaling, screen reader.
- [ ] Performance budgets (dashboard <2s, issue <3s) hold on device.

## Design references

- `docs/states.md`, `docs/rtl-i18n.md`, `docs/foundation.md`,
  `README.md` (Accessibility, Principles).

## Dependencies

- All prior sprints; Sprint 13 (security/perf/audit) signed off.

## Acceptance criteria (PDF §9.1 Technical Definition of Done)

- [ ] Every mobile endpoint: positive + negative permission tests.
- [ ] Standard pagination/limit + consistent error contract everywhere.
- [ ] No sensitive data in logs/push/crash reports.
- [ ] Every screen implements all five states.
- [ ] RTL Persian + LTR English tested on all main screens.
- [ ] Push tested on Android + iOS in foreground/background/terminated.
- [ ] Comment/reply works in Jira web UI + generates Assistant events.
- [ ] API docs + release notes updated for the delivery.

## Definition of Done

- v1.0 builds submitted to Google Play + App Store (or MDM/enterprise
  distribution if the org requires — PDF Appendix B).
- Rollback plan documented; staged rollout enabled.
- Post-release monitoring (latency, push success, crashes) in place.
