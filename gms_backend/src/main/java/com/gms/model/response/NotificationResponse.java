package com.gms.model.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationResponse {

    private Integer id;

    private Integer schoolId;

    private String title;

    private String message;

    private String notificationType;

    private String channel;

    private String priority;

    private String recipientType;

    private String recipientIds;

    private LocalDateTime scheduledAt;

    private LocalDateTime sentAt;

    private String deliveryStatus;

    private String readStatus;

    private String relatedEntityType;

    private Integer relatedEntityId;

    private Boolean isImportant;

    private LocalDateTime expiresAt;

    private String clickActionUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}