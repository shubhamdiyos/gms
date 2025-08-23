package com.gms.service;

import com.gms.model.entity.SchoolCalendar;
import com.gms.model.request.SchoolCalendarRequest;
import com.gms.model.response.SchoolCalendarResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

public interface SchoolCalendarService {

    ResponseEntity<SchoolCalendarResponse> createCalendarEvent(SchoolCalendarRequest calendarRequest, Integer schoolId, Integer empId);

    ResponseEntity<SchoolCalendarResponse> updateCalendarEvent(Integer id, SchoolCalendarRequest calendarRequest, Integer schoolId, Integer empId);

    ResponseEntity<?> deleteCalendarEvent(Integer id, Integer schoolId, Integer empId);

    ResponseEntity<List<SchoolCalendar>> getAllCalendarEvents(Integer schoolId);

    ResponseEntity<SchoolCalendar> getCalendarEventById(Integer id, Integer schoolId);

    ResponseEntity<List<SchoolCalendar>> getCalendarEventsByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate);

    ResponseEntity<List<SchoolCalendar>> getCalendarEventsByAcademicYear(Integer schoolId, String academicYear);
}