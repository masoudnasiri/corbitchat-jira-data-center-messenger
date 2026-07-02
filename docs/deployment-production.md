# CorbitChat — Production Deployment Runbook

Audience: the agent (or engineer) deploying a new CorbitChat plugin
release to the **production** Jira server at `193.162.129.56`.

This runbook is specific to the real production topology (verified
2026-07-02). It is different from the dev recipe in `AGENTS.md`
only in that it adds backup + smoke-test + rollback steps; the
underlying install mechanism is the same because production runs
the same single-container Docker layout as dev.

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
short `docker restart jira-srv` (~1–3 min of downtime while Jira
boots).

---

## 2. Build the release artifact

On the build host, in the plugin repo:

```bash
cd /root/jira-dev/jira-issue-chat-panel

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

# 2. remove ALL previous plugin jars, then copy the new one in.
#    NOTE (2026-07-02): production currently has a stale
#    corbitchat-jira-dc-1.0.0-internal-boards.jar sitting next to
#    the current jar. Remove every corbitchat/messenger jar so only
#    ONE version of the plugin key is present.
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
  /rest/corbit-mobile/1.0/preferences ; do
  echo "$p => $(curl -s -u "$AUTH" -o /dev/null -w '%{http_code}' "$BASE$p")"
done
```

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
  `installed-plugins/` at a time. Remove stale jars (there is
  currently a lingering `-internal-boards.jar` to clean up).
- Test on the dev/test instance (`https://jira.corbitlogic.com`,
  `AGENTS.md` workflow) before touching production; production has
  real users behind `https://jira.7gtech.net`.

---

## 8. One-shot deploy checklist

1. [ ] `ssh corbit-prod` works, three containers up.
2. [ ] `pom.xml` version bumped; `mvn ... -o clean package` green.
3. [ ] Jar copied to `/root/jira-dev/releases/`.
4. [ ] Backup taken (DB dump + current jar) — §4.
5. [ ] Deployed via Path A (UPM) or Path B (file + restart).
6. [ ] Stale/old plugin jars removed; exactly one present.
7. [ ] Smoke test all 6 endpoints = 200 — §5.
8. [ ] Manage apps shows new version, Enabled.
9. [ ] UI pass: chat, comment reply, Board Gallery.
10. [ ] Rollback path confirmed available (previous jar preserved).
