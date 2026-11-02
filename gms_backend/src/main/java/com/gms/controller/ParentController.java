package com.gms.controller;

import com.gms.model.entity.Parent;
import com.gms.model.request.ParentRequest;
import com.gms.model.response.*;
import com.gms.security.TenantContext;
import com.gms.service.ParentService;
import com.gms.util.Constants;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Parent administration and the parent portal.
 *
 * <p>This controller no longer extends {@code AbstractCRUDController}, which supplied an unguarded
 * {@code GET /{id}} and {@code DELETE /{id}}. Neither is declared here, and that is a decision
 * rather than an omission: {@code parents} carries no {@code school_id} of its own — the tenant
 * lives on the {@code parent_student} link — so an id-addressed read would have to reconstruct the
 * tenant from the children, and the delete would orphan the {@code users.parent_id} foreign key.
 * The reads that do exist are tenant-scoped: {@code /parents/profile} and {@code /parents/children/*}
 * resolve the parent from the caller's own token, and {@code /parent-students/parent/{id}/students}
 * passes the caller's school into the join query.</p>
 */
@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping(Constants.API_BASE_V1 + "/parents")
public class ParentController {

    private final ParentService parentService;

    public ParentController(ParentService parentService) {
        this.parentService = parentService;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ParentResponse> create(@Valid @RequestBody ParentRequest request) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return parentService.create(request);
    }

    @PutMapping("/update")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ParentResponse> update(@Valid @RequestBody ParentRequest request) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return parentService.update(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<Parent>> getAll() {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<Parent> parents = parentService.findBySchool();
        if (parents.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(parents);
    }

    @PatchMapping("/toggle")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> toggleActive(@RequestParam Integer id, @RequestParam Boolean isActive) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return parentService.toggleParent(id, isActive);
    }

    // Parent-specific role endpoints
    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('PARENT')")
    public ResponseEntity<ParentResponse> getParentProfile() {
        String username = TenantContext.username();
        ParentResponse response = parentService.getParentProfile(username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/children")
    @PreAuthorize("hasAuthority('PARENT')")
    public ResponseEntity<List<StudentResponse>> getChildren() {
        String username = TenantContext.username();
        List<StudentResponse> response = parentService.getChildren(username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/children/{studentId}/attendance")
    @PreAuthorize("hasAuthority('PARENT')")
    public ResponseEntity<List<AttendanceResponse>> getChildAttendance(@PathVariable Integer studentId) {
        String username = TenantContext.username();
        List<AttendanceResponse> attendance = parentService.getChildAttendance(username, studentId);
        return ResponseEntity.ok(attendance);
    }

    @GetMapping("/children/{studentId}/results")
    @PreAuthorize("hasAuthority('PARENT')")
    public ResponseEntity<List<ResultResponse>> getChildResults(@PathVariable Integer studentId) {
        String username = TenantContext.username();
        List<ResultResponse> results = parentService.getChildResults(username, studentId);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/children/{studentId}/fees")
    @PreAuthorize("hasAuthority('PARENT')")
    public ResponseEntity<List<StudentFeeResponse>> getChildFees(@PathVariable Integer studentId) {
        String username = TenantContext.username();
        List<StudentFeeResponse> fees = parentService.getChildFees(username, studentId);
        return ResponseEntity.ok(fees);
    }

    @GetMapping("/children/{studentId}/timetable")
    @PreAuthorize("hasAuthority('PARENT')")
    public ResponseEntity<List<TimetableResponse>> getChildTimetable(@PathVariable Integer studentId) {
        String username = TenantContext.username();
        List<TimetableResponse> timetable = parentService.getChildTimetable(username, studentId);
        return ResponseEntity.ok(timetable);
    }

    @GetMapping("/children/{studentId}/announcements")
    @PreAuthorize("hasAuthority('PARENT')")
    public ResponseEntity<List<AnnouncementResponse>> getChildAnnouncements(@PathVariable Integer studentId) {
        String username = TenantContext.username();
        List<AnnouncementResponse> announcements = parentService.getChildAnnouncements(username, studentId);
        return ResponseEntity.ok(announcements);
    }

    @GetMapping("/children/{studentId}/subjects")
    @PreAuthorize("hasAuthority('PARENT')")
    public ResponseEntity<List<SubjectResponse>> getChildSubjects(@PathVariable Integer studentId) {
        String username = TenantContext.username();
        List<SubjectResponse> subjects = parentService.getChildSubjects(username, studentId);
        return ResponseEntity.ok(subjects);
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasAuthority('PARENT')")
    public ResponseEntity<List<NotificationResponse>> getParentNotifications() {
        String username = TenantContext.username();
        List<NotificationResponse> notifications = parentService.getParentNotifications(username);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/children/{studentId}/teachers")
    @PreAuthorize("hasAuthority('PARENT')")
    public ResponseEntity<List<EmployeeResponse>> getChildTeachers(@PathVariable Integer studentId) {
        String username = TenantContext.username();
        List<EmployeeResponse> teachers = parentService.getChildTeachers(username, studentId);
        return ResponseEntity.ok(teachers);
    }
}