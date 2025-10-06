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
    public ResponseEntity<List<Result>> getResultsForStudent(@PathVariable Integer studentId) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return resultService.getResultsForStudent(studentId, schoolId);
    }
}