package com.gms.service;

import com.gms.model.entity.Exam;
import com.gms.model.request.ExamRequest;
import com.gms.model.response.ExamResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ExamService {

    ResponseEntity<ExamResponse> createExam(ExamRequest examRequest);

    ResponseEntity<ExamResponse> updateExam(Integer id, ExamRequest examRequest);

    ResponseEntity<?> deleteExam(Integer id);

    ResponseEntity<List<Exam>> getAllExams();

    ResponseEntity<Exam> getExamById(Integer id);

    ResponseEntity<List<Exam>> getExamsByAcademicYear(String academicYear);
}