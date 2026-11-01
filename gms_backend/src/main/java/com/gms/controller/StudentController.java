package com.gms.controller;

import com.gms.model.entity.Student;
import com.gms.model.entity.StudentAdmission;
import com.gms.model.request.StudentRequest;
import com.gms.model.response.*;
import com.gms.repository.StudentAdmissionRepository;
import com.gms.service.StudentService;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Student administration and the student portal.
 *
 * <p>This controller no longer extends {@code AbstractCRUDController}, which supplied an unguarded
 * {@code GET /{id}} and {@code DELETE /{id}}. The read is declared below and is scoped to the
 * caller's school; the delete is not declared at all — the domain's own delete is
 * {@code PATCH /students/toggle}, which deactivates a student and leaves the rows that reference
 * one (attendance, results, fees) intact. A physical delete of a student is refused by the
 * foreign keys that the migrations put in place, so it was never a working operation.</p>
 */
@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping(Constants.API_BASE_V1 + "/students")
public class StudentController {

    private final StudentService studentService;

    @Autowired
    private StudentAdmissionRepository studentAdmissionRepository;
    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody StudentRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return studentService.create(request, empId, schoolId);
    }

    @PutMapping("/update")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<StudentResponse> update(@Valid @RequestBody StudentRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return studentService.update(request, empId, schoolId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<Student>> getAll() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<Student> students = studentService.findBySchool(schoolId);
        return ResponseEntity.ok(students);
    }

    /**
     * One student, read within the caller's school.
     *
     * <p>{@code getById(id, schoolId)} answers {@code EntityNotFoundException} for a row that does
     * not exist and {@code IllegalArgumentException} for a row that belongs to another school; the
     * two are reported as 404 and 403 respectively.</p>
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Student> getById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(studentService.getById(id, schoolId));
        } catch (jakarta.persistence.EntityNotFoundException ex) {
            return ResponseEntity.status(404).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/admissions")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<StudentAdmission>> getAllAdmissions() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(studentAdmissionRepository.findBySchool_Id(schoolId));
    }

    @PatchMapping("/toggle")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> toggleActive(@RequestParam Integer id, @RequestParam Boolean isActive) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return studentService.toggleStudent(id, isActive, schoolId, empId);
    }

    // Student-specific role endpoints
    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<StudentResponse> getStudentProfile() {
        String username = SecurityUtil.getUsernameFromToken();
        StudentResponse response = studentService.getMyProfile();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-classes")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<ClassroomResponse> getMyClass() {
        String username = SecurityUtil.getUsernameFromToken();
        ClassroomResponse classroom = studentService.getMyClassroom(username);
        return ResponseEntity.ok(classroom);
    }

    @GetMapping("/my-subjects")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<SubjectResponse>> getMySubjects() {
        String username = SecurityUtil.getUsernameFromToken();
        List<SubjectResponse> subjects = studentService.getMySubjects(username);
        return ResponseEntity.ok(subjects);
    }

    @GetMapping("/my-attendance")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<AttendanceResponse>> getMyAttendance() {
        String username = SecurityUtil.getUsernameFromToken();
        List<AttendanceResponse> attendance = studentService.getMyAttendance(username);
        return ResponseEntity.ok(attendance);
    }

    @GetMapping("/my-results")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<ResultResponse>> getMyResults() {
        String username = SecurityUtil.getUsernameFromToken();
        List<ResultResponse> results = studentService.getMyResults(username);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/my-fees")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<StudentFeeResponse>> getMyFees() {
        String username = SecurityUtil.getUsernameFromToken();
        List<StudentFeeResponse> fees = studentService.getMyFees(username);
        return ResponseEntity.ok(fees);
    }

    @GetMapping("/my-timetable")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<TimetableResponse>> getMyTimetable() {
        String username = SecurityUtil.getUsernameFromToken();
        List<TimetableResponse> timetable = studentService.getMyTimetable(username);
        return ResponseEntity.ok(timetable);
    }

    @GetMapping("/my-teachers")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<EmployeeResponse>> getMyTeachers() {
        String username = SecurityUtil.getUsernameFromToken();
        List<EmployeeResponse> teachers = studentService.getMyTeachers(username);
        return ResponseEntity.ok(teachers);
    }

    @GetMapping("/my-announcements")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<AnnouncementResponse>> getMyAnnouncements() {
        String username = SecurityUtil.getUsernameFromToken();
        List<AnnouncementResponse> announcements = studentService.getMyAnnouncements(username);
        return ResponseEntity.ok(announcements);
    }

    @GetMapping("/my-notifications")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications() {
        String username = SecurityUtil.getUsernameFromToken();
        List<NotificationResponse> notifications = studentService.getMyNotifications(username);
        return ResponseEntity.ok(notifications);
    }
}
