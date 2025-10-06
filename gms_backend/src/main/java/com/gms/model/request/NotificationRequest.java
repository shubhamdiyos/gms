package com.gms.model.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationRequest {

    private Integer id;

    private String title;

    private String message;

    private String notificationType; // ANNOUNCEMENT, ALERT, REMINDER, MESSAGE

    private String channel; // EMAIL, SMS, IN_APP, PUSH

    private String priority; // LOW, NORMAL, HIGH, URGENT

    private String recipientType; // ALL, ROLE, INDIVIDUAL

    private String recipientIds; // JSON array of recipient IDs

    private LocalDateTime scheduledAt;

    private String relatedEntityType; // EXAM, FEE, EVENT, etc.

    private Integer relatedEntityId;

    private Boolean isImportant;

    private LocalDateTime expiresAt;

    private String clickActionUrl;
}