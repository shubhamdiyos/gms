package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "schools")
public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "school_id", length = 20)
    private String schoolId; // SCH001, SCH002, etc.

    @Column(name = "school_name", nullable = false, length = 150)
    private String schoolName;

    @Column(name = "school_code", nullable = false, length = 20, unique = true)
    private String schoolCode;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "principal_name", length = 120)
    private String principalName;

    @Column(name = "established_year")
    private Integer establishedYear;

    @Column(name = "board_affiliation", length = 50)
    private String boardAffiliation; // CBSE, ICSE, STATE_BOARD, etc.

    @Column(name = "status", length = 20, nullable = false)
    private String status = "1";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

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
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // JsonProperty methods for API responses following reference pattern
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
}
