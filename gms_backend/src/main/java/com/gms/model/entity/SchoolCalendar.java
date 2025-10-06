package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@Table(name = "school_calendars")
public class SchoolCalendar extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_type", length = 50)
    private String eventType; // HOLIDAY, EXAM, EVENT, etc.

    @Column(name = "is_recurring")
    private Boolean isRecurring = false;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "1";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @JsonProperty("school_id")
    public Integer getSchoolId() {
        return school != null ? school.getId() : null;
    }

    @JsonProperty("school_name")
    public String getSchoolName() {
        return school != null ? school.getSchoolName() : null;
    }
}