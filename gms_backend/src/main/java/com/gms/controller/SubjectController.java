package com.gms.controller;

import com.gms.model.entity.Subject;
import com.gms.model.request.SubjectRequest;
import com.gms.model.response.SubjectResponse;
import com.gms.security.TenantContext;
import com.gms.service.SubjectService;
import com.gms.util.Constants;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Subject administration and the teacher-facing read.
 *
 * <p>This controller no longer extends {@code AbstractCRUDController}, which supplied an unguarded
 * {@code GET /{id}} and {@code DELETE /{id}}. Both are declared below and both are scoped to the
 * caller's school. The delete carries its own {@code @PreAuthorize} because the URL layer admits
 * {@code TEACHER} to {@code /subjects/**}: without a method-level guard a teacher could delete any
 * subject, in any school.</p>
 */
@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping(Constants.API_BASE_V1 + "/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<SubjectResponse> create(@Valid @RequestBody SubjectRequest request) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return subjectService.create(request);
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<SubjectResponse> update(@PathVariable Integer id, @Valid @RequestBody SubjectRequest request) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return subjectService.updateSubject(id, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<List<Subject>> getAll() {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<Subject> list = subjectService.findBySchool();
        if (list.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    public ResponseEntity<Subject> getById(@PathVariable Integer id) {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            Subject subject = subjectService.getById(id);
            return ResponseEntity.ok(subject);
        } catch (jakarta.persistence.EntityNotFoundException ex) {
            return ResponseEntity.status(404).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    /**
     * Deletes a subject of the caller's own school.
     *
     * <p>The frontend calls this route ({@code SubjectsPage.jsx}), so it is declared here rather
     * than inherited. The inherited version had no guard at all and no tenant predicate: the URL
     * layer admits {@code TEACHER} to {@code /subjects/**}, so a teacher could delete a subject in
     * any school.</p>
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        Integer schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        if (!subjectService.deleteWithinSchool(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/toggle")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> toggleActive(@RequestParam Integer id, @RequestParam Boolean isActive) {
        Integer empId = TenantContext.employeeId();
        Integer schoolId = TenantContext.schoolId();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return subjectService.toggleSubject(id, isActive);
    }

    @GetMapping("/teacher/assigned")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<List<SubjectResponse>> getAssignedSubjectsForTeacher() {
        List<SubjectResponse> subjects = subjectService.findSubjectsForAuthenticatedTeacher();
        if (subjects.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(subjects);
    }
}
