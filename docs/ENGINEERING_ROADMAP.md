# GMS Engineering Roadmap — the one sequence

**Status:** Active. **Adopted:** 2026-11-01, under the execution mandate ("GMS — Engineering Execution Mandate"),
which replaced issue-by-issue work with a single roadmap and a stated definition of done.
**Supersedes for sequencing:** the milestone tables in
[INFRASTRUCTURE_AUDIT.md §9](infrastructure/INFRASTRUCTURE_AUDIT.md), the ranked backlog ordering in
[TECHNICAL_DEBT.md §2](audit/TECHNICAL_DEBT.md), and the phase numbering of the third mandate. Those documents
survive; their *order* does not lead any more. See §3.

---

## 1. What this document owns

One thing: **the order work happens in, and what "done" means at each step.**

| Question | Owner |
|---|---|
| What is broken, what is risky, what is ugly | [audit/REPOSITORY_AUDIT.md](audit/REPOSITORY_AUDIT.md) · [audit/SECURITY_AUDIT.md](audit/SECURITY_AUDIT.md) · [infrastructure/INFRASTRUCTURE_AUDIT.md](infrastructure/INFRASTRUCTURE_AUDIT.md) |
| What was measured, on which machine, with what command output | [audit/BASELINE_STATUS.md](audit/BASELINE_STATUS.md) |
| Whether the system is ready, area by area | [infrastructure/PRODUCTION_READINESS_MATRIX.md](infrastructure/PRODUCTION_READINESS_MATRIX.md) |
| Why a technology or shape was chosen, and what was rejected | [infrastructure/INFRASTRUCTURE_DECISIONS.md](infrastructure/INFRASTRUCTURE_DECISIONS.md) |
| What the product is for, who it serves, what is in scope | [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md) |
| Module boundaries, data ownership, API contracts, indexing design | [ARCHITECTURE.md](ARCHITECTURE.md) |
| **Sequence, stage gates, acceptance criteria, definition of done** | **this file** |

**The non-duplication rule for this file.** A stage names work by **finding id** (`SEC-*`, `TEN-*`, `DB-*`,
`BKL-*`, `OPS-*`, `ACD-*`, `FIN-*`, `FE-*`, `INF-*`, `ADR-*`, `BR-*`) and never restates the finding. A stage
states its acceptance criteria as **a command and the result that command must produce**, because a criterion
with no measurement attached is a wish. When a stage closes, its evidence goes to `BASELINE_STATUS.md` (or a new
evidence section named there) and the verdict moves in the readiness matrix — not here. This file then changes one
word: `Open` → `Closed`.

## 2. Definition of done — the six clauses

The mandate's sentence is: *"The foundation is complete only when the MVP works end-to-end, the agreed performance
tests pass, tenant isolation is verified, CI/CD works, database migrations are reproducible and operational recovery
has been tested."* Decomposed so each clause can be argued with:

| # | Clause | The test that decides it | Recorded in |
|---|---|---|---|
| DoD-1 | The MVP works end-to-end | A scripted journey — superadmin login → create school → provision admin → admin login → create students at 50k scale → paginated search → dashboard counts → teacher attendance → parent reads one child's result — executed against a database built only by migrations, with the HTTP results and row counts captured | `BASELINE_STATUS.md`, new § |
| DoD-2 | The agreed performance tests pass | The published budget in [PRODUCT_REQUIREMENTS.md §9](PRODUCT_REQUIREMENTS.md) met on ≥50,000 records per school, from `EXPLAIN (ANALYZE, BUFFERS)` and measured endpoint latency, on PostgreSQL 17 (local Docker *and* Testcontainers, which must agree) | evidence section named by DoD-1 |
| DoD-3 | Tenant isolation is verified | Automated negative tests that **attempt** cross-tenant reads/writes on every protected resource and assert 403/404, plus one deliberate-failure control: a test that passes when the guard is removed and fails when it is in place | same |
| DoD-4 | CI/CD works | Every check required in branch protection with `enforce_admins`; one PR that breaks a test proven unmergeable; the pipeline's own yaml validated by a gate that fires on its own edits | `PRODUCTION_READINESS_MATRIX.md` §2.2/2.3 |
| DoD-5 | Migrations are reproducible | `flyway migrate` from an **empty** PostgreSQL database to head, twice, producing byte-identical schema (`pg_dump --schema-only` diff empty); `ddl-auto=validate` and never `update` in any profile | same |
| DoD-6 | Operational recovery is tested | A backup taken, restored into an isolated container, row counts and three sampled aggregates compared; the restore executed by the runbook text, not by an ad-hoc command | same |

**Vocabulary, used everywhere in this roadmap** (mandate: *"Distinguish implemented, tested, verified and deployed"*):
`Implemented` = the code exists and compiles. `Tested` = an automated check exercises it. `Verified` = the check ran
and its output is recorded with the environment that produced it. `Deployed` = running on owner-controlled
infrastructure reachable by a person outside this laptop. **Nothing in GMS has ever been `Deployed`.**

## 3. The sequences this absorbs

Three ordering systems already exist in the repository. Deleting them would destroy citations, so they are mapped
once, here, and after this table only Stage numbers lead.

| Existing sequence | Where it lives | Its status now |
|---|---|---|
| Security programme **M0…M7** (first mandate) | `TECHNICAL_DEBT.md`, `REPOSITORY_AUDIT.md` | M0 and M1 are **done and merged** (PR #10). The rest of that numbering is retired; the work it named is carried by Stage 3 and Stage 4. |
| Infrastructure programme **Infra-M0…M6** (second mandate) | `INFRASTRUCTURE_AUDIT.md §9`, `ADR-012` | Infra-M0 is done. Infra-M2 is **partly landed** (workflow-validation gate, path filters, the OpenTofu guard suite); Infra-M1/M3/M4/M5/M6 map to Stages 2, 5, 6, 6, 7 below. `ADR-012`'s naming rule still applies to old citations — it is amended, not reversed. |
| Ranked backlog **packages 1…14** | `TECHNICAL_DEBT.md §2` | **Kept as the units of work.** The mandate's priority order differs from the debt ranking in three places (§4); packages are *what*, stages are *when done*. |
| Third mandate **phases 0…7** | `INFRASTRUCTURE_AUDIT.md`, `BASELINE_STATUS.md` headings | Historical labels for work already merged. Not renumbered. |

## 4. Where the mandate's priorities and the measured dependencies disagree

The mandate orders *Foundation → Backend → Frontend → Testing → CI/CD*. The existing dependency edges order
*config/schema → tests → tenancy → pagination → performance*. Both orderings are kept, and the three collisions are
resolved explicitly here rather than silently in whichever document was edited last.

| Collision | Resolution | Why |
|---|---|---|
| `DB-*` (Flyway baseline, constraints, indexes) is a *later* package in `TECHNICAL_DEBT §2` (#6) but priority 2 in the mandate | **Stage 2 — schema first, before the MVP slice** | `TEN-03`'s per-recipient notification state and `DB-04`'s `recipient_ids` join table are both schema changes, and `ADR-006`'s fresh baseline needs a real database to be authored against. Doing tenancy before the baseline means migrating a schema nobody owns. |
| Docker Compose is `Infra-M1` and the debt package #7 — a *later* stage in both | **Stage 2 blocks on it** | Nothing can be verified against PostgreSQL while Compose does not boot on a fresh clone (§5, B-1). A Testcontainers CI job cannot be greener than the local stack. |
| Tenant isolation (`TEN-*`) precedes pagination (`BKL-03`) in the mandate's MVP list | **One branch, tenancy first, then paged reads** | Paging an unscoped query produces a fast wrong answer; scoping an unpaged query produces a slow right answer. The defect that matters is the wrong answer. |

## 5. The roadmap

Legend: **State** is `Closed` / `In progress` / `Open` / `Blocked(owner)`. Oracle is required **only** from Stage 6
(`ADR-019`); the interim tooling is Docker Compose + PostgreSQL locally, Testcontainers in CI, GitHub Actions free
runners.

### Stage 1 — Foundation: product and architecture

**Goal:** one agreed product, one agreed architecture, before another line of application code moves.
**State:** **Criteria met, merge pending** — [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md) and
[ARCHITECTURE.md](ARCHITECTURE.md), plus `ADR-019…023`, are this stage's deliverables, and all six criteria below are
satisfied on the branch that adds them. This stage closes when that branch merges; it does not close because the
documents exist. `DoD-1` remains open — writing an MVP spec is not building the MVP.

| Input | Item |
|---|---|
| Work | Write the PRD and freeze MVP scope; personas, roles, journeys, business requirements; module boundaries with allowed dependency direction; per-table data ownership; API contract standard; index and constraint design sized at ≥50,000 records per school; record the decisions as ADRs |
| Findings consumed | `DOC-01…05` (documentation), `DB-02`/`DB-03`/`DB-05` (design inputs, not fixes) |
| Evidence this stage produces | A PRD whose MVP slice maps onto existing endpoints and existing frontend `features/` directories — measured, not invented; an ownership table built from `grep` over the 36 entity files, not from prose |

**Acceptance criteria.**
1. Every MVP item in the PRD cites at least one existing backend endpoint **or** is marked `greenfield`. Count of
   unmarked items: **0**.
2. `ARCHITECTURE.md`'s data-ownership table covers all 36 entity files and all 35 repositories, and names the
   tenancy status of each — spot-checkable with one command per row.
3. The index design lists an index for every foreign key and every documented filter, and every one of them is
   expressed as something a migration will contain (`CREATE INDEX` text), not as a wish. **Met by
   `ARCHITECTURE.md §5.1`**, which exists because a table of index *names* did not satisfy this criterion when it was
   checked against itself — and by `§5.2`, which ran that DDL against a schema derived mechanically from the 36
   entity files and records the nine missing indexes, the un-runnable `ADD COLUMN … NOT NULL`, the V4-before-V6
   ordering error and the one probe artifact that the run found. Criterion met means the design survived execution,
   not that it was written down.
4. `ADR-019…023` each contain an "Alternatives rejected" section with at least two real rejected options.
5. `docs/README.md` classifies both new documents and the link scan still reports 0 broken links.
6. Writing the design found something the earlier registers had wrong, and it was filed in the register that owns the
   namespace rather than argued in a new document: `DB-05`'s both-conventions entities are **four**, not three
   (`Parent` was missed); `DB-07` is new — four per-school natural keys are declared globally unique, so one tenant
   can occupy a document number the next tenant needs. Two more design rows were caught claiming a `school_id` column
   on tables that do not have one (`ExamSubject`, `StudentExam`, `Result`, `StudentFee`, `FeePayment`), which is the
   same error class as the 10-table list in `§4` and is why the DDL was written before this stage closed.

**Not in this stage:** any code change. A stage that changes code while the product is undefined is how GMS acquired
two audit-column conventions (`DB-05`).

### Stage 2 — A schema someone can own

**Goal:** the database is defined by migrations, on a stack that boots from a clean clone.
**State:** Open. **Depends on:** Stage 1's index design.

| # | Work | Closes |
|---|---|---|
| B-1 | Compose foundation: three compose files, multi-stage non-root images with `HEALTHCHECK`, per-service env files, two networks with `internal: true`, Makefile → `docker compose` | `INF-02/04/05/06/07/08/09/12/17/19`, Infra-M1, debt #7 |
| B-2 | Flyway dependency + `db/migration` + **V1 baseline** generated from the Stage-2 design (not from the friend-owned Neon, which is retired), then `ddl-auto=validate` in **every** profile including `dev` | `DB-01`, `ADR-006` amended by `ADR-022` |
| B-3 | Constraints and indexes as migrations: `ARCHITECTURE.md §5.1`'s V2–V6 DDL is transcribed into Flyway files and **re-run against the real V1 baseline and a populated table** — §5.2 already ran it against a schema derived from the entities, so the open risk is the gap that probe did not cover: Hibernate's own column types, `validate`'s agreement, and backfills on rows that exist. The derivation script itself is committed as `scripts/derive-entity-schema.py` so the run is reproducible from a checkout | `DB-02`, `DB-03` (part), `DB-07`, `ADR-020` level 3 |
| B-4 | Normalise the JSON-in-TEXT sets into join tables (§5.1's V6: `notifications.recipient_ids`, `announcements.target_ids`, `messages.recipient_ids`) and give `Notification` per-recipient read state | `DB-04`, `TEN-03` (shared `readStatus`) |

**Acceptance criteria.**
1. `rm -rf` of a scratch volume, `docker compose up`, `mvn -Dspring-boot.run.profiles=dev verify` → green, on a
   machine that has never seen this project.
2. `flyway migrate` from empty to head, twice, and `pg_dump --schema-only` of the two runs diffs **empty**.
3. `grep -rn "ddl-auto" gms_backend/src/main/resources/ | grep -v validate` → **no output**.
4. `EXPLAIN` on the ten hottest queries shows the planned index being used, captured with `ANALYZE, BUFFERS`.
5. `mvn verify` test count strictly greater than today's **42**, with the new tests being the schema/seed tests.
6. Rule I-2 closed by the query, not the prose: `ARCHITECTURE.md §5.1`'s unindexed-foreign-key `SELECT` returns
   **zero rows** against a schema built only by the real V1 baseline plus V2–V6, and its output is pasted into the PR
   rather than described. §5.2 reports this already true against the entity-derived probe — which is precisely why
   this criterion names the baseline: a check that passes on a schema you generated yourself is weaker than the same
   check on one Flyway builds.
7. V3 is demonstrated to **fail** on a database seeded with one deliberate duplicate (`§9` step 3) — the
   deliberate-failure control `ADR-018` and PR #19 established, applied to a constraint instead of a Terraform guard.
   §5.2's insert table shows six such rejections on the probe; the baseline run owes the same six, from migrations
   rather than from hand-run files.

**Not in this stage:** tenant enforcement in Java (Stage 3), because a guard written against a schema that can still
be silently edited by Hibernate proves nothing.

### Stage 3 — Security and tenant isolation

**Goal:** a principal can only ever reach rows belonging to the school its token asserts, and no endpoint is
reachable by inheritance.
**State:** Open. **Depends on:** Stage 2.

| # | Work | Closes |
|---|---|---|
| C-1 | Delete `AbstractCRUDController` / `AbstractCRUDService` and make every extendee declare its own endpoints (`ADR-023`). Measured live surface on 2026-11-01: 6 controllers extend it; `GET /{id}` is inherited-and-unguarded on 3 (`Parent`, `Student`, `Employee` — `Subject:64`, `Classroom:64`, `School:81` override it); `DELETE /{id}` is inherited-and-unguarded on **all 6**, because `EmployeeController:68` maps `/toggle`, which does not shadow `/{id}`; the comment at `EmployeeController.java:65-67` records that a hand-written `getById` was *deleted* precisely because the base class supplied one. That comment is the case for deleting the base class rather than guarding it | `SEC-05` |
| C-2 | One tenant resolution path: the `schoolId` claim parsed once per request into a request-scoped context; services and repositories take it from there instead of a parameter that a caller may forget | `TEN-01…07`, debt #5 |
| C-3 | School-scoped repository methods for the 6 repositories that have none (`ExamSubject`, `Parent`, `Result`, `StudentExam`, `Timetable`, `TimetableSlot`) and for the 10 entities with no direct school link, via their owning aggregate | `TEN-*`, `DB-03` |
| C-4 | Put the ownership check where the JWT is verified, so a wrong tenant is 403/404 and not a filtered empty list | `SEC-06` |
| C-5 | `@PreAuthorize` on every mutating endpoint; deny-by-default verified by a mapping scan that fails the build on an unannotated handler | `SEC-04`, `OPS-*` |
| C-6 | Resolve the role vocabulary: 39 guards name `ACCOUNTANT`, which is in no enum, issued by no code and unknown to the frontend (`SEC-17`). Either the role exists and `RoleEnum` plus provisioning grow, or those guards collapse to `ADMIN` and the string disappears | `SEC-16`, `SEC-17` |

**Acceptance criteria.**
1. A parameterised integration test suite that, for **every** protected resource, authenticates as school A and
   attempts read/update/delete of a school B id, asserting 403 or 404. Resource count in the suite ≥ controller
   count in `controller/` (36 today), enforced by a test that reads the controller list itself.
2. The **deliberate-failure control** that this repository has used twice before (`ADR-018`, PR #19): remove one
   guard on a scratch branch, show the suite goes red on exactly that resource, restore it. Recorded with run ids.
3. `grep -rn "extends AbstractCRUDController" gms_backend/src/main/java/` → **no output**.
4. A `MockMvc` assertion for every one of the six extendees that `GET /{id}` and `DELETE /{id}` answer 401/403 for a
   caller with no role claim. The reason this is a criterion and not a code comment: `SEC-05` was written against
   **seven** extendees including `UserController`, and `UserController.java:25` turns out not to extend the base at
   all — so at least one row of that finding had been edited out from under it. The live surface is re-measured in
   `REPOSITORY_AUDIT.md` and the test is what stops the next edit from silently changing it.
5. One test per role that issues a real token and calls one endpoint guarded for that role, asserting 200 — and the
   mirror test asserting a role that should not reach it gets 403. This is the only way `SEC-17` can be closed: the
   claim set in a token, not the string in an annotation, decides access.
6. All existing 42 tests still green; no check downgraded to `continue-on-error` to get there.

### Stage 4 — The MVP vertical slice

**Goal:** the frozen MVP in [PRODUCT_REQUIREMENTS.md §8](PRODUCT_REQUIREMENTS.md) works end-to-end for two tenants
at once.
**State:** Open. **Depends on:** Stage 3.

Work: server-side pagination, sorting and filtering for every list resource (debt #10, `BKL-03`, `FE-08`); response
envelope and one error contract (`BKL-01`, `BKL-07`); DTOs instead of entity leakage (`SEC-04/ACD`); OpenAPI
generated from code, not typed; the frontend contract repair (`FE-01…08`); dashboards computed from aggregate
queries rather than `array.length`; the ACD/FIN stubs the MVP actually needs (`ACD-01…04`, `ACD-07`, `ACD-10`,
`ACD-12`, `FIN-04`, `FIN-06`, and `SubjectServiceImpl.getById:102`).

**Acceptance criteria.**
1. DoD-1's scripted journey passes, twice, from an empty database.
2. No list endpoint returns an unbounded collection: `grep -rc "ResponseEntity<List<" controller/` falls from
   today's **90** to only the resources the PRD declares bounded, and every one of those is on a named list with a
   stated row ceiling.
3. Every list request round-trip carries `page`/`size`/`sort` and the server, not the browser, does the slicing
   (proven by a network-level assertion that the payload is bounded by `size`).
4. Frontend: no page derives a count by downloading a collection.
5. `npm run build` green, and the frontend test count > 0 (today: **0** Vitest tests).

### Stage 5 — Verification at volume, and CI that refuses

**Goal:** the performance claims are measurements, and the pipeline can say no.
**State:** Open. **Depends on:** Stage 4.

Work: a seeded dataset generator (`scripts/seed-performance.sql` + a JUnit **ignored-by-default** harness) creating
≥50,000 students and proportional attendance/fee/result/timetable rows for 2 schools; Testcontainers integration
jobs; the remaining Infra-M2 items (required status checks with `enforce_admins`, dependabot, secret scan, image
scan, SBOM, artefact retention, CODEOWNERS).

**Acceptance criteria.**
1. `DoD-2` budgets met, with `EXPLAIN (ANALYZE, BUFFERS)` output and latency percentiles recorded per query —
   p50/p95/p99 for the ten hottest endpoints, not a single average.
2. The generator is re-runnable and idempotent; the seeded counts are asserted, not assumed.
3. N+1 detection: a test that fails the build when a request issues more than a stated number of SQL statements.
4. A PR that breaks a test is unmergeable **even with `--admin`** (`DoD-4`), demonstrated once.
5. `mvn verify` in CI runs unit **and** integration jobs; CI minutes reported against estimate, per the standing
   zero-unexpected-cost rule.

### Stage 6 — Deployment preparation (Oracle enters)

**Goal:** a deploy and a rollback each executed once, on owner-controlled infrastructure.
**State:** Open. **Oracle required: yes** — first stage where it is. `ADR-019` moved the provisioning step here;
`ADR-015` (target) and `ADR-018` (the OpenTofu stack, already merged and guard-tested) are unchanged.

Work: owner runs `oci setup config`; read the four cost numbers from the account **before** `apply`; `tofu plan` →
review → `tofu apply`; provisioning the owner's own Neon project (`ADR-016`); `Infra-M3` deployment engineering —
versioned `scripts/deploy.sh`, GHCR images tagged by commit SHA, digest recorded at deploy, `environment: production`
with required reviewers, deployment lock, post-deploy SHA/health verification; `Infra-M4` — Caddy + TLS, HTTPS
enforcement, security headers, 22/80/443 only, least-privilege DB role; backups (`Infra-M5`: `db-backup.sh` /
`db-restore.sh`, encrypted, rotated, one owner-approved off-host destination) and the five runbooks
(`DEPLOYMENT_RUNBOOK`, `ROLLBACK_RUNBOOK`, `BACKUP_AND_RESTORE`, `INCIDENT_RESPONSE`, `OPERATIONS_GUIDE`), which
[docs/README.md §4](README.md) correctly refuses to write before the pipeline exists.

**Acceptance criteria.** DoD-5 on the production database engine; DoD-6 with a real restore; one rollback executed;
a concurrent deploy serialised; an external port scan from this machine showing **only** 22 (restricted to the
owner's `/32`, which is one of the seven HCL guards), 80 (redirecting) and 443 — and 5432/8080/3000 confirmed closed;
**no** `tofu apply` output containing a resource type that was not reviewed in the plan.

### Stage 7 — Production qualification

**Goal:** every row of the readiness matrix is `VERIFIED` with a citation, or explicitly `BLOCKED` with the owner
action named. No row promoted because someone wrote it confidently.
**State:** Open. **Oracle required: yes.**
Work: `Infra-M6` re-run with runtime evidence; host reboot recovery; failed-deploy and rollback rehearsals; the
qualification report; then the post-foundation module backlog on the same standards (`ACD-*`, `FIN-*`, `FE-*` design
system, the remaining `DOC-*`).

## 6. Standing constraints — every stage, no exceptions

- **Never push directly to `master`.** One feature branch per stage-package, PR, then merge.
- **Never bypass repository protection or required checks** without the owner naming the bypass. Every merge so far
  has needed `--admin` because protection is not yet switched on (that is Stage 5's `DoD-4`, and it is an owner
  action, not a code action).
- **Zero unexpected cost.** GitHub-hosted `ubuntu-latest` only; no paid runners, no managed services, no billable
  feature started without asking. Docker on the runner is free; Testcontainers is Docker. Estimated and actual
  Actions minutes are reported for every package.
- **No destructive database operations.** Never `docker compose down -v`. Never drop a schema without a stated,
  authorised, tested backup. The retired friend-owned Neon database stays untouched and its credentials unused.
- **No secrets in source control, logs, reports or chat.** The owner is never asked to paste a private key,
  password or token; only public values are named in issues.
- **No duplicates.** Before creating a file, endpoint, entity, DTO, repository, service or utility, search for an
  existing one and reconcile with it. Modification of a poor implementation beats a second implementation.
- **A task is not complete without evidence** — the command, its output, and the environment that produced it.
- **A failing check stays failing** until it is fixed or its irrelevance is argued in writing. Never silenced by
  `continue-on-error`.

## 7. Blocked on the owner (unchanged, and Oracle is no longer one of them)

| Blocker | Needed | Gates which stage |
|---|---|---|
| Branch protection | Name the five checks, `strict`, `enforce_admins`, review count 0 | Stage 5 (`DoD-4`) |
| Dead `EC2_*` secrets | Delete `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`, `EC2_SSH_KNOWN_HOSTS` from the repo/org | Housekeeping; recorded in `ADR-017` |
| Neon ownership | Create **your own** project, verify the email | Stage 6 |
| DNS | Where `bytendra.com` / `bytencia.com` are hosted | Stage 6 (TLS) |
| Licence | A licence has never been chosen; the README's MIT claim was removed because `LICENSE` has never existed | Before any public distribution |
| ~~Oracle account setup~~ | ~~`oci setup config`~~ | **Deferred to Stage 6 by `ADR-019`. Nothing is blocked on it now.** |

## 8. Stage status board

| Stage | Gate | State | Cost | Oracle |
|---|---|---|---|---|
| 1 Foundation | PRD + architecture + ADR-019…023 accepted | **Criteria met; acceptance = the merge** | $0 | No |
| 2 Schema | migrate-empty-twice diff empty; `ddl-auto=validate` everywhere | Open | $0 | No |
| 3 Security & tenancy | cross-tenant suite green + one guard-deletion control red | Open | $0 | No |
| 4 MVP slice | journey end-to-end for two tenants, bounded lists | Open | $0 | No |
| 5 Volume + CI | 50k budgets met, checks required and enforced | Open | $0 (runner minutes only) | No |
| 6 Deployment prep | one deploy + one rollback + one restore observed | Open | ₹0 expected (Always Free) — **owner approval required before any paid resource** | **Yes** |
| 7 Qualification | matrix fully `VERIFIED`/`BLOCKED`, no promotion by authorship | Open | $0 | Yes |

**Reading this honestly:** nothing beyond Stage 1's documents has started, and no stage above 1 is `Closed`. The
roadmap is the plan; the evidence sections of `BASELINE_STATUS.md` are the proof. If the two disagree, the proof
wins and this table is wrong.
