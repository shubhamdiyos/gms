package com.gms.repository;

import com.gms.model.entity.AcademicYear;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcademicYearRepository extends TenantScopedRepository<AcademicYear> {

    List<AcademicYear> findBySchool_IdAndStatus(Integer schoolId, String status);

    boolean existsBySchool_IdAndName(Integer schoolId, String name);

    /**
     * Every year still carrying the current flag, including soft-deleted rows: the partial unique
     * index {@code uq_academic_years_one_current} does not filter on status, so a deleted year that
     * kept the flag would keep blocking the next one.
     */
    @Query("select ay from AcademicYear ay where ay.school.id = :schoolId and ay.isCurrent = true")
    List<AcademicYear> findCurrentYears(@Param("schoolId") Integer schoolId);
}
