#!/bin/bash
# Milestone 6/7 end-to-end smoke test.
# Runs the real application against a COPY of data.db (target/smoke.db) on a
# spare port, exercises the new admin console API/pages over HTTP, then stops.
# The production data.db is never touched.
#
# The initial administrator password used below is a throwaway value for that
# copy only; it is not a credential for any real deployment.
set -u

cd "$(dirname "$0")/.." || exit 1
BASE=http://127.0.0.1:18111
WORK=target/smoke
rm -rf "$WORK"; mkdir -p "$WORK"
cp data.db "$WORK/smoke.db"

echo "=== starting app on port 18111 (db copy: $WORK/smoke.db) ==="
JVM_ARGS="-Dspring.datasource.url=jdbc:sqlite:$WORK/smoke.db?busy_timeout=5000"
JVM_ARGS="$JVM_ARGS -Dserver.port=18111"
JVM_ARGS="$JVM_ARGS -Dlifecomposer.admin.initial-password=Smoke-Only-Passw0rd!2026"
JVM_ARGS="$JVM_ARGS -Dembedding.enabled=false"
JVM_ARGS="$JVM_ARGS -Dspring.devtools.restart.enabled=false"
for uc in qa planning profile sql chat; do
  JVM_ARGS="$JVM_ARGS -Dllm.useCases.$uc.enabled=false"
done

nohup ./mvnw -o spring-boot:run -Dspring-boot.run.jvmArguments="$JVM_ARGS" > "$WORK/app.log" 2>&1 &
APP_PID=$!

cleanup() {
  echo "=== stopping app (pid $APP_PID) ==="
  for pid in $(lsof -ti tcp:18111 2>/dev/null); do kill "$pid" 2>/dev/null; done
  kill "$APP_PID" 2>/dev/null
  sleep 2
}
trap cleanup EXIT

echo "=== waiting for startup ==="
READY=no
for i in $(seq 1 90); do
  if curl -s -o /dev/null -m 2 "$BASE/api/qa/health"; then READY=yes; break; fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "application process exited during startup"; tail -40 "$WORK/app.log"; exit 1
  fi
  sleep 2
done
echo "ready=$READY after ${i} polls"
if [ "$READY" != "yes" ]; then tail -60 "$WORK/app.log"; exit 1; fi

echo
echo "=== bootstrap evidence in the startup log ==="
grep -E "admin_bootstrap|admin_password_rotated|Import mode|Started LifeComposer" "$WORK/app.log" | head -5

echo
echo "=== anonymous access is rejected ==="
for p in /admin /admin/user /api/admin/dashboard /api/admin/users; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE$p")
  echo "  $p -> $code"
done

JAR="$WORK/cookies.txt"
TOKEN=$(curl -s -c "$JAR" "$BASE/api/csrf" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
echo
echo "csrf token acquired: ${TOKEN:0:8}..."

post_json() { # url body
  curl -s -b "$JAR" -c "$JAR" -X POST "$1" \
    -H "X-XSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' -H 'User-Agent: SmokeClient/1.0' \
    -d "$2" -o /dev/null -w '%{http_code}'
}

echo "register smoke admin user -> $(post_json "$BASE/api/users/register" '{"username":"smokeadmin","password":"pass123"}')"

python3 scripts/promote_smoke_admin.py "$WORK/smoke.db"

TOKEN=$(curl -s -b "$JAR" -c "$JAR" "$BASE/api/csrf" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
echo "login -> $(post_json "$BASE/api/users/login" '{"username":"smokeadmin","password":"pass123"}')"

echo
echo "=== admin API over HTTP ==="
for p in "/api/admin/dashboard" "/api/admin/users?pageSize=3" "/api/admin/user-profiles" \
         "/api/admin/planning-history?pageSize=2" "/api/admin/chat-messages?pageSize=2" \
         "/api/admin/feedback" "/api/admin/college-credit-rules?pageSize=2" \
         "/api/admin/credit-activities" "/api/admin/resources?pageSize=2" \
         "/api/admin/rag-chunks?pageSize=2" "/api/admin/capability-tags" \
         "/api/admin/capability-reference?section=skill_profiles" "/api/admin/chat-usage" \
         "/api/admin/users/1/profile" "/api/admin/rag-chunks/rag_001" "/api/admin/feedback/999999" \
         "/api/admin/users?sort=password_hash" "/api/admin/users?evil=1"; do
  out=$(curl -s -b "$JAR" -w '\n%{http_code}' "$BASE$p")
  code=$(echo "$out" | tail -1)
  body=$(printf '%s' "$out" | sed '$d' | cut -c1-160)
  echo "  [$code] $p"
  echo "        $body"
done

echo
echo "=== privacy spot checks (must all print 0) ==="
for p in "/api/admin/users" "/api/admin/rag-chunks" "/api/admin/credit-activities"; do
  n=$(curl -s -b "$JAR" "$BASE$p" | grep -c -E 'password_hash|embedding_json|certificate_ref' || true)
  echo "  forbidden-field hits in $p = $n"
done

echo
echo "=== console pages and shared assets ==="
for p in /admin /admin/user /admin/profile /admin/planning /admin/chat /admin/feedback_management \
         /admin/credit-rules /admin/credit-activities /admin/resources /admin/rag \
         /admin/capability-tags /admin/capability-reference /admin/usage /admin.js /admin.css /csrf.js; do
  code=$(curl -s -b "$JAR" -o "$WORK/page.out" -w '%{http_code}' "$BASE$p")
  size=$(wc -c < "$WORK/page.out" | tr -d ' ')
  view=$(grep -o 'data-admin-view="[A-Za-z]*"' "$WORK/page.out" | head -1)
  echo "  [$code] $p bytes=$size $view"
done

echo
echo "=== quota reset + weak temporary password rejection ==="
TARGET_ID=$(curl -s -b "$JAR" "$BASE/api/admin/users?q=smokeadmin" | python3 -c 'import sys,json;print(json.load(sys.stdin)["items"][0]["id"])')
echo "  reset today quota -> $(curl -s -b "$JAR" -X POST "$BASE/api/users/admin/chat-quota/reset/$TARGET_ID" -H "X-XSRF-TOKEN: $TOKEN" -o /dev/null -w '%{http_code}')"
echo "  weak temp password (000000) -> $(curl -s -b "$JAR" -X POST "$BASE/api/users/admin/reset-password/$TARGET_ID" -H "X-XSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' -d '{"newPassword":"000000"}' -o "$WORK/weak.out" -w '%{http_code}')"
cat "$WORK/weak.out"; echo
echo "  strong temp password -> $(curl -s -b "$JAR" -X POST "$BASE/api/users/admin/reset-password/$TARGET_ID" -H "X-XSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' -d '{"newPassword":"SmokeTempPass!2026"}' -o "$WORK/strong.out" -w '%{http_code}')"
cat "$WORK/strong.out"; echo

echo
echo "=== real data.db untouched? ==="
echo "  data.db mtime: $(stat -f '%Sm' data.db)"
echo "  smoke.db exists: $(test -f "$WORK/smoke.db" && echo yes)"

echo
echo "SMOKE TEST DONE"
