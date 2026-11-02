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
#   produce (101,800 students, 509,000 student_fees, 203,600 attendances, 203,600
#   student_exams, 101,800 results, 103,480 users … — 1,840,468 rows in total
#   across the 4 tenant schools) BEFORE it starts the clock, refuses to measure
#   anything if a single expected count is wrong, and cross-checks the API against
#   the DB (search totalElements must equal the roll the SQL says that endpoint is
#   allowed to serve — see §3) so a backend wired to some other database cannot
#   pass either.
#
#   The volume clause is asserted TWICE, because the two halves of it are not the
#   same claim. NFR-01 says "≥50,000 records per school in the primary tables, 2
#   schools" — a per-school floor — while the cross-table total is only this
#   script's own headline, the "100k+ rows in total across the 4 tenant schools"
#   line it has always printed. A sum can clear 50,000 with no single table
#   anywhere near it, and until this card the sum was the only line standing
#   between the repo and a "volume verified" claim: the seed it measured against
#   summed past 125,000 rows while its largest tenant held 2,400 pupils, roughly
#   eight times short of the clause the file was named for. So the per-school
#   check below is the one that names NFR-01, and the total check stays as the
#   separate assertion it has always been.
#
# WHAT THIS SCRIPT DOES *NOT* MEASURE — say it out loud before the roadmap does:
#   * cold-cache behaviour: every run is a warm-cache run; there is no restart
#     or `pg_dropcache` step between probes, so these numbers are the steady
#     state, not the first-hit state;
#   * multi-instance or load-balanced behaviour: one backend process is measured;
#   * connection-pool saturation and concurrent load: sections 1-4 are strictly
#     sequential (one in-flight request at a time, so HikariCP never queues) —
#     section 4b is the concurrent leg, eight clients in flight per endpoint, and
#     its budget is the same NFR-02 pair, unmodified;
#   * p99 with statistical strength (20 samples, see above) and percentiles of
#     the SQL layer (owned by the seed's EXPLAIN probes, not this script).
#
# Prerequisites (in run order — the order matters, see the bootstrap note):
#   * a backend on GMS_BASE_URL (default http://localhost:8080) started against
#     the disposable database gms_perf_seed at V7 — all seven migrations,
#     V1__baseline_current_schema through V7__record_who_wrote_the_pupil, in the
#     version order the seed's own HOW TO RUN lists — AFTER
#     scripts/seed/perf_seed_50k.sql was loaded and `ANALYZE;` was run.
#     The seed TRUNCATEs `users` and `schools`, so the backend must be RESTARTED
#     after the load: SuperAdminBootstrapService re-creates the platform account
#     and binds it to school id 1 via createOrGetSystemSchool()'s findById(1),
#     which after the seed is 'Delhi Public Academy' — the first of the two
#     50,000-pupil volume tenants NFR-01 is about. (V7 is the migration that adds
#     created_by / updated_by to students, classrooms, subjects and
#     student_admissions; a database left at V6 is a schema the current jar's
#     entities no longer describe, and ddl-auto=validate fails before the first
#     probe. The bootstrap account itself lands clean: the seed resyncs every
#     identity sequence in its section 26, so it takes the id above the seeded
#     blocks rather than colliding with one.)
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

# Empty by default, which keeps the container transport. Set it (e.g. 127.0.0.1)
# to reach the same database over TCP instead — see sql().
PG_HOST=${GMS_PG_HOST:-}
PG_PORT=${GMS_PG_PORT:-5432}

# Concurrent-load leg (see section 4b). 8 clients is the smallest number that
# can saturate Spring Boot's default 10-connection HikariCP pool once a request
# holds more than one, and 0 turns the leg off for an operator who only wants
# the historical sequential run.
CONCURRENCY=${GMS_PERF_CONCURRENCY:-8}
CONC_REQUESTS=${GMS_PERF_CONC_REQUESTS:-$((CONCURRENCY * 10))}
CONC_TIMEOUT=${GMS_PERF_CONC_TIMEOUT:-10}

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
  # Two transports, one contract. The container path is the one this harness has
  # always used; the TCP path exists because a measurement taken through
  # `docker exec` is unavailable on any host whose container runtime is down,
  # and the harness's own rule is that no DB handle means no measurement — not
  # a skipped run that still prints PASS lines. GMS_PG_HOST selects the TCP
  # path; leaving it unset keeps every existing invocation on Docker, byte for
  # byte. The password is read from the same named variable either way and is
  # never echoed.
  if [[ -n "$PG_HOST" ]]; then
    PGPASSWORD="${!PG_PASSWORD_ENV}" psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$PERF_DB" \
      -v ON_ERROR_STOP=1 -tAc "$1" 2>&1
  else
    docker exec -e PGPASSWORD="${!PG_PASSWORD_ENV}" "$PG_CONTAINER" psql -U "$PG_USER" -d "$PERF_DB" \
      -v ON_ERROR_STOP=1 -tAc "$1" 2>&1
  fi
}

# ── 1. Prove we are standing on the right database ───────────────────────────
echo "=== 1. Database provenance ==="
CURRENT_DB=$(sql "SELECT current_database()" | tr -d '[:space:]')
check "psql is talking to the perf database" "$PERF_DB" "$CURRENT_DB"
[[ "$CURRENT_DB" == "$PERF_DB" ]] || { echo "FATAL: refusing to measure database '$CURRENT_DB'"; exit 1; }

# ── 2. Assert the seed landed, per table, BEFORE any clock starts ────────────
# The expectations are read off perf_seed_50k.sql itself (its SHAPE header and
# the arithmetic of each section), and section 28 of the seed independently
# pins students / student_fees / attendances / users and the per-school floor
# below. fee_payments and receipts are derived rows (a WHERE clause over seeded
# data), so their expectation is the same WHERE clause re-evaluated, and the two
# must agree. Everything except the four fixed-per-school tables is a function of
# the roll N = 101,800: 5N student_fees, 2N attendances, 2N student_exams, N
# results, N invoices, N + 1,680 users. N is 101,800 and it is a multiple of 10,
# which is what makes the derived halves exact (see the seed's DETERMINISM note).
echo
echo "=== 2. Seeded row counts (NFR-01's >= 50,000 PER SCHOOL is decided here) ==="
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
-- users and user_roles are counted over the seeded block, not over the whole table.
-- This run REQUIRES a backend restarted after the load (see the header), and that boot
-- is not inert: SuperAdminBootstrapService inserts one platform account plus its
-- SUPERADMIN role row, at an id above every seeded block because the seed's section 26
-- resyncs the identity sequences. So the table holds 103,481 while the seed wrote
-- 103,480, and the extra row is a precondition of the harness rather than a row the seed
-- owes. The predicate below excludes exactly it — the seed writes no SUPERADMIN (its
-- section 24 role split emits STUDENT / PARENT / TEACHER / ADMIN / ACCOUNTANT), so this
-- is a named exclusion, not a tolerance — and section 3 asserts the account is present
-- and bound to school 1, which is the claim that makes the school-scoped probes
-- measurable at all.
SELECT 'users|' || count(*) FROM users u
  WHERE NOT EXISTS (SELECT 1 FROM user_roles r WHERE r.user_id = u.id AND r.role = 'SUPERADMIN') UNION ALL
SELECT 'user_roles|' || count(*) FROM user_roles WHERE role <> 'SUPERADMIN' UNION ALL
SELECT 'notifications|' || count(*) FROM notifications UNION ALL
SELECT 'announcements|' || count(*) FROM announcements")
[[ "$COUNTS" == *"ERROR"* || -z "$COUNTS" ]] && { echo "FATAL: count probe failed: $COUNTS"; exit 1; }

# The contract of perf_seed_50k.sql at N = 101,800 pupils. Eight of the 27 counted
# tables moved when the two volume tenants went from 2,400 and 1,800 pupils to 50,000
# each; seventeen did not, because they are fixed per school (4 schools, 8
# academic_years, 24 classrooms, 12 sections, 24 subjects, 180 employees, 1,500
# parents, 3,000 parent links, 48 timetables, 288 teacher_assignments, 1,440
# timetable_slots, 24 exams, 144 exam_subjects, 144 fees, 48 fee_structures, 4,000
# notifications, 4,000 announcements). The eight that moved, with the arithmetic that
# produces them:
#   students        N                = 101,800
#   student_fees    5 per pupil      = 509,000
#   attendances     2 per pupil      = 203,600
#   student_exams   2 per pupil      = 203,600
#   results         half of those (the even row_number ids)     = 101,800
#   invoices        one per pupil    = 101,800
#   users           180 employees + N pupils + 1,500 parents    = 103,480
#   user_roles      one row per user                            = 103,480
# fee_payments and receipts stay derived — they are a WHERE clause over seeded rows and
# are re-checked as such below, which is why they answer "" here.
expected_count() { # bash-3.2-safe lookup: the contract of perf_seed_50k.sql
  case "$1" in
    schools) echo 4 ;;              academic_years) echo 8 ;;
    classrooms) echo 24 ;;          sections) echo 12 ;;
    subjects) echo 24 ;;            employees) echo 180 ;;
    parents) echo 1500 ;;           students) echo 101800 ;;
    parent_student) echo 3000 ;;    timetables) echo 48 ;;
    teacher_assignments) echo 288 ;; timetable_slots) echo 1440 ;;
    attendances) echo 203600 ;;     exams) echo 24 ;;
    exam_subjects) echo 144 ;;      student_exams) echo 203600 ;;
    results) echo 101800 ;;         fees) echo 144 ;;
    fee_structures) echo 48 ;;      student_fees) echo 509000 ;;
    invoices) echo 101800 ;;        users) echo 103480 ;;
    user_roles) echo 103480 ;;      notifications) echo 4000 ;;
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

# The cross-table total, stated once so this script's own "100k+ rows in total" headline
# has a named test. It is NOT the NFR-01 clause and must not be read as one: a sum can
# clear 50,000 with no single table anywhere near it, which is precisely what the
# 125,000-row seed did while this line passed. The clause itself is asserted PER SCHOOL
# a few lines below, where the roll is counted tenant by tenant.
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
S2_STUDENTS=$(sql "SELECT count(*) FROM students WHERE school_id = 2" | tr -d '[:space:]')
check "school 1 carries its share of the roll (the first 50,000-pupil volume tenant)" "50000" "$S1_STUDENTS"
check "school 2 carries its share of the roll (the second 50,000-pupil volume tenant)" "50000" "$S2_STUDENTS"

# NFR-01 in its own words, so the clause has a line that says the clause.
# PRODUCT_REQUIREMENTS.md:182 reads "≥50,000 records per school in the primary tables,
# 2 schools", and its measurement column closes with "(ACD-08 makes this per-school)" —
# the clause's own text, not a reading of it. docs/ENGINEERING_ROADMAP.md's DoD-2 runs
# the latency budgets "on ≥50,000 records per school". `students` is the primary table
# and schools 1 and 2 are the two tenants the seed sizes for it, so the test is a floor
# on EACH tenant's own count. The cross-table sum above deliberately does not stand in
# for it: the shape this card replaced cleared that sum with 2,400 pupils in its largest
# tenant, roughly eight times short of the clause the seed file is named after.
check "NFR-01: >= 50,000 students PER SCHOOL in both volume tenants (schools 1 and 2)" "yes" \
  "$([[ "$S1_STUDENTS" -ge 50000 && "$S2_STUDENTS" -ge 50000 ]] \
       && echo yes || echo "no (school 1 = $S1_STUDENTS, school 2 = $S2_STUDENTS)")"

STUDENT_ID=$(sql "SELECT id FROM students WHERE school_id = 1 ORDER BY id LIMIT 1" | tr -d '[:space:]')
CLASSROOM_ID=$(sql "SELECT id FROM classrooms WHERE school_id = 1 ORDER BY id LIMIT 1" | tr -d '[:space:]')

# ── 3. Log in, and cross-check API against DB ────────────────────────────────
echo
echo "=== 3. Platform login and API-vs-DB cross-check ==="
code=$(call POST /auth/login "" "{\"username\":\"$SUPERUSER\",\"password\":\"$SUPERPASSWORD\"}")
check "superadmin login accepted" "200" "$code"
PLATFORM_TOKEN=$(field '.accessToken')
[[ -z "$PLATFORM_TOKEN" ]] && { echo "FATAL: no platform token — is the backend running against $PERF_DB and RESTARTED after the seed (it TRUNCATEs users)?"; exit 1; }

# The other half of that restart: the bootstrap account the header says the school-scoped
# probes depend on. Counted above as excluded from the seeded block, so it is asserted
# here as present — otherwise section 2's exclusion would be hiding a missing account,
# and a run whose platform user is bound to the wrong tenant measures the wrong school.
BOOTSTRAP=$(sql "SELECT u.username || '|' || COALESCE(u.school_id::text,'NULL') FROM users u JOIN user_roles r ON r.user_id = u.id WHERE r.role = 'SUPERADMIN' ORDER BY u.id LIMIT 1" | tr -d '[:space:]')
check "the bootstrap platform account exists and is bound to school 1" "$SUPERUSER|1" "$BOOTSTRAP"

# totalElements is the seam between the two halves: if the API cannot see what the SQL
# counted, every latency number below belongs to some other database and this run is
# void. The roll the API can see is NOT the 101,800 the SQL counted, and that is the
# endpoint's own contract, not a gap in the seed: StudentRepository.findStudents puts
# `and s.status <> '0'` in both the page and the count query (so search agrees with
# GET /students, which filters the same way), and the seed deactivates 5,357 pupils with
# id % 19 = 18. 101,800 - 5,357 = 96,443 is therefore the number the API owes, and both
# halves of it are asserted here: the SQL side against the pinned figure, the API side
# against the SQL's own re-evaluation of that predicate — the same mechanism the derived
# fee counts use above. Neither a shrunken seed nor a backend pointed at another
# database can clear both.
ACTIVE_STUDENTS=$(sql "SELECT count(*) FROM students WHERE status <> '0'" | tr -d '[:space:]')
check "the seeded roll less its deactivated pupils is 96,443 (what search may serve)" "96443" "$ACTIVE_STUDENTS"
code=$(call GET "/students/search?page=0&size=1" "$PLATFORM_TOKEN")
TOTAL_ELEMENTS=$(field '.totalElements')
check "the API sees exactly the seeded active roll (search totalElements)" "$ACTIVE_STUDENTS" "$TOTAL_ELEMENTS"

# Server-side cap (StudentServiceImpl.MAX_SEARCH_SIZE = 100) — behaviour probe,
# not a latency probe: ask for 500, get the capped page.
code=$(call GET "/students/search?page=0&size=500" "$PLATFORM_TOKEN")
CAPPED=$(jq -r '.content | length' "$body")
check "GET /students/search caps size server-side at MAX_SEARCH_SIZE" "100" "$CAPPED"

# The same ceiling on the list endpoint that had none. This is the NFR-04 half of the
# fix, asserted as behaviour rather than trusted from the code: ask for 500 pupils of a
# 50,000-pupil tenant and the response must be 100 rows, not the roll. Before the
# ceiling landed this call serialised all 47,369 active pupils of school 1 and measured
# p95 378.5 ms on one run and 299.5 ms on the next — the same endpoint straddling the
# 300 ms budget on run conditions, which is why the fix is a ceiling and not a re-run.
code=$(call GET "/students?size=500" "$PLATFORM_TOKEN")
LIST_ROWS=$(jq -r 'length' "$body")
check "GET /students caps its list server-side at the search ceiling" "100" "$LIST_ROWS"
# A capped read is only honest if it returns the newest pupils rather than an arbitrary
# prefix; the dashboard's "recent admissions" panel depends on that ordering.
LIST_ORDER=$(jq -r '[.[].id] | (if . == (sort | reverse) then "desc" else "not-desc" end)' "$body")
check "GET /students returns the capped page newest-first" "desc" "$LIST_ORDER"

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
#  selective: 'student52000@' matches exactly one seeded row. The seed's email format
#             is 'student' || student.id || '@pupil.school' || school_id || '.example',
#             so 'student52000@' is pupil 52,000's address and nothing else's: the '@'
#             tail rules out the same digits in a first_name ('Student52000') or an
#             admission number ('STD020052000'), and no email can contain it as a prefix,
#             because ids stop at six digits (the roll ends at 101,800) — findStudents
#             does a lower-LIKE '%…%' over first/last/studentId/email, so the term has to
#             be judged against all four. Pupil 52,000 is in school 2 — the second volume
#             tenant, which the old 'student4173@' term never reached — and is one of the
#             96,443 active rows search may serve (52,000 % 19 = 16, not 18). One match
#             out of 96,443 is the selective end of the range; the term would stop being
#             selective if the seed's email format ever lost the '@' or the digits moved.
#  non-selective: 'er' is inside every last_name 'VermaNN' and every student email, so
#             all 96,443 servable rows match — the page must still come back in budget
#             while the count scan covers the whole 101,800-row table;
#  classId filter: school 1's first classroom, 8,334 pupils of which search may serve
#             7,896 (the grade-aligned block is ((n-1)*6)/50000, so the six classrooms of
#             a 50,000-pupil tenant hold 8,334, 8,333, 8,333, 8,334, 8,333, 8,333);
#  deep page: page=4000 at size 20 is OFFSET 80,000 over the 96,443-row roll search
#             serves, i.e. page 4,000 of 4,823 — 83% of the way in, comfortably short of
#             the last page, and still inside the data, so the response carries twenty
#             real rows. That is what makes the probe deep rather than merely far: the
#             sort and the OFFSET 80,000 walk are both paid for a page the caller can
#             actually use, and the count query runs again as it does on every page.
#             It replaces page=1000, which was written when the roll was 6,000 rows: at
#             5,685 servable rows the last page was index 284, so that request sat PAST
#             THE END and returned empty content — the line labelled "deep" had been
#             measuring the cost of an out-of-range page. The offset walk was real, the
#             pagination was not, and no budget proven that way is one this repo can
#             quote. Re-run the arithmetic whenever the roll changes: the page index must
#             stay under totalPages = ceil(servable rows / size), and page*size + size
#             must fit inside the servable roll or the page comes back short or empty.
#  max size: the server-side cap of 100 rows (StudentServiceImpl.MAX_SEARCH_SIZE);
#  GET /students: bounded now, and the reason it is bounded is this script's own first
#             measurement. StudentController.getAll used to take NO paging params and
#             handed back the caller's whole school — for the superadmin, bound to
#             school 1, that was all 47,369 active pupils of a 50,000-pupil tenant
#             (a 2,274-row read at the old seed size). Measured twice on identical data
#             it came back p95 378.5 ms and then 299.5 ms: over NFR-02's budget on one
#             run, under it on the next, straddling the line on JVM and page-cache state.
#             A number that flips with the weather is not a pass, so the endpoint got the
#             ceiling NFR-04 already asks for and these two lines measure it: the default
#             page, and a request for 500 that the server must refuse down to 100. The
#             budget was not adjusted to make the old shape fit.
#  by-id read: student $STUDENT_ID, inside the caller's school, so it is 200 and
#             not the 403 the cross-tenant read would answer.
measure 'search?query=<selective>'   "/students/search?query=student52000@&page=0&size=20" "$PLATFORM_TOKEN"
measure 'search?query=<matches-all>' "/students/search?query=er&size=20"                  "$PLATFORM_TOKEN"
measure 'search?classId=<filter>'    "/students/search?classId=${CLASSROOM_ID}&page=0&size=20" "$PLATFORM_TOKEN"
measure 'search?page=4000 (deep)'    "/students/search?page=4000&size=20"                 "$PLATFORM_TOKEN"
measure 'search?size=100 (max)'      "/students/search?page=0&size=100"                   "$PLATFORM_TOKEN"
measure 'GET /students (default)'    "/students"                                          "$PLATFORM_TOKEN"
measure 'GET /students (capped max)' "/students?size=500"                                 "$PLATFORM_TOKEN"
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

# ── 4b. Concurrent load ──────────────────────────────────────────────────────
# WHY THIS SECTION EXISTS. The header's own honesty list says this harness
# measures "strictly sequential (one in-flight request at a time), so HikariCP
# never queues". That is a true statement about the section above and a gap in
# the clause DoD-2 is really about: a SaaS portal does not serve one user at a
# time, and an endpoint can clear p95 alone and still fall over when ten of it
# run together — which is where a connection pool, a servlet thread pool and a
# per-request row count all become visible at once. Sequential latency says the
# query is fast; concurrent latency says the system is fast. Only the second one
# is what a school experiences.
#
# THE BUDGET IS NOT MOVED. NFR-02 says "p95 < 300 ms and p99 < 800 ms for the
# ten hottest endpoints at volume". It says at volume; it does not say one
# request at a time. So the same two numbers are applied here unmodified. What
# is added is not a stricter budget but a stricter condition: every request must
# come back 2xx, and a request that never completes within --max-time counts as
# a failure rather than as a slow sample, because a timeout is how saturation
# actually presents to a user.
#
# WHAT IS ALSO RECORDED, because p95 alone will not say *why* it moved:
#   * throughput (requests/second) over the whole closed set, so a rising p95
#     with flat throughput is a different story from a rising p95 with rising
#     latency and a queue;
#   * the peak number of ACTIVE backends this database saw during the run,
#     sampled from pg_stat_activity while the load was in flight. A pool ceiling
#     shows up as that count plateauing while client-side latency keeps climbing
#     — it is the difference between "the database is slow" and "the database was
#     never asked", and nothing in the sequential half can tell them apart.
#     The sampler holds one connection of its own, so read every peak as +1.
echo
if [[ "$CONCURRENCY" -le 0 ]]; then
  skip 'concurrent load (section 4b)' 'GMS_PERF_CONCURRENCY=0 — the leg was turned off for this run'
elif ! command -v xargs >/dev/null 2>&1 || ! xargs -P 1 -I @@X@@ true @@X@@ 2>/dev/null; then
  skip 'concurrent load (section 4b)' 'this platform has no xargs -P/-I parallel form'
elif ! command -v python3 >/dev/null 2>&1; then
  skip 'concurrent load (section 4b)' 'python3 is needed for a wall clock with sub-second resolution'
else
  echo "=== 4b. Concurrent load — $CONCURRENCY clients in flight, $CONC_REQUESTS requests per endpoint ==="
  cfile=$(mktemp); cflag=$(mktemp); csample=$(mktemp)
  trap 'rm -f "$body" "$results" "$cfile" "$cflag" "$csample"' EXIT

  conc_measure() { # conc_measure <label> <route> <token>
    local label=$1 route=$2 token=$3
    local url wall start end ok peak thr stats mn p50 p95 mx verdict codes
    : > "$cfile"; : > "$csample"; : > "$cflag"

    ( while [[ -e "$cflag" ]]; do
        sql "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND state = 'active'" \
          >> "$csample" 2>/dev/null
        sleep 0.1
      done ) &
    local sampler=$!

    start=$(python3 -c 'import time; print("%.3f" % time.time())')
    for i in $(seq 1 "$CONC_REQUESTS"); do printf '%s%s\n' "$API" "$route"; done \
      | xargs -P "$CONCURRENCY" -I @@GMSURL@@ curl -s -o /dev/null --max-time "$CONC_TIMEOUT" \
              -w '%{time_total} %{http_code}\n' -H "Authorization: Bearer $token" "@@GMSURL@@" >> "$cfile" 2>&1
    end=$(python3 -c 'import time; print("%.3f" % time.time())')

    rm -f "$cflag"; wait "$sampler" 2>/dev/null

    wall=$(awk -v a="$start" -v b="$end" 'BEGIN{ d = b - a; if (d <= 0) d = 0.001; printf "%.3f", d }')
    ok=$(awk '$2 ~ /^2/ {c++} END{print c+0}' "$cfile")
    peak=$(sort -n "$csample" | tail -1)
    [[ -n "$peak" ]] || peak=0
    thr=$(awk -v n="$CONC_REQUESTS" -v w="$wall" 'BEGIN{printf "%.1f", n / w}')

    stats=$(cut -d' ' -f1 "$cfile" | sort -n | awk '
      function ceil(x) { return (x == int(x)) ? x : int(x) + 1 }
      { v[NR] = $1 + 0 }
      END {
        if (NR == 0) { print "0 0 0 0"; exit }
        printf "%.1f %.1f %.1f %.1f", v[1]*1000, v[ceil(0.50*NR)]*1000, v[ceil(0.95*NR)]*1000, v[NR]*1000
      }')
    mn=${stats%% *}; local rest=${stats#* }; p50=${rest%% *}; rest=${rest#* }
    p95=${rest%% *}; mx=${rest#* }
    codes=$(cut -d' ' -f2 "$cfile" | sort | uniq -c | tr '\n' ' ' | sed 's/^ *//')

    verdict=ok
    [[ "$ok" == "$CONC_REQUESTS" ]] || verdict="NOT-2XX($ok/$CONC_REQUESTS)"
    if [[ "$verdict" == ok ]]; then
      verdict=$(awk -v p95="$p95" -v mx="$mx" -v b95="$BUDGET_P95" -v b99="$BUDGET_P99" \
        'BEGIN{ if (p95+0 >= b95+0) print "P95-OVER"; else if (mx+0 >= b99+0) print "P99-OVER"; else print "ok" }')
    fi
    check "$label: $CONC_REQUESTS requests at $CONCURRENCY-wide — all 2xx, p95 < ${BUDGET_P95}ms, worst < ${BUDGET_P99}ms" \
      "ok" "$verdict"
    printf '%-28s %3d %6d %8s %8s %8s %8s %8s %7s   %-16s %s\n' \
      "$label" "$CONCURRENCY" "$ok" "$mn" "$p50" "$p95" "$mx" "${thr}/s" "peak=${peak}" "$codes" >> "$conc_results"
  }

  conc_results=$(mktemp)
  conc_measure 'search?query=<selective>'   "/students/search?query=student52000@&page=0&size=20" "$PLATFORM_TOKEN"
  conc_measure 'search?query=<matches-all>' "/students/search?query=er&size=20"                   "$PLATFORM_TOKEN"
  conc_measure 'search?page=4000 (deep)'    "/students/search?page=4000&size=20"                 "$PLATFORM_TOKEN"
  conc_measure 'GET /students (default)'    "/students"                                           "$PLATFORM_TOKEN"
  conc_measure 'GET /students/{id}'         "/students/${STUDENT_ID}"                             "$PLATFORM_TOKEN"
  conc_measure 'GET /dashboard/school'      "/dashboard/school"                                   "$PLATFORM_TOKEN"

  echo
  printf '%-28s %3s %6s %8s %8s %8s %8s %8s %7s   %s\n' \
    'CONCURRENCY' 'W' 'ok' 'min' 'p50' 'p95' 'max' 'rps' 'dbpeak' 'codes'
  cat "$conc_results"
  rm -f "$cfile" "$cflag" "$csample" "$conc_results"
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
echo "  cold cache, multiple instances. Concurrent load is measured — see section 4b above."
echo
echo "What this run decides about volume, stated at the size of the claim: NFR-01's floor"
echo "is met PER SCHOOL (schools 1 and 2 hold 50,000 pupils each, asserted above), the"
echo "deep-pagination line lands inside the roll instead of past its end, and the"
echo "API-visible roll is asserted on its own terms — 96,443, because findStudents"
echo "excludes the seed's 5,357 deactivated pupils — never by comparing it to the"
echo "101,800 the SQL counted. It also decides GET /students: measured unbounded it ran"
echo "47,369 pupils at p95 378.5 ms (over NFR-02's 300 ms) and then 299.5 ms (under it)"
echo "on identical data, so the endpoint as written could not be certified either way."
echo "It now has the ceiling NFR-04 asks for, and the two lines above — the row count and"
echo "the newest-first order — are what makes the capped read an honest answer rather"
echo "than a truncated one."
echo

echo "==================================================="
printf 'PASSED: %d   FAILED: %d   SKIPPED: %d\n' "$((checks - failures))" "$failures" "$skipped"
[[ $failures -eq 0 ]] || exit 1
