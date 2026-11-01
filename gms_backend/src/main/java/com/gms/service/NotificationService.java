package com.gms.service;

import com.gms.model.entity.Notification;
import com.gms.model.request.NotificationRequest;
import com.gms.model.response.NotificationResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationService {

    ResponseEntity<NotificationResponse> createNotification(NotificationRequest request);

    ResponseEntity<NotificationResponse> updateNotification(Integer id, NotificationRequest request);

    ResponseEntity<?> deleteNotification(Integer id);

    ResponseEntity<List<Notification>> getAllNotifications();

    ResponseEntity<Notification> getNotificationById(Integer id);

    ResponseEntity<List<Notification>> getNotificationsByRecipientType(String recipientType);

    ResponseEntity<List<Notification>> getNotificationsByUserId(Integer userId);

    ResponseEntity<List<Notification>> getScheduledNotifications(LocalDateTime scheduledTime);

    ResponseEntity<List<Notification>> getNotificationsByType(String notificationType);

    ResponseEntity<List<Notification>> getNotificationsByPriority(String priority);

    ResponseEntity<Long> countPendingNotifications();

    ResponseEntity<Long> countUnreadNotifications();

    ResponseEntity<?> markNotificationAsRead(Integer id);

    ResponseEntity<?> sendScheduledNotifications();
}