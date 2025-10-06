package com.gms.model.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReceiptRequest {

    private Integer id;

    private String receiptNumber;

    private LocalDate receiptDate;

    private BigDecimal amount;

    private String paymentMethod;

    private String referenceNumber;

    private String description;

    private String status;

    private Integer studentId;

    private Integer parentId;

    private Integer feePaymentId;

    private Integer invoiceId;

    private String receivedBy;
}