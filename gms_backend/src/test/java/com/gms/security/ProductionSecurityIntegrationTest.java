package com.gms.security;

import com.gms.config.CookieCsrfTokenRepository;
import com.gms.config.CorsConfig;
import com.gms.config.SecurityConfig;
import com.gms.controller.AuthController;
import com.gms.service.EmployeeService;
import com.gms.service.LoginAttemptService;
import com.gms.service.UserService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the production SecurityFilterChain's HTTP behavior without a database.
 *
 * <p>Uses the real {@link SecurityConfig} (CSRF enabled, cookie-based token repository)
 * with pass-through substitutes for the two custom filters and mock beans for all
 * database-dependent services. MockMvc is built with {@code standaloneSetup} so the
 * controller mappings are registered directly, and the production security filter
 * chain is added as a servlet filter.</p>
 *
 * <p>The "test" profile is NOT activated, so {@code TestSecurityConfig} (which disables CSRF)
 * is never loaded.</p>
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {
        SecurityConfig.class,
        CorsConfig.class,
        ProductionSecurityIntegrationTest.PassThroughFilters.class,
        ProductionSecurityIntegrationTest.MockBeans.class
})
@DisplayName("Production Security Configuration - HTTP Behavior Tests")
class ProductionSecurityIntegrationTest {

    private static final String API = "/api/v1";
    private static final String AUTH_COOKIE = "gms_auth";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        UserService userService = context.getBean(UserService.class);
        EmployeeService employeeService = context.getBean(EmployeeService.class);
        LoginAttemptService loginAttemptService = context.getBean(LoginAttemptService.class);
        JwtTokenProvider jwtTokenProvider = context.getBean(JwtTokenProvider.class);
        AuthenticationManager authenticationManager = context.getBean(AuthenticationManager.class);
        PasswordEncoder passwordEncoder = context.getBean(PasswordEncoder.class);

        AuthController authController = new AuthController(
                authenticationManager, jwtTokenProvider, userService,
                employeeService, passwordEncoder, loginAttemptService);

        Filter springSecurityFilterChain = context.getBean("springSecurityFilterChain", Filter.class);

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .apply(springSecurity(springSecurityFilterChain))
                .build();
    }

    @Configuration
    static class MockBeans {
        @Bean
        public UserService userService() {
            return Mockito.mock(UserService.class);
        }

        @Bean
        public EmployeeService employeeService() {
            return Mockito.mock(EmployeeService.class);
        }

        @Bean
        public LoginAttemptService loginAttemptService() {
            return Mockito.mock(LoginAttemptService.class);
        }

        @Bean
        public JwtTokenProvider jwtTokenProvider() {
            return Mockito.mock(JwtTokenProvider.class);
        }

        @Bean
        public UserDetailsService userDetailsService() {
            return Mockito.mock(UserDetailsService.class);
        }

        @Bean
        public HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
            return new HandlerMappingIntrospector();
        }
    }

    @Configuration
    static class PassThroughFilters {

        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                                                UserDetailsService userDetailsService) {
            return new JwtAuthenticationFilter(tokenProvider, userDetailsService);
        }

        @Bean
        public PasswordChangeEnforcementFilter passwordChangeEnforcementFilter() {
            return new PasswordChangeEnforcementFilter(null) {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                 FilterChain chain) throws ServletException, IOException {
                    chain.doFilter(request, response);
                }
            };
        }
    }

    // ─── Test 1: Unauthenticated requests ───

    @Nested
    @DisplayName("Test 1: Unauthenticated requests to protected endpoints return 401")
    class UnauthenticatedRequests {

        @Test
        @DisplayName("POST /api/v1/auth/change-password without authentication returns 401")
        void changePassword_withoutAuth_returns401() throws Exception {
            mockMvc.perform(post(API + "/auth/change-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oldPassword\":\"old\",\"newPassword\":\"NewPass123!\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        @Test
        @DisplayName("GET /api/v1/users/me without authentication returns 401")
        void usersMe_withoutAuth_returns401() throws Exception {
            mockMvc.perform(get(API + "/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        @Test
        @DisplayName("GET /api/v1/dashboard/school without authentication returns 401")
        void dashboard_withoutAuth_returns401() throws Exception {
            mockMvc.perform(get(API + "/dashboard/school"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─── Test 2: Authenticated requests ───

    @Nested
    @DisplayName("Test 2: Authenticated requests pass the security layer")
    class AuthenticatedRequests {

        @Test
        @DisplayName("GET /api/v1/auth/change-password with ADMIN passes security (405 = reached controller)")
        void changePassword_withAdmin_passesSecurity() throws Exception {
            mockMvc.perform(get(API + "/auth/change-password")
                            .with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN"))))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("GET /api/v1/auth/csrf-token is accessible without authentication (anonymous)")
        void csrfTokenEndpoint_isPublic() throws Exception {
            mockMvc.perform(get(API + "/auth/csrf-token"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status).isNotEqualTo(401);
                        assertThat(status).isNotEqualTo(403);
                    });
        }
    }

    // ─── Test 3: Anonymous endpoints ───

    @Nested
    @DisplayName("Test 3: Anonymous endpoints accessible without authentication")
    class AnonymousEndpoints {

        @Test
        @DisplayName("POST /api/v1/auth/login passes security (controller handles auth)")
        void login_passesSecurity() throws Exception {
            mockMvc.perform(post(API + "/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"unknown\",\"password\":\"wrong\"}"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status).isIn(200, 400, 401);
                        assertThat(status).isNotEqualTo(403);
                    });
        }

        @Test
        @DisplayName("POST /api/v1/auth/logout is accessible without authentication")
        void logout_isPublic() throws Exception {
            mockMvc.perform(post(API + "/auth/logout")
                            .with(csrf()))
                    .andExpect(status().isOk());
        }
    }

    // ─── Tests 4-8: CSRF protection ───

    @Nested
    @DisplayName("Tests 4-8: CSRF protection for state-changing requests")
    class CsrfProtection {

        @Test
        @DisplayName("Test 4: POST with valid CSRF token passes CSRF filter (not 403)")
        void post_withValidCsrf_passesCsrfFilter() throws Exception {
            mockMvc.perform(post(API + "/auth/change-password")
                            .with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oldPassword\":\"old\",\"newPassword\":\"NewPass123!\"}"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status)
                                .as("CSRF filter should permit the request (not 403)")
                                .isNotEqualTo(403);
                    });
        }

        @Test
        @DisplayName("Test 5: POST without CSRF token is rejected (403)")
        void post_withoutCsrf_rejected() throws Exception {
            mockMvc.perform(post(API + "/auth/change-password")
                            .with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oldPassword\":\"old\",\"newPassword\":\"NewPass123!\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Test 6: POST with invalid CSRF token is rejected (403)")
        void post_withInvalidCsrf_rejected() throws Exception {
            mockMvc.perform(post(API + "/auth/change-password")
                            .with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                            .header(CSRF_HEADER, "invalid-token-value")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oldPassword\":\"old\",\"newPassword\":\"NewPass123!\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Test 7: POST with mismatched CSRF cookie and header is rejected")
        void post_withMismatchedCsrf_rejected() throws Exception {
            mockMvc.perform(post(API + "/auth/change-password")
                            .with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                            .cookie(new Cookie(CSRF_COOKIE, "cookie-token"))
                            .header(CSRF_HEADER, "different-header-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oldPassword\":\"old\",\"newPassword\":\"NewPass123!\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Test 8: GET requests are NOT blocked by CSRF")
        void get_notBlockedByCsrf() throws Exception {
            mockMvc.perform(get(API + "/auth/change-password")
                            .with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN"))))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertThat(status)
                                .as("GET must not be rejected by CSRF (403)")
                                .isNotEqualTo(403);
                    });
        }

        @Test
        @DisplayName("PUT without CSRF token is rejected")
        void put_withoutCsrf_rejected() throws Exception {
            mockMvc.perform(put(API + "/auth/change-password")
                            .with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DELETE without CSRF token is rejected")
        void delete_withoutCsrf_rejected() throws Exception {
            mockMvc.perform(delete(API + "/students/1")
                            .with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PATCH without CSRF token is rejected")
        void patch_withoutCsrf_rejected() throws Exception {
            mockMvc.perform(patch(API + "/students/1")
                            .with(user("admin").authorities(new SimpleGrantedAuthority("ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("The request attribute carries the repository token unmasked")
        void requestAttribute_exposesTheCookieToken() {
            CookieCsrfTokenRepository repo = new CookieCsrfTokenRepository();
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            CsrfToken token = repo.generateToken(request);
            repo.saveToken(token, request, response);
            request.setCookies(new Cookie(CSRF_COOKIE, token.getToken()));

            CsrfToken loaded = repo.loadToken(request);
            new CsrfTokenRequestAttributeHandler().handle(request, response, () -> loaded);

            CsrfToken fromAttribute = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            assertThat(fromAttribute.getToken())
                    .as("the React SPA reads the cookie, the Flutter app reads the body")
                    .isEqualTo(token.getToken());
        }
    }

    // ─── Tests 9-10: Cookie attributes ───

    @Nested
    @DisplayName("Tests 9-10: Cookie attributes and logout behavior")
    class CookieBehavior {

        @Test
        @DisplayName("Test 9: Logout clears the authentication cookie with Max-Age=0")
        void logout_clearsAuthCookie() throws Exception {
            MvcResult result = mockMvc.perform(post(API + "/auth/logout").with(csrf()))
                    .andExpect(status().isOk())
                    .andReturn();

            List<String> setCookieHeaders = result.getResponse().getHeaders("Set-Cookie");
            assertThat(setCookieHeaders).isNotEmpty();

            String authCookieHeader = setCookieHeaders.stream()
                    .filter(h -> h.startsWith(AUTH_COOKIE + "="))
                    .findFirst()
                    .orElse(null);

            assertThat(authCookieHeader).isNotNull();
            assertThat(authCookieHeader).contains("Max-Age=0");
            assertThat(authCookieHeader).contains("HttpOnly");
            assertThat(authCookieHeader).contains("Secure");
            assertThat(authCookieHeader).contains("SameSite=Lax");
            assertThat(authCookieHeader).contains("Path=/");
        }

        @Test
        @DisplayName("Test 10: Auth cookie has HttpOnly attribute")
        void authCookie_hasHttpOnly() throws Exception {
            MvcResult result = mockMvc.perform(post(API + "/auth/logout").with(csrf()))
                    .andExpect(status().isOk())
                    .andReturn();

            String authCookieHeader = authCookieFrom(result);
            assertThat(authCookieHeader).contains("HttpOnly");
        }

        @Test
        @DisplayName("Test 10: Auth cookie has Secure attribute")
        void authCookie_hasSecure() throws Exception {
            MvcResult result = mockMvc.perform(post(API + "/auth/logout").with(csrf()))
                    .andExpect(status().isOk())
                    .andReturn();

            String authCookieHeader = authCookieFrom(result);
            assertThat(authCookieHeader).contains("Secure");
        }

        @Test
        @DisplayName("Test 10: Auth cookie has SameSite=Lax attribute")
        void authCookie_hasSameSite() throws Exception {
            MvcResult result = mockMvc.perform(post(API + "/auth/logout").with(csrf()))
                    .andExpect(status().isOk())
                    .andReturn();

            String authCookieHeader = authCookieFrom(result);
            assertThat(authCookieHeader).contains("SameSite=Lax");
        }

        @Test
        @DisplayName("CSRF cookie does NOT have HttpOnly (JavaScript must read it)")
        void csrfCookie_notHttpOnly() {
            CookieCsrfTokenRepository repo = new CookieCsrfTokenRepository();
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            CsrfToken token = repo.generateToken(request);
            repo.saveToken(token, request, response);

            String csrfCookieHeader = response.getHeaders("Set-Cookie").stream()
                    .filter(h -> h.startsWith(CSRF_COOKIE + "="))
                    .findFirst()
                    .orElse(null);

            assertThat(csrfCookieHeader).isNotNull();
            assertThat(csrfCookieHeader).doesNotContain("HttpOnly");
        }

        @Test
        @DisplayName("CSRF cookie has Secure and SameSite=Lax attributes")
        void csrfCookie_hasCorrectAttributes() {
            CookieCsrfTokenRepository repo = new CookieCsrfTokenRepository();
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            CsrfToken token = repo.generateToken(request);
            repo.saveToken(token, request, response);

            String csrfCookieHeader = response.getHeaders("Set-Cookie").stream()
                    .filter(h -> h.startsWith(CSRF_COOKIE + "="))
                    .findFirst()
                    .orElse(null);

            assertThat(csrfCookieHeader).contains("Secure");
            assertThat(csrfCookieHeader).contains("SameSite=Lax");
            assertThat(csrfCookieHeader).contains("Path=/");
        }

        private static String authCookieFrom(MvcResult result) {
            return result.getResponse().getHeaders("Set-Cookie").stream()
                    .filter(h -> h.startsWith(AUTH_COOKIE + "="))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Auth cookie not found in Set-Cookie headers"));
        }
    }

    // ─── Role-based access control ───

    @Nested
    @DisplayName("Role-based access control")
    class RoleBasedAccess {

        @Test
        @DisplayName("STUDENT cannot access /api/v1/auth/self-register (ADMIN/SUPERADMIN only)")
        void student_cannotAccessSelfRegister() throws Exception {
            mockMvc.perform(post(API + "/auth/self-register")
                            .with(user("student").authorities(new SimpleGrantedAuthority("STUDENT")))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"empId\":1,\"schoolId\":1,\"username\":\"test\",\"password\":\"Test123!\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TEACHER cannot access /api/v1/students (ADMIN/SUPERADMIN only)")
        void teacher_cannotAccessStudents() throws Exception {
            mockMvc.perform(get(API + "/students")
                            .with(user("teacher").authorities(new SimpleGrantedAuthority("TEACHER"))))
                    .andExpect(status().isForbidden());
        }
    }
}
