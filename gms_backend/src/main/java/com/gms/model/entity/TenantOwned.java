package com.gms.model.entity;

/**
 * An entity whose rows belong to exactly one tenant.
 *
 * <p>Every implementation maps the {@code school_id} column with the canonical block (a {@code @JsonIgnore}d
 * {@code @ManyToOne} on a column that is {@code NOT NULL} in the database), which is what lets
 * {@link com.gms.repository.TenantScopedRepository} write one JPQL predicate — {@code e.school.id} — instead of
 * asking each of the 153 call sites to remember to add its own. The interface is the compiler's copy of that
 * promise; {@code TenantScopedReadArchTest} is the build's copy of the promise that no repository breaks it.</p>
 */
public interface TenantOwned {

    School getSchool();
}
