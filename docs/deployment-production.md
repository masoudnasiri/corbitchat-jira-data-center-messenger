# CorbitChat — Production Deployment Runbook

Audience: the agent (or engineer) deploying a new CorbitChat plugin
release to the **production** Jira server at `193.162.129.56`.

This runbook is specific to the real production topology (verified
2026-07-02, re-verified after the `1.0.0-mobile-s11-fix3`
deploy). It is different from the dev recipe in `AGENTS.md`
only in that it adds backup + smoke-test + rollback steps; the
underlying install mechanism is the same because production runs
the same single-container Docker layout as dev.

> **Current production plugin version: `1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat7a-correct1`.**
> **Current production PWA: `1.0.0+94 / figma-phase7b-design-lock1-prod` (7G TECH).**
> Rollback plugin: `figmachat5a-shared-open-hotfix`. Rollback PWA: `+88`.
> Official freeze: `docs/production-baseline.md`.
> Source repo (only): `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane`.
> **Never build from** `/root/jira-dev/jira-issue-chat-panel`.
> (Deployed 2026-08-14 via Path B — P0 shared-gallery Open message hotfix.
> Shared `باز کردن پیام` now calls `openConversationAtMessage`.
> Jar sha256 `aebc17088461e9792e5f87cf9b5cb94493dc08a68aa25336d8ce52e53843b373`.
> Rollback plugin: `figmachat5-search-mentions`.
> Rollback PWA: `+87 / figma-chat-design-reconcile1`.
> Backups: `/root/jira-backups/*-20260814-125535*` on `corbit-prod`.
> Rollback scripts: `/root/jira-backups/rollback-figmachat5a-20260814-125535/`.)
> Previous marker below (`1.0.0-s12e-reportscope1`) retained for history:
> (Deployed 2026-07-29 via Path B — Phase 2 of the Report Applicability Policy.
> Adds the compute-level applicability gate + admin settings + read-only impact
> endpoint (`GET /rest/jim/1.0/admin/report-applicability/impact`). Shipped with
> the policy **DISABLED** (`reportApplicabilityEnabled=false`) — no behaviour
> change vs approvalfields4. NO provisioning, NO mapping change, NO ledger
> refresh, NO settings enablement. Approval resolver still uses fallback IDs
> (usingFallback=true, configuredFieldId=null). Verified: mobile/health version,
> all 4 health endpoints 200, statusCounts, issues/reports, all 6 issue filters
> (assigned/today/overdue/followup/awaiting/rejected), Assistant feed 200,
> approval-field diagnostics unchanged, report queue depth 0. Jar sha256
> `b3aa7fc3510731dc55c4501683d3d6402771800ef996bf57eedefc376e51c5ac`.
> DB backup: `/root/jira-backups/jira-db-20260729-165852.sql`.
> Rollback jar (approvalfields4):
> `/root/jira-backups/installed-plugins-20260729-165852/corbitchat-jira-dc-1.0.0-s12e-approvalfields4.jar`.)
> Previous marker below (`1.0.0-s12e-approvalfields4`) retained for history:
> (Deployed 2026-07-28 via Path B, code-only rollout of the safe custom-field
> provisioning / environment-specific approval-field resolver. NO Production
> provisioning was executed and NO Production mapping was changed — Production
> keeps resolving all 5 approval fields via the hardcoded fallback IDs
> (status cf_11503, approver cf_11305, proposed cf_11309, approved cf_11501,
> reason cf_11502). Diagnostics confirm all 5 `exists/typeOk/optionsOk=true`,
> `usingFallback=true`, `configuredFieldId=null`; custom-field count unchanged
> at 131. Admin provisioning/mapping endpoints exist but are SYSTEM_ADMIN-gated
> and were not invoked. Rollback jar: `assistant-filters1` in
> `/root/jira-backups/installed-plugins-20260728-135838/`.)
> Previous marker below (`1.0.0-s12e-report5`) retained for history:
> (Deployed 2026-07-19 via Path B. Cumulative over `usersearchfix1`. Two fixes:
> (1) report lifecycle — a due-based CLOSED issue now auto-reopens when the
> effective deadline is extended into the future (PM-79 was stuck "deadline
> ended" after its due moved 07-15→07-20); added an effective-deadline resolver
> (system Due → Approved cf_11501 → Proposed cf_11309, never First Report).
> (2) Report tab web UI — the `jim-report-resources` web-resource now binds to
> `jira.view.issue` + `jira.navigator` contexts (was context-less, so the
> ajax-loaded tab JS never loaded → blank until refresh, and never in the Issue
> Navigator); JS init switched to `JIRA.Events.NEW_CONTENT_ADDED` + per-issue-key
> re-init (no stale data on navigator switch) + Retry error state. Verified on
> prod: PM-79 ACTIVE/submitActive; report JS present in View-Issue and Navigator
> batches; user-search fix intact. Rollback jar: `usersearchfix1` in
> `/root/jira-backups/installed-plugins-20260719-183941/`.)
> Previous marker below (`1.0.0-s12e-usersearchfix1`) retained for history:
> (Deployed 2026-07-18 via Path B as a P0 hotfix: chat user-search /
> member-picker filters were hiding the *configured reviewer user*
> (`m.nasiri`, a real person) because they called `isReviewerUserKey`. Added a
> narrow `isHiddenSystemAccountUser*` predicate that hides ONLY the dedicated
> hidden account `pluginuser`, and pointed `JimUserSearchServiceImpl`,
> `JimGroupServiceImpl.requireNotReviewer`, and `JimRestJsonMapper` member-list
> at it. Verified on prod: search `masoud`/`nasiri`/`m.nasiri` now returns
> `m.nasiri`; `pluginuser` stays hidden. Rollback jar: `s12e-groupadmin-avatar1`
> in `/root/jira-backups/installed-plugins-20260718-065726/`.)
> Previous marker below (`1.0.0-s12e-assistantfix3`) retained for history:
> (Deployed 2026-07-17 via Path B as a P0 hotfix: `JimEventExecutor` now uses
> two lanes — a `jim-assistant-event-processor` pool for latency-sensitive
> Assistant cards and a separate `jim-maintenance-processor` for heavy
> best-effort work (`report_recalc`, project-chat sync). Root cause was the old
> single shared thread: `report_recalc` writes an issue field → synchronous
> ISSUE_UPDATED → third-party Time-to-SLA recompute (~1s/query, ~60s/recalc)
> blocked Assistant creation behind it for minutes-to-hours. Verified on prod:
> mention → Assistant row in ~0.24s; report_recalc now isolated. KNOWN
> FOLLOW-UP: report_recalc itself is ~57-60s/run and continuously backlogged on
> the maintenance lane — needs a separate performance fix.)
> Previous marker below (`1.0.0-s12e-assistantfix2`) retained for history:
> (Deployed 2026-07-16 via Path B as a P0 hotfix: the Jira Assistant
> feed no longer applies any age-based `since` window to the SYSTEM
> conversation, so poll/refresh/resume/deep-link paths can no longer
> empty it; `hasOlder` in the mobile BFF is now derived from the raw
> batch. Built on top of `assistantfix1`, which itself carried the
> reporting, comment-reactions, Jira-wide Jalali display and i18n work.)
> Previous marker below (`1.0.0-s12e-loc5`) retained for history:
> (Deployed 2026-07-13 via Path B. `loc3` added web-chat localization
> — Persian UI + Jalali dates — on top of `s12e-sched5`; `loc4`
> rendered the schedule/due-date approval card's "Proposed date" in the
> effective calendar and localized its labels/status; `loc5` replaced
> the Jalali converter with the verified Borkowski algorithm — the
> previous JDN/break-table transcription was off by one year and
> produced an out-of-range (undefined) month — and forces the approval
> card RTL in Persian mode.)
> Bump past it for the next release. Note: mobile-only changes
> (e.g. the Sprint 11.4 UI polish) require NO production redeploy —
> only a new app build.
>
> **Note (2026-07-13):** UPM "Upload app" is DISABLED on production
> (`Plugins cannot be installed via upload`, HTTP 403), so Path A is
> unavailable — use **Path B (file install + restart)**. Production's
> Tomcat connector is already proxy-aware
> (`scheme="https" proxyName="jira.7gtech.net" proxyPort="443"`), so
> it does NOT have the dev XSRF/proxy issue documented in
> `docs/reverse-proxy-xsrf-fix.md`; browser writes work there.

---

## 0. Prerequisites — SSH access

A dedicated SSH key already exists on the build host
(`vm-206238`, this workspace's host) and is authorized on
production:

- Private key: `~/.ssh/corbit_prod_193_162_129_56` (NEVER commit
  this, never copy it into the repo).
- Public key: `~/.ssh/corbit_prod_193_162_129_56.pub`.
- SSH config alias (in `~/.ssh/config`):

```
Host corbit-prod
    HostName 193.162.129.56
    User root
    Port 22
    IdentityFile ~/.ssh/corbit_prod_193_162_129_56
    IdentitiesOnly yes
```

Verify access before doing anything else:

```bash
ssh corbit-prod 'hostname && docker ps --format "{{.Names}}\t{{.Status}}"'
```

Expected: host `vm-186356` and three running containers
(`jira-srv`, `nginx-jira`, `mysql-jira`).

> If you are running from a host **without** network access to
> `193.162.129.56`, none of the `ssh corbit-prod` steps will work —
> run them from a host that can reach the server (the build host
> `vm-206238` can).

---

## 1. Production topology (verified 2026-07-02)

| Piece | Value |
|---|---|
| Host | `vm-186356` (Ubuntu 24.04), IP `193.162.129.56` |
| Jira | Docker container `jira-srv`, image `haxqer/jira:9.17.5` |
| TLS/proxy | Docker container `nginx-jira` (ports 80/443) |
| DB | Docker container `mysql-jira` (MySQL 8.0) |
| Jira home | named volume `jira_home_data` → `/var/jira` |
| Plugin dir | `/var/jira/plugins/installed-plugins/` (inside `jira-srv`) |
| Direct Jira URL | `http://193.162.129.56:8080` |
| Public URL | `https://jira.7gtech.net` (via nginx) |

> The public production URL is **`https://jira.7gtech.net`**.
> `https://jira.corbitlogic.com` is the **test/dev** server — do not
> point production deploys or smoke tests at it.

Because this is a **single node** (no Jira shared home / no
cluster), there is no rolling restart — a file install needs one
`docker restart jira-srv`. **Observed production boot times range
from ~5 up to ~25 minutes** (the instance has grown); keep polling
the health endpoint patiently — a long 503 phase is normal, not a
failed deploy. Verify container status/logs via
`ssh corbit-prod 'docker ps; docker logs jira-srv --tail 20'`
before considering a rollback.

---

## 2. Build the release artifact

On the build host, in the plugin repo:

```bash
cd /root/jira-dev/jira-issue-chat-panel-mcp-control-plane

# NEVER build from /root/jira-dev/jira-issue-chat-panel
# Bump <version> in pom.xml for every release so it is traceable
# and rollback-able. Keep the plugin key UNCHANGED
# (com.corbitlogic.corbitchat.jira.dc) or AO data is orphaned.

mvn -s /root/.m2/settings.xml \
    -Dmaven.repo.local=/root/maven-repos/atlassian-jira-offline \
    -o clean package
```

Artifact: `target/corbitchat-jira-dc-<version>.jar` (deploy this
one — NOT the `*-obr.jar`).

Preserve a copy for rollback:

```bash
mkdir -p /root/jira-dev/releases
cp target/corbitchat-jira-dc-<version>.jar /root/jira-dev/releases/
```

### 2.1 Pre-package checklist (mandatory — every future chat build)

Do this **before** `mvn ... package`. Fail the release if any item is missing.
Do not start new chat work from another tree. Phase 6 scripts:
`scripts/run-phase6-dev.sh` and `docs/regression-checklist.md`.

```bash
REPO=/root/jira-dev/jira-issue-chat-panel-mcp-control-plane
STALE=/root/jira-dev/jira-issue-chat-panel
JS=$REPO/src/main/resources/js/jim-messenger.js
XML=$REPO/src/main/resources/atlassian-plugin.xml
VM=$REPO/src/main/resources/templates/messenger-app.vm

# 1. Build only from the official repo
[ "$(pwd)" = "$REPO" ] || { echo FAIL: not in $REPO; exit 1; }
[ ! -e "$STALE" ] || echo "WARN: stale tree exists at $STALE — do not build from it"
"$REPO/scripts/check-no-secrets.sh" || { echo FAIL: secret scan; exit 1; }

# 2. Assistant report filters + event types in JS config
grep -q "key: 'reports'" "$JS" || { echo FAIL: reports filter; exit 1; }
grep -q "key: 'reportMentions'" "$JS" || { echo FAIL: reportMentions filter; exit 1; }
grep -q "key: 'reportsReceived'" "$JS" || { echo FAIL: reportsReceived filter; exit 1; }
for et in REPORT_SUBMITTED REPORT_SUBMITTED_TO_ME REPORT_REPLY REPORT_MENTION; do
  grep -q "$et" "$JS" || { echo FAIL: missing $et; exit 1; }
done

# 3. Search jump
grep -q 'function openConversationAtMessage' "$JS" || { echo FAIL: openConversationAtMessage; exit 1; }
grep -q 'openConversationAtMessage(cid, mid)' "$JS" || { echo FAIL: search click jump; exit 1; }
grep -q 'data-conversation-id' "$JS" || { echo FAIL: conversationId on result; exit 1; }
grep -q 'data-message-id' "$JS" || { echo FAIL: messageId on result; exit 1; }

# 4. Preserved Production UX
grep -q 'jim-sidebar-actions-compact' "$VM" || { echo FAIL: compact sidebar; exit 1; }
grep -q 'shared' "$JS" || { echo FAIL: shared galleries hook; exit 1; }
grep -q 'function openForwardModalForMessages' "$JS" || { echo FAIL: multi-forward; exit 1; }
grep -q "t('Saved Messages')" "$JS" || { echo FAIL: Saved Messages; exit 1; }
grep -q "Forward': 'فوروارد'" "$JS" || { echo FAIL: Persian Forward; exit 1; }
grep -q "Copy': 'کپی'" "$JS" || { echo FAIL: Persian Copy; exit 1; }
grep -q "Share': 'اشتراک" "$JS" || { echo FAIL: Persian Share; exit 1; }
grep -q "ذخیره در پیام‌های ذخیره‌شده" "$JS" || { echo FAIL: Persian Save; exit 1; }
grep -q "Delete for me': 'حذف برای من'" "$JS" || { echo FAIL: Persian Delete; exit 1; }

# 5. WRM packaging rule: never keep generated *-min.js/css in source.
# jira-maven-plugin compress-resources writes min files into the JAR from the
# current source at package time. If a stale *-min.js also sits in
# src/main/resources, Jira WRM serves that older file and Phase 5 (or any
# later) JS/CSS silently disappears.
grep -q 'name="jim-messenger.js" location="/js/jim-messenger.js"' "$XML" \
  || { echo FAIL: WRM asset path; exit 1; }
test ! -e "$REPO/src/main/resources/js/jim-messenger-min.js" \
  || { echo FAIL: stale jim-messenger-min.js would shadow live JS; exit 1; }
test ! -e "$REPO/src/main/resources/js/jim-api-min.js" \
  || { echo FAIL: stale jim-api-min.js would shadow live JS; exit 1; }
test ! -e "$REPO/src/main/resources/css/jim-messenger-min.css" \
  || { echo FAIL: stale jim-messenger-min.css would shadow live CSS; exit 1; }
# Any other jim-*-min stand-in in resources is also a WRM shadow risk.
if find "$REPO/src/main/resources" \( -name 'jim-*-min.js' -o -name 'jim-*-min.css' \) | grep -q .; then
  echo FAIL: unexpected jim-*-min asset in src/main/resources
  find "$REPO/src/main/resources" \( -name 'jim-*-min.js' -o -name 'jim-*-min.css' \)
  exit 1
fi

# 6. No generated artifacts / secrets in the commit
git -C "$REPO" status --short | grep -Eiq \
  '(\.jar|\.apk|\.sql|backup|dump|\.env|secret|token|credential)' \
  && { echo FAIL: generated/secret files dirty; exit 1; }

echo PRE-PACKAGE CHECKLIST OK
```

Manual confirmations (same gate):

- [ ] Working directory is `/root/jira-dev/jira-issue-chat-panel-mcp-control-plane`
- [ ] Not building from `/root/jira-dev/jira-issue-chat-panel`
- [ ] Assistant report filters exist (`reports`, `reportMentions`, `reportsReceived`)
- [ ] `REPORT_SUBMITTED`, `REPORT_SUBMITTED_TO_ME`, `REPORT_REPLY`, `REPORT_MENTION` are in the JS config
- [ ] `openConversationAtMessage` exists
- [ ] Search result click uses `conversationId` + `messageId`
- [ ] Compact sidebar exists
- [ ] Shared galleries exist
- [ ] Multi-forward exists
- [ ] Saved Messages exists
- [ ] Copy / Share / Forward / Delete / Save labels are Persian
- [ ] `jim-messenger.js` is the WRM-served asset (`atlassian-plugin.xml`)
- [ ] No generated artifacts, logs, backups, secrets, or tokens are committed
- [ ] `pom.xml` version is a **new** unique suffix (never reuse `figmachat4c` / `4d` / `4e`)
- [ ] No `jim-*-min.js` / `jim-*-min.css` in `src/main/resources` (WRM shadow risk)
- [ ] Chat-row `@` badge is an **unread mention** indicator (`unreadMentionCount` after `lastRead`, bounded). Do not fake counts client-side. Do not count Assistant report events as chat-row mentions.

---

## 3. Choose a deploy path

Two supported paths. **Path A (UPM upload) is preferred for
production** — no restart, dynamic enable, one-click disable. Use
Path B (file + restart) only if UPM upload is unavailable.

### Path A — UPM upload (preferred, no restart)

Via the admin UI:

1. Sign in to `https://jira.7gtech.net` as a Jira admin.
2. **⚙ → Manage apps → Upload app**.
3. Upload `corbitchat-jira-dc-<version>.jar`.
4. UPM installs + enables it dynamically and runs any AO schema
   upgrade. Confirm the version on **Manage apps**.

Via REST (for scripting) — run from a host that can reach the
public URL:

```bash
BASE='https://jira.7gtech.net'
JAR=target/corbitchat-jira-dc-<version>.jar
TOKEN=$(curl -s -u ADMIN:PASS "$BASE/rest/plugins/1.0/?os_authType=basic" -I \
        | awk -F': ' '/upm-token/{print $2}' | tr -d '\r')
curl -u ADMIN:PASS \
  -H "Accept: application/vnd.atl.plugins+json" \
  -H "upm-token: $TOKEN" \
  -F "plugin=@${JAR}" \
  "$BASE/rest/plugins/1.0/?token=$TOKEN"
```

### Path B — file install into the container (needs restart)

Step B1 — **Back up first** (see §4).

Step B2 — copy the jar to the server and into the container:

```bash
JAR=target/corbitchat-jira-dc-<version>.jar

# 1. copy to the server host
scp "$JAR" corbit-prod:/root/

# 2. remove ALL previous plugin jars, then copy the new one in, so
#    only ONE version of the plugin key is ever present. (The stale
#    -internal-boards.jar noted on 2026-07-02 has been cleaned up.)
ssh corbit-prod 'bash -lc "
  docker exec -u 0 jira-srv bash -lc \"rm -f /var/jira/plugins/installed-plugins/corbitchat-jira-dc-*.jar /var/jira/plugins/installed-plugins/jira-internal-messenger-*.jar\"
  JAR=$(basename '"$JAR"')
  docker cp /root/$JAR jira-srv:/var/jira/plugins/installed-plugins/
  docker exec -u 0 jira-srv bash -lc \"chmod 644 /var/jira/plugins/installed-plugins/$JAR && ls -la /var/jira/plugins/installed-plugins/corbitchat-jira-dc-*.jar\"
  rm -f /root/$JAR
"'
```

Step B3 — restart Jira and wait for it to come up:

```bash
ssh corbit-prod 'docker restart jira-srv'
```

(Do NOT use `-it` with `docker exec` — no TTY here.)

---

## 4. Backup (mandatory before Path B; recommended before Path A)

AO tables live in the Jira MySQL DB, and attachments live on the
`jira_home_data` volume. Back up both. Read DB creds from the
container env at runtime rather than hardcoding them:

```bash
ssh corbit-prod 'bash -lc "
  TS=$(date +%Y%m%d-%H%M%S)
  mkdir -p /root/jira-backups
  # DB dump (creds pulled from the mysql container env)
  DBUSER=$(docker exec mysql-jira bash -lc \"printf %s \\\$MYSQL_USER\")
  DBPASS=$(docker exec mysql-jira bash -lc \"printf %s \\\$MYSQL_PASSWORD\")
  DBNAME=$(docker exec mysql-jira bash -lc \"printf %s \\\$MYSQL_DATABASE\")
  docker exec mysql-jira sh -c \"exec mysqldump -u\\\"$DBUSER\\\" -p\\\"$DBPASS\\\" \\\"$DBNAME\\\"\" > /root/jira-backups/jira-db-\$TS.sql
  # Current plugin jar (for a fast rollback)
  docker cp jira-srv:/var/jira/plugins/installed-plugins/ /root/jira-backups/installed-plugins-\$TS
  ls -la /root/jira-backups/
"'
```

Keep the last few backups. For a big instance, also snapshot the
`jira_home_data` volume during a maintenance window.

---

## 5. Smoke test after deploy

Wait until Jira is up (health flips 503 → 401/200), then test both
the existing web REST and the new mobile BFF. Use a real admin /
user credential:

```bash
BASE='https://jira.7gtech.net'   # or http://193.162.129.56:8080
AUTH='USER:PASS'
for p in \
  /rest/jim/1.0/health \
  /rest/jim/1.0/ao-health \
  /rest/corbit-mobile/1.0/health \
  /rest/corbit-mobile/1.0/ao-health \
  /rest/corbit-mobile/1.0/bootstrap \
  /rest/corbit-mobile/1.0/preferences \
  /rest/corbit-mobile/1.0/branding ; do
  echo "$p => $(curl -s -u "$AUTH" -o /dev/null -w '%{http_code}' "$BASE$p")"
done

# Confirm the running plugin version:
curl -s -u "$AUTH" "$BASE/rest/corbit-mobile/1.0/health"   # → {"version":"..."}
```

For chat-affecting releases, additionally run a mobile-session
functional pass (login → send/read → attachments → cleanup); see
`docs/handover-agent-current-state.md` §7–§8 for the recipe.

Expect all **200** (`bootstrap`/`preferences` require an
authenticated user). Then confirm in **Manage apps** that the
plugin shows the new version and is **Enabled**, and do a quick UI
pass: open CorbitChat, send/receive a message, open an issue with a
comment reply, open the Board Gallery.

---

## 6. Rollback

- **Path A (UPM):** Manage apps → uninstall the new version →
  upload the previous jar from `/root/jira-dev/releases/`.
- **Path B (file):** restore the preserved jar and restart:

```bash
ssh corbit-prod 'bash -lc "
  docker exec -u 0 jira-srv bash -lc \"rm -f /var/jira/plugins/installed-plugins/corbitchat-jira-dc-*.jar\"
  # copy the previous good jar back in from the backup taken in §4
  PREV=/root/jira-backups/installed-plugins-<TS>/installed-plugins/corbitchat-jira-dc-<oldversion>.jar
  docker cp \$PREV jira-srv:/var/jira/plugins/installed-plugins/
  docker restart jira-srv
"'
```

Because the plugin key and AO schema are stable across these
builds, data is preserved on rollback. Only restore the DB dump
(§4) if a release actually performed a destructive schema change
(none to date).

---

## 7. Hard rules

- **Never** commit or copy the SSH private key
  (`~/.ssh/corbit_prod_193_162_129_56`) into the repo.
- **Never** change the plugin key
  (`com.corbitlogic.corbitchat.jira.dc`) — it would create a second
  plugin and orphan all AO data.
- **Bump `pom.xml` `<version>`** every release; keep the built jar
  in `/root/jira-dev/releases/`.
- Any new AO entity must keep `AO_<prefix>_<TABLE>` ≤ 30 chars
  (this bit us before — see `JimMobilePreference` using
  `@Table("JimMobilePref")`).
- Only ONE `corbitchat-jira-dc-*.jar` may live in
  `installed-plugins/` at a time. Always remove stale jars during
  install (Path B step B2 does this).
- Test on the dev/test instance (`https://jira.corbitlogic.com`,
  `AGENTS.md` workflow) before touching production; production has
  real users behind `https://jira.7gtech.net`.

---

## 8. One-shot deploy checklist

1. [ ] `ssh corbit-prod` works, three containers up.
2. [ ] Pre-package checklist §2.1 passed (official repo only; report filters; search jump; compact / galleries / multi-forward / Saved Messages; Persian action labels; WRM asset aligned).
3. [ ] `pom.xml` version bumped; `mvn ... -o clean package` green.
4. [ ] Jar copied to `/root/jira-dev/releases/`.
5. [ ] Backup taken (DB dump + current jar) — §4.
6. [ ] Deployed via Path A (UPM) or Path B (file + restart).
7. [ ] Stale/old plugin jars removed; exactly one present.
8. [ ] Smoke test all 6 endpoints = 200 — §5.
9. [ ] Manage apps shows new version, Enabled.
10. [ ] UI pass: chat, comment reply, Board Gallery.
11. [ ] Rollback path confirmed available (previous jar preserved).
