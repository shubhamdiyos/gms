package com.gms.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FinancialTransactionRequest {

    private Integer id;

    @NotBlank
    @Size(max = 50)
    private String transactionType;

    @NotBlank
    @Size(max = 100)
    private String transactionId;

    @NotNull
    @Positive
    private BigDecimal amount;

    @Size(min = 3, max = 3)
    private String currency;

    @NotNull
    private LocalDate transactionDate;

    @Size(max = 50)
    private String paymentMethod;

    @Size(max = 50)
    private String paymentGateway;

    @Size(max = 500)
    private String description;

    @Size(max = 100)
    private String referenceNumber;

    @NotBlank
    @Size(max = 20)
    private String status;

    private Integer studentId;

    private Integer employeeId;

    private Integer feePaymentId;
}