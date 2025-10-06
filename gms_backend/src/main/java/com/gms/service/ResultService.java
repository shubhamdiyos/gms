package com.gms.service;

import com.gms.model.entity.Result;
import com.gms.model.request.ResultRequest;
import com.gms.model.response.ResultResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ResultService {

    ResponseEntity<ResultResponse> recordResult(ResultRequest request, Integer schoolId, Integer empId);

    ResponseEntity<ResultResponse> updateResult(Integer id, ResultRequest request, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteResult(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<Result>> getResultsForStudentExam(Integer studentExamId, Integer schoolId);

    ResponseEntity<List<Result>> getResultsForStudent(Integer studentId, Integer schoolId);
}