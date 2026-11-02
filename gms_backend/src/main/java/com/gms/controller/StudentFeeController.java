package com.gms.controller;

import com.gms.model.entity.StudentFee;
import com.gms.model.request.StudentFeeRequest;
import com.gms.model.response.StudentFeeResponse;
import com.gms.security.TenantContext;
import com.gms.service.StudentFeeService;
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
        return studentFeeService.createStudentFee(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<StudentFeeResponse> updateStudentFee(@PathVariable Integer id,
                                                              @Valid @RequestBody StudentFeeRequest request) {
        return studentFeeService.updateStudentFee(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteStudentFee(@PathVariable Integer id) {
        return studentFeeService.deleteStudentFee(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<StudentFee>> getAllStudentFees() {
        return studentFeeService.getAllStudentFees();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<StudentFee> getStudentFeeById(@PathVariable Integer id) {
        return studentFeeService.getStudentFeeById(id);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT', 'PARENT')")
    public ResponseEntity<List<StudentFee>> getStudentFeesByStudent(@PathVariable Integer studentId) {
        return studentFeeService.getStudentFeesByStudent(studentId);
    }

    @GetMapping("/academic-year")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<List<StudentFee>> getStudentFeesByAcademicYear(@RequestParam String academicYear) {
        return studentFeeService.getStudentFeesByAcademicYear(academicYear);
    }

    @GetMapping("/collected-total")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<BigDecimal> getTotalCollectedFees() {
        BigDecimal totalCollected = studentFeeService.getTotalCollectedFeesBySchoolId();
        return ResponseEntity.ok(totalCollected);
    }

    @GetMapping("/outstanding-total")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<BigDecimal> getTotalOutstandingFees() {
        BigDecimal totalOutstanding = studentFeeService.getTotalOutstandingFeesBySchoolId();
        return ResponseEntity.ok(totalOutstanding);
    }
}