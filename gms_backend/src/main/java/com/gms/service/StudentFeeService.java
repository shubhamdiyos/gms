package com.gms.service;

import com.gms.model.entity.StudentFee;
import com.gms.model.request.StudentFeeRequest;
import com.gms.model.response.StudentFeeResponse;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StudentFeeService {

    ResponseEntity<StudentFeeResponse> createStudentFee(StudentFeeRequest request);

    ResponseEntity<StudentFeeResponse> updateStudentFee(Integer id, StudentFeeRequest request);

    ResponseEntity<?> deleteStudentFee(Integer id);

    ResponseEntity<List<StudentFee>> getAllStudentFees();

    ResponseEntity<StudentFee> getStudentFeeById(Integer id);

    ResponseEntity<List<StudentFee>> getStudentFeesByStudent(Integer studentId);

    ResponseEntity<List<StudentFee>> getStudentFeesByAcademicYear(String academicYear);

    BigDecimal getTotalCollectedFeesBySchoolId();

    BigDecimal getTotalOutstandingFeesBySchoolId();
    
    // Additional methods for service-to-service communication with multi-tenant support
    StudentFee findById(Integer id);
    
    StudentFee save(StudentFee studentFee);
}