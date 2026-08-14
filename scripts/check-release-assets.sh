#!/usr/bin/env bash
# Source + packaging safety. Does not talk to Production.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/common.sh
. "$ROOT/scripts/lib/common.sh"

JS="$ROOT/src/main/resources/js/jim-messenger.js"
API="$ROOT/src/main/resources/js/jim-api.js"
XML="$ROOT/src/main/resources/atlassian-plugin.xml"
POM="$ROOT/pom.xml"
RES="$ROOT/src/main/resources"

section "Version"
if grep -q "<version>$PLUGIN_BASELINE</version>" "$POM"; then
  pass "pom version is $PLUGIN_BASELINE"
else
  fail "pom version is not $PLUGIN_BASELINE"
  grep -m1 '<version>' "$POM" || true
fi

section "No stale min in source"
if find "$RES" \( -name 'jim-*-min.js' -o -name 'jim-*-min.css' \) | grep -q .; then
  fail "jim-*-min.js/css present under src/main/resources (WRM shadow risk)"
  find "$RES" \( -name 'jim-*-min.js' -o -name 'jim-*-min.css' \)
else
  pass "no jim-*-min.js/css in source resources"
fi

section "WRM resource paths"
if grep -q 'name="jim-messenger.js" location="/js/jim-messenger.js"' "$XML"; then
  pass "WRM points at jim-messenger.js"
else
  fail "WRM messenger JS path missing"
fi
if grep -q 'name="jim-messenger.css" location="/css/jim-messenger.css"' "$XML"; then
  pass "WRM points at jim-messenger.css"
else
  fail "WRM messenger CSS path missing"
fi

section "Required JS handlers (source)"
need_js() {
  if grep -q "$1" "$JS"; then
    pass "js: $2"
  else
    fail "js missing: $2 ($1)"
  fi
}
need_js 'function openConversationAtMessage' 'openConversationAtMessage'
need_js 'openConversationAtMessage(conversationId, messageId)' 'shared/search jump call'
need_js 'data-goto-message' 'shared-gallery data-goto-message'
need_js 'jim-message-highlight' 'jim-message-highlight'
need_js 'function openForwardModalForMessages' 'openForwardModalForMessages'
need_js "data-action=\"open-mention\"" 'mention badge handler'
need_js 'REPORT_SUBMITTED' 'REPORT_SUBMITTED'
need_js 'REPORT_SUBMITTED_TO_ME' 'REPORT_SUBMITTED_TO_ME'
need_js 'REPORT_REPLY' 'REPORT_REPLY'
need_js 'REPORT_MENTION' 'REPORT_MENTION'
need_js "key: 'reports'" 'reports filter'
need_js "key: 'reportMentions'" 'reportMentions filter'
need_js "key: 'reportsReceived'" 'reportsReceived filter'
need_js 'گزارش‌ها' 'Persian گزارش‌ها'
need_js 'منشن گزارش' 'Persian منشن گزارش'
need_js 'گزارش‌های دریافتی' 'Persian گزارش‌های دریافتی'

if grep -q 'messagesAround' "$API"; then
  pass "jim-api.js messagesAround"
else
  fail "jim-api.js missing messagesAround"
fi

finish
