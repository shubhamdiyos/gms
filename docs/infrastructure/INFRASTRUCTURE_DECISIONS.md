# GMS Infrastructure Architecture Decisions (ADR)

Decisions taken during Infra-M0 for the second MASTER ENGINEERING MANDATE. Each record states the constraint that
forced the choice, the alternative that was rejected and why, the consequences the project has to live with, the
cost, and the evidence that would later prove the decision paid off.

Status vocabulary: **Accepted** (adopted, reversible by a superseding ADR) · **Proposed** (recommended, awaiting an
owner action or a measurement) · **Superseded** (replaced, with a link).

No decision below has been executed against production. Nothing in this file authorises a purchase, an account, a
provisioning action or a deployment; where a decision needs one, it says so and stays `Proposed`.

Baseline: `7a75d39`, 2026-11-01. Cross-references: [INFRASTRUCTURE_AUDIT.md](INFRASTRUCTURE_AUDIT.md) (`INF-*`),
[docs/audit/REPOSITORY_AUDIT.md](../audit/REPOSITORY_AUDIT.md) (`OPS-*`, `SEC-*`),
[PRODUCTION_READINESS_MATRIX.md](PRODUCTION_READINESS_MATRIX.md).

---

## ADR-001 — Keep the existing AWS EC2 host and Docker Compose as the deployment target

**Status:** Accepted, **contingent on a host survey that cannot be performed today** (INF-03).
**Date:** 2026-11-01.

**Context.** Mandate §2 requires that "existing AWS EC2 infrastructure must be evaluated before recommending a
replacement", and §7 names the preferred target as *existing EC2 + Docker Compose + secure reverse proxy +
automated release deployment*. The repository already contains a compose stack, two Dockerfiles and an SSH deploy
workflow. The EC2 instance is currently unreachable from two independent observation points (§6.1 of the audit).

**Decision.** Do not introduce a new platform. Modernise in place: the same host, a reviewed version of the
compose topology, images from a registry, and a deployment script that lives in this repository. Evaluate
alternatives only if the host survey shows the instance cannot hold two stacks (ADR-008).

**Alternatives considered.**
- *Container service / managed PaaS (ECS Fargate, App Runner, Fly.io, Render)* — better operational story per
  dollar of effort, but every one of them is a new recurring cost and a new credential surface, which §2 forbids
  without approval. Rejected for cost, on the merits of a separate evaluation once GMS has paying tenants.
- *Kubernetes* — rejected outright in ADR-007.
- *Rewriting the deploy as a pure `git pull && docker compose up` on the host with no registry* — keeps zero new
  services, but loses artefact lineage (§6.2 requires provenance between commit and running code), so it was
  narrowed to the *mechanism* while adding a registry for the *artefact* (ADR-004/005).

**Consequences.** The programme inherits a single-host, single-database failure domain, root-volume constraints it
cannot yet measure, and an SSH trust model. It also inherits zero new spend, which is the governing requirement.
If the host turns out to be unrecoverable, this ADR is superseded rather than quietly ignored — the review of the
existing infrastructure is recorded here so a replacement can be argued from evidence, not from convenience.

**Cost.** $0 incremental. No resource is resized, replaced or created by this decision.

**Verification that the decision was right.** Within 30 days of a host survey: the instance holds two compose
projects plus a proxy within its memory/disk headroom; a full deploy from the registry completes in under 5
minutes; no new billable AWS resource was required.

---

## ADR-002 — Split compose into `compose.yaml` + `compose.dev.yaml` + `compose.prod.yaml`

**Status:** Accepted. **Date:** 2026-11-01.

**Context.** `docker-compose.yml` is the only compose file and mixes intents: it publishes developer port bindings
(`:12-13`, `:41-42`, `:61-62`), defaults `SPRING_PROFILES_ACTIVE` to `dev` (`:39`), and injects the whole app `.env`
into every service (`:6-7`). Mandate §4.5 asks for exactly this split and warns against file proliferation.

**Decision.** Three files, no more:
- `compose.yaml` — service definitions, names, networks, volumes, log policy, healthcheck *intervals*. No ports,
  no `env_file`, no profile default.
- `compose.dev.yaml` — loopback-bound `127.0.0.1:` port mappings, bind-mounted source where hot reload needs it,
  `SPRING_PROFILES_ACTIVE=dev`, `ddl-auto` relaxation **only** here.
- `compose.prod.yaml` — `image:` from the registry (never `build:`), `restart: unless-stopped`, resource limits,
  `internal: true` data network, only the proxy publishes a port, explicit `env_file` per service.

**Alternatives considered.** *One file plus `profiles:`* — fewer files, but a production flag inside the same
document is how INF-02's published 5432 reaches a real host; rejected. *A fourth `compose.staging.yaml`* — staging
is the same file with a different `COMPOSE_PROJECT_NAME` and env file (ADR-008); a fourth file would be
proliferation without a difference.

**Consequences.** The Makefile and the deploy script must always pass `-f` twice, which is easy to get wrong once
and therefore must be asserted, not remembered: Infra-M2 adds a CI job that renders both configurations and greps
the prod render for `build:`, `5432:` and `SPRING_PROFILES_ACTIVE: dev`. Compose merges `environment:` maps
per-key rather than replacing them, so per-service env *files* become the primary channel and inline
`environment:` stays minimal — a rule the current file already half-follows (`:33-35`).

**Cost.** $0.

**Verification.** `docker compose -f compose.yaml -f compose.prod.yaml config` shows no build section, no db port
and `prod` profile; the dev variant keeps the developer bindings; both exit 0 on `config --quiet`; `make up` boots
the dev pair on a clean clone.

---

## ADR-003 — Caddy as the reverse proxy and TLS terminator

**Status:** **Proposed** — needs the domain decision in §5 of the audit (owner approval) before it can be
executed, and needs the host survey (ADR-001) before it can be installed. **Date:** 2026-11-01.

**Context.** No TLS terminates anywhere in the request path (INF-01), and the deploy's post-deploy check depends
on the very port that must be closed (INF-14). The proxy must therefore do four things at once: terminate TLS,
enforce HTTPS, publish the only public port, and give the health gate something to probe.

**Decision.** Caddy, as a container on the `edge` network, configured by one versioned `Caddyfile`. Automatic
ACME issuance and renewal, HTTP→HTTPS by default, sane headers, `reverse_proxy` to `backend:8080`, and
`metrics` on an internal listener. `nginx` remains only as the SPA asset server if the frontend stays on Vercel
(ADR-013), in which case even that container disappears.

**Alternatives considered.**
- *nginx + certbot* — the team already knows nginx (`gms_frontend/nginx.conf`) and it is marginally smaller, but
  renewal needs a cron, a hook and a reload path that must be tested; on a host nobody can SSH into, an untested
  renewal is how a certificate expires silently. Chosen against.
- *Cloudflare in front* — free tier would give TLS without a domain challenge, but it inserts a third party into
  the request path for a system holding student PII, and its free plan still requires an account creation the
  owner has not approved. Chosen against on privacy and on §2.
- *Vercel-only, origin stays plaintext* — this is effectively today's architecture. It protects browser→Vercel and
  leaves Vercel→origin in the clear; §8 of the mandate asks for HTTPS enforcement on the entry point we own.
  Chosen against.

**Consequences.** A second runtime dependency on the host, one more container to update, and an unavoidable
external dependency: ACME needs a DNS name the owner controls. Until that exists, Caddy can serve an internally
trusted CA or a self-signed certificate, which is honest only for machine clients — browsers will refuse it. The
decision therefore has a hard predecessor: the domain. If the owner declines to buy one, the fallback is to keep
Vercel's TLS and restrict the origin to Vercel's egress ranges, which is a weaker guarantee and must be recorded
as such rather than presented as "HTTPS enabled".

**Cost.** $0 for Caddy (open source, free tier needs nothing). The domain it depends on is ~$10–15/year and is
**not** approved.

**Verification.** `curl -sI https://<origin>/api/v1/ping` → 200 with TLS 1.2+, valid chain, HSTS present;
`http://` → 308; the certificate renews unattended and the renewal failure path produces a visible signal;
external `nc -zv <host> 8080 5432` both fail; the deploy's health gate passes through the proxy.

---

## ADR-004 — GitHub Container Registry, git-SHA image tags, digest recorded at deploy

**Status:** Accepted as the design; implementation is Infra-M3. **Date:** 2026-11-01.

**Context.** Builds today produce `gms-v1-backend:latest` and `gms-v1-frontend:latest` with no revision label
(§7.1 of the audit); the deploy builds nothing at all (INF-09). §6.2 requires traceable tags, the recorded commit
SHA, release metadata and provenance between source and deployed image, and forbids `latest` as the only release
identifier.

**Decision.** Publish to **GHCR**, keep the packages **public** (matching the repository's visibility, so no
private-package storage is consumed — relevant because artefact/package storage is a shared quota, INF-16). Tag
`ghcr.io/shubhamdiyos/gms-backend:<git-sha>` and `:latest` (the latter as a convenience pointer only, never as a
deploy input). The deploy job resolves the tag to `sha256:…` **once**, pins the digest in the compose render, and
writes `release.json` (sha, digest, image tags, migrations applied, timestamp, actor, CI run id) to the host.

**Alternatives considered.** *Docker Hub* — free for public images but a second account and token to manage;
rejected for one fewer credential. *Build on the host from a git checkout* — no registry at all, but the running
artefact is then untagged, uncacheable and unrollbackable without a rebuild; rejected against §6.2. *Amazon ECR* —
technically tidy given EC2, but adds a billable resource (~$0.10/GB-month) and an IAM surface for no capability
gain; rejected under §2.

**Consequences.** A `GITHUB_TOKEN`-scoped push permission must be declared explicitly (`packages: write`) and no
other workflow should get it. GHCR requires the package to exist or be visible-able before first push, which is a
one-time step. Keeping packages public means the image contents are world-readable — acceptable only because
INF-05/INF-17 first ensure no secret is ever inside a layer; that dependency is why ADR-004 cannot land before
the container work in Infra-M1.

**Cost.** $0 for public images on GHCR.

**Verification.** Two CI runs on one commit yield identical digests; the release file on the host names a digest
that `docker inspect` confirms is running; `docker history` and a filesystem scan of the pushed image contain no
`.env`, no key and no credential; rolling back means changing one tag and re-running, and is executed once in
front of a reviewer.

---

## ADR-005 — Keep SSH as the transport, version the deployment it executes

**Status:** Accepted; implementation is Infra-M3. **Date:** 2026-11-01.

**Context.** The existing path SSHes to the host and runs `~/deploy_backend.sh`, a script that is not in the
repository (OPS-02, INF-03) and which is the reason nothing about production is reproducible. §6.3 also demands
that deployment never run from untrusted pull-request code, that it be serialised, and that SSH keys never appear
in logs.

**Decision.** Keep SSH — it exists, costs nothing, and needs no new agent on the host — but replace the remote
script with `scripts/deploy.sh` **in this repository**, which the workflow copies to the host and executes by
checksum. Add: `environment: production` with a required reviewer, `cancel-in-progress: false` with a real lock
file (`flock`) so two deploys cannot interleave, an explicit `--sha` argument that the host must echo back, and a
refusal if the running digest does not match the intended one. Secrets stay in the host's env files, never in the
artefact and never in a log line.

**Alternatives considered.** *Ansible* — better for multi-host, and the day GMS has two hosts it becomes the right
answer; today it adds a Python control dependency and a new vocabulary for one machine. *A pull-based agent
(systemd timer polling GHCR)* — eliminates inbound SSH entirely, which is a genuine security improvement, but
cannot provide the human approval gate §6.3 asks for and adds a component to debug on a host nobody can reach.
*GitHub Deployments/Environments with a webhook to a hosted runner* — the approval UI is exactly what is wanted
and this decision uses `environment:` for it, but the trigger still needs a path onto the host, which is SSH.

**Consequences.** Inbound SSH to the host remains open, so the firewall/known-hosts work (OPS-03 done, INF-10) is
a precondition rather than a nicety, and the deploy identity must be a dedicated non-root user with sudo limited to
the compose commands, not the account the operator uses interactively. The lock file must be tested for the
crash case — a lock left behind by a killed job must not wedge deployment forever.

**Cost.** $0.

**Verification.** Two concurrent deploy attempts serialise observably; a second `environment` reviewer can veto a
deploy; the workflow fails if the deployed digest ≠ the intended digest; `~/deploy_backend.sh` no longer exists on
the host and nothing references it; the deploy script in the repo is byte-identical to the one the host executes.

---

## ADR-006 — Flyway, with an expand-migrate-verify-contract discipline

**Status:** **Proposed** — the baseline requires an authorised production schema export that has not been given.
**Date:** 2026-11-01.

**Context.** No migration tool exists: `grep -i flyway gms_backend/pom.xml` returns nothing, there is no
`db/migration` directory, and every `spring.flyway.*` key in `application.properties:40-41` and
`application-prod.properties:15-17` is inert (OPS-07). The live schema was produced by Hibernate `update`, so it
has drifted from the entity model with no record (INF-11). §9 forbids destructive migrations run automatically,
forbids concurrent migrations from replicas, and requires a controlled migration step.

**Decision.** Flyway Community. Baseline created from the **authorised** prod schema export — reviewed by a human,
committed as `V1__baseline.sql`, with `baseline-on-migrate=false` retained so a fresh empty database is the only
thing that may run it. Thereafter every schema change is a numbered migration; `ddl-auto` is `validate` in every
profile except dev, and dev's `update` relaxation is deleted once V1 exists. Migrations run as a **one-off job**
(`docker compose run --rm migrate`) before the new application container starts, never from the application's own
boot, so two replicas can never race.

**Alternatives considered.** *Liquibase* — stronger for undo and for heterogeneous databases, at the cost of XML/YAML
changelog ceremony for a single Postgres target. *Hibernate `ddl-auto=update` forever* — the current de facto
answer, and precisely what makes rollback impossible; rejected. *jOOQ Flyway* — no advantage here.

**Consequences.** A baseline built from a schema nobody has inspected may itself contain drift; therefore V1 is
reviewed against the entity model and any mismatch becomes an explicit follow-up migration rather than a silent
fix. `clean-disabled=true` must stay (already set at `application-prod.properties:17`). Rollback of a migration is
**never automatic** — the discipline is forward-only, which is why §7 requires the
expand → migrate → verify → contract sequence: add the nullable column, backfill, switch reads/writes, then drop
the old column in a later release. Every migration that cannot follow that shape is documented in the runbook as
requiring manual recovery, with a backup taken first (ADR-009).

**Cost.** $0 (Flyway Community is open source; no database service changes).

**Verification.** `mvn flyway:validate` green in CI; a fresh database builds to the current schema from migrations
alone, with no Hibernate DDL; prod boot under `ddl-auto=validate` succeeds against that schema; a migration that
breaks the contract fails the pipeline before any deploy; the expand/contract sequence is rehearsed on staging
with a copy of masked data.

---

## ADR-007 — No Kubernetes

**Status:** Accepted. **Date:** 2026-11-01.

**Context.** §7 explicitly says not to introduce Kubernetes unless scale and operational requirements justify it.

**Decision.** Docker Compose on a single host. Revisit when there is more than one host, more than one tenant
isolation boundary needing hard network policy, or an availability requirement that a single instance cannot meet.

**Alternatives considered.** *Nomad* — lighter, but still a scheduler to operate for three containers. *EKS* —
a control-plane cost and an IAM surface; §2 rules it out.

**Consequences.** Scaling beyond one host is a rewrite of this decision, not a config change — accepted
deliberately, because the current failure mode is not capacity, it is exposure and reproducibility. Compose gives
no rolling update across replicas; blue/green must be built at the proxy (ADR-014).

**Cost.** $0.

**Verification that the decision was right.** Two consecutive quarters in which no outage is attributable to
single-host scheduling; no tenant requires isolation stronger than an internal network plus per-request
`school_id` enforcement (which is application work, tracked as TEN-01…07).

---

## ADR-008 — Staging as a second compose project on the same host

**Status:** **Proposed** — blocked on the host capacity measurement (INF-03) and on the masked fixture.
**Date:** 2026-11-01.

**Context.** There is no staging; the only deploy target is production, and `application-prod.properties` has
never booted (INF-11). §5 says not to assume a separate paid staging environment is affordable, and §5 also
requires that automated tests can never reach a production database.

**Decision.** Two compose projects on the existing host: `gms-prod` and `gms-stage`, each with its own project name,
its own volume, its own networks and its own env file, bound to different loopback ports and reachable only
through the proxy. Staging is fed from a **masked sample dataset**, never a copy of live PII, and the `prod`
Spring profile is exercised there before production sees it.

**Alternatives considered.** *A second small EC2 instance* — cleanest isolation, but new recurring spend needing
approval, and the mandate says quantify before recommending. *Containers-in-CI only* — proves boot but not the
long-lived behaviour (restart policies, log growth, disk pressure). *No staging* — the current state, and the
reason an unbooted profile is scheduled to be the first thing production runs.

**Consequences.** One noisy neighbour: staging and production share a kernel, so resource limits (INF-07) stop
being optional — an unbounded staging JVM will OOM the production database. Staging credentials must be a
separate JWT secret and a separate DB role, otherwise a staging token authenticates against production and the
environment boundary is decorative.

**Cost.** $0 if the host has headroom — **which is currently unmeasurable**, so this ADR stays `Proposed`. If it
does not fit, the choices are a larger instance (approval required) or a documented decision to test the `prod`
profile locally against a throwaway database and accept weaker evidence.

**Verification.** `curl https://stage.<domain>` works, `nc -zv <host> <stage db port>` from outside fails; staging
has its own volume that a production deploy cannot touch; a production token is rejected by staging and vice
versa; the prod-profile boot under `ddl-auto=validate` completes there.

---

## ADR-009 — Encrypted `pg_dump`, on-host rotation, and one owner-approved off-host destination

**Status:** Accepted for the on-host mechanism; **blocked** for the destination, which is an owner cost decision.
**Date:** 2026-11-01.

**Context.** No backup, no restore, one volume, one copy (INF-13, OPS-16). §9 requires encrypted backups,
verified integrity and restores tested in isolation; §12 forbids inventing RPO/RTO without testing. Measured:
the dev database is 9.4 MB and `pg_dump | gzip -1` is 12.7 KB, so storage volume is not the problem — placement is.

**Decision.** `scripts/db-backup.sh` runs on the host: `pg_dump -Fc` → `age` (or GPG) encryption with a key held
**outside** the repository and outside the image → SHA-256 checksum → append to a manifest → prune to 7 daily + 4
monthly. `scripts/db-restore.sh` restores into a throwaway container on a different compose project, runs
`ANALYZE`, reports per-table row counts and exits non-zero on a checksum or count mismatch. A weekly automated
restore is the integrity test; a restore nobody has run is not a backup.

**Alternatives considered.** *WAL archiving / PITR* — better RPO, but a continuous second stream to another
location and an operational failure mode of its own; deferred until there are paying tenants. *Filesystem snapshots
on EBS* — cheap and fast, but a snapshot of a live Postgres without a checkpoint is a corrupt dump. *Volume copy
into the same volume group* — no protection against the host being lost.

**Consequences.** Off-host placement is unavoidable for a real backup and is exactly the thing this programme may
not decide unilaterally. Until an owner-approved destination exists, the truthful documentation states: **backups
are on-host only, and a lost host means lost data** — no RPO/RTO claim may be written anywhere that implies
otherwise. Encryption introduces a key-management dependency: a backup encrypted with a key that is also on the
host is not a backup, so the recovery procedure requires the key to be escrowed off-box by the owner.

**Cost.** $0 on-host. Any object storage at measured scale is under $1/month (S3 Standard ≈ $0.023/GB-month),
which is still an unauthorised recurring charge and needs the owner's explicit yes.

**Verification.** A restore executed end-to-end on a machine other than the source; row counts and checksums
compared and recorded; a corrupted dump rejected with a checksum error; the key demonstrably absent from the
repository, the image and the host's env files; the weekly restore job observed failing loudly when pointed at a
bad file.

---

## ADR-010 — Logs and health endpoints first; Prometheus deferred; no hosted monitoring

**Status:** Accepted. **Date:** 2026-11-01.

**Context.** `micrometer-registry-prometheus` is a dependency (`pom.xml:59-60`) yet
`management.endpoints.web.exposure.include=health,info` (`application.properties:65`) means
`/actuator/prometheus` is never served; the trace id reaches MDC and the response header but no log pattern
( INF-15). §10 asks for a practical baseline and warns against building an expensive stack unnecessarily.

**Decision.** Ship the cheap three first: (1) a console pattern carrying `%X{traceId}`, (2) `/actuator/health`
with liveness/readiness separated and `show-details=never`, `/actuator/info` reporting the build SHA the CI
injected, and `/actuator/prometheus` behind the existing `ADMIN`/`SUPERADMIN` rule in `SecurityConfig:115`, and
(3) small shell checks on the host (disk, memory, container health, HTTP probe) whose failure is visible. Do not
deploy Prometheus, Grafana or Loki yet — the host's capacity is unknown and an observability stack that competes
with the JVM and Postgres for RAM is how INF-07 becomes an outage.

**Alternatives considered.** *Grafana Cloud / BetterStack / Datadog free tiers* — all require an account, a
credential and, for anything useful, a paid plan; §2 forbids it and §10 discourages it. *Uptime-Kuma or Gatus
self-hosted* — a good next step, but they are a second service to keep alive on the host whose failure also
removes the signal; deferred to Infra-M5. *Structured JSON logs now* — do it as part of (1) only if a JSON encoder
costs no RAM; otherwise the pattern change is enough.

**Consequences.** Without a metrics database there is no history, so the baseline questions in §10 are answered by
probes and logs at the moment of asking, not retrospectively. That is an honest limitation of zero-cost operation
and must be written in the operations guide rather than glossed. Promoting this ADR to "run Prometheus" is a
capacity decision that needs the host survey and its own ADR.

**Cost.** $0.

**Verification.** A request's trace id is greppable in the container logs for that request within 5 minutes;
`/actuator/info` reports the SHA that the deploy job claims; unauthenticated `/actuator/prometheus` is 401/403
while an admin token gets metrics; the disk check provably fires on a filled test volume.

---

## ADR-011 — Artefacts on `master`, reports on pull requests

**Status:** Accepted. **Date:** 2026-11-01.

**Context.** Every `ci.yml` run uploads a 53.4 MB jar plus JaCoCo; 6 live artefacts = 154.74 MB against a 500 MB
plan quota that is shared with Packages (INF-16). No workflow consumes the PR jar.

**Decision.** `if: github.event_name == 'push' && github.ref == 'refs/heads/master'` on the jar upload; PR runs
publish only small reports (lint output, JaCoCo XML with `retention-days: 2`). Once ADR-004 exists, the jar stops
being an artefact altogether and becomes a registry image — the artefact is the wrong medium for a build output
that has to be deployable.

**Alternatives considered.** *Compress the artefact* — a 57 MB Spring Boot jar is already ~zip; no meaningful win.
*Shorter retention only* — keeps the waste, hides the meter. *Keep uploading "for debugging"* — debugging happens
by re-running CI on the branch, which is free on a public repo.

**Consequences.** A PR run no longer leaves a downloadable jar; reviewers who want it must build locally or fetch
the release image. Accepted, because nothing has ever fetched it.

**Cost.** Target $0; current exposure ≈ 1.1 GB steady-state at 3 runs/day against a 500 MB quota, overage billed
per GB-month.

**Verification.** After one week of normal activity, live artefact bytes stay under a stated ceiling and PR runs
produce no jar; a job asserts the ceiling and fails if exceeded.

---

## ADR-012 — Two milestone series, explicitly named

**Status:** Accepted. **Date:** 2026-11-01.

**Context.** The first mandate's milestones were M0–M7 and its M1 (the security package) is merged as PR #10.
The infrastructure mandate numbers its own milestones M0–M6, so "M1" now means two different finished things
depending on which document is open — a real ambiguity in `docs/audit/*`, which reference "M1" freely.

**Decision.** Keep the mandate's numbering, always prefixed outside its own document: **Infra-M0 … Infra-M6**.
Security-programme milestones are cited as **M1 (security)**, and the audit documents link rather than renumber.
`docs/audit/TECHNICAL_DEBT.md` and `docs/audit/REPOSITORY_AUDIT.md` keep their existing references untouched.

**Consequences.** Prose is slightly clunkier; the alternative — renumbering an already-merged programme's
milestones — would silently invalidate every existing citation, which is worse.

**Cost.** $0.

**Verification.** Searching the docs for "M1" resolves to exactly one meaning per file, and
[INFRASTRUCTURE_AUDIT.md §9](INFRASTRUCTURE_AUDIT.md) is the single place the infra series is defined.

---

## ADR-013 — Keep the SPA on Vercel; make the EC2 origin API-only

**Status:** Proposed (depends on ADR-003's domain decision). **Date:** 2026-11-01.

**Context.** The frontend is served by Vercel with a rewrite to the EC2 backend (`vercel.json:5`), while a second,
contradictory path exists: `gms_frontend/Dockerfile` + `nginx.conf`, which has no `/api` proxy at all and is
therefore unable to serve the application (OPS-10). Two deployment paths for one artifact, neither complete.
Confirmed live during M0, not inferred: a Vercel GitHub App is already wired to this repository — PR #11 reported a
`Vercel` check against `shubhamdiyos-projects/gms-react` — so preview deployments are produced per pull request by a
third party that no document in this repository names, and its preview-URL access policy is unverified.

**Decision.** Declare the Vercel path canonical and the container path **not deployed**. Serve the SPA from Vercel
(free HTTPS, free CDN, no host load), and let the EC2 host run API + Postgres only, behind Caddy. Fold the
frontend image out of the production compose stack — or keep it as a *self-hosted option* with a working `/api`
proxy, but then say plainly that it is the fallback, not the live path. Committing the origin hostname to a public
repository is removed at the same time by moving the rewrite target into a Vercel environment variable.

**Alternatives considered.** *Everything on EC2* — one less third party and no plaintext Vercel→origin hop once
Caddy is in place, and it is the right answer if the owner refuses to buy a domain, because Vercel's free
`*.vercel.app` origin cannot present a trusted certificate for a custom API hostname. *Vercel for API too* —
serverless functions are a new billable service under load, and the `api/v1/[...path].js` proxies that attempted it
are dead code importing a dependency that is not in `package.json`.

**Consequences.** The deployment topology in `compose.prod.yaml` has one fewer public service, which makes the
networking fix in INF-02 simpler. But it keeps a cross-cloud hop that only a domain and a TLS origin can protect,
and it keeps Vercel — an external party — in the request path for tenant data, which should be an explicit
acceptance in the privacy review rather than an accident of history.

**Cost.** $0 on Vercel's hobby tier for a project of this traffic; no new spend introduced by this decision.

**Verification.** One documented path from browser to data; the dead path is either fixed and tested or deleted;
`gms_frontend/vercel.json` no longer contains the origin hostname; a cold-start request through the proxy returns
200 over HTTPS end-to-end.

---

## ADR-014 — Be honest about what "zero downtime" means here

**Status:** Accepted. **Date:** 2026-11-01.

**Context.** §7 says not to claim zero downtime if the architecture cannot guarantee it. A single host, a single
backend container and a `compose up -d` recreate cannot be overlap-safe today: there is no readiness gate in the
proxy, no graceful shutdown (`server.shutdown` unset, INF-06/§2.1), and no second instance.

**Decision.** Infra-M3 delivers a **brief, declared interruption**: drain (`server.shutdown=graceful` +
`spring.lifecycle.timeout-per-shutdown-phase=30s`), replace, wait for `/actuator/health/readiness` to report
`UP`, then route. Blue/green — two backend containers on different loopback ports with Caddy switching upstreams
— is the target once ADR-001's capacity measurement confirms there is RAM for two JVMs, and it is recorded as a
follow-up rather than a claim. Rolling deployment needs ≥2 instances and is out of scope.

**Consequences.** The window is a few seconds and must be *measured and published*, not asserted; a migration that
cannot run while the old version is live (ADR-006) still causes an interruption regardless of the switching
mechanism, which is the more honest number to communicate to school administrators.

**Cost.** $0 for drain; blue/green costs a second JVM's RAM on the same host — quantified before adoption, not
after.

**Verification.** A deploy observed from a client loop records HTTP status and latency per second; the number of
non-200 responses is reported in the runbook; SIGTERM mid-request is tested to prove in-flight work completes;
blue/green may only be claimed after a rollback is executed by switching the upstream back.

## Amendments — the third mandate (cloud infrastructure & production execution, 2026-11-01)

The owner supplied the target platform the audit could only ask about: Oracle Cloud Always Free ARM64 compute, a
fresh owner-owned Neon PostgreSQL, Caddy, and an existing domain (`bytencia.com`) that must not be repurchased.
That is a **superseding input**, so the affected decisions are amended here rather than rewritten in place — the
original text above stays as the record of what was known and why it was chosen then.

| ADR | Amendment | New status |
|---|---|---|
| 001 Keep EC2 + Compose | **Superseded by ADR-015.** The owner states the EC2 infrastructure is *permanently closed* and is not a deployment target. The "host survey that cannot be performed" is no longer pending — it is moot. Compose survives; the host does not. | **Superseded** |
| 003 Caddy + real TLS | **Un-blocked on cost, still blocked on access.** The domain exists and is owned, so the ~$10–15/yr line in the audit's cost ledger is withdrawn. What remains is DNS control: a record can only be added where `bytencia.com`'s DNS is actually hosted, and Caddy can only issue a certificate once that record resolves to the OCI public IP. | **Proposed → Accepted, pending DNS access** |
| 005 Keep SSH, version the deployment | **Superseded by ADR-017** for the transport question. The `EC2_SSH_KNOWN_HOSTS` constraint that ordered the whole programme dies with the EC2 host, and the dead-secret inventory changes: `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY` become artefacts to delete, not to complete. | **Superseded** |
| 006 Flyway baseline | **The blocker disappears and is replaced by a different one.** There is no production schema to export — the target database is fresh, so `baseline-on-migrate` is not the path; V1 must be *generated from the JPA model* and validated against a disposable PostgreSQL before anything connects. The audit's `ddl-auto=validate` + Flyway-enabled `prod` profile is now the intended first boot, not a migration of unknown data. | **Proposed → Accepted (fresh baseline)** |
| 008 Staging as a second project | Capacity is no longer unknown in the abstract: the target shape is declared as 2 OCPU / 12 GB / ~100 GB. That is enough to stop deferring the question, but the numbers are *requested*, not *verified* — Always Free eligibility and Mumbai capacity must be measured on the real account (ADR-015's verification step) before staging is promised. | **Proposed (capacity declared, unverified)** |
| 009 Encrypted dumps, tested restores | Source changes from a self-hosted container to **managed Neon**, which changes what "off-host" means: the dump leaves a third party's service rather than a volume on your own box. The mandate is explicit that Neon free-tier restore is *not* an accepted substitute, so the requirement stands and the free destination must be verified against current limits, not tutorial claims. | **Accepted, mechanism re-targeted** |
| 013 Vercel SPA, API-only origin | The "API-only origin" half is now about OCI, not EC2. The `vercel.json` rewrite that pointed `/api/*` at `http://ec2-…:8080` is **deleted in this branch**: the host is closed (TCP connect to 65.0.109.47:8080 times out after 8 s, DNS still resolves), so the rewrite could not be carrying traffic and its removal cannot break a working path. Until the OCI origin and TLS exist, the deployed SPA has **no API route at all** — a deliberate, stated gap rather than a silent one. | **Proposed, origin re-targeted** |

### ADR-015 — Oracle Cloud Always Free ARM64 as the compute target

**Status:** Accepted as the target; **not provisioned**. **Date:** 2026-11-01.

**Context.** The mandate names `VM.Standard.A1.Flex` (2 OCPU / 12 GB, Ubuntu 24.04 ARM64, ~100 GB boot volume) in
India West (Mumbai) on an already-active account, free tier only. The audit's ADR-001 could not recommend a
replacement for EC2 because the existing host could not be measured; the owner has now removed that need by
closing it.

**Decision.** GMS targets one OCI ARM64 virtual machine running Docker Compose, behind Caddy, with the database
external (Neon). Nothing is created until eligibility, capacity and charge are read from the real account.

**Alternatives rejected.** *Managed PaaS (Fly.io/Railway/Render/Paasfly)* — free tiers there are metered
hours/credits and routinely convert to bills, which fails "₹0 infrastructure expenditure" less safely than an
Always Free ARM shape with a declared allowance. *Kubernetes* — already rejected (ADR-007) and unjustifiable for
one service pair. *Keeping EC2* — not available; the account is closed.

**Consequences.** Every image must be built for `linux/arm64` — the audit's build evidence on this machine is
already arm64 (Colima), but the CI build job must publish multi-arch or arm64 explicitly or the deploy pulls the
wrong architecture. Outbound traffic and any ephemeral-public-IP policy must be checked against current OCI
terms, not remembered ones. A self-managed VM keeps the whole hardening surface (mandate §9.4: SSH, firewall,
unattended-upgrades, Docker daemon not exposed, non-root deploy user) that a PaaS would have absorbed.

**Cost.** ₹0 target. **Verification.** `oci instance get` + the console's own eligibility flag, the shape's
`available` capacity response in `ap-mumbai-1`, a zero line in the billing report after 24 h, and
`uname -m` = `aarch64` over SSH.

### ADR-016 — Fresh owner-owned Neon as the production database; the old Neon is retired, not migrated

**Status:** Accepted; **account not created**. **Date:** 2026-11-01.

**Context.** The credential committed in `application.properties` before M1 (SEC-01) belonged to a **friend's**
Neon account. The owner does not control that account and cannot rotate, restore or migrate from it.

**Decision.** Treat the old database as RETIRED: no access attempts, no recovery, no data migration, no host or
project identifier reintroduced anywhere — including in git history, which will still contain the literal. A new
project (`gms-production`, database `gms`, Free plan, `ap-southeast-1` if suitable) is created under the owner's
own account, and the schema arrives only through the Flyway V1 of ADR-006.

**Alternatives rejected.** *Ask the friend to rotate the credential* — it keeps production dependent on an account
the project does not own, which is the actual risk SEC-01 was about. *Dump and restore the old data* — explicitly
forbidden by the mandate, and the schema is not reproducible from the repository anyway (OPS-07).

**Consequences.** SEC-01's remediation changes shape: the exposure can never be *revoked by the project*, only
*abandoned*, so the finding closes as **RETIRED (unrotatable by design)** with the residual risk owned by the
friend's account, and the repository's obligation is that no live configuration references it. A fresh database
also means **no data exists to test against**, which makes the synthetic-fixture work (Phase 1) a prerequisite for
every integration and tenant-isolation test rather than a nicety.

**Cost.** ₹0 (Free plan) — limits to be read from the account, not from documentation. **Verification.** The
project exists under the owner's login, the connection string is TLS-only and pooled, the app boots against it
with `ddl-auto=validate`, and `git grep` for the retired host returns only historical documentation.

### ADR-017 — Deployment transport to OCI, decided after the host exists

**Status:** **Proposed** — deliberately not accepted yet. **Date:** 2026-11-01.

**Context.** ADR-005 kept SSH because the alternative was to rewrite a working (if undocumented) pipeline. There is
no pipeline now: `deploy-backend.yml` is deleted in this branch, so the question is open on a clean slate.

**Options to choose between, once the VM is real:** (a) SSH + `docker compose pull && up -d` from a versioned
`scripts/deploy.sh`, with an OCI-generated key pair and a pinned `known_hosts`; (b) a registry-pull systemd unit
with no SSH in the deploy path at all; (c) an OCI-native mechanism if the account's free terms make one
available. The mandate's rules apply to all three — never deploy untrusted PR code, never expose a private key,
deployment approval before the deploy step, health verification after it.

**Why not decided now.** Choosing a transport before measuring the host is how the EC2 pipeline became an
untracked remote script. **Verification.** Whichever wins, the acceptance test is the same one Infra-M3 always
required: one deploy observed end-to-end on a non-production target, and **one rollback executed**.

### ADR-018 — OpenTofu, not Terraform, and guards written in HCL rather than in prose

**Status:** **Accepted**. **Date:** 2026-11-01.

**Context.** Withdrawing the OCI Console browser route in favour of the CLI or infrastructure-as-code makes a
tool decision unavoidable: the §9 topology (VCN, IGW, subnet, security list, one flexible-shape instance) had
been described in prose in four places and executed nowhere. Prose describing a network is also how §9.2's
"never expose 5432/8080/3000" survives — or does not — the next person who edits it.

**Decision.** Infrastructure definitions live in [`infra/oci/`](../../infra/oci/README.md), written in
OpenTofu v1.13.2 against the `oracle/oci` provider pinned to **9.11.0** in the committed
`.terraform.lock.hcl`. Two halves: the tool, and the discipline that the *rules* in §9 are encoded as
`validation` blocks and locals instead of comments.

**Alternatives rejected.** *Terraform* — BUSL-1.1 since Aug 2023; OpenTofu is the MPL-2.0 fork and resolves the
same upstream provider, so nothing is given up for a licence that is not free. *Pulumi* — a second runtime and
a dependency on a vendor cloud for state, for a stack this size. *Ansible or shell against the Console* — the
route just withdrawn, and it holds no state, so it cannot answer "does this already exist", which is the
question that prevents a second VCN.

**The part that matters more than the tool choice.** Four of §9's rules are enforced by construction rather
than by care, and every row below was executed rather than assumed — first on this machine, and since PR #18 on every
change to the stack in CI:

| Rule in §9 | Mechanism | Measured |
|---|---|---|
| "Do not select paid shapes" | `local.shape = "VM.Standard.A1.Flex"` — the shape is **not a variable** | no argument can select another shape |
| "SSH port 22: restrict to my current public IP /32" | `validation` requiring a `/32` prefix | `ssh_source_cidr=0.0.0.0/0` → plan fails; `203.0.113.0/24` → plan fails |
| "Never upload, transmit, or expose my private SSH key" | `validation` requiring a `.pub` path, because the value is read by `file()` into `metadata` | `ssh_public_key_path=/Users/x/.ssh/id_rsa` → plan fails |
| "verify … storage allocation" (200 GB block allowance) | `validation` bounds: boot 50–200 GB, OCPUs 1–4, RAM 1–24 GB | `boot_volume_size_gb=400` → plan fails; `shape_ocpus=8` → plan fails |
| "Never expose 5432 / 8080 / 3000" | those ingress rules do not exist; the list is closed at 22/80/443 plus ICMP type 3 code 4 inside the VCN | no such port appears in `network.tf` |

Plus `lifecycle.prevent_destroy` on the instance, and a `check` block that fails with a readable message when
the region has no Ubuntu 24.04 ARM64 image instead of an index-out-of-range.

**Consequences.** `tofu validate` does **not** evaluate `variable` validation blocks; `tofu plan` does.
Measured: all seven invalid values above return `exit=0` and `Success! The configuration is valid` from
`tofu validate`, while the same values each produce one `Error: Invalid value for variable` under `tofu plan`
(with no OCI credentials present — the guard fires alongside the `open ~/.oci/config: no such file or
directory` error, not after it, which is what lets the guards be demonstrated before the tenancy is reachable).
So the command that is safe to run without credentials is also the command that proves nothing about the guards,
and anyone documenting this stack has to say which of the two they ran.

One caveat belongs in the record because it happened: a first pass of that test reported all seven as *caught*
by `tofu validate`, and three later reproductions of the same command — including the identical piping — report
all seven as *not* caught, with captured exit codes. The discrepancy is in the harness (a pipe-then-`grep -q`
loop in an interactive zsh: `grep -q` exits the moment it decides, SIGPIPEs the producer, and the pipeline reports
failure for a command that succeeded), not in OpenTofu. That is now closed rather than merely noted. Two
independent things happened since: the exit-code runs kept saying the same thing, and the same assertion was
re-run on GitHub's runners with the output captured into a variable instead of piped — where a guard physically
deleted from `variables.tf` left `tofu validate` **green** (run 41289417174). A harness written differently is no
longer the only evidence, and both readings agree. The lesson survives the resolution: a claim that flips when the
test is written differently is a reason to write the test down, not to pick the convenient answer — and writing it
down, in CI, is what closed it.

State is local and gitignored. That is honest for one operator on one laptop and unsafe the moment a second
exists: two working copies against one local `terraform.tfstate` is how production gets two VCNs. Remote state
is a migration to plan, not a backend block to add on hope.

**Cost.** ₹0 — OpenTofu and the provider are downloads, and nothing in this decision contacts OCI.
**Verification.** `tofu version` = v1.13.2 (archive SHA-256 checked against the published `SHA256SUMS`),
`tofu init` resolving `registry.opentofu.org/oracle/oci` 9.11.0, `tofu validate` →
`Success! The configuration is valid`, and the guard table above reproduced by running the plans. The suite then
moved off this machine, because a table of results I ran once is not a gate: PR #18 added a `ci.yml` job that runs
`fmt -check`, `init -backend=false`, `validate`, the seven-value guard suite and the §9.2 port grep, and it executed
on the pull request (run 41287513214) and on the push to `master` at that PR's own merge commit (run 41287750885),
7/7 `ok` in each. So a deleted guard now fails a build rather than only failing to be noticed — and that last claim
was itself tested, by deleting one: PR #19 (closed unmerged) removed the `ssh_source_cidr` validation block, and run
41289417174 came back green through `validate` and red at the guard step with exactly the two SSH-source assertions
failing. `tofu validate` passing across a physically deleted guard, on GitHub's runner, is this ADR's central claim
reproduced somewhere other than the machine that wrote it.
**Not verified, and not claimable yet: any plan or apply against the real tenancy**, because
`oci setup config` is interactive-only and the owner has not run it. See ADR-015.

## Amendments — the execution mandate (one roadmap, 2026-11-01)

The owner replaced issue-by-issue work with a single roadmap and stated the infrastructure sequencing directly:
*"Oracle Cloud infrastructure is not required at the beginning of GMS development"*, *"I recommend postponing Oracle
provisioning until the application is ready for deployment"*, and *"We should design GMS for production deployment
from day one, but we don't need to provision production infrastructure from day one."* The interim tooling is Docker
Compose + PostgreSQL locally, Testcontainers for database integration testing, GitHub Actions for CI/CD, and the
owner's own Neon project when production configuration starts. The target decisions survive; **when they are
executed** does not, so the affected rows are amended here rather than rewritten in place.

| ADR | Amendment | New status |
|---|---|---|
| 006 Flyway, fresh baseline | **Timing and profile rule moved into ADR-022.** "Generated from the JPA model" was the right instinct and the wrong authority — a schema generated by `ddl-auto` is a schema nobody reviews, and `application-dev.properties:20` still says `update`. V1 is authored as SQL against a disposable PostgreSQL, and `validate` becomes the only value any profile may carry. | **Accepted, mechanism replaced** |
| 012 Two milestone series | **Amended, not reversed.** A *third* series now exists (the execution mandate's Stage 1…7, in [ENGINEERING_ROADMAP.md](../ENGINEERING_ROADMAP.md) §3). The naming rule survives and extends: `M1 (security)`, `Infra-M2`, `Stage 3`. What changes is that only Stage numbers lead, and [ENGINEERING_ROADMAP.md §3](../ENGINEERING_ROADMAP.md) is the single mapping table — `ADR-012` no longer has to. | **Accepted, extended by the roadmap's §3** |
| 015 OCI ARM64 as the compute target | **Target unchanged, execution moved to Stage 6** (`ADR-019`). The ten stack files in `infra/oci` stay exactly where they are — declared, format-checked, validated and guard-tested in CI, never applied — and the *reason* nothing is applied is now a plan rather than a blocker. | **Accepted as target; provisioning deferred to Stage 6** |
| 016 Fresh owner-owned Neon | **Same deferral.** The ban on the friend-owned database is untouched and absolute. What moves is the account creation itself, to Stage 6, because until the application can be deployed there is no production environment to configure. | **Accepted; creation deferred to Stage 6** |
| 017 Deployment transport, decided after the host exists | **Reinforced.** The host now has a date rather than a dependency: it exists in Stage 6, so this ADR's "after" got a meaning. | **Proposed (unchanged), Stage 6** |
| 003 Caddy + TLS; 008 staging; 009 backups | **All three inherit the deferral** — each needs the host or the database that Stage 6 creates. Their acceptance criteria carry forward verbatim; the readiness-matrix rows move from "gap" to "planned, Stage 6". | **Accepted, re-timed** |

### ADR-019 — Build the application first; provision the cloud when it is deployable

**Status:** **Accepted**. **Date:** 2026-11-01. **Supersedes:** the *sequencing* implied by ADR-015 and ADR-016.
**Does not supersede:** their targets.

**Context.** Two owner instructions arrived a week apart. The 12-point OCI instruction said, in effect, *go and set the
account up*: use the CLI and infrastructure-as-code, inspect before creating, verify cost after provisioning, keep
doing repository engineering meanwhile. The execution mandate then said the opposite about order: Oracle is not
required at the beginning and should be postponed until the application is deployment-ready. Both are instructions
about the same tenancy, and only one of them can be obeyed first. The audit trail matters here, because the earlier
instruction is the one `infra/oci/` was written against, and a future reader will otherwise ask why a validated
OpenTofu stack sits unapplied.

**Decision.** Stages 1–5 (foundation, schema, tenancy, MVP, verification) and CI/CD execute with **zero cloud
resources**: Docker Compose + PostgreSQL locally, Testcontainers in CI (Docker on a GitHub-hosted `ubuntu-latest`
runner costs nothing and is the same engine), and the owner's GitHub Actions allowance as the only billable surface.
`tofu apply`, the Neon project creation, DNS records and TLS are **Stage 6 and 7 work**, and the owner action that
gates them — `oci setup config`, which is interactive and cannot be delegated to an agent — is therefore not a
blocker on anything before Stage 6.

**Alternatives rejected.**
- *Provision now, build against it* — what the OCI instruction implied. It puts a live public host in front of an
  application whose `SEC-01…SEC-05` history includes a committed database credential, a forgeable JWT signing key and
  unguarded cross-tenant deletes. A real host makes those defects real traffic, and it starts the "Always Free"
  clock on resources the application cannot yet use. Rejected on risk, not on cost.
- *Skip the infrastructure-as-code work until Stage 6* — rejects two years of lessons in this repository's own
  history. The `infra/oci` stack and its seven HCL guards are already merged and already run in CI, and PR #19 proved
  a deleted guard fails the build. Declarative definitions authored before the account exists are *why* the eventual
  `apply` is reviewable; writing them under time pressure after the host exists is how accidental resources appear.
  Kept, unchanged, unapplied.
- *Use Vercel/Fly/Railway as an interim deployment* — a paid or quasi-free third-party origin, contradicting both the
  zero-unexpected-cost rule and `ADR-015`'s already-accepted target. Rejected.
- *Neon free tier now, as a development database* — it would give a hosted PostgreSQL with no effort, and it is the
  specific thing the mandate forbids twice: the friend-owned Neon instance is retired and its credentials must not be
  reused, and a second managed database created "temporarily" is the duplicate-resource pattern the OCI instruction
  named. Local Compose is the answer for development, and Testcontainers for CI. Rejected.

**Consequences.** The roadmap's first five stages become purely local and purely testable, which raises the
standard of evidence available at every stage gate rather than lowering it: a schema, a tenant guard and a 50k
performance run are all provable on `ubuntu-latest`. It also means the first time GMS touches a network other than
`localhost` is Stage 6, so the deployment path is exercised *once*, with migrations, tests and the guard suite
already green — instead of being debugged on a live host while a security defect is open. One cost is real and should
be said plainly: nothing in Stages 1–5 will exercise TLS termination, proxy configuration, public-interface
hardening or OCI capacity availability, and each of those has historically been where an unprovisioned plan meets
reality (`ADR-008`'s "declared, unverified" 2 OCPU / 12 GB is one of them). Stage 6 absorbs that risk deliberately,
in the open, as its own gate.

**Cost.** ₹0, and now verifiably so: no cloud API is contacted before Stage 6. GitHub Actions minutes are the only
meter, reported per package as they have been since `ADR-011`.
**Verification.** This ADR is verified by the absence of a resource, which cannot be asserted — it can only be shown.
Two commands are the evidence, both run 2026-11-01: `tofu plan` has **never been executed against the tenancy**
(`oci setup config` has not been run, so no `~/.oci/config` exists on this machine — the same error the CI guard
suite deliberately reads), and the roadmap's Stages 1–5 add no `oracle-oci`, Neon or DNS provider dependency to any
workflow file. `grep -rniE "oci|neon|vercel" .github/workflows/` returns nothing that authenticates to a cloud;
the only `oci` occurrence is the `infra/**` **job that runs OpenTofu validation with no credentials by design**.

### ADR-020 — One shared schema, `school_id` on every row, and a composite foreign key that makes cross-tenant rows impossible

**Status:** **Accepted**. **Date:** 2026-11-01. **Implements:** the mandate's *"Enforce tenant isolation at the server
and database-query level"*; closes the design half of `TEN-01…07`.

**Context.** GMS has 25 entities with a `school_id` link, **10 with none at all** (`Attendance`, `ExamSubject`,
`FeePayment`, `Parent`, `Result`, `StudentExam`, `StudentFee`, `TeacherAssignment`, `Timetable`, `TimetableSlot`) and
6 repositories with no school-scoped method. `TEN-06` records that Hibernate's `@Where`/`@Filter` appear nowhere and
there is no RLS. `TEN-01` records that a `schoolId` argument is accepted by some services and then ignored, with a
literal TODO. `TEN-07` records that `SecurityUtil` returns `null` off-request and callers pass that null as a filter
value. Whatever is decided next has to be a mechanism, because six findings describe six different ways the current
manual discipline fails.

**Decision.** Three layers, in [ARCHITECTURE.md §4](../ARCHITECTURE.md): the `schoolId` claim parsed **once** per
request into a request-scoped context; a tenant argument on every repository method that reads tenant data; and the
tenant key **materialised on child rows**, constrained by a composite foreign key to the parent's
`(id, school_id)` candidate key, so a row that joins school 7's student to school 9's attendance cannot be inserted
by any code path — including a path nobody has read yet.

**Alternatives rejected.**
- *Schema-per-tenant* — one schema per school means migrations multiply with customers, the connection pool has to be
  switched per request, and `Flyway` must run N times per deploy. For a solo operator at zero cost with a handful of
  pilot schools this is an operational system, not an application.
- *Database-per-tenant* — the same, plus one Neon endpoint per school, which is a paid resource and a rule breach.
- *PostgreSQL row-level security as the first line* — technically the strongest and the right Stage-7 evaluation
  (`TEN-06` lists it as debt item 14). Rejected **for now**, for two concrete reasons: RLS policies are invisible to
  JPA's plan and interact badly with an `ddl-auto`-managed schema, and a policy would be authored against a schema
  that has no migrations yet (`DB-01`). The composite-FK design is what keeps RLS *available* later: the column a
  policy needs will already exist and already be `NOT NULL`.
- *`@Filter`/`@Where` discriminator only* — one annotation on 36 entities, and it silently no-ops outside a session
  with the filter enabled, which is precisely the class of "the guard is present and inert" defect `SEC-16` and
  `SEC-17` had to be written about. Kept as an open question (`ARCHITECTURE.md §10 A-1`), not as the mechanism.
- *Six hand-written ownership checks in the affected controllers* — the option `SEC-05`'s remediation was originally
  drafted with, and `TECHNICAL_DEBT §2.1` already explains why it was refused: patching six controllers by hand
  creates six copies of a rule the mandate asks to express once.

**Consequences.** Every child table gains a column, a backfill and a constraint, so Stage 2's migration order is
load-bearing (`ARCHITECTURE.md §9`: add column → backfill → assert zero-null → constraint → index, never the reverse).
It also puts the application permanently in tension with `ddl-auto`: Hibernate will not generate a composite
foreign key, which is one more reason for `ADR-022`. And it means the ten tenant-less tables stop being an exception
list that every service has to remember — `TEN-04`'s complaint was exactly that "filter by school" there is a
multi-join the service must remember, and none of them did.

**Cost.** ₹0 — a schema decision, no new service.
**Verification.** Three things, all runnable: the cross-tenant suite in Stage 3's `DoD-3` (an *attempt* per protected
resource, asserting 403/404); a Stage-2 database test that tries to insert the mismatched composite key and asserts a
constraint violation — **the layer that must be proven is the one nobody wrote code for**; and `grep -c "school_id"
model/entity/*.java` rising until no entity is missing it except the deliberate global set (`parents`).

*One of the three has now happened, partially and at the schema level only.* `ARCHITECTURE.md §5.2` records an insert
of an `attendances` row pointing at a school-7 student while carrying `school_id = 9` against a database built from
this design: PostgreSQL rejected it with `violates foreign key constraint "fk_attendance_student_same_school"`, and
the same row with the correct tenant was accepted. That is the first time this ADR's central claim — that a
cross-tenant row becomes *impossible* rather than unlikely — has been observed rather than argued. It is not `DoD-3`:
there was no application in the loop, no HTTP request, no JWT, and the schema was derived mechanically from the
entities rather than authored as a Flyway baseline. The two remaining proofs are exactly those absences.

**Implementation status (2026-11-01, Stage 3 `C-2`, `C-3` and `C-7`).** Layer 1 — the claim — is **shipped**: `JwtAuthenticationFilter`
parses the token once per request and populates `security/TenantContext`, `util/SecurityUtil` (325 call sites in 41
files, each re-parsing *and re-verifying* the signature) is deleted rather than wrapped, and `TenantParameterArchTest`
fails the build if any service method accepts the tenant as an argument — the "a caller may forget" path that
`TEN-01`/`TEN-07` describe is closed by an architecture test rather than by discipline. A request that names another
school's row is refused with **403** (`TenantAccessDeniedException`), so the read path and the write path now answer
alike.

Layer 2 — the query discriminator — is **shipped for every read this repository declares**, on the same day and the
same branch, by `C-3`. The state at `C-2`'s close was ten declared reads that queried across schools —
`resultRepository.findByStudentExam_Student_Id`, `studentExamRepository.findByStudentId`,
`feePaymentRepository.findByStudentFee_Student_Id`, `invoiceRepository.findByStudentId`, `receiptRepository.findByStudentId`,
`attendanceRepository.findByStudent_IdAndDateBetween`, `timetableRepository.findByClassroom_Id`,
`teacherAssignmentRepository.findByTeacher_Id`, `parentRepository.findByStudentsId`, `userRepository.findByRole` — and
that list stays here because it was measured against the branch rather than guessed. None of those ten exists now: each
was rewritten to name the school or deleted where a scoped form already meant the same question. `RepositoryTenantScopeArchTest` fails the build when a
declared read names no school, and it measures the repositories from their compiled interfaces rather than from a list
somebody maintains: at the commit immediately before `C-3`, **170 declared reads, 115 carrying the tenant, 55 carrying
none, 37 indefensible** (test red); on this branch, **168 / 150 / 18 / 0** (green), re-measured after `C-7` at
**169 / 151 / 18 / 0** — one read added and one more carrying the tenant, because closing layer 3 required
`TimetableSlotRepository.findByIdAndSchool_Id`. Each of the 18 is allow-listed with
the key that makes it global by design. Two limits belong to the claim rather than to a footnote: it reads
*declarations*, so the `findById` / `findAll` / `findAllById` / `delete` that `JpaRepository` hands every repository are
invisible to it and are `C-4`; and a predicate that reaches the tenant by travelling a join is only as good as the
column it travels, which is the next paragraph.

Layer 3 — the materialised key with the composite foreign key — **shipped on the same branch and the same day, by
`C-7`**. The measurement that justified the card is quoted rather than deleted, because it is the reason this layer is
described the way it is: all nine `V2` `school_id` columns existed and the `timetables` row the running application
created through its own
service had `school_id = NULL`, because no Java entity mapped the column. PostgreSQL checks a composite foreign key
with MATCH SIMPLE, so a NULL in one column satisfied `V5`'s same-school constraint instead of violating it. What is
now true instead: the nine entities (`Attendance`, `ExamSubject`, `FeePayment`, `Result`, `StudentExam`, `StudentFee`,
`TeacherAssignment`, `Timetable`, `TimetableSlot`) map the column as a required `@ManyToOne` and 11 create paths in 8
services fill it from `TenantContext.schoolId()` — **from the claim, never copied from the parent row**, because a
copied tenant compares a value against the row it was copied from and can never disagree, which would make this layer
the expensive half of a tautology. `V6` holds all ten deferred `SET NOT NULL` flips behind ten backfill-and-assert
pairs, four `(id, school_id)` candidate keys and seven more same-school foreign keys join them; `parent_student`, a
`@ManyToMany` join table with no entity to map, is materialised by `trg_parent_student_tenant_key`, which derives the
tenant from the student and **raises** on a hand-written row that disagrees rather than silently overwriting it.
Measured: `EntityTenantColumnArchTest` fails the build for a tenant table whose entity does not map the column
(`C-7: 35 tables carry an integer school_id, 36 entity classes cover 34 of them, 1 have no entity and a reason`);
`verify-c7-tenant-key.sh` 66/66, including a cross-tenant `POST /timetables` refused at **403** by
`fk_timetable_classroom_same_school` and `rows with a NULL tenant, in any tenant table, platform-wide: 0`.

Layers 1 and 2 protect HTTP traffic today; layer 3 is what makes a defect in layers 1 or 2 survivable, which is the
point of having three. It does not make a wrong-tenant *read* impossible — `findById` is still inherited and still
loads before the check, which is `C-4`, and level 3 constrains what may be written about a row rather than who may
fetch it.

`ARCHITECTURE.md §4` and the roadmap's `C-3` and `C-7` cards carry the measurements and the deliberate-failure controls;
the verification of layer 1 is in [BASELINE_STATUS.md §16](../audit/BASELINE_STATUS.md) and of layer 3 in
[§17](../audit/BASELINE_STATUS.md). ADR-020 is therefore **all three layers shipped** on this branch — and still not
closed, because `C-4` owns the inherited reads, `C-5` owns deny-by-default at the endpoint, and RLS stays a Stage-7
evaluation. The roadmap says so rather than marking the ADR done.

### ADR-021 — A modular monolith enforced by architecture tests, not by moving 282 files

**Status:** **Accepted**. **Date:** 2026-11-01. **Implements:** the mandate's *"establish the modular monolith"* and
*"define module boundaries, dependencies, API contracts, data ownership"*.

**Context.** The backend is package-by-layer: 282 files in 11 packages (`controller` 36, `model/entity` 37,
`repository` 35, `service` 40 + `impl` 34, `model/request` 41, `model/response` 36, `security` 4, `config` 5,
`util` 6, `enums` 3, `exception` 3). The frontend is already package-by-feature — 18 directories under
`gms_frontend/src/features/` that map one-to-one onto the modules in [ARCHITECTURE.md §2](../ARCHITECTURE.md). So
"modular monolith" could mean two very different pieces of work here.

> **Re-measured 2026-11-01.** Stage 3 `C-1` deleted `AbstractCRUDController` and `AbstractCRUDService`, so the count
> is now **280** files in 11 packages (`controller` 35, `service` 39 + `impl` 34; the others unchanged). The decision
> this ADR took is unaffected — the point was and is that the boundary is enforced by tests rather than by moving
> files, and two fewer files to move does not change that.

**Decision.** Keep the layout; enforce the boundary with **ArchUnit** rules in the test scope, written as the six
assertions in `ARCHITECTURE.md §7` (one writer per table, no cross-module repository access, no entity in a response,
no tenant-less read, no `ResponseEntity` inside a service, every handler authorised). Violations are grandfathered by
a frozen allow-list so the build fails on a **new** violation and not on history; the list shrinks stage by stage and
a PR that lengthens it is refused.

**Alternatives rejected.**
- *Re-package into `com.gms.<module>.{api,application,domain,persistence}` now* — 282 file moves, every `file:line`
  citation in six registers going stale simultaneously, two months of diffs that are all renames, and the module map
  gained is exactly the one already written in `ARCHITECTURE.md §2`. Rejected on cost with no correctness benefit;
  retained as a mechanical post-DoD step if the module count grows past ~20.
- *Separate services per module* — the mandate says monolith; `ADR-007` already rejected Kubernetes, and a solo
  operator at ₹0 cannot run twelve deployables, their networks, their CI and their contracts.
- *Maven/Gradle multi-module enforcement* — real compile-time boundaries, but it is a build-system rewrite touching
  `pom.xml`, CI, Dockerfiles and IDE setup for all 282 files, i.e. the same rejection as re-packaging with extra
  risk. If a later stage wants hard encapsulation, this is the upgrade path from ArchUnit, not a replacement of it.
- *"Boundaries in the documentation only"* — this repository's own `ADR-018` lesson, stated there for infrastructure
  and true for architecture: *"the rules stop being enforceable-by-habit the moment the file is bigger than the
  person editing it."* Prose rules were exactly how `SEC-16`'s thirteen inert guards survived.

**Consequences.** ArchUnit is a test-scope dependency from Maven Central, so the boundary rules run inside the
`mvn verify` that CI already executes — no new job, no new cost, no new language. The allow-list means the rules are
introduced with debt named rather than debt hidden, and its count is the honest measure of `D-1…D-4` progress
today: 90 entity-returning endpoints, 65 of 74 service files importing `ResponseEntity`, 6 unscoped repositories,
3 unguarded inherited reads.

**Cost.** ₹0.
**Verification.** The rules must fail on a violation that is *deliberately introduced*, the same control used for the
HCL guards (`ADR-018`, PR #19) and for the CI path filter (`BASELINE_STATUS.md §11.1`): add one cross-module
repository reference on a scratch branch, show ArchUnit red on exactly that assertion, restore. A rule that has never
been observed to fail is documentation.

### ADR-022 — Flyway owns the schema; `ddl-auto` is `validate` in every profile, including dev

**Status:** **Accepted**. **Date:** 2026-11-01. **Amends:** `ADR-006`. **Closes:** `DB-01`, and the `DB-05`
convention split as a *consequence* of the same rule.

**Context.** Measured today: `application.properties:33` is `${JPA_DDL_AUTO:validate}`, `application-dev.properties:20`
is `${JPA_DDL_AUTO:update}`, the two Flyway properties at `application.properties:40-41` point at a
`classpath:db/migration` that **does not exist**, `flyway-core` is **not in `pom.xml`**, and there are **0 `.sql`
files** in the repository. So the sentence that has been repeated in three documents — "Flyway is enabled" — is true
about a property and false about a system. Meanwhile `ADR-006`'s amendment proposed generating V1 from the JPA model,
which is the same thing Hibernate does badly on command.

**Decision.** The schema is **authored as versioned SQL in `src/main/resources/db/migration`**, in the order
`ARCHITECTURE.md §9` specifies, and it is the **only** thing that changes a database. `spring.jpa.hibernate.ddl-auto`
is `validate` in `main`, `dev`, `test` and `prod` — with no `${…}` default that can fall back to `update` — and the
build fails on `grep -rn ddl-auto gms_backend/src/main/resources/ | grep -v validate`. Flyway runs on application
start **only** in `dev`/`test`; in `prod` it is a deployment step executed before the new version receives traffic
(`ADR-006`'s expand-migrate-verify-contract discipline is unchanged).

**Alternatives rejected.**
- *`ddl-auto=update` in dev, migrations everywhere else* — the status quo, and the reason three registers describe a
  schema that exists in nobody's history. It makes the dev database the authority and CI a place where a different
  schema appears; it also silently accepts the entity/classification changes that Stage 2 is trying to *review*.
  Rejected: two schemas, one of them unreviewed.
- *`ddl-auto=create-drop` in tests* — fast and dishonest. It proves nothing about `DoD-5` (migrations from an empty
  database) and hides exactly the constraint failures `ADR-020`'s composite keys are for. Rejected.
- *Generate V1 from `hibernate_schemaexport` and hand-edit it* — the tempting middle path. Rejected because the
  generated DDL omits what the design needs: composite foreign keys, `CHECK` constraints, partial unique indexes, and
  every index in `ARCHITECTURE.md §5`. It also produces a baseline whose content is derived from the 4 entities that
  duplicate `BaseAuditEntity` inconsistently (`DB-05`, re-measured: `Exam`, `Parent`, `SchoolCalendar`, `Section`),
  freezing the split instead of resolving it.
- *Liquibase* — changesets and XML/YAML for the same job; the mandate names Flyway, `ADR-006` chose Flyway, and the
  properties in `application.properties` already assume it. Rejected for no upside.
- *JPA `@Index` annotations instead of migrations* — Hibernate can create them under `update`, which is the profile
  this ADR is deleting. Indexes are deployment artefacts, reviewed like SQL. Rejected.

**Consequences.** Stage 2 must author 36 `CREATE TABLE`s by hand (or from a one-off export of a *disposable*
`update`-generated database, reviewed line by line before it becomes V1 — the ADR's compromise, explicitly labelled)
and thereafter every entity change arrives with a migration file, reviewed in the same PR. Dev boot gets slower by one
migration run. `DB-05`'s two audit-column conventions become a single decision that has to be made *in SQL*, which is
where it can be seen. And the mandate's line *"Never use Hibernate schema update in production"* becomes weaker than
what is now enforced: Hibernate never writes schema anywhere, so `prod` cannot be the place where the rule is finally
obeyed.

**Cost.** ₹0 — a Maven Central dependency and SQL files.
**Verification.** `DoD-5`, run twice: `flyway migrate` from an empty PostgreSQL to head, `pg_dump --schema-only` of
each run, diff **empty**; a Testcontainers job in CI that repeats it on the runner; the `ddl-auto` grep above as a
build step; and one negative control — a migration that must fail, by seeding a duplicate into a table about to
receive a `UNIQUE` constraint (`ARCHITECTURE.md §9` step 3). A constraint migration that cannot fail has not been
tested.

**Verification, as of Stage 2 (branch `feat/stage2-flyway-baseline`, not yet merged).** Of the four checks above:

| Check | Observed |
|---|---|
| migrate-to-head twice, dump diff empty | **Yes.** `scripts/verify-schema.sh` §1 builds two throwaway databases from `V1`–`V5` and diffs `pg_dump --schema-only` of each → 1503 lines, **identical**. Repeating it is `bash scripts/verify-schema.sh`, not a description of a run. |
| negative control | **Yes**, after one invalid attempt. The first control seeded a duplicate `invoices.invoice_number`, which `V1`'s own global unique rejects before `V3` ever runs — a control that passes for the wrong reason. The working control (`§4`) seeds a duplicate `students.student_id` into a database migrated only to `V2`, then runs `V3` and asserts it fails: `could not create unique index "uq_students_school_student_id" … is duplicated`. |
| `ddl-auto` grep as a build step | **Partly.** The grep is true on the branch — `application.properties`, `-dev` and `-prod` now hold the literal `validate`, and `${JPA_DDL_AUTO:…}` is deleted from all three and from `.env.example`, so no environment variable can reintroduce `update`. It is **not a build step**: no CI job runs it, and `master` still carries the `${JPA_DDL_AUTO:update}` default. |
| Testcontainers job in CI | **No.** Deferred with the rest of the automated-test work (`ENGINEERING_ROADMAP.md` Stage 5, criteria 4–5). |

`§5` and `§6` of the same script add the two checks the ADR did not name, because a constraint that only ever gets
tested in one direction is a guess: DB-07's relaxation is measured as *accepted* across two schools and *refused*
inside one, and `students.email`'s partial unique is measured as accepting two blank emails, a `NULL` and the same
address in a different school, then refusing a repeat inside one school.

Two things this ADR could not have predicted:

1. **`prod` still migrates on boot.** `application-prod.properties:15` — `spring.flyway.enabled=true` — was left
   untouched, so the half of the decision that says *"in `prod` it is a deployment step executed before the new version
   receives traffic"* is **not implemented**; `ddl-auto=validate` is the half that is. The gap is `C-9`.
2. **`flyway-core` alone does not speak PostgreSQL.** Flyway 10 — which Spring Boot 3.3.3 manages, at 10.10.0 — moved
   the dialect into `flyway-database-postgresql`; `flyway-core` 10.10.0 contains no PostgreSQL classes. Boot fails with
   *"Unsupported Database: PostgreSQL 16.15"* unless both artifacts are declared. Neither this ADR nor
   `ARCHITECTURE.md §9` said so, and a pom that "has Flyway" is precisely the property-true, system-false claim the ADR
   was written to end.

**The boot half, and the trap in it.** `DoD-5`'s other clause — the application starts against a schema the migrations
own — was observed on a fresh database with the packaged jar: `Successfully applied 5 migrations … now at version v5`,
`Started GmsApplication in 6.763 seconds`, `/actuator/health/readiness` → `{"status":"UP"}`. The trap: an earlier
attempt run against a jar built *before* the pom edit logged **no Flyway line at all** and died at
`Schema-validation: missing table [academic_years]`. A stale artifact does not skip migrations loudly; it skips them
silently and then blames the schema. `unzip -l target/*.jar | grep -c flyway` is the check, and the rebuild is part of
the gate, not a preamble to it.

`ADR-020` gets its evidence here rather than in prose: `V5`'s composite keys are exercised by an insert that puts a
`school_id = 2` attendance row on a `school_id = 1` student, and the server refuses it — `violates foreign key
constraint "fk_attendance_student_same_school"` (`§3`). That is the ADR's entire argument, demonstrated by a database
instead of asserted by a document.

### ADR-023 — Delete `AbstractCRUDController` and `AbstractCRUDService`

**Status:** **Accepted and implemented** on `feat/stage2-flyway-baseline`, 2026-11-01. **Closes:** `SEC-05`.
**Implements:** the mandate's *"Avoid unrestricted CRUD inheritance"*.

**Context.** Two base classes that auto-register endpoints: `AbstractCRUDController.java:30,41,52` maps
`GET /{id}`, `GET` and `DELETE /{id}` with **no** `@PreAuthorize` and **no** tenant predicate, and
`AbstractCRUDService.java:51-59` implements them with bare `repository.findById` / `findAll` / `deleteById`. Six
controllers extend it, and the live unguarded surface re-measured on 2026-11-01 is `GET /{id}` on 3 of them
(`ParentController`, `StudentController`, `EmployeeController`) and `DELETE /{id}` on **all six** — `SEC-05`'s text
still says "seven … including `User`", and `UserController.java:25` turns out not to extend the base at all, so the
finding is corrected in place. The evidence that the inheritance is load-bearing in the wrong direction is a comment:
`EmployeeController.java:65-67` records that a hand-written `getById` was **deleted** because the base class already
supplied one.

**Decision.** Delete both classes and their generics. Each controller declares its own operations — annotated,
tenant-scoped, DTO-returning, paged where the collection is unbounded. Where two controllers genuinely need the same
behaviour, the shared thing becomes a **service method**, not a mapping.

**Alternatives rejected.**
- *Keep the base class, add a guard to it* — attractive, and wrong in two ways. It cannot work: the base has no
  concept of the tenant, because its generic is `T` with no `school_id` contract, so "a guard in the base" means
  inventing a tenant interface across 36 entities — a larger change than the one being avoided. And it keeps the
  property the mandate rejects: a controller can acquire a public route by *extending a class*, so the endpoint
  inventory of a security review is the union of six inheritance graphs rather than one grep of annotations.
- *Abstract away the mappings only, keep the service base* — half the deletion, and the half that leaves `findAll()`
  with no tenant predicate reachable from every subclass.
- *Deny-by-default in `SecurityConfig` instead* — the filter chain would then be the authorisation model, which
  `SEC-06` (matcher order shadowing the method-level grants) and `SEC-16` (inert vocabulary) both show this codebase
  cannot operate safely. Method security stays the mechanism; a build-time scan that fails on an unannotated handler
  is the enforcement (`ARCHITECTURE.md §7`).
- *Leave it and fix by exception* — rejected because the exception list is what people are asked to remember, and
  `TEN-01`/`TEN-04`/`TEN-07` are four documented ways that remembering already failed.

**Consequences.** Six controllers gain explicit `getById`/`delete` decisions, and the app gains its first honest
endpoint count: after deletion, the routes that exist are the routes someone wrote, which makes every later surface
review (including the OpenAPI work in Stage 4) a read rather than a derivation. The 3 inherited mappings disappear and
4 explicit ones replace them, so the mapping count moves from **232 to 233** — and the number that matters is that
**0 of them arrive by inheritance**. The unbounded-`List` count falls from **90 to 89**, because the base class's
unguarded `getAll` was one of them. The guard count rises from **206 to 210**: the four new operations carry their own
`@PreAuthorize`, and no other controller lost one.

> **Correction to the first draft of this ADR.** The first draft said the frontend "has no call site for `DELETE` on
> these resources". That was wrong: `gms_frontend/src/store/api/apiSlice.js:258` calls `DELETE /classrooms/{id}` and
> `:290` calls `DELETE /subjects/{id}`, and both resources had inherited an unguarded delete. The two call sites were
> therefore **live frontend behaviour** and the inherited `DELETE /{id}` was reachable from the application's own
> client — which is why the two deletes are re-declared explicitly rather than dropped. One further call site is
> outright wrong and belongs to Stage 4's frontend-contract repair: `apiSlice.js:249` sends `PUT /classrooms/{id}`
> while the controller declares `PUT /classrooms/update` with the id in the body, so the classroom edit form calls a
> route that does not exist. Measured live on 2026-11-01: `PUT /api/v1/classrooms/1` answers **405** while
> `PUT /api/v1/classrooms/update` answers **200**. The subject edit is fine — `apiSlice.js:281` sends
> `PUT /subjects/update/{id}` and the controller declares `PUT /subjects/update/{id}`; measured **200**.

**Implementation (2026-11-01).** Both classes are deleted. 25 service implementations dropped `extends
AbstractCRUDService<X, Integer>` and their `super(repository)` call; 0 of the 36 controllers now extend anything.
`GET /{id}` is declared explicitly, and tenant-scoped, on `Student`, `Employee` and `School`; it is **not** declared
on `Parent` (the tenant lives on `parent_student`, not on `parents`) and not on `User` (it already had one, and it is
now scoped). `DELETE /{id}` is declared, tenant-scoped and `ADMIN`/`SUPERADMIN`-only on `Subject` and `Classroom`,
where the frontend calls it. Three tenancy holes closed along the way, each found by re-reading the code the
inheritance had been hiding: `ParentServiceImpl.findBySchool` returned **every platform's parents**; `UserService`'s
`getById`/`deleteById` pair had no tenant predicate, so any administrator could read or delete any other school's
account; and `SchoolService.getById` did not exist, so the platform owner could list schools but not read one.

**Two 500s found by running it, not by reading it.** The live run against a real PostgreSQL surfaced two
client-error paths that the catch-all `@ExceptionHandler(Exception.class)` was answering as **500**:

| Request | Was | Now | Cause |
|---|---|---|---|
| `DELETE /api/v1/students/1` | 500 | **405** | `HttpRequestMethodNotSupportedException` — the path still answers `GET`, so the method mismatch reached the catch-all |
| `DELETE /api/v1/classrooms/1` (classroom with a student) | 500 | **409** | `DataIntegrityViolationException` — `students.classroom_id` still references the row |
| `GET /api/v1/students/1` as a `TEACHER` | 500 | **403** | `AuthorizationDeniedException extends AccessDeniedException` — every method-level refusal in the app answered 500 |

The third row is the more serious one: it was not specific to this card, it was the answer to *every* refusal the
method layer had ever produced. `GlobalExceptionHandler` now maps `AccessDeniedException` → 403,
`HttpRequestMethodNotSupportedException` → 405 and `DataIntegrityViolationException` → 409, each with the same
`{traceId, path, error, message}` envelope and no disclosure. The wider client-error mapping (415, unreadable body,
missing parameter, type mismatch) is still outstanding and belongs to Stage 4's error-contract card (`BKL-01`,
`BKL-07`).

**Cost.** ₹0.
**Verification.**
- `grep -rn "extends AbstractCRUDController" gms_backend/src/main/java/` → **no output**. Same for
  `AbstractCRUDService` and for `extends AbstractCRUD` in the controller package.
- `EndpointInheritanceTest` — **33 tests**, all green: no controller inherits its endpoints, every declared operation
  carries its own `@PreAuthorize`, the inherited read and delete routes are absent from the mapping table, and each
  of the six extendees answers 401 without a token and 403 without a role claim.
- **Deliberate-failure control**, run as this repository has run twice before (`ADR-018`, PR #19): restoring the
  inherited `DELETE /{id}` on `StudentController` and removing its `@PreAuthorize` made the suite go red on exactly the
  two assertions that cover it, and the file was restored afterwards.
- **Live cross-tenant run** — `scripts/verify-c1-tenancy.sh`, **29 checks, 0 failures**, against the packaged jar on
  PostgreSQL 16.15 with the schema built by Flyway from an empty database. It creates two schools and their
  administrators, proves 403 across the tenant boundary and 204 inside it, and proves the four deleted routes are
  gone (405, or 403 where `anyRequest().denyAll()` answers first).
- Full suite: **79 tests green** (42 pre-existing + 33 new + 4 exception-mapping tests). No check was downgraded.

---

## Summary

| ADR | Decision | Status | Needs the owner |
|---|---|---|---|
| 001 | Keep existing EC2 + Compose | **Superseded by 015** (host permanently closed) | — |
| 002 | Three compose files | Accepted | — |
| 003 | Caddy + real TLS | Accepted, pending DNS access | **where `bytencia.com` DNS is hosted** |
| 004 | GHCR, SHA tags, digest pinning | Accepted | `packages: write` allowance |
| 005 | SSH kept, deployment versioned | **Superseded by 017** | — |
| 006 | Flyway, forward-only, expand-migrate-verify-contract | Accepted, **fresh baseline** (no prod export needed) | — |
| 007 | No Kubernetes | Accepted | — |
| 008 | Staging as a second project | Proposed (capacity declared, unverified) | masked fixture |
| 009 | Encrypted dumps, tested restores | Accepted, re-targeted at Neon | **off-host destination approval** |
| 010 | Logs + health now, Prometheus later | Accepted | — |
| 011 | Artefacts on master only | Accepted | — |
| 012 | `Infra-M` vs security `M` naming | Accepted | — |
| 013 | Vercel SPA, API-only origin | Proposed, origin re-targeted; EC2 rewrite deleted | domain decision |
| 014 | Declared interruption, no zero-downtime claim | Accepted | — |
| 015 | OCI Always Free ARM64 VM + Compose | Accepted as target, **not provisioned**; the stack is on `master` in `infra/oci`, validates, and its guards run in CI — but nothing has been applied. **`ADR-019` moves provisioning to Stage 6** | at Stage 6: **`oci setup config` (interactive); free-eligibility + public-IP cost confirmation** |
| 016 | Fresh owner-owned Neon; old Neon retired | Accepted, **not created**; creation deferred to Stage 6 by `ADR-019`. The ban on the friend-owned database is unchanged and absolute | at Stage 6: **account creation, email verification** |
| 017 | OCI deployment transport | Proposed, deferred until the host exists — which now has a date: Stage 6 | — |
| 018 | OpenTofu for OCI infra; §9 rules encoded as HCL guards | **Accepted** | — |
| 019 | Application first, cloud at Stage 6; Stages 1–5 need zero cloud resources | **Accepted**; supersedes the *sequencing* of 015/016, never their targets | nothing until Stage 6 |
| 020 | Shared schema, `school_id` on every row, composite FK to the parent's `(id, school_id)` | **Accepted**; layer 1 (the claim) **shipped 2026-11-01** with Stage 3 `C-2`, layer 2 (the query discriminator) **shipped the same day** for every declared read with `C-3`, layer 3 (the materialised key) **shipped the same day** with `C-7` — the nine entities map the column, ten `SET NOT NULL` land as `V6` behind backfill-and-assert, `parent_student` is held by trigger, and 18 same-school foreign keys now bind the application's own writes; `C-8`'s join tables inherit `V7` — RLS deferred to a Stage-7 evaluation, deliberately kept addable. Not closed: inherited reads still load before they check (`C-4`), and the endpoint is not deny-by-default (`C-5`) | — |
| 021 | Modular monolith enforced by ArchUnit over the existing package-by-layer layout | **Accepted**; re-packaging retained as a post-DoD mechanical step | — |
| 022 | Flyway owns the schema; `ddl-auto=validate` in **every** profile | **Accepted**, amends 006 | — |
| 023 | Delete `AbstractCRUDController` / `AbstractCRUDService` | **Accepted and implemented 2026-11-01**; closes `SEC-05` — 79 tests green, live tenancy run 29/29 | — |
