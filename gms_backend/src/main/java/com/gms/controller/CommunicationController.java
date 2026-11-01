package com.gms.controller;

import com.gms.model.request.NotificationRequest;
import com.gms.security.TenantContext;
import com.gms.service.CommunicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/communications")
public class CommunicationController {

    private final CommunicationService communicationService;

    public CommunicationController(CommunicationService communicationService) {
        this.communicationService = communicationService;
    }

    @PostMapping("/notifications/send")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> sendNotification(@Valid @RequestBody NotificationRequest request) {
        return communicationService.sendNotification(request);
    }

    @PostMapping("/emails/send")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> sendEmail(@RequestParam String toEmail,
                                      @RequestParam String subject,
                                      @RequestParam String content) {
        return communicationService.sendEmail(toEmail, subject, content);
    }

    @PostMapping("/sms/send")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> sendSms(@RequestParam String toPhone,
                                     @RequestParam String message) {
        return communicationService.sendSms(toPhone, message);
    }

    @PostMapping("/notifications/in-app")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> sendInAppNotification(@Valid @RequestBody NotificationRequest request) {
        return communicationService.sendInAppNotification(request);
    }

    @PostMapping("/notifications/push")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> sendPushNotification(@Valid @RequestBody NotificationRequest request) {
        return communicationService.sendPushNotification(request);
    }

    @PostMapping("/notifications/bulk")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> sendBulkNotifications(@Valid @RequestBody NotificationRequest request) {
        return communicationService.sendBulkNotifications(request);
    }

    @PostMapping("/notifications/process-scheduled")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> processScheduledNotifications() {
        return communicationService.processScheduledNotifications();
    }
}