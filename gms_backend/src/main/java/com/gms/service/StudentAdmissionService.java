package com.gms.service;

import com.gms.model.request.StudentAdmissionRequest;
import org.springframework.http.ResponseEntity;

public interface StudentAdmissionService {

    ResponseEntity<?> applyForAdmission(StudentAdmissionRequest studentAdmissionRequest);

    ResponseEntity<?> approveAdmission(Integer admissionId);

    ResponseEntity<?> rejectAdmission(Integer admissionId);
}
