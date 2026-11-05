package com.gms.service.impl;

import com.gms.BasePostgresIntegrationTest;
import com.gms.exception.NotFoundException;
import com.gms.model.entity.*;
import com.gms.model.request.AttendanceItem;
import com.gms.model.request.BulkAttendanceRequest;
import com.gms.repository.*;
import com.gms.security.TenantContext;
import com.gms.service.AttendanceService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * R4.4 — PostgreSQL-backed attendance integrity verification.
 *
 * Tests run against a real Testcontainers PostgreSQL database to verify:
 * - Transaction atomicity of bulk attendance operations (ATT-06 fix)
 * - Database constraint enforcement (uq_attendance_student_slot_date)
 * - Concurrent submission handling
 * - Cross-school isolation
 */
class AttendancePostgresIntegrationTest extends BasePostgresIntegrationTest {

    @Autowired private AttendanceService attendanceService;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private ClassroomRepository classroomRepository;
    @Autowired private TimetableRepository timetableRepository;
    @Autowired private TimetableSlotRepository timetableSlotRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private TeacherAssignmentRepository teacherAssignmentRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    private School school1;
    private School school2;
    private Classroom classroom1;
    private Classroom classroom2;
    private AcademicYear academicYear;
    private Subject subject;
    private Employee teacherEmployee;
    private User teacher;
    private Timetable timetable1;
    private TimetableSlot slot1;
    private Student student1;
    private Student student2;
    private Student student3; // different classroom
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                "TRUNCATE TABLE attendances, timetable_slots, teacher_assignments, timetables, " +
                "students, classrooms, subjects, academic_years, user_roles, employees, users, " +
                "parents, schools CASCADE").executeUpdate();
            entityManager.flush();
        });

        // A Monday, matching the MONDAY slot created below.
        testDate = LocalDate.of(2026, 11, 2);

        school1 = transactionTemplate.execute(status -> {
            School s = new School();
            s.setSchoolName("Test School 1");
            s.setSchoolCode("TS001");
            s.setAddress("Test Address 1");
            s.setPhone("1234567890");
            s.setEmail("school1@test.com");
            return schoolRepository.save(s);
        });

        school2 = transactionTemplate.execute(status -> {
            School s = new School();
            s.setSchoolName("Test School 2");
            s.setSchoolCode("TS002");
            s.setAddress("Test Address 2");
            s.setPhone("0987654321");
            s.setEmail("school2@test.com");
            return schoolRepository.save(s);
        });

        classroom1 = transactionTemplate.execute(status -> {
            Classroom c = new Classroom();
            c.setName("Class 10A");
            c.setGrade("10");
            c.setSection("A");
            c.setSchool(school1);
            return classroomRepository.save(c);
        });

        classroom2 = transactionTemplate.execute(status -> {
            Classroom c = new Classroom();
            c.setName("Class 10B");
            c.setGrade("10");
            c.setSection("B");
            c.setSchool(school1);
            return classroomRepository.save(c);
        });

        academicYear = transactionTemplate.execute(status -> {
            AcademicYear ay = new AcademicYear();
            ay.setSchool(school1);
            ay.setName("2026-2027");
            ay.setStartDate(LocalDate.of(2026, 4, 1));
            ay.setEndDate(LocalDate.of(2027, 3, 31));
            ay.setCurrent(true);
            return academicYearRepository.save(ay);
        });

        subject = transactionTemplate.execute(status -> {
            Subject sub = new Subject();
            sub.setSchool(school1);
            sub.setName("Mathematics");
            sub.setCode("MATH101");
            return subjectRepository.save(sub);
        });

        teacherEmployee = transactionTemplate.execute(status -> {
            Employee emp = new Employee();
            emp.setSchool(school1);
            emp.setEmployeeId("EMP001");
            emp.setFirstName("Teacher");
            emp.setLastName("One");
            emp.setEmail("teacher1@test.com");
            emp.setEmployeeStatus("1");
            return employeeRepository.save(emp);
        });

        teacher = transactionTemplate.execute(status -> {
            User u = new User();
            u.setSchool(school1);
            u.setFullName("Teacher One");
            u.setEmail("teacher1@test.com");
            u.setPassword("encoded-password");
            u.setUsername("teacher1");
            u.setUserId("USR001");
            u.setUserStatus("1");
            u.setEnabled(true);
            u.setRoles(Set.of("TEACHER"));
            u.setEmployee(teacherEmployee);
            return userRepository.save(u);
        });

        TeacherAssignment assignment = transactionTemplate.execute(status -> {
            TeacherAssignment ta = new TeacherAssignment();
            ta.setSchool(school1);
            ta.setTeacher(teacherEmployee);
            ta.setSubject(subject);
            ta.setClassroom(classroom1);
            ta.setAcademicYear("2026-2027");
            return teacherAssignmentRepository.save(ta);
        });

        timetable1 = transactionTemplate.execute(status -> {
            Timetable tt = new Timetable();
            tt.setSchool(school1);
            tt.setClassroom(classroom1);
            tt.setAcademicYear("2026-2027");
            return timetableRepository.save(tt);
        });

        slot1 = transactionTemplate.execute(status -> {
            TimetableSlot slot = new TimetableSlot();
            slot.setSchool(school1);
            slot.setTimetable(timetable1);
            slot.setDayOfWeek("MONDAY");
            slot.setPeriod(1);
            slot.setAssignment(assignment);
            return timetableSlotRepository.save(slot);
        });

        student1 = transactionTemplate.execute(status -> {
            Student s = new Student();
            s.setFirstName("Student");
            s.setLastName("One");
            s.setSchool(school1);
            s.setClassroom(classroom1);
            s.setStudentId("STD001");
            return studentRepository.save(s);
        });

        student2 = transactionTemplate.execute(status -> {
            Student s = new Student();
            s.setFirstName("Student");
            s.setLastName("Two");
            s.setSchool(school1);
            s.setClassroom(classroom1);
            s.setStudentId("STD002");
            return studentRepository.save(s);
        });

        student3 = transactionTemplate.execute(status -> {
            Student s = new Student();
            s.setFirstName("Student");
            s.setLastName("Three");
            s.setSchool(school1);
            s.setClassroom(classroom2); // Different classroom
            s.setStudentId("STD003");
            return studentRepository.save(s);
        });
    }

    @Test
    @DisplayName("R4.4-01: All-valid bulk submission commits all expected records")
    void allValidBulkSubmission_commitsAllRecords() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacherEmployee.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school1.getId());

            BulkAttendanceRequest request = new BulkAttendanceRequest();
            request.setTimetableSlotId(slot1.getId());
            request.setDate(testDate);
            request.setAttendance(List.of(
                    createAttendanceItem(student1.getId(), "PRESENT"),
                    createAttendanceItem(student2.getId(), "PRESENT")
            ));

            ResponseEntity<?> response = attendanceService.recordBulkAttendance(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);

            List<Attendance> attendances = transactionTemplate.execute(status ->
                    attendanceRepository.findAll());
            assertThat(attendances).hasSize(2);
            assertThat(attendances)
                    .extracting(a -> a.getStudent().getId())
                    .containsExactlyInAnyOrder(student1.getId(), student2.getId());
        }
    }

    @Test
    @DisplayName("R4.4-02: Classroom mismatch results in zero records persisted")
    void classroomMismatch_zeroRecordsPersisted() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacherEmployee.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school1.getId());

            BulkAttendanceRequest request = new BulkAttendanceRequest();
            request.setTimetableSlotId(slot1.getId());
            request.setDate(testDate);
            request.setAttendance(List.of(
                    createAttendanceItem(student1.getId(), "PRESENT"),
                    createAttendanceItem(student2.getId(), "PRESENT"),
                    createAttendanceItem(student3.getId(), "PRESENT") // Wrong classroom
            ));

            assertThatThrownBy(() -> attendanceService.recordBulkAttendance(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not belong to the classroom");

            List<Attendance> attendances = transactionTemplate.execute(status ->
                    attendanceRepository.findAll());
            assertThat(attendances).isEmpty();
        }
    }

    @Test
    @DisplayName("R4.4-03: Missing student results in zero records persisted")
    void missingStudent_zeroRecordsPersisted() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacherEmployee.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school1.getId());

            BulkAttendanceRequest request = new BulkAttendanceRequest();
            request.setTimetableSlotId(slot1.getId());
            request.setDate(testDate);
            request.setAttendance(List.of(
                    createAttendanceItem(student1.getId(), "PRESENT"),
                    createAttendanceItem(99999, "PRESENT") // Non-existent student
            ));

            assertThatThrownBy(() -> attendanceService.recordBulkAttendance(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Student not found");

            List<Attendance> attendances = transactionTemplate.execute(status ->
                    attendanceRepository.findAll());
            assertThat(attendances).isEmpty();
        }
    }

    @Test
    @DisplayName("R4.4-04: Existing duplicates are skipped without creating duplicate records")
    void existingDuplicates_skippedWithoutCreatingDuplicates() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacherEmployee.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school1.getId());

            BulkAttendanceRequest request1 = new BulkAttendanceRequest();
            request1.setTimetableSlotId(slot1.getId());
            request1.setDate(testDate);
            request1.setAttendance(List.of(
                    createAttendanceItem(student1.getId(), "PRESENT"),
                    createAttendanceItem(student2.getId(), "PRESENT")
            ));
            attendanceService.recordBulkAttendance(request1);

            BulkAttendanceRequest request2 = new BulkAttendanceRequest();
            request2.setTimetableSlotId(slot1.getId());
            request2.setDate(testDate);
            request2.setAttendance(List.of(
                    createAttendanceItem(student1.getId(), "PRESENT"),
                    createAttendanceItem(student2.getId(), "ABSENT")
            ));
            ResponseEntity<?> response2 = attendanceService.recordBulkAttendance(request2);

            assertThat(response2.getStatusCode().value()).isEqualTo(200);

            List<Attendance> attendances = transactionTemplate.execute(status ->
                    attendanceRepository.findAll());
            assertThat(attendances).hasSize(2);
        }
    }

    @Test
    @DisplayName("R4.4-05: Retry after lost response does not create duplicate records")
    void retryAfterLostResponse_noDuplicates() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacherEmployee.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school1.getId());

            BulkAttendanceRequest request = new BulkAttendanceRequest();
            request.setTimetableSlotId(slot1.getId());
            request.setDate(testDate);
            request.setAttendance(List.of(
                    createAttendanceItem(student1.getId(), "PRESENT"),
                    createAttendanceItem(student2.getId(), "PRESENT")
            ));

            attendanceService.recordBulkAttendance(request);
            attendanceService.recordBulkAttendance(request); // Simulated retry

            List<Attendance> attendances = transactionTemplate.execute(status ->
                    attendanceRepository.findAll());
            assertThat(attendances).hasSize(2);
        }
    }

    @Test
    @DisplayName("R4.7-01: Concurrent submissions - exactly one record persists, all threads handled gracefully")
    void concurrentSubmissions_respectUniqueConstraint() throws InterruptedException {
        // R4.7 fix: Establish TenantContext independently inside each worker thread
        // instead of using MockedStatic which is thread-local to the main test thread.
        
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        
        List<ResponseEntity<?>> responses = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        List<Throwable> exceptions = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger badRequestCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    
                    // R4.7: Set up TenantContext for this worker thread
                    TenantContext.set(new TenantContext.Principal(
                            teacher.getUsername(),
                            teacher.getId(),
                            school1.getId(),
                            teacherEmployee.getId(),
                            Set.of("TEACHER")
                    ));
                    
                    BulkAttendanceRequest request = new BulkAttendanceRequest();
                    request.setTimetableSlotId(slot1.getId());
                    request.setDate(testDate);
                    request.setAttendance(List.of(
                            createAttendanceItem(student1.getId(), "PRESENT")
                    ));
                    
                    ResponseEntity<?> response = attendanceService.recordBulkAttendance(request);
                    responses.add(response);
                    
                    // Track actual HTTP status codes
                    if (response.getStatusCode().value() == 200) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode().value() == 400) {
                        badRequestCount.incrementAndGet();
                    }
                } catch (DataIntegrityViolationException e) {
                    exceptions.add(e);
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    // R4.7: Clear TenantContext to prevent thread pollution
                    TenantContext.clear();
                    completionLatch.countDown();
                }
            });
        }

        latch.countDown();
        completionLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // CRITICAL: Verify exactly one database record exists
        List<Attendance> attendances = transactionTemplate.execute(status ->
                attendanceRepository.findAll().stream()
                        .filter(a -> a.getStudent().getId().equals(student1.getId())
                                && a.getTimetableSlot().getId().equals(slot1.getId())
                                && a.getDate().equals(testDate))
                        .toList());
        
        // Log actual behavior for analysis
        System.out.println("Concurrent test results (R4.7):");
        System.out.println("  Total threads: " + threadCount);
        System.out.println("  HTTP 200 responses: " + successCount.get());
        System.out.println("  HTTP 400 responses: " + badRequestCount.get());
        System.out.println("  DataIntegrityViolation exceptions: " + conflictCount.get());
        System.out.println("  Database records persisted: " + attendances.size());
        System.out.println("  Exceptions captured: " + exceptions.size());
        
        // Verify exactly one record persisted (database constraint enforced)
        assertThat(attendances)
                .as("Exactly one attendance record must exist after concurrent submissions")
                .hasSize(1);
        
        // Verify all threads completed
        int totalCompleted = successCount.get() + badRequestCount.get() + conflictCount.get();
        assertThat(totalCompleted)
                .as("All threads must complete (success, bad request, or conflict)")
                .isEqualTo(threadCount);
        
        // Verify no unexpected exceptions (all should be DataIntegrityViolationException if any)
        assertThat(exceptions)
                .as("No unexpected exceptions should occur")
                .allMatch(e -> e instanceof DataIntegrityViolationException);
        
        // Verify at least one success (the first thread should succeed)
        assertThat(successCount.get())
                .as("At least one thread must succeed in creating the attendance record")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("R4.4-07: Cross-school attendance isolation is enforced")
    void crossSchoolAttendance_isolationEnforced() {
        Student school2Student = transactionTemplate.execute(status -> {
            Classroom school2Classroom = new Classroom();
            school2Classroom.setName("Class 10C");
            school2Classroom.setGrade("10");
            school2Classroom.setSection("C");
            school2Classroom.setSchool(school2);
            school2Classroom = classroomRepository.save(school2Classroom);

            Student s = new Student();
            s.setFirstName("School2");
            s.setLastName("Student");
            s.setSchool(school2);
            s.setClassroom(school2Classroom);
            s.setStudentId("STD_S2_001");
            return studentRepository.save(s);
        });

        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacherEmployee.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school1.getId());

            BulkAttendanceRequest request = new BulkAttendanceRequest();
            request.setTimetableSlotId(slot1.getId());
            request.setDate(testDate);
            request.setAttendance(List.of(
                    createAttendanceItem(school2Student.getId(), "PRESENT")
            ));

            assertThatThrownBy(() -> attendanceService.recordBulkAttendance(request))
                    .isInstanceOf(NotFoundException.class);

            List<Attendance> attendances = transactionTemplate.execute(status ->
                    attendanceRepository.findAll());
            assertThat(attendances).isEmpty();
        }
    }

    @Test
    @DisplayName("R4.4-08: Direct duplicate insertion is rejected by PostgreSQL")
    void directDuplicateInsertion_rejectedByPostgreSQL() {
        transactionTemplate.executeWithoutResult(status -> {
            Attendance a1 = new Attendance();
            a1.setSchool(school1);
            a1.setStudent(student1);
            a1.setTimetableSlot(slot1);
            a1.setDate(testDate);
            a1.setStatus("PRESENT");
            a1.setCreatedBy(teacher);
            a1.setUpdatedBy(teacher);
            attendanceRepository.save(a1);
            attendanceRepository.flush();
        });

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            Attendance a2 = new Attendance();
            a2.setSchool(school1);
            a2.setStudent(student1);
            a2.setTimetableSlot(slot1);
            a2.setDate(testDate);
            a2.setStatus("ABSENT");
            a2.setCreatedBy(teacher);
            a2.setUpdatedBy(teacher);
            attendanceRepository.save(a2);
            attendanceRepository.flush();
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("R4.4-09: Constraint violation via service produces correct response")
    void constraintViolationViaService_handledGracefully() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacherEmployee.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school1.getId());

            BulkAttendanceRequest request = new BulkAttendanceRequest();
            request.setTimetableSlotId(slot1.getId());
            request.setDate(testDate);
            request.setAttendance(List.of(
                    createAttendanceItem(student1.getId(), "PRESENT")
            ));

            ResponseEntity<?> first = attendanceService.recordBulkAttendance(request);
            assertThat(first.getStatusCode().value()).isEqualTo(200);

            ResponseEntity<?> second = attendanceService.recordBulkAttendance(request);
            assertThat(second.getStatusCode().value()).isEqualTo(200);

            List<Attendance> attendances = transactionTemplate.execute(status ->
                    attendanceRepository.findAll().stream()
                            .filter(a -> a.getStudent().getId().equals(student1.getId())
                                    && a.getTimetableSlot().getId().equals(slot1.getId())
                                    && a.getDate().equals(testDate))
                            .toList());
            assertThat(attendances).hasSize(1);
        }
    }

    private AttendanceItem createAttendanceItem(Integer studentId, String status) {
        AttendanceItem item = new AttendanceItem();
        item.setStudentId(studentId);
        item.setStatus(status);
        item.setRemarks("Test remark");
        return item;
    }
}
