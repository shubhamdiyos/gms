#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# ci-live-tenancy.sh — the live-tenancy gate, defined once.
#
#   GMS_JAR=gms_backend/target/gms-0.1.0-SNAPSHOT.jar ./scripts/ci-live-tenancy.sh
#
# What it does: boots the built jar against a throwaway PostgreSQL whose schema
# Flyway brings up from V1 to the last migration, waits for the anonymous readiness
# probe, then runs every live verification suite in the tree against that single
# boot, and refuses the run when any suite fails **or skips**.
#
# Why it is a script and not workflow YAML: the `live-tenancy` job in
# `.github/workflows/ci.yml` and an operator's laptop have to execute the same
# instructions. The same sequence written twice — once in YAML, once in a README —
# is free to drift, and the drift shows up as CI proving something no local run
# reproduces.
#
# Prerequisites, all of them mintable by anyone with the repository:
#   * PostgreSQL reachable at $DB_URL, empty at the start, whose role owns the
#     database. The migration chain runs during boot and `ddl-auto=validate`
#     means a schema mismatch dies here rather than in a later suite.
#   * $GMS_JAR — the Spring Boot jar that `mvn -B clean verify` produced.
#   * a container named $GMS_PG_CONTAINER (default `gms-postgres`) running that
#     same server: verify-c2/c3/c4/c7/p1 and verify-security read the database
#     through `docker exec … psql`, which is how they check columns no HTTP
#     response exposes.
#   * curl, jq, python3, docker on PATH.
#
# Nothing here is a secret. The application boots against a database that exists
# for the length of one run inside a throwaway machine, seeded with invented
# tenants, and is discarded afterwards — the same reasoning that put a literal
# password on the `postgres:` service in ci.yml's `backend` job. A repository
# secret would be worse, not better: an unset secret expands to the empty string,
# PostgreSQL refuses to initialise without a password, and the job dies on a
# missing prerequisite instead of on a failed assertion. That is why this file
# reads no `secrets.*` value at all; the twelve it used to name are gone.
#
# The skip policy is the point of the harness. Every suite in the tree skips an
# assertion when its environment cannot ask the question — no teacher password, no
# readable controller sources, no SQL log — and a skipped assertion reads as a pass
# in a job whose only verdict is an exit code. So the runner counts them, and a count
# above that suite's documented ceiling fails the job. The ceilings are not lenience:
# one suite has seventeen skips that are structural (a by-id route that grants no
# SUPERADMIN cannot be used to ask whether the tenant filter held), and a rule that
# can never be satisfied is a rule that gets switched off. Each number carries its
# reason in the table, and a skip beyond it turns the gate red.
#
# Exit: 0 only when every suite ran, every check passed, and no suite skipped past its ceiling.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

ROOT=${GMS_REPO_ROOT:-.}
BASE_URL=${GMS_BASE_URL:-http://localhost:8080}
JAR=${GMS_JAR:?set GMS_JAR to the path of the jar built by mvn -B clean verify}
BOOT_TIMEOUT=${GMS_BOOT_TIMEOUT_SECONDS:-180}
WORK_DIR=${GMS_CI_WORK_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/gms-live-ci.XXXXXX")}
APP_LOG="$WORK_DIR/backend.log"

DB_URL=${DB_URL:?set DB_URL to the JDBC URL the application boots on}
DB_USERNAME=${DB_USERNAME:?set DB_USERNAME}
DB_PASSWORD=${DB_PASSWORD:?set DB_PASSWORD}
VERIFY_DB=${GMS_VERIFY_DB:?set GMS_VERIFY_DB to the database name behind DB_URL}
PG_CONTAINER=${GMS_PG_CONTAINER:-gms-postgres}
PG_USER=${GMS_PG_USER:-gms_user}
PG_PASSWORD_ENV=${GMS_PG_PASSWORD_ENV:-DB_PASSWORD}
SUPERADMIN_USER=${GMS_SUPERADMIN_USERNAME:?set GMS_SUPERADMIN_USERNAME}
SUPERADMIN_PASSWORD=${GMS_SUPERADMIN_PASSWORD:?set GMS_SUPERADMIN_PASSWORD}

mkdir -p "$WORK_DIR"

# The suites and the application must be pointed at the same database. They reach
# it by different transports — JDBC for the app, `docker exec psql` for the probes —
# so a mismatch is invisible until a suite reads zero rows and reports a pass that
# nothing proved. Checked, not assumed.
DB_IN_URL=$(printf '%s' "$DB_URL" | sed -E 's#\?.*$##; s#.*/##')
if [[ "$DB_IN_URL" != "$VERIFY_DB" ]]; then
  echo "FATAL: DB_URL points at database '$DB_IN_URL' while GMS_VERIFY_DB is '$VERIFY_DB'."
  echo "       The application would migrate one database and the SQL probes would read"
  echo "       another, so every database-backed assertion would pass without evidence."
  exit 1
fi

# The suites are the contract. A file that has been renamed or deleted would leave
# the gate green with a hole in it, so absence is fatal here rather than a step
# GitHub can skip: `jobs.<id>.if` is evaluated before the checkout, which is why the
# disabled stub this replaces had to probe for presence and step over a missing
# suite. Inside this script there is no such constraint, so there is no such lenience.
#
# The fifth field is a skip ceiling, and it exists because a blanket "any skip fails"
# rule would have to be relaxed the first time a suite legitimately could not ask a
# question — and a rule that gets relaxed in practice is a rule that is not read. The
# ceiling is the count that is *documented*, so a new skip fails the gate while the
# old ones do not make the job permanently ungreenable, and each number names the
# reason it is not zero. Zero is the expectation, not the concession.
SUITES=(
  "verify-c1-tenancy|scripts/verify-c1-tenancy.sh|suite|0|"
  "verify-c2-tenant-context|scripts/verify-c2-tenant-context.sh|suite|0|"
  "verify-c3-scoped-reads|scripts/verify-c3-scoped-reads.sh|suite|0|"
  "verify-c4-owned-reads|scripts/verify-c4-owned-reads.sh|suite|17|fifteen by-id routes grant no SUPERADMIN, two resources declare no DELETE (ARCHITECTURE.md:38)"
  "verify-c7-tenant-key|scripts/verify-c7-tenant-key.sh|suite|0|"
  "verify-mvp-e2e|scripts/verify-mvp-e2e.sh|suite|0|"
  "p1-fixtures|scripts/seed/p1_fixtures.sh|fixture|0|"
  "verify-p1-hardening|scripts/verify-p1-hardening.sh|suite|0|"
  "verify-security|scripts/verify-security.sh|suite|0|"
)

# Stated rather than left out, so the gap is in the record and not in a reader's
# memory of which steps were wired.
NOT_RUN=(
  "verify-perf-50k.sh — measures the DoD-2 budgets against the 1.84M-row seed; its own guard refuses a
   half-loaded table, so it needs a load this job would pay for on every pull request. It runs where it
   has always run: the operator's machine, and the numbers are in docs/ENGINEERING_ROADMAP.md Stage 5."
  "verify-schema.sh — creates and drops probe databases (gms_schema_probe*, the gms_v3 negative).
   Destructive by design, so it stays a hand-run operator script; the chain it validates is validated
   in CI by the boot above, which fails if Flyway and ddl-auto=validate disagree."
)

for binary in curl jq python3 docker java; do
  command -v "$binary" >/dev/null 2>&1 || { echo "FATAL: $binary is required on PATH"; exit 1; }
done
[[ -f "$JAR" ]] || { echo "FATAL: no jar at $JAR"; exit 1; }

for record in "${SUITES[@]}"; do
  IFS='|' read -r name path kind ceiling reason <<< "$record"
  [[ "$ceiling" =~ ^[0-9]+$ ]] || { echo "FATAL: $name has no numeric skip ceiling"; exit 1; }
  (( ceiling == 0 )) || [[ -n "$reason" ]] \
    || { echo "FATAL: $name has a nonzero skip ceiling and no reason for it"; exit 1; }
  [[ -f "$ROOT/$path" ]] || { echo "FATAL: $name is not in the tree at $path"; exit 1; }
  [[ -x "$ROOT/$path" ]] || { echo "FATAL: $path is not executable; the job would die on Permission denied"; exit 1; }
done

# One value for the pair the suites and the bootstrapped account must agree on.
export GMS_SUPERADMIN_USERNAME="$SUPERADMIN_USER"
export GMS_SUPERADMIN_PASSWORD="$SUPERADMIN_PASSWORD"
export APP_SUPERADMIN_ENABLED=true
export APP_SUPERADMIN_USERNAME="$SUPERADMIN_USER"
export APP_SUPERADMIN_PASSWORD="$SUPERADMIN_PASSWORD"
export APP_SUPERADMIN_EMAIL="${APP_SUPERADMIN_EMAIL:-ci@invalid.example}"
export APP_SUPERADMIN_FULL_NAME="${APP_SUPERADMIN_FULL_NAME:-Platform Administrator}"
# Same reason as ci-playwright-e2e.sh: the account's password is the secret this job already
# holds, not an issued temporary, so the rotation gate would block the fixtures' first write.
export APP_SUPERADMIN_FORCE_PASSWORD_CHANGE=false
export GMS_TEST_USERNAME="$SUPERADMIN_USER"
export GMS_TEST_PASSWORD="$SUPERADMIN_PASSWORD"
# Not decoration: verify-mvp-e2e.sh skips three least-privilege refusals without a
# teacher password, and verify-c3 defaults its own staff pair. Setting both keeps the
# run at zero skips, which is the harness's exit condition.
export GMS_TEACHER_PASSWORD="${GMS_TEACHER_PASSWORD:-Teacher_Verify_2026!}"
export GMS_STAFF_PASSWORD="${GMS_STAFF_PASSWORD:-Staff_Verify_2026!}"
export GMS_VERIFY_DB="$VERIFY_DB"
export GMS_PG_CONTAINER="$PG_CONTAINER"
export GMS_PG_USER="$PG_USER"
export GMS_PG_PASSWORD_ENV="$PG_PASSWORD_ENV"
export GMS_BASE_URL="$BASE_URL"
export GMS_CONTROLLER_DIR="${GMS_CONTROLLER_DIR:-$ROOT/gms_backend/src/main/java/com/gms/controller}"
export GMS_P1_FIXTURE_ENV="${GMS_P1_FIXTURE_ENV:-$WORK_DIR/p1-fixture.env}"
# verify-mvp-e2e.sh creates these two schools and p1_fixtures.sh then locates its tenants by
# code instead of by "the lowest school ids". Both read the code from the environment, so the
# pair is set here once — a laptop run and the CI job then point at the same two schools, and a
# default edited in one suite cannot silently make the other look at a tenant nobody created.
export GMS_SCHOOL_A_CODE="${GMS_SCHOOL_A_CODE:-MVP801}"
export GMS_SCHOOL_B_CODE="${GMS_SCHOOL_B_CODE:-MVP802}"
# verify-mvp-e2e.sh rotates the two provisioned administrators to these values after their first
# sign-in, and p1_fixtures.sh signs in with the same ones. The issued passwords are random and
# never leave the creation response, so this pair is the only thing that lets the fixture step
# reach the tenants the MVP step built. Exported once, so a default edited in one script cannot
# leave the other authenticating with a password nobody set.
export GMS_SCHOOL_A_ADMIN_PASSWORD="${GMS_SCHOOL_A_ADMIN_PASSWORD:-Mvp_Admin_A_2026!}"
export GMS_SCHOOL_B_ADMIN_PASSWORD="${GMS_SCHOOL_B_ADMIN_PASSWORD:-Mvp_Admin_B_2026!}"
# p1_fixtures.sh rotates the parent account to this and writes it into the env file the negative
# tests source; exporting it keeps a rerun's "already present" path on the same value.
export GMS_PARENT_PASSWORD="${GMS_PARENT_PASSWORD:-Parent_Verify_2026!}"
export DB_URL DB_USERNAME DB_PASSWORD
# Two more things the validator refuses without, and neither is a decision about
# production configuration: a signing key that exists only for this process, and an
# origin no browser in this job will ever send. Minted per run so nothing in the
# repository can carry it forward.
export SECURITY_JWT_SECRET="${SECURITY_JWT_SECRET:-$(openssl rand -base64 48)}"
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-https://live-tenancy-gate.invalid}"
# verify-security.sh predates this harness and reads the database through the application's own
# variable names: `DB_NAME` for the database, and `GMS_DB_CONTAINER` — not `GMS_PG_CONTAINER`,
# which the other nine suites use — for the container its `docker exec` probes travel through.
# Every variable below is one the harness's own flag has to reach; leaving this out is invisible
# on any machine whose container is literally named `gms-postgres`, because that is exactly the
# default the suite falls back to. It is not invisible anywhere else: pointed at a second server,
# the suite asked the first one for a database that does not exist there, three probes printed
# `FATAL: database … does not exist` to stderr, and the throttling assertion failed on an empty
# field — a red gate whose only defect was which container the pipe was welded to.
export DB_NAME="$VERIFY_DB"
export GMS_DB_CONTAINER="$PG_CONTAINER"

# Built without back-references: BSD sed substitutes an unmatched group as the literal
# `\2` rather than as empty, so a capture-group version of this line works on the CI
# runner and silently breaks on a macOS laptop — which is the one place the
# local-reproduction property of this script is paid for.
PORT=$(printf '%s' "$BASE_URL" | sed -E 's#^[a-zA-Z]+://##; s#^[^:/]*##; s#^:##; s#[/].*$##')
[[ "$PORT" =~ ^[0-9]+$ ]] || PORT=8080

echo "== preflight =="
echo "jar        $JAR"
echo "base url   $BASE_URL (port $PORT)"
echo "database   $VERIFY_DB via $DB_URL as $DB_USERNAME; probes through docker exec $PG_CONTAINER"
echo "work dir   $WORK_DIR"
docker inspect -f '{{.State.Running}}' "$PG_CONTAINER" 2>/dev/null | grep -q true \
  || { echo "FATAL: no running container named $PG_CONTAINER - the SQL probes have no transport"; exit 1; }

echo
echo "== boot =="
# The profile is deliberately unset. `dev` relaxes StartupSecurityValidator and lets
# JwtTokenProvider mint an ephemeral key, so a suite run under it would be proving a
# build that refuses nothing at startup. Without a profile the boot itself is the
# first assertion: the validator must pass, Flyway must apply the chain, and
# ddl-auto=validate must accept the schema it produced.
JPA_SHOW_SQL=true java -jar "$JAR" --server.port="$PORT" >"$APP_LOG" 2>&1 &
APP_PID=$!
echo "pid $APP_PID; waiting for $BASE_URL/actuator/health/readiness (max ${BOOT_TIMEOUT}s)"

# Readiness, not liveness: the web server answers liveness while the CommandLineRunners are
# still executing, and SuperAdminBootstrapRunner is one of them. A suite that starts on
# liveness can log in before the platform account exists and get a 401 that looks like an
# auth failure. READINESS turns accepting-traffic only after ApplicationReadyEvent.
ready=""
for ((waited = 0; waited < BOOT_TIMEOUT; waited += 3)); do
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "FATAL: the application exited during boot after ${waited}s. Last lines:"
    tail -n 60 "$APP_LOG"
    exit 1
  fi
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$BASE_URL/actuator/health/readiness" || echo 000)
  if [[ "$code" == "200" ]]; then ready=yes; break; fi
  sleep 3
done
if [[ -z "$ready" ]]; then
  echo "FATAL: nothing answered GET $BASE_URL/actuator/health/readiness within ${BOOT_TIMEOUT}s."
  tail -n 60 "$APP_LOG"
  kill "$APP_PID" 2>/dev/null
  exit 1
fi
grep -q "Startup security validation passed" "$APP_LOG" \
  || echo "::warning:: the boot log holds no 'Startup security validation passed' line"
BOOT_SECONDS=$waited
echo "up after ${BOOT_SECONDS}s"

# c7 reads the generated INSERTs out of this log, so the file it names must be the
# one the application is writing to right now. Without it that suite reports a skip.
export GMS_SQL_LOG="$APP_LOG"

summarize() { # summarize <log> -> "<failed count>|<skipped count>|<last summary line>"
  local log=$1 failed=-1 skipped=0 last=""
  if grep -qE 'FAILED: [0-9]+' "$log"; then
    failed=$(grep -oE 'FAILED: [0-9]+' "$log" | tail -1 | grep -oE '[0-9]+')
  elif grep -qE 'failures=[0-9]+' "$log"; then
    failed=$(grep -oE 'failures=[0-9]+' "$log" | tail -1 | grep -oE '[0-9]+')
  elif grep -qE '[0-9]+ failed' "$log"; then
    failed=$(grep -oE '[0-9]+ failed' "$log" | tail -1 | grep -oE '[0-9]+')
  fi
  if grep -qE 'SKIPPED: [0-9]+' "$log"; then
    skipped=$(grep -oE 'SKIPPED: [0-9]+' "$log" | tail -1 | grep -oE '[0-9]+')
  elif grep -qE '[0-9]+ skipped' "$log"; then
    skipped=$(grep -oE '[0-9]+ skipped' "$log" | tail -1 | grep -oE '[0-9]+')
  fi
  last=$(grep -E 'PASSED:|checks=|passed,' "$log" | tail -1 | sed 's/^[[:space:]]*//')
  printf '%s|%s|%s' "$failed" "$skipped" "$last"
}

rows=()
red=0
skips_total=0
echo
echo "== suites =="
for record in "${SUITES[@]}"; do
  IFS='|' read -r name path kind ceiling reason <<< "$record"
  log="$WORK_DIR/$name.log"
  started=$(date +%s)
  ( cd "$ROOT" && bash "$path" ) >"$log" 2>&1
  status=$?
  seconds=$(( $(date +%s) - started ))

  if [[ "$kind" == "fixture" ]]; then
    if [[ $status -eq 0 ]]; then
      set -a; . "$GMS_P1_FIXTURE_ENV"; set +a
      echo "p1-fixtures  ok  ${seconds}s  env -> $GMS_P1_FIXTURE_ENV"
      rows+=("| $name | ran | $seconds s | fixtures built | - |")
    else
      red=1
      echo "p1-fixtures  FAIL exit=$status after ${seconds}s"
      tail -n 25 "$log"
      rows+=("| $name | FAILED | $seconds s | exit $status | the negative tests have no identities |")
    fi
    continue
  fi

  IFS='|' read -r failed skipped last < <(summarize "$log")
  verdict="ok"
  if [[ $status -ne 0 ]]; then verdict="FAIL"; red=1; fi
  if [[ "$skipped" =~ ^[0-9]+$ ]] && (( skipped > ceiling )); then
    # The rule, stated once: an environment that could not ask a question has not answered it.
    # The knobs a suite reads to decide whether it can ask are the teacher and staff passwords,
    # `GMS_VERIFY_DB` plus the container that holds it, `GMS_CONTROLLER_DIR` and `GMS_SQL_LOG` — and
    # this harness exports all five, the last two unconditionally, the database pair after a preflight
    # that refuses to start without a reachable container. So a skip above the ceiling means the
    # environment moved, not that the suite is lenient. (`GMS_REQUIRE_DB` stays unset: it turns an
    # absent database into a fatal error, and an absent database here is the preflight's FATAL line or
    # a ceiling breach, neither of which needs a third gate in front of it.)
    verdict="SKIP"; red=1
  fi
  (( ${skipped:-0} > 0 )) && skips_total=$(( skips_total + skipped ))
  note=""
  [[ "$verdict" == "ok" && "${skipped:-0}" -gt 0 ]] \
    && note="  (${skipped} skip(s) at or below the ceiling of ${ceiling}: ${reason})"
  printf '%-24s %-5s %ss  failed=%s skipped=%s/%s  %s%s\n' \
    "$name" "$verdict" "$seconds" "$failed" "$skipped" "$ceiling" "$last" "$note"
  if [[ "$verdict" != "ok" ]]; then
    echo "  ── failing lines from $log ──"
    grep -E '^(FAIL|SKIP|FATAL| *body:)' "$log" | head -30
    tail -n 10 "$log"
  fi
  rows+=("| $name | $verdict | $seconds s | ${last:-no summary line} | exit $status |")
  if (( ceiling > 0 )); then
    rows+=("| └ skip ceiling | ${skipped:-?} of $ceiling used | — | $reason | — |")
  fi
done

echo
echo "== teardown =="
kill "$APP_PID" 2>/dev/null
wait "$APP_PID" 2>/dev/null

summary=$(
  echo "## Live tenancy gate"
  echo
  echo "Backend booted from \`$(basename "$JAR")\` against \`$VERIFY_DB\` on \`$DB_URL\`"
  echo "(profile: none — \`StartupSecurityValidator\` enforced), up in ${BOOT_SECONDS}s."
  echo
  echo "| step | verdict | wall clock | summary line it printed | exit |"
  echo "| --- | --- | --- | --- | --- |"
  printf '%s\n' "${rows[@]}"
  echo
  echo "### Not run by this job, and why"
  echo
  printf -- '- %s\n' "${NOT_RUN[@]}"
)
printf '%s\n' "$summary"
[[ -n "${GITHUB_STEP_SUMMARY:-}" ]] && printf '%s\n' "$summary" >> "$GITHUB_STEP_SUMMARY"

echo
if [[ $red -eq 0 ]]; then
  echo "live-tenancy: ${#SUITES[@]} steps ran, 0 failed, ${skips_total} skipped (every one at or below its documented ceiling)"
  exit 0
fi
echo "live-tenancy: REFUSED — see the FAIL/SKIP lines above"
exit 1
