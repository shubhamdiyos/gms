package com.gms.service.impl;

import com.gms.model.entity.*;
import com.gms.enums.RoleEnum;
import com.gms.exception.NotFoundException;
import com.gms.model.request.StudentRequest;
import com.gms.model.response.StudentResponse;
import com.gms.repository.ClassroomRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.SectionRepository;
import com.gms.repository.StudentRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.StudentService;
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

@Service
@Transactional
public class StudentServiceImpl extends AbstractCRUDService<Student, Integer> implements StudentService {

    private final StudentRepository studentRepository;
    private final SchoolRepository schoolRepository;
    private final ClassroomRepository classroomRepository;
    private final SectionRepository sectionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public StudentServiceImpl(StudentRepository studentRepository, SchoolRepository schoolRepository, ClassroomRepository classroomRepository, SectionRepository sectionRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        super(studentRepository);
        this.studentRepository = studentRepository;
        this.schoolRepository = schoolRepository;
        this.classroomRepository = classroomRepository;
        this.sectionRepository = sectionRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
        if (userRepository.existsByEmail(student.getEmail())) {
            // Or link to existing user if that's a desired feature
            throw new IllegalStateException("A user with this email already exists.");
        }

        User user = new User();
        user.setSchool(student.getSchool());
        user.setStudent(student);
        user.setFullName(student.getFullName());
        user.setEmail(student.getEmail());
        user.setUsername(UsernameGenerator.generateUsername(student.getFirstName(), student.getLastName(), userRepository));
        user.setPassword(passwordEncoder.encode("welcome123")); // Default temporary password
        user.setRoles(Collections.singleton(RoleEnum.STUDENT.name()));
        user.setRequirePasswordChange(true);
        user.setEnabled(true);

        userRepository.save(user);
    }

    @Override
    public Student createStudentFromRequest(StudentRequest request, Student student, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            throw new IllegalArgumentException("Employee ID or School ID cannot be null");
        }

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        Classroom classroom = classroomRepository.findById(request.getClassId())
                .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));
        if (!classroom.getSchool().getId().equals(schoolId)) {
            throw new IllegalArgumentException("Classroom does not belong to current school");
        }

        Section section = null;
        if (request.getSectionId() != null) {
            section = sectionRepository.findById(request.getSectionId())
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
            Classroom classroom = classroomRepository.findById(request.getClassId())
                    .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));
            if (!classroom.getSchool().getId().equals(schoolId)) {
                throw new IllegalArgumentException("Classroom does not belong to current school");
            }
            student.setClassroom(classroom);
        }
        if (request.getSectionId() != null) {
            Section section = sectionRepository.findById(request.getSectionId())
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
        User user = userRepository.findByUsername(username)
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
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Student student = user.getStudent();
        if (student == null) {
            throw new NotFoundException("STUDENT_NOT_FOUND", "Student profile not found for this user");
        }

        return toResponse(student);
    }
}
