package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gms.enums.StatusEnum;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Designation Entity - Defines job roles and hierarchy within schools
 * Features:
 * - Auto-generated ID with prefix "DES" (DES001, DES002...)
 * - School-specific designations
 * - Hierarchical level system (1=Principal, 2=Vice Principal, etc.)
 * - Department categorization (Academic, Administrative, Support)
 * - Extensible for future organizational needs
 */
@Getter
@Setter
@Entity
@Table(name = "designations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"designation_id"}),
    @UniqueConstraint(columnNames = {"school_id", "title"})
})
public class Designation extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @NotBlank(message = "Designation ID is required")
    @Column(name = "designation_id", nullable = false, length = 10, unique = true)
    private String designationId; // DES001, DES002, etc.

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @NotBlank(message = "Title is required")
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @NotNull(message = "Department is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "department", nullable = false)
    private StatusEnum.Department department;

    @NotNull(message = "Hierarchy level is required")
    @Column(name = "hierarchy_level", nullable = false)
    private Integer hierarchyLevel; // 1=Principal, 2=Vice Principal, 3=Head Teacher, etc.

    @Column(name = "min_qualification", length = 200)
    private String minQualification;

    @Column(name = "min_experience")
    private Integer minExperience; // in years

    @Column(name = "is_teaching_role", nullable = false)
    private Boolean isTeachingRole = false;

    @Column(name = "is_administrative_role", nullable = false)
    private Boolean isAdministrativeRole = false;

    @Column(name = "max_positions")
    private Integer maxPositions; // Maximum number of employees that can have this designation

    @Column(name = "current_count")
    private Integer currentCount = 0; // Current number of employees with this designation

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "reports_to_level")
    private Integer reportsToLevel; // Which hierarchy level this position reports to

    // JsonProperty methods for API responses
    @JsonProperty("designation_id")
    public String getDesignationIdForJson() {
        return designationId;
    }

    @JsonProperty("school_id")
    public Integer getSchoolId() {
        return school != null ? school.getId() : null;
    }

    @JsonProperty("school_name")
    public String getSchoolName() {
        return school != null ? school.getSchoolName() : null;
    }

    @JsonProperty("department_code")
    public String getDepartmentCode() {
        return department != null ? department.getCode() : null;
    }

    @JsonProperty("department_name")
    public String getDepartmentName() {
        return department != null ? department.getDescription() : null;
    }

    @JsonProperty("available_positions")
    public Integer getAvailablePositions() {
        if (maxPositions == null) return null;
        return maxPositions - (currentCount != null ? currentCount : 0);
    }

    @JsonProperty("is_position_available")
    public Boolean isPositionAvailable() {
        if (maxPositions == null) return true; // Unlimited positions
        return (currentCount != null ? currentCount : 0) < maxPositions;
    }

    // Helper method to access school entity without JSON serialization
    @JsonIgnore
    public School getSchoolEntity() {
        return school;
    }
}
