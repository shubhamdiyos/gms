#!/usr/bin/env bash
# Shared CSRF bootstrap for the HTTP suites.
#
# The application protects every state-changing request with the double-submit cookie pattern
# (`SecurityConfig`): `GET /api/v1/auth/csrf-token` issues the `XSRF-TOKEN` cookie, and the
# request must echo the same value in `X-XSRF-TOKEN`. The SPA and the Flutter client already
# do this; the suites authenticate with a bearer token and therefore never sent a cookie at
# all, so enabling the protection turned their first POST into a 403 that named nothing.
#
# This library is not a bypass and not a test-only switch: it performs the same handshake a
# browser does. Usage, from a suite that already defines `$API`:
#
#     . "$(dirname "${BASH_SOURCE[0]}")/lib/csrf.sh"
#     ...
#     args+=("${CSRF_ARGS[@]}")          # inside the request helper, before curl runs
#
# `csrf_prepare` fetches the token once per run and leaves the cookie jar in `$CSRF_JAR`, so
# every call after the first reuses one token/cookie pair. It returns 1 when the handshake
# itself fails, which is an infrastructure fault (the token route is permitted anonymously)
# and should stop the suite rather than be reported as a product finding.

CSRF_JAR=${CSRF_JAR:-$(mktemp "${TMPDIR:-/tmp}/gms-csrf.XXXXXX")}
CSRF_ARGS=()

csrf_prepare() { # csrf_prepare <api-root>; fills CSRF_ARGS, returns 1 on failure
  local base=$1 token
  if (( ${#CSRF_ARGS[@]} > 0 )); then
    return 0
  fi
  token=$(curl -s -c "$CSRF_JAR" -b "$CSRF_JAR" "$base/auth/csrf-token" \
    | jq -r '.token // empty' 2>/dev/null)
  if [[ -z "$token" ]]; then
    printf 'FATAL: GET %s/auth/csrf-token issued no token; every POST would be answered 403\n' \
      "$base" >&2
    return 1
  fi
  CSRF_ARGS=(-b "$CSRF_JAR" -c "$CSRF_JAR" -H "X-XSRF-TOKEN: $token")
}

# The JWT a successful login issued, read out of the jar that login wrote.
#
# `AuthController.login` puts the token in the HttpOnly `gms_auth` cookie and returns a null
# `accessToken`, so a suite that used to read `.accessToken` from the body now reads it here.
# The cookie is removed once its value is known so that a request the suite means to send
# anonymously really is anonymous: the jar is shared by every request in the run, and a
# surviving `gms_auth` would authenticate even the calls that pass no token. Bearer precedence
# in `JwtAuthenticationFilter` (an explicit header wins over the ambient cookie) covers the
# calls that do pass one; this covers the ones that do not.
csrf_auth_token() {
  local token tmp
  token=$(awk -F'\t' '$6 == "gms_auth" { value = $7 } END { print value }' "$CSRF_JAR" 2>/dev/null)
  if [[ -n "$token" ]]; then
    tmp=$(mktemp "${TMPDIR:-/tmp}/gms-csrf-auth.XXXXXX")
    awk -F'\t' '$6 != "gms_auth"' "$CSRF_JAR" > "$tmp" && mv "$tmp" "$CSRF_JAR"
  fi
  printf '%s' "$token"
}

# The one-time administrator credential a `/schools/create` response carried.
#
# The creation response is the only moment the plaintext exists — `SchoolServiceImpl` stores a
# BCrypt hash and nothing else — and its password is random since ACD-13, so a suite can no
# longer derive it from the administrator's name. Read it immediately after the create call,
# before the next request overwrites the body file. Sets ISSUED_ADMIN and
# ISSUED_ADMIN_PASSWORD; returns 1 when the response carried neither, which means provisioning
# changed shape and the caller has no credential to sign in with.
csrf_issued_admin() { # csrf_issued_admin <json file>
  ISSUED_ADMIN=$(jq -r '.adminUsername // empty' "$1" 2>/dev/null)
  ISSUED_ADMIN_PASSWORD=$(jq -r '.temporaryPassword // empty' "$1" 2>/dev/null)
  if [[ -z "$ISSUED_ADMIN" || -z "$ISSUED_ADMIN_PASSWORD" ]]; then
    printf 'FATAL: the creation response carried no one-time administrator credential\n' >&2
    return 1
  fi
}

# Rotate an account's password through the API, the way a first sign-in would.
#
# Every account the server issues a credential for is flagged requirePasswordChange, and
# `PasswordChangeEnforcementFilter` answers any other route 403 until the flag clears. The JWT
# keeps working across the rotation — the filter reads the flag from the freshly loaded user on
# every request — so the caller goes on using the token it signed in with. Prints the HTTP
# status; the response body is discarded because the request has none worth reading.
csrf_rotate_password() { # csrf_rotate_password <api-root> <token> <current> <new>
  local base=$1 token=$2 current=$3 wanted=$4 payload
  csrf_prepare "$base" || return 1
  payload=$(jq -nc --arg o "$current" --arg n "$wanted" '{oldPassword:$o,newPassword:$n}')
  curl -s -o /dev/null -w '%{http_code}' -X POST "$base/auth/change-password" \
    "${CSRF_ARGS[@]}" -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' -d "$payload"
}
