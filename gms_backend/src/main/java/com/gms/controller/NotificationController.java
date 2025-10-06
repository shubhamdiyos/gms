package com.gms.controller;

import com.gms.model.entity.Notification;
import com.gms.model.request.NotificationRequest;
import com.gms.model.response.NotificationResponse;
import com.gms.service.NotificationService;
import com.gms.util.SecurityUtil;
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
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return notificationService.createNotification(request, schoolId, empId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<NotificationResponse> updateNotification(@PathVariable Integer id,
                                                                   @Valid @RequestBody NotificationRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return notificationService.updateNotification(id, request, schoolId, empId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<?> deleteNotification(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return notificationService.deleteNotification(id, schoolId, empId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Notification>> getAllNotifications() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return notificationService.getAllNotifications(schoolId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<Notification> getNotificationById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return notificationService.getNotificationById(id, schoolId);
    }

    @GetMapping("/recipient-type/{recipientType}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Notification>> getNotificationsByRecipientType(@PathVariable String recipientType) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return notificationService.getNotificationsByRecipientType(schoolId, recipientType);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'PARENT')")
    public ResponseEntity<List<Notification>> getNotificationsByUserId(@PathVariable Integer userId) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return notificationService.getNotificationsByUserId(schoolId, userId);
    }

    @GetMapping("/scheduled")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<List<Notification>> getScheduledNotifications(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledTime) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return notificationService.getScheduledNotifications(schoolId, scheduledTime);
    }

    @GetMapping("/type/{notificationType}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Notification>> getNotificationsByType(@PathVariable String notificationType) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return notificationService.getNotificationsByType(schoolId, notificationType);
    }

    @GetMapping("/priority/{priority}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<Notification>> getNotificationsByPriority(@PathVariable String priority) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return notificationService.getNotificationsByPriority(schoolId, priority);
    }

    @GetMapping("/pending-count")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<Long> countPendingNotifications() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return notificationService.countPendingNotifications(schoolId);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<Long> countUnreadNotifications() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return notificationService.countUnreadNotifications(schoolId);
    }

    @PutMapping("/{id}/mark-read")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<?> markNotificationAsRead(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return notificationService.markNotificationAsRead(id, schoolId);
    }

    @PostMapping("/send-scheduled")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<?> sendScheduledNotifications() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return notificationService.sendScheduledNotifications(schoolId);
    }
}