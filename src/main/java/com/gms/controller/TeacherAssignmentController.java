
package com.gms.controller;

import com.gms.model.request.TeacherAssignmentRequest;
import com.gms.model.response.TeacherAssignmentResponse;
import com.gms.service.TeacherAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teacher-assignments")
public class TeacherAssignmentController {

    private final TeacherAssignmentService teacherAssignmentService;

    public TeacherAssignmentController(TeacherAssignmentService teacherAssignmentService) {
        this.teacherAssignmentService = teacherAssignmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeacherAssignmentResponse> createAssignment(@Valid @RequestBody TeacherAssignmentRequest request) {
        TeacherAssignmentResponse response = teacherAssignmentService.createAssignment(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
