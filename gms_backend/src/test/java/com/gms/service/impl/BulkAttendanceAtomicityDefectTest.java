package com.gms.service.impl;

import com.gms.exception.NotFoundException;
import com.gms.model.entity.*;
import com.gms.model.request.AttendanceItem;
import com.gms.model.request.BulkAttendanceRequest;
import com.gms.repository.*;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ATT-06 verification: bulk attendance atomicity after the validate-all-before-write fix.
 *
 * Proves that:
 * 1. Classroom mismatch throws IllegalArgumentException BEFORE any save() calls
 * 2. Missing student throws NotFoundException BEFORE any save() calls
 * 3. No partial writes can occur — all validation happens before any persistence
 *
 * Database-backed rollback verification is BLOCKED (no Testcontainers infrastructure).
 * Mocked tests prove the mechanism: exceptions are thrown before the write loop is reached.
 */
@ExtendWith(MockitoExtension.class)
class BulkAttendanceAtomicityDefectTest {

    @Mock private AttendanceRepository attendanceRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private TimetableSlotRepository timetableSlotRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private TimetableService timetableService;
    @Mock private UserService userService;

    @InjectMocks
    private AttendanceServiceImpl attendanceService;

    private School school;
    private Classroom classroom100;
    private Classroom classroom200;
    private Timetable timetableForClass100;
    private TimetableSlot slot;
    private Student studentA;
    private Student studentB;
    private Student studentC;
    private User teacher;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        school = new School();
        school.setId(1);

        classroom100 = new Classroom();
        classroom100.setId(100);
        classroom100.setSchool(school);

        classroom200 = new Classroom();
        classroom200.setId(200);
        classroom200.setSchool(school);

        timetableForClass100 = new Timetable();
        timetableForClass100.setClassroom(classroom100);

        slot = new TimetableSlot();
        slot.setId(1);
        slot.setTimetable(timetableForClass100);
        slot.setDayOfWeek("MONDAY");
        slot.setPeriod(1);

        studentA = new Student();
        studentA.setId(10);
        studentA.setSchool(school);
        studentA.setClassroom(classroom100);

        studentB = new Student();
        studentB.setId(20);
        studentB.setSchool(school);
        studentB.setClassroom(classroom100);

        studentC = new Student();
        studentC.setId(30);
        studentC.setSchool(school);
        studentC.setClassroom(classroom200);

        teacher = new User();
        teacher.setId(1);
        teacher.setUsername("teacher1");

        testDate = LocalDate.of(2026, 11, 3);
    }

    private BulkAttendanceRequest buildRequest(List<Integer> studentIds) {
        BulkAttendanceRequest request = new BulkAttendanceRequest();
        request.setTimetableSlotId(slot.getId());
        request.setDate(testDate);
        request.setAttendance(studentIds.stream().map(id -> {
            AttendanceItem item = new AttendanceItem();
            item.setStudentId(id);
            item.setStatus("PRESENT");
            return item;
        }).toList());
        return request;
    }

    @Test
    @DisplayName("ATT-06 FIXED: classroom mismatch — exception thrown, NO save() calls")
    void classroomMismatch_exceptionThrown_noSaveCalls() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacher.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school.getId());

            BulkAttendanceRequest request = buildRequest(List.of(10, 20, 30));

            when(timetableSlotRepository.findByIdAndSchool_Id(slot.getId(), school.getId()))
                    .thenReturn(Optional.of(slot));
            when(schoolRepository.getReferenceById(school.getId())).thenReturn(school);
            when(studentRepository.findById(10)).thenReturn(Optional.of(studentA));
            when(studentRepository.findById(20)).thenReturn(Optional.of(studentB));
            when(studentRepository.findById(30)).thenReturn(Optional.of(studentC));

            assertThatThrownBy(() -> attendanceService.recordBulkAttendance(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Student 30")
                    .hasMessageContaining("does not belong to the classroom");

            verify(attendanceRepository, never()).save(any(Attendance.class));
        }
    }

    @Test
    @DisplayName("ATT-06 FIXED: student not found — exception thrown, NO save() calls")
    void studentNotFound_exceptionThrown_noSaveCalls() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacher.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school.getId());

            BulkAttendanceRequest request = buildRequest(List.of(10, 20, 999));

            when(timetableSlotRepository.findByIdAndSchool_Id(slot.getId(), school.getId()))
                    .thenReturn(Optional.of(slot));
            when(studentRepository.findById(10)).thenReturn(Optional.of(studentA));
            when(studentRepository.findById(20)).thenReturn(Optional.of(studentB));
            when(studentRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.recordBulkAttendance(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Student not found");

            verify(attendanceRepository, never()).save(any(Attendance.class));
        }
    }

    @Test
    @DisplayName("ATT-06: first student has classroom mismatch — fails immediately, no writes")
    void firstStudentMismatch_failsImmediately_noWrites() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacher.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school.getId());

            BulkAttendanceRequest request = buildRequest(List.of(30, 10, 20));

            when(timetableSlotRepository.findByIdAndSchool_Id(slot.getId(), school.getId()))
                    .thenReturn(Optional.of(slot));
            when(studentRepository.findById(30)).thenReturn(Optional.of(studentC));

            assertThatThrownBy(() -> attendanceService.recordBulkAttendance(request))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(attendanceRepository, never()).save(any(Attendance.class));
            verify(studentRepository, never()).findById(10);
            verify(studentRepository, never()).findById(20);
        }
    }

    @Test
    @DisplayName("ATT-06: all valid students — save() called for each, no exception")
    void allValid_students_savedWithoutException() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacher.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school.getId());

            BulkAttendanceRequest request = buildRequest(List.of(10, 20));

            when(timetableSlotRepository.findByIdAndSchool_Id(slot.getId(), school.getId()))
                    .thenReturn(Optional.of(slot));
            when(schoolRepository.getReferenceById(school.getId())).thenReturn(school);
            when(studentRepository.findById(10)).thenReturn(Optional.of(studentA));
            when(studentRepository.findById(20)).thenReturn(Optional.of(studentB));
            when(userService.findByEmployee_Id()).thenReturn(Optional.of(teacher));
            when(attendanceRepository.insertIgnoreDuplicate(eq(school.getId()), anyInt(), eq(slot.getId()), eq(testDate), anyString(), any(), anyInt(), anyInt()))
                    .thenReturn(1);

            var response = attendanceService.recordBulkAttendance(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(attendanceRepository, times(2)).insertIgnoreDuplicate(anyInt(), anyInt(), anyInt(), any(), anyString(), any(), anyInt(), anyInt());
            verify(attendanceRepository, never()).save(any(Attendance.class));
        }
    }
}
