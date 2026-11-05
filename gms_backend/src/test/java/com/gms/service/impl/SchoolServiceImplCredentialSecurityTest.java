package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gms.model.entity.Designation;
import com.gms.model.entity.Employee;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.SchoolRequest;
import com.gms.repository.DesignationRepository;
import com.gms.repository.EmployeeRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.EmailService;
import com.gms.util.PasswordValidator;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * SEC-CRED-03: Verify that SchoolServiceImpl generates secure, unpredictable admin credentials.
 *
 * <p>The old pattern {@code username + "@123"} was predictable and violated credential security
 * best practices. These tests verify that the new implementation uses cryptographically secure
 * random passwords that pass the password policy validator.</p>
 */
@ExtendWith(MockitoExtension.class)
class SchoolServiceImplCredentialSecurityTest {

    private static final Integer SUPERADMIN_EMP_ID = 999;

    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DesignationRepository designationRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private SchoolServiceImpl schoolService;

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext.Principal(
                "superadmin", null, null, SUPERADMIN_EMP_ID, java.util.Set.of("SUPERADMIN")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_generatesSecureRandomPassword_notPredictablePattern() {
        SchoolRequest request = new SchoolRequest();
        request.setSchoolName("Test School");
        request.setSchoolCode("TS001");
        request.setAddress("123 Test St");
        request.setEmail("school@test.com");
        request.setAdminFullName("John Admin");
        request.setAdminEmail("john@test.com");

        School savedSchool = new School();
        savedSchool.setId(1);
        savedSchool.setSchoolName("Test School");
        when(schoolRepository.save(any(School.class))).thenReturn(savedSchool);
        when(schoolRepository.existsBySchoolCode(any())).thenReturn(false);
        when(schoolRepository.existsByEmail(any())).thenReturn(false);
        when(designationRepository.existsBySchool_IdAndTitle(any(), any())).thenReturn(false);
        when(designationRepository.save(any())).thenAnswer(inv -> {
            Designation d = inv.getArgument(0);
            d.setId(1);
            return d;
        });
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(100);
            return user;
        });
        when(employeeRepository.save(any())).thenAnswer(inv -> {
            Employee emp = inv.getArgument(0);
            emp.setId(200);
            return emp;
        });
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        schoolService.create(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.times(2)).save(userCaptor.capture());
        User savedUser = userCaptor.getAllValues().get(1);

        String encodedPassword = savedUser.getPassword();
        assertThat(encodedPassword).isEqualTo("hashed");

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(passwordCaptor.capture());
        String rawPassword = passwordCaptor.getValue();

        assertThat(rawPassword).isNotNull();
        assertThat(rawPassword).hasSize(12);
        assertThat(rawPassword).doesNotMatch(".*@123$");
        assertThat(rawPassword).doesNotContain("john");
        assertThat(rawPassword).doesNotContain("admin");

        assertThat(PasswordValidator.isValid(rawPassword)).isTrue();
    }

    @Test
    void create_generatesUniquePasswords_onMultipleCalls() {
        SchoolRequest request1 = new SchoolRequest();
        request1.setSchoolName("School 1");
        request1.setSchoolCode("S1");
        request1.setAddress("Address 1");
        request1.setEmail("school1@test.com");
        request1.setAdminFullName("Admin One");
        request1.setAdminEmail("admin1@test.com");

        SchoolRequest request2 = new SchoolRequest();
        request2.setSchoolName("School 2");
        request2.setSchoolCode("S2");
        request2.setAddress("Address 2");
        request2.setEmail("school2@test.com");
        request2.setAdminFullName("Admin Two");
        request2.setAdminEmail("admin2@test.com");

        School savedSchool1 = new School();
        savedSchool1.setId(1);
        School savedSchool2 = new School();
        savedSchool2.setId(2);

        when(schoolRepository.save(any(School.class))).thenReturn(savedSchool1).thenReturn(savedSchool2);
        when(schoolRepository.existsBySchoolCode(any())).thenReturn(false);
        when(schoolRepository.existsByEmail(any())).thenReturn(false);
        when(designationRepository.existsBySchool_IdAndTitle(any(), any())).thenReturn(false);
        when(designationRepository.save(any())).thenAnswer(inv -> {
            Designation d = inv.getArgument(0);
            d.setId(1);
            return d;
        });
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(user.getId() != null ? user.getId() + 100 : 100);
            return user;
        });
        when(employeeRepository.save(any())).thenAnswer(inv -> {
            Employee emp = inv.getArgument(0);
            emp.setId(200);
            return emp;
        });
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        schoolService.create(request1);
        schoolService.create(request2);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder, org.mockito.Mockito.times(2)).encode(passwordCaptor.capture());

        String password1 = passwordCaptor.getAllValues().get(0);
        String password2 = passwordCaptor.getAllValues().get(1);

        assertThat(password1).isNotEqualTo(password2);
        assertThat(password1).hasSize(12);
        assertThat(password2).hasSize(12);
    }

    @Test
    void create_setsRequirePasswordChangeFlag() {
        SchoolRequest request = new SchoolRequest();
        request.setSchoolName("Test School");
        request.setSchoolCode("TS002");
        request.setAddress("123 Test St");
        request.setEmail("school2@test.com");
        request.setAdminFullName("Jane Admin");
        request.setAdminEmail("jane@test.com");

        School savedSchool = new School();
        savedSchool.setId(1);
        when(schoolRepository.save(any(School.class))).thenReturn(savedSchool);
        when(schoolRepository.existsBySchoolCode(any())).thenReturn(false);
        when(schoolRepository.existsByEmail(any())).thenReturn(false);
        when(designationRepository.existsBySchool_IdAndTitle(any(), any())).thenReturn(false);
        when(designationRepository.save(any())).thenAnswer(inv -> {
            Designation d = inv.getArgument(0);
            d.setId(1);
            return d;
        });
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(employeeRepository.save(any())).thenAnswer(inv -> {
            Employee emp = inv.getArgument(0);
            emp.setId(200);
            return emp;
        });
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        schoolService.create(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.times(2)).save(userCaptor.capture());
        User savedUser = userCaptor.getAllValues().get(1);

        assertThat(savedUser.isRequirePasswordChange()).isTrue();
    }

    @Test
    void create_hashesPasswordBeforeStorage() {
        SchoolRequest request = new SchoolRequest();
        request.setSchoolName("Test School");
        request.setSchoolCode("TS003");
        request.setAddress("123 Test St");
        request.setEmail("school3@test.com");
        request.setAdminFullName("Bob Admin");
        request.setAdminEmail("bob@test.com");

        School savedSchool = new School();
        savedSchool.setId(1);
        when(schoolRepository.save(any(School.class))).thenReturn(savedSchool);
        when(schoolRepository.existsBySchoolCode(any())).thenReturn(false);
        when(schoolRepository.existsByEmail(any())).thenReturn(false);
        when(designationRepository.existsBySchool_IdAndTitle(any(), any())).thenReturn(false);
        when(designationRepository.save(any())).thenAnswer(inv -> {
            Designation d = inv.getArgument(0);
            d.setId(1);
            return d;
        });
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(employeeRepository.save(any())).thenAnswer(inv -> {
            Employee emp = inv.getArgument(0);
            emp.setId(200);
            return emp;
        });
        when(passwordEncoder.encode(any())).thenReturn("BCrypt$2a$10$hashed");

        schoolService.create(request);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(passwordCaptor.capture());
        String rawPassword = passwordCaptor.getValue();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.times(2)).save(userCaptor.capture());
        User savedUser = userCaptor.getAllValues().get(1);

        assertThat(savedUser.getPassword()).isEqualTo("BCrypt$2a$10$hashed");
        assertThat(savedUser.getPassword()).isNotEqualTo(rawPassword);
        assertThat(rawPassword).hasSize(12);
    }
}
