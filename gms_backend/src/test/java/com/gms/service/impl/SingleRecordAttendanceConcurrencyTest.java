package com.gms.service.impl;

import com.gms.BasePostgresIntegrationTest;
import com.gms.model.entity.*;
import com.gms.model.request.AttendanceRequest;
import com.gms.model.response.AttendanceResponse;
import com.gms.repository.*;
import com.gms.security.TenantContext;
import com.gms.service.AttendanceService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage H: Single-record attendance concurrency verification.
 *
 * Tests that the atomic upsert fix (replacing check-then-act) correctly handles
 * concurrent identical single-record submissions.
 *
 * Expected: exactly one record persists; duplicate submissions return 400 Bad Request.
 */
class SingleRecordAttendanceConcurrencyTest extends BasePostgresIntegrationTest {

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

    private School school;
    private Classroom classroom;
    private TimetableSlot slot;
    private Student student;
    private Employee teacherEmployee;
    private User teacher;
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

        school = transactionTemplate.execute(status -> {
            School s = new School();
            s.setSchoolName("Test School");
            s.setSchoolCode("TS001");
            s.setAddress("Test Address");
            s.setPhone("1234567890");
            s.setEmail("school@test.com");
            return schoolRepository.save(s);
        });

        classroom = transactionTemplate.execute(status -> {
            Classroom c = new Classroom();
            c.setName("Class 10A");
            c.setGrade("10");
            c.setSection("A");
            c.setSchool(school);
            return classroomRepository.save(c);
        });

        AcademicYear academicYear = transactionTemplate.execute(status -> {
            AcademicYear ay = new AcademicYear();
            ay.setSchool(school);
            ay.setName("2026-2027");
            ay.setStartDate(LocalDate.of(2026, 4, 1));
            ay.setEndDate(LocalDate.of(2027, 3, 31));
            ay.setCurrent(true);
            return academicYearRepository.save(ay);
        });

        Subject subject = transactionTemplate.execute(status -> {
            Subject sub = new Subject();
            sub.setSchool(school);
            sub.setName("Mathematics");
            sub.setCode("MATH101");
            return subjectRepository.save(sub);
        });

        teacherEmployee = transactionTemplate.execute(status -> {
            Employee emp = new Employee();
            emp.setSchool(school);
            emp.setEmployeeId("EMP001");
            emp.setFirstName("Teacher");
            emp.setLastName("One");
            emp.setEmail("teacher@test.com");
            emp.setEmployeeStatus("1");
            return employeeRepository.save(emp);
        });

        teacher = transactionTemplate.execute(status -> {
            User u = new User();
            u.setSchool(school);
            u.setFullName("Teacher One");
            u.setEmail("teacher@test.com");
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
            ta.setSchool(school);
            ta.setTeacher(teacherEmployee);
            ta.setSubject(subject);
            ta.setClassroom(classroom);
            ta.setAcademicYear("2026-2027");
            return teacherAssignmentRepository.save(ta);
        });

        Timetable timetable = transactionTemplate.execute(status -> {
            Timetable tt = new Timetable();
            tt.setSchool(school);
            tt.setClassroom(classroom);
            tt.setAcademicYear("2026-2027");
            return timetableRepository.save(tt);
        });

        slot = transactionTemplate.execute(status -> {
            TimetableSlot s = new TimetableSlot();
            s.setSchool(school);
            s.setTimetable(timetable);
            s.setDayOfWeek("MONDAY");
            s.setPeriod(1);
            s.setAssignment(assignment);
            return timetableSlotRepository.save(s);
        });

        student = transactionTemplate.execute(status -> {
            Student st = new Student();
            st.setFirstName("Student");
            st.setLastName("One");
            st.setSchool(school);
            st.setClassroom(classroom);
            st.setStudentId("STD001");
            return studentRepository.save(st);
        });
    }

    @Test
    @DisplayName("Stage H-01: Concurrent identical single-record submissions produce exactly one record")
    void concurrentSingleRecordSubmissions_exactlyOneRecordPersists() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    latch.await();

                    // Set up TenantContext for this worker thread
                    TenantContext.set(new TenantContext.Principal(
                            teacher.getUsername(),
                            teacher.getId(),
                            school.getId(),
                            teacherEmployee.getId(),
                            Set.of("TEACHER")
                    ));

                    AttendanceRequest request = new AttendanceRequest();
                    request.setStudentId(student.getId());
                    request.setTimetableSlotId(slot.getId());
                    request.setDate(testDate);
                    request.setStatus("PRESENT");
                    request.setRemarks("Test");

                    ResponseEntity<AttendanceResponse> response = attendanceService.recordAttendance(request);

                    if (response.getStatusCode().value() == 200) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode().value() == 400) {
                        duplicateCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Unexpected exception — should not happen with atomic upsert
                    System.err.println("Unexpected exception: " + e.getMessage());
                } finally {
                    TenantContext.clear();
                    completionLatch.countDown();
                }
            });
        }

        latch.countDown();
        completionLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // Verify exactly one record in database
        List<Attendance> attendances = transactionTemplate.execute(status ->
                attendanceRepository.findAll().stream()
                        .filter(a -> a.getStudent().getId().equals(student.getId())
                                && a.getTimetableSlot().getId().equals(slot.getId())
                                && a.getDate().equals(testDate))
                        .toList());

        System.out.println("Single-record concurrency test results (Stage H-01):");
        System.out.println("  Total threads: " + threadCount);
        System.out.println("  HTTP 200 responses: " + successCount.get());
        System.out.println("  HTTP 400 responses (duplicates): " + duplicateCount.get());
        System.out.println("  Database records persisted: " + attendances.size());

        // CRITICAL: Exactly one record must exist
        assertThat(attendances)
                .as("Exactly one attendance record must exist after concurrent submissions")
                .hasSize(1);

        // Exactly one thread should succeed, rest should get 400
        assertThat(successCount.get())
                .as("Exactly one thread should succeed")
                .isEqualTo(1);

        assertThat(duplicateCount.get())
                .as("Remaining threads should receive 400 Bad Request")
                .isEqualTo(threadCount - 1);
    }
}
