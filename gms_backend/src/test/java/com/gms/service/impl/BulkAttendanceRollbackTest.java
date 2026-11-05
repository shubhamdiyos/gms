package com.gms.service.impl;

import com.gms.BasePostgresIntegrationTest;
import com.gms.model.entity.*;
import com.gms.model.request.AttendanceItem;
import com.gms.model.request.BulkAttendanceRequest;
import com.gms.repository.*;
import com.gms.security.TenantContext;
import com.gms.service.AttendanceService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage I: Transaction rollback after partial write verification.
 *
 * Tests that a bulk attendance transaction rolls back completely when a failure
 * occurs after some inserts have executed. Uses a temporary PostgreSQL trigger
 * to force a controlled failure at the database level.
 *
 * Expected: zero records persist after rollback, even though some inserts succeeded.
 */
class BulkAttendanceRollbackTest extends BasePostgresIntegrationTest {

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
    private Student student1;
    private Student student2;
    private Student student3;
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

        testDate = LocalDate.of(2026, 11, 3);

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

        student1 = transactionTemplate.execute(status -> {
            Student st = new Student();
            st.setFirstName("Student");
            st.setLastName("One");
            st.setSchool(school);
            st.setClassroom(classroom);
            st.setStudentId("STD001");
            return studentRepository.save(st);
        });

        student2 = transactionTemplate.execute(status -> {
            Student st = new Student();
            st.setFirstName("Student");
            st.setLastName("Two");
            st.setSchool(school);
            st.setClassroom(classroom);
            st.setStudentId("STD002");
            return studentRepository.save(st);
        });

        student3 = transactionTemplate.execute(status -> {
            Student st = new Student();
            st.setFirstName("Student");
            st.setLastName("Three");
            st.setSchool(school);
            st.setClassroom(classroom);
            st.setStudentId("STD003");
            return studentRepository.save(st);
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DROP TRIGGER IF EXISTS force_failure_trigger ON attendances").executeUpdate();
            entityManager.createNativeQuery("DROP FUNCTION IF EXISTS force_failure_after_second_insert()").executeUpdate();
        });
    }

    @Test
    @DisplayName("Stage I-02: Bulk attendance transaction rolls back completely after partial write failure")
    void bulkAttendance_partialFailure_completeRollback() {
        // Set up TenantContext
        TenantContext.set(new TenantContext.Principal(
                teacher.getUsername(),
                teacher.getId(),
                school.getId(),
                teacherEmployee.getId(),
                Set.of("TEACHER")
        ));

        try {
            // Create a temporary trigger that fails on the 3rd insert using session variable
            transactionTemplate.executeWithoutResult(status -> {
                entityManager.createNativeQuery("""
                    CREATE OR REPLACE FUNCTION force_failure_after_second_insert()
                    RETURNS TRIGGER AS $$
                    BEGIN
                        -- Use a session variable to track insert count within transaction
                        IF current_setting('app.insert_count', true) IS NULL THEN
                            PERFORM set_config('app.insert_count', '1', false);
                        ELSE
                            PERFORM set_config('app.insert_count', 
                                (current_setting('app.insert_count')::int + 1)::text, false);
                        END IF;
                        
                        -- Fail on 3rd insert
                        IF current_setting('app.insert_count')::int >= 3 THEN
                            RAISE EXCEPTION 'Forced failure after second insert (Stage I test)';
                        END IF;
                        
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql;
                    """).executeUpdate();

                entityManager.createNativeQuery("""
                    CREATE TRIGGER force_failure_trigger
                    BEFORE INSERT ON attendances
                    FOR EACH ROW
                    EXECUTE FUNCTION force_failure_after_second_insert();
                    """).executeUpdate();
            });

            // Build bulk request with 3 students
            BulkAttendanceRequest request = new BulkAttendanceRequest();
            request.setTimetableSlotId(slot.getId());
            request.setDate(testDate);

            List<AttendanceItem> entries = List.of(
                    createEntry(student1.getId(), "PRESENT"),
                    createEntry(student2.getId(), "PRESENT"),
                    createEntry(student3.getId(), "PRESENT")  // This should trigger the failure
            );
            request.setAttendance(entries);

            // Execute and expect exception
            assertThatThrownBy(() -> attendanceService.recordBulkAttendance(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Forced failure after second insert");

            // Verify ZERO records persisted (complete rollback)
            List<Attendance> attendances = transactionTemplate.execute(status ->
                    attendanceRepository.findAll().stream()
                            .filter(a -> a.getDate().equals(testDate)
                                    && a.getTimetableSlot().getId().equals(slot.getId()))
                            .toList());

            System.out.println("Bulk attendance rollback test results (Stage I-02):");
            System.out.println("  Requested inserts: 3");
            System.out.println("  Database records persisted: " + attendances.size());

            assertThat(attendances)
                    .as("Zero attendance records must persist after transaction rollback")
                    .isEmpty();

        } finally {
            TenantContext.clear();
        }
    }

    private AttendanceItem createEntry(Integer studentId, String status) {
        AttendanceItem entry = new AttendanceItem();
        entry.setStudentId(studentId);
        entry.setStatus(status);
        return entry;
    }
}
