package com.gms.controller;

import com.gms.model.entity.Parent;
import com.gms.model.entity.Student;
import com.gms.model.request.ParentStudentRequest;
import com.gms.service.ParentStudentService;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/parent-student")
public class ParentStudentController {

    @Autowired
    private ParentStudentService parentStudentService;

    @PostMapping("/assign")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> assignStudentsToParent(@Valid @RequestBody ParentStudentRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return parentStudentService.assignStudentsToParent(request, schoolId, empId);
    }

    @DeleteMapping("/remove")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> removeStudentFromParent(@RequestParam Integer parentId, @RequestParam Integer studentId) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return parentStudentService.removeStudentFromParent(parentId, studentId, schoolId, empId);
    }

    @GetMapping("/parent/{parentId}/students")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'PARENT')")
    public ResponseEntity<List<Student>> getStudentsForParent(@PathVariable Integer parentId) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return parentStudentService.getStudentsForParent(parentId, schoolId);
    }

    @GetMapping("/student/{studentId}/parents")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<List<Parent>> getParentsForStudent(@PathVariable Integer studentId) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return parentStudentService.getParentsForStudent(studentId, schoolId);
    }
}