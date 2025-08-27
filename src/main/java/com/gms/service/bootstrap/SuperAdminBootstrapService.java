package com.gms.service.bootstrap;

import com.gms.config.SuperAdminBootstrapProperties;
import com.gms.enums.RoleEnum;
import com.gms.enums.StatusEnum;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.service.SchoolService;
import com.gms.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * SuperAdmin Bootstrap Service
 * 
 * Provides secure initialization logic for automatic superadmin and system school creation.
 * This service follows the established WindsurfGMS service architecture patterns and
 * implements proper security measures for production deployment.
 * 
 * Key Features:
 * - Environment-based credential management
 * - Secure password hashing with BCrypt
 * - Transactional operations for data consistency
 * - Comprehensive validation and error handling
 * - Service-to-service communication compliance
 * - Multi-tenant architecture support
 * - Detailed logging for audit trails
 * 
 * Security Considerations:
 * - Passwords are never logged or exposed
 * - BCrypt encryption for password storage
 * - Validation of configuration properties
 * - Skip logic to prevent duplicate creation
 * - Secure system school initialization
 * 
 * @author WindsurfGMS
 * @version 1.0
 * @since 2024-08-25
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminBootstrapService {

    private final SuperAdminBootstrapProperties properties;
    private final UserService userService;
    private final SchoolService schoolService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Perform superadmin bootstrap initialization
     * 
     * This method orchestrates the complete superadmin bootstrap process including:
     * 1. Configuration validation
     * 2. System school creation/verification
     * 3. SuperAdmin user creation
     * 4. Role assignment
     * 
     * @throws RuntimeException if bootstrap fails
     */
    @Transactional
    public void performBootstrap() {
        log.info("Starting SuperAdmin bootstrap process...");
        
        try {
            // Validate configuration
            validateConfiguration();
            
            // Check if bootstrap should be performed
            if (!properties.shouldBootstrap()) {
                log.info("SuperAdmin bootstrap is disabled or not properly configured. Skipping...");
                return;
            }

            // Check if superadmin already exists
            if (properties.isSkipIfExists() && superAdminExists()) {
                log.info("SuperAdmin already exists and skipIfExists is enabled. Skipping bootstrap...");
                return;
            }

            // Create or get system school
            School systemSchool = createOrGetSystemSchool();
            log.info("System school initialized: {} (ID: {})", systemSchool.getSchoolName(), systemSchool.getId());

            // Create superadmin user
            User superAdmin = createSuperAdminUser(systemSchool);
            log.info("SuperAdmin user created successfully: {} (ID: {})", superAdmin.getUsername(), superAdmin.getId());

            log.info("SuperAdmin bootstrap completed successfully!");
            
        } catch (Exception e) {
            log.error("SuperAdmin bootstrap failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to bootstrap SuperAdmin", e);
        }
    }

    /**
     * Validate bootstrap configuration properties
     * 
     * @throws IllegalStateException if configuration is invalid
     */
    private void validateConfiguration() {
        log.debug("Validating SuperAdmin bootstrap configuration...");
        
        try {
            properties.validate();
            log.debug("Configuration validation passed: {}", properties.getConfigurationSummary());
        } catch (IllegalStateException e) {
            log.error("Configuration validation failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Check if a superadmin user already exists in the system
     * 
     * @return true if superadmin exists, false otherwise
     */
    private boolean superAdminExists() {
        log.debug("Checking if SuperAdmin already exists...");
        
        try {
            // Check by username
            Optional<User> existingUser = userService.findByUsername(properties.getUsername());
            if (existingUser.isPresent()) {
                log.debug("SuperAdmin found by username: {}", properties.getUsername());
                return true;
            }

            // Check by email - need to implement this in UserService
            // For now, we'll just check by username
            log.debug("No existing SuperAdmin found");
            return false;
            
        } catch (Exception e) {
            log.warn("Error checking for existing SuperAdmin: {}", e.getMessage());
            // In case of error, assume superadmin might exist to be safe
            return true;
        }
    }

    /**
     * Create or retrieve the system school for superadmin
     * 
     * @return System school entity
     */
    private School createOrGetSystemSchool() {
        log.debug("Creating or retrieving system school...");
        
        try {
            // Check if system school already exists
            Optional<School> existingSchool = schoolService.findById(1);
            if (existingSchool.isPresent()) {
                log.debug("System school already exists: {} (ID: {})", 
                         existingSchool.get().getSchoolName(), existingSchool.get().getId());
                return existingSchool.get();
            }
            
            // Create new system school directly since this is a special bootstrap case
            School systemSchool = new School();
            systemSchool.setSchoolName(properties.getSystemSchool().getName());
            systemSchool.setSchoolCode(properties.getSystemSchool().getCode());
            systemSchool.setEmail(properties.getSystemSchool().getEmail());
            systemSchool.setAddress(properties.getSystemSchool().getAddress());
            systemSchool.setPhone(properties.getSystemSchool().getPhone());
            systemSchool.setPrincipalName(properties.getSystemSchool().getPrincipalName());
            systemSchool.setEstablishedYear(properties.getSystemSchool().getEstablishedYear());
            systemSchool.setBoardAffiliation(properties.getSystemSchool().getBoardAffiliation());
            systemSchool.setEnabled(true);
            systemSchool.setStatus(StatusEnum.SchoolStatus.ACTIVE.getCode());
            systemSchool.setCreatedAt(Instant.now());
            systemSchool.setUpdatedAt(Instant.now());
            
            // Save the system school using the service's create method
            School createdSchool = schoolService.create(systemSchool);
            log.debug("System school created successfully: {} (ID: {})", 
                     createdSchool.getSchoolName(), createdSchool.getId());
            
            return createdSchool;
            
        } catch (Exception e) {
            log.error("Failed to create/get system school: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize system school", e);
        }
    }

    /**
     * Create superadmin user with secure configuration
     * 
     * @param systemSchool The system school to associate with superadmin
     * @return Created superadmin user
     */
    private User createSuperAdminUser(School systemSchool) {
        log.debug("Creating SuperAdmin user...");
        
        try {
            // Create superadmin user entity
            User superAdmin = new User();
            superAdmin.setUsername(properties.getUsername());
            superAdmin.setEmail(properties.getEmail());
            superAdmin.setFullName(properties.getFullName());
            
            // Secure password hashing - using the password encoder consistently
            String plainPassword = properties.getPassword();
            log.debug("Encoding password for superadmin user: {}", properties.getUsername());
            String hashedPassword = passwordEncoder.encode(plainPassword);
            superAdmin.setPassword(hashedPassword);
            
            // Security settings
            superAdmin.setEnabled(properties.isAccountEnabled());
            superAdmin.setAccountLocked(properties.isAccountLocked());
            superAdmin.setEmailVerified(properties.isEmailVerified());
            superAdmin.setRequirePasswordChange(properties.isForcePasswordChange());
            superAdmin.setPasswordExpired(false);
            superAdmin.setEnabled(true);
            superAdmin.setLoginAttempts(0);
            
            // User status and metadata
            superAdmin.setUserStatus(StatusEnum.UserStatus.ACTIVE.getCode());
            superAdmin.setSchool(systemSchool);
            superAdmin.setCreatedAt(Instant.now());
            superAdmin.setUpdatedAt(Instant.now());
            superAdmin.setVersion(1);

            // Save user with SUPERADMIN role
            User createdUser = userService.save(superAdmin);
            createdUser.getRoles().add(RoleEnum.SUPERADMIN.getCode());
            User savedUser = userService.save(createdUser);
            
            log.debug("SuperAdmin user created successfully: {} (ID: {})", 
                     savedUser.getUsername(), savedUser.getId());
            
            // Log security settings (without sensitive data)
            log.debug("SuperAdmin security settings - Enabled: {}, EmailVerified: {}, ForcePasswordChange: {}", 
                     savedUser.isEnabled(), savedUser.isEmailVerified(), 
                     savedUser.isRequirePasswordChange());
            
            return savedUser;
            
        } catch (Exception e) {
            log.error("Failed to create SuperAdmin user: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create SuperAdmin user", e);
        }
    }

    /**
     * Get bootstrap status information for monitoring
     * 
     * @return Bootstrap status summary
     */
    public BootstrapStatus getBootstrapStatus() {
        BootstrapStatus status = new BootstrapStatus();
        status.setEnabled(properties.isEnabled());
        status.setConfigured(properties.shouldBootstrap());
        status.setSuperAdminExists(superAdminExists());
        status.setSkipIfExists(properties.isSkipIfExists());
        status.setConfiguration(properties.getConfigurationSummary());
        
        return status;
    }

    /**
     * Bootstrap Status Information
     */
    public static class BootstrapStatus {
        private boolean enabled;
        private boolean configured;
        private boolean superAdminExists;
        private boolean skipIfExists;
        private String configuration;

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isConfigured() { return configured; }
        public void setConfigured(boolean configured) { this.configured = configured; }

        public boolean isSuperAdminExists() { return superAdminExists; }
        public void setSuperAdminExists(boolean superAdminExists) { this.superAdminExists = superAdminExists; }

        public boolean isSkipIfExists() { return skipIfExists; }
        public void setSkipIfExists(boolean skipIfExists) { this.skipIfExists = skipIfExists; }

        public String getConfiguration() { return configuration; }
        public void setConfiguration(String configuration) { this.configuration = configuration; }

        @Override
        public String toString() {
            return String.format(
                "BootstrapStatus[enabled=%s, configured=%s, superAdminExists=%s, skipIfExists=%s]",
                enabled, configured, superAdminExists, skipIfExists
            );
        }
    }
}