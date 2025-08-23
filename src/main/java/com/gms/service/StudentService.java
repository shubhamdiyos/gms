package com.gms.service;

import com.gms.model.entity.Student;
import com.gms.model.request.StudentRequest;
import com.gms.model.response.StudentResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface StudentService {
    List<Student> findBySchool(Integer schoolId);
    Student getById(Integer id, Integer schoolId);
    ResponseEntity<StudentResponse> create(StudentRequest request, Integer empId, Integer schoolId);
    ResponseEntity<StudentResponse> update(StudentRequest request, Integer empId, Integer schoolId);
    ResponseEntity<?> toggleStudent(Integer id, Boolean isActive, Integer schoolId, Integer empId);
    Student createStudentFromRequest(StudentRequest request, Student student, Integer empId, Integer schoolId);
    
    // Student-specific methods
    StudentResponse getStudentProfile(String username);
    StudentResponse getMyProfile();
}