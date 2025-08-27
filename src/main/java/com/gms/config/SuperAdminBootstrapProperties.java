package com.gms.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SuperAdmin Bootstrap Configuration Properties
 * 
 * Manages environment-based credential configuration for automatic superadmin initialization.
 * This class provides secure credential management using Spring Boot's configuration properties
 * with validation and environment variable support.
 * 
 * Environment Variables:
 * - SUPERADMIN_USERNAME: SuperAdmin username (default: superadmin)
 * - SUPERADMIN_PASSWORD: SuperAdmin password (REQUIRED in production)
 * - SUPERADMIN_EMAIL: SuperAdmin email (default: superadmin@gms.local)
 * - SUPERADMIN_FULL_NAME: SuperAdmin full name (default: Super Administrator)
 * - SUPERADMIN_FORCE_PASSWORD_CHANGE: Force password change on first login (default: true)
 * - SUPERADMIN_BOOTSTRAP_ENABLED: Enable/disable bootstrap (default: true)
 * - SUPERADMIN_SKIP_IF_EXISTS: Skip creation if superadmin already exists (default: true)
 * 
 * @author WindsurfGMS
 * @version 1.0
 * @since 2024-08-25
 */
@ConfigurationProperties(prefix = "app.superadmin")
@Validated
@Getter
@Setter
public class SuperAdminBootstrapProperties {

    /**
     * Enable or disable superadmin bootstrap functionality
     * Environment Variable: SUPERADMIN_BOOTSTRAP_ENABLED
     * Default: true
     */
    private boolean enabled = true;

    /**
     * Skip superadmin creation if one already exists
     * Environment Variable: SUPERADMIN_SKIP_IF_EXISTS
     * Default: true
     */
    private boolean skipIfExists = true;

    /**
     * SuperAdmin username
     * Environment Variable: SUPERADMIN_USERNAME
     * Default: superadmin
     */
    @NotBlank(message = "SuperAdmin username cannot be blank")
    @Size(min = 3, max = 50, message = "SuperAdmin username must be between 3 and 50 characters")
    private String username = "superadmin";

    /**
     * SuperAdmin password
     * Environment Variable: SUPERADMIN_PASSWORD
     * REQUIRED: Must be provided via environment variable in production
     */
    @NotBlank(message = "SuperAdmin password cannot be blank")
    @Size(min = 8, message = "SuperAdmin password must be at least 8 characters")
    private String password = "SuperAdmin123!";

    /**
     * SuperAdmin email address
     * Environment Variable: SUPERADMIN_EMAIL
     * Default: superadmin@gms.local
     */
    @NotBlank(message = "SuperAdmin email cannot be blank")
    @Email(message = "SuperAdmin email must be a valid email address")
    private String email = "superadmin@gms.local";

    /**
     * SuperAdmin full name
     * Environment Variable: SUPERADMIN_FULL_NAME
     * Default: Super Administrator
     */
    @NotBlank(message = "SuperAdmin full name cannot be blank")
    @Size(max = 100, message = "SuperAdmin full name cannot exceed 100 characters")
    private String fullName = "Super Administrator";

    /**
     * Force password change on first login
     * Environment Variable: SUPERADMIN_FORCE_PASSWORD_CHANGE
     * Default: true
     */
    private boolean forcePasswordChange = true;

    /**
     * Account enabled status
     * Default: true
     */
    private boolean accountEnabled = true;

    /**
     * Account locked status
     * Default: false
     */
    private boolean accountLocked = false;

    /**
     * Email verified status
     * Default: true (for bootstrap user)
     */
    private boolean emailVerified = true;

    /**
     * System school configuration for superadmin
     */
    private SystemSchool systemSchool = new SystemSchool();

    /**
     * System School Configuration for SuperAdmin
     */
    @Getter
    @Setter
    public static class SystemSchool {
        
        /**
         * System school name
         * Environment Variable: SYSTEM_SCHOOL_NAME
         * Default: System
         */
        private String name = "System";

        /**
         * System school code
         * Environment Variable: SYSTEM_SCHOOL_CODE
         * Default: SYSTEM
         */
        private String code = "SYSTEM";

        /**
         * System school email
         * Environment Variable: SYSTEM_SCHOOL_EMAIL
         * Default: system@gms.local
         */
        @Email(message = "System school email must be a valid email address")
        private String email = "system@gms.local";

        /**
         * System school address
         * Environment Variable: SYSTEM_SCHOOL_ADDRESS
         * Default: System Administration Office
         */
        private String address = "System Administration Office";

        /**
         * System school phone
         * Environment Variable: SYSTEM_SCHOOL_PHONE
         * Default: +1-000-000-0000
         */
        private String phone = "+1-000-000-0000";

        /**
         * System school principal name
         * Environment Variable: SYSTEM_SCHOOL_PRINCIPAL
         * Default: System Administrator
         */
        private String principalName = "System Administrator";

        /**
         * System school establishment year
         * Environment Variable: SYSTEM_SCHOOL_YEAR
         * Default: 2024
         */
        private Integer establishedYear = 2024;

        /**
         * System school board affiliation
         * Environment Variable: SYSTEM_SCHOOL_BOARD
         * Default: SYSTEM
         */
        private String boardAffiliation = "SYSTEM";
    }

    /**
     * Validate configuration properties
     * 
     * @throws IllegalStateException if configuration is invalid
     */
    public void validate() {
        if (enabled && (password == null || password.trim().isEmpty())) {
            throw new IllegalStateException(
                "SuperAdmin password must be provided via environment variable SUPERADMIN_PASSWORD " +
                "when bootstrap is enabled. For security reasons, password cannot be hardcoded."
            );
        }

        if (enabled && password != null && password.length() < 8) {
            throw new IllegalStateException(
                "SuperAdmin password must be at least 8 characters long for security compliance."
            );
        }
        
        // Ensure systemSchool is not null
        if (systemSchool == null) {
            systemSchool = new SystemSchool();
        }
    }

    /**
     * Check if bootstrap should be performed
     * 
     * @return true if bootstrap is enabled and configured properly
     */
    public boolean shouldBootstrap() {
        return enabled && password != null && !password.trim().isEmpty();
    }

    /**
     * Get sanitized configuration info for logging (without sensitive data)
     * 
     * @return configuration summary without password
     */
    public String getConfigurationSummary() {
        return String.format(
            "SuperAdminBootstrap[enabled=%s, skipIfExists=%s, username=%s, email=%s, fullName=%s, forcePasswordChange=%s]",
            enabled, skipIfExists, username, email, fullName, forcePasswordChange
        );
    }
}