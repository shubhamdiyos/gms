package com.gms.service.impl;

import com.gms.BasePostgresIntegrationTest;
import com.gms.model.entity.Classroom;
import com.gms.model.entity.Employee;
import com.gms.model.entity.Parent;
import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.entity.User;
import com.gms.model.request.ParentRequest;
import com.gms.model.response.ParentResponse;
import com.gms.model.response.StudentResponse;
import com.gms.repository.ClassroomRepository;
import com.gms.repository.EmployeeRepository;
import com.gms.repository.ParentRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.ParentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParentManagementPostgresIntegrationTest extends BasePostgresIntegrationTest {

    @Autowired private ParentService parentService;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private ClassroomRepository classroomRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ParentRepository parentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    private School schoolOne;
    private Student studentOne;
    private Student secondStudentOne;
    private Student studentTwo;
    private Parent parentOne;
    private Parent parentTwo;
    private User parentUserOne;
    private User parentUserTwo;
    private User adminOne;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "TRUNCATE TABLE parent_student, user_roles, users, employees, parents, " +
                    "students, classrooms, schools CASCADE").executeUpdate();
            entityManager.flush();
        });

        transactionTemplate.executeWithoutResult(status -> {
            schoolOne = schoolRepository.save(school("Management School One", "MS001", "one@management.test"));
            School schoolTwo = schoolRepository.save(school("Management School Two", "MS002", "two@management.test"));
            Classroom classroomOne = classroomRepository.save(classroom(schoolOne, "10-A"));
            Classroom classroomTwo = classroomRepository.save(classroom(schoolTwo, "10-A"));

            studentOne = studentRepository.save(student(schoolOne, classroomOne, "STD001", "First", "Child"));
            secondStudentOne = studentRepository.save(student(schoolOne, classroomOne, "STD002", "Second", "Child"));
            studentTwo = studentRepository.save(student(schoolTwo, classroomTwo, "STD001", "Other", "Child"));

            parentOne = parentRepository.save(parent("Original", "Parent", "original@management.test", studentOne));
            parentTwo = parentRepository.save(parent("Foreign", "Parent", "foreign@management.test", studentTwo));
            parentUserOne = userRepository.save(user(schoolOne, "parent.one", "PARENT", parentOne));
            parentUserTwo = userRepository.save(user(schoolTwo, "parent.two", "PARENT", parentTwo));
            parentOne.setUser(parentUserOne);
            parentTwo.setUser(parentUserTwo);
            parentOne = parentRepository.save(parentOne);
            parentTwo = parentRepository.save(parentTwo);

            Employee administrator = new Employee();
            administrator.setSchool(schoolOne);
            administrator.setEmployeeId("EMP001");
            administrator.setFirstName("School");
            administrator.setLastName("Admin");
            administrator.setEmail("admin.one@management.test");
            administrator.setEmployeeStatus("1");
            administrator = employeeRepository.save(administrator);

            adminOne = user(schoolOne, "admin.one", "ADMIN", null);
            adminOne.setEmployee(administrator);
            adminOne = userRepository.save(adminOne);
        });
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Parent update persists contact details, unique child links, and login identity")
    void updatePersistsParentChildrenAndLoginIdentity() {
        String originalPassword = parentUserOne.getPassword();
        String originalUsername = parentUserOne.getUsername();
        ParentRequest request = updateRequest();
        request.setFirstName("Updated");
        request.setLastName("Family");
        request.setEmail("updated@management.test");
        request.setPhoneNumber("0987654321");
        request.setStudentIds(List.of(studentOne.getId(), secondStudentOne.getId(), studentOne.getId()));

        ResponseEntity<ParentResponse> response = asAdmin(() -> parentService.update(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getFirstName()).isEqualTo("Updated");
        assertThat(response.getBody().getEmail()).isEqualTo("updated@management.test");
        assertThat(response.getBody().getEnabled()).isTrue();
        assertThat(response.getBody().getTemporaryPassword()).isNull();
        assertThat(response.getBody().getStudents()).extracting(StudentResponse::getId)
                .containsExactlyInAnyOrder(studentOne.getId(), secondStudentOne.getId());

        transactionTemplate.executeWithoutResult(status -> {
            Parent persisted = parentRepository.findById(parentOne.getId()).orElseThrow();
            assertThat(persisted.getFullName()).isEqualTo("Updated Family");
            assertThat(persisted.getEmail()).isEqualTo("updated@management.test");
            assertThat(persisted.getPhoneNumber()).isEqualTo("0987654321");
            assertThat(persisted.getStudents()).hasSize(2);

            User login = userRepository.findById(parentUserOne.getId()).orElseThrow();
            assertThat(login.getFullName()).isEqualTo("Updated Family");
            assertThat(login.getEmail()).isEqualTo("updated@management.test");
            assertThat(login.getPhoneNumber()).isEqualTo("0987654321");
            assertThat(login.getPassword()).isEqualTo(originalPassword);
            assertThat(login.getUsername()).isEqualTo(originalUsername);
            assertThat(login.isEnabled()).isTrue();
        });
    }

    @Test
    @DisplayName("Parent update requires an explicit parent ID")
    void updateRequiresParentId() {
        ParentRequest request = updateRequest();
        request.setId(null);

        ResponseEntity<ParentResponse> response = asAdmin(() -> parentService.update(request));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Parent update rejects a parent from another school")
    void updateRejectsForeignParent() {
        ParentRequest request = updateRequest();
        request.setId(parentTwo.getId());

        assertThatThrownBy(() -> asAdmin(() -> parentService.update(request)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Parent not found");
    }

    @Test
    @DisplayName("Parent update rejects a child from another school without changing the family")
    void updateRejectsForeignStudent() {
        ParentRequest request = updateRequest();
        request.setStudentIds(List.of(studentTwo.getId()));

        assertThatThrownBy(() -> asAdmin(() -> parentService.update(request)))
                .isInstanceOf(EntityNotFoundException.class);

        transactionTemplate.executeWithoutResult(status -> {
            Parent persisted = parentRepository.findById(parentOne.getId()).orElseThrow();
            assertThat(persisted.getEmail()).isEqualTo("original@management.test");
            assertThat(persisted.getStudents()).extracting(Student::getId).containsExactly(studentOne.getId());
        });
    }

    @Test
    @DisplayName("Parent update rejects an email owned by another parent")
    void updateRejectsDuplicateEmail() {
        ParentRequest request = updateRequest();
        request.setEmail(parentTwo.getEmail());

        assertThatThrownBy(() -> asAdmin(() -> parentService.update(request)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A parent with this email already exists.");
    }

    @Test
    @DisplayName("Parent toggle disables and re-enables the login account")
    void toggleUpdatesLoginAccountState() {
        ResponseEntity<?> disabledResponse = asAdmin(() -> parentService.toggleParent(parentOne.getId(), false));

        assertThat(disabledResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disabledResponse.getBody()).isInstanceOf(ParentResponse.class);
        assertThat(((ParentResponse) disabledResponse.getBody()).getEnabled()).isFalse();
        assertLoginState(false, "0");

        ResponseEntity<?> enabledResponse = asAdmin(() -> parentService.toggleParent(parentOne.getId(), true));

        assertThat(((ParentResponse) enabledResponse.getBody()).getEnabled()).isTrue();
        assertLoginState(true, "1");
    }

    @Test
    @DisplayName("Parent toggle rejects another school's parent without changing its login")
    void toggleRejectsForeignParent() {
        assertThatThrownBy(() -> asAdmin(() -> parentService.toggleParent(parentTwo.getId(), false)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Parent not found");

        User foreignLogin = userRepository.findById(parentUserTwo.getId()).orElseThrow();
        assertThat(foreignLogin.isEnabled()).isTrue();
        assertThat(foreignLogin.getUserStatus()).isEqualTo("1");
    }

    private void assertLoginState(boolean enabled, String status) {
        User login = userRepository.findById(parentUserOne.getId()).orElseThrow();
        assertThat(login.isEnabled()).isEqualTo(enabled);
        assertThat(login.getUserStatus()).isEqualTo(status);
    }

    private ParentRequest updateRequest() {
        ParentRequest request = new ParentRequest();
        request.setId(parentOne.getId());
        request.setFirstName(parentOne.getFirstName());
        request.setLastName(parentOne.getLastName());
        request.setEmail(parentOne.getEmail());
        request.setPhoneNumber(parentOne.getPhoneNumber());
        request.setStudentIds(List.of(studentOne.getId()));
        return request;
    }

    private <T> T asAdmin(Supplier<T> action) {
        TenantContext.set(new TenantContext.Principal(
                adminOne.getUsername(), adminOne.getId(), adminOne.getSchoolId(),
                adminOne.getEmployeeId(), Set.of("ADMIN")));
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }

    private static School school(String name, String code, String email) {
        School school = new School();
        school.setSchoolName(name);
        school.setSchoolCode(code);
        school.setAddress("Integration test address");
        school.setPhone("1234567890");
        school.setEmail(email);
        return school;
    }

    private static Classroom classroom(School school, String name) {
        Classroom classroom = new Classroom();
        classroom.setSchool(school);
        classroom.setName(name);
        classroom.setGrade("10");
        classroom.setSection("A");
        return classroom;
    }

    private static Student student(
            School school, Classroom classroom, String admissionNumber, String firstName, String lastName) {
        Student student = new Student();
        student.setSchool(school);
        student.setClassroom(classroom);
        student.setStudentId(admissionNumber);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        return student;
    }

    private static Parent parent(String firstName, String lastName, String email, Student student) {
        Parent parent = new Parent();
        parent.setFirstName(firstName);
        parent.setLastName(lastName);
        parent.setEmail(email);
        parent.setStudents(new ArrayList<>(List.of(student)));
        return parent;
    }

    private static User user(School school, String username, String role, Parent parent) {
        User user = new User();
        user.setSchool(school);
        user.setParent(parent);
        user.setFullName(username);
        user.setEmail(username + "@management.test");
        user.setUsername(username);
        user.setUserId(username.toUpperCase().replace('.', '_'));
        user.setPassword("encoded-password");
        user.setRoles(Set.of(role));
        return user;
    }
}
