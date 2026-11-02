
package com.gms.service;

import com.gms.model.request.TeacherAssignmentRequest;
import com.gms.model.response.TeacherAssignmentResponse;

import java.util.List;

public interface TeacherAssignmentService {

    TeacherAssignmentResponse createAssignment(TeacherAssignmentRequest request);
    
    List<TeacherAssignmentResponse> getAllAssignmentsBySchool();
    
    List<TeacherAssignmentResponse> getAssignmentsByTeacher(Integer teacherId);
    
    List<TeacherAssignmentResponse> getAssignmentsByClassroom(Integer classroomId);
    
    List<TeacherAssignmentResponse> getAssignmentsByAcademicYear(String academicYear);
    
    TeacherAssignmentResponse updateAssignment(Integer id, TeacherAssignmentRequest request);
    
    void deleteAssignment(Integer id);
    
    List<TeacherAssignmentResponse> getAssignmentsByUsername(String username);
}
