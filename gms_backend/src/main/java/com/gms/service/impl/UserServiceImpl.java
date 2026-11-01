package com.gms.service.impl;

import com.gms.config.TenantContextAuditorAware;
import com.gms.enums.RoleEnum;
import com.gms.model.entity.Employee;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.exception.NotFoundException;
import com.gms.model.request.UserRequest;
import com.gms.model.response.UserResponse;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.*;
import com.gms.util.PasswordValidator;
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
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final SchoolService schoolService;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, SchoolService schoolService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.schoolService = schoolService;
        this.passwordEncoder = passwordEncoder;
    }

    public User create(User entity) {
        return userRepository.save(entity);
    }

    @Override
    public ResponseEntity<UserResponse> create(UserRequest request) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            return ResponseEntity.badRequest().build();
        }

        User user = createUserFromRequest(request, new User());
        User saved = this.create(user);
        return ResponseEntity.ok(toResponse(saved));
    }

    public User createUserFromRequest(UserRequest request, User user) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            throw new IllegalArgumentException("Employee ID or School ID cannot be null");
        }

        if (userRepository.existsBySchool_IdAndEmail(TenantContext.schoolId(), request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        // Find school and creator user
        School school = schoolService.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // BKL-08: the auditor is the principal the verified token names, not an employee pointer. The
        // empId claim is the employee primary key for staff and the user primary key for the bootstrapped
        // platform owner (AuthController:98), so resolving the creator through it either failed outright
        // for that caller or, when the two id spaces happened to collide, recorded a stranger as the
        // creator. The employee lookup stays as the fallback so the endpoint keeps answering the same way
        // for a caller no identifier resolves at all.
        User employeeCreator = this.findByEmployee_Id().orElse(null);
        User creator = TenantContextAuditorAware.currentAuditor(userRepository).orElse(employeeCreator);
        if (creator == null) {
            throw new EntityNotFoundException("Creator user not found");
        }

        // "id" and the audit/bookkeeping columns are ignored on the way in: a create must not accept a
        // primary key from the body, and a caller must never be able to name the account a row is
        // attributed to. No UserRequest field carries those names today, which is why the list is
        // explicit rather than merely correct -- a future field is exactly what an ignore list is for.
        org.springframework.beans.BeanUtils.copyProperties(
                request, user, "id", "schoolId", "createdBy", "updatedBy", "createdAt", "updatedAt", "version"
        );
        user.setSchool(school);
        user.setUpdatedBy(creator);
        user.setCreatedBy(creator);
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
     * <p>This is the only role validator in the application and it is part of the service contract:
     * {@code /auth/self-register} mints its role through it as well, so no endpoint can write a code
     * outside the vocabulary straight onto the entity (findings SEC-17, EMPLOYEE half, closed by
     * Stage 3 C-6).</p>
     *
     * @throws IllegalArgumentException when a role is unknown or the caller may not grant it
     */
    @Override
    public Set<String> authoriseRoles(Set<String> requested) {
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
        return TenantContext.roles().contains(role.getCode());
    }

    @Override
    public ResponseEntity<UserResponse> update(UserRequest request) {
        if (request.getId() == null) {
            throw new IllegalArgumentException("User ID must be provided for update");
        }

        User user = userRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        User employeeUpdater = this.findByEmployee_Id().orElse(null);
        User updater = TenantContextAuditorAware.currentAuditor(userRepository).orElse(employeeUpdater);
        if (updater == null) {
            throw new EntityNotFoundException("Updater user not found");
        }

        // The ignore list is the audit-loss guard: a copy that named createdBy would write null over
        // the creator, and the row would silently lose the one fact the trail cannot be rebuilt from.
        org.springframework.beans.BeanUtils.copyProperties(
                request,
                user,
                "id", "createdBy", "createdAt", "school", "schoolId", "updatedBy", "updatedAt",
                "password", "version", "roles"
        );
        user.setUpdatedBy(updater);
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
        // The scoped findById already answers the tenancy question: a school administrator gets
        // 404 here for a foreign id, and the platform owner's cross-tenant branch gets the row.
        return userRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found with ID: " + id));
    }

    @Override
    @Transactional
    public boolean deleteWithinSchool(Integer id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return false;
        }
        userRepository.delete(user);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> listBySchool(Pageable pageable) {
        Page<User> users = userRepository.findAllBySchool_Id(TenantContext.schoolId(), pageable);
        return users.map(this::toResponse);
    }

    @Override
    public List<User> findBySchool() {
        return userRepository.findBySchool_Id(TenantContext.schoolId());
    }
    
    // Additional methods needed for service-to-service communication
    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    @Override
    public Optional<User> findByEmployee_Id() {
        return userRepository.findByEmployee_Id(TenantContext.employeeId());
    }

    @Override
    public boolean existsByEmployee_Id(Integer subjectEmployeeId) {
        return userRepository.existsByEmployee_Id(subjectEmployeeId);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsBySchool_IdAndEmail(TenantContext.schoolId(), email);
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
