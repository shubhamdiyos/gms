package com.gms.controller;

import com.gms.model.entity.Employee;
import com.gms.model.entity.User;
import com.gms.model.request.AuthLoginRequest;
import com.gms.model.request.ChangePasswordRequest;
import com.gms.model.request.UserRequest;
import com.gms.model.request.SelfRegisterRequest;
import com.gms.model.response.AuthTokenResponse;
import com.gms.repository.EmployeeRepository;
import com.gms.repository.UserRepository;
import com.gms.security.JwtTokenProvider;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import com.gms.util.PasswordValidator;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping(Constants.API_BASE_V1 + "/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider, UserRepository userRepository, EmployeeRepository employeeRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login-username")
    public ResponseEntity<AuthTokenResponse> loginWithUsername(@RequestBody UserRequest request) {
        // Resolve username -> email to leverage existing AuthenticationManager configuration
        User domainUser = userRepository.findByUsername(request.getUsername()).orElse(null);
        if (domainUser == null || domainUser.getEmail() == null) {
            return ResponseEntity.status(401).build();
        }

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(domainUser.getEmail(), request.getPassword())
        );

        UserDetails principal = (UserDetails) auth.getPrincipal();
        Set<String> roles = principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .collect(java.util.stream.Collectors.toSet());

        Integer schoolId = (domainUser.getSchool() != null) ? domainUser.getSchool().getId() : null;
        Integer empId = (domainUser.getEmployee() != null) ? domainUser.getEmployee().getId() : null;
        String subject = domainUser.getUsername();
        String token = tokenProvider.createToken(subject, roles, schoolId, empId);

        boolean requirePasswordChange = domainUser.isRequirePasswordChange();
        AuthTokenResponse payload = new AuthTokenResponse(token, 3600, requirePasswordChange);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        
        UserDetails principal = (UserDetails) auth.getPrincipal();
        Set<String> roles = principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .collect(java.util.stream.Collectors.toSet());

        // Load domain User to enrich token with schoolId and empId and get first-login flag
        User domainUser = userRepository.findByEmail(principal.getUsername()).orElse(null);
        Integer schoolId = domainUser != null && domainUser.getSchool() != null ? domainUser.getSchool().getId() : null;
        Integer empId = domainUser != null && domainUser.getEmployee() != null ? domainUser.getEmployee().getId() : null;

        // IMPORTANT: Use domain username as JWT subject to keep consistency with downstream lookups
        // Controllers use SecurityUtil.getUsernameFromToken() and then userRepository.findByUsername()
        // So the subject must be the system username, not the email used for authentication
        String subject = (domainUser != null) ? domainUser.getUsername() : principal.getUsername();
        String token = tokenProvider.createToken(subject, roles, schoolId, empId);

        boolean requirePasswordChange = domainUser != null && domainUser.isRequirePasswordChange();
        return ResponseEntity.ok(new AuthTokenResponse(token, 3600, requirePasswordChange));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String username = SecurityUtil.getUsernameFromToken();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userRepository.findByUsername(username).orElse(null);
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
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/self-register")
    public ResponseEntity<Void> selfRegister(@Valid @RequestBody SelfRegisterRequest request) {
        // Validate employee exists and belongs to provided school
        Employee employee = employeeRepository.findById(request.getEmpId()).orElse(null);
        if (employee == null || employee.getSchool() == null || !employee.getSchool().getId().equals(request.getSchoolId())) {
            return ResponseEntity.status(400).build();
        }

        // Ensure username is unique and employee does not already have a user
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(409).build();
        }
        if (userRepository.findByEmployee_Id(employee.getId()).isPresent()) {
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

        userRepository.save(user);
        return ResponseEntity.ok().build();
    }
}
