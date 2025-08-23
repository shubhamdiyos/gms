package com.gms.service.impl;

import com.gms.model.entity.Classroom;
import com.gms.model.entity.Employee;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.ClassroomRequest;
import com.gms.model.response.ClassroomResponse;
import com.gms.repository.ClassroomRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.TeacherAssignmentRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.ClassroomService;
import com.gms.util.SecurityUtil;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClassroomServiceImpl extends AbstractCRUDService<Classroom, Integer> implements ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;

    public ClassroomServiceImpl(ClassroomRepository classroomRepository, SchoolRepository schoolRepository, UserRepository userRepository, TeacherAssignmentRepository teacherAssignmentRepository) {
        super(classroomRepository);
        this.classroomRepository = classroomRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
        this.teacherAssignmentRepository = teacherAssignmentRepository;
    }

    @Override
    public ResponseEntity<ClassroomResponse> create(ClassroomRequest request, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            return ResponseEntity.badRequest().build();
        }

        Classroom classroom = createClassroomFromRequest(request, new Classroom(), empId, schoolId);
        Classroom saved = super.create(classroom);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    public Classroom createClassroomFromRequest(ClassroomRequest request, Classroom classroom, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            throw new IllegalArgumentException("Employee ID or School ID cannot be null");
        }

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        boolean exists = classroomRepository.existsBySchool_IdAndNameAndGradeAndSection(
                schoolId, request.getName(), request.getGrade(), request.getSection());
        if (exists) {
            throw new IllegalArgumentException("Classroom already exists with same name/grade/section");
        }
        BeanUtils.copyProperties(request, classroom, "id", "createdAt", "updatedAt", "version");
        classroom.setSchool(school);
        return classroom;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Classroom> findBySchool(Integer schoolId) {
        return classroomRepository.findAllBySchool_IdAndStatusNot(schoolId, "0");
    }

    @Override
    @Transactional(readOnly = true)
    public Classroom getById(Integer id, Integer schoolId) {
        Classroom classroom = classroomRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));
        if (!classroom.getSchoolId().equals(schoolId) || "0".equals(classroom.getStatus())) {
            throw new IllegalArgumentException("Classroom does not belong to current school");
        }
        return classroom;
    }

    @Override
    public ResponseEntity<ClassroomResponse> update(ClassroomRequest request, Integer empId, Integer schoolId) {
        if (request.getId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Classroom classroom = classroomRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));
        if (!classroom.getSchoolId().equals(schoolId) || "0".equals(classroom.getStatus())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        BeanUtils.copyProperties(
                request,
                classroom,
                "id",
                "createdAt",
                "updatedAt",
                "version",
                "school"
        );

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        classroom.setSchool(school);

        Classroom saved = classroomRepository.save(classroom);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    public ResponseEntity<?> toggleClassroom(Integer id, Boolean isActive, Integer schoolId, Integer empId) {
        Classroom existing = classroomRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));

        if (!existing.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        existing.setStatus(isActive ? "1" : "0");

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        existing.setSchool(school);

        Classroom saved = classroomRepository.save(existing);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassroomResponse> findClassroomsForAuthenticatedTeacher() {
        String username = SecurityUtil.getUsernameFromToken();
        if (username == null) {
            return Collections.emptyList();
        }

        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            return Collections.emptyList();
        }

        User user = userOptional.get();
        Employee teacher = user.getEmployee();
        if (teacher == null) {
            return Collections.emptyList();
        }

        return teacherAssignmentRepository.findByTeacherId(teacher.getId()).stream()
                .map(assignment -> toResponse(assignment.getClassroom()))
                .distinct()
                .collect(Collectors.toList());
    }

    private ClassroomResponse toResponse(Classroom classroom) {
        return new ClassroomResponse(
                classroom.getId(),
                classroom.getSchoolId(),
                classroom.getName(),
                classroom.getGrade(),
                classroom.getSection()
        );
    }

    // Method to get repository for AbstractCRUDService compatibility
    public ClassroomRepository getRepository() {
        return classroomRepository;
    }
}