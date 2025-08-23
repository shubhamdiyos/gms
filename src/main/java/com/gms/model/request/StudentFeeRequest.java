package com.gms.model.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class StudentFeeRequest {
    private Integer id; // for updates, optional
    private Integer studentId;
    private Integer feeId;
    private String status; // PENDING, PAID, PARTIAL
    private BigDecimal amountPaid;
}