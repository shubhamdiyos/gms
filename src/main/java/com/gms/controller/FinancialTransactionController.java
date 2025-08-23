package com.gms.controller;

import com.gms.model.entity.FinancialTransaction;
import com.gms.model.request.FinancialTransactionRequest;
import com.gms.model.response.FinancialTransactionResponse;
import com.gms.service.FinancialTransactionService;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/financial-transactions")
public class FinancialTransactionController {

    private final FinancialTransactionService financialTransactionService;

    public FinancialTransactionController(FinancialTransactionService financialTransactionService) {
        this.financialTransactionService = financialTransactionService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FinancialTransactionResponse> createFinancialTransaction(@Valid @RequestBody FinancialTransactionRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return financialTransactionService.createFinancialTransaction(request, schoolId, empId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FinancialTransactionResponse> updateFinancialTransaction(@PathVariable Integer id,
                                                                                 @Valid @RequestBody FinancialTransactionRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return financialTransactionService.updateFinancialTransaction(id, request, schoolId, empId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteFinancialTransaction(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return financialTransactionService.deleteFinancialTransaction(id, schoolId, empId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FinancialTransaction>> getAllFinancialTransactions() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return financialTransactionService.getAllFinancialTransactions(schoolId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<FinancialTransaction> getFinancialTransactionById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return financialTransactionService.getFinancialTransactionById(id, schoolId);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FinancialTransaction>> getFinancialTransactionsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return financialTransactionService.getFinancialTransactionsByDateRange(schoolId, startDate, endDate);
    }

    @GetMapping("/income-summary")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<BigDecimal> getTotalIncomeByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        BigDecimal totalIncome = financialTransactionService.getTotalIncomeByDateRange(schoolId, startDate, endDate);
        return ResponseEntity.ok(totalIncome);
    }

    @GetMapping("/fee-collection")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<BigDecimal> getTotalFeeCollection() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        BigDecimal totalFeeCollection = financialTransactionService.getTotalFeeCollection(schoolId);
        return ResponseEntity.ok(totalFeeCollection);
    }
}