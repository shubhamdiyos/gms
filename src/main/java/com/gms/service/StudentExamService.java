package com.gms.service;

import com.gms.model.entity.StudentExam;
import com.gms.model.request.StudentExamRequest;
import com.gms.model.response.StudentExamResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface StudentExamService {

    ResponseEntity<StudentExamResponse> registerStudentForExam(StudentExamRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deregisterStudentFromExam(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<StudentExam>> getStudentsForExamSubject(Integer examSubjectId, Integer schoolId);

    ResponseEntity<List<StudentExam>> getExamsForStudent(Integer studentId, Integer schoolId);
}