package com.gms.service;

import com.gms.model.request.NotificationRequest;
import org.springframework.http.ResponseEntity;

public interface CommunicationService {

    ResponseEntity<?> sendNotification(NotificationRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> sendEmail(String toEmail, String subject, String content, Integer schoolId);

    ResponseEntity<?> sendSms(String toPhone, String message, Integer schoolId);

    ResponseEntity<?> sendInAppNotification(NotificationRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> sendPushNotification(NotificationRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> sendBulkNotifications(NotificationRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> processScheduledNotifications(Integer schoolId);
}