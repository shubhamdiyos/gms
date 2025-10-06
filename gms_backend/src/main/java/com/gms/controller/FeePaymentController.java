package com.gms.controller;

import com.gms.model.entity.FeePayment;
import com.gms.model.request.FeePaymentRequest;
import com.gms.model.response.FeePaymentResponse;
import com.gms.service.FeePaymentService;
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
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return feePaymentService.createFeePayment(request, schoolId, empId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FeePaymentResponse> updateFeePayment(@PathVariable Integer id,
                                                               @Valid @RequestBody FeePaymentRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return feePaymentService.updateFeePayment(id, request, schoolId, empId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteFeePayment(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return feePaymentService.deleteFeePayment(id, schoolId, empId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FeePayment>> getAllFeePayments() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return feePaymentService.getAllFeePayments(schoolId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<FeePayment> getFeePaymentById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return feePaymentService.getFeePaymentById(id, schoolId);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT', 'PARENT')")
    public ResponseEntity<List<FeePayment>> getFeePaymentsByStudent(@PathVariable Integer studentId) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return feePaymentService.getFeePaymentsByStudent(studentId, schoolId);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FeePayment>> getFeePaymentsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return feePaymentService.getFeePaymentsByDateRange(schoolId, startDate, endDate);
    }

    @GetMapping("/total-payments")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<BigDecimal> getTotalPaymentsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        BigDecimal totalPayments = feePaymentService.getTotalPaymentsBySchoolIdAndDateRange(schoolId, startDate, endDate);
        return ResponseEntity.ok(totalPayments);
    }

    @GetMapping("/payment-count")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<Long> getCountOfPaymentsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Long paymentCount = feePaymentService.getCountOfPaymentsBySchoolIdAndDate(schoolId, paymentDate);
        return ResponseEntity.ok(paymentCount);
    }
}