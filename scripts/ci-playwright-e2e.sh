#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# ci-playwright-e2e.sh — the browser gate, defined once.
#
#   GMS_JAR=gms_backend/target/gms-0.1.0-SNAPSHOT.jar ./scripts/ci-playwright-e2e.sh
#
# What it does: creates a database that exists only for this run inside a reachable
# PostgreSQL, boots the built jar against it with no Spring profile so Flyway brings
# the schema up from V1 and `StartupSecurityValidator` has to pass on the way, waits
# on the anonymous liveness probe, builds the frontend bundle, and runs the Playwright
# suite in `gms_frontend/e2e/` against that single boot. Then it stops the application
# and drops the database it created.
#
# Why it is a script and not workflow YAML: the same reason `ci-live-tenancy.sh` is one,
# and the same reason `playwright.config.js` refuses to start a backend. A sequence
# written twice — once in YAML, once in a README, once in a test config — is free to
# drift, and the drift shows up as CI proving something no local run reproduces. The
# `Playwright e2e` job in `.github/workflows/ci.yml` and an operator's laptop have to
# execute the same instructions, so they execute this file.
#
# What it deliberately does NOT do:
#   * It does not migrate the database by hand. The boot is the migration: the app runs
#     Flyway and then `ddl-auto=validate`, so a chain that does not match the entities
#     dies here rather than as a 500 inside a test's third assertion.
#   * It does not start `vite preview`. `playwright.config.js`'s `webServer` owns that
#     process, because it is the thing that exists only for the length of a run and
#     every test needs it before its first navigation. A second owner would be the one
#     place this gate and a laptop could disagree about. It is also not `vite dev`: see
#     the comment on `webServer.command` there — the dev server defaults the bundle to
#     an absolute `http://localhost:8080/api/v1`, which is a cross-origin call this
#     boot's CORS list refuses.
#   * It does not touch a database it did not create. Every DROP below is guarded by
#     the flag this run's own CREATE statement sets.
#
# Prerequisites, all of them mintable by anyone holding the repository:
#   * $GMS_JAR, or a jar to find under `$ROOT/gms_backend/target/`.
#   * $DB_URL — the JDBC URL of an empty cluster whose role may CREATE DATABASE. Only
#     its server, port, credentials and query parameters are used; the database name in
#     it is replaced by this run's, so the gate never writes the cluster's own database.
#   * a running container named $GMS_PG_CONTAINER holding that same server: the database
#     is created and dropped with `docker exec … psql`, which is how `ci-live-tenancy.sh`'s
#     SQL halves reach it too.
#   * curl, docker, java, node, npm, openssl on PATH; node >= the version the `frontend`
#     job pins, because `npm ci` installs the same tree.
#
# Nothing here is a secret. The database is invented per run, the tenants are invented by
# the fixture, the signing key is minted by `openssl` for one process, and the platform
# account is the pair `ci-live-tenancy.sh` already requires. A repository secret would be
# worse, not better: an unset secret expands to the empty string, PostgreSQL refuses to
# initialise without a password, and the job would die on a missing prerequisite instead
# of on a failed assertion. That is why this file reads no `secrets.*` value at all.
#
# Exit: the suite's exit code, and nothing else. A boot that never came up, a schema that
# did not validate and a platform account that was not minted each die earlier with their
# own FATAL line and the tail of the backend log, because every one of them would otherwise
# arrive as four identical-looking test failures.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

ROOT=${GMS_REPO_ROOT:-.}
FRONTEND_DIR="$ROOT/gms_frontend"
API_ORIGIN=${GMS_E2E_API_ORIGIN:-http://localhost:8080}
WEB_PORT=${GMS_E2E_WEB_PORT:-4173}
BOOT_TIMEOUT=${GMS_BOOT_TIMEOUT_SECONDS:-180}
WORK_DIR=${GMS_CI_WORK_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/gms-playwright-e2e-ci.XXXXXX")}
APP_LOG="$WORK_DIR/backend.log"
E2E_LOG="$WORK_DIR/playwright-e2e.log"

DB_URL=${DB_URL:?set DB_URL to the JDBC URL of the PostgreSQL the run creates its database inside}
DB_USERNAME=${DB_USERNAME:?set DB_USERNAME}
DB_PASSWORD=${DB_PASSWORD:?set DB_PASSWORD}
PG_CONTAINER=${GMS_PG_CONTAINER:-gms-postgres}
PG_USER=${GMS_PG_USER:-gms_user}
PG_PASSWORD_ENV=${GMS_PG_PASSWORD_ENV:-DB_PASSWORD}
SUPERADMIN_USER=${GMS_SUPERADMIN_USERNAME:?set GMS_SUPERADMIN_USERNAME - the suite provisions every tenant through this account}
SUPERADMIN_PASSWORD=${GMS_SUPERADMIN_PASSWORD:?set GMS_SUPERADMIN_PASSWORD}

# One tag, two jobs: it names the database and it is the value `helpers/api.js` stamps into
# every school code. Passing it in is how CI makes a run addressable ("the database of run
# 41488389433"); left unset on a laptop it becomes the clock, which is unique enough because
# the check below refuses rather than shares when a name is already taken.
# `date +%s` and not `%N`: the runner's GNU date and the laptop's BSD date disagree about
# nanoseconds, and run `41485021174` died exactly that way when `p1_fixtures.sh` asked BSD
# `date -v-3d` of a GNU runner.
E2E_TAG=$(printf '%s' "${GMS_E2E_TAG:-$(date +%s)}" | tr 'A-Z' 'a-z')
E2E_DB=${GMS_E2E_DB:-gms_e2e_${E2E_TAG}}

mkdir -p "$WORK_DIR"

# ── 1. the name has to be this run's, or there is no run ──────────────────────
# The shape is a guard, not a style preference, and it holds up three things at once:
#   * teardown drops by name, so a name this script cannot prove it minted is a name it
#     must not be allowed to spell into a DROP DATABASE;
#   * PostgreSQL identifiers are 63 bytes, and this one is `gms_e2e_` plus the tag;
#   * `helpers/api.js` builds `E2E<${tag}><n><label>` and `SchoolRequest.schoolCode` is
#     `@Size(max = 20)`, so a tag longer than twelve characters makes the fixture's own
#     school code a 400 — a failure that would read as a backend defect.
# Lowercased above because an unquoted identifier is folded to lowercase by PostgreSQL:
# a mixed-case tag would make two runs that believe they are different share one database.
if [[ ! "$E2E_DB" =~ ^gms_e2e_[a-z0-9]{1,12}$ ]]; then
  echo "FATAL: '$E2E_DB' is not a per-run database name."
  echo "       It has to be gms_e2e_<1-12 lowercase alphanumerics>, and GMS_E2E_DB is"
  echo "       only for pointing this run at a name its own tag cannot spell."
  exit 1
fi

# The database this run boots on must not be the one the cluster was initialised with —
# on the CI runner that is `gms`, the same name `live-tenancy` uses, and a run that wrote
# there would be indistinguishable from the gate next door and would survive it.
# Read off the URL rather than taken from GMS_VERIFY_DB: this script's database is the one it
# mints, so the only honest description of the target is the one already in the URL.
SEED_DB=$(printf '%s' "$DB_URL" | sed -E 's#\?.*$##; s#.*/##')
# A URL with no database segment (`…:5432`) leaves `host:5432` in that variable, and one
# ending in a bare slash leaves it empty; either would make the rewrite below splice the new
# name into the middle of the host and boot against a server this run never created.
if [[ -z "$SEED_DB" || "$SEED_DB" == *:* || "$SEED_DB" == */* ]]; then
  echo "FATAL: '$DB_URL' does not name a database."
  echo "       Give this script the URL the live-tenancy job gives, ending in /<database>."
  exit 1
fi
if [[ "$SEED_DB" == "$E2E_DB" ]]; then
  echo "FATAL: DB_URL and the per-run database name are both '$E2E_DB'."
  echo "       This script must create and drop its own database, never the cluster's."
  exit 1
fi

# Rebuild the JDBC URL onto the per-run database, keeping everything the caller had to
# know about the server. Query parameters are split off first and re-attached last, so a
# `?currentSchema=…` or a `?sslmode=…` the operator needs is not silently dropped.
DB_QUERY=""
DB_BASE="$DB_URL"
if [[ "$DB_BASE" == *\?* ]]; then
  DB_QUERY="?${DB_BASE#*\?}"
  DB_BASE="${DB_BASE%%\?*}"
fi
[[ "$DB_BASE" == jdbc:postgresql://*/* ]] \
  || { echo "FATAL: '$DB_URL' is not a jdbc:postgresql:// URL with a database segment."; exit 1; }
# `%/*` removes the shortest trailing `/…`, which after the `://` host/port/path shape is
# exactly the database segment checked above — the guard is what makes that safe.
RUN_DB_URL="${DB_BASE%/*}/${E2E_DB}${DB_QUERY}"

# The application and the suite must be pointed at the same database by the same name —
# the rule `ci-live-tenancy.sh:75-81` checks between two variables a human wrote. Here the
# one is derived from the other, so what is being checked is the derivation.
DB_URL="$RUN_DB_URL"
[[ "$(printf '%s' "$DB_URL" | sed -E 's#\?.*$##; s#.*/##')" == "$E2E_DB" ]] \
  || { echo "FATAL: the derived DB_URL does not name $E2E_DB."; exit 1; }

# ── 2. the jar ────────────────────────────────────────────────────────────────
# Resolved the way the `live-tenancy` job resolves it from the `gms-backend-jar` artifact
# (ci.yml's "Where the jar landed" step), moved into the script so a laptop and CI apply
# one rule instead of two. `*.jar` and not `*.jar*`: the repackaged plain jar is
# `gms-0.1.0-SNAPSHOT.jar.original`, and booting that would test a build with no embedded
# classes — more than one match means the build changed its output shape and this gate
# cannot say which artifact it booted.
shopt -s nullglob
if [[ -n "${GMS_JAR:-}" ]]; then
  JAR="$GMS_JAR"
else
  jars=("$ROOT"/gms_backend/target/*.jar)
  if (( ${#jars[@]} != 1 )); then
    echo "FATAL: GMS_JAR is unset and gms_backend/target/*.jar matched ${#jars[@]} files."
    # No backticks in this message: an unescaped pair inside a double-quoted string is a
    # command substitution, and 'Lint every workflow' already lost a run to exactly that
    # shape in ci.yml (ci.yml's own note on the jar-path message).
    echo "       Set GMS_JAR to the jar that 'mvn -B clean verify' produced."
    exit 1
  fi
  JAR="${jars[0]}"
fi
shopt -u nullglob

for binary in curl docker java node npm openssl; do
  command -v "$binary" >/dev/null 2>&1 || { echo "FATAL: $binary is required on PATH"; exit 1; }
done
[[ -f "$JAR" ]] || { echo "FATAL: no jar at $JAR"; exit 1; }
[[ -d "$FRONTEND_DIR" ]] || { echo "FATAL: no frontend at $FRONTEND_DIR"; exit 1; }

# ── 3. the environment both halves read ───────────────────────────────────────
# One value for the pair the boot and the fixture must agree on: the app is configured
# through APP_SUPERADMIN_* (application.properties binds exactly those names, and the
# bare SUPERADMIN_* spelling that survives in older notes binds to nothing), while
# GMS_SUPERADMIN_* is the name the live-tenancy job already passes and this script's own
# flag. Both are exported from the one variable above, so an operator who sets the pair
# the CI job sets gets a platform account the suite can log into.
export GMS_SUPERADMIN_USERNAME="$SUPERADMIN_USER"
export GMS_SUPERADMIN_PASSWORD="$SUPERADMIN_PASSWORD"
export APP_SUPERADMIN_ENABLED=true
export APP_SUPERADMIN_USERNAME="$SUPERADMIN_USER"
export APP_SUPERADMIN_PASSWORD="$SUPERADMIN_PASSWORD"
export APP_SUPERADMIN_EMAIL="${APP_SUPERADMIN_EMAIL:-ci@invalid.example}"
export APP_SUPERADMIN_FULL_NAME="${APP_SUPERADMIN_FULL_NAME:-Platform Administrator}"
# The bootstrap default is true, and the filter it feeds blocks every write until the
# account rotates. That is the right production posture — the password there is an issued
# temporary — but this account's password comes from a CI secret the suite already holds,
# and there is no operator to rotate it. Left true, `/schools/create` answers 403
# PASSWORD_CHANGE_REQUIRED and every tenant fixture dies on its first write.
export APP_SUPERADMIN_FORCE_PASSWORD_CHANGE=false

# Not decoration: the fixture's first act is a platform login, and without an account it
# fails as `the platform account could not log in (answered 401)` in every test.
export GMS_E2E_PLATFORM_USERNAME="$SUPERADMIN_USER"
export GMS_E2E_PLATFORM_PASSWORD="$SUPERADMIN_PASSWORD"
export GMS_E2E_TAG="$E2E_TAG"
export GMS_E2E_API_ORIGIN="$API_ORIGIN"
export GMS_E2E_WEB_PORT="$WEB_PORT"
# GMS_E2E_BASE_URL deliberately unset: `playwright.config.js` already knows how to spell a
# base URL from a port, and exporting a second spelling of the same value gives the two of
# them a way to disagree.
unset GMS_E2E_BASE_URL

# A signing key that exists only for this process, and an origin no browser in the job will
# ever send — both minted/stated exactly as `ci-live-tenancy.sh:165-166` does, and for the
# same reason: the validator refuses an empty secret and refuses a `localhost` origin, so
# the boot has to be given something, and neither answer may be reachable. `.invalid` is
# the reserved TLD no-one can register, which is what makes the origin unreachable rather
# than merely inconvenient. The fourth test in the suite depends on this being refused:
# it posts from the page's real origin and asserts 403 `Invalid CORS request`.
export SECURITY_JWT_SECRET="${SECURITY_JWT_SECRET:-$(openssl rand -base64 48)}"
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-https://playwright-e2e-gate.invalid}"

# `VITE_API_BASE_URL` is read at build time by `getApiBaseUrl()`, and a value baked into
# `dist/` outranks the production branch that returns the relative `/api/v1`. If the CI
# job's environment happened to carry one — it is a plausible thing for a deploy job to
# export to every step — the bundle would call an absolute origin, every journey would
# trip the CORS refusal meant for the fourth test, and the failure would name none of it.
unset VITE_API_BASE_URL

export DB_URL DB_USERNAME DB_PASSWORD
# GMS_VERIFY_DB, GMS_PG_CONTAINER and GMS_PG_USER are deliberately NOT exported. Those are
# the names `ci-live-tenancy.sh`'s SQL halves read to reach a database through `docker exec`,
# and this run has no SQL half — the browser suite asks the server over HTTP and reads the
# database only through the API. Exporting this run's per-run name under the live gate's
# variable would be the one way for a nested `ci-live-tenancy.sh` to be pointed at a database
# it did not create and would not drop.

# Built without back-references, ported from `ci-live-tenancy.sh:179-184`: BSD sed
# substitutes an unmatched group as the literal `\2` rather than as empty, so a
# capture-group version of this line works on the CI runner and silently breaks on a macOS
# laptop — the one place the local-reproduction property of this script is paid for.
API_PORT=$(printf '%s' "$API_ORIGIN" | sed -E 's#^[a-zA-Z]+://##; s#^[^:/]*##; s#^:##; s#[/].*$##')
[[ "$API_PORT" =~ ^[0-9]+$ ]] || API_PORT=8080
WEB_BASE="http://localhost:${WEB_PORT}"

echo "== preflight =="
echo "jar         $JAR"
echo "api origin  $API_ORIGIN (port $API_PORT)"
echo "web base    $WEB_BASE - served by the suite's own vite preview, not by this script"
echo "database    $E2E_DB (per-run) via $DB_URL as $DB_USERNAME; created through docker exec $PG_CONTAINER"
echo "platform    $SUPERADMIN_USER"
echo "work dir    $WORK_DIR"
echo "backend log $APP_LOG"
echo "suite log   $E2E_LOG"
docker inspect -f '{{.State.Running}}' "$PG_CONTAINER" 2>/dev/null | grep -q true \
  || { echo "FATAL: no running container named $PG_CONTAINER - this run has no way to create its database"; exit 1; }
# `reuseExistingServer: !process.env.CI` means a server already on the web port is reused
# on a laptop and is a hard error in CI, where the message it produces does not name the
# process that took the port. Checked here in both cases, because a suite driving some
# other build is the worst kind of green.
if curl -s -o /dev/null --max-time 2 "$WEB_BASE"; then
  echo "FATAL: something already answers on $WEB_BASE."
  echo "       The suite starts its own vite preview; a second server on the port means"
  echo "       the tests would drive a build this run did not make."
  exit 1
fi

# ── 4. the per-run database ───────────────────────────────────────────────────
echo
echo "== database =="
# The name was validated against a fixed regex above, so it can be interpolated into the
# statement without quoting and without hiding a semicolon.
if [[ -n "$(docker exec -e PGPASSWORD="${!PG_PASSWORD_ENV}" "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -tA \
  -c "SELECT 1 FROM pg_database WHERE datname = '$E2E_DB';")" ]]; then
  echo "FATAL: $PG_CONTAINER already holds a database named $E2E_DB."
  echo "       This run would have had to share it, and a shared database is not per-run:"
  echo "       the fixture's uniqueness guards only prove anything over a schema it drained."
  echo "       Either let GMS_E2E_TAG fall back to the clock, or drop $E2E_DB by hand."
  exit 1
fi
# OWNER $PG_USER, because the application connects as that role and `ddl-auto=validate`
# plus Flyway need a schema the same role owns — the same invariant ci-live-tenancy.sh's
# header states for the database it boots on.
docker exec -e PGPASSWORD="${!PG_PASSWORD_ENV}" "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -q \
  -c "CREATE DATABASE $E2E_DB OWNER $PG_USER;"
CREATED_DB=yes
echo "created $E2E_DB (owner $PG_USER); nothing else in this run may touch a database"

# Teardown lives in a function on the trap rather than at the bottom of the file, because a
# FAILED run has to release its database too: the name is derived from the tag, so a run
# that left it behind makes the next run of the same tag die on the existence check above.
# A gate that can only be run once per tag is a gate that gets its guard removed. It is
# idempotent for the same reason — the happy path calls it where its output belongs, in
# front of the summary, and the trap then finds nothing left to do.
cleanup() {
  # The application we stop exits by signal, and a container that has already gone answers
  # the DROP with an error; neither is a finding, and the run's verdict is the suite's exit
  # code. `set -e` is switched off for this block rather than each command neutralised in
  # place, so what leaves the function is still the status it was called with.
  set +e
  if [[ -n "${APP_PID:-}" && "${APP_STOPPED:-}" != "yes" ]]; then
    # Checked, not assumed: the path that gets here after a boot failure is one where the
    # application has already exited by itself, and reporting a kill it did not need would
    # send a reader looking for a process that stopped nothing.
    if kill -0 "$APP_PID" 2>/dev/null; then
      kill "$APP_PID" 2>/dev/null
      wait "$APP_PID" 2>/dev/null
    fi
    APP_STOPPED=yes
    echo "application (pid $APP_PID) stopped"
  fi
  if [[ "${CREATED_DB:-}" == "yes" && "${DB_DROPPED:-}" != "yes" ]]; then
    # Three tries, because the connection pool outlives the process by a moment and
    # PostgreSQL refuses to drop a database anyone is still attached to. A failed drop is
    # reported rather than hidden: a leaked per-run database is a real residue, and the
    # next run's existence check would otherwise be blamed for it.
    for attempt in 1 2 3; do
      if docker exec -e PGPASSWORD="${!PG_PASSWORD_ENV}" "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -q \
        -c "DROP DATABASE IF EXISTS $E2E_DB;"; then
        DB_DROPPED=yes
        echo "dropped $E2E_DB"
        break
      fi
      [[ $attempt -eq 3 ]] && echo "WARNING: $E2E_DB was NOT dropped - remove it by hand"
      sleep 2
    done
  fi
  # Restored, because the happy path calls this function and then keeps going: leaving
  # `errexit` off for the lines after teardown would be a silent change of rules for whoever
  # adds a step at the bottom of this file.
  set -e
}

# `exit "$status"` here is what preserves the run's verdict: a trap that simply fell off the
# end would exit with the status of its last command, and a green suite would turn into
# whatever `cleanup` happened to end on.
on_exit() { local status=$?; cleanup; exit "$status"; }
trap on_exit EXIT

# ── 5. boot ───────────────────────────────────────────────────────────────────
echo
echo "== boot =="
# The profile is deliberately unset, copied from ci-live-tenancy.sh:201's reasoning. `dev`
# relaxes StartupSecurityValidator and lets JwtTokenProvider mint an ephemeral key, so a
# browser run under it would be proving a build that refuses nothing at startup — and the
# fourth test's whole claim is that the deployment's CORS list refuses a stranger, which a
# dev profile would quietly widen. Without a profile the boot is the first assertion: the
# validator must pass, Flyway must apply the chain to the empty database created above, and
# ddl-auto=validate must accept the schema it produced.
# JPA_SHOW_SQL stays off (the live harness turns it on so verify-c7 can read statements
# out of this log): nothing here parses SQL, and the browser suite's transcript is E2E_LOG.
java -jar "$JAR" --server.port="$API_PORT" >"$APP_LOG" 2>&1 &
APP_PID=$!
echo "pid $APP_PID; waiting for $API_ORIGIN/actuator/health/liveness (max ${BOOT_TIMEOUT}s)"

ready=""
for ((waited = 0; waited < BOOT_TIMEOUT; waited += 3)); do
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "FATAL: the application exited during boot after ${waited}s. Last lines:"
    tail -n 60 "$APP_LOG"
    exit 1
  fi
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$API_ORIGIN/actuator/health/liveness" || echo 000)
  if [[ "$code" == "200" ]]; then ready=yes; break; fi
  sleep 3
done
if [[ -z "$ready" ]]; then
  echo "FATAL: nothing answered GET $API_ORIGIN/actuator/health/liveness within ${BOOT_TIMEOUT}s."
  tail -n 60 "$APP_LOG"
  exit 1
fi
# `grep -m1` into a variable rather than `grep -q`, so the lines can be echoed below as well as
# refused on their absence. Attached to the job log and the step summary for the same reason the
# console transcript is attached even on a green test: "this was the hardened boot" is only
# evidence if a reader can see the line the validator wrote, instead of taking the harness's word
# for a check it performs and then discards.
STARTUP_VALIDATION_LINE=$(grep -m1 "Startup security validation passed" "$APP_LOG") \
  || { echo "FATAL: the boot log holds no 'Startup security validation passed' line, so this"; \
       echo "       is not the hardened boot the CORS refusal in test four is measured against."; \
       tail -n 40 "$APP_LOG"; exit 1; }
# Checked rather than assumed, because the alternative is four tests that each fail at their
# first login and none of them naming the reason. Awaited rather than read once: the bootstrap is
# an ApplicationRunner, and liveness answers from the servlet container before the runners have
# executed — a single read here saw "bootstrap starting" and declared the boot unusable on a run
# whose account was minted 4 ms later.
for ((held = 0; held < BOOT_TIMEOUT; held += 1)); do
  SUPERADMIN_BOOTSTRAP_LINE=$(grep -m1 "SuperAdmin bootstrap completed successfully" "$APP_LOG" || true)
  [[ -n "$SUPERADMIN_BOOTSTRAP_LINE" ]] && break
  sleep 1
done
[[ -n "$SUPERADMIN_BOOTSTRAP_LINE" ]] \
  || { echo "FATAL: no platform account was minted by this boot; every fixture login would 401."; \
       grep -i "SuperAdmin bootstrap" "$APP_LOG" | tail -5; exit 1; }
BOOT_SECONDS=$waited
echo "up after ${BOOT_SECONDS}s"
echo "boot proof, verbatim from the application log:"
printf '  %s\n' "$STARTUP_VALIDATION_LINE" "$SUPERADMIN_BOOTSTRAP_LINE"

# ── 6. the browser suite ──────────────────────────────────────────────────────
cd "$FRONTEND_DIR"
echo
echo "== frontend =="
# A named function rather than a compound `! [[ file -nt dir ]]` inside the `if` below,
# because that line already has two conditions and a negation in the middle of them.
package_lock_is_newer() { [[ package-lock.json -nt node_modules ]]; }

# Reuse an installed tree when it is both present and not older than the lockfile; a fresh
# checkout installs anyway, and the branch exists for the laptop, where `npm ci` deletes
# and re-fetches every dependency for a run that changed none of them. The lockfile
# comparison is the part that is not laziness: a merged dependency bump must not be run
# against a tree that predates it, or the gate green-lights a bundle CI cannot rebuild.
if [[ -x node_modules/.bin/playwright ]] && ! package_lock_is_newer; then
  echo "node_modules is present and at least as new as package-lock.json - skipping npm ci"
else
  npm ci
fi
# Always built, even when the tree was reused: `vite preview` serves `dist/` from disk, so
# a stale bundle would be what the four tests drive. The build is the cheapest way to make
# "the shipped bundle" mean "this commit".
npm run build

# Browsers are cached per machine, not per checkout, so the download is skipped when the
# profile is already there — the same reasoning that makes the install step conditional.
BROWSERS_PATH=${PLAYWRIGHT_BROWSERS_PATH:-}
if [[ -z "$BROWSERS_PATH" ]]; then
  case "$(uname -s)" in
    Darwin) BROWSERS_PATH="$HOME/Library/Caches/ms-playwright" ;;
    *) BROWSERS_PATH="$HOME/.cache/ms-playwright" ;;
  esac
fi
browser_present=no
for candidate in "$BROWSERS_PATH"/chromium-*; do
  [[ -d "$candidate" ]] && browser_present=yes && break
done
if [[ "$browser_present" == "yes" ]]; then
  echo "chromium already in $BROWSERS_PATH - skipping the browser install"
else
  # `--with-deps` installs the system libraries Chromium links against and is only
  # implemented for apt-based Linux, which is the CI runner. Playwright refuses the flag on
  # macOS, so passing it unconditionally would break the laptop half of this script's reason
  # to exist.
  install_args=(chromium)
  [[ "$(uname -s)" == Linux ]] && install_args=(--with-deps chromium)
  npx playwright install "${install_args[@]}"
fi

echo
echo "== suite =="
# The suite owns its webServer (`playwright.config.js`'s webServer block) and reads the API
# origin out of the environment both for its proxy and for its own fixture calls. `--output`
# puts traces and failure screenshots under the work dir next to the logs, where the CI job
# can upload one path; the HTML reporter stays where the config puts it
# (`gms_frontend/playwright-report/`) rather than at a location this script invents.
# `|| e2e_rc=$?` captures the status instead of swallowing it — the repo's own rule — and
# pipefail makes the pipeline report Playwright's code, not tee's.
e2e_rc=0
npm run e2e -- --output "$WORK_DIR/e2e-artifacts" 2>&1 | tee "$E2E_LOG" || e2e_rc=$?

# Captured into a variable and inspected below, rather than assumed to exist: an unmatched
# grep returns 1, which `set -e` would otherwise read as this script failing, and the run's
# verdict is the suite's exit code on the line above — not the shape of a log line.
verdict_line=$(grep -E '[0-9]+ (passed|failed)' "$E2E_LOG" | tail -1 | sed 's/^[[:space:]]*//') || verdict_line=""
[[ -n "$verdict_line" ]] || verdict_line="(the suite printed no count line)"

echo
echo "== teardown =="
# Called here, in the open, rather than left to the trap, so the operator reads "stopped"
# and "dropped" above the summary and not under it. The application goes first, because
# PostgreSQL will not drop a database its connections are still in; the trap still holds for
# every path that never reaches this line.
cleanup

summary=$(
  echo "## Playwright e2e gate"
  echo
  echo "Booted \`$(basename "$JAR")\` against the per-run database \`$E2E_DB\`"
  echo "(profile: none — \`StartupSecurityValidator\` enforced, Flyway applied at boot), up in ${BOOT_SECONDS}s."
  echo
  echo "Served the built bundle through the suite's own \`vite preview\` on \`$WEB_BASE\`,"
  echo "with \`/api\` proxied to \`$API_ORIGIN\` and \`$API_ORIGIN\`'s CORS list naming only"
  echo "\`$CORS_ALLOWED_ORIGINS\`, so the fourth test's refusal is a real one."
  echo
  echo "| | |"
  echo "| --- | --- |"
  echo "| suite verdict | \`$verdict_line\` |"
  echo "| exit code | $e2e_rc |"
  echo "| startup validation | \`${STARTUP_VALIDATION_LINE#*: }\` |"
  echo "| platform account | \`${SUPERADMIN_BOOTSTRAP_LINE#*: }\` |"
  echo "| suite transcript | \`$E2E_LOG\` |"
  echo "| backend log | \`$APP_LOG\` |"
  echo "| traces | \`$WORK_DIR/e2e-artifacts\` |"
  echo "| HTML report | \`gms_frontend/playwright-report/\` (the config's own location) |"
)
printf '%s\n' "$summary"
[[ -n "${GITHUB_STEP_SUMMARY:-}" ]] && printf '%s\n' "$summary" >> "$GITHUB_STEP_SUMMARY"

echo
if [[ $e2e_rc -eq 0 ]]; then
  echo "playwright-e2e: $verdict_line — one boot, one per-run database created and dropped, ${BOOT_SECONDS}s up"
  exit 0
fi
echo "playwright-e2e: REFUSED (exit $e2e_rc) — see the failing tests above; transcript at $E2E_LOG"
exit "$e2e_rc"
