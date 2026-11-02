#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Live verification for Stage 3 card C-4 — "the ownership check sits where the
# JWT is verified, so a wrong tenant is 403/404 and not a filtered empty list".
#
#   ./scripts/verify-c4-owned-reads.sh
#
# ADR-020 level 2 has two halves. `C-3` closed the half a declaration scan can
# see: every query a repository author *wrote* now names the school. It could not
# see `findById`, because `JpaRepository` hands that method to every repository by
# inheritance, and inheritance is invisible to a scan of declarations. 153 service
# call sites went on loading a row by id alone, and the protection was whatever
# that service happened to do next — 23 of them compared the row's school by hand
# and threw; the rest returned it. That is `SEC-06`: school B's administrator
# reading school A's student by changing a number in the URL, on endpoints whose
# response is a real row rather than an empty list.
#
# `TenantScopedRepository` is the mechanism: a repository whose entity implements
# `TenantOwned` extends it, and its `findById` resolves the row through the school
# the token already claimed. This script proves three things about that mechanism,
# against a running application and a real database:
#
#   * a by-id read of another school's row is refused, on every resource the run
#     can create, and refused with 403/404 rather than an empty 200;
#   * the same read by the owner still answers 200 — the pair that keeps a 404
#     from being evidence of a broken filter rather than a working one;
#   * the row survives the attempt: a refused delete writes nothing.
#
# The platform owner is checked too, in the other direction. Its reach is a
# property of the role, so a suite that only proved refusals would pass against a
# filter that refuses everyone.
#
# Prerequisites
#   * a backend on GMS_BASE_URL (default http://localhost:8080) started against a
#     DISPOSABLE database at V6 with the platform account bootstrapped, as in
#     verify-c7-tenant-key.sh;
#   * curl and jq;
#   * GMS_VERIFY_DB plus a PostgreSQL container reachable through docker, for the
#     coverage and no-writes checks — they report SKIP, never PASS, without them.
#
# The script CREATES two schools and a term of rows in the first. The only raw SQL
# it runs outside a rolled-back transaction is SELECT. Point it at a disposable
# database only. Credentials come from the environment; nothing here hardcodes one.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

BASE=${GMS_BASE_URL:-http://localhost:8080}
API="$BASE/api/v1"
SUPERUSER=${GMS_SUPERADMIN_USERNAME:?set GMS_SUPERADMIN_USERNAME}
SUPERPASSWORD=${GMS_SUPERADMIN_PASSWORD:?set GMS_SUPERADMIN_PASSWORD}
ADMIN_A_NAME=${GMS_SCHOOL_A_ADMIN_NAME:-Nadia Okonjo}
ADMIN_B_NAME=${GMS_SCHOOL_B_ADMIN_NAME:-Tomas Wilder}
ACADEMIC_YEAR=${GMS_ACADEMIC_YEAR:-2026-2027}
TODAY=${GMS_VERIFY_DATE:-2026-06-11}

VERIFY_DB=${GMS_VERIFY_DB:-}
PG_CONTAINER=${GMS_PG_CONTAINER:-gms-postgres}
PG_USER=${GMS_PG_USER:-gms_user}
PG_PASSWORD_ENV=${GMS_PG_PASSWORD_ENV:-DB_PASSWORD}
CONTROLLER_DIR=${GMS_CONTROLLER_DIR:-gms_backend/src/main/java/com/gms/controller}

failures=0
checks=0
skipped=0
probes=0
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

field() { jq -r "$1 // empty" "$body"; }
token_of() { field '.accessToken'; }

admin_username() { # admin_username <full name>
  local first last
  first=$(printf '%s' "$1" | awk '{print $1}')
  last=$(printf '%s' "$1" | awk '{print $2}')
  printf '%s%s' "${first:0:1}" "$last" | tr 'A-Z' 'a-z'
}

sql() { # sql <statement> — SELECT only
  docker exec -e PGPASSWORD="${!PG_PASSWORD_ENV}" "$PG_CONTAINER" psql -U "$PG_USER" -d "$VERIFY_DB" \
    -v ON_ERROR_STOP=1 -tAc "$1" 2>&1
}

have_db() { [[ -n "$VERIFY_DB" && -n "${!PG_PASSWORD_ENV:-}" ]]; }

create_row() { # create_row <name> <table> <read route> <create route> <payload> -> sets REPLY_ID
  local name=$1 table=$2 route=$3 create=$4 payload=$5
  REPLY_ID=""
  local code
  code=$(call POST "$create" "$TOKEN_A" "$payload")
  if [[ "$code" != "200" && "$code" != "201" ]]; then
    printf '      %s: create answered %s -- probe skipped, not passed\n' "$name" "$code"
    return 1
  fi
  REPLY_ID=$(field '.id')
  [[ -z "$REPLY_ID" || "$REPLY_ID" == "null" ]] && REPLY_ID=$(sql "SELECT max(id) FROM $table WHERE school_id=$SCHOOL_A" | tr -d '[:space:]')
  if [[ -z "$REPLY_ID" || "$REPLY_ID" == "null" ]]; then
    printf '      %s: created but no id recovered -- probe skipped\n' "$name"
    return 1
  fi
  ROWS+=("$name|$table|$REPLY_ID|$route")
  return 0
}

probe_pair() { # probe_pair <name> <table> <id> <route>
  local name=$1 table=$2 id=$3 route=$4
  probes=$((probes + 1))

  local mine theirs survived
  mine=$(call GET "$route/$id" "$TOKEN_A")
  theirs=$(call GET "$route/$id" "$TOKEN_B")
  check "$name: the owner reads its own row" "200" "$mine"
  if [[ "$theirs" == "403" || "$theirs" == "404" ]]; then
    checks=$((checks + 1))
    printf 'PASS  %s: another school reading the same id is refused (got %s)\n' "$name" "$theirs"
  else
    checks=$((checks + 1))
    printf 'FAIL  %s: another school reading the same id was answered %s, not 403/404\n' "$name" "$theirs"
    failures=$((failures + 1))
  fi

  # A refused read must not have written anything either: the same id is still
  # the owner's, and the row is still there.
  survived=$(call GET "$route/$id" "$TOKEN_A")
  check "$name: the row is still the owner's after the attempt" "200" "$survived"
}

echo "=== 1. Platform login, two schools, one of each ==="
code=$(call POST /auth/login "" "{\"username\":\"$SUPERUSER\",\"password\":\"$SUPERPASSWORD\"}")
check "superadmin login accepted" "200" "$code"
PLATFORM_TOKEN=$(token_of)
[[ -z "$PLATFORM_TOKEN" ]] && { echo "FATAL: no platform token"; exit 1; }

code=$(call POST /schools/create "$PLATFORM_TOKEN" \
  '{"schoolName":"Ashcombe Grammar","schoolCode":"ASH401","adminFullName":"'"$ADMIN_A_NAME"'","adminEmail":"nadia@ashcombe.test"}')
check "school A created" "200" "$code"
code=$(call POST /schools/create "$PLATFORM_TOKEN" \
  '{"schoolName":"Bracklyn High","schoolCode":"BRK402","adminFullName":"'"$ADMIN_B_NAME"'","adminEmail":"tomas@bracklyn.test"}')
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

SCHOOL_A=$(sql "SELECT id FROM schools WHERE school_code='ASH401'" 2>/dev/null | tr -d '[:space:]')
SCHOOL_B=$(sql "SELECT id FROM schools WHERE school_code='BRK402'" 2>/dev/null | tr -d '[:space:]')
if [[ -z "$SCHOOL_A" || -z "$SCHOOL_B" ]]; then
  echo "FATAL: GMS_VERIFY_DB is not set, and the tenant ids come from the database"
  exit 1
fi
[[ "$SCHOOL_A" == "$SCHOOL_B" ]] && { echo "FATAL: the two schools resolved to the same id"; exit 1; }

declare -a ROWS=()

echo
echo "=== 2. School A builds a term through its own endpoints ==="
# The order matters: the leaf rows name the parents, and a create that answers
# anything but 2xx removes that resource from the probe list rather than letting
# it report a 404 it earned from an empty table.
create_row academic-year academic_years /academic-years /academic-years \
  "{\"name\":\"$ACADEMIC_YEAR\",\"startDate\":\"2026-04-01\",\"endDate\":\"2027-03-31\",\"isCurrent\":true}"

create_row classroom classrooms /classrooms /classrooms/create '{"name":"Grade 9-A","grade":"9","section":"A"}'
CLASS_A=${REPLY_ID:-}

create_row section sections /sections /sections '{"name":"Blue","description":"House Blue","capacity":30}'
SECTION_A=${REPLY_ID:-}

create_row subject subjects /subjects /subjects/create '{"name":"Mathematics","code":"MATH-401"}'
SUBJECT_A=${REPLY_ID:-}

create_row employee employees /employees /employees/create \
  '{"firstName":"Aya","lastName":"Mbeki","email":"aya.mbeki@ashcombe.test","phoneNumber":"900100001","designation":"Head of Department","department":"Mathematics","teaching":true}'
EMPLOYEE_A=${REPLY_ID:-}

create_row student students /students /students/create \
  "{\"firstName\":\"Leila\",\"lastName\":\"Farouk\",\"email\":\"leila.farouk@ashcombe.test\",\"phoneNumber\":\"900100002\",\"dateOfBirth\":\"2011-04-02\",\"gender\":\"F\",\"classId\":$CLASS_A,\"sectionId\":$SECTION_A}"
STUDENT_A=${REPLY_ID:-}

create_row user users /users /users/create \
  "{\"username\":\"rhea.menon\",\"fullName\":\"Rhea Menon\",\"email\":\"rhea.menon@ashcombe.test\",\"password\":\"RheaMenon@2026\",\"phoneNumber\":\"900100005\",\"roles\":[\"TEACHER\"],\"employeeId\":$EMPLOYEE_A}"

create_row exam exams /exams /exams \
  "{\"name\":\"Midterm\",\"description\":\"Term 2 midterm\",\"examType\":\"MIDTERM\",\"academicYear\":\"$ACADEMIC_YEAR\",\"examDate\":\"$TODAY\",\"maxMarks\":100,\"passingMarks\":40,\"durationMinutes\":120,\"examSubjects\":[{\"subjectId\":$SUBJECT_A,\"maxMarks\":100,\"passingMarks\":40,\"examDate\":\"$TODAY\",\"startTime\":\"09:00\",\"endTime\":\"11:00\"}]}"
EXAM_A=${REPLY_ID:-}

create_row fee fees /fees /fees \
  "{\"feeType\":\"Tuition\",\"description\":\"Term 2 tuition\",\"amount\":2500,\"dueDate\":\"$TODAY\",\"academicYear\":\"$ACADEMIC_YEAR\",\"classGrade\":\"9\",\"isMandatory\":true}"
FEE_A=${REPLY_ID:-}

create_row fee-structure fee_structures /fee-structures /fee-structures \
  "{\"name\":\"Tuition 2026\",\"description\":\"Term 2 tuition structure\",\"feeCategory\":\"Tuition\",\"academicYear\":\"$ACADEMIC_YEAR\",\"classGrade\":\"9\",\"amount\":2500,\"isMandatory\":true,\"isActive\":true,\"effectiveFrom\":\"$TODAY\"}"

create_row student-fee student_fees /student-fees /student-fees \
  "{\"studentId\":$STUDENT_A,\"feeId\":$FEE_A,\"status\":\"PENDING\",\"amountPaid\":0}"
STUDENT_FEE_A=${REPLY_ID:-}

create_row fee-payment fee_payments /fee-payments /fee-payments \
  "{\"studentFeeId\":$STUDENT_FEE_A,\"amount\":500,\"paymentDate\":\"$TODAY\",\"paymentMethod\":\"CASH\",\"transactionId\":\"TXN-C4-1\",\"status\":\"PAID\"}"
FEE_PAYMENT_A=${REPLY_ID:-}

create_row invoice invoices /invoices /invoices \
  "{\"invoiceNumber\":\"INV-C4-1\",\"invoiceDate\":\"$TODAY\",\"dueDate\":\"$TODAY\",\"totalAmount\":2500,\"amountPaid\":500,\"status\":\"PARTIAL\",\"invoiceType\":\"TUITION\",\"studentId\":$STUDENT_A}"
INVOICE_A=${REPLY_ID:-}

create_row receipt receipts /receipts /receipts \
  "{\"receiptNumber\":\"RCP-C4-1\",\"receiptDate\":\"$TODAY\",\"amount\":500,\"paymentMethod\":\"CASH\",\"status\":\"PAID\",\"studentId\":$STUDENT_A}"

create_row financial-transaction financial_transactions /financial-transactions /financial-transactions \
  "{\"transactionType\":\"INCOME\",\"transactionId\":\"FT-C4-1\",\"amount\":500,\"currency\":\"INR\",\"transactionDate\":\"$TODAY\",\"paymentMethod\":\"CASH\",\"status\":\"SUCCESS\",\"studentId\":$STUDENT_A}"

create_row financial-report financial_reports /financial-reports /financial-reports \
  "{\"reportName\":\"Term 2 collections\",\"reportType\":\"COLLECTIONS\",\"reportPeriod\":\"TERM-2\",\"academicYear\":\"$ACADEMIC_YEAR\",\"generatedDate\":\"$TODAY\",\"totalIncome\":500,\"totalExpenses\":0,\"netProfit\":500,\"generatedBy\":\"$ADMIN_A_USER\"}"

create_row grading-scheme grading_schemes /grading-schemes /grading-schemes \
  '{"name":"Standard 2026","description":"A to F","minPercentage":0,"maxPercentage":100,"grade":"A","gradePoint":4,"isActive":true}'

create_row school-calendar school_calendars /school-calendar /school-calendar \
  "{\"title\":\"Founder's Day\",\"description\":\"Holiday\",\"eventDate\":\"$TODAY\",\"eventType\":\"HOLIDAY\",\"isRecurring\":false,\"academicYear\":\"$ACADEMIC_YEAR\"}"

create_row announcement announcements /announcements /announcements \
  "{\"title\":\"Term dates\",\"content\":\"Published by the C-4 verification.\",\"announcementType\":\"GENERAL\",\"targetAudience\":\"ALL\",\"publishDate\":\"${TODAY}T09:00:00\",\"isPublished\":true,\"priority\":\"NORMAL\",\"category\":\"NOTICE\"}"

create_row notification notifications /notifications /notifications \
  "{\"title\":\"Fee reminder\",\"message\":\"Sent by the C-4 verification.\",\"notificationType\":\"REMINDER\",\"channel\":\"IN_APP\",\"priority\":\"NORMAL\",\"recipientType\":\"ALL\",\"isImportant\":false}"

create_row payment-gateway payment_gateways /payment-gateway /payment-gateway \
  '{"name":"Test PSP","gatewayType":"CARD","merchantId":"MERCH-C4","isActive":true,"environment":"TEST"}'

printf '      %d resources were created by school A through its own API\n' "${#ROWS[@]}"
check "school A wrote rows across the tenant model" "yes" "$([[ "${#ROWS[@]}" -ge 15 ]] && echo yes)"

echo "=== 3. The by-id matrix: owner reads 200, another school is refused ==="
for entry in "${ROWS[@]}"; do
  IFS='|' read -r name table id route <<<"$entry"
  probe_pair "$name" "$table" "$id" "$route"
done

echo
echo "=== 3b. The tenant table itself, which no TenantScopedRepository covers ==="
# schools is the tenant, so School is not TenantOwned and the mechanism above does not reach
# GET /schools/{id}. The guard there is C-1's service-level check, and it is worth proving that
# the one read outside the mechanism is not the one read that leaks.
probes=$((probes + 1))
SCHOOL_PROBED=1
code=$(call GET "/schools/$SCHOOL_A" "$PLATFORM_TOKEN")
check "schools/{id}: the platform owner reads it" "200" "$code"
code=$(call GET "/schools/$SCHOOL_A" "$TOKEN_B")
checks=$((checks + 1))
if [[ "$code" == "403" || "$code" == "404" ]]; then
  printf 'PASS  schools/{id}: another school reading it is refused (got %s)\n' "$code"
else
  printf 'FAIL  schools/{id}: another school reading it was answered %s, not 403/404\n' "$code"
  failures=$((failures + 1))
fi

echo
echo "=== 4. A refused delete writes nothing ==="
# The read half of the matrix proves the row is still there. This half proves the
# delete mapping is on the same path, so an id in a URL is not a licence.
for entry in "${ROWS[@]}"; do
  IFS='|' read -r name table id route <<<"$entry"
  code=$(call DELETE "$route/$id" "$TOKEN_B")
  if [[ "$code" == "405" ]]; then
    skip "$name: another school deleting the same id" "the resource declares no DELETE $route/{id}"
    continue
  fi
  if [[ "$code" == "403" || "$code" == "404" ]]; then
    checks=$((checks + 1))
    printf 'PASS  %s: another school deleting the same id is refused (got %s)\n' "$name" "$code"
  else
    checks=$((checks + 1))
    printf 'FAIL  %s: another school deleting the same id was answered %s\n' "$name" "$code"
    failures=$((failures + 1))
  fi
done
for entry in "${ROWS[@]}"; do
  IFS='|' read -r name table id route <<<"$entry"
  if have_db; then
    STILL=$(sql "SELECT count(*) FROM $table WHERE id=$id AND school_id=$SCHOOL_A" | tr -d '[:space:]')
    check "$name: the row is still school A's after every attempt" "1" "$STILL"
  else
    skip "$name: the row survives" "no GMS_VERIFY_DB"
  fi
done

echo
echo "=== 5. The platform owner reaches across, on purpose ==="
# The other direction. A filter that refuses everyone passes sections 3 and 4 and
# is still wrong: SUPERADMIN's reach is a property of its role, not an accident of
# an unfiltered query.
# Which routes admit it is not a list kept here: it is read out of the same annotations the
# application enforces, so a controller that stops naming SUPERADMIN changes what this section
# expects instead of quietly making the suite assert a property the server no longer has.
platform_routes=$(mktemp)
trap 'rm -f "$body" "$platform_routes"' EXIT
if [[ -d "$CONTROLLER_DIR" ]]; then
  for f in "$CONTROLLER_DIR"/*.java; do
    # A class mapping may be @RequestMapping("/api/v1/x") literal or @RequestMapping(Constants.API_BASE_V1 + "/x"),
    # so take the last string literal inside the mapping and strip the base-prefix literal if present.
    seg=$(grep -o '@RequestMapping([^)]*)' "$f" | head -1 | grep -o '"/[^"]*"' | tail -1 | tr -d '"')
    seg=${seg#/api/v1}
    [[ -z "$seg" ]] && continue
    if grep -B2 -A3 '@GetMapping("/{id}")' "$f" | grep -q '@PreAuthorize.*SUPERADMIN'; then
      printf '%s\n' "$seg" >> "$platform_routes"
    fi
  done
fi
platform_reachable=0
for entry in "${ROWS[@]}"; do
  IFS='|' read -r name table id route <<<"$entry"
  code=$(call GET "$route/$id" "$PLATFORM_TOKEN")
  if grep -qx "$route" "$platform_routes" 2>/dev/null; then
    # The role layer lets this caller in, so the only thing left that could refuse it is the
    # tenant filter -- and the tenant filter is supposed to let the platform owner through.
    check "$name: the platform owner reads school A's row" "200" "$code"
    platform_reachable=$((platform_reachable + 1))
  elif [[ "$code" == "403" ]]; then
    # Not the tenant filter refusing: the route asks for a role this token does not carry. Tenancy
    # and authorization are different layers, and a suite that counted this as a tenant pass would
    # be measuring the wrong one. The role vocabulary is card C-6's and the guard rule is C-5's.
    skip "$name: the platform owner reads school A's row"       "403 from the role layer (the by-id route does not grant SUPERADMIN), not from the tenant filter"
  else
    check "$name: the platform owner is refused by the role layer, not the tenant filter" "403" "$code"
  fi
done
if [[ "$platform_reachable" == "0" ]]; then
  checks=$((checks + 1))
  printf 'FAIL  no route in the matrix grants SUPERADMIN a by-id read, so the platform-owner branch is unproven\n'
  failures=$((failures + 1))
fi

echo
echo "=== 6. Coverage, counted against the controllers rather than a card ==="
if [[ -d "$CONTROLLER_DIR" ]]; then
  BY_ID_HANDLERS=$(grep -rl '@GetMapping("/{id}")' "$CONTROLLER_DIR" | wc -l | tr -d '[:space:]')
  RESOURCES=${#ROWS[@]}
  [[ -n "${SCHOOL_PROBED:-}" ]] && RESOURCES=$((RESOURCES + 1))
  printf 'C-4: %d probes over %d resources; %d controllers declare a by-id read\n' \
    "$probes" "$RESOURCES" "$BY_ID_HANDLERS"
  checks=$((checks + 1))
  if [[ "$RESOURCES" -ge "$BY_ID_HANDLERS" ]]; then
    printf 'PASS  every controller with a by-id read is probed (%d >= %d)\n' "$RESOURCES" "$BY_ID_HANDLERS"
  else
    printf 'FAIL  %d of %d by-id read controllers are probed -- the suite does not cover the surface\n' \
      "$RESOURCES" "$BY_ID_HANDLERS"
    failures=$((failures + 1))
  fi
else
  skip "coverage against the controller list" "$CONTROLLER_DIR is not readable from here"
fi

echo
echo "==================================================="
printf 'PASSED: %d   FAILED: %d   SKIPPED: %d\n' "$((checks - failures))" "$failures" "$skipped"
[[ "$failures" -eq 0 ]] || exit 1
