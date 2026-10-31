package com.gms.config;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstrap configuration for the platform (SUPERADMIN) account.
 *
 * <p>Everything here is opt-in. {@code enabled} defaults to false and there are no credential
 * defaults: the account is only created when the operator supplies a password. Validation runs in
 * {@link #validate()} rather than through bean-validation annotations, because a disabled bootstrap
 * must not prevent the application from starting with empty values.</p>
 *
 * <p>Bound property keys are {@code app.superadmin.*}, which Spring resolves from the
 * {@code APP_SUPERADMIN_USERNAME}, {@code APP_SUPERADMIN_PASSWORD}, {@code APP_SUPERADMIN_EMAIL}
 * and {@code APP_SUPERADMIN_FULL_NAME} environment variables. The unsuffixed {@code SUPERADMIN_*}
 * names quoted by earlier revisions of this class and of the project documentation bind to nothing
 * (finding SEC-15).</p>
 */
@ConfigurationProperties(prefix = "app.superadmin")
@Getter
@Setter
public class SuperAdminBootstrapProperties {

    /** Minimum accepted length for a bootstrap password. */
    public static final int MIN_PASSWORD_LENGTH = 12;

    /**
     * Passwords that appeared as defaults in this repository and are therefore public. Supplying
     * one of them is treated as supplying none.
     */
    static final Set<String> RETIRED_DEFAULT_PASSWORDS = Set.of("superadmin123!");

    /** Create the account at startup. Off unless the deployment asks for it. */
    private boolean enabled = false;

    /** Skip creation when a SUPERADMIN already exists. */
    private boolean skipIfExists = true;

    private String username = "";

    private String password = "";

    private String email = "";

    private String fullName = "";

    /** Require a password change on first login. */
    private boolean forcePasswordChange = true;

    private boolean accountEnabled = true;

    private boolean accountLocked = false;

    /** Bootstrap accounts are marked verified; there is no mail flow to wait for. */
    private boolean emailVerified = true;

    private SystemSchool systemSchool = new SystemSchool();

    /**
     * The placeholder school the platform account is attached to, because {@code users.school_id}
     * is NOT NULL.
     */
    @Getter
    @Setter
    public static class SystemSchool {
        private String name = "System";
        private String code = "SYSTEM";
        private String email = "system@gms.local";
        private String address = "System Administration Office";
        private String phone = "+1-000-000-0000";
        private String principalName = "System Administrator";
        private Integer establishedYear = 2024;
        private String boardAffiliation = "SYSTEM";
    }

    /**
     * @throws IllegalStateException if bootstrap is enabled but incomplete, so that a
     *     half-configured platform account is never created silently
     */
    public void validate() {
        if (systemSchool == null) {
            systemSchool = new SystemSchool();
        }
        if (!enabled) {
            return;
        }
        require(blanks(username), "APP_SUPERADMIN_USERNAME (3-50 characters)");
        require(blanks(email), "APP_SUPERADMIN_EMAIL");
        require(blanks(fullName), "APP_SUPERADMIN_FULL_NAME");
        if (blanks(password)) {
            throw new IllegalStateException(
                    "Bootstrap is enabled but no password was supplied. Set APP_SUPERADMIN_PASSWORD; "
                            + "there is no longer a default platform-administrator password.");
        }
        if (password.trim().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("APP_SUPERADMIN_PASSWORD must be at least "
                    + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (isRetiredDefault(password)) {
            throw new IllegalStateException(
                    "APP_SUPERADMIN_PASSWORD is the password that shipped as a default in this repository and "
                            + "is public knowledge. Choose a different value.");
        }
    }

    /** Compared case-insensitively: the retired default differs from its lower-case form only in case. */
    static boolean isRetiredDefault(String candidate) {
        return candidate != null && RETIRED_DEFAULT_PASSWORDS.contains(candidate.trim().toLowerCase());
    }

    private static void require(boolean blank, String name) {
        if (blank) {
            throw new IllegalStateException("Bootstrap is enabled but " + name + " is not configured.");
        }
    }

    private static boolean blanks(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * @return true when an operator has asked for the account and given enough information to make it
     */
    public boolean shouldBootstrap() {
        return enabled && !blanks(password) && !isRetiredDefault(password);
    }

    /**
     * @return a loggable summary that never contains the password
     */
    public String getConfigurationSummary() {
        return String.format(
                "SuperAdminBootstrap[enabled=%s, skipIfExists=%s, username=%s, email=%s, fullName=%s,"
                        + " forcePasswordChange=%s, passwordConfigured=%s]",
                enabled, skipIfExists, username, email, fullName, forcePasswordChange, !blanks(password));
    }
}
