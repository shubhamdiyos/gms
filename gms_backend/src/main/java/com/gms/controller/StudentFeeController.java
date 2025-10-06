package com.gms.controller;

import com.gms.model.entity.StudentFee;
import com.gms.model.request.StudentFeeRequest;
import com.gms.model.response.StudentFeeResponse;
import com.gms.service.StudentFeeService;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/student-fees")
public class StudentFeeController {

    private final StudentFeeService studentFeeService;

    public StudentFeeController(StudentFeeService studentFeeService) {
        this.studentFeeService = studentFeeService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<StudentFeeResponse> createStudentFee(@Valid @RequestBody StudentFeeRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return studentFeeService.createStudentFee(request, schoolId, empId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<StudentFeeResponse> updateStudentFee(@PathVariable Integer id,
                                                              @Valid @RequestBody StudentFeeRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return studentFeeService.updateStudentFee(id, request, schoolId, empId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteStudentFee(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return studentFeeService.deleteStudentFee(id, schoolId, empId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<StudentFee>> getAllStudentFees() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return studentFeeService.getAllStudentFees(schoolId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<StudentFee> getStudentFeeById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return studentFeeService.getStudentFeeById(id, schoolId);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT', 'PARENT')")
    public ResponseEntity<List<StudentFee>> getStudentFeesByStudent(@PathVariable Integer studentId) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return studentFeeService.getStudentFeesByStudent(studentId, schoolId);
    }

    @GetMapping("/academic-year")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<StudentFee>> getStudentFeesByAcademicYear(@RequestParam String academicYear) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return studentFeeService.getStudentFeesByAcademicYear(schoolId, academicYear);
    }

    @GetMapping("/collected-total")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<BigDecimal> getTotalCollectedFees() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        BigDecimal totalCollected = studentFeeService.getTotalCollectedFeesBySchoolId(schoolId);
        return ResponseEntity.ok(totalCollected);
    }

    @GetMapping("/outstanding-total")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<BigDecimal> getTotalOutstandingFees() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        BigDecimal totalOutstanding = studentFeeService.getTotalOutstandingFeesBySchoolId(schoolId);
        return ResponseEntity.ok(totalOutstanding);
    }
}