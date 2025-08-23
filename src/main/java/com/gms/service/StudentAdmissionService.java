package com.gms.service;

import com.gms.model.request.StudentAdmissionRequest;
import org.springframework.http.ResponseEntity;

public interface StudentAdmissionService {

    ResponseEntity<?> applyForAdmission(StudentAdmissionRequest studentAdmissionRequest, Integer schoolId);

    ResponseEntity<?> approveAdmission(Integer admissionId, Integer schoolId);

    ResponseEntity<?> rejectAdmission(Integer admissionId, Integer schoolId);
}
