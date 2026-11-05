# GMS M1 Final Blocker Register

**Date:** 2026-11-05  
**Branch:** feature/flutter-m0-foundation  
**Status:** NOT READY — BLOCKERS REMAIN

---

## Executive Summary

This document identifies all blockers preventing M1 implementation approval. Each blocker is classified by severity, impact, evidence, owner, and required resolution.

**Total Blockers:** 18  
**P0 (Critical):** 5  
**P1 (High):** 8  
**P2 (Medium):** 5  

**Recommendation:** NOT READY — BLOCKERS REMAIN

---

## P0 Critical Blockers

### BLOCKER-001: Backend Integration Tests Not Executed

**Severity:** P0 — Critical  
**Impact:** Cannot verify backend endpoints work at runtime  
**Evidence:** 
- Command: `mvn test -Dtest='*IntegrationTest'`
- Error: `Connection to localhost:5433 refused`
- PostgreSQL not running in test environment

**Affected Endpoints:** All 83 backend endpoints  
**Current Classification:** VERIFIED_IN_SOURCE only (not VERIFIED_BY_AUTOMATED_TEST)

**Owner:** Backend Team / DevOps  
**Required Resolution:**
1. Provision PostgreSQL test database (localhost:5433)
2. Execute all backend integration tests
3. Document test results with pass/fail counts
4. Reclassify endpoints as VERIFIED_BY_AUTOMATED_TEST where tests pass

**Blocking M1 Because:** Source code inspection does not prove runtime behavior. Endpoints may fail due to:
- Database connection issues
- SQL syntax errors
- Transaction management defects
- Service layer bugs
- DTO serialization failures

**Estimated Effort:** 1 day

---

### BLOCKER-002: No Role-Specific Dashboard DTOs

**Severity:** P0 — Critical  
**Impact:** TEACHER, STUDENT, PARENT dashboards cannot be implemented as designed  
**Evidence:**
- Searched: `gms_backend/src/main/java/com/gms/model/response/`
- Missing: TeacherDashboardResponse.java, StudentDashboardResponse.java, ParentDashboardResponse.java
- Existing: SchoolDashboardResponse.java (ADMIN only), FinancialDashboardResponse.java (ACCOUNTANT only)

**Affected Screens:**
- TEACHER Dashboard (must compose from 3 endpoints: /teachers/profile, /teachers/classes, /teachers/students)
- STUDENT Dashboard (must compose from 8 endpoints: /students/profile, /students/my-*)
- PARENT Dashboard (must compose from 8+ endpoints: /parents/profile, /parents/children, /parents/child/*)

**Owner:** Backend Team  
**Required Resolution:**
Option A (Recommended): Create dedicated dashboard DTOs
- TeacherDashboardResponse with profile, classes, students fields
- StudentDashboardResponse with profile, classes, attendance, results, fees, timetable, announcements, notifications fields
- ParentDashboardResponse with profile, children, childSummaries fields

Option B: Document composition pattern
- Create frontend aggregators that call multiple endpoints
- Document loading order and error handling
- Accept N+1 query pattern for M1

**Blocking M1 Because:** Dashboard designs assume single-endpoint data sources. Without dedicated DTOs:
- Frontend must make 3-8 API calls per dashboard load
- Loading states become complex (partial data)
- Error handling ambiguous (which endpoint failed?)
- Performance degraded (multiple round trips)

**Estimated Effort:** 2-3 days (Option A), 1 day (Option B)

---

### BLOCKER-003: Missing Design Assets

**Severity:** P0 — Critical  
**Impact:** Cannot implement screens without verified designs  
**Evidence:**
- Stitch export audit (2026-11-05):
  - File 03_teacher_dashboard.png: Shows STUDENT dashboard, not TEACHER
  - File 04_student_dashboard.png: Shows PARENT dashboard, not STUDENT
  - File 05_parent_dashboard.png: Shows ACCOUNTANT fee collection, not PARENT
  - File 08_accountant_fees.png: Duplicate of file 05
  - File 10_parent_attendance.png: 404 error, file not found

**Affected Screens:**
- TEACHER Dashboard (mislabeled)
- STUDENT Dashboard (mislabeled)
- PARENT Dashboard (mislabeled)
- ACCOUNTANT Fee Structure (duplicate)
- PARENT View Child Attendance (missing)

**Owner:** Design Reviewer  
**Required Resolution:**
1. Re-export correct screenshots from Stitch for TEACHER, STUDENT, PARENT dashboards
2. Create ACCOUNTANT Fee Structure screen in Stitch
3. Create PARENT View Child Attendance screen in Stitch
4. Verify all 11 M1 screens have correct, non-duplicate screenshots
5. Export state designs (loading, empty, error, success, confirmation) for all 11 screens

**Blocking M1 Because:** Implementation requires visual specifications. Mislabeled or missing designs lead to:
- Incorrect UI implementation
- Rework and delays
- Inconsistent user experience
- Design-review failures

**Estimated Effort:** 2-3 days

---

### BLOCKER-004: State Designs Not Created

**Severity:** P0 — Critical  
**Impact:** Cannot implement loading, empty, error, success, confirmation states  
**Evidence:**
- Design remediation checklist audit: 0 of 44-55 state designs created
- Required states per screen:
  - Loading (skeleton or spinner)
  - Empty (no data)
  - Error (API failure)
  - Success (data loaded)
  - Confirmation (consequential actions: delete, submit, payment)

**Affected Screens:** All 11 M1 screens  
**Owner:** Design Reviewer  
**Required Resolution:**
1. Create loading state designs for all 11 screens
2. Create empty state designs for all 11 screens
3. Create error state designs for all 11 screens
4. Create success state designs for all 11 screens
5. Create confirmation dialog designs for 3 screens (TEACHER Mark Attendance, TEACHER Enter Results, ACCOUNTANT Record Payment)

**Blocking M1 Because:** Without state designs:
- Frontend developers guess at loading indicators
- Empty states inconsistent across screens
- Error messages not standardized
- Success feedback unclear
- Confirmation dialogs missing for destructive actions

**Estimated Effort:** 5-7 days

---

### BLOCKER-005: PARENT Role Blocked from Financial Dashboard

**Severity:** P0 — Critical  
**Impact:** PARENT cannot view fee overview as designed  
**Evidence:**
- SecurityConfig.java line ~128: `.requestMatchers("/api/v1/financial-dashboard/**").hasAnyRole("ADMIN", "SUPERADMIN", "ACCOUNTANT", "PARENT")`
- FinancialDashboardController.java: `@PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'ACCOUNTANT')")`
- Mismatch: URL-level admits PARENT, method-level blocks PARENT
- Result: PARENT gets 403 Forbidden

**Affected Screens:** PARENT Dashboard (fee overview widget)  
**Owner:** Backend Team / Security  
**Required Resolution:**
Option A (Recommended): Remove PARENT from SecurityConfig URL matcher
- PARENT uses /api/v1/parents/child/{childId}/fees instead
- Document alternative endpoint in navigation matrix

Option B: Add PARENT to @PreAuthorize
- Change to: `@PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'ACCOUNTANT', 'PARENT')")`
- Add tenant isolation to ensure PARENT sees only own children's fees
- Test cross-tenant access attempts

**Blocking M1 Because:** PARENT dashboard design includes fee overview widget. Without access:
- Widget shows error or empty state
- User experience degraded
- Design-reality mismatch

**Estimated Effort:** 0.5 days

---

## P1 High Blockers

### BLOCKER-006: No Pagination on List Endpoints

**Severity:** P1 — High  
**Impact:** Performance degradation with large datasets  
**Evidence:**
- Inspected: All GET list endpoints
- Finding: No Pageable parameter, no Page<T> return type
- Affected endpoints:
  - GET /api/v1/students (ADMIN)
  - GET /api/v1/payments (ACCOUNTANT)
  - GET /api/v1/student-fees (ACCOUNTANT)
  - GET /api/v1/fees (ACCOUNTANT)
  - GET /api/v1/parents (ADMIN)

**Affected Screens:** All list screens  
**Owner:** Backend Team  
**Required Resolution:**
1. Add Pageable parameter to all list endpoints
2. Return Page<T> instead of List<T>
3. Add pagination metadata (totalElements, totalPages, currentPage)
4. Update frontend to support infinite scroll or page navigation

**Blocking M1 Because:** Schools may have 1000+ students. Without pagination:
- API returns all records in single response
- Mobile app loads slowly
- Memory usage spikes
- Network bandwidth wasted

**Estimated Effort:** 3-5 days

---

### BLOCKER-007: Missing Validation on FeePaymentRequest

**Severity:** P1 — High  
**Impact:** Invalid payments can reach service layer  
**Evidence:**
- File: FeePaymentRequest.java
- Finding: No validation annotations (@NotNull, @Positive, @NotBlank)
- Fields: studentFeeId, amount, paymentDate, paymentMethod, remarks
- Backend relies on service-layer null checks

**Affected Screens:** ACCOUNTANT Record Payment  
**Owner:** Backend Team  
**Required Resolution:**
1. Add @NotNull to studentFeeId, amount, paymentDate
2. Add @Positive to amount
3. Add @NotBlank to paymentMethod
4. Add @Valid to controller method parameter
5. Test validation errors return 400 Bad Request

**Blocking M1 Because:** Without validation:
- Invalid payments created (negative amounts, null dates)
- Service layer throws exceptions
- User sees 500 errors instead of 400 validation errors
- Data quality degraded

**Estimated Effort:** 0.5 days

---

### BLOCKER-008: Untyped Object Fields in FinancialDashboardResponse

**Severity:** P1 — High  
**Impact:** Frontend cannot infer structure  
**Evidence:**
- File: FinancialDashboardResponse.java
- Fields typed as Object:
  - recentPayments (should be List<PaymentDTO>)
  - recentInvoices (should be List<InvoiceDTO>)
  - monthlyCollectionTrend (should be List<TrendDTO>)

**Affected Screens:** ACCOUNTANT Dashboard  
**Owner:** Backend Team  
**Required Resolution:**
1. Create PaymentDTO, InvoiceDTO, TrendDTO classes
2. Replace Object with explicit List<DTO> types
3. Update FinancialDashboardService to populate typed DTOs
4. Test JSON serialization produces expected structure

**Blocking M1 Because:** Without typed DTOs:
- Frontend must use dynamic typing (Map<String, dynamic>)
- Type errors at runtime
- IDE cannot provide autocomplete
- Refactoring difficult

**Estimated Effort:** 1 day

---

### BLOCKER-009: No Aggregation Endpoints for Charts

**Severity:** P1 — High  
**Impact:** Dashboard widgets removed from M1 scope  
**Evidence:**
- Removed widgets:
  - Weekly Attendance Chart (TEACHER Dashboard)
  - Collection Trends Chart (ACCOUNTANT Dashboard)
  - Calendar View (all dashboards)
- Reason: No aggregation endpoints exist
- Existing endpoints return raw records only

**Affected Screens:** TEACHER Dashboard, ACCOUNTANT Dashboard  
**Owner:** Backend Team  
**Required Resolution:**
Option A: Create aggregation endpoints
- GET /api/v1/attendance/weekly-summary?classroomId={id}&weekStart={date}
- GET /api/v1/payments/monthly-trend?startDate={date}&endDate={date}
- GET /api/v1/attendance/calendar?month={date}

Option B: Accept widget removal
- Redesign dashboards without charts
- Document deferred to M2

**Blocking M1 Because:** Original designs included charts. Without aggregation:
- Designs must be revised
- User expectations not met
- M2 scope increases

**Estimated Effort:** 3-5 days (Option A), 0 days (Option B)

---

### BLOCKER-010: No "Current Period" Endpoint

**Severity:** P1 — High  
**Impact:** TEACHER Dashboard cannot show current class  
**Evidence:**
- Existing: GET /api/v1/timetables/teacher/me (returns all slots)
- Missing: Endpoint to determine which slot is active now
- TEACHER Dashboard design includes "Current Period" widget

**Affected Screens:** TEACHER Dashboard  
**Owner:** Backend Team  
**Required Resolution:**
Option A: Create current period endpoint
- GET /api/v1/timetables/teacher/current
- Returns active slot based on current time and day of week

Option B: Compute on frontend
- Fetch all slots
- Filter by current day and time
- Accept timezone complexity

**Blocking M1 Because:** "Current Period" widget requires knowing active slot. Without endpoint:
- Frontend must implement time-based logic
- Timezone handling complex
- Widget may show incorrect data

**Estimated Effort:** 1 day (Option A), 0.5 days (Option B)

---

### BLOCKER-011: StudentFeeResponse Lacks Required Fields

**Severity:** P1 — High  
**Impact:** STUDENT/PARENT fee views incomplete  
**Evidence:**
- File: StudentFeeResponse.java
- Existing fields: id, studentId, feeId, status, amountPaid, createdAt, updatedAt
- Missing fields: totalAmount, dueDate, feeName
- Frontend must join with FeeResponse to display complete info

**Affected Screens:** STUDENT Dashboard (fees widget), PARENT Dashboard (child fees)  
**Owner:** Backend Team  
**Required Resolution:**
1. Add totalAmount, dueDate, feeName to StudentFeeResponse
2. Update StudentFeeService to populate new fields
3. Test response includes all required data

**Blocking M1 Because:** Without complete DTO:
- Frontend makes N+1 queries (fetch fees, then fetch each fee structure)
- Performance degraded
- Code complexity increased

**Estimated Effort:** 0.5 days

---

### BLOCKER-012: FinancialDashboard Lacks Controller-Level Tenant Guard

**Severity:** P1 — High  
**Impact:** Tenant isolation relies on service layer only  
**Evidence:**
- File: FinancialDashboardController.java
- Finding: No TenantContext.schoolId() check in controller
- Authorization: @PreAuthorize only checks role
- Tenant isolation: Happens in FinancialDashboardService

**Affected Screens:** ACCOUNTANT Dashboard  
**Owner:** Backend Team / Security  
**Required Resolution:**
1. Add TenantContext.schoolId() null check in controller
2. Return 401 if schoolId is null
3. Document that service layer filters by schoolId

**Blocking M1 Because:** Defense-in-depth requires tenant checks at multiple layers. Without controller check:
- Service layer bugs could expose cross-tenant data
- Security audit finding
- Compliance risk

**Estimated Effort:** 0.5 days

---

### BLOCKER-013: Accessibility Not Verified

**Severity:** P1 — High  
**Impact:** WCAG AA compliance cannot be claimed  
**Evidence:**
- Status badge contrast ratios: Not measured
- Touch target sizes: Not measured
- Screen reader compatibility: Not tested
- Keyboard navigation: Not tested

**Affected Screens:** All 11 M1 screens  
**Owner:** QA / Design Reviewer  
**Required Resolution:**
1. Measure contrast ratios for all status badges
2. Verify touch targets ≥ 44x44px
3. Test with VoiceOver (iOS) and TalkBack (Android)
4. Test keyboard navigation with external keyboard
5. Document accessibility conformance report

**Blocking M1 Because:** Accessibility is a legal requirement in many jurisdictions. Without verification:
- Legal risk
- Excludes users with disabilities
- Poor user experience

**Estimated Effort:** 2-3 days

---

## P2 Medium Blockers

### BLOCKER-014: Tablet and Landscape Layouts Not Designed

**Severity:** P2 — Medium  
**Impact:** Responsive design incomplete  
**Evidence:**
- Existing designs: Mobile portrait only
- Missing: Tablet portrait, tablet landscape, mobile landscape layouts
- Breakpoints: Not defined

**Affected Screens:** All 11 M1 screens  
**Owner:** Design Reviewer  
**Required Resolution:**
1. Define responsive breakpoints (mobile, tablet, desktop)
2. Create tablet portrait layouts
3. Create tablet landscape layouts
4. Document adaptive behavior

**Blocking M1 Because:** Users will run app on tablets. Without designs:
- Inconsistent experience
- Wasted screen space
- Poor usability

**Estimated Effort:** 3-5 days

---

### BLOCKER-015: M1 Tests Not Implemented

**Severity:** P2 — Medium  
**Impact:** No automated test coverage for M1 features  
**Evidence:**
- M0 tests: 32 passing (storage, CSRF)
- M1 tests planned: 654
- M1 tests implemented: 0
- M1 tests executed: 0

**Affected Screens:** All 11 M1 screens  
**Owner:** Implementation Agent / QA  
**Required Resolution:**
1. Implement unit tests for M1 models (45 tests)
2. Implement unit tests for M1 repositories (64 tests)
3. Implement widget tests for M1 screens (113 tests)
4. Implement integration tests for M1 workflows (20 tests)
5. Implement E2E tests for M1 user journeys (11 tests)

**Blocking M1 Because:** Without tests:
- Regressions undetected
- Refactoring risky
- Quality unverified

**Estimated Effort:** 10-15 days

---

### BLOCKER-016: Error Response Behavior Not Documented

**Severity:** P2 — Medium  
**Impact:** Frontend cannot handle errors consistently  
**Evidence:**
- Inspected: All 12 controllers
- Finding: No standardized error response format
- Existing: ResponseEntity.status().build() (no body)
- Missing: Error code, message, details fields

**Affected Screens:** All 11 M1 screens  
**Owner:** Backend Team  
**Required Resolution:**
1. Define ErrorResponse DTO (code, message, details, timestamp)
2. Update all controllers to return ErrorResponse on failure
3. Document error codes per endpoint
4. Test error scenarios

**Blocking M1 Because:** Without standardized errors:
- Frontend parses different error formats
- Error messages inconsistent
- Debugging difficult

**Estimated Effort:** 2-3 days

---

### BLOCKER-017: Empty Response Behavior Not Documented

**Severity:** P2 — Medium  
**Impact:** Frontend cannot distinguish empty from error  
**Evidence:**
- Inspected: All list endpoints
- Finding: Return empty list [] when no data
- Missing: Explicit empty state indicator
- Ambiguity: Is [] an error or just no data?

**Affected Screens:** All list screens  
**Owner:** Backend Team  
**Required Resolution:**
1. Document that empty list [] means no data (not an error)
2. Consider adding metadata: { data: [], total: 0, empty: true }
3. Test empty scenarios

**Blocking M1 Because:** Without clear empty behavior:
- Frontend shows error for empty lists
- User confused ("Is something broken?")
- Support tickets increase

**Estimated Effort:** 0.5 days

---

### BLOCKER-018: Loading Response Behavior Not Documented

**Severity:** P2 — Medium  
**Impact:** Frontend cannot show loading states  
**Evidence:**
- HTTP is synchronous (request → response)
- No streaming or progress indicators
- Frontend must infer loading from request state

**Affected Screens:** All 11 M1 screens  
**Owner:** Implementation Agent  
**Required Resolution:**
1. Document that frontend shows loading indicator while request in flight
2. Define loading UI patterns (skeleton, spinner, progress bar)
3. Implement loading states in all screens

**Blocking M1 Because:** Without loading states:
- User thinks app frozen
- Poor user experience
- Perceived performance low

**Estimated Effort:** 1-2 days

---

## Blocker Resolution Priority

### Immediate (Before M1 Implementation)

1. **BLOCKER-001:** Execute backend integration tests (1 day)
2. **BLOCKER-003:** Re-export missing design assets (2-3 days)
3. **BLOCKER-005:** Fix PARENT role mismatch (0.5 days)

**Total:** 3.5-4.5 days

### Before M1 Development

4. **BLOCKER-002:** Create role-specific dashboard DTOs OR document composition pattern (1-3 days)
5. **BLOCKER-004:** Create state designs (5-7 days)
6. **BLOCKER-013:** Verify accessibility (2-3 days)

**Total:** 8-13 days

### During M1 Development

7. **BLOCKER-006:** Add pagination (3-5 days)
8. **BLOCKER-007:** Add validation (0.5 days)
9. **BLOCKER-008:** Type Object fields (1 day)
10. **BLOCKER-011:** Add StudentFeeResponse fields (0.5 days)
11. **BLOCKER-012:** Add tenant guard (0.5 days)
12. **BLOCKER-015:** Implement M1 tests (10-15 days)
13. **BLOCKER-016:** Standardize error responses (2-3 days)
14. **BLOCKER-017:** Document empty behavior (0.5 days)
15. **BLOCKER-018:** Implement loading states (1-2 days)

**Total:** 18-27 days

### Deferred to M2

16. **BLOCKER-009:** Create aggregation endpoints (3-5 days) OR accept widget removal
17. **BLOCKER-010:** Create current period endpoint (1 day) OR compute on frontend
18. **BLOCKER-014:** Design tablet layouts (3-5 days)

**Total:** 7-11 days (if not deferred)

---

## Conclusion

**Total Blockers:** 18  
**P0 Critical:** 5 (must resolve before M1 implementation)  
**P1 High:** 8 (should resolve before M1 development)  
**P2 Medium:** 5 (can resolve during M1 development)

**Minimum Resolution Time:** 3.5-4.5 days (P0 only)  
**Recommended Resolution Time:** 11.5-17.5 days (P0 + P1)  
**Full Resolution Time:** 29.5-44.5 days (all blockers)

**Final Determination:** 

# NOT READY — BLOCKERS REMAIN

**Reason:** 5 P0 critical blockers prevent M1 implementation. Product owner must approve blocker resolution plan before proceeding.

**Next Steps:**
1. Product owner reviews blocker register
2. Product owner prioritizes blockers (resolve now vs defer to M2)
3. Backend team resolves P0 blockers (3.5-4.5 days)
4. Design team resolves P0 blockers (2-3 days, parallel)
5. Product owner re-reviews M1 readiness
6. If all P0 blockers resolved, approve M1 implementation

---

**Document Version:** 1.0  
**Last Updated:** 2026-11-05  
**Author:** Implementation Agent  
**Reviewer:** Technical Lead  
**Approver:** Product Owner (pending)
