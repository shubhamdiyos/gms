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

---

## Summary

| ADR | Decision | Status | Needs the owner |
|---|---|---|---|
| 001 | Keep existing EC2 + Compose | Accepted, contingent | host access / survey |
| 002 | Three compose files | Accepted | — |
| 003 | Caddy + real TLS | Proposed | **domain purchase** |
| 004 | GHCR, SHA tags, digest pinning | Accepted | `packages: write` allowance |
| 005 | SSH kept, deployment versioned | Accepted | `EC2_SSH_KNOWN_HOSTS`, reviewer setup |
| 006 | Flyway, forward-only, expand-migrate-verify-contract | Proposed | **authorised prod schema export** |
| 007 | No Kubernetes | Accepted | — |
| 008 | Staging as a second project | Proposed | capacity measurement, masked fixture |
| 009 | Encrypted dumps, tested restores | Accepted / destination blocked | **off-host destination approval** |
| 010 | Logs + health now, Prometheus later | Accepted | — |
| 011 | Artefacts on master only | Accepted | — |
| 012 | `Infra-M` vs security `M` naming | Accepted | — |
| 013 | Vercel SPA, API-only origin | Proposed | domain decision |
| 014 | Declared interruption, no zero-downtime claim | Accepted | — |
