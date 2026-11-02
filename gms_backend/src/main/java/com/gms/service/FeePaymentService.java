package com.gms.service;

import com.gms.model.entity.FeePayment;
import com.gms.model.request.FeePaymentRequest;
import com.gms.model.response.FeePaymentResponse;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface FeePaymentService {

    ResponseEntity<FeePaymentResponse> createFeePayment(FeePaymentRequest request);

    ResponseEntity<FeePaymentResponse> updateFeePayment(Integer id, FeePaymentRequest request);

    ResponseEntity<?> deleteFeePayment(Integer id);

    ResponseEntity<List<FeePayment>> getAllFeePayments();

    ResponseEntity<FeePayment> getFeePaymentById(Integer id);

    ResponseEntity<List<FeePayment>> getFeePaymentsByStudent(Integer studentId);

    ResponseEntity<List<FeePayment>> getFeePaymentsByDateRange(LocalDate startDate, LocalDate endDate);

    BigDecimal getTotalPaymentsBySchoolIdAndDateRange(LocalDate startDate, LocalDate endDate);

    Long getCountOfPaymentsBySchoolIdAndDate(LocalDate paymentDate);
}