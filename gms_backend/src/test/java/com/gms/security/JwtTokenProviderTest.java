package com.gms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Locks in the signing-key rules introduced in milestone M1 (mandate §7, findings SEC-02/AP-1):
 * a production-shaped boot cannot obtain a predictable key, and a token cannot be replayed across
 * issuers or tampered with.
 */
class JwtTokenProviderTest {

    private static final String VALID_SECRET = "unit-test-signing-secret-0123456789abcdef";

    private final MockEnvironment nonDevEnvironment = new MockEnvironment();

    private JwtTokenProvider provider(String secret) {
        return new JwtTokenProvider(nonDevEnvironment, secret, 3600L, "gms");
    }

    private static MockEnvironment dev() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        return environment;
    }

    @Test
    void refusesToStartWithoutASecret() {
        assertThatIllegalStateException()
                .isThrownBy(() -> provider(""))
                .withMessageContaining("SECURITY_JWT_SECRET");
    }

    @Test
    void refusesASecretShorterThanTheSigningAlgorithmRequires() {
        assertThatIllegalStateException()
                .isThrownBy(() -> provider("too-short"))
                .withMessageContaining("32");
    }

    @Test
    void refusesTheSecretThatShippedAsTheDefault() {
        assertThatIllegalStateException()
                .isThrownBy(() -> provider("change-me-please-change-me-please-change-me-please"));
    }

    @Test
    void issuesAndReadsBackItsOwnClaims() {
        JwtTokenProvider subject = provider(VALID_SECRET);

        String token = subject.createToken("teacher-one", Set.of("TEACHER"), 7, 42);

        Claims claims = subject.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("teacher-one");
        assertThat(claims.get("schoolId", Integer.class)).isEqualTo(7);
        assertThat(claims.get("empId", Integer.class)).isEqualTo(42);
        assertThat(claims.getIssuer()).isEqualTo("gms");
        assertThat(subject.getRoles(token)).containsExactly("TEACHER");
        assertThat(subject.isTokenValid(token)).isTrue();
    }

    @Test
    void rejectsATokenSignedByAnotherIssuerUsingTheSameKey() {
        String foreignToken = new JwtTokenProvider(nonDevEnvironment, VALID_SECRET, 3600L, "other-service")
                .createToken("admin", Set.of("ADMIN"));

        assertThatThrownBy(() -> provider(VALID_SECRET).parseClaims(foreignToken)).isInstanceOf(Exception.class);
    }

    @Test
    void rejectsATamperedPayload() {
        String token = provider(VALID_SECRET).createToken("admin", Set.of("ADMIN"));
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 1)
                + (parts[1].endsWith("A") ? "B" : "A") + "." + parts[2];

        assertThatThrownBy(() -> provider(VALID_SECRET).parseClaims(tampered)).isInstanceOf(Exception.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        JwtTokenProvider expired = new JwtTokenProvider(nonDevEnvironment, VALID_SECRET, -60L, "gms");

        assertThat(expired.isTokenValid(expired.createToken("admin", Set.of("ADMIN")))).isFalse();
    }

    @Test
    void devProfileFallsBackToAnEphemeralKeyInsteadOfAPublicOne() {
        JwtTokenProvider dev = new JwtTokenProvider(
                dev(), "", 3600L, "gms");

        assertThat(dev.isTokenValid(dev.createToken("devadmin", Set.of("ADMIN")))).isTrue();
    }

    @Test
    void twoDevBootsCannotReadEachOthersTokens() {
        JwtTokenProvider first = new JwtTokenProvider(
                dev(), "", 3600L, "gms");
        JwtTokenProvider second = new JwtTokenProvider(
                dev(), "", 3600L, "gms");

        String token = first.createToken("devadmin", Set.of("ADMIN"));
        assertThatThrownBy(() -> second.parseClaims(token)).isInstanceOf(Exception.class);
    }

    @Test
    void rejectsABlankIssuer() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new JwtTokenProvider(nonDevEnvironment, VALID_SECRET, 3600L, " "));
    }
}
