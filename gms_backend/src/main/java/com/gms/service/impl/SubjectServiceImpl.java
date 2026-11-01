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
import com.gms.security.TenantContext;
import com.gms.service.SubjectService;
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
public class SubjectServiceImpl implements SubjectService {

    private final SubjectRepository subjectRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final TeacherAssignmentRepository teacherAssignmentRepository;

    public SubjectServiceImpl(SubjectRepository subjectRepository, SchoolRepository schoolRepository, UserRepository userRepository, TeacherAssignmentRepository teacherAssignmentRepository) {
        this.subjectRepository = subjectRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
        this.teacherAssignmentRepository = teacherAssignmentRepository;
    }

    @Override
    public ResponseEntity<SubjectResponse> create(SubjectRequest request) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            return ResponseEntity.badRequest().build();
        }

        Subject subject = createSubjectFromRequest(request, new Subject());
        Subject saved = subjectRepository.save(subject);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    public Subject createSubjectFromRequest(SubjectRequest request, Subject subject) {
        if (TenantContext.employeeId() == null || TenantContext.schoolId() == null) {
            throw new IllegalArgumentException("Employee ID or School ID cannot be null");
        }

        if (subjectRepository.existsBySchool_IdAndCode(TenantContext.schoolId(), request.getCode())) {
            throw new IllegalArgumentException("Subject code already exists in this school");
        }

        BeanUtils.copyProperties(request, subject, "id", "createdAt", "updatedAt", "version");

        School school = schoolRepository.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        subject.setSchool(school);

        return subject;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Subject> findBySchool() {
        return subjectRepository.findAllBySchool_IdAndStatusNot(TenantContext.schoolId(), "0");
    }

    @Override
    @Transactional(readOnly = true)
    public Subject getById(Integer id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));
        // What remains is the archive rule, which is business state rather than tenancy and which
        // the platform owner is not exempt from either. The school comparison this line replaces was
        // a second copy of the predicate the scoped findById already applied, and because the
        // bootstrapped platform owner carries its own SYSTEM school id it refused that owner too.
        if ("0".equals(subject.getStatus())) {
            throw new IllegalArgumentException("Subject is archived");
        }
        return subject;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Subject> findById(Integer id) {
        return subjectRepository.findById(id);
    }

    @Override
    @Transactional
    public boolean deleteWithinSchool(Integer id) {
        // The load is scoped to the caller's school, so a foreign id yields nothing; the school
        // comparison this method used to make was a second copy of that predicate, and it refused
        // the platform owner whose own SYSTEM school id differs from the row's.
        Subject subject = subjectRepository.findById(id).orElse(null);
        if (subject == null) {
            return false;
        }
        subjectRepository.delete(subject);
        return true;
    }

    @Override
    public ResponseEntity<SubjectResponse> update(SubjectRequest request) {
        // For Subject, we need to get the ID from the path parameter, not from request
        // This will be handled by the controller
        throw new UnsupportedOperationException("Update method requires ID parameter from controller");
    }

    @Override
    public ResponseEntity<SubjectResponse> updateSubject(Integer id, SubjectRequest request) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));

        // The scoped load already answered the tenancy question; what remains is the archive rule,
        // which is business state rather than tenancy and which the platform owner is not exempt
        // from either.
        if ("0".equals(subject.getStatus())) {
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

        School school = schoolRepository.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        subject.setSchool(school);

        Subject saved = subjectRepository.save(subject);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    public ResponseEntity<?> toggleSubject(Integer id, Boolean isActive) {
        Subject existing = subjectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Subject not found"));

        // The toggle writes to the row the scoped load returned, so tenancy was settled at the load;
        // the school comparison this path used to make only refused the platform owner.
        existing.setStatus(isActive ? "1" : "0");

        School school = schoolRepository.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));
        existing.setSchool(school);

        Subject saved = subjectRepository.save(existing);
        return ResponseEntity.ok(toResponse(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubjectResponse> findSubjectsForAuthenticatedTeacher() {
        String username = TenantContext.username();
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

        return teacherAssignmentRepository.findByTeacher_School_IdAndTeacher_Id(TenantContext.schoolId(), teacher.getId()).stream()
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