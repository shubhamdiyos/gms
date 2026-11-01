# GMS Technical Debt Register

**Date:** 2026-11-01 · **Baseline:** `256b8e4`
IDs reference [`REPOSITORY_AUDIT.md`](./REPOSITORY_AUDIT.md). This file adds the *engineering* dimension: effort, blast radius, blocking relationships, and — per the governing rule — **which existing asset must be repaired instead of replaced**.

## 0. Reuse-first rule applied to this codebase

No duplicate implementations are permitted where a capability already exists. Every item below names the existing asset to modify. New files are only acceptable where the audit shows nothing exists (migrations, tests, design-system primitives, tenant context).

| Capability | Existing asset to repair | Must **not** be created |
|---|---|---|
| Pagination | `UserServiceImpl.java:173` already paginates users (no controller calls it); controllers already return `ResponseEntity` | a second paging framework, a new list-response wrapper alongside the current one |
| DTO mapping | per-service `mapToResponse` methods in 34 impls | MapStruct/ModelMapper added beside hand-mapping — pick one and convert incrementally |
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
| BKL-07 | `Constants.API_BASE_V1` used by only ~14 of 36 controllers; the rest hardcode `/api/v1` | P3 | S | path drift | — | replace literals with the constant |
| BKL-08 | Two audit-column conventions (`created_on` vs `created_at`), three entities carrying both | P2 | M | every report/query touching audits; migration surface | blocked by **OPS-07** (no schema baseline) | choose one via migration; do not add a third |
| BKL-09 | `RoleEnum` exists but roles persist as raw strings in `user_roles`, and a sixth value `"EMPLOYEE"` is created at runtime (**SEC-09**) | P1 | M | every authorization rule | **SEC-06** depends on this | either map `RoleEnum` via `@Enumerated(STRING)` or keep strings and enforce one constant source — not both |
| BKL-10 | The error contract maps four client errors and leaves the rest to the catch-all, so a malformed body, a wrong media type or a missing query parameter answers **500** | P2 | S | every caller that sends a malformed request | — | extend the existing `GlobalExceptionHandler` with `HttpMediaTypeNotSupportedException` → 415, `HttpMessageNotReadableException` → 400, `MissingServletRequestParameterException` → 400; keep the `{traceId, path, error, message}` envelope. Stage 4's error-contract card (**BKL-01**, **BKL-07**) is where the envelope itself is unified — do not build a second one here |

### Data layer

Stage 2 moved most of this register — **on a branch**. "Closed on branch" below means the SQL exists in
`gms_backend/src/main/resources/db/migration/` on `feat/stage2-flyway-baseline` and is proved by
[`scripts/verify-schema.sh`](../../scripts/verify-schema.sh); it does **not** mean `master` is fixed. Re-measure with the
grep in each row's *Reuse action* against `master` and the finding is still true there. `ENGINEERING_ROADMAP.md` §"Stage 2
result, measured" holds the numbers.

| ID | Debt | Severity | Effort | Notes | Reuse action |
|---|---|---|---|---|---|
| OPS-07 | No schema in version control; `ddl-auto` conflicts across three files; Flyway enabled but not a dependency | **P0 (ops)** | L | **Closed on branch.** `flyway-core` **and** `flyway-database-postgresql` are now in `pom.xml` (Flyway 10 split the Postgres dialect out of core; without the second artifact boot fails with *"Unsupported Database: PostgreSQL 16.15"*), **six** migrations exist (`V1`–`V6`; `V7` is reserved for `C-8`, which is why `C-7` did not land where its card said it would), and all three profiles say the literal `validate` with the `${JPA_DDL_AUTO:…}` default deleted so no environment variable can restore `update`. Still open: no CI job runs the chain, and `application-prod.properties:15` (`spring.flyway.enabled=true`) still migrates on boot rather than as a deployment step — `C-9` | add `flyway-core` + baseline from an authorised schema export; then delete the `update` override in compose — **done**, `ADR-022` carries the verification record |
| DB-01 | `validate` against a schema that exists only in Neon | P1 | – | **Closed on branch.** `V1` is the baseline, captured from a disposable Postgres 16.15 server and reviewed; a Flyway-migrated database now passes Hibernate `validate` on boot (`Successfully validated 5 migrations`) | same fix |
| DB-02 | Missing unique constraints: `employees.email`, `students.email`, `classrooms(name,grade,section)`, `attendance(student,slot,date)`, `timetable_slots(day,period)` | P1 | M | **Closed on branch** — all five, in `V3`. `students.email` needed a partial index (`WHERE email IS NOT NULL AND email <> ''`) because the column is nullable and blank means "no mailbox", not "shared mailbox"; `employees.email` is `nullable = false`, so it takes a plain key. Duplicates were not cleaned because the migrated database has no rows to clean — on a live schema this migration **will** fail on existing duplicates, which is the intended behaviour, not a defect | add via migration with a duplicate-cleanup step; constraints, not more `existsBy` checks — **done**, 35 named uniques measured |
| DB-03 | No indexes despite `school_id + status + date` patterns | P2 | S | **Closed on branch.** `V4` creates them and `V4`/`V5` assert two invariants in SQL: every tenant-keyed table has an index leading on `school_id` (**I-1**), every navigated FK is indexed on its first column (**I-2**, amended by measurement — 62 `created_by`/`updated_by` references are exempt, by name and with a reason, because no entity navigates them). Measured: 87 secondary indexes on 38 tables, from 0 | composite `(school_id, …)` indexes in migrations — **done** |
| DB-04 | JSON-in-TEXT sets (`recipient_ids`, `target_ids`, `report_data`) | P1 | L | root cause of **TEN-03** | normalise into join tables — replaces the LIKE query, no new query mechanism — **not started; `C-8`** |
| DB-05 | `Parent` global, `Employee`/`Student` emails globally unique-ish but unenforced | P1 | M | **TEN-05**. `V3` names the `parents.email` global key and records it as owner decision D-2 rather than silently changing its scope | decide the model explicitly in a migration + ADR, then enforce — **half done**: enforced, not decided |
| DB-06 | No soft-delete concept despite README claiming one; three inconsistent status styles | P1 | M | `countActiveSchools` and employee-ACTIVE counters return 0 permanently | one status enum (**FIN-07**) + a `status` column convention; do **not** add a `deleted` flag beside `status` |
| DB-07 | Four globally-`unique` natural keys that are logically per-school (`designations.designation_id`, `invoices.invoice_number`, `receipts.receipt_number`, `communication_templates.template_code`) | P1 | S | **Closed on branch**, and checked in *both* directions rather than one: the same invoice number is accepted in two schools and refused inside one. A migration that relaxes a constraint needs a test that the constraint still binds somewhere | `uq (school_id, …)` in the V3 migration; measured with `grep -rn "unique = true" model/entity/` → 8 — **done**, `V3` |
| DB-08 | `uq_timetable_classroom_year` is platform-wide, so a cross-tenant timetable write is refused by the wrong rule | P1 | S | found by measurement on 2026-11-01 while verifying `C-7`: school B naming school A's classroom got **409 "already exists"** because the unique is evaluated before `fk_timetable_classroom_same_school`, and the body disclosed that a timetable for another school's classroom and year exists. `GlobalExceptionHandler`'s `*_same_school` branch now answers 403, which fixes the response and not the key | `V5`'s `(id, school_id)` candidate-key pattern applied to the unique: replace it with `uq (school_id, classroom_id, academic_year)` in the Stage 4 timetable migration, and assert in `verify-c7-tenant-key.sh` §6 that the refusal arrives from the FK, not from the unique — the current script already sidesteps the collision with a distinct `academic_year`, which is a workaround worth deleting |
| FIN-03 | No `@Transactional` in 11 financial/communication services | **P0 (integrity)** | S | atomicity bugs today | annotations only, but must precede **FIN-04/05** |

### Service layer

| ID | Debt | Severity | Effort | Reuse action |
|---|---|---|---|---|
| TEN-01…07 | Tenancy as a convention; 15+ endpoints accept and ignore `schoolId` | **P0** | L | one `TenantContext` + a mandatory tenant in every repository query — **the code half is done on branch (`feat/stage2-flyway-baseline`, 2026-11-01)**: `C-2` deleted `util/SecurityUtil`, parses the token once per request into `security/TenantContext`, forbids a tenant parameter on any service method (`TenantParameterArchTest`) and answers a cross-tenant refusal with 403; `C-3` put the tenant in the query — `RepositoryTenantScopeArchTest` measures 55 declared reads with no tenant at the end of `C-2` (37 indefensible) and 18 now, each allow-listed with the constraint that makes it global by design, and its generated SQL was captured to prove the predicate reaches the database. **Still open**: the tenant key the schema declares but the application never writes (`C-7`, and a composite FK on a NULL column is not enforced), the `findById`/`findAll`/`findAllById`/`delete` that `JpaRepository` hands every repository unscoped (`C-4`), and deny-by-default on the endpoints themselves (`C-5`) |
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
| 5 | Tenant invariant: `TenantContext`, tenant in every query, ownership guard | TEN-01…07, SEC-06 | 3, 4 | L — **the first two halves shipped 2026-11-01, and the third arrived from an unexpected direction**: `C-2` gave every service one way to learn the tenant (86 tests green, live 32/32), `C-3` put it in every declared query (87 green, live 25/25, 37 unscoped reads fixed, 18 allow-listed), and `C-7` — not on this package's original list — made the database enforce it, with nine entity mappings, ten `SET NOT NULL` as `V6`, seven more same-school keys and a trigger for the join table that has no entity (89 green, live 66/66). Still open in this package: the ownership guard on inherited reads is `C-4`, deny-by-default at the endpoint is `C-5`, and the role vocabulary is `C-6` |
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

Every figure above is reproducible from the commands in [BASELINE_STATUS.md](BASELINE_STATUS.md) §2, §6 and §9; that
file is the source of truth if these two documents ever disagree.

