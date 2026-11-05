package com.gms.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
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
    private static final String AUTH_COOKIE_NAME = "gms_auth";

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
        // An explicitly presented credential wins over the ambient one.
        //
        // The SPA sends no Authorization header at all — it relies on the cookie — so ordering
        // header-first changes nothing for a browser. A client that does send one is naming the
        // identity it wants to act as, and this filter used to authenticate it as the cookie's
        // user instead: a request carrying school A's Bearer token from a cookie jar that last
        // signed in as school B was served as B. The e2e fixture arranges several tenants through
        // one cookie jar and measured exactly that — school A's administrator was answered
        // PASSWORD_CHANGE_REQUIRED because school B's un-rotated cookie had been used.
        String token = extractTokenFromHeader(request);
        if (token == null) {
            token = extractTokenFromCookie(request);
        }
        
        if (token == null) {
            return;
        }
        
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
                    integerClaim(claims, "uid"),
                    integerClaim(claims, "schoolId"),
                    integerClaim(claims, "empId"),
                    roles(claims)));
        } catch (Exception ex) {
            log.debug("JWT parse/validate failed: {}", ex.getMessage());
        }
    }

    /**
     * Extracts JWT token from HttpOnly authentication cookie.
     * Returns null if cookie is not present or has no value.
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AUTH_COOKIE_NAME.equals(cookie.getName())) {
                String token = cookie.getValue();
                return (token != null && !token.isEmpty()) ? token : null;
            }
        }
        return null;
    }

    /**
     * Extracts JWT token from Authorization header (Bearer token).
     * Returns null if header is not present or malformed.
     */
    private String extractTokenFromHeader(HttpServletRequest request) {
        String header = request.getHeader(SecurityConstants.AUTH_HEADER);
        if (header == null || !header.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return null;
        }
        return header.substring(SecurityConstants.BEARER_PREFIX.length());
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
