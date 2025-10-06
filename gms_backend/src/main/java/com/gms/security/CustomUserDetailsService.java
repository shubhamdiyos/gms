package com.gms.security;

import com.gms.model.entity.User;
import com.gms.service.UserService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    public CustomUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    /**
     * Load user by username for Spring Security authentication.
     * This method only supports username-based authentication, not email-based authentication.
     * 
     * @param username The username to look up
     * @return UserDetails object for Spring Security
     * @throws UsernameNotFoundException if user is not found
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("Loading user by username: " + username);
        // Find user by username only (no email fallback)
        Optional<User> userOptional = userService.findByUsername(username);
        User user = userOptional.orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        
        System.out.println("User found: " + user.getUsername() + " with password: " + user.getPassword());
        
        Set<GrantedAuthority> authorities = user.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority(r))  // Don't add ROLE_ prefix
                .collect(Collectors.toSet());
                
        System.out.println("User roles: " + user.getRoles());
        
        // Use the username for Spring Security authentication (not email)
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(), 
                user.getPassword(), 
                user.isEnabled(), 
                true, 
                true, 
                true, 
                authorities
        );
    }
}