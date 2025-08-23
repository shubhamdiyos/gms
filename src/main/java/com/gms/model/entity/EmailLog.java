package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "email_logs")
public class EmailLog extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    @Column(name = "to_email", nullable = false, length = 255)
    private String toEmail;

    @Column(name = "cc_emails", length = 1000)
    private String ccEmails;

    @Column(name = "bcc_emails", length = 1000)
    private String bccEmails;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "template_id")
    private Integer templateId;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivery_status", length = 20) // SENT, DELIVERED, FAILED, OPENED
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

    @Column(name = "attachments", columnDefinition = "TEXT") // JSON array of attachment info
    private String attachments;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "is_important", nullable = false)
    private Boolean isImportant = false;
}