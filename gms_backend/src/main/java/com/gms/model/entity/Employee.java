package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "employee_id", length = 20)
    private String employeeId; // EMP001, EMP002, etc.

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "designation", length = 100)
    private String designation;

    @Column(name = "department", length = 50)
    private String department;

    @Column(name = "qualification", length = 200)
    private String qualification;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "date_of_joining")
    private LocalDate dateOfJoining;

    @Column(name = "salary")
    private BigDecimal salary;

    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 15)
    private String emergencyContactPhone;

    @Column(name = "employee_status", length = 20, nullable = false)
    private String employeeStatus = "1";

    @Column(name = "is_teaching", nullable = false)
    private boolean isTeaching = false;

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

    // JsonProperty methods for API responses
    @JsonProperty("school_id")
    public Integer getSchoolId() {
        return school != null ? school.getId() : null;
    }

    @JsonProperty("school_name")
    public String getSchoolName() {
        return school != null ? school.getSchoolName() : null;
    }

    @JsonProperty("full_name")
    public String getFullName() {
        return firstName + " " + lastName;
    }

    @JsonIgnore
    public School getSchoolEntity() {
        return school;
    }
    
    @JsonIgnore
    public User getCreatedByUser() {
        return createdBy;
    }
    
    @JsonIgnore
    public User getUpdatedByUser() {
        return updatedBy;
    }
    
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
