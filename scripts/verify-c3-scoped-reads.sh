#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Live verification for Stage 3 card C-3 — "the tenant is in the query".
#
#   ./scripts/verify-c3-scoped-reads.sh
#
# C-2 put the tenant in the request context. C-3 is ADR-020's second layer: the
# context has to reach the SQL. Before this card 54 declared repository reads
# carried no school at all, so a caller who supplied another school's row id got
# that school's row — the tenant was known to the service and forgotten by the
# query. 36 of those reads are now scoped; the 18 that remain are allow-listed in
# RepositoryTenantScopeArchTest, each with the reason it is not a leak.
#
# What this proves, against a running instance and a real database:
#   * a read that arrives at another school's row id finds nothing, rather than
#     finding it and then deciding — GET /timetables/classroom/{id} has no
#     ownership guard at all, so the query is the only thing standing between
#     school B and school A's timetable;
#   * email uniqueness is enforced per school, which is what uq_users_school_email
#     (V3:41) and uq_employees_school_email (V3:53) actually promise. The global
#     check this card replaced told school A that school B had already registered a
#     mailbox: a cross-tenant oracle, and a stricter rule than the schema's;
#   * the same mailbox is still refused twice inside one school;
#   * the generated SQL itself carries the school predicate, not merely the
#     result set.
#
# Prerequisites
#   * a backend on GMS_BASE_URL (default http://localhost:8080) started against a
#     DISPOSABLE database with the platform account bootstrapped, as in
#     verify-c2-tenant-context.sh;
#   * curl and jq;
#   * GMS_VERIFY_DB plus a PostgreSQL container reachable through docker for the
#     row-level checks — without them those checks report SKIP, never PASS;
#   * optionally GMS_SQL_LOG pointing at the backend's stdout with
#     JPA_SHOW_SQL=true, for the generated-SQL check.
#
# The script CREATES two schools, two administrators, classrooms, a timetable and
# staff rows. Point it at a disposable database only.
#
# Credentials come from the environment; nothing here hardcodes one.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

BASE=${GMS_BASE_URL:-http://localhost:8080}
API="$BASE/api/v1"
SUPERUSER=${GMS_SUPERADMIN_USERNAME:?set GMS_SUPERADMIN_USERNAME}
SUPERPASSWORD=${GMS_SUPERADMIN_PASSWORD:?set GMS_SUPERADMIN_PASSWORD}
ADMIN_A_NAME=${GMS_SCHOOL_A_ADMIN_NAME:-Ingrid Ashworth}
ADMIN_B_NAME=${GMS_SCHOOL_B_ADMIN_NAME:-Jad Prelic}
STAFF_PASSWORD=${GMS_STAFF_PASSWORD:-Staff_Verify_2026!}
SHARED_EMAIL=${GMS_SHARED_EMAIL:-shared.staff@gms.test}
SHARED_USER_EMAIL=${GMS_SHARED_USER_EMAIL:-shared.account@gms.test}
ACADEMIC_YEAR=${GMS_ACADEMIC_YEAR:-2026-2027}

VERIFY_DB=${GMS_VERIFY_DB:-}
PG_CONTAINER=${GMS_PG_CONTAINER:-gms-postgres}
PG_USER=${GMS_PG_USER:-gms_user}
SQL_LOG=${GMS_SQL_LOG:-}

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

log_lines() { [[ -n "$SQL_LOG" && -r "$SQL_LOG" ]] && wc -l < "$SQL_LOG" || echo 0; }

log_slice() { # log_slice <from line>
  awk -v from="$1" 'NR >= from' "$SQL_LOG"
}

echo "=== 1. Platform login, and two schools ==="
code=$(call POST /auth/login "" "{\"username\":\"$SUPERUSER\",\"password\":\"$SUPERPASSWORD\"}")
check "superadmin login accepted" "200" "$code"
PLATFORM_TOKEN=$(token_of)
[[ -z "$PLATFORM_TOKEN" ]] && { echo "FATAL: no platform token"; exit 1; }

code=$(call POST /schools/create "$PLATFORM_TOKEN" \
  '{"schoolName":"Northgate Secondary","schoolCode":"NGS301","adminFullName":"'"$ADMIN_A_NAME"'","adminEmail":"ingrid@northgate.test"}')
check "school A created" "200" "$code"
code=$(call POST /schools/create "$PLATFORM_TOKEN" \
  '{"schoolName":"Selborne College","schoolCode":"SEL302","adminFullName":"'"$ADMIN_B_NAME"'","adminEmail":"jad@selborne.test"}')
check "school B created" "200" "$code"

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
echo "=== 2. A timetable read that has no ownership guard ==="
# /timetables/classroom/{id} goes from the path variable straight into the
# repository. Before C-3 the query was findByClassroom_Id(classroomId): school B
# naming school A's classroom got school A's timetable. Now the tenant is a
# predicate, so the row is simply not there.
code=$(call POST /classrooms/create "$TOKEN_A" '{"name":"Grade 9-A","grade":"9","section":"A"}')
check "classroom created in school A" "200" "$code"
CLASS_A=$(jq -r '.id' "$body")
code=$(call POST /classrooms/create "$TOKEN_B" '{"name":"Grade 9-B","grade":"9","section":"B"}')
check "classroom created in school B" "200" "$code"
CLASS_B=$(jq -r '.id' "$body")

MARK=$(( $(log_lines) + 1 ))
code=$(call POST /timetables "$TOKEN_A" \
  "{\"classroomId\":$CLASS_A,\"academicYear\":\"$ACADEMIC_YEAR\",\"slots\":[]}")
check "school A created a timetable on its own classroom" "200" "$code"

code=$(call GET "/timetables/classroom/$CLASS_A" "$TOKEN_A")
check "school A reads its own timetable" "200" "$code"
code=$(call GET "/timetables/classroom/$CLASS_A" "$TOKEN_B")
check "school B naming school A's classroom finds no timetable" "404" "$code"
code=$(call GET "/timetables/classroom/$CLASS_B" "$TOKEN_B")
check "school B's own classroom still has no timetable of school A's" "404" "$code"

if [[ -n "$VERIFY_DB" ]]; then
  ROWS=$(sql "SELECT COUNT(*) FROM timetables t JOIN classrooms c ON c.id = t.classroom_id WHERE c.school_id = (SELECT id FROM schools WHERE school_code='SEL302')")
  check "school B's read wrote nothing into school B" "0" "$ROWS"
  SCHOOL_OF_TT=$(sql "SELECT c.school_id FROM timetables t JOIN classrooms c ON c.id = t.classroom_id JOIN schools s ON s.id = c.school_id WHERE s.school_code = 'NGS301'")
  check "the timetable is recorded against school A" "1" "$(printf '%s' "$SCHOOL_OF_TT" | grep -cE '^[0-9]+$')"
else
  skip "row-level timetable checks" "GMS_VERIFY_DB is not set"
fi

if [[ -n "$SQL_LOG" && -r "$SQL_LOG" ]]; then
  SLICE=$(log_slice "$MARK" | grep -iE 'select .*from timetables' | head -1)
  if [[ -z "$SLICE" ]]; then
    skip "generated-SQL check" "no timetables select in the log slice"
  else
    case "$SLICE" in
      *school_id=*) check "the timetable select carries the school predicate" "yes" "yes" ;;
      *)            check "the timetable select carries the school predicate" "yes" "no" ;;
    esac
  fi
else
  skip "generated-SQL check" "GMS_SQL_LOG is not set (start the backend with JPA_SHOW_SQL=true)"
fi

echo
echo "=== 3. Employee email is unique per school, not per platform ==="
# uq_employees_school_email (V3:53). existsByEmail before C-3 answered for every
# school, so school B's hiring locked school A out of the same address.
EMP_PAYLOAD='{"firstName":"Rowan","lastName":"Meyer","email":"'"$SHARED_EMAIL"'","designation":"Bursar","department":"Finance","teaching":false}'
code=$(call POST /employees/create "$TOKEN_A" "$EMP_PAYLOAD")
check "school A registers the shared staff address" "200" "$code"
code=$(call POST /employees/create "$TOKEN_B" "$EMP_PAYLOAD")
check "school B registers the same address (global check refused this)" "200" "$code"
code=$(call POST /employees/create "$TOKEN_A" '{"firstName":"Rowan","lastName":"Meyer","email":"'"$SHARED_EMAIL"'","designation":"Librarian","department":"Library","teaching":false}')
check "school A is still refused its own duplicate" "400" "$code"

echo
echo "=== 4. User email is unique per school, not per platform ==="
# uq_users_school_email (V3:41) against uq_users_username (V3:34): the username
# stays global, which is why login can still look a user up by it.
USER_PAYLOAD='{"username":"shared.staff.a","email":"'"$SHARED_USER_EMAIL"'","password":"'"$STAFF_PASSWORD"'","fullName":"Shared Staff A","roles":["TEACHER"]}'
code=$(call POST /users/create "$TOKEN_A" "$USER_PAYLOAD")
check "school A creates an account on the shared mailbox" "200" "$code"
code=$(call POST /users/create "$TOKEN_B" \
  '{"username":"shared.staff.b","email":"'"$SHARED_USER_EMAIL"'","password":"'"$STAFF_PASSWORD"'","fullName":"Shared Staff B","roles":["TEACHER"]}')
check "school B creates an account on the same mailbox (global check refused this)" "200" "$code"
code=$(call POST /users/create "$TOKEN_A" \
  '{"username":"shared.staff.a2","email":"'"$SHARED_USER_EMAIL"'","password":"'"$STAFF_PASSWORD"'","fullName":"Duplicate A","roles":["TEACHER"]}')
check "school A is still refused its own duplicate mailbox" "400" "$code"

if [[ -n "$VERIFY_DB" ]]; then
  HOLDERS=$(sql "SELECT COUNT(DISTINCT school_id) FROM employees WHERE email = '$SHARED_EMAIL'")
  check "the shared staff address is held by exactly two schools" "2" "$HOLDERS"
  PER_SCHOOL=$(sql "SELECT COUNT(*) FROM employees WHERE email = '$SHARED_EMAIL' AND school_id = (SELECT id FROM schools WHERE school_code='NGS301')")
  check "school A holds that address once, not twice" "1" "$PER_SCHOOL"
  USER_SCHOOLS=$(sql "SELECT COUNT(DISTINCT school_id) FROM users WHERE email = '$SHARED_USER_EMAIL'")
  check "the shared mailbox reached two schools' user rows" "2" "$USER_SCHOOLS"
  NO_CROSS=$(sql "SELECT COUNT(*) FROM users WHERE email = '$SHARED_USER_EMAIL' AND username LIKE '%a2'")
  check "the refused duplicate was never written" "0" "$NO_CROSS"
else
  skip "row-level uniqueness checks" "GMS_VERIFY_DB is not set"
fi

echo
echo "=== 5. Login by username is still global, and still works ==="
# The allow-list keeps findByUsername tenant-free because the constraint behind it
# is global. If that read had been scoped by mistake, this login would fail.
code=$(call POST /auth/login "" "{\"username\":\"shared.staff.b\",\"password\":\"$STAFF_PASSWORD\"}")
check "a per-school account still logs in through the global username lookup" "200" "$code"

echo
echo "==================================================="
printf 'PASSED: %d   FAILED: %d   SKIPPED: %d\n' "$checks" "$failures" "$skipped"
[[ $failures -eq 0 ]] || exit 1
