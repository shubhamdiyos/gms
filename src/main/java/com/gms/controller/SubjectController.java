package com.gms.controller;

import com.gms.model.entity.Subject;
import com.gms.model.request.SubjectRequest;
import com.gms.model.response.SubjectResponse;
import com.gms.service.SubjectService;
import com.gms.service.impl.SubjectServiceImpl;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:3000"})
@RequestMapping(Constants.API_BASE_V1 + "/subjects")
public class SubjectController extends AbstractCRUDController<Subject, Integer> {

    private final SubjectService subjectService;

    public SubjectController(SubjectServiceImpl subjectServiceImpl) {
        super(subjectServiceImpl);
        this.subjectService = subjectServiceImpl;
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubjectResponse> create(@Valid @RequestBody SubjectRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return subjectService.create(request, empId, schoolId);
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubjectResponse> update(@PathVariable Integer id, @Valid @RequestBody SubjectRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return subjectService.updateSubject(id, request, empId, schoolId);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Override
    public ResponseEntity<List<Subject>> getAll() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<Subject> list = subjectService.findBySchool(schoolId);
        if (list.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Override
    public ResponseEntity<Subject> getById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            Subject subject = subjectService.getById(id, schoolId);
            return ResponseEntity.ok(subject);
        } catch (jakarta.persistence.EntityNotFoundException ex) {
            return ResponseEntity.status(404).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @PatchMapping("/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleActive(@RequestParam Integer id, @RequestParam Boolean isActive) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return subjectService.toggleSubject(id, isActive, schoolId, empId);
    }

    @GetMapping("/teacher/assigned")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<SubjectResponse>> getAssignedSubjectsForTeacher() {
        List<SubjectResponse> subjects = subjectService.findSubjectsForAuthenticatedTeacher();
        if (subjects.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(subjects);
    }
}
