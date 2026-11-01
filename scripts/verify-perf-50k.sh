#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Stage 5 measurement harness — "the performance claims are measurements".
#
#   ./scripts/verify-perf-50k.sh
#
# The performance clause this card must satisfy is DoD-2 in
# docs/ENGINEERING_ROADMAP.md: "The agreed performance tests pass — the published
# budget in PRODUCT_REQUIREMENTS.md §9 met on ≥50,000 records … with measured
# endpoint latency", and Stage 5's acceptance criterion 1: "DoD-2 budgets met,
# with EXPLAIN (ANALYZE, BUFFERS) output and latency percentiles recorded per
# query — p50/p95/p99 for the ten hottest endpoints, not a single average."
# This script supplies the endpoint-latency half of that criterion against the
# running API; the seed file's footer owns the EXPLAIN half.
#
# THE BUDGETS ARE NOT INVENTED HERE. They are quoted from PRODUCT_REQUIREMENTS.md
# §9, NFR-02: "API latency | p95 < 300 ms and p99 < 800 ms for the ten hottest
# endpoints at volume". So BUDGET_P95=300, BUDGET_P99=800. Nothing stricter is
# asserted, on purpose — the roadmap's rule is to meet the published budget, not
# a hero number. (NFR-03's "p95 < 100 ms per statement" is a per-SQL budget
# measured by EXPLAIN, not per HTTP call; conflating the two would silently
# move the goalposts, so this script does not use 100 ms anywhere.)
# At RUNS=20 samples the p99 estimator is weak, so the p99 budget is checked
# against the worst run (max), which is the nearest-rank p99 at that N, and the
# table labels it as such. That is a stated limitation, not a downgrade.
#
# WHY THE COUNT ASSERTION COMES FIRST, AND WHY IT CAN KILL THE RUN
#   This repo has been bitten twice by the same failure mode: a green latency
#   number measured over an empty (or half-seeded) table. An empty `students`
#   table answers every search in 5 ms and "passes" — the measurement would then
#   certify performance that was never performed. So this script asserts the
#   exact per-table counts that scripts/seed/perf_seed_50k.sql is designed to
#   produce (6,000 students, 30,000 student_fees, 12,000 attendances, 12,000
#   student_exams, 6,000 results, 7,680 users … — 100k+ rows in total across the
#   4 tenant schools) BEFORE it starts the clock, refuses to measure anything if
#   a single expected count is wrong, and cross-checks the API against the DB
#   (search totalElements must equal the SQL count) so a backend wired to some
#   other database cannot pass either.
#
# WHAT THIS SCRIPT DOES *NOT* MEASURE — say it out loud before the roadmap does:
#   * cold-cache behaviour: every run is a warm-cache run; there is no restart
#     or `pg_dropcache` step between probes, so these numbers are the steady
#     state, not the first-hit state;
#   * multi-instance or load-balanced behaviour: one backend process is measured;
#   * connection-pool saturation: the calls are strictly sequential (one
#     in-flight request at a time), so HikariCP never queues — concurrent-load
#     behaviour is a Stage 6 question and no number here speaks to it;
#   * p99 with statistical strength (20 samples, see above) and percentiles of
#     the SQL layer (owned by the seed's EXPLAIN probes, not this script).
#
# Prerequisites (in run order — the order matters, see the bootstrap note):
#   * a backend on GMS_BASE_URL (default http://localhost:8080) started against
#     the disposable database gms_perf_seed at V6, AFTER
#     scripts/seed/perf_seed_50k.sql was loaded and `ANALYZE;` was run.
#     The seed TRUNCATEs `users` and `schools`, so the backend must be RESTARTED
#     after the load: SuperAdminBootstrapService re-creates the platform account
#     and binds it to school id 1 via createOrGetSystemSchool()'s findById(1),
#     which after the seed is 'Delhi Public Academy' (the 2,400-student tenant).
#     That binding is what makes GET /students, GET /students/{id} and
#     GET /dashboard/school (which all 401 without a school claim) measurable
#     with the superadmin token, against real volume;
#   * GMS_PERF_DB + the PostgreSQL container reachable through docker, exactly as
#     in verify-c7-tenant-key.sh (`docker exec -e PGPASSWORD=… gms-postgres psql
#     -U gms_user -d <db>`). Unlike that script, this one treats DB access as
#     mandatory and refuses to run without it — an unmeasured provenance is not
#     a measurement. The password is never echoed.
#   * curl and jq.
#
# The only raw SQL here is SELECT. It mutates nothing.
#
# Credentials come from the environment; nothing here hardcodes one.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

BASE=${GMS_BASE_URL:-http://localhost:8080}
API="$BASE/api/v1"
SUPERUSER=${GMS_SUPERADMIN_USERNAME:?set GMS_SUPERADMIN_USERNAME}
SUPERPASSWORD=${GMS_SUPERADMIN_PASSWORD:?set GMS_SUPERADMIN_PASSWORD}

# Optional: a real school ADMIN token. The seed's users carry placeholder bcrypt
# hashes (the file says so), so a seeded admin cannot log in unless the operator
# has fixed one password out-of-band. When the pair is supplied, the tenant-
# scoped variants of two probes are measured; otherwise they report SKIP, never
# PASS — the same rule verify-c7 uses for its row-level checks.
ADMIN_USER=${GMS_SCHOOL_ADMIN_USERNAME:-}
ADMIN_PASSWORD=${GMS_SCHOOL_ADMIN_PASSWORD:-}

PERF_DB=${GMS_PERF_DB:-gms_perf_seed}
PG_CONTAINER=${GMS_PG_CONTAINER:-gms-postgres}
PG_USER=${GMS_PG_USER:-gms_user}
PG_PASSWORD_ENV=${GMS_PG_PASSWORD_ENV:-DB_PASSWORD}
RUNS=${GMS_PERF_RUNS:-20}

# The published budgets (PRD §9 NFR-02), quoted above. Not tuned per endpoint.
BUDGET_P95=300
BUDGET_P99=800

failures=0
checks=0
skipped=0
body=$(mktemp)
results=$(mktemp)
trap 'rm -f "$body" "$results"' EXIT

[[ "$RUNS" =~ ^[0-9]+$ && "$RUNS" -ge 5 ]] || { echo "FATAL: GMS_PERF_RUNS must be an integer >= 5"; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "FATAL: jq is required"; exit 1; }
[[ -n "${!PG_PASSWORD_ENV:-}" ]] || { echo "FATAL: \$$PG_PASSWORD_ENV must be set — without the database the seed cannot be proven, and an empty table would pass every latency line. See the header."; exit 1; }

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

sql() { # sql <statement> — SELECT only, per the header
  docker exec -e PGPASSWORD="${!PG_PASSWORD_ENV}" "$PG_CONTAINER" psql -U "$PG_USER" -d "$PERF_DB" \
    -v ON_ERROR_STOP=1 -tAc "$1" 2>&1
}

# ── 1. Prove we are standing on the right database ───────────────────────────
echo "=== 1. Database provenance ==="
CURRENT_DB=$(sql "SELECT current_database()" | tr -d '[:space:]')
check "psql is talking to the perf database" "$PERF_DB" "$CURRENT_DB"
[[ "$CURRENT_DB" == "$PERF_DB" ]] || { echo "FATAL: refusing to measure database '$CURRENT_DB'"; exit 1; }

# ── 2. Assert the seed landed, per table, BEFORE any clock starts ────────────
# The expectations are read off perf_seed_50k.sql itself (its SHAPE header and
# the arithmetic of each section), and section 28 of the seed independently
# pins students/student_fees/attendances. fee_payments and receipts are derived
# rows (a WHERE clause over seeded data), so their expectation is the same WHERE
# clause re-evaluated, and the two must agree.
echo
echo "=== 2. Seeded row counts (the >= 50,000 clause is decided here) ==="
COUNTS=$(sql "
SELECT 'schools|' || count(*) FROM schools UNION ALL
SELECT 'academic_years|' || count(*) FROM academic_years UNION ALL
SELECT 'classrooms|' || count(*) FROM classrooms UNION ALL
SELECT 'sections|' || count(*) FROM sections UNION ALL
SELECT 'subjects|' || count(*) FROM subjects UNION ALL
SELECT 'employees|' || count(*) FROM employees UNION ALL
SELECT 'parents|' || count(*) FROM parents UNION ALL
SELECT 'students|' || count(*) FROM students UNION ALL
SELECT 'parent_student|' || count(*) FROM parent_student UNION ALL
SELECT 'timetables|' || count(*) FROM timetables UNION ALL
SELECT 'teacher_assignments|' || count(*) FROM teacher_assignments UNION ALL
SELECT 'timetable_slots|' || count(*) FROM timetable_slots UNION ALL
SELECT 'attendances|' || count(*) FROM attendances UNION ALL
SELECT 'exams|' || count(*) FROM exams UNION ALL
SELECT 'exam_subjects|' || count(*) FROM exam_subjects UNION ALL
SELECT 'student_exams|' || count(*) FROM student_exams UNION ALL
SELECT 'results|' || count(*) FROM results UNION ALL
SELECT 'fees|' || count(*) FROM fees UNION ALL
SELECT 'fee_structures|' || count(*) FROM fee_structures UNION ALL
SELECT 'student_fees|' || count(*) FROM student_fees UNION ALL
SELECT 'fee_payments|' || count(*) FROM fee_payments UNION ALL
SELECT 'invoices|' || count(*) FROM invoices UNION ALL
SELECT 'receipts|' || count(*) FROM receipts UNION ALL
SELECT 'users|' || count(*) FROM users UNION ALL
SELECT 'user_roles|' || count(*) FROM user_roles UNION ALL
SELECT 'notifications|' || count(*) FROM notifications UNION ALL
SELECT 'announcements|' || count(*) FROM announcements")
[[ "$COUNTS" == *"ERROR"* || -z "$COUNTS" ]] && { echo "FATAL: count probe failed: $COUNTS"; exit 1; }

expected_count() { # bash-3.2-safe lookup: the contract of perf_seed_50k.sql
  case "$1" in
    schools) echo 4 ;;              academic_years) echo 8 ;;
    classrooms) echo 24 ;;          sections) echo 12 ;;
    subjects) echo 24 ;;            employees) echo 180 ;;
    parents) echo 1500 ;;           students) echo 6000 ;;
    parent_student) echo 3000 ;;    timetables) echo 48 ;;
    teacher_assignments) echo 288 ;; timetable_slots) echo 1440 ;;
    attendances) echo 12000 ;;      exams) echo 24 ;;
    exam_subjects) echo 144 ;;      student_exams) echo 12000 ;;
    results) echo 6000 ;;           fees) echo 144 ;;
    fee_structures) echo 48 ;;      student_fees) echo 30000 ;;
    invoices) echo 6000 ;;          users) echo 7680 ;;
    user_roles) echo 7680 ;;        notifications) echo 4000 ;;
    announcements) echo 4000 ;;     fee_payments) echo "" ;;  # derived, checked separately
    receipts) echo "" ;;            # derived, checked separately
    *) echo "?" ;;
  esac
}

total=0
while IFS='|' read -r tbl rows; do
  [[ -z "$tbl" ]] && continue
  rows=$(printf '%s' "$rows" | tr -d '[:space:]')
  total=$((total + rows))
  want=$(expected_count "$tbl")
  if [[ -z "$want" ]]; then
    printf '      %-20s %8s rows (derived count, checked below)\n' "$tbl" "$rows"
  else
    check "seed row count: $tbl" "$want" "$rows"
    printf '      %-20s %8s rows\n' "$tbl" "$rows"
  fi
done <<EOF
$COUNTS
EOF

# A >= 50,000 gate, stated once so the DoD-2 clause has a named test — even
# though the per-table assertions above already imply it.
check "the seed holds at least 50,000 rows in total" "yes" \
  "$([[ "$total" -ge 50000 ]] && echo yes || echo "no ($total)")"
printf '      TOTAL ROWS MEASURED: %s across %s tables\n' "$total" 27

# The derived pairs: the INSERT's WHERE clause re-evaluated must equal the table.
FP_ROWS=$(sql "SELECT count(*) FROM fee_payments" | tr -d '[:space:]')
FP_WHERE=$(sql "SELECT count(*) FROM student_fees WHERE id % 2 = 1 AND amount_paid > 0" | tr -d '[:space:]')
check "fee_payments equals its seed derivation (odd id, paid > 0)" "$FP_WHERE" "$FP_ROWS"
RC_ROWS=$(sql "SELECT count(*) FROM receipts" | tr -d '[:space:]')
RC_WHERE=$(sql "SELECT count(*) FROM fee_payments WHERE status = 'SUCCESS'" | tr -d '[:space:]')
check "receipts equals its seed derivation (SUCCESS payments)" "$RC_WHERE" "$RC_ROWS"

# The multi-tenant shape the probes depend on (at least two schools, the two
# named ones being the ones the routes below filter on).
TWO_SCHOOLS=$(sql "SELECT count(*) FROM schools WHERE school_name IN ('Delhi Public Academy','Riverside International School')" | tr -d '[:space:]')
check "both probe schools exist (Delhi Public Academy, Riverside)" "2" "$TWO_SCHOOLS"
S1_STUDENTS=$(sql "SELECT count(*) FROM students WHERE school_id = 1" | tr -d '[:space:]')
check "school 1 carries its share of the roll (the 2,400-tenant)" "2400" "$S1_STUDENTS"
STUDENT_ID=$(sql "SELECT id FROM students WHERE school_id = 1 ORDER BY id LIMIT 1" | tr -d '[:space:]')
CLASSROOM_ID=$(sql "SELECT id FROM classrooms WHERE school_id = 1 ORDER BY id LIMIT 1" | tr -d '[:space:]')

# ── 3. Log in, and cross-check API against DB ────────────────────────────────
echo
echo "=== 3. Platform login and API-vs-DB cross-check ==="
code=$(call POST /auth/login "" "{\"username\":\"$SUPERUSER\",\"password\":\"$SUPERPASSWORD\"}")
check "superadmin login accepted" "200" "$code"
PLATFORM_TOKEN=$(field '.accessToken')
[[ -z "$PLATFORM_TOKEN" ]] && { echo "FATAL: no platform token — is the backend running against $PERF_DB and RESTARTED after the seed (it TRUNCATEs users)?"; exit 1; }

# totalElements is the seam between the two halves: the SQL said 6,000 rows; if
# the API cannot see them, every latency number below belongs to some other
# database and this run is void.
code=$(call GET "/students/search?page=0&size=1" "$PLATFORM_TOKEN")
TOTAL_ELEMENTS=$(field '.totalElements')
check "the API sees exactly the seeded roll (search totalElements)" "6000" "$TOTAL_ELEMENTS"

# Server-side cap (StudentServiceImpl.MAX_SEARCH_SIZE = 100) — behaviour probe,
# not a latency probe: ask for 500, get the capped page.
code=$(call GET "/students/search?page=0&size=500" "$PLATFORM_TOKEN")
CAPPED=$(jq -r '.content | length' "$body")
check "GET /students/search caps size server-side at MAX_SEARCH_SIZE" "100" "$CAPPED"

ADMIN_TOKEN=""
if [[ -n "$ADMIN_USER" && -n "$ADMIN_PASSWORD" ]]; then
  code=$(call POST /auth/login "" "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASSWORD\"}")
  if [[ "$code" == "200" ]]; then
    ADMIN_TOKEN=$(field '.accessToken')
  else
    printf '      note: the school admin login answered %s; its probes will report SKIP\n' "$code"
  fi
fi

# ── 4. The measurement harness ───────────────────────────────────────────────
# Percentiles are nearest-rank over the sorted run times (ceil(p*N)); awk does
# the arithmetic — no bc, no bash-4 constructs. Each line is one sequential,
# warm-cache, single-in-flight call (see the header's honesty list).
echo
echo "=== 4. Endpoint latency — $RUNS runs each, sequential, warm ==="

measure() { # measure <label> <route> <token>
  local label=$1 route=$2 token=$3
  local f i resp ms code ok stats mn p50 p95 mx verdict
  f=$(mktemp)
  for i in $(seq 1 "$RUNS"); do
    resp=$(curl -s -o "$body" -w '%{time_total} %{http_code}' -H "Authorization: Bearer $token" "$API$route")
    code=${resp##* }
    ms=$(awk -v t="${resp%% *}" 'BEGIN{printf "%.1f", t*1000}')
    printf '%s %s\n' "$ms" "$code" >> "$f"
  done
  ok=$(awk '$2 ~ /^2/ {c++} END{print c+0}' "$f")
  stats=$(cut -d' ' -f1 "$f" | sort -n | awk '
    function ceil(x) { return (x == int(x)) ? x : int(x) + 1 }
    { v[NR] = $1 + 0 }
    END {
      if (NR == 0) { print "0 0 0 0"; exit }
      printf "%.1f %.1f %.1f %.1f", v[1], v[ceil(0.50*NR)], v[ceil(0.95*NR)], v[NR]
    }')
  mn=${stats%% *}; rest=${stats#* }; p50=${rest%% *}; rest=${rest#* }
  p95=${rest%% *}; mx=${rest#* }

  verdict=ok
  [[ "$ok" == "$RUNS" ]] || verdict="NOT-2XX($ok/$RUNS)"
  if [[ "$verdict" == ok ]]; then
    # The published budgets, applied as-is: p95 < 300ms, and the worst run
    # against the 800ms p99 budget (nearest-rank p99 at N=$RUNS).
    verdict=$(awk -v p95="$p95" -v mx="$mx" -v b95="$BUDGET_P95" -v b99="$BUDGET_P99" \
      'BEGIN{ if (p95+0 >= b95+0) print "P95-OVER"; else if (mx+0 >= b99+0) print "P99-OVER"; else print "ok" }')
  fi
  check "$label: all $RUNS runs 2xx and p95 < ${BUDGET_P95}ms / worst-run(p99 proxy) < ${BUDGET_P99}ms (PRD NFR-02)" \
    "ok" "$verdict"
  printf '%-28s %4d %4d %8s %8s %8s %8s %14s   %s\n' \
    "$label" "$RUNS" "$ok" "$mn" "$p50" "$p95" "$mx" "${BUDGET_P95}ms" "$verdict" >> "$results"
  rm -f "$f"
}

# The routes are the real ones from StudentController and DashboardController
# (all under /api/v1; search params exactly as the @GetMapping declares them:
# query, classId, page, size, sort).
#
#  selective: 'student4173@' matches exactly one seeded email (StudentRepository
#             findStudents does a lower-LIKE %…% over first/last/studentId/email);
#  non-selective: 'er' is inside every last_name 'VermaNN' — all 6,000 rows match,
#             so the page must still come back in budget while the count scan
#             covers the whole roll;
#  classId filter: school 1's first classroom (400 students);
#  deep page: page=1000 at size 20 is past totalPages=300 for this dataset, so it
#             returns empty content — that is still the honest deep-pagination
#             measurement, because PostgreSQL pays the sort + OFFSET 20000 walk
#             and the app runs the count query on every page either way;
#  max size: the server-side cap of 100 rows (StudentServiceImpl.MAX_SEARCH_SIZE);
#  GET /students: StudentController.getAll takes NO paging params — it hands back
#             the caller's whole school (the superadmin is bound to school 1, so
#             this is the 2,400-row read the MVP frontend must not lean on; it is
#             measured exactly as it is written, cap-less, on purpose);
#  by-id read: student $STUDENT_ID, inside the caller's school, so it is 200 and
#             not the 403 the cross-tenant read would answer.
measure 'search?query=<selective>'   "/students/search?query=student4173@&page=0&size=20" "$PLATFORM_TOKEN"
measure 'search?query=<matches-all>' "/students/search?query=er&size=20"                  "$PLATFORM_TOKEN"
measure 'search?classId=<filter>'    "/students/search?classId=${CLASSROOM_ID}&page=0&size=20" "$PLATFORM_TOKEN"
measure 'search?page=1000 (deep)'    "/students/search?page=1000&size=20"                 "$PLATFORM_TOKEN"
measure 'search?size=100 (max)'      "/students/search?page=0&size=100"                   "$PLATFORM_TOKEN"
measure 'GET /students (whole roll)' "/students"                                           "$PLATFORM_TOKEN"
measure 'GET /students/{id}'         "/students/${STUDENT_ID}"                            "$PLATFORM_TOKEN"
measure 'GET /dashboard/school'      "/dashboard/school"                                  "$PLATFORM_TOKEN"
measure 'GET /dashboard/platform'    "/dashboard/platform"                                "$PLATFORM_TOKEN"

if [[ -n "$ADMIN_TOKEN" ]]; then
  measure 'ADMIN search?query=<tenant>' "/students/search?query=er&size=20" "$ADMIN_TOKEN"
  measure 'ADMIN GET /dashboard/school' "/dashboard/school"                 "$ADMIN_TOKEN"
else
  skip 'ADMIN-scoped variants of /students/search and /dashboard/school' \
       'GMS_SCHOOL_ADMIN_USERNAME/PASSWORD not set (seeded password hashes are placeholders)'
fi

# ── 5. The table ─────────────────────────────────────────────────────────────
echo
echo "p95 budget = ${BUDGET_P95}ms, worst-run/p99 budget = ${BUDGET_P99}ms — both quoted from"
echo "PRODUCT_REQUIREMENTS.md §9 NFR-02 (\"p95 < 300 ms and p99 < 800 ms for the ten"
echo "hottest endpoints at volume\"), as required by DoD-2 / Stage 5 criterion 1."
echo
printf '%-28s %4s %4s %8s %8s %8s %8s %14s   %s\n' \
  'endpoint' 'runs' 'ok' 'min(ms)' 'p50(ms)' 'p95(ms)' 'max(ms)' 'p95 budget' 'verdict'
cat "$results"
echo
echo "Not measured here (the roadmap must repeat this, not just the numbers):"
echo "  cold cache, multiple instances, connection-pool saturation, concurrent load."
echo

echo "==================================================="
printf 'PASSED: %d   FAILED: %d   SKIPPED: %d\n' "$((checks - failures))" "$failures" "$skipped"
[[ $failures -eq 0 ]] || exit 1
