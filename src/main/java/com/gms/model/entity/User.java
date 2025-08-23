package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter @Setter
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;


    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean requirePasswordChange = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 30)
    private Set<String> roles = new HashSet<>();

    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Parent parent;

    @Column(name = "user_id", length = 20)
    private String userId; // USR001, USR002, etc.

    @Column(name = "username", nullable = false, length = 100, unique = true)
    private String username;

    @Column(name = "user_status", length = 20, nullable = false)
    private String userStatus = "1";

    @Column(name = "password_expired", nullable = false)
    private boolean passwordExpired = false;

    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked = false;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "login_attempts")
    private Integer loginAttempts = 0;

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
    
    @JsonProperty("school_id")
    public Integer getSchoolId() {
        return school != null ? school.getId() : null;
    }
    
    @JsonProperty("school_name")
    public String getSchoolName() {
        return school != null ? school.getSchoolName() : null;
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

    @JsonProperty("employee_id")
    public Integer getEmployeeId() {
        return employee != null ? employee.getId() : null;
    }

    @JsonProperty("employee_name")
    public String getEmployeeName() {
        return employee != null ? employee.getFullName() : null;
    }

    @JsonIgnore
    public Employee getEmployeeEntity() {
        return employee;
    }

    @JsonProperty("student_id")
    public Integer getStudentId() {
        return student != null ? student.getId() : null;
    }

    @JsonProperty("student_name")
    public String getStudentName() {
        return student != null ? student.getFullName() : null;
    }

    @JsonIgnore
    public Student getStudentEntity() {
        return student;
    }

    @JsonProperty("parent_id")
    public Integer getParentId() {
        return parent != null ? parent.getId() : null;
    }

    @JsonProperty("parent_name")
    public String getParentName() {
        return parent != null ? (parent.getFirstName() + " " + parent.getLastName()) : null;
    }

    @JsonIgnore
    public Parent getParentEntity() {
        return parent;
    }
}
