package com.gms.security;

import com.gms.model.entity.User;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Custom UserDetails implementation that carries the project's User entity fields
 * needed for authentication and authorization decisions.
 *
 * <p>This class wraps the {@link User} entity and exposes the {@code requirePasswordChange}
 * flag to downstream filters without requiring additional database queries.</p>
 *
 * <p>SEC-CRED-02: The {@code requirePasswordChange} flag is used by
 * {@link PasswordChangeEnforcementFilter} to enforce forced initial password changes
 * without performing a duplicate database lookup.</p>
 */
@Getter
public class GmsUserDetails implements UserDetails {

    private final Integer userId;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final boolean accountLocked;
    private final boolean passwordExpired;
    private final boolean requirePasswordChange;
    private final Set<String> roles;
    private final Collection<? extends GrantedAuthority> authorities;

    public GmsUserDetails(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.enabled = user.isEnabled();
        this.accountLocked = user.isAccountLocked();
        this.passwordExpired = user.isPasswordExpired();
        this.requirePasswordChange = user.isRequirePasswordChange();
        // Defensive copy: prevent external mutation of roles from affecting authorization
        this.roles = Set.copyOf(user.getRoles());
        this.authorities = this.roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !accountLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !passwordExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
