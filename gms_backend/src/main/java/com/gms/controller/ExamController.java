package com.gms.controller;

import com.gms.model.entity.Exam;
import com.gms.model.request.ExamRequest;
import com.gms.model.response.ExamResponse;
import com.gms.service.ExamService;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/exams")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ExamResponse> createExam(@Valid @RequestBody ExamRequest examRequest) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return examService.createExam(examRequest, schoolId, empId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ExamResponse> updateExam(@PathVariable Integer id,
                                                 @Valid @RequestBody ExamRequest examRequest) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return examService.updateExam(id, examRequest, schoolId, empId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteExam(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return examService.deleteExam(id, schoolId, empId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<List<Exam>> getAllExams() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return examService.getAllExams(schoolId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<Exam> getExamById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return examService.getExamById(id, schoolId);
    }

    @GetMapping("/academic-year")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<List<Exam>> getExamsByAcademicYear(@RequestParam String academicYear) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return examService.getExamsByAcademicYear(schoolId, academicYear);
    }
}