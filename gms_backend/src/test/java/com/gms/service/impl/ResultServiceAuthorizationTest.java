package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.*;
import com.gms.repository.ResultRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentExamRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class ResultServiceAuthorizationTest {

    private static final Integer SCHOOL_A = 2;
    private static final Integer SCHOOL_B = 3;
    private static final Integer STUDENT_1 = 10;
    private static final Integer STUDENT_2 = 20;
    private static final Integer ADMIN_USER = 2;
    private static final Integer SUPERADMIN_USER = 1;
    private static final Integer STUDENT_EXAM_ID = 100;

    @Mock private StudentExamRepository studentExamRepository;
    @Mock private ResultRepository resultRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ResultServiceImpl resultService;

    private StudentExam studentExam;
    private Result result;

    @BeforeEach
    void setUp() {
        School schoolA = new School();
        schoolA.setId(SCHOOL_A);

        Student student1 = new Student();
        student1.setId(STUDENT_1);
        student1.setSchool(schoolA);
        student1.setFirstName("Student");
        student1.setLastName("One");

        studentExam = new StudentExam();
        studentExam.setId(STUDENT_EXAM_ID);
        studentExam.setStudent(student1);
        studentExam.setSchool(schoolA);

        result = new Result();
        result.setId(1);
        result.setStudentExam(studentExam);
        result.setSchool(schoolA);
        result.setStatus("1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // --- SEC-F1-02: Student ownership ---

    @Test
    @DisplayName("SEC-F1-02: Student can access their own student-exam result")
    void studentCanAccessOwnResult() {
        TenantContext.set(new TenantContext.Principal("student1", 50, SCHOOL_A, 10, Set.of("STUDENT")));

        User caller = new User();
        Student callerStudent = new Student();
        callerStudent.setId(STUDENT_1);
        caller.setStudent(callerStudent);

        when(studentExamRepository.findById(STUDENT_EXAM_ID)).thenReturn(Optional.of(studentExam));
        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(caller));
        when(resultRepository.findByStudentExam_Student_School_IdAndStudentExam_Id(SCHOOL_A, STUDENT_EXAM_ID))
                .thenReturn(Optional.of(result));

        ResponseEntity<List<Result>> response = resultService.getResultsForStudentExam(STUDENT_EXAM_ID);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("SEC-F1-02: Student cannot access another student's result (same school)")
    void studentCannotAccessAnotherStudentsResult() {
        TenantContext.set(new TenantContext.Principal("student2", 60, SCHOOL_A, 20, Set.of("STUDENT")));

        User caller = new User();
        Student callerStudent = new Student();
        callerStudent.setId(STUDENT_2);
        caller.setStudent(callerStudent);

        when(studentExamRepository.findById(STUDENT_EXAM_ID)).thenReturn(Optional.of(studentExam));
        when(userRepository.findByUsername("student2")).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> resultService.getResultsForStudentExam(STUDENT_EXAM_ID))
                .isInstanceOf(TenantAccessDeniedException.class)
                .hasMessage("Not authorized to access this student-exam's results");
    }

    @Test
    @DisplayName("SEC-F1-02: Cross-school access denied by tenant-scoped repository")
    void crossSchoolAccessDenied() {
        TenantContext.set(new TenantContext.Principal("student1", 50, SCHOOL_B, 10, Set.of("STUDENT")));

        when(studentExamRepository.findById(STUDENT_EXAM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resultService.getResultsForStudentExam(STUDENT_EXAM_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Student exam not found");
    }

    @Test
    @DisplayName("SEC-F1-02: Student with no profile cannot access results")
    void studentWithNoProfileDenied() {
        TenantContext.set(new TenantContext.Principal("student_no_profile", 70, SCHOOL_A, 30, Set.of("STUDENT")));

        User caller = new User();
        caller.setStudent(null);

        when(studentExamRepository.findById(STUDENT_EXAM_ID)).thenReturn(Optional.of(studentExam));
        when(userRepository.findByUsername("student_no_profile")).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> resultService.getResultsForStudentExam(STUDENT_EXAM_ID))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    // --- Administrative access ---

    @Test
    @DisplayName("SEC-F1-02: ADMIN can access any result in their school")
    void adminCanAccessAnyResultInSchool() {
        TenantContext.set(new TenantContext.Principal("admin1", ADMIN_USER, SCHOOL_A, 5, Set.of("ADMIN")));

        when(studentExamRepository.findById(STUDENT_EXAM_ID)).thenReturn(Optional.of(studentExam));
        when(resultRepository.findByStudentExam_Student_School_IdAndStudentExam_Id(SCHOOL_A, STUDENT_EXAM_ID))
                .thenReturn(Optional.of(result));

        ResponseEntity<List<Result>> response = resultService.getResultsForStudentExam(STUDENT_EXAM_ID);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @DisplayName("SEC-F1-02: TEACHER can access any result in their school")
    void teacherCanAccessAnyResultInSchool() {
        TenantContext.set(new TenantContext.Principal("teacher1", 9, SCHOOL_A, 15, Set.of("TEACHER")));

        when(studentExamRepository.findById(STUDENT_EXAM_ID)).thenReturn(Optional.of(studentExam));
        when(resultRepository.findByStudentExam_Student_School_IdAndStudentExam_Id(SCHOOL_A, STUDENT_EXAM_ID))
                .thenReturn(Optional.of(result));

        ResponseEntity<List<Result>> response = resultService.getResultsForStudentExam(STUDENT_EXAM_ID);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @DisplayName("SEC-F1-02: SUPERADMIN can access results across tenants")
    void superadminCanAccessResultsAcrossTenants() {
        TenantContext.set(new TenantContext.Principal("superadmin", SUPERADMIN_USER, 1, 1, Set.of("SUPERADMIN")));

        when(studentExamRepository.findById(STUDENT_EXAM_ID)).thenReturn(Optional.of(studentExam));
        when(resultRepository.findByStudentExam_Student_School_IdAndStudentExam_Id(any(), eq(STUDENT_EXAM_ID)))
                .thenReturn(Optional.of(result));

        ResponseEntity<List<Result>> response = resultService.getResultsForStudentExam(STUDENT_EXAM_ID);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // --- Invalid/missing IDs ---

    @Test
    @DisplayName("SEC-F1-02: Non-existent studentExamId returns 404")
    void nonExistentStudentExamIdReturns404() {
        TenantContext.set(new TenantContext.Principal("student1", 50, SCHOOL_A, 10, Set.of("STUDENT")));

        when(studentExamRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resultService.getResultsForStudentExam(999))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Student exam not found");
    }

    @Test
    @DisplayName("SEC-F1-02: Empty result set returns 200 with empty list")
    void emptyResultSetReturnsOkWithEmptyList() {
        TenantContext.set(new TenantContext.Principal("student1", 50, SCHOOL_A, 10, Set.of("STUDENT")));

        User caller = new User();
        Student callerStudent = new Student();
        callerStudent.setId(STUDENT_1);
        caller.setStudent(callerStudent);

        when(studentExamRepository.findById(STUDENT_EXAM_ID)).thenReturn(Optional.of(studentExam));
        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(caller));
        when(resultRepository.findByStudentExam_Student_School_IdAndStudentExam_Id(SCHOOL_A, STUDENT_EXAM_ID))
                .thenReturn(Optional.empty());

        ResponseEntity<List<Result>> response = resultService.getResultsForStudentExam(STUDENT_EXAM_ID);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEmpty();
    }
}
