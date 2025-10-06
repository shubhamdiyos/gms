package com.gms.repository;

import com.gms.model.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    List<Notification> findBySchoolIdAndDeliveryStatusOrderByCreatedOnDesc(Integer schoolId, String deliveryStatus);

    List<Notification> findBySchoolIdAndDeliveryStatus(Integer schoolId, String deliveryStatus);

    List<Notification> findBySchoolIdAndRecipientTypeAndDeliveryStatus(Integer schoolId, String recipientType, String deliveryStatus);

    @Query("SELECT n FROM Notification n WHERE n.school.id = :schoolId AND n.recipientType = 'INDIVIDUAL' AND n.recipientIds LIKE %:userId% AND n.deliveryStatus = :status ORDER BY n.createdOn DESC")
    List<Notification> findBySchoolIdAndUserIdAndDeliveryStatus(@Param("schoolId") Integer schoolId, @Param("userId") Integer userId, @Param("status") String status);

    List<Notification> findBySchoolIdAndScheduledAtBeforeAndDeliveryStatus(Integer schoolId, LocalDateTime scheduledTime, String deliveryStatus);

    List<Notification> findBySchoolIdAndNotificationTypeAndDeliveryStatus(Integer schoolId, String notificationType, String deliveryStatus);

    List<Notification> findBySchoolIdAndPriorityAndDeliveryStatus(Integer schoolId, String priority, String deliveryStatus);

    List<Notification> findBySchoolIdAndRelatedEntityTypeAndRelatedEntityId(Integer schoolId, String entityType, Integer entityId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.school.id = :schoolId AND n.deliveryStatus = 'PENDING'")
    Long countPendingNotificationsBySchoolId(@Param("schoolId") Integer schoolId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.school.id = :schoolId AND n.readStatus = 'UNREAD'")
    Long countUnreadNotificationsBySchoolId(@Param("schoolId") Integer schoolId);
}