# GMS M1 Backend Evidence Matrix (CORRECTED)

**Date:** 2026-11-05  
**Branch:** feature/flutter-m0-foundation  
**Verification Method:** Source code inspection only (no runtime tests executed)

---

## Evidence Classification Definitions

| Classification | Definition | Verification Method |
|----------------|------------|---------------------|
| **VERIFIED_IN_SOURCE** | Endpoint exists in controller with @RequestMapping, @PreAuthorize, and returns typed DTO | Source code inspection |
| **VERIFIED_BY_AUTOMATED_TEST** | Test exists and passed in CI or local execution | Test execution with evidence |
| **VERIFIED_BY_MANUAL_RUNTIME_TEST** | Endpoint called at runtime with verified response | Manual testing with logs |
| **PARTIALLY_SUPPORTED** | Endpoint exists but has documented gaps (missing validation, pagination, etc.) | Source inspection + gap analysis |
| **NOT_IMPLEMENTED** | No controller method exists for this route | Source inspection |
| **UNKNOWN** | Cannot verify due to missing access or documentation | N/A |

---

## Test Execution Status

### Flutter M0 Tests
- **Executed:** Yes (2026-11-05 12:00 UTC)
- **Command:** `flutter test --reporter=compact`
- **Result:** 32/32 tests passed
- **Test Files:**
  - `mobile/test/storage_service_test.dart` — 2 tests
  - `mobile/test/csrf_interceptor_test.dart` — 30 tests
- **Evidence:** Test output shows all tests passing with detailed logs

### Backend Integration Tests
- **Executed:** No
- **Reason:** PostgreSQL not running on localhost:5433
- **Command Attempted:** `mvn test -Dtest='*IntegrationTest'`
- **Error:** `Connection to localhost:5433 refused`
- **Status:** CANNOT VERIFY — requires database infrastructure
- **Impact:** All backend endpoints classified as VERIFIED_IN_SOURCE only, not VERIFIED_BY_AUTOMATED_TEST

---

## Controller-by-Controller Evidence

### 1. DashboardController

**File:** `gms_backend/src/main/java/com/gms/controller/DashboardController.java`  
**Endpoints:** 2

| # | Method | Path | Classification | Authorization | DTO | Evidence |
|---|--------|------|----------------|---------------|-----|----------|
| 1 | GET | /api/v1/dashboard/school | VERIFIED_IN_SOURCE | @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')") | SchoolDashboardResponse | Lines 53-60 |
| 2 | GET | /api/v1/dashboard/platform | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('SUPERADMIN')") | PlatformDashboardResponse | Lines 68-75 |

**DTO Verification:**
- SchoolDashboardResponse fields verified: schoolId (Integer), totalStudents (long), totalEmployees (long), totalClassrooms (long), totalSections (long), totalExams (long), feesCollectedAmount (BigDecimal), feesUncollectedAmount (BigDecimal), generatedAt (Instant)
- **Tenant Isolation:** Line 56-58 checks TenantContext.schoolId() != null, returns 401 if null
- **Pagination:** None (single aggregate response)
- **Validation:** None (read-only endpoint)
- **Error Response:** 401 if no schoolId in token

**Test Status:** NOT TESTED (requires PostgreSQL)

---

### 2. FinancialDashboardController

**File:** `gms_backend/src/main/java/com/gms/controller/FinancialDashboardController.java`  
**Endpoints:** 1

| # | Method | Path | Classification | Authorization | DTO | Evidence |
|---|--------|------|----------------|---------------|-----|----------|
| 1 | GET | /api/v1/financial-dashboard | PARTIALLY_SUPPORTED | @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'ACCOUNTANT')") | FinancialDashboardResponse | Source inspection |

**DTO Verification:**
- FinancialDashboardResponse fields: totalFeesExpected, totalFeesCollected, totalFeesUncollected, collectionRatePercentage, recentPayments (Object), recentInvoices (Object), monthlyCollectionTrend (Object), feesByClass, feesByStatus, topDefaulters, academicYearId, schoolId, generatedAt
- **CRITICAL GAP:** recentPayments, recentInvoices, monthlyCollectionTrend are typed as `Object` instead of explicit DTOs
- **Tenant Isolation:** Relies on service layer, no controller-level check
- **Pagination:** None
- **Validation:** None (read-only)
- **Error Response:** Not documented

**Test Status:** NOT TESTED (requires PostgreSQL)

**Security Finding:** SecurityConfig line ~128 admits PARENT to `/api/v1/financial-dashboard/**` but @PreAuthorize blocks PARENT. This is a configuration mismatch.

---

### 3. StudentController

**File:** `gms_backend/src/main/java/com/gms/controller/StudentController.java`  
**Endpoints:** 17

#### Admin Operations (7 endpoints)

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 1 | POST | /api/v1/students | VERIFIED_IN_SOURCE | @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')") | StudentRequest → StudentResponse |
| 2 | PUT | /api/v1/students/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')") | StudentRequest → StudentResponse |
| 3 | GET | /api/v1/students | PARTIALLY_SUPPORTED | @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')") | List<StudentResponse> |
| 4 | GET | /api/v1/students/search | VERIFIED_IN_SOURCE | @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')") | List<StudentResponse> |
| 5 | GET | /api/v1/students/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')") | StudentResponse |
| 6 | GET | /api/v1/students/admissions | VERIFIED_IN_SOURCE | @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')") | List<StudentResponse> |
| 7 | PATCH | /api/v1/students/{id}/toggle-status | VERIFIED_IN_SOURCE | @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')") | StudentResponse |

#### Student Self-Service (10 endpoints)

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 8 | GET | /api/v1/students/profile | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('STUDENT')") | StudentResponse |
| 9 | GET | /api/v1/students/my-classes | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('STUDENT')") | List<ClassResponse> |
| 10 | GET | /api/v1/students/my-subjects | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('STUDENT')") | List<SubjectResponse> |
| 11 | GET | /api/v1/students/my-attendance | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('STUDENT')") | List<AttendanceResponse> |
| 12 | GET | /api/v1/students/my-results | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('STUDENT')") | List<ResultResponse> |
| 13 | GET | /api/v1/students/my-fees | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('STUDENT')") | List<StudentFeeResponse> |
| 14 | GET | /api/v1/students/my-timetable | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('STUDENT')") | TimetableResponse |
| 15 | GET | /api/v1/students/my-teachers | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('STUDENT')") | List<EmployeeResponse> |
| 16 | GET | /api/v1/students/my-announcements | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('STUDENT')") | List<AnnouncementResponse> |
| 17 | GET | /api/v1/students/my-notifications | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('STUDENT')") | List<NotificationResponse> |

**DTO Verification:**
- StudentResponse fields: id (Long), schoolId (Long), classId (Long), sectionId (Long), studentId (String), firstName (String), lastName (String), email (String), phoneNumber (String), dateOfBirth (LocalDate), gender (String), status (String), temporaryPassword (String, JsonInclude.NON_NULL)
- **CRITICAL GAP:** GET /api/v1/students returns unpaginated list — no Pageable parameter
- **Validation:** StudentRequest has @NotBlank on firstName, lastName, gender; @Email on email; @NotNull on classId
- **Tenant Isolation:** Service layer filters by schoolId from TenantContext

**Test Status:** NOT TESTED (requires PostgreSQL)

---

### 4. TeacherController

**File:** `gms_backend/src/main/java/com/gms/controller/TeacherController.java`  
**Endpoints:** 3

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 1 | GET | /api/v1/teachers/profile | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | EmployeeResponse |
| 2 | GET | /api/v1/teachers/classes | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | List<ClassResponse> |
| 3 | GET | /api/v1/teachers/students | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | List<StudentResponse> |

**DTO Verification:**
- EmployeeResponse fields: id, schoolId, employeeId, firstName, lastName, fullName (computed), email, phoneNumber, designation, department, joiningDate, status, user (UserResponse)
- **CRITICAL GAP:** No TeacherDashboardResponse DTO exists — TEACHER dashboard must compose from 3 endpoints
- **Tenant Isolation:** Service layer filters by employeeId from TenantContext

**Test Status:** NOT TESTED (requires PostgreSQL)

---

### 5. AttendanceController

**File:** `gms_backend/src/main/java/com/gms/controller/AttendanceController.java`  
**Endpoints:** 8

#### Teacher Operations (4 endpoints)

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 1 | POST | /api/v1/attendance | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | AttendanceRequest → AttendanceResponse |
| 2 | POST | /api/v1/attendance/bulk | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | BulkAttendanceRequest → BulkAttendanceResponse |
| 3 | PUT | /api/v1/attendance/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | AttendanceRequest → AttendanceResponse |
| 4 | DELETE | /api/v1/attendance/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | void |

#### Read Operations (4 endpoints)

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 5 | GET | /api/v1/attendance/student/{studentId} | VERIFIED_IN_SOURCE | STUDENT self, PARENT (ownership validated) | List<AttendanceResponse> |
| 6 | GET | /api/v1/attendance/classroom/{classroomId} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | List<AttendanceResponse> |
| 7 | GET | /api/v1/attendance/student/{studentId}/range | VERIFIED_IN_SOURCE | STUDENT self, PARENT (ownership validated) | List<AttendanceResponse> |
| 8 | GET | /api/v1/attendance/classroom/{classroomId}/range | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | List<AttendanceResponse> |

**DTO Verification:**
- AttendanceResponse fields: id, student (StudentResponse), timetableSlot (TimetableSlotResponse), status (PRESENT/ABSENT/LATE/EXCUSED), date (LocalDate), remarks, markedAt, markedBy
- BulkAttendanceResponse fields: totalStudents, createdCount, skippedCount, skippedStudentIds, message
- BulkAttendanceRequest fields: timetableSlotId (@NotNull), date (@NotNull), attendance (List<AttendanceItem> @Valid)
- AttendanceItem fields: studentId (@NotNull), status (@NotBlank), remarks
- **CRITICAL GAP:** No aggregation endpoint for weekly attendance chart
- **Tenant Isolation:** Service layer validates ownership for STUDENT/PARENT access

**Test Status:** NOT TESTED (requires PostgreSQL)

---

### 6. FeePaymentController

**File:** `gms_backend/src/main/java/com/gms/controller/FeePaymentController.java`  
**Endpoints:** 9

#### Accountant Operations (5 endpoints)

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 1 | POST | /api/v1/payments | PARTIALLY_SUPPORTED | @PreAuthorize("hasAuthority('ACCOUNTANT')") | FeePaymentRequest → FeePaymentResponse |
| 2 | PUT | /api/v1/payments/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | FeePaymentRequest → FeePaymentResponse |
| 3 | DELETE | /api/v1/payments/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | void |
| 4 | GET | /api/v1/payments | PARTIALLY_SUPPORTED | @PreAuthorize("hasAuthority('ACCOUNTANT')") | List<FeePaymentResponse> |
| 5 | GET | /api/v1/payments/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | FeePaymentResponse |

#### Read Operations (4 endpoints)

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 6 | GET | /api/v1/payments/student/{studentId} | VERIFIED_IN_SOURCE | STUDENT self, PARENT (ownership validated) | List<FeePaymentResponse> |
| 7 | GET | /api/v1/payments/date-range | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | List<FeePaymentResponse> |
| 8 | GET | /api/v1/payments/total-payments | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | BigDecimal |
| 9 | GET | /api/v1/payments/payment-count | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | Long |

**DTO Verification:**
- FeePaymentResponse fields: id, studentFeeId, amount (BigDecimal), paymentDate (LocalDate), paymentMethod (CASH/CHECK/ONLINE/BANK_TRANSFER), remarks, createdAt, createdBy
- FeePaymentRequest fields: studentFeeId, amount, paymentDate, paymentMethod, remarks
- **CRITICAL GAP:** FeePaymentRequest has NO validation annotations (@NotNull, @Positive, @NotBlank)
- **CRITICAL GAP:** GET /api/v1/payments returns unpaginated list
- **CRITICAL GAP:** No time-series aggregation endpoint for collection trends chart
- **Tenant Isolation:** Service layer validates ownership for STUDENT/PARENT access

**Test Status:** NOT TESTED (requires PostgreSQL)

---

### 7. StudentFeeController

**File:** `gms_backend/src/main/java/com/gms/controller/StudentFeeController.java`  
**Endpoints:** 9

#### Accountant Operations (5 endpoints)

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 1 | POST | /api/v1/student-fees | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | StudentFeeRequest → StudentFeeResponse |
| 2 | PUT | /api/v1/student-fees/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | StudentFeeRequest → StudentFeeResponse |
| 3 | DELETE | /api/v1/student-fees/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | void |
| 4 | GET | /api/v1/student-fees | PARTIALLY_SUPPORTED | @PreAuthorize("hasAuthority('ACCOUNTANT')") | List<StudentFeeResponse> |
| 5 | GET | /api/v1/student-fees/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | StudentFeeResponse |

#### Read Operations (4 endpoints)

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 6 | GET | /api/v1/student-fees/student/{studentId} | VERIFIED_IN_SOURCE | STUDENT self, PARENT (ownership validated) | List<StudentFeeResponse> |
| 7 | GET | /api/v1/student-fees/academic-year/{academicYearId} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | List<StudentFeeResponse> |
| 8 | GET | /api/v1/student-fees/collected-total | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | BigDecimal |
| 9 | GET | /api/v1/student-fees/outstanding-total | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | BigDecimal |

**DTO Verification:**
- StudentFeeResponse fields: id, studentId, feeId, status (PENDING/PARTIAL/PAID/OVERDUE), amountPaid (BigDecimal), createdAt, updatedAt
- **CRITICAL GAP:** StudentFeeResponse lacks totalAmount, dueDate, feeName — frontend must join with FeeResponse
- **CRITICAL GAP:** GET /api/v1/student-fees returns unpaginated list
- **Tenant Isolation:** Service layer validates ownership for STUDENT/PARENT access

**Test Status:** NOT TESTED (requires PostgreSQL)

---

### 8. FeeController

**File:** `gms_backend/src/main/java/com/gms/controller/FeeController.java`  
**Endpoints:** 7

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 1 | POST | /api/v1/fees | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | FeeRequest → FeeResponse |
| 2 | PUT | /api/v1/fees/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | FeeRequest → FeeResponse |
| 3 | DELETE | /api/v1/fees/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | void |
| 4 | GET | /api/v1/fees | PARTIALLY_SUPPORTED | @PreAuthorize("hasAuthority('ACCOUNTANT')") | List<FeeResponse> |
| 5 | GET | /api/v1/fees/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | FeeResponse |
| 6 | GET | /api/v1/fees/academic-year/{academicYearId} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | List<FeeResponse> |
| 7 | GET | /api/v1/fees/total-amount | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('ACCOUNTANT')") | BigDecimal |

**DTO Verification:**
- FeeResponse fields: id, academicYearId, feeName, feeType (TUITION/TRANSPORT/LIBRARY/LAB/EXAM/OTHER), amount (BigDecimal), description, createdAt, updatedAt
- **CRITICAL GAP:** GET /api/v1/fees returns unpaginated list

**Test Status:** NOT TESTED (requires PostgreSQL)

---

### 9. ResultController

**File:** `gms_backend/src/main/java/com/gms/controller/ResultController.java`  
**Endpoints:** 5

#### Teacher Operations (3 endpoints)

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 1 | POST | /api/v1/results | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | ResultRequest → ResultResponse |
| 2 | PUT | /api/v1/results/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | ResultRequest → ResultResponse |
| 3 | DELETE | /api/v1/results/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | void |

#### Read Operations (2 endpoints)

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 4 | GET | /api/v1/results/exam-subject/{examId}/{subjectId} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | List<ResultResponse> |
| 5 | GET | /api/v1/results/student/{studentId} | VERIFIED_IN_SOURCE | STUDENT self, PARENT (ownership validated) | List<ResultResponse> |

**DTO Verification:**
- ResultResponse fields: id, studentExamId, studentName, examName, subjectName, obtainedMarks (Double), maxMarks (Double), grade, percentage (Double), remarks, status (ACTIVE/INACTIVE), createdAt, updatedAt
- **Tenant Isolation:** Service layer validates ownership for STUDENT/PARENT access

**Test Status:** NOT TESTED (requires PostgreSQL)

---

### 10. StudentExamController

**File:** `gms_backend/src/main/java/com/gms/controller/StudentExamController.java`  
**Endpoints:** 4

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 1 | POST | /api/v1/student-exams | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | StudentExamRequest → StudentExamResponse |
| 2 | DELETE | /api/v1/student-exams/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | void |
| 3 | GET | /api/v1/student-exams/exam-subject/{examId}/{subjectId} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | List<StudentExamResponse> |
| 4 | GET | /api/v1/student-exams/student/{studentId} | VERIFIED_IN_SOURCE | STUDENT self, PARENT (ownership validated) | List<StudentExamResponse> |

**DTO Verification:**
- StudentExamResponse fields: id, studentId, examId, subjectId, studentName, examName, subjectName, maxMarks (Double), createdAt
- **Tenant Isolation:** Service layer validates ownership for STUDENT/PARENT access

**Test Status:** NOT TESTED (requires PostgreSQL)

---

### 11. ParentController

**File:** `gms_backend/src/main/java/com/gms/controller/ParentController.java`  
**Endpoints:** 14

#### Admin Operations (4 endpoints)

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 1 | POST | /api/v1/parents | VERIFIED_IN_SOURCE | @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')") | ParentRequest → ParentResponse |
| 2 | PUT | /api/v1/parents/{id} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')") | ParentRequest → ParentResponse |
| 3 | GET | /api/v1/parents | PARTIALLY_SUPPORTED | @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')") | List<ParentResponse> |
| 4 | PATCH | /api/v1/parents/{id}/toggle-status | VERIFIED_IN_SOURCE | @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')") | ParentResponse |

#### Parent Self-Service (10 endpoints)

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 5 | GET | /api/v1/parents/profile | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('PARENT')") | ParentResponse |
| 6 | GET | /api/v1/parents/children | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('PARENT')") | List<StudentResponse> |
| 7 | GET | /api/v1/parents/child/{childId}/attendance | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('PARENT')") | List<AttendanceResponse> |
| 8 | GET | /api/v1/parents/child/{childId}/results | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('PARENT')") | List<ResultResponse> |
| 9 | GET | /api/v1/parents/child/{childId}/fees | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('PARENT')") | List<StudentFeeResponse> |
| 10 | GET | /api/v1/parents/child/{childId}/timetable | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('PARENT')") | TimetableResponse |
| 11 | GET | /api/v1/parents/child/{childId}/announcements | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('PARENT')") | List<AnnouncementResponse> |
| 12 | GET | /api/v1/parents/child/{childId}/subjects | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('PARENT')") | List<SubjectResponse> |
| 13 | GET | /api/v1/parents/child/{childId}/teachers | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('PARENT')") | List<EmployeeResponse> |
| 14 | GET | /api/v1/parents/notifications | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('PARENT')") | List<NotificationResponse> |

**DTO Verification:**
- ParentResponse fields: id, schoolId, firstName, lastName, email, phoneNumber, address, occupation, students (List<StudentResponse>), temporaryPassword (JsonInclude.NON_NULL), status (ACTIVE/INACTIVE), createdAt, updatedAt
- **CRITICAL GAP:** No ParentDashboardResponse DTO exists — PARENT dashboard must compose from 8+ endpoints
- **CRITICAL GAP:** GET /api/v1/parents returns unpaginated list
- **Tenant Isolation:** Service layer validates child ownership for all child-specific endpoints

**Test Status:** NOT TESTED (requires PostgreSQL)

---

### 12. TimetableController

**File:** `gms_backend/src/main/java/com/gms/controller/TimetableController.java`  
**Endpoints:** 4

| # | Method | Path | Classification | Authorization | DTO |
|---|--------|------|----------------|---------------|-----|
| 1 | POST | /api/v1/timetables | VERIFIED_IN_SOURCE | @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')") | TimetableRequest → TimetableResponse |
| 2 | GET | /api/v1/timetables/classroom/{classroomId} | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | TimetableResponse |
| 3 | GET | /api/v1/timetables/teacher/me | VERIFIED_IN_SOURCE | @PreAuthorize("hasAuthority('TEACHER')") | TimetableResponse |
| 4 | GET | /api/v1/timetables/student/me | VERIFIED_IN_SOURCE | STUDENT self, PARENT (via StudentController) | TimetableResponse |

**DTO Verification:**
- TimetableResponse fields: id, classroomId, classroomName, academicYear, slots (List<TimetableSlotResponse>)
- TimetableSlotResponse fields: id, dayOfWeek (MONDAY/TUESDAY/WEDNESDAY/THURSDAY/FRIDAY/SATURDAY), startTime (LocalTime), endTime (LocalTime), subjectName, teacherName, roomNumber
- **CRITICAL GAP:** No "current period" endpoint — cannot determine which slot is active now
- **CRITICAL GAP:** No month-level aggregation for calendar view

**Test Status:** NOT TESTED (requires PostgreSQL)

---

## Summary Statistics

**Total Endpoints Inspected:** 83  
**Total Controllers:** 12

### Evidence Classification

| Classification | Count | Percentage |
|----------------|-------|------------|
| VERIFIED_IN_SOURCE | 72 | 86.7% |
| VERIFIED_BY_AUTOMATED_TEST | 0 | 0% |
| VERIFIED_BY_MANUAL_RUNTIME_TEST | 0 | 0% |
| PARTIALLY_SUPPORTED | 11 | 13.3% |
| NOT_IMPLEMENTED | 0 | 0% |
| UNKNOWN | 0 | 0% |

### Critical Gaps Identified

1. **No role-specific dashboard DTOs:** TeacherDashboardResponse, StudentDashboardResponse, ParentDashboardResponse do not exist
2. **No pagination:** All list endpoints return unpaginated results
3. **Missing validation:** FeePaymentRequest has no validation annotations
4. **Untyped Object fields:** FinancialDashboardResponse has 3 Object-typed fields
5. **No aggregation endpoints:** Weekly attendance chart, collection trends, calendar view not supported
6. **No "current period" endpoint:** Cannot determine active timetable slot
7. **SecurityConfig mismatch:** PARENT role admitted at URL level but blocked at method level for financial-dashboard

### Test Execution Status

| Test Suite | Executed | Passed | Failed | Evidence |
|------------|----------|--------|--------|----------|
| Flutter M0 Unit Tests | Yes | 32 | 0 | `flutter test` output |
| Backend Integration Tests | No | N/A | N/A | PostgreSQL not running |
| M1 Mobile Tests | No | N/A | N/A | Not implemented yet |

---

## Conclusion

**Verification Level:** SOURCE CODE INSPECTION ONLY

All 83 endpoints have been verified in source code. However, **zero endpoints have been verified by automated test execution** due to PostgreSQL infrastructure requirements.

**Recommendation:** Backend integration tests must be executed in a proper test environment before M1 implementation can proceed with confidence.

**Document Version:** 2.0 (CORRECTED)  
**Last Updated:** 2026-11-05  
**Author:** Implementation Agent  
**Reviewer:** Technical Lead  
**Status:** Evidence-based, awaiting runtime verification
