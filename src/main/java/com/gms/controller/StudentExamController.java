package com.gms.controller;

import com.gms.model.entity.StudentExam;
import com.gms.model.request.StudentExamRequest;
import com.gms.model.response.StudentExamResponse;
import com.gms.service.StudentExamService;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/student-exams")
public class StudentExamController {

    private final StudentExamService studentExamService;

    public StudentExamController(StudentExamService studentExamService) {
        this.studentExamService = studentExamService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<StudentExamResponse> registerStudentForExam(@Valid @RequestBody StudentExamRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return studentExamService.registerStudentForExam(request, schoolId, empId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deregisterStudentFromExam(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return studentExamService.deregisterStudentFromExam(id, schoolId, empId);
    }

    @GetMapping("/exam-subject/{examSubjectId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<List<StudentExam>> getStudentsForExamSubject(@PathVariable Integer examSubjectId) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return studentExamService.getStudentsForExamSubject(examSubjectId, schoolId);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT')")
    public ResponseEntity<List<StudentExam>> getExamsForStudent(@PathVariable Integer studentId) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return studentExamService.getExamsForStudent(studentId, schoolId);
    }
}