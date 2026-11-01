package com.gms.service;

import com.gms.model.request.NotificationRequest;
import org.springframework.http.ResponseEntity;

public interface CommunicationService {

    ResponseEntity<?> sendNotification(NotificationRequest request);

    ResponseEntity<?> sendEmail(String toEmail, String subject, String content);

    ResponseEntity<?> sendSms(String toPhone, String message);

    ResponseEntity<?> sendInAppNotification(NotificationRequest request);

    ResponseEntity<?> sendPushNotification(NotificationRequest request);

    ResponseEntity<?> sendBulkNotifications(NotificationRequest request);

    ResponseEntity<?> processScheduledNotifications();
}