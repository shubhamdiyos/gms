package com.gms.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

/**
 * The platform account used to be created on every boot with a password baked into the source
 * (findings SEC-03, SEC-15). These tests pin the opposite contract: off by default, and a
 * half-configured bootstrap is an error rather than a warning.
 */
class SuperAdminBootstrapPropertiesTest {

    private SuperAdminBootstrapProperties complete(String password) {
        SuperAdminBootstrapProperties properties = new SuperAdminBootstrapProperties();
        properties.setEnabled(true);
        properties.setUsername("platform-admin");
        properties.setEmail("platform-admin@example.org");
        properties.setFullName("Platform Administrator");
        properties.setPassword(password);
        return properties;
    }

    @Test
    void bootstrapIsOffByDefault() {
        SuperAdminBootstrapProperties properties = new SuperAdminBootstrapProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.shouldBootstrap()).isFalse();
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void thereIsNoDefaultCredential() {
        assertThat(new SuperAdminBootstrapProperties().getPassword()).isEmpty();
        assertThat(new SuperAdminBootstrapProperties().getUsername()).isEmpty();
        assertThat(new SuperAdminBootstrapProperties().getEmail()).isEmpty();
    }

    @Test
    void enabledWithoutAPasswordFailsInsteadOfWarning() {
        SuperAdminBootstrapProperties properties = new SuperAdminBootstrapProperties();
        properties.setEnabled(true);
        properties.setUsername("platform-admin");
        properties.setEmail("platform-admin@example.org");
        properties.setFullName("Platform Administrator");

        assertThatIllegalStateException()
                .isThrownBy(properties::validate)
                .withMessageContaining("APP_SUPERADMIN_PASSWORD");
    }

    @Test
    void enabledWithoutAnIdentityFails() {
        SuperAdminBootstrapProperties properties = new SuperAdminBootstrapProperties();
        properties.setEnabled(true);
        properties.setPassword("a-sufficiently-long-passphrase");

        assertThatIllegalStateException()
                .isThrownBy(properties::validate)
                .withMessageContaining("APP_SUPERADMIN_USERNAME");
    }

    @Test
    void rejectsThePasswordThatShippedAsTheDefault() {
        SuperAdminBootstrapProperties properties = complete("SuperAdmin123!");

        assertThatIllegalStateException().isThrownBy(properties::validate);
        assertThat(properties.shouldBootstrap()).isFalse();
    }

    @Test
    void rejectsAPassphraseShorterThanThePolicy() {
        assertThatIllegalStateException().isThrownBy(complete("short#1A")::validate);
    }

    @Test
    void acceptsACompleteConfiguration() {
        SuperAdminBootstrapProperties properties = complete("a-sufficiently-long-passphrase");

        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.shouldBootstrap()).isTrue();
    }

    @Test
    void summaryNeverCarriesThePassword() {
        String secret = "a-sufficiently-long-passphrase";

        assertThat(complete(secret).getConfigurationSummary())
                .doesNotContain(secret)
                .contains("passwordConfigured=true");
    }
}
