package com.gms.model.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvoiceRequest {

    private Integer id;

    private String invoiceNumber;

    private LocalDate invoiceDate;

    private LocalDate dueDate;

    private BigDecimal totalAmount;

    private BigDecimal amountPaid;

    private String status;

    private String invoiceType;

    private Integer studentId;

    private Integer parentId;

    private String billingAddress;

    private String notes;

    private BigDecimal taxAmount;

    private BigDecimal discountAmount;
}