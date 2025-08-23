
package com.gms.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "timetable_slots")
public class TimetableSlot extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timetable_id", nullable = false)
    private Timetable timetable;

    @Column(name = "day_of_week", nullable = false, length = 10) // MONDAY, TUESDAY, etc.
    private String dayOfWeek;

    @Column(name = "period", nullable = false)
    private Integer period; // 1, 2, 3, etc.

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private TeacherAssignment assignment;
}
