package com.gms.repository;

import com.gms.model.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, Integer> {

    List<EmailLog> findBySchoolIdAndSentAtBetweenOrderBySentAtDesc(Integer schoolId, LocalDateTime startDate, LocalDateTime endDate);

    List<EmailLog> findBySchoolIdAndDeliveryStatusOrderBySentAtDesc(Integer schoolId, String deliveryStatus);

    List<EmailLog> findBySchoolIdAndToEmailOrderBySentAtDesc(Integer schoolId, String toEmail);

    List<EmailLog> findBySchoolIdAndRelatedEntityTypeAndRelatedEntityIdOrderBySentAtDesc(Integer schoolId, String entityType, Integer entityId);

    List<EmailLog> findByToEmailAndDeliveryStatusOrderBySentAtDesc(String toEmail, String deliveryStatus);

    @Query("SELECT e FROM EmailLog e WHERE e.school.id = :schoolId AND e.sentAt >= :startDate AND e.sentAt <= :endDate AND e.deliveryStatus = 'DELIVERED'")
    List<EmailLog> findDeliveredEmailsByDateRange(@Param("schoolId") Integer schoolId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(e) FROM EmailLog e WHERE e.school.id = :schoolId AND e.sentAt >= :startDate AND e.sentAt <= :endDate")
    Long countEmailsByDateRange(@Param("schoolId") Integer schoolId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(e) FROM EmailLog e WHERE e.school.id = :schoolId AND e.sentAt >= :startDate AND e.sentAt <= :endDate AND e.deliveryStatus = 'FAILED'")
    Long countFailedEmailsByDateRange(@Param("schoolId") Integer schoolId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(e) FROM EmailLog e WHERE e.school.id = :schoolId AND e.sentAt >= :startDate AND e.sentAt <= :endDate AND e.deliveryStatus = 'DELIVERED'")
    Long countDeliveredEmailsByDateRange(@Param("schoolId") Integer schoolId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}