package com.gms.config;

import com.gms.service.bootstrap.SuperAdminBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * SuperAdmin Bootstrap Runner
 * 
 * CommandLineRunner implementation that automatically initializes the superadmin user
 * and system school during application startup. This component ensures that the system
 * has a functional superadmin account available immediately after deployment.
 * 
 * Key Features:
 * - Automatic execution on application startup
 * - Environment-based configuration support
 * - Comprehensive error handling and logging
 * - Production-ready security implementation
 * - Conditional execution based on profiles
 * - Order-controlled initialization sequence
 * 
 * Execution Order:
 * - High priority (Order = 1) to ensure early initialization
 * - Runs after Spring context is fully loaded
 * - Executes before other application components
 * 
 * Profile Support:
 * - Enabled by default for all profiles
 * - Can be disabled for testing profiles if needed
 * - Configurable via spring.profiles.active
 * 
 * Environment Variables:
 * - SUPERADMIN_BOOTSTRAP_ENABLED: Enable/disable bootstrap (default: true)
 * - SUPERADMIN_PASSWORD: Required superadmin password
 * - SUPERADMIN_USERNAME: Superadmin username (default: superadmin)
 * - SUPERADMIN_EMAIL: Superadmin email (default: superadmin@gms.local)
 * - And all other SuperAdminBootstrapProperties variables
 * 
 * @author WindsurfGMS
 * @version 1.0
 * @since 2024-08-25
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SuperAdminBootstrapProperties.class)
@Order(1) // High priority to ensure early initialization
@Profile("!test") // Skip for test profiles to avoid interference with test data
@Slf4j
public class SuperAdminBootstrapRunner implements CommandLineRunner {

    private final SuperAdminBootstrapService bootstrapService;
    private final SuperAdminBootstrapProperties properties;

    /**
     * Execute superadmin bootstrap on application startup
     * 
     * This method is called automatically by Spring Boot after the application context
     * is fully loaded but before the application is marked as ready to serve requests.
     * 
     * @param args Command line arguments passed to the application
     */
    @Override
    public void run(String... args) {
        log.info("=== WindsurfGMS SuperAdmin Bootstrap Starting ===");
        
        try {
            // Log startup information (without sensitive data)
            logStartupInfo();
            
            // Perform bootstrap initialization
            executeBootstrap();
            
            // Log completion status
            logCompletionStatus();
            
        } catch (Exception e) {
            handleBootstrapError(e);
        }
        
        log.info("=== WindsurfGMS SuperAdmin Bootstrap Completed ===");
    }

    /**
     * Log startup information for debugging and monitoring
     */
    private void logStartupInfo() {
        log.info("SuperAdmin Bootstrap Configuration: {}", properties.getConfigurationSummary());
        log.debug("Bootstrap enabled: {}", properties.isEnabled());
        log.debug("Skip if exists: {}", properties.isSkipIfExists());
        
        // Log environment information
        String[] activeProfiles = getActiveProfiles();
        log.info("Active Spring profiles: {}", String.join(", ", activeProfiles));
        
        // Validate environment requirements
        if (properties.isEnabled()) {
            validateEnvironmentRequirements();
        }
    }

    /**
     * Execute the bootstrap process with proper error handling
     */
    private void executeBootstrap() {
        if (!properties.isEnabled()) {
            log.info("SuperAdmin bootstrap is disabled. Skipping initialization...");
            return;
        }

        if (!properties.shouldBootstrap()) {
            log.warn("SuperAdmin bootstrap is enabled but not properly configured. Check environment variables.");
            return;
        }

        log.info("Executing SuperAdmin bootstrap process...");
        long startTime = System.currentTimeMillis();
        
        try {
            bootstrapService.performBootstrap();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("SuperAdmin bootstrap completed successfully in {} ms", duration);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("SuperAdmin bootstrap failed after {} ms: {}", duration, e.getMessage());
            throw e; // Re-throw to be handled by outer error handler
        }
    }

    /**
     * Log completion status and bootstrap results
     */
    private void logCompletionStatus() {
        try {
            SuperAdminBootstrapService.BootstrapStatus status = bootstrapService.getBootstrapStatus();
            log.info("Bootstrap Status: {}", status.toString());
            
            if (status.isEnabled() && status.isConfigured()) {
                if (status.isSuperAdminExists()) {
                    log.info("✅ SuperAdmin is available and ready");
                } else {
                    log.warn("⚠️ SuperAdmin bootstrap was configured but superadmin was not found");
                }
            } else {
                log.info("ℹ️ SuperAdmin bootstrap was skipped (disabled or not configured)");
            }
            
        } catch (Exception e) {
            log.warn("Could not retrieve bootstrap status: {}", e.getMessage());
        }
    }

    /**
     * Handle bootstrap errors with appropriate logging and actions
     * 
     * @param e The exception that occurred during bootstrap
     */
    private void handleBootstrapError(Exception e) {
        log.error("❌ SuperAdmin bootstrap failed: {}", e.getMessage(), e);
        
        // Log specific error guidance
        if (e.getMessage() != null) {
            if (e.getMessage().contains("password")) {
                log.error("💡 HINT: Ensure SUPERADMIN_PASSWORD environment variable is set with a secure password (minimum 8 characters)");
            } else if (e.getMessage().contains("database") || e.getMessage().contains("connection")) {
                log.error("💡 HINT: Ensure database is running and accessible before starting the application");
            } else if (e.getMessage().contains("validation")) {
                log.error("💡 HINT: Check SuperAdmin configuration properties for invalid values");
            }
        }
        
        // In production, we might want to fail fast if superadmin bootstrap fails
        // For now, we'll log the error and continue, allowing manual intervention
        log.warn("🔧 SuperAdmin bootstrap failed but application will continue. Manual superadmin creation may be required.");
        
        // TODO: Consider adding metrics/monitoring alerts for bootstrap failures
        // TODO: Consider implementing retry mechanism for transient failures
    }

    /**
     * Validate environment requirements for bootstrap
     */
    private void validateEnvironmentRequirements() {
        log.debug("Validating environment requirements...");
        
        // Check for required environment variables
        String password = System.getenv("SUPERADMIN_PASSWORD");
        if (password == null || password.trim().isEmpty()) {
            log.warn("⚠️ SUPERADMIN_PASSWORD environment variable is not set. Bootstrap may fail.");
            log.info("💡 Set SUPERADMIN_PASSWORD environment variable with a secure password for production deployment");
        }
        
        // Log other important environment variables (without values)
        logEnvironmentVariable("SUPERADMIN_USERNAME");
        logEnvironmentVariable("SUPERADMIN_EMAIL");
        logEnvironmentVariable("SUPERADMIN_FULL_NAME");
        logEnvironmentVariable("SUPERADMIN_BOOTSTRAP_ENABLED");
        logEnvironmentVariable("SUPERADMIN_SKIP_IF_EXISTS");
        logEnvironmentVariable("SUPERADMIN_FORCE_PASSWORD_CHANGE");
    }

    /**
     * Log environment variable status without exposing values
     * 
     * @param variableName The environment variable name to check
     */
    private void logEnvironmentVariable(String variableName) {
        String value = System.getenv(variableName);
        if (value != null && !value.trim().isEmpty()) {
            log.debug("✅ {} is set", variableName);
        } else {
            log.debug("⭕ {} is not set (using default)", variableName);
        }
    }

    /**
     * Get active Spring profiles
     * 
     * @return Array of active profile names
     */
    private String[] getActiveProfiles() {
        try {
            // This is a simplified way to get profiles; in a real Spring context,
            // you would inject Environment and use environment.getActiveProfiles()
            String profiles = System.getProperty("spring.profiles.active");
            if (profiles != null && !profiles.trim().isEmpty()) {
                return profiles.split(",");
            }
            return new String[]{"default"};
        } catch (Exception e) {
            log.debug("Could not determine active profiles: {}", e.getMessage());
            return new String[]{"unknown"};
        }
    }
}