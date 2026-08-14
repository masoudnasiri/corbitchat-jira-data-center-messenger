#!/usr/bin/env bash
# Fail if this tree is the stale plugin repo or a forbidden lineage.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/common.sh
. "$ROOT/scripts/lib/common.sh"

section "Source safety"
if [ "$ROOT" != "$OFFICIAL_PLUGIN_REPO" ]; then
  fail "not official plugin repo (cwd=$ROOT expected=$OFFICIAL_PLUGIN_REPO)"
else
  pass "official plugin repo"
fi

if [ "$ROOT" = "$STALE_PLUGIN_REPO" ]; then
  fail "stale repo /root/jira-dev/jira-issue-chat-panel must never be used"
else
  pass "not the stale plugin tree"
fi

if git -C "$ROOT" rev-parse --abbrev-ref HEAD >/dev/null 2>&1; then
  BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD)"
  echo "INFO  branch=$BRANCH"
  if echo "$BRANCH" | grep -Eq 'reportreplies-workdays1|feature/reply-composer-attachments'; then
    fail "forbidden lineage branch: $BRANCH"
  else
    pass "branch is not a stale lineage"
  fi
else
  fail "not a git repo"
fi

section "Staged artifacts / secrets"
if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  STAGED="$(git -C "$ROOT" diff --cached --name-only || true)"
  if [ -n "$STAGED" ]; then
    echo "$STAGED" | while IFS= read -r f; do
      case "$f" in
        *.jar|*.apk|*.tgz|*.sql|*.log|*firebase*admin*.json|*credentials.json|*.pem|*secret*)
          fail "staged generated/secret path: $f"
          ;;
      esac
    done
    pass "no forbidden generated/secret paths in the index (or index empty of them)"
  else
    pass "nothing staged"
  fi
fi

finish
