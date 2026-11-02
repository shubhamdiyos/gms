
package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * A soft-deleted assignment keeps its audit trail and stays out of every JPA read through the
 * restriction; the entity has no lifecycle status column to reuse.
 */
@Getter
@Setter
@Entity
@Table(name = "teacher_assignments")
// The version predicate is required, not stylistic: the entity is @Version'd (BaseAuditEntity), so
// Hibernate binds id AND version to this statement — a single placeholder fails with
// "column index is out of range: 2" at delete time.
@SQLDelete(sql = "UPDATE teacher_assignments SET deleted = true, version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
public class TeacherAssignment extends BaseAuditEntity implements TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Employee teacher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;

    @Column(name = "academic_year", nullable = false, length = 9) // e.g. 2024-2025
    private String academicYear;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;
}
