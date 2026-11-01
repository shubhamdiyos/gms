package com.gms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gms.config.CorsConfig;
import com.gms.config.SecurityConfig;
import com.gms.service.SchoolService;
import io.jsonwebtoken.Claims;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The tenant is resolved once per request, and once per request only.
 *
 * <p>Before the tenant context, a controller asked {@code SecurityUtil.getSchoolIdFromToken()}, which
 * pulled the {@code Authorization} header off the request and re-parsed and re-verified the JWT
 * signature. Every controller asked three times — for the username, the roles and the school — and the
 * services that wanted the tenant had to be handed it as a parameter, so a caller could pass the wrong
 * school or pass none at all.</p>
 *
 * <p>These tests assert the property that replaced it: the claims are parsed once, the tenant is read
 * from the context, and the context does not outlive the request that created it.</p>
 */
@WebMvcTest(controllers = {com.gms.controller.SchoolController.class})
@Import({SecurityConfig.class, CorsConfig.class})
class TenantContextTest {

    private static final String BEARER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private SchoolService schoolService;

    @AfterEach
    void noContextSurvivesTheTest() {
        TenantContext.clear();
    }

    @Test
    void theTokenIsVerifiedOncePerRequest() throws Exception {
        authenticate("admin", Set.of("ADMIN"));

        mockMvc.perform(get("/api/v1/schools").header("Authorization", BEARER))
                .andExpect(status().isOk());

        // Before: the controller parsed the token once for the username, once for the roles and once
        // for the school, and every service that wanted the tenant parsed it again.
        verify(tokenProvider, times(1)).parseClaims(anyString());
    }

    @Test
    void theTenantReachesTheServiceFromTheClaims() throws Exception {
        authenticate("admin", Set.of("ADMIN"));
        AtomicReference<Integer> schoolTheQueryWasScopedTo = new AtomicReference<>();
        when(schoolService.findBySchool()).thenAnswer(invocation -> {
            schoolTheQueryWasScopedTo.set(TenantContext.schoolId());
            return List.of();
        });

        mockMvc.perform(get("/api/v1/schools").header("Authorization", BEARER))
                .andExpect(status().isOk());

        // The school in the claim is the school the query is scoped to, and the caller had no way to
        // name a different one: the service takes the tenant, not a school id.
        verify(schoolService).findBySchool();
        assertThat(schoolTheQueryWasScopedTo.get()).isEqualTo(1);
    }

    @Test
    void theContextIsEmptyOnceTheRequestHasFinished() throws Exception {
        authenticate("admin", Set.of("ADMIN"));

        mockMvc.perform(get("/api/v1/schools").header("Authorization", BEARER))
                .andExpect(status().isOk());

        // A pooled thread must not carry one caller's tenant into the next caller's request.
        assertThat(TenantContext.current()).isNull();
        assertThat(TenantContext.schoolId()).isNull();
        assertThat(TenantContext.username()).isNull();
        assertThat(TenantContext.roles()).isEmpty();
    }

    @Test
    void anUnauthenticatedRequestLeavesNoTenantBehind() throws Exception {
        mockMvc.perform(get("/api/v1/schools")).andExpect(status().isUnauthorized());

        assertThat(TenantContext.current()).isNull();
        assertThat(TenantContext.schoolId()).isNull();
    }

    @Test
    void theContextOutsideARequestHasNoTenant() {
        assertThat(TenantContext.current()).isNull();
        assertThat(TenantContext.schoolId()).isNull();
        assertThat(TenantContext.employeeId()).isNull();
        assertThat(TenantContext.username()).isNull();
        assertThat(TenantContext.roles()).isEmpty();
        assertThat(TenantContext.isPlatformOwner()).isFalse();
    }

    /**
     * The filter reads the tenant and the authorities from the claims it has already verified.
     */
    private void authenticate(String username, Set<String> roles) {
        Claims claims = Mockito.mock(Claims.class);
        lenient().when(claims.getSubject()).thenReturn(username);
        lenient().when(claims.get("schoolId")).thenReturn(1);
        lenient().when(claims.get("empId")).thenReturn(1);
        lenient().when(claims.get("roles")).thenReturn(new ArrayList<>(roles));
        lenient().when(tokenProvider.parseClaims(anyString())).thenReturn(claims);
        lenient().when(userDetailsService.loadUserByUsername(username))
                .thenReturn(new Caller(username, roles));
    }

    private static final class Caller implements UserDetails {

        private final String username;
        private final Set<String> roles;

        private Caller(String username, Set<String> roles) {
            this.username = username;
            this.roles = roles;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            List<GrantedAuthority> authorities = new ArrayList<>();
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(role)));
            return authorities;
        }

        @Override
        public String getPassword() {
            return "";
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
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}
