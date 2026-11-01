package com.gms.service.impl;

import com.gms.exception.TenantAccessDeniedException;
import com.gms.model.entity.Classroom;
import com.gms.model.entity.Employee;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.ClassroomRequest;
import com.gms.model.response.ClassroomResponse;
import com.gms.repository.ClassroomRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.*;
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
public class ClassroomServiceImpl implements ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final SchoolService schoolService;
    private final UserService userService;

    public ClassroomServiceImpl(ClassroomRepository classroomRepository, SchoolService schoolService, UserService userService) {
        this.classroomRepository = classroomRepository;
        this.schoolService = schoolService;
        this.userService = userService;
    }

    @Override
    public ResponseEntity<ClassroomResponse> create(ClassroomRequest request) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Classroom classroom = createClassroomFromRequest(request, new Classroom());
        Classroom saved = classroomRepository.save(classroom);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    public Classroom createClassroomFromRequest(ClassroomRequest request, Classroom classroom) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            throw new IllegalArgumentException("Employee ID or School ID cannot be null");
        }

        School school = schoolService.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        boolean exists = classroomRepository.existsBySchool_IdAndNameAndGradeAndSection(
                TenantContext.schoolId(), request.getName(), request.getGrade(), request.getSection());
        if (exists) {
            throw new IllegalArgumentException("Classroom already exists with same name/grade/section");
        }
        BeanUtils.copyProperties(request, classroom, "id", "createdAt", "updatedAt", "version");
        classroom.setSchool(school);
        return classroom;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Classroom> findBySchool() {
        return classroomRepository.findAllBySchool_IdAndStatusNot(TenantContext.schoolId(), "0");
    }

    @Override
    @Transactional(readOnly = true)
    public Classroom getById(Integer id) {
        Classroom classroom = classroomRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));
        if (!classroom.getSchoolId().equals(TenantContext.schoolId()) || "0".equals(classroom.getStatus())) {
            throw new TenantAccessDeniedException("Classroom does not belong to current school");
        }
        return classroom;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Classroom> findById(Integer id) {
        return classroomRepository.findById(id);
    }

    @Override
    @Transactional
    public boolean deleteWithinSchool(Integer id) {
        Classroom classroom = classroomRepository.findById(id).orElse(null);
        if (classroom == null) {
            return false;
        }
        if (!TenantContext.schoolId().equals(classroom.getSchoolId())) {
            return false;
        }
        classroomRepository.delete(classroom);
        return true;
    }

    @Override
    public ResponseEntity<ClassroomResponse> update(ClassroomRequest request) {
        if (request.getId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Classroom classroom = classroomRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));
        if (!classroom.getSchoolId().equals(TenantContext.schoolId()) || "0".equals(classroom.getStatus())) {
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

        School school = schoolService.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        classroom.setSchool(school);

        Classroom saved = classroomRepository.save(classroom);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    public ResponseEntity<?> toggleClassroom(Integer id, Boolean isActive) {
        Classroom existing = classroomRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));

        if (!existing.getSchool().getId().equals(TenantContext.schoolId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        existing.setStatus(isActive ? "1" : "0");

        School school = schoolService.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        existing.setSchool(school);

        Classroom saved = classroomRepository.save(existing);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassroomResponse> findClassroomsForAuthenticatedTeacher() {
        String username = TenantContext.username();
        if (username == null) {
            return Collections.emptyList();
        }

        Optional<User> userOptional = userService.findByUsername(username);
        if (userOptional.isEmpty()) {
            return Collections.emptyList();
        }

        User user = userOptional.get();
        Employee teacher = user.getEmployee();
        if (teacher == null) {
            return Collections.emptyList();
        }

        // For now, using a simpler approach - return empty list
        // This method needs to be enhanced when proper service methods are available
        return Collections.emptyList();
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
}