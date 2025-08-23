package com.gms.model.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FinancialDashboardResponse {

    // Overall financial summary
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    private BigDecimal netProfit;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;

    // Fee collection statistics
    private BigDecimal totalFeeExpected;
    private BigDecimal totalFeeCollected;
    private BigDecimal totalFeeOutstanding;
    private BigDecimal feeCollectionRate;
    
    // Payment statistics
    private Long totalPayments;
    private BigDecimal averagePaymentAmount;
    private Long paymentsToday;
    private Long paymentsThisWeek;
    private Long paymentsThisMonth;

    // Student fee statistics
    private Long totalStudents;
    private Long studentsWithPendingFees;
    private Long studentsWithOverdueFees;
    private BigDecimal averageFeePerStudent;

    // Recent transactions
    private Object recentPayments; // Will be a list of recent payment objects
    private Object recentInvoices; // Will be a list of recent invoice objects
    
    // Chart data
    private Object monthlyCollectionTrend; // Monthly fee collection data for charts
    private Object paymentMethodDistribution; // Distribution of payments by method
    
    // Alerts and notifications
    private Long overdueInvoices;
    private Long pendingApprovals;
    private Long upcomingDueDates;
    
    // Last updated timestamp
    private LocalDate lastUpdated;
}