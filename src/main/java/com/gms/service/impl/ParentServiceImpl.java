package com.gms.service.impl;

import com.gms.model.entity.*;
import com.gms.enums.RoleEnum;
import com.gms.exception.NotFoundException;
import com.gms.model.request.ParentRequest;
import com.gms.model.response.ParentResponse;
import com.gms.model.response.StudentResponse;
import com.gms.repository.ParentRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.ParentService;
import com.gms.util.SecurityUtil;
import com.gms.util.UsernameGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ParentServiceImpl extends AbstractCRUDService<Parent, Integer> implements ParentService {

    private final ParentRepository parentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final PasswordEncoder passwordEncoder;

    public ParentServiceImpl(ParentRepository parentRepository, StudentRepository studentRepository, UserRepository userRepository, SchoolRepository schoolRepository, PasswordEncoder passwordEncoder) {
        super(parentRepository);
        this.parentRepository = parentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public ResponseEntity<ParentResponse> create(ParentRequest request, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        Parent parent = new Parent();
        Parent savedParent = parentRepository.save(parent);
        createUserForParent(savedParent, schoolId);
        return ResponseEntity.ok(toResponse(savedParent));
    }

    public ParentResponse createParentFromRequest(ParentRequest request, Parent parent, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            throw new IllegalArgumentException("Employee ID or School ID cannot be null");
        }
        
        // This method is a placeholder to match the pattern, but Parent creation is handled differently
        return toResponse(parent);
    }

    public ResponseEntity<ParentResponse> update(ParentRequest request, Integer empId, Integer schoolId) {
        // Parent update is handled differently, returning not implemented
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    public ResponseEntity<?> toggleParent(Integer id, Boolean isActive, Integer schoolId, Integer empId) {
        // Parent doesn't have a status field, returning not implemented
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public List<Parent> findBySchool(Integer schoolId) {
        // For now, return all parents since Parent doesn't have direct school relationship
        return parentRepository.findAll();
    }

    @Override
    public ResponseEntity<ParentResponse> createParent(ParentRequest parentRequest, Integer schoolId) {
        Parent parent = new Parent();
        BeanUtils.copyProperties(parentRequest, parent);

        List<Student> students = studentRepository.findAllById(parentRequest.getStudentIds());
        if (students.isEmpty() || students.size() != parentRequest.getStudentIds().size()) {
            throw new EntityNotFoundException("One or more students not found or list is empty");
        }
        parent.setStudents(students);

        Parent savedParent = parentRepository.save(parent);

        createUserForParent(savedParent, schoolId);

        return ResponseEntity.ok(toResponse(savedParent));
    }

    @Override
    public List<StudentResponse> getMyChildren() {
        String username = SecurityUtil.getUsernameFromToken();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Parent parent = user.getParent();
        if (parent == null) {
            throw new NotFoundException("PARENT_NOT_FOUND", "Parent profile not found for this user");
        }

        return parent.getStudents().stream()
                .map(this::toStudentResponse)
                .collect(Collectors.toList());
    }

    private void createUserForParent(Parent parent, Integer schoolId) {
        if (userRepository.existsByEmail(parent.getEmail())) {
            throw new IllegalStateException("A user with this email already exists.");
        }

        User user = new User();
        user.setSchool(parent.getStudents().get(0).getSchool()); // Assuming all students are in the same school
        user.setParent(parent);
        user.setFullName(parent.getFirstName() + " " + parent.getLastName());
        user.setEmail(parent.getEmail());
        user.setUsername(UsernameGenerator.generateUsername(parent.getFirstName(), parent.getLastName(), userRepository));
        user.setPassword(passwordEncoder.encode("welcome123")); // Default temporary password
        user.setRoles(Collections.singleton(RoleEnum.PARENT.name()));
        user.setRequirePasswordChange(true);
        user.setEnabled(true);

        userRepository.save(user);
        
        parent.setUser(user);
        parentRepository.save(parent);
    }

    private ParentResponse toResponse(Parent parent) {
        List<StudentResponse> studentResponses = parent.getStudents().stream()
                .map(this::toStudentResponse)
                .collect(Collectors.toList());

        return new ParentResponse(
                parent.getId(),
                parent.getFirstName(),
                parent.getLastName(),
                parent.getEmail(),
                parent.getPhoneNumber(),
                studentResponses
        );
    }

    private StudentResponse toStudentResponse(Student s) {
        return new StudentResponse(
                s.getId(),
                s.getSchoolId(),
                s.getClassroomId(),
                s.getSectionId(),
                s.getStudentId(),
                s.getFirstName(),
                s.getLastName(),
                s.getEmail(),
                s.getPhoneNumber(),
                s.getDateOfBirth(),
                s.getGender(),
                s.getStatus()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ParentResponse getParentProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Parent parent = user.getParent();
        if (parent == null) {
            throw new NotFoundException("PARENT_NOT_FOUND", "Parent profile not found for this user");
        }

        return toResponse(parent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentResponse> getChildren(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Parent parent = user.getParent();
        if (parent == null) {
            throw new NotFoundException("PARENT_NOT_FOUND", "Parent profile not found for this user");
        }

        return parent.getStudents().stream()
                .map(this::toStudentResponse)
                .collect(Collectors.toList());
    }
}