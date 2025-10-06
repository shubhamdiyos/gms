package com.gms.service.impl;

import com.gms.model.entity.Notification;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.NotificationRequest;
import com.gms.model.response.NotificationResponse;
import com.gms.repository.NotificationRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationServiceImpl extends AbstractCRUDService<Notification, Integer> implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   SchoolRepository schoolRepository,
                                   UserRepository userRepository) {
        super(notificationRepository);
        this.notificationRepository = notificationRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<NotificationResponse> createNotification(NotificationRequest request, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Create notification
        Notification notification = new Notification();
        notification.setSchool(school);
        notification.setTitle(request.getTitle());
        notification.setMessage(request.getMessage());
        notification.setNotificationType(request.getNotificationType());
        notification.setChannel(request.getChannel());
        notification.setPriority(request.getPriority());
        notification.setRecipientType(request.getRecipientType());
        notification.setRecipientIds(request.getRecipientIds());
        notification.setScheduledAt(request.getScheduledAt());
        notification.setRelatedEntityType(request.getRelatedEntityType());
        notification.setRelatedEntityId(request.getRelatedEntityId());
        notification.setIsImportant(request.getIsImportant());
        notification.setExpiresAt(request.getExpiresAt());
        notification.setClickActionUrl(request.getClickActionUrl());
        notification.setDeliveryStatus("PENDING");
        notification.setReadStatus("UNREAD");
        notification.setCreatedBy(creator);
        notification.setUpdatedBy(creator);

        Notification savedNotification = notificationRepository.save(notification);

        return ResponseEntity.ok(mapToResponse(savedNotification));
    }

    @Override
    public ResponseEntity<NotificationResponse> updateNotification(Integer id, NotificationRequest request, Integer schoolId, Integer empId) {
        // Validate notification exists
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        // Validate school
        if (!notification.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update notification
        notification.setTitle(request.getTitle());
        notification.setMessage(request.getMessage());
        notification.setNotificationType(request.getNotificationType());
        notification.setChannel(request.getChannel());
        notification.setPriority(request.getPriority());
        notification.setRecipientType(request.getRecipientType());
        notification.setRecipientIds(request.getRecipientIds());
        notification.setScheduledAt(request.getScheduledAt());
        notification.setRelatedEntityType(request.getRelatedEntityType());
        notification.setRelatedEntityId(request.getRelatedEntityId());
        notification.setIsImportant(request.getIsImportant());
        notification.setExpiresAt(request.getExpiresAt());
        notification.setClickActionUrl(request.getClickActionUrl());
        notification.setUpdatedBy(updater);

        Notification savedNotification = notificationRepository.save(notification);

        return ResponseEntity.ok(mapToResponse(savedNotification));
    }

    @Override
    public ResponseEntity<?> deleteNotification(Integer id, Integer schoolId, Integer empId) {
        // Validate notification exists
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        // Validate school
        if (!notification.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting delivery status to CANCELLED
        notification.setDeliveryStatus("CANCELLED");
        notification.setUpdatedBy(updater);

        notificationRepository.save(notification);
        return ResponseEntity.ok("Notification deleted successfully");
    }

    @Override
    public ResponseEntity<List<Notification>> getAllNotifications(Integer schoolId) {
        List<Notification> notifications = notificationRepository.findBySchoolIdAndDeliveryStatusOrderByCreatedOnDesc(schoolId, "PENDING");
        return ResponseEntity.ok(notifications);
    }

    @Override
    public ResponseEntity<Notification> getNotificationById(Integer id, Integer schoolId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        if (!notification.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(notification);
    }

    @Override
    public ResponseEntity<List<Notification>> getNotificationsByRecipientType(Integer schoolId, String recipientType) {
        List<Notification> notifications = notificationRepository.findBySchoolIdAndRecipientTypeAndDeliveryStatus(schoolId, recipientType, "PENDING");
        return ResponseEntity.ok(notifications);
    }

    @Override
    public ResponseEntity<List<Notification>> getNotificationsByUserId(Integer schoolId, Integer userId) {
        List<Notification> notifications = notificationRepository.findBySchoolIdAndUserIdAndDeliveryStatus(schoolId, userId, "PENDING");
        return ResponseEntity.ok(notifications);
    }

    @Override
    public ResponseEntity<List<Notification>> getScheduledNotifications(Integer schoolId, LocalDateTime scheduledTime) {
        List<Notification> notifications = notificationRepository.findBySchoolIdAndScheduledAtBeforeAndDeliveryStatus(schoolId, scheduledTime, "PENDING");
        return ResponseEntity.ok(notifications);
    }

    @Override
    public ResponseEntity<List<Notification>> getNotificationsByType(Integer schoolId, String notificationType) {
        List<Notification> notifications = notificationRepository.findBySchoolIdAndNotificationTypeAndDeliveryStatus(schoolId, notificationType, "PENDING");
        return ResponseEntity.ok(notifications);
    }

    @Override
    public ResponseEntity<List<Notification>> getNotificationsByPriority(Integer schoolId, String priority) {
        List<Notification> notifications = notificationRepository.findBySchoolIdAndPriorityAndDeliveryStatus(schoolId, priority, "PENDING");
        return ResponseEntity.ok(notifications);
    }

    @Override
    public ResponseEntity<Long> countPendingNotifications(Integer schoolId) {
        Long count = notificationRepository.countPendingNotificationsBySchoolId(schoolId);
        return ResponseEntity.ok(count);
    }

    @Override
    public ResponseEntity<Long> countUnreadNotifications(Integer schoolId) {
        Long count = notificationRepository.countUnreadNotificationsBySchoolId(schoolId);
        return ResponseEntity.ok(count);
    }

    @Override
    public ResponseEntity<?> markNotificationAsRead(Integer id, Integer schoolId) {
        // Validate notification exists
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        // Validate school
        if (!notification.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Mark as read
        notification.setReadStatus("READ");
        notificationRepository.save(notification);

        return ResponseEntity.ok("Notification marked as read");
    }

    @Override
    public ResponseEntity<?> sendScheduledNotifications(Integer schoolId) {
        LocalDateTime now = LocalDateTime.now();
        List<Notification> scheduledNotifications = notificationRepository.findBySchoolIdAndScheduledAtBeforeAndDeliveryStatus(schoolId, now, "PENDING");
        
        // In a complete implementation, we would actually send these notifications
        // For now, we'll just return the list
        return ResponseEntity.ok(scheduledNotifications);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setSchoolId(notification.getSchool().getId());
        response.setTitle(notification.getTitle());
        response.setMessage(notification.getMessage());
        response.setNotificationType(notification.getNotificationType());
        response.setChannel(notification.getChannel());
        response.setPriority(notification.getPriority());
        response.setRecipientType(notification.getRecipientType());
        response.setScheduledAt(notification.getScheduledAt());
        response.setSentAt(notification.getSentAt());
        response.setDeliveryStatus(notification.getDeliveryStatus());
        response.setReadStatus(notification.getReadStatus());
        response.setRelatedEntityType(notification.getRelatedEntityType());
        response.setRelatedEntityId(notification.getRelatedEntityId());
        response.setIsImportant(notification.getIsImportant());
        response.setExpiresAt(notification.getExpiresAt());
        response.setClickActionUrl(notification.getClickActionUrl());
        response.setCreatedAt(notification.getCreatedOn() != null ? notification.getCreatedOn().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() : null);
        response.setUpdatedAt(notification.getUpdatedOn() != null ? notification.getUpdatedOn().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() : null);
        return response;
    }
}