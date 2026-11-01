package com.gms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gms.config.CorsConfig;
import com.gms.config.SecurityConfig;
import com.gms.model.entity.Student;
import com.gms.repository.StudentAdmissionRepository;
import com.gms.security.JwtTokenProvider;
import com.gms.service.ClassroomService;
import com.gms.service.EmployeeService;
import com.gms.service.ParentService;
import com.gms.service.SchoolService;
import com.gms.service.StudentService;
import com.gms.service.SubjectService;
import io.jsonwebtoken.Claims;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * ADR-023's verification, executed rather than asserted.
 *
 * <p>Three properties are checked here, and each one used to be false:</p>
 * <ol>
 *   <li><b>No endpoint arrives by inheritance.</b> Every controller in the slice must extend
 *       {@code Object}, and the route inventory is read from the handler mapping rather than
 *       counted from a document.</li>
 *   <li><b>Every declared operation is guarded.</b> Each handler method carries
 *       {@code @PreAuthorize}; the inherited ones did not, and they were reachable by any
 *       authenticated caller the URL layer admitted.</li>
 *   <li><b>The reads and deletes that remain are tenant-scoped.</b> A caller of school 1 asking
 *       for school 2's student is refused, and a teacher — whom the URL layer admits to
 *       {@code /subjects/**} — cannot delete a subject.</li>
 * </ol>
 */
@WebMvcTest(controllers = {
        ParentController.class,
        StudentController.class,
        EmployeeController.class,
        SubjectController.class,
        ClassroomController.class,
        SchoolController.class})
@Import({SecurityConfig.class, CorsConfig.class})
class EndpointInheritanceTest {

    private static final String BEARER = "Bearer test-token";
    private static final String CONTROLLER_PACKAGE = "com.gms.controller";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private ParentService parentService;

    @MockBean
    private StudentService studentService;

    @MockBean
    private EmployeeService employeeService;

    @MockBean
    private SubjectService subjectService;

    @MockBean
    private ClassroomService classroomService;

    @MockBean
    private SchoolService schoolService;

    @MockBean
    private StudentAdmissionRepository studentAdmissionRepository;

    // ── 1. The endpoint inventory ────────────────────────────────────────────

    @Test
    void noControllerInheritsItsEndpoints() {
        assertThat(ourHandlers())
                .isNotEmpty()
                .allSatisfy(handler -> assertThat(handler.getBeanType().getSuperclass())
                        .as("%s must not extend a base controller", handler.getBeanType().getSimpleName())
                        .isEqualTo(Object.class));
    }

    @Test
    void everyDeclaredOperationCarriesItsOwnGuard() {
        assertThat(ourHandlers())
                .isNotEmpty()
                .allSatisfy(handler -> assertThat(handler.getMethodAnnotation(PreAuthorize.class))
                        .as("%s#%s must declare @PreAuthorize",
                                handler.getBeanType().getSimpleName(), handler.getMethod().getName())
                        .isNotNull());
    }

    @Test
    void theInheritedDeleteRoutesAreGone() {
        assertThat(routesFor(RequestMethod.DELETE))
                .contains("/api/v1/subjects/{id}", "/api/v1/classrooms/{id}")
                .doesNotContain("/api/v1/students/{id}", "/api/v1/parents/{id}",
                        "/api/v1/employees/{id}", "/api/v1/schools/{id}");
    }

    @Test
    void theInheritedReadRoutesAreGone() {
        assertThat(routesFor(RequestMethod.GET))
                .contains("/api/v1/students/{id}", "/api/v1/employees/{id}", "/api/v1/subjects/{id}",
                        "/api/v1/classrooms/{id}", "/api/v1/schools/{id}")
                .doesNotContain("/api/v1/parents/{id}");
    }

    // ── 2. An unauthenticated caller ─────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/parents/1", "/api/v1/students/1", "/api/v1/employees/1",
            "/api/v1/subjects/1", "/api/v1/classrooms/1", "/api/v1/schools/1"})
    void getByIdWithoutATokenIsUnauthorized(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/parents/1", "/api/v1/students/1", "/api/v1/employees/1",
            "/api/v1/subjects/1", "/api/v1/classrooms/1", "/api/v1/schools/1"})
    void deleteWithoutATokenIsUnauthorized(String path) throws Exception {
        mockMvc.perform(delete(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/parents/1", "/api/v1/students/1", "/api/v1/employees/1",
            "/api/v1/subjects/1", "/api/v1/classrooms/1", "/api/v1/schools/1"})
    void getByIdWithNoRoleClaimIsForbidden(String path) throws Exception {
        authenticate("nobody", Set.of());
        mockMvc.perform(get(path).header("Authorization", BEARER)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/parents/1", "/api/v1/students/1", "/api/v1/employees/1",
            "/api/v1/subjects/1", "/api/v1/classrooms/1", "/api/v1/schools/1"})
    void deleteWithNoRoleClaimIsForbidden(String path) throws Exception {
        authenticate("nobody", Set.of());
        mockMvc.perform(delete(path).header("Authorization", BEARER)).andExpect(status().isForbidden());
    }

    // ── 3. The tenant boundary ───────────────────────────────────────────────

    @Test
    void aSchoolAdministratorCannotReadAnotherSchoolsStudent() throws Exception {
        authenticate("admin", Set.of("ADMIN"));
        when(studentService.getById(2))
                .thenThrow(new IllegalArgumentException("Student does not belong to current school"));

        mockMvc.perform(get("/api/v1/students/2").header("Authorization", BEARER))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSchoolAdministratorCanReadItsOwnStudent() throws Exception {
        authenticate("admin", Set.of("ADMIN"));
        when(studentService.getById(1)).thenReturn(new Student());

        mockMvc.perform(get("/api/v1/students/1").header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    void aTeacherCannotDeleteASubject() throws Exception {
        // The URL layer admits TEACHER to /subjects/**. Before ADR-023 the delete was inherited,
        // carried no @PreAuthorize and no tenant predicate, so the teacher could delete any
        // subject in any school.
        authenticate("teacher", Set.of("TEACHER"));

        mockMvc.perform(delete("/api/v1/subjects/1").header("Authorization", BEARER))
                .andExpect(status().isForbidden());

        verify(subjectService, never()).deleteWithinSchool(1);
    }

    @Test
    void anAdministratorCannotDeleteAnotherSchoolsSubject() throws Exception {
        authenticate("admin", Set.of("ADMIN"));
        when(subjectService.deleteWithinSchool(2)).thenReturn(false);

        mockMvc.perform(delete("/api/v1/subjects/2").header("Authorization", BEARER))
                .andExpect(status().isNotFound());
    }

    @Test
    void anAdministratorCanDeleteItsOwnSubject() throws Exception {
        authenticate("admin", Set.of("ADMIN"));
        when(subjectService.deleteWithinSchool(1)).thenReturn(true);

        mockMvc.perform(delete("/api/v1/subjects/1").header("Authorization", BEARER))
                .andExpect(status().isNoContent());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Makes the JWT filter resolve a caller with the given authorities, in school 1.
     *
     * <p>The tenant and the authorities are read from the claims the filter has already verified, so the
     * stub is on {@code claims.get(...)} — the same place the production path reads them. Before the
     * tenant context, the filter and the controllers each parsed the token separately and the test had
     * to stub both.</p>
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

    /** The handlers this repository declares, with the framework's own error controller excluded. */
    private List<HandlerMethod> ourHandlers() {
        return handlerMapping.getHandlerMethods().values().stream()
                .filter(handler -> handler.getBeanType().getName().startsWith(CONTROLLER_PACKAGE))
                .collect(Collectors.toList());
    }

    private Set<String> routesFor(RequestMethod method) {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getKey().getMethodsCondition().getMethods().contains(method))
                .map(EndpointInheritanceTest::patternOf)
                .collect(Collectors.toSet());
    }

    private static String patternOf(Map.Entry<RequestMappingInfo, HandlerMethod> entry) {
        return entry.getKey().getPathPatternsCondition().getPatternValues().stream()
                .findFirst().orElse("");
    }

    /**
     * A principal with exactly the authorities it was given — including none at all, which
     * {@code User.withUsername(...)} refuses to build.
     */
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
