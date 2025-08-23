package com.gms.model.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SmsLogRequest {

    private Integer id;

    private String toPhone;

    private String message;

    private Integer templateId;

    private LocalDateTime sentAt;

    private String deliveryStatus; // SENT, DELIVERED, FAILED, PENDING

    private String errorMessage;

    private String providerResponse;

    private String messageId;

    private String relatedEntityType;

    private Integer relatedEntityId;

    private BigDecimal cost;

    private String currency;

    private String provider;

    private Integer retryCount;

    private Boolean isImportant;
}