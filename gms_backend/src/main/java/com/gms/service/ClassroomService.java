package com.gms.service;

import com.gms.model.entity.Classroom;
import com.gms.model.request.ClassroomRequest;
import com.gms.model.response.ClassroomResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ClassroomService {
    List<Classroom> findBySchool(Integer schoolId);
    Classroom getById(Integer id, Integer schoolId);
    ResponseEntity<ClassroomResponse> create(ClassroomRequest request, Integer empId, Integer schoolId);
    ResponseEntity<ClassroomResponse> update(ClassroomRequest request, Integer empId, Integer schoolId);
    ResponseEntity<?> toggleClassroom(Integer id, Boolean isActive, Integer schoolId, Integer empId);
    Classroom createClassroomFromRequest(ClassroomRequest request, Classroom classroom, Integer empId, Integer schoolId);
    
    // Service-to-service communication method
    java.util.Optional<Classroom> findById(Integer id);
    List<ClassroomResponse> findClassroomsForAuthenticatedTeacher();
}
