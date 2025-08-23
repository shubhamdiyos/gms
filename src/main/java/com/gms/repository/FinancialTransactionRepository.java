package com.gms.repository;

import com.gms.model.entity.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Integer> {

    List<FinancialTransaction> findBySchoolIdAndTransactionDateBetween(Integer schoolId, LocalDate startDate, LocalDate endDate);

    List<FinancialTransaction> findBySchoolIdAndTransactionType(Integer schoolId, String transactionType);

    List<FinancialTransaction> findBySchoolIdAndStatus(Integer schoolId, String status);

    @Query("SELECT SUM(ft.amount) FROM FinancialTransaction ft WHERE ft.school.id = :schoolId AND ft.transactionDate BETWEEN :startDate AND :endDate AND ft.status = 'SUCCESS'")
    BigDecimal getTotalIncomeBySchoolIdAndDateRange(@Param("schoolId") Integer schoolId, 
                                                   @Param("startDate") LocalDate startDate, 
                                                   @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(ft.amount) FROM FinancialTransaction ft WHERE ft.school.id = :schoolId AND ft.transactionType = 'FEE_PAYMENT' AND ft.status = 'SUCCESS'")
    BigDecimal getTotalFeeCollectionBySchoolId(@Param("schoolId") Integer schoolId);
}