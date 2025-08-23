package com.gms.controller;

import com.gms.model.response.FinancialDashboardResponse;
import com.gms.service.FinancialDashboardService;
import com.gms.util.SecurityUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/financial-dashboard")
public class FinancialDashboardController {

    private final FinancialDashboardService financialDashboardService;

    public FinancialDashboardController(FinancialDashboardService financialDashboardService) {
        this.financialDashboardService = financialDashboardService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<FinancialDashboardResponse> getFinancialDashboard() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return financialDashboardService.getFinancialDashboardData(schoolId);
    }
}