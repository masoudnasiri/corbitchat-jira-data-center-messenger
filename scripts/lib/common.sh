# Shared helpers for Phase 6 smoke/check scripts.
# Safe for DEV. Does not mutate Production.

pass() { echo "PASS  $*"; PASSES=$((PASSES + 1)); }
fail() { echo "FAIL  $*"; FAILS=$((FAILS + 1)); }
skip() { echo "SKIP  $*"; SKIPS=$((SKIPS + 1)); }
section() { echo; echo "=== $* ==="; }

PASSES=0
FAILS=0
SKIPS=0

finish() {
  echo
  echo "RESULT  pass=$PASSES fail=$FAILS skip=$SKIPS"
  if [ "$FAILS" -gt 0 ]; then
    echo "OVERALL FAIL"
    exit 1
  fi
  echo "OVERALL PASS"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "FAIL  missing command: $1"; exit 1; }
}

OFFICIAL_PLUGIN_REPO="/root/jira-dev/jira-issue-chat-panel-mcp-control-plane"
STALE_PLUGIN_REPO="/root/jira-dev/jira-issue-chat-panel"
OFFICIAL_MOBILE_REPO="/root/jira-dev/corbitchat-mobile"

PLUGIN_BASELINE="1.0.0-s12e-mcpcontrol13-report-calendar1-figmachat5a-shared-open-hotfix"
PWA_LABEL="figma-chat-shared-open-hotfix"
PWA_BUILD="88"
