
package com.gms.controller;

import com.gms.model.request.TimetableRequest;
import com.gms.model.response.TimetableResponse;
import com.gms.service.TimetableService;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/timetables")
public class TimetableController {

    private final TimetableService timetableService;

    public TimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<TimetableResponse> createOrUpdateTimetable(@Valid @RequestBody TimetableRequest request) {
        TimetableResponse response = timetableService.createOrUpdateTimetable(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/classroom/{classroomId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER', 'STUDENT')")
    public ResponseEntity<TimetableResponse> getTimetableForClassroom(@PathVariable Integer classroomId) {
        TimetableResponse response = timetableService.getTimetableForClassroom(classroomId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/teacher/me")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<TimetableResponse> getTimetableForCurrentTeacher() {
        Integer teacherId = SecurityUtil.getEmpIdFromToken();
        if (teacherId == null) {
            throw new IllegalStateException("Employee ID not found in token");
        }
        TimetableResponse response = timetableService.getTimetableForTeacher(teacherId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/student/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<TimetableResponse> getTimetableForCurrentStudent() {
        // Get student ID from the security context
        String username = SecurityUtil.getUsernameFromToken();
        // In a complete implementation, we would retrieve the student based on the username
        // For now, we'll throw an exception as this requires additional implementation
        throw new UnsupportedOperationException("Getting student timetable requires additional implementation to retrieve student from username");
    }
}
