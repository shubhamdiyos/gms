package com.gms.controller;

import com.gms.model.entity.SchoolCalendar;
import com.gms.model.request.SchoolCalendarRequest;
import com.gms.model.response.SchoolCalendarResponse;
import com.gms.security.TenantContext;
import com.gms.service.SchoolCalendarService;
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
        return schoolCalendarService.createCalendarEvent(calendarRequest);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<SchoolCalendarResponse> updateCalendarEvent(@PathVariable Integer id,
                                                                    @Valid @RequestBody SchoolCalendarRequest calendarRequest) {
        return schoolCalendarService.updateCalendarEvent(id, calendarRequest);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteCalendarEvent(@PathVariable Integer id) {
        return schoolCalendarService.deleteCalendarEvent(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<SchoolCalendar>> getAllCalendarEvents() {
        return schoolCalendarService.getAllCalendarEvents();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<SchoolCalendar> getCalendarEventById(@PathVariable Integer id) {
        return schoolCalendarService.getCalendarEventById(id);
    }

    @GetMapping("/range")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<SchoolCalendar>> getCalendarEventsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return schoolCalendarService.getCalendarEventsByDateRange(startDate, endDate);
    }

    @GetMapping("/academic-year")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER', 'STUDENT', 'PARENT')")
    public ResponseEntity<List<SchoolCalendar>> getCalendarEventsByAcademicYear(
            @RequestParam String academicYear) {
        return schoolCalendarService.getCalendarEventsByAcademicYear(academicYear);
    }
}