# GMS Infrastructure Audit — Phase 0 (M0)

Scope: container architecture, CI/CD, runtime, networking, persistence, observability and delivery
engineering, as they exist on this branch today. Written for the second MASTER ENGINEERING MANDATE
(*Production-Grade Infrastructure, Container Architecture, DevOps & CI/CD Modernization*), §3.

**This file does not restate [REPOSITORY_AUDIT.md](../audit/REPOSITORY_AUDIT.md).** That document owns the
application findings (`SEC-`, `TEN-`, `FIN-`, `DB-`, `FE-`, `OPS-`, `DOC-`, `BKL-`). This file owns `INF-`
findings about *how the software is built, shipped, run and recovered*, and it reuses an `OPS-` id only by
linking to it, never by copying it.

Baseline measured on branch `infra/gms-platform-audit` at commit `7a75d39` (the merge of PR #10), 2026-11-01.
Docker Engine 29.5.2, Docker Compose v5.6.1, macOS arm64 host.

---

## 1. Method — what was actually executed

Nothing below is inferred from filenames. Every finding cites a command that ran on this machine or an API
response retrieved this session.

| # | Command / query | What it established |
|---|---|---|
| 1 | `docker ps --format …{{.Ports}}` | `gms-postgres` is live and bound to `0.0.0.0:5432` **and** `[::]:5432` — INF-02 is not theoretical |
| 2 | `docker inspect gms-postgres` (Config.Env keys only, values never printed) | 27 environment keys inside the database container — INF-05 |
| 3 | same, `HostConfig` | `RestartPolicy=no`, `CapDrop=null`, `ReadonlyRootfs=false`, `SecurityOpt=null`, `Memory=0`, `NanoCPUs=0`, `User=""`, `LogConfig={json-file, {}}` — INF-06/07/08 |
| 4 | `docker compose config --quiet` → exit 0 | the compose file parses; M1's `version:`/`${DB_PASSWORD:?}` fixes hold — INF-22 |
| 5 | `make build` → `make: docker-compose: No such file or directory`, `Error 1`; `make up` → exit 2 | the documented container workflow is entirely broken on the current toolchain — INF-12 |
| 6 | `docker compose build backend frontend` | performed as runtime validation (mandate §13); reported in §7 below |
| 7 | `npx eslint . --format json` | 47 errors / 22 files, 0 warnings — revalidates the carried-forward figure exactly |
| 8 | `gh api repos/…/branches/master/protection` | `required_status_checks.contexts=[]`, `strict=false`, `enforce_admins.enabled=false`, `required_approving_review_count=1`, `require_last_push_approval=true` — INF-10 |
| 9 | `gh api repos/…/environments/{Production,Preview}` | both exist; both have `protection_rules: []`; both have `can_admins_bypass: true` — INF-10 |
| 10 | `gh secret list` | `EC2_HOST`, `EC2_SSH_KEY`, `EC2_USER` exist. `EC2_SSH_KNOWN_HOSTS` does not. **No** `SECURITY_JWT_SECRET` / `APP_SUPERADMIN_*` |
| 11 | `gh run list` / `gh run view 41245769175 --log-failed` | the deploy workflow executed on `master` at 2026-10-31T21:54:46Z, was refused by the host-key guard, and then independently recorded `ssh: connect to host … port 22: Connection timed out` at 21:55:01Z — §6 |
| 12 | `gh api repos/…/actions/runs` | CI run 41245769121 succeeded in 50 s wall time on the merge commit |
| 13 | `gh api repos/…/actions/artifacts` | 6 live artifacts, 162,252,049 bytes = 154.74 MB — INF-16 |
| 14 | `gh api accounts/shubhamdiyos/settings/billing/actions` → HTTP 404 | metered Actions usage is **not** readable through the REST API for a personal account; the quota check in §5 is arithmetic, not telemetry |
| 15 | `psql 'select pg_size_pretty(pg_database_size('gms'))'` and a `pg_dump \| gzip -1` on the **local dev** database | 9.4 MB database, 12.7 KB compressed dump — the backup sizing input in §5.3 |
| 16 | `grep -rn "logging.pattern" gms_backend/src/main/resources` → no match, plus reading `TraceIdFilter.java` | the filter *is* registered; no log pattern prints the id — INF-15, and a correction (§6.6) |

Where a claim could not be tested, it is in [§8](#8-what-could-not-be-tested-and-what-is-blocked-on-the-owner), not in a finding.

---

## 2. As-is topology

Three buildable units, one compose file, one network, no proxy, no TLS term, no IaC.

```
github.com/shubhamdiyos/gms  (PUBLIC repo, default branch `master`)
│
├── .github/workflows/ci.yml ................. PR + push(master): mvn verify, npm lint/build  [advisory only]
├── .github/workflows/deploy-backend.yml ..... push(master): ssh → ~/deploy_backend.sh          [no build, no env gate]
│                                            ✗ the remote script is NOT in this repository
│
├── docker-compose.yml ....................... ONE flat bridge network `gms-v1_gms-network`
│     postgres:16-alpine ......... host:5432 → 0.0.0.0  (published on every interface)
│     backend  (Dockerfile) ...... host:8080 → 0.0.0.0
│     frontend (Dockerfile) ...... host:3000 → :80      nginx, no /api proxy, no TLS
│     volumes: postgres_data (named) — no backup consumer anywhere
│
├── gms_frontend/vercel.json:5 ............... /api/:path* → http://<EC2 public DNS>:8080/api/:path*
│                                              (plaintext, port 8080 open, hostname committed to a public repo)
└── no reverse proxy · no certificate · no staging · no IaC · no registry · no artefact lineage
```

Runtime facts measured, not assumed:

| Property | postgres | backend | frontend |
|---|---|---|---|
| runs as | root (`User=""`) | root (no `USER` in Dockerfile) | root (nginx master) |
| restart policy | `no` | `no` | `no` |
| memory / CPU limit | none (`Memory=0`, `NanoCPUs=0`) | none | none |
| `cap_drop` / `no-new-privileges` / `read_only` | none / none / false | none / none / false | none / none / false |
| log driver | `json-file`, **no max-size, no max-file** | same | same |
| healthcheck | `pg_isready` (defined) | `wget /actuator/health/readiness` (defined) | **none** |
| graceful shutdown | n/a | **none** — `server.shutdown` is unset in all three profiles | n/a |
| JVM container flags | n/a | **none** — no `-XX:MaxRAMPercentage`, no `JAVA_TOOL_OPTIONS` | n/a |

Image sizes on this host, for scale: `maven:3.9-eclipse-temurin-21` 847 MB (build stage),
`eclipse-temurin:21-jre-alpine` 287 MB (runtime base), `postgres:16-alpine` 411 MB.

---

## 3. Findings

Severity vocabulary is the mandate's: **CRITICAL / HIGH / MEDIUM / LOW / INFORMATIONAL**.
Each finding carries evidence, impact, remediation, cost and verification criteria — a remediation is not
accepted until its verification criterion has been executed and recorded.

### INF-01 · CRITICAL — Credentials and student data cross the public internet in plaintext

**Evidence.** `gms_frontend/vercel.json:5` rewrites `/api/:path*` to `http://<ec2 public dns>:8080/api/:path*`.
There is no certificate, key, `listen 443`, `ssl_certificate` or TLS term anywhere in the repository:
`grep -rliE "ssl_certificate|443" --include=*.conf --include=*.yml --include=Dockerfile` returns nothing, and
`gms_frontend/nginx.conf:1` listens on 80 only. Port 8080 is published on all interfaces (§1 row 1).

**What is and is not protected.** Browser → Vercel is HTTPS with a Vercel-managed certificate. The
Vercel → EC2 hop is plaintext HTTP over the public internet and carries every `Authorization: Bearer <JWT>`,
every password on `/api/v1/auth/login`, and every student, guardian and payment record. A direct API client
(the two Postman collections, `scripts/verify-security.sh`, any future mobile app) is plaintext end to end.
If the containerised frontend is ever used instead of Vercel, there is no TLS at all.

**Impact.** Credential and PII interception on the one hop the owner controls; a captured JWT is a bearer
token valid for `SECURITY_JWT_VALIDITY_SECONDS=3600` with no revocation store, so capture is not just
disclosure — it is account takeover within the captured user's tenant.

**Remediation.** Put a TLS terminator in front of the backend on the host (Caddy or nginx + a certificate),
close public 8080 (INF-02/04), point `vercel.json` at `https://`, and make plaintext API access a configuration
error rather than the default. Caddy is preferred for zero-cost operation because it automates issuance and
renewal without a cron or a DNS provider API token.

**Cost.** The software is $0. The blocker is identity: Let's Encrypt will not issue for
`*.ap-south-1.compute.amazonaws.com` because the owner does not control that DNS zone, and it cannot issue for
`*.vercel.app` either. A real certificate therefore requires **a domain the owner controls** — roughly
$10–15/year, which is recurring spend and needs explicit approval under mandate §2 before anything is bought.
**No domain is to be purchased by this programme.** Until then the honest state is "HTTPS to Vercel, plaintext
Vercel→origin", and the interim mitigation is to remove the public 8080 binding so that only the Vercel hop can
reach the API.

**Verification.** `curl -sSI https://<origin>/api/v1/ping` returns 200 over TLS 1.2+ with a trusted certificate;
`curl -sS http://…` redirects to HTTPS; `nmap`/`nc` from an external host shows 8080 and 5432 closed; certificate
renewal is observed to succeed unattended once, and the failure path emits a signal.

---

### INF-02 · CRITICAL — The database is reachable from every network interface of the host

**Evidence.** `docker ps` on this machine, right now: `gms-postgres … 0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp`.
That binding comes from `docker-compose.yml:12-13`, which publishes 5432 unconditionally, and the same file
publishes 8080 (`:41-42`) and the frontend's 80 as 3000 (`:61-62`). Docker inserts its own `iptables` rules that
bypass a UFW-managed allow-list, so "the firewall blocks 5432" is not a safe assumption on a Docker host.

**Impact.** On any host with a routable interface — which the EC2 instance has — `postgres:16` is exposed to the
internet with a single credential and no rate limit. A successful guess is total data loss *and* total tenant
compromise in one step, because the schema is multi-tenant with `school_id` columns, not separate databases.
It also makes the backup/restore story (§9 of the mandate) moot: an attacker need not exfiltrate a dump.

**Remediation.** In `compose.prod.yaml`, bind loopback only (`127.0.0.1:5432:5432`) or omit the port mapping
entirely and reach Postgres through `docker compose exec`; make the reverse proxy the only published service.
Keep the developer-facing binding in `compose.dev.yaml` where loopback-only is still the default.

**Cost.** $0.

**Verification.** From an external host, `nc -zv <ec2> 5432` and `nc -zv <ec2> 8080` both fail;
`docker ps` shows no non-loopback binding for 5432; the application still functions through the proxy; the
existing `scripts/verify-security.sh` still passes against the proxied URL.

---

### INF-03 · CRITICAL — Production's runtime topology cannot be derived from the repository

**Evidence.** `.github/workflows/deploy-backend.yml:90` executes `ssh … 'test -f ~/deploy_backend.sh && bash ~/deploy_backend.sh'`.
`git ls-files | grep -i deploy` returns only the workflow itself: the script is not versioned. `git ls-files`
contains no Terraform, Pulumi, Ansible, cloud-init or systemd unit — there is no infrastructure-as-code of any
kind. The one attempt to observe the live host (run 41245769175, 2026-10-31T21:55:01Z) failed with
`ssh: connect to host … port 22: Connection timed out`, so the host's OS, packages, disk, security groups and
running containers are unknown as of this audit.

**Impact.** Nothing about production is reproducible, reviewable or recoverable. The image tag actually running,
the profile it boots with, whether `ddl-auto` is `update` or `validate`, where the volume lives and whether it is
dumped — all are decided by an unreviewed file on a machine that currently answers no connection. This is the
single finding that makes rollback (mandate §7) and DR (§12) impossible rather than merely undocumented, and it
is why several readiness rows in the matrix are `BLOCKED` instead of `NOT_IMPLEMENTED`.

**Remediation.** Version the host-side deployment as `scripts/deploy.sh` (or a `compose.prod.yaml` pulled by a
systemd-timer/cron `git pull`), define the instance in code, and capture the current host state into
`docs/infrastructure/` once SSH is restored. Add the host fingerprint as `EC2_SSH_KNOWN_HOSTS`.

**Cost.** $0 for tooling. Reaching the host may cost the owner an EC2 console session — if the instance was
stopped, restarting it is within the already-paid footprint; **replacing** it is not in scope (§2 says evaluate
the existing EC2 before recommending a replacement).

**Verification.** `git ls-files` contains the exact script executed on the host; a clean clone + that script
reproduces the running stack on a second machine; `docker compose config` on the host matches the repository
file byte-for-byte.

---

### INF-04 · HIGH — One flat network gives the public-facing container a direct route to the database

**Evidence.** `docker-compose.yml:21-22,46-47,65-66` place all three services on the single `gms-network` bridge;
`docker inspect gms-postgres` confirms exactly one attached network (`gms-v1_gms-network`). There is no
`internal:` network and no service-level isolation.

**Impact.** Compromise of the nginx container — which serves untrusted input on port 80 — yields same-L2 access
to Postgres and to the backend's 8080, bypassing every `SecurityConfig` rule. `depends_on` (`:63-64`, shorthand
list form) is start-order only and is not health or isolation, which mandate §4.5 names explicitly.

**Remediation.** Two networks: `edge` (proxy ↔ backend) and `data` (backend ↔ postgres, `internal: true`). The
proxy is the only container with a published port; the frontend becomes static assets served by the proxy, or a
container on `edge` only.

**Cost.** $0.

**Verification.** `docker network inspect` shows postgres attached only to an `internal` network; `docker exec
gms-frontend nc -zv gms-postgres 5432` fails; `docker exec gms-backend pg_isready -h postgres` still succeeds.

---

### INF-05 · HIGH — The database container is handed the entire application secret file

**Evidence.** `docker-compose.yml:6-7` sets `env_file: [.env]` on the `postgres` service. `docker inspect
gms-postgres` lists 27 keys, and among them — names only, values were not printed — are `MAIL_PASSWORD`,
`MAIL_USERNAME`, `CORS_ALLOWED_ORIGINS`, `JPA_DDL_AUTO`, `FLYWAY_ENABLED`, `JPA_SHOW_SQL`, `LOG_LEVEL_BOOTSTRAP`,
`LOG_LEVEL_COM_GMS`, `DB_PASSWORD`, `DB_USERNAME`, `DB_URL`-adjacent keys. Postgres needs three of these.
`SECURITY_JWT_SECRET` and `APP_SUPERADMIN_PASSWORD` are absent from the local container only because this
machine's `.env` leaves them blank — on a configured host they would be inside the database container too.

**Impact.** Every value is readable by anyone who can run `docker inspect` — i.e. any member of the `docker`
group, which mandate §11 says to treat as privileged. It widens the blast radius of a low-privilege host
compromise from "can read mail credentials" to "can read the signing key", and it violates least privilege as
literally worded in §9 ("database credentials are least-privilege").

**Remediation.** Give each service an explicit `environment:` block with only what it consumes; reserve
`env_file` for the backend, and split it into `gms-backend.env` / `gms-proxy.env` files. Never let a service
inherit a file written for another service.

**Cost.** $0.

**Verification.** `docker inspect` on each container lists only its own keys; a scripted assertion in CI fails
if `SECURITY_JWT_SECRET` or `MAIL_PASSWORD` appears in any container other than the backend.

---

### INF-06 · HIGH — Nothing restarts, and nothing bounds log growth

**Evidence.** `docker inspect` reports `RestartPolicy=no`; `grep -n "restart:" docker-compose.yml` finds no
match. `LogConfig` is `json-file` with an empty options map — no `max-size`, no `max-file`. The EC2 instance size
and root volume are unknown (INF-03), so headroom cannot be quantified.

**Impact.** Two independent outages. (a) A host reboot — or any Docker daemon restart — leaves GMS down until a
human notices, which contradicts §4.1 "predictable startup" and §12 "host reboot recovery". (b) Spring Boot logs
at `INFO` per request through `GlobalExceptionHandler` and `TraceIdFilter`; an unbounded `json-file` on a small
root volume fills the disk, and on this stack disk exhaustion takes Postgres with it, turning a log-hygiene
problem into data unavailability. This is precisely the "is disk space running low" question from §10 that
currently has no answer.

**Remediation.** `restart: unless-stopped` on application services (never `always`, so an operator's deliberate
`stop` survives); `logging: {driver: json-file, options: {max-size: 10m, max-file: 5}}` as a compose anchor
applied to every service; a scheduled `df -h` check that alerts; and `--log-opt` defaults documented before any
new host is provisioned.

**Cost.** $0.

**Verification.** `docker compose restart`/host reboot brings the stack back to `healthy` with no human action —
measured by rebooting a test host, not asserted; `docker info` / `docker inspect` shows the rotation options;
the log directory stays under a stated byte ceiling for 24 h of traffic.

---

### INF-07 · HIGH — No container has a resource limit, and the JVM has no heap policy

**Evidence.** `Memory=0` and `NanoCPUs=0`/`null` on the live container (§1 row 3); no `deploy.resources.limits`, no
`mem_limit`, no `cpus` anywhere in `docker-compose.yml`. `gms_backend/Dockerfile:24` is
`ENTRYPOINT ["java", "-jar", "app.jar"]` — no `-XX:MaxRAMPercentage`, no `-XX:+UseContainerSupport` expression,
no `JAVA_TOOL_OPTIONS`, and no `-Xmx`.

**Impact.** On the shared EC2 host, Postgres and the JVM compete for the same unbounded memory. A Spring Boot
process with an unset heap will grow toward the *host's* RAM rather than the container's, and the kernel OOM
killer then chooses a victim — historically the database. One busy report query can therefore cause an outage
that no application test would predict. It also defeats §4.1's "explicit resource limits where supported".

**Remediation.** Set per-service limits that sum below the host's capacity with headroom, give the JVM
`-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError`, and prefer `restart: unless-stopped` so a killed JVM
recovers. Record the chosen numbers with the host size that justifies them (blocked on INF-03 for the real host;
start from the dev box).

**Cost.** $0 — and it is the argument for not enlarging the instance.

**Verification.** `docker stats` shows the ceiling enforced; a deliberate memory spike OOM-kills only the
offending container, Postgres survives, and the backend returns to `healthy` automatically.

---

### INF-08 · HIGH — Every container runs as root with no capability drops

**Evidence.** `gms_backend/Dockerfile` and `gms_frontend/Dockerfile` contain no `USER` directive; the runtime
images (`eclipse-temurin:21-jre-alpine`, `nginx:alpine`) default to root. `docker inspect` shows `CapDrop=null`,
`SecurityOpt=null` (so no `no-new-privileges:true`) and `ReadonlyRootfs=false`. Observed rather than inferred for
the built images: `Config.User` is empty on both and `id -u` returns **0** inside `gms-v1-backend:latest` (§7.1).

**Impact.** Any remote code execution in the app becomes root in the container and, with the default Docker
socket mount and no user namespace, is one step from root on the host. Mandate §11 treats this directly.

**Remediation.** `USER 10001:10001` in both images with correct file ownership; `cap_drop: [ALL]` plus
`NET_BIND_SERVICE` only where a service truly needs it (i.e. nowhere, if the proxy listens on 8080/8443
above 1024 and the kernel forwards); `security_opt: [no-new-privileges:true]`; `read_only: true` with explicit
writable tmpfs for whatever genuinely needs to write. Note the honest caveat: nginx needs a writable
`/var/cache/nginx` and `/var/run`, and Spring Boot needs a writable temp dir — these must be enumerated per
service, tested, and not enabled blind (§4.3: "Do not blindly enable security settings that break application
behavior").

**Cost.** $0.

**Verification.** `docker top <ctr> -o user` is non-root for all three services; the full stack still starts and
passes the runtime suite in [§7](#7-runtime-validation-performed-during-m0); a deliberate write outside the tmpfs
paths fails, proving `read_only` is real.

---

### INF-09 · HIGH — Builds are not reproducible, and the deployed image is not traceable to a commit

**Evidence.** `gms_frontend/Dockerfile:18` is `FROM nginx:alpine` — no version tag at all, so it resolves to
whatever `:latest` means on the day the image is built. `:2` `node:20-alpine`, `:9` `RUN npm install` (not
`npm ci`, and the comment on `:8` says the lockfile is deliberately not trusted), `postgres:16-alpine` and
`eclipse-temurin:21-jre-alpine` in `docker-compose.yml:4` and `gms_backend/Dockerfile:2,14` are floating majors.
`gms_backend/Dockerfile:11` runs `mvn clean package -DskipTests` — the artefact that ships has never run the
suite. No image is built by CI at all (`ci.yml` builds a jar; `deploy-backend.yml` builds nothing), so there is
no tag, no digest, no `org.opencontainers.image.revision` label and no link from a running container back to a
commit.

**Impact.** Two builds of the same commit can differ; a `latest` drift can change production behaviour without a
code change; and when something breaks there is no way to answer "which commit is running". Mandate §6.2 forbids
exactly this ("Never deploy arbitrary mutable `latest` images as the only release identifier").

**Remediation.** Pin every base image to a digest with a documented renewal cadence; `npm ci` against the
committed lockfile; run tests in CI and build the image from the tested artefact; tag images
`<git-sha>` (plus `sha256:<digest>` recorded at deploy time); add OCI labels for revision, branch and build
time. Also cache the Maven repository between builds — the 139.5 s `dependency:go-offline` step in §7.1 is the
price of not doing so, and it is why builds get "optimized" by skipping checks later. Registry choice is recorded
as [ADR-004](INFRASTRUCTURE_DECISIONS.md).

**Cost.** $0 for GitHub Actions on a public repository (runner minutes are free for public repos) and $0 for
GHCR public images; private-image storage shares the same quota pressure as INF-16, so keep the release image
**public** or the storage math gets worse.

**Verification.** Two consecutive builds of one commit produce identical layer digests; `docker inspect
<release image>` reports the OCI revision label matching `git rev-parse HEAD`; deploying `latest` alone is
refused by the workflow.

---

### INF-10 · HIGH — CI is advisory, deploy is unguarded, and the Environments that exist have no protection

**Evidence.** `gh api …/branches/master/protection`: `required_status_checks.contexts = []`, `strict = false`,
`enforce_admins.enabled = false`. Reviews are configured (`required_approving_review_count = 1`,
`require_last_push_approval = true`, `dismiss_stale_reviews = true`) but admins bypass them — and PR #10 was in
fact merged with `--admin` on 2026-10-31, which is on the record in
[BASELINE_STATUS.md §11](../audit/BASELINE_STATUS.md). `gh api …/environments/Production` and `/Preview` both
return `protection_rules: []` with `can_admins_bypass: true`. Neither workflow declares `environment:`
(`grep -n environment .github/workflows/*.yml` → no match), and `deploy-backend.yml` has no top-level
`permissions:` block, so every run receives a default-writable `GITHUB_TOKEN`. `ci.yml:32` does declare
`permissions: {contents: read}`.

**Impact.** A green `ci.yml` is not what stops a merge; a maintainer with push access can merge unverified code,
and merging to `master` immediately and automatically SSHes to production with no approval, no artefact
verification and no build step (OPS-01). The two environments are decoration, not gates.

**Remediation.** Make `Backend build and tests` and `Frontend lint and build` required checks (with `strict`),
set `enforce_admins: true`, attach `environment: production` with required reviewers to the deploy job, narrow
`permissions` in `deploy-backend.yml` to `contents: read`, and pin `webfactory/ssh-agent` to a SHA. The gate must
be turned on in repository settings, which is an owner action; the workflow edits are code and land in Infra-M2.

**Cost.** $0.

**Verification.** A PR that fails `ci.yml` cannot be merged *even as admin* (attempt it on a throwaway branch and
record the refusal); a deploy run shows an explicit `waiting` → approved transition attributable to a named
reviewer; the token for a deploy run is read-only (`gh api` on the job's permissions).

---

### INF-11 · HIGH — There is no staging, and the production profile has never booted

**Evidence.** `application-prod.properties` exists and is written correctly (`ddl-auto=validate`,
`flyway.baseline-on-migrate=false`, no defaults), but it has never been executed: booting it requires the Neon
credential that is still unrotated (SEC-01) and mandate §25 forbids connecting to production without
authorisation. `docker-compose.yml:39` sets `SPRING_PROFILES_ACTIVE` with a `dev` default, so the *only* container
configuration in the repository boots the relaxed profile. GitHub's `Preview` environment has no jobs and no
protection rules.

**Impact.** The first time the production configuration runs is the deployment itself — with no schema history
(OPS-07), no TLS and no gate. `ddl-auto=validate` against a Hibernate-`update`-created schema is likely to fail
at boot, which converts "deploy" into "outage" until the baseline exists.

**Remediation.** Create a compose-based staging stack on the existing host (or a second container set with its
own volume) fed by a **masked sample dataset** (mandate §25.16 forbids fabricated data in production dashboards;
a staging-only fixture is not a dashboard); boot `prod` there; require a green staging boot before the deploy
gate clears. A second paid instance is not assumed affordable (§5 of the mandate says so explicitly), so the
plan is one host, two stacks, isolated by network and port — recorded in [ADR-008](INFRASTRUCTURE_DECISIONS.md).

**Cost.** $0 incremental if it fits the existing instance's headroom, which is **unmeasured** (INF-03, INF-07).
If it does not fit, the options are a larger instance (recurring spend — approval required) or no staging.
This must be decided with numbers, not optimistically.

**Verification.** A `SPRING_PROFILES_ACTIVE=prod` boot succeeds against a schema built only by migrations; the
staging URL is unreachable from outside; a documented runbook shows the prod-parity deploy on staging.

---

### INF-12 · HIGH — The documented container workflow does not run, and one target deletes the data

**Evidence.** Every one of the 15 recipes in `Makefile` invokes `docker-compose` (v1): `make build` fails here
with `make: docker-compose: No such file or directory` → `Error 1`, and `make up` exits 2. The binary is not
installed; Compose v5.6.1 is available as `docker compose`. `make clean` (`:65-67`) is `docker-compose down -v`
with the warning printed *after* the destructive call, and `.PHONY` (`:1`) omits seven declared targets.
`Makefile:102` hardcodes `psql -U gms_user -d gms` even though both are templated in compose.

**Impact.** A new developer following the README cannot start the stack at all, so the practical fallback is to
improvise — which is how data-destroying commands get run in desperation. `make clean` silently deletes
`postgres_data`; there is no backup to restore from (INF-13), so `make clean` is currently equivalent to
"destroy the database".

**Remediation.** Replace all 15 recipes with `docker compose`, add `-f compose.yaml -f compose.dev.yaml`, guard
`clean` behind an explicit confirmation variable, and add `test`, `migrate`, `db-backup`, `db-restore` and
`config-validate` targets so the operational verbs in §12 of the mandate are reachable from the repository
rather than from memory.

**Cost.** $0.

**Verification.** `make build`, `make up`, `make ps` succeed on a clean clone; `make clean` refuses to run without
the confirmation token; `make db-backup` produces a file that `make db-restore` accepts in an isolated directory.

---

### INF-13 · HIGH — Data durability has exactly one copy and no recovery path

**Evidence.** One named volume (`docker-compose.yml:68-71`), no dump script (`git ls-files` has no backup file),
no restore procedure, no off-host copy, no rotation, no integrity check. Cross-ref
[OPS-16](../audit/REPOSITORY_AUDIT.md) — recorded as absent in the first audit; this pass adds the measurement
needed to plan around it. The **local dev** database is 9.4 MB and its `pg_dump | gzip -1` is 12.7 KB, so storage
is not the constraint; *placement* is.

**Impact.** A failed EBS volume, an `rm`, or the INF-02 exposure ends the product. §9 of the mandate ("Never store
production data only inside an ephemeral container filesystem") is nominally satisfied by the volume but the
"…and verify a restore" half is entirely unmet, and §12 forbids inventing RPO/RTO numbers — so today the honest
RPO is "everything since the last manual dump", which is none.

**Remediation.** A versioned `scripts/db-backup.sh`: `pg_dump -Fc`, encrypt (age or GPG with an owner-held key),
checksum, retain 7 daily + 4 monthly, write an `INDEX.json`, and `scripts/db-restore.sh` that restores into a
throwaway container and reports row counts per table. Off-host placement is the owner's cost decision
([ADR-009](INFRASTRUCTURE_DECISIONS.md)) — on-host-only is *not* a backup and must be labelled as such wherever
it appears.

**Cost.** $0 on-host. Any *real* (off-host) backup needs a second location: S3 at this data size is well under
$1/month ($0.023/GB-month for Standard, and a dump measured in MB), a second small instance is not, and
Backblaze B2's 10 GB free tier requires creating an account, which mandate §2 forbids without approval.
Recommendation: ask the owner to approve exactly one off-host destination; do not provision it unilaterally.

**Verification.** A restore is executed end-to-end into an isolated container from a dump produced by the script;
the restored row counts match the source; a corrupted dump fails loudly with a checksum error; the encryption
key is proven to be outside the repository and outside the image.

---

### INF-14 · MEDIUM — The deploy health gate depends on the exposure that must be closed

**Evidence.** `deploy-backend.yml:99` probes `http://$EC2_HOST:8080/api/v1/ping`, and `:96` retries ten times
before failing. `gms_frontend/nginx.conf` has no `/api` proxy block, so the containerised frontend cannot reach
the API at all (cross-ref [OPS-10](../audit/REPOSITORY_AUDIT.md)).

**Impact.** Fixing INF-01/INF-02 by closing public 8080 breaks the only post-deploy check that exists. These two
must be designed together or the security fix silently removes the availability signal — the classic way a
hardening branch ships an unmonitored production.

**Remediation.** Move the health check behind the proxy over HTTPS, or run it on-host (`curl
http://127.0.0.1:8080/actuator/health/readiness` through the existing SSH session) so it needs no public port.
Separate liveness from readiness as §10 requires, and verify the deployed SHA and image digest as §6.3 requires.

**Cost.** $0.

**Verification.** The deploy job passes with 8080 and 5432 closed to the internet and fails when the proxied
health endpoint returns non-200; the job prints the running commit SHA and refuses if it differs from the
triggering commit.

---

### INF-15 · MEDIUM — Logs, metrics and version identity are all present in the code and absent in operations

**Evidence.** `gms_backend/src/main/java/com/gms/util/TraceIdFilter.java:16-18` registers the filter as a
`@Component` at `HIGHEST_PRECEDENCE` and it does put `MDC` (`:26`) and echo `X-Trace-Id` (`:28`) — but
`grep -rn "logging.pattern" gms_backend/src/main/resources` finds **no** pattern, so Spring's default pattern has
no `%X{traceId}` and the id never reaches the log stream. `pom.xml:59-60` declares
`micrometer-registry-prometheus`, while `application.properties:65` exposes only `health,info`, so
`/actuator/prometheus` is never served. `info` has no `build.*` contributor configured, so "which version is
running" (§10) is unanswerable. `GlobalExceptionHandler` already emits `traceId=` into its own messages by
reading MDC, which is the one place the correlation survives.

**Impact.** The §10 monitoring baseline — is it up, is the DB reachable, are errors increasing, is disk full,
which version, when was the last deploy — cannot be met by any existing channel, and multi-instance log
correlation is impossible despite the code being written for it.

**Remediation.** Add `logging.pattern.console=%d{ISO8601} [traceId=%X{traceId}] %-5level %logger{36} - %msg%n`
(or structured JSON via `logback-spring.xml`), expose `health,info,prometheus` with prometheus behind the
authenticated `/actuator/**` rule already in `SecurityConfig:115`, enable `management.info.env.enabled` with
build properties injected from CI, and ship a log-rotation policy consistent with INF-06.

**Cost.** $0 — Prometheus/Grafana is deliberately *not* adopted yet; see
[ADR-010](INFRASTRUCTURE_DECISIONS.md) for the footprint reasoning.

**Verification.** A request's `X-Trace-Id` is findable in the container log for that request; `/actuator/prometheus`
returns metrics only for an authenticated caller; the deployed version reported by `/actuator/info` equals the
commit the workflow just deployed.

---

### INF-16 · MEDIUM — CI uploads a 53 MB artefact that nothing downloads

**Evidence.** `ci.yml:36-40` uploads `gms-backend-jar` on every run. `gh api …/actions/artifacts`: 6 live
artefacts totalling 162,252,049 bytes (154.74 MB) from three runs — 53.4 MB per jar, plus 0.68 MB JaCoCo, each
with `retention-days: 7`. Nothing in either workflow consumes them, and no release process uses them. GitHub
Free includes 500 MB of artefact storage **shared with Packages**, overage billed per GB-month;
`gh api accounts/…/settings/billing/actions` returns 404 for a personal account, so the metered figure cannot be
read from code.

**Impact.** At ~3 runs/day the steady-state footprint is 7 days × 3 × 54 MB ≈ 1.1 GB — over the included 500 MB,
which either deletes artefacts (losing the JaCoCo trend the team wants) or incurs a per-GB-month charge, silently
crossing the zero-cost rule. The published community answer that public-repo artefacts are exempt is not stated
in current official docs, so it must not be relied on.

**Remediation.** Upload the jar only on `push` to `master` (or better, only in the release workflow that consumes
it), keep JaCoCo on PRs with `retention-days: 2`, and add a job that fails the workflow if the repository's live
artefact bytes exceed a stated ceiling. Report estimated and actual usage in every infrastructure change, per the
founder's standing rule.

**Cost.** Target: $0. Estimated exposure if unchanged and *not* exempt: ~0.6 GB/month over quota ≈ $0.15/month —
immaterial in dollars, material as an unapproved recurring charge.

**Verification.** After one week of normal PR activity, live artefact bytes stay under the stated ceiling; a PR
run produces no jar artefact; the release run still produces exactly one.

---

### INF-17 · MEDIUM — `.dockerignore` gaps let tracked build-time files into the build context

**Evidence.** `gms_frontend/.dockerignore` excludes `.env.local` and `.env.*.local` but **not** `.env.production`
(which *is* tracked: `git ls-files` lists it) and not `api/` (the Vercel serverless proxies) or `vercel.json`;
`Dockerfile:12` then runs `COPY . .`. `gms_backend/.dockerignore` excludes `target/`, `memory-bank/` and `*.md`
but not `.env`.

**Impact — stated precisely.** The final runtime images do **not** contain these files: the frontend stage copies
only `dist` and `nginx.conf`, and the backend stage copies only `pom.xml` and `src`. So this is not a live secret
leak today — `gms_frontend/.env.production` holds only `VITE_API_BASE_URL=/api/v1`, and `VITE_`-prefixed values
are public by construction. The real risk is the *pattern*: a future `VITE_SECRET_*` or a stray `.env` would be
baked into an intermediate layer, shipped in the build cache, and visible to anyone with image pull access; and
copying `api/` + `node_modules` inflates the context (272 MB of `node_modules` on this machine) for no reason.

**Remediation.** Add `.env`, `.env.*` (then explicitly re-`COPY` the one file the build needs), `api/`,
`vercel.json`, `dist/`, `coverage/`, `node_modules/` and `test-results/` to both ignore files; assert in CI that
no built image layer contains `.env` (`history -q` + a `docker run --entrypoint sh -c 'ls -a /app'` scan).

**Cost.** $0.

**Verification.** A canary string placed in `.env.production` appears in **neither** `docker history` output nor a
filesystem scan of the built image; measured build context drops from hundreds of MB to source-only.

---

### INF-18 · MEDIUM — Nothing scans dependencies, images, secrets or licences

**Evidence.** `git ls-files` contains no `.github/dependabot.yml`, no `CODEOWNERS`, no Trivy/grype/Snyk/Docker
Bench Security step, no SBOM generator, no secret-scanning workflow; `ci.yml` has two jobs (backend, frontend) and
no security stage. The repository's own history is the evidence that secret scanning is not enforced: a bcrypt
hash and a live signing key were committed earlier and are recorded as SEC-01/SEC-02.

**Impact.** Supply-chain drift is invisible: a vulnerable transitive dependency or a compromised base-image layer
reaches production with no signal, and a re-run of the same mistake is caught only by a human reviewer. §4.3 and
§11 list these controls explicitly.

**Remediation.** Dependabot for `maven`, `npm` and `github-actions` (free, and it also fixes the unpinned action
SHAs); `gitleaks` or the built-in push protection in a PR job; Trivy on both images and on the filesystem; a
CycloneDX SBOM artefact per release. Advisory vs blocking must be set honestly: image scanning starts advisory
until the finding count is known, then becomes blocking — and the switch is a documented decision, not a way to
make CI green (§6.1 forbids silently changing a gate).

**Cost.** $0 for all of the above on GitHub-hosted runners for a public repo; Trivy and Syft are open source.

**Verification.** A deliberately vulnerable test dependency produces a red build; an injected fake secret in a
branch is caught before merge; a release run leaves a downloadable SBOM and a scanned image report as artefacts.

---

### INF-19 · LOW — One compose file carries both development and production intent

**Evidence.** `docker-compose.yml` simultaneously publishes developer ports (`:12-13`, `:41-42`, `:61-62`),
defaults the profile to `dev` (`:39`), sets `env_file: .env` (`:6-7`, `:30-31`), and is the only compose file in
the repository. There is no `compose.prod.yaml`, no `compose.dev.yaml`, and no `.env` template *for the proxy
layer* (`gms_frontend/.env.production` is the frontend's only template, and `SPRING_PROFILES_ACTIVE=dev` is
hardcoded at `.env.example:18`).

**Impact.** Production inherits development defaults by silence — which is exactly how INF-02's published 5432 and
INF-11's `dev` profile would reach a real host. Mandate §4.5 asks for the split by name, and §5 for per-environment
configuration.

**Remediation.** `compose.yaml` (shared service definitions), `compose.dev.yaml` (loopback bindings, hot reload,
`dev` profile), `compose.prod.yaml` (`restart`, limits, isolation, internal networks, no published db port,
explicit image tags from the registry rather than `build:`), plus a `.env.production.example`. Keep the file count
at three — §4.5 says avoid unnecessary proliferation, and a fourth `compose.staging.yaml` is not needed if
staging is a second project name on the same host.

**Cost.** $0.

**Verification.** `docker compose -f compose.yaml -f compose.prod.yaml config` renders no `build:` section, no
5432 binding and no `SPRING_PROFILES_ACTIVE: dev`; the same command for dev renders the developer bindings; both
pass `config --quiet`.

---

### INF-20 · LOW — The variable catalogue is incomplete in both directions

**Evidence.** `.env.example:23-24` documents `DB_HOST` and `DB_PORT`, and `grep -rn "DB_HOST\|DB_PORT"` across
`gms_backend/src/main/resources`, `docker-compose.yml`, `.github/workflows` and `scripts` finds **no consumer** —
which contradicts the rule printed at the top of that very file (`:10-11`: "an entry that binds to no property is
a bug"). Conversely the three frontend build variables actually in use — `VITE_API_BASE_URL`, `VITE_MOCK_AUTH`,
`VITE_APP_NAME` (12 + 9 + 2 references) — appear in no template at all.

**Impact.** An operator who sets `DB_PORT` to move the database port believes they have configured something;
an operator deploying the frontend has no documented way to turn mock mode off or point the SPA at an API origin.
Both are startup-configuration surprises that surface as outages.

**Remediation.** Delete `DB_HOST`/`DB_PORT` or make compose consume them; add a documented `VITE_*` block with an
explicit "production must set `VITE_MOCK_AUTH=false`" note; keep `StartupSecurityValidator` as the enforcement
point so the catalogue is not merely documentation.

**Cost.** $0.

**Verification.** Every key in both templates has at least one `grep` hit in consuming code, asserted by a CI job;
the reverse direction (a consumed variable with no documented name) is asserted by the same script.

---

### INF-21 · INFORMATIONAL — Two suspected risks were tested and are not risks

Recorded because "checked and clear" is a result, and it prevents a later pass from re-flagging them.

* Mock fallback: `apiSlice.js:16` and `authService.js:5` both gate on
  `import.meta.env.VITE_MOCK_AUTH === 'true'`, so an unset variable is *off*. §25's "do not silently fall back to
  mock APIs in production" holds by construction.
* API origin fallback: `apiSlice.js:5-14` returns `/api/v1` when `import.meta.env.PROD`, and only falls back to
  `http://localhost:8080/api/v1` in a development build. A production bundle cannot point at localhost.

Also verified: `SecurityConfig:114-115` keeps `/actuator/health/**` anonymous and `management.endpoint.health.show-details=never`
(`application.properties:66-67`), so §10's "health endpoints must not disclose internals" is satisfied at the
payload level today.

### INF-22 · INFORMATIONAL — M1's compose fixes survived revalidation

`docker compose config --quiet` exits 0 on a fresh checkout with a `.env` present, the obsolete `version:` key is
gone, and `${DB_PASSWORD:?…}` (`:11`, `:38`) fails loudly rather than defaulting. That is the §13 static
validation for those edits, and it is the reason OPS-08 is partially closed rather than re-opened here.

---

## 4. Disposition of the existing `OPS-` findings

The first audit already owns these; this table records only what Infra-M0 changed. No finding is restated.

| Finding | Status after Infra-M0 | What this pass added |
|---|---|---|
| OPS-01 deploy has no gate | Still open, now with numbers | `required_status_checks.contexts=[]` and `protection_rules: []` fetched; merged via `--admin` once — INF-10 |
| OPS-02 unversioned deploy script | Still open, escalated | absorbed into INF-03 (CRITICAL): it is why rollback and DR are blocked, not merely undocumented |
| OPS-03 host-key verification disabled | **Closed in code, verified executed** | run 41245769175 log: `##[error]secret EC2_SSH_KNOWN_HOSTS is not set … Deploy is refused` — this is the one infra control with runtime proof |
| OPS-04 hostname echoed in logs | **Largely not real** | the failing run's log shows `EC2_HOST: ***` and `***@***`; GitHub masks secret *values*. `:67` still prints the first 10 characters deliberately. Downgraded; the committed DNS name in `vercel.json:5` is the actual exposure (INF-01) |
| OPS-05 `cancel-in-progress: true` on deploy | Still open | `:27` confirmed; belongs with Infra-M3's deploy rewrite |
| OPS-06 no rollback | Still open | root cause named: INF-03 (no artefact lineage) + INF-09 (no immutable tags) |
| OPS-07 no migrations | Still open, re-verified | `grep -i flyway gms_backend/pom.xml` → absent, so every `spring.flyway.*` key is inert; dev defaults to `ddl-auto=${JPA_DDL_AUTO:update}` (`application-dev.properties:20`) |
| OPS-08 compose cannot boot from a fresh clone | **Partially closed** | INF-22: config validates; the `env_file: .env` requirement on both services remains, and ports/networks remain — INF-19 |
| OPS-09 EOL `docker-compose` in Makefile | Still open, escalated | proved broken on this machine (`make build` → `Error 1`) — INF-12 |
| OPS-10 frontend image cannot reach an API | Still open | `nginx.conf` re-read: no `/api` block; combined with INF-14 the container path has no health gate either |
| OPS-11 debug logging by default | **Closed in code** | `application.properties:95-96` now defaults `INFO`; rotation/size still unbounded — INF-06 |
| OPS-12 JaCoCo decorative | Improved | `mvn -B verify` runs in CI and a coverage artefact is produced (`jacoco-coverage`, 679 KB) — the artefact-sizing problem is INF-16 |
| OPS-13 no Spring profiles | **Closed in code** | base/dev/prod exist; `prod` has never booted — INF-11 |
| OPS-15 `.gitignore` gaps | Still open | `.gitignore:2-4` covers `.env*` correctly and root `.env` is untracked, but `gms_frontend/.env.production` is tracked and `*.pem`/`*.key`/`secrets/` are absent — INF-17 |
| OPS-16 no backup procedure | Still open, sized | measured dump size turns "unknown" into "MB-scale, placement is the problem" — INF-13 |
| OPS-17 health endpoint always DOWN | Fixed by M1 (readiness group) | compose healthcheck now targets `/actuator/health/readiness` (`:49`); the prometheus/info half remains — INF-15 |

`SEC-01` (Neon credential in git history, unrotated), `SEC-02` (signing key default), `SEC-15` (unbound
`SUPERADMIN_*` names) and `BKL-08` are application-security findings and stay in
[SECURITY_AUDIT.md](../audit/SECURITY_AUDIT.md). Infra-M0 does not duplicate them; §5 of the mandate's
"never allow automated tests to reach production" is satisfied today only because the credential is unrotated and
unreachable, which is not a control.

---

## 5. Zero-cost ledger (mandate §2)

Measured first, then the money.

**Runner minutes.** The repository is **public**, so GitHub-hosted `ubuntu-latest` minutes are free for it; no
finding in this audit asks for a paid runner. `ci.yml` measured 50 s wall for two parallel jobs; the failing
deploy run measured 18 s. Estimated ~2–4 minutes per PR; actual per-run durations are in §1 row 12.

**Artefact storage.** 154.74 MB live against a 500 MB plan quota shared with Packages — 31 % consumed by three
runs. Overage is billed per GB-month. See INF-16 for the fix and the estimate; nothing here should be paid for.

**What is genuinely blocked on owner approval (no spend has occurred, and none is authorised by this audit):**

| Need | Cost | Status |
|---|---|---|
| A domain the owner controls, so Let's Encrypt/Caddy can issue a real certificate for the API origin (INF-01) | ~$10–15/year, recurring | **Not purchased. Owner decision.** Without it, TLS on the EC2 origin is not achievable with zero-cost tooling |
| Off-host backup destination (INF-13) | <$1/month at measured data size; more if retained long | **Not provisioned.** Creating any third-party account is outside mandate §2 without approval |
| A second instance or larger instance for staging (INF-11) | unknown until the host is measurable (INF-03) | **Not provisioned.** The existing EC2 must be evaluated before any replacement is recommended |
| Container registry | $0 (GHCR public images) | decision recorded, not yet implemented — [ADR-004](INFRASTRUCTURE_DECISIONS.md) |
| Prometheus/Grafana/Loki stack | $0 software, but RAM/CPU on a host whose capacity is unknown | **Deferred deliberately** — [ADR-010](INFRASTRUCTURE_DECISIONS.md) |

**Cloud cost increase caused by this audit: $0.** No resource was created, purchased, resized or subscribed to;
no account was opened.

---

## 6. Revalidation of the carried-forward findings (mandate §17)

Each item was re-tested rather than trusted. Two changed, and one item in the previous documentation was
outright wrong.

1. **"The deployed backend is unreachable."** → **Reconfirmed, with a second observation point.** Run
   41245769175 recorded `ssh: connect to host … port 22: Connection timed out` at 2026-10-31T21:55:01Z — from a
   GitHub runner, not from this laptop, so it is not a local-network artefact. Consequence unchanged: no
   production claim in this document is verified against production.
2. **"`deploy-backend.yml` has never successfully run on the current workflow."** → **Partly wrong, and better
   than reported.** Two things are now true that the previous text did not say. (a) The workflow *executed* on the
   merge commit and its new guard **refused the deploy on its first execution** (`secret EC2_SSH_KNOWN_HOSTS is
   not set … Deploy is refused rather than run with host-key checking disabled`) — that is a control proven at
   runtime, not authored-only. (b) It has succeeded historically (run 19420087045, 2025-11-17T06:06Z and earlier)
   when host-key checking was disabled, which is how today's unreachable host acquired an unverified state.
   `EC2_HOST`/`EC2_SSH_KEY`/`EC2_USER` **do** exist as secrets; only `EC2_SSH_KNOWN_HOSTS` is missing, so the
   previous blanket claim "requires owner-provisioned secrets" was imprecise.
3. **"Required status checks are empty."** → **Reconfirmed exactly**: `contexts: []`, `strict: false`,
   `enforce_admins: false`. Reviews exist but are admin-bypassable, and were bypassed once.
4. **"47 pre-existing frontend lint errors are advisory."** → **Reconfirmed**: `npx eslint . --format json` →
   47 errors, 0 warnings, across **22** files (the previous note's "~25 files" is corrected to 22). None
   originate in the security package; `ci.yml` keeps lint advisory with the measurement recorded in the file.
5. **"BKL-08 is open."** → **Reconfirmed open** and untouched: infrastructure work must not change authentication
   or authorisation behaviour (§17), so `AuthController.java:91` stays as it is, tracked in
   [TECHNICAL_DEBT.md](../audit/TECHNICAL_DEBT.md).
6. **"A `TraceIdFilter` exists but is not wired into the chain."** → **This was wrong.** It is a `@Component`
   extending `OncePerRequestFilter` at `Ordered.HIGHEST_PRECEDENCE` (`TraceIdFilter.java:16-18`), so Spring Boot
   does register it, and it does populate MDC and set the response header. The accurate defect is that no
   `logging.pattern` references `%X{…}`, so the id never reaches a log line. Corrected in
   [REPOSITORY_AUDIT.md §13](../audit/REPOSITORY_AUDIT.md) and reframed as INF-15.
7. **New, and the most consequential correction:** `EC2_SSH_KNOWN_HOSTS` absence means **the deploy pipeline is
   currently safe-by-accident**. Once the owner adds the known-hosts secret and the host becomes reachable,
   merge-to-`master` will deploy unverified code with no environment approval (INF-10) and no build step
   (OPS-01). Restoring connectivity must therefore be sequenced *after* Infra-M2/M3, not before. Recording that
   ordering is the main reason this audit exists.

---

## 7. Runtime validation performed during M0

| Check | Result |
|---|---|
| `docker compose config --quiet` | **PASS** (exit 0) |
| Live container security posture | **measured** — root, `restart=no`, no caps dropped, no limits, unbounded json-file logs (INF-05/06/07/08) |
| Live port bindings | **measured** — `0.0.0.0:5432` and `[::]:5432` published (INF-02) |
| `make build` / `make up` | **FAIL by design of the file** — v1 binary absent (INF-12) |
| `docker compose build backend frontend` | executed for §13; reported in §7.1 |
| Frontend lint | **47 errors / 22 files** — unchanged, advisory |
| Backend unit tests (`mvn -B verify` in CI) | **PASS** in CI runs 41245769121 / 41245531864 / 41245233160 |
| Docs-only change starts no build | **PASS (negative case exercised)** — PR #11 is documentation-only and produced **no** `ci.yml` run (`gh run list` shows nothing for the branch), which is the `paths-ignore` contract at `ci.yml:11-22` observed from the other side. The deploy workflow's `paths:` filter is not proven by this, since it triggers only on `push` to `master`. |
| A Vercel GitHub App is already in the delivery path | **observed** — PR #11 reports `Vercel / pass` against `shubhamdiyos-projects/gms-react`, so preview deployments are built per PR by a third party that no document in this repository names. Relevant to ADR-013 and to §8's privacy review: the access policy of those preview URLs is unverified. |
| `scripts/verify-security.sh` (18 assertions) | **18/18 PASS** locally, recorded in [BASELINE_STATUS.md §13.2](../audit/BASELINE_STATUS.md) |

### 7.1 Image build result

`docker compose build backend frontend` completed with **exit 0** on this host. Slowest single step: **139.5 s**
(`mvn dependency:go-offline` at `gms_backend/Dockerfile:7`), because nothing caches the Maven repository between
builds — the same 847 MB `maven:3.9-eclipse-temurin-21` stage redownloads the world every time the dependency set
changes.

| Measurement | backend | frontend |
|---|---|---|
| image name:tag produced | `gms-v1-backend:latest` | `gms-v1-frontend:latest` |
| size | 399 MB | 94.8 MB |
| `Config.User` | **empty → root** | **empty → root** |
| `Config.Healthcheck` | **NONE** | **NONE** |
| build labels | 3 (BuildKit provenance only, no OCI revision) | 4 (same) |
| runtime `id -u` | **0** (executed inside the container) | not executed |
| build tooling in runtime image | **absent — no `mvn`** (multi-stage separation confirmed) | n/a |
| runtime entrypoint | `java -jar app.jar` — no `-XX:MaxRAMPercentage`, no `JAVA_OPTS` | `nginx -g daemon off;` |

What this validates and what it does not. It proves both Dockerfiles **build** (so Infra-M1 starts from working
multi-stage definitions, not from broken ones) and that build-only dependencies stay out of the runtime image. It
converts INF-08 from inference to observation: the images really do run as uid 0. It also demonstrates INF-09
concretely — the artefacts of a successful build of commit `7a75d39` are addressable only as `:latest`, with no
revision label, so nothing here can be traced back to the commit that produced it. It does **not** validate
runtime behaviour: no container from either image was started, so no claim about startup, health, signals or
shutdown is made from this run.

---

## 8. What could not be tested, and what is blocked on the owner

Not evidence of safety. Mandate §14 requires these rows to be `BLOCKED`, not `PARTIALLY_IMPLEMENTED`.

1. **Everything about the live host.** SSH from two independent observation points times out on port 22, so
   instance type, disk, OS packages, security groups, running containers and the actual `deploy_backend.sh` are
   unknown (INF-03). No runtime, deployment, rollback or restore claim can be made.
2. **The production Spring profile has never booted** (INF-11) — needs a database that is not production, which
   needs either a schema export the owner has not authorised or a local copy of the masked fixture.
3. **Metered Actions/package storage usage** is unreadable via the REST API for a personal account (§1 row 14); the
   quota analysis is arithmetic on artefact bytes.
4. **TLS renewal** cannot be observed because no certificate exists anywhere.
5. **Owner actions outstanding, unchanged from the security programme:** rotate the exposed Neon credential
   (SEC-01); set `EC2_SSH_KNOWN_HOSTS`, `SECURITY_JWT_SECRET`, `APP_SUPERADMIN_*` as repository secrets;
   turn the CI checks into required checks and enable `enforce_admins`; restore or formally retire the EC2 host;
   authorise a prod schema export for the Flyway baseline; decide 403-vs-404 for cross-tenant reads; approve or
   reject the domain and off-host-backup costs in §5.
6. **The Vercel preview-deployment policy is unverified.** A Vercel GitHub App is attached to this repository and
   builds a preview for each pull request (§7). Whether those preview URLs are unlisted, password-protected or
   indexable is a privacy question (§18 of the first mandate) that cannot be answered from the repository, and it
   matters because the app ships a mock mode that renders synthetic tenant data when `VITE_MOCK_AUTH=true`.

---

## 9. Milestone plan for Infra-M1…M6

Mandate §15 names these. **Namespace note:** the first mandate's milestones M0–M7 were the *security* programme
(M1 is merged); this programme's M0–M6 are *infrastructure*. Where confusion is possible, this programme writes
"Infra-M1". Recorded as [ADR-012](INFRASTRUCTURE_DECISIONS.md) so the two series never silently collide.

| Milestone | Scope | Acceptance criteria (all must be executed, not asserted) | Cost | Findings closed |
|---|---|---|---|---|
| **Infra-M1** Container foundation | `compose.yaml` + `compose.dev.yaml` + `compose.prod.yaml`; multi-stage non-root images with `HEALTHCHECK`; digest-pinned bases; `npm ci`; `.dockerignore` hardening; Makefile → `docker compose` + guarded `clean`; per-service env files; two networks with `internal: true` | dev stack boots green on a clean clone; prod stack renders with no `build:` and no published db port; `docker top -o user` non-root ×3; `config --quiet` ×2; image layer scan finds no `.env`; restart and SIGTERM behaviour observed | $0 | INF-02/04/05/06/07/08/09/12/17/19 |
| **Infra-M2** CI foundation | workflow permissions pinned and minimal; required status checks + `enforce_admins`; dependabot; secret scan; image scan (Trivy, advisory→blocking); SBOM; artefact-retention fix; CODEOWNERS; documented-and-consumed variable catalogue | a failing or red-lint-escalating PR cannot merge even as admin; an injected fake secret blocks merge; live artefact bytes under a stated ceiling for a week | $0 | INF-10/16/18/20 |
| **Infra-M3** Deployment engineering | versioned `scripts/deploy.sh` replacing the untracked remote script; host survey written up; immutable `<sha>` image tags in a registry; `environment: production` with required reviewers; `cancel-in-progress: false`; deployment lock; post-deploy SHA/digest verification + proxied health; expand-migrate-verify-contract migrations (Flyway baseline from an authorised schema export) | one deploy on a non-production target observed end-to-end; **one rollback executed**; a stale deploy job refused; a concurrent deploy serialised | $0 | INF-03/09/11 (part)/14 (part), OPS-01/02/05/06/07 |
| **Infra-M4** Runtime security | Caddy/nginx + TLS; HTTPS enforcement; security headers; `client_max_body_size`; proxy timeouts; rate limiting; 8080/5432 closed publicly; least-privilege DB role; secret rotation procedure | external scan shows only 443 (and 80 redirecting) open; renewal observed once and its failure path tested; the app works through the proxy with `VITE_API_BASE_URL=/api/v1` | **domain ~$10–15/yr — owner approval required before purchase** | INF-01/02/14 |
| **Infra-M5** Observability and recovery | log pattern with `%X{traceId}`; json-file rotation; `/actuator/prometheus` behind auth; `/actuator/info` build contributor; `db-backup.sh` / `db-restore.sh` with checksum + encryption + rotation; the five runbooks | a trace id is findable in logs for a real request; a restore executed into an isolated container and row counts compared; disk-pressure alert observed firing on a full test volume | <$1/mo if off-host approved; $0 otherwise | INF-06/13/15, OPS-16 |
| **Infra-M6** Production qualification | full §14 scorecard re-run with runtime evidence; host reboot recovery; failed-deploy and rollback rehearsals; staging boot of `prod` | every row either `VERIFIED` with a citation or explicitly `BLOCKED` with the owner action named; no row promoted on authorship alone | $0 | closes the programme |

Dependency rule, and it is the most important line in this file: **do not restore EC2 connectivity or add
`EC2_SSH_KNOWN_HOSTS` before Infra-M2 and Infra-M3 land.** Today's missing secret is the only thing preventing an
ungated production deploy; re-enabling SSH first converts a latent risk into an active one.
