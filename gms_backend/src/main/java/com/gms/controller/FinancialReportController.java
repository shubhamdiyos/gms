package com.gms.controller;

import com.gms.model.entity.FinancialReport;
import com.gms.model.request.FinancialReportRequest;
import com.gms.model.response.FinancialReportResponse;
import com.gms.security.TenantContext;
import com.gms.service.FinancialReportService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/financial-reports")
public class FinancialReportController {

    private final FinancialReportService financialReportService;

    public FinancialReportController(FinancialReportService financialReportService) {
        this.financialReportService = financialReportService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FinancialReportResponse> createFinancialReport(@Valid @RequestBody FinancialReportRequest request) {
        return financialReportService.createFinancialReport(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<FinancialReportResponse> updateFinancialReport(@PathVariable Integer id,
                                                                     @Valid @RequestBody FinancialReportRequest request) {
        return financialReportService.updateFinancialReport(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteFinancialReport(@PathVariable Integer id) {
        return financialReportService.deleteFinancialReport(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FinancialReport>> getAllFinancialReports() {
        return financialReportService.getAllFinancialReports();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<FinancialReport> getFinancialReportById(@PathVariable Integer id) {
        return financialReportService.getFinancialReportById(id);
    }

    @GetMapping("/date-range")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FinancialReport>> getFinancialReportsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return financialReportService.getFinancialReportsByDateRange(startDate, endDate);
    }

    @GetMapping("/academic-year")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<FinancialReport>> getFinancialReportsByAcademicYear(@RequestParam String academicYear) {
        return financialReportService.getFinancialReportsByAcademicYear(academicYear);
    }
}