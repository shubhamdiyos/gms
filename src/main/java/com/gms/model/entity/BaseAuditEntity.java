package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Base audit entity containing common audit fields for all entities.
 * All entities should extend this class to automatically get audit trail functionality.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseAuditEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Version
    @Column(name = "version")
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_on", updatable = false)
    private Instant createdOn;

    @UpdateTimestamp
    @Column(name = "updated_on")
    private Instant updatedOn;

    // JsonProperty methods for API responses
    @JsonProperty("created_by_id")
    public Integer getCreatedById() {
        return createdBy != null ? createdBy.getId() : null;
    }

    @JsonProperty("created_by_name")
    public String getCreatedByName() {
        return createdBy != null ? createdBy.getFullName() : null;
    }

    @JsonProperty("updated_by_id")
    public Integer getUpdatedById() {
        return updatedBy != null ? updatedBy.getId() : null;
    }

    @JsonProperty("updated_by_name")
    public String getUpdatedByName() {
        return updatedBy != null ? updatedBy.getFullName() : null;
    }

    // Helper methods to access entity objects without JSON serialization
    @JsonIgnore
    public User getCreatedByUser() {
        return createdBy;
    }

    @JsonIgnore
    public User getUpdatedByUser() {
        return updatedBy;
    }
}
