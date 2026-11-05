package com.gms.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StudentFeeResponse {
    private Integer id;
    private Integer studentId;
    private Integer feeId;
    private String status;
    private BigDecimal amountPaid;
    private BigDecimal totalAmount;
    private BigDecimal balanceAmount;
    private LocalDate dueDate;
    private LocalDate lastPaymentDate;
    private String studentName;
    private String feeType;
    private String description;
    private String academicYear;
}