# GMS Architecture — modules, ownership, tenancy, contracts

**Status:** Canonical. **Written:** 2026-11-01 as the Stage-1 deliverable of
[ENGINEERING_ROADMAP.md](ENGINEERING_ROADMAP.md) (execution mandate Priority 1: *"establish the modular monolith"*).
**Owns:** module boundaries, dependency direction, data ownership, the tenancy mechanism, API contract standards,
and the index/constraint design sized for ≥50,000 records per school.
**Does not own:** defects (the registers in [audit/](audit/) and
[infrastructure/INFRASTRUCTURE_DECISIONS.md](infrastructure/INFRASTRUCTURE_DECISIONS.md)), scope and personas
([PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md)), or sequence (the roadmap). Where this file names a finding id,
the finding's text stays in the register that owns it — this file states the *design that closes it*.

## 1. The as-is, measured

Every number here comes from a command run against the tree, not from an earlier document. The structural counts are
re-measured on 2026-11-02 on `feat/stage2-flyway-baseline` after `C-1`…`C-8`; where a row moved from the `master`
baseline at `bcbb71c` (2026-11-01) the baseline value is named in the row. `C-8` moves several of them: it adds
`AuthorshipEntity`, `V7__record_who_wrote_the_pupil.sql` and the `uid` JWT claim, and each row it moved says so below.

| Surface | Measured | Command shape |
|---|---|---|
| Java files under `gms_backend/src/main/java` | **292** (280 at the `master` baseline; the count moved with `C-1`…`C-8` — `C-8` adds `model/entity/AuthorshipEntity.java`) | `find … -name '*.java' \| wc -l` |
| Packages | 11 top-level: `config` 7, `controller` 36, `enums` 3, `exception` 4, `model/entity` 39, `model/request` 41, `model/response` 38, `repository` 37, `security` 5, `service` 40 (+ `bootstrap` 1, `impl` 35), `util` 5 — **292 in total**: 291 across those directories plus `GmsApplication.java` | per-directory counts |
| Entities / tables | 39 files in `model/entity`: 36 entity classes plus `TenantOwned`, `BaseAuditEntity` and, since `C-8`, `AuthorshipEntity`; `EntityTenantColumnArchTest` prints `C-7: 35 tables carry an integer school_id, 36 entity classes cover 34 of them, 1 have no entity and a reason` | `ls model/entity`, `@Table(name=…)` |
| Repositories | **37** files — 36 interfaces plus the `TenantScopedRepository` base, which **33** of the 36 extend (`SchoolRepository`, `ParentRepository`, `DashboardStatisticsRepository` are the three exempt, each with a reason) | `ls repository` |
| Controllers | **36** files, all 36 with a class-level `@RequestMapping`, **none extending a base class** | `grep -l "@RequestMapping"` |
| Endpoint mappings | **236** | `grep -rhoE "^[[:space:]]*@(Get\|Post\|Put\|Patch\|Delete)Mapping" \| wc -l` |
| Endpoints returning an unbounded `List` | **89** `ResponseEntity<List<` | same grep in `controller/` |
| Endpoints returning `Page` or taking `Pageable` | **1 of 236.** `GET /api/v1/students/search` (`StudentController.java:98-110`) returns a `Page<StudentResponse>` with a server-side query/class filter, a `page`/`size` pair capped server-side and a whitelisted `sort`. `grep -rEn "Page<\|Pageable" src/main/java` still finds the original dead path — `UserServiceImpl.java:206` paginates users (`listBySchool`, declared at `UserService.java:16`) and no controller calls it — so 235 endpoints, and every list the frontend renders, remain unbounded. | `grep -rEn "Page<\|Pageable" src/main/java` |
| `@PreAuthorize` guards | **234** over 236 mappings | `grep -rhoE "^[[:space:]]*@PreAuthorize" src/main/java/com/gms/controller \| wc -l` |
| Secondary indexes declared in the model | **0** (`@Index`, `indexes = {`) | `grep -rE "@Index\|indexes *= *\\{" model/entity/` |
| Unique constraints declared | **1** (`Designation.java:25-28`) | `grep -c uniqueConstraints` |
| Entities carrying a tenant key | **34** with `@ManyToOne School` on `school_id`, 1 with a scalar `schoolId` (`School` itself, the display code at `School.java:23-24`), **1 with none** (`Parent`, global by `ADR-020`/PRD D-2). **Closed by `C-7` on 2026-11-01**: the 10 that had none are the nine this card mapped (`Attendance`, `ExamSubject`, `FeePayment`, `Result`, `StudentExam`, `StudentFee`, `TeacherAssignment`, `Timetable`, `TimetableSlot`) plus `Parent`, which stays global by design and carries the tenant on its `parent_student` link instead | per-file grep (`private School school`) |
| Repository interfaces with no tenant argument | **3 of the 36**, each with a recorded reason: `SchoolRepository` (that table *is* the tenant), `ParentRepository` (global by D-2) and `DashboardStatisticsRepository` (aggregate reads, owns no table). **Closed by `C-4`**: the other 33 extend `TenantScopedRepository`, so the tenant travels with the read rather than with a method name. The 6 this row used to count (`ExamSubject`, `Parent`, `Result`, `StudentExam`, `Timetable`, `TimetableSlot`) were a count of *unscoped method signatures*; five of the six now inherit a tenant-keyed `findById` — `ParentRepository` is the exempt one — and the test prints 181 declared reads with 23 allow-listed and each carrying the reason the schema supports | `grep -L "extends TenantScopedRepository"` |
| Flyway | **7 migrations on the branch** — `V1__baseline_current_schema.sql` … `V7__record_who_wrote_the_pupil.sql` in `gms_backend/src/main/resources/db/migration/` — and `flyway-core` is a real dependency at `gms_backend/pom.xml:54` (with `flyway-database-postgresql` at `:58`). What this row used to say — "configured in properties, **inert**: no `flyway-core` in `pom.xml`, no `src/main/resources/db/`, **0 `.sql` files**" — described `master`; it is false of this branch and `master` still carries no migration (`git ls-tree master -r \| grep -c db/migration` = 0) | `ls db/migration`, `grep flyway pom.xml` |
| `ddl-auto` | `validate` in `application.properties:33`, `application-dev.properties:26` and `application-prod.properties:13`; the value **`update` appears in no profile** | `grep -rn "ddl-auto" src/main/resources` |
| Services importing `ResponseEntity` | **65 of 76** service files (642 occurrences of the token across the tree) | `grep -rl "ResponseEntity" …/service \| wc -l` |
| Automated backend tests | **106**, 0 failures, 0 skipped (`mvn -B verify`, JDK 21) on `feat/stage2-flyway-baseline` at `C-8`'s close; 42 at the `master` baseline | the 16 `gms_backend/target/surefire-reports/TEST-*.xml` on this build; the run is recorded in `ENGINEERING_ROADMAP.md`'s Stage-3 per-card evidence blocks and its §8 status board. (`BASELINE_STATUS.md` §13.1 is the Stage-0 run of **36** tests, so it is not the citation for this number.) |
| Live MVP proof | **294 PASS / 0 FAIL / 0 SKIP**, and the script's own roll-up line prints **FEATURES PROVED: 11 of 11** — every MVP feature reports PROVED, none skipped — measured on a freshly migrated `V1`–`V7` disposable database seeded with two tenants. The per-card scripts on the same build: `verify-c1-tenancy.sh` 29/0, `verify-c2-tenant-context.sh` 32/0/0, `verify-c3-scoped-reads.sh` 24/0/1, `verify-c4-owned-reads.sh` 118/0/17, `verify-c7-tenant-key.sh` 65/0/1 | `bash scripts/verify-mvp-e2e.sh` against the running jar |
| Frontend | 29 pages across 16 `features/` directories; 5 dashboards; **0** test files | `find … -name '*.jsx'`, `find … -name '*.test.*'` |

**Reading of that table.** The application is broad and shallow: a great deal of surface, very little of it backed by
a mechanism. Three rows drove the whole architecture, and two of the three have since been closed by the mechanism
rather than by prose — which is the point of the table, so the reader sees both states. **0 indexes declared in the
model** against **≥50,000 records** was a capacity problem before it was a performance problem; it is now held in the
migrations rather than the annotations (`V4`–`V6` create the indexes; §5.3 counts 87 secondary `idx_*` indexes after
`V2`–`V5`), and Stage 5 still owes the `EXPLAIN` at 50k. **10 tenant-less tables** plus **6 unscoped repositories**
meant `BR-02` could not be satisfied by adding checks to services, because half the rows had nothing to check against:
`C-7` mapped the column on the nine that could carry it and `V6` made it NOT NULL, and `C-4` moved the ownership test
into the load itself, leaving **3 named exemptions** rather than 6 unscoped repositories. **0 `.sql` files with
`ddl-auto=update` in dev** was `master`'s state and is false of this branch in both halves; what it meant there was
that the schema the tests would prove anything about got generated by whichever entity file was edited last
(`DB-01`, `DB-05`). Here **Flyway owns the schema**: the branch carries **seven migrations** (`V1`–`V7`) and `validate`
in every profile, so a schema and a model that disagree stop the boot instead of quietly re-drawing each other.

## 2. Modules

A module is **the unit that owns data**, not a deployable. Twelve modules, derived from the 36 tables and the 36
repository interfaces that already exist — no new vocabulary, and no re-packaging (see §7).

| Module | Owns tables | Owns repositories | API prefix(es) | Primary personas |
|---|---|---|---|---|
| `identity` | `users`, `parents`* | `UserRepository`, `ParentRepository` | `/auth`, `/users` | all |
| `tenancy` | `schools` | `SchoolRepository` | `/schools` | P-1 |
| `staff` | `employees`, `designations` | `EmployeeRepository`, `DesignationRepository` | `/employees`, `/teachers` | P-2 |
| `students` | `students`, `student_admissions`, and the `parent_student` link | `StudentRepository`, `StudentAdmissionRepository` | `/students`, `/student-admissions` | P-2 |
| `academic-structure` | `classrooms`, `sections`, `subjects`, `academic_years` | `ClassroomRepository`, `SectionRepository`, `SubjectRepository`, `AcademicYearRepository` | `/classrooms`, `/sections`, `/subjects`, `/academic-years` | P-2 |
| `attendance` | `attendances` | `AttendanceRepository` | `/attendance` | P-3 |
| `examinations` | `exams`, `exam_subjects`, `student_exams`, `results`, `grading_schemes` | `ExamRepository`, `ExamSubjectRepository`, `StudentExamRepository`, `ResultRepository`, `GradingSchemeRepository` | `/exams`, `/student-exams`, `/results`, `/grading-schemes` | P-3 |
| `timetable` | `timetables`, `timetable_slots`, `teacher_assignments` | `TimetableRepository`, `TimetableSlotRepository`, `TeacherAssignmentRepository` | `/timetables`, `/teacher-assignments` | P-3, P-4 |
| `finance` | `fees`, `fee_structures`, `student_fees`, `fee_payments`, `invoices`, `receipts`, `financial_transactions`, `financial_reports`, `payment_gateways` | `FeeRepository`, `FeeStructureRepository`, `StudentFeeRepository`, `FeePaymentRepository`, `InvoiceRepository`, `ReceiptRepository`, `FinancialTransactionRepository`, `FinancialReportRepository`, `PaymentGatewayRepository` | `/fees`, `/fee-structures`, `/student-fees`, `/fee-payments`, `/invoices`, `/receipts`, `/financial-*`, `/payment-gateway` | P-6 (issuable since Stage 3 `C-6`; its frontend surface is open — `SEC-17`) |
| `communication` | `notifications`, `announcements`, `messages`, `communication_templates`, `email_logs`, `sms_logs`, `school_calendar` | `NotificationRepository`, `AnnouncementRepository`, `CommunicationTemplateRepository`, `EmailLogRepository`, `SmsLogRepository`, `SchoolCalendarRepository` — and **none** for `messages` (`COM-04`: the entity has no repository at all) | `/notifications`, `/announcements`, `/communications`, `/school-calendar` | P-2…P-5 |
| `platform` | none — reads aggregates only | none | `/financial-dashboard`, superadmin report routes | P-1 |
| `audit` | the `created_by` / `updated_by` pair, declared once on `AuthorshipEntity` and inherited through `BaseAuditEntity`, plus the `version` column of the latter: **35 of the 36 entity classes carry the two columns** — 28 through `BaseAuditEntity`, 5 (`School`, `Student`, `Classroom`, `Subject`, `StudentAdmission`) straight from `AuthorshipEntity`, and `Employee` and `User` declaring their own pair. `academic_years` is the sole entity class left without an actor column, and the `parent_student` / `user_roles` links have neither. **Closed by `C-8` on 2026-11-02**, which is what turned this from a claim about a superclass into a fact about the database: `V7__record_who_wrote_the_pupil.sql` adds `created_by` / `updated_by` and eight `REFERENCES users (id)` keys to `students`, `classrooms`, `subjects` and `student_admissions` (`schools` already had the columns in the baseline and had no annotation to write into them), and `AuditingEntityListener` fills both from `TenantContextAuditorAware`, which resolves the author from the `uid` claim of the verified token as a JPA reference — no query per write, and no author guessed for a row written outside a request; the live half of that sentence is MVP feature `F11` in `scripts/verify-mvp-e2e.sh` — **20 checks, 0 failures, 0 skips** on the two-tenant `V1`–`V7` run §1 records | none | none | — |

**Coverage check, because Stage 1's acceptance criterion says the table must be spot-checkable:** 36 tables and all
**36** repository interfaces appear exactly once above (`ls repository | wc -l` = 37; the extra file is the
`TenantScopedRepository` base, which owns no table, and `DashboardStatisticsRepository` is the one named repo whose
module row describes it as "reads aggregates only"; the `parent_student` link is a
`@ManyToMany` join table declared inside `Parent.java:41-47`, so it has no repository of its own and is owned by
`students` per the row above). `MessageRepository` is the one entity/repo pair the map records as *missing*, and its
absence is the finding, not an oversight in this table.

\* `parents` is the one table whose ownership straddles two modules by design (`ADR-020`): the *person* is global
(`Parent.java:31` — globally unique email) and the *relationship to a school* is the `parent_student` join table,
owned by `students`. D-2 in the PRD is the product decision behind that.

## 3. Data ownership and dependency direction

**Rule D-1 — one writer.** Exactly one module may write a table. Any other module that needs the data reads it
through the owner's service interface, never through the owner's repository. Today `StudentController` injects
`StudentAdmissionRepository` directly (`StudentController.java:7`, field at `:38`) — a cross-module write path with no
owner in the loop. That is the pattern this rule forbids.

**Rule D-2 — downward only.** `controller → service → repository → entity`. A repository may not know a service
exists; an entity may not know a controller exists. Already true, and must be made *enforced* (§7) rather than
habitual.

**Rule D-3 — responses are DTOs, never entities.** 77 of the 236 handlers still name an entity type in their response
signature — 21 return the entity bare, 56 return `List<Entity>` — counted on this build by the same handler scan §1
uses, and `ParentResponse.java:3` is the one response record that holds an entity field (`SEC-04`/`ACD`-family
leakage, `BKL-01`). The **90** this section carried was the Stage-1 tally of endpoints returning an unbounded `List`,
a different metric that §1 now records at **89**; nothing here re-asserts 90 as an entity-leak count. Every response
is a `model/response` record; every request body is a `model/request` record with bean validation. The entity graph
stops at the service.

**Rule D-4 — a module may not read another module's rows without a tenant predicate it did not have to remember.**
Consequence of §4: the tenant key travels with the query, so a repository method that takes no tenant argument is a
build failure.

**Allowed cross-module edges** (everything else must go through the owning service or an event):

| From → To | Allowed because |
|---|---|
| `attendance`, `examinations`, `timetable`, `finance` → `students`, `academic-structure`, `staff` | they reference those rows; read-only by D-1 |
| `communication` → `students`, `staff`, `academic-structure` | audience resolution needs enrolment facts |
| `platform`, `finance` → everything | aggregate reads only, never writes (`Rule D-5`: the platform module owns no table) |
| `identity` → `tenancy` | a user is issued inside a school; the school id is a claim, so the read happens at token issue |
| anything → `audit` | the audit columns are set by a JPA entity listener, not by callers |

**Forbidden:** a repository in module A queried from module B's service; a service returning another module's
entity; a controller reaching `security/` internals directly. Before Stage 3's `C-2` the last clause was broken by
`SecurityUtil`, a static re-parse of the `Authorization` header called from controllers; it has been deleted and
§4's request-scoped value replaced it.

## 4. Tenancy

Decided in [ADR-020](infrastructure/INFRASTRUCTURE_DECISIONS.md); recorded here as the mechanism, because the
mechanism is what Stage 2's migrations and Stage 3's code must both agree on.

**Shape:** shared schema, shared tables, **`school_id` on every row**, enforced at three levels rather than one.

1. **Claim.** The JWT carries `schoolId` already. It is parsed **once** per request into a request-scoped
   `TenantContext`, and controllers no longer each call `SecurityUtil.getSchoolIdFromToken()` and pass an `Integer`
   down that any service signature may drop (`TEN-01`, `TEN-07`: `SecurityUtil` returns null off-request and
   callers pass that null as a filter value).

   **Shipped 2026-11-01 (Stage 3 `C-2`).** `JwtAuthenticationFilter` parses the token once and populates
   `security/TenantContext` (username, school id, employee id, roles), clearing it in a `finally` so a pooled
   thread never carries one caller's tenant forward. `util/SecurityUtil` — a static re-parse *and re-verification*
   of the token, 325 call sites in 41 files — is deleted, not wrapped. No service method may accept the tenant as
   an argument; `TenantParameterArchTest` fails the build if one does, so the "a caller may forget" path is
   closed rather than documented. A request that names another school's row is refused with 403
   (`TenantAccessDeniedException`), not 400. What level 2 then still owed is recorded below.
2. **Query.** Every repository method that reads tenant data takes the tenant explicitly, and Hibernate applies the
   discriminator at the SQL level, so an unscoped `findAll()` cannot be written by accident (`TEN-06`: `@Where` /
   `@Filter` appear nowhere today).

   **Shipped 2026-11-01 (Stage 3 `C-3`) for the reads this repository declares, and completed 2026-11-02 (Stage 3
   `C-4`) for the inherited by-id read.** `RepositoryTenantScopeArchTest`
   fails the build when a declared repository read carries no tenant and records no reason. Measured with that test
   on both sides: 170 declared reads with 55 carrying no tenant at the end of `C-2`, 37 of them indefensible; 168
   declared reads with 18 carrying none at `C-3`'s close and 169/151/18 after `C-7` added one scoped read; on the
   build that closes `C-4`, **181 declared reads, 158 carrying the tenant, 23 carrying none and every one of them
   allow-listed** with the constraint that makes it safe — the `SchoolRepository` reads, because that table *is* the
   tenant, and the rest on lookups whose column `V3` made globally unique (`uq_users_username`, `uq_parents_email`) or
   whose association already pins the school. The test prints those three figures on every build, so the numbers here
   are recomputed rather than transcribed. The proof is the SQL, not the result set:

   ```sql
   select … from timetables t1_0 join classrooms c1_0 on c1_0.id = t1_0.classroom_id
   where c1_0.school_id = ? and t1_0.classroom_id = ?
   ```

   Three limits, stated rather than implied. (a) This level governs *declared* methods, and the inherited ones needed a
   different mechanism, which `C-4` shipped on 2026-11-02: `repository/TenantScopedRepository.java`, a
   `@NoRepositoryBean` interface whose `findById` override (line 57-61) routes every by-id read either through
   `findByIdOwnedBy(id, TenantContext.schoolId())`
   (line 45) or, for the platform owner and off-request bootstrap only, `findByIdAnyTenant`. **The ownership check now
   sits at the point of load, not in the service below it**: `TenantScopedReadArchTest` prints
   `C-4: 33 repositories serve a tenant-owned entity and every one of them scopes findById by the claim; 3 serve the
   tenant table itself or a row with no single owner` (`SchoolRepository`, `ParentRepository` — global by D-2 — and
   `DashboardStatisticsRepository`), and a wrong-tenant by-id read matches no row, so it answers **404 rather than the
   403 `C-2` established for a refusal the service raises itself**. That is a behaviour change and not a relaxation:
   `scripts/verify-c1-tenancy.sh`, `verify-c2-tenant-context.sh` and `verify-c7-tenant-key.sh` were widened on the same
   build to accept 403 *or* 404, each with the reason recorded. What still
   carries no tenant is `findAll`, `findAllById` and `delete` — recorded as open by `TenantScopedReadArchTest`'s own
   javadoc (`:39-41`) — and `getById`/`getReferenceById`, which cannot be filtered (they hand back a proxy before any
   predicate can run) and are therefore *forbidden* on tenant-owned repositories rather than scoped. (b) The predicate
   above travels through a join to `classrooms.school_id`
   because `timetables.school_id`, added by `V2`, was NULL on rows the application writes: no entity mapped it.
   A composite FK with a NULL column is not enforced, so level 3's guarantee was bypassable by the application's own
   inserts. **Closed by `C-7` on 2026-11-01**: nine entities map the column, the write path fills it from the claim,
   and `V6` made all ten NOT NULL. The join predicate above still does the filtering — `C-7` did not rewrite the reads —
   but it is no longer the *only* thing between school B and school A's rows, which is what made `C-3`'s measurement a
   control rather than a courtesy. (c) `@Where`/`@Filter` remain unused by choice: an explicit parameter
   on every method is visible in the call site, whereas a session filter is invisible in exactly the way a forgotten
   `schoolId` argument was.

   **What closing (a) measured.** Moving the predicate into the load deleted the hand-written comparisons that
   duplicated it and kept the ones guarding an attachment: `service/impl` carries **23 lines of tenant comparison
   today, 21 distinct checks** (`TeacherAssignmentServiceImpl.java:58-60` is one `if` across three lines), down from
   **107** at the commit `C-4` started from (`7c7789d`). The residue is not a re-implementation of the load — it names
   what the load cannot see: an attachment the row points at (a classroom, a section, a fee, a teacher, a subject),
   the creator's own school, the parent's child, and the `schools` self-comparisons that answer to the platform owner.
   The entity-side lazy-proxy 500s the card exposed are now guarded and policed by `EntityLazyAccessorArchTest`, which
   prints `C-4: 37 derived accessors read a lazy association, 15 of them ask whether it is loaded first (73 lazy
   to-one fields and 49 @JsonProperty accessors across 39 entity sources)` — the figures moved again when `C-8` added
   `AuthorshipEntity`, which is exactly why the test prints them rather than this file keeping them. Live, against a
   running application on a disposable database at **V7**: `scripts/verify-c4-owned-reads.sh`
   **118 PASS / 0 FAIL / 17 SKIP**, **22 probes over the 22 controllers that declare a by-id read**, re-measured
   unchanged on the build that carries `C-8`. The 17 SKIPs are **not** the checks that need a database handle: they are
   the platform-owner probes on by-id routes whose own `@PreAuthorize` does not admit `SUPERADMIN`, where a 403 proves
   the role layer worked and nothing at all about the tenant filter, so the script reports SKIP rather than a PASS it
   has not earned (the layer distinction §7.1 states as its limit (ii)). The full `mvn -B verify` suite on this build is
   **106 tests, 0 failures**, summed from the 16 `target/surefire-reports/TEST-*.xml` the build leaves behind; the same
   suite was 101 at `C-4`'s close, 104 once the audit-listener and principal-attribution work landed, and `C-8`'s two
   attribution tests take the last step. What
   `C-4` does **not** close: the three inherited forms named in (a) above, the perimeter inventory that
   `anyRequest().denyAll()` turns into a correctness question (`ParentController`'s four administrative routes match
   no URL rule), and any claim about a deployment — none of this has been `Deployed`, and `master` still carries no
   migrations.
3. **Database.** The tenant key is *materialised* on child rows and constrained against the parent, so a mismatch
   is impossible rather than discouraged:

```sql
-- parent: the tenant key is part of the candidate key
ALTER TABLE students ADD CONSTRAINT uq_students_id_school UNIQUE (id, school_id);

-- child: the composite FK can only be satisfied by a parent in the SAME school
ALTER TABLE attendances ADD COLUMN school_id integer NOT NULL;
ALTER TABLE attendances ADD CONSTRAINT fk_attendance_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);
```

A row that joins a school-7 student to school-9 attendance **cannot be inserted**, no matter which code path wrote
it — including the one nobody has read yet. This is the same principle `ADR-018` applied to infrastructure: the rule
lives in the artefact, not in the prose.

**The 10 tables that gain `school_id`:** `attendances`, `exam_subjects`, `fee_payments`, `parents`* , `results`,
`student_exams`, `student_fees`, `teacher_assignments`, `timetables`, `timetable_slots` — each derived once from its
owning aggregate, which is how `V2` backfilled it and how `V6` proved it non-null. \* `parents` stays global by D-2;
its *links* gain the tenant key. **What this row no longer describes is the model**: `C-7` mapped nine of these ten —
`Attendance`, `ExamSubject`, `FeePayment`, `Result`, `StudentExam`, `StudentFee`, `TeacherAssignment`, `Timetable`,
`TimetableSlot` now carry their own `@ManyToOne School` on `school_id`, so `Timetable` no longer reaches its school
only through `classroom_id` (`Timetable.java:23-25` is the tenant, `:27-28` the classroom) and the join predicate
`C-3` measured is no longer the only line of defence. `Parent.java` still has no school association at all, by design.

**Tenancy status of every entity, and the path V2 backfills from** — the column Stage 2 needed to write the
`UPDATE … FROM` statements without re-deriving the graph by hand; since `V2`–`V6` ran, the derivation path is also the
record of where each materialised key came from:

| Status | Entities (how the tenant is reached) |
|---|---|
| **Direct** — `@ManyToOne School` on `school_id` (**34**) | `AcademicYear`, `Announcement`, `Attendance`, `Classroom`, `CommunicationTemplate`, `Designation`, `EmailLog`, `Employee`, `Exam`, `ExamSubject`, `Fee`, `FeePayment`, `FeeStructure`, `FinancialReport`, `FinancialTransaction`, `GradingScheme`, `Invoice`, `Message`, `Notification`, `PaymentGateway`, `Receipt`, `Result`, `SchoolCalendar`, `Section`, `SmsLog`, `Student`, `StudentAdmission`, `StudentExam`, `StudentFee`, `Subject`, `TeacherAssignment`, `Timetable`, `TimetableSlot`, `User`. This row read **25** until `C-7` mapped the nine that `V2` had materialised but no entity owned, so 34 of the 36 entity classes now carry the key themselves |
| **Self** | `School` (it *is* the tenant; a scalar `schoolId` display code at `School.java:23-24`) |
| **Derived from (the path `V2` backfilled, and `C-7` then mapped as **Direct**)** | 1 hop: `Attendance`→`student`; `ExamSubject`→`exam`; `FeePayment`→`studentFee`; `StudentExam`→`student`; `StudentFee`→`student`; `TeacherAssignment`→`teacher` (`Employee`); `Timetable`→`classroom` (`@OneToOne`, `unique = true`). 2 hops: `Result`→`studentExam`→`student`; `TimetableSlot`→`timetable`→`classroom` (and →`assignment`→`teacher`→`school`) |
| **Global by design** | `Parent` (no school association; globally unique email at `Parent.java:31`; the tenant lives on the `parent_student` link instead — PRD D-2) — the one entity class with no tenant column and no exemption needed, because its tenancy is on the link `V6` holds with `trg_parent_student_tenant_key` |
| **No repository at all** | `Message` (`COM-04`) — a tenant-keyed table that is also unwritable through any data layer |

**Rejected:**
- *Schema-per-tenant* — a migration per tenant and a connection pool per schema; with tens of schools this is an
  operational system GMS cannot staff or cost.
- *Database-per-tenant* — the same, multiplied by the zero-cost rule.
- *PostgreSQL row-level security now* — correct eventually, wrong first: RLS policies are invisible to JPA's query
  planner and interact badly with `ddl-auto`; a third reason written here at Stage 1 — authoring them against a schema
  with no migrations at all (`DB-01`) — has since aged out, because the branch carries seven (`V1`–`V7`) and §5.3 is
  the run against them. The first two are why it stays out. `TEN-06`
  already lists RLS evaluation as debt item #14. It returns as a Stage-7 candidate, and the
  composite-FK design above is what makes it *addable* later: the column RLS needs will already exist.
- *Tenant id as a column on `users` only* — the login's school is not the row's school; `User.java` already carries
  `employee_id`, `student_id`, `parent_id` as parallel pointers, which is exactly how `TEN-02`'s raw-id loads became
  possible.

## 5. Index and constraint design at ≥50,000 records

PostgreSQL does **not** index foreign keys, and `grep` shows **0** indexes declared (§1). At the target volume, every
`school_id` filter and every join in the app is a sequential scan today. Below is the design Stage 2 ships as
migrations; the naming convention is `idx_<table>_<columns>` and `uq_<table>_<columns>`.

**Rule I-1:** every tenant-scoped index leads with `school_id`. **Rule I-2:** every foreign key gets an index unless
a composite starting with it already exists. **Rule I-3:** every natural key listed in `DB-02` becomes a real
`UNIQUE` constraint — a duplicate caught by the database cannot be created by a race in code.

| Table | Indexes / constraints | Serves |
|---|---|---|
| `schools` | `uq_schools_school_code` (already `School.java:29` `unique = true`), `idx_schools_enabled_status` | P-1 list + filter |
| `users` | `uq_users_username` (global — `User.java:74` already `unique = true`), `uq_users_school_email (school_id, email)`, `uq_users_school_user_id (school_id, user_id)`, `idx_users_school_status (school_id, user_status)`, `idx_users_employee_id`, `idx_users_student_id`, `idx_users_parent_id` | login, per-school staff lists, the three parallel `@OneToOne` pointers at `User.java:56-69` |
| `user_roles` | `uq (user_id, role)`, `idx_user_roles_role (role)` | roles live here (`User.java:48-51`, an `@ElementCollection Set<String>`), so every "who is a TEACHER" query is a join into a collection table that has no index and no natural key today |
| `students` | `uq_students_student_id (school_id, student_id)` — closes `ACD-08`'s cross-tenant numbering structurally, `idx_students_school_status (school_id, status)`, `idx_students_school_class (school_id, classroom_id)`, `idx_students_school_section (school_id, section_id)`, `idx_students_school_name (school_id, last_name, first_name)`, `uq_students_school_email (school_id, email) WHERE email IS NOT NULL AND email <> ''` (partial — `DB-02`'s fifth key, added by §5.3 correction 4), `idx_students_email`, `idx_students_dob` | the paged list, the default sort, search by admission number, name and email |
| `employees` | `uq_employees_employee_id (school_id, employee_id)`, `uq_employees_school_email (school_id, email)`, `idx_employees_school_status (school_id, employee_status)`, `idx_employees_school_designation (school_id, designation)`, `idx_employees_school_department (school_id, department)`, `idx_employees_is_teaching` | staff lists filtered by status/role. `designation` is a plain `varchar(100)` on `Employee.java` (`@Column(name = "designation")`), **not** a FK to the unused `designations` table — a second `DB-05`-shaped split this design records rather than silently "fixes" |
| `classrooms` | `uq_classrooms_school_name_grade_section (school_id, name, grade, section)` | `DB-02` |
| `sections` | `uq_sections_school_name (school_id, name)` | `DB-02` |
| `subjects` | `uq_subjects_school_code (school_id, code)` | duplicate subject codes |
| `academic_years` | `uq_academic_years_school_name (school_id, name)`, `idx_academic_years_school_current (school_id, is_current)` | one "current" year per school; `AcademicYear` does carry `school_id NOT NULL` (`:27-28`), unlike the 10 in §4 |
| `attendances` | **`uq_attendance_student_slot_date (student_id, timetable_slot_id, date)`** — `ACD-03`'s missing duplicate guard, `idx_attendance_school_date_status (school_id, date, status)` — the rollup, `idx_attendance_student_date (student_id, date)` | one mark per child per slot; percentage queries |
| `timetables` | `uq_timetable_classroom_year (classroom_id, academic_year)` — the model still declares `classroom_id` alone `unique = true` (`Timetable.java:28`, `DB-07`), which forbids a second year's timetable for a classroom; `V3` drops the database-level key and adds the composite above, so the annotation now describes a constraint the schema no longer enforces (§5.1's note on the same gap) | the read path in MVP item 7 |
| `timetable_slots` | `uq_slot_day_period (timetable_id, day_of_week, period)` — `ACD-06`'s conflict rule at the level that cannot lose a race, `idx_slot_assignment (assignment_id)` | teacher timetable, conflict detection |
| `teacher_assignments` | `uq_assignment_teacher_subject_class_year (teacher_id, subject_id, classroom_id, academic_year)`, `idx_assignment_classroom_year (classroom_id, academic_year)`, `idx_assignment_teacher (teacher_id)` | "my classes", "my subjects" |
| `exams` | `uq_exam_school_name_year (school_id, name, academic_year)`, `idx_exam_school_date (school_id, exam_date)` | duplicate exams (`ACD-07`) |
| `exam_subjects` | `uq_exam_subject (exam_id, subject_id)`, `idx_exam_subject_exam (exam_id)`, `idx_exam_subject_subject (subject_id)` | marks entry. `ExamSubject` had **no** `school_id` in the model when this row was written; `V2` materialised the column, `C-7` mapped it and `V6` made it NOT NULL, and `V4` adds `idx_exam_subject_school (school_id)` — the key this design assumed would only exist later |
| `student_exams` | `uq_student_exam_subject (student_id, exam_subject_id)`, `idx_student_exam_exam_subject (exam_subject_id)` | double registration; same V2 dependency |
| `results` | `uq_result_student_exam (student_exam_id)`, `idx_result_status (status)` | published vs draft, child's result; `Result` also has no `school_id` before V2, so the parent's "my child's published results" query is a three-hop join until then |
| `grading_schemes` | `uq_grading_scheme_school_name_grade (school_id, name, grade)`, plus `CHECK (min_percentage <= max_percentage)` — `ACD-05`'s bands are `min_percentage`/`max_percentage`, which nothing compares today | band sanity at the boundary, where a race cannot beat it. The unique includes `grade` because a *scheme is a set of bands* — one row per grade — so `(school_id, name)` alone would cap every scheme at one band |
| `fees` | `idx_fee_school_status (school_id, status)`, `idx_fee_school_academic_year (school_id, academic_year)`, `idx_fee_school_due (school_id, due_date)` | fee structure lists |
| `student_fees` | `uq_student_fee (student_id, fee_id)`, `idx_student_fee_fee (fee_id)`, `idx_student_fee_student_status (student_id, status)` | `FIN-02` (empty lists), reminders. **`StudentFee` mapped no tenant column when this row was written** — its entity columns were `student_id`, `fee_id`, `total_amount`, `status`, `amount_paid`, `balance_amount`, `due_date`, `waiver_amount`, `fine_amount`, `last_payment_date` — so the "this school's overdue fees" query was a join into `students` until `V2` materialised the key. `C-7` put the column on the entity and `V6` made it NOT NULL, so `(school_id, status, due_date)` is legal today; `V4` ships `idx_student_fee_school` while it is still nullable |
| `fee_payments` | `uq_fee_payment_reference (payment_reference) WHERE payment_reference IS NOT NULL` (partial — manual `CASH` rows legitimately have no reference), `idx_fee_payment_student_fee (student_fee_id)`, `idx_fee_payment_date (payment_date)` | `FIN-01`'s verification needs a reference to verify against. `FeePayment` hangs off `student_fees`, so the whole money ledger was tenant-less at row level until `V2` added the column and `V6` enforced it; `C-7` mapped it, and `V4` adds `idx_fee_payment_school (school_id)` alongside `idx_student_fee_school` |
| `invoices` / `receipts` | `uq_invoice_school_number (school_id, invoice_number)`, `uq_receipt_school_number (school_id, receipt_number)`, `idx_invoice_student`, `idx_receipt_payment` | `FIN-04` linkage |
| `notifications` | `idx_notification_school_delivery_created (school_id, delivery_status, created_on DESC)`, **new join table `notification_recipients (notification_id, user_id, read_at, read_by)` with `uq (notification_id, user_id)` and `idx (user_id, read_at)`** | `BR-11`, `DB-04`, per-recipient read state, `TEN-03` |
| `announcements` | `idx_announcement_school_published (school_id, is_published, publish_date DESC)`, **new join table `announcement_targets`** replacing `target_ids` | `COM-03`, the paged list |
| `messages` | **new join table `message_recipients`** replacing `recipient_ids`; `Message` needs its first repository (`COM-04`) | threads, unread counts |
| `parent_student` | composite primary key `(parent_id, student_id)` + `idx (student_id)`, **plus `school_id` and its own composite FK to `students`** | `BR-03`: the assertion that a child belongs to *this* school |

**Search semantics, decided rather than left to whoever writes the controller.** The MVP contract is
*prefix* matching on `student_id`, `last_name`, `first_name` (indexable with a `text_pattern_ops` btree, so it
scales at 50k) and *exact* on `email` / `phone_number`. Substring search (`LIKE '%x%'`) is **out of the MVP**: it
cannot use a btree, and `pg_trgm` + GIN is a real answer with a real cost — a migration to add an extension, a
write-amplification penalty on every insert, and a p95 that has not been measured here. The trigger for revisiting
it is written into Stage 5: if prefix-plus-exact cannot find a known student in the seeded 50k dataset at p95 under
300 ms, GIN moves into scope with that measurement attached.

**Count.** **0 secondary indexes declared in the model today**, and uniqueness expressed two ways: 8 column-level
`unique = true` annotations plus one table-level `uniqueConstraints` block holding 2 more (`Designation.java:25-28`).
The design above names about 35 indexes and about 25 unique constraints explicitly, and Rule I-2 adds one index per
foreign key across the tables not itemised. The exact totals are deliberately *not* asserted here as a number: a
count typed into a design document is not evidence. Stage 2's acceptance test is that `pg_indexes` on a database
built only by migrations **diffs empty against this table**, which is a check rather than a claim.

### 5.1 The same design as migration text

Stage 1's acceptance criterion for this section is that the design is "expressed as something a migration will
contain, not as a wish". A table of index *names* is still a wish — it can be admired without ever being run against
a column list. So this is the DDL, authored against the column names read off the entity files above (§1–§4), and
Stage 2's `B-3` job is to transcribe it, run it, and report what failed rather than what was intended.

```sql
-- V2__materialise_tenant_key.sql
-- Order matters and the reason is data, not style: results needs student_exams.school_id,
-- fee_payments needs student_fees.school_id and timetable_slots needs timetables.school_id,
-- so running the dependents first would backfill NULL into the next table.
ALTER TABLE attendances         ADD COLUMN school_id integer;
ALTER TABLE student_exams       ADD COLUMN school_id integer;
ALTER TABLE exam_subjects       ADD COLUMN school_id integer;
ALTER TABLE results             ADD COLUMN school_id integer;
ALTER TABLE student_fees        ADD COLUMN school_id integer;
ALTER TABLE fee_payments        ADD COLUMN school_id integer;
ALTER TABLE teacher_assignments ADD COLUMN school_id integer;
ALTER TABLE timetables          ADD COLUMN school_id integer;
ALTER TABLE timetable_slots     ADD COLUMN school_id integer;

UPDATE attendances       a  SET school_id = s.school_id  FROM students s       WHERE s.id  = a.student_id;
UPDATE student_exams     se SET school_id = s.school_id  FROM students s       WHERE s.id  = se.student_id;
UPDATE exam_subjects     es SET school_id = e.school_id  FROM exams e          WHERE e.id  = es.exam_id;
UPDATE results           r  SET school_id = se.school_id FROM student_exams se WHERE se.id = r.student_exam_id;
UPDATE student_fees      sf SET school_id = s.school_id  FROM students s       WHERE s.id  = sf.student_id;
UPDATE fee_payments      fp SET school_id = sf.school_id FROM student_fees sf  WHERE sf.id = fp.student_fee_id;
UPDATE teacher_assignments ta SET school_id = e.school_id FROM employees e     WHERE e.id  = ta.teacher_id;
UPDATE timetables        t  SET school_id = c.school_id  FROM classrooms c     WHERE c.id  = t.classroom_id;
UPDATE timetable_slots   ts SET school_id = t.school_id  FROM timetables t     WHERE t.id  = ts.timetable_id;

-- The assertion, not a comment. Written as nine clauses because a loop that cannot be read
-- is not a guard: an unowned row must abort the migration, not become a NULL that SET NOT NULL
-- turns into an error three statements later with no table named.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM attendances WHERE school_id IS NULL)         THEN RAISE EXCEPTION 'V2: attendances has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM student_exams WHERE school_id IS NULL)       THEN RAISE EXCEPTION 'V2: student_exams has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM exam_subjects WHERE school_id IS NULL)       THEN RAISE EXCEPTION 'V2: exam_subjects has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM results WHERE school_id IS NULL)             THEN RAISE EXCEPTION 'V2: results has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM student_fees WHERE school_id IS NULL)        THEN RAISE EXCEPTION 'V2: student_fees has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM fee_payments WHERE school_id IS NULL)        THEN RAISE EXCEPTION 'V2: fee_payments has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM teacher_assignments WHERE school_id IS NULL) THEN RAISE EXCEPTION 'V2: teacher_assignments has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM timetables WHERE school_id IS NULL)          THEN RAISE EXCEPTION 'V2: timetables has rows with no tenant'; END IF;
  IF EXISTS (SELECT 1 FROM timetable_slots WHERE school_id IS NULL)     THEN RAISE EXCEPTION 'V2: timetable_slots has rows with no tenant'; END IF;
END $$;

ALTER TABLE attendances         ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE student_exams       ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE exam_subjects       ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE results             ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE student_fees        ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE fee_payments        ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE teacher_assignments ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE timetables          ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE timetable_slots     ALTER COLUMN school_id SET NOT NULL;
```

```sql
-- V3__natural_keys.sql  (DB-02 and DB-07; each of these is a race a service can no longer lose)
ALTER TABLE schools   ADD CONSTRAINT uq_schools_school_code UNIQUE (school_code);
ALTER TABLE users     ADD CONSTRAINT uq_users_username       UNIQUE (username);
ALTER TABLE users     ADD CONSTRAINT uq_users_school_email   UNIQUE (school_id, email);
ALTER TABLE users     ADD CONSTRAINT uq_users_school_user_id UNIQUE (school_id, user_id);
ALTER TABLE students  ADD CONSTRAINT uq_students_school_student_id UNIQUE (school_id, student_id);
-- partial: students.email is nullable and '' is "no mailbox", not "shared mailbox" (§5.3 correction 4)
CREATE UNIQUE INDEX uq_students_school_email ON students (school_id, email)
    WHERE email IS NOT NULL AND email <> '';
ALTER TABLE employees ADD CONSTRAINT uq_employees_school_employee_id UNIQUE (school_id, employee_id);
ALTER TABLE employees ADD CONSTRAINT uq_employees_school_email UNIQUE (school_id, email);
ALTER TABLE classrooms ADD CONSTRAINT uq_classrooms_school_name_grade_section UNIQUE (school_id, name, grade, section);
ALTER TABLE sections  ADD CONSTRAINT uq_sections_school_name UNIQUE (school_id, name);
ALTER TABLE subjects  ADD CONSTRAINT uq_subjects_school_code UNIQUE (school_id, code);
ALTER TABLE academic_years ADD CONSTRAINT uq_academic_years_school_name UNIQUE (school_id, name);
CREATE UNIQUE INDEX uq_academic_years_one_current ON academic_years (school_id) WHERE is_current;
ALTER TABLE attendances ADD CONSTRAINT uq_attendance_student_slot_date UNIQUE (student_id, timetable_slot_id, date);
ALTER TABLE timetables  ADD CONSTRAINT uq_timetable_classroom_year UNIQUE (classroom_id, academic_year);
ALTER TABLE timetable_slots ADD CONSTRAINT uq_slot_day_period UNIQUE (timetable_id, day_of_week, period);
ALTER TABLE teacher_assignments ADD CONSTRAINT uq_assignment_teacher_subject_class_year
    UNIQUE (teacher_id, subject_id, classroom_id, academic_year);
ALTER TABLE exams ADD CONSTRAINT uq_exam_school_name_year UNIQUE (school_id, name, academic_year);
ALTER TABLE exam_subjects ADD CONSTRAINT uq_exam_subject UNIQUE (exam_id, subject_id);
ALTER TABLE student_exams ADD CONSTRAINT uq_student_exam_subject UNIQUE (student_id, exam_subject_id);
ALTER TABLE results  ADD CONSTRAINT uq_result_student_exam UNIQUE (student_exam_id);
ALTER TABLE grading_schemes ADD CONSTRAINT uq_grading_scheme_school_name_grade UNIQUE (school_id, name, grade);
ALTER TABLE grading_schemes ADD CONSTRAINT ck_grading_band_order CHECK (min_percentage <= max_percentage);
ALTER TABLE fees ADD CONSTRAINT uq_fee_school_type_year_grade_category
    UNIQUE (school_id, fee_type, academic_year, class_grade, student_category);
ALTER TABLE student_fees ADD CONSTRAINT uq_student_fee UNIQUE (student_id, fee_id);
CREATE UNIQUE INDEX uq_fee_payment_reference ON fee_payments (payment_reference)
    WHERE payment_reference IS NOT NULL;
ALTER TABLE invoices ADD CONSTRAINT uq_invoice_school_number UNIQUE (school_id, invoice_number);
ALTER TABLE receipts ADD CONSTRAINT uq_receipt_school_number UNIQUE (school_id, receipt_number);
ALTER TABLE designations ADD CONSTRAINT uq_designation_school_code UNIQUE (school_id, designation_id);
ALTER TABLE parents  ADD CONSTRAINT uq_parents_email UNIQUE (email);   -- global by PRD D-2, not an oversight
-- The role vocabulary lives in a Hibernate @ElementCollection table with no key at all, so "the same user is a
-- TEACHER twice" is insertable today; this is the constraint that makes SEC-17's vocabulary fix have a floor.
ALTER TABLE user_roles ADD CONSTRAINT uq_user_roles_user_role UNIQUE (user_id, role);
ALTER TABLE parent_student ADD CONSTRAINT pk_parent_student PRIMARY KEY (parent_id, student_id);
```

`invoices.invoice_number`, `receipts.receipt_number`, `designations.designation_id` and
`communication_templates.template_code` already carry a **global** `unique = true` (`DB-07`), so V3 is a
drop-then-add on those — `V3__natural_keys.sql:16-23` drops each hashed baseline key and adds the `(school_id, …)`
form. The design also required the entity annotation to go in the same commit, and **it did not**: the four are still
declared (`Invoice.java:27`, `Receipt.java:27`, `Designation.java:37`, `CommunicationTemplate.java:27`), as is
`timetables.classroom_id` (`Timetable.java:28`), whose global key `V3:27-28` likewise replaced with the composite the
product actually wants. The reason no boot failed is that `validate` compares tables and columns, not constraints, so
an annotation describing a key the schema no longer holds is invisible to it — which makes this a documentation defect
rather than a startup error, and an **open** one: the model currently claims four uniqueness rules the migrated
database does not implement. `ADR-022`'s loud-failure mechanism still governs the case it was written for, a missing
table or column.

```sql
-- V4__indexes.sql  (DB-03; Rule I-1 leads every tenant-scoped index with school_id)
CREATE INDEX idx_schools_enabled_status       ON schools (enabled, status);
CREATE INDEX idx_users_school_status          ON users (school_id, user_status);
CREATE INDEX idx_users_employee               ON users (employee_id);
CREATE INDEX idx_users_student                ON users (student_id);
CREATE INDEX idx_users_parent                 ON users (parent_id);
CREATE INDEX idx_user_roles_role              ON user_roles (role);
CREATE INDEX idx_students_school_status       ON students (school_id, status);
CREATE INDEX idx_students_school_classroom    ON students (school_id, classroom_id);
CREATE INDEX idx_students_school_section      ON students (school_id, section_id);
CREATE INDEX idx_students_school_name         ON students (school_id, last_name, first_name);
CREATE INDEX idx_students_student_id_prefix   ON students (school_id, student_id varchar_pattern_ops);
CREATE INDEX idx_students_last_name_prefix    ON students (school_id, last_name varchar_pattern_ops);
CREATE INDEX idx_students_email               ON students (email);
CREATE INDEX idx_students_dob                 ON students (date_of_birth);
CREATE INDEX idx_employees_school_status      ON employees (school_id, employee_status);
CREATE INDEX idx_employees_school_designation ON employees (school_id, designation);
CREATE INDEX idx_employees_school_department  ON employees (school_id, department);
CREATE INDEX idx_employees_is_teaching        ON employees (is_teaching);
CREATE INDEX idx_classrooms_school_status     ON classrooms (school_id, status);
CREATE INDEX idx_sections_school_status       ON sections (school_id, status);
CREATE INDEX idx_subjects_school_status       ON subjects (school_id, status);
CREATE INDEX idx_academic_years_school_current ON academic_years (school_id, is_current);
CREATE INDEX idx_attendance_school_date_status ON attendances (school_id, date, status);
CREATE INDEX idx_attendance_student_date       ON attendances (student_id, date);
CREATE INDEX idx_attendance_slot               ON attendances (timetable_slot_id);
CREATE INDEX idx_timetable_school              ON timetables (school_id);
CREATE INDEX idx_timetable_slot_timetable      ON timetable_slots (timetable_id);
CREATE INDEX idx_timetable_slot_assignment     ON timetable_slots (assignment_id);
CREATE INDEX idx_assignment_school             ON teacher_assignments (school_id);
CREATE INDEX idx_assignment_classroom_year     ON teacher_assignments (classroom_id, academic_year);
CREATE INDEX idx_assignment_teacher            ON teacher_assignments (teacher_id);
CREATE INDEX idx_assignment_subject            ON teacher_assignments (subject_id);
CREATE INDEX idx_exam_school_date              ON exams (school_id, exam_date);
CREATE INDEX idx_exam_school_year              ON exams (school_id, academic_year);
CREATE INDEX idx_exam_subject_exam             ON exam_subjects (exam_id);
CREATE INDEX idx_exam_subject_subject          ON exam_subjects (subject_id);
CREATE INDEX idx_student_exam_exam_subject     ON student_exams (exam_subject_id);
CREATE INDEX idx_result_status                 ON results (status);
CREATE INDEX idx_grading_scheme_school_active  ON grading_schemes (school_id, is_active);
CREATE INDEX idx_fee_school_status             ON fees (school_id, status);
CREATE INDEX idx_fee_school_year               ON fees (school_id, academic_year);
CREATE INDEX idx_fee_school_due                ON fees (school_id, due_date);
CREATE INDEX idx_student_fee_fee               ON student_fees (fee_id);
CREATE INDEX idx_student_fee_student_status    ON student_fees (student_id, status);
CREATE INDEX idx_fee_payment_date              ON fee_payments (payment_date);
CREATE INDEX idx_invoice_school_status         ON invoices (school_id, status);
CREATE INDEX idx_invoice_student               ON invoices (student_id);
CREATE INDEX idx_invoice_parent                ON invoices (parent_id);
CREATE INDEX idx_receipt_school_status         ON receipts (school_id, status);
CREATE INDEX idx_receipt_payment               ON receipts (fee_payment_id);
CREATE INDEX idx_receipt_invoice               ON receipts (invoice_id);
CREATE INDEX idx_notification_school_created   ON notifications (school_id, created_on DESC);
CREATE INDEX idx_notification_school_delivery  ON notifications (school_id, delivery_status, created_on DESC);
CREATE INDEX idx_announcement_school_published ON announcements (school_id, is_published, publish_date DESC);
CREATE INDEX idx_announcement_school_pinned    ON announcements (school_id, is_pinned);
CREATE INDEX idx_message_school_sender         ON messages (school_id, sender_id);
CREATE INDEX idx_message_thread                ON messages (parent_message_id);
CREATE INDEX idx_parent_user                   ON parents (user_id);
```

The three `varchar_pattern_ops` entries are what make MVP item 5's *prefix* search indexable at 50k rows; a plain
btree on a `varchar` cannot serve `LIKE 'STD12%'` under a non-`C` collation, which is the trap `§5`'s search
paragraph decided against discovering in Stage 5.

```sql
-- V5__composite_tenant_keys.sql  (ADR-020 level 3; the §4 pattern, in the same dependency order as V2)
-- schools needs no candidate key: its primary key is already (id), and the tenant is itself.
ALTER TABLE students       ADD CONSTRAINT uq_students_id_school      UNIQUE (id, school_id);
ALTER TABLE employees      ADD CONSTRAINT uq_employees_id_school     UNIQUE (id, school_id);
ALTER TABLE exams          ADD CONSTRAINT uq_exams_id_school         UNIQUE (id, school_id);
ALTER TABLE student_exams  ADD CONSTRAINT uq_student_exams_id_school UNIQUE (id, school_id);
ALTER TABLE exam_subjects  ADD CONSTRAINT uq_exam_subjects_id_school UNIQUE (id, school_id);
ALTER TABLE student_fees   ADD CONSTRAINT uq_student_fees_id_school  UNIQUE (id, school_id);
ALTER TABLE timetables     ADD CONSTRAINT uq_timetables_id_school    UNIQUE (id, school_id);

ALTER TABLE attendances         ADD CONSTRAINT fk_attendance_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);
ALTER TABLE student_exams       ADD CONSTRAINT fk_student_exam_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);
ALTER TABLE exam_subjects       ADD CONSTRAINT fk_exam_subject_exam_same_school
    FOREIGN KEY (exam_id, school_id) REFERENCES exams (id, school_id);
ALTER TABLE student_fees        ADD CONSTRAINT fk_student_fee_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);
ALTER TABLE results             ADD CONSTRAINT fk_result_student_exam_same_school
    FOREIGN KEY (student_exam_id, school_id) REFERENCES student_exams (id, school_id);
ALTER TABLE fee_payments        ADD CONSTRAINT fk_fee_payment_student_fee_same_school
    FOREIGN KEY (student_fee_id, school_id) REFERENCES student_fees (id, school_id);
ALTER TABLE timetable_slots     ADD CONSTRAINT fk_slot_timetable_same_school
    FOREIGN KEY (timetable_id, school_id) REFERENCES timetables (id, school_id);
ALTER TABLE teacher_assignments ADD CONSTRAINT fk_assignment_teacher_same_school
    FOREIGN KEY (teacher_id, school_id) REFERENCES employees (id, school_id);
ALTER TABLE invoices            ADD CONSTRAINT fk_invoice_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);
ALTER TABLE receipts            ADD CONSTRAINT fk_receipt_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);
-- The link table is the one place a tenant is *added to a relationship* rather than a row owner, and it cannot be
-- done in one statement: parent_student already has rows, so NOT NULL on a fresh column fails on contact.
ALTER TABLE parent_student ADD COLUMN school_id integer;
UPDATE parent_student ps SET school_id = s.school_id FROM students s WHERE s.id = ps.student_id;
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM parent_student WHERE school_id IS NULL) THEN
    RAISE EXCEPTION 'V5: a parent link points at a student with no school';
  END IF;
END $$;
ALTER TABLE parent_student ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE parent_student ADD CONSTRAINT fk_parent_student_student_same_school
    FOREIGN KEY (student_id, school_id) REFERENCES students (id, school_id);

-- Added by the §5.2 run rather than by the design: three foreign keys existed with no index leading on their
-- first column, and V4 could not have created them because these constraints are what this file introduces.
CREATE INDEX idx_parent_student_student ON parent_student (student_id);
CREATE INDEX idx_fee_payment_student_fee ON fee_payments (student_fee_id);
CREATE INDEX idx_receipt_student        ON receipts (student_id);
```

`timetable_slots` also references `teacher_assignments` (`assignment_id`), and that pair's tenant match is *transitive*
through two parents, so V5 constrains it to `timetables` only; the assignment check is a Stage-5 finding with a
measured query behind it rather than a constraint invented here. **What shipped as V6 (C-7, 2026-11-01) added seven more
same-school pairs** — `timetables.classroom_id`, `teacher_assignments.subject_id` and `.classroom_id`,
`student_fees.fee_id`, `exam_subjects.subject_id`, `student_exams.exam_subject_id`, `attendances.timetable_slot_id` —
and still not this one, so the transitive case above remains the open edge. It also made all ten of those columns NOT
NULL, which is what turned the pair from advisory into enforced: a composite FK with a NULL column is satisfied, not
checked.

*Numbering, corrected twice — once when `C-7` landed and once when `C-8` did:* the join tables planned below were
drafted as `V6__recipient_join_tables.sql`, moved to `V7` when `C-7` took the next free slot, and **`V7` is now taken by
a different card altogether**. `git ls-tree --name-only HEAD -r` against `db/migration/` returns
`V1__baseline_current_schema`, `V2__materialise_tenant_key`, `V3__natural_keys`, `V4__indexes`,
`V5__composite_tenant_keys`, `V6__require_the_tenant_key` and **`V7__record_who_wrote_the_pupil.sql`** — the last is
`C-8`'s authorship migration, shipped 2026-11-02 (§2's `audit` row). **The join tables below are therefore owed `V8`,
and nothing in the repository numbers them.** A card that ships them must take `V8` and must not write `V7`: reusing a
number that already has a committed file behind it is the out-of-order failure below, and this time it would be
authored rather than accidental. The `V7` that `C-7` first wrote down was itself an artefact of a draft: the file
existed briefly as `V7__require_the_tenant_key.sql`, and a local database migrated by that draft jar reported
`now at version v7`, which is when the out-of-order rule was actually observed — Flyway's default `outOfOrder=false`
refused the pending `V6` against that database. The hazard is real; the history that triggered it was this machine's, not
the branch's. What has changed since this note was written is that a v7 is now legitimate: a database migrated by this
build sits on `V7` by design, and the error case left is a *second* file claiming a number a committed file already owns.

```sql
-- V8__recipient_join_tables.sql  — NUMBER OWED, NOT SHIPPED. No file of this name is in db/migration/ (the seven that
-- are end at V7__record_who_wrote_the_pupil.sql, which C-8 took for the author trail), and this block is still the
-- design's draft rather than a migration.
-- (DB-04, BR-11, TEN-03; the TEXT columns stay and deprecate for one release)
CREATE TABLE notification_recipients (
    notification_id integer NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    user_id         integer NOT NULL REFERENCES users (id)         ON DELETE CASCADE,
    school_id       integer NOT NULL REFERENCES schools (id),
    read_at         timestamptz,
    read_by         integer REFERENCES users (id),
    PRIMARY KEY (notification_id, user_id)
);
CREATE INDEX idx_notification_recipient_unread
    ON notification_recipients (school_id, user_id, read_at) WHERE read_at IS NULL;

CREATE TABLE announcement_targets (
    announcement_id integer NOT NULL REFERENCES announcements (id) ON DELETE CASCADE,
    user_id         integer NOT NULL REFERENCES users (id)         ON DELETE CASCADE,
    school_id       integer NOT NULL REFERENCES schools (id),
    read_at         timestamptz,
    PRIMARY KEY (announcement_id, user_id)
);
CREATE TABLE message_recipients (
    message_id integer NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id    integer NOT NULL REFERENCES users (id)    ON DELETE CASCADE,
    school_id  integer NOT NULL REFERENCES schools (id),
    read_at    timestamptz,
    PRIMARY KEY (message_id, user_id)
);

-- §5.2 again: a PRIMARY KEY of (notification_id, user_id) answers "who has seen this", and nothing answers
-- "what has this user not seen" — which is the query every dashboard in MVP item 10 actually asks.
CREATE INDEX idx_notification_recipient_user ON notification_recipients (user_id, read_at);
CREATE INDEX idx_announcement_target_school  ON announcement_targets (school_id);
CREATE INDEX idx_announcement_target_user    ON announcement_targets (user_id, read_at);
CREATE INDEX idx_message_recipient_school    ON message_recipients (school_id);
CREATE INDEX idx_message_recipient_user      ON message_recipients (user_id, read_at);
CREATE INDEX idx_notification_recipient_reader
    ON notification_recipients (read_by) WHERE read_by IS NOT NULL;
```

**Rule I-2 is a query, not a promise.** The lists above were written by hand from the design, so they can miss a
foreign key the author did not think about. This is the check that makes "every foreign key gets an index" a fact —
run against a schema built only by `V1`–`V6` it returns **zero rows**, and that answer is not a transcription: the
predicate is a `RAISE EXCEPTION` inside `V4`, `V5` and `V6` — re-run rather than shared, because a guard checked only
at the moment it happens to be true is not an invariant — so a chain that applies clean *is* the zero-row result.
`V7` carries no copy of it and does not need one: it adds no relation, only eight foreign keys on `created_by` /
`updated_by`, the two column names the guard exempts by name. That means the published query has **not** been re-run
against a `V1`–`V7` schema by anything in this repository — it is expected to stay empty, and re-running it is the
`V8` card's job. It checks each constraint's *leading* column,
because the second half of a composite `(child_id, school_id)` key is never looked up on its own:

```sql
SELECT c.conname, c.conrelid::regclass AS table_name,
       a.attname AS leading_fk_column, cardinality(c.conkey) AS fk_columns
FROM pg_constraint c
JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord) ON true
JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
WHERE c.contype = 'f'
  AND k.ord = 1
  AND NOT EXISTS (
        SELECT 1 FROM pg_index i
        WHERE i.indrelid = c.conrelid AND i.indkey[0] = k.attnum
  )
ORDER BY 2, 3;
```

These statements were written against the column names in `§1–§4`, then executed — see `§5.2`, which records what the
first run rejected and what it forced this section to change. Stage 2's `B-3` re-runs them against a real V1 baseline
and a populated table, where the backfills and `ddl-auto=validate` can actually disagree with the design; the evidence
Stage 2 owes is what fails there, not what passed here.

### 5.2 What executing §5.1 found

The section above says a design that has never been parsed is "a slightly more expensive wish", so §5.1 was parsed
before Stage 1 closed. Not against the retired Neon, not against `gms` (the local development database, untouched),
and not against a Flyway baseline that does not exist yet (it does now — `§5.3` is the run against it): against **a
schema derived mechanically from the 36 entity files** — one pass reading `@Table`, `@Column`, `@JoinColumn`,
`@CollectionTable` and `@JoinTable` names out of `model/entity/` and emitting `CREATE TABLE` for 38 tables. That is the
cheapest available way to make the *column names* in this document answer to the code rather than to whoever typed
them, and it is reproducible.

Command, on a scratch cluster — **PostgreSQL 14.13** (the local Homebrew server, which is a version *below* the 16 the
compose file and `ADR-015` target, so anything this run proves about `pg14` has to be re-proved on 16 in Stage 2) —
in a database named for the session and dropped after:

```text
createdb gms_ddl_probe_s1
psql -d gms_ddl_probe_s1 -f derived_schema.sql                  # 38 tables, from the entities
for f in V2__materialise_tenant_key V3__natural_keys V4__indexes
         V5__composite_tenant_keys V6__recipient_join_tables; do   # the last is the join-table DRAFT, see below
  psql -v ON_ERROR_STOP=0 -q -d gms_ddl_probe_s1 -f $f.sql      # each in its own transaction, like Flyway
done
psql -d gms_ddl_probe_s1 -f i2_check.sql                        # the query above
```

**Result: 0 errors across the five files; the Rule I-2 query returns 0 rows.** 41 tables after the run (38 derived +
the three join tables **the draft** creates). What landed: **68** named `idx_*` indexes, **36** `uq_*` unique indexes, **144**
indexes total (primary keys included), and **11** composite foreign keys, **1** `CHECK`, **1** `PRIMARY KEY`.

*This transcript is left in the tense it was measured in, because it is a record of a run rather than a plan. Three
things have since changed about it, and the third is the one a reader reproducing the loop above will hit:* the file
drafted here as `V6__recipient_join_tables.sql` **is not a migration in the repository** — `git ls-tree --name-only
HEAD -r` against `db/migration/` holds `V1__baseline_current_schema` … `V7__record_who_wrote_the_pupil.sql`, and the
join tables are still unnumbered, now owed **`V8`** (`§5.1`'s numbering note): this draft's `V6` slot went to `C-7` and
the `V7` slot it was then promised to `C-8`'s authorship file, so neither number is free for it any more. The slot this
draft occupied belongs to a different card: the shipped **V6 requires the
tenant key** — it re-backfills and then flips `SET NOT NULL` on ten tables, holds `parent_student` (a `@ManyToMany`
join table with no entity to set the column on) with the `trg_parent_student_tenant_key` trigger, adds four
`(id, school_id)` candidate keys and seven more same-school foreign keys, re-runs the I-1 and I-2 guards, and ends by
refusing any nullable `school_id` left outside `schools`, `email_logs`, `sms_logs`. It creates **no** join tables and
**no** new `CREATE TABLE`. The probe's 11 composite foreign keys are now 18, and what the run above proves about the
draft is only that the draft parses.

Four things this run changed in the design, in the order it found them:

1. **Nine foreign keys had no index leading on their first column** — `parent_student.student_id`,
   `fee_payments.student_fee_id`, `receipts.student_id` and six in the **draft** join tables, where a
   `PRIMARY KEY (notification_id, user_id)` answers "who has seen this" but not "what has this user not seen". The
   strict form of the query (every component of every FK) reported **16** rows; the published form checks leading
   columns only, because a composite key's second half is never looked up alone. Both numbers are honest; the
   second one is the check.
2. **`ALTER TABLE parent_student ADD COLUMN school_id integer NOT NULL` cannot run.** That table has rows in any real
   database, so V5 was rewritten as add → backfill → assert → `SET NOT NULL`, which is the same shape V2 uses for
   the nine owned tables. Caught by the prose, not the parser — the parser accepts the broken form because the table
   is empty in a probe.
3. **V4 cannot index the tables the join-file draft creates.** Those index statements were drafted in the index
   file and had to move into the join-table file itself (the file that is still waiting for its number, now `V8`); a
   migration set is ordered by dependency, and this one had not been.
4. **One probe artifact, not a design defect**: `is_current` came out `integer` from the derivation script's type
   map, so `WHERE is_current` failed the first run with "argument of WHERE must be type boolean". Fixed in the
   generator, re-run clean. It is listed because the alternative — silently discarding the failing run — is how this
   repository ended up with `SEC-16`'s thirteen inert guards.

Then the constraints were attacked rather than admired, on the same migrated schema:

| Insert | Expected | Actual |
|---|---|---|
| an attendance row for a school-7 student carrying `school_id = 9` | rejected | **rejected** — `violates foreign key constraint "fk_attendance_student_same_school"` |
| the same row with `school_id = 7` | accepted | accepted (`INSERT 0 1`) |
| a second student numbered `STD100` **inside** school 7 | rejected | rejected — `uq_students_school_student_id` |
| a student numbered `STD100` in school **9** | accepted | accepted (`INSERT 0 1`) — per-school numbering is the product requirement (`ACD-08`) |
| a grading band with `min_percentage = 90, max_percentage = 80` | rejected | rejected — `ck_grading_band_order` |
| two `academic_years` with `is_current = true` in one school | rejected | rejected — `uq_academic_years_one_current` |
| a duplicate `(user_id, 'TEACHER')` row in `user_roles` | rejected | rejected — `uq_user_roles_user_role` |

The first row is the one that matters. `ADR-020`'s entire argument is that cross-tenant rows should be *impossible*
rather than *discouraged*, and that argument was, until this run, a paragraph with a SQL fragment in it. It now has a
rejection message attached. What this does **not** prove: that the real production schema has these columns, that the
backfills work on populated tables, that `EXPLAIN` uses the indexes at 50k, or that Hibernate's `validate` agrees with
any of it — all four are Stage 2's job against a real V1 baseline, and `DoD-5` stays open.

One limitation stated plainly, because `docs/README §3` already says it about the link linter and the same rule applies
here: `derived_schema.sql` was written for this run, lives in `/tmp`, and is **not committed**, so `§5.2` is a
described result rather than a checkout-reproducible one. `B-3` promotes it into the repository as
`scripts/derive-entity-schema.py`, alongside the Testcontainers harness, so that "the DDL was run" stops being a
sentence a reader has to take on trust. Until then, treat every number in this section as reported, not reproduced.

### 5.3 What Stage 2's run against the real baseline changed

`§5.2` ran `§5.1` against a schema derived from the annotations. Stage 2 ran it against the schema a server
actually produced, and the difference is where the four corrections below came from. The method, now reproducible
from a checkout rather than described: boot the application once against an empty disposable PostgreSQL 16.15 with
`ddl-auto` forced to `update` for that single probe run — **`update` is in no profile** (§1 measures `validate` in all
three), so this was a one-off override and not a setting anyone reproduces by editing a properties file — dump with
`pg_dump --schema-only`, review, and that dump is `V1__baseline_current_schema.sql`.
What Hibernate wrote on that server, measured at Stage 2's close (the dump is the baseline, so the counts below describe
`V1`–`V5`; `V6` and `V7` have since added the ten tenant flips, four more candidate keys, seven more same-school foreign
keys and eight audit keys):

| | V1 alone | after V2–V5 |
|---|---|---|
| tables | 38 | 38 |
| secondary `idx_*` indexes | **0** | 87 |
| named `uq_*` constraints | 0 (14 opaque `uk…` hashes) | 35 |
| named `uq_*` unique **indexes** on top of those | 0 | 3 — the two partial uniques §5.1 specifies (`uq_academic_years_one_current`, `uq_fee_payment_reference`) plus the one this run added (`uq_students_school_email`, correction 4) |
| composite foreign keys | 0 | 11 |
| tables carrying the tenant key | 26 | 35 |

**1. Rule I-2 needed an exemption with a number attached to it.** The unindexed-foreign-key query returns **69 rows**
against V1+V2+V3, and **62 of them are `created_by` or `updated_by`** — the audit references on **thirty-one tables**,
which is what `V1__baseline_current_schema.sql` actually carries: 28 of them reached by `BaseAuditEntity`, `Employee`
and `User` declaring their own pair, `schools` having the columns with no annotation to write into them. Indexing those
would be 62 write-time taxes for a predicate no endpoint issues, and they cannot be folded into a tenant-scoped index
because they do not carry the tenant. The rule is therefore *navigated* foreign keys, the exemption is expressed by
column name inside the assertion so an unfamiliar relation still has to earn an index, and the remaining 7
(`students.classroom_id`, `students.section_id`, `student_admissions.classroom_id`,
`financial_transactions.{school_id,student_id,employee_id,fee_payment_id}`) are indexed. The assertion is a
`RAISE EXCEPTION` in `V4`, re-run in `V5` because `V5` introduces eleven new relations, and
re-run a third time in `V6`, which introduces seven more — three files carrying the same predicate on purpose, because
the file that adds a relation is the one that can forget its index.

*What `C-8` changed here, and what it did not:* the pair is now declared once on `AuthorshipEntity` and shapes **35 of
the 36 entity classes** (`academic_years` being the only one left without it), and `V7` adds the columns plus **eight**
more audit foreign keys on `students`, `classrooms`, `subjects` and `student_admissions` — so the exempt population grew
by eight and every one of them falls inside the exemption the guard reads, because the guard matches on the column
*names* `created_by` / `updated_by` rather than on a count. At table level the chain now puts an actor column on **35 of
the 38 tables**: `V1` gave 31 of its 38 a `created_by`, `V7` adds the pair plus eight keys to the four owned tables that
lacked it, and the three left with none are `academic_years`, `parent_student` and `user_roles` — the last two join
tables, with no column for a listener to fill. The absolute figures above (**69 rows, 62 exempt**) were
measured against `V1`+`V2`+`V3` **before `V7`** and have **not been re-counted** since; a reader who wants the current
totals must re-run §5's query against a `V1`–`V7` schema rather than adjust 62 by hand.

**2. `SET NOT NULL` is a code change wearing a SQL costume.** `§5.1` flipped each materialised tenant column to NOT
NULL in the same file that added it. Against the baseline that is a production outage: `grep -in "school"` over the
ten affected entity files (`Attendance`, `ExamSubject`, `FeePayment`, `Result`, `StudentExam`, `StudentFee`,
`TeacherAssignment`, `Timetable`, `TimetableSlot`, `Parent`) returns **nothing for all ten**. No entity maps the
column, so no insert can set it, so the constraint would reject the application's own writes. `V2` adds, backfills and
*asserts* — the assertion survives, because it is a statement about the existing rows and it is the one that tells an
operator whether the data is tenant-clean — and the flip moves to `C-7` with the entity field and the service
assignment that make it true. `parent_student` has the same problem in a sharper form: `Parent.java:41-47` declares it
as `@ManyToMany @JoinTable`, so Hibernate's collection persistence inserts `(parent_id, student_id)` and nothing else.

*Closed 2026-11-01 by `C-7`, with the correction it found: the grep covered **nine** entity files, not ten, because
`parent_student` is a join table and has no file to grep — which is why its tenth flip ships as
`trg_parent_student_tenant_key` rather than an entity field. The nine now carry the mapping, eleven create paths in
eight services assign the tenant from the claim rather than from the parent row, and `V6` holds the ten flips behind
ten backfill-and-assert pairs. `verify-c7-tenant-key.sh` measured that at **66/66** on the day it shipped, and re-runs
at **65 PASS / 0 FAIL / 1 SKIP** on the `C-8` jar — the one SKIP is a check whose environment handle the run does not
expose, and the script reports SKIP rather than PASS without it — including
`rows with a NULL tenant, in any tenant table, platform-wide: 0`.*

**3. `ddl-auto=validate` agrees with the migration chain, and that is the gate.** The application boots against a
database it migrated itself: `Successfully applied 5 migrations … now at version v5`, `Started GmsApplication in 6.763
seconds`, `/actuator/health/readiness` → `UP`, and `POST /api/auth/login` → `401` — a completed database read against a
schema nobody hand-edited. Hibernate raised no validation error, which is the only claim `validate` can make and the
one that decides whether the chain and the entity model describe the same database. `pg_dump --schema-only` of two
independent runs of the chain diffs **empty** at 1503 lines each (`scripts/verify-schema.sh` §1). *Re-run 2026-11-01
after `C-7`: `Successfully applied 6 migrations … now at version v6` on a freshly created database, with
`ddl-auto=validate` and no validation error — which is the gate that proves nine new `@ManyToOne` mappings agree with
the DDL column by column, and the one thing a unit test suite that excludes JPA cannot catch.* *Re-run 2026-11-02 on the
`C-8` jar: the chain is seven migrations deep, `validate` still raises nothing, and every live figure §1 and §4 record
is measured on exactly that `V1`–`V7` database — so the gate is being re-checked by each card rather than assumed from
this run.*

The first attempt at that boot is worth recording because it is the failure mode this project keeps meeting: it used a
jar built *before* the `pom.xml` edit, so the log contains **no Flyway line at all** and the run dies at
`Schema-validation: missing table [academic_years]` — the exact error `BASELINE_STATUS` R1 attributed to the absence of
migrations. A stale artifact does not report a missing dependency; it reports a missing schema. Any gate that boots a
jar has to build it first, and `unzip -l target/*.jar | grep -c flyway` is the one-line check that says which of the
two failures is on screen.

**4. A register is a checklist, and `V3` had missed one line of it.** `DB-02` names five missing natural keys; the
first draft of `V3` shipped four and the write-up said "closed" — the finding had been checked against the migration,
not the migration against the finding. The fifth is `students.email`, and it will not take the shape the other four
took: the column is nullable (`Student.java:49`) and a blank means "this child has no mailbox", so a plain
`UNIQUE (school_id, email)` would refuse the second sibling registered without an address. It ships as a partial
unique, `uq_students_school_email … WHERE email IS NOT NULL AND email <> ''`, which is the same instrument `§5.1`
already uses for `fee_payments.payment_reference`. `employees.email` keeps the plain key because
`Employee.java:41` declares it `nullable = false` — the asymmetry is a fact about the data, not an inconsistency in
the file. `scripts/verify-schema.sh` §6 tests the predicate in both directions, because a partial unique can fail by
being too tight exactly as easily as by being decorative.

The `§5.2` attack table was re-run against this baseline by `scripts/verify-schema.sh`, which is now the checkout
reproducer `§5.2` said it was waiting for: the cross-tenant attendance row is refused by
`fk_attendance_student_same_school`, the same-tenant row is accepted, `V3` fails on a seeded duplicate admission
number with `could not create unique index "uq_students_school_student_id"`, and `DB-07` is tested in both
directions — one `invoice_number` in two schools accepted, the same number twice in one school refused. `§5.2`'s
closing caveat about uncommitted artifacts is superseded: `scripts/derive-entity-schema.py` is committed and, with the
two parser bugs that section found now fixed in it, agrees with the live server on 38 of 38 tables and 598 of 598
columns.

## 6. API contract standard

Every endpoint is under `/api/v1/…` already (36 class-level mappings, `ls gms_backend/src/main/java/com/gms/controller`)
— the version prefix survives; the rest is new
because there is no convention to reconcile with (`BKL-01`, `BKL-07`, `SEC-08`).

**Success envelope**
```json
{ "data": …, "meta": { "page": 0, "size": 25, "totalElements": 51742, "totalPages": 2070, "sorted": "lastName,asc" },
  "traceId": "…" }
```
**Error envelope** (replaces the current leak in `SEC-08`; `traceId` is the `TraceIdFilter` value, so a user report
and a log line meet each other)
```json
{ "error": { "code": "CROSS_TENANT_ACCESS", "message": "Resource not found", "details": [], "traceId": "…" } }
```
Cross-tenant misses answer **404**, not 403, where existence itself is the secret; 403 is reserved for a
well-scoped request the role may not make. *This became a property of the code rather than of the contract on
2026-11-02 (`C-4`): a by-id read naming another school's id matches no row, so the 404 comes from the query, while a
refusal a service raises itself is still 403 (`TenantAccessDeniedException`) — which is why the live scripts for
`C-1`, `C-2` and `C-7` accept either, with the reason recorded per check.*

**Collection grammar** — `page` (0-based), `size` (default 25, ceiling 100, clamped server-side), `sort`
(`property,asc|desc`, allow-listed per resource), `q` (prefix search), plus explicit filters per resource
(`status`, `classroomId`, `dueBefore`, …). Spring Data's `Pageable`/`Sort` bind the request; a `Page<T>` leaves the
service; `@PageableDefault(size = 25, sort = "lastName")` is written where defaults live — no unbounded `findAll`
survives this contract (**89 → 0**, with any genuinely bounded collection named and ceilinged). The **90** this line
carried was the Stage-1 count of unbounded-`List` endpoints; `C-1`'s removal of three inherited reads took it to the
**89** §1 measures today, and the closing number is the target, not a measurement.

**The CRUD inheritance goes away** (`ADR-023`). `AbstractCRUDController.java:30,41,52` auto-registers
`GET /{id}`, `GET` and `DELETE /{id}` for its six extendees, and `AbstractCRUDService.java:51-59` implements those
with `repository.findById` / `repository.findAll` — **no tenant predicate, no role guard, and a hard delete**.
Measured live surface on 2026-11-01: `GET /{id}` inherited-and-unguarded on 3 of 6 (`ParentController`,
`StudentController`, `EmployeeController`; `SubjectController:64`, `ClassroomController:64`, `SchoolController:81`
override it), `DELETE /{id}` inherited-and-unguarded on **all 6** — `EmployeeController.java:68` maps `/toggle`,
which does not shadow `/{id}` — and `EmployeeController.java:65-67` contains a comment recording that a hand-written
`getById` was deleted *because* the base class supplied one. `getAll` is overridden in all six, so the unfiltered
`findAll` half is not currently reachable: **closed by six copies of a rule rather than by a mechanism**, which is
why the mechanism is deletion of the base class, not six more guards. Deletion also closes the mandate's Priority-2
line *"Avoid unrestricted CRUD inheritance"* against the same six files, and it makes the count of endpoints a
person can trust: after it, the mapping count is a number that equals the routes someone wrote.

> **Implemented 2026-11-01** (Stage 3 `C-1`, on `feat/stage2-flyway-baseline`). Both base classes are deleted; 25
> service implementations and the then-35 controllers are detached from them (the directory holds 36 controller
> classes today). The inherited reads are replaced by explicit,
> school-scoped declarations on `Student`, `Employee` and `School`; the inherited deletes by guarded, school-scoped
> declarations on `Subject` and `Classroom` — the two resources the frontend actually deletes — and are not declared at
> all on `Parent`, `Student`, `Employee`, `School` and `User`, where the domain's own soft delete (`/toggle`) is the
> operation that exists. The mapping count moves **232 → 233** (3 inherited removed, 4 explicit added), the
> unbounded-`List` count **90 → 89**, the guard count **206 → 210**, and the file count **282 → 280** (the tree today
> measures 236 handlers, 234 guards and 292 files; see §1). Verification:
> `EndpointInheritanceTest` (33 cases), `scripts/verify-c1-tenancy.sh` (29 live checks, 0 failures), the
> deliberate-failure control red on exactly the two guarded assertions, full suite **78 green**. `ADR-023` carries the
> correction to the first draft's claim that the frontend had no delete call site — it
> has two — and the two 500s (a method mismatch and a refused delete) that the live run exposed.

**Health, auth and the rest** — `/actuator/health` stays unauthenticated for probes and nothing else
(`SEC-13`, `INF-15`); `anyRequest().denyAll()` (`SecurityConfig.java:152`) means every route group no matcher names is
refused rather than admitted. `BKL-04` is still open on both halves: springdoc-openapi is **not** in `gms_backend/pom.xml`,
and the chain no longer permits `/v3/api-docs` or `swagger-ui` at all — the `SecurityConfig.java:35-37` permit this
section used to cite was the audited revision (`REPOSITORY_AUDIT.md:78`), so adding the dependency now has to add the
matchers with it, and the generated document is never hand-typed.

## 7. Backend structure: boundaries without a re-packaging

The target is a modular monolith. The measured reality is **package-by-layer** (11 packages, 292 files, §1). Two
honest options existed and the second is taken:

- *Re-package into `com.gms.<module>.{api,application,domain,persistence}`* — 292 files move (280 at the decision),
  every citation in
  every register goes stale at once, every diff for the next two stages becomes a rename, and the architecture
  gained is the architecture §2 already describes. Rejected for cost with no correctness benefit
  (`ADR-021`), and noted as a **post-DoD** mechanical step if module count grows.
- *Keep the layout, enforce the boundary with a test.* A build-failing assertion in the test scope states the rules
  in §3 and §4 — one writer per table, no repository crossed into a foreign service, no entity in a response, no
  inherited `JpaRepository` read that loads a tenant row before anybody decides who may name it. A violation
  fails `mvn verify`, which already runs in CI.

**The instrument is not ArchUnit, and this section said it was.** `gms_backend/pom.xml` declares no ArchUnit
dependency — its dependencies are the Spring starters, Flyway (`pom.xml:54`, `:58`), PostgreSQL, mail, Micrometer,
Lombok, JJWT and `spring-boot-starter-test`. The boundary is held by hand-written `*ArchTest` classes under
`gms_backend/src/test/java/com/gms/` (six of them: four decide from the compiled classes, and two —
`TenantScopedReadArchTest` and `EntityLazyAccessorArchTest` — additionally walk the source tree with `Files.walk`),
not by a library. Same verdict, same CI hook — but a reviewer who searches the build for an ArchUnit
rule concludes none exists, which is how an unenforced rule gets trusted. Corrected here and flagged against
`ADR-021` (`infrastructure/INFRASTRUCTURE_DECISIONS.md:846`).

The rules, which of them an actual test enforces, and where each stands:

| Rule | Assertion | Test that enforces it | Today |
|---|---|---|---|
| D-1 one writer | `*Repository` of module A is referenced only from A's `*ServiceImpl` | **none** | fails (directly and predictably — `repository` appears in `controller`) |
| D-2 layering | no `repository` → `service`, no `entity` → `controller` | **none** | passes today, and nothing keeps it passing |
| D-3 DTOs | no `model.entity…` type in a controller return signature | **none** | **fails in 77 handler signatures** (21 bare, 56 `List<Entity>`; §3's D-3 count — the 90 it used to cite was the unbounded-`List` metric, which §1 keeps at 89) |
| D-4 tenancy | no read of a tenant row without the tenant | `TenantParameterArchTest`, `RepositoryTenantScopeArchTest`, `EntityTenantColumnArchTest`, `TenantScopedReadArchTest`, `EntityLazyAccessorArchTest` | green — declared reads carry the school or name the key that makes them global (158 of 181, 23 allow-listed with a reason); by-id reads resolve through `TenantScopedRepository`, 33 tenant-owned repositories in the mechanism and 3 exempt with a reason; the hand-written tenant comparisons the scoped load made dead are gone (23 lines / 21 checks left, 107 at `7c7789d`); inherited `findAll`/`findAllById`/`delete` stay open (§4) |
| Contract | no `ResponseEntity` inside `service/` (`BKL-01`) | **none** | fails in 65 of 76 service files |
| Security | every handler carries its own `@PreAuthorize`, or is on a named public list | `DenyByDefaultAuthorizationArchTest` | **green** — `C-5`: 236 handlers, 234 guarded, 2 deliberately public; 6 distinct authorities named; §7.1 |

Those "fails" entries are the point of the table: the architecture document ships with the measured distance between
the rules and the code, so Stage 3 and Stage 4 can close them one grep at a time instead of discovering them at
review. The rules are introduced `allow-list`-first — the current violators are enumerated in a frozen list and the
test fails only on a **new** violation — because a red build that nobody can turn green is the same as no build. The
four rows marked **none** are the honest remainder: they are assertions this document makes and the build does not.

### 7.1 Deny-by-default at the endpoint (`C-5`), stated exactly

`gms_backend/src/test/java/com/gms/security/DenyByDefaultAuthorizationArchTest.java` (7 tests) is the card's
instrument, and it is a declaration scan, not a permission system: it loads every compiled type in
`com/gms/controller` (test classes excluded, nothing initialised, no Spring context) and reads the
`RUNTIME`-retained annotations off the methods themselves — the same state Spring's handler mapping reads when it
registers the endpoint, which a source scan of a package that writes `@PreAuthorize` both above and below the mapping
annotation (and quotes it inside two javadocs — `SubjectController.java:21` and `ClassroomController.java:21`, which is
why a source grep counts 236 `@PreAuthorize` occurrences where the bytecode holds 234 guards) cannot promise. Measured
at `C-5`'s close, and printed by the test on
every build: **"C-5: 236 handlers, 234 guarded, 2 deliberately public"** and **"C-5: guards name 6 distinct
authorities: [ACCOUNTANT, ADMIN, PARENT, STUDENT, SUPERADMIN, TEACHER]"**. Before the card, 23 of the 233 handlers
carried no guard. What it enforces:

1. **Every handler method carries its own `@PreAuthorize`.** An unannotated handler fails the build unless its
   `SimpleName.methodName` is on a two-entry public list — `AuthController.login` (the credential check itself;
   `SecurityConfig.java:122`) and `HealthController.ping` (liveness probe; `SecurityConfig.java:123`). The list is
   checked for staleness in both directions: an exemption naming a handler that no longer exists is an offence, no
   exemption may match more than one handler (overloads are legal Java and separate endpoints), guarded + exempt is
   asserted to equal the whole scan, and the scan itself must find ≥200 handlers or it reports itself as broken
   rather than green.
2. **No guard may sit on the class.** `@PreAuthorize` on a type is inherited by every method, including next month's,
   so a class-level gate silently defeats rule 1; none exists today and the assertion forbids one anyway.
3. **No guard may use `hasRole` / `hasAnyRole`.** `CustomUserDetailsService` grants `SimpleGrantedAuthority(role.getCode())`
   with no `ROLE_` prefix, so those helpers would refuse every caller forever; the scan reads every annotation on every
   type, method and constructor for those literals.
4. **Every authority a guard names must exist in the granted vocabulary** (`ADMIN`, `TEACHER`, `STUDENT`, `PARENT`,
   `ACCOUNTANT`, `SUPERADMIN`), or the guard must be one of `isAuthenticated`/`permitAll`/`denyAll`; a typo becomes a
   red build instead of a permanently locked door. A companion test pins that vocabulary against `RoleEnum` **in both
   directions**, so a role added on either side alone goes red. `C-6` is what made the two agree: `ACCOUNTANT` was the
   one code the enum did not mint — granted and guarded by 39 handlers, unreachable in practice, the mismatch `SEC-06`
   recorded — and the card closed it by adding the constant rather than by deleting the guards that name it. Because
   `user_roles.role` is a `varchar(30)` with no `CHECK` constraint, no migration was needed; a school can be given an
   accountant today and a token can now carry `ACCOUNTANT`. What the constant does **not** change is the shape of the
   role: on the money module the accountant is read-only — every `POST`/`PUT`/`DELETE` on the ten fee/finance/invoice/
   receipt/transaction controllers is `hasAuthority('ADMIN')`, and only the 39 guards naming `ACCOUNTANT` sit on reads —
   and `SecurityConfig`'s `DASHBOARD` matcher (`:89-91`, applied at `:147`) admits only `ADMIN` and `SUPERADMIN`, so
   `/dashboard/school` stays out of reach. What `C-6` does **not** close is the frontend surface for that role (§8, `SEC-17`).
5. **A class that declares handlers without `@RestController`/`@Controller` fails**, because such a handler serves
   nothing and would satisfy every rule above while doing so.
6. **The public list stays at two**, so a third exemption has to be argued at `SecurityConfig` first.

Four limits the class javadoc states (`DenyByDefaultAuthorizationArchTest.java:66-91`), repeated because a guard that
hides them reads as proof: (i) it is a scan of declarations and the classpath is its universe — a controller the
component scan misses satisfies the test and serves nothing, and only a live route inventory separates those two
(`EndpointInheritanceTest` covers six of the 36); (ii) a guard is a gate, not a tenant predicate — `hasAuthority('TEACHER')`
says a teacher may reach the route, not which school's row, which is §4's subject; (iii) the rule proves the guard is
*present*, not that it is *right* — a guard the URL layer already admits passes, and what this catches is the shapes
that fail closed or silently; (iv) the two exemptions cite `SecurityConfig` by transcribed line number rather than
re-deriving the matcher list, so a card that moves those matchers must move this allow-list with it.

The URL layer has stopped being the fallback for a missing guard: `SecurityConfig.java:152` is `anyRequest().denyAll()`,
so a route no matcher names is refused at the perimeter rather than admitted to any authenticated caller. What C-5
does **not** claim is per-request authorisation testing — that is §4's mechanism and `scripts/verify-c4-owned-reads.sh`.

## 8. Frontend architecture

The frontend is already the module map: 16 directories under `gms_frontend/src/features/` that match §2's modules,
and 5 role dashboards that match §3's personas. It is **reconciled, not replaced** (the standing rule against
duplicates):

- The dashboards are no longer hardcoded and no longer count `array.length`: `DashboardController` serves
  `/dashboard/school` and `/dashboard/platform`, and `features/dashboard/AdminDashboard.jsx:74` and
  `features/dashboard/SuperAdminDashboard.jsx:61` consume them through `apiSlice.js:62-89`. `SuperAdminDashboard`
  therefore stopped deriving cross-tenant academic aggregates from list endpoints — the platform module owns no table
  (§3, D-5) — and the platform response carries platform-wide counts only (schools, users, students, employees).
  This is *wired*, and it is now *reached* — §1's live row is `scripts/verify-mvp-e2e.sh` at **294 PASS / 0 FAIL /
  0 SKIP** with **FEATURES PROVED: 11 of 11**, so every MVP feature including these dashboards answers on a
  two-tenant `V1`–`V7` database — but the *figures* have not been compared row by row against a seeded dataset, so no
  claim here says they are correct.
- Client-side slicing (`FE-08`: pages fetch the full list and slice locally) is
  replaced by §6's grammar as each resource is paged server-side — one `paginationMode="server"` change per table,
  in the same PR as that resource's paged endpoint, so no screen is ever half-paged. **1 of the 236** handler mappings
  is paged today (`GET /api/v1/students/search`, §1), so this is still nearly all of the work.
- `store/api/apiSlice.js` is the only place a path is written; the OpenAPI document (§6) becomes its check, and the
  mock mode (`VITE_USE_MOCKS`) is dev-only and must be asserted off in the prod bundle.
- The `student` and `parent` feature directories overlap (`features/student/*` vs `features/parent/*`, both holding
  child-facing reads) — one of the two will absorb the other in Stage 4 rather than both being kept. Recorded here
  so the duplication is a decision, not an accident.
- Zero tests today. Vitest + Testing Library are added in Stage 4 as *contract* tests against the envelope in §6,
  not as a coverage exercise.

## 9. Migration sequencing inside Stage 2

Order matters, and the reason is measurable: constraints fail on duplicate data, and there is no test fixture for
duplicates until one exists.

1. **V1** — baseline `CREATE TABLE` for all 36 tables from `ARCHITECTURE.md §2`'s ownership, using the *existing*
   column names so no code change is implied (`flyway baseline` is not used; V1 is authored, per `ADR-006`).
2. **V2** — add `school_id` to the 10 tenant-less tables, backfilled from their parents (`UPDATE … FROM`),
   `NOT NULL` only after the backfill is asserted zero-null. **Two corrections from what shipped:** V2 adds the column
   to **nine** tables and the tenth, `parent_student`, is added by `V5` with its composite keys; and the flip did not
   ship at all, because no entity mapped the column, so a NOT NULL there was an outage rather than a control — see
   `ENGINEERING_ROADMAP.md`'s Stage 2 finding 2. All ten `SET NOT NULL` flips arrive as `V6`, with the entity fields
   that make them true.
3. **V3** — natural-key `UNIQUE` constraints (§5), on a database seeded with a deliberate duplicate to prove the
   migration **fails** — a constraint migration that cannot fail has not been tested.
4. **V4** — indexes (§5), all `CREATE INDEX` (non-concurrent for the local stack; `CONCURRENTLY` is a
   production-only variant in the same file behind a documented flag, because `CONCURRENTLY` cannot run inside a
   transaction and Flyway runs each migration in one).
5. **V5** — composite FKs (`§4` level 3). `§9`'s original instruction was one table pair first (`students` →
   `attendances`), proven, then the rest; Stage 2 shipped all eleven at once. That is a deviation and it is recorded
   as one: the reason the step was split was fear of what it would do to real data, and the probe answered that
   question — the chain applies clean to an empty PostgreSQL 16.15 and the cross-tenant insert is refused — while the
   risk that actually remained (`validate` agreeing with the migration chain) was closed by booting the application
   against the migrated schema. A staged rollout would have bought one more observation of the same two facts.
6. **V6** — `V6__require_the_tenant_key.sql`, **shipped 2026-11-01 by `C-7`**. This is the file §4's level 3 always
   needed and §9 had no slot for: the ten `SET NOT NULL` flips Stage 2 deliberately deferred, each behind its own
   backfill-and-assert, plus four `(id, school_id)` candidate keys, seven same-school FKs, the
   `trg_parent_student_tenant_key` trigger for the join table that has no entity, and the index guards re-run. It took
   `V6` because that was the next number no committed file owned (`git ls-tree HEAD` on `db/migration/` returned
   `V1`–`V5` at that moment); the `V7` written on the original `C-7` card was a slip. What has since moved is the
   reservation below it: `C-8` took `V7` for the author trail rather than for the join tables, so the join tables'
   number is now `V8` (§5.1's numbering note). The `outOfOrder=false` rule is not a
   theory here — it fired against this machine's draft jar, where a database had been migrated to v7 and then refused a
   pending `V6`.
7. **V7** — `V7__record_who_wrote_the_pupil.sql`, **shipped 2026-11-02 by `C-8`**: `created_by` / `updated_by` plus
   eight `REFERENCES users (id)` foreign keys on `students`, `classrooms`, `subjects` and `student_admissions`, so the
   question "who registered this pupil" has a column to answer it from. The Java half is `AuthorshipEntity`, which
   `BaseAuditEntity`, `School`, `Student`, `Classroom`, `Subject` and `StudentAdmission` extend (§2's `audit` row), and
   the columns are written by `AuditingEntityListener` from the `uid` claim of the verified token — never from a value
   in the request body, and never guessed for a write that happened outside a request. Nothing is backfilled: a
   manufactured author is the one thing an audit trail must not contain, so historical rows read NULL, which is the
   truth. **The join tables this item used to name** (`notification_recipients`, `announcement_targets`,
   `message_recipients`), backfilled from the JSON-in-TEXT columns with the old columns left in place and deprecated for
   one release (`DB-04`), are **not shipped, and are owed `V8`**: a migration nobody reads is schema with no behaviour
   attached, and the read path that would use it is still open now that `C-8` has spent itself on the author trail. What
   they are *not* owed is `V7`, which a committed file already owns.
8. `ddl-auto=validate` in every profile — **done**, and as literals rather than `${JPA_DDL_AUTO:validate}`, so no
   environment variable reaches `update`. The dev/test migrate-on-start half is done with Spring Boot's own Flyway
   integration rather than a `FlywayMigrationStrategy` bean, because there was nothing to customise. The prod half is
   **not** done as written: `application-prod.properties:15` (`spring.flyway.enabled=true`) leaves Flyway enabled on
   boot, so a production migration still happens inside the process that serves traffic.
   `ADR-006`'s expand-migrate-verify-contract step belongs to a deployment pipeline, and until Stage 6 builds one,
   disabling migrations in prod would leave a fresh production database with no schema at all. That is the honest
   state, not the intended one; the card that closes it is `C-9` in [ENGINEERING_ROADMAP.md](ENGINEERING_ROADMAP.md).

V1–V7 above are not sketches: their text is `gms_backend/src/main/resources/db/migration/`, `§5.1` is the design they
came from, `§5.2` is the report of running that design against a schema derived from the entities, and `§5.3` is the
report of running it against the baseline itself. V1 has no *draft* because it cannot have one: it is what a real
server produced, captured once from a disposable database and reviewed line by line, which is the route `ADR-022`
names as its own compromise rather than the one it rejects.

## 10. Not yet decided

| # | Question | Blocked on | Default being carried |
|---|---|---|---|
| A-1 | Hibernate `@Filter`/`@Where` vs explicit method arguments for level 2 of §4 | a measured p95 difference on 50k rows (Stage 5) | explicit arguments — visible in the query, testable without a session trick |
| A-2 | ~~Composite-FK enforcement on all 10 child tables or the 5 that already have a single parent~~ | nothing — `V5` shipped all 11 at once and `V6` added 7 more, so the choice was made by measurement, not by this table | **all 18 same-school pairs enforced.** The "5 first" default assumed a risk the probe had already measured away, and "5" was itself wrong: `V5`'s own count was 11 |
| A-3 | Soft delete (`status`) as a global concept vs per-module | `BR-*` review after Stage 3 | per-module; hard delete exists nowhere after `ADR-023` |
| A-4 | Whether `platform` reads aggregates through SQL views or query DSL | Stage 5's N+1 measurements | query DSL, one aggregate per dashboard card |
| A-5 | `users.employee_id/student_id/parent_id` parallel pointers vs one principal link table | a schema decision inside V2 | keep the pointers, add the composite FKs; a `principal` refactor is post-DoD |
| A-6 | RLS as the second tenant line | Stage 7 evaluation | deferred deliberately — `§4`'s column makes it addable later |
