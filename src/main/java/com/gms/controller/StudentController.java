package com.gms.controller;

import com.gms.model.entity.Student;
import com.gms.model.entity.StudentAdmission;
import com.gms.model.request.StudentRequest;
import com.gms.model.response.*;
import com.gms.repository.StudentAdmissionRepository;
import com.gms.service.StudentService;
import com.gms.service.impl.StudentServiceImpl;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:3000"})
@RequestMapping(Constants.API_BASE_V1 + "/students")
public class StudentController extends AbstractCRUDController<Student, Integer> {

    private final StudentService studentService;

    @Autowired
    private StudentAdmissionRepository studentAdmissionRepository;

    public StudentController(StudentServiceImpl studentServiceImpl) {
        super(studentServiceImpl);
        this.studentService = studentServiceImpl;
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody StudentRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return studentService.create(request, empId, schoolId);
    }

    @PutMapping("/update")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentResponse> update(@Valid @RequestBody StudentRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return studentService.update(request, empId, schoolId);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Student>> getAll() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<Student> students = studentService.findBySchool(schoolId);
        if (students.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(students);
    }

    @GetMapping("/admissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StudentAdmission>> getAllAdmissions() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(studentAdmissionRepository.findBySchool_Id(schoolId));
    }

    @PatchMapping("/toggle")
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudentResponse> getStudentProfile() {
        String username = SecurityUtil.getUsernameFromToken();
        StudentResponse response = studentService.getMyProfile();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-classes")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ClassroomResponse> getMyClass() {
        String username = SecurityUtil.getUsernameFromToken();
        ClassroomResponse classroom = studentService.getMyClassroom(username);
        return ResponseEntity.ok(classroom);
    }

    @GetMapping("/my-subjects")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<SubjectResponse>> getMySubjects() {
        String username = SecurityUtil.getUsernameFromToken();
        List<SubjectResponse> subjects = studentService.getMySubjects(username);
        return ResponseEntity.ok(subjects);
    }

    @GetMapping("/my-attendance")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<AttendanceResponse>> getMyAttendance() {
        String username = SecurityUtil.getUsernameFromToken();
        List<AttendanceResponse> attendance = studentService.getMyAttendance(username);
        return ResponseEntity.ok(attendance);
    }

    @GetMapping("/my-results")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<ResultResponse>> getMyResults() {
        String username = SecurityUtil.getUsernameFromToken();
        List<ResultResponse> results = studentService.getMyResults(username);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/my-fees")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<StudentFeeResponse>> getMyFees() {
        String username = SecurityUtil.getUsernameFromToken();
        List<StudentFeeResponse> fees = studentService.getMyFees(username);
        return ResponseEntity.ok(fees);
    }

    @GetMapping("/my-timetable")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<TimetableResponse>> getMyTimetable() {
        String username = SecurityUtil.getUsernameFromToken();
        List<TimetableResponse> timetable = studentService.getMyTimetable(username);
        return ResponseEntity.ok(timetable);
    }

    @GetMapping("/my-teachers")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<EmployeeResponse>> getMyTeachers() {
        String username = SecurityUtil.getUsernameFromToken();
        List<EmployeeResponse> teachers = studentService.getMyTeachers(username);
        return ResponseEntity.ok(teachers);
    }

    @GetMapping("/my-announcements")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<AnnouncementResponse>> getMyAnnouncements() {
        String username = SecurityUtil.getUsernameFromToken();
        List<AnnouncementResponse> announcements = studentService.getMyAnnouncements(username);
        return ResponseEntity.ok(announcements);
    }

    @GetMapping("/my-notifications")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications() {
        String username = SecurityUtil.getUsernameFromToken();
        List<NotificationResponse> notifications = studentService.getMyNotifications(username);
        return ResponseEntity.ok(notifications);
    }
}
