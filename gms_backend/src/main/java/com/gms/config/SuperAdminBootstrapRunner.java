package com.gms.config;

import com.gms.service.bootstrap.SuperAdminBootstrapService;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the platform (SUPERADMIN) account at startup when, and only when,
 * {@code app.superadmin.enabled} is true.
 *
 * <p>A bootstrap that was explicitly requested but cannot be completed now aborts startup instead of
 * logging a warning and serving an instance with no administrator (finding SEC-03).</p>
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@EnableConfigurationProperties(SuperAdminBootstrapProperties.class)
@Profile("!test")
public class SuperAdminBootstrapRunner implements CommandLineRunner {

    private final SuperAdminBootstrapService bootstrapService;
    private final SuperAdminBootstrapProperties properties;
    private final Environment environment;

    @Override
    public void run(String... args) {
        log.info("Active Spring profiles: {}", describeProfiles());

        if (!properties.isEnabled()) {
            log.info("SuperAdmin bootstrap is disabled (app.superadmin.enabled=false); no platform account created.");
            return;
        }

        properties.validate();
        if (!properties.shouldBootstrap()) {
            throw new IllegalStateException("SuperAdmin bootstrap is enabled but not configured. "
                    + properties.getConfigurationSummary());
        }

        log.info("SuperAdmin bootstrap starting: {}", properties.getConfigurationSummary());
        long startTime = System.currentTimeMillis();
        try {
            bootstrapService.performBootstrap();
            log.info("SuperAdmin bootstrap completed in {} ms", System.currentTimeMillis() - startTime);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("SuperAdmin bootstrap failed after {} ms", System.currentTimeMillis() - startTime, e);
            throw new IllegalStateException(
                    "SuperAdmin bootstrap was requested but failed, so the application will not serve traffic with"
                            + " an unconfigured platform account: " + e.getMessage(), e);
        }
    }

    private String describeProfiles() {
        String[] active = environment.getActiveProfiles();
        return active.length == 0 ? "default" : String.join(", ", active);
    }
}
