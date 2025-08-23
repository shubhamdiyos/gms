package com.gms.controller;

import com.gms.model.entity.FinancialReport;
import com.gms.model.request.FinancialReportRequest;
import com.gms.model.response.FinancialReportResponse;
import com.gms.service.FinancialReportService;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/financial-reports")
public class FinancialReportController {

    private final FinancialReportService financialReportService;

    public FinancialReportController(FinancialReportService financialReportService) {
        this.financialReportService = financialReportService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FinancialReportResponse> createFinancialReport(@Valid @RequestBody FinancialReportRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return financialReportService.createFinancialReport(request, schoolId, empId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FinancialReportResponse> updateFinancialReport(@PathVariable Integer id,
                                                                     @Valid @RequestBody FinancialReportRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return financialReportService.updateFinancialReport(id, request, schoolId, empId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteFinancialReport(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return financialReportService.deleteFinancialReport(id, schoolId, empId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FinancialReport>> getAllFinancialReports() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return financialReportService.getAllFinancialReports(schoolId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<FinancialReport> getFinancialReportById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return financialReportService.getFinancialReportById(id, schoolId);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FinancialReport>> getFinancialReportsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return financialReportService.getFinancialReportsByDateRange(schoolId, startDate, endDate);
    }

    @GetMapping("/academic-year")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FinancialReport>> getFinancialReportsByAcademicYear(@RequestParam String academicYear) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return financialReportService.getFinancialReportsByAcademicYear(schoolId, academicYear);
    }
}