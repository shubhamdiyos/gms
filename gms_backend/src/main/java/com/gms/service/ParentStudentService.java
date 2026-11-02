package com.gms.service;

import com.gms.model.entity.Student;
import com.gms.model.request.ParentStudentRequest;
import com.gms.model.response.ParentResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ParentStudentService {

    ResponseEntity<?> assignStudentsToParent(ParentStudentRequest request);

    ResponseEntity<?> removeStudentFromParent(Integer parentId, Integer studentId);

    ResponseEntity<List<Student>> getStudentsForParent(Integer parentId);

    ResponseEntity<List<ParentResponse>> getParentsForStudent(Integer studentId);
}