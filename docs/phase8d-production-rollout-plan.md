# Phase 8D — Production Rollout Plan (PLAN ONLY — NOT EXECUTED)

**Target plugin:** `…-8c-groupsend1-chatlimit15000-1-8d-wfview1`
**Target mobile/PWA:** `1.0.0+115` / `figma-phase8d-workflowview1-prod` (deploy only if owner accepts mobile).
**Current Production (read-only):** plugin last ops-confirmed `8c-groupsend1` (chatlimit15000 separately staged); PWA build 114 / `figma-phase8c-groupsend1-prod`.

## Risk / blast radius
- Additive, read-only: new REST endpoints (`/issues/{key}/workflow-view` web + mobile), one new issue-tabpanel + web-resource, one shared read-only service. **No AO/DB schema change, no migration.** No existing tool/endpoint/contract modified. Mobile adds one screen + one button.
- The plugin JAR tree also carries prior DEV work (8a/8b/8c/chatlimit15000); production is behind at 8c-groupsend1. Operator decides sequencing; the workflow-viewer change itself is isolated and additive.

## Preflight
1. Confirm current prod plugin baseline + enabled; confirm prod PWA build 114. Both nodes UP/ACTIVE (cluster), HAProxy backends UP, DB `.205` reachable, NFS `/var/jira/shared` mounted.
2. Confirm no in-flight native-workflow dependency (this phase does not touch the native designer; its blank state is unrelated and expected).

## Backups
- Shared-home installed-plugins JAR of the current baseline; `pluginversion`/`pluginstate` snapshot.
- Full MySQL `.205` logical backup (precaution; **no schema change in 8D**).
- Current `/mobile/` PWA bundle (if mobile rollout accepted).
- Runbook backup entry.

## Install (UPM / shared-home only — NO docker cp)
1. If UPM upload disabled (403): temporarily add JVM flag `-Dupm.plugin.upload.enabled=true` via **drain-based rolling recreate (node2 then node1)**, one node at a time; preserve javaagent + RMI hostname.
2. UPM-upload `…-8d-wfview1.jar`; confirm enabled on both nodes; `/rest/jim/1.0/health` + `ao-health` = 200 via LB and per node.
3. Remove the temporary flag with another rolling recreate.

## Mobile/PWA (only if owner accepts mobile changes)
- Static swap of the `/mobile/` docroot with the 7G TECH PWA build-115 bundle (backup 114 first); reload nginx. Confirm `/mobile/version.json` → build 115 / `figma-phase8d-workflowview1-prod`. Distribute the 7G APK build 115 to the owner as needed. Otherwise leave prod PWA at 114 (backend endpoints are additive; older PWA simply lacks the button).

## Validation (post-install, both nodes)
- Plugin version `…-8d-wfview1`, enabled. On a web issue page: the "Workflow / فرآیند" tab appears, opens the custom viewer, statuses/transitions render, current highlighted, available transitions match the native transition menu, conditions/validators/post-functions show with redaction; native Workflow Designer not invoked. Regression: Report/Views/Attachments/Comments/chat intact.
- Mobile (if deployed): "مشاهده فرآیند" near status chips opens the viewer.

## Rollback
- Plugin: UPM disable/uninstall `…-8d-wfview1`, reinstall the prior baseline JAR. No data migration to reverse (read-only feature). 
- PWA (if deployed): restore the backed-up build-114 bundle. Remove temp upload flag if enabled.

**Await explicit owner approval + preflight. No UPM upload, Jira restart, or HAProxy change in this phase.**
