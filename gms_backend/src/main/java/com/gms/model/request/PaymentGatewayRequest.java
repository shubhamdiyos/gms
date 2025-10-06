package com.gms.model.request;

import lombok.Data;

@Data
public class PaymentGatewayRequest {

    private Integer id;

    private String name;

    private String gatewayType;

    private String merchantId;

    private String apiKey;

    private String secretKey;

    private String webhookUrl;

    private Boolean isActive;

    private String environment;
}