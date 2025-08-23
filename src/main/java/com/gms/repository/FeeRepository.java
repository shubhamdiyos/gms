
package com.gms.repository;

import com.gms.model.entity.Fee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeeRepository extends JpaRepository<Fee, Integer> {

    List<Fee> findBySchoolIdAndStatus(Integer schoolId, String status);

    List<Fee> findBySchoolIdAndAcademicYearAndStatus(Integer schoolId, String academicYear, String status);

    List<Fee> findBySchoolIdAndClassGradeAndStudentCategoryAndStatus(
            Integer schoolId, String classGrade, String studentCategory, String status);

    @Query("SELECT SUM(f.amount) FROM Fee f WHERE f.school.id = :schoolId AND f.academicYear = :academicYear AND f.status = 'ACTIVE'")
    java.math.BigDecimal getTotalFeeAmountBySchoolIdAndAcademicYear(@Param("schoolId") Integer schoolId, 
                                                                  @Param("academicYear") String academicYear);
}
