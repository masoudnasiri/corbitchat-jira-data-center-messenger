#!/usr/bin/env bash
# Fetch the live DEV WRM batch and assert required handlers.
# Does not mutate Production. Override with JIM_BASE / JIM_USER / JIM_PASS.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/common.sh
. "$ROOT/scripts/lib/common.sh"

JIM_BASE="${JIM_BASE:-http://185.83.181.194:8080}"
JIM_USER="${JIM_USER:-m.nasiri}"
JIM_PASS="${JIM_PASS:-Man@782761}"

if echo "$JIM_BASE" | grep -Eq '7gtech|193\\.162\\.129'; then
  fail "refusing to run against Production ($JIM_BASE)"
  finish
fi

require_cmd curl
TMP="$(mktemp)"
trap 'rm -f "$TMP" "$TMP.page"' EXIT

section "Chat page + WRM URL"
CODE="$(curl -s -u "$JIM_USER:$JIM_PASS" -o "$TMP.page" -w '%{http_code}' "$JIM_BASE/plugins/servlet/jim/chat" || echo 000)"
if [ "$CODE" = "200" ]; then
  pass "chat page 200"
else
  fail "chat page HTTP $CODE"
  finish
fi

JS_PATH="$(grep -o '/s/[^"]*jim-messenger-resources\.js' "$TMP.page" | head -1 || true)"
if [ -z "$JS_PATH" ]; then
  fail "no jim-messenger-resources.js batch URL in chat HTML"
  finish
fi
echo "INFO  WRM $JS_PATH"
if echo "$JS_PATH" | grep -q "$PLUGIN_BASELINE"; then
  pass "WRM URL contains $PLUGIN_BASELINE"
else
  fail "WRM URL missing baseline version ($PLUGIN_BASELINE)"
fi

section "Live batch markers"
curl -s -u "$JIM_USER:$JIM_PASS" -o "$TMP" "$JIM_BASE$JS_PATH"
[ -s "$TMP" ] || { fail "empty WRM batch"; finish; }
pass "WRM batch downloaded ($(wc -c < "$TMP") bytes)"

need() {
  if grep -q "$1" "$TMP"; then
    pass "live: $2"
  else
    fail "live missing: $2"
  fi
}
need 'data-goto-message' 'shared-gallery data-goto-message'
need 'data-goto-conversation' 'shared-gallery data-goto-conversation'
need 'jim-message-highlight' 'jim-message-highlight'
need 'messagesAround' 'messagesAround'
need 'open-mention' 'mention badge'
need 'گزارش‌ها' 'گزارش‌ها'
need 'منشن گزارش' 'منشن گزارش'
need 'گزارش‌های دریافتی' 'گزارش‌های دریافتی'
need 'REPORT_SUBMITTED' 'REPORT_SUBMITTED'
need 'باز کردن پیام' 'باز کردن پیام'
need 'Saved Messages' 'Saved Messages'

if grep -q '#jim-message-list \[data-message-id' "$TMP"; then
  fail "old querySelector-only shared-open handler still in live batch"
else
  pass "old shared-open querySelector path absent"
fi

finish
