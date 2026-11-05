package com.gms.service.impl;

import com.gms.exception.NotFoundException;
import com.gms.model.entity.*;
import com.gms.model.request.AttendanceItem;
import com.gms.model.request.AttendanceRequest;
import com.gms.model.request.BulkAttendanceRequest;
import com.gms.model.response.AttendanceResponse;
import com.gms.repository.*;
import com.gms.security.TenantContext;
import com.gms.service.TimetableService;
import com.gms.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

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
 * ATT-02 regression tests: slot-level attendance duplicate detection.
 * 
 * Verifies that attendance uniqueness is enforced at (student_id, timetable_slot_id, date)
 * granularity, matching the database constraint uq_attendance_student_slot_date.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplSlotLevelTest {

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

    private School school;
    private Student student1;
    private Student student2;
    private TimetableSlot slot1;
    private TimetableSlot slot2;
    private User teacher;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        school = new School();
        school.setId(1);

        Classroom classroom = new Classroom();
        classroom.setId(100);
        classroom.setSchool(school);

        student1 = new Student();
        student1.setId(10);
        student1.setSchool(school);
        student1.setClassroom(classroom);

        student2 = new Student();
        student2.setId(20);
        student2.setSchool(school);
        student2.setClassroom(classroom);

        Timetable timetable = new Timetable();
        timetable.setClassroom(classroom);

        slot1 = new TimetableSlot();
        slot1.setId(1);
        slot1.setTimetable(timetable);
        slot1.setDayOfWeek("MONDAY");
        slot1.setPeriod(1);

        slot2 = new TimetableSlot();
        slot2.setId(2);
        slot2.setTimetable(timetable);
        slot2.setDayOfWeek("MONDAY");
        slot2.setPeriod(2);

        teacher = new User();
        teacher.setId(1000);
        teacher.setEmployee(new Employee());
        teacher.getEmployee().setId(500);

        testDate = LocalDate.of(2026, 11, 3);
    }

    @Test
    @DisplayName("ATT-02-01: Same student, same date, different slots — both records must be saved")
    void recordAttendance_sameStudentSameDateDifferentSlots_bothSaved() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(500);
            tenantContext.when(TenantContext::schoolId).thenReturn(1);

            when(studentRepository.findById(10)).thenReturn(Optional.of(student1));
            when(timetableSlotRepository.findByIdAndSchool_Id(1, 1)).thenReturn(Optional.of(slot1));
            when(timetableSlotRepository.findByIdAndSchool_Id(2, 1)).thenReturn(Optional.of(slot2));
            when(userService.findByEmployee_Id()).thenReturn(Optional.of(teacher));

            // First slot: atomic upsert succeeds (returns 1)
            when(attendanceRepository.insertIgnoreDuplicate(1, 10, 1, testDate, "PRESENT", null, teacher.getId(), teacher.getId()))
                    .thenReturn(1);
            Attendance attendance1 = new Attendance();
            attendance1.setId(1);
            attendance1.setStudent(student1);
            attendance1.setTimetableSlot(slot1);
            attendance1.setDate(testDate);
            attendance1.setStatus("PRESENT");
            when(attendanceRepository.findByStudent_School_IdAndStudent_IdAndTimetableSlot_IdAndDate(1, 10, 1, testDate))
                    .thenReturn(Optional.of(attendance1));

            AttendanceRequest request1 = new AttendanceRequest();
            request1.setStudentId(10);
            request1.setDate(testDate);
            request1.setTimetableSlotId(1);
            request1.setStatus("PRESENT");

            ResponseEntity<AttendanceResponse> response1 = attendanceService.recordAttendance(request1);

            assertThat(response1.getStatusCode().value()).isEqualTo(200);
            verify(attendanceRepository, times(1)).insertIgnoreDuplicate(any(), any(), any(), any(), any(), any(), any(), any());

            // Second slot: atomic upsert succeeds (returns 1)
            when(attendanceRepository.insertIgnoreDuplicate(1, 10, 2, testDate, "PRESENT", null, teacher.getId(), teacher.getId()))
                    .thenReturn(1);
            Attendance attendance2 = new Attendance();
            attendance2.setId(2);
            attendance2.setStudent(student1);
            attendance2.setTimetableSlot(slot2);
            attendance2.setDate(testDate);
            attendance2.setStatus("PRESENT");
            when(attendanceRepository.findByStudent_School_IdAndStudent_IdAndTimetableSlot_IdAndDate(1, 10, 2, testDate))
                    .thenReturn(Optional.of(attendance2));

            AttendanceRequest request2 = new AttendanceRequest();
            request2.setStudentId(10);
            request2.setDate(testDate);
            request2.setTimetableSlotId(2);
            request2.setStatus("PRESENT");

            ResponseEntity<AttendanceResponse> response2 = attendanceService.recordAttendance(request2);

            assertThat(response2.getStatusCode().value()).isEqualTo(200);
            verify(attendanceRepository, times(2)).insertIgnoreDuplicate(any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("ATT-02-02: Same student, same date, same slot — duplicate must be rejected")
    void recordAttendance_sameStudentSameDateSameSlot_rejected() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(500);
            tenantContext.when(TenantContext::schoolId).thenReturn(1);

            when(studentRepository.findById(10)).thenReturn(Optional.of(student1));
            when(timetableSlotRepository.findByIdAndSchool_Id(1, 1)).thenReturn(Optional.of(slot1));
            when(userService.findByEmployee_Id()).thenReturn(Optional.of(teacher));

            // Atomic upsert returns 0 (duplicate detected at database level)
            when(attendanceRepository.insertIgnoreDuplicate(1, 10, 1, testDate, "PRESENT", null, teacher.getId(), teacher.getId()))
                    .thenReturn(0);

            AttendanceRequest request = new AttendanceRequest();
            request.setStudentId(10);
            request.setDate(testDate);
            request.setTimetableSlotId(1);
            request.setStatus("PRESENT");

            ResponseEntity<AttendanceResponse> response = attendanceService.recordAttendance(request);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            verify(attendanceRepository, times(1)).insertIgnoreDuplicate(any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("ATT-02-03: Different students, same date and slot — both records must be saved")
    void recordAttendance_differentStudentsSameDateAndSlot_bothSaved() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(500);
            tenantContext.when(TenantContext::schoolId).thenReturn(1);

            when(studentRepository.findById(10)).thenReturn(Optional.of(student1));
            when(studentRepository.findById(20)).thenReturn(Optional.of(student2));
            when(timetableSlotRepository.findByIdAndSchool_Id(1, 1)).thenReturn(Optional.of(slot1));
            when(userService.findByEmployee_Id()).thenReturn(Optional.of(teacher));

            // Student 1: atomic upsert succeeds (returns 1)
            when(attendanceRepository.insertIgnoreDuplicate(1, 10, 1, testDate, "PRESENT", null, teacher.getId(), teacher.getId()))
                    .thenReturn(1);
            Attendance attendance1 = new Attendance();
            attendance1.setId(1);
            attendance1.setStudent(student1);
            attendance1.setTimetableSlot(slot1);
            attendance1.setDate(testDate);
            attendance1.setStatus("PRESENT");
            when(attendanceRepository.findByStudent_School_IdAndStudent_IdAndTimetableSlot_IdAndDate(1, 10, 1, testDate))
                    .thenReturn(Optional.of(attendance1));

            AttendanceRequest request1 = new AttendanceRequest();
            request1.setStudentId(10);
            request1.setDate(testDate);
            request1.setTimetableSlotId(1);
            request1.setStatus("PRESENT");

            ResponseEntity<AttendanceResponse> response1 = attendanceService.recordAttendance(request1);
            assertThat(response1.getStatusCode().value()).isEqualTo(200);

            // Student 2: atomic upsert succeeds (returns 1)
            when(attendanceRepository.insertIgnoreDuplicate(1, 20, 1, testDate, "PRESENT", null, teacher.getId(), teacher.getId()))
                    .thenReturn(1);
            Attendance attendance2 = new Attendance();
            attendance2.setId(2);
            attendance2.setStudent(student2);
            attendance2.setTimetableSlot(slot1);
            attendance2.setDate(testDate);
            attendance2.setStatus("PRESENT");
            when(attendanceRepository.findByStudent_School_IdAndStudent_IdAndTimetableSlot_IdAndDate(1, 20, 1, testDate))
                    .thenReturn(Optional.of(attendance2));

            AttendanceRequest request2 = new AttendanceRequest();
            request2.setStudentId(20);
            request2.setDate(testDate);
            request2.setTimetableSlotId(1);
            request2.setStatus("PRESENT");

            ResponseEntity<AttendanceResponse> response2 = attendanceService.recordAttendance(request2);
            assertThat(response2.getStatusCode().value()).isEqualTo(200);

            verify(attendanceRepository, times(2)).insertIgnoreDuplicate(any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("ATT-02-04: Different schools — tenant isolation maintained")
    void recordAttendance_differentSchools_tenantIsolation() {
        School school2 = new School();
        school2.setId(2);

        Student studentInSchool2 = new Student();
        studentInSchool2.setId(30);
        studentInSchool2.setSchool(school2);

        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            // Caller from school 1
            tenantContext.when(TenantContext::employeeId).thenReturn(500);
            tenantContext.when(TenantContext::schoolId).thenReturn(1);

            // Student from school 2 cannot be found by school 1's query
            when(studentRepository.findById(30)).thenReturn(Optional.empty());

            AttendanceRequest request = new AttendanceRequest();
            request.setStudentId(30);
            request.setDate(testDate);
            request.setTimetableSlotId(1);
            request.setStatus("PRESENT");

            assertThatThrownBy(() -> attendanceService.recordAttendance(request))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessage("Student not found");

            verify(attendanceRepository, never()).save(any(Attendance.class));
        }
    }

    @Test
    @DisplayName("ATT-02-05: Bulk request containing mixed valid and duplicate entries")
    void recordBulkAttendance_mixedValidAndDuplicates_onlyValidSaved() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(500);
            tenantContext.when(TenantContext::schoolId).thenReturn(1);

            when(timetableSlotRepository.findByIdAndSchool_Id(1, 1)).thenReturn(Optional.of(slot1));
            when(studentRepository.findById(10)).thenReturn(Optional.of(student1));
            when(studentRepository.findById(20)).thenReturn(Optional.of(student2));
            when(schoolRepository.getReferenceById(1)).thenReturn(school);
            when(userService.findByEmployee_Id()).thenReturn(Optional.of(teacher));

            // Student 10: insertIgnoreDuplicate returns 1 (inserted)
            when(attendanceRepository.insertIgnoreDuplicate(eq(1), eq(10), eq(1), eq(testDate), anyString(), any(), anyInt(), anyInt()))
                    .thenReturn(1);
            // Student 20: insertIgnoreDuplicate returns 0 (duplicate)
            when(attendanceRepository.insertIgnoreDuplicate(eq(1), eq(20), eq(1), eq(testDate), anyString(), any(), anyInt(), anyInt()))
                    .thenReturn(0);

            BulkAttendanceRequest request = new BulkAttendanceRequest();
            request.setTimetableSlotId(1);
            request.setDate(testDate);

            AttendanceItem item1 = new AttendanceItem();
            item1.setStudentId(10);
            item1.setStatus("PRESENT");

            AttendanceItem item2 = new AttendanceItem();
            item2.setStudentId(20);
            item2.setStatus("PRESENT");

            request.setAttendance(List.of(item1, item2));

            ResponseEntity<?> response = attendanceService.recordBulkAttendance(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            // insertIgnoreDuplicate called for both students
            verify(attendanceRepository, times(2)).insertIgnoreDuplicate(anyInt(), anyInt(), anyInt(), any(), anyString(), any(), anyInt(), anyInt());
            // save() should never be called in bulk path
            verify(attendanceRepository, never()).save(any(Attendance.class));
        }
    }

    @Test
    @DisplayName("ATT-02-06: Retry of an identical bulk request — idempotent")
    void recordBulkAttendance_retryIdenticalRequest_idempotent() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(500);
            tenantContext.when(TenantContext::schoolId).thenReturn(1);

            when(timetableSlotRepository.findByIdAndSchool_Id(1, 1)).thenReturn(Optional.of(slot1));
            when(studentRepository.findById(10)).thenReturn(Optional.of(student1));
            when(schoolRepository.getReferenceById(1)).thenReturn(school);
            when(userService.findByEmployee_Id()).thenReturn(Optional.of(teacher));

            // First call inserts (returns 1), retry finds duplicate (returns 0)
            when(attendanceRepository.insertIgnoreDuplicate(eq(1), eq(10), eq(1), eq(testDate), anyString(), any(), anyInt(), anyInt()))
                    .thenReturn(1).thenReturn(0);

            BulkAttendanceRequest request = new BulkAttendanceRequest();
            request.setTimetableSlotId(1);
            request.setDate(testDate);

            AttendanceItem item = new AttendanceItem();
            item.setStudentId(10);
            item.setStatus("PRESENT");
            request.setAttendance(List.of(item));

            ResponseEntity<?> response1 = attendanceService.recordBulkAttendance(request);
            assertThat(response1.getStatusCode().value()).isEqualTo(200);

            // Second attempt (retry): insertIgnoreDuplicate returns 0 (duplicate)
            ResponseEntity<?> response2 = attendanceService.recordBulkAttendance(request);
            assertThat(response2.getStatusCode().value()).isEqualTo(200);

            // insertIgnoreDuplicate called once per bulk request (twice total)
            verify(attendanceRepository, times(2)).insertIgnoreDuplicate(anyInt(), anyInt(), anyInt(), any(), anyString(), any(), anyInt(), anyInt());
            // save() should never be called in bulk path
            verify(attendanceRepository, never()).save(any(Attendance.class));
        }
    }

    @Test
    @DisplayName("ATT-02-07: Transaction rollback after an invalid entry")
    void recordBulkAttendance_invalidEntry_transactionRollsBack() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(500);
            tenantContext.when(TenantContext::schoolId).thenReturn(1);

            when(timetableSlotRepository.findByIdAndSchool_Id(1, 1)).thenReturn(Optional.of(slot1));
            when(schoolRepository.getReferenceById(1)).thenReturn(school);
            when(studentRepository.findById(10)).thenReturn(Optional.of(student1));
            // Student 99 does not exist — validation loop throws before any writes
            when(studentRepository.findById(99)).thenReturn(Optional.empty());

            BulkAttendanceRequest request = new BulkAttendanceRequest();
            request.setTimetableSlotId(1);
            request.setDate(testDate);

            AttendanceItem validItem = new AttendanceItem();
            validItem.setStudentId(10);
            validItem.setStatus("PRESENT");

            AttendanceItem invalidItem = new AttendanceItem();
            invalidItem.setStudentId(99);
            invalidItem.setStatus("PRESENT");

            request.setAttendance(List.of(validItem, invalidItem));

            assertThatThrownBy(() -> attendanceService.recordBulkAttendance(request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Student not found");

            // ATT-06 fix: validate-all-before-write ensures the exception is thrown
            // during the validation pass, before any write calls occur.
            verify(attendanceRepository, never()).save(any(Attendance.class));
            verify(attendanceRepository, never()).insertIgnoreDuplicate(anyInt(), anyInt(), anyInt(), any(), any(), any(), anyInt(), anyInt());
        }
    }

    @Test
    @DisplayName("ATT-02-08: Existing attendance records remain readable")
    void getAttendanceForStudent_existingRecords_readable() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::schoolId).thenReturn(1);

            Attendance attendance1 = new Attendance();
            attendance1.setId(1);
            attendance1.setStudent(student1);
            attendance1.setTimetableSlot(slot1);
            attendance1.setDate(testDate);
            attendance1.setStatus("PRESENT");

            Attendance attendance2 = new Attendance();
            attendance2.setId(2);
            attendance2.setStudent(student1);
            attendance2.setTimetableSlot(slot2);
            attendance2.setDate(testDate);
            attendance2.setStatus("PRESENT");

            when(attendanceRepository.findByStudent_School_IdAndStudent_IdAndDateBetween(1, 10, testDate, testDate))
                    .thenReturn(List.of(attendance1, attendance2));

            List<AttendanceResponse> responses = attendanceService.getAttendanceForStudent(10, testDate, testDate);

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getId()).isEqualTo(1);
            assertThat(responses.get(1).getId()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("ATT-02-09: Bulk attendance with multiple slots on same date for same student")
    void recordBulkAttendance_multipleSlotsSameDate_allSaved() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(500);
            tenantContext.when(TenantContext::schoolId).thenReturn(1);

            when(timetableSlotRepository.findByIdAndSchool_Id(1, 1)).thenReturn(Optional.of(slot1));
            when(timetableSlotRepository.findByIdAndSchool_Id(2, 1)).thenReturn(Optional.of(slot2));
            when(studentRepository.findById(10)).thenReturn(Optional.of(student1));
            when(schoolRepository.getReferenceById(1)).thenReturn(school);
            when(userService.findByEmployee_Id()).thenReturn(Optional.of(teacher));

            // First bulk: slot 1 — insertIgnoreDuplicate returns 1 (inserted)
            when(attendanceRepository.insertIgnoreDuplicate(eq(1), eq(10), eq(1), eq(testDate), anyString(), any(), anyInt(), anyInt()))
                    .thenReturn(1);

            BulkAttendanceRequest request1 = new BulkAttendanceRequest();
            request1.setTimetableSlotId(1);
            request1.setDate(testDate);
            AttendanceItem item1 = new AttendanceItem();
            item1.setStudentId(10);
            item1.setStatus("PRESENT");
            request1.setAttendance(List.of(item1));

            ResponseEntity<?> response1 = attendanceService.recordBulkAttendance(request1);
            assertThat(response1.getStatusCode().value()).isEqualTo(200);

            // Second bulk: slot 2 (same student, same date, different slot)
            when(attendanceRepository.insertIgnoreDuplicate(eq(1), eq(10), eq(2), eq(testDate), anyString(), any(), anyInt(), anyInt()))
                    .thenReturn(1);

            BulkAttendanceRequest request2 = new BulkAttendanceRequest();
            request2.setTimetableSlotId(2);
            request2.setDate(testDate);
            AttendanceItem item2 = new AttendanceItem();
            item2.setStudentId(10);
            item2.setStatus("PRESENT");
            request2.setAttendance(List.of(item2));

            ResponseEntity<?> response2 = attendanceService.recordBulkAttendance(request2);
            assertThat(response2.getStatusCode().value()).isEqualTo(200);

            // Both bulk calls should each invoke insertIgnoreDuplicate once
            verify(attendanceRepository, times(2)).insertIgnoreDuplicate(anyInt(), anyInt(), anyInt(), any(), anyString(), any(), anyInt(), anyInt());
            // save() should never be called in bulk path
            verify(attendanceRepository, never()).save(any(Attendance.class));
        }
    }
}
