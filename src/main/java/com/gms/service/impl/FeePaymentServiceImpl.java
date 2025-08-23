package com.gms.service.impl;

import com.gms.model.entity.FeePayment;
import com.gms.model.entity.StudentFee;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.FeePaymentRequest;
import com.gms.model.response.FeePaymentResponse;
import com.gms.repository.FeePaymentRepository;
import com.gms.repository.StudentFeeRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.FeePaymentService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class FeePaymentServiceImpl extends AbstractCRUDService<FeePayment, Integer> implements FeePaymentService {

    private final FeePaymentRepository feePaymentRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public FeePaymentServiceImpl(FeePaymentRepository feePaymentRepository,
                                StudentFeeRepository studentFeeRepository,
                                SchoolRepository schoolRepository,
                                UserRepository userRepository) {
        super(feePaymentRepository);
        this.feePaymentRepository = feePaymentRepository;
        this.studentFeeRepository = studentFeeRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<FeePaymentResponse> createFeePayment(FeePaymentRequest request, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Validate student fee exists and belongs to school
        StudentFee studentFee = studentFeeRepository.findById(request.getStudentFeeId())
                .orElseThrow(() -> new EntityNotFoundException("Student fee not found"));
        if (!studentFee.getStudent().getSchoolId().equals(schoolId)) {
            throw new IllegalArgumentException("Student fee does not belong to the specified school");
        }

        // Create fee payment
        FeePayment feePayment = new FeePayment();
        feePayment.setStudentFee(studentFee);
        feePayment.setAmount(request.getAmount());
        feePayment.setPaymentDate(request.getPaymentDate());
        feePayment.setPaymentMethod(request.getPaymentMethod());
        feePayment.setTransactionId(request.getTransactionId());
        feePayment.setRemarks(request.getRemarks());
        feePayment.setPaymentReference(request.getPaymentReference());
        feePayment.setBankName(request.getBankName());
        feePayment.setChequeNumber(request.getChequeNumber());
        feePayment.setStatus("SUCCESS");
        feePayment.setCreatedBy(creator);
        feePayment.setUpdatedBy(creator);

        FeePayment savedFeePayment = feePaymentRepository.save(feePayment);

        // Update student fee with payment information
        updateStudentFeeWithPayment(studentFee, request.getAmount());

        return ResponseEntity.ok(mapToResponse(savedFeePayment));
    }

    @Override
    public ResponseEntity<FeePaymentResponse> updateFeePayment(Integer id, FeePaymentRequest request, Integer schoolId, Integer empId) {
        // Validate fee payment exists
        FeePayment feePayment = feePaymentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee payment not found"));

        // Validate school
        if (!feePayment.getStudentFee().getStudent().getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update fee payment
        feePayment.setAmount(request.getAmount());
        feePayment.setPaymentDate(request.getPaymentDate());
        feePayment.setPaymentMethod(request.getPaymentMethod());
        feePayment.setTransactionId(request.getTransactionId());
        feePayment.setRemarks(request.getRemarks());
        feePayment.setPaymentReference(request.getPaymentReference());
        feePayment.setBankName(request.getBankName());
        feePayment.setChequeNumber(request.getChequeNumber());
        feePayment.setStatus(request.getStatus());
        feePayment.setUpdatedBy(updater);

        FeePayment savedFeePayment = feePaymentRepository.save(feePayment);

        return ResponseEntity.ok(mapToResponse(savedFeePayment));
    }

    @Override
    public ResponseEntity<?> deleteFeePayment(Integer id, Integer schoolId, Integer empId) {
        // Validate fee payment exists
        FeePayment feePayment = feePaymentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee payment not found"));

        // Validate school
        if (!feePayment.getStudentFee().getStudent().getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting status to CANCELLED
        feePayment.setStatus("CANCELLED");
        feePayment.setUpdatedBy(updater);

        feePaymentRepository.save(feePayment);
        return ResponseEntity.ok("Fee payment deleted successfully");
    }

    @Override
    public ResponseEntity<List<FeePayment>> getAllFeePayments(Integer schoolId) {
        List<FeePayment> feePayments = feePaymentRepository.findByStudentFee_Student_School_IdAndPaymentDateBetween(
                schoolId, LocalDate.now().minusYears(1), LocalDate.now());
        return ResponseEntity.ok(feePayments);
    }

    @Override
    public ResponseEntity<FeePayment> getFeePaymentById(Integer id, Integer schoolId) {
        FeePayment feePayment = feePaymentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee payment not found"));

        if (!feePayment.getStudentFee().getStudent().getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(feePayment);
    }

    @Override
    public ResponseEntity<List<FeePayment>> getFeePaymentsByStudent(Integer studentId, Integer schoolId) {
        List<FeePayment> feePayments = feePaymentRepository.findByStudentFee_Student_Id(studentId);
        // Filter by schoolId if needed
        return ResponseEntity.ok(feePayments);
    }

    @Override
    public ResponseEntity<List<FeePayment>> getFeePaymentsByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate) {
        List<FeePayment> feePayments = feePaymentRepository.findByStudentFee_Student_School_IdAndPaymentDateBetween(schoolId, startDate, endDate);
        return ResponseEntity.ok(feePayments);
    }

    @Override
    public BigDecimal getTotalPaymentsBySchoolIdAndDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate) {
        return feePaymentRepository.getTotalPaymentsBySchoolIdAndDateRange(schoolId, startDate, endDate);
    }

    @Override
    public Long getCountOfPaymentsBySchoolIdAndDate(Integer schoolId, LocalDate paymentDate) {
        return feePaymentRepository.getCountOfPaymentsBySchoolIdAndDate(schoolId, paymentDate);
    }

    private void updateStudentFeeWithPayment(StudentFee studentFee, BigDecimal paymentAmount) {
        BigDecimal newAmountPaid = studentFee.getAmountPaid().add(paymentAmount);
        studentFee.setAmountPaid(newAmountPaid);
        studentFee.setBalanceAmount(studentFee.getTotalAmount().subtract(newAmountPaid));
        studentFee.setLastPaymentDate(LocalDate.now());
        
        // Update status based on payment amount
        if (newAmountPaid.compareTo(studentFee.getTotalAmount()) >= 0) {
            studentFee.setStatus("PAID");
        } else if (newAmountPaid.compareTo(BigDecimal.ZERO) > 0) {
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
        return response;
    }
}