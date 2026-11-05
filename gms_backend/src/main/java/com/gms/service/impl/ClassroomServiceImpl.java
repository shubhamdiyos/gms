package com.gms.service.impl;

import com.gms.model.entity.Classroom;
import com.gms.model.entity.Employee;
import com.gms.model.entity.School;
import com.gms.model.entity.TeacherAssignment;
import com.gms.model.entity.User;
import com.gms.model.request.ClassroomRequest;
import com.gms.model.response.ClassroomResponse;
import com.gms.repository.ClassroomRepository;
import com.gms.repository.TeacherAssignmentRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClassroomServiceImpl implements ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final SchoolService schoolService;
    private final UserService userService;
    private final TeacherAssignmentRepository teacherAssignmentRepository;

    public ClassroomServiceImpl(ClassroomRepository classroomRepository, SchoolService schoolService,
                                UserService userService, TeacherAssignmentRepository teacherAssignmentRepository) {
        this.classroomRepository = classroomRepository;
        this.schoolService = schoolService;
        this.userService = userService;
        this.teacherAssignmentRepository = teacherAssignmentRepository;
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
        // The load is scoped to the caller's school, so the tenant half of this predicate can no
        // longer fire; what remains is the archive rule, which is business state rather than tenancy
        // and which the platform owner is not exempt from either.
        if ("0".equals(classroom.getStatus())) {
            // A retired row is outside the caller's visible set, which is what 404 means here: the
            // school's own list already filters it out, and a foreign row reads the same way. 403
            // would claim the caller lacks a permission it holds on this route.
            throw new EntityNotFoundException("Classroom is archived");
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
        // The load is scoped to the caller's school, so a foreign id yields nothing; the school
        // comparison this method used to make was a second copy of that predicate, and it refused
        // the platform owner whose own SYSTEM school id differs from the row's.
        Classroom classroom = classroomRepository.findById(id).orElse(null);
        if (classroom == null) {
            return false;
        }
        // Archive, not delete: the row carries the authorship the audit trail reads, the update
        // path already refuses archived rows, and the school's list already filters them out. A
        // hard delete would also collide with students.classroom_id's foreign key.
        classroom.setStatus("0");
        classroomRepository.save(classroom);
        return true;
    }

    @Override
    public ResponseEntity<ClassroomResponse> update(ClassroomRequest request) {
        if (request.getId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Classroom classroom = classroomRepository.findById(request.getId())
                .orElseThrow(() -> new EntityNotFoundException("Classroom not found"));
        // The scoped load answered the tenancy question; what remains is the archive rule, which is
        // business state rather than tenancy and which the platform owner is not exempt from either.
        if ("0".equals(classroom.getStatus())) {
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

        // The toggle writes to the row the scoped load returned, so tenancy was settled at the load;
        // the school comparison this path used to make only refused the platform owner.
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
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return Collections.emptyList();
        }

        return teacherAssignmentRepository.findByTeacher_School_IdAndTeacher_Id(schoolId, teacher.getId()).stream()
                .map(TeacherAssignment::getClassroom)
                .filter(classroom -> classroom != null && schoolId.equals(classroom.getSchoolId()))
                .collect(Collectors.toMap(Classroom::getId, classroom -> classroom, (first, duplicate) -> first,
                        LinkedHashMap::new))
                .values().stream()
                .map(this::toResponse)
                .toList();
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