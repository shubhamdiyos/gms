package com.gms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gms.config.CorsConfig;
import com.gms.config.SecurityConfig;
import com.gms.config.SuperAdminBootstrapProperties;
import com.gms.model.entity.User;
import com.gms.security.JwtTokenProvider;
import com.gms.service.ClassroomService;
import com.gms.service.ParentService;
import com.gms.service.SubjectService;
import com.gms.service.UserService;
import io.jsonwebtoken.Claims;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * An empty collection answers 200 with {@code []}, never 204.
 *
 * <p>Measured live on 2026-11-01 (DB {@code gms_p2c}, school 4 before its first classroom): these six
 * reads answered {@code 204 No Content} for a school with no rows. A 204 carries no body, the SPA's
 * RTK Query layer hands the page {@code null}, and a destructuring default {@code data: x = []} only
 * fires on {@code undefined} — so {@code x.map(...)} threw and unmounted the entire route, with no
 * error boundary to catch it. "No rows yet" is a successful read of an empty array, not a body the
 * client has to guess the shape of.</p>
 *
 * <p>The six endpoints here are the ones that had the defect. The last test is the gate for the other
 * twenty-nine controllers: it fails the build the next time any {@code @GetMapping} handler answers
 * {@code noContent()}, because the same crash would come back with the next feature page.</p>
 */
@WebMvcTest(controllers = {
        ClassroomController.class,
        SubjectController.class,
        UserController.class,
        ParentController.class})
@Import({SecurityConfig.class, CorsConfig.class})
@ActiveProfiles("test")
class EmptyCollectionContractTest {

    private static final String BEARER = "Bearer test-token";
    private static final Pattern GET_MAPPING = Pattern.compile("@GetMapping");
    private static final Pattern ANY_MAPPING = Pattern.compile("@(Get|Post|Put|Patch|Delete)Mapping");
    private static final Pattern METHOD_NAME =
            Pattern.compile("(?:public|private|protected)\\s+\\S+\\s+(\\w+)\\(");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private ClassroomService classroomService;

    @MockBean
    private SubjectService subjectService;

    @MockBean
    private UserService userService;

    @MockBean
    private ParentService parentService;

    @MockBean
    private SuperAdminBootstrapProperties superAdminBootstrapProperties;

    @AfterEach
    void noContextSurvivesTheTest() {
        com.gms.security.TenantContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/classrooms",
            "/api/v1/subjects",
            "/api/v1/users",
            "/api/v1/parents"})
    void anEmptySchoolReadsAnEmptyArrayNotAMissingBody(String path) throws Exception {
        authenticate("admin", Set.of("ADMIN"));
        when(classroomService.findBySchool()).thenReturn(List.of());
        when(subjectService.findBySchool()).thenReturn(List.of());
        when(userService.findBySchool()).thenReturn(List.of());
        when(parentService.findBySchool()).thenReturn(List.of());

        // Before the fix: 204, 0-byte body, and the page that consumed it crashed on null.map.
        mockMvc.perform(get(path).header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @Test
    void aTeacherWithNoAssignmentsStillReadsAnEmptyArray() throws Exception {
        authenticate("teacher", Set.of("TEACHER"));
        when(classroomService.findClassroomsForAuthenticatedTeacher()).thenReturn(List.of());
        when(subjectService.findSubjectsForAuthenticatedTeacher()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/classrooms/teacher/assigned").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));

        mockMvc.perform(get("/api/v1/subjects/teacher/assigned").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    /**
     * The one place 204 is correct is a delete; the one place it is not is a read. Every
     * {@code @GetMapping} handler in the package must keep its body free of {@code noContent()}.
     */
    @Test
    void noGetMappingHandlerAnywhereAnswersNoContent() throws Exception {
        Path controllerDir = Path.of("src/main/java/com/gms/controller");
        assertThat(controllerDir)
                .as("the controller sources must be where surefire runs from, or this gate is blind")
                .exists();

        List<String> offenders = new ArrayList<>();
        try (var files = Files.walk(controllerDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith("Test.java")) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                for (String block : getMappingBlocks(source)) {
                    if (block.contains("noContent")) {
                        offenders.add(name + ": " + firstSignature(block));
                    }
                }
            }
        }
        assertThat(offenders)
                .as("a GET handler must never answer 204 -- an empty body is read as null by the SPA")
                .isEmpty();
    }

    /** Each region of the source from one {@code @GetMapping} up to the next mapping annotation. */
    private static List<String> getMappingBlocks(String source) {
        Matcher start = GET_MAPPING.matcher(source);
        List<String> blocks = new ArrayList<>();
        int from = 0;
        while (start.find(from)) {
            Matcher next = ANY_MAPPING.matcher(source);
            blocks.add(next.find(start.end()) ? source.substring(start.start(), next.start())
                    : source.substring(start.start()));
            from = start.end();
        }
        return blocks;
    }

    private static String firstSignature(String block) {
        Matcher m = METHOD_NAME.matcher(block);
        return m.find() ? m.group(1) : "<unknown method>";
    }

    /**
     * Makes the JWT filter resolve a caller with the given authorities, in school 1.
     *
     * <p>The tenant and the authorities are read from the claims the filter has already verified, so the
     * stub is on {@code claims.get(...)} — the same place the production path reads them.</p>
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

        // Stage F: The Caller principal is not a GmsUserDetails, so PasswordChangeEnforcementFilter
        // falls back to UserService.findByUsername(). Mock it to return a user that does NOT require
        // a password change, so the filter allows the request through.
        User fallbackUser = new User();
        fallbackUser.setUsername(username);
        fallbackUser.setRequirePasswordChange(false);
        fallbackUser.setEnabled(true);
        fallbackUser.setAccountLocked(false);
        fallbackUser.setPasswordExpired(false);
        fallbackUser.setRoles(roles);
        lenient().when(userService.findByUsername(username))
                .thenReturn(Optional.of(fallbackUser));
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
