package com.gms.model.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvoiceResponse {

    private Integer id;

    private Integer schoolId;

    private String invoiceNumber;

    private LocalDate invoiceDate;

    private LocalDate dueDate;

    private BigDecimal totalAmount;

    private BigDecimal amountPaid;

    private BigDecimal balanceAmount;

    private String status;

    private String invoiceType;

    private Integer studentId;

    private String studentName;

    private Integer parentId;

    private String parentName;

    private String billingAddress;

    private String notes;

    private BigDecimal taxAmount;

    private BigDecimal discountAmount;
}