package com.gms.service.impl;

import com.gms.model.entity.Employee;
import com.gms.model.entity.School;
import com.gms.model.entity.Subject;
import com.gms.model.entity.User;
import com.gms.model.request.SubjectRequest;
import com.gms.model.response.SubjectResponse;
import com.gms.repository.SchoolRepository;
import com.gms.repository.SubjectRepository;
import com.gms.repository.TeacherAssignmentRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.SubjectService;
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
public class SubjectServiceImpl extends AbstractCRUDService<Subject, Integer> implements SubjectService {

    private final SubjectRepository subjectRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;

    public SubjectServiceImpl(SubjectRepository subjectRepository, SchoolRepository schoolRepository, UserRepository userRepository, TeacherAssignmentRepository teacherAssignmentRepository) {
        super(subjectRepository);
        this.subjectRepository = subjectRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
        this.teacherAssignmentRepository = teacherAssignmentRepository;
    }

    @Override
    public ResponseEntity<SubjectResponse> create(SubjectRequest request, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            return ResponseEntity.badRequest().build();
        }

        Subject subject = createSubjectFromRequest(request, new Subject(), empId, schoolId);
        Subject saved = super.create(subject);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    public Subject createSubjectFromRequest(SubjectRequest request, Subject subject, Integer empId, Integer schoolId) {
        if (empId == null || schoolId == null) {
            throw new IllegalArgumentException("Employee ID or School ID cannot be null");
        }

        if (subjectRepository.existsBySchool_IdAndCode(schoolId, request.getCode())) {
            throw new IllegalArgumentException("Subject code already exists in this school");
        }

        BeanUtils.copyProperties(request, subject, "id", "createdAt", "updatedAt", "version");

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        subject.setSchool(school);

        return subject;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Subject> findBySchool(Integer schoolId) {
        return subjectRepository.findAllBySchool_IdAndStatusNot(schoolId, "0");
    }

    @Override
    @Transactional(readOnly = true)
    public Subject getById(Integer id, Integer schoolId) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));
        if (!subject.getSchoolId().equals(schoolId) || "0".equals(subject.getStatus())) {
            throw new IllegalArgumentException("Subject does not belong to current school");
        }
        return subject;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Subject> findById(Integer id) {
        return subjectRepository.findById(id);
    }

    @Override
    public ResponseEntity<SubjectResponse> update(SubjectRequest request, Integer empId, Integer schoolId) {
        // For Subject, we need to get the ID from the path parameter, not from request
        // This will be handled by the controller
        throw new UnsupportedOperationException("Update method requires ID parameter from controller");
    }

    @Override
    public ResponseEntity<SubjectResponse> updateSubject(Integer id, SubjectRequest request, Integer empId, Integer schoolId) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));

        if (!subject.getSchoolId().equals(schoolId) || "0".equals(subject.getStatus())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        BeanUtils.copyProperties(
                request,
                subject,
                "id",
                "createdAt",
                "updatedAt",
                "version",
                "school"
        );

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        subject.setSchool(school);

        Subject saved = subjectRepository.save(subject);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    public ResponseEntity<?> toggleSubject(Integer id, Boolean isActive, Integer schoolId, Integer empId) {
        Subject existing = subjectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));

        if (!existing.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        existing.setStatus(isActive ? "1" : "0");

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        existing.setSchool(school);

        Subject saved = subjectRepository.save(existing);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectResponse> findSubjectsForAuthenticatedTeacher() {
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

        return teacherAssignmentRepository.findByTeacher_Id(teacher.getId()).stream()
                .map(assignment -> toResponse(assignment.getSubject()))
                .distinct()
                .collect(Collectors.toList());
    }

    private SubjectResponse toResponse(Subject subject) {
        return new SubjectResponse(
                subject.getId(),
                subject.getSchoolId(),
                subject.getName(),
                subject.getCode()
        );
    }
}