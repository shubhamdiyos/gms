package com.gms.service;

import com.gms.model.entity.School;
import com.gms.model.request.SchoolRequest;
import com.gms.model.response.SchoolResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

public interface SchoolService {
    List<School> findBySchool(Integer schoolId);
    School getById(Integer id, Integer schoolId);
    ResponseEntity<SchoolResponse> create(SchoolRequest request, Integer empId, Integer schoolId);
    ResponseEntity<SchoolResponse> update(SchoolRequest request, Integer empId, Integer schoolId);
    ResponseEntity<?> toggleSchool(Integer id, Boolean isActive, Integer schoolId, Integer empId);
    School createSchoolFromRequest(SchoolRequest request, School school, Integer empId, Integer schoolId);
    
    // Base method for service-to-service communication (no tenant validation needed for schools)
    Optional<School> findById(Integer id);
    
    // Simple create method for bootstrap and other special cases
    School create(School school);
}