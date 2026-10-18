#!/usr/bin/env bash
# DEV API smoke. Read-only GETs plus existing search/shared/around.
# Does not mutate Production. Override with JIM_BASE / JIM_USER / JIM_PASS.
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
require_cmd python3

http_get() {
  local path="$1"
  curl -s -u "$JIM_USER:$JIM_PASS" -o /tmp/jim-smoke.body -w '%{http_code}' "$JIM_BASE$path" || echo 000
}

section "Health"
for p in /rest/jim/1.0/health /rest/jim/1.0/ao-health /rest/corbit-mobile/1.0/health /rest/corbit-mobile/1.0/ao-health; do
  CODE="$(http_get "$p")"
  if [ "$CODE" = "200" ]; then
    pass "$p 200"
  else
    fail "$p HTTP $CODE"
  fi
done

section "Conversations"
CODE="$(http_get "/rest/jim/1.0/conversations")"
if [ "$CODE" = "200" ]; then
  pass "conversations 200"
else
  fail "conversations HTTP $CODE"
  finish
fi

CID="$(python3 - <<'PY'
import json
d=json.load(open('/tmp/jim-smoke.body'))
items=d if isinstance(d,list) else d.get('items') or d.get('conversations') or []
cid=None
for c in items:
    if c.get('id') and c.get('type') in ('DIRECT','GROUP','SELF') or c.get('self'):
        cid=c.get('id')
        if c.get('type')=='DIRECT' or c.get('self'):
            break
print(cid or '')
PY
)"
if [ -n "$CID" ]; then
  pass "picked conversationId=$CID"
else
  fail "no conversation id in list"
  finish
fi

section "Search DTO"
CODE="$(http_get "/rest/jim/1.0/conversations/search?q=at&limit=3")"
if [ "$CODE" = "200" ]; then
  pass "search 200"
  python3 - <<'PY' || fail "search DTO shape"
import json,sys
d=json.load(open('/tmp/jim-smoke.body'))
ok=True
for k in ('results','query'):
    if k not in d:
        print('missing',k); ok=False
hits=d.get('results') or []
if hits:
    h=hits[0]
    for k in ('conversationId','messageId'):
        if k not in h:
            print('hit missing',k); ok=False
    print('INFO  first hit conversationId=%s messageId=%s' % (h.get('conversationId'), h.get('messageId')))
else:
    print('INFO  zero hits (DTO keys still present)')
sys.exit(0 if ok else 1)
PY
  [ $? -eq 0 ] && pass "search DTO has conversationId/messageId when hits exist"
else
  fail "search HTTP $CODE"
fi

section "Shared + around"
for typ in files media links; do
  CODE="$(http_get "/rest/jim/1.0/conversations/$CID/shared?type=$typ&limit=2")"
  if [ "$CODE" != "200" ]; then
    fail "shared type=$typ HTTP $CODE"
    continue
  fi
  pass "shared type=$typ 200"
  python3 - "$typ" <<'PY' || fail "shared DTO $1"
import json,sys
typ=sys.argv[1]
d=json.load(open('/tmp/jim-smoke.body'))
items=d.get('items') or []
if not items:
    print('INFO  no %s items' % typ)
    sys.exit(0)
it=items[0]
missing=[k for k in ('conversationId','messageId') if it.get(k) in (None,'')]
if missing:
    print('missing',missing); sys.exit(1)
print('INFO  %s itemId=%s conversationId=%s messageId=%s' % (typ, it.get('itemId'), it.get('conversationId'), it.get('messageId')))
open('/tmp/jim-smoke.mid','w').write(str(it.get('messageId')))
sys.exit(0)
PY
done

if [ -f /tmp/jim-smoke.mid ]; then
  MID="$(cat /tmp/jim-smoke.mid)"
  CODE="$(http_get "/rest/jim/1.0/conversations/$CID/messages/around/$MID?before=5&after=5")"
  if [ "$CODE" = "200" ]; then
    pass "around/$MID 200"
    python3 - "$MID" <<'PY' || fail "around missing target"
import json,sys
mid=int(sys.argv[1])
d=json.load(open('/tmp/jim-smoke.body'))
msgs=d.get('items') or d.get('messages') or []
ids=[m.get('id') for m in msgs if isinstance(m,dict)]
sys.exit(0 if mid in ids else 1)
PY
    [ $? -eq 0 ] && pass "around page contains messageId=$MID"
  else
    fail "around HTTP $CODE"
  fi
else
  skip "no shared messageId to around-fetch"
fi

section "Admin pages (HTTP)"
for p in /plugins/servlet/jim/admin /plugins/servlet/jim/mcp-admin; do
  CODE="$(http_get "$p")"
  case "$CODE" in
    200|302) pass "$p $CODE" ;;
    *) fail "$p HTTP $CODE" ;;
  esac
done

rm -f /tmp/jim-smoke.body /tmp/jim-smoke.mid
finish
