package com.gms.repository;

import com.gms.model.entity.FinancialReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FinancialReportRepository extends JpaRepository<FinancialReport, Integer> {

    List<FinancialReport> findBySchoolIdOrderByGeneratedDateDesc(Integer schoolId);

    List<FinancialReport> findBySchoolIdAndReportType(Integer schoolId, String reportType);

    List<FinancialReport> findBySchoolIdAndAcademicYear(Integer schoolId, String academicYear);

    List<FinancialReport> findBySchoolIdAndGeneratedDateBetween(Integer schoolId, LocalDate startDate, LocalDate endDate);
}