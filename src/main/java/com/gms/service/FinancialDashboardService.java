package com.gms.service;

import com.gms.model.response.FinancialDashboardResponse;
import org.springframework.http.ResponseEntity;

public interface FinancialDashboardService {

    ResponseEntity<FinancialDashboardResponse> getFinancialDashboardData(Integer schoolId);
}