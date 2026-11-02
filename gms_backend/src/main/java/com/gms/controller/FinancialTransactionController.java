package com.gms.controller;

import com.gms.model.entity.FinancialTransaction;
import com.gms.model.request.FinancialTransactionRequest;
import com.gms.model.response.FinancialTransactionResponse;
import com.gms.security.TenantContext;
import com.gms.service.FinancialTransactionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/financial-transactions")
public class FinancialTransactionController {

    private final FinancialTransactionService financialTransactionService;

    public FinancialTransactionController(FinancialTransactionService financialTransactionService) {
        this.financialTransactionService = financialTransactionService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FinancialTransactionResponse> createFinancialTransaction(@Valid @RequestBody FinancialTransactionRequest request) {
        return financialTransactionService.createFinancialTransaction(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FinancialTransactionResponse> updateFinancialTransaction(@PathVariable Integer id,
                                                                                 @Valid @RequestBody FinancialTransactionRequest request) {
        return financialTransactionService.updateFinancialTransaction(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteFinancialTransaction(@PathVariable Integer id) {
        return financialTransactionService.deleteFinancialTransaction(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FinancialTransaction>> getAllFinancialTransactions() {
        return financialTransactionService.getAllFinancialTransactions();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<FinancialTransaction> getFinancialTransactionById(@PathVariable Integer id) {
        return financialTransactionService.getFinancialTransactionById(id);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FinancialTransaction>> getFinancialTransactionsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return financialTransactionService.getFinancialTransactionsByDateRange(startDate, endDate);
    }

    @GetMapping("/income-summary")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<BigDecimal> getTotalIncomeByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        BigDecimal totalIncome = financialTransactionService.getTotalIncomeByDateRange(startDate, endDate);
        return ResponseEntity.ok(totalIncome);
    }

    @GetMapping("/fee-collection")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<BigDecimal> getTotalFeeCollection() {
        BigDecimal totalFeeCollection = financialTransactionService.getTotalFeeCollection();
        return ResponseEntity.ok(totalFeeCollection);
    }
}