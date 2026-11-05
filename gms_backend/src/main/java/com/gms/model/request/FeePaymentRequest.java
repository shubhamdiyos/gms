package com.gms.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class FeePaymentRequest {
    @Positive
    private Integer id;

    @NotNull
    @Positive
    private Integer studentFeeId;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal amount;

    @NotNull
    @PastOrPresent
    private LocalDate paymentDate;

    @NotBlank
    @Pattern(regexp = "(?i:CASH|BANK_TRANSFER|CARD|CHEQUE|ONLINE)")
    private String paymentMethod;

    @Size(max = 100)
    private String transactionId;

    @Size(max = 500)
    private String remarks;

    @NotBlank
    @Size(max = 100)
    private String paymentReference;

    @Size(max = 100)
    private String bankName;

    @Size(max = 50)
    private String chequeNumber;

    @Size(max = 20)
    private String status;
}