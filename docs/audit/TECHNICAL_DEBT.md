# GMS Technical Debt Register

**Date:** 2026-11-01 · **Baseline:** `256b8e4`
IDs reference [`REPOSITORY_AUDIT.md`](./REPOSITORY_AUDIT.md). This file adds the *engineering* dimension: effort, blast radius, blocking relationships, and — per the governing rule — **which existing asset must be repaired instead of replaced**.

## 0. Reuse-first rule applied to this codebase

No duplicate implementations are permitted where a capability already exists. Every item below names the existing asset to modify. New files are only acceptable where the audit shows nothing exists (migrations, tests, design-system primitives, tenant context).

| Capability | Existing asset to repair | Must **not** be created |
|---|---|---|
| Pagination | `AbstractCRUDController.java:5` already imports `Pageable`; controllers already return `ResponseEntity` | a second paging framework, a new list-response wrapper alongside the current one |
| DTO mapping | per-service `mapToResponse` methods in 34 impls | MapStruct/ModelMapper added beside hand-mapping — pick one and convert incrementally |
| Error contract | `exception/GlobalExceptionHandler` + `DomainException` (declared, unused) | a new `ApiResponse`/`Result` wrapper layered over the existing exception types |
| Tenancy | `util/SecurityUtil` call sites in controllers | a parallel `TenantFilter` that coexists with header re-parsing |
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

### Data layer

| ID | Debt | Severity | Effort | Notes | Reuse action |
|---|---|---|---|---|---|
| OPS-07 | No schema in version control; `ddl-auto` conflicts across three files; Flyway enabled but not a dependency | **P0 (ops)** | L | the database is currently unexportable and unrebuildable | add `flyway-core` + baseline from an authorised schema export; then delete the `update` override in compose |
| DB-01 | `validate` against a schema that exists only in Neon | P1 | – | same fix | – |
| DB-02 | Missing unique constraints: `employees.email`, `students.email`, `classrooms(name,grade,section)`, `attendance(student,slot,date)`, `timetable_slots(day,period)` | P1 | M | duplicates already possible; **ACD-03/06/08** are symptoms | add via migration with a duplicate-cleanup step; constraints, not more `existsBy` checks |
| DB-03 | No indexes despite `school_id + status + date` patterns | P2 | S | **BKL-03** magnifies this | composite `(school_id, …)` indexes in migrations |
| DB-04 | JSON-in-TEXT sets (`recipient_ids`, `target_ids`, `report_data`) | P1 | L | root cause of **TEN-03** | normalise into join tables — replaces the LIKE query, no new query mechanism |
| DB-05 | `Parent` global, `Employee`/`Student` emails globally unique-ish but unenforced | P1 | M | **TEN-05** | decide the model explicitly in a migration + ADR, then enforce |
| DB-06 | No soft-delete concept despite README claiming one; three inconsistent status styles | P1 | M | `countActiveSchools` and employee-ACTIVE counters return 0 permanently | one status enum (**FIN-07**) + a `status` column convention; do **not** add a `deleted` flag beside `status` |
| FIN-03 | No `@Transactional` in 11 financial/communication services | **P0 (integrity)** | S | atomicity bugs today | annotations only, but must precede **FIN-04/05** |

### Service layer

| ID | Debt | Severity | Effort | Reuse action |
|---|---|---|---|---|
| TEN-01…07 | Tenancy as a convention; 15+ endpoints accept and ignore `schoolId` | **P0** | L | one `TenantContext` + mandatory tenant parameter on existing repository methods |
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

## 2. Ranked backlog

Ordered by (risk removed ÷ effort), with the dependency edges that matter.

| # | Package | Resolves | Depends on | Est. |
|---|---|---|---|---|
| 1 | Fail-closed configuration + `prod` profile + remove insecure defaults | SEC-01*, SEC-02, SEC-03, OPS-11, OPS-13 | owner credential rotation | M |
| 2 | Delete credential logging, response-leak and dead endpoints | SEC-07, SEC-08, BKL-05, BKL-06, BKL-07 | — | S |
| 3 | Close escalation/destruction holes: roles, inherited CRUD, self-register | SEC-04, SEC-05, SEC-09, SEC-12, SEC-13 | 1 | M |
| 4 | Test harness + CI gate (build, unit, integration, coverage) | OPS-01, OPS-14, OPS-12 | 2 | L |
| 5 | Tenant invariant: `TenantContext`, mandatory tenant params, ownership guard | TEN-01…07, SEC-06 | 3, 4 | L |
| 6 | Schema baseline via Flyway + constraints + indexes | OPS-07, DB-01…06, BKL-08 | 4 | L |
| 7 | Compose v2 + Makefile + non-root images + working nginx proxy | OPS-08/09/10, SEC-14 | — | M |
| 8 | Financial integrity: transactions, state machine, linkage, real metrics | FIN-01…08 | 5, 6 | XL |
| 9 | Academic correctness: `%`, grades, conflicts, ids, scaffolding | ACD-01…12 | 5, 6 | L |
| 10 | API contracts: DTOs, pagination, validation, OpenAPI | BKL-01…04, API-05, ACD-09 | 5 | L |
| 11 | Frontend contract repair + mock scoping + auth-state | FE-01…08 | 10 | L |
| 12 | Design system + shared components, migrate 9 pages | FE-09…12 | 11 | XL |
| 13 | Docs: PRD, architecture, ADRs, roadmap; repair `docs/` | DOC-01…05 | — | M |
| 14 | E2E, backups, observability, RLS evaluation | OPS-16, mandate §16/§17 | 4, 7 | L |

\* SEC-01's repository half (placeholders + fail-fast) is code work; the rotation itself is an owner action outside this repo.

## 3. What must not be touched yet

* `School`/`Student`/`FeePayment`/`Invoice`/`Receipt` entity fields, until **OPS-07** provides a migration path — there is currently no way to change a column safely.
* Any `ddl-auto` behaviour against the live database (**mandate §9.1** and §25 rules 1–3): no schema export has been authorised yet.
* `docker-compose` `update` → `validate` flip until a baseline migration exists, or local startup breaks.
* The 9 duplicated pages' markup until their data contracts (**FE-01**) are fixed, or a refactor silently changes what each page displays with no test to notice.
* `.github/workflows/deploy-backend.yml` triggers, until a test job exists to guard them — narrowing the trigger without a gate just hides the risk.

## 4. Debt quantification

| Metric | Value |
|---|---|
| Backend Java files / LOC | 284 / 17,997 |
| Frontend source files / LOC | 51 / 11,119 |
| Controllers | 36 (~230 mappings) |
| Entities / repositories / service impls | 37 / 35 / 34 |
| RTK Query endpoints | 71 (34 queries, 37 mutations) |
| Test classes / methods | **0 / 0** |
| Migrations | **0** `.sql` files, no migration dependency |
| `@Transactional` in financial services | 0 of 11 |
| Controllers with no method security | 6 |
| Controllers with no matching path rule | 20 of 36 |
| P0 findings open | 9 |
| Postman request coverage | ~19% |
| Markdown files that are genuine documentation | 6 of 59 |
