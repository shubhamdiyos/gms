package com.gms.service.impl;

import com.gms.model.response.FinancialDashboardResponse;
import com.gms.repository.DashboardStatisticsRepository;
import com.gms.repository.FeePaymentRepository;
import com.gms.repository.InvoiceRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentFeeRepository;
import com.gms.security.TenantContext;
import com.gms.service.FinancialDashboardService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
public class FinancialDashboardServiceImpl implements FinancialDashboardService {

    private final SchoolRepository schoolRepository;
    private final DashboardStatisticsRepository dashboardStatisticsRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final FeePaymentRepository feePaymentRepository;
    private final InvoiceRepository invoiceRepository;

    public FinancialDashboardServiceImpl(SchoolRepository schoolRepository,
                                         DashboardStatisticsRepository dashboardStatisticsRepository,
                                         StudentFeeRepository studentFeeRepository,
                                         FeePaymentRepository feePaymentRepository,
                                         InvoiceRepository invoiceRepository) {
        this.schoolRepository = schoolRepository;
        this.dashboardStatisticsRepository = dashboardStatisticsRepository;
        this.studentFeeRepository = studentFeeRepository;
        this.feePaymentRepository = feePaymentRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<FinancialDashboardResponse> getFinancialDashboardData() {
        Integer schoolId = TenantContext.schoolId();
        schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        LocalDate today = LocalDate.now();
        String academicYear = getCurrentAcademicYear();
        FinancialDashboardResponse dashboard = new FinancialDashboardResponse();

        BigDecimal totalFeeExpected = zeroOrSelf(
                studentFeeRepository.getTotalExpectedFeesBySchoolIdAndAcademicYear(schoolId, academicYear));
        BigDecimal totalFeeCollected = zeroOrSelf(
                studentFeeRepository.getTotalCollectedFeesBySchoolIdAndAcademicYear(schoolId, academicYear));
        BigDecimal totalFeeOutstanding = zeroOrSelf(
                studentFeeRepository.getTotalOutstandingFeesBySchoolIdAndAcademicYear(schoolId, academicYear));

        dashboard.setTotalFeeExpected(totalFeeExpected);
        dashboard.setTotalFeeCollected(totalFeeCollected);
        dashboard.setTotalFeeOutstanding(totalFeeOutstanding);
        dashboard.setFeeCollectionRate(totalFeeExpected.signum() > 0
                ? totalFeeCollected.multiply(BigDecimal.valueOf(100))
                        .divide(totalFeeExpected, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        LocalDate paymentStartDate = today.minusDays(29);
        BigDecimal paymentAmount = zeroOrSelf(
                feePaymentRepository.getTotalPaymentsBySchoolIdAndDateRange(schoolId, paymentStartDate, today));
        long totalPayments = feePaymentRepository.getCountOfPaymentsBySchoolIdAndDateRange(
                schoolId, paymentStartDate, today);
        dashboard.setTotalPayments(totalPayments);
        dashboard.setAveragePaymentAmount(totalPayments > 0
                ? paymentAmount.divide(BigDecimal.valueOf(totalPayments), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        dashboard.setPaymentsToday(feePaymentRepository.getCountOfPaymentsBySchoolIdAndDate(schoolId, today));
        dashboard.setPaymentsThisWeek(feePaymentRepository.getCountOfPaymentsBySchoolIdAndDateRange(
                schoolId, today.minusDays(6), today));
        dashboard.setPaymentsThisMonth(feePaymentRepository.getCountOfPaymentsBySchoolIdAndDateRange(
                schoolId, today.withDayOfMonth(1), today));

        dashboard.setTotalIncome(totalFeeCollected);

        long totalStudents = dashboardStatisticsRepository.countStudentsBySchool(schoolId);
        dashboard.setTotalStudents(totalStudents);
        dashboard.setStudentsWithPendingFees(
                studentFeeRepository.countStudentsWithPendingFees(schoolId, academicYear));
        dashboard.setStudentsWithOverdueFees(
                studentFeeRepository.countStudentsWithOverdueFees(schoolId, academicYear, today));
        dashboard.setAverageFeePerStudent(totalStudents > 0
                ? totalFeeExpected.divide(BigDecimal.valueOf(totalStudents), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        dashboard.setOverdueInvoices(invoiceRepository.countOverdueInvoices(schoolId, today));
        dashboard.setUpcomingDueDates(studentFeeRepository.countUpcomingDueDates(
                schoolId, academicYear, today, today.plusDays(30)));
        dashboard.setLastUpdated(today);

        return ResponseEntity.ok(dashboard);
    }

    private static String getCurrentAcademicYear() {
        int currentYear = LocalDate.now().getYear();
        return currentYear + "-" + (currentYear + 1);
    }

    private static BigDecimal zeroOrSelf(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
