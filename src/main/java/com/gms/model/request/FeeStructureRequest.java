package com.gms.model.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FeeStructureRequest {

    private Integer id;

    private String name;

    private String description;

    private String feeCategory;

    private String academicYear;

    private String classGrade;

    private String studentCategory;

    private BigDecimal amount;

    private Boolean isMandatory;

    private Boolean isActive;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}