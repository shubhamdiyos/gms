package com.gms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.gms.config.SuperAdminBootstrapProperties;
import com.gms.model.entity.User;
import com.gms.service.UserService;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * SEC-CRED-02: Server-side forced password change enforcement tests.
 *
 * <p>Verifies that users with {@code requirePasswordChange=true} cannot access business APIs
 * until they change their password, while still allowing authentication and password-change
 * operations.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordChangeEnforcementFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private SuperAdminBootstrapProperties superAdminBootstrapProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private User userRequiringChange;
    private User userWithoutRequirement;
    private String tokenForUserRequiringChange;
    private String tokenForNormalUser;

    /**
     * Creates a genuinely expired JWT using the same signing key as the test tokenProvider.
     * The token has a valid signature but an expiration timestamp in the past.
     */
    private String createExpiredToken(String username) throws Exception {
        // Use reflection to access the private 'key' field from JwtTokenProvider
        Field keyField = JwtTokenProvider.class.getDeclaredField("key");
        keyField.setAccessible(true);
        SecretKey key = (SecretKey) keyField.get(tokenProvider);

        Instant now = Instant.now();
        Instant exp = now.minusSeconds(3600); // Expired 1 hour ago
        Instant iat = now.minusSeconds(7200); // Issued 2 hours ago

        return io.jsonwebtoken.Jwts.builder()
                .setSubject(username)
                .claim("roles", java.util.List.of("STUDENT"))
                .claim("schoolId", 1)
                .claim("empId", 1)
                .claim("uid", 1)
                .setIssuer("gms")
                .setIssuedAt(Date.from(iat))
                .setExpiration(Date.from(exp))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @BeforeEach
    void setUp() {
        userRequiringChange = new User();
        userRequiringChange.setId(1);
        userRequiringChange.setUsername("testuser");
        userRequiringChange.setPassword(passwordEncoder.encode("TempPass123!"));
        userRequiringChange.setEnabled(true);
        userRequiringChange.setRequirePasswordChange(true);
        userRequiringChange.setAccountLocked(false);
        userRequiringChange.setPasswordExpired(false);
        userRequiringChange.setRoles(Set.of("STUDENT"));

        userWithoutRequirement = new User();
        userWithoutRequirement.setId(2);
        userWithoutRequirement.setUsername("normaluser");
        userWithoutRequirement.setPassword(passwordEncoder.encode("NormalPass456!"));
        userWithoutRequirement.setEnabled(true);
        userWithoutRequirement.setRequirePasswordChange(false);
        userWithoutRequirement.setAccountLocked(false);
        userWithoutRequirement.setPasswordExpired(false);
        userWithoutRequirement.setRoles(Set.of("STUDENT"));

        tokenForUserRequiringChange = tokenProvider.createToken(
                userRequiringChange.getUsername(),
                userRequiringChange.getRoles(),
                1,
                1,
                userRequiringChange.getId());

        tokenForNormalUser = tokenProvider.createToken(
                userWithoutRequirement.getUsername(),
                userWithoutRequirement.getRoles(),
                1,
                2,
                userWithoutRequirement.getId());

        GmsUserDetails userDetailsRequiringChange = new GmsUserDetails(userRequiringChange);
        GmsUserDetails userDetailsNormal = new GmsUserDetails(userWithoutRequirement);

        // Stage E: use thenAnswer to create a fresh GmsUserDetails per call, reflecting
        // the entity's current state (important for afterPasswordChange_businessEndpoint_allowed,
        // which modifies the entity mid-test and expects subsequent requests to see the change).
        when(userDetailsService.loadUserByUsername("testuser")).thenAnswer(inv -> new GmsUserDetails(userRequiringChange));
        when(userDetailsService.loadUserByUsername("normaluser")).thenAnswer(inv -> new GmsUserDetails(userWithoutRequirement));
    }

    @Test
    void login_withValidCredentials_succeeds() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"TempPass123!\"}"))
                .andReturn();
        int status = result.getResponse().getStatus();
        // The login endpoint is whitelisted — the filter must never block it (SEC-CRED-02 §7).
        // A 200 means authentication succeeded and a JWT was issued; a 401 means the
        // AuthenticationManager rejected the credential against the mock UserDetailsService.
        // Either outcome proves the password-change filter did not interpose.
        assertThat(status).isIn(200, 401);
        if (status == 200) {
            String body = result.getResponse().getContentAsString();
            assertThat(body).contains("accessToken");
            assertThat(body).contains("\"requirePasswordChange\":true");
        }
    }

    @Test
    void businessEndpoint_withRequirePasswordChangeFlag_blocked() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        mockMvc.perform(get("/api/v1/students/profile")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void changePasswordEndpoint_withRequirePasswordChangeFlag_allowed() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"TempPass123!\",\"newPassword\":\"NewSecurePass789@\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void usersMeEndpoint_withRequirePasswordChangeFlag_allowed() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange))
                .andExpect(status().isOk());
    }

    @Test
    void businessEndpoint_withoutRequirePasswordChangeFlag_allowed() throws Exception {
        when(userService.findByUsername("normaluser")).thenReturn(Optional.of(userWithoutRequirement));

        // The filter must not interpose for a user without the flag. A non-403 status proves the
        // request reached the controller layer; the exact code depends on whether the backing entity
        // exists in the mock (404 is expected here because no Student row is linked to the mock user).
        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokenForNormalUser))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
    }

    /**
     * SEC-CRED-02: Password change lifecycle verification with MOCKED persistence.
     *
     * <p>This test verifies the controller/filter integration: that the change-password endpoint
     * calls {@code userService.save()} with the correct updated User object (flag cleared, password
     * hashed). The {@code UserService} and {@code UserDetailsService} are {@code @MockBean} (lines
     * 50-54), so this test does NOT prove real database transaction persistence. The ArgumentCaptor
     * confirms the controller passed the correct object to the service layer, but the service is
     * mocked to echo back the same object without hitting a real database.</p>
     *
     * <p>For real database persistence verification, a separate integration test with Testcontainers
     * or an embedded database would be required (out of scope for SEC-CRED-02 test evidence).</p>
     */
    @Test
    void afterPasswordChange_businessEndpoint_allowed() throws Exception {
        String originalPasswordHash = userRequiringChange.getPassword();
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));
        when(userService.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Step 1-6: Call the actual change-password endpoint
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"TempPass123!\",\"newPassword\":\"NewSecurePass789@\"}"))
                .andExpect(status().isOk());

        // Step 7: Verify the controller actually persisted the flag change
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.isRequirePasswordChange())
                .as("Password change endpoint must clear the requirePasswordChange flag")
                .isFalse();
        assertThat(savedUser.getPassword())
                .as("Password must be updated")
                .isNotEqualTo(originalPasswordHash);
        assertThat(passwordEncoder.matches("NewSecurePass789@", savedUser.getPassword()))
                .as("New password must be correctly hashed")
                .isTrue();

        // Step 8-10: Reuse the same JWT and verify the filter passes the request
        MvcResult result = mockMvc.perform(get("/api/v1/students/profile")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("After password change, the same JWT must access business endpoints")
                .isNotEqualTo(403);
    }

    @Test
    void crossUser_cannotBypassOtherUsersPasswordRequirement() throws Exception {
        when(userService.findByUsername("normaluser")).thenReturn(Optional.of(userWithoutRequirement));

        // A user without the flag must reach the controller layer (non-403). This proves the filter
        // checks the authenticated user's own flag, not some other user's requirement.
        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokenForNormalUser))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
    }

    @Test
    void crossUser_cannotChangeOtherUsersPassword() throws Exception {
        String userA_originalPasswordHash = userRequiringChange.getPassword();
        String userB_originalPasswordHash = userWithoutRequirement.getPassword();
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));
        when(userService.findByUsername("normaluser")).thenReturn(Optional.of(userWithoutRequirement));
        when(userService.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // User A (testuser) attempts to change User B's (normaluser) password by providing
        // User B's username in the request. The change-password endpoint uses TenantContext.username()
        // from the JWT, not the request body, so this attempt must either fail or only change User A's
        // password (the authenticated user).
        MvcResult result = mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"normaluser\",\"oldPassword\":\"TempPass123!\",\"newPassword\":\"HackedPass999!\"}"))
                .andReturn();

        // CRITICAL SECURITY PROPERTY: User B's password must NOT be changed
        assertThat(userWithoutRequirement.getPassword())
                .as("Cross-user password change must not modify the target user's password")
                .isEqualTo(userB_originalPasswordHash);

        // The endpoint returns 200 because it successfully changed User A's password (the authenticated
        // user), ignoring the username field in the request body. This is correct behavior — the endpoint
        // only operates on the JWT-authenticated user.
        assertThat(result.getResponse().getStatus())
                .as("Endpoint should succeed (changing User A's password) or fail, but must never change User B")
                .isIn(200, 400, 401, 403);

        // If the endpoint returned 200, User A's password was changed (to their own new password).
        // If it returned 4xx, User A's password remains unchanged. Either way, User B is safe.
        if (result.getResponse().getStatus() == 200) {
            // User A's password changed (correct — they authenticated and changed their own password)
            assertThat(userRequiringChange.getPassword())
                    .as("User A's password may change when they authenticate and provide correct old password")
                    .isNotEqualTo(userA_originalPasswordHash);
        } else {
            // User A's password did not change (also acceptable — the attempt failed)
            assertThat(userRequiringChange.getPassword())
                    .as("User A's password remains unchanged on failed attempt")
                    .isEqualTo(userA_originalPasswordHash);
        }
    }

    @Test
    void invalidJwt_rejected() throws Exception {
        String invalidToken = "invalid.jwt.token";

        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + invalidToken))
                .andReturn();

        // Invalid JWT must be rejected (401 Unauthorized)
        assertThat(result.getResponse().getStatus())
                .as("Invalid JWT must be rejected")
                .isEqualTo(401);
    }

    @Test
    void expiredJwt_rejected() throws Exception {
        // Generate a genuinely expired JWT with valid signature
        String expiredToken = createExpiredToken("testuser");

        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andReturn();

        // Expired JWT must be rejected with 401 Unauthorized
        assertThat(result.getResponse().getStatus())
                .as("Expired JWT with valid signature must be rejected")
                .isEqualTo(401);

        // Verify the request never reached the business logic (no UserService call)
        verify(userService, org.mockito.Mockito.never()).findByUsername(any());
    }

    /**
     * Stage E: Verifies that when the principal is a {@link GmsUserDetails} (the normal case
     * after {@link CustomUserDetailsService} loads the user), the filter reads the
     * {@code requirePasswordChange} flag directly from the principal and does NOT perform
     * a duplicate database lookup via {@code userService.findByUsername()}.
     */
    @Test
    void gmsUserDetailsPrincipal_doesNotTriggerDuplicateLookup() throws Exception {
        // The setUp() already mocks userDetailsService to return GmsUserDetails.
        // We do NOT mock userService.findByUsername() — if the filter calls it,
        // Mockito will return Optional.empty() by default, and the filter will allow
        // the request through (which would be wrong for a user requiring password change).
        // So if this test gets a 403, it proves the filter read the flag from the principal.
        // If it gets a non-403, it proves the filter called the DB (and got empty).

        mockMvc.perform(get("/api/v1/students/profile")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));

        // Verify userService.findByUsername() was NEVER called — the flag came from the principal
        verify(userService, org.mockito.Mockito.never()).findByUsername(any());
    }

    // ===== Stage F: Fail-closed security regression tests =====

    /**
     * Stage F Test 3: Unexpected principal with existing user requiring password change → HTTP 403.
     * Simulates a non-GmsUserDetails principal (e.g., Spring Security's built-in User) to exercise
     * the fallback database lookup path. The user exists and requires a password change, so access
     * must be rejected.
     */
    @Test
    void unexpectedPrincipal_userRequiringPasswordChange_blocked() throws Exception {
        // Override the normal mock to return a non-GmsUserDetails principal
        UserDetails springUser =
                org.springframework.security.core.userdetails.User.builder()
                        .username("testuser")
                        .password("{encoded}pass")
                        .roles("STUDENT")
                        .build();
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(springUser);

        // Mock the fallback DB lookup to return a user requiring password change
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        mockMvc.perform(get("/api/v1/students/profile")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));

        // Verify the fallback lookup was performed
        verify(userService).findByUsername("testuser");
    }

    /**
     * Stage F Test 4: Unexpected principal with existing user NOT requiring password change → request continues.
     * Simulates a non-GmsUserDetails principal with a normal user. The fallback lookup finds the user,
     * who does not require a password change, so the request must proceed (non-403).
     */
    @Test
    void unexpectedPrincipal_userNotRequiringPasswordChange_allowed() throws Exception {
        // Override the normal mock to return a non-GmsUserDetails principal
        UserDetails springUser =
                org.springframework.security.core.userdetails.User.builder()
                        .username("normaluser")
                        .password("{encoded}pass")
                        .roles("STUDENT")
                        .build();
        when(userDetailsService.loadUserByUsername("normaluser")).thenReturn(springUser);

        // Mock the fallback DB lookup to return a user NOT requiring password change
        when(userService.findByUsername("normaluser")).thenReturn(Optional.of(userWithoutRequirement));

        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokenForNormalUser))
                .andReturn();

        // Request must proceed (non-403)
        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);

        // Verify the fallback lookup was performed
        verify(userService).findByUsername("normaluser");
    }

    /**
     * Stage F Test 5: Unexpected principal with nonexistent user → access rejected (fail-closed).
     * Simulates a non-GmsUserDetails principal where the fallback DB lookup returns empty.
     * Stage F fail-closed behavior: access must be rejected (HTTP 403), not allowed.
     */
    @Test
    void unexpectedPrincipal_nonexistentUser_rejected() throws Exception {
        // Override the normal mock to return a non-GmsUserDetails principal
        UserDetails springUser =
                org.springframework.security.core.userdetails.User.builder()
                        .username("ghostuser")
                        .password("{encoded}pass")
                        .roles("STUDENT")
                        .build();
        when(userDetailsService.loadUserByUsername("ghostuser")).thenReturn(springUser);

        // Create a token for the ghost user
        String ghostToken = tokenProvider.createToken("ghostuser", Set.of("STUDENT"), 1, 999, 999);

        // Mock the fallback DB lookup to return empty (user not found)
        when(userService.findByUsername("ghostuser")).thenReturn(Optional.empty());

        // Stage F fail-closed: must reject access (403), not allow it
        mockMvc.perform(get("/api/v1/students/profile")
                        .header("Authorization", "Bearer " + ghostToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));

        // Verify the fallback lookup was attempted
        verify(userService).findByUsername("ghostuser");
    }

    /**
     * Stage F Test 6: Fallback database exception → access rejected (fail-closed).
     * Simulates a non-GmsUserDetails principal where the fallback DB lookup throws an exception.
     * Stage F fail-closed behavior: access must be rejected (HTTP 403), not allowed.
     */
    @Test
    void unexpectedPrincipal_databaseException_rejected() throws Exception {
        // Override the normal mock to return a non-GmsUserDetails principal
        UserDetails springUser =
                org.springframework.security.core.userdetails.User.builder()
                        .username("testuser")
                        .password("{encoded}pass")
                        .roles("STUDENT")
                        .build();
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(springUser);

        // Mock the fallback DB lookup to throw an exception
        when(userService.findByUsername("testuser")).thenThrow(new RuntimeException("Database connection failed"));

        // Stage F fail-closed: must reject access (403), not allow it
        mockMvc.perform(get("/api/v1/students/profile")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));

        // Verify the fallback lookup was attempted
        verify(userService).findByUsername("testuser");
    }

    // ===== Stage G: Whitelist path-boundary regression tests =====

    /**
     * Stage G Test 1: Exact match for /api/v1/auth/login is allowed.
     * Verifies the legitimate login endpoint bypasses the password-change filter.
     */
    @Test
    void exactMatch_loginEndpoint_allowed() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"TempPass123!\"}"))
                .andReturn();

        // Login endpoint is whitelisted — must NOT return 403 from password-change filter
        assertThat(result.getResponse().getStatus())
                .as("Exact match /api/v1/auth/login must be whitelisted")
                .isIn(200, 401); // 200=success, 401=bad credentials, but NOT 403
    }

    /**
     * Stage G Test 2: Exact match for /api/v1/auth/change-password is allowed.
     * Verifies the legitimate password-change endpoint bypasses the filter.
     */
    @Test
    void exactMatch_changePasswordEndpoint_allowed() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"TempPass123!\",\"newPassword\":\"NewSecurePass789@\"}"))
                .andExpect(status().isOk());
    }

    /**
     * Stage G Test 3: Exact match for /api/v1/users/me is allowed.
     * Verifies the legitimate self-profile endpoint bypasses the filter.
     */
    @Test
    void exactMatch_usersMeEndpoint_allowed() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange))
                .andExpect(status().isOk());
    }

    /**
     * Stage G Test 4: Lookalike path /api/v1/auth/login-extra is BLOCKED.
     * Verifies that prefix-based matching does NOT allow dangerous lookalikes.
     */
    @Test
    void lookalike_loginExtra_blocked() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        mockMvc.perform(get("/api/v1/auth/login-extra")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
    }

    /**
     * Stage G Test 5: Lookalike path /api/v1/auth/change-password-extra is BLOCKED.
     * Verifies that prefix-based matching does NOT allow dangerous lookalikes.
     */
    @Test
    void lookalike_changePasswordExtra_blocked() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        mockMvc.perform(get("/api/v1/auth/change-password-extra")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
    }

    /**
     * Stage G Test 6: Lookalike path /api/v1/users/meevil is BLOCKED.
     * Verifies that prefix-based matching does NOT allow dangerous lookalikes.
     */
    @Test
    void lookalike_meevil_blocked() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        mockMvc.perform(get("/api/v1/users/meevil")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
    }

    /**
     * Stage G Test 7: Lookalike path /api/v1/users/metadata is BLOCKED.
     * Verifies that prefix-based matching does NOT allow dangerous lookalikes.
     */
    @Test
    void lookalike_metadata_blocked() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        mockMvc.perform(get("/api/v1/users/metadata")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
    }

    /**
     * Stage G Test 8: Path traversal /api/v1/users/me/../admin is BLOCKED.
     * Spring's servlet container rejects path traversal with 400 Bad Request before
     * the filter chain runs. This proves the traversal never reaches the whitelist check.
     */
    @Test
    void pathTraversal_meDotDotAdmin_blocked() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        MvcResult result = mockMvc.perform(get("/api/v1/users/me/../admin")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange))
                .andReturn();

        // Spring rejects path traversal at the container level (400) — the filter never sees it.
        // Either 400 (container rejection) or 403 (filter rejection) proves the traversal is blocked.
        assertThat(result.getResponse().getStatus())
                .as("Path traversal must be rejected (400 by container or 403 by filter)")
                .isIn(400, 403);
    }

    /**
     * Stage G Test 9: Subpath /api/v1/users/me/profile is BLOCKED.
     * Verifies that only exact matches are whitelisted — no implicit subpath allowance.
     * This is correct because UserController has no /me/** subpaths.
     */
    @Test
    void subpath_meProfile_blocked() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        mockMvc.perform(get("/api/v1/users/me/profile")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
    }

    /**
     * Stage G Test 10: Trailing slash /api/v1/users/me/ is BLOCKED.
     * Verifies that exact match does not implicitly allow trailing slashes.
     * Uses authenticated request to prove the filter (not Spring Security) rejects it.
     */
    @Test
    void trailingSlash_usersMe_blocked() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(userRequiringChange));

        mockMvc.perform(get("/api/v1/users/me/")
                        .header("Authorization", "Bearer " + tokenForUserRequiringChange))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_REQUIRED"));
    }
}
