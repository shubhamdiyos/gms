package com.gms.controller;

import com.gms.model.entity.Parent;
import com.gms.model.request.ParentRequest;
import com.gms.model.response.*;
import com.gms.service.ParentService;
import com.gms.service.impl.ParentServiceImpl;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:3000"})
@RequestMapping(Constants.API_BASE_V1 + "/parents")
public class ParentController extends AbstractCRUDController<Parent, Integer> {

    private final ParentService parentService;

    public ParentController(ParentServiceImpl parentServiceImpl) {
        super(parentServiceImpl);
        this.parentService = parentServiceImpl;
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ParentResponse> create(@Valid @RequestBody ParentRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return parentService.create(request, empId, schoolId);
    }

    @PutMapping("/update")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ParentResponse> update(@Valid @RequestBody ParentRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return parentService.update(request, empId, schoolId);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Parent>> getAll() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<Parent> parents = parentService.findBySchool(schoolId);
        if (parents.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(parents);
    }

    @PatchMapping("/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleActive(@RequestParam Integer id, @RequestParam Boolean isActive) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return parentService.toggleParent(id, isActive, schoolId, empId);
    }

    // Parent-specific role endpoints
    @GetMapping("/profile")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ParentResponse> getParentProfile() {
        String username = SecurityUtil.getUsernameFromToken();
        ParentResponse response = parentService.getParentProfile(username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/children")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<StudentResponse>> getChildren() {
        String username = SecurityUtil.getUsernameFromToken();
        List<StudentResponse> response = parentService.getChildren(username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/children/{studentId}/attendance")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<AttendanceResponse>> getChildAttendance(@PathVariable Integer studentId) {
        String username = SecurityUtil.getUsernameFromToken();
        List<AttendanceResponse> attendance = parentService.getChildAttendance(username, studentId);
        return ResponseEntity.ok(attendance);
    }

    @GetMapping("/children/{studentId}/results")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<ResultResponse>> getChildResults(@PathVariable Integer studentId) {
        String username = SecurityUtil.getUsernameFromToken();
        List<ResultResponse> results = parentService.getChildResults(username, studentId);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/children/{studentId}/fees")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<StudentFeeResponse>> getChildFees(@PathVariable Integer studentId) {
        String username = SecurityUtil.getUsernameFromToken();
        List<StudentFeeResponse> fees = parentService.getChildFees(username, studentId);
        return ResponseEntity.ok(fees);
    }

    @GetMapping("/children/{studentId}/timetable")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<TimetableResponse>> getChildTimetable(@PathVariable Integer studentId) {
        String username = SecurityUtil.getUsernameFromToken();
        List<TimetableResponse> timetable = parentService.getChildTimetable(username, studentId);
        return ResponseEntity.ok(timetable);
    }

    @GetMapping("/children/{studentId}/announcements")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<AnnouncementResponse>> getChildAnnouncements(@PathVariable Integer studentId) {
        String username = SecurityUtil.getUsernameFromToken();
        List<AnnouncementResponse> announcements = parentService.getChildAnnouncements(username, studentId);
        return ResponseEntity.ok(announcements);
    }

    @GetMapping("/children/{studentId}/subjects")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<SubjectResponse>> getChildSubjects(@PathVariable Integer studentId) {
        String username = SecurityUtil.getUsernameFromToken();
        List<SubjectResponse> subjects = parentService.getChildSubjects(username, studentId);
        return ResponseEntity.ok(subjects);
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<NotificationResponse>> getParentNotifications() {
        String username = SecurityUtil.getUsernameFromToken();
        List<NotificationResponse> notifications = parentService.getParentNotifications(username);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/children/{studentId}/teachers")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<List<EmployeeResponse>> getChildTeachers(@PathVariable Integer studentId) {
        String username = SecurityUtil.getUsernameFromToken();
        List<EmployeeResponse> teachers = parentService.getChildTeachers(username, studentId);
        return ResponseEntity.ok(teachers);
    }
}