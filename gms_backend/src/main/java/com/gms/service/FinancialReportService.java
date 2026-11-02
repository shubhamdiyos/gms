package com.gms.service;

import com.gms.model.entity.FinancialReport;
import com.gms.model.request.FinancialReportRequest;
import com.gms.model.response.FinancialReportResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

public interface FinancialReportService {

    ResponseEntity<FinancialReportResponse> createFinancialReport(FinancialReportRequest request);

    ResponseEntity<FinancialReportResponse> updateFinancialReport(Integer id, FinancialReportRequest request);

    ResponseEntity<?> deleteFinancialReport(Integer id);

    ResponseEntity<List<FinancialReport>> getAllFinancialReports();

    ResponseEntity<FinancialReport> getFinancialReportById(Integer id);

    ResponseEntity<List<FinancialReport>> getFinancialReportsByDateRange(LocalDate startDate, LocalDate endDate);

    ResponseEntity<List<FinancialReport>> getFinancialReportsByAcademicYear(String academicYear);
}