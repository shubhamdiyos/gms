package com.gms.service.impl;

import com.gms.model.entity.School;
import com.gms.model.entity.SchoolCalendar;
import com.gms.model.entity.User;
import com.gms.model.request.SchoolCalendarRequest;
import com.gms.model.response.SchoolCalendarResponse;
import com.gms.repository.SchoolCalendarRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.SchoolCalendarService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class SchoolCalendarServiceImpl extends AbstractCRUDService<SchoolCalendar, Integer> implements SchoolCalendarService {

    private final SchoolCalendarRepository schoolCalendarRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    @Autowired
    public SchoolCalendarServiceImpl(SchoolCalendarRepository schoolCalendarRepository,
                                    SchoolRepository schoolRepository,
                                    UserRepository userRepository) {
        super(schoolCalendarRepository);
        this.schoolCalendarRepository = schoolCalendarRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<SchoolCalendarResponse> createCalendarEvent(SchoolCalendarRequest calendarRequest, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Create calendar event
        SchoolCalendar calendarEvent = new SchoolCalendar();
        calendarEvent.setSchool(school);
        calendarEvent.setTitle(calendarRequest.getTitle());
        calendarEvent.setDescription(calendarRequest.getDescription());
        calendarEvent.setEventDate(calendarRequest.getEventDate());
        calendarEvent.setEventType(calendarRequest.getEventType());
        calendarEvent.setIsRecurring(calendarRequest.getIsRecurring());
        calendarEvent.setAcademicYear(calendarRequest.getAcademicYear());
        calendarEvent.setStatus("1");
        calendarEvent.setCreatedBy(creator);
        calendarEvent.setUpdatedBy(creator);

        SchoolCalendar savedEvent = schoolCalendarRepository.save(calendarEvent);

        // Map to response
        SchoolCalendarResponse response = new SchoolCalendarResponse();
        response.setId(savedEvent.getId());
        response.setSchoolId(savedEvent.getSchoolId());
        response.setSchoolName(savedEvent.getSchoolName());
        response.setTitle(savedEvent.getTitle());
        response.setDescription(savedEvent.getDescription());
        response.setEventDate(savedEvent.getEventDate());
        response.setEventType(savedEvent.getEventType());
        response.setIsRecurring(savedEvent.getIsRecurring());
        response.setAcademicYear(savedEvent.getAcademicYear());
        response.setStatus(savedEvent.getStatus());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<SchoolCalendarResponse> updateCalendarEvent(Integer id, SchoolCalendarRequest calendarRequest, Integer schoolId, Integer empId) {
        // Validate calendar event exists
        SchoolCalendar calendarEvent = schoolCalendarRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Calendar event not found"));

        // Validate school
        if (!calendarEvent.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update calendar event
        calendarEvent.setTitle(calendarRequest.getTitle());
        calendarEvent.setDescription(calendarRequest.getDescription());
        calendarEvent.setEventDate(calendarRequest.getEventDate());
        calendarEvent.setEventType(calendarRequest.getEventType());
        calendarEvent.setIsRecurring(calendarRequest.getIsRecurring());
        calendarEvent.setAcademicYear(calendarRequest.getAcademicYear());
        calendarEvent.setUpdatedBy(updater);

        SchoolCalendar savedEvent = schoolCalendarRepository.save(calendarEvent);

        // Map to response
        SchoolCalendarResponse response = new SchoolCalendarResponse();
        response.setId(savedEvent.getId());
        response.setSchoolId(savedEvent.getSchoolId());
        response.setSchoolName(savedEvent.getSchoolName());
        response.setTitle(savedEvent.getTitle());
        response.setDescription(savedEvent.getDescription());
        response.setEventDate(savedEvent.getEventDate());
        response.setEventType(savedEvent.getEventType());
        response.setIsRecurring(savedEvent.getIsRecurring());
        response.setAcademicYear(savedEvent.getAcademicYear());
        response.setStatus(savedEvent.getStatus());

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<?> deleteCalendarEvent(Integer id, Integer schoolId, Integer empId) {
        // Validate calendar event exists
        SchoolCalendar calendarEvent = schoolCalendarRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Calendar event not found"));

        // Validate school
        if (!calendarEvent.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Soft delete
        calendarEvent.setStatus("0");

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));
        calendarEvent.setUpdatedBy(updater);

        schoolCalendarRepository.save(calendarEvent);
        return ResponseEntity.ok("Calendar event deleted successfully");
    }

    @Override
    public ResponseEntity<List<SchoolCalendar>> getAllCalendarEvents(Integer schoolId) {
        List<SchoolCalendar> events = schoolCalendarRepository.findBySchool_IdAndStatus(schoolId, "1");
        return ResponseEntity.ok(events);
    }

    @Override
    public ResponseEntity<SchoolCalendar> getCalendarEventById(Integer id, Integer schoolId) {
        Optional<SchoolCalendar> eventOptional = schoolCalendarRepository.findById(id);
        if (eventOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        SchoolCalendar event = eventOptional.get();
        if (!event.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(event);
    }

    @Override
    public ResponseEntity<List<SchoolCalendar>> getCalendarEventsByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate) {
        List<SchoolCalendar> events = schoolCalendarRepository.findBySchool_IdAndEventDateBetween(schoolId, startDate, endDate);
        return ResponseEntity.ok(events);
    }

    @Override
    public ResponseEntity<List<SchoolCalendar>> getCalendarEventsByAcademicYear(Integer schoolId, String academicYear) {
        List<SchoolCalendar> events = schoolCalendarRepository.findBySchool_IdAndAcademicYearAndStatus(schoolId, academicYear, "1");
        return ResponseEntity.ok(events);
    }
}