package com.gms.service.impl;

import com.gms.model.entity.Attendance;
import com.gms.model.entity.Classroom;
import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.entity.Timetable;
import com.gms.model.entity.TimetableSlot;
import com.gms.model.request.AttendanceItem;
import com.gms.model.request.AttendanceRequest;
import com.gms.model.request.BulkAttendanceRequest;
import com.gms.repository.AttendanceRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentRepository;
import com.gms.repository.TimetableSlotRepository;
import com.gms.security.TenantContext;
import com.gms.service.TimetableService;
import com.gms.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A register row names a timetable slot, so it can only describe a lesson that runs that day.
 *
 * <p>Found live on 2026-11-06: {@code attendances} id 5 held a FRIDAY date against a MONDAY slot,
 * accepted because the table's unique key is (student, slot, date) and nothing compared the slot's
 * {@code day_of_week} to the date. The client already filtered the period list by weekday; the
 * server did not, so any direct call could write the row.</p>
 */
@ExtendWith(MockitoExtension.class)
class AttendanceDayOfWeekGuardTest {

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private TimetableSlotRepository timetableSlotRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private TimetableService timetableService;
    @Mock
    private UserService userService;

    @InjectMocks
    private AttendanceServiceImpl attendanceService;

    private Student student;
    private TimetableSlot mondaySlot;
    private LocalDate friday;
    private LocalDate monday;

    @BeforeEach
    void setUp() {
        School school = new School();
        school.setId(1);

        Classroom classroom = new Classroom();
        classroom.setId(100);
        classroom.setSchool(school);
        Timetable timetable = new Timetable();
        timetable.setClassroom(classroom);

        student = new Student();
        student.setId(10);
        student.setSchool(school);
        student.setClassroom(classroom);

        mondaySlot = new TimetableSlot();
        mondaySlot.setId(1);
        mondaySlot.setTimetable(timetable);
        mondaySlot.setDayOfWeek("MONDAY");
        mondaySlot.setPeriod(1);

        monday = LocalDate.of(2026, 11, 2);
        friday = LocalDate.of(2026, 11, 6);
    }

    @Test
    @DisplayName("DAY-01: Single record against a slot that does not run that day is rejected")
    void recordAttendance_slotDoesNotRunOnDate_rejected() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(500);
            tenantContext.when(TenantContext::schoolId).thenReturn(1);

            when(studentRepository.findById(10)).thenReturn(Optional.of(student));
            when(timetableSlotRepository.findByIdAndSchool_Id(1, 1)).thenReturn(Optional.of(mondaySlot));

            AttendanceRequest request = new AttendanceRequest();
            request.setStudentId(10);
            request.setDate(friday);
            request.setTimetableSlotId(1);
            request.setStatus("PRESENT");

            assertThatThrownBy(() -> attendanceService.recordAttendance(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MONDAY")
                    .hasMessageContaining("FRIDAY");

            verify(attendanceRepository, never()).insertIgnoreDuplicate(
                    any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("DAY-02: A missing date is rejected rather than written as null")
    void recordAttendance_missingDate_rejected() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(500);
            tenantContext.when(TenantContext::schoolId).thenReturn(1);

            when(studentRepository.findById(10)).thenReturn(Optional.of(student));
            when(timetableSlotRepository.findByIdAndSchool_Id(1, 1)).thenReturn(Optional.of(mondaySlot));

            AttendanceRequest request = new AttendanceRequest();
            request.setStudentId(10);
            request.setDate(null);
            request.setTimetableSlotId(1);
            request.setStatus("PRESENT");

            assertThatThrownBy(() -> attendanceService.recordAttendance(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("date is required");

            verify(attendanceRepository, never()).insertIgnoreDuplicate(
                    any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("DAY-03: The whole class register is rejected before any row is written")
    void recordBulkAttendance_slotDoesNotRunOnDate_rejectedBeforeAnyWrite() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(500);
            tenantContext.when(TenantContext::schoolId).thenReturn(1);

            when(timetableSlotRepository.findByIdAndSchool_Id(1, 1)).thenReturn(Optional.of(mondaySlot));

            AttendanceItem item = new AttendanceItem();
            item.setStudentId(10);
            item.setStatus("PRESENT");

            BulkAttendanceRequest request = new BulkAttendanceRequest();
            request.setTimetableSlotId(1);
            request.setDate(friday);
            request.setAttendance(List.of(item));

            assertThatThrownBy(() -> attendanceService.recordBulkAttendance(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MONDAY");

            verify(attendanceRepository, never()).insertIgnoreDuplicate(
                    anyInt(), anyInt(), anyInt(), any(), any(), any(), anyInt(), anyInt());
            verify(studentRepository, never()).findById(anyInt());
        }
    }

    @Test
    @DisplayName("DAY-04: A correction cannot move a row onto a slot that does not run that day")
    void updateAttendance_slotDoesNotRunOnDate_rejected() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(500);
            tenantContext.when(TenantContext::schoolId).thenReturn(1);

            Attendance existing = new Attendance();
            existing.setId(5);
            existing.setStudent(student);
            existing.setTimetableSlot(mondaySlot);
            existing.setDate(monday);
            existing.setStatus("PRESENT");

            when(attendanceRepository.findById(5)).thenReturn(Optional.of(existing));
            when(studentRepository.findById(10)).thenReturn(Optional.of(student));

            AttendanceRequest request = new AttendanceRequest();
            request.setStudentId(10);
            request.setDate(friday);
            request.setStatus("ABSENT");

            assertThatThrownBy(() -> attendanceService.updateAttendance(5, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MONDAY")
                    .hasMessageContaining("FRIDAY");

            verify(attendanceRepository, never()).save(any(Attendance.class));
        }
    }
}
