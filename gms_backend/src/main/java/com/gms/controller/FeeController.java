package com.gms.controller;

import com.gms.model.entity.Fee;
import com.gms.model.request.FeeRequest;
import com.gms.model.response.FeeResponse;
import com.gms.service.FeeService;
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
@RequestMapping("/api/v1/fees")
public class FeeController {

    private final FeeService feeService;

    public FeeController(FeeService feeService) {
        this.feeService = feeService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FeeResponse> createFee(@Valid @RequestBody FeeRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return feeService.createFee(request, schoolId, empId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FeeResponse> updateFee(@PathVariable Integer id,
                                                @Valid @RequestBody FeeRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return feeService.updateFee(id, request, schoolId, empId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteFee(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return feeService.deleteFee(id, schoolId, empId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<Fee>> getAllFees() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return feeService.getAllFees(schoolId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<Fee> getFeeById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return feeService.getFeeById(id, schoolId);
    }

    @GetMapping("/academic-year")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<Fee>> getFeesByAcademicYear(@RequestParam String academicYear) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return feeService.getFeesByAcademicYear(schoolId, academicYear);
    }

    @GetMapping("/total-amount")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<BigDecimal> getTotalFeeAmountBySchoolIdAndAcademicYear(@RequestParam String academicYear) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        BigDecimal totalAmount = feeService.getTotalFeeAmountBySchoolIdAndAcademicYear(schoolId, academicYear);
        return ResponseEntity.ok(totalAmount);
    }
}