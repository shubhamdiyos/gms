# R5 — Browser Role Sweep and Attendance Day-of-Week Guard

**Date:** 2026-11-06
**Branch:** fix/attendance-day-of-week-guard
**Base:** master @ 470bc9e
**Status:** VERIFIED

---

## Executive Summary

Every role was driven through the running SPA in a real browser against the live dev API. The
sweep surfaced one data-integrity defect in attendance recording and four UI correctness defects.
All five are fixed, regression-tested, and re-verified in the browser.

- **ATT-DOW-01 (data integrity):** an attendance row could name a timetable slot whose weekday did
  not match the attendance date. Fixed with a server-side guard on create, bulk create and update.
- **UI-01..UI-04 (labels/values):** a missing space in the SUPERADMIN banner, "1 Periods" on the
  teacher dashboard, register rows counted as days on the student page, and raw class IDs shown to
  parents instead of the class name.
- **Tests:** 69 backend tests across 12 classes, 9 frontend unit tests — all green.
- **Live proof:** the guarded write path now answers HTTP 400 with the slot weekday and the date's
  weekday instead of persisting a meaningless row.

---

## Environment

| Component | Detail |
|---|---|
| Backend | Spring Boot 3.3.3, Java 21, `--spring.profiles.active=dev`, port 8080 |
| Database | PostgreSQL 17 on 5433, schema `gms`, Flyway chain through V11 |
| Frontend | production build (`dist`) served at `http://127.0.0.1:5173` with a same-origin `/api` proxy |
| Browser | Chrome via browser-use MCP, fresh login per role |

---

## Role Sweep Results

All six roles authenticated with their own credentials and their role-scoped navigation, with live
API data (no mock mode). Observations quoted below are the rendered text captured during the sweep.

| Role | Pages exercised | Live observation |
|---|---|---|
| SUPERADMIN | dashboard, schools, users, academic years, notifications | "Live API data: 5 schools listed by GET /schools \| 0 unread notifications" |
| ADMIN | dashboard, students, employees, classrooms, attendance, exams, fees | role navigation complete; no console errors on visited pages |
| TEACHER | dashboard, timetable, attendance marking, results | "TODAY'S CLASSES 1 — Period 1" |
| STUDENT | dashboard, attendance, results, timetable, fees | "3 Marked Periods / 2 Present / 1 Absent / 0 Late" |
| PARENT | dashboard, children, child attendance, results, fees | "Class: Grade 9-A"; child header "Roll No: STD001 • Class: Grade 9-A • Section: Blue" |
| ACCOUNTANT | dashboard, fee collection, invoices, reports | role navigation complete; fee balances render from live records |

Role isolation held throughout: each role saw only the routes granted to it, and tenant-scoped reads
returned only the caller's school data.

---

## ATT-DOW-01 — Attendance date vs. timetable weekday

### Finding

The `attendances` table stores `(student_id, timetable_slot_id, date, status)` with unique key
`uq_attendance_student_slot_date (student_id, timetable_slot_id, date)`. It does not store a weekday,
and no layer compared the slot's `day_of_week` with the date's weekday. A register row for Friday
could therefore be written against Monday's period. The dev database contained exactly that:

```
id | date       | timetable_slot_id | status
5  | 2026-11-06 | 1                 | PRESENT
-- slot 1: day_of_week = MONDAY, 2026-11-06 is a FRIDAY
```

The row is not corrupt on read — it is simply meaningless: it claims a Monday lesson was held on a
Friday.

### Fix

`AttendanceServiceImpl` now verifies the slot weekday at all three write paths, after the slot has
been resolved and before any row is written:

- `recordAttendance` — after slot lookup.
- `recordBulkAttendance` — after slot lookup, before students are loaded or inserted, so a bad bulk
  request writes nothing.
- `updateAttendance` — after the slot-resolution block, so the row that is written is the row that
  was checked.

The guard is a single helper used by all three paths:

```java
private static void requireSlotScheduledOn(TimetableSlot slot, LocalDate date) {
    if (date == null) {
        throw new IllegalArgumentException("Attendance date is required");
    }
    String scheduledDay = slot.getDayOfWeek();
    String actualDay = date.getDayOfWeek().name();
    if (scheduledDay == null || !scheduledDay.equalsIgnoreCase(actualDay)) {
        throw new IllegalArgumentException(String.format(
                "Timetable slot %d runs on %s, but %s is a %s",
                slot.getId(), scheduledDay, date, actualDay));
    }
}
```

`IllegalArgumentException` maps to HTTP 400 through the existing `GlobalExceptionHandler`.

### Live proof

```
POST /api/v1/attendance
{"studentId":1,"date":"2026-11-06","status":"PRESENT","timetableSlotId":1}

HTTP/1.1 400
{"error":"Invalid request",
 "message":"Timetable slot 1 runs on MONDAY, but 2026-11-06 is a FRIDAY"}
```

No row was written for that request.

### Regression tests

`AttendanceDayOfWeekGuardTest` (new, 4 tests):

| Test | Assertion |
|---|---|
| DAY-01 | single record with mismatched weekday → `IllegalArgumentException`, no insert |
| DAY-02 | missing date → `IllegalArgumentException`, no insert |
| DAY-03 | bulk request with mismatched weekday → throws before student load and before any insert |
| DAY-04 | update of a row to a mismatched date → throws, `save` never called |

Seven existing fixtures used a Tuesday date with MONDAY slots. They now use Monday 2026-11-02, the
weekday their slots declare, with a comment recording why.

---

## UI Corrections

| ID | Page | Before | After |
|---|---|---|---|
| UI-01 | SUPERADMIN dashboard | "schoolslisted" (missing JSX space) | "5 schools listed by GET /schools" |
| UI-02 | TEACHER dashboard | "1 Periods 1" | "1 — Period 1" |
| UI-03 | STUDENT attendance | registers counted and labelled as days ("Total Days") | periods ("Marked Periods"), rate = present periods / marked periods |
| UI-04 | PARENT dashboard + children | "Class ID: 9", "Section ID: 2" | "Class: Grade 9-A", "Section: Blue" |

UI-04 required data, not formatting: `/classrooms` and `/sections` are admin/teacher reads, so a
parent holding only `classId`/`sectionId` could not resolve names client-side. `StudentResponse` now
carries `className` and `sectionName`, populated by `ParentServiceImpl` and `ParentStudentServiceImpl`
from the student's classroom and section relations. Teacher and student endpoints pass `null` because
they do not display names.

---

## Test Evidence

Backend (12 classes, 69 tests, 0 failures, 0 errors, 0 skipped):

| Class | Tests |
|---|---|
| AttendanceDayOfWeekGuardTest | 4 |
| AttendanceServiceImplSlotLevelTest | 9 |
| BulkAttendanceAtomicityDefectTest | 4 |
| BulkAttendanceResponseContractTest | 7 |
| BulkAttendanceRollbackTest | 1 |
| AttendanceConcurrencyPostgresTest | 5 |
| AttendancePostgresIntegrationTest | 9 |
| SingleRecordAttendanceConcurrencyTest | 1 |
| ParentCredentialProvisioningTest | 4 |
| ParentManagementPostgresIntegrationTest | 7 |
| ParentPortalPostgresIntegrationTest | 7 |
| ParentStudentAuthorizationTest | 11 |

Command:

```
cd gms_backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn -Dtest='AttendanceDayOfWeekGuardTest,AttendanceServiceImplSlotLevelTest,BulkAttendanceAtomicityDefectTest,BulkAttendanceResponseContractTest,BulkAttendanceRollbackTest,AttendanceConcurrencyPostgresTest,AttendancePostgresIntegrationTest,SingleRecordAttendanceConcurrencyTest,ParentCredentialProvisioningTest,ParentManagementPostgresIntegrationTest,ParentPortalPostgresIntegrationTest,ParentStudentAuthorizationTest' test
```

Frontend (`npx vitest run`): 2 files, 9 tests passed.

---

## Local Data Hygiene

- Removed the invalid attendance row `id=5` (Friday date on a Monday slot) and cleared the test remark
  `"via teacher API check"` left by an earlier verification session.
- Remaining register rows are coherent with their slots: 2026-11-05 (Thursday) periods 1 and 2, and
  2026-11-06 (Friday) period 1.

## Residual Local Observations (not repository defects)

These are properties of the local dev database, not of migrations or committed seed data. They are
inputs to the reproducible-seed task and are recorded so the demo state is not mistaken for the
product state.

- `employees` contains two rows for Aya Mbeki in school 2 (ids 3 and 6).
- The only `academic_years` row (2026-2027) has `is_current = false`. Dashboards derive the displayed
  year from the current date, so this affects the academic-year screen rather than the dashboards.
- School 4 (Northgate Secondary) has one user and no students, an empty tenant.

No production database changes, no branch-protection changes. The changes are proposed on a feature
branch and merge only after required checks pass.
