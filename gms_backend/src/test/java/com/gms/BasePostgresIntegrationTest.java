package com.gms;

import com.gms.config.SuperAdminBootstrapProperties;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Base class for PostgreSQL-backed integration tests.
 * 
 * <p>Uses the existing Docker PostgreSQL container on localhost:5433 with a dedicated
 * test database (gms_test). Flyway migrations run automatically against the test database.
 * Each test class is responsible for cleaning up its own data to ensure test isolation.</p>
 * 
 * <p>Subclasses should:
 * <ul>
 *   <li>Use {@code @SpringBootTest} to load the full application context</li>
 *   <li>Inject repositories and services as needed</li>
 *   <li>Set up test data in {@code @BeforeEach} methods</li>
 *   <li>Clean up test data in {@code @AfterEach} or {@code @BeforeEach} methods</li>
 * </ul>
 * </p>
 * 
 * <p>Requirements:
 * <ul>
 *   <li>Docker PostgreSQL container must be running on localhost:5433</li>
 *   <li>Database: gms_test</li>
 *   <li>Credentials: gms_user / gms_password (from .env file)</li>
 * </ul>
 * </p>
 * 
 * <p>Start the container with: {@code docker compose up postgres -d}</p>
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableConfigurationProperties(SuperAdminBootstrapProperties.class)
public abstract class BasePostgresIntegrationTest {
    // No Testcontainers — uses existing Docker PostgreSQL container configured in application-test.properties
}
