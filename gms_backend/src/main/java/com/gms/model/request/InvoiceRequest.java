package com.gms.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvoiceRequest {

    private Integer id;

    @NotBlank
    @Size(max = 50)
    private String invoiceNumber;

    @NotNull
    private LocalDate invoiceDate;

    @NotNull
    private LocalDate dueDate;

    @NotNull
    private BigDecimal totalAmount;

    private BigDecimal amountPaid;

    @NotBlank
    @Size(max = 20)
    private String status;

    @Size(max = 50)
    private String invoiceType;

    private Integer studentId;

    private Integer parentId;

    @Size(max = 500)
    private String billingAddress;

    @Size(max = 1000)
    private String notes;

    private BigDecimal taxAmount;

    private BigDecimal discountAmount;
}