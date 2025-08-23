package com.gms.model.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FinancialReportRequest {

    private Integer id;

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

    private String reportData;

    private String generatedBy;
}