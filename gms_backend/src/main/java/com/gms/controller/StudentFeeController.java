package com.gms.controller;

import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.Parent;
import com.gms.model.entity.Student;
import com.gms.model.entity.StudentFee;
import com.gms.model.entity.User;
import com.gms.model.request.StudentFeeRequest;
import com.gms.model.response.StudentFeeResponse;
import com.gms.security.TenantContext;
import com.gms.service.StudentFeeService;
import com.gms.service.UserService;
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
    private final UserService userService;

    public StudentFeeController(StudentFeeService studentFeeService, UserService userService) {
        this.studentFeeService = studentFeeService;
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<StudentFeeResponse> createStudentFee(@Valid @RequestBody StudentFeeRequest request) {
        return studentFeeService.createStudentFee(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<StudentFeeResponse> updateStudentFee(@PathVariable Integer id,
                                                              @Valid @RequestBody StudentFeeRequest request) {
        return studentFeeService.updateStudentFee(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
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
        // F-3 fix: PARENT callers must verify they are the parent of this student
        if (TenantContext.roles().contains("PARENT")) {
            validateParentOwnsStudent(TenantContext.username(), studentId);
        }
        return studentFeeService.getStudentFeesByStudent(studentId);
    }

    // F-3: Verify the authenticated parent has a relationship to the named student
    private void validateParentOwnsStudent(String username, Integer studentId) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new TenantAccessDeniedException("User not found"));
        Parent parent = user.getParent();
        if (parent == null) {
            throw new TenantAccessDeniedException("Parent profile not found");
        }
        boolean isParentOfStudent = parent.getStudents().stream()
                .anyMatch(student -> student.getId().equals(studentId));
        if (!isParentOfStudent) {
            throw new TenantAccessDeniedException("Not authorized to access this student's data");
        }
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