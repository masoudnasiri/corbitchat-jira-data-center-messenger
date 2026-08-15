# Phase 7H — Implementation report (DEV)

DEV-only. Production, Gateway, and Workspace Agent were not changed.

## Baseline before this phase

| Surface | Version |
|---------|---------|
| DEV plugin | `figmachat7g-dashboard-metrics1` |
| DEV Hub PWA | `1.0.0+101 / figma-phase7g-dashboard-copy-rtl1` |
| Production plugin | `figmachat7g-dashboard-metrics1` (7G already live) |
| Production PWA | `1.0.0+101 / figma-phase7g-dashboard-copy-rtl1-prod` |
| Gateway | `gateway-2.7-prod16` StartedAt `2026-07-31T23:35:19Z` |

## What shipped

### Plugin `figmachat7h-functional1`

- Follow-up JQL is now watched-only, not reporter-based:
  `watcher = currentUser() AND statusCategory != Done AND (assignee is EMPTY OR assignee != currentUser()) AND (reporter is EMPTY OR reporter != currentUser())`
- Applied to dashboard `openWorkCounts.followup` and `filter=followup`.
- Awaiting / Rejected use approval status + `statusCategory != Done` + (approver OR assignee OR reporter). Field ids still come from `JimApprovalFields` (DEV `cf[10500]` / `cf[10501]`; Production fallback `11503` / `11305`).
- Watcher JQL was verified on DEV before coding. No reporter fallback.

### Mobile `1.0.0+102 / figma-phase7h-functional1`

- Tasks empty states are filter-specific and honest.
- Multi-select Forward works for N messages in chronological order; Share added.
- Short-press on a text bubble opens the existing reaction tray; long-press overlay unchanged; attachment taps still open content.
- File download sanitizes names, verifies non-zero size, and offers retry.
- Read-only offline cache (server+user scoped): conversations, messages, bootstrap. Logout clears the bound cache. Send/react/forward/delete are blocked offline.
- Settings: auto-download images/videos/files/audio + Wi-Fi only (conservative: Wi-Fi-only skips auto-download when network type is unknown).

## Deferred / limitations

- Wi-Fi detection is not implemented; Wi-Fi-only is conservative.
- PWA file “open” remains browser tab download (no native FileProvider).
- Offline is read-only. No send queue.
- Cold-start offline needs a previously cached bootstrap for that server+user.
- DEV currently has **zero** Rejected approval issues — Rejected tab stays honestly empty until real Rejected data exists.
- Gallery bulk APIs were not added.

## Artifacts (not committed)

| Artifact | sha256 |
|----------|--------|
| Plugin jar | `b3933dfed2ad986661c725fcaae13397a33f118175d7a7b80445239668a54949` |
| Hub PWA tgz | `2da81e95a592b5f055dc7a7573b8ef1d765eec80cca7d4ea839df4329e9d0331` |
| 7G TECH PWA tgz | `673d654c318c6d4364c6db3ddfab7212dc5f2559c1537be33e61ee2437736cf5` |
| Hub APK | `122e790fc61f38f85aaf56936f2d92ff37caa94ecb09eb3a912ebecd71a0d32a` |
| 7G TECH APK | `98237f9b1025e010c1585bcc3b87c3f9d01d3f32ee6f20062670d77f5b045774` |

DEV Hub PWA is live at `/mobile/` (`1.0.0+102` / Hub). 7G TECH artifacts were **not** deployed.

## Not changed

Assigned / today / overdue, `reportSummary`, boards, report/calendar, Workspace Agent, Gateway, Production.
