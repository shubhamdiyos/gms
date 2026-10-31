package com.gms.config;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a deployment that is missing or weakening a security control.
 *
 * <p>The checks run outside the {@code dev} profile, so both {@code --spring.profiles.active=prod}
 * and the plain {@code java -jar} invocation used by the EC2 deploy are covered. Local development
 * is the only environment allowed to lean on convenience defaults, and it is named explicitly.</p>
 *
 * <p>Each check maps to a finding in {@code docs/audit/SECURITY_AUDIT.md} that was produced by an
 * insecure default being reachable in production rather than by an operator mistake. The intent is
 * that the deployment fails in seconds with an actionable message instead of booting into a
 * vulnerable state.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupSecurityValidator implements InitializingBean {

    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        if (isDevProfileActive()) {
            log.warn("Active profile contains 'dev' - startup security validation is relaxed. "
                    + "Never run this profile in an internet-reachable environment.");
            return;
        }

        List<String> failures = new java.util.ArrayList<>();

        requireText(failures, "spring.datasource.url",
                "DB_URL is unset, so the application has no database. Copying a connection string into "
                        + "application.properties is finding SEC-01 and must not return.");
        requireText(failures, "spring.datasource.username", "DB_USERNAME is unset.");
        requireText(failures, "spring.datasource.password", "DB_PASSWORD is unset.");

        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto", "validate");
        if (!List.of("validate", "none").contains(ddlAuto.toLowerCase())) {
            failures.add("spring.jpa.hibernate.ddl-auto=" + ddlAuto + ". Schema drift must be applied by "
                    + "Flyway migrations, never by Hibernate at boot (OPS-07).");
        }

        List<String> origins = corsOrigins();
        if (origins.isEmpty()) {
            failures.add("cors.allowed-origins is empty. Set CORS_ALLOWED_ORIGINS to the exact browser "
                    + "origins that may call this API.");
        }
        for (String origin : origins) {
            if (origin.contains("localhost") || origin.contains("127.0.0.1")) {
                failures.add("cors.allowed-origins contains a dev origin (" + origin
                        + "). Production origins must not include localhost.");
            }
            if ("*".equals(origin)) {
                failures.add("cors.allowed-origins contains '*' while allow-credentials is true (" + origin + ").");
            }
        }

        boolean bootstrapEnabled = environment.getProperty("app.superadmin.enabled", Boolean.class, false);
        if (bootstrapEnabled) {
            for (String key : List.of("app.superadmin.username", "app.superadmin.password",
                    "app.superadmin.email", "app.superadmin.full-name")) {
                requireText(failures, key, key + " is required when app.superadmin.enabled=true.");
            }
        }

        String logLevel = environment.getProperty("logging.level.com.gms", "INFO");
        if (List.of("debug", "trace").contains(logLevel.toLowerCase())) {
            failures.add("logging.level.com.gms=" + logLevel + " is enabled outside dev. Request payloads and "
                    + "identifiers reach the log store (OPS-11).");
        }

        if (!failures.isEmpty()) {
            String report = String.join("\n  - ", failures);
            log.error("Startup security validation failed:\n  - {}", report);
            throw new IllegalStateException("Startup security validation failed with " + failures.size()
                    + " problem(s):\n  - " + report);
        }
        log.info("Startup security validation passed (profiles: {}).", describeProfiles());
    }

    private List<String> corsOrigins() {
        String raw = environment.getProperty("cors.allowed-origins", "");
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    private void requireText(List<String> failures, String key, String message) {
        String value = environment.getProperty(key, "");
        if (value.isBlank()) {
            failures.add(key + ": " + message);
        }
    }

    private boolean isDevProfileActive() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    private String describeProfiles() {
        String[] active = environment.getActiveProfiles();
        return active.length == 0 ? "none" : String.join(", ", active);
    }
}
