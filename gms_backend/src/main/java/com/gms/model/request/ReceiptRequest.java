package com.gms.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReceiptRequest {

    private Integer id;

    @NotBlank
    @Size(max = 50)
    private String receiptNumber;

    @NotNull
    private LocalDate receiptDate;

    @NotNull
    @Positive
    private BigDecimal amount;

    @Size(max = 50)
    private String paymentMethod;

    @Size(max = 100)
    private String referenceNumber;

    @Size(max = 500)
    private String description;

    @NotBlank
    @Size(max = 20)
    private String status;

    private Integer studentId;

    private Integer parentId;

    private Integer feePaymentId;

    private Integer invoiceId;

    @Size(max = 100)
    private String receivedBy;
}