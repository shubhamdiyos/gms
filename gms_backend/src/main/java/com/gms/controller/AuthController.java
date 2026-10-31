package com.gms.controller;

import com.gms.model.entity.Employee;
import com.gms.model.entity.User;
import com.gms.model.request.LoginRequest;
import com.gms.model.request.ChangePasswordRequest;
import com.gms.model.request.SelfRegisterRequest;
import com.gms.model.response.AuthTokenResponse;
import com.gms.security.JwtTokenProvider;
import com.gms.service.EmployeeService;
import com.gms.service.LoginAttemptService;
import com.gms.service.UserService;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import com.gms.util.PasswordValidator;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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

import java.util.HashSet;
import java.util.Set;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping(Constants.API_BASE_V1 + "/auth")
public class AuthController {

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
     * @param request LoginRequest containing username and password
     * @return AuthTokenResponse with JWT token and user information
     */
    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
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
            Set<String> roles = principal.getAuthorities().stream()
                    .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                    .collect(java.util.stream.Collectors.toSet());

            Integer schoolId = (domainUser.getSchool() != null) ? domainUser.getSchool().getId() : null;
            // For superadmin and other special users, use user ID as employee ID if no employee is associated
            Integer empId = (domainUser.getEmployee() != null) ? domainUser.getEmployee().getId() : domainUser.getId();
            String token = tokenProvider.createToken(domainUser.getUsername(), roles, schoolId, empId);

            AuthTokenResponse payload = new AuthTokenResponse(token,
                    (int) tokenProvider.getValiditySeconds(), domainUser.isRequirePasswordChange());
            return ResponseEntity.ok(payload);
        } catch (AuthenticationException e) {
            loginAttemptService.onFailure(domainUser);
            log.debug("Login failed for {}: {}", request.getUsername(), e.getClass().getSimpleName());
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String username = SecurityUtil.getUsernameFromToken();
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
        if (userService.findByEmployee_Id(employee.getId()).isPresent()) {
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
        Set<String> roles = new HashSet<>();
        roles.add("EMPLOYEE");
        user.setRoles(roles);

        userService.save(user);
        return ResponseEntity.ok().build();
    }
}