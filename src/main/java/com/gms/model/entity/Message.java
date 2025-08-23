package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "messages")
public class Message extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "subject", length = 200)
    private String subject;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "message_type", nullable = false, length = 50) // DIRECT, GROUP, BROADCAST
    private String messageType;

    @Column(name = "sender_id", nullable = false)
    private Integer senderId;

    @Column(name = "sender_type", nullable = false, length = 20) // USER, SYSTEM
    private String senderType;

    @Column(name = "recipient_type", nullable = false, length = 20) // INDIVIDUAL, GROUP, ROLE
    private String recipientType;

    @Column(name = "recipient_ids", columnDefinition = "TEXT") // JSON array of recipient IDs
    private String recipientIds;

    @Column(name = "parent_message_id")
    private Integer parentMessageId;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "is_archived", nullable = false)
    private Boolean isArchived = false;

    @Column(name = "attachment_urls", columnDefinition = "TEXT") // JSON array of attachment URLs
    private String attachmentUrls;

    @Column(name = "priority", length = 20) // LOW, NORMAL, HIGH, URGENT
    private String priority = "NORMAL";

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "delivery_status", length = 20) // SENT, DELIVERED, READ, FAILED
    private String deliveryStatus = "SENT";
}