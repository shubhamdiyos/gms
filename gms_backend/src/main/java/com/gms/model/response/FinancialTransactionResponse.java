package com.gms.model.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FinancialTransactionResponse {

    private Integer id;

    private Integer schoolId;

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

    private String studentName;

    private Integer employeeId;

    private String employeeName;

    private Integer feePaymentId;
}