# GMS Security Audit

**Date:** 2026-11-01 · **Baseline:** `256b8e4` · **Type:** static, white-box, full-source review plus build verification. No live exploitation was attempted and the production database was not contacted.

Findings carry IDs defined in [`REPOSITORY_AUDIT.md`](./REPOSITORY_AUDIT.md); this document does not restate them — it reorganises them by **attack path**, adds the endpoint gate matrix, and records remediation sequencing. Do not copy findings between these files; cite the ID.

---

## 1. Exposure model

The deployment referenced by [`gms_frontend/vercel.json:5`](../../gms_frontend/vercel.json) is a public hostname serving an API over plaintext HTTP on port 8080, with a frontend bundle that prefills the bootstrap administrator's credentials. Assume every finding below is reachable by an unauthenticated internet user today. Severity ratings reflect that, not a hypothetical intranet.

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
| `GET /notifications/user/{id}` | PARENT | any user's notifications (**TEN-03**) |
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
| AbstractCRUD (base) | inherited | varies | **none** | **no** | **SEC-05** — 7 subclasses inherit unguarded `GET /{id}` + `DELETE /{id}` |
| School | `/schools` | ADMIN+ | yes | yes | **SEC-05** — inherited delete reachable by any ADMIN |
| Student | `/students` | ADMIN+ | 15 | mostly | **SEC-06** — the 10 `my-*` STUDENT grants are shadowed |
| Parent | `/parents` | only `/profile`,`/children` | 14 | partial | inherited read/delete open; **ACD-01** create is broken |
| Employee | `/employees` | ADMIN+ | 4 | yes | inherited `GET /{id}` cross-tenant (**TEN-02**) |
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

### SEC-REMED — the invariant to introduce

Mandate §6 asks for centralised tenant resolution. Concretely, on the existing architecture and without new infrastructure:

* a request-scoped `TenantContext` bean populated from the authenticated principal, replacing static header re-parsing in `SecurityUtil`;
* every tenant-scoped repository method takes the tenant as a mandatory parameter (compile-time pressure instead of a forgotten `if`);
* entities that lack `school_id` (**TEN-04**) get a derived `schoolId` in their repository query via the owning join, or gain the column through a migration — not a service-level `if`;
* one `OwnershipGuard.assertAccessible(entityRef)` used by all IDOR'd endpoints, returning 404 to satisfy the resource-disclosure policy in mandate §6;
* `@PreAuthorize` retained, but path-prefix role rules removed so first-match shadowing cannot recur.

## 6. Privacy note

Mandate §19 applies: this system holds children's personal data, contacts, dates of birth, marks and financial records. The current state has cross-tenant child-record disclosure (**TEN-01**), PII printed to logs (**SEC-07**, **ACD-12**), no retention or deletion policy (**DOC**/**OPS-16**), attachment uploads disabled but `attachment_urls` columns present (**OPS-13**), and no audit trail of who read which student. A DPDP-oriented review should treat AP-4 as a reportable personal-data breach scenario, not merely a bug. This audit does not constitute legal advice and mandates the owner obtain appropriate review.
