package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gms.model.entity.Parent;
import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.entity.User;
import com.gms.model.response.ParentResponse;
import com.gms.repository.ParentRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.*;
import com.gms.util.PasswordValidator;
import java.util.List;
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
 * ACD-13-02: Parent credential provisioning tests.
 *
 * <p>Verifies that every newly created parent account receives a unique, cryptographically secure
 * temporary password that is hashed before storage, returned once in the creation response, and
 * accompanied by the {@code requirePasswordChange} flag.</p>
 */
@ExtendWith(MockitoExtension.class)
class ParentCredentialProvisioningTest {

    private static final Integer SCHOOL_ID = 2;

    @Mock private ParentRepository parentRepository;
    @Mock private StudentService studentService;
    @Mock private UserService userService;
    @Mock private SchoolService schoolService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AttendanceService attendanceService;
    @Mock private ResultService resultService;
    @Mock private StudentFeeService studentFeeService;
    @Mock private TimetableService timetableService;
    @Mock private AnnouncementService announcementService;
    @Mock private SubjectService subjectService;
    @Mock private NotificationService notificationService;
    @Mock private TeacherAssignmentService teacherAssignmentService;

    @InjectMocks
    private ParentServiceImpl parentService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @BeforeEach
    void setUpTenant() {
        TenantContext.set(new TenantContext.Principal("admin1", 1, SCHOOL_ID, 5, Set.of("ADMIN")));
    }

    @Test
    void createdParentReceivesUniqueTemporaryPasswordInResponse() {
        Student student = buildStudent(1, "alice", "smith");
        when(studentService.findById(1)).thenReturn(Optional.of(student));
        when(parentRepository.save(any(Parent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.existsByEmail(anyString())).thenReturn(false);
        when(userService.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(100);
            return u;
        });

        com.gms.model.request.ParentRequest request = new com.gms.model.request.ParentRequest();
        request.setFirstName("Jane");
        request.setLastName("Smith");
        request.setEmail("jane@test.com");
        request.setStudentIds(List.of(1));

        ResponseEntity<ParentResponse> response = parentService.createParent(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTemporaryPassword()).isNotNull();
        assertThat(response.getBody().getTemporaryPassword()).isNotEqualTo("welcome123");
        assertThat(PasswordValidator.isValid(response.getBody().getTemporaryPassword())).isTrue();
    }

    @Test
    void twoParentsReceiveDifferentPasswords() {
        Student student1 = buildStudent(1, "alice", "smith");
        Student student2 = buildStudent(2, "bob", "jones");

        when(studentService.findById(1)).thenReturn(Optional.of(student1));
        when(studentService.findById(2)).thenReturn(Optional.of(student2));
        when(parentRepository.save(any(Parent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.existsByEmail(anyString())).thenReturn(false);
        when(userService.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(100);
            return u;
        });

        com.gms.model.request.ParentRequest request1 = new com.gms.model.request.ParentRequest();
        request1.setFirstName("Jane");
        request1.setLastName("Smith");
        request1.setEmail("jane@test.com");
        request1.setStudentIds(List.of(1));

        com.gms.model.request.ParentRequest request2 = new com.gms.model.request.ParentRequest();
        request2.setFirstName("Mary");
        request2.setLastName("Jones");
        request2.setEmail("mary@test.com");
        request2.setStudentIds(List.of(2));

        ResponseEntity<ParentResponse> response1 = parentService.createParent(request1);
        ResponseEntity<ParentResponse> response2 = parentService.createParent(request2);

        String password1 = response1.getBody().getTemporaryPassword();
        String password2 = response2.getBody().getTemporaryPassword();

        assertThat(password1).isNotEqualTo(password2);
    }

    @Test
    void parentPasswordIsHashedBeforeStorage() {
        Student student = buildStudent(1, "alice", "smith");
        when(studentService.findById(1)).thenReturn(Optional.of(student));
        when(parentRepository.save(any(Parent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$HASHED");
        when(userService.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(100);
            return u;
        });

        com.gms.model.request.ParentRequest request = new com.gms.model.request.ParentRequest();
        request.setFirstName("Jane");
        request.setLastName("Smith");
        request.setEmail("jane@test.com");
        request.setStudentIds(List.of(1));

        parentService.createParent(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        User savedUser = captor.getValue();

        assertThat(savedUser.getPassword()).isEqualTo("$2a$10$HASHED");
        assertThat(savedUser.getPassword()).doesNotContain("welcome123");
    }

    @Test
    void createdParentHasRequirePasswordChangeFlag() {
        Student student = buildStudent(1, "alice", "smith");
        when(studentService.findById(1)).thenReturn(Optional.of(student));
        when(parentRepository.save(any(Parent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userService.existsByEmail(anyString())).thenReturn(false);
        when(userService.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(100);
            return u;
        });

        com.gms.model.request.ParentRequest request = new com.gms.model.request.ParentRequest();
        request.setFirstName("Jane");
        request.setLastName("Smith");
        request.setEmail("jane@test.com");
        request.setStudentIds(List.of(1));

        parentService.createParent(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        assertThat(captor.getValue().isRequirePasswordChange()).isTrue();
    }

    private Student buildStudent(Integer id, String firstName, String lastName) {
        Student student = new Student();
        student.setId(id);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setEmail(firstName + "@test.com");
        School school = new School();
        school.setId(SCHOOL_ID);
        student.setSchool(school);
        com.gms.model.entity.Classroom classroom = new com.gms.model.entity.Classroom();
        classroom.setId(100);
        classroom.setSchool(school);
        student.setClassroom(classroom);
        return student;
    }
}
