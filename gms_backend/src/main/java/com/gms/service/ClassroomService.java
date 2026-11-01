package com.gms.service;

import com.gms.model.entity.Classroom;
import com.gms.model.request.ClassroomRequest;
import com.gms.model.response.ClassroomResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ClassroomService {
    List<Classroom> findBySchool();
    Classroom getById(Integer id);
    ResponseEntity<ClassroomResponse> create(ClassroomRequest request);
    ResponseEntity<ClassroomResponse> update(ClassroomRequest request);
    ResponseEntity<?> toggleClassroom(Integer id, Boolean isActive);
    Classroom createClassroomFromRequest(ClassroomRequest request, Classroom classroom);

    /**
     * Deletes a classroom, but only if it belongs to the caller's school.
     *
     * @return true when a row was deleted, false when no such classroom exists in that school
     */
    boolean deleteWithinSchool(Integer id);
    
    // Service-to-service communication method
    java.util.Optional<Classroom> findById(Integer id);
    List<ClassroomResponse> findClassroomsForAuthenticatedTeacher();
}
