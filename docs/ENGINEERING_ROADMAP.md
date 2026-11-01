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
   `BaseAuditEntity` puts on thirty-four tables. Nothing reads "rows created by user 41", so those
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
`C-7` lands the two `SET NOT NULL` flips that Stage 2 had to leave open, and `C-8` is B-4's join
tables, which only earn their keep once the code that reads `recipient_ids` reads them instead.

### Stage 3 — Security and tenant isolation

**Goal:** a principal can only ever reach rows belonging to the school its token asserts, and no endpoint is
reachable by inheritance.
**State:** Open. **Depends on:** Stage 2.

| # | Work | Closes |
|---|---|---|
| C-1 | Delete `AbstractCRUDController` / `AbstractCRUDService` and make every extendee declare its own endpoints (`ADR-023`). Measured live surface on 2026-11-01: 6 controllers extend it; `GET /{id}` is inherited-and-unguarded on 3 (`Parent`, `Student`, `Employee` — `Subject:64`, `Classroom:64`, `School:81` override it); `DELETE /{id}` is inherited-and-unguarded on **all 6**, because `EmployeeController:68` maps `/toggle`, which does not shadow `/{id}`; the comment at `EmployeeController.java:65-67` records that a hand-written `getById` was *deleted* precisely because the base class supplied one. That comment is the case for deleting the base class rather than guarding it. **Done 2026-11-01 on the branch**: both classes deleted, 25 service impls and all 36 controllers detached, the two frontend-called deletes re-declared tenant-scoped, three tenancy holes closed (`ParentServiceImpl.findBySchool` returned every platform's parents; `UserService.getById`/`deleteById` had no tenant predicate; `SchoolService.getById` did not exist). Measured: 33-test suite green, live script 29/29, control red on exactly 2 assertions, full suite 78/78 | `SEC-05` |
| C-2 | One tenant resolution path: the `schoolId` claim parsed once per request into a request-scoped context; services and repositories take it from there instead of a parameter that a caller may forget | `TEN-01…07`, debt #5 |
| C-3 | School-scoped repository methods for the 6 repositories that have none (`ExamSubject`, `Parent`, `Result`, `StudentExam`, `Timetable`, `TimetableSlot`) and for the 10 entities with no direct school link, via their owning aggregate | `TEN-*`, `DB-03` |
| C-4 | Put the ownership check where the JWT is verified, so a wrong tenant is 403/404 and not a filtered empty list | `SEC-06` |
| C-5 | `@PreAuthorize` on every mutating endpoint; deny-by-default verified by a mapping scan that fails the build on an unannotated handler | `SEC-04`, `OPS-*` |
| C-6 | Resolve the role vocabulary: 39 guards name `ACCOUNTANT`, which is in no enum, issued by no code and unknown to the frontend (`SEC-17`). Either the role exists and `RoleEnum` plus provisioning grow, or those guards collapse to `ADMIN` and the string disappears | `SEC-16`, `SEC-17` |
| C-7 | Finish what Stage 2 deliberately left open: map `school_id` on the ten entities that do not carry it (`Attendance`, `ExamSubject`, `FeePayment`, `Result`, `StudentExam`, `StudentFee`, `TeacherAssignment`, `Timetable`, `TimetableSlot`, `Parent`), set it in the services that create those rows, then land the two deferred `SET NOT NULL` flips as a V7 — including `parent_student`, whose insert path is Hibernate's `@ManyToMany` collection persistence (`Parent.java:41-47`) and so needs the column mapped before it can be required. A tenant column the application may leave NULL is a column, not a control | `ADR-020` level 3 completed, Stage 2 finding 2 |
| C-8 | §5.1's V6 as a migration and as code: `notification_recipients`, `announcement_targets`, `message_recipients`, per-recipient read state on `Notification`, and the repositories rewritten off `recipient_ids` | `DB-04`, `TEN-03`, moved from B-4 |

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
| 3 Security & tenancy | cross-tenant suite green + one guard-deletion control red | **In progress** — `C-1` done on `feat/stage2-flyway-baseline`: both base classes deleted, 33-test inheritance suite + live cross-tenant script (29 checks) green, deliberate-failure control red on exactly the guarded assertions. `C-2…C-8` open | $0 | No |
| 4 MVP slice | journey end-to-end for two tenants, bounded lists | Open | $0 | No |
| 5 Volume + CI | 50k budgets met, checks required and enforced | Open | $0 (runner minutes only) | No |
| 6 Deployment prep | one deploy + one rollback + one restore observed | Open | ₹0 expected (Always Free) — **owner approval required before any paid resource** | **Yes** |
| 7 Qualification | matrix fully `VERIFIED`/`BLOCKED`, no promotion by authorship | Open | $0 | Yes |

**Reading this honestly:** Stage 1 is `Closed` on `master`. Stage 2 is not — its migrations, its verification
script and its Compose repair exist only on `feat/stage2-flyway-baseline` (`231bdab`, PR #25), and the branch cannot
merge itself: `master` requires one approving review with last-push approval, the founder cannot approve their own PR,
and the delivery objective's *"Never bypass branch protection using admin overrides"* rules out the way the last three
branches got in — which is why PR #25 is green on every check and still `BLOCKED`. So as of this writing `master`
still describes a project with **0 migrations and `ddl-auto=update` in dev**, and that is the state a fresh clone
receives. Stage 3 has started — `C-1` is implemented and measured on the same unmerged branch — and nothing above
Stage 1 is `Closed`. The roadmap is the plan; the evidence sections of `BASELINE_STATUS.md` are the proof. If the two
disagree, the proof wins and this table is wrong.
