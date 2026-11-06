#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# run-demo.sh — one command that turns a freshly started backend into the demo a
# presenter can click through.
#
# It does three things, in this order:
#   1. (with --docker) brings the database and backend up from docker-compose.yml,
#      using the platform account and JWT secret already in .env;
#   2. runs seed-demo.sh, which builds the demo tenant through the product's own API
#      and writes /tmp/gms-demo-tenant.env;
#   3. runs verify-demo.sh (the five role logins read their screens' data back), then
#      serves the React SPA and the Flutter web build on the ports CORS already allows.
#
# Nothing here is a test fixture: every seeded row was created by a real request, so a
# demo that runs is a demo of the real stack. The script blocks after starting the two
# web servers; Ctrl-C stops them.
#
# Usage:
#   GMS_SUPERADMIN_USERNAME=... GMS_SUPERADMIN_PASSWORD=... scripts/demo/run-demo.sh
#   scripts/demo/run-demo.sh --docker          # uses APP_SUPERADMIN_* from .env
#   scripts/demo/run-demo.sh --seed-only       # seed and verify, serve nothing
#   scripts/demo/run-demo.sh --no-verify       # skip the read-back
#   scripts/demo/run-demo.sh --no-react --no-flutter
#
# Env:
#   GMS_BASE_URL        backend root                     (default http://localhost:8080)
#   GMS_SUPERADMIN_*    platform owner, unless --docker  (required in --existing mode)
#   DEMO_REACT_PORT     SPA port                         (default 5173, CORS-known)
#   DEMO_FLUTTER_PORT   Flutter web port                 (default 3000, CORS-known)
#   GMS_DEMO_ENV        state file                       (default /tmp/gms-demo-tenant.env)
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

BASE=${GMS_BASE_URL:-http://localhost:8080}
REACT_PORT=${DEMO_REACT_PORT:-5173}
FLUTTER_PORT=${DEMO_FLUTTER_PORT:-3000}
OUT=${GMS_DEMO_ENV:-/tmp/gms-demo-tenant.env}
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)

WITH_DOCKER=0
SEED_ONLY=0
VERIFY=1
SERVE_REACT=1
SERVE_FLUTTER=1

say() { printf '%s\n' "$*" >&2; }
die() { printf 'FATAL: %s\n' "$*" >&2; exit 1; }

while (($# > 0)); do
  case "$1" in
    --docker) WITH_DOCKER=1 ;;
    --seed-only|--no-serve) SEED_ONLY=1 ;;
    --no-verify) VERIFY=0 ;;
    --no-react) SERVE_REACT=0 ;;
    --no-flutter) SERVE_FLUTTER=0 ;;
    --port-react) shift; REACT_PORT=${1:?--port-react needs a port} ;;
    --port-flutter) shift; FLUTTER_PORT=${1:?--port-flutter needs a port} ;;
    -h|--help) sed -n '2,40p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown option $1 (try --help)" ;;
  esac
  shift
done

command -v jq >/dev/null 2>&1 || die "jq is required"

# Ports other than these two are not in the backend's default CORS list, so the browser
# would be refused before the login screen could work.
if [[ "${CORS_ALLOWED_ORIGINS:-}" != *":$REACT_PORT"* && "$REACT_PORT" != "5173" ]]; then
  say "warning: the backend's default CORS list allows :5173, not :$REACT_PORT."
  say "         Start it with CORS_ALLOWED_ORIGINS=http://localhost:$REACT_PORT or keep 5173."
fi
if [[ "${CORS_ALLOWED_ORIGINS:-}" != *":$FLUTTER_PORT"* && "$FLUTTER_PORT" != "3000" ]]; then
  say "warning: the backend's default CORS list allows :3000, not :$FLUTTER_PORT."
  say "         Start it with CORS_ALLOWED_ORIGINS=http://localhost:$FLUTTER_PORT or keep 3000."
fi

if (( WITH_DOCKER == 1 )); then
  [[ -f "$ROOT/.env" ]] || die "--docker needs a .env at $ROOT/.env (copy .env.example)"
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/.env"
  set +a
  [[ -n "${APP_SUPERADMIN_USERNAME:-}" && -n "${APP_SUPERADMIN_PASSWORD:-}" ]] || die \
    ".env must set APP_SUPERADMIN_USERNAME and APP_SUPERADMIN_PASSWORD for --docker"
  [[ -n "${SECURITY_JWT_SECRET:-}" ]] || die ".env must set SECURITY_JWT_SECRET"
  GMS_SUPERADMIN_USERNAME=${GMS_SUPERADMIN_USERNAME:-$APP_SUPERADMIN_USERNAME}
  GMS_SUPERADMIN_PASSWORD=${GMS_SUPERADMIN_PASSWORD:-$APP_SUPERADMIN_PASSWORD}

  command -v docker >/dev/null 2>&1 || die "docker is not installed"
  say "Starting the database and backend from docker-compose.yml …"
  (cd "$ROOT" && docker compose up -d db backend) || die "docker compose up failed"

  deadline=$((SECONDS + 300))
  until [[ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$BASE/actuator/health/readiness" 2>/dev/null)" == "200" ]]; do
    (( SECONDS < deadline )) || die "the backend did not become ready within 300s; docker compose logs backend"
    sleep 5
  done
fi

[[ -n "${GMS_SUPERADMIN_USERNAME:-}" && -n "${GMS_SUPERADMIN_PASSWORD:-}" ]] || die \
  "set GMS_SUPERADMIN_USERNAME and GMS_SUPERADMIN_PASSWORD (or use --docker with .env)"

say "Seeding the demo tenant …"
GMS_BASE_URL="$BASE" \
GMS_DEMO_ENV="$OUT" \
DEMO_REACT_URL="http://localhost:$REACT_PORT" \
DEMO_FLUTTER_URL="http://localhost:$FLUTTER_PORT" \
  "$ROOT/scripts/demo/seed-demo.sh" || die "seeding failed"

if (( VERIFY == 1 )); then
  say "Reading the tenant back as all five roles …"
  GMS_BASE_URL="$BASE" GMS_DEMO_ENV="$OUT" "$ROOT/scripts/demo/verify-demo.sh" || die \
    "the demo tenant did not pass its read-back; fix that before presenting"
fi

if (( SEED_ONLY == 1 )); then
  say "Seed and verification finished; no web server was started (--seed-only)."
  exit 0
fi

pids=()
cleanup() {
  local pid
  for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null; done
  wait 2>/dev/null
}
trap cleanup EXIT INT TERM

wait_for_port() { # wait_for_port <port> <what> <log>
  local deadline=$((SECONDS + 90))
  until curl -s -o /dev/null --max-time 2 "http://localhost:$1/"; do
    if (( SECONDS >= deadline )); then
      say "FATAL: $2 did not answer on :$1; see $3"
      return 1
    fi
    sleep 2
  done
}

if (( SERVE_REACT == 1 )); then
  if [[ -d "$ROOT/gms_frontend/node_modules" ]]; then
    say "Starting the React SPA on :$REACT_PORT …"
    # Vite 7 treats stdin EOF as "the parent died" and shuts the server down. A background
    # runner has no TTY, so that EOF arrives at once; CI=true makes Vite skip the listener.
    (cd "$ROOT/gms_frontend" && CI=true exec npm run dev -- --port "$REACT_PORT" --strictPort) \
      >/tmp/gms-demo-react.log 2>&1 &
    pids+=($!)
    wait_for_port "$REACT_PORT" "the React SPA" /tmp/gms-demo-react.log || die
  else
    say "React SPA not started: gms_frontend/node_modules is missing (cd gms_frontend && npm ci)."
  fi
fi

if (( SERVE_FLUTTER == 1 )); then
  if command -v flutter >/dev/null 2>&1; then
    if [[ ! -f "$ROOT/mobile/build/web/index.html" ]]; then
      say "Building the Flutter web client (first run, a few minutes) …"
      (cd "$ROOT/mobile" && flutter build web --release --dart-define="API_BASE_URL=$BASE") \
        || die "flutter build web failed"
    fi
    say "Starting the Flutter web client on :$FLUTTER_PORT …"
    (cd "$ROOT/mobile" && exec python3 -m http.server "$FLUTTER_PORT" --directory build/web) \
      >/tmp/gms-demo-flutter.log 2>&1 &
    pids+=($!)
    wait_for_port "$FLUTTER_PORT" "the Flutter web client" /tmp/gms-demo-flutter.log || die
  else
    say "Flutter web not started: the flutter command is not on PATH."
  fi
fi

cat <<EOF

Demo is running.

  React SPA     http://localhost:$REACT_PORT
  Flutter web   http://localhost:$FLUTTER_PORT
  API           $BASE/api/v1

  The access sheet above lists the six logins and the one demo password.
  Ctrl-C stops the web servers (the backend and database are left running).

EOF

wait
