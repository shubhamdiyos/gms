# GMS Security Audit

**Date:** 2026-11-01 · **Baseline:** `256b8e4` · **Type:** static, white-box, full-source review plus build verification. No live exploitation was attempted and the production database was not contacted.

Findings carry IDs defined in [`REPOSITORY_AUDIT.md`](./REPOSITORY_AUDIT.md); this document does not restate them — it reorganises them by **attack path**, adds the endpoint gate matrix, and records remediation sequencing. Do not copy findings between these files; cite the ID.

---

## 1. Exposure model

The deployment that `gms_frontend/vercel.json:5` used to point at was a public hostname serving an API over
plaintext HTTP on port 8080, reached by a frontend bundle that prefilled the bootstrap administrator's credentials.
**Two of those three facts have since changed**, and the sentence above is kept because it is what §1's severities
were rated against: the prefilled login was removed by security-M1 (verified in
[REPOSITORY_AUDIT.md §16](REPOSITORY_AUDIT.md)), and the EC2 rewrite was **deleted** on 2026-11-01 when the host was
confirmed permanently closed (`[INFRASTRUCTURE_AUDIT.md §10](../infrastructure/INFRASTRUCTURE_AUDIT.md)`). What has
**not** changed is the requirement: the replacement origin must not repeat it, so plaintext HTTP from a browser to
the API is a P0 acceptance gate for the OCI deployment, not a historical curiosity. Until a TLS-terminating proxy
and a real hostname exist, treat any future deployment URL as exposing credentials in transit by default.

Two facts make every other control moot and are therefore **P0 regardless of effort**:

* the signing key is a public constant, so authentication can be bypassed outright (**SEC-02**);
* the datasource credential is committed, so the isolation the application enforces (badly) is unnecessary to defeat (**SEC-01**).

Anyone holding the Neon credential can read every tenant's students, marks, payments and contacts without sending an HTTP request, and any write they make is invisible to application logs.

## 2. Attack paths

### AP-1 · Forge an administrator token (no credentials needed)

1. Construct a JWT with `sub` = any username, `roles` = `["SUPERADMIN"]`, `schoolId` = any integer, signed HS256 with the placeholder key from `JwtTokenProvider.java:22`.
2. Call any endpoint. `JwtAuthenticationFilter.java:39-45` accepts it; `SecurityConfig.java:43` grants `/schools/**`; `SecurityUtil` then *derives the tenant from the forged claim*, so the attacker chooses which school to appear to belong to.

Root cause: **SEC-02** plus the fact that tenancy travels in the token rather than being resolved server-side from the authenticated principal (mandate §6 "do not trust schoolId from … headers"). Note the mitigating detail: authorities are re-read from the database (`CustomUserDetailsService.java:42-44`), so the forged `roles` claim governs *tenant selection* while the DB governs *authorization*. A forged `schoolId` for a user who genuinely has SUPERADMIN is therefore sufficient for cross-tenant reach; and **SEC-04** lets the attacker obtain a genuine SUPERADMIN row first.

### AP-2 · Escalate through the ordinary user endpoints

1. Log in as any low-privilege role — a student, or an account created via the unauthenticated `POST /auth/self-register` (**SEC-09**).
2. `POST /api/v1/users/create` with `roles:["SUPERADMIN"]` in the body. No `@PreAuthorize` exists on `UserController`, `/users/create` matches no path rule, so `anyRequest().authenticated()` admits it, and `UserServiceImpl.java:85-87` persists the attacker's role list verbatim.
3. Log in as that account.

Same entry point reaches `PUT /api/v1/users/update`, which sets another user's password from the request body (`UserServiceImpl.java:117-122` with an explicit `password` value) — account takeover of any administrator in the caller's school without knowing their password.

> **Closed on branch (security-M1, then Stage 3 `C-5`).** `UserController` now carries 7 `@PreAuthorize` guards for
> its 7 handlers, `POST /users/create` and `PUT /users/update` are `ADMIN`/`SUPERADMIN`-gated and matched by
> `PLATFORM_MANAGEMENT` (`SecurityConfig.java:55-58`, applied at `:141`), a caller-supplied role list is no longer
> persisted verbatim — an unknown role and a `SUPERADMIN` grant from a caller who does not hold it both throw, which
> is what `UserServiceImplRoleGuardTest` pins — and an unmatched route is refused by `anyRequest().denyAll()` (`:152`)
> instead of admitted by `authenticated()`. The AP-2 above is the as-found path, kept because §2's severities were rated against
> it.

### AP-3 · Destroy another tenant

> **Closed 2026-11-01 on `feat/stage2-flyway-baseline` (Stage 3 `C-1`).** The inherited `DELETE /{id}` no longer
> exists; the deletes the frontend calls are declared explicitly, guarded `ADMIN`/`SUPERADMIN`, and refuse another
> school's row. Evidence in [§5.2](#52-status-after-stage-3-c-1-2026-11-01-featstage2-flyway-baseline).

`DELETE /api/v1/schools/{id}` is never overridden, so it is inherited from `AbstractCRUDController.java:52` and lands on `AbstractCRUDService.deleteById` — a real `repository.delete` with no tenant predicate and no role annotation. The only gate is `SecurityConfig.java:43`, which grants **ADMIN** as well as SUPERADMIN. An administrator of any school, created cheaply via AP-2, deletes schools by integer id. The same shape applies to `/students/{id}`, `/parents/{id}`, `/users/{id}`, `/employees/{id}`, `/classrooms/{id}`, `/subjects/{id}` (**SEC-05**).

`POST /api/v1/timetables` is worse in one respect and better in another: it is destructive (`timetable.getSlots().clear()`, `TimetableServiceImpl.java:48-64`) and takes `classroomId` from the body with no tenant parameter at all (**TEN-04**), but `@PreAuthorize` does gate it — so it needs a compromised admin rather than any authenticated user.

### AP-4 · Read every tenant's records by enumeration

Integer primary keys plus ~15 endpoints that accept a `schoolId` and ignore it (**TEN-01**). No guessing is required beyond incrementing an id:

| Endpoint | Granted to | Returns |
|---|---|---|
| `GET /fee-payments/student/{id}` | PARENT | any student's payment history incl. bank, cheque, transaction ids |
| `GET /invoices/student/{id}` | PARENT | any student's invoices |
| `GET /receipts/student/{id}` | PARENT | any student's receipts |
| `GET /results/student/{id}` | STUDENT | any student's marks |
| `GET /student-exams/student/{id}` | STUDENT | any student's exam registrations |
| `GET /attendance/student/{id}`, `/classroom/{id}` | TEACHER | any school's attendance |
| `GET /notifications/user/{id}` | ~~PARENT~~ **ADMIN/SUPERADMIN** | was any user's notifications (**TEN-03**); the route is narrowed in `fix/tenant-notification-recipients` and the recipient match is now exact, but the shared read flag is still open (§16.1) |
| `GET /users`, `GET /users/{id}` | any authenticated (**SEC-04**) | usernames, emails, phones, roles, enabled/lock state |
| `GET /parents`, `GET /parents/{id}` | mixed | parent contact details; `Parent` is a global table (**TEN-05**) |

`GET /users` filters by the caller's school (`UserController.java:46-47`) so it is a horizontal rather than cross-tenant leak — but it returns raw `User` entities (**BKL-02**), which is the widest PII surface in the API.

### AP-5 · Settle a fee without paying

`FeePaymentServiceImpl.java:54-62` trusts the submitted amount and stamps `SUCCESS`; `:69 → :171-185` decrements the balance. `transactionId`, `paymentReference` and `chequeNumber` are free text and are never verified, because `PaymentGatewayServiceImpl` implements no payment operations at all — it is a configuration table with an unused `webhookUrl` (**FIN-01**). Any parent or accounting role that can reach `POST /fee-payments` obtains a zero-cost balance and a receipt-adjacent record. There is no ledger row to disagree with it, since payment never writes `FinancialTransaction` (**FIN-04**), and `deleteFeePayment` leaves the credit in place after cancelling (**FIN-05**).

### AP-6 · Brute force and credential stuffing

No throttling, no lockout enforcement, and a generic 401 that also masks server errors (**SEC-10**). The `loginAttempts`, `account_locked` and `password_expired` columns are decorative. The published bootstrap password (**SEC-03**) means the attack is not even brute force: it is a single documented request to the deployed `/api/v1/auth/login`.

### AP-7 · Steal live sessions

Tokens live in `localStorage` (`authService.js:8-9`) and are attached by JS, so any XSS exfiltrates a fully valid one-hour bearer credential — and **FE-02** means one environment variable in the hosting dashboard removes the real backend entirely, after which `mockAuthService.js:77-83` accepts any password and defaults to SUPERADMIN, with the on-screen indicator removed by commit `8ba3342`. Client-side role checks (`ProtectedRoute.jsx:30`) are cosmetic by construction (**FE-03**).

### AP-8 · Read the attack surface from the logs

**SEC-07** writes password hashes to stdout on every login *and every authenticated request*; **OPS-11** has `com.gms` at DEBUG in all profiles; **SEC-08** returns exception classes, root causes and source file/line in HTTP bodies, so an unauthenticated prober can map internal structure; `CorsConfig.java:27` logs the allowed-origin list; **ACD-12** logs new administrator passwords at INFO three times. A log reader is an authenticated-user-equivalent.

### AP-9 · Supply-chain and deploy-channel

**OPS-01/02** — pushing to `master` redeploys production with no build or test step, via an SSH script that is not in version control, with host-key verification disabled (**OPS-03**) and the target hostname echoed into public job logs (**OPS-04**). An attacker with write access to the repository, or a position on the deploy network path, obtains code execution on the box holding the tenant data. **OPS-15** means a `.env.production`-style file would be committed by default.

## 3. Endpoint gate matrix (36 controllers)

*The matrix below has 37 rows: 35 controller files, the
`ExamSubject` row — whose endpoints live on `ExamController`, there is no `ExamSubjectController` — and the
`AbstractCRUD` row, which was a 36th file at the audited revision and is deleted (`C-1`). The heading read "36
controllers" while that base class still existed. Corrected 2026-11-02: `ls gms_backend/src/main/java/com/gms/controller
| wc -l` now returns **36**, because `DashboardController` joined the package while `AbstractCRUD` left it — the base
class's deletion and the new controller offset each other, but the *composition* of the 35 file rows is no longer what
the directory holds: the matrix **omits `DashboardController`** (see `controller/DashboardController.java`). Its two
handlers are neither unguarded nor unmatched — `GET /dashboard/school` is `hasAnyAuthority('ADMIN', 'SUPERADMIN')`
and `GET /dashboard/platform` `hasAuthority('SUPERADMIN')`, and the `DASHBOARD` matcher group
(`SecurityConfig.java:89-91`, applied at `:147`) names its prefix — so the omission is a gap in this table, not a gap
in the perimeter. Read the 35 rows as "the controllers that existed when the matrix was drawn, minus `AbstractCRUD`
rendered as its own row".*

Legend — **P** = matched by a `SecurityConfig` path rule; **M** = has method security; **✔** = tenant predicate applied in the service.

| Controller | Base path | P | M | ✔ | Verdict |
|---|---|---|---|---|---|
| Auth | `/auth` | login permitAll | 2 | partial | **SEC-09** — ~~2 of 3 endpoints should not be public~~ → `C-5`/`C-6`: only `login` is anonymous; `change-password` is guarded `isAuthenticated()` (`AuthController.java:113`) under `SELF_SERVICE`, and `self-register` is guarded `ADMIN`/`SUPERADMIN` (`:139`) with its own perimeter rule (`SecurityConfig.java:150`) |
| User | `/users` | only `/users/me` | **none** | partial | **SEC-04/05** — create/update/list/by-id/delete open to any role |
| AbstractCRUD (base) | ~~inherited~~ | ~~varies~~ | ~~none~~ | ~~no~~ | **SEC-05 — closed 2026-11-01 (`C-1`)**: the base class is deleted, so no subclass inherits a mapping |
| School | `/schools` | ADMIN+ | yes | yes | ~~**SEC-05** — inherited delete reachable by any ADMIN~~ → `C-1`: the delete is not declared; the read is platform-owner-or-own-school |
| Student | `/students` | ADMIN+ | 15 | mostly | **SEC-06** — the 10 `my-*` STUDENT grants are shadowed |
| Parent | `/parents` | only `/profile`,`/children` | 14 | partial | ~~inherited read/delete open~~ → `C-1`: neither read nor delete is declared; **ACD-01** create is broken |
| Employee | `/employees` | ADMIN+ | 4 | yes | ~~inherited `GET /{id}` cross-tenant (**TEN-02**)~~ → `C-1`: the read is declared and school-scoped |
| Teacher | `/teachers` | 3 specific paths | **none** | partial | other methods fall to `authenticated()` |
| TeacherAssignment | `/teacher-assignments` | **none** | 8 | yes | path rule absent → role gate only |
| Classroom | `/classrooms` | ADMIN+ | 6 | yes | **SEC-06** — TEACHER grants shadowed |
| Section | `/sections` | **none** | 5 | partial | no class-level `@Transactional` (**ACD-06** family) |
| Subject | `/subjects` | ADMIN+ | 6 | yes | TEACHER read grant shadowed; `update()` throws |
| AcademicYear | `/academic-years` | ADMIN+ | 5 | yes | `@Valid` missing (**ACD-09**); single-current rule absent |
| Attendance | `/attendance` | TEACHER+ | **none** | **no** | **TEN-01** — three reads ignore tenant |
| Timetable | `/timetables` | TEACHER+ | 4 | **no** | **TEN-04** — destructive, body-supplied classroom |
| Exam | `/exams` | **none** | 6 | yes | no `passingMarks` validation (**ACD-07**) |
| ExamSubject | (via Exam) | **none** | – | **no** | `findByExamId` unfiltered |
| StudentExam | `/student-exams` | **none** | 4 | **no** | **TEN-01** — STUDENT may read any student's registrations |
| Result | `/results` | **none** | 5 | **no** | **TEN-01** — cross-tenant marks disclosure (**ACD-04**) |
| GradingScheme | `/grading-schemes` | **none** | 5 | yes | bands unvalidated (**ACD-05**) |
| Fee | `/fees` | ADMIN+ | 7 | yes | ~~**SEC-06** — blocks the ACCOUNTANT role~~ → `C-6`: `ACCOUNTANT` is mintable and the `FINANCIAL` matcher names it (`SecurityConfig.java:144`); writes stay `ADMIN` (§5.5) |
| FeeStructure | `/fee-structures` | **none** | 6 | yes | written, never read (**FIN-04**) |
| StudentFee | `/student-fees` | **none** | 9 | **no** | **FIN-02** — every list returns empty |
| FeePayment | `/fee-payments` | **none** | 9 | **no** | **FIN-01** unverified success; **TEN-01** leak |
| Invoice | `/invoices` | **none** | 7 | **no** | **FIN-04** orphaned; **TEN-01** leak |
| Receipt | `/receipts` | **none** | 7 | **no** | same |
| PaymentGateway | `/payment-gateway` | **none** | 5 | yes | **SEC-12** — secrets in responses |
| FinancialTransaction | `/financial-transactions` | **none** | 8 | yes | manual CRUD only |
| FinancialReport | `/financial-reports` | **none** | 7 | yes | client-supplied totals; delete is a no-op (**FIN-06**) |
| FinancialDashboard | `/financial-dashboard` | **none** | 1 | yes | fabricated metrics (**FIN-06**) |
| Notification | `/notifications` | ADMIN+ | 14 | partial | **SEC-06** STUDENT/PARENT shadowed; **TEN-03** |
| Announcement | `/announcements` | ADMIN+ | 14 | partial | **SEC-06**; **COM-03** |
| Communication | `/communications` | **none** | 7 | – | **COM-02** — 100% stub, returns fake success |
| SchoolCalendar | `/school-calendar` | **none** | 7 | yes | no date validation |
| StudentAdmission | `/student-admissions` | **none** | 2 | partial | `POST /apply` open, no `@Valid` (**ACD-09**) |
| ParentStudent | `/parent-students` | **none** | 4 | **no** | **TEN-05** — first-child inference |
| Health | `/ping` | permitAll | – | – | acceptable |

Structural conclusions as audited: (a) 20 of the 35 controllers that existed then had **no matching path rule**, so
their gate was only the
annotations they carry plus the `anyRequest().authenticated()` catch-all; (b) several controllers carried **no method
security at all** (Auth, User, Teacher, Attendance, Health, and the CRUD base); (c) tenant predicates were absent from
10 controllers' read paths. This is why mandate §6 requires an enforced invariant rather than 35 hand-gated files — see
`SEC-REMED` below.

**Both (a) and (b) are now false as descriptions of the code, and the corrections are structural, not cosmetic.**
(a) The catch-all is `anyRequest().denyAll()` (`SecurityConfig.java:152`), the matcher groups (`SecurityConfig.java:55-95`) name every
prefix the 36 controllers serve except `/parents/**` outside its self-service trio — so an unlisted route is now
*refused for every caller* rather than admitted to any authenticated one. Fail-closed is the right default; it is not
the same control as "reachable", and it moves the risk from disclosure to outage. The one route group this row can name
as unreachable today is `POST /parents/create`, `PUT /parents/update`, `GET /parents`, `PATCH /parents/toggle`
(`ParentController.java:39-76`, all four guarded `ADMIN`/`SUPERADMIN`): no URL matcher covers them, so `denyAll()`
answers 403 before the guard is consulted. That is a perimeter inventory task, not a tenancy one, and it is recorded
here rather than resolved.
(b) 234 of the 236 handler methods now carry their own `@PreAuthorize` — counted by `DenyByDefaultAuthorizationArchTest`
off the compiled handlers rather than by `grep -c` per file over `controller/`, because the source text holds 236
`@PreAuthorize` *occurrences* and two of them sit inside javadoc (`SubjectController.java:21`,
`ClassroomController.java:21`), so only the bytecode state matches what Spring's handler mapping reads; the
two exceptions being `AuthController.login` and `HealthController.ping`; `AttendanceController` has 8 guards for 8
handlers, `UserController` 7/7, `TeacherController` 3/3, `AuthController` 2/3, and no controller carries a class-level
guard at all. See `ARCHITECTURE.md §7.1` for the test that pins it.

*Reading this table in November 2026: it is the audit's as-is moment and the Verdict column carries the corrections of
`C-1` as they were made, but the ✔ column is not current. `C-2` and `C-3` moved the tenant into `TenantContext` and
then into the query for every declared repository read (§5.3, §5.4), and `C-7` — 2026-11-01 — changed the meaning of
the **bold `no`s** in a way this table cannot show: for all nine entities that lacked the column — `Attendance`,
`ExamSubject`, `FeePayment`, `Result`, `StudentExam`, `StudentFee`, `TeacherAssignment`, `Timetable`, `TimetableSlot` —
the tenant is no longer only a service convention but a NOT NULL column the database compares against the row it
points at. Concretely, the `no` at
`/timetables` line 113 is now defended by `fk_timetable_classroom_same_school` as well as by the service, and the live
run measures a straddling write refused at 403. Two more columns have moved since, and both moved to a mechanism
rather than to a re-count. The **M** column is now a floor, not a measure: the rows that read `none` (User, Teacher,
Attendance, and Auth — which today carries 2 guards for its 3 handlers, `change-password` behind `isAuthenticated()`
and `self-register` behind `ADMIN`/`SUPERADMIN` at both layers) are guarded handler-by-handler today — 234 of 236
methods carry `@PreAuthorize`, the two exceptions being
`AuthController.login` and `HealthController.ping`, and `DenyByDefaultAuthorizationArchTest` refuses a third (§5.5).
The **✔** column now describes two mechanisms where the audit saw one: a by-id read resolves through
`repository/TenantScopedRepository.java:56-61`, whose `findById` override asks the database for
`e.id = :id and e.school.id = :schoolId` (`:44-45`) with the tenant taken from the claim, and 33 of the 36
repository *interfaces* extend it — three are exempt, each with a stated reason: `ParentRepository` (global by D-2),
`SchoolRepository` (the tenant table itself) and `DashboardStatisticsRepository` (bound to `School`, declaring counts
only, so it carries no inherited by-id read of a tenant-owned row). `TenantScopedRepository` is the 37th file in
`repository/` and not a repository of an entity.
So school B naming school A's id no longer *loads* the row: it matches nothing, and the 404 comes from the query
rather than from a service remembering to check. What the ✔ column still cannot show is `findAll`, `findAllById` and
the inherited `delete`, which remain unfiltered and are recorded as open by `TenantScopedReadArchTest.java:39-41`.
`SEC-06` is no longer a finding in the present tense. Its two halves closed as two mechanisms: the **grants shadowed**
rows (`Student`'s `my-*`, `Classroom`, `Subject`, `Notification`, `Announcement`) closed with `C-5` on 2026-11-02, when
the self-service matchers were moved ahead of the administrative ones (`SecurityConfig.java:128-138`, before
`PLATFORM_MANAGEMENT` at `:141`), so a STUDENT calling `GET /timetables/student/me` — the example the shadowing needed,
because first-match-wins had the `ACADEMIC_OPERATIONS` group refuse it before the handler's own grant was reached — is
no longer refused by the perimeter; and the **unreachable role** half closed with `C-6` the same day, when `ACCOUNTANT`
became a `RoleEnum` constant and the guards that named it became satisfiable (§5.5). What survives of the finding is
its cause: first-match-wins is still the matcher semantics, so a new route without a matcher is a 403 for everybody —
the `ParentController` case below stands as the live example.*

## 4. What is genuinely good here

Worth preserving, and easy to lose in a rewrite:

* Authorities are loaded from the database per request, so a revoked or role-changed user is governed immediately — the token's role claim is not trusted for authorization.
* BCrypt with `requirePasswordChange` on first login, and a real complexity regex (`PasswordValidator.java:8`).
* `csrf.disable()` is correct for stateless bearer auth; `sessionCreationPolicy(STATELESS)` is correct; `open-in-view=false` is the right call even though **BKL-02** is its consequence; `server.error.include-message=never` is present.
* Money is `BigDecimal(10,2)` throughout with **no** float/double anywhere — the hardest thing to retrofit later was already done.
* Optimistic locking via `@Version` on the audit base and `User`.
* CORS is a maintained allowlist, not `*`; the mail path is env-driven and clean; no real `.env` ever entered git history.
* Method security is enabled and, where authors remembered to use it (`StudentController`, `SchoolController`, fee controllers), it is reasonably granular and consistently uses unprefixed authorities.

## 5. Remediation sequencing

Dependency order, not preference order:

1. **Owner-side, before anything else** — rotate the Neon credential (**SEC-01**); provision `security.jwt.secret` and `app.superadmin.password` in the deployment environment. Removing a literal from `HEAD` does not revoke a credential that is already in history (**mandate §25**). This step is external to the repository and requires the owner.
2. **Fail-closed configuration** — no insecure defaults, startup assertions, a `prod` Spring profile (**OPS-13**). Without this, any fix in step 3 can silently regress.
3. **Kill the escalation and destruction paths** — **SEC-04**, **SEC-05**, **SEC-09**, **SEC-07**, **SEC-08**, **SEC-12**, **SEC-13**. These are local, high-value, low-risk edits.
4. **Rebuild authorization as one mechanism** — resolve tenant and principal from `SecurityContext`, delete the contradictory path-prefix rules (**SEC-06**), and add a single ownership assertion used by every tenant-scoped service (**TEN-01…07**).
5. **Test gate before further refactors** — the cross-tenant matrix from mandate §6 is the acceptance test for steps 3–4, and **OPS-14** means there is currently no way to prove any of it. Step 5 must land in CI (**OPS-01/02**) or the fixes are unverifiable, which mandate §3 explicitly forbids ("do not mark a vulnerability as fixed merely because code has been changed").
6. **Financial integrity** — **FIN-01…06**, after tenant enforcement exists, since a payment state machine is meaningless while balances are cross-tenant writable.
7. **Transport and hosting** — **SEC-14**, **OPS-03/04/05/06**.

### 5.1 Status after M1 (`fix/gms-critical-security`)

Step 2 landed completely and step 3 landed **partially**: **SEC-04**, **SEC-07**, **SEC-08**, **SEC-09**, **SEC-10**,
**SEC-13**, **SEC-15**, **SEC-16** and **BKL-07** are closed with runtime or test evidence, while **SEC-05** (unguarded
inherited `GET /{id}` / `DELETE /{id}`) and **SEC-12** (plaintext gateway keys in responses) are untouched, and
**SEC-11** (revocation) and **SEC-14** (transport) remain open by dependency. The row-by-row dispositions, with the
evidence each one earned, are in [REPOSITORY_AUDIT.md §16](REPOSITORY_AUDIT.md) and the command log is in
[BASELINE_STATUS.md §13](BASELINE_STATUS.md).

Two things this audit must not let the reader conclude:

* **Nothing here is deployed.** The EC2 host is unreachable, so every verification is against a local dev-profile boot
  on a disposable database. The attack paths AP-1…AP-9 may still describe the live system.
* **Step 4 is not done.** AP-1's root cause — tenancy travelling in the token instead of being resolved server-side
  from the authenticated principal (**TEN-01…07**) — is unchanged by M1. M1 made the *mechanisms* honest (a role check
  now means something, a denied request now says so); it did not make the *tenancy invariant* true. A caller who
  legitimately holds a SUPERADMIN token can still address another school's rows through the unfiltered repositories,
  which is exactly what the tenant-isolation package and mandate §6's cross-tenant matrix exist to close.

### 5.2 Status after Stage 3 `C-1` (2026-11-01, `feat/stage2-flyway-baseline`)

**SEC-05 is closed on the branch** and **AP-3 is closed with it**. The mechanism the finding named — an inherited,
unguarded, tenant-blind `DELETE /{id}` reachable by any administrator — no longer exists in the code at all:
`AbstractCRUDController` and `AbstractCRUDService` are deleted, `grep -rl "extends AbstractCRUDController"
controller/` returns nothing, and every delete the frontend actually calls (`DELETE /api/v1/subjects/{id}`,
`DELETE /api/v1/classrooms/{id}`) is now declared explicitly, guarded `hasAnyAuthority('ADMIN', 'SUPERADMIN')`, and
refuses a row belonging to another school. The read side is the same story: `GET /{id}` on `Student`, `Employee` and
`School` is tenant-scoped, `Parent`'s is not declared at all, and `User`'s read and delete no longer reach
`repository.findById`/`deleteById` with no predicate.

*Evidence:* `EndpointInheritanceTest` (33 cases) and `scripts/verify-c1-tenancy.sh` (29 live checks against the
packaged jar on PostgreSQL 16.15, 0 failures); the deliberate-failure control red on exactly the two guarded
assertions; full suite **78 green**.

Two facts about this closure that belong in the record rather than in a footnote:

* **The first draft of `ADR-023` was wrong about the frontend.** It claimed no frontend call site existed for
  `DELETE` on these resources. `apiSlice.js:258` and `:290` do call it. The inherited delete was therefore live
  application behaviour, not a latent route — which is why the two deletes are re-declared rather than dropped, and
  why the ADR now carries the correction in the open.
* **AP-3's shape survives in a narrower form.** The platform owner still holds a SUPERADMIN token that reaches across
  tenants by design, and `TEN-01…07` — tenancy resolved server-side from the authenticated principal rather than
  trusted from the token — was still open. `C-1` closed the *unauthenticated* destruction path; it did not close the
  *trusted-but-wrong-tenant* one. That was `C-2`, `C-3` and `C-4`. The first two have since landed (§5.3, §5.4) and so
  has `C-7`, which removes `TEN-04`'s unmapped tenant column from the list — every tenant table with an entity now maps
  `school_id`, and the one without an entity is held by a trigger. What this bullet described when it was written —
  `TEN-02`'s raw-id loads, where "a token from school B can *fetch* school A's row by id and get a 404 or a 403 only
  because a service remembers to check" — is no longer the state of the code, and the sentence was the wrong shape in
  one respect even then: the remembering was already happening. See §5.5.

One unrelated defect was found and fixed on the way, and it was not a Stage 3 finding: every method-level
`@PreAuthorize` refusal was answering **500** instead of **403**, because `AuthorizationDeniedException` (a subclass of
`AccessDeniedException`) reached the catch-all `@ExceptionHandler(Exception.class)`. A client-mistake mapping was wrong
in the same way — a method mismatch answered 500 instead of 405, and a delete the database refused because another row
still referenced it answered 500 instead of 409. `GlobalExceptionHandler` now maps all three.

### 5.3 Status after Stage 3 `C-2` (2026-11-01, `feat/stage2-flyway-baseline`)

**The first clause of `SEC-REMED` is met.** The audit's own sentence for it was: *"Tenant is re-derived per call from
the raw HTTP header by a static holder (`util/SecurityUtil.java:59-99`) rather than from the already-authenticated
`SecurityContext`."* That is no longer true. `SecurityUtil` is deleted; `JwtAuthenticationFilter` parses the claims
**once** and puts the tenant, the employee and the roles into `security/TenantContext`, a request-scoped holder cleared
in a `finally` so a pooled thread cannot carry one caller's tenant into the next caller's request. 221 service methods
no longer take the tenant as a parameter, and `TenantParameterArchTest` fails the build if one ever does again.

What this closes, precisely:

* **The signature re-verification.** `SecurityUtil` re-parsed *and re-verified* the JWT signature on every one of its
  325 call sites across 41 files — the same token was checked three times in most controllers (username, roles,
  school) and again in every service that wanted the tenant.
* **The parameter a caller may forget or get wrong.** A service method that takes `schoolId` can be called with the
  wrong one. There is no longer such a parameter to get wrong: the tenant is a property of the request, not of the
  call.
* **The audit trail as a parameter.** `UserServiceImpl` now resolves the creator from the context, so `created_by`
  cannot be pointed at somebody else's account by a caller.

*Evidence:* `TenantContextTest` (5 cases), `TenantParameterArchTest` (1 case), `GlobalExceptionHandlerTest` (9 cases),
`scripts/verify-c2-tenant-context.sh` (32 live checks against the packaged jar on PostgreSQL 16.15, 0 failures) and
`scripts/verify-c1-tenancy.sh` re-run against the same build (29 live checks, 0 failures); the deliberate-failure
control red on exactly the "parsed once" assertion; full suite **86 green**.

**What this left open.** `SEC-REMED`'s second clause — *"every tenant-scoped repository method takes the tenant as a
mandatory parameter"* — was `C-3`, and it is the half that matters for a cross-tenant **read**. `C-2` made the tenant
unambiguous; it did not make every query school-scoped. The queries that still span schools were listed in
[BASELINE_STATUS.md §15.8](BASELINE_STATUS.md) and were the remaining part of `TEN-01`. The audit's own rows for
`TEN-01` — the cross-tenant reads on `/fee-payments/student/{id}`, `/invoices/student/{id}`,
`/receipts/student/{id}`, `/results/student/{id}`, `/student-exams/student/{id}` and `/attendance/student/{id}` — were
therefore **not** closed by this card, and the table in §4 must not be read as saying otherwise. `C-3` has since
closed them for the reads a repository declares; §5.4 is that record.

One inconsistency was closed along the way because the live run exposed it: a cross-tenant refusal answered **400** on
the write path and **403** on the read path. 23 refusals now throw `TenantAccessDeniedException`, which extends
`AccessDeniedException`, so both paths answer 403.

### 5.4 Status after Stage 3 `C-3` (2026-11-01, `feat/stage2-flyway-baseline`)

**The second clause of `SEC-REMED` is met — for the reads this repository declares.** At the close of `C-2` there were
55 declared repository reads naming no school, 37 of them with no defence. Now every declared read either carries the
school in its own predicate or sits on an allow-list that states the constraint making it global by design, and a
build-failing test refuses a new declared read that does neither.

The instrument is `RepositoryTenantScopeArchTest`, which reads the compiled repository interfaces rather than a
hand-maintained list, because a list is what every previous count in these documents was and two of them were wrong.
At the commit immediately before `C-3`: **170 declared reads, 115 carrying the tenant, 55 carrying none, 37
offences** — the test red. On this branch: **168, 150, 18, 0** — green. The 18 are 12 in `SchoolRepository` (the
tenant table itself; a query that *is* the school needs no school predicate), 4 in `UserRepository` (2 keyed on
`uq_users_username`, V3:34, which is global, and 2 scoped transitively because the employee row they start from
already belongs to one school) and 2 in `ParentRepository` (`uq_parents_email`, V3:38, global by owner decision D-2).
Ten methods were deleted instead of rewritten where a scoped form already existed — the bug was two ways to ask the
same question, one of them unsafe — and the rest were rewritten to name the school.

*Evidence:* full suite **87 green**; `scripts/verify-c3-scoped-reads.sh` **25 checks, 0 failures** against the packaged
jar on the same build and database that the `C-1` (29) and `C-2` (32) scripts pass; **the same script against the
pre-`C-3` build gives 6 failures**, the first being school B reading school A's timetable through the unguarded
`GET /timetables/classroom/{id}` — the defect `TEN-01` described, observed live on one side of a rebuild; the SQL the
application emitted, captured from its own log, contains the school predicate; and four deliberate-failure controls,
each red on exactly the assertion meant to catch it and no other. Two of those controls failed *before* the change
they were meant to prove, and the reason (a restored file kept an older mtime, so Maven skipped recompiling it) is
recorded in BASELINE_STATUS §16.4 — a control that passes for the wrong reason is worse than no control.

Per-school email uniqueness is a **deliberate behaviour change**, not collateral: `POST /users/create` and
`POST /employees/create` now collide against `uq_users_school_email` (V3:41) and `uq_employees_school_email` (V3:53)
instead of a global `email`, because both keys were always per-school and the global check was a cross-tenant oracle
that told school A which mailboxes school B had registered. Both directions are pinned — the same address is accepted
in school B after school A claims it, and refused again in school A.

*What this does not close.* Three things, in order of how easy they are to mistake for done:

* **Inherited CRUD.** `JpaRepository` hands every repository `findById`, `findAll`, `findAllById` and `delete`, and a
  declaration-level test cannot see them because no repository declares them. **Closed for by-id reads by `C-4`** —
  read §5.5, which also corrects what this bullet's original wording claimed about those reads. `findAll`,
  `findAllById` and the inherited `delete` stay open.
* **Level 3 of ADR-020 — closed 2026-11-01 by `C-7`, and it is worth reading what closed it.** Postgres enforces a
  composite foreign key with MATCH SIMPLE, so a `NULL` tenant column satisfies it — and the application *was* writing
  `NULL`: on the verification database, after a live create through `TimetableServiceImpl`, the `timetables` row had
  `school_id = NULL` even though `V2` added the column, because nine entities did not map it. So `V5`'s same-school
  constraints bound the data a careful migration inserts and not the data the running software writes. `C-7` maps the
  nine, fills them from the claim rather than from the parent row, and lands all ten deferred `SET NOT NULL` flips as
  `V6` behind backfill-and-assert, adds seven more same-school pairs, and holds `parent_student` — a `@ManyToMany` join
  table with no entity to map — by database trigger. Measured now: `rows with a NULL tenant, in any tenant table,
  platform-wide: 0`; 18 same-school FKs; and a cross-tenant `POST /timetables` refused with **403** by
  `fk_timetable_classroom_same_school` rather than by a `WHERE` clause. What level 3 still does not do is stop school B
  *reading* school A's row — it constrains what may be written about a row, which is why the bullet above it stays open.
* **Deny-by-default at the endpoint.** 23 mutating handlers reached a service with no `@PreAuthorize` at `C-3`'s close
  (`SEC-REMED`'s last clause). A school-scoped query behind an unguarded route is a scoped read that any authenticated
  caller may ask for; the scope makes it not-another-school's, it does not make it the caller's to ask. **Closed by
  `C-5`** — §5.5.

### 5.5 Status after Stage 3 `C-4`, `C-5`, `C-6` and `C-8` (2026-11-02, `feat/stage2-flyway-baseline`)

**`TEN-02` was mis-stated in this register, and the correction matters more than the closure.** §5.4's third bullet and
the roadmap both counted the places a service called `findById` — "67 call sites … no ownership check" here, "155
`findById` and 6 `getById` call sites" in [BASELINE_STATUS.md §17.6](BASELINE_STATUS.md) — and the wording read as that
many live cross-tenant reads by id. It was not a leak count, and treating it as one hid what the actual defects were:

* **The refusal already existed; only its location was wrong.** Tenancy was a *convention* repeated inside service
  methods rather than one mechanism below them. A grep for `.equals(TenantContext.schoolId())` under `service/impl/`
  returns **107 lines** at the commit `C-4` starts from and **23 lines — 21 distinct comparisons** in the tree today,
  after `C-4` deleted the copies it made redundant and its follow-through classified the rest; the by-id probes run
  against two schools on the verification database returned no foreign bodies.
  The defect was **duplication** — a predicate in thirty-odd files, each of which could omit or mistype it — not an
  open leak. This audit will not carry a leak count it cannot stand behind: where a number is replaced here, the
  replacement is the mechanism and the file it lives in.
* **Two of those duplicated checks answered HTTP 500 to the row's rightful owner.** `StudentFeeServiceImpl.getStudentFeeById`
  and `FeePaymentServiceImpl.getFeePaymentById` compared
  `studentFee.getStudent().getSchoolId()` and `feePayment.getStudentFee().getStudent().getSchoolId()`. Both traverse
  associations declared `@ManyToOne(fetch = FetchType.LAZY)` (`model/entity/StudentFee.java:27-30`,
  `model/entity/FeePayment.java:27-30`), and `Student.getSchoolId()` (`model/entity/Student.java:77`) dereferences its
  own lazy `school`, so on a request whose session has already closed — `spring.jpa.open-in-view=false`,
  `application.properties:36` — the comparison initialises a proxy with nobody holding a session and surfaces as a
  server error. A tenant control that fails by refusing to answer the owner is still a failed control, and it is a
  worse one than a missing check because the response looks like an outage.
* **The closure is one mechanism at the query layer.** `repository/TenantScopedRepository.java` (a `@NoRepositoryBean`
  interface) overrides `findById` (`:56-61`) to call `findByIdOwnedBy(id, TenantContext.schoolId())` —
  `where e.id = :id and e.school.id = :schoolId` (`:44-45`) — and reserves `findByIdAnyTenant` for the platform owner
  and the bootstrap runner, with a `null` tenant matching no row rather than every row. **33 of the 36 repository
  interfaces extend it**; three do not, and `TenantScopedReadArchTest` prints the split — `33` scoped, `3` serving
  the tenant table itself or a row with no single owner: `ParentRepository` (global by D-2), `SchoolRepository`
  (the tenant *is* the row) and `DashboardStatisticsRepository` (bound to `School`, counts only — no inherited
  by-id read of a tenant-owned row to scope). The guard on the
  guard is `repository/TenantScopedReadArchTest`: red when a `TenantOwned` entity's repository sits outside the
  mechanism, when production code reaches for `getById`/`getReferenceById` on one — those hand back a proxy before any
  predicate can run, so they are forbidden rather than filtered — and when anything outside the interface itself calls
  `findByIdAnyTenant`. `EntityLazyAccessorArchTest` is the companion that keeps the 500 class of defect out of the
  entities' own derived accessors.
  Still open in the same finding, and named by `TenantScopedReadArchTest.java:39-41`: inherited `findAll`,
  `findAllById` and `delete`.

**`C-5` — deny-by-default at the endpoint — is closed, and closed by a build-time scan rather than by a re-count.**
`security/DenyByDefaultAuthorizationArchTest.java` (7 tests) reads the compiled controller classes and fails the build
when a handler declares no `@PreAuthorize` of its own, when a guard sits on the class instead of the method, when a
guard uses `hasRole`/`hasAnyRole` (the granted authorities carry no `ROLE_` prefix), when a guard names an authority
nobody is ever granted, when a public exemption no longer matches a real handler or covers more than one, or when the
anonymous allow-list grows past `AuthController.login` and `HealthController.ping`. Measured against the compiled
handlers today:
**234 guards over 236 handler methods, 2 named exemptions** — and *compiled* is load-bearing, because
`grep -c @PreAuthorize` over `controller/` reports 236 occurrences, two of them inside javadoc
(`SubjectController.java:21`, `ClassroomController.java:21`), while the mapping-annotation count does agree with the
scan; the deliberate-failure control was run: deleting `AnnouncementController.createAnnouncement`'s
`@PreAuthorize` fails the test while printing `C-5: 236 handlers, 233 guarded, 2 deliberately public`, and restoring
it returns `234 guarded` and green. No class-level guard exists; `SecurityConfig.java:152` is `anyRequest().denyAll()`, so
the URL layer no longer stands in for a missing annotation. What the test does not prove is in its own javadoc
(`:66-91`) and in `ARCHITECTURE.md §7.1`: a guard is a gate, not a tenant predicate, and presence is not correctness.

`SEC-06`'s remaining half — the headline of this register for two revisions, that `ACCOUNTANT` was a granted, guarded
authority no code path could mint — is **closed by `C-6`, executed 2026-11-02**. The mechanism is small and it is real:
`enums/RoleEnum.java` now declares **six** constants including `ACCOUNTANT` at `RoleEnum.java:11`; `user_roles.role` is
a plain `varchar(30)` with no Postgres enum and no `CHECK`, so no migration was needed to make it issuable. The
**39** guards that already named it — measured over `controller/`, the whole money module — became satisfiable rather
than permanently closed, and the `FINANCIAL` matcher admits `ACCOUNTANT` (`SecurityConfig.java:144`), so the URL layer
and the guard layer now agree on a role a caller can actually hold. The `DenyByDefaultAuthorizationArchTest` check
that every authority a guard names must be a role the application can mint — the check that existed to stop this
mismatch drifting further — now reports guards naming exactly 6 distinct authorities
(`ACCOUNTANT, ADMIN, PARENT, STUDENT, SUPERADMIN, TEACHER`), all of them mintable. `SEC-17`'s mirror closes with it:
`/auth/self-register` no longer writes the literal `"EMPLOYEE"` — a string no guard names and no enum declares — and
routes its role through `UserService.authoriseRoles`, the same validator `/users/create` uses, which rejects a code
outside `RoleEnum` before the username is taken.

*What `C-6` does **not** close, stated so the row above is not read bigger than the card.* The accountants' **writes**
do not exist as a decision: every `POST`/`PUT`/`DELETE` on the finance controllers is still `hasAuthority('ADMIN')`
(`FeeController.java:30`, `:36`, `:43` and their siblings), which is a policy inherited from the pre-`ACCOUNTANT`
codebase and a product decision with a tenant consequence — not a guard to add quietly. And the minted role has no
surface beyond one page: `apiSlice.js` has no endpoints for the finance reads `ACCOUNTANT` is allowed beyond fees
(`/fees`, `/student-fees/student/{id}` and the student/parent fee reads are there; `fee-structures`, `invoices`,
`receipts`, `financial-transactions`, `financial-reports` are not), so an accountant logs in and sees one screen.

**`C-8`, executed 2026-11-02, closes the write half of the audit trail.** The gap was stated in the migration's own
header — the question "who registered this pupil" had no answer in the database for four of the busiest tables — and
it now does, through one mechanism rather than per-service memory: `AuthorshipEntity`
(a `@MappedSuperclass` declaring `@CreatedBy`/`@LastModifiedBy` once) is inherited by `BaseAuditEntity`, putting
`created_by`/`updated_by` on **33 of the 36 entity classes** — 28 through `BaseAuditEntity`, 5 (`School`, `Student`,
`Classroom`, `Subject`, `StudentAdmission`) through `AuthorshipEntity` directly because their timestamps are named
`created_at`/`updated_at`; `AcademicYear`, `Employee` and `User` carry no auditing author (`Employee`/`User` map a
business `created_by` association to their creating account, which is a different statement from the auditing one);
`V7__record_who_wrote_the_pupil.sql` adds those columns
to the four tables that had timestamps but no actor columns — `students`, `classrooms`, `subjects`,
`student_admissions` — with the eight `REFERENCES public.users(id)` keys, `NO ACTION` like the baseline's other actor
columns, because `ON DELETE SET NULL` would let an account deletion erase the trail; and the JWT now carries `uid`
(`users.id`, `JwtTokenProvider.java:127-135`) so the auditor resolves the author from the verified claim with
`getReferenceById` and issues no query inside the flush. What `C-8` does **not** close, in its own card's words:
hard deletes remove the row *and* its trail, so "who deleted this" has no answer, and no API surfaces the trail to a
school — the columns and the `created_by_id`/`updated_by_id` response fields exist, but there is no audit view.

Stage-3-wide evidence at the close of these cards, on a fresh V1→V7 database with two tenants: `verify-mvp-e2e.sh`
**294 passed / 0 failed / 0 skipped, FEATURES PROVED: 11 of 11**; the per-card scripts unchanged on the final build
— `C-1` 29/0, `C-2` 32/0/0, `C-3` 24/0/1, `C-4` 118/0/17, `C-7` 65/0/1; unit and architecture suite **106 green**.

### SEC-REMED — the invariant to introduce

Mandate §6 asks for centralised tenant resolution. Concretely, on the existing architecture and without new infrastructure:

* a request-scoped `TenantContext` bean populated from the authenticated principal, replacing static header re-parsing in `SecurityUtil`; — **done (`C-2`)**: `security/TenantContext`, filled by `JwtAuthenticationFilter`, cleared in a `finally`; `util/SecurityUtil` deleted.
* every tenant-scoped repository method takes the tenant as a mandatory parameter (compile-time pressure instead of a forgotten `if`); — **done differently, and the difference is load-bearing**: `C-2` *forbade* a tenant parameter on a service method (`TenantParameterArchTest`) because the value a caller may pass is the value a caller may get wrong. The tenant reaches the query from `TenantContext`, explicitly named in every declared predicate (`C-3`) and in `TenantScopedRepository.findById` (`C-4`).
* entities that lack `school_id` (**TEN-04**) get a derived `schoolId` in their repository query via the owning join, or gain the column through a migration — not a service-level `if`; — **done (`C-7`)**: the column, `V2` then `V6`'s `SET NOT NULL`, and nine new entity mappings.
* one `OwnershipGuard.assertAccessible(entityRef)` used by all IDOR'd endpoints, returning 404 to satisfy the resource-disclosure policy in mandate §6; — **not built, and the shape changed instead of the count**: an ownership guard is a check *after* the load, which is what made `TEN-02` a duplication problem. `TenantScopedRepository.findByIdOwnedBy` (`:44-45`) answers 404 by matching no row, which satisfies the same disclosure policy from the query layer, and `TenantScopedReadArchTest` enforces that every tenant-owned repository goes through it. A separate guard would now be the third mechanism for one predicate.
* `@PreAuthorize` retained, but path-prefix role rules removed so first-match shadowing cannot recur. — **half done**: the annotations are retained and now total 234 over 236 handlers (counted off the compiled bytecode, for the reason given in §5.5) with a test refusing a new unguarded one (`C-5`, §5.5). The prefix rules were **not** removed; `SEC-06`'s shadowing was fixed by ordering self-service matchers first (`SecurityConfig.java:128-138`, ahead of the administrative groups from `:141`, the javadoc's rule 1) and the catch-all became `denyAll()` (`:152`). First-match-wins is still the semantics, so the rule that a new route needs a matcher is still a rule in prose — `ARCHITECTURE.md §7.1`'s fourth limitation, and the reason `ParentController`'s four administrative parent endpoints 403 today.

## 6. Privacy note

Mandate §19 applies: this system holds children's personal data, contacts, dates of birth, marks and financial records. The state at audit had cross-tenant child-record disclosure (**TEN-01** — since closed for declared and by-id reads by `C-3`/`C-4`; the inherited `findAll`, `findAllById` and `delete` are the named residue, §5.5), PII printed to logs (**SEC-07**, **ACD-12**), no retention or deletion policy (**DOC**/**OPS-16**), attachment uploads disabled but `attachment_urls` columns present (**OPS-13**), and no audit trail of who read which student — the **write** half of that sentence changed on 2026-11-02: `C-8` stamps `created_by`/`updated_by` on 33 of the 36 entity classes (§5.5), but a read leaves no trace, hard deletes destroy the write trail with the row, and no API surfaces the trail to a school, so the sentence still stands for anything a school could actually look at. A DPDP-oriented review should treat AP-4 as a reportable personal-data breach scenario, not merely a bug. This audit does not constitute legal advice and mandates the owner obtain appropriate review.
