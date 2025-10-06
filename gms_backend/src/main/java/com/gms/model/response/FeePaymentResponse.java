package com.gms.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FeePaymentResponse {
    private Integer id;
    private Integer studentFeeId;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String paymentMethod;
    private String remarks;
}