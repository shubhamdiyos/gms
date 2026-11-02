
package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

/**
 * A soft-deleted attendance row keeps its audit trail (who recorded it, created_on) and stays out
 * of every JPA read through the restriction. There is no lifecycle status column to reuse —
 * {@code status} here means PRESENT/ABSENT/LATE — so the flag is its own column.
 */
@Getter
@Setter
@Entity
@Table(name = "attendances")
// The version predicate is required, not stylistic: the entity is @Version'd (BaseAuditEntity), so
// Hibernate binds id AND version to this statement — a single placeholder fails with
// "column index is out of range: 2" at delete time.
@SQLDelete(sql = "UPDATE attendances SET deleted = true, version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
public class Attendance extends BaseAuditEntity implements TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timetable_slot_id", nullable = false)
    private TimetableSlot timetableSlot;

    @Column(name = "status", nullable = false, length = 10) // PRESENT, ABSENT, LATE
    private String status;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;
}
