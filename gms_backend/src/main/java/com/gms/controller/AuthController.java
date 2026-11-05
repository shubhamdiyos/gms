package com.gms.controller;

import com.gms.enums.RoleEnum;
import com.gms.model.entity.Employee;
import com.gms.model.entity.User;
import com.gms.model.request.LoginRequest;
import com.gms.model.request.ChangePasswordRequest;
import com.gms.model.request.SelfRegisterRequest;
import com.gms.model.response.AuthTokenResponse;
import com.gms.security.JwtTokenProvider;
import com.gms.security.TenantContext;
import com.gms.service.EmployeeService;
import com.gms.service.LoginAttemptService;
import com.gms.service.UserService;
import com.gms.util.Constants;
import com.gms.util.PasswordValidator;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping(Constants.API_BASE_V1 + "/auth")
public class AuthController {

    private static final String AUTH_COOKIE_NAME = "gms_auth";
    private static final String COOKIE_PATH = "/";
    private static final int COOKIE_MAX_AGE_SECONDS = 7 * 24 * 60 * 60; // 7 days

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    private final EmployeeService employeeService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider, 
                         UserService userService, EmployeeService employeeService, PasswordEncoder passwordEncoder,
                         LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userService = userService;
        this.employeeService = employeeService;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * Unified login endpoint for all users including superadmin, admin, teacher, parent, and student.
     * Accepts username and password for authentication.
     *
     * <p>No part of the credential is logged (mandate §7.4). Every failure answers with the same bare
     * 401 so the endpoint cannot be used to enumerate accounts, and repeated failures lock the account
     * through {@link LoginAttemptService}.</p>
     *
     * <p>On success, sets an HttpOnly cookie containing the JWT token. The cookie attributes are:</p>
     * <ul>
     *   <li>HttpOnly: prevents JavaScript access (XSS mitigation)</li>
     *   <li>Secure: only sent over HTTPS (production requirement)</li>
     *   <li>SameSite=Lax: CSRF mitigation for cross-site requests</li>
     *   <li>Path=/: available to all endpoints</li>
     *   <li>Max-Age: matches JWT validity period</li>
     * </ul>
     *
     * @param request LoginRequest containing username and password
     * @param response HttpServletResponse for setting the authentication cookie
     * @return AuthTokenResponse with user information (token omitted, now in cookie)
     */
    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                                    HttpServletResponse response) {
        Optional<User> userOptional = userService.findByUsername(request.getUsername());
        if (userOptional.isEmpty()) {
            log.debug("Login for unknown username: {}", request.getUsername());
            return ResponseEntity.status(401).build();
        }
        User domainUser = userOptional.get();

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            loginAttemptService.onSuccess(domainUser);

            UserDetails principal = (UserDetails) auth.getPrincipal();
            // The authorities come back as bare role codes (CustomUserDetailsService grants them with
            // no ROLE_ prefix); the replace is defensive for a principal loaded any other way. The
            // set's iteration order is unspecified, so the token's role order is fixed where the claim
            // is written — JwtTokenProvider.rolesClaim sorts them into the array (Stage 3 C-6), which
            // is what lets a client read the claim as data instead of guessing a precedence.
            Set<String> roles = principal.getAuthorities().stream()
                    .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                    .collect(java.util.stream.Collectors.toSet());

            Integer schoolId = (domainUser.getSchool() != null) ? domainUser.getSchool().getId() : null;
            // For superadmin and other special users, use user ID as employee ID if no employee is associated
            Integer empId = (domainUser.getEmployee() != null) ? domainUser.getEmployee().getId() : domainUser.getId();
            String token = tokenProvider.createToken(domainUser.getUsername(), roles, schoolId, empId,
                    domainUser.getId());

            // Set HttpOnly cookie with JWT token
            setAuthCookie(response, token, (int) tokenProvider.getValiditySeconds());

            // Response body no longer includes the token (it's in the cookie), but we keep the
            // expiresIn and requirePasswordChange fields for client-side session management
            AuthTokenResponse payload = new AuthTokenResponse(null,
                    (int) tokenProvider.getValiditySeconds(), domainUser.isRequirePasswordChange());
            return ResponseEntity.ok(payload);
        } catch (AuthenticationException e) {
            loginAttemptService.onFailure(domainUser);
            log.debug("Login failed for {}: {}", request.getUsername(), e.getClass().getSimpleName());
            return ResponseEntity.status(401).build();
        }
    }

    /**
     * Logout endpoint that clears the authentication cookie.
     *
     * <p>This endpoint is anonymous (no authentication required) because the client may call it
     * when the token is expired or invalid. Clearing the cookie is sufficient for logout since
     * the JWT is stateless and short-lived.</p>
     *
     * @param response HttpServletResponse for clearing the authentication cookie
     * @return 200 OK on successful logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        clearAuthCookie(response);
        return ResponseEntity.ok().build();
    }

    /**
     * Sets the authentication cookie with security attributes.
     *
     * <p>SameSite is set to Lax via the Set-Cookie header attribute because the Jakarta Servlet
     * Cookie API does not expose SameSite directly. The cookie is sent with Secure flag in
     * production (HTTPS required) and HttpOnly to prevent JavaScript access.</p>
     */
    private void setAuthCookie(HttpServletResponse response, String token, int maxAgeSeconds) {
        // Use manual Set-Cookie header to include SameSite attribute (not supported by Cookie API)
        response.addHeader("Set-Cookie",
                String.format("%s=%s; Path=%s; Max-Age=%d; HttpOnly; Secure; SameSite=Lax",
                        AUTH_COOKIE_NAME, token, COOKIE_PATH, maxAgeSeconds));
    }

    /**
     * Clears the authentication cookie by setting Max-Age to 0.
     */
    private void clearAuthCookie(HttpServletResponse response) {
        // Use manual Set-Cookie header to include SameSite attribute (not supported by Cookie API)
        response.addHeader("Set-Cookie",
                String.format("%s=; Path=%s; Max-Age=0; HttpOnly; Secure; SameSite=Lax",
                        AUTH_COOKIE_NAME, COOKIE_PATH));
    }

    /**
     * CSRF token endpoint for SPA clients.
     *
     * <p>The frontend calls this endpoint after login to retrieve a CSRF token. The token is
     * automatically set in the XSRF-TOKEN cookie by Spring Security's CsrfTokenRepository, and
     * this endpoint returns it in the response body for convenience. The frontend must send this
     * token in the X-XSRF-TOKEN header on all state-changing requests (POST, PUT, PATCH, DELETE).</p>
     *
     * @param request HttpServletRequest to access the CSRF token
     * @return Map with the CSRF token value
     */
    @GetMapping("/csrf-token")
    public ResponseEntity<java.util.Map<String, String>> getCsrfToken(HttpServletRequest request) {
        org.springframework.security.web.csrf.CsrfToken csrfToken = 
            (org.springframework.security.web.csrf.CsrfToken) request.getAttribute(
                org.springframework.security.web.csrf.CsrfToken.class.getName());
        
        if (csrfToken == null) {
            // CSRF token should always be present due to Spring Security filter
            return ResponseEntity.status(500).build();
        }
        
        return ResponseEntity.ok(java.util.Map.of(
            "token", csrfToken.getToken(),
            "headerName", csrfToken.getHeaderName(),
            "parameterName", csrfToken.getParameterName()
        ));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String username = TenantContext.username();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }
        
        Optional<User> userOptional = userService.findByUsername(username);
        User user = userOptional.orElse(null);
        
        if (user == null) {
            return ResponseEntity.status(404).build();
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            return ResponseEntity.status(400).build();
        }
        if (!PasswordValidator.isValid(request.getNewPassword())) {
            return ResponseEntity.status(400).build();
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setRequirePasswordChange(false);
        userService.save(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/self-register")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Void> selfRegister(@Valid @RequestBody SelfRegisterRequest request) {
        // Validate employee exists and belongs to provided school
        Optional<Employee> employeeOptional = employeeService.findById(request.getEmpId());
        Employee employee = employeeOptional.orElse(null);
        
        if (employee == null || employee.getSchool() == null || !employee.getSchool().getId().equals(request.getSchoolId())) {
            return ResponseEntity.status(400).build();
        }

        // Ensure username is unique and employee does not already have a user
        if (userService.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(409).build();
        }
        if (userService.existsByEmployee_Id(employee.getId())) {
            return ResponseEntity.status(409).build();
        }

        if (!PasswordValidator.isValid(request.getPassword())) {
            return ResponseEntity.status(400).build();
        }

        // Create user linked to employee and school
        User user = new User();
        user.setSchool(employee.getSchool());
        user.setEmployee(employee);
        user.setFullName(employee.getFullName());
        user.setEmail(employee.getEmail());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(true);
        user.setRequirePasswordChange(false);
        // TEACHER, deliberately and narrowly (Stage 3 C-6, SEC-17's EMPLOYEE half).
        //
        // This endpoint registers a login for an Employee row that already exists — school staff. The
        // request DTO (SelfRegisterRequest) carries no role field, so the role is the server's choice
        // and must be the least-privilege one in RoleEnum that still describes staff: TEACHER.
        //   * ADMIN / SUPERADMIN / ACCOUNTANT would be an escalation handed out by a form with no role
        //     input, and ACCOUNTANT is the money surface this very card made issuable through
        //     /users/create, where an administrator chooses it knowingly.
        //   * STUDENT / PARENT are wrong in kind: their self-service routes resolve through the
        //     student_id / parent_id pointers on User, which this account does not have.
        //
        // The literal "EMPLOYEE" this used to write is gone: no guard names it, so the account could
        // reach nothing while still burning the username and the existsByEmployee_Id slot that a
        // correctly provisioned account for the same employee needs. The code is routed through the
        // same validator /users/create uses, so anything outside RoleEnum now answers 400 before the
        // username is taken rather than minting another unreachable login.
        user.setRoles(userService.authoriseRoles(Set.of(RoleEnum.TEACHER.getCode())));

        userService.save(user);
        return ResponseEntity.ok().build();
    }
}