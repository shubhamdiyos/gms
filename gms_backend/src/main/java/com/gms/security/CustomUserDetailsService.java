package com.gms.security;

import com.gms.model.entity.User;
import com.gms.service.UserService;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
     * @param username The username to look up
     * @return UserDetails object for Spring Security
     * @throws UsernameNotFoundException if user is not found
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> userOptional = userService.findByUsername(username);
        User user = userOptional.orElseThrow(
                () -> new UsernameNotFoundException("User not found with username: " + username));

        Set<GrantedAuthority> authorities = user.getRoles().stream()
                .map(SimpleGrantedAuthority::new)  // roles are stored without the ROLE_ prefix
                .collect(Collectors.toSet());

        log.debug("Loaded authentication principal {} (enabled={}, locked={}, passwordExpired={})",
                user.getUsername(), user.isEnabled(), user.isAccountLocked(), user.isPasswordExpired());

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                true,
                !user.isPasswordExpired(),
                !user.isAccountLocked(),
                authorities);
    }
}
