package com.gms.controller;

import com.gms.model.entity.Student;
import com.gms.model.entity.StudentAdmission;
import com.gms.model.request.StudentRequest;
import com.gms.model.response.*;
import com.gms.repository.StudentAdmissionRepository;
import com.gms.security.TenantContext;
import com.gms.service.StudentService;
import com.gms.util.Constants;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return studentService.create(request);
    }

    @PutMapping("/update")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<StudentResponse> update(@Valid @RequestBody StudentRequest request) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return studentService.update(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<Student>> getAll() {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<Student> students = studentService.findBySchool();
        return ResponseEntity.ok(students);
    }

    /**
     * Server-side student search, filtering and pagination — MVP feature #9, added alongside
     * {@code GET /students}, which is unchanged.
     *
     * <p>Returns a Spring Data {@link Page} of {@link StudentResponse} so the client reads
     * {@code content[]}, {@code totalElements} and {@code totalPages} from one response. The path is the
     * literal {@code /search}; it is deliberately not {@code /students/{id}}, which would collide with the
     * by-id route declared next.</p>
     *
     * <p>Tenancy is decided from the caller's verified claims, never from a query parameter, and the
     * service owns the branch: a school {@code ADMIN} searches only its own students, the platform owner
     * ({@code SUPERADMIN}) searches across every school, and a bounded caller whose token carries no school
     * gets an empty page. The guard below only refuses a caller that could read neither its own tenant nor
     * across tenants, mirroring {@code SchoolController.getAll()}'s treatment of the platform owner.</p>
     *
     * @param query   optional case-insensitive free text over first name, last name, student id and email
     * @param classId optional classroom ({@code classId}) filter
     * @param page    0-based page index, defaults to 0
     * @param size    page size, defaults to 20 and is capped server-side
     * @param sort    {@code field} or {@code field,direction}; only whitelisted fields are honoured
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Page<StudentResponse>> search(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "classId", required = false) Integer classId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", required = false) String sort) {
        if (!TenantContext.readsAcrossTenants() && TenantContext.schoolId() == null) {
            return ResponseEntity.status(401).build();
        }
        Page<StudentResponse> result = studentService.searchStudents(query, classId, page, size, sort);
        return ResponseEntity.ok(result);
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
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            return ResponseEntity.ok(studentService.getById(id));
        } catch (jakarta.persistence.EntityNotFoundException ex) {
            return ResponseEntity.status(404).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/admissions")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<List<StudentAdmission>> getAllAdmissions() {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(studentAdmissionRepository.findBySchool_Id(schoolId));
    }

    @PatchMapping("/toggle")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> toggleActive(@RequestParam Integer id, @RequestParam Boolean isActive) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return studentService.toggleStudent(id, isActive);
    }

    // Student-specific role endpoints
    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<StudentResponse> getStudentProfile() {
        StudentResponse response = studentService.getMyProfile();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-classes")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<ClassroomResponse> getMyClass() {
        String username = TenantContext.username();
        ClassroomResponse classroom = studentService.getMyClassroom(username);
        return ResponseEntity.ok(classroom);
    }

    @GetMapping("/my-subjects")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<SubjectResponse>> getMySubjects() {
        String username = TenantContext.username();
        List<SubjectResponse> subjects = studentService.getMySubjects(username);
        return ResponseEntity.ok(subjects);
    }

    @GetMapping("/my-attendance")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<AttendanceResponse>> getMyAttendance() {
        String username = TenantContext.username();
        List<AttendanceResponse> attendance = studentService.getMyAttendance(username);
        return ResponseEntity.ok(attendance);
    }

    @GetMapping("/my-results")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<ResultResponse>> getMyResults() {
        String username = TenantContext.username();
        List<ResultResponse> results = studentService.getMyResults(username);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/my-fees")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<StudentFeeResponse>> getMyFees() {
        String username = TenantContext.username();
        List<StudentFeeResponse> fees = studentService.getMyFees(username);
        return ResponseEntity.ok(fees);
    }

    @GetMapping("/my-timetable")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<TimetableResponse>> getMyTimetable() {
        String username = TenantContext.username();
        List<TimetableResponse> timetable = studentService.getMyTimetable(username);
        return ResponseEntity.ok(timetable);
    }

    @GetMapping("/my-teachers")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<EmployeeResponse>> getMyTeachers() {
        String username = TenantContext.username();
        List<EmployeeResponse> teachers = studentService.getMyTeachers(username);
        return ResponseEntity.ok(teachers);
    }

    @GetMapping("/my-announcements")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<AnnouncementResponse>> getMyAnnouncements() {
        String username = TenantContext.username();
        List<AnnouncementResponse> announcements = studentService.getMyAnnouncements(username);
        return ResponseEntity.ok(announcements);
    }

    @GetMapping("/my-notifications")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications() {
        String username = TenantContext.username();
        List<NotificationResponse> notifications = studentService.getMyNotifications(username);
        return ResponseEntity.ok(notifications);
    }
}
