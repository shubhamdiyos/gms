# GMS Repository Audit — Findings Register

**Audit date:** 2026-11-01
**Baseline commit:** `256b8e4` (branch `master`)
**Method:** full static read of 284 Java files (18.0k LOC), 51 frontend files (11.1k LOC), build/toolchain verification, git-history inspection. No production database connection was made and no production deployment was triggered.

**This file is the single source of truth for findings.** Other documents in `docs/` reference finding IDs (`SEC-01`, `TEN-01`, …) rather than restating them. Add a finding here first; then cite it.

Severity: **P0** critical security / data loss / privilege escalation · **P1** major functionality, security or reliability · **P2** important engineering or usability · **P3** optimisation.

---

## 1. Existing architecture (as-is)

Two-application monorepo, no shared module.

```
gms_backend/     Spring Boot 3.3.3, Java 21, Maven, Spring Data JPA, PostgreSQL (Neon)
  com.gms.config      SecurityConfig, CorsConfig, SuperAdminBootstrap*
  com.gms.security    JwtTokenProvider, JwtAuthenticationFilter, CustomUserDetailsService
  com.gms.controller  36 REST controllers (+ AbstractCRUDController base)
  com.gms.service     74 interfaces/abstract (+ service.impl: 34, 6.5k LOC)
  com.gms.repository  35 Spring Data JPA repositories
  com.gms.model       entity (37), request (40), response (~40)
  com.gms.enums       RoleEnum, AdmissionStatus, StatusEnum (nested enums)
gms_frontend/    React 18, Vite 7, MUI 5, Redux Toolkit + RTK Query (71 endpoints)
  store/api/apiSlice.js  single RTK Query service, 669 LOC
  services/              axios client + mock layer (mockBaseQuery, mockAuthService, mockData)
  features/<domain>/     18 feature folders, page-per-folder
```

Request lifecycle: `JwtAuthenticationFilter` → path rules in `SecurityConfig` → `@PreAuthorize` on controller → service → repository → DTO/entity → Jackson.

**Layering defect (BKL-01).** Services return `org.springframework.http.ResponseEntity` (`StudentService.java:13-16`, `NotificationServiceImpl.java:132`), so the web tier leaks into the business tier and services fabricate HTTP 403/404/501 instead of throwing domain exceptions. This is why several "errors" are silent (see FIN-03, ACD-05).

**No dependency injection of tenant context.** Tenant is re-derived per call from the raw HTTP header by a static holder (`util/SecurityUtil.java:59-99`) rather than from the already-authenticated `SecurityContext`. It returns `null` outside a request scope (async, scheduled, tests), and every service must remember to compare the passed `schoolId`. Findings TEN-01…TEN-09 are the consequences.

## 2. Existing product functionality

Verified state of the modules the README advertises:

| Module | Status | Evidence |
|---|---|---|
| School CRUD, tenant bootstrap | partially works | `SchoolServiceImpl.java:61-134` |
| User CRUD | works but unsafe | SEC-04, SEC-05 |
| Student / Employee / Parent / Teacher | partially works | see ACD-01…ACD-04 |
| Classroom, Section, Subject, AcademicYear | partially works | ACD-06, ACD-07 |
| Attendance marking | works, no percentages | ACD-03 |
| Exams, Results, Grading | broken end-to-end | ACD-04, ACD-05 |
| Timetable | no conflict detection | ACD-08 |
| Fee master, StudentFee | **list endpoints always empty** | FIN-02 |
| Invoices, Receipts | orphaned, never linked | FIN-04 |
| Payments | unverified, no ledger | FIN-01, FIN-05 |
| Financial reports/dashboard | fabricated numbers | FIN-06 |
| Notifications | stored, never delivered | COM-01, COM-02 |
| Announcements | works, no read tracking | COM-03 |
| Messaging | 100% stub | COM-04 |
| Email | real SMTP only for credentials | COM-05 |
| Student self-service portal | **unreachable (403)** | SEC-06 |
| Parent self-service portal | **unreachable + frontend crash** | SEC-06, FE-07 |
| Teacher portals | partially reachable | SEC-06 |
| Pagination / search / filtering | not implemented anywhere | API-05 |
| File upload / attachments | disabled at framework level | OPS-07 |

## 3. API surface

36 controllers, ~230 mapping annotations, base path `/api/v1`. `Constants.API_BASE_V1` is used by only ~14 controllers; the other ~22 hardcode the string (API-08).

No response envelope, no `Page`/`Pageable` on any endpoint, no OpenAPI dependency despite `SecurityConfig.java:35-37` permitting `swagger-ui`/`v3/api-docs` paths that do not exist.

Verb-in-path convention (`/create`, `/update`, `/toggle`) mixed with RESTful paths; IDs sourced from body, `@PathVariable` and `@RequestParam` in the same API (API-04).

## 4. Database relationships (as-is)

**38 tables** are generated from the 37 entity classes (measured by booting the application with
`ddl-auto=update` against a disposable PostgreSQL 16 and dumping the result — BASELINE_STATUS §6). The tenant column
`school_id` is present on **26** tables and **absent on 12**: `attendances`, `exam_subjects`, `fee_payments`,
`student_fees`, `student_exams`, `results`, `timetables`, `timetable_slots`, `teacher_assignments`, `parents`,
`parent_student`, `user_roles`. That list is the hard part of mandate §6: payments, results, fees and attendance —
the most sensitive records — have no tenant column at all and are reachable only by traversal (TEN-04). `Parent` is
absent by design: it is global and shared across schools via `parent_student`.

Measured against the generated schema: **123 foreign keys** (referential integrity is real), **14 unique constraints**
(`schools.school_code`, `users.username`, `users.email` among them, but **no tenant-scoped composite unique** such as
`(school_id, roll_number)`), and **zero secondary indexes** — every `school_id` filter runs against an unindexed
column (DB-03). Missing natural-key constraints remain: `employees.email`, `students.email`,
`classrooms(name,grade,section)`, `attendance(student, slot, date)`, `timetable_slots(day, period)` (DB-02).

JSON-in-TEXT columns used for sets: `notifications.recipient_ids`, `announcements.target_ids`, `messages.recipient_ids`, `financial_reports.report_data` — unqueryable and unindexable (DB-04).

Two competing audit-column conventions: `BaseAuditEntity` maps `created_on`/`updated_on`, while 8 entities inline `created_at`/`updated_at`; `Exam`, `Section`, `SchoolCalendar` declare **both** (four timestamp columns) (DB-05).

**No schema is version-controlled.** Zero `.sql` files, no `flyway-core` or Liquibase dependency, yet `application.properties:16-17` enables Flyway against a nonexistent `classpath:db/migration`. `ddl-auto=validate` therefore validates against a schema that exists only inside the live Neon instance (DB-01).

## 5. Security architecture (as-is) and findings

> **Runtime verification (2026-11-01).** SEC-02, SEC-03, SEC-07, SEC-08 and the raw-entity defect below were
> reproduced against a locally started instance on a disposable database, not merely read out of the source. A JWT
> signed offline with the repository's default secret was accepted by `GET /schools`; login with the shipped default
> bootstrap password returned `200` and a token; the login trace contained the BCrypt digest; `GET /users` answered
> `500` with a body naming the Spring exception, the Hibernate root cause and a JVM source line. Transcripts and the
> exact commands are in [BASELINE_STATUS.md](BASELINE_STATUS.md) §2 and §4. No production host or database was
> involved, so the same probes against the live deployment remain an authorised owner action.

### P0 — critical

**SEC-01 — Live production database credential committed to a public repository.**
`gms_backend/src/main/resources/application.properties:5-7` contains a real Neon endpoint, owner role and password. The repository clones over HTTPS without authentication (public) and the values are in git history permanently (introduced `5eb1c23`).
*Impact:* direct read/write to the production database of every tenant — full data breach, not just application-level.
*Remediation:* rotate the credential in the Neon console (owner action, outside this repo); move datasource settings to env placeholders with no fallback in non-dev profiles; add a startup assertion that fails when a placeholder is unresolved. Scrubbing history is hygiene, not remediation — rotation is mandatory.
*Verification:* `git log -S` shows no secret in new commits; a build with unset `DB_URL` fails fast in the `prod` profile; a connection test with the new credential succeeds.

**SEC-02 — JWT signed with a publicly known key.**
`security/JwtTokenProvider.java:22` defaults the HMAC secret to a literal placeholder. `security.jwt.secret` is set in **no** properties file, Dockerfile, compose service, workflow or `.env.example` (verified by repository-wide grep).
*Impact:* anyone can mint a token carrying `roles:[SUPERADMIN]` and arbitrary `schoolId`/`empId` — total authentication and tenant-isolation bypass, not merely an auth weakness.
*Remediation:* require the secret; length/entropy-validate at startup (≥32 bytes, reject the known default); externalise per environment; treat all tokens minted before rotation as invalid.
*Verification:* integration test asserting startup failure with the default secret, and a unit test rejecting a token signed with the placeholder key.

**SEC-03 — Default superadmin account created from source-code defaults.**
`config/SuperAdminBootstrapProperties.java:42,58,67,76` ship `enabled=true`, username `superadmin` and a literal default password; `app.superadmin.*` is configured nowhere. `validate()` (`:187-199`) only rejects blank values, so the shipped default always passes, and `SuperAdminBootstrapRunner.java:193-197` merely logs a warning when the environment variable is absent. The password is also published in `README.md:128` and five `memory-bank/` files; `gms_frontend/src/features/auth/Login.jsx:49-50` prefills it into the production bundle with a quick-login button.
*Impact:* publicly documented administrator credentials for a live deployment = trivial full administrative takeover.
*Remediation:* remove defaults; require explicit configuration; refuse bootstrap in non-dev profiles when unset; delete credentials from all documentation and from `Login.jsx`; provide a dev-only seeding mechanism (see §7 of the mandate).
*Verification:* startup with unset `APP_SUPERADMIN_PASSWORD` fails; grep confirms no credential literal remains in `src/`, `docs/` or `README.md`.

**SEC-04 — Client-controlled role assignment.**
`service/impl/UserServiceImpl.java:85-87` performs `user.setRoles(request.getRoles())` straight from the request body with no allowlist and no check on the caller's own role; `UserRequest.roles` is a client-settable field (`model/request/UserRequest.java:30`).
*Impact:* privilege escalation to SUPERADMIN through the normal user-creation path (mandate §6 test case "a user cannot change their own role to SUPERADMIN" currently fails).
*Remediation:* never copy roles from input; derive roles server-side from the acting principal and an explicit allowlist of grantable roles; separate "administrative user creation" from "self-update".
*Verification:* test posting `roles:[SUPERADMIN]` as ADMIN asserts the persisted roles are only ADMIN; `@WebMvcTest` for the endpoint.

**SEC-05 — Unguarded inherited CRUD, including hard delete, across tenancy.**
`controller/AbstractCRUDController.java:30,41,52` auto-register `GET /{id}`, `GET` (unfiltered `findAll`) and `DELETE /{id}`; `service/AbstractCRUDService.java:39-60` calls `repository.deleteById`/`findAll` with **no** tenant predicate and the base class carries **no** `@PreAuthorize`. Seven controllers inherit it — `School`, `Student`, `Parent`, `Employee`, `Classroom`, `Subject`, `User` — and **none** overrides `DELETE /{id}`; only Classroom/Subject/School override `getById`.
*Impact:* `DELETE /api/v1/schools/{id}` is reachable by any ADMIN (path rule `SecurityConfig.java:43` grants ADMIN) and hard-deletes another tenant's school row. `GET/DELETE /api/v1/users/{id}`, `/parents/{id}` require only "authenticated", so a STUDENT can read or delete any account — while `GET /api/v1/users/{id}` also discloses cross-tenant PII.
*Remediation:* delete the inherited endpoints; require each controller to declare explicit, tenant-checked, role-gated operations. Alternatively keep the base class but make it abstract-only and never map it.
*Verification:* `MockMvc` matrix asserting 403/404 for cross-tenant `DELETE` on each of the 7 resources (mandate §6 scenarios).

### P1 — major

**SEC-06 — SecurityFilterChain matcher order blocks the STUDENT, PARENT and teacher portals.**
`config/SecurityConfig.java:45` matches `/api/v1/students/**` (ADMIN|SUPERADMIN) **before** `:58-59` declare `/students/profile`, `/my-classes`, `/my-subjects`, `/my-attendance`, `/my-results`, `/my-fees` for STUDENT. Spring evaluates matchers in declaration order and the first match wins, so the coarse rule denies the request before `@PreAuthorize("hasAuthority('STUDENT')")` (`controller/StudentController.java:90,98-161`) is reached. Identical shadowing: `/api/v1/notifications/**` (`:50`) and `/api/v1/announcements/**` (`:51`) defeat the STUDENT/PARENT grants in `NotificationController.java:117` and `AnnouncementController.java:53-109`; `/api/v1/classrooms/**` (`:46`) defeats the TEACHER grant at `ClassroomController.java:52,93`; `/api/v1/fees/**` (`:49`) blocks the `ACCOUNTANT` role referenced by `FeeController.java`. `/students/my-timetable`, `/my-teachers`, `/my-announcements`, `/my-notifications` have no STUDENT rule at all.
*Impact:* the student and parent portals — the reason the product is sold to schools — are dead in production (403). Teachers cannot load assigned classrooms. Also note `/api/v1/test/password` (`:40`) is a `permitAll` rule for an endpoint no controller maps.
*Remediation:* declare specific/self-service matchers **before** coarse collection matchers, or drop path-prefix role rules entirely in favour of method security, which is already enabled.
*Verification:* integration test logging in as STUDENT asserting 200 on all ten `/students/my-*` paths; regression test that ADMIN cannot reach them.

**SEC-07 — Password hashes printed to standard output.**
`controller/AuthController.java:76` logs the hash plus username at `:65,72,80,84,102` with `e.printStackTrace()` at `:103`; `security/CustomUserDetailsService.java:35,40,46` prints the hash on **every authenticated request** because the JWT filter calls `loadUserByUsername` per request.
*Impact:* offline cracking material and full user enumeration in stdout, container logs and CI logs; also a performance issue (debug I/O in the hot path).
*Remediation:* remove all `System.out`/`printStackTrace` from main sources; SLF4J only; never serialize credential fields.
*Verification:* `grep -rn "System.out\|printStackTrace" src/main` returns zero; a logback appender test asserts the hash string never appears in a login log line.

**SEC-08 — Exception handler discloses internals.**
`exception/GlobalExceptionHandler.java:44-66` returns `exception`, `rootCause`, `rootMessage`, `at` and `rootAt` (class names, SQL/constraint messages, source file and line) in the response body, and its `Exception` catch-all absorbs `EntityNotFoundException` and `IllegalArgumentException`, which services throw routinely (`UserServiceImpl.java:59,64,67,74`; `ResultServiceImpl.java:148`), converting 400/404 conditions into 500 with a stack map.
*Remediation:* return a stable error envelope with a correlation ID; keep detail server-side only; add explicit handlers for not-found, illegal-argument, access-denied and validation-summaries (currently only the first field error is returned, `:22`).
*Verification:* an endpoint forced to fail asserts the body contains no `com.gms` class name or file path; `AccessDeniedException` asserts 403, not 500.

**SEC-09 — Unauthenticated account provisioning and unprotected password change.**
`SecurityConfig.java:39` permits all of `/api/v1/auth/**`, which includes `POST /auth/self-register` (`AuthController.java:133`) and `POST /auth/change-password` (`:108`). Self-register trusts caller-supplied `empId`/`schoolId` (`:136-141`) and assigns the role `"EMPLOYEE"` (`:166`) — a string outside the five product roles, so the created account is gated by no path rule at all.
*Remediation:* narrow the permitAll list to `/auth/login`; require an invitation token for provisioning; replace `"EMPLOYEE"` with the documented role model.
*Verification:* unauthenticated POST to both endpoints asserts 401.

**SEC-10 — No login throttling; account-state columns ignored.**
No rate limiting exists anywhere (grep for `ratelimit|bucket4j` → 0 hits); `AuthController.java:101-105` collapses every exception, including DB failures, into a bare 401. `User.loginAttempts`, `accountLocked` and `passwordExpired` (`model/entity/User.java:80-95`) are never read or incremented — `CustomUserDetailsService.java:49-57` hardcodes `accountNonLocked`, `credentialsNonExpired` and `accountNonExpired` to `true`.
*Remediation:* persist failed-attempt counts, lock past a threshold, return 423 with a generic message; honour the three existing columns or delete them.
*Verification:* N failed logins then a correct password asserts lockout; a row with `account_locked=true` asserts rejection.

**SEC-11 — Token revocation and password-change invalidation absent.**
No refresh endpoint and no logout endpoint exist (`AuthController` has exactly three mappings); the 1-hour HS256 token cannot be revoked and is not bound to the user's password version, so `change-password` (`:127-129`) leaves stolen tokens valid. `AuthController.java:99` hardcodes `expiresIn:3600` independently of `security.jwt.validity-seconds`, so the value the API advertises can lie.
*Remediation:* add a `jti`/denylist or a credential-generation claim checked by the filter; document the revocation strategy (mandate §7.9, §7.12); take `expiresIn` from the provider.
*Verification:* test asserting a pre-change-password token is rejected.

**SEC-12 — Plaintext gateway secrets returned by the API.**
`model/entity/PaymentGateway.java:33,36` store `api_key`/`secret_key` as plaintext columns with no `@JsonIgnore`, and `controller/PaymentGatewayController.java:53,60` serialize the raw entity. The redacting mapper that exists (`PaymentGatewayServiceImpl.java:136-145`) is used only on create/update.
*Remediation:* encrypt at rest, exclude from all responses, audit who has read them.
*Verification:* response-schema test asserting no key fields on list/get.

**SEC-13 — Actuator exposed without authentication.**
`SecurityConfig.java:34` permits `/actuator/**`; `application.properties:20` exposes `health,info`. `/health` detail disclosure plus an unauthenticated reachability probe path.
*Remediation:* permitAll only `/actuator/health/liveness|readiness`; authenticate the rest.
*Verification:* unauthenticated `/actuator/health` returns the minimal group payload; `/actuator/info` and any added endpoint assert 401.

**SEC-14 — JWT-bearing traffic over plaintext HTTP with the host disclosed.**
`gms_frontend/vercel.json:5` rewrites `/api/:path*` to `http://ec2-…compute.amazonaws.com:8080` — public, unencrypted, and a live infrastructure hostname in a public repository. Two serverless proxies (`api/[...path].js`, `api/v1/[...path].js`) that read `BACKEND_BASE_URL`/`PROXY_SHARED_SECRET` are shadowed by the rewrite and therefore dead.
*Remediation:* HTTPS termination at Cloudflare or the platform, backend reachable only through it; delete dead proxies; keep hostnames out of the repo.
*Verification:* `curl -v` shows TLS; the deployed frontend has no direct `:8080` call.

**SEC-15 — The documented secret-injection names do not bind, so the insecure default survives any operator setup.**
`SuperAdminBootstrapProperties.java:31` is `@ConfigurationProperties(prefix = "app.superadmin")`, so the only
environment variables that can reach it are `APP_SUPERADMIN_*`. The project's own documentation instructs operators to
set `SUPERADMIN_PASSWORD` (`gms_backend/memory-bank/superadminBootstrap.md`, and the same wrong names appear in the
class Javadoc at `:20-23`) — a name that binds to nothing. `.env.example` ships no `APP_SUPERADMIN_*` or JWT entry at
all, and `docker-compose.yml` passes neither, so an operator who follows the documentation faithfully, believes the
secret was injected, and restarts the service keeps running the literal from source. This is the mechanism that makes
SEC-02/SEC-03 unfixable by configuration alone: there is no correct name to set in the deployed environment, because
the intended name was never the effective one. Same class of defect at `security.jwt.secret`, which has no `${…}`
placeholder, so only the undocumented relaxed-binding name `SECURITY_JWT_SECRET` could ever work.
*Remediation (M1):* pick one canonical naming scheme and enforce it — placeholders in `application.properties`
(`security.jwt.secret=${SECURITY_JWT_SECRET}`, `app.superadmin.password=${APP_SUPERADMIN_PASSWORD:}`) with a
prod-profile validator that fails startup on blank/short/default values; ship the names in `.env.example`; pass them in
Compose; correct the Javadoc and the memory-bank tables.
*Verification:* a `prod`-profile integration test asserting startup failure when `APP_SUPERADMIN_PASSWORD` is unset and
success when it is set, plus an assertion that the seeded BCrypt digest matches the injected value and not the default;
a repository-wide grep showing no `SUPERADMIN_PASSWORD` without the `APP_` prefix.

**SEC-16 — Thirteen authorisation guards written in a vocabulary the application can never produce.**
At baseline `git grep -c "has\(Any\)\?Role(" HEAD` returns 13 matches in four controllers — 6 `hasRole('ADMIN')` /
`hasAnyRole('ADMIN','TEACHER')` on parent and subject reads, 2 `hasRole('TEACHER')`, 1 `hasRole('STUDENT')`. `hasRole('X')`
tests for the authority `ROLE_X`, but `CustomUserDetailsService.java:43` (its own comment said `// Don't add ROLE_ prefix`)
grants the bare role code from `user_roles`, and no code in the repository ever prefixes it. The expression is therefore
never true: those thirteen endpoints answer **403 for every caller including a genuine SUPERADMIN**, so the parent,
subject and timetable portals were unreachable regardless of tenancy. This is not an opening, it is a silent denial of
service, and it is dangerous for a different reason: an inert rule teaches the next operator that role checks do not
work and that the fix is to widen the filter-chain matchers — which is exactly how SEC-04 and SEC-06 were made.
*Remediation (M1):* converted to the mechanism the rest of the codebase already uses (`hasAuthority` /
`hasAnyAuthority` on the bare role code) rather than introducing a prefix or a second authorization system; the
`ROLE_`-prefix idea is explicitly rejected because 200+ existing guards and `AuthController.java:87` assume the bare
code.
*Verification:* `git grep "hasRole("` returns nothing; `scripts/verify-security.sh` §3 asserts a real SUPERADMIN token
reaches `/users`, `/users/me` and `/actuator/info` and is refused `/students/my-fees` with 403 (checked, not assumed:
both the allow and the deny path are exercised).

### Tenant isolation (mandate §6)

**TEN-01 — `schoolId` accepted then ignored, with a literal TODO.** `FeePaymentServiceImpl.java:150-152`, `ReceiptServiceImpl.java:145-147`, `InvoiceServiceImpl.java:154-156`, `ResultServiceImpl.java:162-168`, `StudentExamServiceImpl.java:127`, `AttendanceServiceImpl.java:201-211`, `TimetableServiceImpl.java:72-99`, `UserServiceImpl.java:130` all receive the tenant and never filter by it — several carry the comment `// Filter by schoolId if needed`. Repositories back this: `FeePaymentRepository.java:19`, `ReceiptRepository.java:17`, `InvoiceRepository.java:17`, `ResultRepository.java:13,16`, `AttendanceRepository.java:16,18`, `UserRepository.java:70` (`findByRole` spans every school), `TeacherAssignmentRepository.java:21` (`findByAcademicYear` spans every school).
*Confirmed reachable cross-tenant reads:* `GET /fee-payments/student/{id}` and `/invoices/student/{id}` and `/receipts/student/{id}` (PARENT allowed), `GET /results/student/{id}` (STUDENT allowed), `GET /student-exams/student/{id}`, `GET /attendance/student/{id}`, `/attendance/classroom/{id}`.

**TEN-02 — Raw-id loads with no ownership check.** `StudentServiceImpl.java:158`, `ClassroomServiceImpl.java:88`, `SectionServiceImpl.java:159`, `SubjectServiceImpl.java:94`, `EmployeeServiceImpl.java:99`, `ParentServiceImpl.java:309`.

**TEN-03 — Notification audience resolved by substring match.** `NotificationRepository.java:21-22` uses `recipientIds LIKE %:userId%` against a JSON TEXT column, so user 1 matches 11, 21, 100 and 1234 — an intra-tenant disclosure plus a full-table scan. `NotificationServiceImpl.java:156-159` never compares the path `userId` to the caller, so `GET /notifications/user/{userId}` (`NotificationController.java:74`) exposes any user's notifications, and `markNotificationAsRead` (`:192-207`) mutates a single shared read flag for all recipients.

**TEN-04 — Entities with no tenant column** (see §4) make "filter by school" a multi-join the service must remember; none of them do. `TimetableServiceImpl.java:40` takes `classroomId` from the request body with no `schoolId` parameter in the signature at all (`TimetableController.java:26-30`) — one tenant can rebuild another's timetable, and the method `clear()`s existing slots first (`:48-64`), so the attack is destructive, not merely read-only.

**TEN-05 — `Parent` is global by design** (`model/entity/Parent.java:17-61`, no school association, globally unique email) and `StudentAdmissionServiceImpl.java:118` reuses a parent by email across schools, while `ParentStudentServiceImpl.java:48` infers the parent's school from the **first child only** — a childless parent passes and can then be linked to students of any school; `assignStudentsToParent` (`:70-71`) also clears the entire existing child list on an "assign more" call.

**TEN-06 — No Hibernate tenant discriminator** (`@Where`/`@Filter` absent repository-wide), no RLS, and no composite `(school_id, …)` constraints, so isolation depends 100% on hand-written service checks that are demonstrably missing in 15+ places.

**TEN-07 — `SecurityUtil` returns null off-request**, and callers pass that null as a filter value; `EmployeeRepository.java:23,28` and `ParentRepository.java:15` are global lookups against columns with no unique constraint (`employees.email`, `students.email`), so `findByEmail` can throw `NonUniqueResultException` across tenants.

## 6. Financial-integrity findings

**FIN-01 (P0) — Payments marked SUCCESS without verification.** `FeePaymentServiceImpl.java:54-62` takes the amount and all references (`transactionId`, `paymentReference`, `chequeNumber`, `:57-61`) from the client and unconditionally sets `status="SUCCESS"`, then `:69 → :171-185` reduces the student's balance. `PaymentGatewayServiceImpl.java:36-134` is config-table CRUD only — no charge, capture, webhook receiver, or HTTP client. Fabricated payment records therefore settle balances. *Remediation:* two-state model (INITIATED→VERIFIED) with provider signature verification, or an explicit "manual payment by authorised administrator" path with an audit record. Mandate §8 "never fabricate transaction IDs" cannot currently be satisfied.

**FIN-02 (P1) — Every student-fee list endpoint returns empty.** Rows are created with `status="PENDING"` (`StudentFeeServiceImpl.java:75`) but all queries filter `status="ACTIVE"` (`:143,168,174`) — a value the entity never holds. Fee collection screens are permanently blank.

**FIN-03 (P1) — No `@Transactional` anywhere in the financial or communication services.** Verified zero occurrences in `Fee`, `FeeStructure`, `StudentFee`, `FeePayment`, `PaymentGateway`, `Receipt`, `Invoice`, `FinancialTransaction`, `FinancialReport`, `FinancialDashboard`, `Notification`, `Announcement` impls. Consequently `FeePaymentServiceImpl.java:66` (insert payment) and `:69` (update balance) commit as two independent transactions; the same two-table hazard exists at `:101` and `:125`. `StudentAdmissionServiceImpl.java:112-139` performs three saves in three transactions.

**FIN-04 (P1) — Invoice and Receipt are orphaned.** `InvoiceServiceImpl.java:48-63` never sets student/parent, so `getInvoicesByStudent` (`:130`) can never match; nothing ever updates `Invoice.amountPaid/balanceAmount/status` when a payment lands. `ReceiptServiceImpl.java:47-58` never populates `feePayment`/`invoice`, so a receipt acknowledges nothing. Mandate §8's lifecycle (structure → assignment → invoice → payment → receipt → reconciliation) does not exist: `FeeStructure` is written and never read by any biller, and no `FeePayment` creates a `FinancialTransaction`, so the ledger and the payments table are independent books.

**FIN-05 (P1) — Balance arithmetic is wrong.** `updateFeePayment` (`:90-101`) overwrites the amount without recomputing the balance; `deleteFeePayment` (`:122`) marks CANCELLED but leaves the credit applied; overpayment is unguarded (no `amount ≤ balance`, balance can go negative and status still becomes PAID, `:178`); negative amounts are accepted (`FeePaymentRequest.java:14` has no `@Positive`); `StudentFeeServiceImpl.java:76-77` accepts an arbitrary `amountPaid` at creation with no payment record and `:106-110` overwrites it non-additively.

**FIN-06 (P1) — Dashboards and reports fabricate figures.** `FinancialDashboardServiceImpl.java:68-70` casts a money **sum** to `Long` and returns it as payment **count**; `:74` computes `totalPayments / totalPayments` (always ≈1.00); `:45,107-112` hardcodes the academic year `"2026-2027"` instead of reading `AcademicYear`; `:84-100` pins eleven fields to ZERO; expected is per-year (`:48`) while collected is all-time (`:49`), so the collection rate can exceed 100%. `FinancialReportServiceImpl.java:54-58` persists client-supplied income/expenses/net with no `net = income − expenses` check; `:105-124` "delete" only sets `updatedBy` yet returns success.

**FIN-07 (P2) — Status strings are unvalidated and inconsistent.** All financial states are raw string literals in both writes and JPQL (`StudentFeeServiceImpl.java:75,143`; `FeePaymentServiceImpl.java:62,122`; `ReceiptServiceImpl.java:121`; `InvoiceServiceImpl.java:130`); update paths copy client values verbatim (`FeePaymentServiceImpl.java:98`, `InvoiceServiceImpl.java:92`, `ReceiptServiceImpl.java:87`, `NotificationServiceImpl.java:91-94`), so one typo (`"paid"`) silently removes a row from every SUM/COUNT aggregate. There is no financial status in `enums/StatusEnum` at all. `SchoolServiceImpl.java:245` writes `"0"`, which `StatusEnum.fromCode` rejects (`StatusEnum.java:34`).

**FIN-08 (P2) — Currency and rounding.** Money is correctly `BigDecimal(10,2)` everywhere (no float/double anywhere — verified) but `setScale` never appears on inputs, `FinancialTransactionServiceImpl.java:53,87` copies client currency unvalidated, and only `FinancialTransaction` carries a currency column (defaulting `"INR"`, `FinancialTransaction.java:37`); Fee/StudentFee/FeePayment/Invoice/Receipt have none.

## 7. Academic-domain findings

**ACD-01 (P1) — `ParentServiceImpl.create()` saves an unpopulated entity.** `:68-77` persists `new Parent()` with no fields copied from the request; `createUserForParent` then reads `parent.getStudents().get(0)` (`:290`) → `IndexOutOfBoundsException` for a childless parent. `update`/`toggleParent` return HTTP 501 (`:88-96`).

**ACD-02 (P1) — Seven student self-service methods are stubs.** `StudentServiceImpl.java:317-446` return `Collections.emptyList()` with TODOs (attendance, results, fees, timetable, teachers, announcements, notifications) — so even after SEC-06 the student portal would show nothing. Related helpers `:481-505` map only `id`.

**ACD-03 (P1) — Attendance has no percentage and no duplicate guard.** No `attendancePercentage` computation exists anywhere (verified grep). `AttendanceServiceImpl.java:166-168` lets a student and date be changed with no re-check; bulk marking silently skips duplicates (`:112-131`); timetable-slot validation is commented out (`:59-64,99-110`). There is no unique `(student, slot, date)` constraint, so duplicates are storable.

**ACD-04 (P1) — Results bypass the grading scheme.** `ResultServiceImpl.java:74` stores `request.getGrade()` verbatim; `GradingScheme` bands are never consulted. `:63-68` divides by `examSubject.maxMarks` with no zero guard, `:77` creates results already PUBLISHED (no draft/publish gate), and one-exam-per-student is enforced only by an application check (`:58-60`) with no `@Transactional` (see FIN-03).

**ACD-05 (P2) — Grading bands are unvalidated.** `GradingSchemeServiceImpl.java:36-62` never checks `min ≤ max` and never checks band overlap; a scheme is a flat pile of rows with no parent grouping.

**ACD-06 (P1) — Timetable has no conflict detection.** `TimetableServiceImpl.java:54-56` only checks the assignment's classroom matches the timetable. Double-booking a teacher or a period is unchecked, `timetable_slots` has no unique `(day, period)`, `getSlots().clear()` (`:48-64`) destroys history on every save, and `getTimetableForTeacher` (`:80-91`) computes `teacherSlots` from `findAll()` and then **discards it**, returning an empty response.

**ACD-07 (P2) — Exams are unvalidated.** `ExamServiceImpl.java` never checks `passingMarks ≤ maxMarks` and never checks subject/time overlap; duplicate `StudentExam` registration is explicitly not prevented (`StudentExamServiceImpl.java:66-67` "In a complete implementation, we would check this").

**ACD-08 (P2) — Admission numbers are global counters.** `StudentServiceImpl.java:234-244` derives `STD%03d` from `studentRepository.count()+1` across all tenants with a 10,000-attempt collision loop; `SchoolServiceImpl.java:272-277` uses the same pattern for designation IDs, so concurrent school creation duplicates them; `generateUniqueUsername` (`:279-288`) is check-then-act and racy. `StudentAdmissionServiceImpl.java:84-142` creates an approved Student with **no** `studentId` and **no** `User` login, unlike `StudentServiceImpl.create` (`:83-101`), so admitted students cannot sign in.

**ACD-09 (P1) — No date-of-birth validation.** `StudentRequest.java:33` carries the comment `// TODO: Add @Past for dateOfBirth if required`; future DOBs are accepted. 23 of 40 request DTOs have no constraints at all and two controllers omit `@Valid` (`StudentAdmissionController.java:20`, `AcademicYearController.java:24,30`).

**ACD-10 (P2) — Hardcoded temp password.** `"welcome123"` appears at `StudentServiceImpl.java:95` and `ParentServiceImpl.java:295` (mitigated by `requirePasswordChange=true`, still a known constant). `SuperAdminBootstrapService.java:158` locates the system school by `findById(1)` rather than by code `"SYSTEM"`; `superAdminExists()` (`:141-145`) swallows exceptions and returns `true`, so a transient DB error permanently skips bootstrap while logging success, and `handleBootstrapError` (`Runner:164-184`) is non-fatal — the app serves traffic with no administrator.

**ACD-11 (P1) — School provisioning creates no academic scaffolding.** `SchoolServiceImpl.java:61-134` seeds 2 designations, 1 ADMIN user and 1 employee only — no academic year, no classes, no sections, no subjects, no fee structure, no grading scheme. A new tenant therefore opens into an application with nothing to configure against.

**ACD-12 (P1) — Admin bootstrap password is predictable and logged.** `SchoolServiceImpl.java:86-88` builds `firstInitial + surname + "@123"`, emailed at `:124` and printed at INFO three times by `EmailServiceImpl.java:59,63,67`.

## 8. Communication findings

**COM-01 (P1) — Nothing is ever delivered.** `NotificationServiceImpl.java:62-63` stamps `PENDING`; no code writes `SENT` or `sentAt`; `sendScheduledNotifications` (`:210-217`) is a self-admitted stub. No `@Scheduled` or `@EnableScheduling` exists in the codebase, so no delivery job can run. All list queries filter `deliveryStatus='PENDING'` (`:133,151,157,163,169,175`), meaning anything marked SENT would vanish from the UI.

**COM-02 (P1) — The API lies about delivery.** `CommunicationServiceImpl.java:12,19,26,33,40,47,54` each return a fake success string with no repository write and no provider call. Mandate §25 rule 12 forbids claiming unexecuted work; this endpoint currently does exactly that to its caller.

**COM-03 (P2) — Announcements have no audience resolution or read tracking.** `targetIds` is never resolved (`AnnouncementServiceImpl.java:51`), `viewCount` is returned (`:266`) but never incremented, and `createAnnouncement` never sets `isPublished` true, so announcements are born unpublished while `delete` (`:118`) only flips the flag.

**COM-04 (P2) — Messaging is unimplementable.** `Message` has **no repository** at all, and `parent_message_id` is a raw Integer self-reference, not an association.

**COM-05 (P1) — Only real outbound channel is SMTP for credentials** (`EmailServiceImpl.java:55`), which conflicts with SEC-14/Acd-12 by mailing and logging predictable passwords. `email_logs`/`sms_logs` have a nullable school, so `EmailLogRepository.java:23` / `SmsLogRepository.java:23` read across tenants.

## 9. Backend engineering findings

**BKL-01 (P2)** Services return `ResponseEntity` (§1) — the layering break that makes every other error-handling finding harder to fix.
**BKL-02 (P1)** ~28 controllers serialize raw JPA entities (full list in the API audit), while `application.properties:38` sets `open-in-view=false` and relations are `FetchType.LAZY` (`Student.java:26-38`; `Employee.java:30,89,94`). The derived `@JsonProperty getSchoolName()` / `getCreatedByName()` getters (`User.java:140-143`, `BaseAuditEntity.java:50-53`) force proxy initialisation after the transaction has committed → `LazyInitializationException`/500 on list endpoints. Unidirectional EAGER pairs (`Timetable.slots`, `User.roles`) are the only ones that survive.
**BKL-03 (P1)** No pagination: `Page`/`Pageable` appear in no controller (`AbstractCRUDController.java:5` imports `Pageable` and never uses it); ~20 unbounded `findAll()`-style reads, largest at `StudentFeeServiceImpl.java:143`, `AnnouncementServiceImpl.java:127-181`.
**BKL-04 (P2)** No mapper library; `BeanUtils.copyProperties` with growing hand-maintained ignore lists (`StudentServiceImpl.java:127`, `UserServiceImpl.java:109-113`) and **no exclusions at all** at `EmployeeServiceImpl.java:71` and `ParentServiceImpl.java:107` — mass-assignment surface. A 12-argument `StudentResponse` constructor is duplicated in three impls.
**BKL-05 (P1)** Dead and misleading code: `DomainException` is never thrown by any service;
`ParentStudentServiceImpl.java:12` imports `AbstractCRUDService` and never extends it; `SubjectServiceImpl.java:102`
throws `UnsupportedOperationException` from a mapped endpoint. In addition, **three `main()` password utilities and one
compiled binary sit inside `src/main/java`, so they are packaged into the production jar** and each one hardcodes the
bootstrap administrator's default password: `util/PasswordEncoderTest.java:10`,
`util/PasswordVerificationTest.java:6` (which `System.out.println`s it), `util/PasswordTestUtil.java:59`, plus the
committed class file `src/main/java/com/gms/util/PasswordVerificationTest.class`. These are not tests — `src/test` does
not exist (OPS-14) — they are shipped code that re-publishes SEC-03's default credential to anyone holding the jar.
*Remediation:* delete all four files in M1; if BCrypt spot-checks are genuinely wanted, they belong under
`src/test/java` with no real credential.
*Verification:* `git ls-files | grep -E '\.class$'` returns nothing; `grep -rn` for the default password matches only
`SuperAdminBootstrapProperties` (removed in M1) and `application.properties` (removed in M1); `unzip -l` of the built
jar lists no `Test`/`Util` password classes.

**BKL-06 (P2)** Inconsistent empties: several list endpoints return 204 while others return `[]` (`ParentController.java:59-61`, `ClassroomController.java:59`).

**BKL-07 (P1)** Client-caused rejections answered **HTTP 500**, and `UserRequest.email` had no format validation.
`GlobalExceptionHandler` mapped only the repository's own `NotFoundException`; the two exception types the service layer
actually throws for bad input fell through to the catch-all. `EntityNotFoundException` is the codebase's not-found signal (28 service implementations import it), and every
`throw new IllegalArgumentException` site is a caller-correctable
conflict — duplicate business key, null tenant reference, password below policy, unknown role code. So `POST /users/create`
with a duplicated email returned 500, and `not-an-email` passed bean validation entirely.
*Remediation (M1):* added an `IllegalArgumentException` → 400 and `EntityNotFoundException` → 404 handler (both logged at
WARN with the trace id, both returning a business message rather than the exception identity), and `@Email` on
`UserRequest.email`. `DomainException`/`NotFoundException` are untouched — the new handlers complement the existing
mechanism instead of replacing it.
*Verification:* `GlobalExceptionHandlerTest` (4 cases) plus the live probes in `docs/audit/BASELINE_STATUS.md` §4.6 —
duplicate email now `400 {"error":"Invalid request","message":"Email already in use"}`, malformed email
`400 {"fields":{"email":"must be a well-formed email address"}}`, absent creator `404` with no internals.
*Deliberately not done:* mapping `EntityNotFoundException` to 404 does mean a Hibernate-originated instance (a proxy whose
row vanished) also reports 404 instead of 500. That trade is accepted for M1 because the alternative — rewriting 28 call
sites onto a repository exception — is the schema/test-gated refactor this audit schedules for M3+.

**BKL-08 (P1) — Found while verifying M1.** A bootstrapped administrator can never create a user.
`UserServiceImpl.createUserFromRequest` resolves the acting principal through `findByEmployee_Id(empId)` and throws
`EntityNotFoundException("Creator user not found")` when it fails. `SuperAdminBootstrapRunner` creates a `User` row with
no `Employee`, and `AuthController.java:91` papers over that with `empId = employee != null ? employee.getId() : user.getId()`
— substituting a **user** primary key into a claim that every reader interprets as an **employee** primary key. So a
fresh deployment issues a token with `empId=2` for the user whose id is 2, the lookup queries `employees`, finds nothing,
and `POST /users/create` — the endpoint every other module's `created_by` chain depends on — fails. The two id spaces
collide by accident, which also means a bootstrap admin whose user id happens to equal some other tenant's employee id
would be recorded as created-by *that* employee.
At baseline this presented as a 500; after BKL-07 it presents as a 404. Neither is correct: the caller cannot fix it.
*Remediation:* undecided and owner-facing — either provision an `Employee` alongside the bootstrap user and delete the
id-substitution, or stop deriving `created_by` from an employee identity and derive it from the `User` principal.
Recorded here for the M2/M3 schema and audit-trail work rather than patched silently in M1.
*Verification:* reproducible on a disposable database — `select employee_id from users where username='smokeadmin'` is
null with `select count(*) from employees` equal to 0, the issued token decodes to `{"sub":"smokeadmin","empId":2,…}`,
and the create call returns the creator-not-found 404 above.

## 10. Frontend findings

**FE-01 (P1) — Contract drift; the app has never round-tripped its own backend.**
Wrong paths: `apiSlice.js:249` `PUT /classrooms/{id}` vs `ClassroomController.java:40` `PUT /classrooms/update`; `:461` `POST /sections/create` vs `SectionController.java:24` `POST /sections`; `:399` `PATCH /notifications/{id}/read` vs `NotificationController.java:117` `PUT /{id}/mark-read`; `:520` `GET /results` has no mapping.
Wrong payloads: `StudentsPage.jsx:43-50` sends `classroomId`, `StudentRequest.java:41` requires `classId`; `ClassroomsPage.jsx:36-41` sends `className/capacity/roomNumber` vs required `name/grade/section`; `SubjectsPage.jsx:36-40` `subjectName/subjectCode/credits` vs `name/code`; `FeesPage.jsx:42-48` sends `feeName` (absent) and omits required `academicYear`; `EmployeesPage.jsx:40-49` sends `salary/dateOfBirth/gender` (absent) and omits required `teaching`; `AcademicYearsPage.jsx:36-40` `yearName` vs `name` + required `isCurrent`; `TeacherGradesPage.jsx:36-55` `studentId/marksObtained` vs `ResultRequest.studentExamId/obtainedMarks`.
Wrong field reads: `DashboardLayout.jsx:46` filters `n.read_status` while the API sends `readStatus`; `StudentsPage.jsx:117` reads `classroom.className` while the entity exposes `name`.

**FE-02 (P1) — Mock mode can silently ship.** Gated only by `VITE_MOCK_AUTH === 'true'` (`apiSlice.js:16`, `authService.js:5`); currently off, but there is no `.env.example` documenting the flag, no `import.meta.env.DEV &&` guard, no CI check, and commit `8ba3342` removed the only on-screen indicator. In mock mode `mockAuthService.js:77-83` accepts **any password** and defaults unknown users to **SUPERADMIN** (`:80`) with an unsigned fake JWT, and `mockBaseQuery.js:291-292` returns `ok([])` for unmatched paths, so every FE-01 bug above is invisible and looks like "no data". Mandate §25 rule 18 is directly implicated.

**FE-03 (P1)** Authorization is driven by a client-decoded JWT in `localStorage` (`authService.js:31-38,60-66,86`, keys `gms_token`/`gms_user`); editing `gms_user.roles` in localStorage passes every `ProtectedRoute` gate (`ProtectedRoute.jsx:30`). With FE-02 there is no server to disagree.

**FE-04 (P1)** 401 handling exists only in the unused axios stack (`apiClient.js:44-49`); RTK Query has no `baseQueryWithReauth`, so an expired token leaves the SPA mounted with error toasts and empty tables. `useAuth.js:18` instantiates `useLoginMutation` and never calls it — the RTK `login` endpoint and its `transformResponse` are dead code.

**FE-05 (P2)** Routing: `/unauthorized` (`ProtectedRoute.jsx:31`) has no route, so a denied navigation silently redirects back to the dashboard; the layout route is `path="/*"` (`App.jsx:148`), making the `*` splat redirect at `:386` unreachable, so typos render an empty shell; sidebar links `/admin/reports`, `/parent/attendance|results|fees` and `/dashboard` are unrouted. `App.jsx:103-110` runs `localStorage.clear()` inside the render body; `useAuth.js:102-108` dispatches during render (double-fires under `StrictMode`, `main.jsx:5`).

**FE-06 (P1) — Fabricated dashboard metrics** (mandate §11.2, §25 rule 11): `AdminDashboard.jsx:86-91` fee collection "₹2.4L"/"85%", `TeacherDashboard.jsx:52-69` 156 students / 12 grades / 4 classes, `StudentDashboard.jsx:47,61,74-78,158-167` 85% attendance and attendance counts, `ParentDashboard.jsx:51-70,122-127` 87% / B+ / ₹15,000, `SuperAdminDashboard.jsx:54-57,176-191,232-258` hardcoded counts plus a fake "database contains" list.

**FE-07 (P1) — Parent dashboard throws.** `ParentDashboard.jsx:9,39` calls `useGetParentProfileQuery`, which is exported (`apiSlice.js:599`) with **no** endpoint definition in the `endpoints` builder → `undefined is not a function` on first render for every parent.

**FE-08 (P2) — Client-side pagination over unpaged payloads.** Every page fetches the full `List<T>` and slices locally (`initialState.pagination.pageSize`, no `paginationMode="server"`), and dashboards derive counts from `array.length`, forcing whole-table downloads.

**FE-09 (P2) — No shared component layer.** `components/common/` contains only `ProtectedRoute.jsx`; the header+add-button+600px-DataGrid+dialog page anatomy is duplicated across ≥9 pages, `StatCard` is duplicated 5×, delete confirmation is `window.confirm()` in 10 files, and "View" buttons in `StudentsPage.jsx:184-188`, `FeesPage.jsx:185-189`, `ExamsPage.jsx:222-225`, `ResultsPage.jsx:190-193` have **no onClick at all**. This is the mandate §11.1 design-system gap, and the reuse rule that governs how it must be fixed (extract once, migrate pages; do not add a second parallel set).

**FE-10 (P2)** Forms/validation: 11 pages redeclare local Yup schemas; `constants/index.js:135-142` `VALIDATION_MESSAGES` is unused by all of them, so the documented password policy is enforced by nobody (`SuperAdminSettingsPage.jsx:19-31` checks only `min(8)` against a backend regex requiring all four classes → bare 400 with no message). `SchoolsPage.jsx:47-56` uses `when('isEdit')` where `isEdit` is passed as resolver **context**, which Yup reads from values — admin bootstrap fields are never validated on create. `Login.jsx:31,35` demands `min(3)/min(6)` vs backend `@NotBlank`, rejecting valid short credentials. No `@Size` limit is mirrored anywhere, so all backend length failures surface as silent 400s. `AttendancePage.jsx:59,184` instantiates `useForm` but saves via a plain `onClick`, and firing N parallel `markAttendance` POSTs (`:95-103`) makes partial saves possible.

**FE-11 (P2)** Date/money handling: `toISOString().split('T')[0]` on LocalDate strings shifts days (`StudentsPage.jsx:82`, `EmployeesPage.jsx:75`, `AcademicYearsPage.jsx:66-67`); `SchoolsPage.jsx:481-489` renders "Invalid Date"; money inputs are `type="number"` with no `step`/`min` (`FeesPage.jsx:297-304`) and `₹${value?.toLocaleString()}` breaks when the API sends a string (`:152`). `TeacherGradesPage.jsx:279-282` hardcodes exam menu items, `:123` sends `studentExamId: parseInt(selectedExam)`, and `:356-358` mixes controlled/uncontrolled student selection.

**FE-12 (P3)** Theme is a 25-line `createTheme` inside `App.jsx:57-81` (no theme module), 300+ inline `sx` uses repeat a hardcoded purple gradient in ≥10 files, and dark mode is half-declared (`uiSlice.js:5,20` never read, no toggle). `src/index.css` and `src/App.css` are untouched Vite boilerplate, never imported; `index.html` still says "gms_frontend" with the Vite favicon.

**FE-13 (P2)** Redux: `store.js:15` whitelists `persist/*` actions although redux-persist is not installed; `uiSlice` is entirely unused by the components that supposedly consume it; `authSlice.js:40` calls `AuthService.logout()` inside a reducer. `providesTags:['Parent']` (`apiSlice.js:216`) names a tag type that is not in `tagTypes` → RTK dev warning and never invalidated.

**FE-14 (P2)** Tooling: `package.json:6-11` has no `test` script and devDependencies contain no runner, so the README's `npm test` fails outright; React 18 runtime with `@types/react@19` and no TypeScript; `axios` and RTK Query are two parallel HTTP stacks; `recharts`/`dayjs` unused; `Dockerfile:9` uses `npm install` over a committed lockfile; `eslint.config.js:17,21` sets `ecmaVersion` twice.
*Measured at M1 (2026-11-01):* `npm run lint` exits non-zero with **47 errors across ~25 files** — 38 `no-unused-vars`,
8 `no-undef`, 1 `no-useless-catch` — none of them in the code this branch touched. 8 of the 47 live in the dead
`api/[...path].js` proxies (**SEC-14**). Consequence for CI: `.github/workflows/ci.yml` runs lint and tests as
**advisory** steps (`continue-on-error: true`) while `npm ci` and `npm run build` remain blocking. That is a decision
with a cost — a lint regression can reach `master` — taken deliberately instead of silencing 47 errors with
`eslint-disable` comments or skipping the step, and it is reversed by the frontend contract package (§2 row 11).

## 11. Infrastructure, CI/CD and operations findings

**OPS-01 (P0) — Production deploys with no test gate.** `.github/workflows/deploy-backend.yml` contains no `mvn` or `npm` step at all: push to `master` touching `gms_backend/**` → SSH → run `~/deploy_backend.sh`. There is no build job, no `needs:` chain, no `environment:` protection (`deploy-backend.yml:14-17`), so no approval gate exists.

**OPS-02 (P0) — Deploy script is outside version control.** `:60` executes `test -f ~/deploy_backend.sh && bash …` — image builds, environment variables, DDL strategy and restart behaviour are unauditable and unreproducible, violating mandate §14 "do not execute an unaudited remote deployment script".

**OPS-03 (P1) — SSH host-key verification disabled** at `:47`, `:60`, `:87` (`StrictHostKeyChecking=no`), and `webfactory/ssh-agent@v0.9.0` is unpinned to a SHA. Mandate §14/§25 rule 8 forbids the former.

**OPS-04 (P1) — Secrets and hostname echoed despite a "sanitized" label.** `set -euxo pipefail` at `:35,45,59,86` expands `ssh "$EC2_USER@$EC2_HOST"` into the run log, defeating the masking at `:37`.
> **Revalidated in Infra-M0 and downgraded.** Run 41245769175's actual log shows `EC2_HOST: ***` and
> `+ ssh … ***@***` — GitHub masks secret *values*, including inside the `set -x` trace, so nothing was disclosed.
> The residual issue is only the deliberate `${EC2_HOST:0:10}` prefix at `:67`. The real host-disclosure exposure is
> the committed origin in `gms_frontend/vercel.json:5`, recorded as INF-01/ADR-013 rather than here.

**OPS-05 (P1) — `cancel-in-progress: true` on an SSH deploy** (`:15-17`) can kill a run mid-script and leave EC2 half-updated; deploy jobs must use `false`.

**OPS-06 (P1) — No rollback.** The health poll (`:62-77`) only reports failure after ten attempts; the bad release stays live.

**OPS-07 (P1) — No migrations exist.** No `flyway-core` dependency, no `db/migration` directory, zero `.sql` files; `application.properties:16-17` enables Flyway against a path that does not exist. `ddl-auto` has three conflicting values — `validate` (`:11`), `update` (`docker-compose.yml:41`), `validate` (`.env.example:16`) — and the untracked EC2 script decides which applies. Hibernate `update` on a 40-table multi-tenant schema means the live schema has drifted from the entity model with no record, no version and no rebuild path (mandate §9.1).

**OPS-08 (P1) — Compose cannot start from a fresh clone.** `docker-compose.yml:8-9,32-33` require `env_file: .env`, which the repo does not ship and `.gitignore:2` forbids, so `make up` fails before the `${VAR:-default}` fallbacks at `:11-13` apply. `version: '3.8'` (`:1`) is obsolete. Both Postgres (`:15`) and app (`:47`) ports are published to the host, and `/actuator` is unauthenticated (SEC-13). No `restart:` policy on any service; `postgres.healthcheck` (`:18-22`) hardcodes `pg_isready -U gms_user -d gms` while user/db are templated, so overriding `DB_NAME` breaks `depends_on: service_healthy` (`:48-50`). `SUPERADMIN_PASSWORD` and the JWT secret are never passed, so a compose deployment inherits both insecure defaults
(SEC-02, SEC-03) — and the bootstrap name is not even the one that binds (SEC-15).

**OPS-09 (P1) — Every Makefile target uses the EOL `docker-compose` v1 binary** instead of `docker compose`, contradicting mandate §12; `.PHONY` (`:1`) omits 7 of the declared targets; `clean` (`:65-67`) deletes volumes with no confirmation and warns only afterwards; `shell-db` (`:102`) hardcodes `-U gms_user -d gms`; there is no `test`, `migrate`, `db-backup` or `db-restore` target.

**OPS-10 (P2) — The frontend image cannot reach an API.** `gms_frontend/Dockerfile` serves via `nginx.conf` (SPA fallback, gzip, asset caching, basic headers) but there is **no `/api` proxy block**, while `.env.production:1` sets `VITE_API_BASE_URL=/api/v1` — so the container 404s every call. Combined with `vercel.json` doing the rewrite, the frontend Dockerfile is dead code. Both Dockerfiles run as **root**, have no `HEALTHCHECK`, use unpinned tags, and the backend copies the fat jar wholesale although `pom.xml:115` enables Spring layers. `gms_backend/.dockerignore` excludes `target/` and `memory-bank/` but **not `.env`**. `ENTRYPOINT` has no `-XX:MaxRAMPercentage` or heap cap — container-hostile on the Alpine JRE image.

**OPS-11 (P1) — Debug logging by default in every profile.** `application.properties:42-43` set `com.gms` and both bootstrap classes to `DEBUG`, so with SEC-07's `System.out` statements the log stream contains hashes and tenant provisioning detail.

**OPS-12 (P2) — JaCoCo is decorative.** `pom.xml:118-122` declares 0.8.10 with no `<executions>` → it never instruments, never reports, never gates coverage, while CI never runs tests anyway (OPS-01).

**OPS-13 (P2) — No Spring profiles exist.** Only `application.properties`; no `application-dev`/`-prod`, no `spring.profiles.active`. Dev/prod separation rests entirely on env interpolation plus an untracked remote script. `spring.servlet.multipart.enabled=false` (`:39`) means no file upload is possible anywhere, which contradicts `announcements.attachment_urls`.

**OPS-14 (P1) — Zero automated tests.** No `gms_backend/src/test` directory exists (0 classes, 0 methods); `mvn test` passes by running nothing. Frontend has no test files and no runner. `spring-boot-starter-test` is declared but unused. The only verification in the project is 45 Postman requests against ~230 mappings (~19%), and both collections ship `"auth": {}` with unset `{{school_id}}`/`{{user_id}}`, so none run without manual token pasting. **Any refactor of the 36 controllers / 40 entities is currently unguarded.**

**OPS-15 (P2) — `.gitignore` gaps.** `.env`, `.env.local`, `.env.*.local` are covered and no real `.env` ever entered history (verified `git log --diff-filter=A`), but `.env.production` / `.env.prod*` are **not** ignored (one is tracked), and there are no `*.class`, `*.pem`, `*.key`, `secrets/` rules — which is how `PasswordVerificationTest.class` got committed.

**OPS-16 (P2) — No backup or recovery procedure exists** anywhere in the repo (mandate §17). The only data safety net
is Neon's free-tier restore window, which §17 explicitly forbids relying on.

**OPS-17 (P1) — `/actuator/health` is permanently `DOWN`, so every health gate that exists is broken.**
`spring.mail.*` defaults to `smtp.gmail.com:547/587` with empty username and password
(`application.properties:23-26`), so Boot's `MailHealthIndicator` fails on every probe and drags the aggregate status
to `DOWN` — reproduced: `{"status":"DOWN"}` on a fully started instance, cause visible in the log as
`jakarta.mail.AuthenticationFailedException: failed to connect, no password specified?` Because
`management.endpoint.health.show-details` is unset, the cause is invisible to an operator. Consequences: the Compose
backend healthcheck `wget --spider http://localhost:8080/actuator/health || exit 1` (`docker-compose.yml:56-60`) can
never pass, the CI deploy poll in `deploy-backend.yml:62-77` would report failure even on a healthy release, and any
future readiness gate inherits a false negative.
*Remediation:* expose `health/groups` (`liveness`, `readiness`) and put mail in neither, or disable the indicator
(`management.health.mail.enabled=false`) until SMTP is actually configured; gate readiness on `db` + `ping` only.
*Verification:* an unauthenticated `GET /actuator/health/liveness` returns `UP` with no SMTP configured; a Compose run
reaches `healthy` for the backend service.

## 12. Documentation findings

**DOC-01 (P2)** — *partially remediated 2026-11-01.* `README.md:90-96` instructed developers to hand-edit
`application.properties` to localhost — the file that carries the live credential (SEC-01) — and `mvn spring-boot:run`
then silently connects to production. Replaced with a Compose + command-line-override recipe, an explicit JDK 21
warning and the reproducible-failure caveats. `:159` advertised an "automatic token refresh mechanism" that does not
exist (SEC-11) and `:169-178` asserted school-level isolation, tenant-aware queries, SuperAdmin-only cross-school
access and role-hierarchy enforcement, none of which is enforced (TEN-01…07, SEC-06); both sections now carry measured
status labels and finding IDs. `:212,218` prescribed `mvn test` and `npm test`, both false (OPS-14, FE-14); the Testing
section now states what each command actually does. Also corrected: the clone URL pointed at
`github.com/imshubhy/gms` while the remote is `shubhamdiyos/gms`, and `:128` published the administrator password
(SEC-03) — replaced by the `APP_SUPERADMIN_*` property table.
**Still open:** `:240` links a `LICENSE` file that does not exist (choosing a licence is an owner decision, not a
documentation fix); `:41` says React 18 while `package.json` pins React 19 types on Vite 7; `:48-72` omits `docs/`,
`.github/`, compose and the Makefile and labels the tree `WindsurfGMS/`.
*Verification:* the grep `grep -rnI "imshubhy/gms\|@123\b" README.md docs gms_backend/memory-bank` returns nothing, and
a second pass for the two former literal secrets (the shipped bootstrap password and the exposed database password —
values deliberately not repeated in this document) is empty across all tracked Markdown, JSON and source files.

**DOC-02 (P1)** — *remediated in the working tree 2026-11-01; credential rotation still owed by the owner.*
Credentials and PII were published in documentation, which mandate §3 forbids:
`docs/WindsurfGMS-Postman-Testing-Guide.md` (superadmin pair, **a second personal admin account with its password**,
the default-password pattern, and a personal e-mail), `gms_backend/memory-bank/superadminBootstrap.md:25`,
`authenticationQuickReference.md:46`, `postmanCollectionUpdates.md:31-32`, `postgresqlMCPConnection.md:49-57` (real
database rows including a third party's e-mail address), and `WindsurfGMS-Postman-Collection-Part1.json:43,108,172`.
All literal values are now placeholders or Postman variables (`{{superadmin_password}}`, `<APP_SUPERADMIN_PASSWORD>`),
the collection carries declared-but-empty variables plus a note, `postgresqlMCPConnection.md` keeps the structural
observation and drops the row dumps, and the same guide's unverified "tenant isolation is enforced" claims were
rewritten as assertions to be proven.
**Still exposed:** the historic values remain in git history and, for the database credential, in
`application.properties` until the owner rotates it (SEC-01) — removing a literal from `HEAD` revokes nothing.
The README team block now routes contact through the GitHub handle instead of a personal address.
*Verification:* the grep above is empty; `git log -S` confirms history is untouched and therefore that rotation, not
rewriting, is the remediation.


**DOC-03 (P2)** 50 of the repository's 59 markdown files are AI session artifacts committed inside `gms_backend/memory-bank/` (`currentSessionSummary.md`, `errorResolutionLog.md`, …), several of which contradict the code — e.g. three different claims about Flyway (`progress.md:35`, `systemPatterns.md:46`, `application.properties:16`) are all wrong since Flyway is not a dependency. Mandate §21 requires separating genuine documentation from historical notes; do not delete without checking for operational content.

**DOC-04 (P2)** `docs/GMS-Frontend-Development-Guide.md:30-58` documents a directory tree (`utils/`, `routes/`, `themes/`, `App.js`, features `users/`, `payments/`, `timetable/`, `reports/`) that does not exist. `gms_frontend/README.md` is untouched Vite boilerplate. `docs/GMS-Project-Progress.md` marks every module "✅ Completed" including pagination and comprehensive validation. `.github/pull_request_template.md` is not a template but one closed PR with "[x] All tests pass" pre-checked.

**DOC-05 (P2)** Mock mode (`mockBaseQuery.js`, `mockAuthService.js`, `mockData.js`, `apiSlice.js` changes) is mentioned only in `GMS-Frontend-Component-Architecture.md`; README, the API integration guide and the progress report still present the app as fully backend-integrated.

## 13. Missing features (relative to mandate §4.3 and §6)

Server-side pagination, sorting and filtering; OpenAPI generation; centralised tenant resolution and ownership assertions; row-level security evaluation; refresh tokens and a revocation store; password reset (no endpoint exists); role-change propagation policy; idempotency keys on financial writes; payment verification or an honest manual path; invoice↔payment↔receipt linkage; refunds; late fees and due-date processing; attendance percentage; grade derivation from the scheme; draft/publish result workflow; admission-number sequencing per tenant; audit trail (no audit table; `created_by` only); soft delete as a consistent concept; data retention/deletion policy; backup/restore automation; structured JSON logging with correlation IDs (the `TraceIdFilter` **is** registered — a `@Component` at
`Ordered.HIGHEST_PRECEDENCE`, `TraceIdFilter.java:16-18` — and it does populate MDC (`:26`) and set `X-Trace-Id`
(`:28`), but no `logging.pattern` references `%X{…}`, so the id reaches the response header and never the log
line; see INF-15); error tracking; readiness/liveness separation; non-root containers; HTTPS; test suites of any
kind; PR quality gates.

## 14. Technical debt summary and remediation order

The debt concentrates in four places: (1) authorization is expressed twice, in two mechanisms that contradict each other (SEC-04/05/06); (2) tenancy is a convention rather than an enforced invariant (TEN-01…07); (3) money has no transaction boundary and no single source of truth (FIN-01…06); (4) there is no schema history and no test suite, so nothing is safely changeable (OPS-07, OPS-14).

The ranked remediation sequence is in **[TECHNICAL_DEBT.md §2](TECHNICAL_DEBT.md)** (package table) and **§2.1** (what M1 actually
closed); `docs/roadmap/MODERNIZATION_ROADMAP.md`, which earlier revisions of this file pointed at, **does not exist yet** — it
is the milestone M1 docs package, and citing an unwritten file is itself a DOC-class defect. The dependency rule to respect
is unchanged: **tests before refactors** (OPS-14 blocks TEN-06 and BKL-02), **schema baseline before entity changes** (OPS-07
blocks DB-01/DB-05), and **SEC-01/02/03 before any deploy** — those three are public information in the repository itself
(credential in git history, signing key in source, bootstrap password in documentation), which is why they do not depend on
whether the recorded EC2 host currently answers.

## 15. Baseline build status (verified)

The full command-by-command matrix lives in **[BASELINE_STATUS.md](BASELINE_STATUS.md)**; this table is only the
verdict, and every row there is backed by an executed command rather than an expectation.

| Check | Verified result |
|---|---|
| `mvn compile` on JDK 21 | **BUILD SUCCESS** |
| `mvn compile` on JDK 24 (the shell default on this machine) | fails — `Fatal error compiling: ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN`; Lombok/javac incompatibility, not a source defect |
| `mvn test` | **BUILD SUCCESS while executing nothing** — surefire reports `No tests to run`, `src/test` does not exist |
| `mvn -DskipTests package` | success — runnable Boot jar produced |
| Frontend `npm ci` / `npm run build` | both succeed; single 1.147 MB JS chunk, no code splitting |
| `npm test` | impossible — script and framework absent (FE-14) |
| `docker compose config` without `.env` | exit 1 — `env file .env not found` (OPS-08) |
| `docker compose up -d postgres` | success once `.env` exists |
| App boot against an **empty** PostgreSQL 16 under the committed `ddl-auto=validate` | **confirmed failure**: `Schema-validation: missing table [academic_years]` (OPS-07) |
| App boot with `ddl-auto=update` against the same database | success — Hibernate creates the schema; this is the only way the application can start today |
| Runtime security probes | forged-token acceptance, default-credential login, hash logging, error-body leakage and a 500 on `GET /users` were all **reproduced**; see BASELINE_STATUS §4 |
| App boot against live Neon | **not attempted** — production access requires owner authorisation |

## 16. M1 remediation status (`fix/gms-critical-security`, verified)

Mandate §3: *"Do not mark a vulnerability as fixed merely because code has been changed."* Each row therefore states
the **status vocabulary** and the **evidence that earned it**, and nothing is claimed beyond what was executed. All
evidence below is against a **local** dev-profile boot on a disposable PostgreSQL 16 container, except the two
deploy-workflow rows, whose evidence is a **CI run on the production path**. Nothing in this table means the
deployed system changed: run 41245769175 was *refused* by the host-key guard and the EC2 host is unreachable (§17),
so no M1 change has run in production — which is the intended outcome of that guard, not an accident.

Status vocabulary — **Verified** = observed at runtime or asserted by an executed test · **Changed** = code is correct
in `HEAD` but the behaviour was not observed · **Open** = untouched by this branch · **Owner** = requires an action
outside the repository.

| Finding | Status | Evidence |
|---|---|---|
| SEC-01 committed Neon credential | **Owner** | The literals are gone from `application.properties:24-26` (`${DB_URL:}` etc.), which is a repository change only. The credential in git history is still valid until rotated; `git log -p` still shows it. Revocation is the owner's action and no amount of code change performs it. |
| SEC-02 publicly known JWT key | **Verified** (local) | `JwtTokenProvider` has no default; blank, <32-byte and retired-default secrets fail startup outside `dev`, and `dev` gets a per-process `SecureRandom` key. `JwtTokenProviderTest` (10 cases) proves a token from one dev boot is unreadable by another; `verify-security.sh` proves a token forged with the old published secret is answered **401**. |
| SEC-03 default bootstrap credential | **Verified** | `SuperAdminBootstrapProperties` defaults are `enabled=false` with blank identity and secret, the credential that historically shipped as the repository default is refused case-insensitively (a real bug caught by `SuperAdminBootstrapPropertiesTest`: the comparison was against the lower-cased literal, so the capitalised form everyone was told to use passed), and enabled-with-incomplete-config aborts startup. The dev boot in BASELINE_STATUS §13 created its account purely from environment variables. |
| SEC-04 client-controlled role assignment | **Verified** | `UserServiceImpl.authoriseRoles` validates every code against `RoleEnum` and requires a SUPERADMIN authority to grant SUPERADMIN; `UserServiceImplRoleGuardTest` (4 cases) covers `ROOT`, unknown codes and the update path. |
| SEC-05 unguarded inherited CRUD | **Open** (1 of 7) | Only `UserController` left the template. `Parent`, `Student`, `Subject`, `School`, `Classroom`, `Employee` still inherit `GET /{id}` and `DELETE /{id}` returning raw entities with no tenant predicate — the delete path in AP-3 is unchanged. Needs the ownership guard from the tenant-isolation package, not a per-controller patch. |
| SEC-06 matcher order blocking portals | **Verified** (admin paths) | `verify-security.sh` §3 exercises both directions: an admin token reaches `/users` and `/users/me`, is refused `/students/my-fees` with **403**, and an unlisted path fails closed with **403** under `anyRequest().denyAll()`. **Not** verified: STUDENT and PARENT portal happy paths, because a disposable database with no seeded enrolments cannot produce those principals. |
| SEC-07 password hashes in logs | **Verified** | `CustomUserDetailsService` logs only `enabled/locked/passwordExpired` flags; the full dev boot log for the matrix run (login failures, lockout, authenticated reads) contains no `$2a$` digest — grep of the captured log, not an assumption. |
| SEC-08 error bodies disclose internals | **Verified** | 500 body is `{traceId,path,error,message}` with a reference-only message; 403 body is `{"error":"Forbidden"}`; `GlobalExceptionHandlerTest` asserts a 500 whose exception message contains a JDBC URL and a password leaks neither. |
| SEC-09 unauthenticated self-register, unprotected password change | **Verified** | `POST /auth/self-register` is now `hasAnyAuthority("ADMIN","SUPERADMIN")` and answers **401** anonymously. `change-password` reads the principal from the token, verifies `oldPassword` with `passwordEncoder.matches`, and enforces `PasswordValidator` — no caller-supplied username. |
| SEC-10 no throttling, lockout columns ignored | **Verified** | `LoginAttemptService` on the pre-existing `login_attempts`/`account_locked` columns; the matrix's §5 proves ten failures set `account_locked=true` (read from the database, not inferred) and the **correct** password is then refused **401**. `LoginAttemptServiceTest` covers the reset path. |
| SEC-11 no revocation / refresh | **Open** | Untouched; needs a token-version or jti store (schema work, M2+). |
| SEC-12 plaintext gateway keys in responses | **Open** | `PaymentGatewayController.java:53,60` still return `List<PaymentGateway>`/`PaymentGateway`; the redacting mapper remains create/update-only. Scheduled with the financial-integrity package. |
| SEC-13 actuator without authentication | **Verified** | `/actuator/info` is **401** anonymously and **200** for SUPERADMIN; only `health` stays public, on the readiness group, with `show-details=never` in prod. |
| SEC-14 plaintext HTTP, host disclosed | **Open** | Deployment-side (`vercel.json` rewrite, TLS termination); unreachable from here — see §17. |
| SEC-15 documented names do not bind | **Verified** (dev) | `application.properties` now carries `${SECURITY_JWT_SECRET}`, `${APP_SUPERADMIN_*}`, `${DB_*}` placeholders; `.env.example` and Compose pass the same names; the memory-bank page that told operators to set `SUPERADMIN_PASSWORD` is corrected. Prod-profile boot **not** executed. |
| SEC-16 inert `hasRole` guards | **Verified** | 13 → 0 (`git grep hasRole(` is empty), converted to the 216-guard `hasAuthority` convention already in the codebase rather than a new mechanism. |
| BKL-07 client errors as 500 | **Verified** | New 400/404 handlers plus `@Email`; live probes in BASELINE_STATUS §13.4 and `GlobalExceptionHandlerTest` (4 cases). |
| BKL-08 bootstrap admin cannot create users | **Open** — new | Discovered while probing BKL-07; deliberately not patched in a security branch because the fix is a schema/audit-trail decision (§13's missing audit trail). |
| OPS-08 Compose cannot boot from a fresh clone | **Changed** | Obsolete `version:` key removed, `${DB_PASSWORD:?}` now fails loudly, `DB_URL`/credentials/`SPRING_PROFILES_ACTIVE` are passed, healthcheck moved to `/actuator/health/readiness`. Still `env_file: .env` on both services, so a clone without a `.env` still fails at `docker compose config` — the full fix belongs to the M4 Docker package. |
| OPS-11 debug logging in every profile | **Verified** | Base and prod default to INFO (`application.properties:95-98`); DEBUG is only what `application-dev.properties` opts into, and `StartupSecurityValidator` refuses to boot with `com.gms` at DEBUG/TRACE outside `dev`. |
| OPS-14 no test suite | **Changed** | 36 backend tests now execute under `mvn test` and JaCoCo writes a report; `mvn -DskipTests` is no longer the only command that works. Frontend still has zero tests (FE-14) and no Playwright/Testcontainers coverage — that is M3. |
| FE Quick-Login default credential button | **Verified** | Removed from `Login.jsx`, along with the blank `defaultValues`; the "Clear Storage & Logout" escape hatch is retained. |
| Deploy workflow triggered by documentation edits | **Verified** | `deploy-backend.yml` `paths:` narrowed to backend/runtime inputs and `ci.yml` ignores docs. The workflow **executed** on the merge commit (run 41245769175, 2026-10-31T21:54:46Z) — a `master` push touching `gms_backend/src/**` did trigger it, which is the observable half of the trigger contract. The negative case (a docs-only push not triggering) has not yet been exercised. |
| SSH host-key verification disabled in deploy | **Verified** | `SSH_OPTS` pins `StrictHostKeyChecking=yes` with a `known_hosts` file, and the job fails when `EC2_SSH_KNOWN_HOSTS` is unset. Run 41245769175 log: `##[error]secret EC2_SSH_KNOWN_HOSTS is not set … Deploy is refused rather than run with host-key checking disabled`. This is the only control in this table with a **production-path** runtime proof rather than a local one. |
| Dead password utilities packaged in the jar | **Verified** | `util/PasswordEncoderTest.java`, `util/PasswordVerificationTest.java`, `util/PasswordTestUtil.java`, the committed `PasswordVerificationTest.class` and the top-level `JwtDecoder.java` are deleted; the tracked-file list contains no `.class` entry. |

## 17. What M1 could not verify, and why

1. **The deployed backend is unreachable.** Three attempts now fail the same way — two from this machine and one
   from a GitHub runner (`ssh: connect to host … port 22: Connection timed out`, recorded in run 41245769175 at
   2026-10-31T21:55:01Z). The runner observation matters: it is not a local-network artefact. Every status above is
   therefore *local*. Mandate §25.13 and §27 apply literally: this branch is not evidence that production is safer,
   only that the code awaiting deployment is.
2. **`ci.yml` has executed and passed; `deploy-backend.yml` has executed and refused.** The first CI run on PR #10
   returned `Backend build and tests=success, Frontend lint and build=success` on `ubuntu-latest`, and the merge
   commit re-ran green in 50 s — so `mvn -B verify` against JDK 21 and `npm ci`/`npm run build` are proven on a
   clean runner, not just on this machine. The deploy workflow also ran and was stopped by its own host-key guard
   (§16), so the accurate statement is: **the pipeline that SSHes into production has never reached the host.**
   What remains unestablished: the CI checks are advisory (`required_status_checks: []`, `enforce_admins: false`,
   re-fetched in Infra-M0), the two frontend steps configured as `continue-on-error` did not have to prove anything,
   and neither workflow's `environment:` gate exists even though `Production` and `Preview` environments do.
   `EC2_HOST`/`EC2_USER`/`EC2_SSH_KEY` are provisioned; `EC2_SSH_KNOWN_HOSTS`, `SECURITY_JWT_SECRET` and
   `APP_SUPERADMIN_*` are not (`gh secret list`).
3. **Prod profile never booted.** `application-prod.properties` (`ddl-auto=validate`, Flyway enabled,
   `baseline-on-migrate=false`) is written but unexercised, and deliberately so: booting it needs the Neon credential
   the owner has not rotated, and §25.2 forbids connecting to production without explicit authorisation.
4. **Cross-tenant behaviour is untouched by design.** M1 closes authentication and authorisation *mechanisms*; the
   tenancy invariant (TEN-01…07) is the next package, because fixing SEC-05 and the ownership guards without a
   `TenantContext` would mean writing the same guard twelve times.


## 18. Infra-M0 — what the infrastructure audit changed here

The second MASTER ENGINEERING MANDATE (Production-Grade Infrastructure, Container Architecture, DevOps & CI/CD)
opened a separate findings namespace so this file stays the application's record and the platform gets its own:

- **[docs/infrastructure/INFRASTRUCTURE_AUDIT.md](../infrastructure/INFRASTRUCTURE_AUDIT.md)** — 22 `INF-*` findings
  with executed evidence, and the §17 revalidation of every claim carried forward from this document.
- **[docs/infrastructure/PRODUCTION_READINESS_MATRIX.md](../infrastructure/PRODUCTION_READINESS_MATRIX.md)** — the §14
  scorecard, ten areas, five statuses.
- **[docs/infrastructure/INFRASTRUCTURE_DECISIONS.md](../infrastructure/INFRASTRUCTURE_DECISIONS.md)** — fourteen ADRs,
  including the four that need an owner decision before any money or account is involved.

Four corrections to this file came out of that pass, and they are recorded here rather than silently patched:

| Item in this file | Correction | Where |
|---|---|---|
| §13 "`TraceIdFilter` exists but is not wired into the chain" | **Wrong.** It is a `@Component` at `Ordered.HIGHEST_PRECEDENCE` and does populate MDC; the true defect is that no `logging.pattern` emits `%X{traceId}`. §13 reworded. | INF-15 |
| OPS-04 "hostname echoed in logs, defeating the masking" | **Downgraded.** The failing run's log shows `EC2_HOST: ***` and `***@***`; GitHub masks secret values inside `set -x` traces. Annotated at §11. | INF-01 |
| OPS-03 host-key verification | **Closed in code *and* proven executed** — the guard refused a production deploy on its first run. §16 rows promoted from *Changed* to *Verified*. | audit §6.2 |
| §17 "the deploy workflow has never run; it needs owner-provisioned secrets" | **Partly wrong.** It ran on the merge commit, and `EC2_HOST`/`EC2_USER`/`EC2_SSH_KEY` **do** exist. Only `EC2_SSH_KNOWN_HOSTS` is absent. | audit §6.2 |

The consequence that reorders the programme is stated once, at the end of
[INFRASTRUCTURE_AUDIT.md §9](../infrastructure/INFRASTRUCTURE_AUDIT.md): the missing known-hosts secret is currently
the only thing standing between a merge to `master` and an ungated production deploy. Restoring SSH reachability
**before** the CI gate and the deployment pipeline are rebuilt converts a latent risk into an active one, so the
owner action list in BASELINE_STATUS §11 is now ordered, not merely enumerated.
