package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * The <em>who</em> of the audit trail, declared once for every table that has the two columns.
 *
 * <p>{@link BaseAuditEntity} is this pair plus {@code version} and the {@code created_on} / {@code updated_on}
 * timestamps, and twenty-eight tables are shaped exactly like that. Five more — {@code schools},
 * {@code students}, {@code classrooms}, {@code subjects} and {@code student_admissions} — stamp time into
 * {@code created_at} / {@code updated_at} instead (V1), so extending {@code BaseAuditEntity} would have given
 * them two pairs of timestamps for one event. They take the authorship alone, and {@code BaseAuditEntity}
 * extends this class, so "who wrote it" has exactly one owner in the mapping.</p>
 *
 * <p>{@link EntityListeners} is why no service has to remember anything: {@link AuditingEntityListener}
 * writes the two fields from {@code AuditConfiguration}'s {@code AuditorAware}, which answers from the
 * verified JWT subject and never from a value in the request body. A row written outside a request — the
 * bootstrap runner, a migration — has no auditor and is left untouched rather than attributed to a guess.</p>
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuthorshipEntity {

    @JsonIgnore
    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @JsonIgnore
    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @JsonProperty("created_by_id")
    public Integer getCreatedById() {
        return identifierOf(createdBy);
    }

    @JsonProperty("created_by_name")
    public String getCreatedByName() {
        return fullNameIfLoaded(createdBy);
    }

    @JsonProperty("updated_by_id")
    public Integer getUpdatedById() {
        return identifierOf(updatedBy);
    }

    @JsonProperty("updated_by_name")
    public String getUpdatedByName() {
        return fullNameIfLoaded(updatedBy);
    }

    /**
     * The name is only in the response when the creator was already loaded.
     *
     * <p>Answering it from the proxy would issue a select per row serialised — one for the author of every
     * pupil on a page — and there is no session to issue it in: {@code open-in-view=false} closes the
     * session with the transaction, before the message converter runs. The id needs no load, so it is
     * always present; the name is dropped rather than bought with an N+1.</p>
     */
    private static String fullNameIfLoaded(User user) {
        if (user == null || !Hibernate.isInitialized(user)) {
            return null;
        }
        return user.getFullName();
    }

    private static Integer identifierOf(User user) {
        if (user == null) {
            return null;
        }
        if (user instanceof HibernateProxy proxy) {
            LazyInitializer initializer = proxy.getHibernateLazyInitializer();
            if (initializer.isUninitialized()) {
                return ((Number) initializer.getIdentifier()).intValue();
            }
        }
        return user.getId();
    }

    @JsonIgnore
    public User getCreatedByUser() {
        return createdBy;
    }

    @JsonIgnore
    public User getUpdatedByUser() {
        return updatedBy;
    }
}
