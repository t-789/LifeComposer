#!/bin/bash
# Review follow-up smoke test: real HTTP verification of the temporary-password
# lifecycle, session invalidation and the race-free last-administrator guard.
#
# Runs the real application against a COPY of data.db on a spare port, then stops.
# The production data.db is never touched.
#
# Every password literal in this file is a throwaway value for that copy only.
set -u

cd "$(dirname "$0")/.." || exit 1
BASE=http://127.0.0.1:18112
PORT=18112
WORK=target/smoke-temp
rm -rf "$WORK"; mkdir -p "$WORK"
cp data.db "$WORK/smoke.db"

echo "=== starting app on port $PORT (db copy: $WORK/smoke.db) ==="
JVM_ARGS="-Dspring.datasource.url=jdbc:sqlite:$WORK/smoke.db?busy_timeout=5000&transaction_mode=immediate"
JVM_ARGS="$JVM_ARGS -Dserver.port=$PORT"
JVM_ARGS="$JVM_ARGS -Dlifecomposer.admin.initial-password=Smoke-Only-Passw0rd!2026"
JVM_ARGS="$JVM_ARGS -Dembedding.enabled=false -Dspring.devtools.restart.enabled=false"
for uc in qa planning profile sql chat; do
  JVM_ARGS="$JVM_ARGS -Dllm.useCases.$uc.enabled=false"
done

nohup ./mvnw -o spring-boot:run -Dspring-boot.run.jvmArguments="$JVM_ARGS" > "$WORK/app.log" 2>&1 &
APP_PID=$!

cleanup() {
  echo "=== stopping app ==="
  for pid in $(lsof -ti tcp:$PORT 2>/dev/null); do kill "$pid" 2>/dev/null; done
  kill "$APP_PID" 2>/dev/null
  sleep 2
}
trap cleanup EXIT

for i in $(seq 1 90); do
  if curl -s -o /dev/null -m 2 "$BASE/api/qa/health"; then break; fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then echo "app died"; tail -40 "$WORK/app.log"; exit 1; fi
  sleep 2
done
echo "app ready after ${i} polls"

# --------------------------------------------------------------------- helpers
token_for() { curl -s -b "$1" -c "$1" "$BASE/api/csrf" | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])'; }

post() { # jar path json -> prints "<body>\n<status>"
  local jar="$1" path="$2" json="$3" tok
  tok=$(token_for "$jar")
  curl -s -b "$jar" -c "$jar" -X POST "$BASE$path" \
    -H "X-XSRF-TOKEN: $tok" -H 'Content-Type: application/json' -H 'User-Agent: SmokeClient/1.0' \
    -d "$json" -w '\n%{http_code}'
}

put() { # jar path -> prints "<body>\n<status>"
  local jar="$1" path="$2" tok
  tok=$(token_for "$jar")
  curl -s -b "$jar" -c "$jar" -X PUT "$BASE$path" \
    -H "X-XSRF-TOKEN: $tok" -H 'User-Agent: SmokeClient/1.0' -w '\n%{http_code}'
}

put_json() { # jar path json -> prints "<body>\n<status>"
  local jar="$1" path="$2" json="$3" tok
  tok=$(token_for "$jar")
  curl -s -b "$jar" -c "$jar" -X PUT "$BASE$path" \
    -H "X-XSRF-TOKEN: $tok" -H 'Content-Type: application/json' -H 'User-Agent: SmokeClient/1.0' \
    -d "$json" -w '\n%{http_code}'
}

get_code() { curl -s -b "$1" -o "$WORK/last-body.json" -w '%{http_code}' "$BASE$2"; }

status_of() { echo "$1" | tail -1; }
body_of() { echo "$1" | sed '$d'; }

ADMIN_JAR="$WORK/admin.jar"
VICTIM_JAR="$WORK/victim.jar"
OTHER_JAR="$WORK/other.jar"
: > "$ADMIN_JAR"; : > "$VICTIM_JAR"; : > "$OTHER_JAR"

echo
echo "=== prepare an administrator and a target account ==="
echo "  register smokeadmin      -> $(status_of "$(post "$ADMIN_JAR" /api/users/register '{"username":"smokeadmin","password":"pass123"}')")"
echo "  register smoketarget     -> $(status_of "$(post "$ADMIN_JAR" /api/users/register '{"username":"smoketarget","password":"pass123"}')")"
python3 scripts/smoke-temp-password-db.py promote "$WORK/smoke.db"

TOKEN=$(token_for "$ADMIN_JAR")
echo "  admin login              -> $(status_of "$(post "$ADMIN_JAR" /api/users/login '{"username":"smokeadmin","password":"pass123"}')")"
TARGET_ID=$(curl -s -b "$ADMIN_JAR" "$BASE/api/admin/users?q=smoketarget" | python3 -c 'import sys,json;print(json.load(sys.stdin)["items"][0]["id"])')
echo "  smoketarget id = $TARGET_ID"

echo
echo "=== 1) the target logs in on 'device A' before the reset ==="
echo "  device A login           -> $(status_of "$(post "$VICTIM_JAR" /api/users/login '{"username":"smoketarget","password":"pass123"}')")"
echo "  device A /api/users/current -> $(get_code "$VICTIM_JAR" /api/users/current)"

echo
echo "=== 2) administrator issues a temporary password ==="
RESET=$(post "$ADMIN_JAR" "/api/users/admin/reset-password/$TARGET_ID" '{"newPassword":"Smoke-Temp-Pass!2026"}')
echo "  reset status             -> $(status_of "$RESET")"
echo "  reset body               -> $(body_of "$RESET")"
echo "  body contains password?  -> $(body_of "$RESET" | grep -c 'Smoke-Temp-Pass' )  (must be 0)"
echo "  weak temp password       -> $(status_of "$(post "$ADMIN_JAR" "/api/users/admin/reset-password/$TARGET_ID" '{"newPassword":"000000"}')")  (must be 400)"

echo
echo "=== 3) the pre-reset session is now dead ==="
echo "  device A /api/users/current -> $(get_code "$VICTIM_JAR" /api/users/current) $(cat "$WORK/last-body.json" | head -c 80)"

echo
echo "=== 4) login with the temporary password forces a change ==="
NEW_JAR="$WORK/temp.jar"; : > "$NEW_JAR"
LOGIN=$(post "$NEW_JAR" /api/users/login '{"username":"smoketarget","password":"Smoke-Temp-Pass!2026"}')
echo "  login status             -> $(status_of "$LOGIN")"
echo "  login body               -> $(body_of "$LOGIN")"
echo "  /api/profiles/me         -> $(get_code "$NEW_JAR" /api/profiles/me) $(cat "$WORK/last-body.json" | head -c 80)"
echo "  /api/chat/history        -> $(get_code "$NEW_JAR" /api/chat/history) $(cat "$WORK/last-body.json" | head -c 80)"
echo "  /front/change-password   -> $(get_code "$NEW_JAR" /front/change-password)"

echo
echo "=== 5) forced change ==="
echo "  wrong current password   -> $(status_of "$(post "$NEW_JAR" /api/users/password '{"currentPassword":"nope","newPassword":"Smoke-Own-Pass!2026"}')")  (must be 400)"
echo "  weak new password        -> $(status_of "$(post "$NEW_JAR" /api/users/password '{"currentPassword":"Smoke-Temp-Pass!2026","newPassword":"short"}')")  (must be 400)"
CHANGE=$(post "$NEW_JAR" /api/users/password '{"currentPassword":"Smoke-Temp-Pass!2026","newPassword":"Smoke-Own-Pass!2026"}')
echo "  valid change             -> $(status_of "$CHANGE") $(body_of "$CHANGE")"
echo "  same session still works -> $(get_code "$NEW_JAR" /api/users/current)"
echo "  temp password dead       -> $(status_of "$(post "$OTHER_JAR" /api/users/login '{"username":"smoketarget","password":"Smoke-Temp-Pass!2026"}')")  (must be 400)"
echo "  new password works       -> $(status_of "$(post "$OTHER_JAR" /api/users/login '{"username":"smoketarget","password":"Smoke-Own-Pass!2026"}')")"

echo
echo "=== 6) temporary password expiry ==="
post "$ADMIN_JAR" "/api/users/admin/reset-password/$TARGET_ID" '{"newPassword":"Smoke-Temp-Pass!2026"}' > /dev/null
python3 scripts/smoke-temp-password-db.py expire "$WORK/smoke.db"
EXPIRE_JAR="$WORK/expire.jar"; : > "$EXPIRE_JAR"
EXPIRED=$(post "$EXPIRE_JAR" /api/users/login '{"username":"smoketarget","password":"Smoke-Temp-Pass!2026"}')
echo "  login with expired temp  -> $(status_of "$EXPIRED") $(body_of "$EXPIRED")  (must be 403)"

echo
echo "=== 7) last-administrator guard over HTTP ==="
# Demote every administrator except smokeadmin, then confirm the last one is protected.
OTHER_ADMINS=$(curl -s -b "$ADMIN_JAR" "$BASE/api/admin/users?type=2&pageSize=200" | python3 -c '
import sys, json
items = json.load(sys.stdin)["items"]
print(" ".join(str(i["id"]) for i in items if i["username"] != "smokeadmin"))')
for id in $OTHER_ADMINS; do
  echo "  demote admin #$id           -> $(status_of "$(put "$ADMIN_JAR" "/api/users/$id/revoke-admin")")"
done
SELF_ID=$(curl -s -b "$ADMIN_JAR" "$BASE/api/admin/users?q=smokeadmin" | python3 -c 'import sys,json;print(json.load(sys.stdin)["items"][0]["id"])')
echo "  admins left              -> $(curl -s -b "$ADMIN_JAR" "$BASE/api/admin/users?type=2&pageSize=200" | python3 -c 'import sys,json;print(json.load(sys.stdin)["total"])')"
echo "  demote the last admin    -> $(status_of "$(put "$ADMIN_JAR" "/api/users/$SELF_ID/revoke-admin")")  (must be 409)"
echo "  ban the last admin       -> $(status_of "$(put_json "$ADMIN_JAR" "/api/users/$SELF_ID/ban" '{"banTime":"0"}')")  (must be 409)"
echo "  ban with no body         -> $(status_of "$(put "$ADMIN_JAR" "/api/users/$SELF_ID/ban")")  (must be 400, not 500)"

echo "=== 8) production data.db untouched ==="
echo "  data.db mtime: $(stat -f '%Sm' data.db)"

echo
echo "TEMP PASSWORD SMOKE TEST DONE"
