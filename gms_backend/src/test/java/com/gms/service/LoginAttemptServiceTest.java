package com.gms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gms.model.entity.User;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Brute-force countermeasures have to be written to something. These are the columns the schema
 * has carried since the first revision and that nothing updated (finding SEC-09).
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private UserService userService;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(userService);
        ReflectionTestUtils.setField(service, "maxFailedAttempts", 3);
    }

    private User account(Integer attempts) {
        User user = new User();
        user.setUsername("teacher-one");
        user.setLoginAttempts(attempts);
        return user;
    }

    @Test
    void recordsAFailureAndKeepsTheAccountUsable() {
        User user = account(0);
        when(userService.save(any(User.class))).thenReturn(user);

        assertThat(service.onFailure(user)).isFalse();
        assertThat(user.getLoginAttempts()).isEqualTo(1);
        assertThat(user.isAccountLocked()).isFalse();
    }

    @Test
    void locksTheAccountWhenTheThresholdIsReached() {
        User user = account(2);
        when(userService.save(any(User.class))).thenReturn(user);

        assertThat(service.onFailure(user)).isTrue();
        assertThat(user.getLoginAttempts()).isEqualTo(3);
        assertThat(user.isAccountLocked()).isTrue();
    }

    @Test
    void treatsAMissingAttemptCounterAsZero() {
        User user = account(null);
        when(userService.save(any(User.class))).thenReturn(user);

        assertThat(service.onFailure(user)).isFalse();
        assertThat(user.getLoginAttempts()).isEqualTo(1);
    }

    @Test
    void aSuccessfulLoginClearsTheCounterAndStampsTheVisit() {
        User user = account(2);
        when(userService.save(any(User.class))).thenReturn(user);

        service.onSuccess(user);

        assertThat(user.getLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isBetween(Instant.now().minusSeconds(5), Instant.now().plusSeconds(5));
    }
}
