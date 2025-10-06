package com.gms.repository;

import com.gms.model.entity.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcademicYearRepository extends JpaRepository<AcademicYear, Integer> {

    List<AcademicYear> findBySchool_IdAndStatus(Integer schoolId, String status);

    boolean existsBySchool_IdAndName(Integer schoolId, String name);
}
