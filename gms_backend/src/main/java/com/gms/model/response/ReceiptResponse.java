package com.gms.model.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReceiptResponse {

    private Integer id;

    private Integer schoolId;

    private String receiptNumber;

    private LocalDate receiptDate;

    private BigDecimal amount;

    private String paymentMethod;

    private String referenceNumber;

    private String description;

    private String status;

    private Integer studentId;

    private String studentName;

    private Integer parentId;

    private String parentName;

    private Integer feePaymentId;

    private Integer invoiceId;

    private String receivedBy;
}