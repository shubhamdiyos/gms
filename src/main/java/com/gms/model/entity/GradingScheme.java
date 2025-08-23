package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "grading_schemes")
public class GradingScheme extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "min_percentage", precision = 5, scale = 2)
    private BigDecimal minPercentage;

    @Column(name = "max_percentage", precision = 5, scale = 2)
    private BigDecimal maxPercentage;

    @Column(name = "grade", length = 10)
    private String grade;

    @Column(name = "grade_point")
    private Integer gradePoint;

    @Column(name = "remarks", length = 200)
    private String remarks;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}