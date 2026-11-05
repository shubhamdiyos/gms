package com.gms.security;

import com.gms.model.entity.User;
import com.gms.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * SEC-CRED-02: Server-side enforcement of the forced initial password change.
 *
 * <p>This filter runs after {@link JwtAuthenticationFilter} and checks whether the authenticated
 * user has the {@code requirePasswordChange} flag set. If true, the filter blocks access to all
 * business APIs and allows only a whitelist of authentication and self-service endpoints.</p>
 *
 * <p>The whitelist includes (exact match only — Stage G):</p>
 * <ul>
 *   <li>{@code /api/v1/auth/login} — initial authentication</li>
 *   <li>{@code /api/v1/auth/change-password} — the password change endpoint itself</li>
 *   <li>{@code /api/v1/auth/csrf-token} — the double-submit handshake the change needs</li>
 *   <li>{@code /api/v1/users/me} — read/update own profile</li>
 * </ul>
 *
 * <p>{@code /api/v1/auth/csrf-token} is on the list because leaving it off deadlocked every
 * forced rotation: the SPA refetches the token as soon as login returns
 * {@code requirePasswordChange}, that GET answered 403 with this filter's own body, and the
 * browser then had no token to send with the {@code change-password} POST — the one call that
 * would have cleared the flag. The endpoint discloses nothing but a token the caller's own
 * cookie already carries.</p>
 *
 * <p>Stage G: replaced prefix-based matching ({@code startsWith}) with exact path matching
 * ({@code Set.contains}) to prevent lookalike path bypasses such as
 * {@code /api/v1/auth/login-extra} or {@code /api/v1/users/meevil}. All three whitelisted
 * endpoints are leaf paths with no legitimate subpaths, so exact match is both safe and correct.</p>
 *
 * <p>All other requests answer with 403 Forbidden and a JSON error body indicating that a password
 * change is required. The flag is cleared by {@code AuthController.changePassword()} after a
 * successful password change, so subsequent requests proceed normally.</p>
 *
 * <p>Stage E performance fix: reads the flag from the {@link GmsUserDetails} principal already
 * loaded by {@link JwtAuthenticationFilter}, avoiding a duplicate database lookup. Falls back to
 * a database query only when the principal is not a {@code GmsUserDetails} (fail-closed).</p>
 */
@Component
public class PasswordChangeEnforcementFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeEnforcementFilter.class);

    private static final Set<String> WHITELISTED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/change-password",
            "/api/v1/auth/csrf-token",
            "/api/v1/users/me"
    );

    private final UserService userService;

    public PasswordChangeEnforcementFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        // Stage G: exact-match whitelist — prefix matching allowed lookalike paths
        // (e.g. /api/v1/auth/login-extra matched /api/v1/auth/login).
        if (WHITELISTED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check if the request is authenticated
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = authentication.getName();
        if (username == null || "anonymousUser".equals(username)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Stage E: read the flag from the principal loaded by JwtAuthenticationFilter,
        // avoiding a duplicate database lookup. Falls back to a DB query if the principal
        // is not a GmsUserDetails. Stage F: fail-closed — empty result rejects access.
        Optional<Boolean> requiresPasswordChange = resolveRequiresPasswordChange(authentication, username);

        // Fail-closed: if resolution returns empty (user not found or error), reject access
        if (requiresPasswordChange.isEmpty() || requiresPasswordChange.get()) {
            log.debug("Blocking access to {} for user {} — password change required or user not verified", path, username);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"You must change your password before accessing this resource.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resolves the requirePasswordChange flag for the authenticated user.
     *
     * <p>Fast path: reads from {@link GmsUserDetails} principal (no DB query).</p>
     * <p>Fallback: queries the database if the principal is not a GmsUserDetails.</p>
     * <p>Fail-closed: returns empty if user not found or lookup fails, which rejects access.</p>
     *
     * @return Optional<Boolean>: empty = reject (user not found/error), true = reject (password change required), false = allow
     */
    private Optional<Boolean> resolveRequiresPasswordChange(Authentication authentication, String username) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof GmsUserDetails gmsUser) {
            return Optional.of(gmsUser.isRequirePasswordChange());
        }

        // Fallback: if the principal is not the expected type, query the database.
        // Fail-closed: if user not found or exception occurs, return empty (reject access).
        log.debug("Principal is not GmsUserDetails (type={}); falling back to database lookup for user {}",
                principal != null ? principal.getClass().getName() : "null", username);
        try {
            Optional<User> userOptional = userService.findByUsername(username);
            return userOptional.map(User::isRequirePasswordChange);
            // If userOptional is empty, returns Optional.empty() → fail-closed (reject access)
        } catch (Exception ex) {
            log.warn("Database lookup failed for user {} during password-change enforcement; rejecting access", username, ex);
            return Optional.empty(); // Fail-closed: reject access on error
        }
    }
}
