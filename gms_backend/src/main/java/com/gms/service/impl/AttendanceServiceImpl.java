
package com.gms.service.impl;

import com.gms.model.entity.Attendance;
import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.entity.TimetableSlot;
import com.gms.model.entity.User;
import com.gms.exception.NotFoundException;
import com.gms.model.request.AttendanceRequest;
import com.gms.model.request.BulkAttendanceRequest;
import com.gms.model.response.AttendanceResponse;
import com.gms.model.response.StudentResponse;
import com.gms.model.response.TimetableSlotResponse;
import com.gms.repository.AttendanceRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.TimetableSlotRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final SchoolRepository schoolRepository;
    private final TimetableSlotRepository timetableSlotRepository;
    private final StudentService studentService;
    private final TimetableService timetableService;
    private final UserService userService;

    public AttendanceServiceImpl(AttendanceRepository attendanceRepository,
                               SchoolRepository schoolRepository,
                               TimetableSlotRepository timetableSlotRepository,
                               StudentService studentService,
                               TimetableService timetableService,
                               UserService userService) {
        this.attendanceRepository = attendanceRepository;
        this.schoolRepository = schoolRepository;
        this.timetableSlotRepository = timetableSlotRepository;
        this.studentService = studentService;
        this.timetableService = timetableService;
        this.userService = userService;
    }

    @Override
    @Transactional
    public ResponseEntity<AttendanceResponse> recordAttendance(AttendanceRequest request) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            return ResponseEntity.badRequest().build();
        }

        // The load is scoped to the caller's school, so a foreign id yields nothing
        Student student = studentService.findById(request.getStudentId())
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));

        // The DTO calls this field optional; the schema does not — attendances.timetable_slot_id is
        // NOT NULL, so the code that read the id and then dropped it could never write the row.
        if (request.getTimetableSlotId() == null) {
            return ResponseEntity.badRequest().build();
        }
        TimetableSlot timetableSlot = timetableSlotRepository
                .findByIdAndSchool_Id(request.getTimetableSlotId(), TenantContext.schoolId())
                .orElseThrow(() -> new NotFoundException("TIMETABLE_SLOT_NOT_FOUND",
                        "Timetable slot not found for the current school"));

        // Check if attendance already exists for this student on this date
        List<Attendance> existingAttendance = attendanceRepository.findByStudent_School_IdAndStudent_IdAndDateBetween(TenantContext.schoolId(),
                request.getStudentId(), request.getDate(), request.getDate());
        
        if (!existingAttendance.isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }

        // Create attendance record
        Attendance attendance = new Attendance();
        attendance.setSchool(schoolRepository.getReferenceById(TenantContext.schoolId()));
        attendance.setStudent(student);
        attendance.setTimetableSlot(timetableSlot);
        attendance.setDate(request.getDate());
        attendance.setStatus(request.getStatus());
        attendance.setRemarks(request.getRemarks());
        
        // Set creator/updated by
        User creator = userService.findByEmployee_Id()
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));
        attendance.setCreatedBy(creator);
        attendance.setUpdatedBy(creator);

        Attendance savedAttendance = attendanceRepository.save(attendance);
        return ResponseEntity.ok(mapToResponse(savedAttendance));
    }

    @Override
    @Transactional
    public ResponseEntity<?> recordBulkAttendance(BulkAttendanceRequest request) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            return ResponseEntity.badRequest().build();
        }

        // One slot for the whole class register, read with the tenant in the query.
        TimetableSlot timetableSlot = timetableSlotRepository
                .findByIdAndSchool_Id(request.getTimetableSlotId(), TenantContext.schoolId())
                .orElseThrow(() -> new NotFoundException("TIMETABLE_SLOT_NOT_FOUND",
                        "Timetable slot not found for the current school"));

        School school = schoolRepository.getReferenceById(TenantContext.schoolId());

        for (var item : request.getAttendance()) {
            Student student = studentService.findById(item.getStudentId())
                    .orElseThrow(() -> new NotFoundException("STUDENT_NOT_FOUND", "Student not found"));

            if (!student.getClassroomId().equals(timetableSlot.getTimetable().getClassroom().getId())) {
                return ResponseEntity.badRequest().build();
            }

            // Check if attendance already exists for this student on this date
            List<Attendance> existingAttendance = attendanceRepository.findByStudent_School_IdAndStudent_IdAndDateBetween(TenantContext.schoolId(),
                    item.getStudentId(), request.getDate(), request.getDate());
            
            if (existingAttendance.isEmpty()) {
                Attendance attendance = new Attendance();
                attendance.setSchool(school);
                attendance.setStudent(student);
                attendance.setTimetableSlot(timetableSlot);
                attendance.setDate(request.getDate());
                attendance.setStatus(item.getStatus());
                attendance.setRemarks(item.getRemarks());
                
                // Set creator/updated by
                User creator = userService.findByEmployee_Id()
                        .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));
                attendance.setCreatedBy(creator);
                attendance.setUpdatedBy(creator);
                
                attendanceRepository.save(attendance);
            }
        }
        
        return ResponseEntity.ok().build();
    }

    @Override
    @Transactional
    public ResponseEntity<AttendanceResponse> updateAttendance(Integer attendanceId, AttendanceRequest request) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new EntityNotFoundException("Attendance record not found"));

        // The load is scoped to the caller's school, so a foreign id yields nothing
        Student student = studentService.findById(request.getStudentId())
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));

        // The register already has a slot, and the column is NOT NULL, so an update that does not name
        // a new one keeps the existing one rather than writing a null over it.
        if (request.getTimetableSlotId() != null) {
            TimetableSlot timetableSlot = timetableSlotRepository
                    .findByIdAndSchool_Id(request.getTimetableSlotId(), TenantContext.schoolId())
                    .orElseThrow(() -> new NotFoundException("TIMETABLE_SLOT_NOT_FOUND",
                            "Timetable slot not found for the current school"));
            attendance.setTimetableSlot(timetableSlot);
        }

        // Update attendance record
        attendance.setStudent(student);
        attendance.setDate(request.getDate());
        attendance.setStatus(request.getStatus());
        attendance.setRemarks(request.getRemarks());
        
        // Set updated by
        User updater = userService.findByEmployee_Id()
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));
        attendance.setUpdatedBy(updater);

        Attendance savedAttendance = attendanceRepository.save(attendance);
        return ResponseEntity.ok(mapToResponse(savedAttendance));
    }

    @Override
    @Transactional
    public ResponseEntity<?> deleteAttendance(Integer attendanceId) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new EntityNotFoundException("Attendance record not found"));

        attendanceRepository.delete(attendance);
        return ResponseEntity.ok().build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendanceForStudent(Integer studentId, LocalDate from, LocalDate to) {
        List<Attendance> attendances = attendanceRepository.findByStudent_School_IdAndStudent_IdAndDateBetween(TenantContext.schoolId(), studentId, from, to);
        return attendances.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendanceForClassroom(Integer classroomId, LocalDate date) {
        List<Attendance> attendances = attendanceRepository.findByTimetableSlot_Timetable_Classroom_School_IdAndTimetableSlot_Timetable_Classroom_IdAndDate(
                TenantContext.schoolId(), classroomId, date);
        return attendances.stream().map(this::mapToResponse).collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendanceByDateRange(LocalDate startDate, LocalDate endDate) {
        List<Attendance> attendances = attendanceRepository.findBySchoolIdAndDateBetween(TenantContext.schoolId(), startDate, endDate);
        return attendances.stream().map(this::mapToResponse).collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendanceByStudentAndDateRange(Integer studentId, LocalDate startDate, LocalDate endDate) {
        List<Attendance> attendances = attendanceRepository.findByStudent_School_IdAndStudent_IdAndDateBetween(TenantContext.schoolId(), studentId, startDate, endDate);
        return attendances.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private AttendanceResponse mapToResponse(Attendance attendance) {
        StudentResponse studentResponse = new StudentResponse();
        studentResponse.setId(attendance.getStudent().getId());
        studentResponse.setFirstName(attendance.getStudent().getFirstName());
        studentResponse.setLastName(attendance.getStudent().getLastName());
        studentResponse.setEmail(attendance.getStudent().getEmail());

        TimetableSlotResponse timetableSlotResponse = null;
        if (attendance.getTimetableSlot() != null) {
            timetableSlotResponse = mapToSlotResponse(attendance.getTimetableSlot());
        }

        return new AttendanceResponse(
                attendance.getId(),
                studentResponse,
                timetableSlotResponse,
                attendance.getStatus(),
                attendance.getDate(),
                attendance.getRemarks()
        );
    }
    
    public TimetableSlotResponse mapToSlotResponse(TimetableSlot slot) {
        return new TimetableSlotResponse(
                slot.getDayOfWeek(),
                slot.getPeriod(),
                null // In a complete implementation, we would map the assignment
        );
    }
}
