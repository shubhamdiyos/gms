package com.gms.service;

import com.gms.model.entity.FeePayment;
import com.gms.model.request.FeePaymentRequest;
import com.gms.model.response.FeePaymentResponse;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface FeePaymentService {

    ResponseEntity<FeePaymentResponse> createFeePayment(FeePaymentRequest request, Integer schoolId, Integer empId);

    ResponseEntity<FeePaymentResponse> updateFeePayment(Integer id, FeePaymentRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteFeePayment(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<FeePayment>> getAllFeePayments(Integer schoolId);

    ResponseEntity<FeePayment> getFeePaymentById(Integer id, Integer schoolId);

    ResponseEntity<List<FeePayment>> getFeePaymentsByStudent(Integer studentId, Integer schoolId);

    ResponseEntity<List<FeePayment>> getFeePaymentsByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate);

    BigDecimal getTotalPaymentsBySchoolIdAndDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate);

    Long getCountOfPaymentsBySchoolIdAndDate(Integer schoolId, LocalDate paymentDate);
}