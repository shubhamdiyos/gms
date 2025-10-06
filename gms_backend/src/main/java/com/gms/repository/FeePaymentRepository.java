
package com.gms.repository;

import com.gms.model.entity.FeePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface FeePaymentRepository extends JpaRepository<FeePayment, Integer> {

    List<FeePayment> findByStudentFee_Student_School_IdAndPaymentDateBetween(Integer schoolId, LocalDate startDate, LocalDate endDate);

    List<FeePayment> findByStudentFee_Student_Id(Integer studentId);

    List<FeePayment> findByStudentFee_Id(Integer studentFeeId);

    @Query("SELECT SUM(fp.amount) FROM FeePayment fp WHERE fp.studentFee.student.school.id = :schoolId AND fp.paymentDate BETWEEN :startDate AND :endDate AND fp.status = 'SUCCESS'")
    BigDecimal getTotalPaymentsBySchoolIdAndDateRange(@Param("schoolId") Integer schoolId, 
                                                     @Param("startDate") LocalDate startDate, 
                                                     @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(fp) FROM FeePayment fp WHERE fp.studentFee.student.school.id = :schoolId AND fp.paymentDate = :paymentDate AND fp.status = 'SUCCESS'")
    Long getCountOfPaymentsBySchoolIdAndDate(@Param("schoolId") Integer schoolId, 
                                             @Param("paymentDate") LocalDate paymentDate);
}
