package com.gms.repository;

import com.gms.model.entity.TenantOwned;
import com.gms.security.TenantContext;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

/**
 * Reads by id that answer only for the caller's own tenant.
 *
 * <p>{@code C-3} scoped every <em>declared</em> repository query with a tenant predicate and proved it with a
 * build-time scan. It could not scope {@code findById}, because {@code JpaRepository} hands that method to every
 * repository in the project and the scan reads declarations, not inheritances — so 153 service call sites kept
 * loading a row by id alone and then either trusting it or comparing its school by hand. Twenty-three of them did
 * compare; the rest are why {@code SEC-06} was still open after {@code C-3} went green.</p>
 *
 * <p>This is the half of ADR-020 level 2 that a declaration scan cannot reach. A repository whose entity implements
 * {@link TenantOwned} extends this interface instead of {@code JpaRepository} directly, and the override is where
 * the ownership check now lives: the tenant comes from the claims the authentication filter already verified, so a
 * caller cannot pass a different one, cannot pass none, and cannot forget to pass it. School B's id read with
 * school A's token resolves to nothing, which answers 404 rather than returning school A's row.</p>
 *
 * <p>Two callers are deliberately unbounded. The platform owner ({@code SUPERADMIN}) administers across tenants and
 * is identified by its role, not by the system school its account happens to sit in. Code that is not serving a
 * request — the bootstrap runner — has no caller to be bounded by. Everyone else, including an authenticated
 * principal whose token carries no {@code schoolId} claim, is filtered against a tenant of {@code null} and so
 * finds nothing: the claim is what grants the reach, and a missing claim must not become a wildcard.</p>
 *
 * <p>{@code TenantScopedReadArchTest} fails the build when a tenant-owned entity gains a repository that does not
 * extend this, when a service reaches for {@code getById} or {@code getReferenceById} on one, and when anything
 * outside this file calls {@link #findByIdAnyTenant}.</p>
 *
 * @param <T> an entity whose rows belong to exactly one school
 */
@NoRepositoryBean
public interface TenantScopedRepository<T extends TenantOwned> extends JpaRepository<T, Integer> {

    /**
     * The row, and only the row this tenant owns. A {@code null} tenant matches no row rather than every row.
     */
    @Query("select e from #{#entityName} e where e.id = :id and e.school.id = :schoolId")
    Optional<T> findByIdOwnedBy(@Param("id") Integer id, @Param("schoolId") Integer schoolId);

    /**
     * The row whoever owns it. The platform-owner branch of {@link #findById}, and nothing else's.
     */
    @Query("select e from #{#entityName} e where e.id = :id")
    Optional<T> findByIdAnyTenant(@Param("id") Integer id);

    /**
     * The row, if the caller's tenant owns it.
     */
    @Override
    default Optional<T> findById(Integer id) {
        return TenantContext.readsAcrossTenants()
                ? findByIdAnyTenant(id)
                : findByIdOwnedBy(id, TenantContext.schoolId());
    }
}
