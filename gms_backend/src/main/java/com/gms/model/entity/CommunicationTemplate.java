package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "communication_templates")
public class CommunicationTemplate extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Column(name = "template_code", nullable = false, length = 50, unique = true)
    private String templateCode;

    @Column(name = "subject", length = 200)
    private String subject;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "communication_type", nullable = false, length = 50) // EMAIL, SMS, NOTIFICATION
    private String communicationType;

    @Column(name = "category", length = 50) // ADMISSION, FEE, ATTENDANCE, EXAM, EVENT
    private String category;

    @Column(name = "language", length = 10) // en, hi, es, fr, etc.
    private String language = "en";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "variables", columnDefinition = "TEXT") // JSON array of available variables
    private String variables;

    @Column(name = "sender_name", length = 100)
    private String senderName;

    @Column(name = "sender_email", length = 150)
    private String senderEmail;

    @Column(name = "sms_sender_id", length = 20)
    private String smsSenderId;
}