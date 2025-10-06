package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "notification_type", nullable = false, length = 50) // ANNOUNCEMENT, ALERT, REMINDER, MESSAGE
    private String notificationType;

    @Column(name = "channel", nullable = false, length = 50) // EMAIL, SMS, IN_APP, PUSH
    private String channel;

    @Column(name = "priority", length = 20) // LOW, NORMAL, HIGH, URGENT
    private String priority = "NORMAL";

    @Column(name = "recipient_type", nullable = false, length = 50) // ALL, ROLE, INDIVIDUAL
    private String recipientType;

    @Column(name = "recipient_ids", length = 1000) // JSON array of recipient IDs
    private String recipientIds;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivery_status", length = 20) // PENDING, SENT, FAILED, DELIVERED
    private String deliveryStatus = "PENDING";

    @Column(name = "read_status", length = 20) // UNREAD, READ
    private String readStatus = "UNREAD";

    @Column(name = "related_entity_type", length = 50) // EXAM, FEE, EVENT, etc.
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Integer relatedEntityId;

    @Column(name = "is_important", nullable = false)
    private Boolean isImportant = false;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "click_action_url", length = 500)
    private String clickActionUrl;
}