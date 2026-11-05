package com.gms.service.impl;

import com.gms.exception.NotFoundException;
import com.gms.model.entity.*;
import com.gms.model.request.AttendanceItem;
import com.gms.model.request.BulkAttendanceRequest;
import com.gms.model.response.BulkAttendanceResponse;
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
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ATT-01 verification: Bulk attendance response contract.
 * 
 * Verifies that the bulk attendance endpoint returns a detailed summary with
 * created/skipped counts and student IDs, allowing clients to reconcile the response.
 * 
 * Test infrastructure: Unit tests with mocked repositories (not real database).
 */
@ExtendWith(MockitoExtension.class)
class BulkAttendanceResponseContractTest {

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
    private Classroom classroom;
    private Timetable timetable;
    private TimetableSlot slot;
    private Student student1;
    private Student student2;
    private Student student3;
    private User teacher;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        school = new School();
        school.setId(1);

        classroom = new Classroom();
        classroom.setId(100);
        classroom.setSchool(school);

        timetable = new Timetable();
        timetable.setClassroom(classroom);

        slot = new TimetableSlot();
        slot.setId(1);
        slot.setTimetable(timetable);
        slot.setDayOfWeek("MONDAY");
        slot.setPeriod(1);

        student1 = new Student();
        student1.setId(10);
        student1.setSchool(school);
        student1.setClassroom(classroom);

        student2 = new Student();
        student2.setId(20);
        student2.setSchool(school);
        student2.setClassroom(classroom);

        student3 = new Student();
        student3.setId(30);
        student3.setSchool(school);
        student3.setClassroom(classroom);

        teacher = new User();
        teacher.setId(1);
        teacher.setUsername("teacher1");

        // A Monday, matching the MONDAY slot created in setUp.
        testDate = LocalDate.of(2026, 11, 2);
    }

    private BulkAttendanceRequest createBulkRequest(List<Integer> studentIds) {
        BulkAttendanceRequest request = new BulkAttendanceRequest();
        request.setTimetableSlotId(slot.getId());
        request.setDate(testDate);
        
        List<AttendanceItem> items = new ArrayList<>();
        for (Integer studentId : studentIds) {
            AttendanceItem item = new AttendanceItem();
            item.setStudentId(studentId);
            item.setStatus("PRESENT");
            item.setRemarks("Test");
            items.add(item);
        }
        request.setAttendance(items);
        
        return request;
    }

    @Test
    @DisplayName("ATT-01 Scenario 1: All students are new — all created, zero skipped")
    void allStudentsNew_allCreatedZeroSkipped() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacher.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school.getId());

            BulkAttendanceRequest request = createBulkRequest(List.of(10, 20, 30));

            when(timetableSlotRepository.findByIdAndSchool_Id(slot.getId(), school.getId()))
                    .thenReturn(Optional.of(slot));
            when(schoolRepository.getReferenceById(school.getId())).thenReturn(school);
            when(studentRepository.findById(10)).thenReturn(Optional.of(student1));
            when(studentRepository.findById(20)).thenReturn(Optional.of(student2));
            when(studentRepository.findById(30)).thenReturn(Optional.of(student3));
            when(userService.findByEmployee_Id()).thenReturn(Optional.of(teacher));
            
            // All inserts succeed (no duplicates)
            when(attendanceRepository.insertIgnoreDuplicate(eq(school.getId()), anyInt(), eq(slot.getId()), eq(testDate), anyString(), any(), anyInt(), anyInt())).thenReturn(1);

            ResponseEntity<?> response = attendanceService.recordBulkAttendance(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isInstanceOf(BulkAttendanceResponse.class);
            
            BulkAttendanceResponse bulkResponse = (BulkAttendanceResponse) response.getBody();
            assertThat(bulkResponse.getTotalStudents()).isEqualTo(3);
            assertThat(bulkResponse.getCreatedCount()).isEqualTo(3);
            assertThat(bulkResponse.getSkippedCount()).isEqualTo(0);
            assertThat(bulkResponse.getSkippedStudentIds()).isEmpty();
            assertThat(bulkResponse.getMessage()).contains("3 students").contains("0 skipped");
            
            // Verify invariant: totalStudents = createdCount + skippedCount
            assertThat(bulkResponse.getTotalStudents())
                    .isEqualTo(bulkResponse.getCreatedCount() + bulkResponse.getSkippedCount());
            
            verify(attendanceRepository, times(3)).insertIgnoreDuplicate(anyInt(), anyInt(), anyInt(), any(), anyString(), any(), anyInt(), anyInt());
        }
    }

    @Test
    @DisplayName("ATT-01 Scenario 2: All students are duplicates — zero created, all skipped")
    void allStudentsDuplicates_zeroCreatedAllSkipped() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacher.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school.getId());

            BulkAttendanceRequest request = createBulkRequest(List.of(10, 20, 30));

            when(timetableSlotRepository.findByIdAndSchool_Id(slot.getId(), school.getId()))
                    .thenReturn(Optional.of(slot));
            when(schoolRepository.getReferenceById(school.getId())).thenReturn(school);
            when(studentRepository.findById(10)).thenReturn(Optional.of(student1));
            when(studentRepository.findById(20)).thenReturn(Optional.of(student2));
            when(studentRepository.findById(30)).thenReturn(Optional.of(student3));
            when(userService.findByEmployee_Id()).thenReturn(Optional.of(teacher));
            
            // All inserts return 0 (all duplicates)
            when(attendanceRepository.insertIgnoreDuplicate(eq(school.getId()), anyInt(), eq(slot.getId()), eq(testDate), anyString(), any(), anyInt(), anyInt())).thenReturn(0);

            ResponseEntity<?> response = attendanceService.recordBulkAttendance(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isInstanceOf(BulkAttendanceResponse.class);
            
            BulkAttendanceResponse bulkResponse = (BulkAttendanceResponse) response.getBody();
            assertThat(bulkResponse.getTotalStudents()).isEqualTo(3);
            assertThat(bulkResponse.getCreatedCount()).isEqualTo(0);
            assertThat(bulkResponse.getSkippedCount()).isEqualTo(3);
            assertThat(bulkResponse.getSkippedStudentIds()).containsExactlyInAnyOrder(10, 20, 30);
            assertThat(bulkResponse.getMessage()).contains("0 students").contains("3 skipped");
            
            // Verify invariant
            assertThat(bulkResponse.getTotalStudents())
                    .isEqualTo(bulkResponse.getCreatedCount() + bulkResponse.getSkippedCount());
            
            // Verify insertIgnoreDuplicate was called for all 3 students
            verify(attendanceRepository, times(3)).insertIgnoreDuplicate(anyInt(), anyInt(), anyInt(), any(), anyString(), any(), anyInt(), anyInt());
            // Verify no attendance records were saved via legacy save
            verify(attendanceRepository, never()).save(any(Attendance.class));
        }
    }

    @Test
    @DisplayName("ATT-01 Scenario 3: Mixed request — correct created and skipped counts")
    void mixedRequest_correctCreatedAndSkippedCounts() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacher.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school.getId());

            BulkAttendanceRequest request = createBulkRequest(List.of(10, 20, 30));

            when(timetableSlotRepository.findByIdAndSchool_Id(slot.getId(), school.getId()))
                    .thenReturn(Optional.of(slot));
            when(schoolRepository.getReferenceById(school.getId())).thenReturn(school);
            when(studentRepository.findById(10)).thenReturn(Optional.of(student1));
            when(studentRepository.findById(20)).thenReturn(Optional.of(student2));
            when(studentRepository.findById(30)).thenReturn(Optional.of(student3));
            when(userService.findByEmployee_Id()).thenReturn(Optional.of(teacher));
            
            // Student 10 and 30 are new (insert returns 1), student 20 is duplicate (insert returns 0)
            when(attendanceRepository.insertIgnoreDuplicate(eq(school.getId()), eq(10), eq(slot.getId()), eq(testDate), anyString(), any(), anyInt(), anyInt())).thenReturn(1);
            when(attendanceRepository.insertIgnoreDuplicate(eq(school.getId()), eq(20), eq(slot.getId()), eq(testDate), anyString(), any(), anyInt(), anyInt())).thenReturn(0);
            when(attendanceRepository.insertIgnoreDuplicate(eq(school.getId()), eq(30), eq(slot.getId()), eq(testDate), anyString(), any(), anyInt(), anyInt())).thenReturn(1);

            ResponseEntity<?> response = attendanceService.recordBulkAttendance(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            BulkAttendanceResponse bulkResponse = (BulkAttendanceResponse) response.getBody();
            
            assertThat(bulkResponse.getTotalStudents()).isEqualTo(3);
            assertThat(bulkResponse.getCreatedCount()).isEqualTo(2);
            assertThat(bulkResponse.getSkippedCount()).isEqualTo(1);
            assertThat(bulkResponse.getSkippedStudentIds()).containsExactly(20);
            
            // Verify invariant
            assertThat(bulkResponse.getTotalStudents())
                    .isEqualTo(bulkResponse.getCreatedCount() + bulkResponse.getSkippedCount());
            
            verify(attendanceRepository, times(3)).insertIgnoreDuplicate(anyInt(), anyInt(), anyInt(), any(), anyString(), any(), anyInt(), anyInt());
        }
    }

    @Test
    @DisplayName("ATT-01 Scenario 4: Skipped student IDs exactly match the skipped records")
    void skippedStudentIds_exactlyMatchSkippedRecords() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacher.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school.getId());

            BulkAttendanceRequest request = createBulkRequest(List.of(10, 20, 30));

            when(timetableSlotRepository.findByIdAndSchool_Id(slot.getId(), school.getId()))
                    .thenReturn(Optional.of(slot));
            when(schoolRepository.getReferenceById(school.getId())).thenReturn(school);
            when(studentRepository.findById(10)).thenReturn(Optional.of(student1));
            when(studentRepository.findById(20)).thenReturn(Optional.of(student2));
            when(studentRepository.findById(30)).thenReturn(Optional.of(student3));
            
            // Student 10 is new (returns 1), students 20 and 30 are duplicates (return 0)
            when(attendanceRepository.insertIgnoreDuplicate(eq(school.getId()), eq(10), eq(slot.getId()), eq(testDate), anyString(), any(), anyInt(), anyInt())).thenReturn(1);
            when(attendanceRepository.insertIgnoreDuplicate(eq(school.getId()), eq(20), eq(slot.getId()), eq(testDate), anyString(), any(), anyInt(), anyInt())).thenReturn(0);
            when(attendanceRepository.insertIgnoreDuplicate(eq(school.getId()), eq(30), eq(slot.getId()), eq(testDate), anyString(), any(), anyInt(), anyInt())).thenReturn(0);
            
            when(userService.findByEmployee_Id()).thenReturn(Optional.of(teacher));

            ResponseEntity<?> response = attendanceService.recordBulkAttendance(request);

            BulkAttendanceResponse bulkResponse = (BulkAttendanceResponse) response.getBody();
            
            // Verify skipped IDs exactly match
            assertThat(bulkResponse.getSkippedStudentIds())
                    .containsExactlyInAnyOrder(20, 30)
                    .hasSize(2);
            
            assertThat(bulkResponse.getSkippedCount()).isEqualTo(2);
            
            verify(attendanceRepository, times(3)).insertIgnoreDuplicate(anyInt(), anyInt(), anyInt(), any(), anyString(), any(), anyInt(), anyInt());
        }
    }

    @Test
    @DisplayName("ATT-01 Scenario 5: Empty request — behavior is explicit")
    void emptyRequest_behaviorIsExplicit() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacher.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school.getId());

            BulkAttendanceRequest request = createBulkRequest(List.of()); // Empty list

            when(timetableSlotRepository.findByIdAndSchool_Id(slot.getId(), school.getId()))
                    .thenReturn(Optional.of(slot));
            when(schoolRepository.getReferenceById(school.getId())).thenReturn(school);

            ResponseEntity<?> response = attendanceService.recordBulkAttendance(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            BulkAttendanceResponse bulkResponse = (BulkAttendanceResponse) response.getBody();
            
            assertThat(bulkResponse.getTotalStudents()).isEqualTo(0);
            assertThat(bulkResponse.getCreatedCount()).isEqualTo(0);
            assertThat(bulkResponse.getSkippedCount()).isEqualTo(0);
            assertThat(bulkResponse.getSkippedStudentIds()).isEmpty();
            
            // Verify invariant
            assertThat(bulkResponse.getTotalStudents())
                    .isEqualTo(bulkResponse.getCreatedCount() + bulkResponse.getSkippedCount());
            
            verify(attendanceRepository, never()).save(any(Attendance.class));
            verify(attendanceRepository, never()).insertIgnoreDuplicate(anyInt(), anyInt(), anyInt(), any(), any(), any(), anyInt(), anyInt());
        }
    }

    @Test
    @DisplayName("ATT-01 Scenario 6: Duplicate IDs inside one request — deterministic behavior")
    void duplicateIdsInsideOneRequest_deterministicBehavior() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacher.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school.getId());

            // Request with duplicate student ID (10 appears twice)
            BulkAttendanceRequest request = createBulkRequest(List.of(10, 10, 20));

            when(timetableSlotRepository.findByIdAndSchool_Id(slot.getId(), school.getId()))
                    .thenReturn(Optional.of(slot));
            when(schoolRepository.getReferenceById(school.getId())).thenReturn(school);
            when(studentRepository.findById(10)).thenReturn(Optional.of(student1));
            when(studentRepository.findById(20)).thenReturn(Optional.of(student2));
            when(userService.findByEmployee_Id()).thenReturn(Optional.of(teacher));
            
            // First occurrence of student 10 is new (returns 1), second is duplicate (returns 0)
            // Student 20 is new (returns 1)
            when(attendanceRepository.insertIgnoreDuplicate(eq(school.getId()), eq(10), eq(slot.getId()), eq(testDate), anyString(), any(), anyInt(), anyInt()))
                    .thenReturn(1)
                    .thenReturn(0);
            when(attendanceRepository.insertIgnoreDuplicate(eq(school.getId()), eq(20), eq(slot.getId()), eq(testDate), anyString(), any(), anyInt(), anyInt()))
                    .thenReturn(1);

            ResponseEntity<?> response = attendanceService.recordBulkAttendance(request);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            BulkAttendanceResponse bulkResponse = (BulkAttendanceResponse) response.getBody();
            
            // Total is 3 (the request size), created is 2 (student 10 first time, student 20),
            // skipped is 1 (student 10 second time)
            assertThat(bulkResponse.getTotalStudents()).isEqualTo(3);
            assertThat(bulkResponse.getCreatedCount()).isEqualTo(2);
            assertThat(bulkResponse.getSkippedCount()).isEqualTo(1);
            assertThat(bulkResponse.getSkippedStudentIds()).containsExactly(10);
            
            // Verify invariant
            assertThat(bulkResponse.getTotalStudents())
                    .isEqualTo(bulkResponse.getCreatedCount() + bulkResponse.getSkippedCount());
            
            // Verify insertIgnoreDuplicate was called 3 times (once per request item)
            verify(attendanceRepository, times(3)).insertIgnoreDuplicate(anyInt(), anyInt(), anyInt(), any(), anyString(), any(), anyInt(), anyInt());
        }
    }

    @Test
    @DisplayName("ATT-01 Scenario 7: Response message and counts remain consistent")
    void responseMessageAndCounts_consistent() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::employeeId).thenReturn(teacher.getId());
            tenantContext.when(TenantContext::schoolId).thenReturn(school.getId());

            BulkAttendanceRequest request = createBulkRequest(List.of(10, 20));

            when(timetableSlotRepository.findByIdAndSchool_Id(slot.getId(), school.getId()))
                    .thenReturn(Optional.of(slot));
            when(schoolRepository.getReferenceById(school.getId())).thenReturn(school);
            when(studentRepository.findById(10)).thenReturn(Optional.of(student1));
            when(studentRepository.findById(20)).thenReturn(Optional.of(student2));
            when(userService.findByEmployee_Id()).thenReturn(Optional.of(teacher));
            
            // All inserts succeed
            when(attendanceRepository.insertIgnoreDuplicate(eq(school.getId()), anyInt(), eq(slot.getId()), eq(testDate), anyString(), any(), anyInt(), anyInt())).thenReturn(1);

            ResponseEntity<?> response = attendanceService.recordBulkAttendance(request);

            BulkAttendanceResponse bulkResponse = (BulkAttendanceResponse) response.getBody();
            
            // Verify message contains the correct counts
            assertThat(bulkResponse.getMessage())
                    .contains(String.valueOf(bulkResponse.getCreatedCount()))
                    .contains(String.valueOf(bulkResponse.getSkippedCount()));
            
            // Verify message format is consistent
            assertThat(bulkResponse.getMessage())
                    .matches("Attendance recorded for \\d+ students, \\d+ skipped \\(already exists\\)");
        }
    }

}
