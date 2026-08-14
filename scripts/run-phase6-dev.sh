#!/usr/bin/env bash
# Run all Phase 6 DEV smoke/check scripts. Never points at Production.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JIM_BASE="${JIM_BASE:-http://185.83.181.194:8080}"
if [ -z "${JIM_PASS:-}" ]; then
  echo "FAIL  JIM_PASS is required in the environment (do not put passwords in source)"
  exit 1
fi
FAILED=0
run() {
  echo
  echo "######## $1 ########"
  if "$1"; then
    echo "OK    $1"
  else
    echo "BAD   $1"
    FAILED=1
  fi
}
run "$ROOT/scripts/check-no-stale-repo.sh"
run "$ROOT/scripts/check-release-assets.sh"
run "$ROOT/scripts/smoke-chat-web.sh"
run "$ROOT/scripts/smoke-chat-api.sh"
run "$ROOT/scripts/check-wrm-regression.sh"
if [ -x /root/jira-dev/corbitchat-mobile/scripts/smoke-mobile-build.sh ]; then
  run /root/jira-dev/corbitchat-mobile/scripts/smoke-mobile-build.sh
fi
echo
if [ "$FAILED" -eq 0 ]; then
  echo "PHASE6 DEV SMOKE OVERALL PASS"
else
  echo "PHASE6 DEV SMOKE OVERALL FAIL"
  exit 1
fi
