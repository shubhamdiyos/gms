package com.gms.model.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FinancialReportResponse {

    private Integer id;

    private Integer schoolId;

    private String reportName;

    private String reportType;

    private String reportPeriod;

    private String academicYear;

    private LocalDate generatedDate;

    private BigDecimal totalIncome;

    private BigDecimal totalExpenses;

    private BigDecimal netProfit;

    private BigDecimal outstandingFees;

    private BigDecimal collectedFees;

    private String generatedBy;
}