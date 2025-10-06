package com.gms.model.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ResultRequest {

    private Integer studentExamId;

    private Integer obtainedMarks;

    private String grade;

    private BigDecimal percentage;

    private String remarks;
}