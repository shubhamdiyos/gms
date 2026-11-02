#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Live MVP end-to-end verification: the eleven mandatory MVP features walked as
# ONE journey, against a running backend and a real PostgreSQL.
#
#   ./scripts/verify-mvp-e2e.sh
#
# One section per feature, and the register at the end says of each of the eleven
# whether this run PROVED it, FAILED it, or could not prove it (a feature whose
# assertions all reported SKIP is UNPROVEN, not a pass).
#
#    1  superadmin authentication                      AuthController:71
#    2  create / view / update / manage schools        SchoolController:39,61,84,50,103
#    3  a school admin provisioned by school creation   SchoolServiceImpl:84-118
#    4  that admin's token carries its own tenant       JwtTokenProvider:121
#    5  RBAC: a role that may not act is 403, not 500   GlobalExceptionHandler:106
#    6  student registration AND admission             StudentController:43,
#                                                      StudentAdmissionController:19-35
#    7  student CRUD and the archive toggle; no delete  StudentController:119,54,145
#    8  student search, filtering, server-side paging   StudentController:97
#    9  dashboards computed from real database rows     DashboardController:53,68
#   10  cross-tenant isolation, and the platform's reach TenantScopedRepository:57
#   11  audit logging: the write records WHO wrote it   BaseAuditEntity:27,33 + V1 DDL
#
# Prerequisites
#   * a backend on GMS_BASE_URL (default http://localhost:8080) started against a
#     DISPOSABLE database at V6 with the platform account bootstrapped, exactly as
#     in verify-c1-tenancy.sh;
#   * curl and jq;
#   * GMS_VERIFY_DB plus a PostgreSQL container reachable through docker for the
#     SQL half of features 3, 6, 9 and 11 — those checks report SKIP, never PASS,
#     without it. Set GMS_REQUIRE_DB=1 to make a missing database handle fatal
#     instead of skipped, which is what a Stage 4 sign-off run should do.
#
# Environment — everything here comes from the environment; nothing is hardcoded
#   GMS_SUPERADMIN_USERNAME / GMS_SUPERADMIN_PASSWORD   required: the bootstrapped
#       platform account.
#   GMS_SCHOOL_A_ADMIN_NAME / GMS_SCHOOL_B_ADMIN_NAME   the two administrators'
#       full names, TWO space-separated words each: the server builds the username
#       from the first initial plus the surname (SchoolServiceImpl:85), and that is
#       the rule `admin_username` below re-implements rather than a new one.
#   GMS_TEACHER_PASSWORD   optional: a policy-valid password for the throwaway
#       TEACHER account feature 5 needs (min 8, one upper, one lower, one digit, one
#       of @$!%*?&.#_ — util/PasswordValidator; a hyphen fails it, which is how the
#       first run of verify-c1 got it wrong). Unset → the least-privilege refusals
#       SKIP; the ADMIN and SUPERADMIN refusals are still asserted.
#   GMS_BASE_URL GMS_VERIFY_DB GMS_PG_CONTAINER GMS_PG_USER GMS_PG_PASSWORD_ENV
#   GMS_SCHOOL_A_CODE GMS_SCHOOL_B_CODE GMS_SEARCH_SURNAME GMS_REQUIRE_DB
#
# The administrators' initial password is not a secret this script invented. It is
# the rule the server itself applies — SchoolServiceImpl:87 writes `<base>@123` —
# and the base comes from the name the environment supplies. verify-c1 through
# verify-c7 log in with that same rule; this reuses it and adds nothing.
#
# What the run writes: two schools, two administrators and their employee rows, one
# teacher account, four classrooms (two in A, two in B), one subject, one section,
# three employees, eight pupils in school A, two in school B, and three admissions.
# It physically deletes nothing, and every toggle it exercises is restored before
# its section ends, so the dashboard counts in feature 9 stay reachable.
#
# The two school codes are new to this repository's suite (MVP801/MVP802). If either
# is already taken the run stops at feature 2 with a clear message rather than
# reporting a cascade of failures afterwards — that check is what makes the script
# re-runnable against a fresh disposable database instead of merely idempotent on
# one.
#
# One deliberate wrong-password attempt is made, against the platform account, for
# feature 1. It spends one of that account's
# SECURITY_LOGIN_MAX_FAILED_ATTEMPTS credits; it happens once and nowhere else for
# that reason.
#
# Credentials come from the environment; nothing here hardcodes one.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

BASE=${GMS_BASE_URL:-http://localhost:8080}
API="$BASE/api/v1"
SUPERUSER=${GMS_SUPERADMIN_USERNAME:?set GMS_SUPERADMIN_USERNAME}
SUPERPASSWORD=${GMS_SUPERADMIN_PASSWORD:?set GMS_SUPERADMIN_PASSWORD}
ADMIN_A_NAME=${GMS_SCHOOL_A_ADMIN_NAME:-Aarav Menon}
ADMIN_B_NAME=${GMS_SCHOOL_B_ADMIN_NAME:-Beatula Fernandes}
TEACHER_PASSWORD=${GMS_TEACHER_PASSWORD:-}
SCHOOL_A_CODE=${GMS_SCHOOL_A_CODE:-MVP801}
SCHOOL_B_CODE=${GMS_SCHOOL_B_CODE:-MVP802}
SURNAME=${GMS_SEARCH_SURNAME:-Wickrama}

# The disposable database and the container that holds it. The SQL half is skipped,
# loudly, when this is not set.
VERIFY_DB=${GMS_VERIFY_DB:-}
PG_CONTAINER=${GMS_PG_CONTAINER:-gms-postgres}
PG_USER=${GMS_PG_USER:-gms_user}
PG_PASSWORD_ENV=${GMS_PG_PASSWORD_ENV:-DB_PASSWORD}
REQUIRE_DB=${GMS_REQUIRE_DB:-}

failures=0
checks=0
skipped=0
body=$(mktemp)
ref=$(mktemp)
trap 'rm -f "$body" "$ref"' EXIT

# Per-feature tallies, so the register can say which of the eleven the run proved.
# Index 0 is the preflight, which belongs to no feature. `check` and `refused` count
# into F_CHECK; `skip` counts into F_SKIP and never into F_CHECK, which is what lets
# the register tell "assertion ran and passed" from "question was never asked".
CUR=0
TOTAL_FEATURES=11
F_CHECK=(0 0 0 0 0 0 0 0 0 0 0 0 0)
F_FAIL=(0 0 0 0 0 0 0 0 0 0 0 0 0)
F_SKIP=(0 0 0 0 0 0 0 0 0 0 0 0 0)
F_TITLE=(
  "preflight"
  "superadmin authentication"
  "create / view / update / manage schools"
  "a school admin provisioned with the school"
  "the admin's token carries its own tenant"
  "RBAC refuses with 403, not 500"
  "student registration and admission"
  "student CRUD, archive toggle, no delete"
  "student search, filtering, server-side pagination"
  "dashboards computed from real database rows"
  "cross-tenant isolation, and the platform's reach"
  "audit logging records who wrote the row"
)

check() { # check <name> <expected> <actual>
  checks=$((checks + 1))
  F_CHECK[$CUR]=$(( F_CHECK[$CUR] + 1 ))
  if [[ "$2" == "$3" ]]; then
    printf 'PASS  %s (expected %s, got %s)\n' "$1" "$2" "$3"
  else
    printf 'FAIL  %s (expected %s, got %s)\n' "$1" "$2" "$3"
    failures=$((failures + 1))
    F_FAIL[$CUR]=$(( F_FAIL[$CUR] + 1 ))
  fi
}

refused() { # refused <name> <code>
  # The property is "the caller does not get or write the row", not the status code.
  # C-4 moved ownership into the load (TenantScopedRepository:57), so another
  # school's id resolves to nothing and the honest answer is 404; 403 is what a role
  # refusal and a post-load comparison answer. A 200 carrying somebody else's row and
  # a 500 are both failures — this accepts neither, and the paired "disclosed
  # nothing" / "wrote nothing" checks say so independently of the number.
  checks=$((checks + 1))
  F_CHECK[$CUR]=$(( F_CHECK[$CUR] + 1 ))
  if [[ "$2" == "403" || "$2" == "404" ]]; then
    printf 'PASS  %s (refused with %s)\n' "$1" "$2"
  else
    printf 'FAIL  %s (expected a refusal -- 403 or 404 -- got %s)\n' "$1" "$2"
    failures=$((failures + 1))
    F_FAIL[$CUR]=$(( F_FAIL[$CUR] + 1 ))
  fi
}

skip() { # skip <name> <reason>
  skipped=$((skipped + 1))
  F_SKIP[$CUR]=$(( F_SKIP[$CUR] + 1 ))
  printf 'SKIP  %s (%s)\n' "$1" "$2"
}

feature() { # feature <1..11>
  CUR=$1
  echo
  echo "=== F$1. ${F_TITLE[$1]} ==="
}

fnote() { printf '      %s\n' "$1"; }

call() { # call <method> <path> [token] [json]
  local method=$1 route=$2 token=${3:-} payload=${4:-}
  local args=(-s -o "$body" -w '%{http_code}' -X "$method" "$API$route")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$payload" ]] && args+=(-H 'Content-Type: application/json' -d "$payload")
  curl "${args[@]}"
}

field() { jq -r "$1 // empty" "$body"; }
# `// empty` cannot report a JSON `false`, because false is an alternative-triggering value in jq — a
# boolean that is really false reads as absent. This distinguishes "the field is false" from "the field
# is not there", which is the difference between a refusal that worked and a response that never came.
flag() { jq -r "if has(\"$1\") then .[\"$1\"] else empty end" "$body"; }
token_of() { field '.accessToken'; }
save_ref() { cp "$body" "$ref"; }   # keep a response for a later comparison
list_len() { jq 'length' "$body"; } # the list the body holds, as a number

require_value() { # require_value <what> <value>
  checks=$((checks + 1))
  F_CHECK[$CUR]=$(( F_CHECK[$CUR] + 1 ))
  if [[ -n "$2" && "$2" != "null" ]]; then
    printf 'PASS  %s returned a value\n' "$1"
  else
    printf 'FAIL  %s returned no value\n' "$1"
    failures=$((failures + 1))
    F_FAIL[$CUR]=$(( F_FAIL[$CUR] + 1 ))
  fi
}

# The administrator username the school-creation flow generates: first initial plus
# surname, lowercased (SchoolServiceImpl:85). The server also strips anything that is
# not a-z0-9; the names this script is given are letters only, so the two agree.
admin_username() { # admin_username <full name>
  local first last
  first=$(printf '%s' "$1" | awk '{print $1}')
  last=$(printf '%s' "$1" | awk '{print $2}')
  printf '%s%s' "${first:0:1}" "$last" | tr 'A-Z' 'a-z'
}

lc() { printf '%s' "$1" | tr 'A-Z' 'a-z'; }

# The JWT payload, read without verifying the signature. This is not a second
# authority: the server decides access from the signature it does verify, and the
# behavioural checks in feature 4 prove the claim is honoured. The decode is here so
# the claim itself is visible in the register.
jwt_payload() { # jwt_payload <token>
  local seg
  seg=$(printf '%s' "$1" | cut -d. -f2 | tr '_-' '/+')
  case $(( ${#seg} % 4 )) in
    2) seg="${seg}==" ;;
    3) seg="${seg}=" ;;
  esac
  printf '%s' "$seg" | base64 -d 2>/dev/null
}

claim() { # claim <token> <jq filter over the payload>
  jwt_payload "$1" | jq -r "$2 // empty" 2>/dev/null
}

sql() { # sql <statement> — SELECT only
  docker exec -e PGPASSWORD="${!PG_PASSWORD_ENV}" "$PG_CONTAINER" psql -U "$PG_USER" -d "$VERIFY_DB" \
    -v ON_ERROR_STOP=1 -tAc "$1" 2>&1
}

sqlv() { # sqlv <statement> — one scalar, whitespace removed
  sql "$1" | tr -d '[:space:]'
}

have_db() { [[ -n "$VERIFY_DB" && -n "${!PG_PASSWORD_ENV:-}" ]]; }

# Search expectations computed from the school's own unpaginated list, mirroring
# StudentRepository.findStudents (lines 45-70): a row matches when ANY of first name,
# last name, student id or email contains the lower-cased query; a classroom filter
# narrows; archived rows (status '0') are excluded from both sides. Nothing below is
# a hardcoded count, so a search that quietly widened or narrowed cannot agree with
# its own expectation by accident.
expected_matches() { # expected_matches <lowercase query>
  local q=$1
  jq -r --arg q "$q" '
    [.[] | select(
        ((.firstName  // "") | ascii_downcase | contains($q))
     or ((.lastName   // "") | ascii_downcase | contains($q))
     or ((.studentId  // "") | ascii_downcase | contains($q))
     or ((.email      // "") | ascii_downcase | contains($q)) )
     | select(.status != "0")] | length' "$ref"
}
expected_in_class() { # expected_in_class <classId>
  jq -r --argjson c "$1" '[.[] | select(.class_id == $c) | select(.status != "0")] | length' "$ref"
}
expected_all() { jq 'length' "$ref"; }

echo "=== 0. Preflight: the backend is live, and the tools are here ==="
command -v curl >/dev/null 2>&1 || { echo "FATAL: curl is not on PATH"; exit 1; }
command -v jq   >/dev/null 2>&1 || { echo "FATAL: jq is not on PATH"; exit 1; }
code=$(curl -s -o "$body" -w '%{http_code}' "$API/ping")
check "a live backend answers GET /api/v1/ping" "200" "$code" || true
[[ "$code" == "000" || "$code" == "404" || -z "$code" ]] && {
  echo "FATAL: no backend on $API (HealthController:15 maps GET /api/v1/ping)."
  echo "       Nothing below can be verified without one."
  exit 1
}
if [[ -n "$REQUIRE_DB" && "$REQUIRE_DB" != "0" ]] && ! have_db; then
  echo "FATAL: GMS_REQUIRE_DB is set but the database half is unavailable."
  echo "       Set GMS_VERIFY_DB (and the password named by GMS_PG_PASSWORD_ENV,"
  echo "       default DB_PASSWORD) to a container-readable disposable database,"
  echo "       or unset GMS_REQUIRE_DB to accept SKIP on features 3, 6, 9 and 11."
  exit 1
fi
have_db && fnote "the SQL half will run against $VERIFY_DB in $PG_CONTAINER" \
         || fnote "no GMS_VERIFY_DB: the SQL half of features 3, 6, 9 and 11 will SKIP"

# ─────────────────────────────────────────────────────────────────────────────
feature 1
# POST /api/v1/auth/login (AuthController:71), permitAll at SecurityConfig:122.
# 401 for a wrong password is AuthController:107; for an unknown username, :76. Both
# answer an empty body, so the endpoint cannot be used to enumerate accounts.
code=$(call POST /auth/login "" "{\"username\":\"$SUPERUSER\",\"password\":\"$SUPERPASSWORD\"}")
check "F1 superadmin login is accepted" "200" "$code"
PLATFORM_TOKEN=$(token_of)
require_value "F1 the login response carries an access token" "$PLATFORM_TOKEN"
[[ -z "$PLATFORM_TOKEN" ]] && { echo "FATAL: no platform token; nothing below can run"; exit 1; }
check "F1 the token is a bearer JWT" "Bearer" "$(field '.tokenType')"
require_value "F1 and the response states how long it is valid" "$(field '.expiresIn')"

code=$(call POST /auth/login "" "{\"username\":\"$SUPERUSER\",\"password\":\"$SUPERPASSWORD-nope\"}")
check "F1 a wrong password is refused with 401" "401" "$code"
check "F1 and the refusal hands back no token" "" "$(field '.accessToken')"

code=$(call POST /auth/login "" '{"username":"no.such.account","password":"Whatever123!"}')
check "F1 an unknown username is refused with 401" "401" "$code"
check "F1 with the same empty answer, so accounts cannot be enumerated from here" "" "$(field '.error')"

code=$(call GET "/students" "")
check "F1 a guarded route with no token is refused with 401" "401" "$code"
code=$(call GET "/students" "${PLATFORM_TOKEN}tampered")
check "F1 a token that does not verify is refused with 401" "401" "$code"

PLAT_CLAIMS=$(claim "$PLATFORM_TOKEN" '.')
if [[ -n "$PLAT_CLAIMS" ]]; then
  check "F1 the platform token carries the SUPERADMIN role" "yes" \
    "$(printf '%s' "$PLAT_CLAIMS" | jq -r 'if (.roles | index("SUPERADMIN")) then "yes" else "no" end')"
  PLATFORM_SCHOOL=$(printf '%s' "$PLAT_CLAIMS" | jq -r '.schoolId // empty')
  require_value "F1 and the platform account sits in a school of its own (the system school)" "$PLATFORM_SCHOOL"
else
  skip "F1 the token's role claim" "the JWT payload could not be decoded here (needs base64 and jq)"
  PLATFORM_SCHOOL=""
fi

# ─────────────────────────────────────────────────────────────────────────────
feature 2
# POST /schools/create (SchoolController:39, SUPERADMIN-only), GET /schools (:61),
# GET /schools/{id} (:84), PUT /schools/update (:50), PATCH /schools/toggle (:103).
# The retire verb IS the toggle: SchoolController:24-25 documents it, ADR-023 removed
# the inherited delete, and SchoolServiceImpl:248-249 writes status/enabled rather
# than dropping the row. DELETE /schools/{id} is not declared anywhere in the file,
# so it answers 405 from GlobalExceptionHandler:156.
code=$(call POST /schools/create "$PLATFORM_TOKEN" \
  "{\"schoolName\":\"Ashford Vale Secondary\",\"schoolCode\":\"$SCHOOL_A_CODE\",\"email\":\"office@ashfordvale.test\",\"adminFullName\":\"$ADMIN_A_NAME\",\"adminEmail\":\"admin.a@ashfordvale.test\",\"address\":\"14 Ashford Row\",\"phone\":\"9800000001\",\"principalName\":\"Head of Ashford\",\"establishedYear\":1998,\"boardAffiliation\":\"CBSE\"}")
if [[ "$code" != "200" ]]; then
  echo "FATAL: school A was not created (answered $code)."
  echo "       '$SCHOOL_A_CODE' is almost certainly already on this database, which means"
  echo "       it is not the fresh disposable one this script requires. Either recreate the"
  echo "       database or set GMS_SCHOOL_A_CODE / GMS_SCHOOL_B_CODE to unused codes."
  echo "       Body: $(jq -c . "$body" 2>/dev/null)"
  exit 1
fi
SCHOOL_A=$(field '.id')
require_value "F2 school A was created and answers with its tenant id" "$SCHOOL_A"

code=$(call POST /schools/create "$PLATFORM_TOKEN" \
  "{\"schoolName\":\"Brackmoor High\",\"schoolCode\":\"$SCHOOL_B_CODE\",\"email\":\"office@brackmoor.test\",\"adminFullName\":\"$ADMIN_B_NAME\",\"adminEmail\":\"admin.b@brackmoor.test\",\"address\":\"9 Brackmoor Lane\",\"phone\":\"9800000002\",\"principalName\":\"Head of Brackmoor\",\"establishedYear\":2001,\"boardAffiliation\":\"ICSE\"}")
check "F2 school B was created" "200" "$code"
SCHOOL_B=$(field '.id')
require_value "F2 school B was created and answers with its tenant id" "$SCHOOL_B"
[[ "$SCHOOL_A" == "$SCHOOL_B" ]] && { echo "FATAL: the two schools resolved to the same id"; exit 1; }

code=$(call GET "/schools" "$PLATFORM_TOKEN")
check "F2 the platform owner lists schools" "200" "$code"
SCHOOLS_BEFORE=$(list_len)
check "F2 and the list holds both schools this run created" "2" \
  "$(jq --arg a "$SCHOOL_A_CODE" --arg b "$SCHOOL_B_CODE" \
        '[.[] | select(.schoolCode == $a or .schoolCode == $b)] | length' "$body")"

code=$(call GET "/schools/$SCHOOL_A" "$PLATFORM_TOKEN")
check "F2 the platform owner views one school by id" "200" "$code"
check "F2 and the row it views is school A" "$SCHOOL_A_CODE" "$(field '.schoolCode')"

code=$(call PUT /schools/update "$PLATFORM_TOKEN" \
  "{\"id\":$SCHOOL_A,\"schoolName\":\"Ashford Vale Secondary College\",\"schoolCode\":\"$SCHOOL_A_CODE\",\"email\":\"office@ashfordvale.test\",\"address\":\"14 Ashford Row, Rewi Street\",\"phone\":\"9800000001\",\"principalName\":\"Head of Ashford\",\"establishedYear\":1998,\"boardAffiliation\":\"CBSE\"}")
check "F2 the platform owner updates school A" "200" "$code"
check "F2 and the update carries the new name back" "Ashford Vale Secondary College" "$(field '.schoolName')"
code=$(call GET "/schools/$SCHOOL_A" "$PLATFORM_TOKEN")
check "F2 the update is readable afterwards, not merely echoed" "Ashford Vale Secondary College" "$(field '.schoolName')"
check "F2 and so is the address" "14 Ashford Row, Rewi Street" "$(field '.address')"

# Manage = retire, and the retire is a toggle the school's own status column records.
# Restored immediately so features 8 and 9 still count a live tenant.
code=$(call PATCH "/schools/toggle?id=$SCHOOL_B&isActive=false" "$PLATFORM_TOKEN")
check "F2 the platform retires school B (PATCH /schools/toggle)" "200" "$code"
check "F2 and the retired school reports status 0" "0" "$(field '.status')"
check "F2 and reports itself disabled" "false" "$(flag enabled)"
code=$(call GET "/schools/$SCHOOL_B" "$PLATFORM_TOKEN")
refused "F2 a retired school is not readable by id" "$code"
check "F2 and the refusal disclosed none of the retired school's fields" "" "$(field '.schoolCode')"
code=$(call PATCH "/schools/toggle?id=$SCHOOL_B&isActive=true" "$PLATFORM_TOKEN")
check "F2 and the same verb brings it back" "200" "$code"
check "F2 with status 1" "1" "$(field '.status')"
code=$(call GET "/schools/$SCHOOL_B" "$PLATFORM_TOKEN")
check "F2 so the sections after this one count a live school" "200" "$code"

code=$(call DELETE "/schools/$SCHOOL_A" "$PLATFORM_TOKEN")
check "F2 there is no school delete: the retire verb is the toggle" "405" "$code"
check "F2 and the 405 is the documented method refusal, not a server fault" "Method Not Allowed" "$(field '.error')"
code=$(call GET "/schools/$SCHOOL_A" "$PLATFORM_TOKEN")
check "F2 and the school the API refused to delete is still there" "$SCHOOL_A" "$(field '.id')"

# ─────────────────────────────────────────────────────────────────────────────
feature 3
# SchoolServiceImpl:84-118 is the provisioning, reached by POST /schools/create alone:
# username = first initial + surname lowercased (:85), password = `<base>@123` (:87),
# role = ADMIN (:98), plus an Employee row saved and linked (:104-117) so
# TenantContext.employeeId() is non-null in the JWT the admin then authenticates with.
ADMIN_A_USER=$(admin_username "$ADMIN_A_NAME")
ADMIN_B_USER=$(admin_username "$ADMIN_B_NAME")

code=$(call POST /auth/login "" "{\"username\":\"$ADMIN_A_USER\",\"password\":\"${ADMIN_A_USER}@123\"}")
check "F3 school A's administrator logged in with the server's own password rule" "200" "$code"
TOKEN_A=$(token_of)
code=$(call POST /auth/login "" "{\"username\":\"$ADMIN_B_USER\",\"password\":\"${ADMIN_B_USER}@123\"}")
check "F3 school B's administrator logged in the same way" "200" "$code"
TOKEN_B=$(token_of)
if [[ -z "$TOKEN_A" || -z "$TOKEN_B" ]]; then
  echo "FATAL: could not obtain both school administrator tokens."
  echo "       Derived usernames were '$ADMIN_A_USER' and '$ADMIN_B_USER' from the names in"
  echo "       GMS_SCHOOL_A_ADMIN_NAME / GMS_SCHOOL_B_ADMIN_NAME; each must be exactly two"
  echo "       space-separated words for the server's rule to produce them."
  exit 1
fi
code=$(call GET /schools "$TOKEN_A")
check "F3 provisioning is part of creation, not a second call: the admin already reaches its school" \
  "200" "$code"
check "F3 and the provisioned account is school A's own administrator" "$SCHOOL_A" "$(jq -r '.[0].id' "$body")"

if have_db; then
  ADMIN_A_UID=$(sqlv "SELECT id FROM users WHERE username='$ADMIN_A_USER'")
  ADMIN_B_UID=$(sqlv "SELECT id FROM users WHERE username='$ADMIN_B_USER'")
  PLATFORM_UID=$(sqlv "SELECT id FROM users WHERE username='$SUPERUSER'")
  require_value "F3 the platform account's id was read from users (feature 11 needs it)" "$PLATFORM_UID"
  check "F3 school A's administrator row was written into school A" "$SCHOOL_A" \
    "$(sqlv "SELECT school_id FROM users WHERE username='$ADMIN_A_USER'")"
  check "F3 school B's administrator row was written into school B" "$SCHOOL_B" \
    "$(sqlv "SELECT school_id FROM users WHERE username='$ADMIN_B_USER'")"
  check "F3 and the provisioned account holds the ADMIN role" "1" \
    "$(sqlv "SELECT count(*) FROM user_roles WHERE user_id=$ADMIN_A_UID AND role='ADMIN'")"
  check "F3 and exactly one role, so provisioning granted nothing extra" "1" \
    "$(sqlv "SELECT count(*) FROM user_roles WHERE user_id=$ADMIN_A_UID")"
  check "F3 the platform owner gained no ADMIN role from this" "0" \
    "$(sqlv "SELECT count(*) FROM user_roles ur JOIN users u ON u.id=ur.user_id WHERE u.username='$SUPERUSER' AND ur.role='ADMIN'")"
  # The employee the same method creates, so the token can carry empId at all.
  check "F3 provisioning also created and linked the administrator's employee row" "1" \
    "$(sqlv "SELECT count(*) FROM employees e JOIN users u ON u.employee_id=e.id WHERE u.username='$ADMIN_A_USER' AND e.designation='SCHOOL_ADMIN' AND e.school_id=$SCHOOL_A")"
  check "F3 and the user row points at that employee row" "1" \
    "$(sqlv "SELECT count(*) FROM users u JOIN employees e ON e.id = u.employee_id WHERE u.username='$ADMIN_A_USER'")"
else
  ADMIN_A_UID=""; ADMIN_B_UID=""; PLATFORM_UID=""
  skip "F3 the provisioned administrator's school and role" "GMS_VERIFY_DB or the container password is not set"
  skip "F3 that no second role was granted" "GMS_VERIFY_DB or the container password is not set"
  skip "F3 that the platform owner was not given an ADMIN role" "GMS_VERIFY_DB or the container password is not set"
  skip "F3 the employee row provisioning created and linked" "GMS_VERIFY_DB or the container password is not set"
fi

# ─────────────────────────────────────────────────────────────────────────────
feature 4
CLAIM_A=$(claim "$TOKEN_A" '.')
if [[ -n "$CLAIM_A" ]]; then
  check "F4 school A's token claims school A" "$SCHOOL_A" "$(printf '%s' "$CLAIM_A" | jq -r '.schoolId')"
  check "F4 and it claims ADMIN, not SUPERADMIN" "yes" \
    "$(printf '%s' "$CLAIM_A" | jq -r 'if (.roles | index("ADMIN")) and (.roles | index("SUPERADMIN") | not) then "yes" else "no" end')"
  check "F4 school B's token claims school B" "$SCHOOL_B" "$(claim "$TOKEN_B" '.schoolId')"
  check "F4 and the two tokens claim different tenants" "different" \
    "$([[ "$(claim "$TOKEN_A" '.schoolId')" != "$(claim "$TOKEN_B" '.schoolId')" ]] && echo different || echo same)"
  require_value "F4 and the claim carries the administrator's employee id" "$(claim "$TOKEN_A" '.empId')"
else
  skip "F4 the JWT tenant claim, read off the token" "the JWT payload could not be decoded here"
  skip "F4 the JWT role claim, read off the token" "the JWT payload could not be decoded here"
  skip "F4 school B's JWT tenant claim" "the JWT payload could not be decoded here"
  skip "F4 the two tokens naming different tenants" "the JWT payload could not be decoded here"
  skip "F4 the JWT employee claim" "the JWT payload could not be decoded here"
fi
# The behavioural half, which needs no decoder: the claim is what the server routes
# on, and SchoolController:71-79 answers a school caller with its own school only.
code=$(call GET "/schools" "$TOKEN_A")
check "F4 school A's list of schools holds exactly one row" "1" "$(list_len)"
check "F4 and that row is school A, chosen from the token and not from the request" "$SCHOOL_A" "$(jq -r '.[0].id' "$body")"
code=$(call GET "/schools" "$TOKEN_B")
check "F4 school B's list holds exactly one row, and it is school B" "$SCHOOL_B" "$(jq -r '.[0].id' "$body")"
code=$(call GET "/schools?schoolId=$SCHOOL_B" "$TOKEN_A")
check "F4 a tenant parameter on the same request does not move the tenant" "1" "$(list_len)"
check "F4 and it is still school A" "$SCHOOL_A" "$(jq -r '.[0].id' "$body")"
code=$(call GET "/schools/$SCHOOL_B" "$TOKEN_A")
refused "F4 the token's claim also bounds the by-id read, not just the list" "$code"

# ─────────────────────────────────────────────────────────────────────────────
feature 5
# Refusals read straight off the guards, each a 403 with the documented body rather
# than a 500. Two producers, both asserted: the URL layer's accessDeniedHandler
# (SecurityConfig:111-112, `{"error":"Forbidden"}`) and the method layer's
# GlobalExceptionHandler:106-115 (`.error == "Forbidden"` too).
#   GET  /dashboard/platform        DashboardController:69  hasAuthority('SUPERADMIN')
#   POST /schools/create            SchoolController:40     hasAuthority('SUPERADMIN')
#   POST /student-admissions/apply  StudentAdmissionController:20 hasAuthority('ADMIN')
#   POST /sections                  SectionController:25    hasAuthority('ADMIN')
#   GET/POST /students/**, DELETE /subjects/{id} — StudentController:66,44 and
#   SubjectController:96, reached only with a real least-privilege TEACHER token.
code=$(call POST /subjects/create "$TOKEN_A" '{"name":"Mathematics","code":"MATH-801"}')
check "F5 setup: school A created a subject to be protected" "200" "$code"
SUBJECT_A=$(field '.id')
require_value "F5 setup: the subject answered with an id" "$SUBJECT_A"

code=$(call GET "/dashboard/platform" "$TOKEN_A")
check "F5 a school ADMIN asking for platform-wide totals is refused with 403, not 500" "403" "$code"
check "F5 the refusal is an authorisation answer with the documented body" "Forbidden" "$(field '.error')"

code=$(call POST /schools/create "$TOKEN_A" \
  "{\"schoolName\":\"Should Not Exist\",\"schoolCode\":\"${SCHOOL_A_CODE}X\",\"email\":\"nope@mvpedge.test\",\"adminFullName\":\"Nobody Here\",\"adminEmail\":\"nope@mvpedge.test\"}")
check "F5 a school ADMIN creating a school is refused with 403 by the SUPERADMIN-only guard" "403" "$code"
check "F5 and it is the same refusal shape" "Forbidden" "$(field '.error')"
code=$(call GET "/schools" "$PLATFORM_TOKEN")
check "F5 the refused school creation wrote no tenant" "$SCHOOLS_BEFORE" "$(list_len)"

code=$(call GET "/students/admissions" "$TOKEN_A")
ADMISSIONS_BEFORE=$(list_len)
code=$(call POST /student-admissions/apply "$PLATFORM_TOKEN" \
  '{"firstName":"No","lastName":"Admission","email":"no.admission@mvpedge.test"}')
check "F5 the platform owner may not apply for an admission (ADMIN-only guard): 403" "403" "$code"
check "F5 and the guard answers the way a method refusal always does" "Forbidden" "$(field '.error')"
code=$(call GET "/students/admissions" "$TOKEN_A")
check "F5 the refused write left no admission behind" "$ADMISSIONS_BEFORE" "$(list_len)"

code=$(call POST /sections "$PLATFORM_TOKEN" '{"name":"Invisible","description":"must not exist","capacity":10}')
check "F5 the platform owner may not create a section (ADMIN-only guard): 403" "403" "$code"

if [[ -n "$TEACHER_PASSWORD" ]]; then
  TEACHER_UID=""
  code=$(call POST /users/create "$TOKEN_A" \
    "{\"username\":\"e2e.mvp.teacher\",\"email\":\"teacher@ashfordvale.test\",\"password\":\"$TEACHER_PASSWORD\",\"fullName\":\"Uma Teacher\",\"roles\":[\"TEACHER\"]}")
  check "F5 setup: school A provisioned a TEACHER account" "200" "$code"
  TEACHER_UID=$(field '.id')
  code=$(call POST /auth/login "" "{\"username\":\"e2e.mvp.teacher\",\"password\":\"$TEACHER_PASSWORD\"}")
  check "F5 setup: the TEACHER logged in" "200" "$code"
  TOKEN_T=$(token_of)
  if [[ -z "$TOKEN_T" ]]; then
    skip "F5 a TEACHER reading the student list is refused" "the TEACHER token could not be obtained, so there is no bounded caller to refuse"
    skip "F5 and the refusal is the documented 403 body" "no TEACHER token"
    skip "F5 a TEACHER creating a pupil is refused" "no TEACHER token"
    skip "F5 the refused pupil was never written" "no TEACHER token"
    skip "F5 a TEACHER deleting a subject is refused" "no TEACHER token"
    skip "F5 the refused subject delete destroyed nothing" "no TEACHER token"
  else
    code=$(call GET "/students" "$TOKEN_T")
    check "F5 a TEACHER reading the student list is refused with 403, not 500" "403" "$code"
    check "F5 and the answer is the documented refusal body, not a stack of internals" "Forbidden" "$(field '.error')"
    check "F5 which is the URL layer (SecurityConfig:136), so no row was ever loaded" "" "$(field '.totalElements')"
    code=$(call GET "/students" "$TOKEN_A")
    STUDENTS_BEFORE_REFUSE=$(list_len)
    CLASS_PLACEHOLDER_SAFE=${CLASS_A1:-0}
    code=$(call POST /students/create "$TOKEN_T" \
      "{\"firstName\":\"Should\",\"lastName\":\"NotExist\",\"email\":\"not.exist@mvpedge.test\",\"gender\":\"M\",\"classId\":$CLASS_PLACEHOLDER_SAFE}")
    check "F5 a TEACHER creating a pupil is refused with 403, not 500" "403" "$code"
    code=$(call GET "/students" "$TOKEN_A")
    check "F5 and the refused pupil was never written" "$STUDENTS_BEFORE_REFUSE" "$(list_len)"
    code=$(call DELETE "/subjects/$SUBJECT_A" "$TOKEN_T")
    check "F5 a TEACHER deleting a subject is refused with 403, not 500" "403" "$code"
    check "F5 the URL layer admits TEACHER to /subjects/** (SecurityConfig:137), so this is the method guard speaking" "Forbidden" "$(field '.error')"
    code=$(call GET "/subjects/$SUBJECT_A" "$TOKEN_A")
    check "F5 and the refused delete destroyed nothing" "$SUBJECT_A" "$(field '.id')"
  fi
else
  skip "F5 a TEACHER reading the student list is refused" "GMS_TEACHER_PASSWORD is not set; there is no least-privilege token to refuse"
  skip "F5 and the refusal is the documented 403 body" "no TEACHER token"
  skip "F5 a TEACHER creating a pupil is refused" "no TEACHER token"
  skip "F5 the refused pupil was never written" "no TEACHER token"
  skip "F5 a TEACHER deleting a subject is refused" "no TEACHER token"
  skip "F5 the refused subject delete destroyed nothing" "no TEACHER token"
fi

# ─────────────────────────────────────────────────────────────────────────────
feature 6
# Registration is POST /students/create (StudentController:43) — the route the other
# verify scripts use. Admission is StudentAdmissionController's apply / approve /
# reject. School A was created by this run, so every pupil below is this run's row and
# the counts in features 8 and 9 are exact rather than merely bounded.
code=$(call POST /classrooms/create "$TOKEN_A" '{"name":"Grade 9","grade":"9","section":"A"}')
check "F6 setup: classroom 9-A exists in school A" "200" "$code"
CLASS_A1=$(field '.id')
code=$(call POST /classrooms/create "$TOKEN_A" '{"name":"Grade 10","grade":"10","section":"A"}')
check "F6 setup: classroom 10-A exists in school A" "200" "$code"
CLASS_A2=$(field '.id')
# A valid classroom id is required even for a request that is going to be refused:
# Spring resolves and validates @RequestBody before the method guard runs, so a
# schema-invalid body would answer 400 and prove nothing about authorisation.
CLASS_PLACEHOLDER_SAFE=$CLASS_A1
code=$(call POST /sections "$TOKEN_A" "{\"name\":\"Blue\",\"description\":\"Morning roll\",\"capacity\":40}")
check "F6 setup: school A created a section" "200" "$code"
SECTION_A=$(field '.id')
require_value "F6 the cohort has two classrooms and a section" "$CLASS_A1$CLASS_A2$SECTION_A"

register_pupil() { # register_pupil <first name> <classId> <gender>
  local mail
  mail=$(lc "$1").$(lc "$SURNAME")@ashfordvale.test
  code=$(call POST /students/create "$TOKEN_A" \
    "{\"firstName\":\"$1\",\"lastName\":\"$SURNAME\",\"email\":\"$mail\",\"phoneNumber\":\"9710$RANDOM\",\"dateOfBirth\":\"2010-04-1$((RANDOM % 9))\",\"gender\":\"$3\",\"classId\":$2,\"sectionId\":$SECTION_A}")
  [[ "$code" == "200" ]] && fnote "registered $1 $SURNAME as $(field '.studentId') (id $(field '.id'))" \
                        || fnote "registration of $1 answered $code: $(jq -c . "$body" 2>/dev/null)"
}

code=$(call POST /students/create "$TOKEN_A" \
  "{\"firstName\":\"Ishaan\",\"lastName\":\"$SURNAME\",\"email\":\"ishaan.$(lc "$SURNAME")@ashfordvale.test\",\"phoneNumber\":\"9710000001\",\"dateOfBirth\":\"2010-04-11\",\"gender\":\"M\",\"classId\":$CLASS_A1,\"sectionId\":$SECTION_A}")
check "F6 student registration (POST /students/create) is accepted" "200" "$code"
STUDENT_A1=$(field '.id')
STUDENT_A1_SID=$(field '.studentId')
STUDENT_A1_NAME=$(field '.firstName')
require_value "F6 the registered pupil is given a row id" "$STUDENT_A1"
check "F6 and it is admitted with a per-school identifier" "yes" \
  "$([[ -n "$STUDENT_A1_SID" && "$STUDENT_A1_SID" != "null" ]] && echo yes || echo no)"
check "F6 the response is a StudentResponse carrying the tenant it was written into" "$SCHOOL_A" "$(field '.schoolId')"
check "F6 the pupil is created active" "1" "$(field '.status')"
check "F6 and it landed in the classroom it was given" "$CLASS_A1" "$(field '.classId')"

register_pupil "Nimal"  "$CLASS_A1" "M"
register_pupil "Rukshi" "$CLASS_A1" "F"
register_pupil "Dinesh" "$CLASS_A2" "M"
register_pupil "Kamala" "$CLASS_A2" "F"

code=$(call GET "/students" "$TOKEN_A")
check "F6 the five registered pupils are all on the school's list" "5" "$(list_len)"
check "F6 each with a per-school identifier of its own" "5" \
  "$(jq '[.[] | select(.studentId != null and .studentId != "")] | length' "$body")"
check "F6 and each identifier is distinct inside the school" "5" \
  "$(jq '[.[].studentId] | unique | length' "$body")"
check "F6 and none of them belongs to another school" "1" \
  "$(jq --argjson s "$SCHOOL_A" 'if ([.[] | select(.school_id != $s)] | length) == 0 then 1 else 0 end' "$body")"
if have_db; then
  check "F6 registration also opened a login account for each pupil" "5" \
    "$(sqlv "SELECT count(*) FROM users WHERE school_id=$SCHOOL_A AND student_id IS NOT NULL")"
  check "F6 and those accounts carry the STUDENT role and nothing else" "5" \
    "$(sqlv "SELECT count(*) FROM user_roles ur JOIN users u ON u.id=ur.user_id WHERE u.school_id=$SCHOOL_A AND ur.role='STUDENT'")"
  check "F6 each pupil row is stamped into the registering school at the database too" "5" \
    "$(sqlv "SELECT count(*) FROM students WHERE school_id=$SCHOOL_A")"
else
  skip "F6 that registration opened a login account per pupil" "GMS_VERIFY_DB or the container password is not set"
  skip "F6 that each pupil row is stamped into its school at the database" "GMS_VERIFY_DB or the container password is not set"
fi

# Admission: apply, then approve.
code=$(call POST /student-admissions/apply "$TOKEN_A" \
  "{\"firstName\":\"Sanduni\",\"lastName\":\"$SURNAME\",\"email\":\"sanduni.$(lc "$SURNAME")@ashfordvale.test\",\"phoneNumber\":\"9710000006\",\"dateOfBirth\":\"2010-08-02\",\"gender\":\"F\",\"address\":\"3 Kandy Road\",\"classId\":$CLASS_A2,\"sectionId\":$SECTION_A,\"parentFirstName\":\"Nanda\",\"parentLastName\":\"$SURNAME\",\"parentEmail\":\"nanda.$(lc "$SURNAME")@ashfordvale.test\",\"parentPhone\":\"9710000007\",\"previousSchool\":\"Hillwood Primary\",\"emergencyContactName\":\"Nanda $SURNAME\",\"emergencyContactPhone\":\"9710000007\"}")
check "F6 an admission application is accepted (POST /student-admissions/apply)" "200" "$code"
ADMISSION_A=$(field '.id')
require_value "F6 and the application is given a row id" "$ADMISSION_A"
check "F6 and it starts in the APPLIED state" "APPLIED" "$(field '.status')"
check "F6 and it is stamped into the applying school by the token" "$SCHOOL_A" "$(field '.school_id')"
code=$(call GET "/students/admissions" "$TOKEN_A")
check "F6 the application shows on the school's admission list" "1" \
  "$(jq --arg id "$ADMISSION_A" '[.[] | select((.id|tostring) == $id)] | length' "$body")"

code=$(call GET "/students" "$TOKEN_A")
STUDENTS_BEFORE_APPROVE=$(list_len)
code=$(call PUT "/student-admissions/$ADMISSION_A/approve" "$TOKEN_A")
check "F6 an APPLIED admission can be approved (PUT /student-admissions/{id}/approve)" "200" "$code"
code=$(call GET "/students/admissions" "$TOKEN_A")
check "F6 and the application moves to APPROVED" "APPROVED" \
  "$(jq -r --arg id "$ADMISSION_A" '[.[] | select((.id|tostring) == $id)][0].status // empty' "$body")"
code=$(call GET "/students" "$TOKEN_A")
check "F6 and approving wrote exactly one pupil into the school" "$((STUDENTS_BEFORE_APPROVE + 1))" "$(list_len)"
ADMITTED_ID=$(jq -r --arg e "sanduni.$(lc "$SURNAME")@ashfordvale.test" \
  '[.[] | select(.email == $e)] | (.[0].id // empty)' "$body")
ADMITTED_SID=$(jq -r --arg e "sanduni.$(lc "$SURNAME")@ashfordvale.test" \
  '[.[] | select(.email == $e)] | (.[0].studentId // "NONE")' "$body")
require_value "F6 the admitted pupil is findable by the applied-for email" "$ADMITTED_ID"
code=$(call GET "/students/$ADMITTED_ID" "$TOKEN_A")
check "F6 the admitted pupil is readable" "200" "$code"
check "F6 and the admission's classroom carried onto the pupil" "$CLASS_A2" "$(field '.class_id')"
# `StudentAdmission.student` is `@JsonIgnore` (StudentAdmission.java:82-85), so no route can answer
# "which pupil did this application become" — the link is a database fact and is read as one.
if have_db; then
  check "F6 and the admission row now points at the pupil it created" "1" \
    "$(sqlv "SELECT count(*) FROM student_admissions WHERE id=$ADMISSION_A AND student_id = $ADMITTED_ID")"
else
  skip "F6 and the admission row now points at the pupil it created" "the link is @JsonIgnore on the entity, so only a database handle can read it; set GMS_VERIFY_DB"
fi
# The two halves of registration the admission path used to skip. Until this run's card,
# StudentAdmissionServiceImpl:99-114 copied the name, email, phone, date of birth, gender and classroom
# onto the Student, set status "1" and saved it — and never called generateStudentId() nor
# createUserForStudent(). An admitted pupil therefore had no STD### and no login account while a
# registered one had both, against PRD MVP item 3's "with per-school identifiers". approveAdmission now
# calls StudentService.nextStudentId() and createUserForStudent() — the registration path's own two
# helpers, promoted to the interface rather than reimplemented — and the two checks below are what prove
# it, both reading columns no response discloses.
if have_db && [[ -n "$ADMITTED_ID" ]]; then
  check "F6 approval minted a per-school identifier for the admitted pupil, as registration does" "0" \
    "$(sqlv "SELECT count(*) FROM students WHERE id=$ADMITTED_ID AND (student_id IS NULL OR student_id = '')")"
  fnote "expected: 0 admitted pupils without an identifier; a nonzero count above is the admission path"
  fnote "skipping the identifier again. The admitted row is id $ADMITTED_ID."
  check "F6 approval opened a login account for the admitted pupil, as registration does" "1" \
    "$(sqlv "SELECT count(*) FROM users WHERE student_id=$ADMITTED_ID")"
else
  skip "F6 that approval minted a per-school identifier for the admitted pupil" "needs GMS_VERIFY_DB to read students.student_id, which no response discloses"
  skip "F6 that approval opened a login account for the admitted pupil" "needs GMS_VERIFY_DB to read users.student_id"
fi

# The reject path: a second application, refused, and no pupil written.
code=$(call POST /student-admissions/apply "$TOKEN_A" \
  "{\"firstName\":\"Refused\",\"lastName\":\"$SURNAME\",\"email\":\"refused.$(lc "$SURNAME")@ashfordvale.test\",\"gender\":\"M\",\"classId\":$CLASS_A2}")
check "F6 a second application is accepted" "200" "$code"
ADMISSION_R=$(field '.id')
code=$(call GET "/students" "$TOKEN_A")
STUDENTS_BEFORE_REJECT=$(list_len)
code=$(call PUT "/student-admissions/$ADMISSION_R/reject" "$TOKEN_A")
check "F6 an APPLIED admission can be rejected (PUT /student-admissions/{id}/reject)" "200" "$code"
code=$(call GET "/students/admissions" "$TOKEN_A")
check "F6 and the application moves to REJECTED" "REJECTED" \
  "$(jq -r --arg id "$ADMISSION_R" '[.[] | select((.id|tostring) == $id)][0].status // empty' "$body")"
code=$(call GET "/students" "$TOKEN_A")
check "F6 and rejecting wrote no pupil" "$STUDENTS_BEFORE_REJECT" "$(list_len)"
code=$(call PUT "/student-admissions/$ADMISSION_R/reject" "$TOKEN_A")
check "F6 a second decision on a decided admission is a 400, not a 500" "400" "$code"

# ─────────────────────────────────────────────────────────────────────────────
feature 7
code=$(call POST /students/create "$TOKEN_A" \
  "{\"firstName\":\"Oshadu\",\"lastName\":\"Pushpakumara\",\"email\":\"oshadu@brackmoor.test\",\"gender\":\"M\",\"classId\":$CLASS_A1}")
check "F7 setup: a sixth pupil was registered in school A" "200" "$code"
STUDENT_A2=$(field '.id')
STUDENT_A2_SID=$(field '.studentId')
require_value "F7 setup: the second pupil under test has an id" "$STUDENT_A2"

code=$(call GET "/students/$STUDENT_A2" "$TOKEN_A")
check "F7 read one (GET /students/{id})" "200" "$code"
check "F7 and the row read is the row written" "$STUDENT_A2" "$(field '.id')"

code=$(call PUT /students/update "$TOKEN_A" \
  "{\"id\":$STUDENT_A2,\"firstName\":\"Oshada\",\"lastName\":\"Pushpakumara\",\"email\":\"oshadu@brackmoor.test\",\"gender\":\"M\",\"classId\":$CLASS_A1,\"sectionId\":$SECTION_A}")
check "F7 update one (PUT /students/update) is accepted" "200" "$code"
check "F7 and the updated name comes back" "Oshada" "$(field '.firstName')"
code=$(call GET "/students/$STUDENT_A2" "$TOKEN_A")
check "F7 and the update is stored, not merely echoed" "Oshada" "$(field '.firstName')"
check "F7 and the pupil keeps its per-school identifier across the update" "$STUDENT_A2_SID" "$(field '.studentId')"
check "F7 and keeps its tenant too" "$SCHOOL_A" "$(field '.school_id')"

code=$(call GET "/students/999999" "$TOKEN_A")
check "F7 a pupil that does not exist answers 404" "404" "$code"
code=$(call PUT /students/update "$TOKEN_A" \
  "{\"firstName\":\"NoId\",\"lastName\":\"Anywhere\",\"email\":\"noid@mvpedge.test\",\"gender\":\"M\",\"classId\":$CLASS_A1}")
check "F7 an update that names no row is a 400, not a 500" "400" "$code"

# Archive is the domain's delete (StudentController:25-28). Toggled off and back on,
# so feature 9 counts live rows. The whole-list expectation is captured rather than written
# down, because by here the run has created pupils through both registration and admission.
code=$(call GET "/students" "$TOKEN_A")
STUDENTS_WHOLE=$(list_len)
code=$(call PATCH "/students/toggle?id=$STUDENT_A2&isActive=false" "$TOKEN_A")
check "F7 the archive toggle retires a pupil (PATCH /students/toggle)" "200" "$code"
check "F7 and the row is marked status 0" "0" "$(field '.status')"
code=$(call GET "/students/$STUDENT_A2" "$TOKEN_A")
check "F7 an archived pupil is not readable by id (StudentServiceImpl:240, 403 not 404)" "403" "$code"
code=$(call GET "/students" "$TOKEN_A")
check "F7 and it is gone from the school's list" "0" \
  "$(jq --arg id "$STUDENT_A2" '[.[] | select((.id|tostring) == $id)] | length' "$body")"
code=$(call GET "/students/search?query=pushpakumara" "$TOKEN_A")
check "F7 and it is gone from search too, which must agree with the list" "0" "$(jq -r '.totalElements' "$body")"
code=$(call PATCH "/students/toggle?id=$STUDENT_A2&isActive=true" "$TOKEN_A")
check "F7 the same verb restores the pupil" "200" "$code"
check "F7 with status 1" "1" "$(field '.status')"
code=$(call GET "/students/$STUDENT_A2" "$TOKEN_A")
check "F7 so the sections after this one still see a live pupil" "200" "$code"
code=$(call GET "/students" "$TOKEN_A")
check "F7 and the list is whole again" "$STUDENTS_WHOLE" "$(list_len)"

code=$(call DELETE "/students/$STUDENT_A2" "$TOKEN_A")
check "F7 there is no student delete: ADR-023 removed the inherited one" "405" "$code"
check "F7 and the refusal names the missing method rather than faulting" "Method Not Allowed" "$(field '.error')"
code=$(call GET "/students/$STUDENT_A2" "$TOKEN_A")
check "F7 and the pupil this run refused to delete is still there" "$STUDENT_A2" "$(field '.id')"

# ─────────────────────────────────────────────────────────────────────────────
feature 8
# GET /students/search?query=&classId=&page=&size=&sort= (StudentController:97,
# StudentServiceImpl:182) is the route added for the PRD's server-side pagination
# item. It returns a Spring Data Page, so content / totalElements / totalPages / size
# / number are the contract the frontend reads.
UPPER_SURNAME=$(printf '%s' "$SURNAME" | tr 'a-z' 'A-Z')
MIXED_SURNAME=$(printf '%s' "$SURNAME" | awk '{print toupper(substr($1,1,1)) tolower(substr($1,2,1)) toupper(substr($1,3,1)) tolower(substr($1,4))}')
LOWER_SURNAME=$(lc "$SURNAME")

code=$(call GET "/students" "$TOKEN_A")
save_ref
ALL_A=$(expected_all)
fnote "school A holds $ALL_A live pupils, all written by this run; every count below is derived"
fnote "from them rather than hardcoded, so a search that widened or narrowed cannot agree with"
fnote "its own expectation by accident."

code=$(call GET "/students/search" "$TOKEN_A")
check "F8 the search route exists and answers 200" "200" "$code"
check "F8 with a Spring Data Page body rather than a bare array" "true" "$(jq 'type == "object" and (has("content") and has("totalElements") and has("totalPages") and has("size") and has("number"))' "$body")"
check "F8 whose totalElements is the whole tenant" "$ALL_A" "$(jq -r '.totalElements' "$body")"
check "F8 whose content holds that many rows" "$ALL_A" "$(jq -r '.content | length' "$body")"
check "F8 at the documented default page size" "20" "$(jq -r '.size' "$body")"
check "F8 on page 0" "0" "$(jq -r '.number' "$body")"
check "F8 and every row it answers is a StudentResponse carrying its tenant" "1" \
  "$(jq --argjson s "$SCHOOL_A" 'if ([.content[] | select(.schoolId != $s)] | length) == 0 then 1 else 0 end' "$body")"

code=$(call GET "/students/search?query=$LOWER_SURNAME" "$TOKEN_A")
EXPECT=$(expected_matches "$LOWER_SURNAME")
check "F8 a free-text query on the surname matches the derived expectation" "$EXPECT" "$(jq -r '.totalElements' "$body")"
check "F8 and the query matches more than one row, which a paging proof needs" "yes" \
  "$([[ "$EXPECT" -gt 1 ]] && echo yes || echo no)"
code=$(call GET "/students/search?query=$UPPER_SURNAME" "$TOKEN_A")
check "F8 the query is case-insensitive: upper case answers the same count" "$EXPECT" "$(jq -r '.totalElements' "$body")"
code=$(call GET "/students/search?query=$MIXED_SURNAME" "$TOKEN_A")
check "F8 and a mixed-case spelling of it answers the same count too" "$EXPECT" "$(jq -r '.totalElements' "$body")"
code=$(call GET "/students/search?query=ishaan" "$TOKEN_A")
check "F8 the first name is searched, not only the surname" "1" "$(jq -r '.totalElements' "$body")"
code=$(jq -r '.content[0].email' "$body")
code=$(call GET "/students/search?query=ashfordvale&size=100" "$TOKEN_A")
DOMAIN_MATCH=$(sqlv "SELECT count(*) FROM students WHERE school_id = $SCHOOL_A AND status <> '0' AND position('ashfordvale' in lower(email)) > 0")
check "F8 and so is the email, across every pupil that shares the school's domain" "$DOMAIN_MATCH" "$(jq -r '.totalElements' "$body")"
code=$(call GET "/students/search?query=$STUDENT_A1_SID&size=100" "$TOKEN_A")
check "F8 and so is the per-school identifier itself" "1" "$(jq -r '.totalElements' "$body")"
code=$(call GET "/students/search?query=nobodyatall" "$TOKEN_A")
check "F8 a query that matches nothing answers 200 rather than an error" "200" "$code"
check "F8 with zero elements" "0" "$(jq -r '.totalElements' "$body")"
check "F8 and no rows" "0" "$(jq -r '.content | length' "$body")"
check "F8 and an honest page count of zero" "0" "$(jq -r '.totalPages' "$body")"
code=$(call GET "/students/search?query=%25" "$TOKEN_A")
check "F8 a literal percent is matched as text, not as a wildcard" "0" "$(jq -r '.totalElements' "$body")"

# classId filtering.
code=$(call GET "/students/search?classId=$CLASS_A1&size=100" "$TOKEN_A")
EXPECT_A1=$(expected_in_class "$CLASS_A1")
check "F8 a classId filter returns exactly that classroom's pupils" "$EXPECT_A1" "$(jq -r '.totalElements' "$body")"
check "F8 and every row it returns is in that classroom" "1" \
  "$(jq --argjson c "$CLASS_A1" 'if ([.content[] | select(.classId != $c)] | length) == 0 then 1 else 0 end' "$body")"
code=$(call GET "/students/search?classId=$CLASS_A2&query=$LOWER_SURNAME&size=100" "$TOKEN_A")
EXPECT_A2=$(expected_in_class "$CLASS_A2")
check "F8 a query and a classId combine rather than one overriding the other" "$EXPECT_A2" "$(jq -r '.totalElements' "$body")"
code=$(call GET "/students/search?classId=$CLASS_A1&query=nobodyatall" "$TOKEN_A")
check "F8 and an impossible query narrows a real classId to nothing" "0" "$(jq -r '.totalElements' "$body")"
code=$(call GET "/students/search?classId=999999&size=100" "$TOKEN_A")
check "F8 a classroom that does not exist narrows to nothing rather than widening" "0" "$(jq -r '.totalElements' "$body")"
check "F8 and narrows without erroring" "200" "$code"

# Server-side paging: one row per page, and the pages partition the result set.
code=$(call GET "/students/search?query=$LOWER_SURNAME&size=1&page=0&sort=id,asc" "$TOKEN_A")
TOTAL_SID=$(jq -r '.totalElements' "$body")
PAGE0_ID=$(jq -r '.content[0].id' "$body")
check "F8 a page of size 1 over more than one matching row holds exactly one row" "1" "$(jq -r '.content | length' "$body")"
check "F8 while still reporting the whole match count" "$TOTAL_SID" "$(jq -r '.totalElements' "$body")"
check "F8 and the server computed one page per matching row" "$TOTAL_SID" "$(jq -r '.totalPages' "$body")"
code=$(call GET "/students/search?query=$LOWER_SURNAME&size=1&page=1&sort=id,asc" "$TOKEN_A")
PAGE1_ID=$(jq -r '.content[0].id' "$body")
check "F8 page 1 is a different row from page 0, so the offset really moved" "different" \
  "$([[ -n "$PAGE1_ID" && "$PAGE1_ID" != "$PAGE0_ID" ]] && echo different || echo same)"
check "F8 and page 1 is still one row, not the whole result set" "1" "$(jq -r '.content | length' "$body")"
check "F8 and the total is unchanged by the paging" "$TOTAL_SID" "$(jq -r '.totalElements' "$body")"

code=$(call GET "/students/search?size=3&page=0&sort=id,asc" "$TOKEN_A")
TOTAL_PAGES=$(jq -r '.totalPages' "$body")
PAGE_IDS=$(jq -r '[.content[].id] | map(tostring) | join(",")' "$body")
p=1
if [[ "$TOTAL_PAGES" =~ ^[0-9]+$ ]]; then
  while [ "$p" -lt "$TOTAL_PAGES" ]; do
    code=$(call GET "/students/search?size=3&page=$p&sort=id,asc" "$TOKEN_A")
    one=$(jq -r '[.content[].id] | map(tostring) | join(",")' "$body")
    [[ -n "$one" ]] && PAGE_IDS="$PAGE_IDS,$one"
    p=$((p + 1))
  done
fi
PAGE_IDS=$(printf '%s' "$PAGE_IDS" | sed 's/,,*/,/g; s/^,//; s/,$//')
code=$(call GET "/students/search?size=100&sort=id,asc" "$TOKEN_A")
FULL_IDS=$(jq -r '[.content[].id] | map(tostring) | join(",")' "$body")
check "F8 walking every page returns the whole result set exactly once, in order" "$FULL_IDS" "$PAGE_IDS"
check "F8 with no row returned twice" "true" \
  "$(printf '%s' "$PAGE_IDS" | tr ',' '\n' | awk 'NF' | sort | uniq -d | grep -q . && echo false || echo true)"
code=$(call GET "/students/search?size=3&page=99" "$TOKEN_A")
check "F8 a page past the end is 200, not an error" "200" "$code"
check "F8 with no rows" "0" "$(jq -r '.content | length' "$body")"
check "F8 and the same honest totalElements, so a client can tell empty from exhausted" "$ALL_A" "$(jq -r '.totalElements' "$body")"
code=$(call GET "/students/search?page=-1" "$TOKEN_A")
check "F8 a negative page is floored to page 0 rather than failing" "200" "$code"
check "F8 and it really is page 0" "0" "$(jq -r '.number' "$body")"
code=$(call GET "/students/search?size=0" "$TOKEN_A")
check "F8 a zero size falls back to the documented default" "20" "$(jq -r '.size' "$body")"
code=$(call GET "/students/search?size=-5" "$TOKEN_A")
check "F8 and so does a negative one" "20" "$(jq -r '.size' "$body")"
code=$(call GET "/students/search?size=5000" "$TOKEN_A")
check "F8 a size above the server's ceiling is capped, not honoured" "100" "$(jq -r '.size' "$body")"
check "F8 without dropping the rows the cap withheld" "$ALL_A" "$(jq -r '.content | length' "$body")"
code=$(call GET "/students/search?page=abc" "$TOKEN_A")
check "F8 a page that is not a number is a 400, not a 500" "400" "$code"

# Sorting.
code=$(call GET "/students/search?sort=firstName,asc&size=100" "$TOKEN_A")
check "F8 sort=firstName,asc is honoured" "true" \
  "$(jq '[.content[].firstName] == ([.content[].firstName] | sort)' "$body")"
code=$(call GET "/students/search?sort=firstName,DESC&size=100" "$TOKEN_A")
check "F8 sort=firstName,DESC reverses it (the direction is case-insensitive)" "true" \
  "$(jq '[.content[].firstName] == ([.content[].firstName] | sort | reverse)' "$body")"
code=$(call GET "/students/search?sort=id&size=100" "$TOKEN_A")
check "F8 a bare field name sorts ascending" "true" \
  "$(jq '[.content[].id] == ([.content[].id] | sort)' "$body")"
code=$(call GET "/students/search?sort=passwordHash&size=100" "$TOKEN_A")
check "F8 an unwhitelisted sort field is ignored rather than thrown at the query builder" "200" "$code"
check "F8 and the page still carries the full result set" "$ALL_A" "$(jq -r '.totalElements' "$body")"
code=$(call GET "/students/search?size=100" "$TOKEN_A")
check "F8 the unsorted default answers every row" "$ALL_A" "$(jq -r '.content | length' "$body")"
check "F8 and sorts by the documented default field, lastName" "true" \
  "$(jq '[.content[].lastName] == ([.content[].lastName] | sort)' "$body")"

# Reach: a parameter cannot widen the tenant, and only the platform claim can.
# The cross-tenant half of that proof needs a pupil in the *other* tenant wearing the same
# surname; without one, a platform-wide page for "Wickrama" legitimately holds a single tenant
# and "spans more than one" is a question this database cannot answer. School B therefore gets
# its own, deliberately named like school A's cohort — which also replaces the weakest count in
# this file: school B answering zero rows cannot tell a scoped query from an empty one, whereas
# school B answering exactly one, beside school A's seven, can.
code=$(call POST /classrooms/create "$TOKEN_B" '{"name":"Grade 11","grade":"11","section":"B"}')
check "F8 setup: school B created a classroom of its own" "200" "$code"
CLASS_B_SEARCH=$(field '.id')
require_value "F8 setup: that classroom has a row id" "$CLASS_B_SEARCH"
code=$(call POST /students/create "$TOKEN_B" \
  "{\"firstName\":\"Iru\",\"lastName\":\"$SURNAME\",\"email\":\"iru.$LOWER_SURNAME@brackmoor.test\",\"gender\":\"M\",\"classId\":$CLASS_B_SEARCH}")
check "F8 setup: school B registered a pupil sharing school A's surname" "200" "$code"
code=$(call GET "/students/search?schoolId=$SCHOOL_B&size=100" "$TOKEN_A")
check "F8 a schoolId query parameter does not move the tenant" "$ALL_A" "$(jq -r '.totalElements' "$body")"
code=$(call GET "/students/search?query=$LOWER_SURNAME&size=100" "$TOKEN_B")
check "F8 school B's search answers its own one match, not school A's" "1" "$(jq -r '.totalElements' "$body")"
code=$(call GET "/students/search?query=$LOWER_SURNAME&size=100" "$PLATFORM_TOKEN")
PLATFORM_SEEN=$(jq -r '.totalElements' "$body")
TENANTS=$(jq '[.content[].schoolId] | unique | length' "$body")
if have_db; then
  EXPECT_PLATFORM=$(sqlv "SELECT count(*) FROM students WHERE status <> '0' AND lower(last_name) LIKE '%$(lc "$SURNAME")%'")
  check "F8 the platform owner's search is the cross-tenant arm and matches the raw count" "$EXPECT_PLATFORM" "$PLATFORM_SEEN"
else
  skip "F8 the platform search reconciled against a raw count" "GMS_VERIFY_DB or the container password is not set"
  check "F8 the platform owner sees at least school A's matches" "yes" \
    "$([[ "$PLATFORM_SEEN" -ge "$EXPECT" ]] && echo yes || echo no)"
fi
check "F8 and it spans more than one tenant where school A's own search cannot" "yes" \
  "$([[ "$TENANTS" -ge 2 ]] && echo yes || echo no)"
code=$(call GET "/students/search?size=100" "$TOKEN_T")
refused "F8 a TEACHER cannot reach the search route at all" "$code"

# ─────────────────────────────────────────────────────────────────────────────
feature 9
# The cards must be aggregates the database answered, not list lengths the API can
# fake. The shape assertions always run; the reconciliation needs a DB handle and
# SKIPs without one.
code=$(call POST /employees/create "$TOKEN_A" \
  '{"firstName":"Priya","lastName":"Perera","email":"priya.perera@ashfordvale.test","phoneNumber":"9720000001","designation":"TEACHER","department":"ACADEMIC","teaching":true}')
check "F9 setup: school A registered a staff member" "200" "$code"
EMPLOYEE_A=$(field '.id')
code=$(call POST /employees/create "$TOKEN_A" \
  '{"firstName":"Sanjay","lastName":"Fernando","email":"sanjay.fernando@ashfordvale.test","phoneNumber":"9720000002","designation":"ACCOUNTANT","department":"FINANCE","teaching":false}')
check "F9 setup: school A registered a second staff member" "200" "$code"
EMPLOYEE_B=$(field '.id')
require_value "F9 the staff rows have ids" "$EMPLOYEE_A$EMPLOYEE_B"

code=$(call GET "/dashboard/school" "$TOKEN_A")
check "F9 a school dashboard exists (GET /dashboard/school)" "200" "$code"
check "F9 and it is computed for the caller's own school" "$SCHOOL_A" "$(field '.schoolId')"
require_value "F9 with a generation timestamp, so a stale card is at least an honest one" "$(field '.generatedAt')"
check "F9 the students card agrees with the students list this dashboard replaces" "$ALL_A" "$(field '.totalStudents')"
code=$(call GET "/employees" "$TOKEN_A")
EMP_LIST=$(list_len)
code=$(call GET "/classrooms" "$TOKEN_A")
CLS_LIST=$(list_len)
code=$(call GET "/sections" "$TOKEN_A")
SEC_LIST=$(list_len)
code=$(call GET "/dashboard/school" "$TOKEN_A")
check "F9 the staff card agrees with the staff list" "$EMP_LIST" "$(field '.totalEmployees')"
check "F9 the classroom card agrees with the classroom list" "$CLS_LIST" "$(field '.totalClassrooms')"
check "F9 the section card agrees with the section list" "$SEC_LIST" "$(field '.totalSections')"
check "F9 every card is a number rather than a null the client has to guess about" "yes" \
  "$(jq -r 'if (.totalStudents != null and .totalEmployees != null and .totalClassrooms != null and .totalSections != null and .totalExams != null) then "yes" else "no" end' "$body")"
check "F9 a school with no fee rows reports 0 rather than a missing field" "yes" \
  "$(jq -r 'if (.feesCollectedAmount != null and .feesUncollectedAmount != null) then "yes" else "no" end' "$body")"
check "F9 the cards count something, rather than every card being zero" "yes" \
  "$([[ "$ALL_A" -gt 1 && "$EMP_LIST" -gt 1 && "$CLS_LIST" -gt 1 ]] && echo yes || echo no)"
fnote "totalExams is reconciled against SQL below and is 0 here because this MVP journey"
fnote "creates no exam. The card's shape is proven; its counting of exams is not."

# A second tenant's card must be that tenant's numbers, which a global figure could not be.
code=$(call GET "/dashboard/school" "$TOKEN_B")
check "F9 school B's dashboard is school B" "$SCHOOL_B" "$(field '.schoolId')"
B_STUDENTS=$(field '.totalStudents')
check "F9 and it counts its own pupil and none of school A's" "1" "$B_STUDENTS"
check "F9 so the card follows the token's tenant rather than the table" "different" \
  "$([[ "$ALL_A" != "$B_STUDENTS" ]] && echo different || echo same)"

# The delta proof: one more write through the API, and the card moves by exactly one.
code=$(call GET "/dashboard/school" "$TOKEN_A")
BEFORE_STUDENTS=$(field '.totalStudents')
register_pupil "Terin" "$CLASS_A1" "M"
[[ "$code" == "200" ]] && check "F9 setup: a further pupil was registered through the API" "200" "200" \
                       || check "F9 setup: a further pupil was registered through the API" "200" "$code"
code=$(call GET "/dashboard/school" "$TOKEN_A")
check "F9 and the students card moved by exactly one after the write" "$((BEFORE_STUDENTS + 1))" "$(field '.totalStudents')"
code=$(call GET "/students" "$TOKEN_A")
check "F9 and so did the list the card is meant to replace, by the same one" "$((ALL_A + 1))" "$(list_len)"
code=$(call GET "/dashboard/school" "$TOKEN_A")
AFTER_STUDENTS=$(field '.totalStudents')
ALL_A=$AFTER_STUDENTS
code=$(call GET "/students" "$TOKEN_A")
save_ref

if have_db; then
  code=$(call GET "/dashboard/school" "$TOKEN_A")
  D_STUD=$(field '.totalStudents'); D_EMP=$(field '.totalEmployees')
  D_CLS=$(field '.totalClassrooms'); D_SEC=$(field '.totalSections'); D_EXAM=$(field '.totalExams')
  check "F9 the students card IS SELECT count(*) FROM students WHERE school_id=A AND status<>'0'" \
    "$(sqlv "SELECT count(*) FROM students WHERE school_id=$SCHOOL_A AND status <> '0'")" "$D_STUD"
  check "F9 the staff card is the same predicate over employees.employee_status" \
    "$(sqlv "SELECT count(*) FROM employees WHERE school_id=$SCHOOL_A AND employee_status <> '0'")" "$D_EMP"
  check "F9 the classroom card is the same predicate over classrooms.status" \
    "$(sqlv "SELECT count(*) FROM classrooms WHERE school_id=$SCHOOL_A AND status <> '0'")" "$D_CLS"
  check "F9 the section card counts only status = '1', the page's own predicate" \
    "$(sqlv "SELECT count(*) FROM sections WHERE school_id=$SCHOOL_A AND status = '1'")" "$D_SEC"
  check "F9 the exam card counts only status = '1' exams" \
    "$(sqlv "SELECT count(*) FROM exams WHERE school_id=$SCHOOL_A AND status = '1'")" "$D_EXAM"
  check "F9 the cards are not all zero because the SQL is not all zero either" "yes" \
    "$([[ "$D_STUD" -gt 1 ]] && echo yes || echo no)"
else
  skip "F9 the students card reconciled against SELECT count(*)" "GMS_VERIFY_DB or the container password is not set"
  skip "F9 the staff card reconciled against SELECT count(*)" "GMS_VERIFY_DB or the container password is not set"
  skip "F9 the classroom card reconciled against SELECT count(*)" "GMS_VERIFY_DB or the container password is not set"
  skip "F9 the section card reconciled against SELECT count(*)" "GMS_VERIFY_DB or the container password is not set"
  skip "F9 the exam card reconciled against SELECT count(*)" "GMS_VERIFY_DB or the container password is not set"
fi

# Archived rows must leave the card and the raw count together, and come back.
code=$(call PATCH "/students/toggle?id=$STUDENT_A2&isActive=false" "$TOKEN_A")
code=$(call GET "/dashboard/school" "$TOKEN_A")
AFTER_ARCHIVE=$(field '.totalStudents')
check "F9 archiving a pupil drops it from the card by exactly one" "$((AFTER_STUDENTS - 1))" "$AFTER_ARCHIVE"
code=$(call PATCH "/students/toggle?id=$STUDENT_A2&isActive=true" "$TOKEN_A")
code=$(call GET "/dashboard/school" "$TOKEN_A")
AFTER_RESTORE=$(field '.totalStudents')
check "F9 and restoring puts it back, so feature 11 reads live rows" "$AFTER_STUDENTS" "$AFTER_RESTORE"

code=$(call GET "/dashboard/platform" "$PLATFORM_TOKEN")
check "F9 the platform dashboard exists (GET /dashboard/platform)" "200" "$code"
require_value "F9 with a generation timestamp" "$(field '.generatedAt')"
check "F9 its school count is at least the two tenants this run made" "yes" \
  "$(jq -r 'if .totalSchools >= 2 then "yes" else "no" end' "$body")"
check "F9 its student total includes school A's pupils" "yes" \
  "$(jq -r --argjson a "$AFTER_STUDENTS" 'if .totalStudents >= $a then "yes" else "no" end' "$body")"
code=$(call GET "/dashboard/school" "$PLATFORM_TOKEN")
check "F9 the platform owner's own school card is its system school, not another tenant's totals" \
  "$PLATFORM_SCHOOL" "$(field '.schoolId')"
if have_db; then
  code=$(call GET "/dashboard/platform" "$PLATFORM_TOKEN")
  P_SCH=$(field '.totalSchools'); P_USR=$(field '.totalUsers')
  P_STU=$(field '.totalStudents'); P_EMP=$(field '.totalEmployees')
  check "F9 the platform school count IS SELECT count(*) FROM schools WHERE status<>'0'" \
    "$(sqlv "SELECT count(*) FROM schools WHERE status <> '0'")" "$P_SCH"
  check "F9 the platform user count is the same predicate over users.user_status" \
    "$(sqlv "SELECT count(*) FROM users WHERE user_status <> '0'")" "$P_USR"
  check "F9 the platform student count is the same predicate over students.status" \
    "$(sqlv "SELECT count(*) FROM students WHERE status <> '0'")" "$P_STU"
  check "F9 the platform employee count is the same predicate over employees.employee_status" \
    "$(sqlv "SELECT count(*) FROM employees WHERE employee_status <> '0'")" "$P_EMP"
  check "F9 and the platform total is at least school A's card plus school B's, as the PRD's reconciliation demands" \
    "yes" "$([[ "$P_STU" -ge $((AFTER_STUDENTS + B_STUDENTS)) ]] && echo yes || echo no)"
else
  skip "F9 the platform dashboard reconciled against raw counts" "GMS_VERIFY_DB or the container password is not set"
fi

# ─────────────────────────────────────────────────────────────────────────────
feature 10
code=$(call POST /classrooms/create "$TOKEN_B" '{"name":"Grade 9","grade":"9","section":"A"}')
check "F10 setup: school B created a classroom of its own" "200" "$code"
CLASS_B1=$(field '.id')
code=$(call POST /students/create "$TOKEN_B" \
  '{"firstName":"Nadia","lastName":"Fernando","email":"nadia.fernando@brackmoor.test","gender":"F","classId":'$CLASS_B1'}')
check "F10 setup: school B registered a pupil" "200" "$code"
STUDENT_B=$(field '.id')
require_value "F10 school B's pupil has a row of its own" "$STUDENT_B"

# Neither direction of the read, and the refusal must not disclose the row. A 200
# carrying the body is the failure this is written to catch.
code=$(call GET "/students/$STUDENT_B" "$TOKEN_A")
refused "F10 school A cannot read school B's pupil by id" "$code"
check "F10 and the refusal disclosed none of the pupil's fields" "" "$(field '.firstName')"
code=$(call GET "/students/$STUDENT_A1" "$TOKEN_B")
refused "F10 school B cannot read school A's pupil by id" "$code"
check "F10 and that refusal disclosed nothing either" "" "$(field '.email')"
code=$(call GET "/students" "$TOKEN_B")
check "F10 school A's pupils are absent from school B's list, row for row" "0" \
  "$(jq --argjson a "$SCHOOL_A" '[.[] | select(.school_id == $a)] | length' "$body")"
check "F10 and school B's own list holds only school B's two pupils" "2" "$(list_len)"
code=$(call GET "/students/search?query=$LOWER_SURNAME&size=100" "$TOKEN_B")
check "F10 school A's surname reaches school B only through the pupil school B wrote itself" "1" \
  "$(jq -r '.totalElements' "$body")"
fnote "school A holds several Wickramas; the one row here is Iru, created into school B in feature 8."
code=$(call GET "/students/search?classId=$CLASS_A1&size=100" "$TOKEN_B")
check "F10 nor does a foreign classroom id widen it" "0" "$(jq -r '.totalElements' "$body")"
code=$(call GET "/students/admissions" "$TOKEN_B")
check "F10 school A's admissions are absent from school B's admission list" "0" \
  "$(jq --argjson a "$SCHOOL_A" '[.[] | select(.school_id == $a)] | length' "$body")"

# Writes across the boundary are refused the same way, and write nothing.
code=$(call GET "/students" "$TOKEN_A")
save_ref
A_STUDENTS_BEFORE=$(expected_all)
A_IDS_BEFORE=$(jq -r '[.[].id] | map(tostring) | join(",")' "$ref")
code=$(call PUT /students/update "$TOKEN_B" \
  "{\"id\":$STUDENT_A1,\"firstName\":\"Tampered\",\"lastName\":\"$SURNAME\",\"email\":\"ishaan.$(lc "$SURNAME")@ashfordvale.test\",\"gender\":\"M\",\"classId\":$CLASS_A1}")
refused "F10 school B cannot update school A's pupil" "$code"
code=$(call GET "/students/$STUDENT_A1" "$TOKEN_A")
check "F10 and the pupil it tried to rewrite still has its own name" "$STUDENT_A1_NAME" "$(field '.firstName')"
code=$(call PATCH "/students/toggle?id=$STUDENT_A1&isActive=false" "$TOKEN_B")
refused "F10 school B cannot archive school A's pupil" "$code"
code=$(call GET "/students/$STUDENT_A1" "$TOKEN_A")
check "F10 and the pupil it tried to archive is still active" "1" "$(field '.status')"
code=$(call GET "/students" "$TOKEN_A")
check "F10 school A's pupil set is exactly what it was, id for id" "$A_IDS_BEFORE" \
  "$(jq -r '[.[].id] | map(tostring) | join(",")' "$body")"

code=$(call POST /student-admissions/apply "$TOKEN_A" \
  "{\"firstName\":\"Penumbral\",\"lastName\":\"$SURNAME\",\"email\":\"penumbral.$(lc "$SURNAME")@ashfordvale.test\",\"gender\":\"M\",\"classId\":$CLASS_A2}")
check "F10 setup: school A opened an admission to be crossed on" "200" "$code"
ADMISSION_CROSS=$(field '.id')
code=$(call PUT "/student-admissions/$ADMISSION_CROSS/approve" "$TOKEN_B")
refused "F10 school B cannot approve school A's admission" "$code"
code=$(call GET "/students/admissions" "$TOKEN_A")
check "F10 and the application is still APPLIED, not approved behind the tenant's back" "APPLIED" \
  "$(jq -r --arg id "$ADMISSION_CROSS" '[.[] | select((.id|tostring) == $id)][0].status // empty' "$body")"
code=$(call GET "/students" "$TOKEN_A")
check "F10 and the refused approval wrote no pupil into school A" "$A_STUDENTS_BEFORE" "$(list_len)"
code=$(call PATCH "/student-admissions/$ADMISSION_CROSS/reject" "$TOKEN_B")
check "F10 reject is a PUT, and the wrong verb is a 405 rather than a silent route" "405" "$code"

# Other tenant-owned rows, not only pupils.
code=$(call GET "/classrooms/$CLASS_A1" "$TOKEN_B")
refused "F10 school B cannot read school A's classroom by id" "$code"
code=$(call GET "/subjects/$SUBJECT_A" "$TOKEN_B")
refused "F10 school B cannot read school A's subject by id" "$code"
code=$(call GET "/employees/$EMPLOYEE_A" "$TOKEN_B")
refused "F10 school B cannot read school A's staff row by id" "$code"
code=$(call DELETE "/classrooms/$CLASS_A1" "$TOKEN_B")
refused "F10 school B cannot delete school A's classroom" "$code"
code=$(call GET "/classrooms/$CLASS_A1" "$TOKEN_A")
check "F10 and the classroom it tried to delete still exists" "$CLASS_A1" "$(field '.id')"
code=$(call DELETE "/subjects/$SUBJECT_A" "$TOKEN_B")
refused "F10 school B cannot delete school A's subject" "$code"
code=$(call GET "/subjects/$SUBJECT_A" "$TOKEN_A")
check "F10 and that subject survived the attempt" "$SUBJECT_A" "$(field '.id')"
code=$(call PATCH "/schools/toggle?id=$SCHOOL_A&isActive=false" "$TOKEN_B")
refused "F10 school B cannot retire school A" "$code"
code=$(call GET "/schools/$SCHOOL_A" "$PLATFORM_TOKEN")
check "F10 and school A was not retired" "1" "$(field '.status')"
code=$(call PUT /schools/update "$TOKEN_B" "{\"id\":$SCHOOL_A,\"schoolName\":\"Not My School\",\"schoolCode\":\"$SCHOOL_A_CODE\"}")
refused "F10 school B cannot update school A's tenant record" "$code"
code=$(call GET "/schools/$SCHOOL_A" "$PLATFORM_TOKEN")
check "F10 and the name it tried to change is unchanged" "Ashford Vale Secondary College" "$(field '.schoolName')"

# The platform owner deliberately reaches across — the opposite proof, and the one
# that distinguishes a scoped read from a broken one.
code=$(call GET "/students/$STUDENT_A1" "$PLATFORM_TOKEN")
check "F10 the platform owner does read school A's pupil on purpose" "200" "$code"
check "F10 and gets the row it names" "$STUDENT_A1" "$(field '.id')"
code=$(call GET "/students/$STUDENT_B" "$PLATFORM_TOKEN")
check "F10 and school B's pupil too" "200" "$code"
check "F10 which is the difference between isolation and a data outage" "$STUDENT_B" "$(field '.id')"
code=$(call GET "/schools" "$PLATFORM_TOKEN")
check "F10 the platform owner lists every tenant, both of this run's among them" "2" \
  "$(jq --argjson a "$SCHOOL_A" --argjson b "$SCHOOL_B" '[.[] | select(.id == $a or .id == $b)] | length' "$body")"
code=$(call GET "/classrooms/$CLASS_B1" "$PLATFORM_TOKEN")
check "F10 and reaches a classroom in either school by id" "200" "$code"
code=$(call GET "/students/search?query=$LOWER_SURNAME&size=100" "$PLATFORM_TOKEN")
check "F10 its search spans both tenants in one page" "yes" \
  "$(jq -r '[.content[].schoolId] | unique | if length >= 2 then "yes" else "no" end' "$body")"

# A tampered token is a refusal, not a weaker principal.
code=$(call GET "/students/$STUDENT_B" "${TOKEN_A}x")
check "F10 a token whose signature does not verify is refused with 401, not honoured" "401" "$code"

# ─────────────────────────────────────────────────────────────────────────────
feature 11
# "Who wrote this" is a column. BaseAuditEntity maps created_by / updated_by to a User FK through
# AuthorshipEntity, and every table this MVP journey writes now has both: schools (V1:855),
# employees (V1:281) and sections (V1:883) from the baseline, and students, classrooms, subjects and
# student_admissions from V7__record_who_wrote_the_pupil.sql. They were the gap this card closed —
# measured here at 1 of 4 before it — and the count check below is what stops the gap reopening: a
# migration that drops the column, or a schema rebuilt from a baseline that never had it, fails here
# rather than silently leaving the trail unwritten. Whether the column exists is asserted first, then
# whether the application fills it. An unpopulated author is a FAIL with the expectation printed, not
# a softened check.
if have_db; then
  CORE_WITH_AUTHOR=$(sqlv "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND column_name='created_by' AND table_name IN ('schools','students','classrooms','subjects')")
  check "F11 every core MVP write path has an author column (schools, students, classrooms, subjects)" "4" "$CORE_WITH_AUTHOR"
  fnote "measured $CORE_WITH_AUTHOR of 4. Four is V7's work; the baseline had one, and the three"
  fnote "pupil-shaped tables had no place to record an author at all. The next checks say whether"
  fnote "the application writes the columns, which is the half a migration cannot do."

  check "F11 the school this run created records who created it" "$PLATFORM_UID" \
    "$(sqlv "SELECT coalesce(created_by::text,'') FROM schools WHERE id=$SCHOOL_A")"
  fnote "expected the platform account's users.id ($PLATFORM_UID). SchoolServiceImpl"
  fnote "createSchoolFromRequest (:136-163) sets every business field and never calls"
  fnote "setCreatedBy, although UserServiceImpl:76, EmployeeServiceImpl:76 and"
  fnote "SectionServiceImpl:59 all do. The column is there and unused — hence a FAIL, not a SKIP."
  check "F11 and who last updated it" "$PLATFORM_UID" \
    "$(sqlv "SELECT coalesce(updated_by::text,'') FROM schools WHERE id=$SCHOOL_A")"
  check "F11 the account provisioning created records who provisioned it" "$PLATFORM_UID" \
    "$(sqlv "SELECT coalesce(created_by::text,'') FROM users WHERE username='$ADMIN_A_USER'")"
  fnote "SchoolServiceImpl:89-99 builds the admin User with new User() and userRepository.save"
  fnote "directly, bypassing UserServiceImpl.createUserFromRequest, which is the code that sets"
  fnote "created_by. A school created through the API therefore has no author on its own account."

  check "F11 an employee row created through the API records its author" "$ADMIN_A_UID" \
    "$(sqlv "SELECT coalesce(created_by::text,'') FROM employees WHERE id=$EMPLOYEE_A")"
  check "F11 the second employee row likewise" "$ADMIN_A_UID" \
    "$(sqlv "SELECT coalesce(created_by::text,'') FROM employees WHERE id=$EMPLOYEE_B")"
  code=$(call PUT /employees/update "$TOKEN_A" \
    "{\"id\":$EMPLOYEE_A,\"firstName\":\"Priya\",\"lastName\":\"Perera\",\"email\":\"priya.perera@ashfordvale.test\",\"designation\":\"HEAD_OF_DEPARTMENT\",\"department\":\"ACADEMIC\",\"teaching\":true}")
  check "F11 setup: the API updated that employee" "200" "$code"
  check "F11 and the update is attributed to the caller who made it" "$ADMIN_A_UID" \
    "$(sqlv "SELECT coalesce(updated_by::text,'') FROM employees WHERE id=$EMPLOYEE_A")"
  check "F11 and it still names its original creator afterwards" "$ADMIN_A_UID" \
    "$(sqlv "SELECT coalesce(created_by::text,'') FROM employees WHERE id=$EMPLOYEE_A")"
  check "F11 a section created through the API records its author" "$ADMIN_A_UID" \
    "$(sqlv "SELECT coalesce(created_by::text,'') FROM sections WHERE id=$SECTION_A")"
  check "F11 the author is a real account in the same tenant as the row it wrote" "$SCHOOL_A" \
    "$(sqlv "SELECT u.school_id FROM employees e JOIN users u ON u.id=e.created_by WHERE e.id=$EMPLOYEE_A")"
  check "F11 and the author is not the platform owner reaching in from outside" "$ADMIN_A_UID" \
    "$(sqlv "SELECT coalesce(created_by::text,'') FROM employees WHERE id=$EMPLOYEE_A")"
  check "F11 the write carries a time, not a null" "yes" \
    "$(sqlv "SELECT CASE WHEN created_at IS NOT NULL THEN 'yes' ELSE 'no' END FROM employees WHERE id=$EMPLOYEE_A")"

  if [[ -n "${TEACHER_UID:-}" ]]; then
    check "F11 an account created through the API records its author" "$ADMIN_A_UID" \
      "$(sqlv "SELECT coalesce(created_by::text,'') FROM users WHERE id=$TEACHER_UID")"
  else
    skip "F11 that an account created through the API records its author" "GMS_TEACHER_PASSWORD is not set, so no account was created through the API"
  fi

  # Pupils: the "when" half is auditable and real; the "who" half has nowhere to live.
  check "F11 every pupil row this run wrote is stamped with the time it was written" "yes" \
    "$(sqlv "SELECT CASE WHEN count(*) = 0 THEN 'yes' ELSE 'no' END FROM students WHERE school_id=$SCHOOL_A AND created_at IS NULL")"
  check "F11 and optimistic locking is in force on pupils (the version column is populated)" "yes" \
    "$(sqlv "SELECT CASE WHEN count(*) = 0 THEN 'yes' ELSE 'no' END FROM students WHERE school_id=$SCHOOL_A AND version IS NULL")"
  check "F11 a pupil that was updated carries an update time" "yes" \
    "$(sqlv "SELECT CASE WHEN updated_at IS NOT NULL THEN 'yes' ELSE 'no' END FROM students WHERE id=$STUDENT_A2")"
  PUPIL_AUTHOR_COL=$(sqlv "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='students' AND column_name='created_by'")
  if [[ "$PUPIL_AUTHOR_COL" == "1" ]]; then
    check "F11 a pupil row records who wrote it" "$ADMIN_A_UID" \
      "$(sqlv "SELECT coalesce(created_by::text,'') FROM students WHERE id=$STUDENT_A1")"
  else
    skip "F11 that a pupil row records WHO wrote it" "the students table has no created_by column (V1__baseline_current_schema.sql:1036-1052) and Student does not extend BaseAuditEntity, so the 'when' is auditable and the 'who' is not. MVP feature 11 is unmet for pupils by the schema, not by this script."
  fi
  ADMISSION_AUTHOR_COL=$(sqlv "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='student_admissions' AND column_name='created_by'")
  if [[ "$ADMISSION_AUTHOR_COL" == "1" ]]; then
    check "F11 an admission records who applied" "$ADMIN_A_UID" \
      "$(sqlv "SELECT coalesce(created_by::text,'') FROM student_admissions WHERE id=$ADMISSION_A")"
  else
    skip "F11 that an admission records who applied" "student_admissions has no created_by column either (V1__baseline_current_schema.sql:939-964)"
  fi
  CLASSROOM_AUTHOR_COL=$(sqlv "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='classrooms' AND column_name='created_by'")
  [[ "$CLASSROOM_AUTHOR_COL" == "1" ]] \
    && check "F11 a classroom records who created it" "$ADMIN_A_UID" \
         "$(sqlv "SELECT coalesce(created_by::text,'') FROM classrooms WHERE id=$CLASS_A1")" \
    || skip "F11 that a classroom records who created it" "classrooms has no created_by column (V1__baseline_current_schema.sql:121-129)"
else
  skip "F11 which core MVP write paths have an author column at all" "GMS_VERIFY_DB or the container password is not set"
  skip "F11 that a school row records its author and its updater" "GMS_VERIFY_DB or the container password is not set"
  skip "F11 that the provisioned administrator records who provisioned it" "GMS_VERIFY_DB or the container password is not set"
  skip "F11 that an employee row records its author and its updater" "GMS_VERIFY_DB or the container password is not set"
  skip "F11 that a section row records its author" "GMS_VERIFY_DB or the container password is not set"
  skip "F11 that the author is an account in the same tenant as the row it wrote" "GMS_VERIFY_DB or the container password is not set"
  skip "F11 that an account created through the API records its author" "GMS_VERIFY_DB or the container password is not set"
  skip "F11 that a pupil row records the time it was written" "GMS_VERIFY_DB or the container password is not set"
  skip "F11 that a pupil row records WHO wrote it" "GMS_VERIFY_DB or the container password is not set"
  skip "F11 that an admission records who applied" "GMS_VERIFY_DB or the container password is not set"
  skip "F11 that a classroom records who created it" "GMS_VERIFY_DB or the container password is not set"
  echo
  echo "      F11 is UNPROVEN without a database handle. The HTTP layer never serialises an"
  echo "      audit column for these entities, so no amount of API traffic stands in for"
  echo "      this half — which is why the register below reports it as unproven rather"
  echo "      than as a pass with holes in it."
fi

# ─────────────────────────────────────────────────────────────────────────────
echo
echo "=== FEATURE REGISTER ==="
proved=0
failed_features=""
unproven_features=""
f=1
while [ "$f" -le "$TOTAL_FEATURES" ]; do
  real=${F_CHECK[$f]}
  fails=${F_FAIL[$f]}
  skips=${F_SKIP[$f]}
  if [[ "$fails" -gt 0 ]]; then
    verdict="FAILED     ($fails of $real checks failed, $skips skipped)"
    failed_features="$failed_features $f"
  elif [[ "$real" -eq 0 ]]; then
    verdict="UNPROVEN   (not one assertion ran; all $skips questions were skipped)"
    unproven_features="$unproven_features $f"
  else
    verdict="PROVED     ($real checks ran, $skips skipped)"
    proved=$((proved + 1))
  fi
  printf '  %2d  %-47s %s\n' "$f" "${F_TITLE[$f]}" "$verdict"
  f=$((f + 1))
done

echo
printf 'PASSED: %d   FAILED: %d   SKIPPED: %d\n' "$((checks - failures))" "$failures" "$skipped"
printf 'FEATURES PROVED: %d of %d\n' "$proved" "$TOTAL_FEATURES"
[[ -n "$failed_features" ]] && printf 'Features with a failing check:%s\n' "$failed_features"
[[ -n "$unproven_features" ]] && printf 'Features this run could not prove:%s\n' "$unproven_features"
echo
echo "A SKIP means this environment could not ask the question. It is never a pass."
echo "Read the FAIL lines above as the specification they are: the expected value is"
echo "what the MVP requires, the got value is what the build does."
[[ "$failures" -gt 0 ]] && exit 1
exit 0
