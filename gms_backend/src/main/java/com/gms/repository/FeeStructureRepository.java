package com.gms.repository;

import com.gms.model.entity.FeeStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeeStructureRepository extends JpaRepository<FeeStructure, Integer> {

    List<FeeStructure> findBySchoolIdAndIsActive(Integer schoolId, Boolean isActive);

    List<FeeStructure> findBySchoolIdAndAcademicYearAndIsActive(Integer schoolId, String academicYear, Boolean isActive);

    List<FeeStructure> findBySchoolIdAndClassGradeAndStudentCategoryAndIsActive(
            Integer schoolId, String classGrade, String studentCategory, Boolean isActive);
}