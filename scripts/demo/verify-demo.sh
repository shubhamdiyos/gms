#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# verify-demo.sh — reads the seeded tenant back, as each demo role, through the same
# endpoints the React SPA and the Flutter client call.
#
# It is read-only. It signs in with the credentials recorded by seed-demo.sh, discovers
# today's attendance from the pupil's own feed (so it makes no assumption about which
# weekdays were seeded), and fails when any screen would open empty or answer an error.
#
# Usage:  scripts/demo/verify-demo.sh
# Env:    GMS_BASE_URL      backend root  (default http://localhost:8080)
#         GMS_DEMO_ENV      state file    (default /tmp/gms-demo-tenant.env)
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

BASE=${GMS_BASE_URL:-http://localhost:8080}
API="$BASE/api/v1"
OUT=${GMS_DEMO_ENV:-/tmp/gms-demo-tenant.env}

say() { printf '%s\n' "$*" >&2; }
die() { printf 'FATAL: %s\n' "$*" >&2; exit 1; }

command -v jq >/dev/null 2>&1 || die "jq is required"
command -v curl >/dev/null 2>&1 || die "curl is required"
[[ -f "$OUT" ]] || die "no state file at $OUT; run scripts/demo/seed-demo.sh first"
# shellcheck disable=SC1090
. "$OUT"
[[ -n "${DEMO_PASSWORD:-}" ]] || die "$OUT carries no DEMO_PASSWORD"

. "$(dirname "${BASH_SOURCE[0]}")/../lib/csrf.sh"

body=$(mktemp "${TMPDIR:-/tmp}/gms-demo-verify.XXXXXX")
trap 'rm -f "$body"' EXIT
pass=0
fail=0

get() { # get <token> <path> -> status; body in $body
  curl -s -o "$body" -w '%{http_code}' -H "Authorization: Bearer $1" "$API$2"
}

sign_in() { # sign_in <username> -> token
  csrf_prepare "$API" >/dev/null || { say "the CSRF handshake failed"; return 1; }
  local code token
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/auth/login" \
    "${CSRF_ARGS[@]}" -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg u "$1" --arg p "$DEMO_PASSWORD" '{username:$u,password:$p}')")
  [[ "$code" == "200" ]] || { say "login $1 answered $code"; return 1; }
  token=$(csrf_auth_token)
  [[ -n "$token" ]] || { say "login $1 set no gms_auth cookie"; return 1; }
  printf '%s' "$token"
}

check() { # check <token> <path> <jq filter that must be truthy> <label>
  local code
  code=$(get "$1" "$2")
  if [[ "$code" != "200" ]]; then
    printf 'FAIL  %-48s %s (HTTP %s)\n' "$4" "$2" "$code"
    fail=$((fail + 1))
    return
  fi
  if jq -e "$3" "$body" >/dev/null 2>&1; then
    printf 'ok    %-48s %s\n' "$4" "$2"
    pass=$((pass + 1))
  else
    printf 'FAIL  %-48s %s (unexpected body)\n' "$4" "$2"
    head -c 200 "$body" >&2
    printf '\n' >&2
    fail=$((fail + 1))
  fi
}

ADMIN=$(sign_in "${DEMO_ADMIN_USERNAME:-dadmin}") || exit 1
TEACHER=$(sign_in "${DEMO_TEACHER_USERNAME:-demo.teacher}") || exit 1
ACCOUNTANT=$(sign_in "${DEMO_ACCOUNTANT_USERNAME:-demo.accountant}") || exit 1
STUDENT=$(sign_in "${DEMO_STUDENT_USERNAME:-lfarouk}") || exit 1
PARENT=$(sign_in "${DEMO_PARENT_USERNAME:-pfarouk}") || exit 1
echo "all five demo logins signed in"

CLASSROOM_ID=${DEMO_CLASSROOM_ID:-}
STUDENT_ID=${DEMO_STUDENT_ID:-}
[[ -n "$CLASSROOM_ID" && -n "$STUDENT_ID" ]] || die \
  "$OUT predates this verifier (no classroom or pupil id); rerun seed-demo.sh"

check "$ADMIN" /dashboard/school '. != null' "admin dashboard"
check "$ADMIN" '/students/search?query=demo.invalid&size=50' '.totalElements == 4' "admin sees the four pupils"
check "$ADMIN" /classrooms '. | length >= 1' "admin classrooms"
check "$ADMIN" /subjects '. | length >= 2' "admin subjects"
check "$ADMIN" /academic-years '.[] | select(.current == true)' "current academic year"
check "$ADMIN" /teacher-assignments '. | length >= 2' "teacher assignments"
check "$ADMIN" /fees '. | length >= 2' "fee heads"
check "$ADMIN" /student-fees '. | length >= 5' "student fees"
check "$ADMIN" /fee-payments '.[] | select(.paymentReference == "DEMO-RCPT-001")' "part payment recorded"
check "$ADMIN" /announcements '.[] | select(.title == "Parent-Teacher Meeting")' "announcement present"
check "$ADMIN" /notifications '.[] | select(.title == "Fee reminder")' "notification present"
check "$ADMIN" "/students/$STUDENT_ID" '.firstName == "Leila"' "admin pupil detail"

check "$TEACHER" /timetables/teacher/me '.slots | length >= 10' "teacher timetable"
check "$TEACHER" /classrooms/teacher/assigned '. | length >= 1' "teacher assigned classrooms"

# The pupil feed is the authority on which days were seeded; ask it instead of assuming.
from=$(date -v-14d +%F 2>/dev/null || date -d '14 days ago' +%F)
to=$(date +%F)
if [[ "$(get "$TEACHER" "/attendance/student/$STUDENT_ID?from=$from&to=$to")" == "200" ]] \
  && jq -e 'length >= 1' "$body" >/dev/null 2>&1; then
  printf 'ok    %-48s %s\n' "teacher sees pupil attendance" "/attendance/student/$STUDENT_ID"
  pass=$((pass + 1))
  day=$(jq -r '.[0].date' "$body")
  check "$TEACHER" "/attendance/classroom/$CLASSROOM_ID?date=$day" '. | length >= 4' \
    "teacher classroom attendance ($day)"
else
  printf 'FAIL  %-48s %s (no attendance in the last 14 days)\n' \
    "teacher sees pupil attendance" "/attendance/student/$STUDENT_ID"
  fail=$((fail + 1))
fi

check "$ACCOUNTANT" /student-fees '. | length >= 5' "accountant student fees"
check "$ACCOUNTANT" /fee-payments '.[] | select(.paymentReference == "DEMO-RCPT-001")' "accountant payment ledger"
check "$ACCOUNTANT" /financial-dashboard '. != null' "accountant financial dashboard"

check "$STUDENT" /students/profile '.firstName == "Leila"' "student profile"
check "$STUDENT" /students/my-classes '. | length >= 1' "student class"
check "$STUDENT" /students/my-attendance '. | length >= 1' "student attendance"
check "$STUDENT" /students/my-results '. | length >= 1' "student results"
check "$STUDENT" /students/my-fees '. | length >= 2' "student fees"
check "$STUDENT" /students/my-timetable '. | length >= 1' "student timetable"
check "$STUDENT" /students/my-announcements 'length >= 1' "student announcements"
check "$STUDENT" /notifications '.[] | select(.title == "Fee reminder")' "student notification bell"

check "$PARENT" /parents/children '. | length >= 1' "parent children"
check "$PARENT" "/parents/children/$STUDENT_ID/attendance" 'length >= 1' "parent child attendance"
check "$PARENT" "/parents/children/$STUDENT_ID/results" 'length >= 1' "parent child results"
check "$PARENT" "/parents/children/$STUDENT_ID/fees" 'length >= 2' "parent child fees"
check "$PARENT" /parents/profile '. != null' "parent profile"

printf -- '---- %d passed, %d failed\n' "$pass" "$fail"
(( fail == 0 ))
