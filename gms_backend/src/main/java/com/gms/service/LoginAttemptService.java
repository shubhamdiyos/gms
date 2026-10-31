package com.gms.service;

import com.gms.model.entity.User;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces the {@code security.login.max-failed-attempts} limit against the
 * {@code users.login_attempts} and {@code users.account_locked} columns.
 *
 * <p>Those columns have existed since the first schema revision but nothing wrote to them except
 * the bootstrap service, so a locked account still authenticated (finding SEC-09). Locking here is
 * the countermeasure Spring Security's {@code accountNonLocked} flag consumed by
 * {@link com.gms.security.CustomUserDetailsService}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserService userService;

    @Value("${security.login.max-failed-attempts:10}")
    private int maxFailedAttempts;

    @Transactional
    public void onSuccess(User user) {
        user.setLoginAttempts(0);
        user.setLastLoginAt(Instant.now());
        userService.save(user);
    }

    /**
     * @return true when this failure crossed the threshold and locked the account
     */
    @Transactional
    public boolean onFailure(User user) {
        int attempts = user.getLoginAttempts() == null ? 1 : user.getLoginAttempts() + 1;
        user.setLoginAttempts(attempts);
        boolean locked = maxFailedAttempts > 0 && attempts >= maxFailedAttempts;
        if (locked) {
            user.setAccountLocked(true);
            log.warn("Account {} locked after {} failed login attempts", user.getUsername(), attempts);
        } else {
            log.debug("Failed login for account {} (attempt {} of {})",
                    user.getUsername(), attempts, maxFailedAttempts);
        }
        userService.save(user);
        return locked;
    }
}
