package com.gms.service;

import com.gms.model.entity.Subject;
import com.gms.model.request.SubjectRequest;
import com.gms.model.response.SubjectResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface SubjectService {
    List<Subject> findBySchool(Integer schoolId);
    Subject getById(Integer id, Integer schoolId);
    ResponseEntity<SubjectResponse> create(SubjectRequest request, Integer empId, Integer schoolId);
    ResponseEntity<SubjectResponse> update(SubjectRequest request, Integer empId, Integer schoolId);
    ResponseEntity<SubjectResponse> updateSubject(Integer id, SubjectRequest request, Integer empId, Integer schoolId);
    ResponseEntity<?> toggleSubject(Integer id, Boolean isActive, Integer schoolId, Integer empId);
    Subject createSubjectFromRequest(SubjectRequest request, Subject subject, Integer empId, Integer schoolId);
    List<SubjectResponse> findSubjectsForAuthenticatedTeacher();
    
    // Service-to-service communication method
    java.util.Optional<Subject> findById(Integer id);
}
