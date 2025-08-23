package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "results")
public class Result extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_exam_id", nullable = false)
    private StudentExam studentExam;

    @Column(name = "obtained_marks")
    private Integer obtainedMarks;

    @Column(name = "grade", length = 10)
    private String grade;

    @Column(name = "percentage", precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "1"; // 1 = PUBLISHED, 0 = DRAFT
}