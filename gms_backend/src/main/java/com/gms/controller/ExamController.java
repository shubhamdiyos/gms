package com.gms.controller;

import com.gms.model.entity.Exam;
import com.gms.model.request.ExamRequest;
import com.gms.model.response.ExamResponse;
import com.gms.security.TenantContext;
import com.gms.service.ExamService;
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
        return examService.createExam(examRequest);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ExamResponse> updateExam(@PathVariable Integer id,
                                                 @Valid @RequestBody ExamRequest examRequest) {
        return examService.updateExam(id, examRequest);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteExam(@PathVariable Integer id) {
        return examService.deleteExam(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<List<Exam>> getAllExams() {
        return examService.getAllExams();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<Exam> getExamById(@PathVariable Integer id) {
        return examService.getExamById(id);
    }

    @GetMapping("/academic-year")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<List<Exam>> getExamsByAcademicYear(@RequestParam String academicYear) {
        return examService.getExamsByAcademicYear(academicYear);
    }
}