package com.gms.controller;

import com.gms.model.request.StudentAdmissionRequest;
import com.gms.security.TenantContext;
import com.gms.service.StudentAdmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/student-admissions")
public class StudentAdmissionController {

    @Autowired
    private StudentAdmissionService studentAdmissionService;
    
    @PostMapping("/apply")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> applyForAdmission(@RequestBody StudentAdmissionRequest studentAdmissionRequest) {
        return studentAdmissionService.applyForAdmission(studentAdmissionRequest);
    }

    @PutMapping("/{admissionId}/approve")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> approveAdmission(@PathVariable Integer admissionId) {
        return studentAdmissionService.approveAdmission(admissionId);
    }

    @PutMapping("/{admissionId}/reject")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> rejectAdmission(@PathVariable Integer admissionId) {
        return studentAdmissionService.rejectAdmission(admissionId);
    }
}
