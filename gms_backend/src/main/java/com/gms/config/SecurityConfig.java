package com.gms.config;

import com.gms.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Transport-level authorisation for the API.
 *
 * <p>Three rules govern the matcher list below, and each of them exists because the previous
 * revision broke it (findings AP-1, AP-2, SEC-13):</p>
 * <ol>
 *   <li><b>Order.</b> {@code authorizeHttpRequests} is first-match-wins, so the self-service
 *       matchers for students, parents and teachers are declared <i>before</i> the administrative
 *       matchers that share their prefix. A STUDENT reaching {@code /students/profile} used to be
 *       shadowed by the {@code /students/**} ADMIN rule and answered 403.</li>
 *   <li><b>Fail closed.</b> Everything that is not listed is denied. The catch-all used to be
 *       {@code anyRequest().authenticated()}, which silently granted the fifteen route groups that
 *       no matcher named.</li>
 *   <li><b>No wider than the method layer.</b> Each administrative group admits exactly the union of
 *       roles its controllers' {@code @PreAuthorize} annotations mention, plus SUPERADMIN as the
 *       platform owner. The URL layer must not exclude a role that a method-level rule grants —
 *       that is how the ACCOUNTANT role became unreachable (SEC-06).</li>
 * </ol>
 *
 * <p>Authorisation here is coarse perimeter only. Per-record tenant isolation is a separate
 * concern tracked in {@code docs/audit/SECURITY_AUDIT.md} and implemented by the tenant-isolation
 * work package; a correct URL rule is not proof that a request is allowed to see the row.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

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

    /** Read surfaces that parents and students also consume; each method gates itself. */
    private static final String[] SHARED_READS = {
        API + "/results/**", API + "/student-exams/**", API + "/announcements/**",
        API + "/notifications/**", API + "/school-calendar/**", API + "/parent-students/**"
    };

    /**
     * Dashboard aggregates. The URL layer admits the union of what the handlers mention — an
     * {@code ADMIN} and a {@code SUPERADMIN} may both ask about their own school, and the
     * cross-tenant route narrows to {@code SUPERADMIN} on its own {@code @PreAuthorize}. With
     * {@code anyRequest().denyAll()} below, a route group nobody names here is a route that answers
     * 403 to everybody, however correct its method guard is.
     */
    private static final String[] DASHBOARD = {
        API + "/dashboard/**"
    };

    private static final String[] SELF_SERVICE = {
        API + "/auth/change-password",
        API + "/users/me", API + "/users/me/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                          CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
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
                // Error and async dispatches must reach the container's error handling; they are
                // protected by the decision made for the originating REQUEST.
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC, DispatcherType.FORWARD).permitAll()

                // Allow OPTIONS requests for CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ── Anonymous surface: login, liveness and the health probes only ──
                .requestMatchers(HttpMethod.POST, API + "/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, API + "/ping").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasAnyAuthority("ADMIN", "SUPERADMIN")

                // ── Self-service: declared before the administrative matchers on the same prefix ──
                .requestMatchers(SELF_SERVICE).authenticated()
                .requestMatchers(API + "/students/profile", API + "/students/my-*").hasAuthority("STUDENT")
                .requestMatchers(API + "/parents/profile", API + "/parents/notifications",
                        API + "/parents/children/**").hasAuthority("PARENT")
                .requestMatchers(API + "/teachers/**").hasAuthority("TEACHER")
                // The student's own timetable: ACADEMIC_OPERATIONS admits ADMIN/SUPERADMIN/TEACHER only,
                // so with first-match-wins the /timetables/** rule would 403 a STUDENT whose handler
                // explicitly grants it. It is declared here rather than widening that group, because
                // /timetables/classroom/{id} takes a classroom id and belongs to an administrator.
                .requestMatchers(API + "/timetables/student/me").hasAuthority("STUDENT")

                // ── Administrative perimeter ──
                .requestMatchers(PLATFORM_MANAGEMENT).hasAnyAuthority("ADMIN", "SUPERADMIN")
                .requestMatchers(SCHOOL_SETUP).hasAnyAuthority("ADMIN", "SUPERADMIN", "TEACHER")
                .requestMatchers(ACADEMIC_OPERATIONS).hasAnyAuthority("ADMIN", "SUPERADMIN", "TEACHER")
                .requestMatchers(FINANCIAL).hasAnyAuthority("ADMIN", "SUPERADMIN", "ACCOUNTANT", "PARENT")
                .requestMatchers(SHARED_READS).hasAnyAuthority(
                        "ADMIN", "SUPERADMIN", "TEACHER", "STUDENT", "PARENT")
                .requestMatchers(DASHBOARD).hasAnyAuthority("ADMIN", "SUPERADMIN")

                // ── Provisioning endpoints that live under /auth but are not login ──
                .requestMatchers(API + "/auth/self-register").hasAnyAuthority("ADMIN", "SUPERADMIN")

                .anyRequest().denyAll()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeJson(HttpServletResponse response, HttpStatus status, String error)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + error + "\"}");
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
