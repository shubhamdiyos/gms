#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# seed-demo.sh — builds one complete, presentable demo tenant through the API.
#
# Everything this script creates is created the way the product creates it: the platform
# owner signs in, POST /schools/create provisions the school and its administrator, and
# every row below that is a request the SPA or the Flutter client makes. Nothing is
# inserted into the database directly, so a demo that works is a demo of the real stack.
#
# What it leaves behind, all in the demo tenant:
#   * school DEMO001 + administrator, with a known password
#   * academic year, one classroom, two subjects, one teacher login, one accountant login
#   * four pupils (one with a student login), one parent login linked to a pupil
#   * teacher assignments + a Monday–Friday timetable
#   * two fee heads, student fees, and one part payment recorded by the accountant
#   * one exam, one registration, one published result
#   * five school days of attendance with mixed statuses
#   * one published announcement and one in-app notification
#
# Idempotency: a second run reuses what the first created. The one thing that cannot be
# rediscovered through the API is an exam subject id (Exam.examSubjects is @JsonIgnore and
# there is no list route), so it is written to the state file and reused from there. If the
# state file is gone but the tenant exists, the script refuses rather than guessing — use a
# fresh database or a different DEMO_SCHOOL_CODE.
#
# Credentials: DEMO_PASSWORD is never hard-coded. Pass it in, or let this script generate
# one; either way it is written only to the state file (mode 600) and printed to the
# terminal that ran the seed. The platform owner's password is never printed.
#
# Usage:
#   GMS_SUPERADMIN_USERNAME=... GMS_SUPERADMIN_PASSWORD=... \
#     scripts/demo/seed-demo.sh
#
# Environment:
#   GMS_BASE_URL                     backend root            (default http://localhost:8080)
#   GMS_SUPERADMIN_USERNAME/PASSWORD platform owner          (required)
#   DEMO_PASSWORD                    password for demo logins (generated when unset)
#   DEMO_SCHOOL_CODE                 school code             (default DEMO001)
#   DEMO_SCHOOL_NAME                 school name             (default Demo Public School)
#   DEMO_ACADEMIC_YEAR               year label              (default 2026-2027)
#   GMS_DEMO_ENV                     state file              (default /tmp/gms-demo-tenant.env)
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

BASE=${GMS_BASE_URL:-http://localhost:8080}
API="$BASE/api/v1"
SUPERUSER=${GMS_SUPERADMIN_USERNAME:?set GMS_SUPERADMIN_USERNAME to the platform owner username}
SUPERPASSWORD=${GMS_SUPERADMIN_PASSWORD:?set GMS_SUPERADMIN_PASSWORD to the platform owner password}

OUT=${GMS_DEMO_ENV:-/tmp/gms-demo-tenant.env}

# The state file from an earlier run is always read: the exam subject id it records cannot
# be rediscovered through the API, and a rerun that does not know it would refuse. Anything
# the caller passes in the environment wins over the file, and a file that describes a
# different school contributes only its password.
caller_password=${DEMO_PASSWORD:-}
caller_school_code=${DEMO_SCHOOL_CODE:-}
caller_school_name=${DEMO_SCHOOL_NAME:-}
caller_admin_name=${DEMO_ADMIN_NAME:-}
caller_year=${DEMO_ACADEMIC_YEAR:-}
if [[ -f "$OUT" ]]; then
  # shellcheck disable=SC1090
  . "$OUT"
  if [[ -n "${DEMO_SCHOOL_CODE:-}" && "$DEMO_SCHOOL_CODE" != "${caller_school_code:-$DEMO_SCHOOL_CODE}" ]]; then
    DEMO_EXAM_ID=""
    DEMO_EXAM_SUBJECT_MATH_ID=""
    DEMO_STUDENT_EXAM_ID=""
  fi
fi
DEMO_PASSWORD=${caller_password:-${DEMO_PASSWORD:-}}
SCHOOL_CODE=${caller_school_code:-${DEMO_SCHOOL_CODE:-DEMO001}}
SCHOOL_NAME=${caller_school_name:-${DEMO_SCHOOL_NAME:-Demo Public School}}
ADMIN_NAME=${caller_admin_name:-${DEMO_ADMIN_NAME:-Demo Admin}}
YEAR=${caller_year:-${DEMO_ACADEMIC_YEAR:-2026-2027}}
CLASSROOM_NAME="Grade 9 A"
CLASSROOM_GRADE="9"
CLASSROOM_SECTION="A"
EXAM_NAME="Mid-Term Examination 2026"
PAYMENT_REFERENCE="DEMO-RCPT-001"

GENERATED_PASSWORD=0
if [[ -z "${DEMO_PASSWORD:-}" ]]; then
  DEMO_PASSWORD="Demo_$(openssl rand -hex 6)2026!"
  GENERATED_PASSWORD=1
fi
# A password that is created only to be rotated away from, for accounts whose password the
# server generates itself (students, parents) and for role accounts created through /users.
BOOTSTRAP_PASSWORD="Bootstrap_$(openssl rand -hex 4)2026!"

say() { printf '%s\n' "$*" >&2; }
die() { printf 'FATAL: %s\n' "$*" >&2; exit 1; }

command -v jq >/dev/null 2>&1 || die "jq is required"
command -v curl >/dev/null 2>&1 || die "curl is required"

body=$(mktemp "${TMPDIR:-/tmp}/gms-demo-body.XXXXXX")
timetable_json=$(mktemp "${TMPDIR:-/tmp}/gms-demo-timetable.XXXXXX")
trap 'rm -f "$body" "$timetable_json"' EXIT

. "$(dirname "${BASH_SOURCE[0]}")/../lib/csrf.sh"

call() { # call <method> <path> [token] [json] -> HTTP status; body in $body
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

expect() { # expect <status> <what> [also-accepted...]
  local status=$1 what=$2 code
  shift 2
  (($# > 0)) || set -- 200
  for code in "$@"; do
    [[ "$status" == "$code" ]] && return 0
  done
  printf 'FATAL: %s answered %s\n  said: %s\n' "$what" "$status" "$(head -c 500 "$body")" >&2
  exit 1
}

login() { # login <username> <password> -> token, non-zero when refused
  csrf_auth_token >/dev/null 2>&1 || true
  local code
  code=$(call POST /auth/login "" "$(jq -nc --arg u "$1" --arg p "$2" '{username:$u,password:$p}')")
  [[ "$code" == "200" ]] || return 1
  local token
  token=$(jq -r '.accessToken // empty' "$body" 2>/dev/null)
  [[ -n "$token" ]] || token=$(csrf_auth_token)
  [[ -n "$token" ]] || return 1
  printf '%s' "$token"
}

jwt_claim() { # jwt_claim <token> <claim>
  printf '%s' "$1" | jq -Rr --arg c "$2" '
    split(".")[1]
    | gsub("-"; "+") | gsub("_"; "/")
    | . + ("==="[: (4 - (length % 4)) % 4])
    | @base64d | fromjson | .[$c]'
}

derived_username() { # derived_username <first> <last> — UsernameGenerator's rule
  printf '%s%s' "${1:0:1}" "$2" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9'
}

PROBE_USER=""
PROBE_TOKEN=""
probe_login() { # probe_login <base> <password> [schoolId]; collision suffixes are base1..base3
  local base=$1 password=$2 want=${3:-} i candidate token
  for i in "" 1 2 3; do
    candidate="$base$i"
    if token=$(login "$candidate" "$password"); then
      if [[ -z "$want" ]] || [[ "$(jwt_claim "$token" schoolId)" == "$want" ]]; then
        PROBE_USER="$candidate"
        PROBE_TOKEN="$token"
        return 0
      fi
    fi
  done
  return 1
}

rotate() { # rotate <token> <current> <new>
  local status
  status=$(csrf_rotate_password "$API" "$1" "$2" "$3")
  [[ "$status" == "200" ]] || die "password rotation answered $status"
}

shift_date() { # shift_date <days, may be negative> -> YYYY-MM-DD
  local n=$1 arg
  # This BSD date rejects -v0d ("Cannot apply date adjustment"); the sign is required.
  if [[ "$n" == -* ]]; then arg=$n; else arg="+$n"; fi
  if date -v"${arg}"d +%F >/dev/null 2>&1; then
    date -v"${arg}"d +%F
  elif [[ "$n" == -* ]]; then
    date -d "${n#-} days ago" +%F
  else
    date -d "+${n} days" +%F
  fi
}

weekday_of() { # weekday_of <YYYY-MM-DD> -> MONDAY..SUNDAY
  if date -j -f "%Y-%m-%d" "$1" "+%A" >/dev/null 2>&1; then
    date -j -f "%Y-%m-%d" "$1" "+%A" | tr '[:lower:]' '[:upper:]'
  else
    date -d "$1" "+%A" | tr '[:lower:]' '[:upper:]'
  fi
}

# ── 0. The backend must be the one that bootstraps the platform account ───────
say "Checking $BASE …"
health=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/health/readiness" 2>/dev/null)
[[ "$health" == "200" ]] || die "$BASE is not healthy (readiness answered $health); start the backend first"

PLATFORM_TOKEN=$(login "$SUPERUSER" "$SUPERPASSWORD") || die \
  "the platform owner could not sign in at $BASE. Check APP_SUPERADMIN_ENABLED, APP_SUPERADMIN_USERNAME and APP_SUPERADMIN_PASSWORD on the running backend."

code=$(call GET /schools "$PLATFORM_TOKEN")
if [[ "$code" == "403" ]]; then
  die "the platform owner is still flagged requirePasswordChange (403). Boot the backend with APP_SUPERADMIN_FORCE_PASSWORD_CHANGE=false, or sign in once and rotate its password."
fi
expect "$code" "GET /schools"

# ── 1. School + administrator ─────────────────────────────────────────────────
SCHOOL_ID=$(jq -r --arg c "$SCHOOL_CODE" '.[] | select(.schoolCode == $c) | .id' "$body" | head -1)
ADMIN_BASE=$(derived_username "${ADMIN_NAME%% *}" "${ADMIN_NAME#* }")

if [[ -n "$SCHOOL_ID" && "$SCHOOL_ID" != "null" ]]; then
  say "School $SCHOOL_CODE exists (id $SCHOOL_ID); reusing it."
  probe_login "$ADMIN_BASE" "$DEMO_PASSWORD" "$SCHOOL_ID" || die \
    "school $SCHOOL_CODE exists but no administrator signs in with the demo password from $OUT. Use a fresh database, a different DEMO_SCHOOL_CODE, or the password that tenant was seeded with."
  ADMIN_USERNAME=$PROBE_USER
  ADMIN_TOKEN=$PROBE_TOKEN
else
  say "Creating school $SCHOOL_CODE …"
  code=$(call POST /schools/create "$PLATFORM_TOKEN" "$(jq -nc \
    --arg schoolName "$SCHOOL_NAME" \
    --arg schoolCode "$SCHOOL_CODE" \
    --arg adminFullName "$ADMIN_NAME" \
    '{schoolName:$schoolName,schoolCode:$schoolCode,email:"office@demo.invalid",
      adminFullName:$adminFullName,adminEmail:"demo.admin@demo.invalid",
      address:"12 Academy Road, Bengaluru",phone:"9800002001",
      principalName:"Demo Principal",establishedYear:2015,boardAffiliation:"CBSE"}')")
  expect "$code" "POST /schools/create"
  SCHOOL_ID=$(jq -r '.id' "$body")
  csrf_issued_admin "$body" || die "the school was created but no administrator credential was returned"
  issue_user=$ISSUED_ADMIN
  issue_password=$ISSUED_ADMIN_PASSWORD

  issued_token=$(login "$issue_user" "$issue_password") || die \
    "the administrator credential just issued does not sign in"
  [[ "$(jwt_claim "$issued_token" schoolId)" == "$SCHOOL_ID" ]] || die \
    "the issued administrator belongs to another tenant"
  rotate "$issued_token" "$issue_password" "$DEMO_PASSWORD"
  ADMIN_TOKEN=$(login "$issue_user" "$DEMO_PASSWORD") || die \
    "the administrator password was rotated but the new one does not sign in"
  ADMIN_USERNAME=$issue_user
fi

# ── 2. Academic year, classroom, subjects ─────────────────────────────────────
code=$(call GET /academic-years "$ADMIN_TOKEN"); expect "$code" "GET /academic-years"
YEAR_NAME=$(jq -r '.[] | select(.current == true) | .name' "$body" | head -1)
if [[ -z "$YEAR_NAME" || "$YEAR_NAME" == "null" ]]; then
  say "Creating academic year $YEAR …"
  start="${YEAR%%-*}-04-01"
  end="$(( ${YEAR%%-*} + 1 ))-03-31"
  code=$(call POST /academic-years "$ADMIN_TOKEN" "$(jq -nc \
    --arg name "$YEAR" --arg start "$start" --arg end "$end" \
    '{name:$name,startDate:$start,endDate:$end,current:true}')")
  expect "$code" "POST /academic-years"
  YEAR_NAME=$YEAR
else
  say "Using the current academic year $YEAR_NAME."
fi

code=$(call GET /classrooms "$ADMIN_TOKEN"); expect "$code" "GET /classrooms"
CLASSROOM_ID=$(jq -r --arg n "$CLASSROOM_NAME" '.[] | select(.name == $n) | .id' "$body" | head -1)
if [[ -z "$CLASSROOM_ID" || "$CLASSROOM_ID" == "null" ]]; then
  say "Creating classroom $CLASSROOM_NAME …"
  code=$(call POST /classrooms/create "$ADMIN_TOKEN" "$(jq -nc \
    --arg n "$CLASSROOM_NAME" --arg g "$CLASSROOM_GRADE" --arg s "$CLASSROOM_SECTION" \
    '{name:$n,grade:$g,section:$s}')")
  expect "$code" "POST /classrooms/create"
  CLASSROOM_ID=$(jq -r '.id' "$body")
fi

ensure_subject() { # ensure_subject <name> <code> -> id in $SUBJECT_ID
  local name=$1 code_value=$2
  code=$(call GET /subjects "$ADMIN_TOKEN"); expect "$code" "GET /subjects"
  SUBJECT_ID=$(jq -r --arg c "$code_value" '.[] | select(.code == $c) | .id' "$body" | head -1)
  if [[ -z "$SUBJECT_ID" || "$SUBJECT_ID" == "null" ]]; then
    code=$(call POST /subjects/create "$ADMIN_TOKEN" "$(jq -nc --arg n "$name" --arg c "$code_value" '{name:$n,code:$c}')")
    expect "$code" "POST /subjects/create"
    SUBJECT_ID=$(jq -r '.id' "$body")
  fi
}
ensure_subject "Mathematics" "MATH-9"; MATH_ID=$SUBJECT_ID
ensure_subject "Science" "SCI-9";     SCIENCE_ID=$SUBJECT_ID

# ── 3. Teacher, accountant ────────────────────────────────────────────────────
TEACHER_EMAIL="meera.iyer@demo.invalid"
# The employee record is needed on every run — the timetable assignments point at it — and
# the login probe does not reveal it, so resolve it before deciding whether to register.
code=$(call GET /employees "$ADMIN_TOKEN"); expect "$code" "GET /employees"
EMP_ID=$(jq -r --arg e "$TEACHER_EMAIL" '.[] | select(.email == $e) | .id' "$body" | head -1)
if probe_login "demo.teacher" "$DEMO_PASSWORD"; then
  TEACHER_USERNAME=$PROBE_USER
  TEACHER_TOKEN=$PROBE_TOKEN
else
  if [[ -z "$EMP_ID" || "$EMP_ID" == "null" ]]; then
    code=$(call POST /employees/create "$ADMIN_TOKEN" "$(jq -nc \
      --arg e "$TEACHER_EMAIL" \
      '{firstName:"Meera",lastName:"Iyer",email:$e,phoneNumber:"9800002002",
        designation:"Senior Teacher",department:"ACADEMICS",teaching:true}')")
    expect "$code" "POST /employees/create"
    EMP_ID=$(jq -r '.id' "$body")
  fi
  say "Registering the teacher login demo.teacher …"
  code=$(call POST /auth/self-register "$ADMIN_TOKEN" "$(jq -nc \
    --argjson emp "$EMP_ID" --argjson school "$SCHOOL_ID" \
    --arg u "demo.teacher" --arg p "$DEMO_PASSWORD" \
    '{empId:$emp,schoolId:$school,username:$u,password:$p}')")
  if [[ "$code" != "200" ]]; then
    # 409 means the employee already has a login under another name; the state file is
    # the only place that name is recorded.
    die "self-register answered $code: $(head -c 300 "$body"). The employee may already have a login with a different username."
  fi
  TEACHER_TOKEN=$(login "demo.teacher" "$DEMO_PASSWORD") || die "demo.teacher does not sign in after registration"
  TEACHER_USERNAME="demo.teacher"
fi
[[ -n "$EMP_ID" && "$EMP_ID" != "null" ]] || die "the teacher employee record is missing"
[[ -n "$(jwt_claim "$TEACHER_TOKEN" employeeId)" ]] || die "the teacher token carries no employeeId"

if probe_login "demo.accountant" "$DEMO_PASSWORD"; then
  ACCOUNTANT_USERNAME=$PROBE_USER
  ACCOUNTANT_TOKEN=$PROBE_TOKEN
else
  say "Creating the accountant login demo.accountant …"
  code=$(call POST /users/create "$ADMIN_TOKEN" "$(jq -nc \
    --arg u "demo.accountant" --arg p "$BOOTSTRAP_PASSWORD" \
    '{username:$u,email:"demo.accountant@demo.invalid",password:$p,
      fullName:"Rahul Verma",phoneNumber:"9800002003",roles:["ACCOUNTANT"]}')")
  expect "$code" "POST /users/create (ACCOUNTANT)"
  bootstrap_token=$(login "demo.accountant" "$BOOTSTRAP_PASSWORD") || die \
    "the accountant account was created but its bootstrap password does not sign in"
  rotate "$bootstrap_token" "$BOOTSTRAP_PASSWORD" "$DEMO_PASSWORD"
  ACCOUNTANT_TOKEN=$(login "demo.accountant" "$DEMO_PASSWORD") || die \
    "the accountant password was rotated but the new one does not sign in"
  ACCOUNTANT_USERNAME="demo.accountant"
fi

# ── 4. Pupils and one linked parent ───────────────────────────────────────────
ensure_student() { # ensure_student <first> <last> <email> <dob> <gender> -> $STUDENT_ID/$STUDENT_USER
  local first=$1 last=$2 email=$3 dob=$4 gender=$5 base query
  base=$(derived_username "$first" "$last")
  # The search matches one column at a time, so "Leila Farouk" would match nothing; the
  # email is the unique key the script already owns.
  query=$(jq -rn --arg q "$email" '$q | @uri')
  code=$(call GET "/students/search?query=$query" "$ADMIN_TOKEN")
  expect "$code" "GET /students/search"
  STUDENT_ID=$(jq -r --arg e "$email" \
    '.content[] | select(.email == $e) | .id' "$body" | head -1)

  if [[ -n "$STUDENT_ID" && "$STUDENT_ID" != "null" ]]; then
    probe_login "$base" "$DEMO_PASSWORD" || die \
      "pupil $first $last exists but their login does not answer the demo password; use a fresh database or reseed it"
    STUDENT_USER=$PROBE_USER
    return 0
  fi

  code=$(call POST /students/create "$ADMIN_TOKEN" "$(jq -nc \
    --arg f "$first" --arg l "$last" --arg e "$email" --arg d "$dob" --arg g "$gender" \
    --argjson c "$CLASSROOM_ID" \
    '{firstName:$f,lastName:$l,email:$e,phoneNumber:"9800002004",
      dateOfBirth:$d,gender:$g,classId:$c}')")
  expect "$code" "POST /students/create ($first $last)"
  STUDENT_ID=$(jq -r '.id' "$body")
  local issued
  issued=$(jq -r '.temporaryPassword // empty' "$body")
  [[ -n "$issued" ]] || die "student creation returned no temporary password"
  probe_login "$base" "$issued" || die \
    "pupil $first $last was created but the derived username $base does not sign in with the issued password"
  local issued_token=$PROBE_TOKEN
  STUDENT_USER=$PROBE_USER
  rotate "$issued_token" "$issued" "$DEMO_PASSWORD"
}

ensure_student "Leila" "Farouk" "leila.farouk@demo.invalid" "2011-05-14" "FEMALE"
LEILA_ID=$STUDENT_ID; LEILA_USER=$STUDENT_USER
ensure_student "Omar" "Hassan" "omar.hassan@demo.invalid" "2011-08-02" "MALE"; OMAR_ID=$STUDENT_ID
ensure_student "Sara" "Nair" "sara.nair@demo.invalid" "2011-11-23" "FEMALE"; SARA_ID=$STUDENT_ID
ensure_student "Zayn" "Ali" "zayn.ali@demo.invalid" "2011-02-09" "MALE"; ZAYN_ID=$STUDENT_ID

if probe_login "pfarouk" "$DEMO_PASSWORD"; then
  PARENT_USERNAME=$PROBE_USER
else
  say "Creating the parent login pfarouk …"
  code=$(call POST /parents/create "$ADMIN_TOKEN" "$(jq -nc --argjson s "$LEILA_ID" \
    '{firstName:"Priya",lastName:"Farouk",email:"priya.farouk@demo.invalid",
      phoneNumber:"9800002008",studentIds:[$s]}')")
  expect "$code" "POST /parents/create"
  issued=$(jq -r '.temporaryPassword // empty' "$body")
  [[ -n "$issued" ]] || die "parent creation returned no temporary password"
  probe_login "pfarouk" "$issued" || die \
    "the parent was created but pfarouk does not sign in with the issued password"
  parent_token=$PROBE_TOKEN
  PARENT_USERNAME=$PROBE_USER
  rotate "$parent_token" "$issued" "$DEMO_PASSWORD"
fi

# ── 5. Teaching assignments + timetable ───────────────────────────────────────
ensure_assignment() { # ensure_assignment <subjectId> -> $ASSIGNMENT_ID
  local subject=$1
  code=$(call GET /teacher-assignments "$ADMIN_TOKEN"); expect "$code" "GET /teacher-assignments"
  ASSIGNMENT_ID=$(jq -r --argjson s "$subject" --argjson c "$CLASSROOM_ID" --arg y "$YEAR_NAME" \
    '.[] | select(.subject.id == $s and .classroom.id == $c and .academicYear == $y) | .id' "$body" | head -1)
  if [[ -z "$ASSIGNMENT_ID" || "$ASSIGNMENT_ID" == "null" ]]; then
    code=$(call POST /teacher-assignments "$ADMIN_TOKEN" "$(jq -nc \
      --argjson t "$EMP_ID" --argjson s "$subject" --argjson c "$CLASSROOM_ID" --arg y "$YEAR_NAME" \
      '{teacherId:$t,subjectId:$s,classroomId:$c,academicYear:$y}')")
    expect "$code" "POST /teacher-assignments" 200 201
    ASSIGNMENT_ID=$(jq -r '.id' "$body")
  fi
}
ensure_assignment "$MATH_ID";    MATH_ASSIGNMENT_ID=$ASSIGNMENT_ID
ensure_assignment "$SCIENCE_ID"; SCIENCE_ASSIGNMENT_ID=$ASSIGNMENT_ID

# GET /timetables/classroom/{id} answers 404 when the class has no timetable yet. A timetable
# that already has attendance cannot be replaced (the attendance rows reference its slots),
# so an existing one is read and left alone.
code=$(call GET "/timetables/classroom/$CLASSROOM_ID" "$ADMIN_TOKEN")
if [[ "$code" == "200" ]]; then
  cp "$body" "$timetable_json"
  say "Timetable exists; leaving its slots untouched."
elif [[ "$code" == "404" ]]; then
  say "Creating the Monday–Friday timetable …"
  slots=$(jq -nc --argjson math "$MATH_ASSIGNMENT_ID" --argjson sci "$SCIENCE_ASSIGNMENT_ID" '
    [ "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY" ]
    | map(. as $day | [
        {dayOfWeek:$day, period:1, assignmentId:$math},
        {dayOfWeek:$day, period:2, assignmentId:$sci}
      ]) | add')
  code=$(call POST /timetables "$ADMIN_TOKEN" "$(jq -nc \
    --argjson c "$CLASSROOM_ID" --arg y "$YEAR_NAME" --argjson s "$slots" \
    '{classroomId:$c,academicYear:$y,slots:$s}')")
  expect "$code" "POST /timetables"
  cp "$body" "$timetable_json"
else
  die "GET /timetables/classroom/$CLASSROOM_ID answered $code"
fi

slot_id() { # slot_id <DAY> <PERIOD> -> id or empty
  jq -r --arg d "$1" --argjson p "$2" \
    '.slots[] | select(.dayOfWeek == $d and .period == $p) | .id' "$timetable_json" | head -1
}

# ── 6. Fees, student fees, one part payment ───────────────────────────────────
ensure_fee() { # ensure_fee <feeType> <amount> <description> -> $FEE_ID
  local type=$1 amount=$2 description=$3 due
  due=$(shift_date 30)
  code=$(call GET /fees "$ADMIN_TOKEN"); expect "$code" "GET /fees"
  FEE_ID=$(jq -r --arg t "$type" --arg y "$YEAR_NAME" \
    '.[] | select(.feeType == $t and .academicYear == $y) | .id' "$body" | head -1)
  if [[ -z "$FEE_ID" || "$FEE_ID" == "null" ]]; then
    code=$(call POST /fees "$ADMIN_TOKEN" "$(jq -nc \
      --arg t "$type" --argjson a "$amount" --arg d "$description" --arg due "$due" \
      --arg y "$YEAR_NAME" --arg g "$CLASSROOM_GRADE" \
      '{feeType:$t,description:$d,amount:$a,dueDate:$due,academicYear:$y,
        classGrade:$g,studentCategory:"ALL",isMandatory:true}')")
    expect "$code" "POST /fees ($type)"
    FEE_ID=$(jq -r '.id' "$body")
  fi
}
ensure_fee "Tuition Fee" 2500 "Term 1 tuition";       TUITION_FEE_ID=$FEE_ID
ensure_fee "Transport Fee" 1200 "Term 1 school transport"; TRANSPORT_FEE_ID=$FEE_ID

ensure_student_fee() { # ensure_student_fee <studentId> <feeId> -> $STUDENT_FEE_ID
  local student=$1 fee=$2
  code=$(call GET /student-fees "$ADMIN_TOKEN"); expect "$code" "GET /student-fees"
  STUDENT_FEE_ID=$(jq -r --argjson s "$student" --argjson f "$fee" \
    '.[] | select(.studentId == $s and .feeId == $f) | .id' "$body" | head -1)
  if [[ -z "$STUDENT_FEE_ID" || "$STUDENT_FEE_ID" == "null" ]]; then
    code=$(call POST /student-fees "$ADMIN_TOKEN" "$(jq -nc \
      --argjson s "$student" --argjson f "$fee" '{studentId:$s,feeId:$f,status:"PENDING",amountPaid:0}')")
    expect "$code" "POST /student-fees"
    STUDENT_FEE_ID=$(jq -r '.id' "$body")
  fi
}
ensure_student_fee "$LEILA_ID" "$TUITION_FEE_ID"; LEILA_TUITION_FEE=$STUDENT_FEE_ID
ensure_student_fee "$LEILA_ID" "$TRANSPORT_FEE_ID"
ensure_student_fee "$OMAR_ID" "$TUITION_FEE_ID"
ensure_student_fee "$SARA_ID" "$TUITION_FEE_ID"
ensure_student_fee "$ZAYN_ID" "$TUITION_FEE_ID"

code=$(call GET /fee-payments "$ADMIN_TOKEN"); expect "$code" "GET /fee-payments"
if [[ -z "$(jq -r --arg r "$PAYMENT_REFERENCE" '.[] | select(.paymentReference == $r) | .id' "$body" | head -1)" ]]; then
  say "Recording a part payment as the accountant …"
  code=$(call POST /fee-payments "$ACCOUNTANT_TOKEN" "$(jq -nc \
    --argjson sf "$LEILA_TUITION_FEE" --arg d "$(shift_date 0)" --arg r "$PAYMENT_REFERENCE" \
    '{studentFeeId:$sf,amount:1000,paymentDate:$d,paymentMethod:"CASH",
      paymentReference:$r,remarks:"Part payment received at the school office"}')")
  expect "$code" "POST /fee-payments (as ACCOUNTANT)"
fi

# ── 7. Exam, registration, published result ───────────────────────────────────
SEEDED_RESULT=$(call GET "/results/student/$LEILA_ID" "$ADMIN_TOKEN")
expect "$SEEDED_RESULT" "GET /results/student/$LEILA_ID"
RESULT_COUNT=$(jq 'length' "$body")

if (( RESULT_COUNT > 0 )); then
  say "Results already exist for $LEILA_USER; leaving them unchanged."
else
  # The subject ids are only ever returned by the creation response; reuse the state file
  # when this tenant was seeded before.
  if [[ -n "${DEMO_EXAM_SUBJECT_MATH_ID:-}" ]]; then
    MATH_SUBJECT_EXAM_ID=$DEMO_EXAM_SUBJECT_MATH_ID
  else
    code=$(call GET /exams "$ADMIN_TOKEN"); expect "$code" "GET /exams"
    EXAM_ID=$(jq -r --arg n "$EXAM_NAME" --arg y "$YEAR_NAME" \
      '.[] | select(.name == $n and .academicYear == $y) | .id' "$body" | head -1)
    exam_payload=$(jq -nc \
      --arg n "$EXAM_NAME" --arg y "$YEAR_NAME" --arg d "$(shift_date 14)" \
      --argjson m "$MATH_ID" --argjson s "$SCIENCE_ID" \
      '{name:$n,description:"Term 1 assessment",examType:"MID_TERM",academicYear:$y,
        examDate:$d,maxMarks:100,passingMarks:35,durationMinutes:120,
        examSubjects:[
          {subjectId:$m,maxMarks:100,passingMarks:35,examDate:$d,startTime:"09:00",endTime:"11:00"},
          {subjectId:$s,maxMarks:100,passingMarks:35,examDate:$d,startTime:"11:30",endTime:"13:30"}
        ]}')
    if [[ -n "$EXAM_ID" && "$EXAM_ID" != "null" ]]; then
      die "exam $EXAM_NAME exists but its subject ids are not in $OUT. Remove that exam through the API, or delete $OUT and use a fresh database — a second exam would be created otherwise."
    fi
    say "Creating exam $EXAM_NAME …"
    code=$(call POST /exams "$ADMIN_TOKEN" "$exam_payload")
    expect "$code" "POST /exams"
    EXAM_ID=$(jq -r '.id' "$body")
    MATH_SUBJECT_EXAM_ID=$(jq -r --argjson m "$MATH_ID" \
      '.examSubjects[] | select(.subjectId == $m) | .id' "$body" | head -1)
    [[ -n "$MATH_SUBJECT_EXAM_ID" && "$MATH_SUBJECT_EXAM_ID" != "null" ]] || die \
      "the exam was created but its Mathematics subject id was not returned"
  fi

  say "Registering $LEILA_USER for the Mathematics paper …"
  code=$(call POST /student-exams "$ADMIN_TOKEN" "$(jq -nc \
    --argjson s "$LEILA_ID" --argjson e "$MATH_SUBJECT_EXAM_ID" --arg d "$(shift_date 0)" \
    '{studentId:$s,examSubjectId:$e,registrationDate:$d}')")
  expect "$code" "POST /student-exams"
  STUDENT_EXAM_ID=$(jq -r '.id' "$body")

  code=$(call POST /results "$ADMIN_TOKEN" "$(jq -nc \
    --argjson se "$STUDENT_EXAM_ID" \
    '{studentExamId:$se,obtainedMarks:78,grade:"B+",remarks:"Consistent performance across term 1"}')")
  expect "$code" "POST /results"
fi

# ── 8. Five school days of attendance (idempotent upsert) ─────────────────────
statuses=("PRESENT PRESENT PRESENT PRESENT" "PRESENT PRESENT PRESENT ABSENT" \
          "PRESENT ABSENT PRESENT PRESENT" "PRESENT PRESENT LATE PRESENT" \
          "PRESENT PRESENT PRESENT ABSENT")
roster="[{\"id\":$LEILA_ID},{\"id\":$OMAR_ID},{\"id\":$SARA_ID},{\"id\":$ZAYN_ID}]"
seeded_days=0

for back in 0 1 2 3 4 5 6 7 8 9 10 11 12 13; do
  (( seeded_days >= 5 )) && break
  day=$(shift_date "-$back")
  name=$(weekday_of "$day")
  sid=$(slot_id "$name" 1)
  [[ -z "$sid" || "$sid" == "null" ]] && continue
  status_line=${statuses[$seeded_days]}
  marks=$(jq -nc --argjson roster "$roster" --arg s "$status_line" '
    ($s | split(" ")) as $states
    | [ $roster | to_entries[] | {studentId:.value.id, status:$states[.key]} ]')
  code=$(call POST /attendance/bulk "$TEACHER_TOKEN" "$(jq -nc \
    --argjson slot "$sid" --arg d "$day" --argjson a "$marks" \
    '{timetableSlotId:$slot,date:$d,attendance:$a}')")
  expect "$code" "POST /attendance/bulk ($day)"
  seeded_days=$((seeded_days + 1))
done
(( seeded_days > 0 )) || say "No weekday slot in the timetable yet; attendance skipped."

# ── 9. Announcement + notification ───────────────────────────────────────────
# A publication dated in the future is invisible to the student and parent feeds, which
# filter on publishDate <= now, so the demo copy is dated yesterday.
announcement_payload=$(jq -nc \
  --arg d "$(shift_date -1)T09:00:00" \
  '{title:"Parent-Teacher Meeting",content:"The term 1 parent-teacher meeting is on Saturday at 10:00 in the main hall. Please confirm your slot with the class teacher.",
    announcementType:"GENERAL",targetAudience:"ALL",publishDate:$d,
    isPublished:true,priority:"HIGH",category:"EVENT",isPinned:true}')

code=$(call GET /announcements "$ADMIN_TOKEN"); expect "$code" "GET /announcements"
ANNOUNCEMENT_ID=$(jq -r --arg t "Parent-Teacher Meeting" '.[] | select(.title == $t) | .id' "$body" | head -1)
if [[ -z "$ANNOUNCEMENT_ID" || "$ANNOUNCEMENT_ID" == "null" ]]; then
  say "Publishing the demo announcement …"
  code=$(call POST /announcements "$ADMIN_TOKEN" "$announcement_payload")
  expect "$code" "POST /announcements"
  ANNOUNCEMENT_ID=$(jq -r '.id' "$body")
else
  existing_date=$(jq -r --argjson id "$ANNOUNCEMENT_ID" \
    '.[] | select(.id == $id) | .publishDate' "$body" | head -1)
  if [[ "$existing_date" > "$(date '+%Y-%m-%dT%H:%M:%S')" ]]; then
    say "Redating the demo announcement from the future to yesterday …"
    code=$(call PUT "/announcements/$ANNOUNCEMENT_ID" "$ADMIN_TOKEN" "$announcement_payload")
    expect "$code" "PUT /announcements/$ANNOUNCEMENT_ID"
  fi
fi
# Creation does not publish; the publish route is what flips the flag the student and
# parent feeds filter on. Publishing an already-published announcement is still a 200.
code=$(call POST "/announcements/$ANNOUNCEMENT_ID/publish" "$ADMIN_TOKEN")
expect "$code" "POST /announcements/$ANNOUNCEMENT_ID/publish"

code=$(call GET /notifications "$ADMIN_TOKEN"); expect "$code" "GET /notifications"
if [[ -z "$(jq -r '.[] | select(.title == "Fee reminder") | .id' "$body" | head -1)" ]]; then
  code=$(call POST /notifications "$ADMIN_TOKEN" "$(jq -nc \
    '{title:"Fee reminder",message:"The term 1 tuition fee is due in 30 days. Contact the accounts office for a payment plan.",
      notificationType:"REMINDER",channel:"IN_APP",priority:"HIGH",
      recipientType:"ALL",isImportant:true}')")
  expect "$code" "POST /notifications"
fi

# ── 10. State file + access sheet ────────────────────────────────────────────
umask 077
{
  printf '%s=%q\n' \
    DEMO_SCHOOL_CODE "$SCHOOL_CODE" \
    DEMO_SCHOOL_ID "$SCHOOL_ID" \
    DEMO_ACADEMIC_YEAR "$YEAR_NAME" \
    DEMO_CLASSROOM_ID "$CLASSROOM_ID" \
    DEMO_STUDENT_ID "$LEILA_ID" \
    DEMO_ADMIN_USERNAME "$ADMIN_USERNAME" \
    DEMO_TEACHER_USERNAME "$TEACHER_USERNAME" \
    DEMO_ACCOUNTANT_USERNAME "$ACCOUNTANT_USERNAME" \
    DEMO_STUDENT_USERNAME "$LEILA_USER" \
    DEMO_PARENT_USERNAME "$PARENT_USERNAME" \
    DEMO_PASSWORD "$DEMO_PASSWORD" \
    DEMO_EXAM_ID "${EXAM_ID:-${DEMO_EXAM_ID:-}}" \
    DEMO_EXAM_SUBJECT_MATH_ID "${MATH_SUBJECT_EXAM_ID:-${DEMO_EXAM_SUBJECT_MATH_ID:-}}" \
    DEMO_STUDENT_EXAM_ID "${STUDENT_EXAM_ID:-${DEMO_STUDENT_EXAM_ID:-}}"
} > "$OUT"
chmod 600 "$OUT"

react_url=${DEMO_REACT_URL:-http://localhost:5173}
flutter_url=${DEMO_FLUTTER_URL:-http://localhost:3000}

cat <<EOF

Demo tenant is ready. State was written to $OUT (mode 600).

  School          $SCHOOL_NAME ($SCHOOL_CODE, id $SCHOOL_ID)
  Academic year   $YEAR_NAME
  Classroom       $CLASSROOM_NAME (id $CLASSROOM_ID)

  Role          Username              Password
  ------------  --------------------  ------------------------
  SUPERADMIN    $SUPERUSER  (configured on the backend; not printed)
  ADMIN         $ADMIN_USERNAME
  TEACHER       $TEACHER_USERNAME
  ACCOUNTANT    $ACCOUNTANT_USERNAME
  STUDENT       $LEILA_USER
  PARENT        $PARENT_USERNAME

  All five demo logins use: $DEMO_PASSWORD

  React SPA     $react_url
  Flutter web   $flutter_url
  API           $BASE/api/v1

EOF

if (( GENERATED_PASSWORD == 1 )); then
  say "DEMO_PASSWORD was generated for this run; it is in $OUT if you need it again."
fi
