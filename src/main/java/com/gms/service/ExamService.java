package com.gms.service;

import com.gms.model.entity.Exam;
import com.gms.model.request.ExamRequest;
import com.gms.model.response.ExamResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ExamService {

    ResponseEntity<ExamResponse> createExam(ExamRequest examRequest, Integer schoolId, Integer empId);

    ResponseEntity<ExamResponse> updateExam(Integer id, ExamRequest examRequest, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteExam(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<Exam>> getAllExams(Integer schoolId);

    ResponseEntity<Exam> getExamById(Integer id, Integer schoolId);

    ResponseEntity<List<Exam>> getExamsByAcademicYear(Integer schoolId, String academicYear);
}