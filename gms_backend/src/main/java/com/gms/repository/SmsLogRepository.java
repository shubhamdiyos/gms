package com.gms.repository;

import com.gms.model.entity.SmsLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SmsLogRepository extends JpaRepository<SmsLog, Integer> {

    List<SmsLog> findBySchoolIdAndSentAtBetweenOrderBySentAtDesc(Integer schoolId, LocalDateTime startDate, LocalDateTime endDate);

    List<SmsLog> findBySchoolIdAndDeliveryStatusOrderBySentAtDesc(Integer schoolId, String deliveryStatus);

    List<SmsLog> findBySchoolIdAndToPhoneOrderBySentAtDesc(Integer schoolId, String toPhone);

    List<SmsLog> findBySchoolIdAndRelatedEntityTypeAndRelatedEntityIdOrderBySentAtDesc(Integer schoolId, String entityType, Integer entityId);

    List<SmsLog> findByToPhoneAndDeliveryStatusOrderBySentAtDesc(String toPhone, String deliveryStatus);

    @Query("SELECT s FROM SmsLog s WHERE s.school.id = :schoolId AND s.sentAt >= :startDate AND s.sentAt <= :endDate AND s.deliveryStatus = 'DELIVERED'")
    List<SmsLog> findDeliveredSmsByDateRange(@Param("schoolId") Integer schoolId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(s) FROM SmsLog s WHERE s.school.id = :schoolId AND s.sentAt >= :startDate AND s.sentAt <= :endDate")
    Long countSmsByDateRange(@Param("schoolId") Integer schoolId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(s) FROM SmsLog s WHERE s.school.id = :schoolId AND s.sentAt >= :startDate AND s.sentAt <= :endDate AND s.deliveryStatus = 'FAILED'")
    Long countFailedSmsByDateRange(@Param("schoolId") Integer schoolId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(s) FROM SmsLog s WHERE s.school.id = :schoolId AND s.sentAt >= :startDate AND s.sentAt <= :endDate AND s.deliveryStatus = 'DELIVERED'")
    Long countDeliveredSmsByDateRange(@Param("schoolId") Integer schoolId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT SUM(s.cost) FROM SmsLog s WHERE s.school.id = :schoolId AND s.sentAt >= :startDate AND s.sentAt <= :endDate AND s.deliveryStatus = 'DELIVERED'")
    java.math.BigDecimal getTotalCostOfDeliveredSmsByDateRange(@Param("schoolId") Integer schoolId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}