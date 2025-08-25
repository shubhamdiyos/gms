package com.gms.service.impl;

import com.gms.model.entity.*;
import com.gms.enums.RoleEnum;
import com.gms.exception.NotFoundException;
import com.gms.model.request.StudentRequest;
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
import com.gms.service.*;
import com.gms.util.SecurityUtil;
import com.gms.util.UsernameGenerator;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDate;

@Service
@Transactional
public class StudentServiceImpl extends AbstractCRUDService<Student, Integer> implements StudentService {

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

    public StudentServiceImpl(StudentRepository studentRepository, SchoolService schoolService, ClassroomService classroomService, SectionService sectionService, UserService userService, PasswordEncoder passwordEncoder, ResultService resultService, StudentFeeService studentFeeService, TimetableService timetableService, AnnouncementService announcementService, SubjectService subjectService, NotificationService notificationService, TeacherAssignmentService teacherAssignmentService) {
        super(studentRepository);
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
    }

    @Override
    public ResponseEntity<StudentResponse> create(StudentRequest request, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            return ResponseEntity.badRequest().build();
        }

        Student student = createStudentFromRequest(request, new Student(), empId, schoolId);
        Student savedStudent = studentRepository.save(student);

        // Create a corresponding User for the Student
        createUserForStudent(savedStudent);

        return ResponseEntity.ok(toResponse(savedStudent));
    }

    private void createUserForStudent(Student student) {
        if (userService.existsByEmail(student.getEmail())) {
            // Or link to existing user if that's a desired feature
            throw new IllegalStateException("A user with this email already exists.");
        }

        User user = new User();
        user.setSchool(student.getSchool());
        user.setStudent(student);
        user.setFullName(student.getFullName());
        user.setEmail(student.getEmail());
        user.setUsername(UsernameGenerator.generateUsername(student.getFirstName(), student.getLastName(), userService));
        user.setPassword(passwordEncoder.encode("welcome123")); // Default temporary password
        user.setRoles(Collections.singleton(RoleEnum.STUDENT.name()));
        user.setRequirePasswordChange(true);
        user.setEnabled(true);

        userService.save(user);
    }

    @Override
    public Student createStudentFromRequest(StudentRequest request, Student student, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            throw new IllegalArgumentException("Employee ID or School ID cannot be null");
        }

        School school = schoolService.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        Classroom classroom = classroomService.findById(request.getClassId())
                .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));
        if (!classroom.getSchool().getId().equals(schoolId)) {
            throw new IllegalArgumentException("Classroom does not belong to current school");
        }

        Section section = null;
        if (request.getSectionId() != null) {
            section = sectionService.findById(request.getSectionId())
                    .orElseThrow(() -> new EntityNotFoundException("Section not found"));
            if (!section.getSchoolId().equals(schoolId)) {
                throw new IllegalArgumentException("Section does not belong to current school");
            }
        }

        org.springframework.beans.BeanUtils.copyProperties(request, student, "classId", "sectionId", "schoolId", "id", "status", "studentId");
        student.setSchool(school);
        student.setClassroom(classroom);
        student.setSection(section);

        // Generate a simple unique studentId within the school: STD###
        String generatedId = generateStudentId(schoolId);
        student.setStudentId(generatedId);
        student.setStatus("1");

        return student;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Student> findBySchool(Integer schoolId) {
        return studentRepository.findAllBySchool_IdAndStatusNot(schoolId, "0");
    }

    @Override
    @Transactional(readOnly = true)
    public Student getById(Integer id, Integer schoolId) {
        Student student = studentRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Student not found"));
        if (!student.getSchoolId().equals(schoolId) || "0".equals(student.getStatus())) {
            throw new IllegalArgumentException("Student does not belong to current school");
        }
        return student;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Student> findById(Integer id) {
        return studentRepository.findById(id);
    }

    @Override
    public ResponseEntity<StudentResponse> update(StudentRequest request, Integer empId, Integer schoolId) {
        if (request.getId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Student student = studentRepository.findById(request.getId()).orElseThrow(() -> new EntityNotFoundException("Student not found"));
        if (!student.getSchoolId().equals(schoolId) || "0".equals(student.getStatus())) {
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
            if (!classroom.getSchool().getId().equals(schoolId)) {
                throw new IllegalArgumentException("Classroom does not belong to current school");
            }
            student.setClassroom(classroom);
        }
        if (request.getSectionId() != null) {
            Section section = sectionService.findById(request.getSectionId())
                    .orElseThrow(() -> new EntityNotFoundException("Section not found"));
            if (!section.getSchoolId().equals(schoolId)) {
                throw new IllegalArgumentException("Section does not belong to current school");
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
    public ResponseEntity<?> toggleStudent(Integer id, Boolean isActive, Integer schoolId, Integer empId) {
        Student existing = studentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));

        if (!existing.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        existing.setStatus(isActive ? "1" : "0");

        Student saved = studentRepository.save(existing);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public StudentResponse getMyProfile() {
        String username = SecurityUtil.getUsernameFromToken();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }

        return toResponse(student);
    }

    private String generateStudentId(Integer schoolId) {
        int counter = (int) (studentRepository.count() + 1); // coarse; acceptable for now
        String candidate;
        int attempts = 0;
        do {
            candidate = String.format("STD%03d", counter + attempts);
            attempts++;
        } while (studentRepository.existsBySchool_IdAndStudentId(schoolId, candidate) && attempts < 10000);
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
                s.getStatus()
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

        // Get subjects for the student's classroom using service
        // Temporarily simplified - will be enhanced when proper service methods are available
        List<Subject> subjects = subjectService.findBySchool(student.getSchoolId());
        return subjects.stream()
                .map(this::toSubjectResponse)
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

        // TODO: Implement attendance retrieval when circular dependency is resolved
        // For now, return empty list to prevent circular dependency
        return Collections.emptyList();
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

        // Use result service to get student results (placeholder - need proper service method)
        // Temporarily using a mock implementation since ResultService.findBySchool doesn't exist yet
        List<Result> results = Collections.emptyList(); // TODO: Implement proper service method
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

        // Use student fee service to get fees (placeholder - need proper service method)
        // Temporarily using a mock implementation since StudentFeeService.findBySchool doesn't exist yet
        List<StudentFee> studentFees = Collections.emptyList(); // TODO: Implement proper service method
        return studentFees.stream()
                .map(this::toStudentFeeResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimetableResponse> getMyTimetable(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }

        // Use timetable service to get classroom timetable
        // Temporarily using a mock implementation since TimetableService.findBySchool doesn't exist yet
        List<Timetable> timetables = Collections.emptyList(); // TODO: Implement proper service method
        return timetables.stream()
                .map(this::toTimetableResponse)
                .collect(Collectors.toList());
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

        // Use teacher assignment service to get classroom teachers
        // Temporarily using a mock implementation since TeacherAssignmentService.findBySchool doesn't exist yet
        List<TeacherAssignment> assignments = Collections.emptyList(); // TODO: Implement proper service method
        return assignments.stream()
                .map(assignment -> toEmployeeResponse(assignment.getTeacher()))
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

        // Use announcement service to get school announcements
        // Temporarily using a mock implementation since AnnouncementService.findBySchool doesn't exist yet
        List<Announcement> announcements = Collections.emptyList(); // TODO: Implement proper service method
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

        // Use notification service to get user notifications
        // Temporarily using a mock implementation since NotificationService.findBySchool doesn't exist yet
        List<Notification> notifications = Collections.emptyList(); // TODO: Implement proper service method
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
        // Simplified response to avoid field access issues
        ResultResponse response = new ResultResponse();
        response.setId(result.getId());
        // TODO: Map other fields when entity structure is confirmed
        return response;
    }

    private StudentFeeResponse toStudentFeeResponse(StudentFee studentFee) {
        // Simplified response to avoid field access issues
        StudentFeeResponse response = new StudentFeeResponse();
        response.setId(studentFee.getId());
        response.setStudentId(studentFee.getStudent().getId());
        // TODO: Map other fields when entity structure is confirmed
        return response;
    }

    private TimetableResponse toTimetableResponse(Timetable timetable) {
        // Simplified response to avoid field access issues
        TimetableResponse response = new TimetableResponse();
        response.setId(timetable.getId());
        response.setClassroomId(timetable.getClassroom().getId());
        // TODO: Map other fields when entity structure is confirmed
        return response;
    }

    private EmployeeResponse toEmployeeResponse(Employee employee) {
        // Simplified response to avoid field access issues
        EmployeeResponse response = new EmployeeResponse();
        response.setId(employee.getId());
        response.setFullName(employee.getFullName());
        response.setEmail(employee.getEmail());
        // TODO: Map other fields when entity structure is confirmed
        return response;
    }

    private AnnouncementResponse toAnnouncementResponse(Announcement announcement) {
        // Simplified response to avoid field access issues
        AnnouncementResponse response = new AnnouncementResponse();
        response.setId(announcement.getId());
        response.setTitle(announcement.getTitle());
        response.setContent(announcement.getContent());
        // TODO: Map other fields when entity structure is confirmed
        return response;
    }

    private NotificationResponse toNotificationResponse(Notification notification) {
        // Simplified response to avoid field access issues
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setTitle(notification.getTitle());
        response.setMessage(notification.getMessage());
        // TODO: Map other fields when entity structure is confirmed
        return response;
    }
}
