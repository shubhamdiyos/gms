package com.gms.service.impl;

import com.gms.BasePostgresIntegrationTest;
import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.Classroom;
import com.gms.model.entity.Employee;
import com.gms.model.entity.Fee;
import com.gms.model.entity.FeePayment;
import com.gms.model.entity.Parent;
import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.entity.StudentFee;
import com.gms.model.entity.User;
import com.gms.model.request.FeePaymentRequest;
import com.gms.model.response.FeePaymentResponse;
import com.gms.model.response.StudentFeeResponse;
import com.gms.repository.ClassroomRepository;
import com.gms.repository.EmployeeRepository;
import com.gms.repository.FeePaymentRepository;
import com.gms.repository.FeeRepository;
import com.gms.repository.ParentRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentFeeRepository;
import com.gms.repository.StudentRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.FeePaymentService;
import com.gms.service.ParentService;
import com.gms.service.StudentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeeWorkflowPostgresIntegrationTest extends BasePostgresIntegrationTest {

    private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 11, 4);

    @Autowired private FeePaymentService feePaymentService;
    @Autowired private StudentService studentService;
    @Autowired private ParentService parentService;
    @Autowired private FeePaymentRepository feePaymentRepository;
    @Autowired private StudentFeeRepository studentFeeRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private FeeRepository feeRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private ClassroomRepository classroomRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ParentRepository parentRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    private School schoolOne;
    private School schoolTwo;
    private Student studentOne;
    private Student unlinkedStudentOne;
    private Student studentTwo;
    private StudentFee studentFeeOne;
    private StudentFee secondStudentFeeOne;
    private StudentFee archivedStudentFeeOne;
    private StudentFee studentFeeTwo;
    private User accountantOne;
    private User accountantTwo;
    private User studentUserOne;
    private User parentUserOne;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createNativeQuery(
                    "TRUNCATE TABLE fee_payments, student_fees, fees, parent_student, user_roles, " +
                    "users, employees, parents, students, classrooms, schools CASCADE").executeUpdate();
            entityManager.flush();
        });

        transactionTemplate.executeWithoutResult(status -> {
            schoolOne = schoolRepository.save(school("Fee School One", "FS001", "one@fees.test"));
            schoolTwo = schoolRepository.save(school("Fee School Two", "FS002", "two@fees.test"));

            Classroom classroomOne = classroomRepository.save(classroom(schoolOne, "10-A", "A"));
            Classroom unlinkedClassroomOne = classroomRepository.save(classroom(schoolOne, "10-B", "B"));
            Classroom classroomTwo = classroomRepository.save(classroom(schoolTwo, "10-A", "A"));

            studentOne = studentRepository.save(student(schoolOne, classroomOne, "STD001", "Student", "One"));
            unlinkedStudentOne = studentRepository.save(
                    student(schoolOne, unlinkedClassroomOne, "STD002", "Unlinked", "Student"));
            studentTwo = studentRepository.save(student(schoolTwo, classroomTwo, "STD001", "Student", "Two"));

            Fee tuition = feeRepository.save(fee(schoolOne, "TUITION", "Annual tuition", "100.00"));
            Fee transport = feeRepository.save(fee(schoolOne, "TRANSPORT", "School transport", "80.00"));
            Fee archivedFee = feeRepository.save(fee(schoolOne, "LIBRARY", "Archived library fee", "20.00"));
            Fee schoolTwoFee = feeRepository.save(fee(schoolTwo, "TUITION", "Annual tuition", "100.00"));

            studentFeeOne = studentFeeRepository.save(studentFee(schoolOne, studentOne, tuition));
            secondStudentFeeOne = studentFeeRepository.save(studentFee(schoolOne, studentOne, transport));
            StudentFee archived = studentFee(schoolOne, studentOne, archivedFee);
            archived.setStatus("ARCHIVED");
            archivedStudentFeeOne = studentFeeRepository.save(archived);
            studentFeeTwo = studentFeeRepository.save(studentFee(schoolTwo, studentTwo, schoolTwoFee));

            Employee employeeOne = employeeRepository.save(employee(schoolOne, "EMP001", "accountant.one@fees.test"));
            Employee employeeTwo = employeeRepository.save(employee(schoolTwo, "EMP001", "accountant.two@fees.test"));
            accountantOne = userRepository.save(staffUser(schoolOne, employeeOne, "accountant.one"));
            accountantTwo = userRepository.save(staffUser(schoolTwo, employeeTwo, "accountant.two"));

            User studentUser = new User();
            studentUser.setSchool(schoolOne);
            studentUser.setFullName("Student One");
            studentUser.setEmail("student.one@fees.test");
            studentUser.setUsername("student.one");
            studentUser.setUserId("STDUSR001");
            studentUser.setPassword("encoded-password");
            studentUser.setRoles(Set.of("STUDENT"));
            studentUser.setStudent(studentOne);
            studentUserOne = userRepository.save(studentUser);

            Parent parent = new Parent();
            parent.setFirstName("Parent");
            parent.setLastName("One");
            parent.setEmail("parent.one@fees.test");
            parent.setStudents(new ArrayList<>(List.of(studentOne)));
            parent = parentRepository.save(parent);

            User parentUser = new User();
            parentUser.setSchool(schoolOne);
            parentUser.setFullName("Parent One");
            parentUser.setEmail("parent.one@fees.test");
            parentUser.setUsername("parent.one");
            parentUser.setUserId("PARUSR001");
            parentUser.setPassword("encoded-password");
            parentUser.setRoles(Set.of("PARENT"));
            parentUser.setParent(parent);
            parentUserOne = userRepository.save(parentUser);
        });
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Concurrent distinct payments cannot overpay the same locked student fee")
    void concurrentDistinctPaymentsCannotOverpaySameFee() throws InterruptedException {
        ConcurrentResult result = runConcurrently(
                paymentRequest(studentFeeOne, "60.00", "RACE-A"),
                paymentRequest(studentFeeOne, "60.00", "RACE-B"));

        assertThat(result.successes()).isEqualTo(1);
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure).isInstanceOf(IllegalArgumentException.class);
            assertThat(failure).hasMessage("Payment amount exceeds the outstanding balance.");
        });
        assertThat(paymentsFor(studentFeeOne, schoolOne)).hasSize(1);
        assertStudentFeeBalance(studentFeeOne, "60.00", "40.00", "PARTIAL");
    }

    @Test
    @DisplayName("Concurrent duplicate references persist only one payment across different fee rows")
    void concurrentDuplicateReferencesPersistOnlyOnePayment() throws InterruptedException {
        ConcurrentResult result = runConcurrently(
                paymentRequest(studentFeeOne, "20.00", "DUPLICATE-REF"),
                paymentRequest(secondStudentFeeOne, "20.00", "DUPLICATE-REF"));

        assertThat(result.successes()).isEqualTo(1);
        assertThat(result.failures()).singleElement().satisfies(failure ->
                assertThat(failure).hasStackTraceContaining("uq_fee_payment_school_reference"));
        assertThat(paymentsFor(studentFeeOne, schoolOne).size()
                + paymentsFor(secondStudentFeeOne, schoolOne).size()).isEqualTo(1);
        BigDecimal totalPaid = studentFeeAmount(studentFeeOne).add(studentFeeAmount(secondStudentFeeOne));
        assertThat(totalPaid).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("Different schools may reuse the same payment reference")
    void paymentReferenceUniquenessIsTenantScoped() {
        as(accountantOne, () -> feePaymentService.createFeePayment(
                paymentRequest(studentFeeOne, "10.00", "SHARED-REF")));
        as(accountantTwo, () -> feePaymentService.createFeePayment(
                paymentRequest(studentFeeTwo, "10.00", "SHARED-REF")));

        assertThat(paymentsFor(studentFeeOne, schoolOne)).hasSize(1);
        assertThat(paymentsFor(studentFeeTwo, schoolTwo)).hasSize(1);
        assertStudentFeeBalance(studentFeeOne, "10.00", "90.00", "PARTIAL");
        assertStudentFeeBalance(studentFeeTwo, "10.00", "90.00", "PARTIAL");
    }

    @Test
    @DisplayName("Student fee self-service returns complete persisted fee details")
    void studentFeeSelfServiceReturnsCompletePersistedDetails() {
        ResponseEntity<FeePaymentResponse> payment = as(accountantOne, () ->
                feePaymentService.createFeePayment(paymentRequest(studentFeeOne, "25.00", "STUDENT-FEE")));
        assertThat(payment.getStatusCode().is2xxSuccessful()).isTrue();

        List<StudentFeeResponse> responses = as(studentUserOne, () ->
                studentService.getMyFees(studentUserOne.getUsername()));

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(StudentFeeResponse::getId)
                .containsExactlyInAnyOrder(studentFeeOne.getId(), secondStudentFeeOne.getId())
                .doesNotContain(archivedStudentFeeOne.getId());
        StudentFeeResponse response = responseById(responses, studentFeeOne.getId());
        assertCompleteResponse(response, "25.00", "75.00");
        StudentFeeResponse pending = responseById(responses, secondStudentFeeOne.getId());
        assertThat(pending.getStatus()).isEqualTo("PENDING");
        assertThat(pending.getAmountPaid()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Parent child-fee self-service returns the same complete fee details")
    void parentFeeSelfServiceReturnsCompletePersistedDetails() {
        as(accountantOne, () -> feePaymentService.createFeePayment(
                paymentRequest(studentFeeOne, "40.00", "PARENT-FEE")));

        List<StudentFeeResponse> responses = as(parentUserOne, () ->
                parentService.getChildFees(parentUserOne.getUsername(), studentOne.getId()));

        assertThat(responses).hasSize(2);
        StudentFeeResponse response = responseById(responses, studentFeeOne.getId());
        assertCompleteResponse(response, "40.00", "60.00");
    }

    @Test
    @DisplayName("Parent child-fee reads reject an unlinked child in the same school")
    void parentCannotReadFeesForUnlinkedChild() {
        assertThatThrownBy(() -> as(parentUserOne, () ->
                parentService.getChildFees(parentUserOne.getUsername(), unlinkedStudentOne.getId())))
                .isInstanceOf(TenantAccessDeniedException.class)
                .hasMessage("You are not authorized to access this student's information");
    }

    private ConcurrentResult runConcurrently(FeePaymentRequest first, FeePaymentRequest second)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        try {
            for (FeePaymentRequest request : List.of(first, second)) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        as(accountantOne, () -> feePaymentService.createFeePayment(request));
                        successes.incrementAndGet();
                    } catch (Throwable failure) {
                        failures.add(failure);
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
        return new ConcurrentResult(successes.get(), List.copyOf(failures));
    }

    private FeePaymentRequest paymentRequest(StudentFee studentFee, String amount, String reference) {
        FeePaymentRequest request = new FeePaymentRequest();
        request.setStudentFeeId(studentFee.getId());
        request.setAmount(new BigDecimal(amount));
        request.setPaymentDate(PAYMENT_DATE);
        request.setPaymentMethod("cash");
        request.setPaymentReference(reference);
        request.setRemarks("PostgreSQL integration test");
        return request;
    }

    private <T> T as(User user, Supplier<T> action) {
        TenantContext.set(new TenantContext.Principal(
                user.getUsername(), user.getId(), user.getSchoolId(), user.getEmployeeId(),
                user.getRoles().contains("PARENT") ? Set.of("PARENT") :
                        user.getRoles().contains("STUDENT") ? Set.of("STUDENT") : Set.of("ACCOUNTANT")));
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }

    private List<FeePayment> paymentsFor(StudentFee studentFee, School school) {
        return feePaymentRepository.findByStudentFee_Student_School_IdAndStudentFee_Id(
                school.getId(), studentFee.getId());
    }

    private BigDecimal studentFeeAmount(StudentFee studentFee) {
        return studentFeeRepository.findById(studentFee.getId()).orElseThrow().getAmountPaid();
    }

    private void assertStudentFeeBalance(
            StudentFee studentFee, String amountPaid, String balance, String status) {
        StudentFee persisted = studentFeeRepository.findById(studentFee.getId()).orElseThrow();
        assertThat(persisted.getAmountPaid()).isEqualByComparingTo(amountPaid);
        assertThat(persisted.getBalanceAmount()).isEqualByComparingTo(balance);
        assertThat(persisted.getStatus()).isEqualTo(status);
        assertThat(persisted.getLastPaymentDate()).isEqualTo(PAYMENT_DATE);
    }

    private StudentFeeResponse responseById(List<StudentFeeResponse> responses, Integer id) {
        return responses.stream().filter(response -> response.getId().equals(id)).findFirst().orElseThrow();
    }

    private void assertCompleteResponse(StudentFeeResponse response, String amountPaid, String balance) {
        assertThat(response.getId()).isEqualTo(studentFeeOne.getId());
        assertThat(response.getStudentId()).isEqualTo(studentOne.getId());
        assertThat(response.getFeeId()).isEqualTo(studentFeeOne.getFee().getId());
        assertThat(response.getStatus()).isEqualTo("PARTIAL");
        assertThat(response.getAmountPaid()).isEqualByComparingTo(amountPaid);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(response.getBalanceAmount()).isEqualByComparingTo(balance);
        assertThat(response.getDueDate()).isEqualTo(studentFeeOne.getDueDate());
        assertThat(response.getLastPaymentDate()).isEqualTo(PAYMENT_DATE);
        assertThat(response.getStudentName()).isEqualTo("Student One");
        assertThat(response.getFeeType()).isEqualTo("TUITION");
        assertThat(response.getDescription()).isEqualTo("Annual tuition");
        assertThat(response.getAcademicYear()).isEqualTo("2026-2027");
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

    private static Classroom classroom(School school, String name, String section) {
        Classroom classroom = new Classroom();
        classroom.setSchool(school);
        classroom.setName(name);
        classroom.setGrade("10");
        classroom.setSection(section);
        return classroom;
    }

    private static Student student(
            School school, Classroom classroom, String studentId, String firstName, String lastName) {
        Student student = new Student();
        student.setSchool(school);
        student.setClassroom(classroom);
        student.setStudentId(studentId);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        return student;
    }

    private static Fee fee(School school, String type, String description, String amount) {
        Fee fee = new Fee();
        fee.setSchool(school);
        fee.setFeeType(type);
        fee.setDescription(description);
        fee.setAmount(new BigDecimal(amount));
        fee.setDueDate(LocalDate.of(2026, 12, 15));
        fee.setAcademicYear("2026-2027");
        fee.setClassGrade("10");
        fee.setStudentCategory("GENERAL");
        fee.setIsMandatory(true);
        fee.setStatus("ACTIVE");
        return fee;
    }

    private static StudentFee studentFee(School school, Student student, Fee fee) {
        StudentFee studentFee = new StudentFee();
        studentFee.setSchool(school);
        studentFee.setStudent(student);
        studentFee.setFee(fee);
        studentFee.setTotalAmount(fee.getAmount());
        studentFee.setAmountPaid(BigDecimal.ZERO);
        studentFee.setBalanceAmount(fee.getAmount());
        studentFee.setDueDate(fee.getDueDate());
        studentFee.setStatus("PENDING");
        studentFee.setWaiverAmount(BigDecimal.ZERO);
        studentFee.setFineAmount(BigDecimal.ZERO);
        return studentFee;
    }

    private static Employee employee(School school, String staffNumber, String email) {
        Employee employee = new Employee();
        employee.setSchool(school);
        employee.setEmployeeId(staffNumber);
        employee.setFirstName("Accountant");
        employee.setLastName("User");
        employee.setEmail(email);
        employee.setEmployeeStatus("1");
        return employee;
    }

    private static User staffUser(School school, Employee employee, String username) {
        User user = new User();
        user.setSchool(school);
        user.setEmployee(employee);
        user.setFullName("Accountant User");
        user.setEmail(employee.getEmail());
        user.setUsername(username);
        user.setUserId(username.toUpperCase().replace('.', '_'));
        user.setPassword("encoded-password");
        user.setRoles(Set.of("ACCOUNTANT"));
        return user;
    }

    private record ConcurrentResult(int successes, List<Throwable> failures) {
    }
}
