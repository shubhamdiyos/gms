#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Live verification for Stage 3 card C-2 — "one tenant resolution path".
#
#   ./scripts/verify-c2-tenant-context.sh
#
# Before C-2 the tenant reached a service as a parameter: every controller read
# SecurityUtil.getSchoolIdFromToken(), which re-parsed and re-verified the JWT
# signature off the request, and handed the result to the service, which handed
# it to the next service. 325 call sites, 41 files.
#
# After C-2 the filter parses the claims once and puts the tenant in a
# request-scoped context; services and repositories read it from there and no
# longer accept it as an argument.
#
# What this proves, against a running instance and a real database:
#   * the tenant that a write lands in is the one in the token, and the caller
#     has no way to name a different one;
#   * a school-scoped read returns the caller's school and nothing else;
#   * the audit trail is written from the token's employee, through a nested
#     service call that no longer receives it as an argument;
#   * a cross-service check still refuses another school's row;
#   * a token that does not verify is refused;
#   * the platform owner, who is not a tenant, keeps its reach.
#
# Prerequisites
#   * a backend on GMS_BASE_URL (default http://localhost:8080) started against
#     a DISPOSABLE database, with the platform account bootstrapped:
#       APP_SUPERADMIN_ENABLED=true APP_SUPERADMIN_USERNAME=... \
#       APP_SUPERADMIN_PASSWORD=... APP_SUPERADMIN_EMAIL=... \
#       APP_SUPERADMIN_FULL_NAME=... java -jar target/gms-*.jar --spring.profiles.active=dev
#   * curl and jq
#   * for the row-level checks, GMS_VERIFY_DB set to the disposable database
#     name and the PostgreSQL container reachable through docker:
#       GMS_VERIFY_DB=gms_c2_verify ./scripts/verify-c2-tenant-context.sh
#     Without it those checks are reported SKIP, never PASS.
#
# The script CREATES two schools, two administrators, a teacher, a classroom and
# a student. Point it at a disposable database only.
#
# Credentials come from the environment; nothing here hardcodes one.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

BASE=${GMS_BASE_URL:-http://localhost:8080}
API="$BASE/api/v1"
SUPERUSER=${GMS_SUPERADMIN_USERNAME:?set GMS_SUPERADMIN_USERNAME}
SUPERPASSWORD=${GMS_SUPERADMIN_PASSWORD:?set GMS_SUPERADMIN_PASSWORD}
ADMIN_A_NAME=${GMS_SCHOOL_A_ADMIN_NAME:-Carla Principal}
ADMIN_B_NAME=${GMS_SCHOOL_B_ADMIN_NAME:-Damon Principal}
TEACHER_PASSWORD=${GMS_TEACHER_PASSWORD:-Teacher_Verify_2026!}

# The disposable database and the container that holds it. The row-level checks
# are skipped, loudly, when this is not set.
VERIFY_DB=${GMS_VERIFY_DB:-}
PG_CONTAINER=${GMS_PG_CONTAINER:-gms-postgres}
PG_USER=${GMS_PG_USER:-gms_user}

failures=0
checks=0
skipped=0
body=$(mktemp)
trap 'rm -f "$body"' EXIT

check() { # check <name> <expected> <actual>
  checks=$((checks + 1))
  if [[ "$2" == "$3" ]]; then
    printf 'PASS  %s (expected %s, got %s)\n' "$1" "$2" "$3"
  else
    printf 'FAIL  %s (expected %s, got %s)\n' "$1" "$2" "$3"
    failures=$((failures + 1))
  fi
}

refused() { # refused <name> <code>
  # The property is "the caller does not get or write the row", not the status code. `C-4` moved
  # ownership into the load, so another school's id resolves to nothing and the answer is a 404;
  # 403 remains what a role refusal answers. A 200 carrying somebody else's row is a failure here.
  checks=$((checks + 1))
  if [[ "$2" == "403" || "$2" == "404" ]]; then
    printf 'PASS  %s (refused with %s)\n' "$1" "$2"
  else
    printf 'FAIL  %s (expected a refusal -- 403 or 404 -- got %s)\n' "$1" "$2"
    failures=$((failures + 1))
  fi
}

skip() { # skip <name> <reason>
  skipped=$((skipped + 1))
  printf 'SKIP  %s (%s)\n' "$1" "$2"
}

call() { # call <method> <path> [token] [json]
  local method=$1 route=$2 token=${3:-} payload=${4:-}
  local args=(-s -o "$body" -w '%{http_code}' -X "$method" "$API$route")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$payload" ]] && args+=(-H 'Content-Type: application/json' -d "$payload")
  curl "${args[@]}"
}

token_of() { jq -r '.accessToken // empty' "$body"; }

admin_username() { # admin_username <full name>
  local first last
  first=$(printf '%s' "$1" | awk '{print $1}')
  last=$(printf '%s' "$1" | awk '{print $2}')
  printf '%s%s' "${first:0:1}" "$last" | tr 'A-Z' 'a-z'
}

sql() { # sql <statement>
  docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$VERIFY_DB" -tAc "$1" 2>/dev/null
}

echo "=== 1. Platform login ==="
code=$(call POST /auth/login "" "{\"username\":\"$SUPERUSER\",\"password\":\"$SUPERPASSWORD\"}")
check "superadmin login accepted" "200" "$code"
PLATFORM_TOKEN=$(token_of)
[[ -z "$PLATFORM_TOKEN" ]] && { echo "FATAL: no platform token"; exit 1; }

echo
echo "=== 2. The platform creates two schools ==="
code=$(call POST /schools/create "$PLATFORM_TOKEN" \
  '{"schoolName":"Eastfield High","schoolCode":"EFH001","adminFullName":"'"$ADMIN_A_NAME"'","adminEmail":"carla@eastfield.test"}')
check "school A created" "200" "$code"
code=$(call POST /schools/create "$PLATFORM_TOKEN" \
  '{"schoolName":"Westholme Academy","schoolCode":"WHA002","adminFullName":"'"$ADMIN_B_NAME"'","adminEmail":"damon@westholme.test"}')
check "school B created" "200" "$code"

echo
echo "=== 3. Both school administrators log in ==="
ADMIN_A_USER=$(admin_username "$ADMIN_A_NAME")
ADMIN_B_USER=$(admin_username "$ADMIN_B_NAME")
code=$(call POST /auth/login "" "{\"username\":\"$ADMIN_A_USER\",\"password\":\"$ADMIN_A_USER@123\"}")
check "school A admin login" "200" "$code"
TOKEN_A=$(token_of)
code=$(call POST /auth/login "" "{\"username\":\"$ADMIN_B_USER\",\"password\":\"$ADMIN_B_USER@123\"}")
check "school B admin login" "200" "$code"
TOKEN_B=$(token_of)
if [[ -z "$TOKEN_A" || -z "$TOKEN_B" ]]; then
  echo "FATAL: could not obtain both school administrator tokens"
  exit 1
fi

echo
echo "=== 4. School A builds its own tenant data ==="
code=$(call POST /classrooms/create "$TOKEN_A" '{"name":"Grade 7-A","grade":"7","section":"A"}')
check "classroom created in school A" "200" "$code"
CLASS_A=$(jq -r '.id' "$body")
code=$(call POST /subjects/create "$TOKEN_A" '{"name":"Physics","code":"PHYS"}')
check "subject created in school A" "200" "$code"
code=$(call POST /students/create "$TOKEN_A" \
  "{\"firstName\":\"Nina\",\"lastName\":\"Rao\",\"email\":\"nina.rao@eastfield.test\",\"gender\":\"FEMALE\",\"classId\":$CLASS_A}")
check "student created in school A" "200" "$code"
STUDENT_A=$(jq -r '.id' "$body")

echo
echo "=== 5. School B builds its own tenant data ==="
code=$(call POST /classrooms/create "$TOKEN_B" '{"name":"Grade 7-B","grade":"7","section":"B"}')
check "classroom created in school B" "200" "$code"
CLASS_B=$(jq -r '.id' "$body")
code=$(call POST /students/create "$TOKEN_B" \
  "{\"firstName\":\"Yusuf\",\"lastName\":\"Demir\",\"email\":\"yusuf.demir@westholme.test\",\"gender\":\"MALE\",\"classId\":$CLASS_B}")
check "student created in school B" "200" "$code"
STUDENT_B=$(jq -r '.id' "$body")

echo
echo "=== 6. The tenant is the one in the token ==="
# Every school-scoped read is now a query the service builds from the context,
# so the caller cannot widen it. Reading another school's row must still fail.
code=$(call GET "/students/$STUDENT_A" "$TOKEN_A")
check "admin A reads its own student" "200" "$code"
code=$(call GET "/students/$STUDENT_A" "$TOKEN_B")
refused "admin B refused another school's student" "$code"

code=$(call GET "/students" "$TOKEN_B")
check "admin B lists students" "200" "$code"
FOREIGN=$(jq '[.[] | select(.id == '"$STUDENT_A"')] | length' "$body")
check "school A's student is absent from school B's list" "0" "$FOREIGN"
DISTINCT=$(jq '[.[].school_id] | unique | length' "$body")
check "school B's list holds rows from exactly one school" "1" "$DISTINCT"
SCHOOL_B_SEEN=$(jq -r '.[0].school_id' "$body")

code=$(call GET "/students" "$TOKEN_A")
check "admin A lists students" "200" "$code"
FOREIGN=$(jq '[.[] | select(.id == '"$STUDENT_B"')] | length' "$body")
check "school B's student is absent from school A's list" "0" "$FOREIGN"
DISTINCT=$(jq '[.[].school_id] | unique | length' "$body")
check "school A's list holds rows from exactly one school" "1" "$DISTINCT"
SCHOOL_A_SEEN=$(jq -r '.[0].school_id' "$body")
if [[ "$SCHOOL_A_SEEN" == "$SCHOOL_B_SEEN" ]]; then
  check "the two schools are different tenants" "different" "same"
else
  check "the two schools are different tenants" "different" "different"
fi

echo
echo "=== 7. The audit trail is written from the token's employee ==="
# UserServiceImpl.createUserFromRequest resolves the creator through the
# context and no longer accepts it as an argument, so the audit trail cannot
# be pointed at somebody else's account by a caller.
code=$(call POST /users/create "$TOKEN_A" \
  "{\"username\":\"teacher.eastfield\",\"email\":\"teacher@eastfield.test\",\"password\":\"$TEACHER_PASSWORD\",\"fullName\":\"Tess Teacher\",\"roles\":[\"TEACHER\"]}")
check "teacher account created" "200" "$code"
TEACHER_USERNAME=$(jq -r '.username // empty' "$body")
code=$(call POST /auth/login "" "{\"username\":\"teacher.eastfield\",\"password\":\"$TEACHER_PASSWORD\"}")
check "teacher login" "200" "$code"
TEACHER_TOKEN=$(token_of)
code=$(call GET "/students" "$TEACHER_TOKEN")
check "teacher list refused by role" "403" "$code"

if [[ -n "$VERIFY_DB" ]]; then
  AUDIT_CREATOR=$(sql "SELECT u.created_by FROM users u WHERE u.username = 'teacher.eastfield'")
  AUDIT_SCHOOL=$(sql "SELECT u.school_id FROM users u WHERE u.username = 'teacher.eastfield'")
  ADMIN_A_USER_ID=$(sql "SELECT id FROM users WHERE username = '$ADMIN_A_USER'")
  SCHOOL_A_ID=$(sql "SELECT id FROM schools WHERE school_code = 'EFH001'")
  check "the teacher's row records the caller's account as its creator" \
    "$ADMIN_A_USER_ID" "$AUDIT_CREATOR"
  check "the teacher's row records the caller's school as its tenant" \
    "$SCHOOL_A_ID" "$AUDIT_SCHOOL"

  STUDENT_SCHOOL=$(sql "SELECT school_id FROM students WHERE id = $STUDENT_A")
  check "the student's row records the caller's school as its tenant" \
    "$SCHOOL_A_ID" "$STUDENT_SCHOOL"

  # A school-A call must not have written anything into school B.
  CROSS=$(sql "SELECT COUNT(*) FROM users WHERE school_id = (SELECT id FROM schools WHERE school_code='WHA002') AND created_by = $ADMIN_A_USER_ID")
  check "school A's administrator created nothing in school B" "0" "$CROSS"
else
  skip "row-level audit checks" "GMS_VERIFY_DB is not set"
fi

echo
echo "=== 8. A cross-service check still refuses another school's row ==="
# createStudentFromRequest asks classroomService for the classroom. Before `C-4` that load was
# unscoped and the service compared the row's school afterwards, answering 403. Now the load is
# scoped, so school A's caller cannot resolve school B's classroom at all and the answer is 404 --
# a different status for the same refusal, and the check that matters is the row-count one below:
# nothing was written.
code=$(call POST /students/create "$TOKEN_A" \
  "{\"firstName\":\"Omar\",\"lastName\":\"Said\",\"email\":\"omar.said@eastfield.test\",\"gender\":\"MALE\",\"classId\":$CLASS_B}")
refused "student in another school's classroom refused" "$code"
if [[ -n "$VERIFY_DB" ]]; then
  CREATED=$(sql "SELECT COUNT(*) FROM students WHERE email = 'omar.said@eastfield.test'")
  check "the refused student was not written" "0" "$CREATED"
else
  skip "the refused student was not written" "GMS_VERIFY_DB is not set"
fi

echo
echo "=== 9. A token that does not verify is refused ==="
code=$(call GET "/students" "${TOKEN_A}tampered")
check "a tampered token is refused" "401" "$code"
code=$(call GET "/students" "")
check "no token is refused" "401" "$code"

echo
echo "=== 10. The platform owner is not a tenant and keeps its reach ==="
code=$(call GET "/schools" "$PLATFORM_TOKEN")
check "superadmin lists schools" "200" "$code"
BOTH=$(jq '[.[] | select(.schoolCode == "EFH001" or .schoolCode == "WHA002")] | length' "$body")
check "superadmin sees both new schools" "2" "$BOTH"

echo
echo "==================================================="
printf 'PASSED: %d   FAILED: %d   SKIPPED: %d\n' "$checks" "$failures" "$skipped"
[[ $failures -eq 0 ]] || exit 1
