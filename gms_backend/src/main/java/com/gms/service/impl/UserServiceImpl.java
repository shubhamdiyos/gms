package com.gms.service.impl;

import com.gms.enums.RoleEnum;
import com.gms.model.entity.Employee;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.exception.NotFoundException;
import com.gms.model.request.UserRequest;
import com.gms.model.response.UserResponse;
import com.gms.repository.UserRepository;
import com.gms.service.*;
import com.gms.util.PasswordValidator;
import com.gms.util.SecurityUtil;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class UserServiceImpl extends AbstractCRUDService<User, Integer> implements UserService {

    private final UserRepository userRepository;
    private final SchoolService schoolService;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, SchoolService schoolService, PasswordEncoder passwordEncoder) {
        super(userRepository);
        this.userRepository = userRepository;
        this.schoolService = schoolService;
        this.passwordEncoder = passwordEncoder;
    }

    public User create(User entity) {
        return userRepository.save(entity);
    }

    @Override
    public ResponseEntity<UserResponse> create(UserRequest request, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            return ResponseEntity.badRequest().build();
        }

        User user = createUserFromRequest(request, new User(), empId, schoolId);
        User saved = this.create(user);
        return ResponseEntity.ok(toResponse(saved));
    }

    public User createUserFromRequest(UserRequest request, User user, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            throw new IllegalArgumentException("Employee ID or School ID cannot be null");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        // Find school and creator user
        School school = schoolService.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        
        User creatorUser = this.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        org.springframework.beans.BeanUtils.copyProperties(request, user, "schoolId");
        user.setSchool(school);
        user.setUpdatedBy(creatorUser);
        user.setCreatedBy(creatorUser);
        if (!PasswordValidator.isValid(request.getPassword())) {
            throw new IllegalArgumentException("Password does not meet policy requirements");
        }
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(true);
        user.setRequirePasswordChange(true); // Force password change on first login

        // Ensure username is present; default to email if absent (to satisfy NOT NULL constraint)
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername(request.getEmail());
        }

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            user.setRoles(authoriseRoles(request.getRoles()));
        }

        return user;
    }

    /**
     * Validates a role set coming from the API and rejects escalations.
     *
     * <p>Role names are checked against {@link RoleEnum} so a typo can no longer mint an
     * unauthorisable role, and SUPERADMIN may only be granted by a caller who already holds it
     * (finding AP-4: any school ADMIN could create a platform owner, or promote themselves).</p>
     *
     * @throws IllegalArgumentException when a role is unknown or the caller may not grant it
     */
    private Set<String> authoriseRoles(Set<String> requested) {
        Set<String> validated = new HashSet<>();
        for (String code : requested) {
            RoleEnum role = RoleEnum.fromCode(code);   // throws for anything outside the vocabulary
            if (role.isSystemLevel() && !currentCallerHas(RoleEnum.SUPERADMIN)) {
                throw new IllegalArgumentException("Only a SUPERADMIN may grant the SUPERADMIN role");
            }
            validated.add(role.getCode());
        }
        return validated;
    }

    private static boolean currentCallerHas(RoleEnum role) {
        Set<String> callerRoles = SecurityUtil.getRolesFromToken();
        return callerRoles != null && callerRoles.contains(role.getCode());
    }

    @Override
    public ResponseEntity<UserResponse> update(UserRequest request, Integer empId, Integer schoolId) {
        if (request.getId() == null) {
            throw new IllegalArgumentException("User ID must be provided for update");
        }

        User user = userRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        // Enforce tenant boundary
        if (user.getSchool() == null || !user.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        User updaterUser = this.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        org.springframework.beans.BeanUtils.copyProperties(
                request,
                user,
                "id", "createdBy", "createdAt", "school", "schoolId", "updatedBy", "updatedAt",
                "password", "version", "roles"
        );
        user.setUpdatedBy(updaterUser);
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            user.setRoles(authoriseRoles(request.getRoles()));
        }

        // Only update password if provided
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            if (!PasswordValidator.isValid(request.getPassword())) {
                throw new IllegalArgumentException("Password does not meet policy requirements");
            }
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        User saved = this.create(user);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found with ID: " + id));
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> listBySchool(Integer schoolId, Pageable pageable) {
        Page<User> users = userRepository.findAllBySchool_Id(schoolId, pageable);
        return users.map(this::toResponse);
    }

    @Override
    public List<User> findBySchool(Integer schoolId) {
        return userRepository.findBySchool_Id(schoolId);
    }
    
    // Additional methods needed for service-to-service communication
    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    @Override
    public Optional<User> findByEmployee_Id(Integer employeeId) {
        return userRepository.findByEmployee_Id(employeeId);
    }
    
    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
    
    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
    
    @Override
    public User save(User user) {
        return userRepository.save(user);
    }
    
    /**
     * {@inheritDoc}
     *
     * <p>Associations are read defensively: {@code school} and the audit columns are referenced by
     * identifier only, which Hibernate answers from the proxy without a session, while
     * {@code employee} needs its row and is therefore mapped only when it is already loaded.</p>
     */
    @Override
    public UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        org.springframework.beans.BeanUtils.copyProperties(user, response, "createdBy", "updatedBy");
        if (user.getSchool() != null) {
            response.setSchoolId(user.getSchool().getId());
        }
        if (user.getCreatedBy() != null) {
            response.setCreatedBy(user.getCreatedBy().getId());
        }
        if (user.getUpdatedBy() != null) {
            response.setUpdatedBy(user.getUpdatedBy().getId());
        }
        Employee employee = user.getEmployee();
        if (employee != null && org.hibernate.Hibernate.isInitialized(employee)) {
            response.setEmployeeId(employee.getEmployeeId());
            response.setDesignation(employee.getDesignation());
        }
        return response;
    }

    private String generateUniqueUsername(String base) {
        String candidate = base;
        int counter = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + counter;
            counter++;
        }
        return candidate;
    }
}
