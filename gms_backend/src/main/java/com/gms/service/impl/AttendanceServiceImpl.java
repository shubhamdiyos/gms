
package com.gms.service.impl;

import com.gms.model.entity.Attendance;
import com.gms.model.entity.School;
import com.gms.model.entity.Student;
import com.gms.model.entity.TimetableSlot;
import com.gms.model.entity.User;
import com.gms.exception.NotFoundException;
import com.gms.model.request.AttendanceRequest;
import com.gms.model.request.BulkAttendanceRequest;
import com.gms.model.entity.TeacherAssignment;
import com.gms.model.response.AttendanceResponse;
import com.gms.model.response.ClassroomResponse;
import com.gms.model.response.EmployeeResponse;
import com.gms.model.response.StudentResponse;
import com.gms.model.response.SubjectResponse;
import com.gms.model.response.TeacherAssignmentResponse;
import com.gms.model.response.TimetableSlotResponse;
import com.gms.repository.AttendanceRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentRepository;
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
    private final StudentRepository studentRepository;
    private final TimetableService timetableService;
    private final UserService userService;

    public AttendanceServiceImpl(AttendanceRepository attendanceRepository,
                               SchoolRepository schoolRepository,
                               TimetableSlotRepository timetableSlotRepository,
                               StudentRepository studentRepository,
                               TimetableService timetableService,
                               UserService userService) {
        this.attendanceRepository = attendanceRepository;
        this.schoolRepository = schoolRepository;
        this.timetableSlotRepository = timetableSlotRepository;
        this.studentRepository = studentRepository;
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
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));

        // Daily register (V13): no timetable slot means one mark for the whole day. The
        // partial unique index is the duplicate guard, via the same atomic-insert pattern
        // as the period path below. (On the period path below, a missing date is rejected
        // by requireSlotScheduledOn AFTER the slot loads — the guard tests pin that order,
        // so the check lives inside this branch rather than ahead of both.)
        if (request.getTimetableSlotId() == null) {
            if (request.getDate() == null) {
                throw new IllegalArgumentException("Attendance date is required");
            }
            User creator = userService.findByEmployee_Id()
                    .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));
            int dailyInserted = attendanceRepository.insertDailyIgnoreDuplicate(
                    TenantContext.schoolId(),
                    request.getStudentId(),
                    request.getDate(),
                    request.getStatus(),
                    request.getRemarks(),
                    creator.getId(),
                    creator.getId()
            );

            if (dailyInserted == 0) {
                return ResponseEntity.badRequest().build();
            }

            Attendance savedDaily = attendanceRepository
                    .findByStudent_School_IdAndStudent_IdAndTimetableSlotIsNullAndDate(
                            TenantContext.schoolId(),
                            request.getStudentId(),
                            request.getDate()
                    )
                    .orElseThrow(() -> new EntityNotFoundException("Attendance record not found after insert"));

            return ResponseEntity.ok(mapToResponse(savedDaily));
        }

        TimetableSlot timetableSlot = timetableSlotRepository
                .findByIdAndSchool_Id(request.getTimetableSlotId(), TenantContext.schoolId())
                .orElseThrow(() -> new NotFoundException("TIMETABLE_SLOT_NOT_FOUND",
                        "Timetable slot not found for the current school"));
        requireSlotScheduledOn(timetableSlot, request.getDate());

        // Fetch creator user for audit fields — after the slot checks, as before: a bad slot
        // or a wrong-day slot must surface its own error, not a missing-creator one.
        User creator = userService.findByEmployee_Id()
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Stage H: Replace check-then-act race with atomic upsert (consistent with bulk path).
        // INSERT ... ON CONFLICT DO NOTHING is atomic at the database level, eliminating the
        // race condition where concurrent requests could both pass the existsBy check.
        // Returns 1 if inserted, 0 if duplicate (constraint already exists).
        int rowsInserted = attendanceRepository.insertIgnoreDuplicate(
                TenantContext.schoolId(),
                request.getStudentId(),
                request.getTimetableSlotId(),
                request.getDate(),
                request.getStatus(),
                request.getRemarks(),
                creator.getId(),
                creator.getId()
        );

        if (rowsInserted == 0) {
            // Duplicate detected at database level — return 400 Bad Request (preserves API contract)
            return ResponseEntity.badRequest().build();
        }

        // Query the inserted record to return the AttendanceResponse (preserves API contract)
        Attendance savedAttendance = attendanceRepository
                .findByStudent_School_IdAndStudent_IdAndTimetableSlot_IdAndDate(
                        TenantContext.schoolId(),
                        request.getStudentId(),
                        request.getTimetableSlotId(),
                        request.getDate()
                )
                .orElseThrow(() -> new EntityNotFoundException("Attendance record not found after insert"));

        return ResponseEntity.ok(mapToResponse(savedAttendance));
    }

    @Override
    @Transactional
    public ResponseEntity<?> recordBulkAttendance(BulkAttendanceRequest request) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            return ResponseEntity.badRequest().build();
        }

        // Daily register (V13): classroomId with no timetable slot = one mark per student
        // for the whole day, no timetable required. Exactly one scope per request.
        if (request.getTimetableSlotId() == null) {
            if (request.getClassroomId() == null) {
                throw new IllegalArgumentException(
                        "A bulk register needs a timetableSlotId (period register) or a classroomId (daily register)");
            }
            return recordDailyBulkAttendance(request);
        }
        if (request.getClassroomId() != null) {
            throw new IllegalArgumentException(
                    "Send either timetableSlotId or classroomId, not both");
        }

        // One slot for the whole class register, read with the tenant in the query.
        TimetableSlot timetableSlot = timetableSlotRepository
                .findByIdAndSchool_Id(request.getTimetableSlotId(), TenantContext.schoolId())
                .orElseThrow(() -> new NotFoundException("TIMETABLE_SLOT_NOT_FOUND",
                        "Timetable slot not found for the current school"));
        requireSlotScheduledOn(timetableSlot, request.getDate());

        School school = schoolRepository.getReferenceById(TenantContext.schoolId());

        // ATT-06 fix: validate ALL students before writing ANY records.
        // A normal return from inside the loop would not trigger @Transactional rollback,
        // so earlier saves would be committed even though the overall request failed.
        // Throwing unchecked exceptions here ensures the transaction rolls back cleanly.
        Integer expectedClassroomId = timetableSlot.getTimetable().getClassroom().getId();
        List<Student> validatedStudents = new java.util.ArrayList<>();
        for (var item : request.getAttendance()) {
            Student student = studentRepository.findById(item.getStudentId())
                    .orElseThrow(() -> new NotFoundException("STUDENT_NOT_FOUND", "Student not found"));

            if (!student.getClassroomId().equals(expectedClassroomId)) {
                throw new IllegalArgumentException(
                        "Student " + student.getId() + " does not belong to the classroom for this timetable slot");
            }

            validatedStudents.add(student);
        }

        int createdCount = 0;
        java.util.List<Integer> skippedStudentIds = new java.util.ArrayList<>();

        // Fetch creator user lazily (only if there are students to process)
        User creator = null;

        for (int i = 0; i < request.getAttendance().size(); i++) {
            var item = request.getAttendance().get(i);
            Student student = validatedStudents.get(i);

            // Lazy-load creator user on first iteration
            if (creator == null) {
                creator = userService.findByEmployee_Id()
                        .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));
            }

            // Use atomic upsert to eliminate check-then-act race condition.
            // INSERT ... ON CONFLICT DO NOTHING is atomic at the database level,
            // so concurrent identical submissions are handled safely without exceptions.
            // Returns 1 if inserted, 0 if duplicate (constraint already exists).
            int rowsInserted = attendanceRepository.insertIgnoreDuplicate(
                    TenantContext.schoolId(),
                    item.getStudentId(),
                    request.getTimetableSlotId(),
                    request.getDate(),
                    item.getStatus(),
                    item.getRemarks(),
                    creator.getId(),
                    creator.getId()
            );

            if (rowsInserted > 0) {
                createdCount++;
            } else {
                // Duplicate detected at database level, treat as skipped
                skippedStudentIds.add(item.getStudentId());
            }
        }

        // ATT-01 fix: Return detailed summary so the client can reconcile created vs. skipped records
        com.gms.model.response.BulkAttendanceResponse response = new com.gms.model.response.BulkAttendanceResponse();
        response.setTotalStudents(request.getAttendance().size());
        response.setCreatedCount(createdCount);
        response.setSkippedCount(skippedStudentIds.size());
        response.setSkippedStudentIds(skippedStudentIds);
        response.setMessage(String.format("Attendance recorded for %d students, %d skipped (already exists)",
                createdCount, skippedStudentIds.size()));

        return ResponseEntity.ok(response);
    }

    /**
     * The daily register: same validate-all-then-write shape and the same response contract
     * as the period path, but the rows carry no timetable slot, so no timetable needs to
     * exist for the class or the weekday. Students are validated against the classroom the
     * caller named instead of the one a slot would have implied.
     */
    private ResponseEntity<?> recordDailyBulkAttendance(BulkAttendanceRequest request) {
        // ATT-06 fix, same as the period path: validate ALL students before writing ANY.
        List<Student> validatedStudents = new java.util.ArrayList<>();
        for (var item : request.getAttendance()) {
            Student student = studentRepository.findById(item.getStudentId())
                    .orElseThrow(() -> new NotFoundException("STUDENT_NOT_FOUND", "Student not found"));

            if (!request.getClassroomId().equals(student.getClassroomId())) {
                throw new IllegalArgumentException(
                        "Student " + student.getId() + " does not belong to classroom " + request.getClassroomId());
            }

            validatedStudents.add(student);
        }

        int createdCount = 0;
        java.util.List<Integer> skippedStudentIds = new java.util.ArrayList<>();
        User creator = null;

        for (int i = 0; i < request.getAttendance().size(); i++) {
            var item = request.getAttendance().get(i);

            if (creator == null) {
                creator = userService.findByEmployee_Id()
                        .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));
            }

            int rowsInserted = attendanceRepository.insertDailyIgnoreDuplicate(
                    TenantContext.schoolId(),
                    item.getStudentId(),
                    request.getDate(),
                    item.getStatus(),
                    item.getRemarks(),
                    creator.getId(),
                    creator.getId()
            );

            if (rowsInserted > 0) {
                createdCount++;
            } else {
                skippedStudentIds.add(item.getStudentId());
            }
        }

        com.gms.model.response.BulkAttendanceResponse response = new com.gms.model.response.BulkAttendanceResponse();
        response.setTotalStudents(request.getAttendance().size());
        response.setCreatedCount(createdCount);
        response.setSkippedCount(skippedStudentIds.size());
        response.setSkippedStudentIds(skippedStudentIds);
        response.setMessage(String.format("Attendance recorded for %d students, %d skipped (already exists)",
                createdCount, skippedStudentIds.size()));

        return ResponseEntity.ok(response);
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
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));

        // An update that does not name a new slot keeps the existing one — which, for a
        // daily-register record (V13), is no slot at all.
        if (request.getTimetableSlotId() != null) {
            TimetableSlot timetableSlot = timetableSlotRepository
                    .findByIdAndSchool_Id(request.getTimetableSlotId(), TenantContext.schoolId())
                    .orElseThrow(() -> new NotFoundException("TIMETABLE_SLOT_NOT_FOUND",
                            "Timetable slot not found for the current school"));
            attendance.setTimetableSlot(timetableSlot);
        }

        // Checked after the slot has been resolved, so the row that is written is the row that was
        // checked — whichever of the existing or the requested slot survives the block above.
        // A daily record has no slot, so there is no scheduled day to check against.
        if (attendance.getTimetableSlot() != null) {
            requireSlotScheduledOn(attendance.getTimetableSlot(), request.getDate());
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
        List<Attendance> attendances = attendanceRepository.findByClassroomAndDate(
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

    /**
     * An attendance row names a timetable slot, so it can only describe a lesson that actually runs
     * that day. The register has no weekday of its own and the unique key is
     * (student, slot, date), so without this check the database happily accepts Friday's register
     * against Monday's period — a row that is not wrong on read, just meaningless.
     */
    private static void requireSlotScheduledOn(TimetableSlot slot, LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Attendance date is required");
        }
        String scheduledDay = slot.getDayOfWeek();
        String actualDay = date.getDayOfWeek().name();
        if (scheduledDay == null || !scheduledDay.equalsIgnoreCase(actualDay)) {
            throw new IllegalArgumentException(String.format(
                    "Timetable slot %d runs on %s, but %s is a %s",
                    slot.getId(), scheduledDay, date, actualDay));
        }
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
                slot.getId(),
                slot.getDayOfWeek(),
                slot.getPeriod(),
                mapToAssignmentResponse(slot.getAssignment())
        );
    }

    private TeacherAssignmentResponse mapToAssignmentResponse(TeacherAssignment assignment) {
        if (assignment == null) {
            return null;
        }

        EmployeeResponse teacherResponse = null;
        if (assignment.getTeacher() != null) {
            teacherResponse = new EmployeeResponse(
                    assignment.getTeacher().getId(),
                    assignment.getTeacher().getFullName(),
                    assignment.getTeacher().getEmail(),
                    assignment.getTeacher().getDesignation(),
                    assignment.getTeacher().getDepartment()
            );
        }

        SubjectResponse subjectResponse = null;
        if (assignment.getSubject() != null) {
            subjectResponse = new SubjectResponse(
                    assignment.getSubject().getId(),
                    assignment.getSubject().getSchoolId(),
                    assignment.getSubject().getName(),
                    assignment.getSubject().getCode()
            );
        }

        ClassroomResponse classroomResponse = null;
        if (assignment.getClassroom() != null) {
            classroomResponse = new ClassroomResponse(
                    assignment.getClassroom().getId(),
                    assignment.getClassroom().getSchoolId(),
                    assignment.getClassroom().getName(),
                    assignment.getClassroom().getGrade(),
                    assignment.getClassroom().getSection()
            );
        }

        return new TeacherAssignmentResponse(
                assignment.getId(),
                teacherResponse,
                subjectResponse,
                classroomResponse,
                assignment.getAcademicYear()
        );
    }
}
