package com.gms.config;

import com.gms.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/api/v1/ping",
                    "/api/v1/auth/**"
                ).permitAll()
                // RBAC rules
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/schools").hasRole("SUPERADMIN")
                .requestMatchers("/api/v1/employees/**").hasAnyRole("ADMIN", "SUPERADMIN")
                .requestMatchers("/api/v1/classes/**", "/api/v1/subjects/**", "/api/v1/students/**").hasAnyRole("ADMIN", "SUPERADMIN")
                // Teacher role endpoints
                .requestMatchers("/api/v1/teachers/profile", "/api/v1/teachers/classes", "/api/v1/teachers/students").hasRole("TEACHER")
                .requestMatchers("/api/v1/attendance/**", "/api/v1/timetables/**").hasAnyRole("TEACHER", "ADMIN", "SUPERADMIN")
                // Student role endpoints
                .requestMatchers("/api/v1/students/profile", "/api/v1/students/classes", "/api/v1/students/subjects").hasRole("STUDENT")
                .requestMatchers("/api/v1/attendance/student/**").hasAnyRole("STUDENT", "PARENT")
                // Parent role endpoints
                .requestMatchers("/api/v1/parents/profile", "/api/v1/parents/children").hasRole("PARENT")
                .requestMatchers("/api/v1/attendance/parent/**").hasRole("PARENT")
                // User profile endpoints
                .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}