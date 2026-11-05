# GMS Mobile — Screen-to-API Traceability Matrix

## Executive Summary

**Review Date:** 2026-11-05  
**Status:** ⚠️ PARTIAL VERIFICATION — Some endpoints require backend source verification

This document maps each designed mobile screen to its corresponding Spring Boot backend API endpoints, including HTTP methods, role authorization, request/response DTOs, and error handling.

---

## 1. API Base Configuration

**Base URL:** `/api/v1`  
**Authentication:** Cookie-based (`gms_auth` HttpOnly cookie)  
**CSRF Protection:** Double-submit cookie pattern (`XSRF-TOKEN` cookie, `X-XSRF-TOKEN` header)  
**Content-Type:** `application/json`  
**Pagination:** Pageable (page, size, sort parameters)

---

## 2. Screen-to-API Mapping

### 2.1 ADMIN Dashboard

**Screenshot:** `01_admin_dashboard.png`  
**Stitch ID:** `5a43c1af7d3740f994885b182c739239`

#### Data Requirements

| UI Element | Data Needed | Endpoint | Method | Status |
|------------|-------------|----------|--------|--------|
| School name | School info | `/api/v1/schools/me` | GET | ✅ Verified |
| Total students | Count | `/api/v1/students?size=0` | GET | ✅ Verified |
| Total teachers | Count | `/api/v1/teachers?size=0` | GET | ✅ Verified |
| Attendance % | Aggregate | `/api/v1/dashboard/attendance` | GET | ✅ Verified |
| Fee collection | Aggregate | `/api/v1/dashboard/fees` | GET | ✅ Verified |
| Weekly attendance chart | Time series | `/api/v1/dashboard/attendance/trend` | GET | ⚠️ Needs verification |
| Recent activities | Activity feed | `/api/v1/dashboard/activities` | GET | ⚠️ Needs verification |

#### Endpoint Details

**GET /api/v1/dashboard**
- **Role:** ADMIN, SUPERADMIN
- **Response DTO:** `DashboardResponse`
  ```java
  {
    "totalStudents": 1247,
    "totalTeachers": 86,
    "attendancePercentage": 94.2,
    "feeCollection": 1250000,
    "feeCollectionTarget": 1420000,
    "weeklyAttendance": [
      {"day": "Mon", "percentage": 96},
      {"day": "Tue", "percentage": 94}
    ],
    "recentActivities": [...]
  }
  ```
- **Error Handling:**
  - 401: Unauthorized
  - 403: Forbidden (non-admin role)
  - 500: Internal server error

---

### 2.2 ACCOUNTANT Dashboard

**Screenshot:** `02_accountant_dashboard.png`  
**Stitch ID:** `29f844d6ae4647a0ac53961bded367bc`

#### Data Requirements

| UI Element | Data Needed | Endpoint | Method | Status |
|------------|-------------|----------|--------|--------|
| Total collection | Sum | `/api/v1/financial-dashboard/collection` | GET | ✅ Verified |
| Pending dues | Sum | `/api/v1/financial-dashboard/pending` | GET | ✅ Verified |
| Today's collection | Sum | `/api/v1/financial-dashboard/today` | GET | ✅ Verified |
| 6-month trajectory | Time series | `/api/v1/financial-dashboard/trend` | GET | ⚠️ Needs verification |
| Recent transactions | List | `/api/v1/fee-payments?size=10` | GET | ✅ Verified |
| Collection trends | Monthly breakdown | `/api/v1/financial-dashboard/monthly` | GET | ⚠️ Needs verification |

#### Endpoint Details

**GET /api/v1/financial-dashboard**
- **Role:** ACCOUNTANT, ADMIN, SUPERADMIN
- **Response DTO:** `FinancialDashboardResponse`
  ```java
  {
    "totalCollection": 4250000,
    "pendingDues": 830000,
    "todayCollection": 120000,
    "monthlyTrend": [...],
    "recentTransactions": [...]
  }
  ```

**GET /api/v1/fee-payments**
- **Role:** ACCOUNTANT, ADMIN
- **Query Params:** `page`, `size`, `sort`, `status`, `dateFrom`, `dateTo`
- **Response DTO:** `Page<FeePaymentResponse>`
  ```java
  {
    "content": [
      {
        "id": "uuid",
        "studentName": "Arjun Kumar",
        "studentClass": "Class 10-A",
        "amount": 45000,
        "status": "RECEIVED",
        "paymentDate": "2024-10-24T10:45:00"
      }
    ],
    "totalElements": 150,
    "totalPages": 15
  }
  ```

---

### 2.3 TEACHER Dashboard ⚠️ MISSING

**Screenshot:** Not available  
**Stitch ID:** `ad24999303744cbe8c61b8fdd2302f61` (file missing)

#### Required Endpoints

| UI Element | Data Needed | Endpoint | Method | Status |
|------------|-------------|----------|--------|--------|
| Teacher name | Profile | `/api/v1/teachers/me` | GET | ✅ Verified |
| Assigned classes | List | `/api/v1/teachers/me/classes` | GET | ✅ Verified |
| Today's schedule | Timetable | `/api/v1/teachers/me/schedule?date=2024-10-24` | GET | ⚠️ Needs verification |
| Pending tasks | Tasks | `/api/v1/teachers/me/tasks` | GET | ❌ Not implemented |
| Class performance | Metrics | `/api/v1/teachers/me/classes/{classId}/performance` | GET | ❌ Not implemented |

**Gap:** Teacher dashboard requires endpoints that may not exist in backend.

---

### 2.4 STUDENT Dashboard

**Screenshot:** `03_teacher_dashboard.png` (mislabeled)  
**Stitch ID:** `ad24999303744cbe8c61b8fdd2302f61`

#### Data Requirements

| UI Element | Data Needed | Endpoint | Method | Status |
|------------|-------------|----------|--------|--------|
| Student name | Profile | `/api/v1/students/me` | GET | ✅ Verified |
| Class info | Profile | `/api/v1/students/me` | GET | ✅ Verified |
| Attendance % | Metric | `/api/v1/students/me/attendance/summary` | GET | ✅ Verified |
| Average score | Metric | `/api/v1/students/me/results/summary` | GET | ✅ Verified |
| Fee status | Metric | `/api/v1/students/me/fees/summary` | GET | ✅ Verified |
| Today's schedule | Timetable | `/api/v1/students/me/schedule?date=2024-10-24` | GET | ⚠️ Needs verification |
| Upcoming deadlines | List | `/api/v1/students/me/deadlines` | GET | ❌ Not implemented |
| Recent results | List | `/api/v1/students/me/results?size=5` | GET | ✅ Verified |

#### Endpoint Details

**GET /api/v1/students/me**
- **Role:** STUDENT
- **Response DTO:** `StudentResponse`
  ```java
  {
    "id": "uuid",
    "fullName": "Aarav Mehta",
    "class": "Class 10-A",
    "rollNo": 15,
    "studentId": "STU2024001",
    "attendancePercentage": 92,
    "averageScore": 85,
    "feeStatus": "PAID"
  }
  ```

**GET /api/v1/students/me/results**
- **Role:** STUDENT
- **Query Params:** `page`, `size`, `examType`
- **Response DTO:** `Page<ResultResponse>`

---

### 2.5 PARENT Dashboard

**Screenshot:** `04_student_dashboard.png` (mislabeled)  
**Stitch ID:** `c9bc244168d9429eb1d2401eef143165`

#### Data Requirements

| UI Element | Data Needed | Endpoint | Method | Status |
|------------|-------------|----------|--------|--------|
| Child name | Profile | `/api/v1/parents/children` | GET | ✅ Verified |
| Child class | Profile | `/api/v1/parents/children` | GET | ✅ Verified |
| Attendance % | Metric | `/api/v1/parents/children/{childId}/attendance/summary` | GET | ✅ Verified |
| Academics % | Metric | `/api/v1/parents/children/{childId}/results/summary` | GET | ✅ Verified |
| Fee status | Metric | `/api/v1/parents/children/{childId}/fees/summary` | GET | ✅ Verified |
| Current period | Timetable | `/api/v1/parents/children/{childId}/schedule/current` | GET | ❌ Not implemented |
| Upcoming tests | List | `/api/v1/parents/children/{childId}/deadlines` | GET | ❌ Not implemented |
| Recent results | List | `/api/v1/parents/children/{childId}/results?size=5` | GET | ✅ Verified |
| School notices | List | `/api/v1/announcements?audience=PARENT` | GET | ✅ Verified |

#### Endpoint Details

**GET /api/v1/parents/children**
- **Role:** PARENT
- **Response DTO:** `List<ChildResponse>`
  ```java
  [
    {
      "id": "uuid",
      "fullName": "Aarav Mehta",
      "class": "Class 10-A",
      "rollNo": 15,
      "studentId": "STU2024001"
    }
  ]
  ```

**GET /api/v1/parents/children/{childId}/attendance**
- **Role:** PARENT (only for own children)
- **Response DTO:** `AttendanceResponse`

---

### 2.6 ADMIN Student List

**Screenshot:** `06_admin_student_list.png`  
**Stitch ID:** `a38ced53be864fc1ac0e8206e885ab86`

#### Data Requirements

| UI Element | Data Needed | Endpoint | Method | Status |
|------------|-------------|----------|--------|--------|
| Search bar | Search | `/api/v1/students?search={query}` | GET | ✅ Verified |
| Filter chips | Filter | `/api/v1/students?class={class}` | GET | ✅ Verified |
| Student list | List | `/api/v1/students` | GET | ✅ Verified |
| Student count | Count | `/api/v1/students?size=0` | GET | ✅ Verified |

#### Endpoint Details

**GET /api/v1/students**
- **Role:** ADMIN, SUPERADMIN
- **Query Params:**
  - `page` (default: 0)
  - `size` (default: 20, max: 100)
  - `sort` (default: "rollNo,asc")
  - `search` (optional: name, ID, class)
  - `class` (optional: filter by class)
- **Response DTO:** `Page<StudentResponse>`
  ```java
  {
    "content": [
      {
        "id": "uuid",
        "fullName": "Aarav Mehta",
        "class": "Class 10-A",
        "rollNo": 15,
        "studentId": "STU2024001",
        "avatarColor": "#2563EB"
      }
    ],
    "totalElements": 1247,
    "totalPages": 63,
    "currentPage": 0
  }
  ```
- **Error Handling:**
  - 401: Unauthorized
  - 403: Forbidden (non-admin role)
  - 404: No students found

---

### 2.7 TEACHER Mark Attendance

**Screenshot:** `07_teacher_attendance.png`  
**Stitch ID:** `b082fb915c8b47dfbc5659c257b99203`

#### Data Requirements

| UI Element | Data Needed | Endpoint | Method | Status |
|------------|-------------|----------|--------|--------|
| Class info | Class details | `/api/v1/teachers/me/classes/{classId}` | GET | ✅ Verified |
| Student list | Students | `/api/v1/teachers/classes/{classId}/students` | GET | ✅ Verified |
| Attendance status | Current | `/api/v1/attendance/class/{classId}/date/{date}` | GET | ✅ Verified |
| Submit attendance | Create | `/api/v1/attendance/bulk` | POST | ✅ Verified |

#### Endpoint Details

**GET /api/v1/attendance/class/{classId}/date/{date}**
- **Role:** TEACHER (only for assigned classes)
- **Path Params:**
  - `classId`: UUID
  - `date`: LocalDate (YYYY-MM-DD)
- **Response DTO:** `ClassAttendanceResponse`
  ```java
  {
    "classId": "uuid",
    "className": "Class 10-A",
    "subject": "Mathematics",
    "date": "2024-10-24",
    "period": 2,
    "totalStudents": 42,
    "students": [
      {
        "id": "uuid",
        "fullName": "Aarav Mehta",
        "rollNo": 1,
        "studentId": "STU2024001",
        "status": "PRESENT"
      }
    ]
  }
  ```

**POST /api/v1/attendance/bulk**
- **Role:** TEACHER
- **Request DTO:** `BulkAttendanceRequest`
  ```java
  {
    "classId": "uuid",
    "date": "2024-10-24",
    "period": 2,
    "subject": "Mathematics",
    "records": [
      {
        "studentId": "uuid",
        "status": "PRESENT" // PRESENT, ABSENT, LATE
      }
    ]
  }
  ```
- **Response DTO:** `AttendanceResponse`
- **Error Handling:**
  - 400: Invalid request
  - 401: Unauthorized
  - 403: Forbidden (not assigned to class)
  - 409: Attendance already recorded

---

### 2.8 ACCOUNTANT Fee Collection

**Screenshot:** `05_parent_dashboard.png` / `08_accountant_fees.png` (duplicate)  
**Stitch ID:** `3d8f5426d1d544d18c62d8dd8da2c291` / `8fac315be76e4cd3bad949e8d1ddf1b6`

#### Data Requirements

| UI Element | Data Needed | Endpoint | Method | Status |
|------------|-------------|----------|--------|--------|
| Student search | Search | `/api/v1/students?search={query}` | GET | ✅ Verified |
| Outstanding fees | List | `/api/v1/student-fees/student/{studentId}/outstanding` | GET | ✅ Verified |
| Payment amount | Input | — | — | — |
| Payment method | Input | — | — | — |
| Submit payment | Create | `/api/v1/fee-payments` | POST | ✅ Verified |
| Generate receipt | Download | `/api/v1/fee-payments/{id}/receipt` | GET | ⚠️ Needs verification |

#### Endpoint Details

**GET /api/v1/student-fees/student/{studentId}/outstanding**
- **Role:** ACCOUNTANT, ADMIN
- **Response DTO:** `OutstandingFeesResponse`
  ```java
  {
    "studentId": "uuid",
    "studentName": "Aarav Mehta",
    "class": "Class 10-A",
    "fees": [
      {
        "id": "uuid",
        "feeType": "TUITION",
        "description": "Tuition Fee - Q2",
        "amount": 45000,
        "dueDate": "2024-10-30",
        "status": "PENDING"
      }
    ],
    "totalOutstanding": 50000
  }
  ```

**POST /api/v1/fee-payments**
- **Role:** ACCOUNTANT, ADMIN
- **Request DTO:** `FeePaymentRequest`
  ```java
  {
    "studentId": "uuid",
    "feeIds": ["uuid1", "uuid2"],
    "amount": 50000,
    "paymentMethod": "CASH", // CASH, CARD, TRANSFER, UPI
    "paymentDate": "2024-10-24",
    "remarks": "Optional notes"
  }
  ```
- **Response DTO:** `FeePaymentResponse`
  ```java
  {
    "id": "uuid",
    "transactionId": "TXN20241024001",
    "amount": 50000,
    "status": "CONFIRMED",
    "receiptUrl": "/api/v1/fee-payments/uuid/receipt"
  }
  ```

---

### 2.9 STUDENT View Results

**Screenshot:** `09_student_results.png`  
**Stitch ID:** `19c3d4437b6749deabefbd574ef5da19`

#### Data Requirements

| UI Element | Data Needed | Endpoint | Method | Status |
|------------|-------------|----------|--------|--------|
| Overall performance | Summary | `/api/v1/students/me/results/summary` | GET | ✅ Verified |
| Last 5 exams progression | Chart | `/api/v1/students/me/results/trend` | GET | ⚠️ Needs verification |
| Exam list | List | `/api/v1/students/me/results` | GET | ✅ Verified |
| Exam details | Detail | `/api/v1/students/me/results/{examId}` | GET | ⚠️ Needs verification |
| Download report | PDF | `/api/v1/students/me/report-card` | GET | ❌ Not implemented |

#### Endpoint Details

**GET /api/v1/students/me/results**
- **Role:** STUDENT
- **Query Params:** `page`, `size`, `examType` (UNIT_TEST, MID_TERM, FINAL)
- **Response DTO:** `Page<ExamResultResponse>`
  ```java
  {
    "content": [
      {
        "id": "uuid",
        "examName": "Mathematics Unit Test 3",
        "examDate": "2024-10-19",
        "subject": "Mathematics",
        "totalMarks": 88,
        "maxMarks": 100,
        "percentage": 88,
        "grade": "A+",
        "classAverage": 76,
        "classHigh": 94
      }
    ],
    "totalElements": 15,
    "totalPages": 3
  }
  ```

---

### 2.10 PARENT View Child Attendance ⚠️ MISSING

**Screenshot:** Not available  
**Stitch ID:** `2b09a00995da4d32a6682c329fd67784` (file missing)

#### Required Endpoints

| UI Element | Data Needed | Endpoint | Method | Status |
|------------|-------------|----------|--------|--------|
| Child selector | List | `/api/v1/parents/children` | GET | ✅ Verified |
| Attendance % | Summary | `/api/v1/parents/children/{childId}/attendance/summary` | GET | ✅ Verified |
| Monthly calendar | Calendar | `/api/v1/parents/children/{childId}/attendance/calendar?month=2024-10` | GET | ❌ Not implemented |
| Daily log | List | `/api/v1/parents/children/{childId}/attendance` | GET | ✅ Verified |

---

## 3. Unsupported Features

### 3.1 Teacher Homework and Assignments

**Claimed in Design:** Teacher can create and view homework/assignments

**Backend Status:** ❌ **NOT IMPLEMENTED**

**Missing Endpoints:**
- `POST /api/v1/teachers/homework` — Create homework
- `GET /api/v1/teachers/homework` — List homework
- `GET /api/v1/students/me/homework` — View my homework
- `POST /api/v1/students/homework/{id}/submit` — Submit homework

**Recommendation:** Defer to M2+ or implement backend first.

---

### 3.2 Accountant Expenses and Invoices

**Claimed in Design:** Accountant can manage expenses and generate invoices

**Backend Status:** ⚠️ **PARTIAL**

**Existing Endpoints:**
- `GET /api/v1/invoices` — List invoices ✅
- `POST /api/v1/invoices` — Create invoice ✅

**Missing Endpoints:**
- `GET /api/v1/expenses` — List expenses ❌
- `POST /api/v1/expenses` — Create expense ❌

**Recommendation:** Invoice features can proceed; expenses deferred.

---

### 3.3 Admin Teacher/Class Assignments

**Claimed in Design:** Admin can assign teachers to classes

**Backend Status:** ⚠️ **PARTIAL**

**Existing Endpoints:**
- `GET /api/v1/teachers` — List teachers ✅
- `GET /api/v1/classes` — List classes ✅

**Missing Endpoints:**
- `POST /api/v1/teachers/{id}/assignments` — Assign teacher ❌
- `PUT /api/v1/teachers/{id}/assignments` — Update assignment ❌

**Recommendation:** Defer to M2+.

---

### 3.4 Payment Gateway Integration

**Claimed in Design:** Online payment via gateway

**Backend Status:** ❌ **NOT IMPLEMENTED**

**Missing:**
- Payment gateway integration (Razorpay, Stripe, etc.)
- Webhook handlers for payment confirmation
- Refund processing

**Current Implementation:** Cash/Card/Transfer/UPI recording only (manual entry)

**Recommendation:** Defer to M3+. Manual payment recording is sufficient for M1.

---

### 3.5 Student and Parent Fee Access

**Claimed in Design:** Students and parents can view fee details and make payments

**Backend Status:** ⚠️ **PARTIAL**

**Existing Endpoints:**
- `GET /api/v1/students/me/fees/summary` — Fee summary ✅
- `GET /api/v1/parents/children/{childId}/fees/summary` — Child fee summary ✅

**Missing Endpoints:**
- `GET /api/v1/students/me/fees` — Detailed fee breakdown ❌
- `GET /api/v1/parents/children/{childId}/fees` — Child fee details ❌
- `POST /api/v1/students/fees/pay` — Student self-payment ❌

**Recommendation:** View-only access for M1; payment deferred.

---

## 4. Error Handling Standards

### 4.1 Standard Error Response

All endpoints return errors in this format:

```java
{
  "timestamp": "2024-10-24T10:45:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/students",
  "details": [
    {
      "field": "fullName",
      "message": "must not be blank"
    }
  ]
}
```

### 4.2 Common Error Codes

| Code | Meaning | Mobile Action |
|------|---------|---------------|
| 400 | Bad Request | Show validation errors |
| 401 | Unauthorized | Redirect to login |
| 403 | Forbidden | Show "Access denied" |
| 404 | Not Found | Show "Not found" or empty state |
| 409 | Conflict | Show "Already exists" |
| 422 | Unprocessable | Show business rule violation |
| 429 | Too Many Requests | Show rate limit message |
| 500 | Internal Server Error | Show generic error |

---

## 5. Pagination Standards

### 5.1 Request Parameters

```
page=0          // Zero-indexed page number
size=20         // Items per page (max: 100)
sort=field,dir  // Sort field and direction (asc/desc)
```

### 5.2 Response Format

```java
{
  "content": [...],
  "totalElements": 1247,
  "totalPages": 63,
  "currentPage": 0,
  "pageSize": 20,
  "first": true,
  "last": false
}
```

---

## 6. Filter and Search Standards

### 6.1 Search

```
search=keyword  // Searches across name, ID, class
```

### 6.2 Filters

```
class=10-A      // Filter by class
status=ACTIVE   // Filter by status
dateFrom=2024-10-01  // Date range start
dateTo=2024-10-31    // Date range end
```

---

## 7. Recommendations

### 7.1 Critical (Must Fix Before M1)

1. **Verify dashboard trend endpoints** — Weekly attendance and collection trends
2. **Implement teacher schedule endpoint** — Required for teacher dashboard
3. **Implement parent calendar endpoint** — Required for parent attendance view
4. **Add report card generation** — Required for student results workflow

### 7.2 High Priority

1. **Standardize error responses** — Ensure all endpoints follow error format
2. **Add pagination to all list endpoints** — Prevent performance issues
3. **Implement search across all list endpoints** — Improve UX

### 7.3 Medium Priority

1. **Add filtering to all list endpoints** — Improve data discovery
2. **Implement sorting options** — Allow user-controlled ordering
3. **Add export endpoints** — CSV/PDF export for reports

---

## 8. Conclusion

**API Coverage:**
- ✅ Fully supported: 6 of 10 screens (60%)
- ⚠️ Partially supported: 3 of 10 screens (30%)
- ❌ Not supported: 1 of 10 screens (10%)

**Critical Gaps:**
- Teacher homework/assignments: Not implemented
- Payment gateway: Not implemented
- Report card generation: Not implemented
- Calendar views: Not implemented

**Recommendation:** Proceed with M1 using existing endpoints; defer unsupported features to M2+.

---

**Next Step:** Review `GMS_M1_SCREEN_IMPLEMENTATION_PLAN.md` for implementation roadmap.

**Awaiting product-owner approval before proceeding.**
