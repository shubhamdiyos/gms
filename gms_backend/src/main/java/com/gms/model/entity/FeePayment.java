
package com.gms.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "fee_payments")
public class FeePayment extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_fee_id", nullable = false)
    private StudentFee studentFee;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "payment_method", nullable = false, length = 20) // CASH, BANK_TRANSFER, CARD, CHEQUE, ONLINE
    private String paymentMethod;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "cheque_number", length = 50)
    private String chequeNumber;

    @Column(name = "status", nullable = false, length = 20) // SUCCESS, FAILED, PENDING, REFUNDED
    private String status = "SUCCESS";
}
