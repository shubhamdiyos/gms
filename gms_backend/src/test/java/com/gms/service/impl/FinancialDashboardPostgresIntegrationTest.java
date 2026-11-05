package com.gms.service.impl;

import com.gms.BasePostgresIntegrationTest;
import com.gms.model.entity.Classroom;
import com.gms.model.entity.Fee;
import com.gms.model.entity.FeePayment;
import com.gms.model.entity.Invoice;
import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.entity.StudentFee;
import com.gms.model.response.FinancialDashboardResponse;
import com.gms.model.response.SchoolDashboardResponse;
import com.gms.repository.ClassroomRepository;
import com.gms.repository.FeePaymentRepository;
import com.gms.repository.FeeRepository;
import com.gms.repository.InvoiceRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentFeeRepository;
import com.gms.repository.StudentRepository;
import com.gms.security.TenantContext;
import com.gms.service.DashboardService;
import com.gms.service.FinancialDashboardService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialDashboardPostgresIntegrationTest extends BasePostgresIntegrationTest {

    @Autowired private FinancialDashboardService financialDashboardService;
    @Autowired private DashboardService dashboardService;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private ClassroomRepository classroomRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private FeeRepository feeRepository;
    @Autowired private StudentFeeRepository studentFeeRepository;
    @Autowired private FeePaymentRepository feePaymentRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate.executeWithoutResult(status -> entityManager.createNativeQuery(
                "TRUNCATE TABLE fee_payments, invoices, student_fees, fees, students, classrooms, schools CASCADE")
                .executeUpdate());
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void dashboardUsesRealCurrentYearTenantScopedAggregates() {
        School schoolOne = school("Dashboard One", "FD001", "one@dashboard.test");
        School schoolTwo = school("Dashboard Two", "FD002", "two@dashboard.test");
        transactionTemplate.executeWithoutResult(status -> {
            schoolRepository.save(schoolOne);
            schoolRepository.save(schoolTwo);
            Classroom classroomOne = classroomRepository.save(classroom(schoolOne, "10-A"));
            Classroom classroomTwo = classroomRepository.save(classroom(schoolTwo, "10-A"));

            Student firstStudent = studentRepository.save(student(schoolOne, classroomOne, "STD001", "First"));
            Student secondStudent = studentRepository.save(student(schoolOne, classroomOne, "STD002", "Second"));
            Student archivedStudent = studentRepository.save(student(schoolOne, classroomOne, "STD003", "Archived"));
            archivedStudent.setStatus("0");
            studentRepository.save(archivedStudent);
            Student foreignStudent = studentRepository.save(student(schoolTwo, classroomTwo, "STD001", "Foreign"));

            String academicYear = currentAcademicYear();
            String previousAcademicYear = (today.getYear() - 1) + "-" + today.getYear();
            Fee partialFee = feeRepository.save(fee(schoolOne, "TUITION", "1000.00", academicYear, today.plusDays(5)));
            Fee overdueFee = feeRepository.save(fee(schoolOne, "TRANSPORT", "500.00", academicYear, today.minusDays(1)));
            Fee previousFee = feeRepository.save(fee(schoolOne, "PREVIOUS", "900.00", previousAcademicYear, today.plusDays(6)));
            Fee archivedFee = feeRepository.save(fee(schoolOne, "ARCHIVED", "200.00", academicYear, today.plusDays(7)));
            Fee foreignFee = feeRepository.save(fee(schoolTwo, "TUITION", "700.00", academicYear, today.plusDays(5)));

            StudentFee firstStudentFee = studentFeeRepository.save(studentFee(
                    schoolOne, firstStudent, partialFee, "400.00", "600.00", "PARTIAL"));
            studentFeeRepository.save(studentFee(
                    schoolOne, secondStudent, overdueFee, "0.00", "500.00", "PENDING"));
            studentFeeRepository.save(studentFee(
                    schoolOne, firstStudent, previousFee, "900.00", "0.00", "PAID"));
            studentFeeRepository.save(studentFee(
                    schoolOne, firstStudent, archivedFee, "200.00", "0.00", "ARCHIVED"));
            StudentFee foreignStudentFee = studentFeeRepository.save(studentFee(
                    schoolTwo, foreignStudent, foreignFee, "700.00", "0.00", "PAID"));

            feePaymentRepository.save(payment(schoolOne, firstStudentFee, "100.00", today, "SUCCESS", "PAY-TODAY"));
            feePaymentRepository.save(payment(schoolOne, firstStudentFee, "50.00", today.minusDays(2), "SUCCESS", "PAY-WEEK"));
            feePaymentRepository.save(payment(schoolOne, firstStudentFee, "25.00", today.minusDays(10), "SUCCESS", "PAY-MONTH"));
            feePaymentRepository.save(payment(schoolOne, firstStudentFee, "999.00", today, "FAILED", "PAY-FAILED"));
            feePaymentRepository.save(payment(schoolOne, firstStudentFee, "40.00", today.minusDays(40), "SUCCESS", "PAY-OLD"));
            feePaymentRepository.save(payment(schoolTwo, foreignStudentFee, "1000.00", today, "SUCCESS", "PAY-FOREIGN"));

            invoiceRepository.save(invoice(schoolOne, "INV-OVERDUE", today.minusDays(1), "SENT"));
            invoiceRepository.save(invoice(schoolOne, "INV-PAID", today.minusDays(1), "PAID"));
            invoiceRepository.save(invoice(schoolTwo, "INV-FOREIGN", today.minusDays(1), "SENT"));
        });

        FinancialDashboardResponse dashboard = as(schoolOne,
                () -> financialDashboardService.getFinancialDashboardData().getBody());

        assertThat(dashboard.getTotalFeeExpected()).isEqualByComparingTo("1500.00");
        assertThat(dashboard.getTotalFeeCollected()).isEqualByComparingTo("400.00");
        assertThat(dashboard.getTotalFeeOutstanding()).isEqualByComparingTo("1100.00");
        assertThat(dashboard.getFeeCollectionRate()).isEqualByComparingTo("26.67");
        assertThat(dashboard.getTotalPayments()).isEqualTo(3);
        assertThat(dashboard.getAveragePaymentAmount()).isEqualByComparingTo("58.33");
        assertThat(dashboard.getPaymentsToday()).isEqualTo(1);
        assertThat(dashboard.getPaymentsThisWeek()).isEqualTo(2);
        long expectedPaymentsThisMonth = Stream.of(today, today.minusDays(2), today.minusDays(10))
                .filter(date -> !date.isBefore(today.withDayOfMonth(1)))
                .count();
        assertThat(dashboard.getPaymentsThisMonth()).isEqualTo(expectedPaymentsThisMonth);
        assertThat(dashboard.getTotalIncome()).isEqualByComparingTo("400.00");
        assertThat(dashboard.getTotalStudents()).isEqualTo(2);
        assertThat(dashboard.getStudentsWithPendingFees()).isEqualTo(2);
        assertThat(dashboard.getStudentsWithOverdueFees()).isEqualTo(1);
        assertThat(dashboard.getAverageFeePerStudent()).isEqualByComparingTo("750.00");
        assertThat(dashboard.getOverdueInvoices()).isEqualTo(1);
        assertThat(dashboard.getUpcomingDueDates()).isEqualTo(1);
        assertThat(dashboard.getLastUpdated()).isEqualTo(today);
        assertThat(dashboard.getTotalExpenses()).isNull();
        assertThat(dashboard.getNetProfit()).isNull();
        assertThat(dashboard.getTotalAssets()).isNull();
        assertThat(dashboard.getTotalLiabilities()).isNull();
        assertThat(dashboard.getPendingApprovals()).isNull();

        SchoolDashboardResponse schoolDashboard = as(schoolOne, () -> dashboardService.getSchoolDashboard());
        assertThat(schoolDashboard.getFeesCollectedAmount()).isEqualByComparingTo("400.00");
        assertThat(schoolDashboard.getFeesUncollectedAmount()).isEqualByComparingTo("1100.00");
    }

    @Test
    void emptySchoolReceivesZeroAggregatesAndUnavailableMetricsStayNull() {
        School emptySchool = school("Empty Dashboard", "FD003", "empty@dashboard.test");
        transactionTemplate.executeWithoutResult(status -> schoolRepository.save(emptySchool));

        FinancialDashboardResponse dashboard = as(emptySchool,
                () -> financialDashboardService.getFinancialDashboardData().getBody());

        assertThat(dashboard.getTotalFeeExpected()).isZero();
        assertThat(dashboard.getTotalFeeCollected()).isZero();
        assertThat(dashboard.getTotalFeeOutstanding()).isZero();
        assertThat(dashboard.getFeeCollectionRate()).isZero();
        assertThat(dashboard.getTotalPayments()).isZero();
        assertThat(dashboard.getAveragePaymentAmount()).isZero();
        assertThat(dashboard.getPaymentsToday()).isZero();
        assertThat(dashboard.getPaymentsThisWeek()).isZero();
        assertThat(dashboard.getPaymentsThisMonth()).isZero();
        assertThat(dashboard.getTotalStudents()).isZero();
        assertThat(dashboard.getStudentsWithPendingFees()).isZero();
        assertThat(dashboard.getStudentsWithOverdueFees()).isZero();
        assertThat(dashboard.getAverageFeePerStudent()).isZero();
        assertThat(dashboard.getOverdueInvoices()).isZero();
        assertThat(dashboard.getUpcomingDueDates()).isZero();
        assertThat(dashboard.getTotalExpenses()).isNull();
        assertThat(dashboard.getNetProfit()).isNull();
        assertThat(dashboard.getTotalAssets()).isNull();
        assertThat(dashboard.getTotalLiabilities()).isNull();
        assertThat(dashboard.getPendingApprovals()).isNull();
    }

    private <T> T as(School school, java.util.function.Supplier<T> action) {
        TenantContext.set(new TenantContext.Principal(
                "accountant", 1, school.getId(), 1, Set.of("ACCOUNTANT")));
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }

    private String currentAcademicYear() {
        return today.getYear() + "-" + (today.getYear() + 1);
    }

    private static School school(String name, String code, String email) {
        School school = new School();
        school.setSchoolName(name);
        school.setSchoolCode(code);
        school.setAddress("Integration test address");
        school.setPhone("1234567890");
        school.setEmail(email);
        return school;
    }

    private static Classroom classroom(School school, String name) {
        Classroom classroom = new Classroom();
        classroom.setSchool(school);
        classroom.setName(name);
        classroom.setGrade("10");
        classroom.setSection("A");
        return classroom;
    }

    private static Student student(School school, Classroom classroom, String studentId, String firstName) {
        Student student = new Student();
        student.setSchool(school);
        student.setClassroom(classroom);
        student.setStudentId(studentId);
        student.setFirstName(firstName);
        student.setLastName("Student");
        return student;
    }

    private static Fee fee(School school, String type, String amount, String academicYear, LocalDate dueDate) {
        Fee fee = new Fee();
        fee.setSchool(school);
        fee.setFeeType(type);
        fee.setDescription(type);
        fee.setAmount(new BigDecimal(amount));
        fee.setDueDate(dueDate);
        fee.setAcademicYear(academicYear);
        fee.setClassGrade("10");
        fee.setStudentCategory("GENERAL");
        fee.setIsMandatory(true);
        fee.setStatus("ACTIVE");
        return fee;
    }

    private static StudentFee studentFee(School school, Student student, Fee fee, String amountPaid,
                                         String balance, String status) {
        StudentFee studentFee = new StudentFee();
        studentFee.setSchool(school);
        studentFee.setStudent(student);
        studentFee.setFee(fee);
        studentFee.setTotalAmount(fee.getAmount());
        studentFee.setAmountPaid(new BigDecimal(amountPaid));
        studentFee.setBalanceAmount(new BigDecimal(balance));
        studentFee.setDueDate(fee.getDueDate());
        studentFee.setStatus(status);
        studentFee.setWaiverAmount(BigDecimal.ZERO);
        studentFee.setFineAmount(BigDecimal.ZERO);
        return studentFee;
    }

    private static FeePayment payment(School school, StudentFee studentFee, String amount, LocalDate paymentDate,
                                      String status, String reference) {
        FeePayment payment = new FeePayment();
        payment.setSchool(school);
        payment.setStudentFee(studentFee);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentDate(paymentDate);
        payment.setPaymentMethod("CASH");
        payment.setPaymentReference(reference);
        payment.setStatus(status);
        return payment;
    }

    private Invoice invoice(School school, String number, LocalDate dueDate, String status) {
        Invoice invoice = new Invoice();
        invoice.setSchool(school);
        invoice.setInvoiceNumber(number);
        invoice.setInvoiceDate(today.minusDays(10));
        invoice.setDueDate(dueDate);
        invoice.setTotalAmount(new BigDecimal("300.00"));
        invoice.setAmountPaid("PAID".equals(status) ? new BigDecimal("300.00") : BigDecimal.ZERO);
        invoice.setBalanceAmount("PAID".equals(status) ? BigDecimal.ZERO : new BigDecimal("300.00"));
        invoice.setStatus(status);
        return invoice;
    }
}
