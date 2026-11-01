package com.gms.config;

import com.gms.model.entity.User;
import com.gms.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns {@code created_by} / {@code updated_by} from columns into an audit trail.
 *
 * <p>MVP feature 11 is "audit logging: the write records WHO wrote it", and until this class existed the
 * answer for most rows was that it records nobody: the columns were mapped on thirty-four tables
 * ({@code BaseAuditEntity}), and nothing in the application wrote them. There was no
 * {@code @PrePersist}/{@code @PreUpdate} in the mapped superclass, no Hibernate interceptor, no
 * {@code AuditorAware} and no {@code @EnableJpaAuditing} anywhere in {@code src/main} — the twenty services
 * that did fill them each did it by hand, at their own call sites, and the fifteen that did not left the
 * trail empty. That is why {@code docs/ARCHITECTURE.md:101} could claim "the audit columns are set by a JPA
 * entity listener, not by callers": it described the intended design, not the code.</p>
 *
 * <p>Spring Data JPA auditing is the mechanism the schema was already shaped for. {@code @EnableJpaAuditing}
 * installs the {@code AuditingHandler} and its entity listener; {@link TenantContextAuditorAware} answers the
 * one question the handler asks — <em>who</em> — and {@code BaseAuditEntity}'s
 * {@code @EntityListeners(AuditingEntityListener.class)} with {@code @CreatedBy} / {@code @LastModifiedBy}
 * tells it which fields to write. Every entity that extends the superclass is covered by that one
 * declaration; no service has to remember anything, which is the difference between a rule and a habit.</p>
 *
 * <p>The timestamps deliberately stay where they are. {@code createdOn} / {@code updatedOn} are written by
 * Hibernate's own {@code @CreationTimestamp} / {@code @UpdateTimestamp}, and only the auditor annotations are
 * added here — annotating the dates with {@code @CreatedDate} / {@code @LastModifiedDate} as well would have
 * put two writers on one column, and Hibernate's are the ones that already agree with the DDL.</p>
 *
 * <p>{@code spring.jpa.hibernate.ddl-auto=validate} is why this changes no schema: every column the auditor
 * writes already exists in the {@code V1} baseline, nullable, with a foreign key to {@code users(id)}.</p>
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditConfiguration {

    /**
     * The auditor, named because {@code auditorAwareRef} has to point at it and an ambiguous
     * {@code AuditorAware} type would fail the context at startup rather than at a write.
     *
     * <p>The repository is taken through an {@code ObjectProvider} and only resolved when a row is actually
     * being stamped. An {@code AuditorAware} that eagerly depends on a JPA repository is wired into the
     * auditing infrastructure, which is built before the repository proxies are, and the usual result is a
     * startup cycle. Deferring it costs nothing now that the answer is a key: {@code getReferenceById} makes
     * no statement at all, so there is nothing to memoise and no select to pay per row.</p>
     */
    @Bean(name = "auditorAware")
    public AuditorAware<User> auditorAware(ObjectProvider<UserRepository> userRepository) {
        return new TenantContextAuditorAware(userRepository::getObject);
    }
}
