package com.gms.service.impl;

import com.gms.model.entity.Parent;
import com.gms.model.entity.Student;
import com.gms.model.entity.User;
import com.gms.model.request.ParentStudentRequest;
import com.gms.model.response.ParentResponse;
import com.gms.model.response.StudentResponse;
import com.gms.repository.ParentRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.StudentRepository;
import com.gms.repository.UserRepository;
import com.gms.security.TenantContext;
import com.gms.service.ParentStudentService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ParentStudentServiceImpl implements ParentStudentService {

    private final ParentRepository parentRepository;
    private final StudentRepository studentRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    @Autowired
    public ParentStudentServiceImpl(ParentRepository parentRepository,
                                   StudentRepository studentRepository,
                                   SchoolRepository schoolRepository,
                                   UserRepository userRepository) {
        this.parentRepository = parentRepository;
        this.studentRepository = studentRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public ResponseEntity<?> assignStudentsToParent(ParentStudentRequest request) {
        // Validate parent exists
        Parent parent = parentRepository.findById(request.getParentId())
                .orElseThrow(() -> new EntityNotFoundException("Parent not found"));

        // A parent row is global — parents.email is unique across the platform — but a school may
        // re-point only a family that is empty or entirely its own. The guard this replaces read
        // the FIRST student's school, so a family whose first child was in the caller's school
        // passed even when later children belonged to another school, and the clear() that used to
        // follow deleted those foreign join rows on save (ISO-01).
        if (parentBelongsToAnotherSchool(parent)) {
            return ResponseEntity.status(403).body("Parent does not belong to this school");
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(TenantContext.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Validate all students exist. The scoped StudentRepository.findById already answers the
        // tenancy question — a foreign id throws — so the hand-written school comparison is gone.
        // The rows actually attached below still come from the school-filtered query.
        for (Integer studentId : request.getStudentIds()) {
            studentRepository.findById(studentId)
                    .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        }

        // Get all students
        List<Student> students = studentRepository.findBySchool_IdAndIdIn(TenantContext.schoolId(), request.getStudentIds());

        // Replace this school's links only. The guard above means no foreign link can exist here,
        // but the removal stays scoped to the caller's school so the two rules cannot drift.
        parent.getStudents().removeIf(s -> s.getSchoolId().equals(TenantContext.schoolId()));
        parent.getStudents().addAll(students);

        parentRepository.save(parent);

        return ResponseEntity.ok("Students assigned to parent successfully");
    }

    @Override
    @Transactional
    public ResponseEntity<?> removeStudentFromParent(Integer parentId, Integer studentId) {
        // Validate parent exists
        Parent parent = parentRepository.findById(parentId)
                .orElseThrow(() -> new EntityNotFoundException("Parent not found"));

        // Same family rule as assignStudentsToParent: a school removes its own links, never a
        // foreign family's (ISO-03).
        if (parentBelongsToAnotherSchool(parent)) {
            return ResponseEntity.status(403).body("Parent does not belong to this school");
        }

        // Validate student exists. The scoped load already filters by the caller's school, so a
        // foreign id throws rather than reaching a hand-written comparison.
        studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(TenantContext.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Remove student from parent
        parent.getStudents().removeIf(s -> s.getId().equals(studentId));
        parentRepository.save(parent);

        return ResponseEntity.ok("Student removed from parent successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<Student>> getStudentsForParent(Integer parentId) {
        // Validate parent exists
        Parent parent = parentRepository.findById(parentId)
                .orElseThrow(() -> new EntityNotFoundException("Parent not found"));

        // Validate school
        if (parentBelongsToAnotherSchool(parent)) {
            return ResponseEntity.status(403).build();
        }

        // The guard above is a tenant rule and admits every parent of the school equally; a PARENT
        // caller may read only its own family, or the route hands one family's children to another
        // (ISO-02).
        if (isParentCaller()) {
            User self = userRepository.findByUsername(TenantContext.username())
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
            if (self.getParent() == null || !self.getParent().getId().equals(parent.getId())) {
                return ResponseEntity.status(403).build();
            }
        }

        return ResponseEntity.ok(parent.getStudents());
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<List<ParentResponse>> getParentsForStudent(Integer studentId) {
        // Validate student exists; the scoped load already confines the answer to the caller's
        // school, so the hand-written comparison and its 403 are gone — a foreign id is 404.
        studentRepository.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));

        // Responses, not entities: Parent carries a lazy user, and with open-in-view=false the
        // message converter meets that proxy after the session has closed. Mapping runs here,
        // inside the transaction, while the proxies still load.
        List<Parent> parents = parentRepository.findByStudents_School_IdAndStudents_Id(TenantContext.schoolId(), studentId);
        return ResponseEntity.ok(parents.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    /**
     * Whether any of the family's existing children belongs to a school other than the caller's.
     * The empty family belongs to no school and is therefore claimable by the caller.
     */
    private boolean parentBelongsToAnotherSchool(Parent parent) {
        return parent.getStudents().stream()
                .anyMatch(s -> !s.getSchoolId().equals(TenantContext.schoolId()));
    }

    /**
     * A caller whose token carries PARENT and no administrative role. Role sets are disjoint in
     * practice, but the explicit negation keeps a future combined role from widening the read.
     */
    private boolean isParentCaller() {
        return TenantContext.roles().contains("PARENT")
                && !TenantContext.roles().contains("ADMIN")
                && !TenantContext.roles().contains("SUPERADMIN");
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
