package com.gms.service.impl;

import com.gms.config.TenantContextAuditorAware;
import com.gms.model.entity.FeePayment;
import com.gms.model.entity.StudentFee;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.FeePaymentRequest;
import com.gms.model.response.FeePaymentResponse;
import com.gms.repository.FeePaymentRepository;
import com.gms.repository.StudentFeeRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class FeePaymentServiceImpl implements FeePaymentService {

    private final FeePaymentRepository feePaymentRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final SchoolService schoolService;
    private final UserService userService;
    private final UserRepository userRepository;

    public FeePaymentServiceImpl(FeePaymentRepository feePaymentRepository,
                                StudentFeeRepository studentFeeRepository,
                                SchoolService schoolService,
                                UserService userService,
                                UserRepository userRepository) {
        this.feePaymentRepository = feePaymentRepository;
        this.studentFeeRepository = studentFeeRepository;
        this.schoolService = schoolService;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Transactional
    @Override
    public ResponseEntity<FeePaymentResponse> createFeePayment(FeePaymentRequest request) {
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        if (request.getPaymentReference() == null || request.getPaymentReference().isBlank()) {
            throw new IllegalArgumentException("Payment reference is required.");
        }

        School school = schoolService.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        // BKL-08: attribute the payment to the account the verified token names, not to the employee
        // pointer. An accountant may have a users row and no employees row at all, which made every
        // payment they recorded answer 404 "Creator user not found".
        User employeeCreator = userService.findByEmployee_Id().orElse(null);
        User creator = TenantContextAuditorAware.currentAuditor(userRepository).orElse(employeeCreator);
        if (creator == null) {
            throw new EntityNotFoundException("Creator user not found");
        }

        StudentFee studentFee = studentFeeRepository.findByIdForUpdate(
                        request.getStudentFeeId(), TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("Student fee not found"));

        String paymentReference = request.getPaymentReference().trim();
        if (feePaymentRepository.existsBySchool_IdAndPaymentReference(school.getId(), paymentReference)) {
            throw new IllegalArgumentException("Payment reference already exists for this school.");
        }

        BigDecimal outstanding = studentFee.getTotalAmount().subtract(studentFee.getAmountPaid());
        if (request.getAmount().compareTo(outstanding) > 0) {
            throw new IllegalArgumentException("Payment amount exceeds the outstanding balance.");
        }

        FeePayment feePayment = new FeePayment();
        feePayment.setSchool(school);
        feePayment.setStudentFee(studentFee);
        feePayment.setAmount(request.getAmount());
        feePayment.setPaymentDate(request.getPaymentDate());
        feePayment.setPaymentMethod(request.getPaymentMethod().trim().toUpperCase(Locale.ROOT));
        feePayment.setTransactionId(request.getTransactionId());
        feePayment.setRemarks(request.getRemarks());
        feePayment.setPaymentReference(paymentReference);
        feePayment.setBankName(request.getBankName());
        feePayment.setChequeNumber(request.getChequeNumber());
        feePayment.setStatus("SUCCESS");
        feePayment.setCreatedBy(creator);
        feePayment.setUpdatedBy(creator);

        FeePayment savedFeePayment = feePaymentRepository.saveAndFlush(feePayment);
        updateStudentFeeWithPayment(studentFee, request.getAmount(), request.getPaymentDate());

        return ResponseEntity.ok(mapToResponse(savedFeePayment));
    }

    @Override
    public ResponseEntity<FeePaymentResponse> updateFeePayment(Integer id, FeePaymentRequest request) {
        feePaymentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee payment not found"));
        throw new IllegalArgumentException(
                "Recorded fee payments are immutable; a reversal workflow is required.");
    }

    @Override
    public ResponseEntity<?> deleteFeePayment(Integer id) {
        feePaymentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee payment not found"));
        throw new IllegalArgumentException(
                "Recorded fee payments are immutable; a reversal workflow is required.");
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<FeePaymentResponse>> getAllFeePayments() {
        List<FeePaymentResponse> feePayments = feePaymentRepository
                .findByStudentFee_Student_School_IdAndPaymentDateBetween(
                        TenantContext.schoolId(), LocalDate.now().minusYears(1), LocalDate.now())
                .stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(feePayments);
    }

    @Override
    public ResponseEntity<FeePayment> getFeePaymentById(Integer id) {
        FeePayment feePayment = feePaymentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee payment not found"));

        return ResponseEntity.ok(feePayment);
    }

    @Override
    public ResponseEntity<List<FeePayment>> getFeePaymentsByStudent(Integer studentId) {
        List<FeePayment> feePayments = feePaymentRepository.findByStudentFee_Student_School_IdAndStudentFee_Student_Id(TenantContext.schoolId(), studentId);
        // Filter by the tenant's school if needed
        return ResponseEntity.ok(feePayments);
    }

    @Override
    public ResponseEntity<List<FeePayment>> getFeePaymentsByDateRange(LocalDate startDate, LocalDate endDate) {
        List<FeePayment> feePayments = feePaymentRepository.findByStudentFee_Student_School_IdAndPaymentDateBetween(TenantContext.schoolId(), startDate, endDate);
        return ResponseEntity.ok(feePayments);
    }

    @Override
    public BigDecimal getTotalPaymentsBySchoolIdAndDateRange(LocalDate startDate, LocalDate endDate) {
        return feePaymentRepository.getTotalPaymentsBySchoolIdAndDateRange(TenantContext.schoolId(), startDate, endDate);
    }

    @Override
    public Long getCountOfPaymentsBySchoolIdAndDate(LocalDate paymentDate) {
        return feePaymentRepository.getCountOfPaymentsBySchoolIdAndDate(TenantContext.schoolId(), paymentDate);
    }

    private void updateStudentFeeWithPayment(StudentFee studentFee, BigDecimal paymentAmount,
                                             LocalDate paymentDate) {
        BigDecimal newAmountPaid = studentFee.getAmountPaid().add(paymentAmount);
        studentFee.setAmountPaid(newAmountPaid);
        studentFee.setBalanceAmount(studentFee.getTotalAmount().subtract(newAmountPaid));
        studentFee.setLastPaymentDate(paymentDate);

        if (newAmountPaid.compareTo(studentFee.getTotalAmount()) == 0) {
            studentFee.setStatus("PAID");
        } else {
            studentFee.setStatus("PARTIAL");
        }

        studentFeeRepository.save(studentFee);
    }

    private FeePaymentResponse mapToResponse(FeePayment feePayment) {
        FeePaymentResponse response = new FeePaymentResponse();
        response.setId(feePayment.getId());
        response.setStudentFeeId(feePayment.getStudentFee().getId());
        response.setAmount(feePayment.getAmount());
        response.setPaymentDate(feePayment.getPaymentDate());
        response.setPaymentMethod(feePayment.getPaymentMethod());
        response.setRemarks(feePayment.getRemarks());
        response.setPaymentReference(feePayment.getPaymentReference());
        response.setStatus(feePayment.getStatus());
        response.setStudentName(feePayment.getStudentFee().getStudent().getFullName());
        response.setFeeType(feePayment.getStudentFee().getFee().getFeeType());
        return response;
    }
}