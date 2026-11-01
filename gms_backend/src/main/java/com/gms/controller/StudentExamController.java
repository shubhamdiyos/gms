package com.gms.controller;

import com.gms.model.entity.StudentExam;
import com.gms.model.request.StudentExamRequest;
import com.gms.model.response.StudentExamResponse;
import com.gms.security.TenantContext;
import com.gms.service.StudentExamService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/student-exams")
public class StudentExamController {

    private final StudentExamService studentExamService;

    public StudentExamController(StudentExamService studentExamService) {
        this.studentExamService = studentExamService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<StudentExamResponse> registerStudentForExam(@Valid @RequestBody StudentExamRequest request) {
        return studentExamService.registerStudentForExam(request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deregisterStudentFromExam(@PathVariable Integer id) {
        return studentExamService.deregisterStudentFromExam(id);
    }

    @GetMapping("/exam-subject/{examSubjectId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<List<StudentExam>> getStudentsForExamSubject(@PathVariable Integer examSubjectId) {
        return studentExamService.getStudentsForExamSubject(examSubjectId);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT')")
    public ResponseEntity<List<StudentExam>> getExamsForStudent(@PathVariable Integer studentId) {
        return studentExamService.getExamsForStudent(studentId);
    }
}