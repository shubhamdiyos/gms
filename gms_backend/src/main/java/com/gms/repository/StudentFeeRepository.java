
package com.gms.repository;

import com.gms.model.entity.StudentFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface StudentFeeRepository extends JpaRepository<StudentFee, Integer> {

    List<StudentFee> findByStudent_Id(Integer studentId);

    List<StudentFee> findByStudent_School_IdAndStatus(Integer schoolId, String status);

    List<StudentFee> findByStudent_School_IdAndStudent_IdAndStatus(Integer schoolId, Integer studentId, String status);

    List<StudentFee> findByStudent_School_IdAndFee_AcademicYearAndStatus(Integer schoolId, String academicYear, String status);

    @Query("SELECT SUM(sf.amountPaid) FROM StudentFee sf WHERE sf.student.school.id = :schoolId AND sf.status IN ('PAID', 'PARTIAL')")
    BigDecimal getTotalCollectedFeesBySchoolId(@Param("schoolId") Integer schoolId);

    @Query("SELECT SUM(sf.balanceAmount) FROM StudentFee sf WHERE sf.student.school.id = :schoolId AND sf.status IN ('PENDING', 'PARTIAL', 'OVERDUE')")
    BigDecimal getTotalOutstandingFeesBySchoolId(@Param("schoolId") Integer schoolId);
}
