package com.gms.service.impl;

import com.gms.config.TenantContextAuditorAware;
import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.Designation;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.enums.RoleEnum;
import com.gms.enums.StatusEnum;
import com.gms.model.request.SchoolRequest;
import com.gms.model.response.SchoolResponse;
import com.gms.repository.DesignationRepository;
import com.gms.repository.EmployeeRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.EmailService;
import com.gms.service.SchoolService;
import com.gms.util.UsernameGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;
import java.util.Collections;

@Service
public class SchoolServiceImpl implements SchoolService {
    private static final Logger log = LoggerFactory.getLogger(SchoolServiceImpl.class);

    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final DesignationRepository designationRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public SchoolServiceImpl(SchoolRepository schoolRepository,
                             UserRepository userRepository,
                             DesignationRepository designationRepository,
                             EmployeeRepository employeeRepository,
                             PasswordEncoder passwordEncoder,
                             EmailService emailService) {
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
        this.designationRepository = designationRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public ResponseEntity<SchoolResponse> create(SchoolRequest request) {
        if (TenantContext.employeeId() == null) {
            return ResponseEntity.badRequest().build();
        }
        
        // Validate admin fields are present for creation
        if (request.getAdminFullName() == null || request.getAdminFullName().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getAdminEmail() == null || request.getAdminEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        School school = createSchoolFromRequest(request, new School());
        // The auditor is the verified caller, never a value off the request: `schools`, `users` and
        // `employees` each map their own createdBy/updatedBy instead of extending BaseAuditEntity
        // (DB-05's second convention), so the entity listener cannot reach them and this is one of the
        // two places their rows are stamped. Identity resolution itself is not duplicated: it is the
        // same TenantContextAuditorAware the listener calls, behind the same one rule.
        User auditor = TenantContextAuditorAware.currentAuditor(userRepository).orElse(null);
        if (auditor != null) {
            school.setCreatedBy(auditor);
            school.setUpdatedBy(auditor);
        }
        School saved = schoolRepository.save(school);
        
        // Seed Designations (minimal example)
        log.info("[SetupSchool] seeding designations");
        seedDesignationIfAbsent(saved, "SCHOOL_ADMIN", 1);
        seedDesignationIfAbsent(saved, "TEACHER", 5);

        // Create Admin user
        log.info("[SetupSchool] creating admin user");
        String[] nameParts = request.getAdminFullName() != null ? request.getAdminFullName().trim().split("\\s+", 2) : new String[]{"Admin", "User"};
        String base = (nameParts[0].substring(0, 1) + (nameParts.length > 1 ? nameParts[1] : "")).toLowerCase().replaceAll("[^a-z0-9]", "");
        String username = generateUniqueUsername(base);
        String tempPassword = base + "@123"; // simple; replace with stronger generator later

        User admin = new User();
        admin.setSchool(saved);
        admin.setFullName(request.getAdminFullName());
        admin.setEmail(request.getAdminEmail());
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(tempPassword));
        admin.setEnabled(true);
        admin.setRequirePasswordChange(true);
        // Use mutable set for JPA-managed @ElementCollection to avoid UnsupportedOperationException
        admin.setRoles(new HashSet<>(Set.of(RoleEnum.ADMIN.getCode())));
        if (auditor != null) {
            // The provisioned administrator did not create itself: the caller who ran the provisioning
            // did, and that is the row the audit trail has to name.
            admin.setCreatedBy(auditor);
            admin.setUpdatedBy(auditor);
        }
        userRepository.save(admin);
        log.info("[SetupSchool] admin user saved id={} username={}", admin.getId(), admin.getUsername());

        // Create and link Employee for Admin so TenantContext.employeeId() is available in JWT
        log.info("[SetupSchool] creating admin employee");
        com.gms.model.entity.Employee emp = new com.gms.model.entity.Employee();
        emp.setSchool(saved);
        String[] empParts = request.getAdminFullName() != null ? request.getAdminFullName().trim().split("\\s+", 2) : new String[]{"Admin", "User"};
        emp.setFirstName(empParts[0]);
        emp.setLastName(empParts.length > 1 ? empParts[1] : "Admin");
        emp.setEmail(request.getAdminEmail());
        emp.setDepartment("ADMINISTRATIVE");
        emp.setDesignation("SCHOOL_ADMIN");
        emp.setTeaching(false);
        emp.setEnabled(true);
        if (auditor != null) {
            emp.setCreatedBy(auditor);
            emp.setUpdatedBy(auditor);
        }
        // Persist employee and link to admin user
        com.gms.model.entity.Employee savedEmp = employeeRepository.save(emp);
        admin.setEmployee(savedEmp);
        userRepository.save(admin);
        log.info("[SetupSchool] admin employee saved id={} linked to userId={}", savedEmp.getId(), admin.getId());

        // Email credentials
        try {
            log.info("[SetupSchool] sending admin credentials to {}", request.getAdminEmail());
            emailService.sendAdminCredentials(request.getAdminEmail(), request.getSchoolName(), username, tempPassword);
            log.info("[SetupSchool] admin credentials email process completed for {}", request.getAdminEmail());
        } catch (Exception e) {
            log.error("[SetupSchool] unexpected error during email sending to {}: {}", request.getAdminEmail(), e.getMessage(), e);
        }

        log.info("School created id={} code={}, admin username={} email={}", saved.getId(), saved.getSchoolCode(), username, request.getAdminEmail());

        SchoolResponse response = new SchoolResponse(saved.getId(), saved.getSchoolId(), saved.getSchoolName(), saved.getSchoolCode(), saved.getStatus(), saved.isEnabled());
        return ResponseEntity.ok(response);
    }

    @Override
    public School createSchoolFromRequest(SchoolRequest request, School school) {
        if (TenantContext.employeeId() == null) {
            throw new IllegalArgumentException("Employee ID cannot be null");
        }

        // 1) Validate uniqueness
        if (schoolRepository.existsBySchoolCode(request.getSchoolCode())) {
            throw new IllegalArgumentException("School code already exists");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank() && schoolRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("School email already exists");
        }

        // 2) Create School
        log.info("[SetupSchool] creating School entity");
        school.setSchoolName(request.getSchoolName());
        school.setSchoolCode(request.getSchoolCode());
        school.setAddress(request.getAddress());
        school.setPhone(request.getPhone());
        school.setEmail(request.getEmail());
        school.setPrincipalName(request.getPrincipalName());
        school.setEstablishedYear(request.getEstablishedYear());
        school.setBoardAffiliation(request.getBoardAffiliation());
        school.setEnabled(true);
        school.setStatus("1");
        
        return school;
    }

    @Override
    @Transactional(readOnly = true)
    public List<School> findBySchool() {
        // Fix: Only return the school that belongs to the requesting user's school
        // Using repository method for better performance
        return schoolRepository.findByIdAndStatusOne(TenantContext.schoolId())
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<School> findAll() {
        return schoolRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public School getById(Integer id) {
        // A school administrator may read only its own school; the platform owner may read any,
        // because the same caller can already list every school and update or toggle any of them.
        if (!TenantContext.isPlatformOwner() && !id.equals(TenantContext.schoolId())) {
            throw new TenantAccessDeniedException("Access denied: You can only access your own school");
        }

        School school = schoolRepository.findByIdAndStatusOne(id)
                .orElseThrow(() -> new EntityNotFoundException("School not found or not active"));

        if (!school.isEnabled()) {
            throw new IllegalArgumentException("School is not enabled");
        }

        return school;
    }

    @Override
    public ResponseEntity<SchoolResponse> update(SchoolRequest request) {
        // Validate required fields
        if (request.getId() == null) {
            return ResponseEntity.badRequest().build();
        }

        // Find school by ID from request
        School school = schoolRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        
        // Check if user has permission to update this school
        // SuperAdmin can update any school, others can only update their own
        if (!TenantContext.isPlatformOwner()) {
            if (!school.getId().equals(TenantContext.schoolId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        // Update allowed fields
        if (request.getSchoolName() != null) school.setSchoolName(request.getSchoolName());
        if (request.getSchoolCode() != null) school.setSchoolCode(request.getSchoolCode());
        if (request.getAddress() != null) school.setAddress(request.getAddress());
        if (request.getPhone() != null) school.setPhone(request.getPhone());
        if (request.getEmail() != null) school.setEmail(request.getEmail());
        if (request.getPrincipalName() != null) school.setPrincipalName(request.getPrincipalName());
        if (request.getEstablishedYear() != null) school.setEstablishedYear(request.getEstablishedYear());
        if (request.getBoardAffiliation() != null) school.setBoardAffiliation(request.getBoardAffiliation());

        // Only the modifier is stamped here. createdBy is loaded with the row and left alone, which is
        // what makes the creator of a school answerable years later rather than whoever renamed it last.
        TenantContextAuditorAware.currentAuditor(userRepository).ifPresent(school::setUpdatedBy);

        School saved = schoolRepository.save(school);
        SchoolResponse response = new SchoolResponse(saved.getId(), saved.getSchoolId(), saved.getSchoolName(), saved.getSchoolCode(), saved.getStatus(), saved.isEnabled());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<?> toggleSchool(Integer id, Boolean isActive) {
        // Check if user has permission to toggle this school
        // SuperAdmin can toggle any school, others can only toggle their own
        if (!TenantContext.isPlatformOwner()) {
            if (!id.equals(TenantContext.schoolId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        
        // Find school by ID (don't restrict to active only for toggle operation)
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        school.setEnabled(isActive);
        school.setStatus(isActive ? "1" : "0");
        TenantContextAuditorAware.currentAuditor(userRepository).ifPresent(school::setUpdatedBy);

        School saved = schoolRepository.save(school);
        SchoolResponse response = new SchoolResponse(saved.getId(), saved.getSchoolId(), saved.getSchoolName(), saved.getSchoolCode(), saved.getStatus(), saved.isEnabled());
        return ResponseEntity.ok(response);
    }

    private void seedDesignationIfAbsent(School school, String title, int level) {
        boolean exists = designationRepository.existsBySchool_IdAndTitle(school.getId(), title);
        if (!exists) {
            Designation d = new Designation();
            d.setSchool(school);
            d.setTitle(title);
            d.setDesignationId(generateNextDesignationId());
            d.setHierarchyLevel(level);
            d.setIsAdministrativeRole(true);
            d.setIsTeachingRole(false);
            // Map department based on title, default to ADMINISTRATIVE
            StatusEnum.Department dept = "TEACHER".equalsIgnoreCase(title)
                    ? StatusEnum.Department.ACADEMIC
                    : StatusEnum.Department.ADMINISTRATIVE;
            d.setDepartment(dept);
            d.setIsActive(true);
            designationRepository.save(d);
        }
    }

    private String generateNextDesignationId() {
        long count = designationRepository.count();
        long next = count + 1;
        // DES + 6 digits -> length 9, fits in column length 10
        return String.format("DES%06d", next);
    }

    private String generateUniqueUsername(String base) {
        String candidate = base;
        int counter = 0;
        while (userRepository.existsByUsername(candidate)) {
            counter++;
            candidate = base + counter;
            if (counter > 1000) throw new RuntimeException("No available username found after 1000 attempts");
        }
        return candidate;
    }
    
    // Base method for service-to-service communication
    @Override
    public Optional<School> findById(Integer id) {
        return schoolRepository.findById(id);
    }
    
    // Simple create method for bootstrap and other special cases
    @Override
    public School create(School school) {
        return schoolRepository.save(school);
    }
}