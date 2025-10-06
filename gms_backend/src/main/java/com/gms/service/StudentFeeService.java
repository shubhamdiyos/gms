package com.gms.service;

import com.gms.model.entity.StudentFee;
import com.gms.model.request.StudentFeeRequest;
import com.gms.model.response.StudentFeeResponse;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StudentFeeService {

    ResponseEntity<StudentFeeResponse> createStudentFee(StudentFeeRequest request, Integer schoolId, Integer empId);

    ResponseEntity<StudentFeeResponse> updateStudentFee(Integer id, StudentFeeRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteStudentFee(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<StudentFee>> getAllStudentFees(Integer schoolId);

    ResponseEntity<StudentFee> getStudentFeeById(Integer id, Integer schoolId);

    ResponseEntity<List<StudentFee>> getStudentFeesByStudent(Integer studentId, Integer schoolId);

    ResponseEntity<List<StudentFee>> getStudentFeesByAcademicYear(Integer schoolId, String academicYear);

    BigDecimal getTotalCollectedFeesBySchoolId(Integer schoolId);

    BigDecimal getTotalOutstandingFeesBySchoolId(Integer schoolId);
    
    // Additional methods for service-to-service communication with multi-tenant support
    StudentFee findById(Integer id, Integer schoolId);
    
    StudentFee save(StudentFee studentFee);
}