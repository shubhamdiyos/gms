package com.gms.service.impl;

import com.gms.model.entity.FinancialTransaction;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.FinancialTransactionRequest;
import com.gms.model.response.FinancialTransactionResponse;
import com.gms.repository.FinancialTransactionRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.FinancialTransactionService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class FinancialTransactionServiceImpl extends AbstractCRUDService<FinancialTransaction, Integer> implements FinancialTransactionService {

    private final FinancialTransactionRepository financialTransactionRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public FinancialTransactionServiceImpl(FinancialTransactionRepository financialTransactionRepository,
                                 SchoolRepository schoolRepository,
                                 UserRepository userRepository) {
        super(financialTransactionRepository);
        this.financialTransactionRepository = financialTransactionRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<FinancialTransactionResponse> createFinancialTransaction(FinancialTransactionRequest request, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Create financial transaction
        FinancialTransaction financialTransaction = new FinancialTransaction();
        financialTransaction.setSchool(school);
        financialTransaction.setTransactionType(request.getTransactionType());
        financialTransaction.setTransactionId(request.getTransactionId());
        financialTransaction.setAmount(request.getAmount());
        financialTransaction.setCurrency(request.getCurrency());
        financialTransaction.setTransactionDate(request.getTransactionDate());
        financialTransaction.setPaymentMethod(request.getPaymentMethod());
        financialTransaction.setPaymentGateway(request.getPaymentGateway());
        financialTransaction.setDescription(request.getDescription());
        financialTransaction.setReferenceNumber(request.getReferenceNumber());
        financialTransaction.setStatus(request.getStatus());
        financialTransaction.setCreatedBy(creator);
        financialTransaction.setUpdatedBy(creator);

        FinancialTransaction savedFinancialTransaction = financialTransactionRepository.save(financialTransaction);

        return ResponseEntity.ok(mapToResponse(savedFinancialTransaction));
    }

    @Override
    public ResponseEntity<FinancialTransactionResponse> updateFinancialTransaction(Integer id, FinancialTransactionRequest request, Integer schoolId, Integer empId) {
        // Validate financial transaction exists
        FinancialTransaction financialTransaction = financialTransactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Financial transaction not found"));

        // Validate school
        if (!financialTransaction.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update financial transaction
        financialTransaction.setTransactionType(request.getTransactionType());
        financialTransaction.setTransactionId(request.getTransactionId());
        financialTransaction.setAmount(request.getAmount());
        financialTransaction.setCurrency(request.getCurrency());
        financialTransaction.setTransactionDate(request.getTransactionDate());
        financialTransaction.setPaymentMethod(request.getPaymentMethod());
        financialTransaction.setPaymentGateway(request.getPaymentGateway());
        financialTransaction.setDescription(request.getDescription());
        financialTransaction.setReferenceNumber(request.getReferenceNumber());
        financialTransaction.setStatus(request.getStatus());
        financialTransaction.setUpdatedBy(updater);

        FinancialTransaction savedFinancialTransaction = financialTransactionRepository.save(financialTransaction);

        return ResponseEntity.ok(mapToResponse(savedFinancialTransaction));
    }

    @Override
    public ResponseEntity<?> deleteFinancialTransaction(Integer id, Integer schoolId, Integer empId) {
        // Validate financial transaction exists
        FinancialTransaction financialTransaction = financialTransactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Financial transaction not found"));

        // Validate school
        if (!financialTransaction.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting status to CANCELLED
        financialTransaction.setStatus("CANCELLED");
        financialTransaction.setUpdatedBy(updater);

        financialTransactionRepository.save(financialTransaction);
        return ResponseEntity.ok("Financial transaction deleted successfully");
    }

    @Override
    public ResponseEntity<List<FinancialTransaction>> getAllFinancialTransactions(Integer schoolId) {
        List<FinancialTransaction> financialTransactions = financialTransactionRepository.findBySchoolIdAndStatus(schoolId, "SUCCESS");
        return ResponseEntity.ok(financialTransactions);
    }

    @Override
    public ResponseEntity<FinancialTransaction> getFinancialTransactionById(Integer id, Integer schoolId) {
        FinancialTransaction financialTransaction = financialTransactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Financial transaction not found"));

        if (!financialTransaction.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(financialTransaction);
    }

    @Override
    public ResponseEntity<List<FinancialTransaction>> getFinancialTransactionsByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate) {
        List<FinancialTransaction> financialTransactions = financialTransactionRepository.findBySchoolIdAndTransactionDateBetween(schoolId, startDate, endDate);
        return ResponseEntity.ok(financialTransactions);
    }

    @Override
    public BigDecimal getTotalIncomeByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate) {
        return financialTransactionRepository.getTotalIncomeBySchoolIdAndDateRange(schoolId, startDate, endDate);
    }

    @Override
    public BigDecimal getTotalFeeCollection(Integer schoolId) {
        return financialTransactionRepository.getTotalFeeCollectionBySchoolId(schoolId);
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