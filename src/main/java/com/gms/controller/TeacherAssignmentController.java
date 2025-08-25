
package com.gms.controller;

import com.gms.model.request.TeacherAssignmentRequest;
import com.gms.model.response.TeacherAssignmentResponse;
import com.gms.service.TeacherAssignmentService;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(Constants.API_BASE_V1 + "/teacher-assignments")
public class TeacherAssignmentController {

    private final TeacherAssignmentService teacherAssignmentService;

    public TeacherAssignmentController(TeacherAssignmentService teacherAssignmentService) {
        this.teacherAssignmentService = teacherAssignmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeacherAssignmentResponse> createAssignment(@Valid @RequestBody TeacherAssignmentRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        if (schoolId == null || empId == null) {
            return ResponseEntity.status(401).build();
        }
        TeacherAssignmentResponse response = teacherAssignmentService.createAssignment(request, schoolId, empId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<List<TeacherAssignmentResponse>> getAllAssignments() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<TeacherAssignmentResponse> assignments = teacherAssignmentService.getAllAssignmentsBySchool(schoolId);
        return ResponseEntity.ok(assignments);
    }

    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<List<TeacherAssignmentResponse>> getAssignmentsByTeacher(@PathVariable Integer teacherId) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<TeacherAssignmentResponse> assignments = teacherAssignmentService.getAssignmentsByTeacher(teacherId, schoolId);
        return ResponseEntity.ok(assignments);
    }

    @GetMapping("/classroom/{classroomId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<List<TeacherAssignmentResponse>> getAssignmentsByClassroom(@PathVariable Integer classroomId) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<TeacherAssignmentResponse> assignments = teacherAssignmentService.getAssignmentsByClassroom(classroomId, schoolId);
        return ResponseEntity.ok(assignments);
    }

    @GetMapping("/academic-year/{academicYear}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<List<TeacherAssignmentResponse>> getAssignmentsByAcademicYear(@PathVariable String academicYear) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<TeacherAssignmentResponse> assignments = teacherAssignmentService.getAssignmentsByAcademicYear(academicYear, schoolId);
        return ResponseEntity.ok(assignments);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeacherAssignmentResponse> updateAssignment(@PathVariable Integer id, @Valid @RequestBody TeacherAssignmentRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        if (schoolId == null || empId == null) {
            return ResponseEntity.status(401).build();
        }
        TeacherAssignmentResponse response = teacherAssignmentService.updateAssignment(id, request, schoolId, empId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        teacherAssignmentService.deleteAssignment(id, schoolId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-assignments")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<TeacherAssignmentResponse>> getMyAssignments() {
        String username = SecurityUtil.getUsernameFromToken();
        List<TeacherAssignmentResponse> assignments = teacherAssignmentService.getAssignmentsByUsername(username);
        return ResponseEntity.ok(assignments);
    }
}
