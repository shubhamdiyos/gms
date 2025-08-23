package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "exam_subjects")
public class ExamSubject extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "max_marks")
    private Integer maxMarks;

    @Column(name = "passing_marks")
    private Integer passingMarks;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Column(name = "start_time")
    private String startTime; // HH:MM format

    @Column(name = "end_time")
    private String endTime; // HH:MM format

    @Column(name = "status", length = 20, nullable = false)
    private String status = "1"; // 1 = ACTIVE, 0 = INACTIVE
}