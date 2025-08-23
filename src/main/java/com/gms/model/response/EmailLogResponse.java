package com.gms.model.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EmailLogResponse {

    private Integer id;

    private Integer schoolId;

    private String toEmail;

    private String ccEmails;

    private String bccEmails;

    private String subject;

    private String content;

    private Integer templateId;

    private LocalDateTime sentAt;

    private String deliveryStatus;

    private String errorMessage;

    private String providerResponse;

    private String messageId;

    private String relatedEntityType;

    private Integer relatedEntityId;

    private String attachments;

    private Integer retryCount;

    private Boolean isImportant;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}