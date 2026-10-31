#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Security verification matrix for the GMS backend (mandate §7, §15, §27).
#
#   ./scripts/verify-security.sh
#
# Prerequisites
#   * backend reachable at GMS_BASE_URL (default http://localhost:8080)
#   * an account whose role satisfies the admin checks
#       GMS_TEST_USERNAME / GMS_TEST_PASSWORD
#     Create one against a disposable database with the bootstrap:
#       APP_SUPERADMIN_ENABLED=true APP_SUPERADMIN_USERNAME=... \
#       APP_SUPERADMIN_PASSWORD=... APP_SUPERADMIN_EMAIL=... \
#       APP_SUPERADMIN_FULL_NAME=... mvn spring-boot:run -Dspring-boot.run.profiles=dev
#   * docker + the local postgres container, used to read the throttling columns
#
# The script locks the test account while exercising throttling and unlocks it
# again on exit. Never point it at production: it submits failed logins.
# Credentials are read from the environment; nothing here hardcodes one.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

BASE=${GMS_BASE_URL:-http://localhost:8080}
USER_NAME=${GMS_TEST_USERNAME:?set GMS_TEST_USERNAME}
PASSWORD=${GMS_TEST_PASSWORD:?set GMS_TEST_PASSWORD}
DB_CONTAINER=${GMS_DB_CONTAINER:-gms-postgres}
DB_USER=${DB_USERNAME:-gms_user}
DB_NAME=${DB_NAME:-gms}
# The value `JwtTokenProvider.java` carried as the `@Value` default before this branch, i.e. the key
# every clone of the repository could read. It is a published insecure default, not a secret that
# anyone had to keep, and this script's whole purpose is to prove the application no longer accepts
# it. If the application still signed with it, the forged request below would return 200.
RETIRED_DEFAULT_SECRET='change-me-please-change-me-please-change-me-please'

failures=0
checks=0

record() { # record <name> <expected> <actual>
  checks=$((checks + 1))
  if [[ "$2" == "$3" ]]; then
    printf '  PASS  %-46s %s\n' "$1" "$3"
  else
    printf '  FAIL  %-46s expected %s, got %s\n' "$1" "$2" "$3"
    failures=$((failures + 1))
  fi
}

code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

body() {
  printf '{"username":"%s","password":"%s"}' "$USER_NAME" "$1"
}

login_token() {
  local response
  response=$(curl -s -X POST -H 'Content-Type: application/json' -d "$(body "$PASSWORD")" \
    "$BASE/api/v1/auth/login")
  printf '%s' "$response" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("accessToken",""))' 2>/dev/null || {
    printf '      login response: %s\n' "$response"
    return 1
  }
}

# A previous run of section 5 deliberately locks this account. The matrix reads
# its own state, so it must not inherit state left behind by an earlier execution.
unlock_test_user() {
  psql "update users set account_locked=false, login_attempts=0 where username='$USER_NAME';" >/dev/null
}

trap unlock_test_user EXIT

# A token signed with the secret that shipped as the repository default. If the
# application still accepts it, anyone who has read the old source can mint a
# SUPERADMIN credential for this deployment.
forged_token() {
  python3 - "$USER_NAME" "$RETIRED_DEFAULT_SECRET" <<'PY'
import base64, hashlib, hmac, json, sys, time
def b64(raw): return base64.urlsafe_b64encode(raw).rstrip(b"=")
subject, secret = sys.argv[1], sys.argv[2].encode()
header = b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
now = int(time.time())
payload = b64(json.dumps({"sub": subject, "roles": ["SUPERADMIN"], "schoolId": 1, "empId": 1,
                          "iss": "gms", "iat": now, "exp": now + 3600},
                         separators=(",", ":")).encode())
signature = b64(hmac.new(secret, header + b"." + payload, hashlib.sha256).digest())
print((header + b"." + payload + b"." + signature).decode())
PY
}

psql() { docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c "$1"; }

echo "1. Anonymous surface"
record "GET /actuator/health is reachable"      "200" "$(code "$BASE/actuator/health")"
record "GET /api/v1/ping is reachable"          "200" "$(code "$BASE/api/v1/ping")"
record "GET /actuator/info requires a role"     "401" "$(code "$BASE/actuator/info")"
record "GET /api/v1/students requires a token"  "401" "$(code "$BASE/api/v1/students")"
record "POST /auth/self-register is closed"     "401" \
  "$(code -X POST -H 'Content-Type: application/json' -d '{}' "$BASE/api/v1/auth/self-register")"

echo "2. Credential handling"
unlock_test_user
record "wrong password is refused"              "401" \
  "$(code -X POST -H 'Content-Type: application/json' -d "$(body 'definitely-not-the-password')" "$BASE/api/v1/auth/login")"
TOKEN=$(login_token)
if [[ -n "$TOKEN" ]]; then
  printf '  PASS  %-46s obtained\n' "correct password issues a token"
else
  printf '  FAIL  %-46s no accessToken in the response\n' "correct password issues a token"
  failures=$((failures + 1))
fi
checks=$((checks + 1))
record "forged default-secret JWT is refused"   "401" \
  "$(code -H "Authorization: Bearer $(forged_token)" "$BASE/api/v1/students")"

echo "3. Authorisation"
record "GET /api/v1/users/me returns a DTO"     "200" "$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/users/me")"
record "GET /api/v1/users is allowed for admin" "200" "$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/users")"
record "student self-service blocks an admin"   "403" "$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/students/my-fees")"
record "actuator/info opens for SUPERADMIN"     "200" "$(code -H "Authorization: Bearer $TOKEN" "$BASE/actuator/info")"
record "unlisted path fails closed"             "403" "$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/not-a-route")"
LEAK=$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/not-a-route")
printf '%s' "$LEAK" | grep -qiE 'exception|stack|caused by|jdbc|hibernate' \
  && record "denied body leaks no internals" "clean" "leaked" \
  || record "denied body leaks no internals" "clean" "clean"

echo "4. Error shape"
BAD=$(curl -s -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{"fullName":"x","username":"y","email":"not-an-email"}' "$BASE/api/v1/users/create")
printf '%s' "$BAD" | grep -q '"fields"' \
  && record "validation reports per-field errors" "fields" "fields" \
  || record "validation reports per-field errors" "fields" "absent"
printf '%s' "$BAD" | grep -qiE 'exception|stackTrace|rootCause' \
  && record "validation body leaks no internals" "clean" "leaked" \
  || record "validation body leaks no internals" "clean" "clean"

echo "5. Brute-force throttling"
unlock_test_user
for _ in 1 2 3 4 5 6 7 8 9 10; do
  code -X POST -H 'Content-Type: application/json' -d "$(body 'throttling-probe')" "$BASE/api/v1/auth/login" >/dev/null
done
STATE=$(psql "select account_locked||'|'||login_attempts from users where username='$USER_NAME';")
record "ten failures set account_locked"        "true" "${STATE%%|*}"
record "correct password after lock is refused" "401" \
  "$(code -X POST -H 'Content-Type: application/json' -d "$(body "$PASSWORD")" "$BASE/api/v1/auth/login")"
unlock_test_user

echo
echo "GMS_BASE_URL=$BASE  checks=$checks  failures=$failures"
[[ $failures -eq 0 ]] || exit 1
