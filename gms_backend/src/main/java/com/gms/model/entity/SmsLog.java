package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "sms_logs")
public class SmsLog extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    @Column(name = "to_phone", nullable = false, length = 20)
    private String toPhone;

    @Column(name = "message", nullable = false, length = 1600)
    private String message;

    @Column(name = "template_id")
    private Integer templateId;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivery_status", length = 20) // SENT, DELIVERED, FAILED, PENDING
    private String deliveryStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "provider_response", columnDefinition = "TEXT")
    private String providerResponse;

    @Column(name = "message_id", length = 255)
    private String messageId;

    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Integer relatedEntityId;

    @Column(name = "cost", precision = 10, scale = 6)
    private java.math.BigDecimal cost;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "is_important", nullable = false)
    private Boolean isImportant = false;
}