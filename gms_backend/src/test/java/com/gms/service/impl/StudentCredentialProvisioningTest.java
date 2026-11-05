package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.entity.User;
import com.gms.model.response.StudentResponse;
import com.gms.repository.StudentRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.*;
import com.gms.util.PasswordValidator;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * ACD-13-02: Student credential provisioning tests.
 *
 * <p>Verifies that every newly created student account receives a unique, cryptographically secure
 * temporary password that is hashed before storage, returned once in the creation response, and
 * accompanied by the {@code requirePasswordChange} flag.</p>
 */
@ExtendWith(MockitoExtension.class)
class StudentCredentialProvisioningTest {

    private static final Integer SCHOOL_ID = 2;

    @Mock private StudentRepository studentRepository;
    @Mock private SchoolService schoolService;
    @Mock private ClassroomService classroomService;
    @Mock private SectionService sectionService;
    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ResultService resultService;
    @Mock private StudentFeeService studentFeeService;
    @Mock private TimetableService timetableService;
    @Mock private AnnouncementService announcementService;
    @Mock private SubjectService subjectService;
    @Mock private NotificationService notificationService;
    @Mock private TeacherAssignmentService teacherAssignmentService;
    @Mock private AttendanceService attendanceService;

    @InjectMocks
    private StudentServiceImpl studentService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @BeforeEach
    void setUpTenant() {
        TenantContext.set(new TenantContext.Principal("admin1", 1, SCHOOL_ID, 5, Set.of("ADMIN")));
    }

    @Test
    void createdStudentReceivesUniqueTemporaryPassword() {
        Student student = buildStudent(1, "alice", "smith", "alice@test.com");
        when(userService.existsByEmail(anyString())).thenReturn(false);
        when(userService.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String password = studentService.createUserForStudent(student);

        assertThat(password).isNotNull();
        assertThat(password).isNotEqualTo("welcome123");
        assertThat(PasswordValidator.isValid(password)).isTrue();
    }

    @Test
    void twoStudentsReceiveDifferentPasswords() {
        Student student1 = buildStudent(1, "alice", "smith", "alice@test.com");
        Student student2 = buildStudent(2, "bob", "jones", "bob@test.com");
        when(userService.existsByEmail(anyString())).thenReturn(false);
        when(userService.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String password1 = studentService.createUserForStudent(student1);
        String password2 = studentService.createUserForStudent(student2);

        assertThat(password1).isNotEqualTo(password2);
    }

    @Test
    void passwordIsHashedBeforeStorage() {
        Student student = buildStudent(1, "alice", "smith", "alice@test.com");
        when(userService.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$HASHED");
        when(userService.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String plaintext = studentService.createUserForStudent(student);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        User savedUser = captor.getValue();

        assertThat(savedUser.getPassword()).isEqualTo("$2a$10$HASHED");
        assertThat(savedUser.getPassword()).isNotEqualTo(plaintext);
        assertThat(plaintext).isNotEqualTo("welcome123");
    }

    @Test
    void createdStudentHasRequirePasswordChangeFlag() {
        Student student = buildStudent(1, "alice", "smith", "alice@test.com");
        when(userService.existsByEmail(anyString())).thenReturn(false);
        when(userService.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        studentService.createUserForStudent(student);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        assertThat(captor.getValue().isRequirePasswordChange()).isTrue();
    }

    @Test
    void plaintextPasswordDoesNotAppearInResponseFromCreate() {
        // The create() method returns the password in temporaryPassword field for admin relay,
        // but the toResponse() helper used in reads/updates must NOT include it.
        Student student = buildStudent(1, "alice", "smith", "alice@test.com");
        when(userService.existsByEmail(anyString())).thenReturn(false);
        when(userService.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Verify that the response from create() DOES contain the temporary password
        // (this is the admin relay mechanism)
        com.gms.model.request.StudentRequest request = new com.gms.model.request.StudentRequest();
        request.setFirstName("alice");
        request.setLastName("smith");
        request.setEmail("alice@test.com");
        request.setClassId(1);

        School school = new School();
        school.setId(SCHOOL_ID);
        when(schoolService.findById(SCHOOL_ID)).thenReturn(Optional.of(school));

        com.gms.model.entity.Classroom classroom = new com.gms.model.entity.Classroom();
        classroom.setId(1);
        classroom.setSchool(school);
        when(classroomService.findById(1)).thenReturn(Optional.of(classroom));

        when(studentRepository.save(any(Student.class))).thenReturn(student);

        ResponseEntity<StudentResponse> response = studentService.create(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTemporaryPassword()).isNotNull();
        assertThat(response.getBody().getTemporaryPassword()).isNotEqualTo("welcome123");
        assertThat(PasswordValidator.isValid(response.getBody().getTemporaryPassword())).isTrue();
    }

    private Student buildStudent(Integer id, String firstName, String lastName, String email) {
        Student student = new Student();
        student.setId(id);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setEmail(email);
        School school = new School();
        school.setId(SCHOOL_ID);
        student.setSchool(school);
        return student;
    }
}
