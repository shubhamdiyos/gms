package com.gms.model.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FinancialTransactionRequest {

    private Integer id;

    private String transactionType;

    private String transactionId;

    private BigDecimal amount;

    private String currency;

    private LocalDate transactionDate;

    private String paymentMethod;

    private String paymentGateway;

    private String description;

    private String referenceNumber;

    private String status;

    private Integer studentId;

    private Integer employeeId;

    private Integer feePaymentId;
}