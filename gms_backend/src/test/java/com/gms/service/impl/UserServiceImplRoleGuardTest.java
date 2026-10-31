package com.gms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.UserRequest;
import com.gms.repository.UserRepository;
import com.gms.service.SchoolService;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Role assignment used to be taken verbatim from the request body on both create and update, so a
 * school ADMIN could grant SUPERADMIN — including to themselves (finding AP-4).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplRoleGuardTest {

    private static final String VALID_PASSWORD = "Str0ng#Passphrase";

    @Mock
    private UserRepository userRepository;

    @Mock
    private SchoolService schoolService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl service;

    private UserRequest request(Set<String> roles) {
        UserRequest request = new UserRequest();
        request.setUsername("new-admin");
        request.setEmail("new-admin@example.org");
        request.setFullName("New Admin");
        request.setPassword(VALID_PASSWORD);
        request.setRoles(roles);
        return request;
    }

    private void givenTargetSchoolAndCreator() {
        School school = new School();
        school.setId(1);
        User creator = new User();
        creator.setId(99);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(schoolService.findById(1)).thenReturn(Optional.of(school));
        when(userRepository.findByEmployee_Id(7)).thenReturn(Optional.of(creator));
    }

    @Test
    void acceptsRolesInsideTheKnownVocabulary() {
        givenTargetSchoolAndCreator();

        User created = service.createUserFromRequest(request(Set.of("ADMIN", "TEACHER")), new User(), 7, 1);

        assertThat(created.getRoles()).containsExactlyInAnyOrder("ADMIN", "TEACHER");
    }

    @Test
    void rejectsARoleThatIsNotInTheVocabulary() {
        givenTargetSchoolAndCreator();

        assertThatThrownBy(() -> service.createUserFromRequest(request(Set.of("ROOT")), new User(), 7, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown role code");
    }

    @Test
    void cannotGrantSuperadminWithoutHoldingIt() {
        givenTargetSchoolAndCreator();

        assertThatThrownBy(() ->
                service.createUserFromRequest(request(Set.of("SUPERADMIN")), new User(), 7, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only a SUPERADMIN may grant the SUPERADMIN role");
    }

    @Test
    void updateAppliesTheSameGuard() {
        School school = new School();
        school.setId(1);
        User existing = new User();
        existing.setId(5);
        existing.setSchool(school);
        existing.setRoles(Set.of("TEACHER"));
        User updater = new User();
        updater.setId(99);
        when(userRepository.findById(5)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmployee_Id(7)).thenReturn(Optional.of(updater));

        UserRequest request = request(Set.of("SUPERADMIN"));
        request.setId(5);

        assertThatThrownBy(() -> service.update(request, 7, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only a SUPERADMIN may grant the SUPERADMIN role");
        assertThat(existing.getRoles()).containsExactly("TEACHER");
    }
}
