package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.SchoolRequest;
import com.gms.repository.DesignationRepository;
import com.gms.repository.EmployeeRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * ACD-13: Generated school administrator username/password collision.
 *
 * <p>When two schools are provisioned with administrators having the same name base (e.g., "John Smith"
 * → "jsmith"), the second administrator's username gets a suffix ("jsmith1") to avoid collision. The
 * temporary password must be derived from the FINAL username ("jsmith1@123"), not the original base
 * ("jsmith@123"), otherwise the administrator receives credentials that cannot authenticate.</p>
 *
 * <p>This test verifies that the password generation uses the final username after collision resolution.</p>
 */
@ExtendWith(MockitoExtension.class)
class SchoolServiceImplCredentialCollisionTest {

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

    @InjectMocks
    private SchoolServiceImpl schoolService;

    @BeforeEach
    void setUp() {
        // Mock designation repository to avoid NPE in seedDesignationIfAbsent
        when(designationRepository.existsBySchool_IdAndTitle(any(Integer.class), anyString())).thenReturn(false);
        when(designationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(employeeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void whenUsernameCollides_thenPasswordMatchesFinalUsername() {
        // Arrange: first school with "jsmith" already exists
        SchoolRequest request = new SchoolRequest();
        request.setSchoolName("Test School 2");
        request.setAdminFullName("John Smith");
        request.setAdminEmail("jsmith@test2.com");
        request.setAddress("123 Test St");
        request.setPhone("555-1234");

        School savedSchool = new School();
        savedSchool.setId(2);
        savedSchool.setSchoolName("Test School 2");

        when(schoolRepository.save(any(School.class))).thenReturn(savedSchool);
        when(userRepository.existsByUsername("jsmith")).thenReturn(true); // collision
        when(userRepository.existsByUsername("jsmith1")).thenReturn(false); // available
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "encoded_" + invocation.getArgument(0));

        // Mock TenantContext to return a valid employee ID (required by create() method)
        try (var mockedTenantContext = org.mockito.Mockito.mockStatic(com.gms.security.TenantContext.class)) {
            mockedTenantContext.when(com.gms.security.TenantContext::employeeId).thenReturn(1);

            // Act
            schoolService.create(request);

            // Assert: capture all saved users and verify the last one has matching credentials
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.times(2)).save(userCaptor.capture());
            java.util.List<User> savedUsers = userCaptor.getAllValues();
            User finalUser = savedUsers.get(savedUsers.size() - 1); // get the last saved state

            assertThat(finalUser.getUsername()).isEqualTo("jsmith1"); // final username with suffix
            assertThat(finalUser.getPassword()).isEqualTo("encoded_jsmith1@123"); // password derived from final username
            // The password encoder receives "jsmith1@123", NOT "jsmith@123"
            // This proves the fix: tempPassword = username + "@123", not base + "@123"
        }
    }

    @Test
    void whenNoCollision_thenPasswordMatchesBaseUsername() {
        // Arrange: no collision, "jsmith" is available
        SchoolRequest request = new SchoolRequest();
        request.setSchoolName("Test School 1");
        request.setAdminFullName("John Smith");
        request.setAdminEmail("jsmith@test1.com");
        request.setAddress("123 Test St");
        request.setPhone("555-1234");

        School savedSchool = new School();
        savedSchool.setId(1);
        savedSchool.setSchoolName("Test School 1");

        when(schoolRepository.save(any(School.class))).thenReturn(savedSchool);
        when(userRepository.existsByUsername("jsmith")).thenReturn(false); // no collision
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "encoded_" + invocation.getArgument(0));

        // Mock TenantContext to return a valid employee ID (required by create() method)
        try (var mockedTenantContext = org.mockito.Mockito.mockStatic(com.gms.security.TenantContext.class)) {
            mockedTenantContext.when(com.gms.security.TenantContext::employeeId).thenReturn(1);

            // Act
            schoolService.create(request);

            // Assert: capture all saved users and verify the last one has matching credentials
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.times(2)).save(userCaptor.capture());
            java.util.List<User> savedUsers = userCaptor.getAllValues();
            User finalUser = savedUsers.get(savedUsers.size() - 1); // get the last saved state

            assertThat(finalUser.getUsername()).isEqualTo("jsmith"); // base username, no suffix
            assertThat(finalUser.getPassword()).isEqualTo("encoded_jsmith@123"); // password derived from username
        }
    }
}
