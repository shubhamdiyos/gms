package com.gms.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserDetailsService userDetailsService) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            authenticate(request);
            filterChain.doFilter(request, response);
        } finally {
            // The tenant belongs to this request and to no other; a pooled thread must not carry it onward.
            TenantContext.clear();
        }
    }

    private void authenticate(HttpServletRequest request) {
        String header = request.getHeader(SecurityConstants.AUTH_HEADER);
        if (header == null || !header.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return;
        }
        String token = header.substring(SecurityConstants.BEARER_PREFIX.length());
        try {
            Claims claims = tokenProvider.parseClaims(token);
            String username = claims.getSubject();
            if (username == null || SecurityContextHolder.getContext().getAuthentication() != null) {
                return;
            }
            UserDetails details = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    details, null, details.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
            TenantContext.set(new TenantContext.Principal(
                    username,
                    integerClaim(claims, "schoolId"),
                    integerClaim(claims, "empId"),
                    roles(claims)));
        } catch (Exception ex) {
            log.debug("JWT parse/validate failed: {}", ex.getMessage());
        }
    }

    private static Integer integerClaim(Claims claims, String name) {
        Object value = claims.get(name);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Set<String> roles(Claims claims) {
        Object value = claims.get("roles");
        Set<String> roles = new LinkedHashSet<>();
        if (value instanceof List<?> list) {
            list.forEach(role -> roles.add(String.valueOf(role)));
        } else if (value instanceof Set<?> set) {
            set.forEach(role -> roles.add(String.valueOf(role)));
        }
        return roles;
    }
}
