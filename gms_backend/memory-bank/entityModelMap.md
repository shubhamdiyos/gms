## Entity Model Map

This document provides an overview of all entity models in the GMS system and their relationships.

### Core Entities

1. **School**
   - Represents an educational institution
   - Fields: id, schoolId, schoolName, schoolCode, address, phone, email, principalName, establishedYear, boardAffiliation, status, enabled
   - Relationships:
     - One-to-Many with User (school_id)
     - One-to-Many with Employee (school_id)
     - One-to-Many with Student (school_id)
     - One-to-Many with Classroom (school_id)
     - One-to-Many with Subject (school_id)
     - One-to-Many with Fee (school_id)
     - One-to-Many with Designation (school_id)

2. **User**
   - Represents a system user account
   - Fields: id, fullName, email, password, enabled, requirePasswordChange, roles, phoneNumber, userId, username, userStatus, passwordExpired, accountLocked, emailVerified, phoneVerified, lastLoginAt, loginAttempts
   - Relationships:
     - Many-to-One with School (school_id)
     - One-to-One with Employee (employee_id)
     - Self-referencing for createdBy and updatedBy

3. **Employee**
   - Represents a school employee
   - Fields: id, employeeId, fullName, firstName, lastName, email, phoneNumber, dateOfBirth, gender, address, emergencyContactName, emergencyContactPhone, dateOfJoining, employmentType, designation, department, salary, status
   - Relationships:
     - Many-to-One with School (school_id)
     - Self-referencing for createdBy and updatedBy

4. **Student**
   - Represents a school student
   - Fields: id, studentId, firstName, lastName, email, phoneNumber, dateOfBirth, gender, status
   - Relationships:
     - Many-to-One with School (school_id)
     - Many-to-One with Classroom (classroom_id)
     - Self-referencing for createdBy and updatedBy

5. **Classroom**
   - Represents a class or section in a school
   - Fields: id, name, grade, section, status
   - Relationships:
     - Many-to-One with School (school_id)
     - Self-referencing for createdBy and updatedBy

6. **Subject**
   - Represents an academic subject
   - Fields: id, subjectCode, subjectName, description, status
   - Relationships:
     - Many-to-One with School (school_id)
     - Self-referencing for createdBy and updatedBy

7. **Designation**
   - Represents job roles and hierarchy within schools
   - Fields: id, designationId, title, description, department, hierarchyLevel, minQualification, minExperience, isTeachingRole, isAdministrativeRole, maxPositions, currentCount, isActive, reportsToLevel
   - Relationships:
     - Many-to-One with School (school_id)
     - Self-referencing for createdBy and updatedBy

### Financial Entities

8. **Fee**
   - Represents a fee structure
   - Fields: id, feeType, amount, dueDate, academicYear
   - Relationships:
     - Many-to-One with School (school_id)
     - Self-referencing for createdBy and updatedBy

9. **StudentFee**
   - Represents a student's fee assignment
   - Fields: id, status, amountPaid
   - Relationships:
     - Many-to-One with Student (student_id)
     - Many-to-One with Fee (fee_id)
     - One-to-Many with FeePayment (student_fee_id)
     - Self-referencing for createdBy and updatedBy

10. **FeePayment**
    - Represents a payment made for a student fee
    - Fields: id, amount, paymentDate, paymentMethod, remarks
    - Relationships:
      - Many-to-One with StudentFee (student_fee_id)
      - Self-referencing for createdBy and updatedBy

### Academic Entities

11. **Attendance**
    - Represents student attendance records
    - Fields: id, date, status
    - Relationships:
      - Many-to-One with Student (student_id)
      - Many-to-One with Classroom (classroom_id)
      - Self-referencing for createdBy and updatedBy

12. **TeacherAssignment**
    - Represents assignment of teachers to subjects and classes
    - Fields: id, academicYear
    - Relationships:
      - Many-to-One with Employee (employee_id)
      - Many-to-One with Subject (subject_id)
      - Many-to-One with Classroom (classroom_id)
      - Self-referencing for createdBy and updatedBy

13. **Timetable**
    - Represents class timetables
    - Fields: id, academicYear, effectiveDate
    - Relationships:
      - Many-to-One with Classroom (classroom_id)
      - One-to-Many with TimetableSlot (timetable_id)
      - Self-referencing for createdBy and updatedBy

14. **TimetableSlot**
    - Represents individual time slots in a timetable
    - Fields: id, dayOfWeek, startTime, endTime
    - Relationships:
      - Many-to-One with Timetable (timetable_id)
      - Many-to-One with Subject (subject_id)
      - Many-to-One with Employee (employee_id)
      - Self-referencing for createdBy and updatedBy

### Base Entity

15. **BaseAuditEntity**
    - Abstract base class providing audit functionality
    - Fields: createdBy, updatedBy, version, createdOn, updatedOn
    - All other entities extend this class