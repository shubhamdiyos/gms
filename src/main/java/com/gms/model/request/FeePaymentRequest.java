package com.gms.model.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class FeePaymentRequest {
    private Integer id; // for updates, optional
    private Integer studentFeeId;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String paymentMethod; // CASH, BANK_TRANSFER, etc.
    private String transactionId;
    private String remarks;
    private String paymentReference;
    private String bankName;
    private String chequeNumber;
    private String status;
}