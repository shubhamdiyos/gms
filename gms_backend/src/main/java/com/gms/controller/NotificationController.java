package com.gms.controller;

import com.gms.model.entity.Notification;
import com.gms.model.request.NotificationRequest;
import com.gms.model.response.NotificationResponse;
import com.gms.security.TenantContext;
import com.gms.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<NotificationResponse> createNotification(@Valid @RequestBody NotificationRequest request) {
        return notificationService.createNotification(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<NotificationResponse> updateNotification(@PathVariable Integer id,
                                                                   @Valid @RequestBody NotificationRequest request) {
        return notificationService.updateNotification(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<?> deleteNotification(@PathVariable Integer id) {
        return notificationService.deleteNotification(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Notification>> getAllNotifications() {
        return notificationService.getAllNotifications();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<Notification> getNotificationById(@PathVariable Integer id) {
        return notificationService.getNotificationById(id);
    }

    @GetMapping("/recipient-type/{recipientType}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Notification>> getNotificationsByRecipientType(@PathVariable String recipientType) {
        return notificationService.getNotificationsByRecipientType(recipientType);
    }

    // Admin-only because nothing on this path ever compared the id in it to the caller: a TEACHER or
    // PARENT could walk the ids and read another user's notifications (finding TEN-03). The SPA has no
    // call to this route — it reads /notifications/{id}/read — so narrowing it removes a surface without
    // removing a feature.
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<Notification>> getNotificationsByUserId(@PathVariable Integer userId) {
        return notificationService.getNotificationsByUserId(userId);
    }

    @GetMapping("/scheduled")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<List<Notification>> getScheduledNotifications(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledTime) {
        return notificationService.getScheduledNotifications(scheduledTime);
    }

    @GetMapping("/type/{notificationType}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Notification>> getNotificationsByType(@PathVariable String notificationType) {
        return notificationService.getNotificationsByType(notificationType);
    }

    @GetMapping("/priority/{priority}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Notification>> getNotificationsByPriority(@PathVariable String priority) {
        return notificationService.getNotificationsByPriority(priority);
    }

    @GetMapping("/pending-count")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<Long> countPendingNotifications() {
        return notificationService.countPendingNotifications();
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<Long> countUnreadNotifications() {
        return notificationService.countUnreadNotifications();
    }

    @PutMapping("/{id}/mark-read")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<?> markNotificationAsRead(@PathVariable Integer id) {
        return notificationService.markNotificationAsRead(id);
    }

    @PostMapping("/send-scheduled")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<?> sendScheduledNotifications() {
        return notificationService.sendScheduledNotifications();
    }
}