package com.gms.model.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GradingSchemeResponse {

    private Integer id;

    private Integer schoolId;

    private String name;

    private String description;

    private BigDecimal minPercentage;

    private BigDecimal maxPercentage;

    private String grade;

    private Integer gradePoint;

    private String remarks;

    private Boolean isActive;
}