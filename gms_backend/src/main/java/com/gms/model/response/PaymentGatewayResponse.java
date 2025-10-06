package com.gms.model.response;

import lombok.Data;

@Data
public class PaymentGatewayResponse {

    private Integer id;

    private Integer schoolId;

    private String name;

    private String gatewayType;

    private String merchantId;

    private Boolean isActive;

    private String environment;
}