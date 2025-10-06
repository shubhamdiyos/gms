package com.gms.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

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
}