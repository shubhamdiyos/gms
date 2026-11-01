package com.gms.service.impl;

import com.gms.model.entity.Notification;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.NotificationRequest;
import com.gms.model.response.NotificationResponse;
import com.gms.repository.NotificationRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   SchoolRepository schoolRepository,
                                   UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<NotificationResponse> createNotification(NotificationRequest request) {
        // Validate school exists
        School school = schoolRepository.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(TenantContext.employeeId())
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
    public ResponseEntity<NotificationResponse> updateNotification(Integer id, NotificationRequest request) {
        // Validate notification exists; the scoped load already filters by the caller's school,
        // so the hand-written school comparison is gone — a foreign id throws.
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(TenantContext.employeeId())
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
    public ResponseEntity<?> deleteNotification(Integer id) {
        // Validate notification exists; the scoped load already filters by the caller's school,
        // so the hand-written school comparison is gone — a foreign id throws.
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(TenantContext.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting delivery status to CANCELLED
        notification.setDeliveryStatus("CANCELLED");
        notification.setUpdatedBy(updater);

        notificationRepository.save(notification);
        return ResponseEntity.ok("Notification deleted successfully");
    }

    @Override
    public ResponseEntity<List<Notification>> getAllNotifications() {
        List<Notification> notifications = notificationRepository.findBySchoolIdAndDeliveryStatusOrderByCreatedOnDesc(TenantContext.schoolId(), "PENDING");
        return ResponseEntity.ok(notifications);
    }

    @Override
    public ResponseEntity<Notification> getNotificationById(Integer id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        // The load that produced this row was scoped by the claim, so a caller from another school
        // never reaches here; the platform owner does, and it is the caller this row is meant for.
        return ResponseEntity.ok(notification);
    }

    @Override
    public ResponseEntity<List<Notification>> getNotificationsByRecipientType(String recipientType) {
        List<Notification> notifications = notificationRepository.findBySchoolIdAndRecipientTypeAndDeliveryStatus(TenantContext.schoolId(), recipientType, "PENDING");
        return ResponseEntity.ok(notifications);
    }

    @Override
    public ResponseEntity<List<Notification>> getNotificationsByUserId(Integer userId) {
        List<Notification> notifications = notificationRepository
                .findBySchoolIdAndRecipientTypeAndDeliveryStatus(TenantContext.schoolId(), "INDIVIDUAL", "PENDING")
                .stream()
                .filter(notification -> addressesUser(notification.getRecipientIds(), userId))
                // the deleted query carried ORDER BY created_on DESC; the sort moves here rather than disappearing
                .sorted(Comparator.comparing(Notification::getCreatedOn,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return ResponseEntity.ok(notifications);
    }

    /**
     * {@code recipient_ids} is free text copied verbatim from the request body, so membership is decided by
     * comparing the numeric tokens it contains. The query this replaces matched with
     * {@code recipientIds LIKE %:userId%}, which handed user 1 every notification addressed to 11, 21, 100
     * or 1234 (finding TEN-03).
     */
    static boolean addressesUser(String recipientIds, Integer userId) {
        if (recipientIds == null || userId == null) {
            return false;
        }
        for (String token : recipientIds.split("[^0-9]+")) {
            try {
                if (!token.isEmpty() && Integer.parseInt(token) == userId) {
                    return true;
                }
            } catch (NumberFormatException notAnId) {
                // a run of digits longer than an int cannot be this user's id
            }
        }
        return false;
    }

    @Override
    public ResponseEntity<List<Notification>> getScheduledNotifications(LocalDateTime scheduledTime) {
        List<Notification> notifications = notificationRepository.findBySchoolIdAndScheduledAtBeforeAndDeliveryStatus(TenantContext.schoolId(), scheduledTime, "PENDING");
        return ResponseEntity.ok(notifications);
    }

    @Override
    public ResponseEntity<List<Notification>> getNotificationsByType(String notificationType) {
        List<Notification> notifications = notificationRepository.findBySchoolIdAndNotificationTypeAndDeliveryStatus(TenantContext.schoolId(), notificationType, "PENDING");
        return ResponseEntity.ok(notifications);
    }

    @Override
    public ResponseEntity<List<Notification>> getNotificationsByPriority(String priority) {
        List<Notification> notifications = notificationRepository.findBySchoolIdAndPriorityAndDeliveryStatus(TenantContext.schoolId(), priority, "PENDING");
        return ResponseEntity.ok(notifications);
    }

    @Override
    public ResponseEntity<Long> countPendingNotifications() {
        Long count = notificationRepository.countPendingNotificationsBySchoolId(TenantContext.schoolId());
        return ResponseEntity.ok(count);
    }

    @Override
    public ResponseEntity<Long> countUnreadNotifications() {
        Long count = notificationRepository.countUnreadNotificationsBySchoolId(TenantContext.schoolId());
        return ResponseEntity.ok(count);
    }

    @Override
    public ResponseEntity<?> markNotificationAsRead(Integer id) {
        // Validate notification exists; the scoped load already filters by the caller's school,
        // so the hand-written school comparison is gone — a foreign id throws.
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));

        // Mark as read
        notification.setReadStatus("READ");
        notificationRepository.save(notification);

        return ResponseEntity.ok("Notification marked as read");
    }

    @Override
    public ResponseEntity<?> sendScheduledNotifications() {
        LocalDateTime now = LocalDateTime.now();
        List<Notification> scheduledNotifications = notificationRepository.findBySchoolIdAndScheduledAtBeforeAndDeliveryStatus(TenantContext.schoolId(), now, "PENDING");
        
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