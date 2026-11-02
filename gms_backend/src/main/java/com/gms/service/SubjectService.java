package com.gms.service;

import com.gms.model.entity.Subject;
import com.gms.model.request.SubjectRequest;
import com.gms.model.response.SubjectResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface SubjectService {
    List<Subject> findBySchool();
    Subject getById(Integer id);
    ResponseEntity<SubjectResponse> create(SubjectRequest request);
    ResponseEntity<SubjectResponse> update(SubjectRequest request);
    ResponseEntity<SubjectResponse> updateSubject(Integer id, SubjectRequest request);
    ResponseEntity<?> toggleSubject(Integer id, Boolean isActive);
    Subject createSubjectFromRequest(SubjectRequest request, Subject subject);
    List<SubjectResponse> findSubjectsForAuthenticatedTeacher();

    /**
     * Deletes a subject, but only if it belongs to the caller's school.
     *
     * @return true when a row was deleted, false when no such subject exists in that school
     */
    boolean deleteWithinSchool(Integer id);
    
    // Service-to-service communication method
    java.util.Optional<Subject> findById(Integer id);
}
