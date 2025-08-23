package com.gms.service;

import com.gms.model.entity.Parent;
import com.gms.model.entity.Student;
import com.gms.model.request.ParentStudentRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ParentStudentService {

    ResponseEntity<?> assignStudentsToParent(ParentStudentRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> removeStudentFromParent(Integer parentId, Integer studentId, Integer schoolId, Integer empId);

    ResponseEntity<List<Student>> getStudentsForParent(Integer parentId, Integer schoolId);

    ResponseEntity<List<Parent>> getParentsForStudent(Integer studentId, Integer schoolId);
}