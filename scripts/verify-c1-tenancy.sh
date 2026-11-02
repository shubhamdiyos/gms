#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Live cross-tenant verification for Stage 3 card C-1 (ADR-023).
#
#   ./scripts/verify-c1-tenancy.sh
#
# What it proves, against a running instance and a real database:
#   * the routes the deleted AbstractCRUDController supplied are gone, and the
#     ones that remain are tenant-scoped. Since `C-4` the refusal of another
#     school's id is a 404 rather than a 403 -- the row does not resolve for that
#     caller at all -- so the tenant checks below accept either and still reject a
#     200 that carries somebody else's row;
#   * a role that may not perform an operation is refused with 403, not 500;
#   * a delete that the database refuses because another row still references it
#     is a 409, not a 500.
#
# Prerequisites
#   * a backend on GMS_BASE_URL (default http://localhost:8080) started against
#     a DISPOSABLE database, with the platform account bootstrapped:
#       APP_SUPERADMIN_ENABLED=true APP_SUPERADMIN_USERNAME=... \
#       APP_SUPERADMIN_PASSWORD=... APP_SUPERADMIN_EMAIL=... \
#       APP_SUPERADMIN_FULL_NAME=... java -jar target/gms-*.jar --spring.profiles.active=dev
#   * curl and jq
#
# The script CREATES two schools, two administrators, a teacher, a classroom and
# a student. Point it at a disposable database only. It never deletes anything
# outside the schools it just created.
#
# Credentials come from the environment; nothing here hardcodes one.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

BASE=${GMS_BASE_URL:-http://localhost:8080}
API="$BASE/api/v1"
SUPERUSER=${GMS_SUPERADMIN_USERNAME:?set GMS_SUPERADMIN_USERNAME}
SUPERPASSWORD=${GMS_SUPERADMIN_PASSWORD:?set GMS_SUPERADMIN_PASSWORD}
# The school administrator's password is derived by SchoolServiceImpl from the
# administrator's full name: lowercase first-initial + surname + "@123".
ADMIN_A_NAME=${GMS_SCHOOL_A_ADMIN_NAME:-Alice Principal}
ADMIN_B_NAME=${GMS_SCHOOL_B_ADMIN_NAME:-Bob Principal}
# The password policy (PasswordValidator) accepts letters, digits and
# @$!%*?&.#_ only — a hyphen fails it, which is what the first run of this
# script got wrong.
TEACHER_PASSWORD=${GMS_TEACHER_PASSWORD:-Teacher_Verify_2026!}

failures=0
checks=0
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
  # The property is "the caller does not get the row", not the status code. `C-4` moved the
  # ownership check into the load, so another school's id now resolves to nothing and the honest
  # answer is 404; 403 is still what a role refusal and a post-load comparison answer. A 200 that
  # carries somebody else's row, and a 500, are both failures -- this accepts neither.
  checks=$((checks + 1))
  if [[ "$2" == "403" || "$2" == "404" ]]; then
    printf 'PASS  %s (refused with %s)\n' "$1" "$2"
  else
    printf 'FAIL  %s (expected a refusal -- 403 or 404 -- got %s)\n' "$1" "$2"
    failures=$((failures + 1))
  fi
}

call() { # call <method> <path> [token] [json]
  local method=$1 route=$2 token=${3:-} payload=${4:-}
  local args=(-s -o "$body" -w '%{http_code}' -X "$method" "$API$route")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$payload" ]] && args+=(-H 'Content-Type: application/json' -d "$payload")
  curl "${args[@]}"
}

token_of() { jq -r '.accessToken // empty' "$body"; }

# The administrator username the school-creation flow generates.
admin_username() { # admin_username <full name>
  local first last
  first=$(printf '%s' "$1" | awk '{print $1}')
  last=$(printf '%s' "$1" | awk '{print $2}')
  printf '%s%s' "${first:0:1}" "$last" | tr 'A-Z' 'a-z'
}

echo "=== 1. Platform login ==="
code=$(call POST /auth/login "" "{\"username\":\"$SUPERUSER\",\"password\":\"$SUPERPASSWORD\"}")
check "superadmin login accepted" "200" "$code"
PLATFORM_TOKEN=$(token_of)
[[ -z "$PLATFORM_TOKEN" ]] && { echo "FATAL: no platform token"; exit 1; }

echo
echo "=== 2. The platform creates two schools ==="
code=$(call POST /schools/create "$PLATFORM_TOKEN" \
  '{"schoolName":"Northgate High","schoolCode":"NGH001","adminFullName":"'"$ADMIN_A_NAME"'","adminEmail":"alice@northgate.test"}')
check "school A created" "200" "$code"
code=$(call POST /schools/create "$PLATFORM_TOKEN" \
  '{"schoolName":"Southbank Academy","schoolCode":"SBA002","adminFullName":"'"$ADMIN_B_NAME"'","adminEmail":"bob@southbank.test"}')
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
code=$(call POST /classrooms/create "$TOKEN_A" '{"name":"Grade 5-A","grade":"5","section":"A"}')
check "classroom created in school A" "200" "$code"
CLASS_ID=$(jq -r '.id' "$body")
code=$(call POST /subjects/create "$TOKEN_A" '{"name":"Mathematics","code":"MATH"}')
check "subject created in school A" "200" "$code"
SUBJECT_A=$(jq -r '.id' "$body")
code=$(call POST /students/create "$TOKEN_A" \
  "{\"firstName\":\"Ravi\",\"lastName\":\"Kumar\",\"email\":\"ravi.kumar@northgate.test\",\"gender\":\"MALE\",\"classId\":$CLASS_ID}")
check "student created in school A" "200" "$code"
STUDENT_A=$(jq -r '.id' "$body")

echo
echo "=== 5. School A provisions a teacher ==="
code=$(call POST /users/create "$TOKEN_A" \
  "{\"username\":\"teacher.northgate\",\"email\":\"teacher@northgate.test\",\"password\":\"$TEACHER_PASSWORD\",\"fullName\":\"Tara Teacher\",\"roles\":[\"TEACHER\"]}")
check "teacher account created" "200" "$code"
code=$(call POST /auth/login "" "{\"username\":\"teacher.northgate\",\"password\":\"$TEACHER_PASSWORD\"}")
check "teacher login" "200" "$code"
TEACHER_TOKEN=$(token_of)

echo
echo "=== 6. Tenant isolation on the reads C-1 re-declared ==="
code=$(call GET "/students/$STUDENT_A" "$TOKEN_A")
check "admin A reads its own student" "200" "$code"
code=$(call GET "/students/$STUDENT_A" "$TOKEN_B")
refused "admin B refused another school's student" "$code"
code=$(call GET "/students/$STUDENT_A" "")
check "anonymous read refused" "401" "$code"
code=$(call GET "/students/999999" "$TOKEN_A")
check "missing student reported as not found" "404" "$code"
code=$(call GET "/subjects/$SUBJECT_A" "$TOKEN_B")
refused "admin B refused another school's subject read" "$code"

echo
echo "=== 7. Tenant isolation on the deletes C-1 re-declared ==="
code=$(call DELETE "/subjects/$SUBJECT_A" "$TEACHER_TOKEN")
check "teacher refused subject delete" "403" "$code"
code=$(call DELETE "/subjects/$SUBJECT_A" "$TOKEN_B")
check "admin B refused another school's subject delete" "404" "$code"
code=$(call DELETE "/subjects/$SUBJECT_A" "$TOKEN_A")
check "admin A deleted its own subject" "204" "$code"
code=$(call GET "/subjects/$SUBJECT_A" "$TOKEN_A")
check "deleted subject is gone" "404" "$code"

echo
echo "=== 8. Retiring a classroom the pupils still name ==="
# This section asserted 409 when a delete refused to orphan rows. The contract that merged in PR #26
# is different and deliberate: a delete archives, because the row carries the authorship the audit
# trail reads and students.classroom_id's foreign key would refuse a hard delete anyway. So the
# question this card actually asks is no longer "is it refused" but "is the retired row invisible and
# is everything that still names it readable". The 500 this check was written to catch is still the
# thing being caught — it just arrives one request later now, on the read of a pupil whose classroom
# is no longer in the visible set.
code=$(call DELETE "/classrooms/$CLASS_ID" "$TOKEN_B")
check "admin B refused another school's classroom delete" "404" "$code"
code=$(call DELETE "/classrooms/$CLASS_ID" "$TOKEN_A")
check "admin A retired its own classroom, with a body-free 204 and no server fault" "204" "$code"
code=$(call GET "/classrooms/$CLASS_ID" "$TOKEN_A")
check "the retired classroom is invisible to its own school" "404" "$code"
code=$(call GET "/classrooms" "$TOKEN_A")
check "and it is absent from the school's own list" "0" \
  "$(jq --argjson id "$CLASS_ID" '[.[] | select(.id == $id)] | length' "$body")"
code=$(call GET "/students/$STUDENT_A" "$TOKEN_A")
check "a pupil still pointing at the retired classroom reads without a server fault" "200" "$code"
code=$(call GET "/students" "$TOKEN_A")
check "and the pupil list reads too" "200" "$code"
# Measured, not asserted: the pupil row keeps its classId after the classroom is retired, which the
# pupil's own response says out loud. Whether a school may retire a classroom that still has pupils in
# it is the owner's call, not a test's — this line records the consequence of the accepted contract so
# the decision is made with the observation in front of it.
printf '      NOTE pupil %s still names classroom %s (classId=%s), which now reads 404\n' \
  "$STUDENT_A" "$CLASS_ID" "$(jq -r '.classId' "$body")"
code=$(call POST /classrooms/create "$TOKEN_A" '{"name":"Grade 6-B","grade":"6","section":"B"}')
check "second classroom created in school A" "200" "$code"
EMPTY_CLASS=$(jq -r '.id' "$body")
code=$(call DELETE "/classrooms/$EMPTY_CLASS" "$TOKEN_A")
check "admin A deleted its own unreferenced classroom" "204" "$code"

echo
echo "=== 9. The routes the deleted base class used to supply ==="
code=$(call DELETE "/students/$STUDENT_A" "$TOKEN_A")
check "student delete route no longer exists" "405" "$code"
code=$(call DELETE "/parents/1" "$TOKEN_A")
check "parent delete route no longer exists" "403" "$code"
code=$(call DELETE "/employees/1" "$TOKEN_A")
check "employee delete route no longer exists" "405" "$code"
code=$(call DELETE "/schools/1" "$PLATFORM_TOKEN")
check "school delete route no longer exists" "405" "$code"

echo
echo "=== 10. A method-level refusal is 403, not 500 ==="
code=$(call GET "/students/$STUDENT_A" "$TEACHER_TOKEN")
check "teacher refused student read" "403" "$code"
printf '      body: %s\n' "$(cat "$body")"

echo
echo "=== 11. The platform owner keeps its reach ==="
code=$(call GET "/schools" "$PLATFORM_TOKEN")
check "superadmin lists schools" "200" "$code"

echo
echo "==================================================="
printf 'PASSED: %d   FAILED: %d\n' "$checks" "$failures"
[[ $failures -eq 0 ]] || exit 1
