package com.gms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.gms.model.entity.User;
import com.gms.service.UserService;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * The account-state columns on {@code users} must reach Spring Security. Before M1 the loader
 * asserted that every account was unlocked and had a fresh credential, so locking an account was
 * cosmetic (finding SEC-09).
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private CustomUserDetailsService service;

    private User account(boolean enabled, boolean locked, boolean passwordExpired) {
        User user = new User();
        user.setUsername("teacher-one");
        user.setPassword("{encoded}");
        user.setEnabled(enabled);
        user.setAccountLocked(locked);
        user.setPasswordExpired(passwordExpired);
        user.setRoles(Set.of("TEACHER"));
        return user;
    }

    @Test
    void reportsAnEnabledAccountAsUsable() {
        when(userService.findByUsername("teacher-one")).thenReturn(Optional.of(account(true, false, false)));

        UserDetails details = service.loadUserByUsername("teacher-one");

        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }

    @Test
    void honoursTheAccountLockedColumn() {
        when(userService.findByUsername("teacher-one")).thenReturn(Optional.of(account(true, true, false)));

        assertThat(service.loadUserByUsername("teacher-one").isAccountNonLocked()).isFalse();
    }

    @Test
    void honoursThePasswordExpiredColumn() {
        when(userService.findByUsername("teacher-one")).thenReturn(Optional.of(account(true, false, true)));

        assertThat(service.loadUserByUsername("teacher-one").isCredentialsNonExpired()).isFalse();
    }

    @Test
    void disabledAccountsAreNotUsable() {
        when(userService.findByUsername("teacher-one")).thenReturn(Optional.of(account(false, false, false)));

        assertThat(service.loadUserByUsername("teacher-one").isEnabled()).isFalse();
    }

    @Test
    void loadsRolesWithoutTheRolePrefix() {
        when(userService.findByUsername("teacher-one")).thenReturn(Optional.of(account(true, false, false)));

        assertThat(service.loadUserByUsername("teacher-one").getAuthorities())
                .extracting("authority")
                .containsExactly("TEACHER");
    }

    @Test
    void unknownAccountFails() {
        when(userService.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
