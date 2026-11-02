package com.gms.controller;

import com.gms.model.entity.Parent;
import com.gms.model.entity.Student;
import com.gms.model.request.ParentStudentRequest;
import com.gms.security.TenantContext;
import com.gms.service.ParentStudentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/parent-students")
public class ParentStudentController {

    @Autowired
    private ParentStudentService parentStudentService;
    
    @PostMapping("/assign")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> assignStudentsToParent(@Valid @RequestBody ParentStudentRequest request) {
        return parentStudentService.assignStudentsToParent(request);
    }

    @DeleteMapping("/remove")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> removeStudentFromParent(@RequestParam Integer parentId, @RequestParam Integer studentId) {
        return parentStudentService.removeStudentFromParent(parentId, studentId);
    }

    @GetMapping("/parent/{parentId}/students")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'PARENT')")
    public ResponseEntity<List<Student>> getStudentsForParent(@PathVariable Integer parentId) {
        return parentStudentService.getStudentsForParent(parentId);
    }

    @GetMapping("/student/{studentId}/parents")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<List<Parent>> getParentsForStudent(@PathVariable Integer studentId) {
        return parentStudentService.getParentsForStudent(studentId);
    }
}