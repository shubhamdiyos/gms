package com.gms.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base audit entity containing common audit fields for all entities.
 * All entities should extend this class to automatically get audit trail functionality.
 *
 * <p>The <em>who</em> of the trail is {@link AuthorshipEntity}'s, and this class adds the <em>when</em> and the
 * {@code @Version} to it. {@link AuditingEntityListener} writes both halves: {@code AuditConfiguration}'s
 * {@code @EnableJpaAuditing} supplies the auditor from the verified request context. Before that, the columns
 * existed and thirty-four tables' rows went out with them null unless the service in front of them happened to
 * remember — the mechanism is what makes "every write records who wrote it" a property of the schema rather
 * than a habit of twenty files.</p>
 *
 * <p>{@code @EntityListeners} is repeated on this class rather than left to inheritance because the alternative
 * is a silent failure mode: if a mapped superclass's listener were not inherited, every {@code created_on} row
 * in the database would stop being attributed without a single compile error. Registering the same listener
 * twice for one event writes the same auditor twice, which costs nothing and can never be wrong.</p>
 *
 * <p>The listener only ever <em>adds</em> an attribution and only when there is a caller to attribute the row
 * to: outside a request (the bootstrap runner, a scheduled job) it leaves the field exactly as it found it, so
 * the handful of services that still assign {@code createdBy} by hand keep the value they set. On creation both
 * fields are stamped with the caller; on update only {@code updatedBy} is touched, so the creator of a row
 * cannot be rewritten by whoever edits it next.</p>
 *
 * <p>The timestamps are deliberately not annotated. {@code createdOn} / {@code updatedOn} already have a
 * writer — Hibernate's {@code @CreationTimestamp} / {@code @UpdateTimestamp} — and putting
 * {@code @CreatedDate} / {@code @LastModifiedDate} beside them would have given one column two owners.</p>
 */
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
@MappedSuperclass
public abstract class BaseAuditEntity extends AuthorshipEntity {

    @Version
    @Column(name = "version")
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_on", updatable = false)
    private Instant createdOn;

    @UpdateTimestamp
    @Column(name = "updated_on")
    private Instant updatedOn;
}
