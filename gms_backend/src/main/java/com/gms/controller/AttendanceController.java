
package com.gms.controller;

import com.gms.model.entity.Attendance;
import com.gms.model.request.AttendanceRequest;
import com.gms.model.request.BulkAttendanceRequest;
import com.gms.model.response.AttendanceResponse;
import com.gms.security.TenantContext;
import com.gms.service.AttendanceService;
import com.gms.util.Constants;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping(Constants.API_BASE_V1 + "/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<AttendanceResponse> recordAttendance(@Valid @RequestBody AttendanceRequest request) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return attendanceService.recordAttendance(request);
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<?> recordBulkAttendance(@Valid @RequestBody BulkAttendanceRequest request) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return attendanceService.recordBulkAttendance(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<AttendanceResponse> updateAttendance(@PathVariable Integer id, 
                                                             @Valid @RequestBody AttendanceRequest request) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return attendanceService.updateAttendance(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteAttendance(@PathVariable Integer id) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return attendanceService.deleteAttendance(id);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<List<AttendanceResponse>> getAttendanceForStudent(
            @PathVariable Integer studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<AttendanceResponse> response = attendanceService.getAttendanceForStudent(studentId, from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/classroom/{classroomId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<List<AttendanceResponse>> getAttendanceForClassroom(
            @PathVariable Integer classroomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<AttendanceResponse> response = attendanceService.getAttendanceForClassroom(classroomId, date);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/range")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<List<AttendanceResponse>> getAttendanceByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<AttendanceResponse> response = attendanceService.getAttendanceByDateRange(startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/student/{studentId}/range")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<List<AttendanceResponse>> getAttendanceByStudentAndDateRange(
            @PathVariable Integer studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<AttendanceResponse> response = attendanceService.getAttendanceByStudentAndDateRange(studentId, startDate, endDate);
        return ResponseEntity.ok(response);
    }
}
