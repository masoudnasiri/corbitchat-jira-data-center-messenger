# Phase 8B — Production Rollout Plan (PLAN ONLY — NOT EXECUTED)

**Target version:** `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2-8a-dynlevels1-8b-dayunit1`
**Current Production:** `…8a-dynlevels1` (Phase 8A live). Phase 8B is a superset (additive interval unit only).

## Risk / blast radius
- Additive: adds one interval unit (`days`). Existing minutes/hours/daily/cumulative/fixed_minimum paths are byte-for-byte unchanged when no level uses `days` (`workingDayInterval==0`). No schema/endpoint/plugin-key change. No mobile change.
- Behavior only changes for a level an admin explicitly switches to `days`.

## Install method (same constraints as Phase 8A)
- **UPM upload only** (or shared-home) — no docker-cp / manual JAR placement on the DC nodes.
- UPM upload requires the JVM flag `-Dupm.plugin.upload.enabled=true` on both nodes; Phase 8A hit UPM 403 without it. Enable temporarily, then restore upload-disabled posture.
- Rolling, drain-based restart via HAProxy `.30` admin socket — **never both nodes at once**; do not drain node1 if node2 is DOWN. (Phase 8A was blocked on `.30` access — same prerequisite applies here.)
- Preserve JVM javaagent + RMI hostname on each node.

## Steps (operator)
1. Verify prod baseline = `…8a-dynlevels1`; take config/DB backup per runbook.
2. Enable `-Dupm.plugin.upload.enabled=true` on node1 (drain → recreate → rejoin), confirm cluster healthy.
3. UPM-upload `…8b-dayunit1.jar`; confirm plugin enabled, `/rest/jim/1.0/health` + `ao-health` = 200 on both nodes via the LB.
4. Repeat the drained recreate on node2 to pick up the flag (if the flag must persist) — or restore the flag-disabled posture after install if upload should be locked again.
5. Post-install smoke: Report Settings shows `days / روز`; save a test level as `days`, confirm diagnostics `unit=days`, then revert the test level.
6. Regression checklist (AGENTS.md): direct chat, Assistant, assignment/mention cards, unread counts, attachments; report dashboard/CSV render.

## Rollback
- UPM: disable/uninstall `…8b-dayunit1`, reinstall `…8a-dynlevels1` (kept as the rollback artifact). No data migration needed (settings are additive flat keys; a `days` value simply becomes unknown/ignored if rolled back — recommend reverting any `days` level to hours/minutes before rollback).

## Package
Reuse the Phase 8A packaging pattern (JAR + SHA256SUMS + metadata + this plan + rollback notes), staged to node1 `/root/…-transfer/`. No secrets in the package. **Await explicit approval + HAProxy `.30` access before executing.**
