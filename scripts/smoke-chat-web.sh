#!/usr/bin/env bash
# DEV web page smoke (chat + admin HTML). Does not mutate Production.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/common.sh
. "$ROOT/scripts/lib/common.sh"

JIM_BASE="${JIM_BASE:-http://185.83.181.194:8080}"
JIM_USER="${JIM_USER:-m.nasiri}"
require_jim_pass

if echo "$JIM_BASE" | grep -Eq '7gtech|193\\.162\\.129'; then
  fail "refusing to run against Production ($JIM_BASE)"
  finish
fi

require_cmd curl

section "Pages"
for p in /plugins/servlet/jim/chat /plugins/servlet/jim/admin /plugins/servlet/jim/mcp-admin; do
  CODE="$(curl -s -u "$JIM_USER:$JIM_PASS" -o /tmp/jim-web.body -w '%{http_code}' "$JIM_BASE$p" || echo 000)"
  if [ "$CODE" = "200" ]; then
    pass "$p 200"
  else
    fail "$p HTTP $CODE"
  fi
done

section "Chat shell IDs"
CODE="$(curl -s -u "$JIM_USER:$JIM_PASS" -o /tmp/jim-web.body -w '%{http_code}' "$JIM_BASE/plugins/servlet/jim/chat" || echo 000)"
if [ "$CODE" = "200" ]; then
  for id in jim-messenger-app jim-message-input jim-send-button jim-conversation-list jim-message-list; do
    if grep -q "$id" /tmp/jim-web.body; then
      pass "html id $id"
    else
      fail "html missing $id"
    fi
  done
  if grep -q 'jim-messenger-resources' /tmp/jim-web.body; then
    pass "WRM messenger resources referenced"
  else
    fail "WRM messenger resources missing from chat HTML"
  fi
fi

rm -f /tmp/jim-web.body
finish
