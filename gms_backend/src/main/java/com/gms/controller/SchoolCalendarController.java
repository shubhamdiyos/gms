package com.gms.controller;

import com.gms.model.entity.SchoolCalendar;
import com.gms.model.request.SchoolCalendarRequest;
import com.gms.model.response.SchoolCalendarResponse;
import com.gms.service.SchoolCalendarService;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/school-calendar")
public class SchoolCalendarController {

    @Autowired
    private SchoolCalendarService schoolCalendarService;
    
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<SchoolCalendarResponse> createCalendarEvent(@Valid @RequestBody SchoolCalendarRequest calendarRequest) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return schoolCalendarService.createCalendarEvent(calendarRequest, schoolId, empId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<SchoolCalendarResponse> updateCalendarEvent(@PathVariable Integer id,
                                                                    @Valid @RequestBody SchoolCalendarRequest calendarRequest) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return schoolCalendarService.updateCalendarEvent(id, calendarRequest, schoolId, empId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteCalendarEvent(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return schoolCalendarService.deleteCalendarEvent(id, schoolId, empId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<SchoolCalendar>> getAllCalendarEvents() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return schoolCalendarService.getAllCalendarEvents(schoolId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<SchoolCalendar> getCalendarEventById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return schoolCalendarService.getCalendarEventById(id, schoolId);
    }

    @GetMapping("/range")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<SchoolCalendar>> getCalendarEventsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return schoolCalendarService.getCalendarEventsByDateRange(schoolId, startDate, endDate);
    }

    @GetMapping("/academic-year")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<SchoolCalendar>> getCalendarEventsByAcademicYear(
            @RequestParam String academicYear) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return schoolCalendarService.getCalendarEventsByAcademicYear(schoolId, academicYear);
    }
}