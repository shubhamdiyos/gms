package com.gms.config;

import com.gms.security.JwtAuthenticationFilter;
import com.gms.security.PasswordChangeEnforcementFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Test-specific security configuration that disables CSRF protection.
 *
 * <p>This configuration is active only when the "test" profile is active. It disables CSRF
 * protection to simplify integration testing, as adding CSRF tokens to every test request
 * would be cumbersome and error-prone.</p>
 *
 * <p>Production security configuration (with CSRF enabled) is in {@link SecurityConfig}.</p>
 */
@Configuration
@Profile("test")
public class TestSecurityConfig {

    private static final String API = "/api/v1";

    private static final String[] PLATFORM_MANAGEMENT = {
        API + "/schools/**", API + "/users/**", API + "/employees/**", API + "/students/**",
        API + "/student-admissions/**", API + "/academic-years/**"
    };

    private static final String[] SCHOOL_SETUP = {
        API + "/classrooms/**", API + "/sections/**", API + "/subjects/**",
        API + "/teacher-assignments/**", API + "/grading-schemes/**", API + "/communications/**"
    };

    private static final String[] ACADEMIC_OPERATIONS = {
        API + "/exams/**", API + "/attendance/**", API + "/timetables/**"
    };

    private static final String[] FINANCIAL = {
        API + "/fees/**", API + "/fee-structures/**", API + "/fee-payments/**",
        API + "/invoices/**", API + "/receipts/**", API + "/student-fees/**",
        API + "/financial-transactions/**", API + "/financial-reports/**",
        API + "/financial-dashboard/**", API + "/payment-gateway/**"
    };

    private static final String[] SHARED_READS = {
        API + "/results/**", API + "/student-exams/**", API + "/announcements/**",
        API + "/notifications/**", API + "/school-calendar/**", API + "/parent-students/**"
    };

    private static final String[] DASHBOARD = {
        API + "/dashboard/**"
    };

    private static final String[] SELF_SERVICE = {
        API + "/auth/change-password",
        API + "/users/me", API + "/users/me/**"
    };

    @Bean
    @Primary
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                          PasswordChangeEnforcementFilter passwordChangeFilter,
                                          CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            // CSRF disabled for tests to simplify integration testing
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                        writeJson(response, HttpStatus.UNAUTHORIZED, "Unauthorized"))
                .accessDeniedHandler((request, response, deniedException) ->
                        writeJson(response, HttpStatus.FORBIDDEN, "Forbidden")))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC, DispatcherType.FORWARD).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, API + "/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, API + "/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET, API + "/auth/csrf-token").permitAll()
                .requestMatchers(HttpMethod.GET, API + "/ping").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasAnyAuthority("ADMIN", "SUPERADMIN")
                .requestMatchers(SELF_SERVICE).authenticated()
                .requestMatchers(API + "/students/profile", API + "/students/my-*").hasAuthority("STUDENT")
                .requestMatchers(API + "/parents/profile", API + "/parents/notifications",
                        API + "/parents/children/**").hasAuthority("PARENT")
                .requestMatchers(API + "/parents", API + "/parents/create", API + "/parents/update",
                        API + "/parents/toggle").hasAnyAuthority("ADMIN", "SUPERADMIN")
                .requestMatchers(API + "/teachers/**").hasAuthority("TEACHER")
                .requestMatchers(API + "/timetables/student/me").hasAuthority("STUDENT")
                .requestMatchers(PLATFORM_MANAGEMENT).hasAnyAuthority("ADMIN", "SUPERADMIN")
                .requestMatchers(SCHOOL_SETUP).hasAnyAuthority("ADMIN", "SUPERADMIN", "TEACHER")
                .requestMatchers(ACADEMIC_OPERATIONS).hasAnyAuthority("ADMIN", "SUPERADMIN", "TEACHER")
                .requestMatchers(FINANCIAL).hasAnyAuthority("ADMIN", "SUPERADMIN", "ACCOUNTANT")
                .requestMatchers(SHARED_READS).hasAnyAuthority(
                        "ADMIN", "SUPERADMIN", "TEACHER", "STUDENT", "PARENT")
                .requestMatchers(DASHBOARD).hasAnyAuthority("ADMIN", "SUPERADMIN")
                .requestMatchers(API + "/auth/self-register").hasAnyAuthority("ADMIN", "SUPERADMIN")
                .anyRequest().denyAll()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(passwordChangeFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    private static void writeJson(HttpServletResponse response, HttpStatus status, String error)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}
