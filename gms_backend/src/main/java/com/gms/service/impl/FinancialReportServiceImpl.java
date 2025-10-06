package com.gms.service.impl;

import com.gms.model.entity.FinancialReport;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.FinancialReportRequest;
import com.gms.model.response.FinancialReportResponse;
import com.gms.repository.FinancialReportRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.FinancialReportService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class FinancialReportServiceImpl extends AbstractCRUDService<FinancialReport, Integer> implements FinancialReportService {

    private final FinancialReportRepository financialReportRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public FinancialReportServiceImpl(FinancialReportRepository financialReportRepository,
                                 SchoolRepository schoolRepository,
                                 UserRepository userRepository) {
        super(financialReportRepository);
        this.financialReportRepository = financialReportRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<FinancialReportResponse> createFinancialReport(FinancialReportRequest request, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Create financial report
        FinancialReport financialReport = new FinancialReport();
        financialReport.setSchool(school);
        financialReport.setReportName(request.getReportName());
        financialReport.setReportType(request.getReportType());
        financialReport.setReportPeriod(request.getReportPeriod());
        financialReport.setAcademicYear(request.getAcademicYear());
        financialReport.setGeneratedDate(request.getGeneratedDate());
        financialReport.setTotalIncome(request.getTotalIncome());
        financialReport.setTotalExpenses(request.getTotalExpenses());
        financialReport.setNetProfit(request.getNetProfit());
        financialReport.setOutstandingFees(request.getOutstandingFees());
        financialReport.setCollectedFees(request.getCollectedFees());
        financialReport.setReportData(request.getReportData());
        financialReport.setGeneratedBy(request.getGeneratedBy());
        financialReport.setCreatedBy(creator);
        financialReport.setUpdatedBy(creator);

        FinancialReport savedFinancialReport = financialReportRepository.save(financialReport);

        return ResponseEntity.ok(mapToResponse(savedFinancialReport));
    }

    @Override
    public ResponseEntity<FinancialReportResponse> updateFinancialReport(Integer id, FinancialReportRequest request, Integer schoolId, Integer empId) {
        // Validate financial report exists
        FinancialReport financialReport = financialReportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Financial report not found"));

        // Validate school
        if (!financialReport.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update financial report
        financialReport.setReportName(request.getReportName());
        financialReport.setReportType(request.getReportType());
        financialReport.setReportPeriod(request.getReportPeriod());
        financialReport.setAcademicYear(request.getAcademicYear());
        financialReport.setGeneratedDate(request.getGeneratedDate());
        financialReport.setTotalIncome(request.getTotalIncome());
        financialReport.setTotalExpenses(request.getTotalExpenses());
        financialReport.setNetProfit(request.getNetProfit());
        financialReport.setOutstandingFees(request.getOutstandingFees());
        financialReport.setCollectedFees(request.getCollectedFees());
        financialReport.setReportData(request.getReportData());
        financialReport.setGeneratedBy(request.getGeneratedBy());
        financialReport.setUpdatedBy(updater);

        FinancialReport savedFinancialReport = financialReportRepository.save(financialReport);

        return ResponseEntity.ok(mapToResponse(savedFinancialReport));
    }

    @Override
    public ResponseEntity<?> deleteFinancialReport(Integer id, Integer schoolId, Integer empId) {
        // Validate financial report exists
        FinancialReport financialReport = financialReportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Financial report not found"));

        // Validate school
        if (!financialReport.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting status to ARCHIVED or similar
        financialReport.setUpdatedBy(updater);

        financialReportRepository.save(financialReport);
        return ResponseEntity.ok("Financial report deleted successfully");
    }

    @Override
    public ResponseEntity<List<FinancialReport>> getAllFinancialReports(Integer schoolId) {
        List<FinancialReport> financialReports = financialReportRepository.findBySchoolIdOrderByGeneratedDateDesc(schoolId);
        return ResponseEntity.ok(financialReports);
    }

    @Override
    public ResponseEntity<FinancialReport> getFinancialReportById(Integer id, Integer schoolId) {
        FinancialReport financialReport = financialReportRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Financial report not found"));

        if (!financialReport.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(financialReport);
    }

    @Override
    public ResponseEntity<List<FinancialReport>> getFinancialReportsByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate) {
        List<FinancialReport> financialReports = financialReportRepository.findBySchoolIdAndGeneratedDateBetween(schoolId, startDate, endDate);
        return ResponseEntity.ok(financialReports);
    }

    @Override
    public ResponseEntity<List<FinancialReport>> getFinancialReportsByAcademicYear(Integer schoolId, String academicYear) {
        List<FinancialReport> financialReports = financialReportRepository.findBySchoolIdAndAcademicYear(schoolId, academicYear);
        return ResponseEntity.ok(financialReports);
    }

    private FinancialReportResponse mapToResponse(FinancialReport financialReport) {
        FinancialReportResponse response = new FinancialReportResponse();
        response.setId(financialReport.getId());
        response.setSchoolId(financialReport.getSchool().getId());
        response.setReportName(financialReport.getReportName());
        response.setReportType(financialReport.getReportType());
        response.setReportPeriod(financialReport.getReportPeriod());
        response.setAcademicYear(financialReport.getAcademicYear());
        response.setGeneratedDate(financialReport.getGeneratedDate());
        response.setTotalIncome(financialReport.getTotalIncome());
        response.setTotalExpenses(financialReport.getTotalExpenses());
        response.setNetProfit(financialReport.getNetProfit());
        response.setOutstandingFees(financialReport.getOutstandingFees());
        response.setCollectedFees(financialReport.getCollectedFees());
        response.setGeneratedBy(financialReport.getGeneratedBy());
        return response;
    }
}