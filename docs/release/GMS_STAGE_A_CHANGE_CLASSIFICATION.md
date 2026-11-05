# GMS Stage A: Working Tree Preservation and Change Classification

**Date:** 2026-11-03
**Branch:** `test/playwright-e2e`
**HEAD:** `71117ed74b2d028a8ceb97477382fa0fc41c4794`
**Upstream:** `origin/test/playwright-e2e` (HEAD is 6 commits ahead)
**Working tree:** 30 modified tracked files, 40+ untracked files, 0 staged

---

## 1. Git State Reconfirmation

```
Branch:           test/playwright-e2e
HEAD SHA:         71117ed74b2d028a8ceb97477382fa0fc41c4794
HEAD message:     docs(nfr-02): document owner-accepted exceptions for 3 concurrent breaches
Upstream:         origin/test/playwright-e2e (ab8eeef555c070040adab18e224365f70c5b35cc)
Ahead of upstream: 6 commits
Merge base:       ab8eeef555c070040adab18e224365f70c5b35cc
Staged files:     0
Modified files:   30 (unstaged)
Untracked files:  40+ (3 production source, 18 test source, 19 documentation)
```

---

## 2. Modified Tracked Files (30 files)

### 2.1 Build Configuration (1 file)

| File | What Changed | Defect/Requirement | Dependencies | Type |
|------|-------------|-------------------|--------------|------|
| `gms_backend/pom.xml` | Added 3 test-scoped dependencies: `org.testcontainers:testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter` | Task #62 — PostgreSQL integration test infrastructure | `BasePostgresIntegrationTest`, `AttendancePostgresIntegrationTest` | Production (build) |

### 2.2 Security Configuration (1 file)

| File | What Changed | Defect/Requirement | Dependencies | Type |
|------|-------------|-------------------|--------------|------|
| `gms_backend/.../config/SecurityConfig.java` | (1) New import `PasswordChangeEnforcementFilter`; (2) New constructor parameter `passwordChangeFilter`; (3) `.addFilterAfter(passwordChangeFilter, JwtAuthenticationFilter.class)` added to filter chain | SEC-CRED-02 — Mandatory password change enforcement | `PasswordChangeEnforcementFilter` (untracked new file) | Production |

### 2.3 Controllers (14 files)

All 14 controller changes follow the same pattern: adding `SUPERADMIN` to `@PreAuthorize` annotations on GET (read) endpoints. This is **complementary to, not overlapping with** commit `23a0c65` which added `SUPERADMIN` to 42 WRITE operations across 19 controllers. The working tree changes address the READ endpoints that commit `23a0c65` did not cover.

| File | Endpoints Affected | Defect/Requirement | Dependencies | Type |
|------|-------------------|-------------------|--------------|------|
| `AnnouncementController.java` | 10 GET endpoints: list, by-id, published, by-target-audience, by-type, by-category, by-priority, active, pinned | F-4 — SUPERADMIN role coverage on reads | None | Production |
| `ExamController.java` | 3 GET endpoints: list, by-id, by-academic-year | F-4 | None | Production |
| `FeeController.java` | 4 GET endpoints: list, by-id, by-academic-year, total-amount | F-4 | None | Production |
| `FeePaymentController.java` | 6 GET endpoints: list, by-id, by-student, by-date-range, total-by-date-range, count | F-4 | None | Production |
| `FeeStructureController.java` | 3 GET endpoints: list, by-id, by-academic-year | F-4 | None | Production |
| `FinancialDashboardController.java` | 1 GET endpoint: dashboard | F-4 | None | Production |
| `FinancialReportController.java` | 4 GET endpoints: list, by-id, by-date-range, by-academic-year | F-4 | None | Production |
| `GradingSchemeController.java` | 2 GET endpoints: list, by-id | F-4 | None | Production |
| `ParentStudentController.java` | 2 GET endpoints: parent/{id}/students, student/{id}/parents | F-4 | None | Production |
| `PaymentGatewayController.java` | 2 GET endpoints: list, by-id | F-4 | None | Production |
| `ResultController.java` | 2 GET endpoints: by-student-exam, by-student | F-4 | None | Production |
| `SectionController.java` | 2 GET endpoints: list, by-id | F-4 | None | Production |
| `StudentExamController.java` | 2 GET endpoints: by-exam-subject, by-student | F-4 | None | Production |
| `StudentFeeController.java` | 6 GET endpoints: list, by-id, by-student, by-academic-year, total-collected, total-outstanding | F-4 | None | Production |

### 2.4 Response Models (2 files)

| File | What Changed | Defect/Requirement | Dependencies | Type |
|------|-------------|-------------------|--------------|------|
| `ParentResponse.java` | Added `@JsonInclude(NON_NULL)` and `temporaryPassword` field | ACD-13-02 — Return plaintext temporary password on creation | `ParentServiceImpl` | Production |
| `StudentResponse.java` | Added `@JsonInclude(NON_NULL)` and `temporaryPassword` field | ACD-13-02 | `StudentServiceImpl`, `TeacherServiceImpl` | Production |

### 2.5 Repository (1 file)

| File | What Changed | Defect/Requirement | Dependencies | Type |
|------|-------------|-------------------|--------------|------|
| `AttendanceRepository.java` | (1) Added `existsByStudent_School_IdAndStudent_IdAndTimetableSlot_IdAndDate()` for ATT-02 slot-level duplicate detection; (2) Added `insertIgnoreDuplicate()` native query with `INSERT ... ON CONFLICT DO NOTHING` for ATT-06 atomic upsert | ATT-01, ATT-02, ATT-06 | `AttendanceServiceImpl`, `BulkAttendanceResponse` | Production |

### 2.6 Service Interface (1 file)

| File | What Changed | Defect/Requirement | Dependencies | Type |
|------|-------------|-------------------|--------------|------|
| `StudentService.java` | Method signatures stripped of `empId`/`schoolId` parameters (moved to `TenantContext`); added `searchStudents`, `nextStudentId`, `createUserForStudent` returning `String`; `findBySchool` now takes `int size` | OPS-22 (unbounded list), ACD-13 (credential collision), ACD-02 (dashboard stubs) | `StudentServiceImpl` | Production |

### 2.7 Service Implementations (9 files)

| File | What Changed | Defect/Requirement | Dependencies | Type |
|------|-------------|-------------------|--------------|------|
| `AttendanceServiceImpl.java` | **Major rewrite:** (1) Replaced `StudentService` dependency with `StudentRepository`; (2) Replaced check-then-act with atomic `insertIgnoreDuplicate()`; (3) Validate-all-before-write pattern; (4) Lazy creator fetch; (5) Returns `BulkAttendanceResponse`; (6) Slot-level duplicate check via `existsBy...`; (7) Removed `AbstractCRUDService` inheritance | ATT-01 (response contract), ATT-02 (slot-level uniqueness), ATT-06 (atomicity) | `AttendanceRepository`, `BulkAttendanceResponse`, `StudentRepository` | Production |
| `EmailServiceImpl.java` | Removed plaintext email body from 3 `log.info` statements | SEC-CRED-01 — Credentials in email bodies must not appear in logs | `SecureCredentialGenerator` (indirect) | Production |
| `NotificationServiceImpl.java` | (1) Removed `schoolId`/`empId` parameters (use `TenantContext`); (2) Removed `AbstractCRUDService`; (3) Added `filterByAuthorization`, `isAuthorizedForNotification`; (4) Role-based recipient filtering (STUDENT/PARENT see only their own) | F-2 — Cross-user notification access | `TenantContext`, `TenantAccessDeniedException` | Production |
| `ParentServiceImpl.java` | (1) Removed `AbstractCRUDService`; (2) Removed `empId`/`schoolId` params; (3) Returns `List<ParentResponse>` (DTOs, not entities); (4) `createUserForParent` returns plaintext password; (5) Replaced hardcoded `"welcome123"` with `SecureCredentialGenerator.generate()` | ACD-13-02, OPS-22, F-1/F-2 | `SecureCredentialGenerator`, `ParentResponse` | Production |
| `ParentStudentServiceImpl.java` | (1) Removed `AbstractCRUDService`; (2) Removed `schoolId`/`empId` params; (3) `clear()` replaced with `removeIf` scoped to caller's school; (4) PARENT callers restricted to own family; (5) Returns DTOs | ISO-01 (cross-school join-row deletion), ISO-02 (cross-family read), ISO-03 | `ParentResponse`, `StudentResponse`, `TenantContext` | Production |
| `ResultServiceImpl.java` | (1) Removed `AbstractCRUDService`; (2) Removed params; (3) Tenant-scoped existence check; (4) `result.setSchool(school)` added; (5) STUDENT-role guard on `getResultsForStudentExam` | F-1, SEC-F1-02 | `TenantContext`, `TenantAccessDeniedException` | Production |
| `SchoolServiceImpl.java` | (1) Removed `AbstractCRUDService`; (2) Audit trail from `TenantContextAuditorAware`; (3) Replaced `base + "@123"` with `SecureCredentialGenerator.generate()`; (4) SUPERADMIN can list/read all schools | SEC-CRED-03, DB-05, F-4 | `SecureCredentialGenerator`, `TenantContextAuditorAware` | Production |
| `StudentServiceImpl.java` | (1) Removed `AbstractCRUDService`; (2) Paged search (`searchStudents`, `buildPageable`, cap 100); (3) Dashboard methods wired to real services; (4) Secure credential generation; (5) `TenantContext` for caller identity | ACD-13-02, ACD-02, OPS-22, F-1/F-2 | `AttendanceService`, `SecureCredentialGenerator`, `StudentResponse` | Production |
| `TeacherServiceImpl.java` | (1) Repository calls now include `TenantContext.schoolId()`; (2) `StudentResponse` constructor gains trailing `null` for `temporaryPassword` | F-1/F-2 | `TenantContext`, `StudentResponse` | Production |

### 2.8 Frontend (1 file)

| File | What Changed | Defect/Requirement | Dependencies | Type |
|------|-------------|-------------------|--------------|------|
| `gms_frontend/e2e/teacher-journey.spec.js` | Removed 2 unused imports (`API_ROOT`, `generatedUsername`) | Cleanup | None | Test (e2e) |

---

## 3. Untracked Files (40+ files)

### 3.1 Production Source (3 files) — All Required for Build

| File | Purpose | Relationship to Modified Code | Required? |
|------|---------|------------------------------|-----------|
| `gms_backend/.../model/response/BulkAttendanceResponse.java` | Lombok DTO with fields: `totalStudents`, `createdCount`, `skippedCount`, `skippedStudentIds`, `message` | Returned by `AttendanceServiceImpl.recordBulkAttendance()` | **YES** — compilation fails without it |
| `gms_backend/.../security/PasswordChangeEnforcementFilter.java` | Spring `OncePerRequestFilter` implementing SEC-CRED-02; blocks business APIs with 403 when `requirePasswordChange=true`; whitelists `/api/v1/auth/login`, `/api/v1/auth/change-password`, `/api/v1/users/me/**` | Wired into `SecurityConfig.filterChain()` | **YES** — `SecurityConfig` references it; context startup fails without it |
| `gms_backend/.../util/SecureCredentialGenerator.java` | 12-char cryptographically secure password generator (`SecureRandom`); guarantees lowercase, uppercase, digit, special char; Fisher-Yates shuffle | Called by `StudentServiceImpl`, `ParentServiceImpl`, `SchoolServiceImpl` | **YES** — compilation fails without it |

### 3.2 Test Source (18 files) — Not Required for Production Build

| File | Purpose | Tests What | Category |
|------|---------|-----------|----------|
| `BasePostgresIntegrationTest.java` | Abstract base for PostgreSQL integration tests; connects to `localhost:5433/gms_test`; Flyway migrations | Test infrastructure | Integration base |
| `AttendancePostgresIntegrationTest.java` | 9 tests: bulk commit, classroom mismatch, missing student, duplicate skip, retry idempotency, **concurrent submissions (5 threads)**, cross-school isolation, constraint violation | ATT-01/02/06, R4.4/R4.7 | Integration |
| `AttendanceServiceImplSlotLevelTest.java` | 9 unit tests for ATT-02 slot-level duplicate detection | `AttendanceServiceImpl` | Unit |
| `BulkAttendanceAtomicityDefectTest.java` | 4 unit tests for ATT-06 validate-all-before-write | `AttendanceServiceImpl` | Unit |
| `BulkAttendanceResponseContractTest.java` | 7 unit tests for ATT-01 response contract invariant | `AttendanceServiceImpl`, `BulkAttendanceResponse` | Unit |
| `EmailServiceImplLoggingSecurityTest.java` | 5 unit tests for SEC-CRED-01 no-logging-of-credentials | `EmailServiceImpl` | Unit |
| `NotificationAuthorizationTest.java` | 15 unit tests for F-2 notification recipient-level access | `NotificationServiceImpl` | Unit |
| `NotificationCollectionAuthorizationTest.java` | 10 unit tests for F-2 collection endpoint filtering | `NotificationServiceImpl` | Unit |
| `ParentCredentialProvisioningTest.java` | 4 unit tests for ACD-13-02 parent credential security | `ParentServiceImpl` | Unit |
| `ParentStudentAuthorizationTest.java` | 11 unit tests for SEC-F3-06 parent-student relationship auth | `ParentStudentServiceImpl` | Unit |
| `ResultServiceAuthorizationTest.java` | 9 unit tests for SEC-F1-02 result ownership | `ResultServiceImpl` | Unit |
| `SchoolServiceImplCredentialCollisionTest.java` | 2 unit tests for SEC-CRED-03 school admin password security | `SchoolServiceImpl` | Unit |
| `StudentCredentialProvisioningTest.java` | 5 unit tests for ACD-13-02 student credential security | `StudentServiceImpl` | Unit |
| `PasswordChangeEnforcementFilterTest.java` | 10 integration tests for SEC-CRED-02 filter behavior | `PasswordChangeEnforcementFilter` | Integration |
| `SuperAdminReadAuthorizationTest.java` | Parameterized reflection test scanning all `@GetMapping` methods for `SUPERADMIN` | All 14 controllers | Audit |
| `SecureCredentialGeneratorTest.java` (in `util/`) | 5 tests: policy compliance, length, character classes, uniqueness (1000 generations) | `SecureCredentialGenerator` | Unit |
| `application-test.properties` (in `resources/`) | Test profile: PostgreSQL `localhost:5433/gms_test`, Flyway, `ddl-auto=validate` | Test infrastructure | Config |

### 3.3 Documentation (19 files) — Not Required for Build

| File | Subject |
|------|---------|
| `FINAL_BACKEND_CLOSURE_REPORT.md` | Final backend closure and PWA readiness |
| `GMS_DEPLOYMENT_READINESS.md` | Deployment readiness assessment |
| `GMS_PERFORMANCE_REPORT.md` | 50k-record performance measurements |
| `GMS_RELEASE_ACCEPTANCE.md` | Release acceptance criteria |
| `GMS_REMEDIATION_TRACKER.md` | Defect tracking register |
| `GMS_SECURITY_AUDIT.md` | Security audit findings |
| `R3.1_CREDENTIAL_SECURITY_REPORT.md` | Credential security remediation |
| `R3.2_VERIFICATION_CLOSURE_REPORT.md` | Verification closure |
| `R3.3_SEC_CRED_02_VERIFICATION_REPORT.md` | Password enforcement verification |
| `R3.4_SEC_CRED_02_TARGETED_VERIFICATION.md` | Targeted password verification |
| `R3.5_SEC_CRED_02_TEST_EVIDENCE_CORRECTION.md` | Test evidence correction |
| `R4.1_ATTENDANCE_VERIFICATION_REPORT.md` | Attendance verification |
| `R4.2_ATTENDANCE_TRANSACTION_INTEGRITY_REPORT.md` | Transaction integrity |
| `R4.3_POSTGRES_ATTENDANCE_INTEGRITY_REPORT.md` | PostgreSQL integration |
| `R4.4_TESTCONTAINERS_INFRASTRUCTURE_REPORT.md` | Testcontainers infrastructure |
| `R4.5_INTEGRATION_INFRASTRUCTURE_HARDENING_REPORT.md` | Infrastructure hardening |
| `R4.6_ATTENDANCE_REGRESSION_STABILIZATION_REPORT.md` | Regression stabilization |
| `R4.7_ATTENDANCE_CONCURRENCY_VERIFICATION_REPORT.md` | Concurrency verification |
| `R4_PARENT_ATTENDANCE_REMEDIATION_REPORT.md` | Parent/attendance remediation |

---

## 4. Local Six Commits — PR Mapping

### Commit Inventory

| # | SHA | Message | Files Changed | Maps To |
|---|-----|---------|---------------|---------|
| 1 | `3a1b24e` | `fix(security): close F-1 and F-2 cross-user access control gaps` | `FeePaymentController`, `InvoiceController`, `ReceiptController`, `ResultController`, `StudentFeeController`, `NotificationServiceImpl`, + 2 docs | PR #32 |
| 2 | `e1eb8e7` | `docs(pg17): verify V10 migration on PostgreSQL 17.11` | `GMS_REMEDIATION_TRACKER.md` | PR #32 |
| 3 | `23a0c65` | `fix(security): F-4 — add SUPERADMIN to 42 write operations across 19 controllers` | 19 controllers + `AttendanceController`, `AcademicYearController`, etc., + 2 docs | PR #32 |
| 4 | `050849a` | `test(e2e): add teacher, student, and parent browser journeys (WP8)` | 3 e2e specs + `helpers/api.js` + 1 doc | PR #32 |
| 5 | `3673ce7` | `fix(backend): ACD-13, ACD-02, OPS-22 — credential collision, student stubs, nginx proxy` | `SchoolServiceImpl`, `StudentServiceImpl`, `V10__index_the_measured_reads.sql`, `SchoolServiceImplCredentialCollisionTest`, `nginx.conf` | PR #32 |
| 6 | `71117ed` | `docs(nfr-02): document owner-accepted exceptions for 3 concurrent breaches` | `GMS_REMEDIATION_TRACKER.md` | PR #32 |

### PR Mapping

**PR #32** (`test/playwright-e2e` → `ci/live-tenancy-suites`):
- All 6 commits belong to PR #32's branch
- PR #32 base is `ci/live-tenancy-suites` (not `master`)
- PR #32 head is `origin/test/playwright-e2e` at `ab8eeef`
- Local HEAD (`71117ed`) is 6 commits ahead of PR #32's remote head

**PR #30** (`ci/live-tenancy-suites` → `master`):
- PR #30 head is `origin/ci/live-tenancy-suites` at `6fee7cf`
- Current branch contains `6fee7cf` as an ancestor
- All 6 local commits are descendants of PR #30's head
- PR #30 is fully incorporated into the current branch

**PR #29** (`docs/record-pr26-merge` → `master`):
- PR #29 head is `origin/docs/record-pr26-merge` at `a89a0cf`
- PR #29 diverged from the current branch line at `8145a31` (PR #26 merge)
- Current HEAD is 20 commits ahead of PR #29's head
- PR #29 is stale relative to current work; its documentation changes are superseded

---

## 5. Overlapping Changes and Superseded Implementations

### 5.1 Controller SUPERADMIN Changes — Complementary, Not Overlapping

**Commit `23a0c65`** added `SUPERADMIN` to 42 **WRITE** operations (`@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`) across 19 controllers.

**Working tree** adds `SUPERADMIN` to **READ** operations (`@GetMapping`) across 14 controllers.

**Evidence:** `AnnouncementController.java` was NOT modified by commit `23a0c65` (it only has GET endpoints). The working tree diff adds `SUPERADMIN` to all 10 of its `@GetMapping` annotations. These are distinct, complementary changes.

**Risk:** None. The two change sets target different HTTP methods.

### 5.2 Service Layer — No Overlap

The 6 committed changes touch `SchoolServiceImpl` and `StudentServiceImpl` (commit `3673ce7`) and `NotificationServiceImpl` (commit `3a1b24e`). The working tree has further modifications to these same files.

**Evidence:**
- `SchoolServiceImpl.java`: Commit `3673ce7` replaced hardcoded password with `SecureCredentialGenerator`. Working tree diff shows additional changes (removing `AbstractCRUDService`, adding audit trail, SUPERADMIN access).
- `StudentServiceImpl.java`: Commit `3673ce7` added credential collision handling. Working tree diff shows further changes (paged search, dashboard wiring, DTO mapping).
- `NotificationServiceImpl.java`: Commit `3a1b24e` added recipient-level filtering. Working tree diff shows additional authorization methods.

**Risk:** The working tree changes build on the committed changes. They must be committed together to maintain a coherent history.

### 5.3 Attendance Implementation — Working Tree Only

The atomic upsert (`AttendanceRepository.insertIgnoreDuplicate()` and `AttendanceServiceImpl` rewrite) exists ONLY in the working tree. No commit contains these changes.

**Risk:** HIGH — this is the P0 concurrency fix. It must be committed.

### 5.4 SecurityConfig + PasswordChangeEnforcementFilter — Working Tree Only

`SecurityConfig.java` references `PasswordChangeEnforcementFilter`, which is an untracked new file. If committed separately, the build would fail because the filter class doesn't exist in the committed tree.

**Risk:** CRITICAL — `SecurityConfig` and `PasswordChangeEnforcementFilter` must be committed atomically.

---

## 6. Atomic Attendance Upsert — Deep Inspection

### 6.1 SQL and Conflict Target

```sql
INSERT INTO attendances (
    school_id, student_id, timetable_slot_id, date,
    status, remarks, deleted, created_by, updated_by,
    created_on, updated_on, version
) VALUES (
    :schoolId, :studentId, :timetableSlotId, :date,
    :status, :remarks, false, :createdBy, :updatedBy,
    NOW(), NOW(), 0
)
ON CONFLICT (student_id, timetable_slot_id, date) DO NOTHING
```

**Conflict target:** `(student_id, timetable_slot_id, date)` — matches the unique constraint.

**Return value:** `int` — 1 if row inserted, 0 if conflict detected (duplicate).

### 6.2 Unique Database Constraint

**Source:** `gms_backend/src/main/resources/db/migration/V3__natural_keys.sql`, line 59:
```sql
ALTER TABLE attendances ADD CONSTRAINT uq_attendance_student_slot_date
    UNIQUE (student_id, timetable_slot_id, date);
```

**Verified:** The conflict target in the upsert matches this constraint exactly.

### 6.3 Tenant Isolation

- `school_id` is included in the INSERT columns, ensuring the record is associated with the correct tenant.
- The service method checks `TenantContext.schoolId()` at entry and passes it to `insertIgnoreDuplicate()`.
- Student lookup uses `studentRepository.findById()` which is tenant-scoped via `TenantScopedRepository`.
- Timetable slot lookup uses `findByIdAndSchool_Id(slotId, TenantContext.schoolId())` — a foreign slot returns 404.
- The `existsByStudent_School_IdAndStudent_IdAndTimetableSlot_IdAndDate()` method for single-record duplicate check includes `schoolId` in the predicate.

**Assessment:** Tenant isolation is enforced at every data access point.

### 6.4 Transaction Boundaries

- `AttendanceServiceImpl.recordBulkAttendance()` is annotated `@Transactional`.
- `AttendanceRepository.insertIgnoreDuplicate()` is annotated `@Modifying @Transactional`.
- The repository method participates in the service method's outer transaction (Spring's default `REQUIRED` propagation).
- Validation pre-pass (classroom membership check) throws `IllegalArgumentException` before any writes, ensuring clean rollback.
- No partial writes are possible: either all students are processed or the transaction rolls back entirely.

### 6.5 Duplicate and Retry Behavior

**First submission:** `insertIgnoreDuplicate()` returns 1 for each student. `createdCount` increments. Response: `BulkAttendanceResponse` with `createdCount = N`, `skippedCount = 0`.

**Retry (identical submission):** `insertIgnoreDuplicate()` returns 0 for each student. `skippedStudentIds` collects the IDs. Response: `createdCount = 0`, `skippedCount = N`.

**Concurrent identical submissions:** PostgreSQL serializes at the constraint level. One transaction's INSERT succeeds (returns 1); the other's INSERT hits the conflict (returns 0). Both transactions commit successfully — no exceptions, no rollbacks.

**Invariant:** `totalStudents == createdCount + skippedCount` always holds.

### 6.6 PostgreSQL Integration Test Coverage

**Test class:** `AttendancePostgresIntegrationTest` (9 tests, all passing)

| Test | Scenario | Coverage |
|------|----------|----------|
| R4.4-01 | All-valid bulk submission | Happy path |
| R4.4-02 | Classroom mismatch | Validation pre-pass, zero writes |
| R4.4-03 | Missing student | Validation pre-pass, zero writes |
| R4.4-04 | Existing duplicates skipped | Idempotency |
| R4.4-05 | Retry after lost response | Retry safety |
| R4.7-01 | **5 concurrent threads, same data** | Concurrency — the critical test |
| R4.4-07 | Cross-school isolation | Tenant boundary |
| R4.4-08 | Direct duplicate insertion | Constraint enforcement |
| R4.4-09 | Constraint violation via service | Error handling |

**R4.7-01 evidence:** 5 threads → 5 HTTP 200, 0 exceptions, 1 database record.

---

## 7. Outstanding Test Failures and Errors

**Total:** 240 tests run, 171 passed, 11 failures, 58 errors, 0 skipped (71.25% pass rate)

### 7.1 New Failures (Caused by Working Tree Changes)

| Test Class | Failures | Errors | Root Cause | Introduced By |
|------------|----------|--------|------------|---------------|
| `BulkAttendanceResponseContractTest` | 4 | 2 | Mocks expect old check-then-act pattern (`existsBy...` + `save()`); implementation now uses `insertIgnoreDuplicate()` | Working tree: `AttendanceServiceImpl` rewrite |
| `AttendanceServiceImplSlotLevelTest` | 3 | 0 | Same mock misalignment | Working tree: `AttendanceServiceImpl` rewrite |
| `BulkAttendanceAtomicityDefectTest` | 1 | 0 | Same mock misalignment | Working tree: `AttendanceServiceImpl` rewrite |
| `SchoolServiceImplCredentialCollisionTest` | 2 | 0 | Tests expect old password pattern (`username + "@123"`); implementation now uses `SecureCredentialGenerator.generate()` | Working tree: `SchoolServiceImpl` changes |
| `PasswordChangeEnforcementFilterTest` | 0 | 10 | `ApplicationContext` fails to load — `SecurityConfig` now wires `PasswordChangeEnforcementFilter`, but the test context configuration doesn't provide the required beans | Working tree: `SecurityConfig` + new filter |

**Subtotal: 10 failures, 12 errors = 22 test issues caused by working tree changes**

### 7.2 Infrastructure / Environment Failures

| Test Class | Failures | Errors | Root Cause | Pre-existing? |
|------------|----------|--------|------------|---------------|
| `EndpointInheritanceTest` | 0 | 33 | `ApplicationContext` initialization failure — cascading from first context load failure | **YES** — present before working tree changes; same `ApplicationContext` failure pattern |
| `EmptyCollectionContractTest` | 0 | 6 | `ApplicationContext` failure threshold exceeded | **YES** — same `ApplicationContext` failure as `EndpointInheritanceTest` |
| `TenantContextTest` | 0 | 6 | Mock setup errors (likely related to `TenantContext` ThreadLocal behavior in test harness) | **YES** — present in R4.6 report |
| `SqlStatementCountPerRequestTest` | 0 | 1 | Requires separate database `gms_n_plus_1` which is not provisioned | **YES** — infrastructure limitation documented in R4.5 |
| `TenantParameterArchTest` | 1 | 0 | Architecture test detects tenant parameter in method signatures — some service methods still have legacy parameters | **YES** — known architectural debt |

**Subtotal: 1 failure, 46 errors = 47 pre-existing test issues**

### 7.3 Summary

| Category | Failures | Errors | Total |
|----------|----------|--------|-------|
| New (working tree) | 10 | 12 | **22** |
| Pre-existing | 1 | 46 | **47** |
| **Grand total** | **11** | **58** | **69** |

---

## 8. Changes That Must Be Preserved Together

### 8.1 Atomic Group 1: Security Filter Chain (BUILD CRITICAL)

These files form a single atomic unit. Committing any subset without the others will break the build.

| File | Why |
|------|-----|
| `SecurityConfig.java` (modified) | References `PasswordChangeEnforcementFilter` |
| `PasswordChangeEnforcementFilter.java` (untracked) | Required by `SecurityConfig` |

**Risk if split:** `SecurityConfig` fails to compile (missing class) or `ApplicationContext` fails to load (missing bean).

### 8.2 Atomic Group 2: Credential Security (BUILD CRITICAL)

| File | Why |
|------|-----|
| `SecureCredentialGenerator.java` (untracked) | Called by 3 service implementations |
| `StudentServiceImpl.java` (modified) | Calls `SecureCredentialGenerator.generate()` |
| `ParentServiceImpl.java` (modified) | Calls `SecureCredentialGenerator.generate()` |
| `SchoolServiceImpl.java` (modified) | Calls `SecureCredentialGenerator.generate()` |
| `EmailServiceImpl.java` (modified) | Removes credential logging (companion security fix) |

**Risk if split:** Compilation failure — services reference a class that doesn't exist.

### 8.3 Atomic Group 3: Attendance Concurrency Fix (FUNCTIONALLY CRITICAL)

| File | Why |
|------|-----|
| `AttendanceRepository.java` (modified) | Defines `insertIgnoreDuplicate()` |
| `AttendanceServiceImpl.java` (modified) | Calls `insertIgnoreDuplicate()` |
| `BulkAttendanceResponse.java` (untracked) | Return type of `recordBulkAttendance()` |

**Risk if split:** Compilation failure — `AttendanceServiceImpl` references `BulkAttendanceResponse` which doesn't exist.

### 8.4 Atomic Group 4: Response Model Extensions

| File | Why |
|------|-----|
| `StudentResponse.java` (modified) | Added `temporaryPassword` field |
| `ParentResponse.java` (modified) | Added `temporaryPassword` field |
| `StudentServiceImpl.java` (modified) | Sets `temporaryPassword` on creation |
| `ParentServiceImpl.java` (modified) | Sets `temporaryPassword` on creation |
| `TeacherServiceImpl.java` (modified) | Passes `null` for `temporaryPassword` in constructor |

**Risk if split:** Compilation failure — constructor signatures mismatch.

### 8.5 Atomic Group 5: SUPERADMIN Read Authorization

All 14 controller modifications can be committed together or separately without build risk. They are functionally related (all address F-4 for read endpoints) but have no compile-time dependencies on each other.

### 8.6 Atomic Group 6: Test Infrastructure

| File | Why |
|------|-----|
| `pom.xml` (modified) | Testcontainers dependencies |
| `BasePostgresIntegrationTest.java` (untracked) | Base class for integration tests |
| `AttendancePostgresIntegrationTest.java` (untracked) | Extends base class |
| `application-test.properties` (untracked) | Test profile configuration |

**Risk if split:** Test compilation failure — `AttendancePostgresIntegrationTest` extends `BasePostgresIntegrationTest`.

---

## 9. Proposed Commit Grouping

**No commits are created here. This is a proposal only.**

### Commit A: `fix(security): SEC-CRED-01/02/03 — secure credential generation, password enforcement, log sanitization`

**Files:**
- `gms_backend/src/main/java/com/gms/util/SecureCredentialGenerator.java` (new)
- `gms_backend/src/main/java/com/gms/security/PasswordChangeEnforcementFilter.java` (new)
- `gms_backend/src/main/java/com/gms/config/SecurityConfig.java` (modified)
- `gms_backend/src/main/java/com/gms/service/impl/EmailServiceImpl.java` (modified)
- `gms_backend/src/main/java/com/gms/service/impl/StudentServiceImpl.java` (modified — credential parts only)
- `gms_backend/src/main/java/com/gms/service/impl/ParentServiceImpl.java` (modified — credential parts only)
- `gms_backend/src/main/java/com/gms/service/impl/SchoolServiceImpl.java` (modified — credential parts only)
- `gms_backend/src/main/java/com/gms/model/response/StudentResponse.java` (modified)
- `gms_backend/src/main/java/com/gms/model/response/ParentResponse.java` (modified)
- `gms_backend/src/main/java/com/gms/service/StudentService.java` (modified)
- `gms_backend/src/test/java/com/gms/util/SecureCredentialGeneratorTest.java` (new)
- `gms_backend/src/test/java/com/gms/security/PasswordChangeEnforcementFilterTest.java` (new)
- `gms_backend/src/test/java/com/gms/service/impl/StudentCredentialProvisioningTest.java` (new)
- `gms_backend/src/test/java/com/gms/service/impl/ParentCredentialProvisioningTest.java` (new)
- `gms_backend/src/test/java/com/gms/service/impl/SchoolServiceImplCredentialSecurityTest.java` (new)
- `gms_backend/src/test/java/com/gms/service/impl/EmailServiceImplLoggingSecurityTest.java` (new)

**Prerequisite:** None
**Risk:** Compilation dependencies require all files in this group

### Commit B: `fix(security): F-4 — add SUPERADMIN to read endpoints across 14 controllers`

**Files:**
- All 14 modified controllers (Announcement, Exam, Fee, FeePayment, FeeStructure, FinancialDashboard, FinancialReport, GradingScheme, ParentStudent, PaymentGateway, Result, Section, StudentExam, StudentFee)
- `gms_backend/src/test/java/com/gms/security/SuperAdminReadAuthorizationTest.java` (new)

**Prerequisite:** None (independent of Commit A)
**Risk:** Low — annotation-only changes

### Commit C: `fix(attendance): ATT-01/02/06 — atomic upsert, slot-level uniqueness, response contract`

**Files:**
- `gms_backend/src/main/java/com/gms/repository/AttendanceRepository.java` (modified)
- `gms_backend/src/main/java/com/gms/service/impl/AttendanceServiceImpl.java` (modified)
- `gms_backend/src/main/java/com/gms/model/response/BulkAttendanceResponse.java` (new)
- `gms_backend/src/test/java/com/gms/service/impl/AttendanceServiceImplSlotLevelTest.java` (new)
- `gms_backend/src/test/java/com/gms/service/impl/BulkAttendanceAtomicityDefectTest.java` (new)
- `gms_backend/src/test/java/com/gms/service/impl/BulkAttendanceResponseContractTest.java` (new)

**Prerequisite:** None (independent of A and B)
**Risk:** Medium — mock updates needed for existing tests

### Commit D: `fix(security): F-1/F-2 — tenant isolation for notifications, results, parent-student relationships`

**Files:**
- `gms_backend/src/main/java/com/gms/service/impl/NotificationServiceImpl.java` (modified)
- `gms_backend/src/main/java/com/gms/service/impl/ResultServiceImpl.java` (modified)
- `gms_backend/src/main/java/com/gms/service/impl/ParentStudentServiceImpl.java` (modified)
- `gms_backend/src/main/java/com/gms/service/impl/TeacherServiceImpl.java` (modified)
- `gms_backend/src/test/java/com/gms/service/impl/NotificationAuthorizationTest.java` (new)
- `gms_backend/src/test/java/com/gms/service/impl/NotificationCollectionAuthorizationTest.java` (new)
- `gms_backend/src/test/java/com/gms/service/impl/ResultServiceAuthorizationTest.java` (new)
- `gms_backend/src/test/java/com/gms/service/impl/ParentStudentAuthorizationTest.java` (new)

**Prerequisite:** None
**Risk:** Low — isolated service changes

### Commit E: `test(infra): PostgreSQL integration test harness for attendance concurrency`

**Files:**
- `gms_backend/pom.xml` (modified)
- `gms_backend/src/test/java/com/gms/BasePostgresIntegrationTest.java` (new)
- `gms_backend/src/test/java/com/gms/service/impl/AttendancePostgresIntegrationTest.java` (new)
- `gms_backend/src/test/resources/application-test.properties` (new)

**Prerequisite:** Commit C (tests exercise the upsert)
**Risk:** Low — test-only changes

### Commit F: `test(e2e): teacher, student, and parent browser journeys`

**Files:**
- `gms_frontend/e2e/teacher-journey.spec.js` (modified)

**Prerequisite:** None
**Risk:** Low — frontend test only

### Commit G: `docs: release reports R3.1 through R4.7 and final closure`

**Files:**
- All 19 `docs/release/*.md` files (new)

**Prerequisite:** All above commits (documents their verification)
**Risk:** None — documentation only

---

## 10. Dependency Graph

```
                    ┌─────────────────────────────────────┐
                    │         COMMIT ORDERING              │
                    └─────────────────────────────────────┘

  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
  │ Commit B │   │ Commit D │   │ Commit A │   │ Commit F │
  │ SUPERADMIN│   │ F-1/F-2  │   │ SEC-CRED │   │ E2E test │
  │ reads    │   │ tenant   │   │ 01/02/03 │   │ cleanup  │
  └────┬─────┘   └────┬─────┘   └────┬─────┘   └────┬─────┘
       │               │               │               │
       │               │               │               │
       ▼               ▼               ▼               │
  ┌──────────┐         │          ┌──────────┐         │
  │ Commit C │◄────────┘          │ Commit E │         │
  │ ATT-01/  │                    │ Postgres │         │
  │ 02/06    │───────────────────►│ test infra│        │
  └────┬─────┘                    └────┬─────┘         │
       │                               │               │
       │                               │               │
       ▼                               ▼               ▼
  ┌────────────────────────────────────────────────────────┐
  │                    Commit G                            │
  │              Documentation (all reports)               │
  └────────────────────────────────────────────────────────┘

  Legend:
  ──►  "must come after" (hard dependency)
  ──►  "can come after" (soft dependency, independent but logical)
```

### Dependency Matrix

| Commit | Depends On (Hard) | Depends On (Soft) | Blocks |
|--------|-------------------|-------------------|--------|
| A (SEC-CRED) | None | None | G |
| B (SUPERADMIN reads) | None | None | G |
| C (ATT upsert) | None | A (shares `StudentServiceImpl`) | E, G |
| D (F-1/F-2 tenant) | None | None | G |
| E (Postgres test) | C | None | G |
| F (E2E cleanup) | None | None | G |
| G (docs) | A, B, C, D, E, F | None | None |

### Merge Order Recommendation

1. **Parallel (no dependencies):** Commits A, B, D, F can be committed in any order or simultaneously
2. **After A:** Commit C (shares `StudentServiceImpl` changes with A)
3. **After C:** Commit E (tests the upsert)
4. **Last:** Commit G (documents everything)

**Alternative safe order:** A → C → E → B → D → F → G

---

## Appendix: Evidence Index

| Item | Source | Location |
|------|--------|----------|
| HEAD SHA | `git rev-parse HEAD` | `71117ed74b2d028a8ceb97477382fa0fc41c4794` |
| Branch | `git rev-parse --abbrev-ref HEAD` | `test/playwright-e2e` |
| Upstream tracking | `git branch -vv` | `origin/test/playwright-e2e`, ahead 6 |
| Merge base | `git merge-base HEAD origin/test/playwright-e2e` | `ab8eeef555c070040adab18e224365f70c5b35cc` |
| Unique constraint | `V3__natural_keys.sql:59` | `uq_attendance_student_slot_date (student_id, timetable_slot_id, date)` |
| Test results | `target/surefire-reports/*.txt` | 240 run, 11 failures, 58 errors |
| PR list | `gh pr list --state open` | #29, #30, #32 |
| Modified file count | `git status --porcelain=v2` | 30 tracked modified, 40+ untracked |

---

**Report generated:** 2026-11-03
**Status:** READ-ONLY AUDIT COMPLETE
**Next:** Stage B (awaiting authorization)
