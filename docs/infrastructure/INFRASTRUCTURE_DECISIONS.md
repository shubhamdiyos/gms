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
than by care, and every row below was executed on this machine, not assumed:

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
loop in an interactive zsh), not in OpenTofu, and it is not resolved. The exit-code runs are the ones cited, and
a claim that flips when the test is written differently is a reason to write the test down, not to pick the
convenient answer.

State is local and gitignored. That is honest for one operator on one laptop and unsafe the moment a second
exists: two working copies against one local `terraform.tfstate` is how production gets two VCNs. Remote state
is a migration to plan, not a backend block to add on hope.

**Cost.** ₹0 — OpenTofu and the provider are downloads, and nothing in this decision contacts OCI.
**Verification.** `tofu version` = v1.13.2 (archive SHA-256 checked against the published `SHA256SUMS`),
`tofu init` resolving `registry.opentofu.org/oracle/oci` 9.11.0, `tofu validate` →
`Success! The configuration is valid`, and the guard table above reproduced by running the plans.
**Not verified, and not claimable yet: any plan or apply against the real tenancy**, because
`oci setup config` is interactive-only and the owner has not run it. See ADR-015.

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
| 015 | OCI Always Free ARM64 VM + Compose | Accepted as target, **not provisioned**; stack now exists in `infra/oci` and validates, but nothing has been applied | **`oci setup config` (interactive); free-eligibility + public-IP cost confirmation** |
| 016 | Fresh owner-owned Neon; old Neon retired | Accepted, **not created** | **account creation, email verification** |
| 017 | OCI deployment transport | Proposed, deferred until the host exists | — |
| 018 | OpenTofu for OCI infra; §9 rules encoded as HCL guards | **Accepted** | — |
