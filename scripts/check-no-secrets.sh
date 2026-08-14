#!/usr/bin/env bash
# Fail if tracked or staged files look like they contain real secrets.
# Does not print secret values. Placeholders and env-var usage are allowed.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

python3 - "$ROOT" <<'PY'
import os, re, subprocess, sys

root = sys.argv[1]
fails = 0

def tracked():
    out = subprocess.check_output(["git", "ls-files"], cwd=root, text=True)
    return [p for p in out.splitlines() if p]

def staged():
    out = subprocess.check_output(["git", "diff", "--cached", "--name-only"], cwd=root, text=True)
    return [p for p in out.splitlines() if p]

# Patterns that indicate a likely real secret. Do not print the match.
RULES = [
    ("known_dev_jira_password_shape", re.compile(r"Man@[0-9]{4,}"), "critical"),
    ("known_prod_jira_password_shape", re.compile(r"Makaron@"), "critical"),
    ("mysql_password_literal", re.compile(r"-p123123|/\s*`123123`"), "high"),
    ("jim_pass_embedded_default", re.compile(r'JIM_PASS=\$\{JIM_PASS:-.+\}'), "high"),
    ("github_pat", re.compile(r"ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}"), "critical"),
    ("pem_in_json", re.compile(r"-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----"), "critical"),
    ("firebase_admin_json_name", re.compile(r"firebase-adminsdk"), "critical"),
]

PLACEHOLDER_OK = re.compile(
    r"USER:PASS|<pwd>|<DEV_PASSWORD|PASSWORD_REQUIRED_FROM_ENV|MYSQL_PASSWORD|JIM_PASS\b"
)

SKIP_EXT = {".png", ".jpg", ".jpeg", ".gif", ".woff", ".woff2", ".ttf", ".jar", ".class", ".pdf"}

def scan(path, scope):
    global fails
    base = os.path.basename(path)
    if os.path.basename(path) == "check-no-secrets.sh":
        return
    if "firebase-adminsdk" in base and path.endswith(".json"):
        print("FAIL  %s  type=firebase_admin_json  severity=critical  scope=%s" % (path, scope))
        print("      remove from the tree; do not commit admin JSON")
        fails += 1
        return
    ext = os.path.splitext(path)[1].lower()
    if ext in SKIP_EXT:
        return
    full = os.path.join(root, path)
    if not os.path.isfile(full):
        return
    try:
        text = open(full, "r", encoding="utf-8", errors="ignore").read()
    except Exception:
        return
    for name, rx, sev in RULES:
        if name == "pem_in_json" and not path.endswith(".json"):
            continue
        if name == "firebase_admin_json_name" and not path.endswith(".json"):
            continue
        if rx.search(text):
            print("FAIL  %s  type=%s  severity=%s  scope=%s" % (path, name, sev, scope))
            print("      replace with an environment variable or placeholder; do not commit secrets")
            fails += 1

print("=== secret scan (values not printed) ===")
for p in tracked():
    scan(p, "tracked")
for p in staged():
    scan(p, "staged")

if fails:
    print("OVERALL FAIL  findings=%d" % fails)
    print("Remediation: set secrets through environment variables or secure server-side config only.")
    sys.exit(1)
print("OVERALL PASS")
PY
