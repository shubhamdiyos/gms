package com.gms.controller;

import com.gms.model.entity.Classroom;
import com.gms.model.request.ClassroomRequest;
import com.gms.model.response.ClassroomResponse;
import com.gms.service.ClassroomService;
import com.gms.service.impl.ClassroomServiceImpl;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping(Constants.API_BASE_V1 + "/classrooms")
public class ClassroomController extends AbstractCRUDController<Classroom, Integer> {

    private final ClassroomService classroomService;

    public ClassroomController(ClassroomServiceImpl classroomServiceImpl) {
        super(classroomServiceImpl);
        this.classroomService = classroomServiceImpl;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ClassroomResponse> create(@Valid @RequestBody ClassroomRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return classroomService.create(request, empId, schoolId);
    }

    @PutMapping("/update")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ClassroomResponse> update(@Valid @RequestBody ClassroomRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return classroomService.update(request, empId, schoolId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    @Override
    public ResponseEntity<List<Classroom>> getAll() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<Classroom> list = classroomService.findBySchool(schoolId);
        if (list.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN', 'TEACHER')")
    @Override
    public ResponseEntity<Classroom> getById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            Classroom classroom = classroomService.getById(id, schoolId);
            return ResponseEntity.ok(classroom);
        } catch (jakarta.persistence.EntityNotFoundException ex) {
            return ResponseEntity.status(404).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    @PatchMapping("/toggle")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPERADMIN')")
    public ResponseEntity<?> toggleActive(@RequestParam Integer id, @RequestParam Boolean isActive) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return classroomService.toggleClassroom(id, isActive, schoolId, empId);
    }

    @GetMapping("/teacher/assigned")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<List<ClassroomResponse>> getAssignedClassroomsForTeacher() {
        List<ClassroomResponse> classrooms = classroomService.findClassroomsForAuthenticatedTeacher();
        if (classrooms.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(classrooms);
    }
}
