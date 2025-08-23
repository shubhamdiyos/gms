package com.gms.controller;

import com.gms.model.response.EmployeeResponse;
import com.gms.model.response.ClassroomResponse;
import com.gms.model.response.StudentResponse;
import com.gms.service.TeacherService;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(Constants.API_BASE_V1 + "/teachers")
public class TeacherController {

    private final TeacherService teacherService;

    public TeacherController(TeacherService teacherService) {
        this.teacherService = teacherService;
    }

    @GetMapping("/profile")
    public ResponseEntity<EmployeeResponse> getTeacherProfile() {
        String username = SecurityUtil.getUsernameFromToken();
        EmployeeResponse response = teacherService.getTeacherProfile(username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/classes")
    public ResponseEntity<List<ClassroomResponse>> getAssignedClasses() {
        String username = SecurityUtil.getUsernameFromToken();
        List<ClassroomResponse> response = teacherService.getAssignedClasses(username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/students")
    public ResponseEntity<List<StudentResponse>> getAssignedStudents() {
        String username = SecurityUtil.getUsernameFromToken();
        List<StudentResponse> response = teacherService.getAssignedStudents(username);
        return ResponseEntity.ok(response);
    }
}