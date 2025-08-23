package com.gms.service;

import com.gms.model.entity.Notification;
import com.gms.model.request.NotificationRequest;
import com.gms.model.response.NotificationResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationService {

    ResponseEntity<NotificationResponse> createNotification(NotificationRequest request, Integer schoolId, Integer empId);

    ResponseEntity<NotificationResponse> updateNotification(Integer id, NotificationRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteNotification(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<Notification>> getAllNotifications(Integer schoolId);

    ResponseEntity<Notification> getNotificationById(Integer id, Integer schoolId);

    ResponseEntity<List<Notification>> getNotificationsByRecipientType(Integer schoolId, String recipientType);

    ResponseEntity<List<Notification>> getNotificationsByUserId(Integer schoolId, Integer userId);

    ResponseEntity<List<Notification>> getScheduledNotifications(Integer schoolId, LocalDateTime scheduledTime);

    ResponseEntity<List<Notification>> getNotificationsByType(Integer schoolId, String notificationType);

    ResponseEntity<List<Notification>> getNotificationsByPriority(Integer schoolId, String priority);

    ResponseEntity<Long> countPendingNotifications(Integer schoolId);

    ResponseEntity<Long> countUnreadNotifications(Integer schoolId);

    ResponseEntity<?> markNotificationAsRead(Integer id, Integer schoolId);

    ResponseEntity<?> sendScheduledNotifications(Integer schoolId);
}