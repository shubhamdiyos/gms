package com.gms.service.impl;

import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.Employee;
import com.gms.model.entity.FeePayment;
import com.gms.model.entity.FinancialTransaction;
import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.request.FinancialTransactionRequest;
import com.gms.model.response.FinancialTransactionResponse;
import com.gms.repository.EmployeeRepository;
import com.gms.repository.FeePaymentRepository;
import com.gms.repository.FinancialTransactionRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentRepository;
import com.gms.security.TenantContext;
import com.gms.service.FinancialTransactionService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class FinancialTransactionServiceImpl implements FinancialTransactionService {

    private final FinancialTransactionRepository financialTransactionRepository;
    private final SchoolRepository schoolRepository;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;
    private final FeePaymentRepository feePaymentRepository;

    public FinancialTransactionServiceImpl(FinancialTransactionRepository financialTransactionRepository,
                                 SchoolRepository schoolRepository,
                                 StudentRepository studentRepository,
                                 EmployeeRepository employeeRepository,
                                 FeePaymentRepository feePaymentRepository) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.schoolRepository = schoolRepository;
        this.studentRepository = studentRepository;
        this.employeeRepository = employeeRepository;
        this.feePaymentRepository = feePaymentRepository;
    }

    @Transactional
    @Override
    public ResponseEntity<FinancialTransactionResponse> createFinancialTransaction(FinancialTransactionRequest request) {
        School school = schoolRepository.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        FinancialTransaction financialTransaction = new FinancialTransaction();
        financialTransaction.setSchool(school);
        applyRequest(financialTransaction, request);

        return ResponseEntity.ok(mapToResponse(financialTransactionRepository.save(financialTransaction)));
    }

    @Transactional
    @Override
    public ResponseEntity<FinancialTransactionResponse> updateFinancialTransaction(Integer id, FinancialTransactionRequest request) {
        FinancialTransaction financialTransaction = financialTransactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Financial transaction not found"));

        applyRequest(financialTransaction, request);

        return ResponseEntity.ok(mapToResponse(financialTransactionRepository.save(financialTransaction)));
    }

    @Transactional
    @Override
    public ResponseEntity<?> deleteFinancialTransaction(Integer id) {
        FinancialTransaction financialTransaction = financialTransactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Financial transaction not found"));

        // Soft delete by setting status to CANCELLED
        financialTransaction.setStatus("CANCELLED");

        financialTransactionRepository.save(financialTransaction);
        return ResponseEntity.ok("Financial transaction deleted successfully");
    }

    @Transactional(readOnly = true)
    @Override
    public ResponseEntity<List<FinancialTransactionResponse>> getAllFinancialTransactions() {
        List<FinancialTransactionResponse> financialTransactions = financialTransactionRepository
                .findBySchoolIdAndStatus(TenantContext.schoolId(), "SUCCESS")
                .stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(financialTransactions);
    }

    @Transactional(readOnly = true)
    @Override
    public ResponseEntity<FinancialTransactionResponse> getFinancialTransactionById(Integer id) {
        FinancialTransaction financialTransaction = financialTransactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Financial transaction not found"));

        return ResponseEntity.ok(mapToResponse(financialTransaction));
    }

    @Transactional(readOnly = true)
    @Override
    public ResponseEntity<List<FinancialTransactionResponse>> getFinancialTransactionsByDateRange(LocalDate startDate, LocalDate endDate) {
        List<FinancialTransactionResponse> financialTransactions = financialTransactionRepository
                .findBySchoolIdAndTransactionDateBetween(TenantContext.schoolId(), startDate, endDate)
                .stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(financialTransactions);
    }

    @Transactional(readOnly = true)
    @Override
    public BigDecimal getTotalIncomeByDateRange(LocalDate startDate, LocalDate endDate) {
        return financialTransactionRepository.getTotalIncomeBySchoolIdAndDateRange(TenantContext.schoolId(), startDate, endDate);
    }

    @Transactional(readOnly = true)
    @Override
    public BigDecimal getTotalFeeCollection() {
        return financialTransactionRepository.getTotalFeeCollectionBySchoolId(TenantContext.schoolId());
    }

    /**
     * Copies the request onto the transaction, including the student, employee and fee payment
     * links the previous version of this service left null.
     */
    private void applyRequest(FinancialTransaction financialTransaction, FinancialTransactionRequest request) {
        financialTransaction.setTransactionType(request.getTransactionType());
        financialTransaction.setTransactionId(request.getTransactionId());
        financialTransaction.setAmount(request.getAmount());
        if (request.getCurrency() != null) {
            financialTransaction.setCurrency(request.getCurrency());
        }
        financialTransaction.setTransactionDate(request.getTransactionDate());
        financialTransaction.setPaymentMethod(request.getPaymentMethod());
        financialTransaction.setPaymentGateway(request.getPaymentGateway());
        financialTransaction.setDescription(request.getDescription());
        financialTransaction.setReferenceNumber(request.getReferenceNumber());
        financialTransaction.setStatus(request.getStatus());
        financialTransaction.setStudent(resolveStudent(request.getStudentId()));
        financialTransaction.setEmployee(resolveEmployee(request.getEmployeeId()));
        financialTransaction.setFeePayment(resolveFeePayment(request.getFeePaymentId()));
    }

    private Student resolveStudent(Integer studentId) {
        if (studentId == null) {
            return null;
        }
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        if (!student.getSchoolId().equals(TenantContext.schoolId())) {
            throw new TenantAccessDeniedException("Student does not belong to the specified school");
        }
        return student;
    }

    private Employee resolveEmployee(Integer id) {
        if (id == null) {
            return null;
        }
        return employeeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
    }

    private FeePayment resolveFeePayment(Integer feePaymentId) {
        if (feePaymentId == null) {
            return null;
        }
        return feePaymentRepository.findById(feePaymentId)
                .orElseThrow(() -> new EntityNotFoundException("Fee payment not found"));
    }

    private FinancialTransactionResponse mapToResponse(FinancialTransaction financialTransaction) {
        FinancialTransactionResponse response = new FinancialTransactionResponse();
        response.setId(financialTransaction.getId());
        response.setSchoolId(financialTransaction.getSchool().getId());
        response.setTransactionType(financialTransaction.getTransactionType());
        response.setTransactionId(financialTransaction.getTransactionId());
        response.setAmount(financialTransaction.getAmount());
        response.setCurrency(financialTransaction.getCurrency());
        response.setTransactionDate(financialTransaction.getTransactionDate());
        response.setPaymentMethod(financialTransaction.getPaymentMethod());
        response.setPaymentGateway(financialTransaction.getPaymentGateway());
        response.setDescription(financialTransaction.getDescription());
        response.setReferenceNumber(financialTransaction.getReferenceNumber());
        response.setStatus(financialTransaction.getStatus());
        
        if (financialTransaction.getStudent() != null) {
            response.setStudentId(financialTransaction.getStudent().getId());
            response.setStudentName(financialTransaction.getStudent().getFullName());
        }
        
        if (financialTransaction.getEmployee() != null) {
            response.setEmployeeId(financialTransaction.getEmployee().getId());
            response.setEmployeeName(financialTransaction.getEmployee().getFullName());
        }
        
        if (financialTransaction.getFeePayment() != null) {
            response.setFeePaymentId(financialTransaction.getFeePayment().getId());
        }
        
        return response;
    }
}