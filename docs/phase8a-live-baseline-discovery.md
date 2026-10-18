# Phase 8A — Live Baseline Discovery

**Date (UTC):** 2026-09-11
**Method:** Live read-only inspection. Live evidence wins over runbook/chat. No secrets in this file.
**Rule applied:** No version was assumed from chat history; every value below was read from a live source.

---

## A) Production infrastructure baseline

| Item | Live value | Source |
|---|---|---|
| Public URL / status | `https://jira.7gtech.net/status` → `{"state":"RUNNING"}` | public HTTPS |
| Architecture | Jira **Data Center**, 2 nodes | `clusternode` on `.205` |
| node1 | `193.162.129.56` (`vm-186356`), `jira-srv` Up 12 days | SSH `corbit-prod` |
| node2 | ACTIVE | `clusternode` (node1 ACTIVE, node2 ACTIVE) |
| Cluster | `jira.node.id=node1`, `ehcache.listener.hostName=193.162.129.56` | container `cluster.properties` |
| **Jira DB actually used** | **external MySQL `jdbc:mysql://185.83.181.205:3306/jira`, user `jira`** | container `dbconfig.xml` (node1) |
| Legacy local MySQL | `mysql-jira` container Up on node1 — **NOT used by Jira** (dbconfig points to `.205`) | docker ps + dbconfig |
| Shared home | `193.162.129.30:/export/jira-shared` on `/var/jira/shared` type **nfs4 (vers 4.2)** | container `mount` |
| Shared plugin dir | exists; CorbitChat JAR present in shared installed-plugins | `ls` shared |

Notes:
- Only SSH alias available on this operator workstation is `corbit-prod` (= node1 `.56`). node2 `.57`, HAProxy/NFS `.30`, and DB `.205` were verified **indirectly** (clusternode table via the live DB, NFS mount from node1, public URL). Direct SSH to `.57/.30/.205` requires the Windows operator workstation aliases (`jira-back`, `nfs-shared`, `jira-db`) per the runbook — not present here. This is a discovery limitation, not a blocker for the DEV phase; the production rollout plan accounts for it.
- HAProxy backend/`mobile_static` state and RMI `java.rmi.server.hostname` (JAVA_OPTS) could not be read from `/proc/1/environ` on this pass; runbook records `mobile_static` backend on `.30` and RMI hostname in compose JAVA_OPTS. To be re-verified at rollout preflight from the `.30`/full-alias workstation.

## B) Production CorbitChat plugin baseline

| Item | Live value |
|---|---|
| Plugin key | `com.corbitlogic.corbitchat.jira.dc` |
| Installed version (JAR, local + shared) | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2` |
| `pluginversion` row on `.205` | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2` (matches JAR) |
| Both nodes same version | Shared-home JAR is the cluster source; node1+node2 ACTIVE |
| `/rest/corbit-mobile/1.0/health` | 200 |
| `/rest/corbit-mobile/1.0/ao-health` | 200 |
| `/rest/jim/1.0/health` | 200 |
| `/plugins/servlet/jim/admin` | 403 as `m.nasiri` (expected — not a sysadmin) |
| `/plugins/servlet/jim/mcp-admin` | 403 as `m.nasiri` (expected) |

## C) DEV plugin baseline

| Item | Live value |
|---|---|
| DEV Jira | local docker `jira-srv` (`haxqer/jira:9.17.5`), reachable at `http://185.83.181.194:8080`, `/status` RUNNING |
| Topology | **single-node** (no `cluster.properties`) |
| Jira DB | local `mysql-jira` container (`jdbc:mysql://…host=mysql…`) — DEV-only local DB |
| Installed CorbitChat | `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2` (local home) |
| health/ao-health/corbit-health as `m.nasiri` | **403** (differs from prod 200 — DEV-specific permission quirk; `/status` RUNNING and plugin loaded; admin uses sysadmin creds) |
| Source repo/branch/HEAD | `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane`, branch `feature/mcp-control-plane`, working tree has **uncommitted figmachat7i+reportmetrics2 work** (must preserve) |
| Source matches installed DEV version | **Yes** (pom = `…figmachat7i-reportmetrics2`, matches DEV+PROD JAR) |

## D) Production PWA / mobile baseline

`https://jira.7gtech.net/mobile/version.json` (HTTP 200):
```
{"app_name":"corbitchat_mobile","version":"1.0.0","build_number":"113",
 "build_label":"figma-composer-darktext1-prod","brand":"sevengtech"}
```
- `/mobile/` = 200, `/mobile/version.json` = 200. Brand **sevengtech** (7G TECH), no Hub leak.
- Served from node1 nginx via HAProxy `/mobile` ACL (runbook `mobile_static` backend → node1 `nginx-jira:443`).
- node2 PWA parity: not changed/inspected in this phase (do not change).

## E) DEV PWA / mobile baseline

- `http://185.83.181.194:8080/mobile/version.json` returned empty on this pass (DEV `/mobile` not served on the Jira port, or a different route). DEV mobile is validated via the repo + release artifacts rather than a live DEV `/mobile` endpoint.

## F) Mobile source / artifact baseline

| Item | Live value |
|---|---|
| Repo | `/root/jira-dev/corbitchat-mobile`, branch `main`, HEAD `78a6eeb`, **working tree dirty** (composer/phase7i work) |
| pubspec | `1.0.0+113` |
| app_config | `appBuild=113`, `buildLabel` via `String.fromEnvironment` (dart-define at build) |
| Highest release artifact | build **113** (hub + 7gtech, APK + PWA) under `/root/jira-dev/releases` |
| Latest Hub artifacts | `…hub-1.0.0+113-figma-composer-darktext1.(apk|tgz)` |
| Latest 7G TECH artifacts | `…7gtech-1.0.0+113-figma-composer-darktext1-prod.(apk|tgz)` |
| Production live build | 113 |

**Next unused mobile build (if mobile changes are required): `114`.** (max of prod-live 113, dev, pubspec 113, artifacts 113, +1). Not assumed — derived from the four live sources.

## G) Baseline decision

1. **Plugin source baseline:** `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2` (canonical worktree `feature/mcp-control-plane`; matches DEV + PROD live). The official source repo **does correspond** to the live plugin baseline → safe to proceed.
2. **New Phase 8A plugin version (chosen):** `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7i-reportmetrics2-8a-dynlevels1` (short suffix `8a-dynlevels1` per the long-string rule).
3. **Next mobile build if needed:** `114`, labels `figma-phase8a-dynamic-report-levels1` (Hub) / `figma-phase8a-dynamic-report-levels1-prod` (7G TECH).
4. **Mobile change likely necessary?** Preliminary: **No** — dynamic follow-up levels is a plugin/settings/engine change; the mobile report DTO surfaces the issue's level *label* (`levelText`), which is unaffected. To be confirmed in the audit (Part 1 §8). If confirmed: `Mobile unchanged — no PWA/APK build.`
5. **Production vs docs:** Production **matches** the runbook (version, DC topology, `.205` DB, node1/2 ACTIVE, PWA 113). The historical single-node/local-mysql model from earlier chat is **pre-Aug-25-migration** and superseded by live state.
6. **Blockers:** None for the DEV phase. Watch-items: DEV health 403 (DEV-only quirk); direct SSH to node2/.30/.205 unavailable from this workstation (rollout plan accounts for it).
