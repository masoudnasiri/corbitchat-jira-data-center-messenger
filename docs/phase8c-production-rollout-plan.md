# Phase 8C — Production Rollout Plan (PLAN ONLY — NOT EXECUTED)

**Plugin target:** `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2-8a-dynlevels1-8b-dayunit1-8c-groupsend1`
**Current Production plugin (read-only):** `…8b-dayunit1` (enabled). **Prod PWA:** build 113 / `figma-composer-darktext1-prod`.
**Mobile target (only if owner accepts mobile changes):** `1.0.0+114` / `figma-phase8c-groupsend1-prod`.

## Architecture (reference)
node1 `193.162.129.56`, node2 `193.162.129.57`, HAProxy/NFS `193.162.129.30`, external MySQL `185.83.181.205`, shared home `/var/jira/shared`, public `https://jira.7gtech.net`, PWA `/mobile/`.

## Risk / blast radius
- Additive: two nullable AO columns (`JimConversation.SEND_POLICY`, `JimGroupMember.ALLOWED_SENDER`) + new REST endpoints + DTO fields. **No existing group becomes restricted** (null policy = ALL_MEMBERS). No plugin-key/REST-base change. No report-engine change.
- Behavior changes only for a group an admin explicitly restricts.

## Preflight (hard gates)
1. Confirm current prod plugin = `…8b-dayunit1`, enabled; confirm prod PWA build 113.
2. Both Jira nodes UP + ACTIVE (cluster); HAProxy backends UP for node1 & node2; public `/status` RUNNING. **If node2 DOWN → stop.**
3. Confirm DB `185.83.181.205` reachable; NFS shared home mounted on both nodes.

## Backups (AO schema/settings change → take before install)
- MySQL `.205` dump of the plugin AO tables (at minimum `AO_*JIMCONVERSATION`, `AO_*JIMGROUPMEMBER`) + full logical backup per runbook.
- Shared-home installed-plugins JAR copy of the current `…8b-dayunit1`.
- `pluginversion` / `pluginstate` snapshot.
- Current `/mobile/` PWA bundle (only if mobile rollout is accepted).
- Runbook backup entry.

## Plugin install (UPM / shared-home only — NO docker cp)
1. If UPM upload disabled ("Plugins cannot be installed via upload" 403): temporarily add JVM flag `-Dupm.plugin.upload.enabled=true`, applied via **drain-based rolling recreate (node2 then node1)**, one node at a time, preserving javaagent + RMI hostname.
2. UPM-upload `…8c-groupsend1.jar`; verify enabled on both nodes; `/rest/jim/1.0/health` + `ao-health` = 200 via LB and per node.
3. AO auto-migration adds the two nullable columns on first load — verify via `ao-health` / dev AO schema diagnostics that `SEND_POLICY` + `ALLOWED_SENDER` exist.
4. Remove the temporary upload flag with another rolling recreate; restore upload-disabled posture.

## Mobile/PWA (only if owner accepts mobile changes)
- Deploy the 7G TECH PWA `…+114-figma-phase8c-groupsend1-prod` to the production `/mobile/` docroot with a timestamped backup of the current bundle; reload nginx. Otherwise leave prod PWA at 113 (backend is backward compatible: older PWA simply won't show the new controls and treats absent `canSendMessage` as allowed).

## Validation (post-install)
- Plugin version `…8c-groupsend1` enabled on both nodes.
- Group settings show the send-policy control; set a test group restricted, confirm a normal member gets 403 + read-only composer, admin/allowed can send; revert the test group.
- Regression: direct/group/system chats, attachments, reactions, search, shared gallery, dashboards/reports unaffected.

## Rollback
- Plugin: UPM disable/uninstall `…8c-groupsend1`, reinstall the backed-up `…8b-dayunit1`. The two extra columns are harmless if left (ignored by the older build); optionally revert any restricted group to ALL_MEMBERS first. No data migration required.
- PWA (only if deployed): restore the backed-up `/mobile/` bundle.
- Remove the temporary UPM upload flag if it was enabled.

**Await explicit owner approval + preflight before executing. Do not run UPM upload, restart Jira, or change HAProxy in this phase.**
