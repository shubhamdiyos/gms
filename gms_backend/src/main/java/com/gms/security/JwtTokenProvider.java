package com.gms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** HS256 requires at least 256 bits of key material. */
    static final int MIN_SECRET_BYTES = 32;

    /**
     * The literal that shipped as the default value of the signing key until
     * milestone M1. Any secret matching it is rejected rather than trusted, so a
     * deployment cannot silently resume using a key that is public knowledge
     * (finding SEC-02).
     */
    static final Set<String> RETIRED_DEFAULT_SECRETS = Set.of(
            "change-me-please-change-me-please-change-me-please",
            "change-me-please-change-me-please",
            "secret",
            "jwt-secret",
            "your-secret-key");

    private final SecretKey key;
    private final long validitySeconds;
    private final String issuer;

    public JwtTokenProvider(
            Environment environment,
            @Value("${security.jwt.secret:}") String secret,
            @Value("${security.jwt.validity-seconds:3600}") long validitySeconds,
            @Value("${security.jwt.issuer:gms}") String issuer) {
        this.key = Keys.hmacShaKeyFor(resolveSecret(secret, environment));
        this.validitySeconds = validitySeconds;
        this.issuer = requireIssuer(issuer);
    }

    private static byte[] resolveSecret(String secret, Environment environment) {
        if (isUsable(secret)) {
            return secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if (!Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
            throw new IllegalStateException(
                    "security.jwt.secret is missing, shorter than " + MIN_SECRET_BYTES
                            + " bytes, or a retired public default. Set SECURITY_JWT_SECRET to a value generated"
                            + " with `openssl rand -base64 48`. Startup is refused rather than signing tokens with"
                            + " a key that anyone could forge.");
        }
        byte[] ephemeral = new byte[MIN_SECRET_BYTES];
        new SecureRandom().nextBytes(ephemeral);
        log.warn("No SECURITY_JWT_SECRET configured - generated an ephemeral dev-only signing key."
                + " Every JWT is invalidated when this process restarts. Never deploy with the `dev` profile.");
        return ephemeral;
    }

    private static boolean isUsable(String secret) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            return false;
        }
        return !RETIRED_DEFAULT_SECRETS.contains(secret.trim().toLowerCase());
    }

    private static String requireIssuer(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("security.jwt.issuer must not be blank");
        }
        return issuer.trim();
    }

    public String getIssuer() {
        return issuer;
    }

    public long getValiditySeconds() {
        return validitySeconds;
    }

    public String createToken(String subject, Set<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(validitySeconds);
        return Jwts.builder()
                .setSubject(subject)
                .claim("roles", rolesClaim(roles))
                .setIssuer(issuer)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * The token an authenticated login gets: who, in which tenant, acting as which employee, and as which
     * account.
     *
     * <p>{@code uid} is {@code users.id} and exists for one purpose — the audit trail has to name the account
     * that wrote the row, and it is the only identifier on this claim set that does. {@code empId} cannot do
     * that job: {@code AuthController:98} fills it with the employee primary key for staff and the user primary
     * key for a principal with no employee row, so one claim holds two id spaces (finding BKL-08). Reading the
     * auditor off {@code uid} is also what keeps the trail query-free: an auditor resolved by username has to
     * be looked up, and when the entity listener asks for it that lookup happens <em>inside</em> the flush,
     * whose auto-flush re-enters the flush, and the write is lost.</p>
     */
    public String createToken(String subject, Set<String> roles, Integer schoolId, Integer empId, Integer uid) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(validitySeconds);
        return Jwts.builder()
                .setSubject(subject)
                .claim("roles", rolesClaim(roles))
                .claim("schoolId", schoolId)
                .claim("empId", empId)
                .claim("uid", uid)
                .setIssuer(issuer)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * The {@code roles} claim in the form it goes on the wire: the role codes, sorted, as a list.
     *
     * <p>Both callers hand over a {@code Set} — {@code AuthController.login} builds one with
     * {@code Collectors.toSet()}, whose iteration order is unspecified — and a {@code Set} of role
     * codes serialises in whatever order that particular hash happened to produce. Two logins for the
     * same two-role user could therefore carry the same set in a different order, and every consumer
     * that needed "the" role had to guess a precedence. Emitting a sorted array here, at the one
     * place the claim is written, makes the token self-describing.</p>
     *
     * <p>The JSON type is unchanged: a list serialises to the same array of strings a set did, and
     * both readers ({@code JwtAuthenticationFilter.roles} and {@link #getRoles(String)}) parse a list
     * back into a set, so nothing on the validation side moves.</p>
     */
    private static List<String> rolesClaim(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    public Claims parseClaims(String token) {
        Jws<Claims> jws = Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(issuer)
                .build()
                .parseClaimsJws(token);
        return jws.getBody();
    }

    public String getSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public String getUsername(String token) {
        return getSubject(token);
    }

    @SuppressWarnings("unchecked")
    public Set<String> getRoles(String token) {
        Claims claims = parseClaims(token);
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List) {
            return new HashSet<>((List<String>) rolesObj);
        } else if (rolesObj instanceof Set) {
            return (Set<String>) rolesObj;
        }
        return new HashSet<>();
    }

    public Integer getSchoolId(String token) {
        Claims claims = parseClaims(token);
        return (Integer) claims.get("schoolId");
    }

    public Integer getEmployeeId(String token) {
        Claims claims = parseClaims(token);
        return (Integer) claims.get("empId");
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
