# GMS Product Requirements

**Status:** Canonical. **Written:** 2026-11-01 as the Stage-1 deliverable of
[ENGINEERING_ROADMAP.md](ENGINEERING_ROADMAP.md), under the execution mandate's Priority 1 ("Product and
Architecture") and its instruction to *"Consolidate the PRD and freeze the initial MVP scope."*
**Owns:** who the product is for, what it must do, what is in scope, and what "the MVP works" means.
**Does not own:** defects (`SEC-*`, `TEN-*`, `DB-*`, `ACD-*`, `FIN-*`, `COM-*`, `FE-*`, `OPS-*`, `BKL-*`),
sequence ([ENGINEERING_ROADMAP.md](ENGINEERING_ROADMAP.md)), shape decisions
([INFRASTRUCTURE_DECISIONS.md](infrastructure/INFRASTRUCTURE_DECISIONS.md)), or module boundaries and data
ownership ([ARCHITECTURE.md](ARCHITECTURE.md)).

## 1. What this replaces, and why those files survive

| Document | Relationship |
|---|---|
| `gms_backend/memory-bank/projectbrief.md` | Historical. Classified "distrust: all of it" by [docs/README.md §2](README.md); it is prior AI-assisted context, not a requirement source. Kept for history, quoted nowhere below. |
| [GMS-Frontend-Development-Guide.md](GMS-Frontend-Development-Guide.md) | Supporting. It states that *"all data operations are school-bounded"* — that is **intent**, and `TEN-01…07` record that it is not the current behaviour. §4 of this file turns the intent into requirement `BR-02`, which is the sentence the code must eventually satisfy. |
| [GMS-Project-Progress.md](GMS-Project-Progress.md) | Historical. Marks every module "✅ Completed", including pagination, which has never existed (`BKL-03`). Status here is derived from commands, not from that file. |
| [WindsurfGMS-Postman-Testing-Guide.md](WindsurfGMS-Postman-Testing-Guide.md) | Supporting. 45 manual requests against 232 measured endpoint mappings (§6) — the gap is why `DoD-1`'s journey is scripted rather than clicked. |

**No prior document in this repository contains the words PRD or MVP** (searched across `docs/**`, root `*.md`,
`gms_backend/memory-bank/*.md` and `gms_frontend/README.md`). This is the first, which is why
[docs/README.md §4](README.md) lists "the product requirements and feature matrix" as unwritten and why the debt
backlog carries package 13 as *"Docs: PRD, architecture, ADRs, roadmap"*.

## 2. Product statement

GMS is a **multi-tenant school management system**: a school owner (the superadmin) sells a working instance to
individual schools, each of which runs its students, staff, academics, fees and communications inside its own data
boundary, using a web application that a teacher, a student and a parent can each log into and see only their own
world.

The wedge is not breadth — the code already has 36 controllers and 36 entity tables. The wedge is that **a school
can trust the numbers it sees and the privacy it is promised.** Everything in §7 is subordinate to that.

Target shape (mandate): a modular monolith, Java 21 + Spring Boot + PostgreSQL + React, sized from day one for
**at least 50,000 records per school**, deployed on owner-controlled infrastructure, at zero unexpected cost until
there is a paying reason otherwise.

## 3. Personas

Five roles exist in code (`enums/RoleEnum.java:7-11`), five dashboards exist in the frontend
(`features/dashboard/`: Admin, Parent, Student, Teacher, SuperAdmin). The personas below are those five, plus the
role that does **not** exist in code but governs 39 guards (§10, `SEC-17`).

| ID | Persona | Who in code | What they actually need | What they get today |
|---|---|---|---|---|
| P-1 | **Platform owner** ("superadmin") | `SUPERADMIN` | Create and suspend schools, provision each school's first administrator, see platform-wide load, never see inside a school's academic records unless debugging | `SchoolController` create/list; `features/schools/SchoolsPage.jsx`; `SuperAdminDashboard.jsx` |
| P-2 | **School administrator** | `ADMIN` | Run a school: staff, students, classes, subjects, fees, announcements — with correct counts and no cross-school bleed | The widest surface in the app; `ACD-11` leaves a new school without academic scaffolding, so the first task after provisioning is manual |
| P-3 | **Teacher** | `TEACHER` | Take attendance for the classes assigned to them, enter marks against a scheme, see their timetable | `AttendanceController`, `TeacherAssignmentController`, `features/teacher/*` (5 pages). `ACD-03` (no percentage, no duplicate guard), `ACD-04` (marks bypass the grading scheme), `ACD-06` (no timetable conflict detection) |
| P-4 | **Student** | `STUDENT` | Own profile, own classes, subjects, attendance, results, fees, timetable, teachers, announcements, notifications | ten self-service routes exist — `/students/profile` plus nine `/students/my-*` (`StudentController:89-163`); **seven of the backing service methods return `Collections.emptyList()`** (`ACD-02`) and `SEC-06`'s matcher order 403s most of them before the method runs |
| P-5 | **Parent** | `PARENT` | Each child's attendance, results, fees, timetable, subjects, teachers, announcements — and nothing about anyone else's child | 14 routes on `ParentController`, of which **seven** are `/parents/children/{studentId}/*` reads (`:93-151`), and `SEC-05`'s inherited `GET /parents/{id}` is unguarded; the child-link check is the whole product promise for this persona |
| P-6 | **Accountant** | *no enum value* | Fees, invoices, receipts, payment recording, financial reports | 39 `@PreAuthorize` guards name `ACCOUNTANT` (`StudentFeeController`, `FeeController`, `FeePaymentController`, `FeeStructureController`, `InvoiceController`, `ReceiptController`, `FinancialReportController`, `FinancialDashboardController`, `FinancialTransactionController`, plus `SecurityConfig`) and no token can carry it, so in practice **only `ADMIN` can use the money module** |

**Non-persona, stated so it is not accidentally built for:** a District/Education-board regulator role, a
transport/hostel/library module, and a mobile app. None has a code footprint today; none is in scope.

## 4. Business requirements

`BR-*` is a namespace owned by this file. Each is stated so a test can disprove it. "Status" is the vocabulary of
[ENGINEERING_ROADMAP.md §2](ENGINEERING_ROADMAP.md): `implemented` ≠ `tested` ≠ `verified`.

| ID | Requirement | Test that decides it | Status |
|---|---|---|---|
| BR-01 | A platform owner can create a school and it receives a usable administrator account on first login | scripted journey step 2–4 (`DoD-1`) | implemented (`SchoolServiceImpl:61-134`), **not verified**; `ACD-12` predictable password, `BKL-08` admin cannot then create a user |
| BR-02 | No authenticated principal can read, modify or delete a row belonging to another school | cross-tenant suite, one case per protected resource (`DoD-3`) | **not implemented as a mechanism** — `TEN-01…07`, `TEN-06` (no discriminator, no RLS) |
| BR-03 | Each role sees only its own academic world: a student sees own records, a parent sees linked children only | ownership assertion per `/my-*` and `/children/*` endpoint | implemented in `ParentController` by link-table lookup, **not verified**; `SEC-05` gap on `/parents/{id}` |
| BR-04 | Every collection is paged, sortable and filterable **by the server** | request with `size=25` returns ≤25 rows and a `totalElements` from the database | **not implemented** — 0 of 232 endpoints take `Pageable` (`BKL-03`); frontend slices locally (`FE-08`) |
| BR-05 | Dashboards report aggregates computed in the database | SQL aggregate vs API number, same instant, 50k rows | `FIN-06` fabricates figures; `ACD-03` percentages absent |
| BR-06 | Money is right: a payment cannot be recorded as paid without a verified reference, and balance follows payment | `StudentFee.balance == sum(applied payments)` after a scripted payment/edit/delete/refund | `FIN-01` (unverified SUCCESS), `FIN-05` (balance arithmetic) → **not met** |
| BR-07 | Academic results are derived from the school's grading scheme, not typed | band lookup applied to marks; out-of-band marks rejected | `ACD-04` stores `request.getGrade()` verbatim, `ACD-05` bands unvalidated → **not met** |
| BR-08 | Attendance is idempotent per (student, slot, date) and rollups are exact | unique violation on double-mark; percentage matches manual count | `ACD-03` → **not met** |
| BR-09 | Identifiers are per-school and never collide | two schools each admit their first student; both get `-001` | `ACD-08` uses a **global** `count()+1` → **not met** |
| BR-10 | The schema is version-controlled and reproducible | `DoD-5` | `DB-01` (no `.sql`, Flyway inert) → **not met** |
| BR-11 | A notification has per-recipient read state and reaches only its intended audience | read-marking on one recipient leaves others unread | `TEN-03` partially closed (recipient matching); `DB-04`/shared `readStatus` open |
| BR-12 | Credentials are never predictable, logged, or returned by an API | `verify-security.sh` + log grep | `SEC-07` closed in M1; `ACD-10` `"welcome123"` remains |
| BR-13 | The system degrades honestly: no endpoint claims success it cannot prove | response-vs-database assertion for every write | `COM-02` returns fake success strings; `ACD-02` returns empty lists as if data were absent → **not met** |
| BR-14 | One school's failure cannot exhaust the shared database | resource limits + measured query cost at 50k | Stage 5; `INF-07` no limits today |
| BR-15 | A new developer is productive from a clean clone in under 30 minutes | timed fresh-clone run, recorded | `BASELINE_STATUS.md §5` measured this as **failing** (the stack does not boot) |

## 5. Journeys

The MVP journey is J-01 → J-05 run back to back by two different people in two different schools. That pair of
parallel runs **is** `DoD-1`; a single happy path proves nothing about `BR-02`.

| ID | Journey | Steps | Current state |
|---|---|---|---|
| J-01 | Onboard a school | P-1 logs in → creates school → system provisions the first `ADMIN` → credentials reach them → admin logs in and changes them | implemented; blocked by `ACD-11`/`ACD-12`/`BKL-08` |
| J-02 | Build the academic skeleton | P-2 creates academic year → classrooms + sections → subjects → designations → employees → teacher assignments → timetable | implemented as endpoints; `ACD-06` allows a timetable with conflicts; `TimetableController:56` is a stub |
| J-03 | Admit students | P-2 creates students (single + bulk) with admission number, class, section, parent link | implemented; `ACD-08` cross-tenant numbering, `ACD-01` parent creation broken, `ACD-09` no DOB validation |
| J-04 | Run a school day | P-3 takes attendance for a slot, enters marks for an exam, publishes results | implemented as writes; correctness is `ACD-03`/`ACD-04`/`ACD-07` |
| J-05 | Families see their world | P-4 and P-5 log in and read profile, attendance, results, fees, timetable, teachers, announcements, notifications | **the weakest part of the product**: 7 of the student methods are stubs (`ACD-02`) and `SEC-06` shadows the routes |
| J-06 | Charge and collect fees | P-2/P-6 create fee structure → apply to students → record payment → invoice → receipt → report | endpoints exist (9 + 9 + 7 + 7 mappings); `FIN-01…08` mean the numbers cannot be trusted |
| J-07 | Communicate | P-2 publishes an announcement / notification to an audience; recipients see it; read state per recipient | `COM-01` nothing is ever marked sent, `COM-03` audience never resolved, `COM-04` `Message` has no repository |
| J-08 | Operate and recover | deploy → health → backup → restore → rollback | no production exists; Stage 6/7 |

## 6. Modules

The module map — ownership, dependency direction, API prefixes — is [ARCHITECTURE.md §2–§4](ARCHITECTURE.md). It is
not duplicated here. What this file takes from it is the **feature matrix** in §7, whose rows are those modules.

Endpoint counts below are measured by counting mapping annotations in `controller/*.java`: **232**, declared across
35 classes that carry a class-level `@RequestMapping`; 3 of the 232 belong to `AbstractCRUDController` and are
reachable only through its six subclasses ([ARCHITECTURE.md §6](ARCHITECTURE.md)). They matter only as a scale
signal, not as progress.

## 7. Feature matrix

`MVP` = in the frozen slice (§8). `Foundation` = required before MVP can be claimed. `Later` = after DoD.

| Module | API surface | Personas | MVP? | Notes |
|---|---|---|---|---|
| Auth & principals | `/auth` (3) | all | **Yes** | login + token + role/tenant claims; refresh and revocation are `Later` (`SEC-10`, `SEC-11`) |
| Tenancy (schools) | `/schools` (5) | P-1 | **Yes** | create, suspend, list, get |
| Staff & roles | `/employees` (4) `/users` (7) `/teachers` (3) `/designations`–via-employees | P-1, P-2 | **Yes** | provisioning only; full HR is `Later` |
| Students | `/students` (15) `/student-admissions` (3) | P-2 | **Yes** | CRUD + paged search/filter |
| Class structure | `/classrooms` (6) `/sections` (5) `/subjects` (6) `/academic-years` (5) | P-2 | **Yes** | needed by every other MVP row |
| Attendance | `/attendance` (8) | P-3 | **Yes** | mark + read + percentage (`ACD-03`) |
| Exams & results | `/exams` (6) `/student-exams` (4) `/results` (5) `/grading-schemes` (5) | P-3 | Foundation | grade derivation (`ACD-04`) is required before J-05 can show a result |
| Timetable | `/timetables` (4) `/teacher-assignments` (8) | P-3, P-4 | Foundation (read) | one class's timetable for the parent/student view; conflict detection (`ACD-06`) is `Later` |
| Fees | `/fees` (7) `/fee-structures` (6) `/student-fees` (9) `/fee-payments` (9) | P-2, P-6 | **Later** | `FIN-01…08` make this the largest correctness bet in the product; deliberately outside the frozen slice |
| Invoicing & receipts | `/invoices` (7) `/receipts` (7) | P-2, P-5 | Later | `FIN-04` orphans them |
| Financial reports | `/financial-reports` (7) `/financial-transactions` (8) `/financial-dashboard` (1) `/payment-gateway` (5) | P-6 | Later | `SEC-12` plaintext gateway secrets must be fixed before any of it is reachable |
| Communication | `/announcements` (14) `/notifications` (14) `/communications` (7) `/school-calendar` (7) | P-2, P-4, P-5 | Foundation (announcements + notifications) | `COM-01` delivery truth; SMS/email channels are `Later` |
| Dashboards | derived | all five | **Yes** | `BR-05`; `ACD-02`/`FIN-06` must be closed for the student/parent and money dashboards |
| Platform reports | `/superadmin` pages | P-1 | Foundation (count of schools, users, uptime) | no cross-tenant academic aggregates, by rule |

## 8. MVP scope — frozen

**Frozen:** 2026-11-01. Change rule: §11.

### In

The right-hand column is what makes this list auditable rather than aspirational: every item either lands on surface
that already exists, or is marked **greenfield** so nobody discovers in Stage 4 that it was assumed. Count of items
with neither: **0** — which is acceptance criterion 1 of [ENGINEERING_ROADMAP.md Stage 1](ENGINEERING_ROADMAP.md).

| # | MVP item | Existing surface, or greenfield |
|---|---|---|
| 1 | Superadmin authentication and school provisioning end-to-end (J-01) | `/auth` (3 mappings), `POST /schools/create` (`SchoolController:33`), `SchoolServiceImpl:61-134`, `features/auth/Login.jsx`, `features/schools/SchoolsPage.jsx` |
| 2 | Role-based authorisation for the five roles in `RoleEnum`, vocabulary resolved (`SEC-17`) | 206 `@PreAuthorize` guards over 232 mappings, `RoleEnum.java:7-11`; the `ACCOUNTANT` half is **greenfield** (no enum value, no provisioning path, no frontend affordance) |
| 3 | Student CRUD with per-school identifiers (`ACD-08` closed) | `/students` (15 mappings incl. create/update/toggle/list), `/student-admissions` (3), `features/students/StudentsPage.jsx` |
| 4 | Classroom / section / subject / academic-year setup (J-02's minimum) | `/classrooms` (6), `/sections` (5), `/subjects` (6), `/academic-years` (5) and their four pages |
| 5 | Server-side pagination, sorting and search on students, employees, attendance, announcements | **greenfield** — `Page`/`Pageable` appear on 0 endpoints today (`BKL-03`); the frontend's local slicing (`FE-08`) is what it replaces |
| 6 | Attendance marking with idempotency and a real percentage (`ACD-03`) | `/attendance` (8), `features/teacher/AttendancePage.jsx`; the percentage and the uniqueness guard are **greenfield** |
| 7 | One class timetable readable by student and parent | `/timetables` (4), `/students/my-timetable`, `/parents/children/{studentId}/timetable`; `TimetableController:56` is a stub and the backing method is one of `ACD-02`'s seven |
| 8 | One result visible to a parent, derived from a grading scheme (`ACD-04`) | `/results` (5), `/grading-schemes` (5), `/students/my-results`, `/parents/children/{studentId}/results`; the derivation is **greenfield** |
| 9 | Announcements and notifications with per-recipient read state (`DB-04`, `TEN-03`) | `/announcements` (14), `/notifications` (14); the `notification_recipients` join table is **greenfield** (`ADR-020`/`ARCHITECTURE.md §5`) |
| 10 | Dashboards computed from aggregates for all five roles (`BR-05`) | 5 dashboards in `features/dashboard/`; the aggregate queries are **greenfield** (today counts come from `array.length`, `FE-08`) |
| 11 | Tenant isolation as a mechanism, proven by the negative suite (`BR-02`, `DoD-3`) | **greenfield** — `TEN-06` records no discriminator, no RLS, no composite constraints; `TenantContext` does not exist in the codebase (searched, 0 hits) |
| 12 | Automated unit + integration (Testcontainers) + security tests in CI (`DoD-4`) | 42 unit tests and `mvn verify` in `ci.yml` exist; Testcontainers and the cross-tenant suite are **greenfield**; the frontend has **0** test files and no test runner declared in `package.json` at all |
| 13 | A deployment, **only** if owner-controlled infrastructure exists | `ADR-019` makes Stage 6 the first point where infrastructure exists at all. The mandate's own wording is *"if owner-controlled cloud credentials and infrastructure are available"*, so the honest reading is recorded here rather than wished away: **the MVP is complete without a deployment, and deployment is its own stage gate.** |

### Out (non-goals — each is named because the code already half-builds it, or because it is the obvious place a next branch would go)

- Fee/invoice/receipt/payment money flows and the payment gateway (`FIN-01…08`, `SEC-12`).
- SMS and email delivery truth (`COM-01…05`); SMTP is used only to carry bootstrap credentials, which is itself a
  defect (`ACD-10`).
- Transport, hostel, library, storefront, alumni, ERP integrations, board reporting formats.
- Multi-brand theming per school; report templates; Excel/PDF generation beyond the MVP's result sheet.
- Mobile application. The deliverable is the responsive React SPA that already exists; a native client is a separate
  product decision with its own distribution cost.
- Refresh tokens, device sessions, password reset, impersonation, audit-trail UI — designed in Stage 1, built after.
- Kubernetes (`ADR-007`), microservices, event bus, caching tier, read replicas, RLS (evaluated, not assumed — `TEN-06`).
- Anything requiring a paid service.

### The 48-hour claim, stated honestly

The mandate asks for *"a usable MVP within 48 hours"*. What is true today: the surface exists (232 mappings, 36
entities, 29 pages, 5 dashboards) and **42 automated tests** back it; the missing part is not features, it is
correctness, tenancy, pagination and proof. What is not true: that any of that can be made *verified* in 48 hours.
`DoD-3` alone requires a cross-tenant test per protected resource, and `DoD-5` requires a schema baseline that does
not exist yet (`DB-01`). So this roadmap treats 48 hours as the budget for **a demonstrable vertical slice** — J-01 →
J-05 for two tenants, unpaginated, on a migrated database, with the tenant suite passing and everything else marked
`implemented, not verified` — and it does not call that "the MVP is done". A date and a definition of done are
different objects; only one of them can be argued with a command.

## 9. Non-functional requirements

| ID | Requirement | Target | How it will be measured (Stage 5) |
|---|---|---|---|
| NFR-01 | Volume | ≥50,000 records per school in the primary tables, 2 schools | seeded dataset, asserted row counts (`ACD-08` makes this per-school) |
| NFR-02 | API latency | p95 < 300 ms and p99 < 800 ms for the ten hottest endpoints at volume | timer around HTTP calls in the integration harness, percentiles not averages |
| NFR-03 | Query cost | no MVP read does more than O(1) queries per page; p95 < 100 ms per statement | `EXPLAIN (ANALYZE, BUFFERS)` + a statement-count assertion that fails the build on N+1 |
| NFR-04 | Pagination | page size 25 default, 100 max, `size` bounded server-side | request asserting payload length ≤ `size` |
| NFR-05 | Indexing | every `school_id` and every documented filter has an index | `pg_indexes` diff against the design list in [ARCHITECTURE.md §5](ARCHITECTURE.md) |
| NFR-06 | Availability | single node with a declared interruption window; **no zero-downtime claim** (`ADR-014`) | deploy log showing the measured gap |
| NFR-07 | Recovery | RPO ≤ 24 h, RTO ≤ 1 h, restore rehearsed | `DoD-6` |
| NFR-08 | Security baseline | no secret in repo or logs; no default credential alive; TLS on every public hop | the existing `verify-security.sh` plus a repo-wide secret scan in CI |
| NFR-09 | Observability | structured logs with a trace id that survives from request to SQL log | a trace id findable in logs for a real request (`INF-15`) |
| NFR-10 | Cost | ₹0 until Stage 6; then Always Free only, with the four numbers read from the account before `apply` | Actions minutes + OCI spend report per package |

## 10. Open owner decisions

The mandate says MVP scope is frozen and expansion needs a documented reason. The following are *product* forks
that only the owner can settle. A default is chosen so Stage 2 is not blocked; the default is a decision, not a
stall.

| # | Decision | Default if no answer | Why it needs the owner |
|---|---|---|---|
| D-1 | Is an **Accountant** a real role? | **Yes — add `ACCOUNTANT` to `RoleEnum` and issue it in tokens**, because 39 guards already assume it and collapsing them to `ADMIN` concentrates money access in one person | a control decision about who may touch fees (`SEC-17`) |
| D-2 | Is a **Parent** a global person or a school-local record? | **Global person, school-local relationship**: `parents` keeps its globally unique email (`Parent.java:31`), and every `/children/{studentId}/*` read asserts both the `parent_student` link **and** that the student belongs to the caller's school (`TEN-05`) | a real product question: one parent with children in two schools |
| D-3 | Which **board(s)** does the grading vocabulary follow? | CBSE-style percentage bands, editable per school via `GradingScheme` | `ACD-04`/`ACD-05` cannot be "fixed" without a band truth |
| D-4 | **Payments**: gateway integration or manual recording? | **Manual recording, labelled honestly** — `FIN-01` currently marks unverified payments SUCCESS, which is a lie about money | commercial and legal exposure |
| D-5 | **Domain** and brand for the public origin | none until Stage 6 | DNS ownership is an owner action (`bytendra.com` / `bytencia.com`) |
| D-6 | **Licence** | none chosen; the README's MIT claim was removed because `LICENSE` has never existed | a legal effect, not a documentation cleanup |
| D-7 | Data retention / deletion duty for student records | retain until the owner requests deletion; soft-delete via the existing `status` column, consistently | a compliance duty that changes the schema |

## 11. Scope-change log

| Date | Change | Reason | Impact on DoD |
|---|---|---|---|
| 2026-11-01 | MVP frozen as §8 | Execution mandate Priority 1 | baseline |

**Rule:** adding anything to §8 requires a row here with a reason, and removing anything requires a row too. A
silent scope change is how `GMS-Project-Progress.md` ended up claiming pagination was complete.

## 12. What is assumed rather than verified

Everything in §4 marked "implemented, not verified", and specifically: no journey in §5 has ever been executed
end-to-end against a migrated database (there is no migration: `DB-01`); no persona has ever been observed logging
in as anything other than `ADMIN` on a running stack (`SEC-06` predicts the student and parent portals 403, and
that prediction has not been observed); no number in §9 has a measurement behind it yet. The Stage-5 evidence
section replaces this paragraph with results. Until then, this document describes the product, not its status.
