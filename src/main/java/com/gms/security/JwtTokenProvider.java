package com.gms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long validitySeconds;

    public JwtTokenProvider(
            @Value("${security.jwt.secret:change-me-please-change-me-please-change-me-please}") String secret,
            @Value("${security.jwt.validity-seconds:3600}") long validitySeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.validitySeconds = validitySeconds;
    }

    public String createToken(String subject, Set<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(validitySeconds);
        return Jwts.builder()
                .setSubject(subject)
                .claim("roles", roles)
                .setIssuer(SecurityConstants.ISSUER)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createToken(String subject, Set<String> roles, Integer schoolId, Integer empId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(validitySeconds);
        return Jwts.builder()
                .setSubject(subject)
                .claim("roles", roles)
                .claim("schoolId", schoolId)
                .claim("empId", empId)
                .setIssuer(SecurityConstants.ISSUER)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
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
        return (Set<String>) claims.get("roles");
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
