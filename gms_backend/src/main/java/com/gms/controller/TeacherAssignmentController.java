
package com.gms.controller;

import com.gms.model.request.TeacherAssignmentRequest;
import com.gms.model.response.TeacherAssignmentResponse;
import com.gms.security.TenantContext;
import com.gms.service.TeacherAssignmentService;
import com.gms.util.Constants;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/teacher-assignments")
public class TeacherAssignmentController {

    private final TeacherAssignmentService teacherAssignmentService;

    public TeacherAssignmentController(TeacherAssignmentService teacherAssignmentService) {
        this.teacherAssignmentService = teacherAssignmentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<TeacherAssignmentResponse> createAssignment(@Valid @RequestBody TeacherAssignmentRequest request) {
        Integer schoolId = TenantContext.schoolId();
        Integer empId = TenantContext.employeeId();
        if (schoolId == null || empId == null) {
            return ResponseEntity.status(401).build();
        }
        TeacherAssignmentResponse response = teacherAssignmentService.createAssignment(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<List<TeacherAssignmentResponse>> getAllAssignments() {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<TeacherAssignmentResponse> assignments = teacherAssignmentService.getAllAssignmentsBySchool();
        return ResponseEntity.ok(assignments);
    }

    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<List<TeacherAssignmentResponse>> getAssignmentsByTeacher(@PathVariable Integer teacherId) {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<TeacherAssignmentResponse> assignments = teacherAssignmentService.getAssignmentsByTeacher(teacherId);
        return ResponseEntity.ok(assignments);
    }

    @GetMapping("/classroom/{classroomId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<List<TeacherAssignmentResponse>> getAssignmentsByClassroom(@PathVariable Integer classroomId) {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<TeacherAssignmentResponse> assignments = teacherAssignmentService.getAssignmentsByClassroom(classroomId);
        return ResponseEntity.ok(assignments);
    }

    @GetMapping("/academic-year/{academicYear}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<List<TeacherAssignmentResponse>> getAssignmentsByAcademicYear(@PathVariable String academicYear) {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<TeacherAssignmentResponse> assignments = teacherAssignmentService.getAssignmentsByAcademicYear(academicYear);
        return ResponseEntity.ok(assignments);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<TeacherAssignmentResponse> updateAssignment(@PathVariable Integer id, @Valid @RequestBody TeacherAssignmentRequest request) {
        Integer schoolId = TenantContext.schoolId();
        Integer empId = TenantContext.employeeId();
        if (schoolId == null || empId == null) {
            return ResponseEntity.status(401).build();
        }
        TeacherAssignmentResponse response = teacherAssignmentService.updateAssignment(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Integer id) {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        teacherAssignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-assignments")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<List<TeacherAssignmentResponse>> getMyAssignments() {
        String username = TenantContext.username();
        List<TeacherAssignmentResponse> assignments = teacherAssignmentService.getAssignmentsByUsername(username);
        return ResponseEntity.ok(assignments);
    }
}
