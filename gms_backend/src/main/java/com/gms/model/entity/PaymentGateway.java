package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payment_gateways")
public class PaymentGateway extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "gateway_type", nullable = false, length = 50) // STRIPE, RAZORPAY, PAYPAL, etc.
    private String gatewayType;

    @Column(name = "merchant_id", length = 100)
    private String merchantId;

    @Column(name = "api_key", length = 200)
    private String apiKey;

    @Column(name = "secret_key", length = 200)
    private String secretKey;

    @Column(name = "webhook_url", length = 300)
    private String webhookUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "environment", length = 20) // PRODUCTION, SANDBOX
    private String environment;
}