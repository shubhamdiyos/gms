
package com.gms.repository;

import com.gms.model.entity.StudentFee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentFeeRepository extends TenantScopedRepository<StudentFee> {

    List<StudentFee> findByStudent_School_IdAndStudent_Id(Integer schoolId, Integer studentId);

    List<StudentFee> findByStudent_School_IdAndStatusNot(Integer schoolId, String status);

    List<StudentFee> findByStudent_School_IdAndStudent_IdAndStatusNot(
            Integer schoolId, Integer studentId, String status);

    List<StudentFee> findByStudent_School_IdAndFee_AcademicYearAndStatusNot(
            Integer schoolId, String academicYear, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sf FROM StudentFee sf WHERE sf.id = :id AND sf.school.id = :schoolId")
    Optional<StudentFee> findByIdForUpdate(@Param("id") Integer id, @Param("schoolId") Integer schoolId);

    @Query("SELECT SUM(sf.totalAmount) FROM StudentFee sf WHERE sf.school.id = :schoolId AND sf.fee.academicYear = :academicYear AND sf.status <> 'ARCHIVED'")
    BigDecimal getTotalExpectedFeesBySchoolIdAndAcademicYear(
            @Param("schoolId") Integer schoolId, @Param("academicYear") String academicYear);

    @Query("SELECT SUM(sf.amountPaid) FROM StudentFee sf WHERE sf.school.id = :schoolId AND sf.fee.academicYear = :academicYear AND sf.status <> 'ARCHIVED'")
    BigDecimal getTotalCollectedFeesBySchoolIdAndAcademicYear(
            @Param("schoolId") Integer schoolId, @Param("academicYear") String academicYear);

    @Query("SELECT SUM(sf.balanceAmount) FROM StudentFee sf WHERE sf.school.id = :schoolId AND sf.fee.academicYear = :academicYear AND sf.status IN ('PENDING', 'PARTIAL', 'OVERDUE')")
    BigDecimal getTotalOutstandingFeesBySchoolIdAndAcademicYear(
            @Param("schoolId") Integer schoolId, @Param("academicYear") String academicYear);

    @Query("SELECT COUNT(DISTINCT sf.student.id) FROM StudentFee sf WHERE sf.school.id = :schoolId AND sf.fee.academicYear = :academicYear AND sf.status IN ('PENDING', 'PARTIAL', 'OVERDUE')")
    Long countStudentsWithPendingFees(
            @Param("schoolId") Integer schoolId, @Param("academicYear") String academicYear);

    @Query("SELECT COUNT(DISTINCT sf.student.id) FROM StudentFee sf WHERE sf.school.id = :schoolId AND sf.fee.academicYear = :academicYear AND sf.status IN ('PENDING', 'PARTIAL', 'OVERDUE') AND sf.dueDate < :today")
    Long countStudentsWithOverdueFees(
            @Param("schoolId") Integer schoolId, @Param("academicYear") String academicYear,
            @Param("today") LocalDate today);

    @Query("SELECT COUNT(sf) FROM StudentFee sf WHERE sf.school.id = :schoolId AND sf.fee.academicYear = :academicYear AND sf.status IN ('PENDING', 'PARTIAL', 'OVERDUE') AND sf.dueDate BETWEEN :startDate AND :endDate")
    Long countUpcomingDueDates(
            @Param("schoolId") Integer schoolId, @Param("academicYear") String academicYear,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    // Both sums filter on the tenant column student_fees itself carries rather than on
    // student.school.id: `fk_student_fee_student_same_school` ties (student_id, school_id) to
    // students(id, school_id), so the two predicates select the same rows, and dropping the join lets
    // PostgreSQL use idx_student_fee_school instead of hash-joining 509,000 fee rows to 101,800 pupils.
    @Query("SELECT SUM(sf.amountPaid) FROM StudentFee sf WHERE sf.school.id = :schoolId AND sf.status IN ('PAID', 'PARTIAL')")
    BigDecimal getTotalCollectedFeesBySchoolId(@Param("schoolId") Integer schoolId);

    @Query("SELECT SUM(sf.balanceAmount) FROM StudentFee sf WHERE sf.school.id = :schoolId AND sf.status IN ('PENDING', 'PARTIAL', 'OVERDUE')")
    BigDecimal getTotalOutstandingFeesBySchoolId(@Param("schoolId") Integer schoolId);
}
