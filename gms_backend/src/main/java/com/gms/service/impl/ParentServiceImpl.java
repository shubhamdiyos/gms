package com.gms.service.impl;

import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.*;
import com.gms.enums.RoleEnum;
import com.gms.exception.NotFoundException;
import com.gms.model.request.ParentRequest;
import com.gms.model.response.*;
import com.gms.repository.ParentRepository;
import com.gms.security.TenantContext;
import com.gms.service.*;
import com.gms.util.SecureCredentialGenerator;
import com.gms.util.UsernameGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ParentServiceImpl implements ParentService {

    private static final Set<String> PARENT_VISIBLE_AUDIENCES = Set.of("ALL", "STUDENTS", "PARENTS");

    private final ParentRepository parentRepository;
    private final StudentService studentService;
    private final UserService userService;
    private final SchoolService schoolService;
    private final PasswordEncoder passwordEncoder;
    private final AttendanceService attendanceService;
    private final ResultService resultService;
    private final StudentFeeService studentFeeService;
    private final TimetableService timetableService;
    private final AnnouncementService announcementService;
    private final SubjectService subjectService;
    private final NotificationService notificationService;
    private final TeacherAssignmentService teacherAssignmentService;

    public ParentServiceImpl(ParentRepository parentRepository, StudentService studentService, 
                            UserService userService, SchoolService schoolService, 
                            PasswordEncoder passwordEncoder, AttendanceService attendanceService,
                            ResultService resultService, StudentFeeService studentFeeService,
                            TimetableService timetableService, AnnouncementService announcementService,
                            SubjectService subjectService, NotificationService notificationService,
                            TeacherAssignmentService teacherAssignmentService) {
        this.parentRepository = parentRepository;
        this.studentService = studentService;
        this.userService = userService;
        this.schoolService = schoolService;
        this.passwordEncoder = passwordEncoder;
        this.attendanceService = attendanceService;
        this.resultService = resultService;
        this.studentFeeService = studentFeeService;
        this.timetableService = timetableService;
        this.announcementService = announcementService;
        this.subjectService = subjectService;
        this.notificationService = notificationService;
        this.teacherAssignmentService = teacherAssignmentService;
    }

    @Override
    public ResponseEntity<ParentResponse> create(ParentRequest request) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            return ResponseEntity.badRequest().build();
        }
        // This used to save an empty Parent and crash in createUserForParent (get(0) on the empty
        // students collection), so the route this controller serves 500'd on every call. The real
        // implementation is createParent: it resolves the request's student ids through the scoped
        // loads and writes the join rows with them.
        return createParent(request);
    }

    public ParentResponse createParentFromRequest(ParentRequest request, Parent parent) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            throw new IllegalArgumentException("Employee ID or School ID cannot be null");
        }
        
        // This method is a placeholder to match the pattern, but Parent creation is handled differently
        return toResponse(parent);
    }

    @Override
    public ResponseEntity<ParentResponse> update(ParentRequest request) {
        if (request.getId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Parent parent = findParentForCurrentSchool(request.getId());
        parentRepository.findByEmail(request.getEmail())
                .filter(existing -> !existing.getId().equals(parent.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("A parent with this email already exists.");
                });
        if (!parent.getEmail().equals(request.getEmail()) && userService.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("A user with this email already exists.");
        }

        List<Student> students = request.getStudentIds().stream()
                .distinct()
                .map(id -> studentService.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Student not found with id: " + id)))
                .collect(Collectors.toList());

        parent.setFirstName(request.getFirstName());
        parent.setLastName(request.getLastName());
        parent.setEmail(request.getEmail());
        parent.setPhoneNumber(request.getPhoneNumber());
        parent.setStudents(students);

        User user = parent.getUser();
        if (user != null) {
            user.setFullName(parent.getFullName());
            user.setEmail(parent.getEmail());
            user.setPhoneNumber(parent.getPhoneNumber());
            userService.save(user);
        }

        return ResponseEntity.ok(toResponse(parentRepository.save(parent)));
    }

    @Override
    public ResponseEntity<?> toggleParent(Integer id, Boolean isActive) {
        Parent parent = findParentForCurrentSchool(id);
        User user = parent.getUser();
        if (user == null) {
            throw new NotFoundException("USER_NOT_FOUND", "Parent login account not found");
        }

        user.setEnabled(isActive);
        user.setUserStatus(isActive ? "1" : "0");
        userService.save(user);
        return ResponseEntity.ok(toResponse(parent));
    }

    private Parent findParentForCurrentSchool(Integer id) {
        Parent parent = parentRepository.findBySchoolIdAndId(TenantContext.schoolId(), id)
                .orElseThrow(() -> new EntityNotFoundException("Parent not found"));
        if (parent.getStudents().stream()
                .anyMatch(student -> !student.getSchoolId().equals(TenantContext.schoolId()))) {
            throw new TenantAccessDeniedException("Parent does not belong to this school");
        }
        return parent;
    }

    @Override
    public List<ParentResponse> findBySchool() {
        // Responses, not entities: Parent carries a lazy user and a lazy students collection, and
        // with open-in-view=false the message converter would meet them after the session closed.
        // The mapping below runs inside the class-level transaction, while the proxies still load.
        return parentRepository.findBySchoolId(TenantContext.schoolId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ResponseEntity<ParentResponse> createParent(ParentRequest parentRequest) {
        Parent parent = new Parent();
        BeanUtils.copyProperties(parentRequest, parent);

        // Use StudentService instead of direct repository access
        List<Student> students = parentRequest.getStudentIds().stream()
                .map(id -> {
                    Optional<Student> studentOpt = studentService.findById(id);
                    return studentOpt.orElseThrow(() -> new EntityNotFoundException("Student not found with id: " + id));
                })
                .collect(Collectors.toList());
        
        if (students.isEmpty()) {
            throw new EntityNotFoundException("No students found for the provided IDs");
        }
        parent.setStudents(students);

        Parent savedParent = parentRepository.save(parent);

        // ACD-13-02: generate a unique secure temporary password instead of a shared hardcoded one.
        String temporaryPassword = createUserForParent(savedParent);

        ParentResponse response = toResponse(savedParent);
        response.setTemporaryPassword(temporaryPassword);
        return ResponseEntity.ok(response);
    }

    @Override
    public List<StudentResponse> getMyChildren() {
        String username = TenantContext.username();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Parent parent = user.getParent();
        if (parent == null) {
            throw new NotFoundException("PARENT_NOT_FOUND", "Parent profile not found for this user");
        }

        return parent.getStudents().stream()
                .map(this::toStudentResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ParentResponse getParentProfile(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Parent parent = user.getParent();
        if (parent == null) {
            throw new NotFoundException("PARENT_NOT_FOUND", "Parent profile not found for this user");
        }

        return toResponse(parent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentResponse> getChildren(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Parent parent = user.getParent();
        if (parent == null) {
            throw new NotFoundException("PARENT_NOT_FOUND", "Parent profile not found for this user");
        }

        return parent.getStudents().stream()
                .map(this::toStudentResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getChildAttendance(String username, Integer studentId) {
        Student student = validateParentAndChild(username, studentId);
        return attendanceService.getAttendanceByStudentAndDateRange(
                student.getId(), LocalDate.now().minusMonths(1), LocalDate.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResultResponse> getChildResults(String username, Integer studentId) {
        Student student = validateParentAndChild(username, studentId);
        ResponseEntity<List<Result>> resultResponse = resultService.getResultsForStudent(student.getId());
        if (resultResponse.getBody() != null) {
            return resultResponse.getBody().stream()
                    .map(this::mapResultToResponse)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentFeeResponse> getChildFees(String username, Integer studentId) {
        Student student = validateParentAndChild(username, studentId);
        ResponseEntity<List<StudentFee>> feeResponse = studentFeeService.getStudentFeesByStudent(student.getId());
        if (feeResponse.getBody() != null) {
            return feeResponse.getBody().stream()
                    .map(this::mapStudentFeeToResponse)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimetableResponse> getChildTimetable(String username, Integer studentId) {
        validateParentAndChild(username, studentId);
        try {
            return List.of(timetableService.getTimetableForStudent(studentId));
        } catch (NotFoundException ex) {
            if ("TIMETABLE_NOT_FOUND".equals(ex.getCode())) {
                return List.of();
            }
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getChildAnnouncements(String username, Integer studentId) {
        validateParentAndChild(username, studentId);
        List<Announcement> announcements = announcementService.getActiveAnnouncements().getBody();
        if (announcements == null) {
            return List.of();
        }
        return announcements.stream()
                .filter(announcement -> PARENT_VISIBLE_AUDIENCES.contains(announcement.getTargetAudience()))
                .map(this::mapAnnouncementToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectResponse> getChildSubjects(String username, Integer studentId) {
        Student student = validateParentAndChild(username, studentId);
        if (student.getClassroom() == null) {
            return List.of();
        }
        return teacherAssignmentService.getAssignmentsByClassroom(student.getClassroom().getId()).stream()
                .map(TeacherAssignmentResponse::getSubject)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(SubjectResponse::getId, Function.identity(),
                        (first, duplicate) -> first, LinkedHashMap::new))
                .values().stream()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getParentNotifications(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        if (user.getParent() == null) {
            throw new NotFoundException("PARENT_NOT_FOUND", "Parent profile not found for this user");
        }

        List<Notification> notifications = notificationService.getAllNotifications().getBody();
        if (notifications == null) {
            return List.of();
        }
        return notifications.stream()
                .map(this::mapNotificationToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponse> getChildTeachers(String username, Integer studentId) {
        Student student = validateParentAndChild(username, studentId);
        if (student.getClassroom() == null) {
            return List.of();
        }
        return teacherAssignmentService.getAssignmentsByClassroom(student.getClassroom().getId()).stream()
                .map(TeacherAssignmentResponse::getTeacher)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(EmployeeResponse::getId, Function.identity(),
                        (first, duplicate) -> first, LinkedHashMap::new))
                .values().stream()
                .toList();
    }

    private Student validateParentAndChild(String username, Integer studentId) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Parent parent = user.getParent();
        if (parent == null) {
            throw new NotFoundException("PARENT_NOT_FOUND", "Parent profile not found for this user");
        }

        return parent.getStudents().stream()
                .filter(student -> student.getId().equals(studentId))
                .findFirst()
                .orElseThrow(() -> new TenantAccessDeniedException(
                        "You are not authorized to access this student's information"));
    }

    /**
     * ACD-13-02: generates a unique secure temporary password for the parent account.
     *
     * @return the generated plaintext temporary password (returned once to the admin for relay)
     */
    private String createUserForParent(Parent parent) {
        if (userService.existsByEmail(parent.getEmail())) {
            throw new IllegalStateException("A user with this email already exists.");
        }

        String temporaryPassword = SecureCredentialGenerator.generate();

        User user = new User();
        user.setSchool(parent.getStudents().get(0).getSchool());
        user.setParent(parent);
        user.setFullName(parent.getFirstName() + " " + parent.getLastName());
        user.setEmail(parent.getEmail());
        user.setUsername(UsernameGenerator.generateUsername(parent.getFirstName(), parent.getLastName(), userService));
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setRoles(Collections.singleton(RoleEnum.PARENT.name()));
        user.setRequirePasswordChange(true);
        user.setEnabled(true);

        User savedUser = userService.save(user);
        
        parent.setUser(savedUser);
        parentRepository.save(parent);
        return temporaryPassword;
    }

    // Service-to-service communication methods
    @Override
    @Transactional(readOnly = true)
    public Optional<Parent> findById(Integer id) {
        return parentRepository.findById(id);
    }

    @Override
    public Parent save(Parent parent) {
        return parentRepository.save(parent);
    }

    @Override
    public boolean existsByEmail(String email) {
        return parentRepository.existsByEmail(email);
    }

    private ParentResponse toResponse(Parent parent) {
        List<StudentResponse> studentResponses = parent.getStudents().stream()
                .map(this::toStudentResponse)
                .collect(Collectors.toList());

        return new ParentResponse(
                parent.getId(),
                parent.getFirstName(),
                parent.getLastName(),
                parent.getEmail(),
                parent.getPhoneNumber(),
                studentResponses,
                null,
                parent.getUser() != null ? parent.getUser().isEnabled() : null
        );
    }

    private StudentResponse toStudentResponse(Student s) {
        return new StudentResponse(
                s.getId(),
                s.getSchool().getId(),
                s.getClassroom().getId(),
                s.getSection() != null ? s.getSection().getId() : null,
                s.getStudentId(),
                s.getFirstName(),
                s.getLastName(),
                s.getEmail(),
                s.getPhoneNumber(),
                s.getDateOfBirth(),
                s.getGender(),
                s.getStatus(),
                null
        );
    }

    private ResultResponse mapResultToResponse(Result result) {
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

    private StudentFeeResponse mapStudentFeeToResponse(StudentFee studentFee) {
        return StudentFeeResponseMapper.toResponse(studentFee);
    }

    private AnnouncementResponse mapAnnouncementToResponse(Announcement announcement) {
        AnnouncementResponse response = new AnnouncementResponse();
        response.setId(announcement.getId());
        response.setSchoolId(announcement.getSchool().getId());
        response.setTitle(announcement.getTitle());
        response.setContent(announcement.getContent());
        response.setAnnouncementType(announcement.getAnnouncementType());
        response.setTargetAudience(announcement.getTargetAudience());
        response.setPublishDate(announcement.getPublishDate());
        response.setExpiryDate(announcement.getExpiryDate());
        response.setPriority(announcement.getPriority());
        response.setCategory(announcement.getCategory());
        response.setIsPinned(announcement.getIsPinned());
        return response;
    }

    private NotificationResponse mapNotificationToResponse(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setSchoolId(notification.getSchool().getId());
        response.setTitle(notification.getTitle());
        response.setMessage(notification.getMessage());
        response.setNotificationType(notification.getNotificationType());
        response.setChannel(notification.getChannel());
        response.setPriority(notification.getPriority());
        response.setRecipientType(notification.getRecipientType());
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