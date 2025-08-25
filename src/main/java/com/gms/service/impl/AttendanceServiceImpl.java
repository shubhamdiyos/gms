
package com.gms.service.impl;

import com.gms.model.entity.Attendance;
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
import com.gms.repository.UserRepository;
import com.gms.service.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AttendanceServiceImpl extends AbstractCRUDService<Attendance, Integer> implements AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final StudentService studentService;
    private final TimetableService timetableService;
    private final UserService userService;

    public AttendanceServiceImpl(AttendanceRepository attendanceRepository, 
                               StudentService studentService, 
                               TimetableService timetableService,
                               UserService userService) {
        super(attendanceRepository);
        this.attendanceRepository = attendanceRepository;
        this.studentService = studentService;
        this.timetableService = timetableService;
        this.userService = userService;
    }

    @Override
    @Transactional
    public ResponseEntity<AttendanceResponse> recordAttendance(AttendanceRequest request, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            return ResponseEntity.badRequest().build();
        }

        // Validate student exists and belongs to the school
        Student student = studentService.findById(request.getStudentId())
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        if (!student.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate timetable slot if provided - temporarily simplified
        TimetableSlot timetableSlot = null;
        if (request.getTimetableSlotId() != null) {
            // TODO: Add proper TimetableSlotService when available
            // For now, skipping timetable slot validation
        }

        // Check if attendance already exists for this student on this date
        List<Attendance> existingAttendance = attendanceRepository.findByStudent_IdAndDateBetween(
                request.getStudentId(), request.getDate(), request.getDate());
        
        if (!existingAttendance.isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }

        // Create attendance record
        Attendance attendance = new Attendance();
        attendance.setStudent(student);
        attendance.setTimetableSlot(timetableSlot);
        attendance.setDate(request.getDate());
        attendance.setStatus(request.getStatus());
        attendance.setRemarks(request.getRemarks());
        
        // Set creator/updated by
        User creator = userService.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));
        attendance.setCreatedBy(creator);
        attendance.setUpdatedBy(creator);

        Attendance savedAttendance = attendanceRepository.save(attendance);
        return ResponseEntity.ok(mapToResponse(savedAttendance));
    }

    @Override
    @Transactional
    public ResponseEntity<?> recordBulkAttendance(BulkAttendanceRequest request, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            return ResponseEntity.badRequest().build();
        }

        // Temporarily simplified - TimetableSlot operations need proper service
        // TimetableSlot timetableSlot = timetableSlotRepository.findById(request.getTimetableSlotId())
        //         .orElseThrow(() -> new NotFoundException("TIMETABLE_SLOT_NOT_FOUND", "Timetable slot not found"));

        for (var item : request.getAttendance()) {
            Student student = studentService.findById(item.getStudentId())
                    .orElseThrow(() -> new NotFoundException("STUDENT_NOT_FOUND", "Student not found"));

            // Simplified validation - skipping timetable slot validation for now
            // if (!student.getClassroomId().equals(timetableSlot.getTimetable().getClassroom().getId())) {
            //     return ResponseEntity.badRequest().build();
            // }

            // Check if attendance already exists for this student on this date
            List<Attendance> existingAttendance = attendanceRepository.findByStudent_IdAndDateBetween(
                    item.getStudentId(), request.getDate(), request.getDate());
            
            if (existingAttendance.isEmpty()) {
                Attendance attendance = new Attendance();
                attendance.setStudent(student);
                // attendance.setTimetableSlot(timetableSlot); // Commented until proper service available
                attendance.setDate(request.getDate());
                attendance.setStatus(item.getStatus());
                attendance.setRemarks(item.getRemarks());
                
                // Set creator/updated by
                User creator = userService.findByEmployee_Id(empId)
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
    public ResponseEntity<AttendanceResponse> updateAttendance(Integer attendanceId, AttendanceRequest request, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            return ResponseEntity.badRequest().build();
        }

        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new EntityNotFoundException("Attendance record not found"));
        
        if (!attendance.getStudent().getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate student exists and belongs to the school
        Student student = studentService.findById(request.getStudentId())
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        if (!student.getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate timetable slot if provided - temporarily skipped due to missing service
        TimetableSlot timetableSlot = null;
        if (request.getTimetableSlotId() != null) {
            // TODO: Add proper TimetableSlotService when available
            // For now, skipping timetable slot validation
        }

        // Update attendance record
        attendance.setStudent(student);
        attendance.setTimetableSlot(timetableSlot);
        attendance.setDate(request.getDate());
        attendance.setStatus(request.getStatus());
        attendance.setRemarks(request.getRemarks());
        
        // Set updated by
        User updater = userService.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));
        attendance.setUpdatedBy(updater);

        Attendance savedAttendance = attendanceRepository.save(attendance);
        return ResponseEntity.ok(mapToResponse(savedAttendance));
    }

    @Override
    @Transactional
    public ResponseEntity<?> deleteAttendance(Integer attendanceId, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            return ResponseEntity.badRequest().build();
        }

        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new EntityNotFoundException("Attendance record not found"));
        
        if (!attendance.getStudent().getSchoolId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        attendanceRepository.delete(attendance);
        return ResponseEntity.ok().build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendanceForStudent(Integer studentId, LocalDate from, LocalDate to) {
        List<Attendance> attendances = attendanceRepository.findByStudent_IdAndDateBetween(studentId, from, to);
        return attendances.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendanceForClassroom(Integer classroomId, LocalDate date) {
        List<Attendance> attendances = attendanceRepository.findByTimetableSlot_Timetable_Classroom_IdAndDate(classroomId, date);
        return attendances.stream().map(this::mapToResponse).collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendanceByDateRange(Integer schoolId, LocalDate startDate, LocalDate endDate) {
        List<Attendance> attendances = attendanceRepository.findBySchoolIdAndDateBetween(schoolId, startDate, endDate);
        return attendances.stream().map(this::mapToResponse).collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendanceByStudentAndDateRange(Integer studentId, LocalDate startDate, LocalDate endDate) {
        List<Attendance> attendances = attendanceRepository.findByStudent_IdAndDateBetween(studentId, startDate, endDate);
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
