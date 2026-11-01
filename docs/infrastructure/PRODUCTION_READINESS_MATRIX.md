# GMS Production Readiness Matrix — Phase 0 (M0)

The §14 scorecard of the infrastructure mandate, evaluated against what this repository can *prove* today.
Companion to **[INFRASTRUCTURE_AUDIT.md](INFRASTRUCTURE_AUDIT.md)** (`INF-` findings, referenced below by id) and
to **[docs/audit/REPOSITORY_AUDIT.md](../audit/REPOSITORY_AUDIT.md)** (application findings). This file records
status and evidence only; it does not restate remediation plans.

## Status vocabulary — used exactly as the mandate defines it

| Status | Meaning |
|---|---|
| `VERIFIED` | A test, command or observation executed in this session and its result is cited. Nothing else gets this word. |
| `IMPLEMENTED_NOT_VERIFIED` | The code exists and is correct on inspection, but no executed evidence proves it works in the environment it targets. |
| `PARTIALLY_IMPLEMENTED` | Some required controls exist; named gaps remain. |
| `BLOCKED` | Cannot be implemented or measured until an owner action or an external resource is available. |
| `NOT_IMPLEMENTED` | Nothing exists. |

**Overall verdict: GMS is not production-ready and cannot be certified as such from this repository.** Three
areas are `BLOCKED` behind an unreachable host, and the one control that has runtime proof (the SSH host-key
guard) is currently the only thing preventing an unverified automatic deploy. The scorecard is deliberately harsh
for that reason.

---

## 1. Area scorecard

| # | Area | Acceptance criteria (§14) | Status | Primary evidence | Findings |
|---|---|---|---|---|---|
| 1 | **Containers** | Reproducible, secure, independently manageable | **PARTIALLY_IMPLEMENTED** | `docker compose build backend frontend` → exit 0; multi-stage separation confirmed (`no mvn` in runtime image); `docker compose config --quiet` → exit 0 | INF-08 (`id -u`=0), INF-09 (`:latest` only), INF-04/05/06/07, INF-19 |
| 2 | **CI** | Required checks run and report accurately | **PARTIALLY_IMPLEMENTED** | `ci.yml` executed and passed on PR #10 and on the merge commit (runs 41245233160, 41245531864, 41245769121; backend job 50 s), and on the merge of PR #13 (41283928312) — but `required_status_checks.contexts=[]`. **A change to a workflow file now does start a run:** `validate-workflows.yml` fired on master's own merge commit `13bc2ed` (run 41283981839, both jobs green). Signal yes, enforcement no (INF-10) | INF-10, INF-16, INF-18, INF-23 (**closed at capability level**) |
| 3 | **CD** | Controlled, traceable and recoverable | **NOT_IMPLEMENTED** | `deploy-backend.yml` **is gone from `master`** (PR #13 deleted it), so there is no longer any pipeline to critique — the defects listed in Infra-M0 (`no build step, no environment:, no permissions:, cancel-in-progress: true`, unversioned remote script) are the reason it was retired rather than repaired. CD has to be designed against OCI, not inherited from this file | INF-03, INF-09, OPS-01/02/05/06 (**RETIRED**, audit §4) |
| 4 | **Security** | Least privilege and no secret leakage | **PARTIALLY_IMPLEMENTED** | One control proven at runtime: the host-key guard refused a production deploy (audit §6.2). App-level secrets fail-closed via `StartupSecurityValidator`. Everything container-level is inverted | INF-05, INF-08, INF-18, INF-01 |
| 5 | **Networking** | Only intended public services exposed | **NOT_IMPLEMENTED** | `docker ps` shows `0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp` live on this machine; one flat bridge network; no TLS material anywhere in the repo | INF-01, INF-02, INF-04 |
| 6 | **Persistence** | Durable data and tested recovery | **PARTIALLY_IMPLEMENTED** | A named volume exists and survives container recreation in principle; **zero** dumps, **zero** restores executed, no encryption, no rotation, no off-host copy | INF-13, OPS-16 |
| 7 | **Observability** | Health, logs and deployment visibility | **PARTIALLY_IMPLEMENTED** | `/actuator/health/readiness` + `/liveness` exist and are used by the compose healthcheck; trace id reaches the HTTP header and `GlobalExceptionHandler`; no log pattern, no metrics endpoint, no version identity | INF-15, INF-06 |
| 8 | **Reliability** | Graceful failures and restart recovery | **NOT_IMPLEMENTED** | `RestartPolicy=no` measured on the live container; `server.shutdown` unset in all three profiles; no JVM memory policy; no dependency-reconnect behaviour proven | INF-06, INF-07 |
| 9 | **Rollback** | Documented and tested | **NOT_IMPLEMENTED** | No rollback document, script, artefact lineage or previous-known-good pointer exists; the deploy workflow's only post-action is a diagnostic `docker ps -a` | INF-03, INF-09, OPS-06 |
| 10 | **Cost** | No unauthorised spend | **VERIFIED** | Zero resources created, purchased, resized or subscribed to during M0; repo is public so runner minutes are free; the one measurable exposure (154.74 MB live artefacts vs 500 MB included) is quantified in INF-16 with a $0 fix | INF-16 |

Row 10 is the only `VERIFIED` area, and it is verified in the narrow sense: nothing was spent. Rows 1–9 share a
structural problem — three of them (`Containers`, `CI`, `Security`) carry `IMPLEMENTED_NOT_VERIFIED` sub-items
that cannot be promoted until the host is reachable, which makes them **`BLOCKED` at the production level** even
where they are green locally.

---

## 2. Item-level evidence

Each item names the command or artefact that produced the status. An item with no citation belongs in §4.

### 2.1 Container architecture

| Item | Status | Evidence / gap |
|---|---|---|
| Multi-stage build | `VERIFIED` (local build only) | `docker compose build` exit 0; runtime image contains `app.jar` and no Maven |
| Non-root runtime user | `NOT_IMPLEMENTED` | `docker inspect` → `Config.User=""`; executed `id -u` → `0` |
| `HEALTHCHECK` in image | `NOT_IMPLEMENTED` | `Config.Healthcheck=NONE` on both built images; compose defines one for backend/postgres only |
| Digest-pinned bases | `NOT_IMPLEMENTED` | `nginx:alpine` (untagged), `node:20-alpine`, `postgres:16-alpine`, `eclipse-temurin:21-jre-alpine` |
| Deterministic frontend install | `NOT_IMPLEMENTED` | `gms_frontend/Dockerfile:9` `npm install`, with a comment stating the lockfile is not trusted |
| Tests run during image build | `NOT_IMPLEMENTED` | `mvn clean package -DskipTests` (`gms_backend/Dockerfile:11`) |
| `.dockerignore` completeness | `PARTIALLY_IMPLEMENTED` | backend excludes `target/`+`memory-bank/` but not `.env`; frontend omits the tracked `.env.production`, `api/`, `vercel.json` |
| Resource limits | `NOT_IMPLEMENTED` | measured `Memory=0`, `NanoCPUs=null` |
| `cap_drop` / `no-new-privileges` / `read_only` | `NOT_IMPLEMENTED` | measured `CapDrop=null`, `SecurityOpt=null`, `ReadonlyRootfs=false` |
| Independent restartability | `PARTIALLY_IMPLEMENTED` | three services, one compose file, no `depends_on.condition` on the frontend, `restart: no` everywhere |
| Graceful shutdown / signal handling | `NOT_IMPLEMENTED` | `grep server.shutdown` → no match in any profile; exec-form `ENTRYPOINT` does forward SIGTERM, but nothing drains in-flight requests |
| Image ↔ commit traceability | `NOT_IMPLEMENTED` | produced artefacts are `gms-v1-backend:latest` / `gms-v1-frontend:latest`; no OCI revision label |

### 2.2 CI

| Item | Status | Evidence / gap |
|---|---|---|
| Backend compile + test executes | `VERIFIED` | three `ci.yml` runs concluded `success`; job name `Backend build and tests`, `mvn -B verify` |
| Workflow validation (third mandate §12; Infra mandate §6.1 stage 1) | `PARTIALLY_IMPLEMENTED` | **The gap that INF-23 described is closed on `master`.** `validate-workflows.yml` triggers on exactly the `.github/**` set `ci.yml` excludes, and it has now run where `6198d86` could not: on master's own merge commit `13bc2ed` (run 41283981839, `Lint every workflow` + `Check trigger sets and permissions` both green) and again on `7fb25b8` (run 41284869478, both green), which is the first time the *push* trigger rather than a PR trigger had to carry a workflow edit into `master`. Its acceptance test was executed on the PR — red on a deliberately broken trigger (41283176919), green on the revert (41283277168), green on the final head (41283837991). `PARTIALLY_` and not `IMPLEMENTED` because nothing consumes the verdict: the two jobs are not required checks (INF-10), and a workflow still cannot detect its own deletion |
| Frontend lint + production build executes | `VERIFIED` | same runs; `npm ci`, lint advisory (47 errors/22 files measured), `npm run build` |
| Infrastructure definitions execute | `PARTIALLY_IMPLEMENTED` | a fourth `ci.yml` job (`Infrastructure definitions`) installs OpenTofu from the official archive behind a `sha256sum --strict` check against the publisher's `SHA256SUMS`, then runs `tofu fmt -check`, `init -backend=false`, `validate`, the seven plan-time guard suite and the §9.2 forbidden-port check. It has now executed where a local run cannot prove anything: run 41287513214 (job 124791715614) on PR #18 and run 41287750885 (job 124792405019) on the push to `master` at that PR's own merge commit `3cf8fa1`, each step green and the guard suite 7/7 `ok` in both — so the job validates the stack against its own edit, the same property INF-23 demanded of the workflow gate. The suite has also been shown to *fail* when it should: PR #19 (closed unmerged) deleted the `/32` SSH guard, and run 41289417174 stayed green through `validate` while the guard step went red on exactly the two affected assertions. No third-party action and no credentials, so it never touches the tenancy. `PARTIALLY_` and not `VERIFIED` because it is still not a required check: the merge it passed was the ninth consecutive `--admin` override (INF-10) |
| Checks are *required* | `NOT_IMPLEMENTED` | `contexts=[]`, `strict=false`, `enforce_admins=false`; **PRs #10–#18 — nine consecutive merges — all carry zero recorded reviews**, so all nine entered `master` by `--admin`. Nine overrides on one rule is the rule telling you it is wrong, not nine exceptions to note. It also cannot be fixed by simply naming the five checks that now exist: PR #15 (docs-only) started **no** Actions run at all, so a required check on a filtered workflow would stall that PR forever — audit INF-10 holds the three options. PR #18 is the experiment already run: it added a check, the check passed twice, and the override count went from eight to nine anyway |
| Workflow-token least privilege | `PARTIALLY_IMPLEMENTED` | `ci.yml:32` declares `permissions: {contents: read}`; `deploy-backend.yml` declares none |
| Secret scanning | `NOT_IMPLEMENTED` | no workflow, no `gitleaks`, no push-protection config in repo |
| Dependency / image vulnerability scanning | `NOT_IMPLEMENTED` | no Trivy/grype/OSV step; no `.github/dependabot.yml`; no `CODEOWNERS` |
| SBOM | `NOT_IMPLEMENTED` | none produced by any run |
| Migration validation | `BLOCKED` | no migration tool exists (OPS-07) and the prod profile has never booted (INF-11) |
| Container smoke test | `NOT_IMPLEMENTED` | CI builds a jar, never an image, never starts a stack |
| Integration tests against a real database | `NOT_IMPLEMENTED` | no Testcontainers; 42 unit tests only (36 from M1 + 6 in `fix/tenant-notification-recipients`) |
| Advisory vs blocking gates distinguished honestly | `VERIFIED` | the lint step carries its measured count and its reason in `ci.yml`, and `npm test` remains explicitly non-blocking pending M3 of the security programme |

### 2.3 CD / deployment

Tense note before reading this table: `deploy-backend.yml` **was deleted from `master` by PR #13**
(`964acad`, 2026-11-01T07:13:33Z). Every row below describes the pipeline Infra-M0 measured, which is the
specification for what has to be designed against OCI — not a live gate. `PARTIALLY_IMPLEMENTED` on rows 4–6 is
therefore historical: the control existed in a file that no longer exists.

| Item | Status | Evidence / gap |
|---|---|---|
| Deploy gated on a green CI | `NOT_IMPLEMENTED` | no `needs:` relationship exists between the two workflows |
| GitHub Environment used | `NOT_IMPLEMENTED` | `Production` and `Preview` exist but `protection_rules: []`, `can_admins_bypass: true`, and no workflow references either |
| Immutable image tag / digest deploy | `NOT_IMPLEMENTED` | the deploy builds nothing and ships nothing versioned |
| Infrastructure as code for the target platform | `PARTIALLY_IMPLEMENTED` | [`infra/oci/`](../../infra/oci/README.md) is on `master` (PR #18, `3cf8fa1`) and declares §9's VCN, IGW, route table, security list, subnet and `VM.Standard.A1.Flex` instance in OpenTofu v1.13.2 (provider `oracle/oci` 9.11.0 pinned in the committed lock file), and `tofu validate` → **Success**. Seven plan-time guards were each executed locally and now execute in CI on every change to the stack: a `/32` SSH source, a `.pub`-only key path, an OCID-shaped compartment, boot 50–200 GB, OCPUs 1–4, RAM 1–24 GB, non-root deploy user — and the shape is a `local`, so no argument can select a paid one (ADR-018). **`PARTIALLY_` and not `VERIFIED` because nothing has been planned against the real tenancy and nothing has been applied**: `oci setup config` is interactive-only, so the account has never been read, eligibility is unmeasured, and §9.1's four cost numbers are still prose |
| Deployment lock / serialisation | `PARTIALLY_IMPLEMENTED` | `concurrency.group: deploy-backend` exists — but `cancel-in-progress: true` makes it a cancellation, not a lock |
| Pre-deploy checks | `PARTIALLY_IMPLEMENTED` | the host-key guard is real and executed; there is no build, test, image or migration check |
| Post-deploy verification | `PARTIALLY_IMPLEMENTED` | a plaintext HTTP ping retry loop (`:96-107`); no SHA or digest verification |
| Rollback | `NOT_IMPLEMENTED` | nothing to roll back to; no previous-release pointer recorded anywhere |
| Failed-deploy handling | `NOT_IMPLEMENTED` | failure leaves the last (possibly broken) release live; the only post-failure action is `docker ps -a \|\| true` |
| Zero-downtime deployment | `BLOCKED` | cannot be assessed: the host is unreachable and the runtime topology is unversioned (INF-03) |

### 2.4 Runtime, network, data

| Item | Status | Evidence / gap |
|---|---|---|
| HTTPS enforced | `NOT_IMPLEMENTED` | no certificate, no 443 listener, no redirect anywhere in the repository |
| TLS on the API origin hop | `NOT_IMPLEMENTED` | `gms_frontend/vercel.json:5` → plaintext `http://<ec2 dns>:8080` (INF-01) |
| Only the proxy publishes a port | `NOT_IMPLEMENTED` | 5432, 8080 and 3000 all published (INF-02) |
| Internal-only data network | `NOT_IMPLEMENTED` | one flat `gms-v1_gms-network` shared by all three services |
| HTTP security headers | `PARTIALLY_IMPLEMENTED` | `nginx.conf:25-27` sets `X-Frame-Options`, `X-Content-Type-Options` and the deprecated `X-XSS-Protection`; no CSP, HSTS, `Referrer-Policy` or `Permissions-Policy`; served over HTTP so HSTS would be invalid anyway |
| Rate limiting / request size limits | `NOT_IMPLEMENTED` | nothing in `nginx.conf`; `spring.servlet.multipart.enabled=false` (`application.properties:91`) instead, which disables uploads globally rather than bounding them |
| Correct forwarded headers | `NOT_IMPLEMENTED` | no proxy in front of the app, so no `X-Forwarded-*` trust configuration exists |
| Encrypted off-site backup | `NOT_IMPLEMENTED` → **`BLOCKED` on an owner cost decision** | no dump script at all (INF-13) |
| Restore tested | `NOT_IMPLEMENTED` | never executed, and there is nothing to restore from |
| Least-privilege database role | `NOT_IMPLEMENTED` | the app connects as the superuser-equivalent `POSTGRES_USER` created by the image |
| Migrations as a controlled step | `NOT_IMPLEMENTED` | OPS-07 — no `flyway-core` in `pom.xml` (re-verified by grep), so all `spring.flyway.*` keys are inert and Hibernate `update` is the only schema mechanism |
| Log rotation | `NOT_IMPLEMENTED` | measured `LogConfig = json-file, {}` |
| Disk / memory pressure signals | `NOT_IMPLEMENTED` | no monitor, no alert, no threshold |

### 2.5 Secrets and configuration

| Item | Status | Evidence / gap |
|---|---|---|
| No credentials in source or images | `PARTIALLY_IMPLEMENTED` | M1 removed the literal JWT default and the bootstrap password; `grep -c '\$2a\$'` = 0; but the *deployed* secret (SEC-01) still lives in git history until rotated by the owner |
| Fail-fast on missing configuration | `VERIFIED` (locally executed) | `StartupSecurityValidator` aborts boot on a missing/weak value under `prod`; `${DB_PASSWORD:?}` proven by `docker compose config` |
| Documented variable catalogue | `PARTIALLY_IMPLEMENTED` | `.env.example` is thorough for the backend but documents two keys nothing reads and omits all three `VITE_*` (INF-20) |
| Secrets absent from workflow logs | `VERIFIED` | the failing deploy log shows `EC2_HOST: ***`, `***@***`; GitHub masks secret values (correction to OPS-04, §6.2 of the audit) |
| Per-service secret scoping | `NOT_IMPLEMENTED` | 27 app keys inside the Postgres container, including `MAIL_PASSWORD` (INF-05) |
| Rotation procedure | `NOT_IMPLEMENTED` | no document, no runbook, no tooling |
| Deployment secrets provisioned | `BLOCKED` (owner) | `EC2_SSH_KNOWN_HOSTS`, `SECURITY_JWT_SECRET`, `APP_SUPERADMIN_*` are not repository secrets (`gh secret list`) |

---

## 3. Cost and quota status (§2)

| Item | Measured | Included | Status |
|---|---|---|---|
| Actions runner minutes | ~2–4 min per PR; repo is **public** | unlimited for public repos | `VERIFIED` — $0 |
| Live artefact storage | **154.74 MB** (6 artefacts from 3 runs) | 500 MB, shared with Packages | `PARTIALLY_IMPLEMENTED` — 31 % used, fix costs $0 (INF-16) |
| Metered usage via API | `accounts/…/settings/billing/actions` → **HTTP 404** | — | `BLOCKED` — the owner must read the billing page; no code path can |
| Container registry | not used yet | GHCR public images $0 | decision pending implementation ([ADR-004](INFRASTRUCTURE_DECISIONS.md)) |
| Domain for TLS | not owned | — | **approval required** before any purchase (~$10–15/yr) |
| Off-host backup storage | <$1/mo at measured data size | — | **approval required** |
| Additional EC2 capacity | host metrics unobtainable | — | `BLOCKED` |

**Spend caused by this programme so far: $0. Unauthorised spend: $0. Nothing in M0 created an account, a
resource or a subscription.**

---

## 4. Promotion rules for this matrix

How a row moves, stated so that the next reviewer does not have to trust prose.

1. `NOT_IMPLEMENTED → IMPLEMENTED_NOT_VERIFIED` requires a merge that changes the thing, plus the file:line.
2. `IMPLEMENTED_NOT_VERIFIED → VERIFIED` requires an executed command whose output is quoted here — local or CI.
3. `BLOCKED → *` requires an owner action (secret, credential rotation, domain, account, host access) whose
   completion is recorded with a date in [docs/audit/BASELINE_STATUS.md](../audit/BASELINE_STATUS.md).
4. A row may **not** be promoted on the strength of a passing build alone: mandate §13 distinguishes *builds*,
   *tests* and *verification*, and §3 forbids marking something fixed because code changed.
5. Any row that would go green only because a check was relaxed must instead record the relaxation as a finding.

## 5. What would change the verdict

In dependency order — and note that items 1–4 are owner actions, not engineering tasks:

1. Owner rotates SEC-01 and provisions the missing secrets **except** `EC2_SSH_KNOWN_HOSTS` (adding that one
   before step 3 re-arms an ungated automatic deploy — see the ordering rule at the end of
   [INFRASTRUCTURE_AUDIT.md §9](INFRASTRUCTURE_AUDIT.md)).
2. Owner restores or formally retires the EC2 host, and a live host survey is written into
   `docs/infrastructure/`.
3. Infra-M2 makes CI checks required with `enforce_admins` and adds the security stages.
4. Infra-M3 lands versioned deployment, image lineage and an executed rollback.
5. Infra-M1/M4 close the container isolation and TLS findings; only then can `Networking` and `Security` leave
   `NOT_IMPLEMENTED`.
6. Infra-M5 executes a restore and a host-reboot recovery for real; only then can `Persistence` and
   `Reliability` leave their status.

Until then the accurate sentence about this system is: **the application is improving under test, and the
platform it runs on is unverified, exposed, and one approved-merge away from an ungated production deploy.**
