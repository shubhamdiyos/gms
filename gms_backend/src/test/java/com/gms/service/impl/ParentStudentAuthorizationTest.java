package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.gms.model.entity.*;
import com.gms.repository.ParentRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class ParentStudentAuthorizationTest {

    private static final Integer SCHOOL_A = 2;
    private static final Integer SCHOOL_B = 3;
    private static final Integer PARENT_1 = 7;
    private static final Integer PARENT_2 = 8;
    private static final Integer STUDENT_1 = 10;
    private static final Integer STUDENT_2 = 20;
    private static final Integer ADMIN_USER = 2;
    private static final Integer SUPERADMIN_USER = 1;

    @Mock private ParentRepository parentRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ParentStudentServiceImpl parentStudentService;

    private Parent parent1;
    private Student student1;

    @BeforeEach
    void setUp() {
        School schoolA = new School();
        schoolA.setId(SCHOOL_A);

        Classroom classroom = new Classroom();
        classroom.setId(100);
        classroom.setSchool(schoolA);

        student1 = new Student();
        student1.setId(STUDENT_1);
        student1.setSchool(schoolA);
        student1.setClassroom(classroom);

        parent1 = new Parent();
        parent1.setId(PARENT_1);
        parent1.setFirstName("Parent");
        parent1.setLastName("One");
        parent1.setEmail("parent1@test.com");
        parent1.setStudents(new ArrayList<>(List.of(student1)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // --- SEC-F3-06: getStudentsForParent ---

    @Test
    @DisplayName("SEC-F3-06: Parent can see their own children")
    void parentCanSeeOwnChildren() {
        TenantContext.set(new TenantContext.Principal("parent1", 50, SCHOOL_A, 20, Set.of("PARENT")));

        User caller = new User();
        Parent callerParent = new Parent();
        callerParent.setId(PARENT_1);
        caller.setParent(callerParent);

        when(parentRepository.findById(PARENT_1)).thenReturn(Optional.of(parent1));
        when(userRepository.findByUsername("parent1")).thenReturn(Optional.of(caller));

        ResponseEntity<List<Student>> response = parentStudentService.getStudentsForParent(PARENT_1);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("SEC-F3-06: Parent cannot see another parent's children")
    void parentCannotSeeAnotherParentsChildren() {
        TenantContext.set(new TenantContext.Principal("parent2", 60, SCHOOL_A, 25, Set.of("PARENT")));

        User caller = new User();
        Parent callerParent = new Parent();
        callerParent.setId(PARENT_2);
        caller.setParent(callerParent);

        when(parentRepository.findById(PARENT_1)).thenReturn(Optional.of(parent1));
        when(userRepository.findByUsername("parent2")).thenReturn(Optional.of(caller));

        ResponseEntity<List<Student>> response = parentStudentService.getStudentsForParent(PARENT_1);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("SEC-F3-06: Parent from another school cannot see children (family in school A)")
    void crossSchoolParentAccessDenied() {
        TenantContext.set(new TenantContext.Principal("parent1", 50, SCHOOL_B, 20, Set.of("PARENT")));

        User caller = new User();
        Parent callerParent = new Parent();
        callerParent.setId(PARENT_1);
        caller.setParent(callerParent);

        when(parentRepository.findById(PARENT_1)).thenReturn(Optional.of(parent1));

        ResponseEntity<List<Student>> response = parentStudentService.getStudentsForParent(PARENT_1);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("SEC-F3-06: ADMIN can see any parent's children in their school")
    void adminCanSeeAnyParentChildren() {
        TenantContext.set(new TenantContext.Principal("admin1", ADMIN_USER, SCHOOL_A, 5, Set.of("ADMIN")));

        when(parentRepository.findById(PARENT_1)).thenReturn(Optional.of(parent1));

        ResponseEntity<List<Student>> response = parentStudentService.getStudentsForParent(PARENT_1);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("SEC-F3-06: SUPERADMIN can see any parent's children across tenants")
    void superadminCanSeeAnyParentAcrossTenants() {
        TenantContext.set(new TenantContext.Principal("superadmin", SUPERADMIN_USER, SCHOOL_A, 1, Set.of("SUPERADMIN")));

        when(parentRepository.findById(PARENT_1)).thenReturn(Optional.of(parent1));

        ResponseEntity<List<Student>> response = parentStudentService.getStudentsForParent(PARENT_1);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    // --- SEC-F3-06: getParentsForStudent ---

    @Test
    @DisplayName("SEC-F3-06: ADMIN can get parents for a student in their school")
    void adminCanGetParentsForStudent() {
        TenantContext.set(new TenantContext.Principal("admin1", ADMIN_USER, SCHOOL_A, 5, Set.of("ADMIN")));

        when(studentRepository.findById(STUDENT_1)).thenReturn(Optional.of(student1));
        when(parentRepository.findByStudents_School_IdAndStudents_Id(SCHOOL_A, STUDENT_1))
                .thenReturn(List.of(parent1));

        ResponseEntity<?> response = parentStudentService.getParentsForStudent(STUDENT_1);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @DisplayName("SEC-F3-06: Cross-school student access returns 404")
    void crossSchoolStudentAccessReturns404() {
        TenantContext.set(new TenantContext.Principal("admin1", ADMIN_USER, SCHOOL_B, 5, Set.of("ADMIN")));

        when(studentRepository.findById(STUDENT_1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parentStudentService.getParentsForStudent(STUDENT_1))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Student not found");
    }

    // --- SEC-F3-06: assignStudentsToParent ---

    @Test
    @DisplayName("SEC-F3-06: ADMIN cannot assign students from another school's family")
    void adminCannotAssignFromAnotherSchoolFamily() {
        School schoolB = new School();
        schoolB.setId(SCHOOL_B);
        Student studentInB = new Student();
        studentInB.setId(STUDENT_2);
        studentInB.setSchool(schoolB);

        Parent crossSchoolParent = new Parent();
        crossSchoolParent.setId(PARENT_2);
        crossSchoolParent.setStudents(new ArrayList<>(List.of(studentInB)));

        TenantContext.set(new TenantContext.Principal("admin1", ADMIN_USER, SCHOOL_A, 5, Set.of("ADMIN")));

        when(parentRepository.findById(PARENT_2)).thenReturn(Optional.of(crossSchoolParent));

        com.gms.model.request.ParentStudentRequest request = new com.gms.model.request.ParentStudentRequest();
        request.setParentId(PARENT_2);
        request.setStudentIds(List.of(STUDENT_1));

        ResponseEntity<?> response = parentStudentService.assignStudentsToParent(request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("SEC-F3-06: Non-existent parent returns 404")
    void nonExistentParentReturns404() {
        TenantContext.set(new TenantContext.Principal("admin1", ADMIN_USER, SCHOOL_A, 5, Set.of("ADMIN")));

        when(parentRepository.findById(999)).thenReturn(Optional.empty());

        com.gms.model.request.ParentStudentRequest request = new com.gms.model.request.ParentStudentRequest();
        request.setParentId(999);
        request.setStudentIds(List.of(STUDENT_1));

        assertThatThrownBy(() -> parentStudentService.assignStudentsToParent(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Parent not found");
    }

    // --- SEC-F3-06: removeStudentFromParent ---

    @Test
    @DisplayName("SEC-F3-06: ADMIN cannot remove student from another school's family")
    void adminCannotRemoveFromAnotherSchoolFamily() {
        School schoolB = new School();
        schoolB.setId(SCHOOL_B);
        Student studentInB = new Student();
        studentInB.setId(STUDENT_2);
        studentInB.setSchool(schoolB);

        Parent crossSchoolParent = new Parent();
        crossSchoolParent.setId(PARENT_2);
        crossSchoolParent.setStudents(new ArrayList<>(List.of(studentInB)));

        TenantContext.set(new TenantContext.Principal("admin1", ADMIN_USER, SCHOOL_A, 5, Set.of("ADMIN")));

        when(parentRepository.findById(PARENT_2)).thenReturn(Optional.of(crossSchoolParent));

        ResponseEntity<?> response = parentStudentService.removeStudentFromParent(PARENT_2, STUDENT_2);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("SEC-F3-06: Cross-school student removal returns 404")
    void crossSchoolStudentRemovalReturns404() {
        TenantContext.set(new TenantContext.Principal("admin1", ADMIN_USER, SCHOOL_A, 5, Set.of("ADMIN")));

        when(parentRepository.findById(PARENT_1)).thenReturn(Optional.of(parent1));
        when(studentRepository.findById(STUDENT_2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parentStudentService.removeStudentFromParent(PARENT_1, STUDENT_2))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Student not found");
    }
}
