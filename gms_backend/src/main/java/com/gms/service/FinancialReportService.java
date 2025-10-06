package com.gms.service;

import com.gms.model.entity.FinancialReport;
import com.gms.model.request.FinancialReportRequest;
import com.gms.model.response.FinancialReportResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

public interface FinancialReportService {

    ResponseEntity<FinancialReportResponse> createFinancialReport(FinancialReportRequest request, Integer schoolId, Integer empId);

    ResponseEntity<FinancialReportResponse> updateFinancialReport(Integer id, FinancialReportRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteFinancialReport(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<FinancialReport>> getAllFinancialReports(Integer schoolId);

    ResponseEntity<FinancialReport> getFinancialReportById(Integer id, Integer schoolId);

    ResponseEntity<List<FinancialReport>> getFinancialReportsByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate);

    ResponseEntity<List<FinancialReport>> getFinancialReportsByAcademicYear(Integer schoolId, String academicYear);
}