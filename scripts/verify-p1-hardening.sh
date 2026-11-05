#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# verify-p1-hardening.sh — negative tests for the post-PR-#25 security fixes.
#
# Every probe demonstrates a refusal or a preserved row that the pre-fix build
# answered differently. The pre-fix behaviours, each live-measured on master
# ef00eb90 before these fixes were written, are named next to the check.
#
# What it needs:
#   * A running backend built from the branch this script ships with, booted
#     against a fresh database so Flyway V1..V9 applied and ddl-auto=validate
#     accepted the schema (the boot itself is the first assertion of V8/V9).
#   * Fixtures created out-of-band before this script runs: two schools with
#     administrators (school A and school B), a school A parent account with
#     its own login, a school A family distinct from that parent's (parent
#     A2), a school A student, an exam chain in school A (exam -> exam subject
#     -> student exam -> result), a teacher assignment chain in school A, a
#     timetable slot in school A with an attendance row behind it, and a
#     system-school administrator minted by the platform owner.
#   * curl and jq.
#
# Credentials come from the environment; nothing here hardcodes one. The SQL
# this script runs is SELECT-only; the two INSERT probes run inside a
# transaction they immediately roll back, mirroring verify-c7's rule that a
# probe may write nothing that survives it.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

BASE=${GMS_BASE_URL:-http://localhost:8080}
API="$BASE/api/v1"
. "$(dirname "${BASH_SOURCE[0]}")/lib/csrf.sh"
SUPERUSER=${GMS_SUPERADMIN_USERNAME:?set GMS_SUPERADMIN_USERNAME}
SUPERPASSWORD=${GMS_SUPERADMIN_PASSWORD:?set GMS_SUPERADMIN_PASSWORD}
ADMIN=${GMS_ADMIN_USERNAME:?set GMS_ADMIN_USERNAME}
ADMINPASSWORD=${GMS_ADMIN_PASSWORD:?set GMS_ADMIN_PASSWORD}
ADMIN2=${GMS_ADMIN2_USERNAME:?set GMS_ADMIN2_USERNAME}
ADMIN2PASSWORD=${GMS_ADMIN2_PASSWORD:?set GMS_ADMIN2_PASSWORD}
PARENT=${GMS_PARENT_USERNAME:?set GMS_PARENT_USERNAME}
PARENTPASSWORD=${GMS_PARENT_PASSWORD:?set GMS_PARENT_PASSWORD}
SYSADMIN=${GMS_SYSTEM_ADMIN_USERNAME:?set GMS_SYSTEM_ADMIN_USERNAME}
SYSADMINPASSWORD=${GMS_SYSTEM_ADMIN_PASSWORD:?set GMS_SYSTEM_ADMIN_PASSWORD}
# The two tenants the fixture script built its families in. They arrive from that script rather than
# being looked up here — see section 0.
SCHOOL_A_ID=${GMS_P1_SCHOOL_A_ID:?set GMS_P1_SCHOOL_A_ID, written by scripts/seed/p1_fixtures.sh}
SCHOOL_B_ID=${GMS_P1_SCHOOL_B_ID:?set GMS_P1_SCHOOL_B_ID, written by scripts/seed/p1_fixtures.sh}

VERIFY_DB=${GMS_VERIFY_DB:-gms_p1_verify}
PG_CONTAINER=${GMS_PG_CONTAINER:-gms-postgres}
PG_USER=${GMS_PG_USER:-gms_user}
PG_PASSWORD_ENV=${GMS_PG_PASSWORD_ENV:-DB_PASSWORD}

failures=0
checks=0
skipped=0
body=$(mktemp)
trap 'rm -f "$body"' EXIT

[[ -n "${!PG_PASSWORD_ENV:-}" ]] || { echo "FATAL: \$$PG_PASSWORD_ENV must be set — without the database the soft-delete rows cannot be proven"; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "FATAL: jq is required"; exit 1; }

check() { # check <name> <expected> <actual>
  checks=$((checks + 1))
  if [[ "$2" == "$3" ]]; then
    printf 'PASS  %s (got %s)\n' "$1" "$3"
  else
    printf 'FAIL  %s (expected %s, got %s)\n' "$1" "$2" "$3"
    failures=$((failures + 1))
  fi
}

call() { # call <method> <path> [token] [json] -> http code; body in $body
  local method=$1 route=$2 token=${3:-} payload=${4:-}
  local args=(-s -o "$body" -w '%{http_code}' -X "$method" "$API$route")
  if [[ "$method" != "GET" && "$method" != "HEAD" ]]; then
    csrf_prepare "$API" || return 1
    args+=("${CSRF_ARGS[@]}")
  fi
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$payload" ]] && args+=(-H 'Content-Type: application/json' -d "$payload")
  curl "${args[@]}"
}

login() { # login <username> <password> -> token (empty on failure)
  local code
  code=$(call POST /auth/login "" "{\"username\":\"$1\",\"password\":\"$2\"}")
  if [[ "$code" == "200" ]]; then
    local t
    t=$(jq -r '.accessToken // .token // empty' "$body")
    printf '%s' "${t:-$(csrf_auth_token)}"
  else
    echo ""
  fi
}

sql() { # sql <statement> — SELECT only per the header
  docker exec -e PGPASSWORD="${!PG_PASSWORD_ENV}" "$PG_CONTAINER" psql -U "$PG_USER" -d "$VERIFY_DB" \
    -v ON_ERROR_STOP=1 -tAc "$1" 2>&1
}

# ── 0. Log in as every fixture identity ──────────────────────────────────────
echo "=== 0. Logins ==="
SUPER=$(login "$SUPERUSER" "$SUPERPASSWORD")
ADMIN_TOKEN=$(login "$ADMIN" "$ADMINPASSWORD")
ADMIN2_TOKEN=$(login "$ADMIN2" "$ADMIN2PASSWORD")
PARENT_TOKEN=$(login "$PARENT" "$PARENTPASSWORD")
SYSADMIN_TOKEN=$(login "$SYSADMIN" "$SYSADMINPASSWORD")
check "superadmin login" "ok" "$([[ -n "$SUPER" ]] && echo ok || echo missing)"
check "school A admin login" "ok" "$([[ -n "$ADMIN_TOKEN" ]] && echo ok || echo missing)"
check "school B admin login" "ok" "$([[ -n "$ADMIN2_TOKEN" ]] && echo ok || echo missing)"
check "school A parent login" "ok" "$([[ -n "$PARENT_TOKEN" ]] && echo ok || echo missing)"
check "system-school admin login" "ok" "$([[ -n "$SYSADMIN_TOKEN" ]] && echo ok || echo missing)"
[[ -n "$SUPER" && -n "$ADMIN_TOKEN" && -n "$ADMIN2_TOKEN" && -n "$PARENT_TOKEN" && -n "$SYSADMIN_TOKEN" ]] \
  || { echo "FATAL: fixtures incomplete; refusing to measure a partial build"; exit 1; }

# The caller's own tenant: the ADMIN token is school A, and every entity lookup
# below is scoped to it so a school-B row can never satisfy a school-A check.
# The two ids come from the fixture script that built the families, rather than being re-derived here.
# The first draft took "the lowest school ids that are not the platform owner's", which is correct only
# on a database that nothing else has touched; on the shared database the live-tenancy harness runs the
# nine suites against, those two schools are C-1's and C-2's, so the school-B family probe searched a
# tenant with no p1 family in it and reported four failures against a fixture that had built correctly.
[[ "$SCHOOL_A_ID" =~ ^[0-9]+$ && "$SCHOOL_B_ID" =~ ^[0-9]+$ ]] \
  || { echo "FATAL: the fixture school ids are not numbers ('$SCHOOL_A_ID' / '$SCHOOL_B_ID')"; exit 1; }

# ── 1. AUTHZ-00: the parent admin surface answers its own handlers ───────────
echo
echo "=== 1. Parent admin routes (pre-fix: 403 from anyRequest().denyAll(); create 500'd) ==="
check "ADMIN reads GET /parents" "200/204" \
  "$([[ $(call GET /parents "$ADMIN_TOKEN") =~ ^(200|204)$ ]] && echo 200/204 || echo other)"
PARENT_EMAIL="p1.parent.a1@example.com"
NEWP=$(call POST /parents/create "$ADMIN_TOKEN" \
  "{\"firstName\":\"Verify\",\"lastName\":\"Family\",\"email\":\"$PARENT_EMAIL\",\"phoneNumber\":\"5550001111\",\"studentIds\":[]}")
# create requires studentIds per ParentRequest @NotEmpty — an empty list must be refused, not 500.
check "ADMIN POST /parents/create without students is a clean refusal" "400" "$NEWP"
check "PARENT is refused the admin list" "403" "$(call GET /parents "$PARENT_TOKEN")"
check "PARENT is refused the admin create" "403" "$(call POST /parents/create "$PARENT_TOKEN" '{}')"
check "PARENT still reads own children" "200" "$(call GET /parents/children "$PARENT_TOKEN")"
check "unauthenticated is refused the admin parent list" "401" "$(call GET /parents "")"

# ── 2. AUTHZ-01: the classroom timetable no longer promises STUDENT ──────────
echo
echo "=== 2. Timetable classroom read (pre-fix: handler granted STUDENT, URL refused) ==="
check "PARENT is refused the classroom timetable" "403" "$(call GET /timetables/classroom/1 "$PARENT_TOKEN")"
check "unauthenticated is refused the classroom timetable" "401" "$(call GET /timetables/classroom/1 "")"

# ── 3. TENW-02: the platform owner's account is not editable from inside ─────
echo
echo "=== 3. Platform-owner takeover (pre-fix: system-school ADMIN reset the superadmin's password) ==="
SUPER_ID=$(sql "SELECT u.id FROM users u JOIN user_roles r ON r.user_id = u.id WHERE r.role = 'SUPERADMIN' LIMIT 1")
check "found the bootstrapped platform owner" "ok" "$([[ -n "$SUPER_ID" ]] && echo ok || echo missing)"
# The payload is otherwise a legitimate update — real username, policy-valid password — so a
# 403 here can only be the ownership guard, never a bean-validation refusal.
SUPER_NAME=$(sql "SELECT username FROM users WHERE id = $SUPER_ID")
TAKEOVER=$(call PUT /users/update "$SYSADMIN_TOKEN" \
  "{\"id\":$SUPER_ID,\"username\":\"$SUPER_NAME\",\"fullName\":\"Taken Over\",\"email\":\"taken.over@example.com\",\"password\":\"Str0ngPass@1\"}")
check "system-school ADMIN cannot PUT the platform owner" "403" "$TAKEOVER"
DELETE_OWNER=$(call DELETE "/users/$SUPER_ID" "$SYSADMIN_TOKEN")
check "system-school ADMIN cannot DELETE the platform owner" "403" "$DELETE_OWNER"

# ── 4. ISO-01/03: a school re-points only its own families ───────────────────
echo
echo "=== 4. parent-student assignment (pre-fix: first-student guard, whole-collection clear) ==="
PARENT_A2_EMAIL="p1.parent.a2@example.com"
# The other school-A family, created out-of-band; find its id by email.
PARENT_A2_ID=$(sql "SELECT id FROM parents WHERE email = '$PARENT_A2_EMAIL'")
check "found the second school-A family" "ok" "$([[ -n "$PARENT_A2_ID" ]] && echo ok || echo missing)"
PARENT_B_ID=$(sql "SELECT p.id FROM parents p JOIN parent_student ps ON ps.parent_id = p.id JOIN students s ON s.id = ps.student_id WHERE s.school_id = $SCHOOL_B_ID LIMIT 1")
check "found a school-B family" "ok" "$([[ -n "$PARENT_B_ID" ]] && echo ok || echo missing)"
SCHOOL_B_STUDENT_ID=$(sql "SELECT id FROM students WHERE school_id = $SCHOOL_B_ID ORDER BY id LIMIT 1")
ASSIGN_FOREIGN=$(call POST /parent-students/assign "$ADMIN_TOKEN" \
  "{\"parentId\":$PARENT_B_ID,\"studentIds\":[$SCHOOL_B_STUDENT_ID]}")
check "school-A admin cannot assign into a school-B family" "403" "$ASSIGN_FOREIGN"
REMOVE_FOREIGN=$(call DELETE "/parent-students/remove?parentId=$PARENT_B_ID&studentId=$SCHOOL_B_STUDENT_ID" "$ADMIN_TOKEN")
check "school-A admin cannot remove from a school-B family" "403" "$REMOVE_FOREIGN"

# ── 5. ISO-02: a parent reads only its own family ────────────────────────────
echo
echo "=== 5. parent-students read as PARENT (pre-fix: same-school family was readable) ==="
OTHER_FAMILY=$(call GET "/parent-students/parent/$PARENT_A2_ID/students" "$PARENT_TOKEN")
check "school-A parent cannot read another school-A family" "403" "$OTHER_FAMILY"

# ── 6. AUD-01..05: deletes preserve the row and its authorship ───────────────
echo
echo "=== 6. Soft deletes (pre-fix: five endpoints removed the row and its audit) ==="
SUBJECT_ID=$(sql "SELECT id FROM subjects WHERE status = '1' AND school_id = $SCHOOL_A_ID ORDER BY id LIMIT 1")
if [[ -n "$SUBJECT_ID" ]]; then
  check "DELETE /subjects archives instead of removing" "204" "$(call DELETE "/subjects/$SUBJECT_ID" "$ADMIN_TOKEN")"
  check "archived subject row survives" "1" "$(sql "SELECT count(*) FROM subjects WHERE id = $SUBJECT_ID AND status = '0'")"
  check "archived subject keeps its creation stamp" "1" "$(sql "SELECT (created_at IS NOT NULL)::int FROM subjects WHERE id = $SUBJECT_ID")"
else
  skipped=$((skipped + 1)); printf 'SKIP  subjects soft-delete (no active subject fixture)\n'
fi
CLASSROOM_ID=$(sql "SELECT id FROM classrooms WHERE status = '1' AND school_id = $SCHOOL_A_ID ORDER BY id DESC LIMIT 1")
if [[ -n "$CLASSROOM_ID" ]]; then
  check "DELETE /classrooms archives instead of removing" "204" "$(call DELETE "/classrooms/$CLASSROOM_ID" "$ADMIN_TOKEN")"
  check "archived classroom row survives" "1" "$(sql "SELECT count(*) FROM classrooms WHERE id = $CLASSROOM_ID AND status = '0'")"
else
  skipped=$((skipped + 1)); printf 'SKIP  classrooms soft-delete (no active classroom fixture)\n'
fi
ATT_ID=$(sql "SELECT id FROM attendances WHERE deleted = false AND school_id = $SCHOOL_A_ID ORDER BY id DESC LIMIT 1")
if [[ -n "$ATT_ID" ]]; then
  ATT_STUDENT=$(sql "SELECT student_id FROM attendances WHERE id = $ATT_ID")
  # The restriction half is measured as a list length, not an id substring: the response
  # carries no id field, so only the count can show the flagged row leaving the read.
  # The length is read from the response file — the HTTP code is a number, and jq's length
  # of a number is the number itself, which would make this check vacuous.
  call GET "/attendance/student/$ATT_STUDENT/range?startDate=2020-01-01&endDate=2030-01-01" "$ADMIN_TOKEN" >/dev/null
  RANGE_BEFORE=$(jq 'length' "$body" 2>/dev/null)
  check "DELETE /attendance flags instead of removing" "200" "$(call DELETE "/attendance/$ATT_ID" "$ADMIN_TOKEN")"
  check "deleted attendance row survives with deleted = true" "1" "$(sql "SELECT count(*) FROM attendances WHERE id = $ATT_ID AND deleted = true")"
  call GET "/attendance/student/$ATT_STUDENT/range?startDate=2020-01-01&endDate=2030-01-01" "$ADMIN_TOKEN" >/dev/null
  RANGE_AFTER=$(jq 'length' "$body" 2>/dev/null)
  check "restricted read drops exactly the flagged row" "$((RANGE_BEFORE - 1))" "$RANGE_AFTER"
else
  skipped=$((skipped + 1)); printf 'SKIP  attendance soft-delete (no attendance fixture)\n'
fi
TA_ID=$(sql "SELECT id FROM teacher_assignments WHERE deleted = false AND school_id = $SCHOOL_A_ID ORDER BY id DESC LIMIT 1")
if [[ -n "$TA_ID" ]]; then
  call GET /teacher-assignments "$ADMIN_TOKEN" >/dev/null
  TA_LIST_BEFORE=$(jq 'length' "$body" 2>/dev/null)
  check "DELETE /teacher-assignments flags instead of removing" "204" "$(call DELETE "/teacher-assignments/$TA_ID" "$ADMIN_TOKEN")"
  check "deleted assignment row survives with deleted = true" "1" "$(sql "SELECT count(*) FROM teacher_assignments WHERE id = $TA_ID AND deleted = true")"
  call GET /teacher-assignments "$ADMIN_TOKEN" >/dev/null
  TA_LIST_AFTER=$(jq 'length' "$body" 2>/dev/null)
  check "assignment list drops exactly the flagged row" "$((TA_LIST_BEFORE - 1))" "$TA_LIST_AFTER"
else
  skipped=$((skipped + 1)); printf 'SKIP  teacher-assignment soft-delete (no assignment fixture)\n'
fi

# ── 7. DATA-01..05: the composite foreign keys refuse a straddle ─────────────
echo
echo "=== 7. Composite FKs (pre-fix: eleven plain FKs let a row point across schools) ==="
if [[ "$SCHOOL_A_ID" != "$SCHOOL_B_ID" ]]; then
  FOREIGN_CLASSROOM=$(sql "SELECT id FROM classrooms WHERE school_id = $SCHOOL_B_ID ORDER BY id LIMIT 1")
  STRADDLE=$(docker exec -e PGPASSWORD="${!PG_PASSWORD_ENV}" "$PG_CONTAINER" psql -U "$PG_USER" -d "$VERIFY_DB" \
    -v ON_ERROR_STOP=1 -tAc "BEGIN; INSERT INTO students (school_id, classroom_id, student_id, first_name, last_name, email, gender, status, created_at, updated_at) VALUES ($SCHOOL_A_ID, $FOREIGN_CLASSROOM, 'P1VERIFY', 'Probe', 'Probe', 'p1probe@example.com', 'MALE', '1', now(), now()); ROLLBACK;" 2>&1)
  check "student -> foreign classroom is refused by the composite key" "ok" \
    "$([[ "$STRADDLE" == *"fk_students_classroom_same_school"* ]] && echo ok || echo "no refusal: $STRADDLE")"
  FOREIGN_EMPLOYEE=$(sql "SELECT e.id FROM employees e LEFT JOIN users u ON u.employee_id = e.id WHERE e.school_id = $SCHOOL_B_ID AND u.id IS NULL ORDER BY e.id LIMIT 1")
  if [[ -n "$FOREIGN_EMPLOYEE" ]]; then
    STRADDLE2=$(docker exec -e PGPASSWORD="${!PG_PASSWORD_ENV}" "$PG_CONTAINER" psql -U "$PG_USER" -d "$VERIFY_DB" \
      -v ON_ERROR_STOP=1 -tAc "BEGIN; INSERT INTO users (school_id, full_name, email, username, password, enabled, require_password_change, user_status, password_expired, account_locked, email_verified, phone_verified, login_attempts, created_at, updated_at) VALUES ($SCHOOL_A_ID, 'Probe', 'p1probe2@example.com', 'p1probe2', 'x', true, false, '1', false, false, false, false, 0, now(), now()); UPDATE users SET employee_id = $FOREIGN_EMPLOYEE WHERE username = 'p1probe2'; ROLLBACK;" 2>&1)
    check "user -> foreign employee is refused by the composite key" "ok" \
      "$([[ "$STRADDLE2" == *"fk_users_employee_same_school"* ]] && echo ok || echo "no refusal: $STRADDLE2")"
  else
    skipped=$((skipped + 1)); printf 'SKIP  users.employee_id probe (no unlinked school-B employee)\n'
  fi
else
  skipped=$((skipped + 1)); printf 'SKIP  composite-FK probes (only one school in the fixture database)\n'
fi

echo
echo "────────────────────────────────────────────"
printf 'P1 hardening: %d passed, %d failed, %d skipped\n' "$checks" "$failures" "$skipped"
[[ "$failures" -eq 0 ]] || exit 1
