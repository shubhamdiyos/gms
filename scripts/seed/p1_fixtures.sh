#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# p1_fixtures.sh — builds the identities and rows verify-p1-hardening.sh needs.
#
# Run it against a backend that verify-mvp-e2e.sh has already seeded (the two
# schools MVP801/MVP802 with their administrators, pupils, classrooms, subject
# and employees must exist). It adds, through the API and only through the API:
#   * two school-A families with logins  (p1.parent.a1 / p1.parent.a2)
#   * one school-B family with a login   (p1.parent.b1)
#   * a system-school ADMIN minted by the platform owner (p1.sysadmin)
#   * a teacher assignment, a timetable slot behind it, and one attendance row
#
# The login passwords are the server's own documented defaults, not secrets this
# script invented: parents get "welcome123" (ParentServiceImpl createUserForParent)
# and school administrators get "<username>@123" (SchoolServiceImpl provisioning).
# The generated parent username is read back from the database by email because
# UsernameGenerator's collision suffixes are the server's decision.
#
# Output: an env file (default /tmp/gms-p1-fixture.env, mode 600) that
# verify-p1-hardening.sh is sourced with. Nothing here is ever echoed to stdout
# beyond pass/fail lines and the output path.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

BASE=${GMS_BASE_URL:-http://localhost:8080}
API="$BASE/api/v1"
SUPERUSER=${GMS_SUPERADMIN_USERNAME:?set GMS_SUPERADMIN_USERNAME}
SUPERPASSWORD=${GMS_SUPERADMIN_PASSWORD:?set GMS_SUPERADMIN_PASSWORD}
ADMIN_A_NAME=${GMS_SCHOOL_A_ADMIN_NAME:-Aarav Menon}
ADMIN_B_NAME=${GMS_SCHOOL_B_ADMIN_NAME:-Beatula Fernandes}

VERIFY_DB=${GMS_VERIFY_DB:-gms_p1_verify}
PG_CONTAINER=${GMS_PG_CONTAINER:-gms-postgres}
PG_USER=${GMS_PG_USER:-gms_user}
PG_PASSWORD_ENV=${GMS_PG_PASSWORD_ENV:-DB_PASSWORD}
OUT=${GMS_P1_FIXTURE_ENV:-/tmp/gms-p1-fixture.env}

[[ -n "${!PG_PASSWORD_ENV:-}" ]] || { echo "FATAL: \$$PG_PASSWORD_ENV must be set"; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "FATAL: jq is required"; exit 1; }

body=$(mktemp)
trap 'rm -f "$body"' EXIT

call() { # call <method> <path> [token] [json] -> http code
  local method=$1 route=$2 token=${3:-} payload=${4:-}
  local args=(-s -o "$body" -w '%{http_code}' -X "$method" "$API$route")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$payload" ]] && args+=(-H 'Content-Type: application/json' -d "$payload")
  curl "${args[@]}"
}

login() { # login <username> <password> -> token
  local code
  code=$(call POST /auth/login "" "{\"username\":\"$1\",\"password\":\"$2\"}")
  [[ "$code" == "200" ]] && jq -r '.accessToken // .token // empty' "$body" || echo ""
}

sql() {
  docker exec -e PGPASSWORD="${!PG_PASSWORD_ENV}" "$PG_CONTAINER" psql -U "$PG_USER" -d "$VERIFY_DB" \
    -v ON_ERROR_STOP=1 -tAc "$1"
}

admin_username() { # first initial + surname, the rule SchoolServiceImpl applies
  local first=${1%% *} last=${1##* }
  printf '%s%s' "$(echo "${first:0:1}" | tr 'A-Z' 'a-z')" "$(echo "$last" | tr 'A-Z' 'a-z')"
}

SCHOOL_A_CODE=${GMS_SCHOOL_A_CODE:-MVP801}
SCHOOL_B_CODE=${GMS_SCHOOL_B_CODE:-MVP802}

# Which two tenants, and what logins their administrators actually got — both read from the database
# before anything logs in, because neither can be derived reliably.
#   * The tenants are named by the codes `verify-mvp-e2e.sh` creates them with. The first draft picked
#     "the two lowest school ids that are not the platform owner's", which is the right answer on a
#     database only the e2e suite has touched and the wrong one on the shared database the live-tenancy
#     harness runs this on: by then C-1 through C-7 have registered eight schools, so the two lowest
#     belong to C-1, hold one pupil between them, and the fixture died on its own
#     `expected pupils in both schools` guard while pointing at the wrong tenants.
#   * The usernames are what `SchoolServiceImpl` wrote after `generateUniqueUsername` resolved a
#     collision, which is not always the name the request carried. The password is unchanged: the
#     service derives it from the base name, without the counter.
SCHOOL_A=$(sql "SELECT id FROM schools WHERE school_code = '$SCHOOL_A_CODE'" | tr -d '[:space:]')
SCHOOL_B=$(sql "SELECT id FROM schools WHERE school_code = '$SCHOOL_B_CODE'" | tr -d '[:space:]')
[[ "$SCHOOL_A" =~ ^[0-9]+$ && "$SCHOOL_B" =~ ^[0-9]+$ ]] \
  || { echo "FATAL: no schools coded $SCHOOL_A_CODE/$SCHOOL_B_CODE in $VERIFY_DB; run verify-mvp-e2e.sh first"; exit 1; }
ADMIN_A_USER=$(sql "SELECT username FROM users WHERE school_id = $SCHOOL_A ORDER BY id LIMIT 1" | tr -d '[:space:]')
ADMIN_B_USER=$(sql "SELECT username FROM users WHERE school_id = $SCHOOL_B ORDER BY id LIMIT 1" | tr -d '[:space:]')
[[ "$ADMIN_A_USER" =~ ^[A-Za-z0-9_]+$ && "$ADMIN_B_USER" =~ ^[A-Za-z0-9_]+$ ]] \
  || { echo "FATAL: neither school coded $SCHOOL_A_CODE/$SCHOOL_B_CODE has an administrator account"; exit 1; }
ADMIN_A_PASS="$(admin_username "$ADMIN_A_NAME")@123"
ADMIN_B_PASS="$(admin_username "$ADMIN_B_NAME")@123"

echo "== logins =="
PLATFORM=$(login "$SUPERUSER" "$SUPERPASSWORD")
TOKEN_A=$(login "$ADMIN_A_USER" "$ADMIN_A_PASS")
TOKEN_B=$(login "$ADMIN_B_USER" "$ADMIN_B_PASS")
[[ -n "$PLATFORM" && -n "$TOKEN_A" && -n "$TOKEN_B" ]] \
  || { echo "FATAL: platform or school-admin login failed; is this the e2e-seeded database?"; exit 1; }
echo "ok: platform, school A ($ADMIN_A_USER in $SCHOOL_A), school B ($ADMIN_B_USER in $SCHOOL_B)"

# ── families ─────────────────────────────────────────────────────────────────
ensure_family() { # ensure_family <email> <first> <last> <phone> <studentId> <token>
  local email=$1
  local existing
  existing=$(sql "SELECT id FROM parents WHERE email = '$email'")
  if [[ -n "$existing" ]]; then
    echo "ok: family $email already present (id $existing)"
    return 0
  fi
  local code
  code=$(call POST /parents/create "$6" \
    "{\"firstName\":\"$2\",\"lastName\":\"$3\",\"email\":\"$email\",\"phoneNumber\":\"$4\",\"studentIds\":[$5]}")
  if [[ "$code" != "200" && "$code" != "201" ]]; then
    echo "FATAL: POST /parents/create for $email answered $code: $(jq -c . "$body" 2>/dev/null)"
    exit 1
  fi
  echo "ok: family $email created"
}

PUPIL_A1=$(sql "SELECT id FROM students WHERE school_id = $SCHOOL_A ORDER BY id LIMIT 1")
PUPIL_A2=$(sql "SELECT id FROM students WHERE school_id = $SCHOOL_A ORDER BY id LIMIT 1 OFFSET 1")
PUPIL_B=$(sql "SELECT id FROM students WHERE school_id = $SCHOOL_B ORDER BY id LIMIT 1")
[[ -n "$PUPIL_A1" && -n "$PUPIL_A2" && -n "$PUPIL_B" ]] \
  || { echo "FATAL: expected pupils in both schools; run verify-mvp-e2e.sh first"; exit 1; }

ensure_family "p1.parent.a1@example.com" "P1" "Guarda"  "5550001111" "$PUPIL_A1" "$TOKEN_A"
ensure_family "p1.parent.a2@example.com" "P1" "Guardb"  "5550001112" "$PUPIL_A2" "$TOKEN_A"
ensure_family "p1.parent.b1@example.com" "P1" "Guardc"  "5550001113" "$PUPIL_B"  "$TOKEN_B"

PARENT_USERNAME=$(sql "SELECT username FROM users WHERE email = 'p1.parent.a1@example.com'")
[[ -n "$PARENT_USERNAME" ]] || { echo "FATAL: parent a1 has no login account"; exit 1; }

# ── the system-school administrator, minted by the platform owner ────────────
SYSADMIN_USER="p1.sysadmin"
if [[ -z "$(sql "SELECT id FROM users WHERE username = '$SYSADMIN_USER'")" ]]; then
  code=$(call POST /users/create "$PLATFORM" \
    "{\"username\":\"$SYSADMIN_USER\",\"email\":\"p1.sysadmin@example.com\",\"password\":\"Sysadmin@123\",\"fullName\":\"P1 System Admin\",\"roles\":[\"ADMIN\"]}")
  if [[ "$code" != "200" && "$code" != "201" ]]; then
    echo "FATAL: POST /users/create answered $code: $(jq -c . "$body" 2>/dev/null)"
    exit 1
  fi
  echo "ok: system-school admin $SYSADMIN_USER minted"
else
  echo "ok: system-school admin $SYSADMIN_USER already present"
fi
SYS_SCHOOL=$(sql "SELECT school_id FROM users WHERE username = '$SYSADMIN_USER'")
SUPER_SCHOOL=$(sql "SELECT u.school_id FROM users u JOIN user_roles r ON r.user_id=u.id WHERE r.role='SUPERADMIN' LIMIT 1")
[[ "$SYS_SCHOOL" == "$SUPER_SCHOOL" ]] \
  || { echo "FATAL: system admin sits in school $SYS_SCHOOL, platform owner in $SUPER_SCHOOL"; exit 1; }
SYSADMIN_TOKEN=$(login "$SYSADMIN_USER" "Sysadmin@123")
[[ -n "$SYSADMIN_TOKEN" ]] || { echo "FATAL: the minted system-school admin cannot log in"; exit 1; }

# A school-B employee with no login account, so the composite-key probe on
# users.employee_id has a foreign employee that is not already spoken for
# (users.employee_id is unique, so a linked employee cannot be reused).
if [[ -z "$(sql "SELECT id FROM employees WHERE email = 'p1.employee.b1@example.com'")" ]]; then
  code=$(call POST /employees/create "$TOKEN_B" \
    "{\"firstName\":\"P1\",\"lastName\":\"Guardd\",\"email\":\"p1.employee.b1@example.com\",\"phoneNumber\":\"5550001114\",\"designation\":\"Lecturer\",\"department\":\"Science\",\"teaching\":true}")
  [[ "$code" == "200" || "$code" == "201" ]] \
    || { echo "FATAL: POST /employees/create answered $code: $(jq -c . "$body" 2>/dev/null)"; exit 1; }
  echo "ok: school-B employee created"
fi

# ── subject, teacher assignment, timetable slot, attendance ──────────────────
# These re-arm rather than accumulate: the negative-test run archives the subject and
# flags the attendance row and the assignment, so a second run needs a live one of each.
if [[ -z "$(sql "SELECT id FROM subjects WHERE school_id = $SCHOOL_A AND status = '1' LIMIT 1")" ]]; then
  code=$(call POST /subjects/create "$TOKEN_A" '{"name":"P1 Mathematics","code":"P1-MATH"}')
  [[ "$code" == "200" || "$code" == "201" ]] \
    || { echo "FATAL: POST /subjects/create answered $code: $(jq -c . "$body" 2>/dev/null)"; exit 1; }
  echo "ok: subject created"
fi
SUBJECT_ID=$(sql "SELECT id FROM subjects WHERE school_id = $SCHOOL_A AND status = '1' ORDER BY id LIMIT 1")

TEACHER_ID=$(sql "SELECT id FROM employees WHERE school_id = $SCHOOL_A ORDER BY id LIMIT 1")
[[ -n "$TEACHER_ID" && -n "$SUBJECT_ID" ]] \
  || { echo "FATAL: school A lacks an employee or subject to assign"; exit 1; }

# Only a classroom with no timetable yet can take a new slot: the timetable service replaces
# the slots of an existing timetable, and the slot it drops is still referenced by the
# attendance row that points at it (uq_slot_day_period / fk_attendance_slot). When every
# classroom already has a timetable, the existing slot is reused instead.
CLASSROOM_ID=$(sql "SELECT c.id FROM classrooms c LEFT JOIN timetables t ON t.classroom_id = c.id AND t.school_id = c.school_id WHERE c.school_id = $SCHOOL_A AND c.status = '1' AND t.id IS NULL ORDER BY c.id LIMIT 1")
FRESH_CLASSROOM=1
if [[ -z "$CLASSROOM_ID" ]]; then
  CLASSROOM_ID=$(sql "SELECT id FROM classrooms WHERE school_id = $SCHOOL_A AND status = '1' ORDER BY id LIMIT 1")
  FRESH_CLASSROOM=0
fi
[[ -n "$CLASSROOM_ID" ]] || { echo "FATAL: school A has no active classroom"; exit 1; }

# An academic year the (teacher, subject, classroom, year) tuple has not used yet. The
# uniqueness rule spans soft-deleted rows too, so a used year stays used.
YEAR=""
for CANDIDATE in 2025-2026 2026-2027 2027-2028 2028-2029 2029-2030; do
  USED=$(sql "SELECT count(*) FROM teacher_assignments WHERE school_id = $SCHOOL_A AND teacher_id = $TEACHER_ID AND subject_id = $SUBJECT_ID AND classroom_id = $CLASSROOM_ID AND academic_year = '$CANDIDATE'")
  if [[ "$USED" == "0" ]]; then YEAR=$CANDIDATE; break; fi
done
[[ -n "$YEAR" ]] || { echo "FATAL: no free academic year for the assignment fixture"; exit 1; }
code=$(call POST /teacher-assignments "$TOKEN_A" \
  "{\"teacherId\":$TEACHER_ID,\"subjectId\":$SUBJECT_ID,\"classroomId\":$CLASSROOM_ID,\"academicYear\":\"$YEAR\"}")
[[ "$code" == "200" || "$code" == "201" ]] \
  || { echo "FATAL: POST /teacher-assignments answered $code: $(jq -c . "$body" 2>/dev/null)"; exit 1; }
echo "ok: teacher assignment created ($YEAR)"
TA_ID=$(sql "SELECT id FROM teacher_assignments WHERE school_id = $SCHOOL_A ORDER BY id DESC LIMIT 1")

SLOT_ID=""
if [[ "$FRESH_CLASSROOM" == "1" ]]; then
  code=$(call POST /timetables "$TOKEN_A" \
    "{\"classroomId\":$CLASSROOM_ID,\"academicYear\":\"$YEAR\",\"slots\":[{\"dayOfWeek\":\"MONDAY\",\"period\":1,\"assignmentId\":$TA_ID}]}")
  [[ "$code" == "200" || "$code" == "201" ]] \
    || { echo "FATAL: POST /timetables answered $code: $(jq -c . "$body" 2>/dev/null)"; exit 1; }
  echo "ok: timetable slot created"
  SLOT_ID=$(sql "SELECT id FROM timetable_slots WHERE assignment_id = $TA_ID ORDER BY id DESC LIMIT 1")
else
  SLOT_ID=$(sql "SELECT id FROM timetable_slots WHERE school_id = $SCHOOL_A ORDER BY id LIMIT 1")
  echo "ok: reusing timetable slot $SLOT_ID (every classroom already has a timetable)"
fi

if [[ -z "$(sql "SELECT id FROM attendances WHERE school_id = $SCHOOL_A AND deleted = false LIMIT 1")" ]]; then
  # (student, slot, date) is unique and the index spans soft-deleted rows, so the fixture
  # picks the first date of the last month that this pupil has no record for.
  DAY_OFFSET=0
  while [[ $DAY_OFFSET -le 30 ]]; do
    CANDIDATE_DATE=$(date -v-${DAY_OFFSET}d +%F)
    USED=$(sql "SELECT count(*) FROM attendances WHERE student_id = $PUPIL_A1 AND timetable_slot_id = $SLOT_ID AND date = '$CANDIDATE_DATE'")
    [[ "$USED" == "0" ]] && break
    DAY_OFFSET=$((DAY_OFFSET + 1))
  done
  [[ $DAY_OFFSET -le 30 ]] || { echo "FATAL: no free date for the attendance fixture"; exit 1; }
  code=$(call POST /attendance "$TOKEN_A" \
    "{\"studentId\":$PUPIL_A1,\"timetableSlotId\":$SLOT_ID,\"status\":\"PRESENT\",\"date\":\"$CANDIDATE_DATE\",\"remarks\":\"p1 fixture\"}")
  [[ "$code" == "200" || "$code" == "201" ]] \
    || { echo "FATAL: POST /attendance answered $code: $(jq -c . "$body" 2>/dev/null)"; exit 1; }
  echo "ok: attendance row created ($CANDIDATE_DATE)"
fi

# ── hand the credentials to the negative-test script ─────────────────────────
umask 077
{
  echo "GMS_SUPERADMIN_USERNAME=$SUPERUSER"
  echo "GMS_SUPERADMIN_PASSWORD=$SUPERPASSWORD"
  echo "GMS_ADMIN_USERNAME=$ADMIN_A_USER"
  # The base name, not the resolved login: `SchoolServiceImpl` derives the temporary password from the
  # base and only uniquifies the username, so writing `$ADMIN_A_USER@123` here would be right while no
  # collision exists and wrong the moment one does.
  echo "GMS_ADMIN_PASSWORD=$ADMIN_A_PASS"
  echo "GMS_ADMIN2_USERNAME=$ADMIN_B_USER"
  echo "GMS_ADMIN2_PASSWORD=$ADMIN_B_PASS"
  echo "GMS_PARENT_USERNAME=$PARENT_USERNAME"
  echo "GMS_PARENT_PASSWORD=welcome123"
  echo "GMS_SYSTEM_ADMIN_USERNAME=$SYSADMIN_USER"
  echo "GMS_SYSTEM_ADMIN_PASSWORD=Sysadmin@123"
  echo "GMS_VERIFY_DB=$VERIFY_DB"
  # The two tenants, by id, as this script resolved them from their codes. The negative tests need
  # exactly these numbers, and reading them from here is the only way the pair cannot drift: the first
  # draft of `verify-p1-hardening.sh` re-derived them as "the lowest school ids that are not the
  # platform's", which is this database's C-1 tenants on any shared run — so its school-B family probe
  # looked in a school that holds no p1 family and four checks failed on a fixture that had been built
  # correctly two scripts earlier.
  echo "GMS_P1_SCHOOL_A_ID=$SCHOOL_A"
  echo "GMS_P1_SCHOOL_B_ID=$SCHOOL_B"
  echo "GMS_PG_CONTAINER=$PG_CONTAINER"
  echo "GMS_PG_USER=$PG_USER"
  echo "GMS_PG_PASSWORD_ENV=$PG_PASSWORD_ENV"
} > "$OUT"
chmod 600 "$OUT"
echo "env written: $OUT"
