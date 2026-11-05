package com.gms.service.impl;

import com.gms.BasePostgresIntegrationTest;
import com.gms.model.entity.Classroom;
import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.entity.User;
import com.gms.model.response.TimetableResponse;
import com.gms.repository.ClassroomRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.StudentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StudentTimetableEmptyContractTest extends BasePostgresIntegrationTest {

    @Autowired private StudentService studentService;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private ClassroomRepository classroomRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    private User studentUser;
    private Integer schoolId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "TRUNCATE TABLE user_roles, users, students, classrooms, schools CASCADE")
                    .executeUpdate();
            entityManager.flush();
        });

        transactionTemplate.executeWithoutResult(status -> {
            School school = new School();
            school.setSchoolName("Timetable Contract School");
            school.setSchoolCode("TC001");
            school.setAddress("Integration test address");
            school.setPhone("1234567890");
            school.setEmail("timetable@contract.test");
            school = schoolRepository.save(school);
            schoolId = school.getId();

            Classroom classroom = new Classroom();
            classroom.setSchool(school);
            classroom.setName("9-A");
            classroom.setGrade("9");
            classroom.setSection("A");
            classroom = classroomRepository.save(classroom);

            Student student = new Student();
            student.setSchool(school);
            student.setClassroom(classroom);
            student.setStudentId("TC-STD-001");
            student.setFirstName("Empty");
            student.setLastName("Schedule");
            student = studentRepository.save(student);

            User user = new User();
            user.setSchool(school);
            user.setFullName("Empty Schedule");
            user.setEmail("empty.schedule@contract.test");
            user.setUsername("empty.schedule");
            user.setUserId("EMPTY_SCHEDULE");
            user.setPassword("encoded-password");
            user.setRoles(Set.of("STUDENT"));
            user.setStudent(student);
            studentUser = userRepository.save(user);
        });
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A student whose classroom has no timetable yet gets an empty list, not a 500")
    void missingTimetableAnswersEmptyList() {
        TenantContext.set(new TenantContext.Principal(
                studentUser.getUsername(), studentUser.getId(), null,
                schoolId, Set.of("STUDENT")));

        List<TimetableResponse> timetable = studentService.getMyTimetable(studentUser.getUsername());

        assertThat(timetable).isEmpty();
    }
}
