
package com.gms.repository;

import com.gms.model.entity.StudentFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface StudentFeeRepository extends TenantScopedRepository<StudentFee> {

    List<StudentFee> findByStudent_School_IdAndStudent_Id(Integer schoolId, Integer studentId);

    List<StudentFee> findByStudent_School_IdAndStatus(Integer schoolId, String status);

    List<StudentFee> findByStudent_School_IdAndStudent_IdAndStatus(Integer schoolId, Integer studentId, String status);

    List<StudentFee> findByStudent_School_IdAndFee_AcademicYearAndStatus(Integer schoolId, String academicYear, String status);

    // Both sums filter on the tenant column student_fees itself carries rather than on
    // student.school.id: `fk_student_fee_student_same_school` ties (student_id, school_id) to
    // students(id, school_id), so the two predicates select the same rows, and dropping the join lets
    // PostgreSQL use idx_student_fee_school instead of hash-joining 509,000 fee rows to 101,800 pupils.
    @Query("SELECT SUM(sf.amountPaid) FROM StudentFee sf WHERE sf.school.id = :schoolId AND sf.status IN ('PAID', 'PARTIAL')")
    BigDecimal getTotalCollectedFeesBySchoolId(@Param("schoolId") Integer schoolId);

    @Query("SELECT SUM(sf.balanceAmount) FROM StudentFee sf WHERE sf.school.id = :schoolId AND sf.status IN ('PENDING', 'PARTIAL', 'OVERDUE')")
    BigDecimal getTotalOutstandingFeesBySchoolId(@Param("schoolId") Integer schoolId);
}
