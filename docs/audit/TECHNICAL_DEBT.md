# GMS Technical Debt Register

**Date:** 2026-11-01 · **Baseline:** `256b8e4`
IDs reference [`REPOSITORY_AUDIT.md`](./REPOSITORY_AUDIT.md). This file adds the *engineering* dimension: effort, blast radius, blocking relationships, and — per the governing rule — **which existing asset must be repaired instead of replaced**.

## 0. Reuse-first rule applied to this codebase

No duplicate implementations are permitted where a capability already exists. Every item below names the existing asset to modify. New files are only acceptable where the audit shows nothing exists (migrations, tests, design-system primitives, tenant context).

| Capability | Existing asset to repair | Must **not** be created |
|---|---|---|
| Pagination | `UserServiceImpl.java:173` already paginates users (no controller calls it); controllers already return `ResponseEntity` | a second paging framework, a new list-response wrapper alongside the current one |
| DTO mapping | per-service `mapToResponse` methods in 35 impls | MapStruct/ModelMapper added beside hand-mapping — pick one and convert incrementally |
| Error contract | `exception/GlobalExceptionHandler` + `DomainException` (declared, unused) | a new `ApiResponse`/`Result` wrapper layered over the existing exception types |
| Tenancy | `security/TenantContext` + `JwtAuthenticationFilter` (Stage 3 `C-2`, 2026-11-01); before that, `util/SecurityUtil` call sites in controllers | a parallel `TenantFilter` that coexists with header re-parsing — `C-2` deleted `SecurityUtil` rather than adding a second path |
| Auth | `security/JwtTokenProvider`, `JwtAuthenticationFilter` | a second token service, or Spring Authorization Server alongside JWT |
| Shared UI | 9 structurally identical list pages (`SchoolsPage`, `StudentsPage`, `FeesPage`, `ExamsPage`, `AnnouncementsPage`, `ClassroomsPage`, `SubjectsPage`, `AcademicYearsPage`, `ResultsPage`) | a new component library imported next to the copies — extract once, migrate all 9, delete the duplicates |
| Validation schemas | 11 per-page Yup schemas + unused `constants/index.js:135-142` | a second schema module duplicating the first |
| HTTP client | RTK Query `apiSlice.js` (71 endpoints) | continued growth of the parallel axios stack (`apiClient.js`) — consolidate onto RTK Query |
| Role model | `enums/RoleEnum` (defined, largely unused; `user_roles` stores raw strings) | a new permission table or a `permissions` enum beside `RoleEnum` |
| Infra docs | `docs/` (6 genuine guides) | a parallel guide per subject; extend or replace the wrong one (**DOC-04**) |
| Mock layer | `services/mockBaseQuery.js` | additional mock files; scope the existing one to dev/test (**FE-02**) |
| Backup, migrations, tests, design tokens | nothing exists | genuinely new — these are the only net-new artifacts in the plan |

## 1. Debt inventory

Effort: **S** ≤ 1 day · **M** 1–4 days · **L** 1–2 weeks · **XL** multi-week.
Blast radius = what breaks if the change is done badly, given **zero** existing tests (**OPS-14**).

### Structural

| ID | Debt | Severity | Effort | Blast radius | Blocks / Blocked by | Reuse action |
|---|---|---|---|---|---|---|
| BKL-01 | Services return `ResponseEntity`; HTTP semantics inside business logic | P2 | L | every controller; 34 impls | blocks clean exception handling (**SEC-08**) | convert services to throw `DomainException`/`NotFoundException`; controllers map to HTTP. One module at a time, starting with the smallest impls, so no half-migrated module ships |
| BKL-02 | Raw JPA entities serialized while `open-in-view=false` and relations are LAZY | P1 | M | 500s on ~28 controllers' list endpoints | blocked by the DTO pass below | extend the `*Response` DTOs that already exist for the same entities — do not add new response types |
| BKL-03 | No pagination anywhere; ~20 unbounded `findAll` | P1 | M | memory/payload growth; UI already pretends | blocked by **FE-08** for the UI half | adopt `Pageable` in the existing controller signatures; RTK Query endpoints gain `page/pageSize` args |
| BKL-04 | `BeanUtils.copyProperties` with hand-maintained ignore lists; two call sites with **no** exclusions | P2 | M | silent mass-assignment (**SEC-04**) | **SEC-04** must land first | replace reflection copy with explicit mapping in the same method, field by field |
| BKL-05 | Dead/misleading code: unused `DomainException`, unused import, `UnsupportedOperationException` on a mapped route, `main()` utilities + a `.class` in `src/main` | P2 | S | none | — | delete; wire or drop. Mandate §21 |
| BKL-06 | 204 vs `[]` for empty lists | P2 | S | frontend branching | — | pick one convention, apply in the shared exception/advice layer |
| BKL-07 | `Constants.API_BASE_V1` used by only 12 of 36 controllers (`grep -rl API_BASE_V1 controller/ \| wc -l`); the other 24 hardcode `/api/v1` | P3 | S | path drift | — | replace literals with the constant |
| BKL-08 | Two audit-column conventions (`created_on` vs `created_at`), three entities carrying both | P2 | M | every report/query touching audits; migration surface | blocked by **OPS-07** (no schema baseline) | choose one via migration; do not add a third |
| BKL-09 | `RoleEnum` exists but roles persist as raw strings in `user_roles`, and a sixth value `"EMPLOYEE"` is created at runtime (**SEC-09**) — **CLOSED on branch by `C-6` (2026-11-02, `feat/stage2-flyway-baseline`)**: `RoleEnum` now mints exactly six authorities (ACCOUNTANT, ADMIN, PARENT, STUDENT, SUPERADMIN, TEACHER; `ACCOUNTANT` at `RoleEnum.java:11`) and the string `"EMPLOYEE"` is minted **nowhere** — `/auth/self-register` routes through `UserService.authoriseRoles`, so no runtime value escapes the enum | P1 | M | every authorization rule | **SEC-06** depends on this | `C-6` chose the second option — keep strings, enforce one constant source (`RoleEnum` behind `UserService.authoriseRoles`). Honest remainder, none of it on a live path: finance writes are still guarded `hasAuthority('ADMIN')` and `apiSlice.js` has no finance-read endpoints beyond fees, so ACCOUNTANT is mintable but not yet wired to the finance perimeter; `authService.getUserRole()` and `authSlice.selectUserRole` still return `roles?.[0]`, which against the now-sorted claim is the alphabetically-first role rather than the most privileged — both should delegate to `getPrimaryRole` |
| BKL-10 | The error contract maps four client errors and leaves the rest to the catch-all, so a malformed body, a wrong media type or a missing query parameter answers **500** | P2 | S | every caller that sends a malformed request | — | extend the existing `GlobalExceptionHandler` with `HttpMediaTypeNotSupportedException` → 415, `HttpMessageNotReadableException` → 400, `MissingServletRequestParameterException` → 400; keep the `{traceId, path, error, message}` envelope. Stage 4's error-contract card (**BKL-01**, **BKL-07**) is where the envelope itself is unified — do not build a second one here |

### Data layer

Stage 2 moved most of this register — **on a branch**. "Closed on branch" below means the SQL exists in
`gms_backend/src/main/resources/db/migration/` on `feat/stage2-flyway-baseline` and is proved by
[`scripts/verify-schema.sh`](../../scripts/verify-schema.sh); it does **not** mean `master` is fixed. Re-measure with the
grep in each row's *Reuse action* against `master` and the finding is still true there. `ENGINEERING_ROADMAP.md` §"Stage 2
result, measured" holds the numbers.

| ID | Debt | Severity | Effort | Notes | Reuse action |
|---|---|---|---|---|---|
| OPS-07 | No schema in version control; `ddl-auto` conflicts across three files; Flyway enabled but not a dependency | **P0 (ops)** | L | **Closed on branch.** `flyway-core` **and** `flyway-database-postgresql` are now in `pom.xml` (Flyway 10 split the Postgres dialect out of core; without the second artifact boot fails with *"Unsupported Database: PostgreSQL 16.15"*), **seven** migrations exist (`V1`–`V7`; `C-8` has since landed `V7__record_who_wrote_the_pupil.sql` for the audit pair — that reservation is why `C-7` shipped its column as `V6` rather than where its card said it would), and all three profiles say the literal `validate` with the `${JPA_DDL_AUTO:…}` default deleted so no environment variable can restore `update`. Still open: no CI job runs the chain, and `application-prod.properties:15` (`spring.flyway.enabled=true`) still migrates on boot rather than as a deployment step — `C-9` | add `flyway-core` + baseline from an authorised schema export; then delete the `update` override in compose — **done**, `ADR-022` carries the verification record |
| DB-01 | `validate` against a schema that exists only in Neon | P1 | – | **Closed on branch.** `V1` is the baseline, captured from a disposable Postgres 16.15 server and reviewed; a Flyway-migrated database now passes Hibernate `validate` on boot (`Successfully validated 5 migrations`) | same fix |
| DB-02 | Missing unique constraints: `employees.email`, `students.email`, `classrooms(name,grade,section)`, `attendance(student,slot,date)`, `timetable_slots(day,period)` | P1 | M | **Closed on branch** — all five, in `V3`. `students.email` needed a partial index (`WHERE email IS NOT NULL AND email <> ''`) because the column is nullable and blank means "no mailbox", not "shared mailbox"; `employees.email` is `nullable = false`, so it takes a plain key. Duplicates were not cleaned because the migrated database has no rows to clean — on a live schema this migration **will** fail on existing duplicates, which is the intended behaviour, not a defect | add via migration with a duplicate-cleanup step; constraints, not more `existsBy` checks — **done**, 35 named uniques measured |
| DB-03 | No indexes despite `school_id + status + date` patterns | P2 | S | **Closed on branch.** `V4` creates them and `V4`/`V5` assert two invariants in SQL: every tenant-keyed table has an index leading on `school_id` (**I-1**), every navigated FK is indexed on its first column (**I-2**, amended by measurement — 62 `created_by`/`updated_by` references are exempt, by name and with a reason, because no entity navigates them). Measured: 87 secondary indexes on 38 tables, from 0 | composite `(school_id, …)` indexes in migrations — **done** |
| DB-04 | JSON-in-TEXT sets (`recipient_ids`, `target_ids`, `report_data`) | P1 | L | root cause of **TEN-03** | normalise into join tables — replaces the LIKE query, no new query mechanism — **not started; `C-8`** |
| DB-05 | `Parent` global, `Employee`/`Student` emails globally unique-ish but unenforced | P1 | M | **TEN-05**. `V3` names the `parents.email` global key and records it as owner decision D-2 rather than silently changing its scope | decide the model explicitly in a migration + ADR, then enforce — **half done**: enforced, not decided |
| DB-06 | No soft-delete concept despite README claiming one; three inconsistent status styles | P1 | M | `countActiveSchools` and employee-ACTIVE counters return 0 permanently | one status enum (**FIN-07**) + a `status` column convention; do **not** add a `deleted` flag beside `status` |
| DB-07 | Four globally-`unique` natural keys that are logically per-school (`designations.designation_id`, `invoices.invoice_number`, `receipts.receipt_number`, `communication_templates.template_code`) | P1 | S | **Closed on branch**, and checked in *both* directions rather than one: the same invoice number is accepted in two schools and refused inside one. A migration that relaxes a constraint needs a test that the constraint still binds somewhere | `uq (school_id, …)` in the V3 migration; measured with `grep -rn "unique = true" model/entity/` → 8 — **done**, `V3` |
| DB-08 | `uq_timetable_classroom_year` is platform-wide, so a cross-tenant timetable write is refused by the wrong rule | P1 | S | found by measurement on 2026-11-01 while verifying `C-7`: school B naming school A's classroom got **409 "already exists"** because the unique is evaluated before `fk_timetable_classroom_same_school`, and the body disclosed that a timetable for another school's classroom and year exists. `GlobalExceptionHandler`'s `*_same_school` branch now answers 403, which fixes the response and not the key | `V5`'s `(id, school_id)` candidate-key pattern applied to the unique: replace it with `uq (school_id, classroom_id, academic_year)` in the Stage 4 timetable migration, and assert in `verify-c7-tenant-key.sh` §6 that the refusal arrives from the FK, not from the unique — the current script already sidesteps the collision with a distinct `academic_year`, which is a workaround worth deleting |
| DB-09 | A row does not record who wrote it — the actor columns `created_by`/`updated_by` are absent from the tables whose writes need attribution | P2 | M | **Closed on branch for writes by `C-8` (2026-11-02, `V7__record_who_wrote_the_pupil.sql`).** `V1` had already given 31 of its 38 tables a `created_by` (62 audit FKs, all `NO ACTION`); `V7` adds the pair plus 8 keys to `students`, `classrooms`, `subjects`, `student_admissions`, so 35 of 38 tables now carry an actor column and 70 audit FKs exist in total. 33 of the 36 entity classes are stamped by the JPA auditing listener; `Employee` and `User` set a business `created_by` by hand, and `AcademicYear` carries none. What `C-8` does **not** close: 5 hard-delete paths destroy the trail, no API surfaces the trail to a school, and bootstrap/scheduled writes legitimately leave `created_by` NULL with nothing distinguishing "system wrote this" from "no one recorded this" | the actor columns already sit on `AuthorshipEntity`/`BaseAuditEntity` and the listener already stamps them — extend the same `V7` key pattern rather than a second auditing mechanism; the three residues above are separate cards, not a re-do of this one |
| FIN-03 | No `@Transactional` in 11 financial/communication services | **P0 (integrity)** | S | atomicity bugs today | annotations only, but must precede **FIN-04/05** |

### Service layer

| ID | Debt | Severity | Effort | Reuse action |
|---|---|---|---|---|
| TEN-01…07 | Tenancy as a convention; 15+ endpoints accept and ignore `schoolId` | **P0** | L | one `TenantContext` + a mandatory tenant in every repository query — **the code half is done on branch (`feat/stage2-flyway-baseline`, 2026-11-01)**: `C-2` deleted `util/SecurityUtil`, parses the token once per request into `security/TenantContext`, forbids a tenant parameter on any service method (`TenantParameterArchTest`) and answers a cross-tenant refusal with 403; `C-3` put the tenant in the query — `RepositoryTenantScopeArchTest` measures 55 declared reads with no tenant at the end of `C-2` (37 indefensible) and 18 now, each allow-listed with the constraint that makes it global by design, and its generated SQL was captured to prove the predicate reaches the database. **Still open at that writing**: the tenant key the schema declares but the application never writes, the `findById`/`findAll`/`findAllById`/`delete` that `JpaRepository` hands every repository unscoped, and deny-by-default on the endpoints themselves. The first and third have since closed outright, and the second closed for by-id reads — each by a mechanism rather than a re-count: `C-7` mapped and required the column (`V6`); `C-4` moved the by-id read into `TenantScopedRepository.findByIdOwnedBy` (`:44-45`, `:56-61`, extended by 33 of the 36 interfaces — the three tenant-less ones, `ParentRepository`, `SchoolRepository`, `DashboardStatisticsRepository`, are the named exemptions) and `security/DenyByDefaultAuthorizationArchTest` refuses a handler without its own `@PreAuthorize` (234 guards over 236 handlers, 2 named exemptions). What that row still owes is inherited `findAll`/`findAllById`/`delete` (`TenantScopedReadArchTest.java:39-41`) and the perimeter inventory that `denyAll()` makes a correctness question (`ParentController`'s four administrative routes match no path rule). `C-6`'s role vocabulary is no longer owed: `ACCOUNTANT` is a `RoleEnum` constant since 2026-11-02, `"EMPLOYEE"` is minted nowhere, and the mintability of every authority a guard names is one of `C-5`'s checks. Read the correction on the leak count in [SECURITY_AUDIT.md §5.5](SECURITY_AUDIT.md): the site counts here and in `BASELINE_STATUS §17.6` were never a count of foreign rows returned. |
| FIN-01 | Payments marked SUCCESS from client input | **P0** | M | extend the existing `FeePayment` status field into a state machine; verification is a *new* concern (webhook) and belongs in a new handler, not a new entity |
| FIN-04 | Invoice/Receipt orphaned; `FeeStructure` written, never read | P1 | L | wire the existing FK columns (`invoice.student`, `receipt.fee_payment`) — they already exist, they are just never populated |
| FIN-05 | Balance arithmetic wrong on update/delete/overpay | P1 | M | recompute in the existing `updateBalance` path, add guard + idempotency key |
| FIN-06 | Fabricated dashboard maths; "delete" that doesn't delete | P1 | S | fix the existing service methods; no new report engine |
| ACD-01 | `ParentServiceImpl.create` persists an empty entity | P1 | S | populate in the existing method |
| ACD-02 | Seven `getMy*` stubs returning `emptyList` | P1 | M | implement inside the existing methods |
| ACD-03/04/06/07 | Attendance %, grade derivation, timetable conflicts, exam validation | P1 | M | extend the existing `ResultServiceImpl`/`GradingSchemeServiceImpl`/`TimetableServiceImpl` — `GradingScheme` already holds the bands, it is simply never consulted |
| ACD-08 | Global counters for admission/designation ids; racy username generation | P1 | M | replace `count()+1` with a DB sequence in a migration; keep the existing generator signature |
| ACD-11 | School provisioning creates no academic scaffolding | P1 | M | extend `SchoolServiceImpl.create` — the bootstrap pattern already exists there |
| COM-01/02 | Notifications never sent; `CommunicationServiceImpl` returns fake success | P1 | M | implement the existing stubbed methods or delete the endpoints; mandate §25 rule 12 forbids the lie |

### Frontend

| ID | Debt | Severity | Effort | Reuse action |
|---|---|---|---|---|
| FE-01 | Path/payload/read mismatches across ~14 endpoints | **P0 for functionality** | M | correct the existing `apiSlice.js` endpoint definitions and page payloads toward the backend contract — do not add adapter shims |
| FE-02 | Mock mode one env var from production, no indicator | P1 | S | scope the existing `mockBaseQuery` to `import.meta.env.DEV`; document the flag in a new `.env.example` (net-new) |
| FE-03/04 | Client-decoded role as authorization; 401 handling in the unused axios stack | P1 | M | add `baseQueryWithReauth` to the existing `apiSlice`; delete the parallel axios client once the 2 auth calls move to RTK |
| FE-05 | Unreachable `/unauthorized`, dead splat, side effects in render | P2 | S | fix the existing `App.jsx`/`ProtectedRoute.jsx` |
| FE-06/07 | Fabricated metrics; Parent dashboard throws | P1 | S | wire existing endpoints; add the missing `getParentProfile` endpoint to the existing builder |
| FE-09 | No shared components; 9 duplicated pages; dead View buttons | P2 | L | extract from the copies into `components/common/` (which today holds only `ProtectedRoute.jsx`), migrate all 9, then delete the duplicated markup — the reuse rule's main frontend obligation |
| FE-10 | 11 local Yup schemas, unused shared constants, `context` vs `when` bug | P2 | M | one `features/*/schemas.js` or a shared module seeded **from** `VALIDATION_MESSAGES`, then remove the locals |
| FE-12/13/14 | Inline theme, dead `uiSlice`, `persist` whitelist, no test runner, duplicate HTTP stacks | P3 | M | move the existing `createTheme` into a theme module; delete dead slices; add Vitest (net-new) |
| FE-15 | A delegated frontend edit introduced a `WC` icon import that does not exist in the installed MUI version; it survived review only because nothing built the frontend in that window | P2 | S | process rule, not a code edit: an agent that edits frontend source runs `npm run build` before reporting, or the main agent builds before committing the edit — a bad import is a build failure, and the build is the only thing that catches it (the frontend counterpart of `OPS-18`/`OPS-19`) |

### Infrastructure

| ID | Debt | Severity | Effort | Reuse action |
|---|---|---|---|---|
| OPS-01/02 | Deploy with no gate, via an untracked remote script | **P0** | M | build CI around the existing `deploy-backend.yml`; bring `deploy_backend.sh` into the repo |
| OPS-03/04/05 | Host-key verification off, secrets echoed, cancel-in-progress on deploys | P1 | S | edits to the existing workflow |
| OPS-08/09 | Compose unusable from a clean clone; Makefile on EOL v1 | P1 | S | repair the existing files; add `docker compose` and ship `.env.example` defaults so no `.env` is required |
| OPS-10 | Frontend image cannot reach the API; root containers, no healthcheck, unpinned tags | P2 | M | add the missing `/api` block to the existing `nginx.conf`, or delete the frontend Dockerfile as dead — decide, don't maintain both |
| OPS-11/12 | DEBUG by default; JaCoCo declared but inert | P2 | S | profile-appropriate levels; give JaCoCo real executions and a CI gate |
| OPS-13 | No Spring profiles; multipart disabled while attachment columns exist | P1 | M | add `application-dev/prod` (net-new) rather than more env-var guessing |
| OPS-14 | **Zero tests** | **P0 enabler** | L | net-new: backend integration + tenant matrix, frontend Vitest, Playwright journeys |
| OPS-15/16 | gitignore gaps; no backup procedure | P1/P0 | S | extend `.gitignore`; write the backup scripts (net-new) |
| OPS-18 | `mvn package` without `clean` ships resources the source no longer has (found 2026-11-01 by renaming a migration and booting the jar: Flyway applied V6 then the ghost V7 and died on `42710 trigger already exists`) | P1 | S | no new mechanism — `clean` belongs in every command that produces a deployable artifact (`Makefile`, `ci.yml`'s packaging step if one is added), plus `unzip -l target/*.jar \| grep db/migration` compared against `ls src/main/resources/db/migration/`. CI is safe today because it runs `mvn -B verify` on a fresh checkout; the local `package` is what lies |
| OPS-19 | Repackaging under a live JVM: `mvn -o package` while a boot `java -jar` is running from the same artifact overwrites a jar the running JVM still maps, so a subsequent run silently tests a stale build — this session recorded a schema run that "logged no Flyway line at all" for exactly that reason | P1 | S | widen the `OPS-18` rule: `clean` before `package`, and never repackage while a JVM holds the jar — stop the boot process, or build to a distinct artifact, before the next run |
| OPS-20 | The Colima outage surfaced as an application error: when the container runtime stopped, the live verification scripts reported `Connection refused`, which reads as a backend defect rather than "the database is not running" | P2 | S | before the scripts report a connection error they check that the runtime and the database are each up, and distinguish a dead runtime from a dead database — a false application defect costs more triage than the outage it masks |

## 2. Ranked backlog

Ordered by (risk removed ÷ effort), with the dependency edges that matter.

**Sequencing moved on 2026-11-01.** The execution mandate replaced issue-by-issue work with one roadmap, so
[../ENGINEERING_ROADMAP.md](../ENGINEERING_ROADMAP.md) owns *when a package is done*; this table owns *what the units
of work are and which finding ids each resolves*. The two disagree in exactly three places — Flyway's schema baseline,
the compose foundation, and whether tenancy or pagination comes first — and all three are argued in
[../ENGINEERING_ROADMAP.md §4](../ENGINEERING_ROADMAP.md) rather than silently resolved here. **Package 13 is
discharged** (PRD, architecture, ADRs `019…023`, roadmap: see [../README.md §1](../README.md)); its `docs/` repair
half was closed earlier by the index itself.

| # | Package | Resolves | Depends on | Est. |
|---|---|---|---|---|
| 1 | Fail-closed configuration + `prod` profile + remove insecure defaults | SEC-01*, SEC-02, SEC-03, OPS-11, OPS-13 | owner credential rotation | M |
| 2 | Delete credential logging, response-leak and dead endpoints | SEC-07, SEC-08, BKL-05, BKL-06, BKL-07 | — | S |
| 3 | Close escalation/destruction holes: roles, inherited CRUD, self-register | SEC-04, SEC-05, SEC-09, SEC-12, SEC-13, BKL-08 | 1 | M |
| 4 | Test harness + CI gate (build, unit, integration, coverage) | OPS-01, OPS-14, OPS-12 | 2 | L |
| 5 | Tenant invariant: `TenantContext`, tenant in every query, ownership guard | TEN-01…07, SEC-06 | 3, 4 | L — **the first two halves shipped 2026-11-01, and the third arrived from an unexpected direction**: `C-2` gave every service one way to learn the tenant (86 tests green, live 32/32), `C-3` put it in every declared query (87 green, live 25/25, 37 unscoped reads fixed, 18 allow-listed), and `C-7` — not on this package's original list — made the database enforce it, with nine entity mappings, ten `SET NOT NULL` as `V6`, seven more same-school keys and a trigger for the join table that has no entity (89 green, live 66/66). Still open in this package when this row was written: the ownership guard on inherited reads (`C-4`), deny-by-default at the endpoint (`C-5`), and the role vocabulary (`C-6`). The first two closed after it — `TenantScopedRepository.findByIdOwnedBy` puts the tenant in the by-id query for 33 of the 36 repository interfaces, and `DenyByDefaultAuthorizationArchTest` refuses a handler with no guard of its own — and their residue is inherited `findAll`/`findAllById`/`delete`, the perimeter inventory that `denyAll()` makes a correctness question (`ParentController`'s four administrative routes match no rule), and the role vocabulary `C-6` has since closed — `ACCOUNTANT` is a `RoleEnum` constant and `"EMPLOYEE"` is minted nowhere |
| 6 | Schema baseline via Flyway + constraints + indexes | OPS-07, DB-01…06 | 4 | L |
| 7 | Compose v2 + Makefile + non-root images + working nginx proxy | OPS-08/09/10, SEC-14 | — | M |
| 8 | Financial integrity: transactions, state machine, linkage, real metrics | FIN-01…08 | 5, 6 | XL |
| 9 | Academic correctness: `%`, grades, conflicts, ids, scaffolding | ACD-01…12 | 5, 6 | L |
| 10 | API contracts: DTOs, pagination, validation, OpenAPI | BKL-01…04, API-05, ACD-09 | 5 | L |
| 11 | Frontend contract repair + mock scoping + auth-state | FE-01…08 | 10 | L |
| 12 | Design system + shared components, migrate 9 pages | FE-09…12 | 11 | XL |
| 13 | Docs: PRD, architecture, ADRs, roadmap; repair `docs/` | DOC-01…05 | — | M |
| 14 | E2E, backups, observability, RLS evaluation | OPS-16, mandate §16/§17 | 4, 7 | L |

\* SEC-01's repository half (placeholders + fail-fast) is code work; the rotation itself is an owner action outside this repo.

## 2.1 Packages 1–3 after M1 — planned versus actually closed

M1 was scoped as package 1, package 2 and the low-risk half of package 3. It landed:

| Planned | Landed | Evidence |
|---|---|---|
| 1 · Fail-closed config, `prod` profile, insecure defaults | **yes**, on the repository side | `StartupSecurityValidator`, the `application-dev`/`application-prod` split, `${…}` placeholders everywhere; owner credential rotation still outstanding |
| 2 · Credential logging, response leaks, dead packaged utilities | **yes** — SEC-07, SEC-08, BKL-05, BKL-07 | the four `util/Password*` files and the committed `.class` are gone; 36 tests execute; the probe table in BASELINE_STATUS §13.4 |
| 3 · Escalation/destruction holes | **partial by design** — SEC-04, SEC-09, SEC-13, SEC-16 closed; **SEC-05 and SEC-12 left open** | SEC-05 needs the single ownership guard from package 5: patching six controllers by hand would create six copies of a rule the mandate asks to express once. SEC-12 belongs with the financial package. |
| 4 · Test harness + CI gate | **started early, not finished** | backend unit tests, `ci.yml` and JaCoCo exist; Testcontainers, Vitest/RTL, Playwright and the Postman repair are M3 |
| 5 · Tenant invariant | **not started** | next branch, `fix/gms-tenant-isolation` |

The planning error worth keeping: this backlog assumed the ordering constraint that bites is *between code packages*.
It is not — it is *between code and owner action*. Nothing in packages 1–3 can be claimed for the live system until the
credential is rotated, the new secrets are injected on the host, and the CI checks are marked required, and the host
itself is currently unreachable enough that even the claim "the deployed system is the one we audited" is unproven.

## 3. What must not be touched yet

* `School`/`Student`/`FeePayment`/`Invoice`/`Receipt` entity fields, until **OPS-07** provides a migration path — there is currently no way to change a column safely.
* Any `ddl-auto` behaviour against the live database (**mandate §9.1** and §25 rules 1–3): no schema export has been authorised yet.
* `docker-compose` `update` → `validate` flip until a baseline migration exists, or local startup breaks.
* The 9 duplicated pages' markup until their data contracts (**FE-01**) are fixed, or a refactor silently changes what each page displays with no test to notice.
* `.github/workflows/deploy-backend.yml` triggers, until a test job exists to guard them — narrowing the trigger without a gate just hides the risk.
  **Status after M1: this constraint was lifted deliberately, and the reason is recorded here rather than buried in a diff.** M1 added `.github/workflows/ci.yml`
  (backend `mvn verify` + frontend lint/build on every PR and every push to `master`) and narrowed the deploy `paths:` to backend/runtime inputs. That is a gate
  *authored*, and it has now **executed** — the first `ci.yml` run on PR #10 returned `Backend build and tests=success`
  and `Frontend lint and build=success` on ubuntu-latest — but it is not yet *enforced*: branch protection lists
  `required_status_checks: []`, so a green CI is not what stops a merge today. What the narrowing does change is one
  specific real hazard — a documentation-only commit to `master` used to SSH into production. Enforcing the checks is
  the owner action in BASELINE_STATUS §11 item 5.
* **`EC2_SSH_KNOWN_HOSTS` must stay unset, and the EC2 host must stay unreachable, until Infra-M2 and Infra-M3 land.**
  This is the one item on this list that is a *safety* constraint rather than a sequencing preference: the deploy
  workflow now executes on every `master` push and is stopped only by that missing secret
  (run 41245769175, recorded in [INFRASTRUCTURE_AUDIT.md §6.2](../infrastructure/INFRASTRUCTURE_AUDIT.md)). Adding
  the secret, or restoring port 22, hands merge-to-`master` an ungated production deploy with no build step upstream.
* Platform debt has its own register and is not repeated here: findings `INF-01…23` live in
  **[docs/infrastructure/INFRASTRUCTURE_AUDIT.md](../infrastructure/INFRASTRUCTURE_AUDIT.md)**, their status in
  **[PRODUCTION_READINESS_MATRIX.md](../infrastructure/PRODUCTION_READINESS_MATRIX.md)**, and the reasoning behind each
  chosen approach in **[INFRASTRUCTURE_DECISIONS.md](../infrastructure/INFRASTRUCTURE_DECISIONS.md)**. This file keeps
  the application's debt; cross-references between the two are by finding id.

## 4. Debt quantification

| Metric | Value |
|---|---|
| Backend Java files / LOC | **283 / 17,997** (measured `find` + `wc -l`, 2026-11-01) |
| Frontend source files / LOC | **48 / 11,119** — of which 45 are JS/JSX (11,009 LOC) |
| Controllers | 36 (230 mapping annotations: 128 GET, 44 POST, 30 PUT, 23 DELETE, 5 PATCH) |
| Entities / repositories / service impls | 37 / 35 / 34 |
| RTK Query endpoints | 71 (34 queries, 37 mutations) |
| Test classes / methods | **0 / 0** — `src/test` does not exist; `mvn test` prints `No tests to run` and exits green |
| Migrations | **0** `.sql` files, no migration dependency; the app cannot start on a fresh database |
| Schema facts (measured on a disposable DB) | 38 tables, 26 with `school_id`, **12 without**, 123 FKs, 14 unique constraints, **0 secondary indexes** — see BASELINE_STATUS §6 |
| `@Transactional` in financial services | **0 of 10** fee/exam/result service impls; `AttendanceServiceImpl` alone carries 8 |
| Controllers with no method security | 6 |
| Controllers with no matching path rule | 20 of 36 |
| P0 findings open | 9 |
| Postman request coverage | **45 requests / 230 mappings ≈ 19%** |
| Health endpoint | `/actuator/health` = `DOWN` in every environment without SMTP (OPS-17) — breaks the Compose healthcheck |
| Markdown files that are genuine documentation | 6 of 59 |

**Eight rows of this table are the baseline, not the tree** — the same commands re-run on 2026-11-02 give:
**36** controllers (`AbstractCRUDController`, deleted by `C-1`, is not among them; `DashboardController` is the newest),
236 handler mappings + 36 class-level `@RequestMapping`; **39** entity-package files (36 `@Table` classes,
`BaseAuditEntity`, `TenantOwned`, `AuthorshipEntity`), 37 repository files (36 interfaces plus `TenantScopedRepository`),
35 service impls; **16** backend test classes under `src/test/java/com/gms/` and `mvn test` no longer has "nothing to
run"; **7** `.sql` migrations, `V1`–`V7`, with `flyway-core` at `gms_backend/pom.xml:54`; the schema facts, as
re-measured on the migrated database **at `V6` and *before* `V7`**, are in REPOSITORY_AUDIT §4 (**39** tables, 35
carrying the integer `school_id`, 141 FKs of which 18 are same-school pairs, **0** tables left without a tenant column
except `parents` and `user_roles`) — `C-8`'s `V7__record_who_wrote_the_pupil.sql` adds 8 audit FKs to that FK total
(141 → 149) and the `created_by`/`updated_by` pair to 4 more tables' actor columns (`students`, `classrooms`, `subjects`,
`student_admissions`), but the schema totals have **not been re-counted since `V7`**, so every number in that clause is a
pre-`V7` measurement and is labelled as such; controllers with **no** method security: **0** (234 guards over 236
handlers, two named exemptions — `AuthController.login` and `HealthController.ping`, both deliberately public); the "20
of 36 with no matching path rule" row is stale in a way that changes its meaning — the catch-all is
`anyRequest().denyAll()` (`SecurityConfig.java:152`), so an unmatched route is now *refused*, which is a different debt
(a route can be missing from the perimeter and no test says so) rather than a disclosure one.

Every figure above is reproducible from the commands in [BASELINE_STATUS.md](BASELINE_STATUS.md) §2, §6 and §9; that
file is the source of truth if these two documents ever disagree.

