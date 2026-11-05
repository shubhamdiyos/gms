package com.gms.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ACD-13-02: Tests for the secure credential generator.
 *
 * <p>Verifies that every generated password is unique, satisfies the platform's password policy,
 * and contains characters from all required character classes.</p>
 */
class SecureCredentialGeneratorTest {

    @Test
    void generatedPasswordSatisfiesPolicyValidator() {
        for (int i = 0; i < 100; i++) {
            String password = SecureCredentialGenerator.generate();
            assertThat(PasswordValidator.isValid(password))
                    .as("Generated password must pass PasswordValidator: %s", password)
                    .isTrue();
        }
    }

    @Test
    void generatedPasswordHasMinimumLength() {
        for (int i = 0; i < 50; i++) {
            String password = SecureCredentialGenerator.generate();
            assertThat(password.length()).isGreaterThanOrEqualTo(8);
        }
    }

    @Test
    void generatedPasswordContainsAllRequiredCharacterClasses() {
        for (int i = 0; i < 50; i++) {
            String password = SecureCredentialGenerator.generate();
            assertThat(password).matches(".*[a-z].*");
            assertThat(password).matches(".*[A-Z].*");
            assertThat(password).matches(".*\\d.*");
            assertThat(password).matches(".*[@$!%*?&.#_].*");
        }
    }

    @Test
    void twoConsecutiveGenerationsProduceDifferentPasswords() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            passwords.add(SecureCredentialGenerator.generate());
        }
        assertThat(passwords).hasSize(1000);
    }

    @Test
    void generatedPasswordDoesNotContainHardcodedDefault() {
        for (int i = 0; i < 50; i++) {
            String password = SecureCredentialGenerator.generate();
            assertThat(password).isNotEqualTo("welcome123");
        }
    }
}
