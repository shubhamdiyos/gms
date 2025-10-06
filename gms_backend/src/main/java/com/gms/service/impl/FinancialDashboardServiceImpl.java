package com.gms.service.impl;

import com.gms.model.entity.School;
import com.gms.model.response.FinancialDashboardResponse;
import com.gms.repository.SchoolRepository;
import com.gms.service.FeePaymentService;
import com.gms.service.FeeService;
import com.gms.service.StudentFeeService;
import com.gms.service.FinancialDashboardService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
public class FinancialDashboardServiceImpl implements FinancialDashboardService {

    private final SchoolRepository schoolRepository;
    private final FeeService feeService;
    private final StudentFeeService studentFeeService;
    private final FeePaymentService feePaymentService;

    public FinancialDashboardServiceImpl(SchoolRepository schoolRepository,
                                       FeeService feeService,
                                       StudentFeeService studentFeeService,
                                       FeePaymentService feePaymentService) {
        this.schoolRepository = schoolRepository;
        this.feeService = feeService;
        this.studentFeeService = studentFeeService;
        this.feePaymentService = feePaymentService;
    }

    @Override
    public ResponseEntity<FinancialDashboardResponse> getFinancialDashboardData(Integer schoolId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        FinancialDashboardResponse dashboard = new FinancialDashboardResponse();

        // Get current academic year (this would typically come from a service or configuration)
        String currentAcademicYear = getCurrentAcademicYear();

        // Fee collection statistics
        BigDecimal totalFeeExpected = feeService.getTotalFeeAmountBySchoolIdAndAcademicYear(schoolId, currentAcademicYear);
        BigDecimal totalFeeCollected = studentFeeService.getTotalCollectedFeesBySchoolId(schoolId);
        BigDecimal totalFeeOutstanding = studentFeeService.getTotalOutstandingFeesBySchoolId(schoolId);

        dashboard.setTotalFeeExpected(totalFeeExpected != null ? totalFeeExpected : BigDecimal.ZERO);
        dashboard.setTotalFeeCollected(totalFeeCollected != null ? totalFeeCollected : BigDecimal.ZERO);
        dashboard.setTotalFeeOutstanding(totalFeeOutstanding != null ? totalFeeOutstanding : BigDecimal.ZERO);

        // Calculate fee collection rate
        if (totalFeeExpected != null && totalFeeExpected.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal collectionRate = totalFeeCollected.multiply(BigDecimal.valueOf(100))
                    .divide(totalFeeExpected, 2, RoundingMode.HALF_UP);
            dashboard.setFeeCollectionRate(collectionRate);
        } else {
            dashboard.setFeeCollectionRate(BigDecimal.ZERO);
        }

        // Payment statistics (last 30 days)
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        BigDecimal totalPayments = feePaymentService.getTotalPaymentsBySchoolIdAndDateRange(schoolId, startDate, endDate);

        dashboard.setTotalPayments(totalPayments != null ? totalPayments.longValue() : 0L);

        // Average payment amount
        if (totalPayments != null && totalPayments.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal averagePayment = totalPayments.divide(BigDecimal.valueOf(dashboard.getTotalPayments()), 2, RoundingMode.HALF_UP);
            dashboard.setAveragePaymentAmount(averagePayment);
        } else {
            dashboard.setAveragePaymentAmount(BigDecimal.ZERO);
        }

        // Payments today
        Long paymentsToday = feePaymentService.getCountOfPaymentsBySchoolIdAndDate(schoolId, LocalDate.now());
        dashboard.setPaymentsToday(paymentsToday != null ? paymentsToday : 0L);

        // Overall financial summary (placeholder values - would be calculated from actual data)
        dashboard.setTotalIncome(totalFeeCollected != null ? totalFeeCollected : BigDecimal.ZERO);
        dashboard.setTotalExpenses(BigDecimal.ZERO); // Would be calculated from expense data
        dashboard.setNetProfit(totalFeeCollected != null ? totalFeeCollected : BigDecimal.ZERO); // Would be income minus expenses
        dashboard.setTotalAssets(BigDecimal.ZERO); // Would be calculated from asset data
        dashboard.setTotalLiabilities(BigDecimal.ZERO); // Would be calculated from liability data

        // Student statistics (placeholder values - would be calculated from actual data)
        dashboard.setTotalStudents(0L); // Would be calculated from student data
        dashboard.setStudentsWithPendingFees(0L); // Would be calculated from student fee data
        dashboard.setStudentsWithOverdueFees(0L); // Would be calculated from student fee data
        dashboard.setAverageFeePerStudent(BigDecimal.ZERO); // Would be calculated from fee data

        // Alerts and notifications (placeholder values)
        dashboard.setOverdueInvoices(0L); // Would be calculated from invoice data
        dashboard.setPendingApprovals(0L); // Would be calculated from approval workflows
        dashboard.setUpcomingDueDates(0L); // Would be calculated from due date data

        dashboard.setLastUpdated(LocalDate.now());

        return ResponseEntity.ok(dashboard);
    }

    private String getCurrentAcademicYear() {
        // This would typically come from a service or configuration
        // For now, we'll return a placeholder
        int currentYear = LocalDate.now().getYear();
        return currentYear + "-" + (currentYear + 1);
    }
}