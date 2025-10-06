package com.gms.service.impl;

import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.exception.NotFoundException;
import com.gms.model.request.UserRequest;
import com.gms.model.response.UserResponse;
import com.gms.repository.UserRepository;
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

import java.util.List;
import java.util.Optional;

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
        return ResponseEntity.ok(mapToResponse(saved));
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
            user.setRoles(request.getRoles());
        }

        return user;
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
                "id", "createdBy", "createdAt", "school", "schoolId", "updatedBy", "updatedAt", "password", "version"
        );
        user.setUpdatedBy(updaterUser);

        // Only update password if provided
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            if (!PasswordValidator.isValid(request.getPassword())) {
                throw new IllegalArgumentException("Password does not meet policy requirements");
            }
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        User saved = this.create(user);
        return ResponseEntity.ok(mapToResponse(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found with ID: " + id));
        return mapToResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> listBySchool(Integer schoolId, Pageable pageable) {
        Page<User> users = userRepository.findAllBySchool_Id(schoolId, pageable);
        return users.map(this::mapToResponse);
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
    
    private UserResponse mapToResponse(User user) {
        UserResponse response = new UserResponse();
        org.springframework.beans.BeanUtils.copyProperties(user, response);
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
