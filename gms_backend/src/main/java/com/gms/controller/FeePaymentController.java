package com.gms.controller;

import com.gms.model.entity.FeePayment;
import com.gms.model.request.FeePaymentRequest;
import com.gms.model.response.FeePaymentResponse;
import com.gms.security.TenantContext;
import com.gms.service.FeePaymentService;
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
@RequestMapping("/api/v1/fee-payments")
public class FeePaymentController {

    private final FeePaymentService feePaymentService;

    public FeePaymentController(FeePaymentService feePaymentService) {
        this.feePaymentService = feePaymentService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FeePaymentResponse> createFeePayment(@Valid @RequestBody FeePaymentRequest request) {
        return feePaymentService.createFeePayment(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FeePaymentResponse> updateFeePayment(@PathVariable Integer id,
                                                               @Valid @RequestBody FeePaymentRequest request) {
        return feePaymentService.updateFeePayment(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteFeePayment(@PathVariable Integer id) {
        return feePaymentService.deleteFeePayment(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FeePayment>> getAllFeePayments() {
        return feePaymentService.getAllFeePayments();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<FeePayment> getFeePaymentById(@PathVariable Integer id) {
        return feePaymentService.getFeePaymentById(id);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT', 'PARENT')")
    public ResponseEntity<List<FeePayment>> getFeePaymentsByStudent(@PathVariable Integer studentId) {
        return feePaymentService.getFeePaymentsByStudent(studentId);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FeePayment>> getFeePaymentsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return feePaymentService.getFeePaymentsByDateRange(startDate, endDate);
    }

    @GetMapping("/total-payments")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<BigDecimal> getTotalPaymentsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        BigDecimal totalPayments = feePaymentService.getTotalPaymentsBySchoolIdAndDateRange(startDate, endDate);
        return ResponseEntity.ok(totalPayments);
    }

    @GetMapping("/payment-count")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<Long> getCountOfPaymentsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate) {
        Long paymentCount = feePaymentService.getCountOfPaymentsBySchoolIdAndDate(paymentDate);
        return ResponseEntity.ok(paymentCount);
    }
}