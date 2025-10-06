package com.gms.service.impl;

import com.gms.model.entity.*;
import com.gms.enums.RoleEnum;
import com.gms.exception.NotFoundException;
import com.gms.model.request.ParentRequest;
import com.gms.model.response.*;
import com.gms.repository.ParentRepository;
import com.gms.service.*;
import com.gms.util.SecurityUtil;
import com.gms.util.UsernameGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ParentServiceImpl extends AbstractCRUDService<Parent, Integer> implements ParentService {

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
        super(parentRepository);
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
    public ResponseEntity<ParentResponse> create(ParentRequest request, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        Parent parent = new Parent();
        Parent savedParent = parentRepository.save(parent);
        createUserForParent(savedParent, schoolId);
        return ResponseEntity.ok(toResponse(savedParent));
    }

    public ParentResponse createParentFromRequest(ParentRequest request, Parent parent, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            throw new IllegalArgumentException("Employee ID or School ID cannot be null");
        }
        
        // This method is a placeholder to match the pattern, but Parent creation is handled differently
        return toResponse(parent);
    }

    public ResponseEntity<ParentResponse> update(ParentRequest request, Integer empId, Integer schoolId) {
        // Parent update is handled differently, returning not implemented
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    public ResponseEntity<?> toggleParent(Integer id, Boolean isActive, Integer schoolId, Integer empId) {
        // Parent doesn't have a status field, returning not implemented
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public List<Parent> findBySchool(Integer schoolId) {
        // For now, return all parents since Parent doesn't have direct school relationship
        return parentRepository.findAll();
    }

    @Override
    public ResponseEntity<ParentResponse> createParent(ParentRequest parentRequest, Integer schoolId) {
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

        createUserForParent(savedParent, schoolId);

        return ResponseEntity.ok(toResponse(savedParent));
    }

    @Override
    public List<StudentResponse> getMyChildren() {
        String username = SecurityUtil.getUsernameFromToken();
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
        Parent parent = validateParentAndChild(username, studentId);
        
        // TODO: Implement proper attendance retrieval when attendance service methods are available
        return Collections.emptyList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResultResponse> getChildResults(String username, Integer studentId) {
        Parent parent = validateParentAndChild(username, studentId);
        
        // TODO: Implement proper result retrieval when result service methods are available
        return Collections.emptyList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentFeeResponse> getChildFees(String username, Integer studentId) {
        Parent parent = validateParentAndChild(username, studentId);
        
        // TODO: Implement proper fee retrieval when student fee service methods are available
        return Collections.emptyList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimetableResponse> getChildTimetable(String username, Integer studentId) {
        Parent parent = validateParentAndChild(username, studentId);
        
        // TODO: Implement proper timetable retrieval when timetable service methods are available
        return Collections.emptyList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getChildAnnouncements(String username, Integer studentId) {
        Parent parent = validateParentAndChild(username, studentId);
        
        // TODO: Implement proper announcement retrieval when announcement service methods are available
        return Collections.emptyList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectResponse> getChildSubjects(String username, Integer studentId) {
        Parent parent = validateParentAndChild(username, studentId);
        
        // TODO: Implement proper subject retrieval when subject service methods are available
        return Collections.emptyList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getParentNotifications(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Parent parent = user.getParent();
        if (parent == null) {
            throw new NotFoundException("PARENT_NOT_FOUND", "Parent profile not found for this user");
        }

        // TODO: Implement proper notification retrieval when notification service methods are available
        return Collections.emptyList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponse> getChildTeachers(String username, Integer studentId) {
        Parent parent = validateParentAndChild(username, studentId);
        
        // TODO: Implement proper teacher assignment retrieval when teacher assignment service methods are available
        return Collections.emptyList();
    }

    private Parent validateParentAndChild(String username, Integer studentId) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Parent parent = user.getParent();
        if (parent == null) {
            throw new NotFoundException("PARENT_NOT_FOUND", "Parent profile not found for this user");
        }

        boolean isParentOfStudent = parent.getStudents().stream()
                .anyMatch(student -> student.getId().equals(studentId));
        
        if (!isParentOfStudent) {
            throw new IllegalArgumentException("You are not authorized to access this student's information");
        }

        return parent;
    }

    private void createUserForParent(Parent parent, Integer schoolId) {
        if (userService.existsByEmail(parent.getEmail())) {
            throw new IllegalStateException("A user with this email already exists.");
        }

        User user = new User();
        user.setSchool(parent.getStudents().get(0).getSchool()); // Assuming all students are in the same school
        user.setParent(parent);
        user.setFullName(parent.getFirstName() + " " + parent.getLastName());
        user.setEmail(parent.getEmail());
        user.setUsername(UsernameGenerator.generateUsername(parent.getFirstName(), parent.getLastName(), userService));
        user.setPassword(passwordEncoder.encode("welcome123")); // Default temporary password
        user.setRoles(Collections.singleton(RoleEnum.PARENT.name()));
        user.setRequirePasswordChange(true);
        user.setEnabled(true);

        User savedUser = userService.save(user);
        
        parent.setUser(savedUser);
        parentRepository.save(parent);
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
                studentResponses
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
                s.getStatus()
        );
    }
}