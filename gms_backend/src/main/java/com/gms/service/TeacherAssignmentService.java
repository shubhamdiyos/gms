
package com.gms.service;

import com.gms.model.request.TeacherAssignmentRequest;
import com.gms.model.response.TeacherAssignmentResponse;

import java.util.List;

public interface TeacherAssignmentService {

    TeacherAssignmentResponse createAssignment(TeacherAssignmentRequest request, Integer schoolId, Integer empId);
    
    List<TeacherAssignmentResponse> getAllAssignmentsBySchool(Integer schoolId);
    
    List<TeacherAssignmentResponse> getAssignmentsByTeacher(Integer teacherId, Integer schoolId);
    
    List<TeacherAssignmentResponse> getAssignmentsByClassroom(Integer classroomId, Integer schoolId);
    
    List<TeacherAssignmentResponse> getAssignmentsByAcademicYear(String academicYear, Integer schoolId);
    
    TeacherAssignmentResponse updateAssignment(Integer id, TeacherAssignmentRequest request, Integer schoolId, Integer empId);
    
    void deleteAssignment(Integer id, Integer schoolId);
    
    List<TeacherAssignmentResponse> getAssignmentsByUsername(String username);
}
