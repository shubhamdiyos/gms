package com.gms.service;

import com.gms.model.entity.Fee;
import com.gms.model.request.FeeRequest;
import com.gms.model.response.FeeResponse;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

public interface FeeService {

    ResponseEntity<FeeResponse> createFee(FeeRequest request, Integer schoolId, Integer empId);

    ResponseEntity<FeeResponse> updateFee(Integer id, FeeRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteFee(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<Fee>> getAllFees(Integer schoolId);

    ResponseEntity<Fee> getFeeById(Integer id, Integer schoolId);

    ResponseEntity<List<Fee>> getFeesByAcademicYear(Integer schoolId, String academicYear);

    BigDecimal getTotalFeeAmountBySchoolIdAndAcademicYear(Integer schoolId, String academicYear);
}