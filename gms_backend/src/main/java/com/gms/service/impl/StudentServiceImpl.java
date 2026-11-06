package com.gms.service.impl;

import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.*;
import com.gms.enums.RoleEnum;
import com.gms.exception.NotFoundException;
import com.gms.model.request.StudentRequest;
import com.gms.model.request.StudentSelfUpdateRequest;
import com.gms.model.response.ClassroomResponse;
import com.gms.model.response.StudentResponse;
import com.gms.model.response.SubjectResponse;
import com.gms.model.response.AttendanceResponse;
import com.gms.model.response.ResultResponse;
import com.gms.model.response.StudentFeeResponse;
import com.gms.model.response.TimetableResponse;
import com.gms.model.response.EmployeeResponse;
import com.gms.model.response.AnnouncementResponse;
import com.gms.model.response.NotificationResponse;
import com.gms.repository.StudentRepository;
import com.gms.security.TenantContext;
import com.gms.service.*;
import com.gms.util.SecureCredentialGenerator;
import com.gms.util.UsernameGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDate;

@Service
@Transactional
public class StudentServiceImpl implements StudentService {

    /** The page size a caller gets when they omit {@code size}, and the ceiling on any {@code size}. */
    private static final int DEFAULT_SEARCH_SIZE = 20;
    private static final int MAX_SEARCH_SIZE = 100;

    /**
     * The {@link Student} properties a client may sort by. Anything outside this set falls back to
     * {@link #DEFAULT_SEARCH_FIELD} rather than being handed to the query, so an unknown or hostile sort
     * name cannot make Spring Data fail to build the {@code order by} and throw at request time.
     */
    private static final Set<String> SORTABLE_STUDENT_FIELDS = Set.of(
            "id", "firstName", "lastName", "email", "studentId", "dateOfBirth", "gender");
    private static final String DEFAULT_SEARCH_FIELD = "lastName";

    private final StudentRepository studentRepository;
    private final SchoolService schoolService;
    private final ClassroomService classroomService;
    private final SectionService sectionService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final ResultService resultService;
    private final StudentFeeService studentFeeService;
    private final TimetableService timetableService;
    private final AnnouncementService announcementService;
    private final SubjectService subjectService;
    private final NotificationService notificationService;
    private final TeacherAssignmentService teacherAssignmentService;
    private final AttendanceService attendanceService;

    public StudentServiceImpl(StudentRepository studentRepository, SchoolService schoolService, ClassroomService classroomService, SectionService sectionService, UserService userService, PasswordEncoder passwordEncoder, ResultService resultService, StudentFeeService studentFeeService, TimetableService timetableService, AnnouncementService announcementService, SubjectService subjectService, NotificationService notificationService, TeacherAssignmentService teacherAssignmentService, AttendanceService attendanceService) {
        this.studentRepository = studentRepository;
        this.schoolService = schoolService;
        this.classroomService = classroomService;
        this.sectionService = sectionService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.resultService = resultService;
        this.studentFeeService = studentFeeService;
        this.timetableService = timetableService;
        this.announcementService = announcementService;
        this.subjectService = subjectService;
        this.notificationService = notificationService;
        this.teacherAssignmentService = teacherAssignmentService;
        this.attendanceService = attendanceService;
    }

    @Override
    public ResponseEntity<StudentResponse> create(StudentRequest request) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Student student = createStudentFromRequest(request, new Student());
        Student savedStudent = studentRepository.save(student);

        // ACD-13-02: generate a unique secure temporary password instead of a shared hardcoded one.
        // Both halves of the credential are returned once in the creation response: the username is
        // generated server-side, so a password on its own could not be used to sign in.
        ProvisionedAccount credential = provisionStudentUser(savedStudent);

        StudentResponse response = toResponse(savedStudent);
        response.setUsername(credential.username());
        response.setTemporaryPassword(credential.temporaryPassword());
        return ResponseEntity.ok(response);
    }

    @Override
    public String createUserForStudent(Student student) {
        return provisionStudentUser(student).temporaryPassword();
    }

    private ProvisionedAccount provisionStudentUser(Student student) {
        if (userService.existsByEmail(student.getEmail())) {
            throw new IllegalStateException("A user with this email already exists.");
        }

        String temporaryPassword = SecureCredentialGenerator.generate();

        User user = new User();
        user.setSchool(student.getSchool());
        user.setStudent(student);
        user.setFullName(student.getFullName());
        user.setEmail(student.getEmail());
        user.setUsername(UsernameGenerator.generateUsername(student.getFirstName(), student.getLastName(), userService));
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setRoles(Collections.singleton(RoleEnum.STUDENT.name()));
        user.setRequirePasswordChange(true);
        user.setEnabled(true);

        User saved = userService.save(user);
        return new ProvisionedAccount(saved.getUsername(), temporaryPassword);
    }

    /** The login name generated for a provisioned account plus its one-time plaintext password. */
    private record ProvisionedAccount(String username, String temporaryPassword) {}

    @Override
    public Student createStudentFromRequest(StudentRequest request, Student student) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            throw new IllegalArgumentException("Employee ID or School ID cannot be null");
        }

        School school = schoolService.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        Classroom classroom = classroomService.findById(request.getClassId())
                .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));
        if (!classroom.getSchool().getId().equals(TenantContext.schoolId())) {
            throw new TenantAccessDeniedException("Classroom does not belong to current school");
        }

        Section section = null;
        if (request.getSectionId() != null) {
            section = sectionService.findById(request.getSectionId())
                    .orElseThrow(() -> new EntityNotFoundException("Section not found"));
            if (!section.getSchoolId().equals(TenantContext.schoolId())) {
                throw new TenantAccessDeniedException("Section does not belong to current school");
            }
        }

        org.springframework.beans.BeanUtils.copyProperties(request, student, "classId", "sectionId", "schoolId", "id", "status", "studentId");
        student.setSchool(school);
        student.setClassroom(classroom);
        student.setSection(section);

        // Generate a simple unique studentId within the school: STD###
        String generatedId = nextStudentId();
        student.setStudentId(generatedId);
        student.setStatus("1");

        return student;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Student> findBySchool(int size) {
        // The clamp buildPageable already applies to a paged search, on the read that had no ceiling:
        // a volume tenant holds 50,000 pupils and one dashboard card renders five of them.
        int safeSize = size <= 0 ? DEFAULT_SEARCH_SIZE : Math.min(size, MAX_SEARCH_SIZE);
        return studentRepository.findAllBySchool_IdAndStatusNotOrderByIdDesc(
                TenantContext.schoolId(), "0", PageRequest.of(0, safeSize));
    }

    /**
     * Paged, filtered, free-text student search within the caller's tenant (MVP feature #9).
     *
     * <p><b>Which branch answers which caller.</b> The tenant is read from {@link TenantContext}, never
     * from a request parameter. When {@link TenantContext#readsAcrossTenants()} is true — the platform
     * owner ({@code SUPERADMIN}) — the query is passed a {@code null} tenant filter and its
     * {@code :schoolId is null} arm returns every school's students, so a SUPERADMIN search is not
     * silently empty. When it is false — a school {@code ADMIN} — the caller's own {@code schoolId} is
     * bound into the {@code s.school.id} predicate and the search is confined to that school. A bounded
     * caller whose token carries no tenant is failed closed here with an empty page rather than being
     * handed the cross-tenant arm: a missing claim must never widen reach.</p>
     *
     * <p>The result is mapped through the existing {@link #toResponse} so a {@code Student} becomes a
     * {@code StudentResponse} exactly the way every other student endpoint already does it, and the
     * {@code Page} wrapper carries {@code totalElements}/{@code totalPages} to the client.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public Page<StudentResponse> searchStudents(String query, Integer classId, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);

        boolean crossTenant = TenantContext.readsAcrossTenants();
        Integer schoolId = TenantContext.schoolId();

        if (!crossTenant && schoolId == null) {
            // A school caller with no tenant claim sees nothing, not every school.
            return Page.empty(pageable);
        }

        Integer tenantFilter = crossTenant ? null : schoolId;
        String trimmedQuery = (query == null || query.isBlank()) ? null : query.trim();

        Page<Student> students = studentRepository.findStudents(tenantFilter, classId,
                buildSearchPattern(trimmedQuery), pageable);
        return students.map(this::toResponse);
    }

    /**
     * The LIKE pattern a free-text search runs on: trimmed, lowercased, escaped, and only then wrapped in
     * wildcards — in that order, because wrapping first would have the escaper rewrite the {@code %} that
     * makes the search a substring match.
     *
     * <p>{@code !} is the escape character and is doubled before {@code %} and {@code _} are escaped, so a
     * query of {@code 100%} matches the text {@code 100%} rather than every row in the school. The pattern
     * is built here rather than by {@code concat()} in the query because Hibernate renders the latter as
     * {@code … escape ''}, and Postgres answers an empty escape character with
     * {@code function lower(bytea) does not exist} — which was a 500 on every call to the endpoint.</p>
     */
    private String buildSearchPattern(String query) {
        if (query == null) {
            return null;
        }
        String escaped = query.toLowerCase()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    /**
     * A {@link Pageable} that cannot fail: the page is floored at 0, the size falls back to
     * {@link #DEFAULT_SEARCH_SIZE} for any non-positive value and is capped at {@link #MAX_SEARCH_SIZE},
     * and the sort is resolved through {@link #buildSort}.
     */
    private Pageable buildPageable(int page, int size, String sort) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_SEARCH_SIZE : Math.min(size, MAX_SEARCH_SIZE);
        return PageRequest.of(safePage, safeSize, buildSort(sort));
    }

    /**
     * Parses a {@code field} or {@code field,direction} sort string against
     * {@link #SORTABLE_STUDENT_FIELDS}. An unrecognised field — or no field at all — falls back to
     * {@link #DEFAULT_SEARCH_FIELD}; only a literal {@code desc} (any case) reverses the order.
     */
    private Sort buildSort(String sort) {
        String field = DEFAULT_SEARCH_FIELD;
        Sort.Direction direction = Sort.Direction.ASC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String candidate = parts[0].trim();
            if (SORTABLE_STUDENT_FIELDS.contains(candidate)) {
                field = candidate;
            }
            if (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())) {
                direction = Sort.Direction.DESC;
            }
        }
        return Sort.by(direction, field);
    }

    @Override
    @Transactional(readOnly = true)
    public Student getById(Integer id) {
        Student student = studentRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Student not found"));
        // The scoped load already answered the tenancy question, so the hand-written school
        // comparison is gone; what remains is the archive rule, which is business state rather than
        // tenancy and which the platform owner is not exempt from either.
        if ("0".equals(student.getStatus())) {
            // A retired row is outside the caller's visible set, which is what 404 means here: the
            // school's own list already filters it out, and a foreign row reads the same way. 403
            // would claim the caller lacks a permission it holds on this route.
            throw new EntityNotFoundException("Student is archived");
        }
        return student;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Student> findById(Integer id) {
        return studentRepository.findById(id);
    }

    @Override
    public ResponseEntity<StudentResponse> update(StudentRequest request) {
        if (request.getId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Student student = studentRepository.findById(request.getId()).orElseThrow(() -> new EntityNotFoundException("Student not found"));
        // The scoped load above already filtered on the caller's school, so the hand-written
        // school comparison is gone; what remains is the archive rule, which the platform owner
        // is not exempt from either.
        if ("0".equals(student.getStatus())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        // update allowed fields
        if (request.getFirstName() != null) student.setFirstName(request.getFirstName());
        if (request.getLastName() != null) student.setLastName(request.getLastName());
        if (request.getEmail() != null) student.setEmail(request.getEmail());
        if (request.getPhoneNumber() != null) student.setPhoneNumber(request.getPhoneNumber());
        if (request.getDateOfBirth() != null) student.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) student.setGender(request.getGender());
        if (request.getClassId() != null) {
            Classroom classroom = classroomService.findById(request.getClassId())
                    .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));
            if (!classroom.getSchool().getId().equals(TenantContext.schoolId())) {
                throw new TenantAccessDeniedException("Classroom does not belong to current school");
            }
            student.setClassroom(classroom);
        }
        if (request.getSectionId() != null) {
            Section section = sectionService.findById(request.getSectionId())
                    .orElseThrow(() -> new EntityNotFoundException("Section not found"));
            if (!section.getSchoolId().equals(TenantContext.schoolId())) {
                throw new TenantAccessDeniedException("Section does not belong to current school");
            }
            student.setSection(section);
        } else {
            // If sectionId is null, remove section assignment
            student.setSection(null);
        }
        
        Student saved = studentRepository.save(student);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    public ResponseEntity<?> toggleStudent(Integer id, Boolean isActive) {
        Student existing = studentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));

        // The scoped load answers the tenancy question; a foreign id never reaches here.

        existing.setStatus(isActive ? "1" : "0");

        Student saved = studentRepository.save(existing);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public StudentResponse getMyProfile() {
        String username = TenantContext.username();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }

        return toResponse(student);
    }

    @Override
    @Transactional
    public StudentResponse updateMyProfile(StudentSelfUpdateRequest request) {
        String username = TenantContext.username();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }

        student.setFirstName(request.getFirstName().trim());
        student.setLastName(request.getLastName().trim());
        student.setPhoneNumber(request.getPhoneNumber() == null ? null : request.getPhoneNumber().trim());

        return toResponse(studentRepository.save(student));
    }

    @Override
    public String nextStudentId() {
        int counter = (int) (studentRepository.count() + 1); // coarse; acceptable for now
        String candidate;
        int attempts = 0;
        do {
            candidate = String.format("STD%03d", counter + attempts);
            attempts++;
        } while (studentRepository.existsBySchool_IdAndStudentId(TenantContext.schoolId(), candidate) && attempts < 10000);
        if (attempts >= 10000) throw new IllegalStateException("Unable to generate unique student ID");
        return candidate;
    }

    private StudentResponse toResponse(Student s) {
        return new StudentResponse(
                s.getId(),
                s.getSchoolId(),
                s.getClassroomId(),
                s.getSectionId(),
                s.getStudentId(),
                s.getFirstName(),
                s.getLastName(),
                s.getEmail(),
                s.getPhoneNumber(),
                s.getDateOfBirth(),
                s.getGender(),
                s.getStatus(),
                null,
                null,
                null,
                null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public StudentResponse getStudentProfile(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }

        return toResponse(student);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassroomResponse getMyClassroom(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }

        Classroom classroom = student.getClassroom();
        return new ClassroomResponse(
                classroom.getId(),
                classroom.getSchool().getId(),
                classroom.getName(),
                classroom.getGrade(),
                classroom.getSection()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectResponse> getMySubjects(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }

        Classroom classroom = student.getClassroom();
        if (classroom == null) {
            return Collections.emptyList();
        }

        // ACD-02: Return subjects assigned to the student's classroom (not all school subjects)
        List<com.gms.model.response.TeacherAssignmentResponse> assignments = 
                teacherAssignmentService.getAssignmentsByClassroom(classroom.getId());
        return assignments.stream()
                .map(com.gms.model.response.TeacherAssignmentResponse::getSubject)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getMyAttendance(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }

        // ACD-02: Return last 30 days of attendance for the authenticated student
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        return attendanceService.getAttendanceForStudent(student.getId(), startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResultResponse> getMyResults(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }

        // ACD-02: Return all results for the authenticated student
        ResponseEntity<List<Result>> response = resultService.getResultsForStudent(student.getId());
        List<Result> results = response.getBody();
        if (results == null) {
            return Collections.emptyList();
        }
        return results.stream()
                .map(this::toResultResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentFeeResponse> getMyFees(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }

        // ACD-02: Return all fees for the authenticated student
        ResponseEntity<List<StudentFee>> response = studentFeeService.getStudentFeesByStudent(student.getId());
        List<StudentFee> studentFees = response.getBody();
        if (studentFees == null) {
            return Collections.emptyList();
        }
        return studentFees.stream()
                .map(this::toStudentFeeResponse)
                .collect(Collectors.toList());
    }

    /**
     * The caller's class timetable, answered by the timetable service — the same read
     * {@code GET /timetables/student/me} makes, so the school has one implementation of it rather than
     * a second walk over the same rows.
     *
     * <p>A classroom whose timetable has not been built yet is a school still onboarding, not a
     * student with a broken portal: that one case answers an empty list, which is what this route's
     * contract is. A caller with no student profile still fails, because that is a broken account
     * rather than missing data.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<TimetableResponse> getMyTimetable(String username) {
        try {
            return List.of(timetableService.getTimetableForAuthenticatedStudent(username));
        } catch (NotFoundException ex) {
            if ("TIMETABLE_NOT_FOUND".equals(ex.getCode())) {
                return List.of();
            }
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponse> getMyTeachers(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }

        Classroom classroom = student.getClassroom();
        if (classroom == null) {
            return Collections.emptyList();
        }

        // ACD-02: Return teachers assigned to the student's classroom
        List<com.gms.model.response.TeacherAssignmentResponse> assignments = 
                teacherAssignmentService.getAssignmentsByClassroom(classroom.getId());
        return assignments.stream()
                .map(com.gms.model.response.TeacherAssignmentResponse::getTeacher)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getMyAnnouncements(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }

        // ACD-02: Return active (published, non-expired) announcements for the student's school
        ResponseEntity<List<Announcement>> response = announcementService.getActiveAnnouncements();
        List<Announcement> announcements = response.getBody();
        if (announcements == null) {
            return Collections.emptyList();
        }
        return announcements.stream()
                .map(this::toAnnouncementResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }

        // ACD-02: Return notifications addressed to the authenticated student's user account
        ResponseEntity<List<Notification>> response = notificationService.getNotificationsByUserId(user.getId());
        List<Notification> notifications = response.getBody();
        if (notifications == null) {
            return Collections.emptyList();
        }
        return notifications.stream()
                .map(this::toNotificationResponse)
                .collect(Collectors.toList());
    }

    // Helper methods for response mapping
    private SubjectResponse toSubjectResponse(Subject subject) {
        SubjectResponse response = new SubjectResponse();
        response.setId(subject.getId());
        response.setName(subject.getName());
        response.setCode(subject.getCode());
        // response.setDescription(subject.getDescription()); // TODO: Check if this field exists
        return response;
    }

    private AttendanceResponse toAttendanceResponse(Attendance attendance) {
        // Simplified response to avoid field access issues
        AttendanceResponse response = new AttendanceResponse();
        response.setId(attendance.getId());
        
        // Create StudentResponse for the student field
        if (attendance.getStudent() != null) {
            StudentResponse studentResponse = new StudentResponse();
            studentResponse.setId(attendance.getStudent().getId());
            studentResponse.setFirstName(attendance.getStudent().getFirstName());
            studentResponse.setLastName(attendance.getStudent().getLastName());
            response.setStudent(studentResponse);
        }
        
        // Set other basic fields
        response.setStatus(attendance.getStatus());
        response.setDate(attendance.getDate());
        response.setRemarks(attendance.getRemarks());
        
        // TODO: Map timetableSlot when entity structure is confirmed
        return response;
    }

    private ResultResponse toResultResponse(Result result) {
        ResultResponse response = new ResultResponse();
        response.setId(result.getId());
        response.setStudentExamId(result.getStudentExam().getId());
        response.setStudentName(result.getStudentExam().getStudent().getFullName());
        response.setExamName(result.getStudentExam().getExamSubject().getExam().getName());
        response.setSubjectName(result.getStudentExam().getExamSubject().getSubject().getName());
        response.setObtainedMarks(result.getObtainedMarks());
        response.setGrade(result.getGrade());
        response.setPercentage(result.getPercentage());
        response.setRemarks(result.getRemarks());
        response.setStatus(result.getStatus());
        return response;
    }

    private StudentFeeResponse toStudentFeeResponse(StudentFee studentFee) {
        return StudentFeeResponseMapper.toResponse(studentFee);
    }

    private EmployeeResponse toEmployeeResponse(Employee employee) {
        EmployeeResponse response = new EmployeeResponse();
        response.setId(employee.getId());
        response.setFullName(employee.getFullName());
        response.setEmail(employee.getEmail());
        response.setDesignation(employee.getDesignation());
        response.setDepartment(employee.getDepartment());
        return response;
    }

    private AnnouncementResponse toAnnouncementResponse(Announcement announcement) {
        AnnouncementResponse response = new AnnouncementResponse();
        response.setId(announcement.getId());
        response.setSchoolId(announcement.getSchool() != null ? announcement.getSchool().getId() : null);
        response.setTitle(announcement.getTitle());
        response.setContent(announcement.getContent());
        response.setAnnouncementType(announcement.getAnnouncementType());
        response.setTargetAudience(announcement.getTargetAudience());
        response.setTargetIds(announcement.getTargetIds());
        response.setPublishDate(announcement.getPublishDate());
        response.setExpiryDate(announcement.getExpiryDate());
        response.setIsPublished(announcement.getIsPublished());
        response.setPublishedAt(announcement.getPublishedAt());
        response.setPublishedBy(announcement.getPublishedBy());
        response.setPriority(announcement.getPriority());
        response.setCategory(announcement.getCategory());
        response.setAttachmentUrls(announcement.getAttachmentUrls());
        response.setClickActionUrl(announcement.getClickActionUrl());
        response.setIsPinned(announcement.getIsPinned());
        response.setViewCount(announcement.getViewCount());
        return response;
    }

    private NotificationResponse toNotificationResponse(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setSchoolId(notification.getSchool() != null ? notification.getSchool().getId() : null);
        response.setTitle(notification.getTitle());
        response.setMessage(notification.getMessage());
        response.setNotificationType(notification.getNotificationType());
        response.setChannel(notification.getChannel());
        response.setPriority(notification.getPriority());
        response.setRecipientType(notification.getRecipientType());
        response.setRecipientIds(notification.getRecipientIds());
        response.setScheduledAt(notification.getScheduledAt());
        response.setSentAt(notification.getSentAt());
        response.setDeliveryStatus(notification.getDeliveryStatus());
        response.setReadStatus(notification.getReadStatus());
        response.setRelatedEntityType(notification.getRelatedEntityType());
        response.setRelatedEntityId(notification.getRelatedEntityId());
        response.setIsImportant(notification.getIsImportant());
        response.setExpiresAt(notification.getExpiresAt());
        response.setClickActionUrl(notification.getClickActionUrl());
        return response;
    }
}
