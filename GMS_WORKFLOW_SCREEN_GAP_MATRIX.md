# GMS Mobile — Workflow & Screen Gap Matrix

## Executive Summary

**Review Date:** 2026-11-05  
**Status:** ⚠️ CRITICAL GAPS IDENTIFIED

The design deliverables report claims "five critical workflows" are complete, but only **10 static screens** exist. This document maps actual screen-to-screen workflows and identifies all missing screens required for functional completeness.

---

## 1. Claimed vs. Actual Workflow Coverage

### Claimed Workflows (from Design Deliverables Report)

1. **ADMIN Student Management** — Dashboard → Students → Student List → Search/Filter → Student Details → Add/Edit → Save → Confirmation
2. **ACCOUNTANT Fee Collection** — Dashboard → Search Student → Fee Details → Outstanding Balance → Record Payment → Review → Confirmation → Receipt
3. **TEACHER Mark Attendance** — Dashboard → Class Selection → Student List → Mark Attendance → Submit → Confirmation
4. **STUDENT View Results** — Dashboard → Results → Filter → Exam List → Exam Details → Download Report
5. **PARENT View Child Attendance** — Dashboard → Child Selection → Attendance → Calendar View → Monthly Log

### Actual Screen Inventory

| # | Screen | Role | Type | Exists |
|---|--------|------|------|--------|
| 1 | ADMIN Dashboard | ADMIN | Dashboard | ✅ |
| 2 | ACCOUNTANT Dashboard | ACCOUNTANT | Dashboard | ✅ |
| 3 | TEACHER Dashboard | TEACHER | Dashboard | ❌ **MISSING** |
| 4 | STUDENT Dashboard | STUDENT | Dashboard | ✅ |
| 5 | PARENT Dashboard | PARENT | Dashboard | ✅ |
| 6 | ADMIN Student List | ADMIN | List | ✅ |
| 7 | TEACHER Mark Attendance | TEACHER | Form | ✅ |
| 8 | ACCOUNTANT Fee Collection | ACCOUNTANT | Form | ✅ |
| 9 | STUDENT View Results | STUDENT | List/Detail | ✅ |
| 10 | PARENT View Child Attendance | PARENT | Detail | ❌ **MISSING** |

**Coverage:** 8 of 10 screens exist (80%)

---

## 2. Workflow Completeness Analysis

### Workflow 1: ADMIN Student Management

**Claimed Flow:**
```
Dashboard → Students → Student List → Search/Filter → Student Details → Add/Edit → Save → Confirmation
```

**Actual Screens:**
- ✅ Entry: ADMIN Dashboard (`01_admin_dashboard.png`)
- ✅ Intermediate: ADMIN Student List (`06_admin_student_list.png`)
- ❌ Missing: Student Details screen
- ❌ Missing: Add/Edit Student form
- ❌ Missing: Save confirmation
- ❌ Missing: Error states

**Missing Screens:** 4

**Backend Endpoints Required:**
- `GET /api/v1/students` — List students (with pagination, filters)
- `GET /api/v1/students/{id}` — Get student details
- `POST /api/v1/students` — Create student
- `PUT /api/v1/students/{id}` — Update student
- `DELETE /api/v1/students/{id}` — Delete student

**Gap Assessment:** ⚠️ **PARTIAL** — List view exists, but CRUD operations are incomplete

---

### Workflow 2: ACCOUNTANT Fee Collection

**Claimed Flow:**
```
Dashboard → Search Student → Fee Details → Outstanding Balance → Record Payment → Review → Confirmation → Receipt
```

**Actual Screens:**
- ✅ Entry: ACCOUNTANT Dashboard (`02_accountant_dashboard.png`)
- ✅ Form: ACCOUNTANT Fee Collection (`05_parent_dashboard.png` / `08_accountant_fees.png`)
- ❌ Missing: Student search/selection screen
- ❌ Missing: Payment confirmation screen
- ❌ Missing: Receipt generation screen
- ❌ Missing: Success state

**Missing Screens:** 4

**Backend Endpoints Required:**
- `GET /api/v1/fees/student/{studentId}` — Get student fees
- `GET /api/v1/fees/outstanding` — Get outstanding fees
- `POST /api/v1/fee-payments` — Record payment
- `GET /api/v1/fee-payments/{id}/receipt` — Generate receipt

**Gap Assessment:** ⚠️ **PARTIAL** — Payment form exists, but student selection and receipt generation are missing

---

### Workflow 3: TEACHER Mark Attendance

**Claimed Flow:**
```
Dashboard → Class Selection → Student List → Mark Attendance → Submit → Confirmation
```

**Actual Screens:**
- ❌ Missing: TEACHER Dashboard
- ✅ Form: TEACHER Mark Attendance (`07_teacher_attendance.png`)
- ❌ Missing: Class selection screen
- ❌ Missing: Submission confirmation
- ❌ Missing: Success state

**Missing Screens:** 3

**Backend Endpoints Required:**
- `GET /api/v1/teachers/classes` — Get teacher's classes
- `GET /api/v1/attendance/class/{classId}/date/{date}` — Get class attendance
- `POST /api/v1/attendance/bulk` — Record bulk attendance
- `PUT /api/v1/attendance/{id}` — Update single attendance

**Gap Assessment:** ⚠️ **PARTIAL** — Attendance marking form exists, but class selection and confirmation are missing

---

### Workflow 4: STUDENT View Results

**Claimed Flow:**
```
Dashboard → Results → Filter → Exam List → Exam Details → Download Report
```

**Actual Screens:**
- ✅ Entry: STUDENT Dashboard (`03_teacher_dashboard.png`)
- ✅ List/Detail: STUDENT View Results (`09_student_results.png`)
- ❌ Missing: Exam detail screen (individual exam breakdown)
- ❌ Missing: Report card preview
- ❌ Missing: Download confirmation

**Missing Screens:** 3

**Backend Endpoints Required:**
- `GET /api/v1/students/me/results` — Get my results
- `GET /api/v1/students/me/results/{examId}` — Get exam details
- `GET /api/v1/students/me/report-card` — Generate report card

**Gap Assessment:** ⚠️ **PARTIAL** — Results list exists, but exam details and report card are incomplete

---

### Workflow 5: PARENT View Child Attendance

**Claimed Flow:**
```
Dashboard → Child Selection → Attendance → Calendar View → Monthly Log
```

**Actual Screens:**
- ✅ Entry: PARENT Dashboard (`04_student_dashboard.png`)
- ❌ Missing: PARENT View Child Attendance screen
- ❌ Missing: Child selection screen (if multiple children)
- ❌ Missing: Calendar view
- ❌ Missing: Monthly log detail

**Missing Screens:** 4

**Backend Endpoints Required:**
- `GET /api/v1/parents/children` — Get parent's children
- `GET /api/v1/parents/children/{childId}/attendance` — Get child attendance
- `GET /api/v1/parents/children/{childId}/attendance/calendar` — Get calendar view

**Gap Assessment:** ❌ **INCOMPLETE** — Only dashboard exists; attendance view is entirely missing

---

## 3. Complete Screen Gap Inventory

### By Role

#### ADMIN Role
| Screen | Purpose | Priority | Backend Support |
|--------|---------|----------|-----------------|
| Dashboard | School overview | ✅ Exists | ✅ |
| Student List | Browse/search students | ✅ Exists | ✅ |
| Student Details | View individual student | ❌ Missing | ✅ |
| Add/Edit Student | Create/update student | ❌ Missing | ✅ |
| Teacher List | Browse/search teachers | ❌ Missing | ✅ |
| Teacher Details | View teacher profile | ❌ Missing | ✅ |
| Class List | View all classes | ❌ Missing | ✅ |
| Announcement List | View announcements | ❌ Missing | ✅ |
| Announcement Create | Create announcement | ❌ Missing | ✅ |

**ADMIN Coverage:** 2 of 9 screens (22%)

#### ACCOUNTANT Role
| Screen | Purpose | Priority | Backend Support |
|--------|---------|----------|-----------------|
| Dashboard | Financial overview | ✅ Exists | ✅ |
| Fee Collection | Record payment | ✅ Exists | ✅ |
| Student Fee Search | Find student fees | ❌ Missing | ✅ |
| Payment Confirmation | Confirm payment | ❌ Missing | ✅ |
| Receipt Generation | Generate receipt | ❌ Missing | ✅ |
| Fee Structure List | View fee structures | ❌ Missing | ✅ |
| Financial Reports | View reports | ❌ Missing | ✅ |

**ACCOUNTANT Coverage:** 2 of 7 screens (29%)

#### TEACHER Role
| Screen | Purpose | Priority | Backend Support |
|--------|---------|----------|-----------------|
| Dashboard | Class overview | ❌ Missing | ✅ |
| Class List | View assigned classes | ❌ Missing | ✅ |
| Student List | View class students | ❌ Missing | ✅ |
| Mark Attendance | Record attendance | ✅ Exists | ✅ |
| Attendance History | View past attendance | ❌ Missing | ✅ |
| Exam List | View exams | ❌ Missing | ✅ |
| Announcement Create | Create announcement | ❌ Missing | ✅ |

**TEACHER Coverage:** 1 of 7 screens (14%)

#### STUDENT Role
| Screen | Purpose | Priority | Backend Support |
|--------|---------|----------|-----------------|
| Dashboard | Personal overview | ✅ Exists | ✅ |
| Schedule | View timetable | ❌ Missing | ✅ |
| Results List | View exam results | ✅ Exists | ✅ |
| Exam Details | Individual exam breakdown | ❌ Missing | ✅ |
| Report Card | Download report | ❌ Missing | ✅ |
| Fee Status | View fee status | ❌ Missing | ✅ |
| Attendance | View my attendance | ❌ Missing | ✅ |

**STUDENT Coverage:** 2 of 7 screens (29%)

#### PARENT Role
| Screen | Purpose | Priority | Backend Support |
|--------|---------|----------|-----------------|
| Dashboard | Child overview | ✅ Exists | ✅ |
| Child Selection | Switch between children | ❌ Missing | ✅ |
| Child Attendance | View child attendance | ❌ Missing | ✅ |
| Child Results | View child results | ❌ Missing | ✅ |
| Child Fees | View child fees | ❌ Missing | ✅ |
| Announcements | View school notices | ❌ Missing | ✅ |

**PARENT Coverage:** 1 of 6 screens (17%)

---

## 4. Missing Screen Summary

### Critical Missing Screens (Block Workflows)

| # | Screen | Role | Workflow Impact | Backend Endpoint |
|---|--------|------|-----------------|------------------|
| 1 | TEACHER Dashboard | TEACHER | Blocks all teacher workflows | `GET /api/v1/teachers/me` |
| 2 | PARENT View Child Attendance | PARENT | Blocks attendance workflow | `GET /api/v1/parents/children/{id}/attendance` |
| 3 | Student Details | ADMIN | Blocks student management | `GET /api/v1/students/{id}` |
| 4 | Add/Edit Student Form | ADMIN | Blocks student CRUD | `POST/PUT /api/v1/students` |
| 5 | Payment Confirmation | ACCOUNTANT | Blocks fee collection | `POST /api/v1/fee-payments` |
| 6 | Receipt Generation | ACCOUNTANT | Blocks receipt workflow | `GET /api/v1/fee-payments/{id}/receipt` |
| 7 | Class Selection | TEACHER | Blocks attendance workflow | `GET /api/v1/teachers/classes` |
| 8 | Child Selection | PARENT | Blocks child switching | `GET /api/v1/parents/children` |

**Total Critical Missing:** 8 screens

### High Priority Missing Screens

| # | Screen | Role | Purpose |
|---|--------|------|---------|
| 9 | Teacher List | ADMIN | Browse teachers |
| 10 | Announcement List | ADMIN | View announcements |
| 11 | Fee Structure List | ACCOUNTANT | View fee structures |
| 12 | Financial Reports | ACCOUNTANT | View reports |
| 13 | Student Schedule | STUDENT | View timetable |
| 14 | Exam Details | STUDENT | Individual exam breakdown |
| 15 | Report Card | STUDENT | Download report |
| 16 | Child Results | PARENT | View child results |

**Total High Priority:** 8 screens

### Medium Priority Missing Screens

| # | Screen | Role | Purpose |
|---|--------|------|---------|
| 17 | Teacher Details | ADMIN | View teacher profile |
| 18 | Class List | ADMIN | View all classes |
| 19 | Announcement Create | ADMIN/TEACHER | Create announcement |
| 20 | Attendance History | TEACHER | View past attendance |
| 21 | Exam List | TEACHER | View exams |
| 22 | Fee Status | STUDENT | View fee status |
| 23 | Attendance | STUDENT | View my attendance |
| 24 | Child Fees | PARENT | View child fees |
| 25 | Announcements | PARENT | View school notices |

**Total Medium Priority:** 9 screens

---

## 5. Workflow State Coverage

### Required States per Workflow

| State | Description | Coverage |
|-------|-------------|----------|
| **Entry** | Dashboard or list view | ✅ 80% (8/10) |
| **Intermediate** | Search, filter, selection | ❌ 0% (0/10) |
| **Form** | Data entry/edit | ✅ 20% (2/10) |
| **Confirmation** | Review before submit | ❌ 0% (0/10) |
| **Success** | Operation completed | ❌ 0% (0/10) |
| **Error** | Operation failed | ❌ 0% (0/10) |
| **Empty** | No data available | ❌ 0% (0/10) |
| **Loading** | Data fetching | ❌ 0% (0/10) |

**State Coverage:** Only Entry and Form states are partially designed

---

## 6. Recommendations

### 6.1 Immediate Actions (Before M1 Implementation)

1. **Create TEACHER Dashboard** — Blocks all teacher workflows
2. **Create PARENT View Child Attendance** — Blocks parent attendance workflow
3. **Design confirmation screens** — Required for payment and attendance submission
4. **Design success/error states** — Required for all workflows

### 6.2 M1 Scope Reduction

Given the gaps, recommend focusing M1 on:
- **ADMIN:** Dashboard + Student List (existing) + Student Details (new)
- **ACCOUNTANT:** Dashboard + Fee Collection (existing) + Receipt (new)
- **TEACHER:** Dashboard (new) + Mark Attendance (existing)
- **STUDENT:** Dashboard + Results (existing)
- **PARENT:** Dashboard (existing) + Child Attendance (new)

**Total M1 Screens:** 10 (5 existing + 5 new)

### 6.3 M2+ Scope

Defer to M2:
- Teacher class selection and student lists
- Student schedule and fee status
- Parent child selection and results
- Announcement management
- Financial reports

---

## 7. Conclusion

**Current State:**
- 10 screens designed (80% of claimed 10)
- 2 screens missing entirely
- 0 confirmation/success/error states designed
- 5 workflows are incomplete (missing 25+ screens)

**Recommendation:**
Do not claim "workflow complete" until:
1. All entry, form, confirmation, and success screens exist
2. Error and empty states are designed
3. Backend endpoints are verified

**Next Step:** Review `GMS_SCREEN_API_TRACEABILITY.md` for backend integration details.

---

**Awaiting product-owner approval before proceeding.**
