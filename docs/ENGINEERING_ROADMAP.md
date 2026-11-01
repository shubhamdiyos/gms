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
**State:** **Closed** — merged into `master` as `a8748f7` (PR #24). [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md) and
[ARCHITECTURE.md](ARCHITECTURE.md), plus `ADR-019…023`, are this stage's deliverables, and all six criteria below are
satisfied on the branch that added them. The rule that kept this stage honest is the reason the row above it read
*merge pending* rather than *done* for a day: a stage closes when its branch merges, not when its documents exist.
`DoD-1` remains open — writing an MVP spec is not building the MVP.

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
**State:** Executed and evidenced — committed as `231bdab`, opened as **PR #25**, `mergeStateStatus: BLOCKED`
(`REVIEW_REQUIRED`). CI on that PR is green: run `41310639236`, `Backend build and tests` 52 s, `Frontend lint and
build` 30 s, `Infrastructure definitions` 20 s, Vercel preview deployed. **Depends on:** Stage 1's index design. This
stage closes when that PR merges; the migrations existing on a branch is not the same fact, and `master` still boots
with `ddl-auto=${JPA_DDL_AUTO:update}`. Green checks do not merge a PR — no check is a required check (`INF-10`).

| # | Work | Closes | State |
|---|---|---|---|
| B-1 | Compose foundation: three compose files, multi-stage non-root images with `HEALTHCHECK`, per-service env files, two networks with `internal: true`, Makefile → `docker compose` | `INF-02/04/05/06/07/08/09/12/17/19`, Infra-M1, debt #7 | Part — see *Compose, measured* below |
| B-2 | Flyway dependency + `db/migration` + **V1 baseline**, then `ddl-auto=validate` in **every** profile including `dev` | `DB-01`, `ADR-006` amended by `ADR-022` | Done on branch |
| B-3 | Constraints and indexes as migrations: `ARCHITECTURE.md §5.1`'s V2–V6 DDL transcribed and **re-run against the real V1 baseline** | `DB-02`, `DB-03`, `DB-07`, `ADR-020` level 3 | Done on branch, with the findings below — and with `DB-02`'s fifth key added after the register was re-read (`§5.3` correction 4) |
| B-4 | Normalise the JSON-in-TEXT sets into join tables (§5.1's V6: `notifications.recipient_ids`, `announcements.target_ids`, `messages.recipient_ids`) and give `Notification` per-recipient read state | `DB-04`, `TEN-03` (shared `readStatus`) | **Moved to Stage 3.** A join table only pays for itself when the code that reads `recipient_ids` is rewritten to read it, and that code is Stage 3's tenant-isolation work. The delivery objective defines Stage 2 as "Compose foundation, Flyway baseline and schema validation", so V6 stays in §5.1 as design and does not ship as a migration nothing calls |

**Stage 2 result, measured.** `V1__baseline_current_schema.sql` is the ADR-022 compromise executed as
that ADR words it: the application was booted once against an empty disposable database with
`JPA_DDL_AUTO=update`, the schema was dumped with `pg_dump --schema-only`, and the dump became V1
after review. What Hibernate writes today is **38 tables, 14 UNIQUE constraints, 123 foreign keys
and 0 `idx_*` secondary indexes** — that `0` is the number `ARCHITECTURE.md §1` claimed, now confirmed
against a server rather than against a reading of the entities. (The first draft of this line said 128
foreign keys; `pg_constraint` on the applied baseline says 123, and `REPOSITORY_AUDIT §4` had it right
all along. A count copied from a document is a guess wearing a number.) V2–V5 take it to **38 tables, 87
`idx_*` indexes, 35 named uniques, 11 composite foreign keys**. `scripts/verify-schema.sh` runs the
whole proof and is repeatable from a checkout — six sections, and its final line is `ALL CHECKS
PASSED` or nothing.

One of those numbers moved while this stage was being written up. `DB-02` names five missing keys;
`V3` shipped four, and the register said "closed" until the finding was re-read against the list
rather than against the migration. `students.email` was the fifth, and it needed a *partial* unique
(`uq_students_school_email … WHERE email IS NOT NULL AND email <> ''`) rather than a plain one,
because the column is nullable and blank means "this child has no mailbox", not "shared address" —
`employees.email` is `nullable = false` and therefore takes the plain key. `§6` of the script tests
it in both directions, because a partial predicate can fail by being too tight as easily as by being
decorative: two blank emails, a `NULL` and the same address in another school are accepted; a repeat
inside one school is refused.

Three things §5.1 got wrong, found by executing it rather than by re-reading it:

1. **Rule I-2 as stated is unsatisfiable, and the reason is a fact about this schema.** Against
   V1+V2+V3 the query for a foreign key with no index leading on its first column returns **69 rows,
   and 62 of them are `created_by` or `updated_by`** — the audit-bookkeeping references
   `BaseAuditEntity` puts on thirty-one tables. Nothing reads "rows created by user 41", so those
   62 would be 62 write-time taxes for a query that does not exist. The rule is amended to *every
   navigated foreign key*; the exemption is expressed by column name inside the assertion, so an
   unfamiliar FK still has to earn an index; and the 7 that remain (`students.classroom_id`,
   `students.section_id`, `student_admissions.classroom_id`,
   `financial_transactions.{school_id,student_id,employee_id,fee_payment_id}`) are indexed. Both
   assertions now live in the migrations (`V4`, re-asserted in `V5`) instead of in prose.
2. **`SET NOT NULL` cannot ship in Stage 2, and that is a measurement, not a delay.** V2 adds the
   tenant column to nine tables and V5 to `parent_student`; §5.1 also flipped each to NOT NULL after
   asserting the backfill. `grep -in "school"` over the ten affected entity files returns **nothing
   for all ten** — no entity maps the column and no service sets it, so a NOT NULL today is a
   production outage on the first attendance record after the deploy. The columns are added,
   backfilled and asserted; the flip travels with the entity field and the service assignment that
   make it true, which is `C-7` below. Until then V5's composite keys hold the tenant for every row
   that has one and are permissive about the rows the old write path cannot fill — which is exactly
   the gap `DoD-5` measures.
   *Closed 2026-11-01 by `C-7`, with one correction to the wording above: the grep covered **nine**
   entity files, not ten, because `parent_student` is a `@ManyToMany` join table and has no file to
   grep — which is why the tenth `SET NOT NULL` ships behind a database trigger rather than an entity
   field. See `C-7, executed 2026-11-01` under Stage 3.*
3. **The generator §5.2 leaned on had two silent bugs, and committing it forced them out.**
   `@JoinTable\([^)]*name` is greedy across a nested annotation, so it emitted a table called
   `parent_id` instead of `parent_student`; and the field pattern's type class omitted `.`, so
   `private StatusEnum.Department department;` (`Designation.java:53-54`) and
   `private java.math.BigDecimal cost;` (`SmsLog.java:56-57`) were dropped without a word. Fixed in
   `scripts/derive-entity-schema.py`, which is now checked rather than trusted: against the live
   server it agrees on **38 of 38 tables and 598 of 598 columns**.

**A fourth change, and it lands on every developer's machine.** A database that an earlier build created with
`ddl-auto=update` now refuses to boot: `Found non-empty schema(s) "public" but no schema history table`. That message
is the mechanism working — `update`-built schema is not a baseline, it is a second unreviewed schema, and setting
`baselineOnMigrate=true` to make the message stop would apply V2–V5 on top of whatever Hibernate guessed, which is the
defect `ADR-022` exists to delete. The remedy is to let the chain build the database (drop the *local* scratch DB, or
`docker compose down -v`), and it is written in `BASELINE_STATUS §5` rather than run here, because dropping a database
is not reversible and the one on this machine holds rows nobody has asked to lose.

**Acceptance criteria.**
1. `rm -rf` of a scratch volume, `docker compose up`, `mvn -Dspring-boot.run.profiles=dev verify` → green, on a   machine that has never seen this project. **Open.** The Postgres half of the stack is repaired and
   verified (*Compose, measured*), and the backend image now builds with its non-root runtime stage;
   the criterion needs the whole stack plus `mvn verify`, so it closes with Stage 5's CI job.
2. `flyway migrate` from empty to head, twice, and `pg_dump --schema-only` of the two runs diffs
   **empty**. **Met**, by `scripts/verify-schema.sh` §1 — the repeatable form of the check, not a
   transcript of one manual run: two fresh databases, 1503 dump lines each, diff empty. The
   application's own boot against a database it migrated itself is separate evidence and also holds
   (`Successfully applied 5 migrations … now at version v5`, `Started GmsApplication in 6.763
   seconds`, readiness `UP`), with one caveat worth recording: the first attempt used a jar built
   *before* the `pom.xml` edit, which logged no Flyway line at all and failed at
   `Schema-validation: missing table [academic_years]`. A stale artifact skips migrations silently
   and then blames the schema, so the gate includes the rebuild.
3. `grep -rn "ddl-auto" gms_backend/src/main/resources/ | grep -v validate` → **no output**.
   **Met**, and stronger than asked: all three files now carry the literal
   `spring.jpa.hibernate.ddl-auto=validate` with no `${JPA_DDL_AUTO:…}` anywhere, so there is no
   environment variable that reaches `update`. `JPA_DDL_AUTO` is deleted from `.env.example` for the
   reason SEC-15 gives — an entry that binds to no property is a bug.
4. `EXPLAIN` on the ten hottest queries shows the planned index being used, captured with `ANALYZE,
   BUFFERS`. **Moved to Stage 5.** On an empty probe database this criterion is theatre: a plan over
   zero rows is a plan over zero rows. Stage 5 seeds the ≥50,000 representative records the delivery
   objective asks for, and measures the plans there, where an unused index is a finding rather than
   a formality.
5. `mvn verify` test count strictly greater than today's **42**, with the new tests being the
   schema/seed tests. **Moved to Stage 5**, for the same reason: the schema tests that matter are
   Testcontainers jobs over a seeded database, and Stage 5 owns the seed. Stage 2's evidence is
   `scripts/verify-schema.sh`, which is shell rather than JUnit and does not inflate the count — the
   roadmap records that rather than quietly counting it.
6. Rule I-2 closed by the query, not the prose. **Met as amended.** The raw §5.1 query returns 69
   rows against the baseline, 62 of them audit columns; the amended query returns **zero**.
   `scripts/verify-schema.sh` prints both numbers on every run. That is the honest form of the
   criterion: not "the query is empty", but "the query is empty *and* the exemption is named,
   counted and justified".
7. V3 is demonstrated to **fail** on a database seeded with one deliberate duplicate (`§9` step 3).
   **Met.** Seeded: two students in school 1 both holding admission number `A-001` — legal in V1,
   which puts no unique on `students.student_id`. V3 answers with `ERROR: could not create unique
   index "uq_students_school_student_id"` / `DETAIL: Key (school_id, student_id)=(1, A-001) is
   duplicated.` A second control runs the other half of `DB-07`: after the chain, one
   `invoice_number` issued in two schools is **accepted** and the same number twice inside one
   school is **refused** by `uq_invoice_school_number`. `§6` adds the pair for the key that was
   almost missed: `uq_students_school_email` accepts two blank emails, a `NULL` and the same address
   in another school, and refuses a repeat inside one school.
8. **Added by the run.** The cross-tenant write is attempted, not assumed. Against the migrated
   baseline an attendance row that names school 2 while pointing at a school 1 student and slot is
   rejected with `violates foreign key constraint "fk_attendance_student_same_school"`, and the
   same-tenant row in the same script is accepted. `ADR-020` level 3 is now observed on a real
   server built by real migrations. It is still not `DoD-5`: no HTTP request and no JWT were in the
   loop.

**Compose, measured.** The stack did not boot as documented, for a reason nobody had written down:
`postgres` published `5432:5432` on every interface while a Homebrew PostgreSQL 14.13 on the same
host already held 5432. Compose reports a port collision by *not* publishing, so `docker compose ps`
advertised the container and JDBC reached the host server — which is how a schema-capture run
silently produced a PG14 schema for PG16 entities. Postgres is now published
`127.0.0.1:${DB_PORT:-5433}:5432`, loopback-only, and `.env.example`, the dev default in
`application-dev.properties` and the container agree on the number. `frontend` gained a healthcheck
and `depends_on: backend: condition: service_healthy`; the backend runtime stage is now non-root and
sizes its heap with `-XX:MaxRAMPercentage`. **Remaining B-1 items, deliberately not done here:** a
Makefile, the second `internal: true` network and separate compose overlays. Each is convenience or
hardening, none is on the path to a working MVP slice, and *"Do not let Stage 2 … expand
indefinitely"* is the instruction that moves them to Stage 5, where the CI job needs them anyway.

**Not in this stage:** tenant enforcement in Java (Stage 3), because a guard written against a schema that can still
be silently edited by Hibernate proves nothing.

**Carried into Stage 3.** Two schema changes are waiting on application code rather than on more SQL:
`C-7` landed the **ten** `SET NOT NULL` flips Stage 2 had to leave open — the count Stage 2's prose got wrong in
two directions (it said "two", and it said the tenth column had an entity; it does not, so `parent_student` is held
by a trigger) — and is done, as `V6`. `C-8` is B-4's join tables, which only earn their keep once the code that
reads `recipient_ids` reads them instead; its migration is the `V7` this branch kept free by moving `C-7` down a
number.

### Stage 3 — Security and tenant isolation

**Goal:** a principal can only ever reach rows belonging to the school its token asserts, and no endpoint is
reachable by inheritance.
**State:** Open. **Depends on:** Stage 2.

| # | Work | Closes |
|---|---|---|
| C-1 | Delete `AbstractCRUDController` / `AbstractCRUDService` and make every extendee declare its own endpoints (`ADR-023`). Measured live surface on 2026-11-01: 6 controllers extend it; `GET /{id}` is inherited-and-unguarded on 3 (`Parent`, `Student`, `Employee` — `Subject:64`, `Classroom:64`, `School:81` override it); `DELETE /{id}` is inherited-and-unguarded on **all 6**, because `EmployeeController:68` maps `/toggle`, which does not shadow `/{id}`; the comment at `EmployeeController.java:65-67` records that a hand-written `getById` was *deleted* precisely because the base class supplied one. That comment is the case for deleting the base class rather than guarding it. **Done 2026-11-01 on the branch**: both classes deleted, 25 service impls and all 36 controllers detached, the two frontend-called deletes re-declared tenant-scoped, three tenancy holes closed (`ParentServiceImpl.findBySchool` returned every platform's parents; `UserService.getById`/`deleteById` had no tenant predicate; `SchoolService.getById` did not exist). Measured: 33-test suite green, live script 29/29, control red on exactly 2 assertions, full suite 79/79 | `SEC-05` |
| C-2 | One tenant resolution path: the `schoolId` claim parsed once per request into a request-scoped context; services and repositories take it from there instead of a parameter that a caller may forget. **Done 2026-11-01 on the branch**: `SecurityUtil` deleted — it re-parsed and re-verified the JWT signature on every one of 325 call sites across 41 files — and replaced by `security/TenantContext`, a request-scoped holder the filter fills from the claims it has already verified and clears in a `finally` so a pooled thread cannot carry one caller's tenant forward. 35 service interfaces and 31 implementations rewritten: 221 methods took a `schoolId`/`empId`/`creatorEmpId`/`updaterEmpId`/`employeeId` argument and no longer do; 201 now-dead controller locals removed. Two regressions found by review and repaired: the transformation rewrote the string literal `"schoolId"` inside three `BeanUtils.copyProperties` ignore-lists, and it silently turned `AuthController.selfRegister`'s `findByEmployee_Id(employee.getId())` into the caller's own employee — the self-registration uniqueness check would have read an empty tenant. A cross-tenant refusal was answered 400 on the write path and 403 on the read path; `TenantAccessDeniedException` makes both 403. Measured: 86-test suite green, `verify-c1-tenancy.sh` 29/29, new `verify-c2-tenant-context.sh` 32/32, control red on exactly the "verified once" assertion | `TEN-01…07`, debt #5 |
| C-3 | School-scoped repository methods for the reads that span schools. **Done 2026-11-01 on the branch**: measured with `RepositoryTenantScopeArchTest` run against the build that ended `C-2` — 170 declared repository reads, 55 carrying no tenant, 37 of them neither scoped nor explainable. 17 repository interfaces and 13 services (22 call sites) rewritten so the tenant arrives in the query; the same scan now reports 168 reads, 150 carrying the tenant, 18 that do not and every one allow-listed with the reason the schema supports it. Two dead cross-tenant log queries deleted, one true duplicate deleted, the platform-wide `timetableRepository.findAll()` and `studentRepository.findAllById(ids)` replaced with tenant-keyed forms. Measured: 87-test suite green, `verify-c1-tenancy.sh` 29/29, `verify-c2-tenant-context.sh` 32/32, new `verify-c3-scoped-reads.sh` 25/25 — and that same script run against the pre-C-3 build fails 6 checks, one of them a live read of school A's timetable by school B | the query half of `TEN-01…07`; `TEN-06` **partly** — explicit predicates, not a session filter, and inherited reads plus level 3 stay open (`C-4`, `C-7`). `DB-03` was already closed by `B-3` |
| C-4 | Put the ownership check where the JWT is verified, so a wrong tenant is 403/404 and not a filtered empty list. **Done 2026-11-02 on the branch**: `TenantScopedRepository` gives `findById` a tenant predicate so the inherited method stops being a hole (33 repositories scope it, 3 exempt with a reason, counted by `TenantScopedReadArchTest` on every build); the duplicate hand-written comparisons a scoped load makes dead are deleted where they duplicated the load and kept where they guard an attachment; the entity-side lazy-proxy 500s this card exposed are guarded and now policed by `EntityLazyAccessorArchTest`. Measured live: `verify-c4-owned-reads.sh` **118 PASS / 0 FAIL / 17 SKIP**, 22 probes over the 22 controllers that declare a by-id read; the deliberate-failure control on `ClassroomRepository` turns the build-time guard red twice and the live suite red on exactly `classroom` and nothing else; `C-1` 29/29, `C-2` 32/32, `C-3` 24/0 and `C-7` 65/0 re-measured on the same jar after their 403 expectations were widened to "403 or 404" with the reason recorded | `SEC-06` |
| C-5 | `@PreAuthorize` on every mutating endpoint; deny-by-default verified by a mapping scan that fails the build on an unannotated handler. **Done 2026-11-02 on the branch**: `DenyByDefaultAuthorizationArchTest` reads the guard state off the compiled handler rather than the source text, and reports **236 handlers, 234 guarded, 2 deliberately public** (`AuthController.login`, `HealthController.ping`) with the URL rule each is permitted by; no class-level guard, no `hasRole(`, and every authority named by a guard is a role the application can actually mint | `SEC-04`, `OPS-*` |
| C-6 | Resolve the role vocabulary: 39 guards name `ACCOUNTANT`, which is in no enum, issued by no code and unknown to the frontend (`SEC-17`). **Done on the backend 2026-11-02, open on the frontend**: `ACCOUNTANT` is now a `RoleEnum` value and therefore issuable, so the guards that already named it are satisfiable instead of permanently closed; `user_roles.role` is a plain `varchar(30)` with no enum type and no `CHECK`, so no migration is needed. `/auth/self-register` no longer mints the unreachable `"EMPLOYEE"` — it routes its role through the one validator `/users/create` uses, which rejects a non-`RoleEnum` code before the username is taken. The `roles` claim is now sorted where it is written, so a client reads it as data instead of guessing a precedence. What is left is the surface: an accountant has no route, no link and no dashboard | `SEC-16`, `SEC-17` |
| C-7 | Finish what Stage 2 deliberately left open: map `school_id` so the write path fills it, then land the deferred `SET NOT NULL` flips. A tenant column the application may leave NULL is a column, not a control. **Done 2026-11-01 on the branch** — and the card's own plan was wrong twice, which is the first finding. It named **ten** entities; the unmapped column is on **nine**, because `parent_student` is a `@ManyToMany` join table with no entity to map, so it is closed in the database instead: `trg_parent_student_tenant_key` derives the tenant from the student on `BEFORE INSERT OR UPDATE`, raises when the student is missing, and raises when a hand-written row disagrees with its student — a trigger, not a silent coercion, because re-deriving a *conflicting* value would make the column a cache rather than a key. It said the flips would land "as a V7"; they land as **V6**, the next number no committed file owns — `git ls-tree HEAD` against `db/migration/` returns `V1`–`V5` and nothing else — which returns the V7 slot §5.1 had reserved for this card's successor to `C-8`. The nine (`Attendance`, `ExamSubject`, `FeePayment`, `Result`, `StudentExam`, `StudentFee`, `TeacherAssignment`, `Timetable`, `TimetableSlot`) carry the canonical `@JsonIgnore` + `@ManyToOne(fetch = LAZY, optional = false)` + `@JoinColumn(name = "school_id", nullable = false)` block and 11 create paths in 8 services assign it — from `TenantContext.schoolId()`, never copied off the parent row, because a copied tenant makes the same-school FKs it is supposed to satisfy vacuous. The ten flips ship in one migration behind ten idempotent backfills and a ten-clause assertion that names the table it refused to flip, plus four candidate keys (`(id, school_id)`) and **seven** new same-school FKs, and re-runs the V4/V5 index guards because a guard that is checked only once is a guard against the migration that forgot it. Measured: **89-test suite green**; new `EntityTenantColumnArchTest` prints `C-7: 35 tables carry an integer school_id, 36 entity classes cover 34 of them, 1 have no entity and a reason`; `C-3`'s scan moved from 168/150 to **169/151** because the fix needed a new scoped read (`TimetableSlotRepository.findByIdAndSchool_Id`); a fresh database applies V1→V6 and boots with `ddl-auto=validate`; new `verify-c7-tenant-key.sh` **66 passed / 0 failed / 0 skipped**, including per-table "`written by school A, and by nobody else`" for all ten, ten rolled-back `SET school_id = NULL` probes that each first assert a row exists, and a cross-tenant timetable write refused with 403; `C-1`/`C-2`/`C-3`'s scripts re-run on this build 29/29, 32/32, 25/25. **Two controls, each red then restored:** dropping `Result`'s mapping turns the arch test red naming `Result`; pointing `Attendance`'s join column at `org_id` turns the live script red with `attendances: none -- the endpoint never wrote one`. **Three pre-existing bugs the verification surfaced and this card fixes** (each proven from the server log, none a C-7 regression): `POST /student-exams` and `POST /fee-payments` 500'd with `LazyInitializationException` because `mapToResponse` walked a lazy proxy outside a session — now `@Transactional`; `POST /attendance` could never have written a row, since the service read `timetableSlotId` and dropped it against a NOT NULL column, and resolving that slot tenant-scoped is also what makes `fk_attendance_slot_same_school` expressible; and a straddling `POST /timetables` answered **409** from the platform-wide `uq_timetable_classroom_year` before the tenant rule was reached, so `GlobalExceptionHandler` now distinguishes a `*_same_school` violation and returns 403 | `ADR-020` level 3 completed, Stage 2 finding 2, the level-3 half of `TEN-06` |
| C-8 | §5.1's V6 as a migration and as code: `notification_recipients`, `announcement_targets`, `message_recipients`, per-recipient read state on `Notification`, and the repositories rewritten off `recipient_ids`. **Its migration is V7** — §5.1 and §9 of `ARCHITECTURE.md` reserved `V6` for exactly this work, `C-7` has taken that number, and the reservation moves with it; do not number it V8 to look sequential. It may also retire `C-7`'s `trg_parent_student_tenant_key`: the trigger exists because a `@ManyToMany` join table has no entity to map, and if this card gives `parent_student` an entity of its own, the assignment belongs in Java and the trigger should be deleted in the same migration, not left as a second writer of the same column | `DB-04`, `TEN-03`, moved from B-4 |

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

**`C-1` results, 2026-11-01, on `feat/stage2-flyway-baseline`.** Criterion **3** met: the grep for
`AbstractCRUDController` and for `AbstractCRUDService` returns nothing, and no controller in `com.gms.controller`
extends anything. Criterion **4** met: `EndpointInheritanceTest` asserts, for each of the six extendees, that no
handler carries an inherited mapping, that every declared operation carries its own `@PreAuthorize`, and that the
inherited read and delete routes are absent from the mapping table; it also asserts 401 without a token and 403
without a role claim on twelve routes, plus 403 for a school administrator reading another school's student and for a
teacher attempting a delete. Criterion **2** met: restoring the inherited delete on `StudentController` and removing
its guard turned the suite red on exactly the two assertions covering it, and the file was restored. Criterion **6**
met: **79 tests green** (42 pre-existing, 33 new, 4 exception-mapping).

Criteria **1** and **5** are **not** met and are **not** closed by `C-1`. They are the cross-tenant suite for *every*
resource and the per-role token suite; `C-1` covers the six extendees it touched, and the live script
`scripts/verify-c1-tenancy.sh` proves the same boundary end-to-end for two real tenants against a real database
(29 checks, 0 failures). The remaining work is `C-2`…`C-6`, which is what turns the six-resource boundary into a
whole-application one. Two client-error paths the live run exposed were fixed here rather than deferred: a
method mismatch answered **500** instead of 405, and a referenced-row delete answered **500** instead of 409. The
third 500 — every method-level `@PreAuthorize` refusal answering 500 instead of 403 — was also fixed, and it was not
specific to this card.

**`C-2` results, 2026-11-01, on `feat/stage2-flyway-baseline`.** The card's own criterion is a property of the
code, so it is measured two ways: a test and a live run.

**The test.** `TenantContextTest` is a `@WebMvcTest` that stubs the token provider and asserts
`verify(tokenProvider, times(1)).parseClaims(anyString())` — the claims are parsed once per request, not once per
tenant lookup. The assertion has teeth: a deliberate second `parseClaims` call in the filter turned the suite red on
exactly that one test, and the call was removed. Three more tests assert that the tenant reaches the service from
the claims, that the context is empty once the request has finished, and that an unauthenticated request leaves no
tenant behind. `TenantParameterArchTest` reads the bytecode of every type in `com.gms.service` and fails if any
method still declares a parameter named `schoolId`, `empId`, `employeeId`, `creatorEmpId`, `updaterEmpId` or
`tenantId` — the invariant that a future method cannot quietly take the tenant back as an argument. **86 tests
green** (79 before this card, 7 new).

**The live run.** `scripts/verify-c2-tenant-context.sh`, 32 checks, 0 failures, against a real database and two real
schools. It proves that the tenant a write lands in is the one in the token and that the caller has no way to name a
different one; that each administrator's school-scoped list holds rows from exactly one school and never the other
school's; that the audit trail is written from the token's employee — the `users.created_by` column holds the
caller's own account, because `UserServiceImpl` resolves the creator through the context and no longer accepts it as
an argument; that a cross-service check still refuses another school's row; that a token that does not verify is
refused; and that the platform owner, who is not a tenant, keeps its reach. The row-level checks read the database
directly through `docker exec`, and report **SKIP** rather than PASS when no database handle is configured.
`scripts/verify-c1-tenancy.sh` was re-run against the same build: **29 checks, 0 failures**.

**Two regressions were found by review and repaired before the commit.** The transformation rewrote the *string
literal* `"schoolId"` inside three `BeanUtils.copyProperties` ignore-lists into `"TenantContext.schoolId()"`, which
names no bean property — the ignore list would have stopped ignoring it. And it rewrote
`AuthController.selfRegister`'s `findByEmployee_Id(employee.getId())` into the context's employee id: the
self-registration uniqueness check asks about the employee *being registered*, who is not the caller, and on an
unauthenticated endpoint the context is empty, so the check would have silently passed. `UserService.existsByEmployee_Id`
now takes a parameter named for what it is — `subjectEmployeeId` — and the architectural test allows it precisely
because it is not the caller's identity.

**One behaviour changed, deliberately.** `ParentServiceImpl.getChildFees` called
`studentFeeService.getStudentFeesByStudent(studentId, null)`, and the callee compared `student.getSchoolId().equals(null)`
— always false, so the parent's fee list was always empty. The tenant now comes from the context, the check compares
the student's school with the caller's, and the lookup is school-scoped in SQL. This is a fix, not a regression, and it
is recorded here because it changes what a parent sees.

**One inconsistency was closed.** A cross-tenant refusal was answered **400** on the write path (`IllegalArgumentException`
through the global handler) and **403** on the read path (the controllers catch it explicitly). 23 refusals now throw
`TenantAccessDeniedException`, which extends `AccessDeniedException`, so both answer 403. `SchoolController.getById`
needed an explicit `AccessDeniedException` catch because its `catch (Exception)` would otherwise have turned the
refusal into a 404.

**What `C-2` does not close.** `C-2` gives every service one way to learn the tenant; it does not give every
*query* a school predicate. That gap was `C-3`, and `C-3` has since closed it on this branch — see the card below.
`TEN-01` is therefore **partly** closed by `C-2` alone: the tenant is unambiguous and cannot be named wrongly, but
the reads it governs still reached across schools until `C-3`.

**C-3, executed 2026-11-01 — the tenant reaches the SQL.**

**The rule.** Every declared repository read must name the tenant, or be listed in
`RepositoryTenantScopeArchTest` with the reason it is not a leak. `C-2` made the tenant available to every query;
`C-3` is ADR-020's second layer — the tenant is a *predicate*, not a value the service might remember to pass on.

**Measured, with the same instrument on both sides.** Run against `8981842` (the build that ended `C-2`) the scan
reports **170 declared reads, 115 carrying the tenant, 55 carrying none**; the test is red there with **37 offences**
— 55 minus the 18 that are global by design. Run against this branch: **168 reads, 150 carry the tenant, 18 do not**.
The number is printed by the test on every build, so the roadmap figure is recomputed rather than transcribed. The 18
left are 12 on `SchoolRepository` (that table *is* the tenant, so a query on it has no other tenant to leak), 4 on
`UserRepository` (two because `uq_users_username` is global by V3:34 and login must stay a tenant-free lookup, two
because the employee association already pins the school), and 2 on `ParentRepository` (`uq_parents_email`, V3:38, is
global by decision D-2 — a parent deliberately links children across schools).

**What changed.** 17 repository interfaces, 13 services, 22 call sites. The reads that took a bare `studentId`,
`teacherId`, `classroomId`, `examId`, `studentExamId` or `parentId` now take the school through the owning
association — `findByStudentExam_Student_School_IdAndStudentExam_Id`, `findByClassroom_School_IdAndClassroom_Id`,
`findByStudents_School_IdAndStudents_Id` — because a `teacher_id` of 4 is a different person in every school.
`UserRepository` and `EmployeeRepository` lost their global `findByEmail`/`existsByEmail`/`findByUserId`/`findByRole`
forms; `TeacherAssignmentRepository` was rewritten whole; `TimetableSlotRepository` went from empty to two scoped
reads. Removed outright, not rewritten: `EmailLog.findByToEmailAndDeliveryStatus…` and
`SmsLog.findByToPhoneAndDeliveryStatus…` (each had a scoped sibling and no caller),
`TeacherAssignment.findByAcademicYear` (a true duplicate of the scoped method that already existed),
`TimetableRepository.findAll()` as used by `getTimetableForTeacher` (a platform-wide walk in Java that any
authenticated caller could trigger), and `StudentRepository.findAllById(ids)` in
`ParentStudentServiceImpl.getChildFees`, which is now `findBySchool_IdAndIdIn`.

**One behaviour changed, deliberately.** User and employee email uniqueness moved from *global* to *per school*,
which is what `uq_users_school_email` (V3:41) and `uq_employees_school_email` (V3:53) actually promise. The global
check was both a stricter rule than the schema's and a cross-tenant oracle: it told school A that school B had
already registered a mailbox. `verify-c3-scoped-reads.sh` pins both halves — the same address now registers in two
schools and is still refused twice inside one.

**Verified.** 87 tests green (`86 + 1` new arch test, JDK 21). The packaged jar boots against an empty database:
Flyway applies V1→V5 and the context starts in 6.3s, which is what proves the new derived property paths resolve —
Spring Data parses them at bean creation, and the `@WebMvcTest` suite excludes JPA, so no unit test can catch a bad
path. That was checked rather than assumed: `findBySchool_IdAndNotARealProperty` added to `StudentRepository` makes
startup abort with `PropertyReferenceException: No property 'notARealProperty' found for type 'Student'`. Live: C-1
29/29, C-2 32/32, C-3 25/25. **Control:** the same C-3 script against the pre-C-3 build on port 8081 fails 6 checks,
headed by `school B naming school A's classroom finds no timetable (expected 404, got 200)` — a live cross-tenant
read, on a route (`GET /timetables/classroom/{id}`) that has no ownership guard and so depends entirely on the query.
The generated SQL was captured with `JPA_SHOW_SQL=true`:

```
select t1_0.id, … from timetables t1_0 join classrooms c1_0 on c1_0.id = t1_0.classroom_id
where c1_0.school_id = ? and t1_0.classroom_id = ?
```

**What `C-3` does not close.** Three things, and the first is the reason this is not the end of ADR-020. (1) The
tenant predicate here travels through *joins* — `c1_0.school_id` — because V2's `timetables.school_id` column is
still NULL: no Java entity maps it, so the application never writes it. Measured on the verification database: all
nine V2 `school_id` columns exist, and the timetable row created by this run has `school_id = NULL`. That is `C-7`,
and it matters for more than tidiness — a composite FK whose column is NULL is not enforced (Postgres MATCH SIMPLE),
so V5's same-school constraint is currently bypassable by the application's own inserts. **(1) is since closed by
`C-7`, and the closing is measurable, not asserted:** the check C-3 could only make per-table now reports
`rows with a NULL tenant, in any tenant table, platform-wide: 0`, `timetables` is one of the ten tables the live run
proves "written by school A, and by nobody else", and `fk_attendance_slot_same_school` — which could not have been
written while `attendances.school_id` was nullable, because a NULL column satisfies a composite FK — exists. (2) `findById`, `findAll`,
`findAllById` and `delete` are inherited by every repository and carry no tenant; this scan sees declarations only,
so they are `C-4`'s, not because the test missed them but because an inherited method is not a declaration. (3) A
school predicate is not an authorisation decision: `C-5` still owns deny-by-default on the endpoints themselves.

**C-7, executed 2026-11-01 — the tenant stops being a column and becomes a key.**

**The rule.** ADR-020's third layer: the database refuses a row whose tenant disagrees with the tenants of the rows
it points at. Layers 1 and 2 shipped as `C-2` and `C-3`, and both are politeness — if the code is wrong, nothing
downstream notices. This layer is the one that survives the next developer.

**Why it was written as a card before it was written as SQL.** Stage 2 had the correct DDL in §5.1 and chose not to
ship it: a `SET NOT NULL` on a column no entity maps is an outage on the first row written after the deploy. The
verification run proved the reasoning was sound in a way Stage 2 could not have — `POST /attendance` was, in the code
as it stood, *unable* to write a row at all (below), so a NOT NULL there would not have degraded the feature, it would
have deleted it.

**What changed.** Nine entities carry the canonical block (`@JsonIgnore` + `@ManyToOne(fetch = LAZY, optional = false)`
+ `@JoinColumn(name = "school_id", nullable = false)`), and 11 create paths across 8 services assign it. The assigned
value is `TenantContext.schoolId()` — the claim — not `exam.getSchool()`-style inheritance from the parent row. That
distinction is the whole card: a tenant copied from the parent satisfies the NOT NULL while making the same-school FK
vacuous, because the FK then compares a value to the row it was copied from and can never disagree. `parent_student`
has no entity, so its tenant is materialised by `trg_parent_student_tenant_key` (`BEFORE INSERT OR UPDATE OF student_id,
school_id`), which derives from the student, raises if the student is missing, and **raises** on a hand-written row that
disagrees with its student rather than silently overwriting it — re-deriving a conflicting value would demote the column
from a key to a cache.

**The migration is V6, and the reason is a number in the repository rather than a number in a database.** Only `V1`–`V5`
are committed (`git ls-tree HEAD` on `db/migration/`, and `git log --all --diff-filter=A` shows a single commit,
`231bdab`, ever adding a migration file), so `V6` was the next free slot — and `C-8`, which §5.1 and §9 had numbered `V6`
for the join tables, moves to `V7`. What the card originally wrote ("this branch's history is already at v7") is not
true of the branch, and the correction is worth keeping because the *hazard* is real even though the database that
showed it was disposable and local. For a while the file was named
`V7__require_the_tenant_key.sql`; a local database migrated by that draft jar did report `now at version v7`, and when
the draft was renumbered the same database refused the pending `V6` under Flyway's default `outOfOrder=false`. A
later-pending earlier number is a red startup nobody can attribute to the change, and on a shared or long-lived
database it is a real failure mode — it is just not the one this commit ships against. The other finding from that same
draft run stands: `mvn -DskipTests package` without `clean` kept
`target/classes/db/migration/V7__require_the_tenant_key.sql` after the file was renamed in the source tree, so the
packaged jar carried a migration the repository no longer had; Flyway applied V6, then the ghost, and aborted on
`42710 trigger "trg_parent_student_tenant_key" already exists`. `mvn clean package` produces the correct jar and CI is
unaffected (`mvn -B verify` on a fresh checkout), but `package` is a lie without `clean` and that is a debt row.

**V6, counted at the database rather than in the file.** Ten idempotent backfills, a ten-clause assertion that names
the table it refused to flip, ten `SET NOT NULL`, one trigger, four `(id, school_id)` candidate keys and **seven** new
same-school FKs, then V4's and V5's index rules re-run because a guard checked only at the moment it happens to be true
is not an invariant. Measured on the migrated database: **39** tables, **35** integer `school_id` columns of which
**33** are NOT NULL — the two that are not are `email_logs` and `sms_logs`, exempt in writing because `EmailLogService`
and `SmsLogService` are interfaces with no implementation and no writer, and a NOT NULL on a column no code path can
fill is not a control either. `schools.school_id` is excluded from the count entirely: it is a `varchar(20)` business
key, not a tenant FK. **141** foreign keys in total, **18** of them same-school pairs (11 from V5, 7 from V6), **45**
unique constraints, **175** indexes.

**Verified.** 89 tests green (JDK 21, `mvn -B verify`). A fresh disposable database applies V1→V6 and the context
starts with `spring.jpa.hibernate.ddl-auto=validate`, which is the check that the nine new mappings agree with the DDL
column by column. Live: `verify-c7-tenant-key.sh` **66 passed / 0 failed / 0 skipped** — school A writes a whole term
through its own endpoints (16 entities), then each of the ten tables is checked two ways ("written by school A" *and*
"and by nobody else"), then ten `UPDATE … SET school_id = NULL` probes in rolled-back transactions, then four
cross-tenant writes and the SQL itself. `C-1` 29/29, `C-2` 32/32 and `C-3` 25/25 re-run green on this build, and
`C-3`'s own measurement moved from 168/150 to **169/151** because this card added a scoped read
(`TimetableSlotRepository.findByIdAndSchool_Id`) — the two arch tests are counters that cannot be edited to match a
claim. `EntityTenantColumnArchTest` prints `C-7: 35 tables carry an integer school_id, 36 entity classes cover 34 of
them, 1 have no entity and a reason`, and fails on a stale allow-list entry as well as an unexplained table, with a
`hasSizeGreaterThanOrEqualTo(30)` floor so a regex that stopped matching cannot turn red into green.

**Two controls.** (1) Deleting `Result`'s `school` field — the faithful edit is a rename of the join column to
`org_id`, not removal of the field, because removing it stops compilation (`cannot find symbol: setSchool`) and a
build error is not a test failure — turns the arch test red naming `Result maps table results, which carries
school_id, without mapping the column`. (2) Pointing `Attendance`'s join column at `org_id` on a live server turns the
C-7 script red at exactly `attendances: none -- the endpoint never wrote one`, which is the check that the *write* path,
not only the schema, depends on the mapping. Both restored, both green, and the files `touch`ed afterwards because an
older mtime makes Maven silently skip recompiling a restored source.

**Two vacuous checks, found by breaking them.** §3's "every row belongs to school A" also passes on an empty table, so
it now asserts `MINE > 0` first; and the §5 NULL probes "accepted" three tables whose rows the application had never
written — an `UPDATE` against zero rows affects zero rows and raises nothing. Each probe now refuses to run unless a row
exists, with the comment that a probe against an empty table passes without testing anything, which is worse than a
failure.

**Three things this card did that were not on it.** All three are pre-existing defects the verification walked into,
each proven from the server log rather than inferred, and each is a smaller fix than a follow-up card would be:
`POST /student-exams` and `POST /fee-payments` returned **500** with `LazyInitializationException` (`Exam#1` /
`Student#1`, "no Session") because `mapToResponse` walked a lazy proxy after `save()` outside a session — now
`@Transactional` on those create paths, and on `ResultServiceImpl` for the same shape. `POST /attendance` could never
have written a row: the service read `request.getTimetableSlotId()` and dropped it against a NOT NULL column, with a
TODO in its place; resolving the slot tenant-scoped both fixes the endpoint and makes
`fk_attendance_slot_same_school` expressible, which is why that FK is in this migration rather than deferred. And a
cross-tenant `POST /timetables` answered **409**, not 403, because `uq_timetable_classroom_year` is platform-wide and
is reached before the tenant rule — `GlobalExceptionHandler` now recognises a `*_same_school` violation and returns
403 `That record belongs to another school.`, with a test that the constraint name and the table name do not reach the
response body.

**What `C-7` does not close.** (1) Inherited reads: `findById` is called from 155 sites across services and
controllers and `getById` from 6, and `RepositoryTenantScopeArchTest` cannot see any of them because it scans
*declarations* — an inherited method is not a declaration. A wrong-tenant id is still loaded before it is checked;
level 3 constrains what may be written about the row once loaded, which is a different failure from a caller reading
school B's record. That is `C-4`. (2) Deny-by-default on the endpoints is `C-5`. (3)
`invoices` and `receipts` map and write `school_id` but never assign `student` (`InvoiceServiceImpl`,
`ReceiptServiceImpl`), so their V5 same-school keys are vacuous for a reason that has nothing to do with tenancy —
that is FIN-04's missing linkage, and it belongs to the financial package. (4) The bulk attendance path now enforces
"the student is in the slot's classroom" for every row of the register, which is a rule the previous code commented out
rather than applied — if the product ever wants a makeup register recorded against a slot the student does not attend,
that guard is the line to argue about, and nothing here records the decision.


**C-4, executed 2026-11-02 — the row a caller reads by id is the row that caller owns.**

**The rule.** ADR-020 level 2, finished where a declaration scan cannot reach. `C-3` scoped every *declared*
repository query; `findById` is not declared by any repository, it is inherited by all of them, so 155 service call
sites went on loading another school's row by id and then either trusting it or comparing its school by hand. `C-4`
moves the ownership test into the inherited method itself: a repository whose entity implements `TenantOwned` extends
`TenantScopedRepository`, which pairs `findByIdOwnedBy(id, schoolId)` with `findByIdAnyTenant(id)` and overrides
`findById` to choose between them on `TenantContext.readsAcrossTenants()`. The tenant is the one the authentication
filter already verified, so a caller cannot pass a different one, cannot pass none, and cannot forget to pass it.

**What changed.** `TenantScopedRepository` and the `TenantOwned` marker; 33 repositories now extend the base and 3
deliberately do not, counted by `TenantScopedReadArchTest` on every build — `SchoolRepository` and
`DashboardStatisticsRepository` serve the tenant root table itself, and `ParentRepository` serves a row with no single
owning school, whose tenancy lives in `parent_student` and is keyed by `C-7`'s trigger. `prints C-4: 33 repositories
serve a tenant-owned entity and every one of them scopes findById by the claim; 3 serve the tenant table itself or a
row with no single owner`. A wrong-tenant id resolves to nothing, so the answer to school B naming school A's row
stopped being a filtered empty list and became 404.

**The 104 hand-written comparisons, sorted into three buckets instead of one.** With the load scoped, the services
still carried their own school tests; a build (and a security finding) that calls all of them duplication would have
deleted the ones that matter. They were classified:

- **dead duplicate** — the comparison re-tests the row the preceding scoped `findById` already filtered. 83 of them,
  across 27 service implementations, deleted. The reason is not tidiness: the bootstrapped platform owner carries its
  own SYSTEM `school_id`, so `!row.getSchoolId().equals(TenantContext.schoolId())` refuses the one caller the
  mechanism lets through, which is exactly how `subject` and `student` came to answer 403 to `SUPERADMIN` during this
  card's own verification.
- **attachment guard** — the comparison tests a row being *attached* to the one being written (a pupil's classroom, a
  fee's student, an exam's subject). **Kept.** The platform owner reads across tenants, so a guard on the attached row
  is the only thing between a straddling row and the composite FK that would reject it. 21 comparisons remain and
  every one is this bucket or the next.
- **uncovered load** — the row came from a call the base does not scope, so the comparison is the only control:
  `SchoolRepository` (not scoped), `ParentRepository` (not scoped), `UserRepository.findByEmployee_Id` (tenant-free by
  design, since a username is the key), and `StudentFeeServiceImpl.findById`, which tests the *nested* student the
  scoped load of the fee does not cover. Kept, and each is a candidate for a scoped read rather than a hand test.

**Verified.** 101 tests green at the point the card closed; the same suite is 104 on the jar this section's live
numbers were taken from (the audit-listener work and the principal-attribution test landed alongside). Live:
`verify-c4-owned-reads.sh` **118 PASS / 0 FAIL / 17 SKIP** — 22 probes over the 22 controllers that declare a by-id
read, each probe asking school B for school A's id and then asking the platform owner for the same id. The 17 SKIPs
are the by-id routes whose `@PreAuthorize` does not admit `SUPERADMIN`; they record a refusal *by the role layer* and
explicitly do not claim the tenant filter spoke. `C-1` 29/0, `C-2` 32/0/0, `C-3` 24/0/1 and `C-7` 65/0/1 re-measured
on the same jar, after three of those scripts had their expectations changed — see the next paragraph — and `C-3`'s
counter now reads 181 declared reads / 158 carrying the tenant / 23 allow-listed.

**A status code moved, and the suites had to say why.** Before `C-4`, school B naming school A's id was loaded and
then refused by a hand comparison: 403. Afterwards the load itself answers nothing: 404. Three verification scripts
asserted 403 literally, so each gained a `refused()` helper that accepts 403 *or* 404 and still rejects a 200 that
carries somebody else's row, with the reason written into the helper's comment. `C-7`'s straddle check additionally now
proves the database key exists (`SELECT count(*) FROM pg_constraint WHERE conname = 'fk_timetable_classroom_same_school'`)
because the refusal it used to observe had migrated from the load to the constraint. This is a widened *assertion about
the same security property*, verified by re-running the pre-`C-4` jar against the widened script and watching it fail
where it should; it is not a failing check downgraded, which §6 forbids.

**The control this repository requires of itself.** `ClassroomRepository` was broken twice. At build time the guard
went red on both halves of the mechanism — `repository.ClassroomRepository calls findByIdAnyTenant, which reads any
school's row` and `ClassroomRepository re-declares findById, which overrides the tenant filter with a method body this
scan cannot read` — and live, against a server running the broken jar, the suite reported **117 PASS / 1 FAIL**, the
one failure naming exactly `classroom: another school reading the same id was answered 200, not 403/404` and nothing
else. Restored, re-verified green.

**The lazy proxies this card exposed.** Scoping the load made several by-id reads answer 200 for the first time, and
some of them then threw: `open-in-view=false` means any getter other than `getId()` on a lazy to-one outside a session
is a 500. `EntityLazyAccessorArchTest` now polices the entity side — `C-4: 41 derived accessors read a lazy
association, 17 of them ask whether it is loaded first (75 lazy to-one fields and 53 @JsonProperty accessors across 38
entity sources)` — and the live suite shows no 500 on any by-id read it walks. The service side is not all closed:
nested dereferences remain in `ResultServiceImpl` and `ParentServiceImpl`, recorded under debt.

**What `C-4` does not close.** (1) It scopes reads by id. It does not authorise them — `C-5` owns the guard on the
endpoint, and a scoped load behind an unguarded route is still a route anybody authenticated can reach. (2) It does
not make the *write* path tenant-safe on its own: an insert or update that takes a tenant from a request field rather
than the claim is outside this mechanism, which is why the 96 lines this card sorted matter. (3) `getById` and
`getReferenceById` are refused by the arch test rather than scoped by the base — a service that reaches for them on a
tenant-owned entity fails the build, and that is the right answer, because both hand back a proxy without looking at
the tenant. (4) The platform-owner branch is deliberately broad: `SUPERADMIN` reads any school's row by design, so the
guard on `SchoolController`'s writes, not this file, is what keeps that role from being a hole in the other direction.



**C-5, executed 2026-11-02 — a handler that declares no guard does not answer anybody.**

**The rule.** Deny by default means the *absence* of a decision is a refusal. Before this card, 23 of the handlers in
`com.gms.controller` carried no `@PreAuthorize`, and the URL layer admitted any role its perimeter matched — so an
unannotated handler was reachable by every authenticated caller of that prefix. That is the inherited-delete hole
`C-1` closed by hand for six controllers; this closes it for the rest and keeps it closed on the next handler that
ships without a guard.

**Why the guard state is read off the compiled handler, not the source text.** Three reasons, each measured: the two
annotations appear in either order in this package, so a line-window heuristic reports whichever happened to be nearer;
the literal string `@PreAuthorize` occurs inside javadoc (`SubjectController.java:21`, `ClassroomController.java:21`), so
a text scan counted 212 guards where 210 existed; and both `@PreAuthorize` and the mapping annotations are
`RUNTIME`-retained, so the bytecode state is the same state Spring's handler mapping reads when it registers the
endpoint. A test that reads the source can pass while the registered handler disagrees.

**What the scan reports.** `DenyByDefaultAuthorizationArchTest` prints
`C-5: 236 handlers, 234 guarded, 2 deliberately public` and
`C-5: guards name 6 distinct authorities: [ACCOUNTANT, ADMIN, PARENT, STUDENT, SUPERADMIN, TEACHER]`. The two public
handlers are named, not pattern-matched: `AuthController.login` and `HealthController.ping`. Four checks make the
counting non-vacuous rather than merely optimistic: a floor of 200 handlers so a broken pattern cannot report zero
findings as green; the partition `guarded + deliberatelyPublic == handlers`, so a handler cannot fall between the two
lists; no guard may be declared on the *class*, because a class-level `@PreAuthorize` is the widest possible
over-grant; and every authority any guard names must be a role the application can actually mint — which is the seam
`C-6` closed.

**The control was run, and it went red for the right reason.** Deleting `@PreAuthorize` from
`AnnouncementController.createAnnouncement` and re-running the test fails with
`C-5: 236 handlers, 233 guarded, 2 deliberately public` and names that one handler; restoring it returns the print to
`234 guarded` and the suite to green. A guard-counting test that has never been observed to fail is only evidence that
annotations exist somewhere in the package, so this is the check that makes the count mean what it says — and the same
deliberate-failure method is now applied to every card in this stage.

**The other half of deny-by-default is the URL layer, and it disagreed with the method layer.** First-match-wins in
`SecurityConfig` meant `/timetables/**` admitted `ADMIN`, `SUPERADMIN` and `TEACHER` before the self-service matcher
could be reached, so a `STUDENT` calling `GET /timetables/student/me` was refused by the perimeter even though its
handler grants exactly that role. The self-service matchers now precede the admin matchers. The same ordering was
shadowing `PARENT` and `TEACHER` reads, which is the mechanism `SEC-06` had been describing as "grants shadowed" — that
framing is now historical.

**Live.** `verify-mvp-e2e.sh` walks the role layer against a running server and asserts the refusal is a 403 with the
documented body rather than a 500 or a silently-empty success: a `TEACHER` posting a pupil is refused and the pupil list is
byte-for-byte unchanged afterwards; a `TEACHER` deleting a subject is refused *by the method guard*, which the script
proves by noting the URL layer admits `TEACHER` to `/subjects/**`; and a tampered token is a refusal, not a weaker
principal.

**What `C-5` does not close.** A guard is a gate, not a tenant predicate: `hasAuthority('TEACHER')` says a teacher may
reach the route, not which school's row it may read — that is `C-2`/`C-3`/`C-4`'s subject. The rule is about a guard
being *present*, not about it being *right*: a route wide open to a role the URL layer also admits passes every check
here, which is why the authority-vs-`RoleEnum` check and the `SEC-06` self-service ordering are argued separately above.
And the scan's universe is the compiled package: a controller the component scan does not reach satisfies this test and
serves nothing, and only a live route inventory can tell those apart.


**C-6, executed 2026-11-02 on the backend, closed on the frontend the same day — a role the application can mint is a
role a caller can use.**

**The finding, stated accurately.** 39 `@PreAuthorize` guards named `ACCOUNTANT` — the whole money module — while
`RoleEnum` declared five constants and none of them was `ACCOUNTANT`, no code path issued it, and the frontend did not
know the word. The practical effect was not a hole but a wall: the accountant persona in the PRD could not exist, so
`/fees`, `/invoices`, `/receipts`, `/fee-payments`, `/financial-reports` were reachable by `ADMIN` and `SUPERADMIN`
only. `SEC-17` described the mismatch; `SEC-16` described the unreachable role.

**What changed on the backend.** `ACCOUNTANT` is a `RoleEnum` constant, which makes it issuable and makes those 39
guards satisfiable instead of permanently closed; no migration was needed because `user_roles.role` is a plain
`varchar(30)` with no Postgres enum and no `CHECK`, so the vocabulary is enforced in code and policed by the `C-5` scan
rather than by the schema. `/auth/self-register` stopped minting the string `"EMPLOYEE"` — a role no guard names and no
enum declares — and now routes its role through `UserService.authoriseRoles`, the same validator `/users/create` uses,
which rejects a code outside `RoleEnum` *before* it consumes a username. Two handlers that had no guard at all got one
(`changePassword` for any authenticated caller, `selfRegister` for `ADMIN`/`SUPERADMIN`), which is why `C-5` counts 2
public handlers rather than 4. The `roles` claim is written sorted and null-filtered at the point of signing, so a
client reads it as a set rather than guessing that position zero means anything.

**What changed on the frontend.** `ACCOUNTANT` is in the role vocabulary, the label map, the sidebar sections, the
role-precedence order and the landing-route table, so the role that is minted is a role that can log in and see
something. Concretely: an accountant lands on `/admin/fees`, whose only load call is `GET /api/v1/fees` — a route the
backend admits `ACCOUNTANT` to — and the Add/Edit/Delete controls on that page are hidden for a role whose writes the
server refuses, because the API client toasts on every 403 and a button that cannot work is a defect the user sees.
Two route-layer defects came with it: `ProtectedRoute`'s deny path now renders a real `/unauthorized` screen carrying the
denied path instead of an empty layout shell, and the catch-all that used to sit outside the layout — which could never
win against the layout branch — moved inside it, so an unmatched URL redirects to the caller's own landing page. Where a
token carries two roles, the sidebar now composes the sections of every role below the landing role, so an
accountant-teacher is not locked out of the classes they teach.

**Deliberately not done.** `ACCOUNTANT` sits *between* `ADMIN` and `TEACHER` in precedence: it is school-wide (the fee
catalogue spans the school, not a class), so below `TEACHER` would land a money role on a dashboard with no finance
link, and above `ADMIN` would let a read-only role own the write screens. `SecurityConfig`'s `DASHBOARD` matcher still
admits only `ADMIN` and `SUPERADMIN`, so an accountant has no `/dashboard/**` screen; `GET /financial-dashboard` is the
natural route for one and no page exists to consume it. `apiSlice.js` has no endpoints for the seven finance reads that
`ACCOUNTANT` is allowed, so the invoice and receipt pages in the PRD persona stay unlinked. Two latent
order-dependencies survived in files this card did not own — `authService.getUserRole()` and `authSlice.selectUserRole`
both return `roles?.[0]`, which with a sorted claim is the alphabetically-first role rather than the most privileged;
neither is on a live path, and both should delegate to `getPrimaryRole`.

**What `C-6` does not close.** The accountants' *writes* do not exist as a decision: every `POST`/`PUT`/`DELETE` on the
finance controllers is `hasAuthority('ADMIN')`, which is a policy choice inherited from the pre-`ACCOUNTANT` code and
not a consequence of this card. If the PRD's "records a fee payment" is meant literally, an accountant must be able to
write `fee-payments`, and that is a product decision with a tenant consequence, not a guard to add quietly.


**C-8, executed 2026-11-02 — the row records who wrote it, and the answer comes from the token, not from a lookup.**

**The rule.** MVP feature 11 is "basic audit logging", and the cheapest true version of it is that every write stamps
its author. `created_by` is a foreign key to `users(id)`, so the whole feature rests on one question: *which row is the
caller?* Ten years of wrong answers to that question are worse than no answer, because an audit trail that names the
wrong person is evidence against an innocent staff member.

**What changed in the schema.** `V7__record_who_wrote_the_pupil.sql` adds `created_by`/`updated_by` to the four tables
that carried timestamps but no actor — `students`, `classrooms`, `subjects`, `student_admissions` — and the eight
`REFERENCES public.users(id)` keys that go with them. The keys are plain, matching the NO ACTION the V1 baseline already
uses for the other 31 actor columns; `ON DELETE SET NULL` would have silently erased the trail of a departed employee,
which is the opposite of what an audit column is for. Nothing is backfilled: no row in those four tables can be
attributed from data that exists today, and an invented author is worse than an absent one.

**What changed in the mapping.** `AuthorshipEntity` is a `@MappedSuperclass` that declares `createdBy`/`updatedBy`
once, and `BaseAuditEntity` now extends it rather than duplicating them, so the 28 entities that already audited through
`BaseAuditEntity` and the 5 that could not — `School`, `Student`, `Classroom`, `Subject`, `StudentAdmission`, each of
which names its timestamps `created_at`/`updated_at` and would have acquired a second timestamp pair by extending
`BaseAuditEntity` — share one definition. That is 33 of the 36 entity classes stamped by the listener. The count is
stated because it is the honest one: `Employee` and `User` already carry a `created_by`, but theirs is a business fact
(who hired this person, who opened this account) assigned by the service, not an audit trail, so they do not extend the
superclass; `AcademicYear` is the only entity with no author at all. At table level the same card takes V1's 31 tables
with an actor column to 35 of 38, the three exceptions being `academic_years` and the two join tables `parent_student`
and `user_roles`. The JSON the API returns gained
`created_by_id`/`created_by_name`/`updated_by_id`/`updated_by_name`, where the name is read only when the association is
already initialised, because `open-in-view=false` turns a naive getter on a lazy `@ManyToOne` into a 500 on the read
path.

**The mechanism, and the bug it found.** `AuditingEntityListener` fires *during the flush*, so the auditor may not issue
SQL. The first version resolved the caller with `userRepository.findByUsername(principal)`, which triggers Hibernate's
auto-flush — and so it re-entered a flush that was already in progress. `mvn -o clean package` was green and the app
booted; 22 live write checks then failed with a 500 on every `PUT`/`PATCH` for a school, a pupil, a classroom and an
admission, and the server printed `org.hibernate.AssertionFailure: collection was processed twice by flush()` under a
`StackOverflowError`. (That boot log has since been overwritten by the green run it produced, so the string is recorded
here rather than attached; it appeared on every one of those writes.) The affected entities own no collection — none of
`School`, `Student`, `Classroom`, `Subject`, `StudentAdmission` declares one — which is what makes the assertion worth
reading carefully: the re-entered flush is the *session's*, not the written row's, and the persistence context of a
school update can already be holding `Parent.students` or `Timetable.slots` (`Timetable`'s is `FetchType.EAGER`, so it is
loaded on any timetable read). Any audited write in a session that has touched one of the three collection-owning
entities is therefore exposed, which is why this is a mechanism defect and not a defect in the four new columns. The fix
removes the query rather
than reverting the annotation: the JWT now carries `uid` (the `users.id` the token authenticates), and the auditor
answers with `getReferenceById(uid)`, which returns a managed proxy and makes no statement at all. `uid` also settles
BKL-08 in the right direction: the `empId` claim is an *employee* id for staff but the *user* id for the platform owner,
so attributing through it could name a different tenant's employee as the author of a row they never touched. The
auditor is now pinned to the principal and the attribution test asserts `findByEmployee_Id` is never called.

**Live.** `verify-mvp-e2e.sh` F11 is 20 checks that read the actor columns back off a pupil, an admission and a
classroom after each write, assert the author is a real account *in the same tenant as the row it wrote* and that it is
not the platform owner reaching in from outside, and the feature register now marks feature 11 `PROVED (20 checks ran,
0 skipped)`. The whole register is `11 of 11` on this build — 294 assertions, 0 failures, 0 skips — which is the first
time every frozen-MVP feature has been demonstrated rather than asserted. The unit and architecture suite is 106 tests
green (was 104 before this card's two attribution tests), and the Stage 3 suites re-run against a V7 database are
unchanged: C-1 29/0, C-2 32/0/0, C-3 24/0/1, C-4 118/0/17, C-7 65/0/1. The `c3` and `c7` skips are this run's harness,
not the build: both transcripts print `SKIP generated-SQL/INSERT check (GMS_SQL_LOG is not set …)`, because the jar was
booted without `JPA_SHOW_SQL=true`. Everything was re-measured on a rebuilt jar after the V7 header was corrected — 7
`Migrating schema` lines, V1→V7 applied, 294/0/0 again.

**The control, stated as the check it still owes.** `C-5` was proved non-vacuous by deleting a guard and watching the
scan name it; `C-8` has the same control and has not run it yet. Dropping `@CreatedBy` from `AuthorshipEntity` must
turn F11 red on all four tables, and restoring it must turn it green, before the count of 4 is evidence about the
annotation rather than about a value the seed happened to write. It is recorded as owed, not as done.

**What `C-8` does not close.** (1) The five hard-delete paths remove the row *and* its trail, so "who deleted this"
has no answer — that is a soft-delete decision, and it is a product decision. (2) No API surfaces the trail to a
school: the columns exist and the responses name the author, but there is no audit view, which is what PRD feature 11
would have to become before a school could use it. (3) A write with no authenticated caller — the bootstrap runner and
any scheduled job — legitimately leaves `created_by` NULL, and nothing yet distinguishes "system wrote this" from
"nobody looked". (4) `updated_by` records the last writer, not the sequence of writers; a real history needs a
separate event table, which is not MVP scope.


### Stage 4 — The MVP vertical slice

**Goal:** the frozen MVP in [PRODUCT_REQUIREMENTS.md §8](PRODUCT_REQUIREMENTS.md) works end-to-end for two tenants
at once.
**State:** Partly proved on the branch — the 11 frozen-MVP features are all live-proved (11 of 11, 294 assertions, 0
failures, 0 skips on a fresh V1→V7 database with two tenants); the stage's own gate, *bounded* lists, is open.
**Depends on:** Stage 3.

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

**`C-9` — migrations move out of the prod process.** `ADR-022` decided that in `prod` Flyway runs "as a deployment step
executed before the new version receives traffic"; Stage 2 shipped the `ddl-auto=validate` half and left
`application-prod.properties:15` (`spring.flyway.enabled=true`) as it was. It cannot be closed earlier than this stage,
and that is not a delay: flipping the property to `false` before anything else runs `flyway migrate` would leave a fresh
production database with **no schema at all** — a worse failure than the one it prevents. It lands in the same change as
the migrate step in `scripts/deploy.sh`, with `baseline-on-migrate=false` (`:16`) left exactly where it is.

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
| 1 Foundation | PRD + architecture + ADR-019…023 accepted | **Closed** — `a8748f7` | $0 | No |
| 2 Schema | migrate-empty-twice diff empty; `ddl-auto=validate` everywhere | **Executed, unmerged** — `231bdab` / PR #25, CI green, `BLOCKED` on `REVIEW_REQUIRED`. Criteria 2, 3, 6, 7, 8 met on the branch; 1 open; 4, 5 moved to Stage 5 | $0 | No |
| 3 Security & tenancy | cross-tenant suite green + one guard-deletion control red | **Executed on the branch** — `C-1`…`C-7` plus `C-8` are implemented and measured on `feat/stage2-flyway-baseline`: both base classes deleted; the tenant resolved once per request into `TenantContext` and taken from there by every service instead of from a parameter; the tenant travels in the query (37 unscoped declared repository reads fixed, 18 left and each allow-listed with the reason the schema supports it) and then in the key (nine entities map `school_id`, 11 create paths fill it from the claim, the ten deferred `SET NOT NULL` flips land as V6, `parent_student` is held by trigger, 7 more same-school FKs make a straddling row impossible); `C-4` puts ownership in the inherited `findById`, `C-5` makes an unguarded handler a refusal (236 handlers, 234 guarded, 2 named public) with the URL ordering fixed, `C-6` makes `ACCOUNTANT` a mintable role on both sides, and `C-8` stamps the author on 33 of the 36 entity classes through `AuthorshipEntity` + V7, taking the tables with an actor column from 31 to 35 of 38. Measured: **106 tests green**, live scripts **mvp-e2e 294/0/0, C-1 29/0, C-2 32/0/0, C-3 24/0/1, C-4 118/0/17, C-7 65/0/1**, a fresh database applies **V1→V7** and boots under `ddl-auto=validate`, and three deliberate-failure controls are red then green (`C-5` names `AnnouncementController.createAnnouncement` at 233 guarded; `C-7` names `Result`, and `Attendance`'s join column aimed at `org_id` refuses the write). `C-8`'s control is still owed | $0 | No |
| 4 MVP slice | journey end-to-end for two tenants, bounded lists | **All 11 MVP features proved live, stage still open** — `verify-mvp-e2e.sh` reports **11 of 11 PROVED with 294 assertions, 0 failures and 0 skips** against a fresh V1→V7 database and two tenants, so every frozen-MVP item from superadmin auth through the audit trail is now demonstrated rather than asserted. What keeps the stage open is the gate this row names: *bounded* lists. The search endpoint paginates server-side; the other list resources do not, so the work left in Stage 4 is pagination, sorting and filtering across them, not new features | $0 | No |
| 5 Volume + CI | 50k budgets met, checks required and enforced | Open | $0 (runner minutes only) | No |
| 6 Deployment prep | one deploy + one rollback + one restore observed | Open | ₹0 expected (Always Free) — **owner approval required before any paid resource** | **Yes** |
| 7 Qualification | matrix fully `VERIFIED`/`BLOCKED`, no promotion by authorship | Open | $0 | Yes |

**Reading this honestly:** Stage 1 is `Closed` on `master`. Stage 2 is not — its migrations, its verification
script and its Compose repair exist only on `feat/stage2-flyway-baseline` (`231bdab`, PR #25), and the branch cannot
merge itself: `master` requires one approving review with last-push approval, the founder cannot approve their own PR,
and the delivery objective's *"Never bypass branch protection using admin overrides"* rules out the way the last three
branches got in — which is why PR #25 is green on every check and still `BLOCKED`. So as of this writing `master`
still describes a project with **0 migrations and `ddl-auto=update` in dev**, and that is the state a fresh clone
receives. Stage 3 is now executed on that same branch — `C-1` through `C-8` are implemented and measured — and part of
Stage 4 is proved by the same run: the branch applies **seven** migrations, boots a schema that refuses a NULL tenant
and stamps who wrote each row, and answers 294 live assertions across two tenants without a single failure, while
`master` applies none and lets Hibernate guess — so the longer PR #25 sits unreviewed, the further the
merged description of this project drifts from the verified one. Nothing above Stage 1 is `Closed`. The roadmap is the plan; the evidence sections of `BASELINE_STATUS.md` are the proof. If the two
disagree, the proof wins and this table is wrong.
