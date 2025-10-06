package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "financial_reports")
public class FinancialReport extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "report_name", nullable = false, length = 100)
    private String reportName;

    @Column(name = "report_type", nullable = false, length = 50) // DAILY, WEEKLY, MONTHLY, ANNUAL
    private String reportType;

    @Column(name = "report_period", length = 50) // JANUARY, Q1, FY2024-2025, etc.
    private String reportPeriod;

    @Column(name = "academic_year", length = 9)
    private String academicYear;

    @Column(name = "generated_date", nullable = false)
    private LocalDate generatedDate;

    @Column(name = "total_income", precision = 12, scale = 2)
    private BigDecimal totalIncome;

    @Column(name = "total_expenses", precision = 12, scale = 2)
    private BigDecimal totalExpenses;

    @Column(name = "net_profit", precision = 12, scale = 2)
    private BigDecimal netProfit;

    @Column(name = "outstanding_fees", precision = 12, scale = 2)
    private BigDecimal outstandingFees;

    @Column(name = "collected_fees", precision = 12, scale = 2)
    private BigDecimal collectedFees;

    @Column(name = "report_data", columnDefinition = "TEXT")
    private String reportData; // JSON data for detailed report

    @Column(name = "generated_by", length = 100)
    private String generatedBy;
}