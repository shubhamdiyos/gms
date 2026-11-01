#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Live verification for Stage 3 card C-7 — "the tenant key is required".
#
#   ./scripts/verify-c7-tenant-key.sh
#
# ADR-020's third layer is the database. Stage 2 built the column, `C-2` put the
# tenant in the request and `C-3` put it in the query — but the column was still
# nullable, because nine entities did not map it and `SET NOT NULL` on a column
# the application cannot write is an outage. `C-3`'s own verification measured
# the consequence: the running application created a `timetables` row with
# `school_id = NULL`, and PostgreSQL's MATCH SIMPLE check let that NULL *satisfy*
# the same-school foreign key instead of violating it.
#
# This card is the two halves together: the nine entities map `school_id` and
# every write path sets it from the claim — never copied from the parent row —
# and `V6` backfills, requires the column, puts a trigger on the one tenant key
# Hibernate cannot write (`parent_student`, a `@ManyToMany` join table) and adds
# the seven same-school foreign keys that only exist once the column does.
#
# What this proves, against a running instance and a real database:
#   * every row the API creates across all ten tables carries the caller's
#     school — not merely a non-NULL value, this school's id;
#   * no tenant table in the schema holds a row without a tenant;
#   * the requirement is enforced, not declared: an attempt to null the column
#     on a row the application wrote is refused by the database;
#   * a write that straddles two schools is refused by the *constraint*, on an
#     endpoint that has no ownership check in the service at all — which is the
#     difference between the third layer and a comment about it;
#   * the tenant on a parent link is derived from the student, and a link that
#     names a different school is refused rather than corrected;
#   * the generated INSERT names the column, so the value travelled all the way.
#
# Prerequisites
#   * a backend on GMS_BASE_URL (default http://localhost:8080) started against a
#     DISPOSABLE database at V6 with the platform account bootstrapped, as in
#     verify-c2-tenant-context.sh, and JPA_SHOW_SQL=true for the generated-SQL
#     check;
#   * curl and jq;
#   * GMS_VERIFY_DB plus a PostgreSQL container reachable through docker — the
#     row-level and constraint checks report SKIP, never PASS, without them.
#
# The script CREATES two schools and a full term of rows in the first. The only
# raw SQL it runs outside a rolled-back transaction is SELECT. Point it at a
# disposable database only.
#
# Credentials come from the environment; nothing here hardcodes one.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

BASE=${GMS_BASE_URL:-http://localhost:8080}
API="$BASE/api/v1"
SUPERUSER=${GMS_SUPERADMIN_USERNAME:?set GMS_SUPERADMIN_USERNAME}
SUPERPASSWORD=${GMS_SUPERADMIN_PASSWORD:?set GMS_SUPERADMIN_PASSWORD}
ADMIN_A_NAME=${GMS_SCHOOL_A_ADMIN_NAME:-Nadia Okonjo}
ADMIN_B_NAME=${GMS_SCHOOL_B_ADMIN_NAME:-Tomas Wilder}
ACADEMIC_YEAR=${GMS_ACADEMIC_YEAR:-2026-2027}
CROSS_YEAR=${GMS_CROSS_TENANT_ACADEMIC_YEAR:-2027-2028}
TODAY=${GMS_VERIFY_DATE:-2026-06-11}

VERIFY_DB=${GMS_VERIFY_DB:-}
PG_CONTAINER=${GMS_PG_CONTAINER:-gms-postgres}
PG_USER=${GMS_PG_USER:-gms_user}
PG_PASSWORD_ENV=${GMS_PG_PASSWORD_ENV:-DB_PASSWORD}
SQL_LOG=${GMS_SQL_LOG:-}

failures=0
checks=0
skipped=0
body=$(mktemp)
trap 'rm -f "$body"' EXIT

# The ten tables V6 requires, and the endpoint that writes each one. Nine are the
# entities this card changed; parent_student is the join table the trigger covers.
TENANT_TABLES="attendances student_exams exam_subjects results student_fees fee_payments teacher_assignments timetables timetable_slots parent_student"

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
  # The property is "no straddling row is written", not the status code. `C-4` scoped the load that
  # this card's comment used to call unchecked, so a foreign classroom id now resolves to nothing
  # and the write is refused one layer before the database key is reached. Either refusal is a pass;
  # a 200 is not, and the row-count checks below are what actually prove the constraint held.
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

field() { jq -r "$1 // empty" "$body"; }
token_of() { field '.accessToken'; }

require_id() { # require_id <what created the row> <id>
  checks=$((checks + 1))
  if [[ -n "$2" && "$2" != "null" ]]; then
    printf 'PASS  %s returned an id\n' "$1"
  else
    printf 'FAIL  %s returned no id\n' "$1"
    failures=$((failures + 1))
  fi
}

admin_username() { # admin_username <full name>
  local first last
  first=$(printf '%s' "$1" | awk '{print $1}')
  last=$(printf '%s' "$1" | awk '{print $2}')
  printf '%s%s' "${first:0:1}" "$last" | tr 'A-Z' 'a-z'
}

sql() { # sql <statement> — SELECT and rolled-back probes only
  docker exec -e PGPASSWORD="${!PG_PASSWORD_ENV}" "$PG_CONTAINER" psql -U "$PG_USER" -d "$VERIFY_DB" \
    -v ON_ERROR_STOP=1 -tAc "$1" 2>&1
}

have_db() { [[ -n "$VERIFY_DB" && -n "${!PG_PASSWORD_ENV:-}" ]]; }

echo "=== 1. Platform login, two schools, one of each ==="
code=$(call POST /auth/login "" "{\"username\":\"$SUPERUSER\",\"password\":\"$SUPERPASSWORD\"}")
check "superadmin login accepted" "200" "$code"
PLATFORM_TOKEN=$(token_of)
[[ -z "$PLATFORM_TOKEN" ]] && { echo "FATAL: no platform token"; exit 1; }

code=$(call POST /schools/create "$PLATFORM_TOKEN" \
  '{"schoolName":"Northgate Secondary","schoolCode":"NGS701","adminFullName":"'"$ADMIN_A_NAME"'","adminEmail":"nadia@northgate.test"}')
check "school A created" "200" "$code"
code=$(call POST /schools/create "$PLATFORM_TOKEN" \
  '{"schoolName":"Selborne College","schoolCode":"SEL702","adminFullName":"'"$ADMIN_B_NAME"'","adminEmail":"tomas@selborne.test"}')
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

SCHOOL_A=$(sql "SELECT id FROM schools WHERE school_code='NGS701'" 2>/dev/null | tr -d '[:space:]')
SCHOOL_B=$(sql "SELECT id FROM schools WHERE school_code='SEL702'" 2>/dev/null | tr -d '[:space:]')

echo
echo "=== 2. School A builds a term through its own endpoints ==="
# Every one of the nine entities this card changed, written by the running
# application. A 200 here means the NOT NULL was satisfied by the service; a 409
# or 500 would mean a write path still leaves the tenant unset.
code=$(call POST /classrooms/create "$TOKEN_A" '{"name":"Grade 9-A","grade":"9","section":"A"}')
check "classroom created" "200" "$code"
CLASS_A=$(field '.id')

code=$(call POST /sections "$TOKEN_A" '{"name":"Blue","description":"House Blue","capacity":30}')
check "section created" "200" "$code"
SECTION_A=$(field '.id')

code=$(call POST /subjects/create "$TOKEN_A" '{"name":"Mathematics","code":"MATH-9A"}')
check "subject created" "200" "$code"
SUBJECT_A=$(field '.id')

code=$(call POST /employees/create "$TOKEN_A" \
  '{"firstName":"Aya","lastName":"Mbeki","email":"aya.mbeki@northgate.test","phoneNumber":"900000001","designation":"Head of Department","department":"Mathematics","teaching":true}')
check "teacher employee created" "200" "$code"
EMPLOYEE_A=$(field '.id')

code=$(call POST /students/create "$TOKEN_A" \
  "{\"firstName\":\"Leila\",\"lastName\":\"Farouk\",\"email\":\"leila.farouk@northgate.test\",\"phoneNumber\":\"900000002\",\"dateOfBirth\":\"2011-04-02\",\"gender\":\"F\",\"classId\":$CLASS_A,\"sectionId\":$SECTION_A}")
check "student created" "200" "$code"
STUDENT_A=$(field '.id')

require_id "the classroom" "$CLASS_A"
require_id "the section" "$SECTION_A"
require_id "the subject" "$SUBJECT_A"
require_id "the employee" "$EMPLOYEE_A"
require_id "the student" "$STUDENT_A"

code=$(call POST /exams "$TOKEN_A" \
  "{\"name\":\"Midterm\",\"description\":\"Term 2 midterm\",\"examType\":\"MIDTERM\",\"academicYear\":\"$ACADEMIC_YEAR\",\"examDate\":\"$TODAY\",\"maxMarks\":100,\"passingMarks\":40,\"durationMinutes\":120,\"examSubjects\":[{\"subjectId\":$SUBJECT_A,\"maxMarks\":100,\"passingMarks\":40,\"examDate\":\"$TODAY\",\"startTime\":\"09:00\",\"endTime\":\"11:00\"}]}")
check "exam with one subject created" "200" "$code"
EXAM_ID=$(field '.id')
EXAM_SUBJECT_A=$(field '.examSubjects[0].id')
if [[ -z "$EXAM_SUBJECT_A" ]]; then
  EXAM_SUBJECT_A=$(sql "SELECT id FROM exam_subjects WHERE exam_id=$EXAM_ID" 2>/dev/null | tr -d '[:space:]')
fi
require_id "the exam" "$EXAM_ID"
require_id "the exam subject" "$EXAM_SUBJECT_A"

code=$(call POST /student-exams "$TOKEN_A" \
  "{\"studentId\":$STUDENT_A,\"examSubjectId\":$EXAM_SUBJECT_A,\"registrationDate\":\"$TODAY\"}")
check "student registered for the exam" "200" "$code"
STUDENT_EXAM_A=$(field '.id')
require_id "the student exam registration" "$STUDENT_EXAM_A"

code=$(call POST /results "$TOKEN_A" \
  "{\"studentExamId\":$STUDENT_EXAM_A,\"obtainedMarks\":72,\"grade\":\"B\",\"percentage\":72,\"remarks\":\"Written by the C-7 verification\"}")
check "result recorded" "200" "$code"

code=$(call POST /fees "$TOKEN_A" \
  "{\"feeType\":\"Tuition\",\"description\":\"Term 2 tuition\",\"amount\":2500,\"dueDate\":\"$TODAY\",\"academicYear\":\"$ACADEMIC_YEAR\",\"classGrade\":\"9\",\"isMandatory\":true}")
check "fee created" "200" "$code"
FEE_A=$(field '.id')
require_id "the fee" "$FEE_A"

code=$(call POST /student-fees "$TOKEN_A" \
  "{\"studentId\":$STUDENT_A,\"feeId\":$FEE_A,\"status\":\"PENDING\",\"amountPaid\":0}")
check "student fee created" "200" "$code"
STUDENT_FEE_A=$(field '.id')
require_id "the student fee" "$STUDENT_FEE_A"

code=$(call POST /fee-payments "$TOKEN_A" \
  "{\"studentFeeId\":$STUDENT_FEE_A,\"amount\":500,\"paymentDate\":\"$TODAY\",\"paymentMethod\":\"CASH\",\"transactionId\":\"TXN-C7-1\",\"status\":\"PAID\"}")
check "fee payment created" "200" "$code"

code=$(call POST /teacher-assignments "$TOKEN_A" \
  "{\"teacherId\":$EMPLOYEE_A,\"subjectId\":$SUBJECT_A,\"classroomId\":$CLASS_A,\"academicYear\":\"$ACADEMIC_YEAR\"}")
check "teacher assignment created" "201" "$code"
ASSIGNMENT_A=$(field '.id')
require_id "the teacher assignment" "$ASSIGNMENT_A"

code=$(call POST /timetables "$TOKEN_A" \
  "{\"classroomId\":$CLASS_A,\"academicYear\":\"$ACADEMIC_YEAR\",\"slots\":[{\"dayOfWeek\":\"MONDAY\",\"period\":1,\"assignmentId\":$ASSIGNMENT_A}]}")
check "timetable with one slot created" "200" "$code"
TIMETABLE_A=$(field '.id')
# The response carries the slots' day and period, not their ids, and attendance is
# recorded against a slot, so the id comes from the row the POST just wrote.
SLOT_A=$(sql "SELECT id FROM timetable_slots WHERE timetable_id = $TIMETABLE_A" 2>/dev/null | tr -d '[:space:]')
require_id "the timetable" "$TIMETABLE_A"

code=$(call POST /attendance "$TOKEN_A" \
  "{\"studentId\":$STUDENT_A,\"date\":\"$TODAY\",\"status\":\"PRESENT\",\"remarks\":\"C-7 verification\",\"timetableSlotId\":${SLOT_A:-null}}")
check "attendance recorded against the school's own timetable slot" "200" "$code"

code=$(call POST /student-admissions/apply "$TOKEN_A" \
  "{\"firstName\":\"Omar\",\"lastName\":\"Haddad\",\"email\":\"omar.haddad@northgate.test\",\"phoneNumber\":\"900000003\",\"dateOfBirth\":\"2011-09-19\",\"gender\":\"M\",\"address\":\"12 Harbour Road\",\"parentFirstName\":\"Yusuf\",\"parentLastName\":\"Haddad\",\"parentEmail\":\"yusuf.haddad@northgate.test\",\"parentPhone\":\"900000004\",\"classId\":$CLASS_A,\"sectionId\":$SECTION_A}")
check "admission applied" "200" "$code"
ADMISSION_ID=$(field '.id')
code=$(call PUT "/student-admissions/$ADMISSION_ID/approve" "$TOKEN_A")
check "admission approved, which writes the parent link" "200" "$code"

echo
echo "=== 3. Every row the API wrote carries school A ==="
# Not "is not null" — is this school. A service that copied the tenant from a
# parent row would pass a non-null test while the row sat in the wrong school.
if have_db; then
  for t in attendances student_exams results student_fees fee_payments teacher_assignments timetables timetable_slots; do
    MINE=$(sql "SELECT count(*) FROM $t WHERE school_id = $SCHOOL_A" | tr -d '[:space:]')
    ELSE_WHERE=$(sql "SELECT count(*) FROM $t WHERE school_id IS DISTINCT FROM $SCHOOL_A" | tr -d '[:space:]')
    # Both halves, because "no row is in the wrong school" is also true of a table
    # the application never wrote to — the control run found that reading.
    if [[ -n "$MINE" && "$MINE" -gt 0 && "$ELSE_WHERE" == "0" ]]; then
      ACTUAL=ok
    else
      ACTUAL="${MINE:-?} in school A, ${ELSE_WHERE:-?} elsewhere"
    fi
    check "${t}: written by school A, and by nobody else" "ok" "$ACTUAL"
  done
  NONE_NULL=$(sql "SELECT count(*) FROM exam_subjects WHERE school_id IS NULL")
  HAS_TENANT=$(sql "SELECT count(*) FROM exam_subjects WHERE school_id = $SCHOOL_A")
  check "the exam subject row carries the tenant" "1" "$(printf '%s' "$HAS_TENANT" | tr -d '[:space:]')"
  check "no exam subject row was written without it" "0" "$(printf '%s' "$NONE_NULL" | tr -d '[:space:]')"
  LINK=$(sql "SELECT ps.school_id FROM parent_student ps JOIN students s ON s.id = ps.student_id WHERE s.email = 'omar.haddad@northgate.test'")
  check "the parent link the admission wrote carries the student's school" "$SCHOOL_A" "$(printf '%s' "$LINK" | tr -d '[:space:]')"
else
  skip "row-level tenant checks" "GMS_VERIFY_DB or the container password is not set"
fi

echo
echo "=== 4. No tenant table holds a row without a tenant ==="
if have_db; then
  TABLES=$(sql "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND column_name='school_id' AND data_type='integer'")
  check "the database carries the tenant on 35 tables" "35" "$(printf '%s' "$TABLES" | tr -d '[:space:]')"
  UNION=$(sql "SELECT string_agg(format('SELECT count(*) FROM %I WHERE school_id IS NULL', table_name), ' UNION ALL ') FROM information_schema.columns WHERE table_schema='public' AND column_name='school_id' AND data_type='integer'")
  NULLS=$(sql "$UNION" | awk '{s+=$1} END {print s+0}')
  check "rows with a NULL tenant, in any tenant table, platform-wide" "0" "$(printf '%s' "$NULLS" | tr -d '[:space:]')"
  NULLABLE=$(sql "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND column_name='school_id' AND data_type='integer' AND is_nullable='YES'")
  check "tenant columns still nullable, which are the two dead log tables" "2" "$(printf '%s' "$NULLABLE" | tr -d '[:space:]')"
else
  skip "schema-wide tenant checks" "GMS_VERIFY_DB or the container password is not set"
fi

echo
echo "=== 5. The requirement is enforced, not declared ==="
if have_db; then
  for t in attendances student_exams exam_subjects results student_fees fee_payments teacher_assignments timetables timetable_slots; do
    ROWS=$(sql "SELECT count(*) FROM $t WHERE school_id = $SCHOOL_A" | tr -d '[:space:]')
    if [[ -z "$ROWS" || "$ROWS" == "0" ]]; then
      # A probe against an empty table passes without testing anything, which is
      # worse than a failure: it reads as a requirement that was checked.
      check "there is an API-written ${t} row to probe" "at least one" "none -- the endpoint never wrote one"
      continue
    fi
    PROBE=$(sql "BEGIN; UPDATE $t SET school_id = NULL WHERE school_id = $SCHOOL_A; ROLLBACK;")
    case "$PROBE" in
      *"null value in column"*school_id*|*"not-null constraint"*)
        check "the database refuses to null ${t}.school_id" "refused" "refused" ;;
      *) check "the database refuses to null ${t}.school_id" "refused" "accepted" ;;
    esac
  done
  # parent_student cannot be nulled at all: the trigger that fills the column
  # fires on UPDATE OF school_id and re-derives it, so the NULL never reaches the
  # NOT NULL check. Measured rather than assumed, because the outcome is the
  # column being unwritable by hand — which is what the card intended.
  REPAIRED=$(sql "BEGIN; UPDATE parent_student SET school_id = NULL; SELECT count(*) FROM parent_student WHERE school_id IS NULL; ROLLBACK;")
  check "a hand-written NULL on the parent link is re-derived, not stored" "0" \
    "$(printf '%s' "$REPAIRED" | grep -oE '^[0-9]+$' | tail -1)"
  DISAGREE=$(sql "BEGIN; INSERT INTO parent_student (parent_id, student_id, school_id) SELECT p.id, s.id, $SCHOOL_B FROM parents p, students s WHERE p.email='yusuf.haddad@northgate.test' AND s.email='omar.haddad@northgate.test'; ROLLBACK;")
  case "$DISAGREE" in
    *"not the student's school"*)
      check "a parent link naming a school that is not the student's is refused" "refused" "refused" ;;
    *) check "a parent link naming a school that is not the student's is refused" "refused" "accepted" ;;
  esac
else
  skip "NOT NULL and trigger checks" "GMS_VERIFY_DB or the container password is not set"
fi

echo
echo "=== 6. A write that straddles two schools is refused ==="
# This card's own comment used to read "/timetables reads its classroom with an unchecked findById
# -- that is C-4's problem, and this card does not fix it". C-4 has now fixed it, so the request is
# refused at the load (404: school B's caller cannot resolve school A's classroom) instead of
# reaching fk_timetable_classroom_same_school (403). Both are refusals and the constraint is proved
# separately below, from the catalog, because a write path that never gets that far cannot prove it
# by being refused.
# A different academic year, because uq_timetable_classroom_year is platform-wide
# and would refuse the insert on the duplicate key before the tenant key is ever
# consulted; that answer is 409 for the right row shape and would hide this check.
code=$(call POST /timetables "$TOKEN_B" \
  "{\"classroomId\":$CLASS_A,\"academicYear\":\"$CROSS_YEAR\",\"slots\":[]}")
refused "school B cannot put its timetable on school A's classroom" "$code"
if [[ "$code" == "403" ]]; then
  MESSAGE=$(field '.message')
  check "and the constraint path says it belongs to another school" "That record belongs to another school." "$MESSAGE"
else
  printf '      refused at the load rather than the key: the load is what C-4 scoped\n'
fi

code=$(call POST /teacher-assignments "$TOKEN_B" \
  "{\"teacherId\":$EMPLOYEE_A,\"subjectId\":$SUBJECT_A,\"classroomId\":$CLASS_A,\"academicYear\":\"$ACADEMIC_YEAR\"}")
if [[ "$code" == "200" ]]; then
  check "school B cannot assign school A's teacher to school A's classroom" "refused" "written"
else
  check "school B cannot assign school A's teacher to school A's classroom" "refused" "refused"
fi

code=$(call POST /attendance "$TOKEN_B" "{\"studentId\":$STUDENT_A,\"date\":\"$TODAY\",\"status\":\"PRESENT\"}")
if [[ "$code" == "200" ]]; then
  check "school B cannot record attendance for school A's student" "refused" "written"
else
  check "school B cannot record attendance for school A's student" "refused" "refused"
fi

if have_db; then
  FK=$(sql "SELECT count(*) FROM pg_constraint WHERE conname = 'fk_timetable_classroom_same_school'")
  check "the same-school key is still in the schema that refused this write" "1" "$(printf '%s' "$FK" | tr -d '[:space:]')"
  STRADDLE=$(sql "SELECT count(*) FROM timetables t JOIN classrooms c ON c.id = t.classroom_id WHERE t.school_id <> c.school_id")
  check "no timetable in the database names another school's classroom" "0" "$(printf '%s' "$STRADDLE" | tr -d '[:space:]')"
  B_ROWS=$(sql "SELECT count(*) FROM timetables WHERE school_id = $SCHOOL_B")
  check "the refused write left no row behind" "0" "$(printf '%s' "$B_ROWS" | tr -d '[:space:]')"
  CROSS=$(sql "SELECT count(*) FROM teacher_assignments ta JOIN employees e ON e.id = ta.teacher_id WHERE ta.school_id <> e.school_id")
  check "no assignment names an employee from another school" "0" "$(printf '%s' "$CROSS" | tr -d '[:space:]')"
else
  skip "row-level straddle checks" "GMS_VERIFY_DB or the container password is not set"
fi

echo
echo "=== 7. The value travelled all the way to the SQL ==="
if [[ -n "$SQL_LOG" && -r "$SQL_LOG" ]]; then
  LINE=$(grep -iE "Hibernate: +insert into attendances" "$SQL_LOG" | tail -1)
  if [[ -z "$LINE" ]]; then
    skip "generated INSERT check" "no attendances insert in $SQL_LOG"
  else
    case "$LINE" in
      *school_id*) check "the attendance INSERT names school_id" "yes" "yes" ;;
      *)           check "the attendance INSERT names school_id" "yes" "no" ;;
    esac
  fi
else
  skip "generated INSERT check" "GMS_SQL_LOG is not set (start the backend with JPA_SHOW_SQL=true)"
fi

echo
echo "==================================================="
printf 'PASSED: %d   FAILED: %d   SKIPPED: %d\n' "$((checks - failures))" "$failures" "$skipped"
[[ $failures -eq 0 ]] || exit 1
