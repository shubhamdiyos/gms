package com.gms.service;

import com.gms.model.entity.Parent;
import com.gms.model.request.ParentRequest;
import com.gms.model.response.ParentResponse;
import com.gms.model.response.StudentResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ParentService {
    List<com.gms.model.entity.Parent> findBySchool(Integer schoolId);
    
    // Standard CRUD methods following reference pattern
    ResponseEntity<ParentResponse> create(ParentRequest request, Integer empId, Integer schoolId);
    ResponseEntity<ParentResponse> update(ParentRequest request, Integer empId, Integer schoolId);
    ResponseEntity<?> toggleParent(Integer id, Boolean isActive, Integer schoolId, Integer empId);
    ParentResponse createParentFromRequest(ParentRequest request, Parent parent, Integer empId, Integer schoolId);
    
    // Existing methods
    ResponseEntity<ParentResponse> createParent(ParentRequest parentRequest, Integer schoolId);
    List<StudentResponse> getMyChildren();
    
    // Parent-specific methods
    ParentResponse getParentProfile(String username);
    List<StudentResponse> getChildren(String username);
}