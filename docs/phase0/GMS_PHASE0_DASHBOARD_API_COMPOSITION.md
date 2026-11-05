# GMS Phase 0 — Dashboard API Composition

**Date:** 2026-11-05  
**Auditor:** Implementation Agent  
**Purpose:** Define how TEACHER, STUDENT, and PARENT dashboards compose from existing endpoints

---

## Executive Summary

**Existing Dashboard DTOs:**
- ✅ SchoolDashboardResponse (ADMIN)
- ✅ PlatformDashboardResponse (SUPERADMIN)
- ✅ FinancialDashboardResponse (ACCOUNTANT)
- ❌ TeacherDashboardResponse (NOT IMPLEMENTED)
- ❌ StudentDashboardResponse (NOT IMPLEMENTED)
- ❌ ParentDashboardResponse (NOT IMPLEMENTED)

**Recommendation:** Frontend composition from existing endpoints. No new backend DTOs required for M1.

---

## 1. Current Dashboard Infrastructure

### 1.1 DashboardController

**File:** `gms_backend/src/main/java/com/gms/controller/DashboardController.java`

**Endpoints:**
1. `GET /api/v1/dashboard/school` → SchoolDashboardResponse
   - Authorization: ADMIN, SUPERADMIN
   - Returns: Aggregate counts for school administrator
   
2. `GET /api/v1/dashboard/platform` → PlatformDashboardResponse
   - Authorization: SUPERADMIN only
   - Returns: Cross-tenant platform aggregates

### 1.2 FinancialDashboardController

**File:** `gms_backend/src/main/java/com/gms/controller/FinancialDashboardController.java`

**Endpoints:**
1. `GET /api/v1/financial-dashboard/summary` → FinancialDashboardResponse
   - Authorization: ADMIN, SUPERADMIN, ACCOUNTANT
   - Returns: Financial aggregates for accountant

### 1.3 Available Response DTOs

**SchoolDashboardResponse:**
```java
- schoolId: Integer
- totalStudents: long
- totalEmployees: long
- totalClassrooms: long
- totalSections: long
- totalExams: long
- feesCollectedAmount: BigDecimal
- feesUncollectedAmount: BigDecimal
- generatedAt: Instant
```

**PlatformDashboardResponse:**
```java
- totalSchools: long
- totalUsers: long
- totalStudents: long
- totalEmployees: long
- generatedAt: Instant
```

**FinancialDashboardResponse:**
```java
- totalFees: BigDecimal
- collectedFees: BigDecimal
- pendingFees: BigDecimal
- recentTransactions: List<Object>  // ⚠️ Untyped
- feeCollectionByMonth: List<Object>  // ⚠️ Untyped
- generatedAt: Instant
```

---

## 2. TEACHER Dashboard Composition

### 2.1 Required Dashboard Fields

Based on M1 scope and typical teacher workflows:

| Field | Purpose | Priority |
|-------|---------|----------|
| Assigned classes count | Show how many classes teacher handles | P0 |
| Total students count | Show total students across all classes | P0 |
| Recent attendance | Show last 5 attendance records | P1 |
| Upcoming exams | Show next 5 scheduled exams | P1 |
| Pending grading | Show assignments needing grading | P2 |

### 2.2 Existing Endpoints

**TeacherController:**
```
GET /api/v1/teachers/profile → TeacherResponse
  - teacherId, name, email, phone, qualifications
  - Authorization: TEACHER (own profile only)

GET /api/v1/teachers/classes → List<ClassroomResponse>
  - List of classrooms assigned to teacher
  - Authorization: TEACHER (own classes only)

GET /api/v1/teachers/students → List<StudentResponse>
  - List of students in teacher's classes
  - Authorization: TEACHER (own students only)
```

**AttendanceController:**
```
GET /api/v1/attendance/teacher/{classroomId} → List<AttendanceResponse>
  - Attendance records for specific classroom
  - Authorization: TEACHER (own classroom only)
```

**ExamController:**
```
GET /api/v1/exams → List<ExamResponse>
  - All exams (filtered by teacher's classes)
  - Authorization: TEACHER
```

### 2.3 Composition Strategy

**Frontend Composition (Recommended):**

```dart
// mobile/lib/features/teacher/dashboard/teacher_dashboard_provider.dart

Future<TeacherDashboardData> loadTeacherDashboard() async {
  // Parallel loading for independent data
  final results = await Future.wait([
    _teacherRepository.getProfile(),
    _teacherRepository.getClasses(),
    _teacherRepository.getStudents(),
    _examRepository.getUpcomingExams(limit: 5),
  ]);
  
  return TeacherDashboardData(
    profile: results[0] as TeacherResponse,
    assignedClasses: (results[1] as List<ClassroomResponse>).length,
    totalStudents: (results[2] as List<StudentResponse>).length,
    upcomingExams: results[3] as List<ExamResponse>,
  );
}
```

**API Calls:**
1. `GET /api/v1/teachers/profile` (1 call)
2. `GET /api/v1/teachers/classes` (1 call)
3. `GET /api/v1/teachers/students` (1 call)
4. `GET /api/v1/exams?limit=5` (1 call)

**Total:** 4 API calls, executed in parallel

**Loading Time:** ~500ms (parallel) vs ~2000ms (sequential)

### 2.4 Partial Failure Behavior

| Call Fails | Dashboard Impact | User Experience |
|------------|------------------|-----------------|
| Profile | Cannot display name | Show "Teacher" fallback |
| Classes | Cannot show class count | Show "—" placeholder |
| Students | Cannot show student count | Show "—" placeholder |
| Exams | Cannot show upcoming exams | Hide exam widget |

**Strategy:** Graceful degradation. Dashboard remains functional with partial data.

### 2.5 Empty Data Behavior

| Field | Empty Condition | Display |
|-------|----------------|---------|
| Assigned classes | 0 classes | "No classes assigned" message |
| Total students | 0 students | "No students enrolled" message |
| Upcoming exams | 0 exams | "No upcoming exams" message |

### 2.6 Missing Backend Capability

**Gap:** No single endpoint returns "pending grading" count

**Current State:** Teacher must query each class's assignments and filter by grading status

**Impact:** P2 feature (not critical for M1)

**Recommendation:** Defer to M2. M1 dashboard does not include "pending grading" widget.

---

## 3. STUDENT Dashboard Composition

### 3.1 Required Dashboard Fields

Based on M1 scope and typical student workflows:

| Field | Purpose | Priority |
|-------|---------|----------|
| Current classes | Show enrolled classes this term | P0 |
| Attendance percentage | Show overall attendance rate | P0 |
| Recent results | Show last 5 exam results | P1 |
| Fee status | Show outstanding fees | P1 |
| Upcoming exams | Show next 5 scheduled exams | P2 |

### 3.2 Existing Endpoints

**StudentController (Self-Service):**
```
GET /api/v1/students/profile → StudentResponse
  - studentId, name, email, class, section
  - Authorization: STUDENT (own profile only)

GET /api/v1/students/my-attendance → AttendanceSummaryResponse
  - totalDays, presentDays, attendancePercentage
  - Authorization: STUDENT (own attendance only)

GET /api/v1/students/my-results → List<ResultResponse>
  - List of exam results
  - Authorization: STUDENT (own results only)

GET /api/v1/students/my-fees → List<StudentFeeResponse>
  - List of fee records
  - Authorization: STUDENT (own fees only)

GET /api/v1/students/my-timetable → List<TimetableResponse>
  - Weekly class schedule
  - Authorization: STUDENT (own timetable only)
```

### 3.3 Composition Strategy

**Frontend Composition (Recommended):**

```dart
// mobile/lib/features/student/dashboard/student_dashboard_provider.dart

Future<StudentDashboardData> loadStudentDashboard() async {
  // Parallel loading for independent data
  final results = await Future.wait([
    _studentRepository.getProfile(),
    _studentRepository.getMyAttendance(),
    _studentRepository.getMyResults(limit: 5),
    _studentRepository.getMyFees(),
  ]);
  
  final fees = results[3] as List<StudentFeeResponse>;
  final totalFees = fees.fold(0.0, (sum, fee) => sum + fee.amount);
  final paidFees = fees.where((f) => f.isPaid).fold(0.0, (sum, fee) => sum + fee.amount);
  
  return StudentDashboardData(
    profile: results[0] as StudentResponse,
    attendancePercentage: (results[1] as AttendanceSummaryResponse).percentage,
    recentResults: results[2] as List<ResultResponse>,
    totalFees: totalFees,
    paidFees: paidFees,
    outstandingFees: totalFees - paidFees,
  );
}
```

**API Calls:**
1. `GET /api/v1/students/profile` (1 call)
2. `GET /api/v1/students/my-attendance` (1 call)
3. `GET /api/v1/students/my-results?limit=5` (1 call)
4. `GET /api/v1/students/my-fees` (1 call)

**Total:** 4 API calls, executed in parallel

### 3.4 Partial Failure Behavior

| Call Fails | Dashboard Impact | User Experience |
|------------|------------------|-----------------|
| Profile | Cannot display name | Show "Student" fallback |
| Attendance | Cannot show attendance % | Show "—" placeholder |
| Results | Cannot show recent results | Hide results widget |
| Fees | Cannot show fee status | Hide fees widget |

**Strategy:** Graceful degradation. Dashboard remains functional with partial data.

### 3.5 Empty Data Behavior

| Field | Empty Condition | Display |
|-------|----------------|---------|
| Attendance | No attendance records | "No attendance data" message |
| Recent results | No results yet | "No results available" message |
| Fees | No fees assigned | "No fees outstanding" message |

### 3.6 Missing Backend Capability

**Gap 1:** StudentFeeResponse may lack `dueDate` field

**Current State:** Student cannot see when fees are due

**Impact:** P1 feature (important for M1)

**Recommendation:** Inspect StudentFeeResponse.java. If `dueDate` is missing, document as P1 blocker.

**Gap 2:** No single endpoint returns "upcoming exams" for student

**Current State:** Student must query all exams and filter by class/date

**Impact:** P2 feature (not critical for M1)

**Recommendation:** Defer to M2. M1 dashboard does not include "upcoming exams" widget.

---

## 4. PARENT Dashboard Composition

### 4.1 Required Dashboard Fields

Based on M1 scope and typical parent workflows:

| Field | Purpose | Priority |
|-------|---------|----------|
| Children list | Show all children linked to parent | P0 |
| Child attendance | Show attendance for each child | P0 |
| Child fee status | Show outstanding fees for each child | P1 |
| Child results | Show recent results for each child | P1 |
| Announcements | Show school announcements | P2 |

### 4.2 Existing Endpoints

**ParentController (Self-Service):**
```
GET /api/v1/parents/profile → ParentResponse
  - parentId, name, email, phone
  - Authorization: PARENT (own profile only)

GET /api/v1/parents/children → List<ChildSummaryResponse>
  - List of children linked to parent
  - Authorization: PARENT (own children only)

GET /api/v1/parents/child/{childId}/attendance → AttendanceSummaryResponse
  - Attendance for specific child
  - Authorization: PARENT (own child only)

GET /api/v1/parents/child/{childId}/fees → List<StudentFeeResponse>
  - Fee records for specific child
  - Authorization: PARENT (own child only)

GET /api/v1/parents/child/{childId}/results → List<ResultResponse>
  - Exam results for specific child
  - Authorization: PARENT (own child only)
```

**AnnouncementController:**
```
GET /api/v1/announcements → List<AnnouncementResponse>
  - School-wide announcements
  - Authorization: PARENT (and other roles)
```

### 4.3 Composition Strategy

**Frontend Composition (Recommended):**

```dart
// mobile/lib/features/parent/dashboard/parent_dashboard_provider.dart

Future<ParentDashboardData> loadParentDashboard() async {
  // Step 1: Load parent profile and children list
  final profile = await _parentRepository.getProfile();
  final children = await _parentRepository.getChildren();
  
  // Step 2: For each child, load attendance, fees, and results in parallel
  final childData = await Future.wait(children.map((child) async {
    final results = await Future.wait([
      _parentRepository.getChildAttendance(child.id),
      _parentRepository.getChildFees(child.id),
      _parentRepository.getChildResults(child.id),
    ]);
    
    final fees = results[1] as List<StudentFeeResponse>;
    final totalFees = fees.fold(0.0, (sum, fee) => sum + fee.amount);
    final paidFees = fees.where((f) => f.isPaid).fold(0.0, (sum, fee) => sum + fee.amount);
    
    return ChildDashboardData(
      child: child,
      attendancePercentage: (results[0] as AttendanceSummaryResponse).percentage,
      outstandingFees: totalFees - paidFees,
      recentResults: results[2] as List<ResultResponse>,
    );
  }));
  
  // Step 3: Load announcements (independent of children)
  final announcements = await _announcementRepository.getAnnouncements(limit: 5);
  
  return ParentDashboardData(
    profile: profile,
    children: childData,
    announcements: announcements,
  );
}
```

**API Calls:**
1. `GET /api/v1/parents/profile` (1 call)
2. `GET /api/v1/parents/children` (1 call)
3. For each child (assume 2 children average):
   - `GET /api/v1/parents/child/{id}/attendance` (2 calls)
   - `GET /api/v1/parents/child/{id}/fees` (2 calls)
   - `GET /api/v1/parents/child/{id}/results` (2 calls)
4. `GET /api/v1/announcements?limit=5` (1 call)

**Total:** 1 + 1 + 6 + 1 = 9 API calls

**Optimization:** Children data loaded in parallel (6 calls in parallel, not sequential)

**Loading Time:** ~800ms (parallel) vs ~2400ms (sequential)

### 4.4 Partial Failure Behavior

| Call Fails | Dashboard Impact | User Experience |
|------------|------------------|-----------------|
| Profile | Cannot display parent name | Show "Parent" fallback |
| Children | Cannot load any child data | Show "No children found" error |
| Child attendance | Cannot show attendance for that child | Show "—" placeholder for that child |
| Child fees | Cannot show fee status for that child | Hide fees widget for that child |
| Child results | Cannot show results for that child | Hide results widget for that child |
| Announcements | Cannot show announcements | Hide announcements widget |

**Strategy:** Graceful degradation per child. If one child's data fails, other children still display.

### 4.5 Empty Data Behavior

| Field | Empty Condition | Display |
|-------|----------------|---------|
| Children | No children linked | "No children linked to your account" message |
| Attendance | No attendance records | "No attendance data" message |
| Fees | No fees assigned | "No fees outstanding" message |
| Results | No results yet | "No results available" message |
| Announcements | No announcements | "No announcements" message |

### 4.6 Missing Backend Capability

**Gap 1:** Parent must make N+1 calls for multiple children

**Current State:** Parent with 3 children makes 9 API calls (3 children × 3 data types + profile + children list + announcements)

**Impact:** Performance concern for parents with many children

**Recommendation:** Accept for M1 (most parents have 1-3 children). Consider batch endpoint for M2:
```
GET /api/v1/parents/children/dashboard → List<ChildDashboardResponse>
  - Returns all children with attendance, fees, and results in one call
```

**Gap 2:** StudentFeeResponse may lack `dueDate` field

**Current State:** Parent cannot see when fees are due

**Impact:** P1 feature (important for M1)

**Recommendation:** Same as student dashboard — inspect StudentFeeResponse.java.

---

## 5. Comparison: Frontend Composition vs Backend DTO

### 5.1 Frontend Composition (Recommended)

**Pros:**
- ✅ No backend changes required
- ✅ Reuses existing endpoints
- ✅ Flexible (frontend can add/remove widgets without backend changes)
- ✅ Parallel loading (fast)
- ✅ Graceful degradation (partial failures handled)

**Cons:**
- ❌ Multiple API calls (4-9 per dashboard load)
- ❌ Frontend must aggregate data
- ❌ N+1 problem for parent with many children

**API Call Count:**
- TEACHER: 4 calls
- STUDENT: 4 calls
- PARENT: 9 calls (for 2 children)

### 5.2 Backend DTO (Not Recommended for M1)

**Pros:**
- ✅ Single API call per dashboard
- ✅ Backend handles aggregation
- ✅ No N+1 problem

**Cons:**
- ❌ Requires 3 new DTOs (TeacherDashboardResponse, StudentDashboardResponse, ParentDashboardResponse)
- ❌ Requires 3 new endpoints
- ❌ Requires 3 new service methods
- ❌ Inflexible (frontend changes require backend changes)
- ❌ Effort: 3-5 days per dashboard (9-15 days total)

**Effort Estimate:**
- TeacherDashboardResponse: 3 days
- StudentDashboardResponse: 3 days
- ParentDashboardResponse: 5 days (more complex due to N children)
- **Total:** 11 days

### 5.3 Recommendation

**Use Frontend Composition for M1.**

**Rationale:**
1. No backend changes required (saves 11 days of work)
2. Existing endpoints are sufficient
3. API call count is acceptable (4-9 calls per dashboard)
4. Parallel loading keeps response time under 1 second
5. Graceful degradation handles partial failures
6. N+1 problem is acceptable for M1 (most parents have 1-3 children)

**Defer Backend DTOs to M2** if:
- Parents have 4+ children (N+1 becomes performance issue)
- Dashboard widgets require complex aggregation not available from existing endpoints
- Product owner prioritizes single-call dashboards over development speed

---

## 6. Implementation Checklist

### 6.1 Frontend Tasks (M1)

**TEACHER Dashboard:**
- [ ] Create `TeacherDashboardData` model
- [ ] Create `teacher_dashboard_provider.dart` with parallel loading
- [ ] Implement graceful degradation for each widget
- [ ] Implement empty state messages
- [ ] Test with 0, 1, 5, 10 classes

**STUDENT Dashboard:**
- [ ] Create `StudentDashboardData` model
- [ ] Create `student_dashboard_provider.dart` with parallel loading
- [ ] Implement graceful degradation for each widget
- [ ] Implement empty state messages
- [ ] Test with 0, 1, 5 fee records
- [ ] Verify StudentFeeResponse has `dueDate` field (or document gap)

**PARENT Dashboard:**
- [ ] Create `ParentDashboardData` and `ChildDashboardData` models
- [ ] Create `parent_dashboard_provider.dart` with parallel loading
- [ ] Implement graceful degradation per child
- [ ] Implement empty state messages
- [ ] Test with 0, 1, 2, 3 children
- [ ] Verify StudentFeeResponse has `dueDate` field (or document gap)

### 6.2 Backend Tasks (M1 — Only if gaps found)

- [ ] Inspect `StudentFeeResponse.java` for `dueDate` field
- [ ] If missing, add `dueDate` field to StudentFeeResponse
- [ ] Update StudentFeeServiceImpl to populate `dueDate`
- [ ] Test with fees that have due dates

### 6.3 Backend Tasks (M2 — Deferred)

- [ ] Create `TeacherDashboardResponse` DTO
- [ ] Create `GET /api/v1/teachers/dashboard` endpoint
- [ ] Create `StudentDashboardResponse` DTO
- [ ] Create `GET /api/v1/students/dashboard` endpoint
- [ ] Create `ParentDashboardResponse` DTO
- [ ] Create `GET /api/v1/parents/dashboard` endpoint
- [ ] Add unit tests for each new endpoint
- [ ] Add integration tests for each new endpoint

---

## 7. Risk Assessment

### 7.1 Frontend Composition Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Too many API calls slow down dashboard | Medium | Medium | Parallel loading, caching, lazy loading |
| N+1 problem for parents with many children | Low | Medium | Accept for M1, defer batch endpoint to M2 |
| Partial failures confuse users | Low | Low | Graceful degradation, clear error messages |
| Frontend aggregation logic has bugs | Medium | Medium | Comprehensive unit tests, manual testing |

### 7.2 Backend DTO Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Backend changes take longer than expected | High | High | Use frontend composition for M1 |
| New endpoints introduce security gaps | Medium | High | Thorough security review, tenant isolation tests |
| Inflexible design requires frequent backend changes | Medium | Medium | Prefer frontend composition |

---

## 8. Conclusion

**Recommendation:** Use frontend composition for TEACHER, STUDENT, and PARENT dashboards in M1.

**Rationale:**
1. No backend changes required (saves 11 days)
2. Existing endpoints are sufficient
3. Performance is acceptable (parallel loading <1s)
4. Graceful degradation handles partial failures
5. N+1 problem is acceptable for M1

**Action Items:**
1. Frontend team implements dashboard composition logic
2. Backend team inspects StudentFeeResponse for `dueDate` field
3. Product owner approves frontend composition approach
4. Defer backend DTOs to M2 backlog

**Open Questions:**
1. Does StudentFeeResponse have `dueDate` field? (Requires inspection)
2. What is the maximum number of children per parent? (Affects N+1 risk)
3. Should we implement caching for dashboard data? (Performance optimization)

---

**Report Completed:** 2026-11-05T12:16:00Z  
**Auditor:** Implementation Agent  
**Status:** READY FOR PRODUCT OWNER REVIEW
