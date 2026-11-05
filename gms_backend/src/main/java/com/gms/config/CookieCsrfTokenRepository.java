package com.gms.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.util.UUID;

/**
 * Cookie-based CSRF token repository for SPA architecture.
 *
 * <p>This implements the Double Submit Cookie pattern: the CSRF token is stored in a cookie
 * (XSRF-TOKEN) that JavaScript can read, and the frontend sends it back in the X-XSRF-TOKEN
 * header on state-changing requests. The backend validates that the header value matches the
 * cookie value.</p>
 *
 * <p>Security properties:</p>
 * <ul>
 *   <li>Cookie is NOT HttpOnly (JavaScript must read it)</li>
 *   <li>Cookie has Secure flag (HTTPS only in production)</li>
 *   <li>Cookie has SameSite=Lax (additional CSRF mitigation)</li>
 *   <li>Token is cryptographically random (UUID)</li>
 * </ul>
 *
 * <p>This approach is safe because an attacker cannot read the cookie from a different origin
 * due to same-origin policy, so they cannot forge the matching header value.</p>
 */
public class CookieCsrfTokenRepository implements CsrfTokenRepository {

    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String CSRF_PARAMETER_NAME = "_csrf";
    private static final String COOKIE_PATH = "/";

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return new DefaultCsrfToken(CSRF_HEADER_NAME, CSRF_PARAMETER_NAME, createNewToken());
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        if (token == null) {
            // Deliberately keep the cookie that the client already holds.
            //
            // The contract's null means "remove the token", and the one caller in this application
            // is Spring's CsrfAuthenticationStrategy, which SessionManagementFilter runs on every
            // authenticated request: the chain is STATELESS, so the filter's
            // `securityContextRepository.containsContext(request)` is always false and it cannot
            // tell "already signed in" from "signed in on this request". Clearing here therefore
            // fired on every authenticated call — the SPA signed in, loaded /users/me, and the
            // cookie was emptied before its first form submission, which then answered 403
            // {"error":"Forbidden"} because the header no longer matched any cookie. Rotation would
            // need the client to read a replacement, and the replacement is only generated when
            // someone asks for the token, which is exactly what the client just failed to do.
            //
            // Removing the token is not a security loss here: this is a double-submit token whose
            // whole guarantee is that another origin cannot read the cookie, and the only token
            // invalidator this application has is logout — `AuthController.logout` clears the
            // `gms_auth` credential itself, not this value.
            return;
        }
        // Set the cookie with the token value using manual header to include SameSite
        // Not HttpOnly - JavaScript needs to read it
        response.addHeader("Set-Cookie",
                String.format("%s=%s; Path=%s; Secure; SameSite=Lax",
                        CSRF_COOKIE_NAME, token.getToken(), COOKIE_PATH));
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (CSRF_COOKIE_NAME.equals(cookie.getName())) {
                String token = cookie.getValue();
                if (token != null && !token.isEmpty()) {
                    return new DefaultCsrfToken(CSRF_HEADER_NAME, CSRF_PARAMETER_NAME, token);
                }
            }
        }
        return null;
    }

    private String createNewToken() {
        return UUID.randomUUID().toString();
    }
}
