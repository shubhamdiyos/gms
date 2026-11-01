package com.gms.service;

import com.gms.model.entity.SchoolCalendar;
import com.gms.model.request.SchoolCalendarRequest;
import com.gms.model.response.SchoolCalendarResponse;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

public interface SchoolCalendarService {

    ResponseEntity<SchoolCalendarResponse> createCalendarEvent(SchoolCalendarRequest calendarRequest);

    ResponseEntity<SchoolCalendarResponse> updateCalendarEvent(Integer id, SchoolCalendarRequest calendarRequest);

    ResponseEntity<?> deleteCalendarEvent(Integer id);

    ResponseEntity<List<SchoolCalendar>> getAllCalendarEvents();

    ResponseEntity<SchoolCalendar> getCalendarEventById(Integer id);

    ResponseEntity<List<SchoolCalendar>> getCalendarEventsByDateRange(LocalDate startDate, LocalDate endDate);

    ResponseEntity<List<SchoolCalendar>> getCalendarEventsByAcademicYear(String academicYear);
}