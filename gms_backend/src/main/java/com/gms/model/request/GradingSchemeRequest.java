package com.gms.model.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GradingSchemeRequest {

    private Integer id;

    private String name;

    private String description;

    private BigDecimal minPercentage;

    private BigDecimal maxPercentage;

    private String grade;

    private Integer gradePoint;

    private String remarks;

    private Boolean isActive;
}