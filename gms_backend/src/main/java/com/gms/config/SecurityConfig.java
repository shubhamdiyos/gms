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
                    "/api/v1/auth/**",
                    "/api/v1/test/password"
                ).permitAll()
                // SUPERADMIN - Full system access
                .requestMatchers("/api/v1/schools/**").hasAnyAuthority("SUPERADMIN", "ADMIN")
                .requestMatchers("/api/v1/employees/**").hasAnyAuthority("ADMIN", "SUPERADMIN")
                .requestMatchers("/api/v1/students/**").hasAnyAuthority("ADMIN", "SUPERADMIN")
                .requestMatchers("/api/v1/classrooms/**", "/api/v1/classes/**").hasAnyAuthority("ADMIN", "SUPERADMIN")
                .requestMatchers("/api/v1/subjects/**").hasAnyAuthority("ADMIN", "SUPERADMIN")
                .requestMatchers("/api/v1/academic-years/**").hasAnyAuthority("ADMIN", "SUPERADMIN")
                .requestMatchers("/api/v1/fees/**").hasAnyAuthority("ADMIN", "SUPERADMIN")
                .requestMatchers("/api/v1/notifications/**").hasAnyAuthority("ADMIN", "SUPERADMIN", "TEACHER")
                .requestMatchers("/api/v1/announcements/**").hasAnyAuthority("ADMIN", "SUPERADMIN", "TEACHER")
                
                // Teacher role endpoints
                .requestMatchers("/api/v1/teachers/profile", "/api/v1/teachers/classes", "/api/v1/teachers/students").hasAuthority("TEACHER")
                .requestMatchers("/api/v1/attendance/**", "/api/v1/timetables/**").hasAnyAuthority("TEACHER", "ADMIN", "SUPERADMIN")
                
                // Student role endpoints
                .requestMatchers("/api/v1/students/profile", "/api/v1/students/my-classes", "/api/v1/students/my-subjects").hasAuthority("STUDENT")
                .requestMatchers("/api/v1/students/my-attendance", "/api/v1/students/my-results", "/api/v1/students/my-fees").hasAuthority("STUDENT")
                
                // Parent role endpoints
                .requestMatchers("/api/v1/parents/profile", "/api/v1/parents/children").hasAuthority("PARENT")
                
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