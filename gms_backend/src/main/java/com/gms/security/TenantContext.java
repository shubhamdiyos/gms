package com.gms.security;

import com.gms.enums.RoleEnum;

import java.util.Set;

/**
 * The caller's tenant, resolved once per request.
 *
 * <p>Before this existed, a controller asked {@code TenantContext.schoolId()}, which pulled the
 * {@code Authorization} header off the current request and re-parsed and re-verified the JWT signature every
 * time it was called. A single request did that work several times over, and every service that wanted the
 * tenant had to be handed one as a parameter — so a caller could pass the wrong school, or pass none at all,
 * and the service had no way to notice.</p>
 *
 * <p>{@link JwtAuthenticationFilter} now reads the claims it has already verified and puts the result here
 * once. Services read the tenant from here. Outside a request — a scheduled job, an async callback, a unit
 * test — there is no tenant, and the accessors return {@code null} rather than inventing one.</p>
 */
public final class TenantContext {

    private static final ThreadLocal<Principal> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * Who is calling, and which tenant they belong to.
     *
     * @param username   the authenticated principal's username
     * @param schoolId   the tenant the principal belongs to, or {@code null} for one that belongs to none
     * @param employeeId the employee the principal acts as, or {@code null}
     * @param roles      the authorities carried by the token
     */
    public record Principal(String username, Integer schoolId, Integer employeeId, Set<String> roles) {

        public Principal {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }

        /**
         * The platform owner reaches across every tenant; nobody else does.
         */
        public boolean isPlatformOwner() {
            return roles.contains(RoleEnum.SUPERADMIN.getCode());
        }
    }

    public static void set(Principal principal) {
        CURRENT.set(principal);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * The caller, or {@code null} when the code is running outside a request.
     */
    public static Principal current() {
        return CURRENT.get();
    }

    /**
     * The caller's tenant, or {@code null} outside a request.
     */
    public static Integer schoolId() {
        Principal principal = CURRENT.get();
        return principal == null ? null : principal.schoolId();
    }

    /**
     * The employee the caller acts as, or {@code null} outside a request.
     */
    public static Integer employeeId() {
        Principal principal = CURRENT.get();
        return principal == null ? null : principal.employeeId();
    }

    public static String username() {
        Principal principal = CURRENT.get();
        return principal == null ? null : principal.username();
    }

    /**
     * The caller's authorities. Empty outside a request.
     */
    public static Set<String> roles() {
        Principal principal = CURRENT.get();
        return principal == null ? Set.of() : principal.roles();
    }

    public static boolean isPlatformOwner() {
        Principal principal = CURRENT.get();
        return principal != null && principal.isPlatformOwner();
    }
}
