package com.gms.security;

import com.gms.model.entity.User;
import com.gms.service.UserService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    /**
     * Load user by username for Spring Security authentication.
     * This method only supports username-based authentication, not email-based authentication.
     *
     * <p>The returned {@code UserDetails} mirrors the account-state columns that have always
     * existed on {@code users} ({@code enabled}, {@code account_locked}, {@code password_expired})
     * instead of asserting that every account is healthy, so a locked or expired account can no
     * longer authenticate (mandate §7.6, finding SEC-09).</p>
     *
     * <p>Stage E: Returns {@link GmsUserDetails} instead of Spring Security's built-in User,
     * so that downstream filters (e.g. {@link PasswordChangeEnforcementFilter}) can read the
     * {@code requirePasswordChange} flag directly from the principal without a duplicate
     * database lookup.</p>
     *
     * @param username The username to look up
     * @return UserDetails object for Spring Security
     * @throws UsernameNotFoundException if user is not found
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> userOptional = userService.findByUsername(username);
        User user = userOptional.orElseThrow(
                () -> new UsernameNotFoundException("User not found with username: " + username));

        log.debug("Loaded authentication principal {} (enabled={}, locked={}, passwordExpired={})",
                user.getUsername(), user.isEnabled(), user.isAccountLocked(), user.isPasswordExpired());

        return new GmsUserDetails(user);
    }
}
