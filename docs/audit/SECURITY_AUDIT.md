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

Legend — **P** = matched by a `SecurityConfig` path rule; **M** = has method security; **✔** = tenant predicate applied in the service.

| Controller | Base path | P | M | ✔ | Verdict |
|---|---|---|---|---|---|
| Auth | `/auth` | permitAll | none | partial | **SEC-09** — 2 of 3 endpoints should not be public |
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
| Fee | `/fees` | ADMIN+ | 7 | yes | **SEC-06** — blocks the ACCOUNTANT role |
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

Structural conclusions: (a) 20 of 36 controllers have **no matching path rule**, so their gate is only the annotations they carry plus `authenticated()`; (b) 6 controllers carry **no method security at all** (Auth, User, Teacher, Attendance, Health, and the CRUD base); (c) tenant predicates are absent from 10 controllers' read paths. This is why mandate §6 requires an enforced invariant rather than 36 hand-gated files — see `SEC-REMED` below.

*Reading this table in November 2026: it is the audit's as-is moment and the Verdict column carries the corrections of
`C-1` as they were made, but the ✔ column is not current. `C-2` and `C-3` moved the tenant into `TenantContext` and
then into the query for every declared repository read (§5.3, §5.4), and `C-7` — 2026-11-01 — changed the meaning of
the **bold `no`s** in a way this table cannot show: for all nine entities that lacked the column — `Attendance`,
`ExamSubject`, `FeePayment`, `Result`, `StudentExam`, `StudentFee`, `TeacherAssignment`, `Timetable`, `TimetableSlot` —
the tenant is no longer only a service convention but a NOT NULL column the database compares against the row it
points at. Concretely, the `no` at
`/timetables` line 113 is now defended by `fk_timetable_classroom_same_school` as well as by the service, and the live
run measures a straddling write refused at 403. What this table still describes accurately is (a) and (b): 23 mutating
handlers reach a service with no `@PreAuthorize` (`C-5`), and a row is still loaded by raw id before anyone decides
whether the caller may name it (`C-4`).*

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
  `school_id`, and the one without an entity is held by a trigger. What this bullet still describes is
  `TEN-02`'s raw-id loads, which is `C-4`: a token from school B can still *fetch* school A's row by id and get a 404
  or a 403 only because a service remembers to check, not because anything below it cannot answer.

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
  declaration-level test cannot see them because no repository declares them. 67 call sites load a row by raw id with
  no ownership check (`TEN-02`). That is `C-4`.
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
* **Deny-by-default at the endpoint.** 23 mutating handlers still reach a service with no `@PreAuthorize`
  (`SEC-REMED`'s last clause, `C-5`). A school-scoped query behind an unguarded route is a scoped read that any
  authenticated caller may ask for; the scope makes it not-another-school's, it does not make it the caller's to ask.

### SEC-REMED — the invariant to introduce

Mandate §6 asks for centralised tenant resolution. Concretely, on the existing architecture and without new infrastructure:

* a request-scoped `TenantContext` bean populated from the authenticated principal, replacing static header re-parsing in `SecurityUtil`;
* every tenant-scoped repository method takes the tenant as a mandatory parameter (compile-time pressure instead of a forgotten `if`);
* entities that lack `school_id` (**TEN-04**) get a derived `schoolId` in their repository query via the owning join, or gain the column through a migration — not a service-level `if`;
* one `OwnershipGuard.assertAccessible(entityRef)` used by all IDOR'd endpoints, returning 404 to satisfy the resource-disclosure policy in mandate §6;
* `@PreAuthorize` retained, but path-prefix role rules removed so first-match shadowing cannot recur.

## 6. Privacy note

Mandate §19 applies: this system holds children's personal data, contacts, dates of birth, marks and financial records. The current state has cross-tenant child-record disclosure (**TEN-01**), PII printed to logs (**SEC-07**, **ACD-12**), no retention or deletion policy (**DOC**/**OPS-16**), attachment uploads disabled but `attachment_urls` columns present (**OPS-13**), and no audit trail of who read which student. A DPDP-oriented review should treat AP-4 as a reportable personal-data breach scenario, not merely a bug. This audit does not constitute legal advice and mandates the owner obtain appropriate review.
