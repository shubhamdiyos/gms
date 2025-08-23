package com.gms.service;

import com.gms.model.entity.Employee;
import com.gms.model.response.EmployeeResponse;
import com.gms.model.response.ClassroomResponse;
import com.gms.model.response.StudentResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface TeacherService {
    EmployeeResponse getTeacherProfile(String username);
    List<ClassroomResponse> getAssignedClasses(String username);
    List<StudentResponse> getAssignedStudents(String username);
}