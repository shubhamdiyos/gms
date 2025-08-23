package com.gms.model.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmailLogRequest {

    private Integer id;

    private String toEmail;

    private String ccEmails;

    private String bccEmails;

    private String subject;

    private String content;

    private Integer templateId;

    private LocalDateTime sentAt;

    private String deliveryStatus; // SENT, DELIVERED, FAILED, OPENED

    private String errorMessage;

    private String providerResponse;

    private String messageId;

    private String relatedEntityType;

    private Integer relatedEntityId;

    private String attachments; // JSON array of attachment info

    private Integer retryCount;

    private Boolean isImportant;
}