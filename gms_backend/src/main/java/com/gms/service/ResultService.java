package com.gms.service;

import com.gms.model.entity.Result;
import com.gms.model.request.ResultRequest;
import com.gms.model.response.ResultResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ResultService {

    ResponseEntity<ResultResponse> recordResult(ResultRequest request);

    ResponseEntity<ResultResponse> updateResult(Integer id, ResultRequest request);

    ResponseEntity<?> deleteResult(Integer id);

    ResponseEntity<List<Result>> getResultsForStudentExam(Integer studentExamId);

    ResponseEntity<List<Result>> getResultsForStudent(Integer studentId);
}