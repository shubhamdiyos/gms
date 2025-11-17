package com.gms.controller;

import com.gms.model.entity.Result;
import com.gms.model.request.ResultRequest;
import com.gms.model.response.ResultResponse;
import com.gms.service.ResultService;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/results")
public class ResultController {

    private final ResultService resultService;

    public ResultController(ResultService resultService) {
        this.resultService = resultService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ResultResponse> recordResult(@Valid @RequestBody ResultRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return resultService.recordResult(request, schoolId, empId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ResultResponse> updateResult(@PathVariable Integer id,
                                                     @Valid @RequestBody ResultRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return resultService.updateResult(id, request, schoolId, empId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteResult(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return resultService.deleteResult(id, schoolId, empId);
    }

    @GetMapping("/student-exam/{studentExamId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT')")
    public ResponseEntity<List<Result>> getResultsForStudentExam(@PathVariable Integer studentExamId) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return resultService.getResultsForStudentExam(studentExamId, schoolId);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT')")
    public ResponseEntity<List<ResultResponse>> getResultsForStudent(@PathVariable Integer studentId) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        ResponseEntity<List<Result>> result = resultService.getResultsForStudent(studentId, schoolId);
        if (result.getBody() != null) {
            List<ResultResponse> responses = result.getBody().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        }
        return ResponseEntity.ok(java.util.Collections.emptyList());
    }

    private ResultResponse mapToResponse(Result result) {
        ResultResponse response = new ResultResponse();
        response.setId(result.getId());
        response.setStudentExamId(result.getStudentExam().getId());
        response.setStudentName(result.getStudentExam().getStudent().getFullName());
        response.setExamName(result.getStudentExam().getExamSubject().getExam().getName());
        response.setSubjectName(result.getStudentExam().getExamSubject().getSubject().getName());
        response.setObtainedMarks(result.getObtainedMarks());
        response.setGrade(result.getGrade());
        response.setPercentage(result.getPercentage());
        response.setRemarks(result.getRemarks());
        response.setStatus(result.getStatus());
        return response;
    }
}