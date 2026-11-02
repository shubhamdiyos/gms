package com.gms.service;

import com.gms.model.entity.Fee;
import com.gms.model.request.FeeRequest;
import com.gms.model.response.FeeResponse;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

public interface FeeService {

    ResponseEntity<FeeResponse> createFee(FeeRequest request);

    ResponseEntity<FeeResponse> updateFee(Integer id, FeeRequest request);

    ResponseEntity<?> deleteFee(Integer id);

    ResponseEntity<List<Fee>> getAllFees();

    ResponseEntity<Fee> getFeeById(Integer id);

    ResponseEntity<List<Fee>> getFeesByAcademicYear(String academicYear);

    BigDecimal getTotalFeeAmountBySchoolIdAndAcademicYear(String academicYear);
}