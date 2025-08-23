package com.gms.model.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ResultResponse {

    private Integer id;

    private Integer studentExamId;

    private String studentName;

    private String examName;

    private String subjectName;

    private Integer obtainedMarks;

    private String grade;

    private BigDecimal percentage;

    private String remarks;

    private String status;
}