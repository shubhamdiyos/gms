package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gms.enums.AdmissionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "student_admissions")
public class StudentAdmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id")
    private Classroom classroom;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "parent_first_name", length = 50)
    private String parentFirstName;

    @Column(name = "parent_last_name", length = 50)
    private String parentLastName;

    @Column(name = "parent_email", length = 150)
    private String parentEmail;

    @Column(name = "parent_phone", length = 15)
    private String parentPhone;

    @Column(name = "previous_school", length = 150)
    private String previousSchool;

    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 15)
    private String emergencyContactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AdmissionStatus status;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @Version
    @Column(name = "version")
    private Integer version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @JsonProperty("school_id")
    public Integer getSchoolId() { return school != null ? school.getId() : null; }

    @JsonProperty("classroom_id")
    public Integer getClassroomId() { return classroom != null ? classroom.getId() : null; }

    @JsonProperty("student_id")
    public Integer getStudentId() { return student != null ? student.getId() : null; }

    @JsonProperty("full_name")
    public String getFullName() { return (firstName + " " + lastName).trim(); }

    @JsonProperty("parent_full_name")
    public String getParentFullName() { return (parentFirstName + " " + parentLastName).trim(); }
}
