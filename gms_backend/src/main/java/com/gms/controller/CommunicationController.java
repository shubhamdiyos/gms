package com.gms.controller;

import com.gms.model.request.NotificationRequest;
import com.gms.service.CommunicationService;
import com.gms.util.SecurityUtil;
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
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return communicationService.sendNotification(request, schoolId, empId);
    }

    @PostMapping("/emails/send")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> sendEmail(@RequestParam String toEmail,
                                      @RequestParam String subject,
                                      @RequestParam String content) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return communicationService.sendEmail(toEmail, subject, content, schoolId);
    }

    @PostMapping("/sms/send")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> sendSms(@RequestParam String toPhone,
                                     @RequestParam String message) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return communicationService.sendSms(toPhone, message, schoolId);
    }

    @PostMapping("/notifications/in-app")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> sendInAppNotification(@Valid @RequestBody NotificationRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return communicationService.sendInAppNotification(request, schoolId, empId);
    }

    @PostMapping("/notifications/push")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> sendPushNotification(@Valid @RequestBody NotificationRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return communicationService.sendPushNotification(request, schoolId, empId);
    }

    @PostMapping("/notifications/bulk")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> sendBulkNotifications(@Valid @RequestBody NotificationRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return communicationService.sendBulkNotifications(request, schoolId, empId);
    }

    @PostMapping("/notifications/process-scheduled")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<?> processScheduledNotifications() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return communicationService.processScheduledNotifications(schoolId);
    }
}