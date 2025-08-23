package com.gms.controller;

import com.gms.model.entity.Parent;
import com.gms.model.request.ParentRequest;
import com.gms.model.response.ParentResponse;
import com.gms.model.response.StudentResponse;
import com.gms.service.ParentService;
import com.gms.service.impl.ParentServiceImpl;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:3000"})
@RequestMapping(Constants.API_BASE_V1 + "/parents")
public class ParentController extends AbstractCRUDController<Parent, Integer> {

    private final ParentService parentService;

    public ParentController(ParentServiceImpl parentServiceImpl) {
        super(parentServiceImpl);
        this.parentService = parentServiceImpl;
    }

    @PostMapping("/create")
    public ResponseEntity<ParentResponse> create(@Valid @RequestBody ParentRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return parentService.create(request, empId, schoolId);
    }

    @PutMapping("/update")
    public ResponseEntity<ParentResponse> update(@Valid @RequestBody ParentRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return parentService.update(request, empId, schoolId);
    }

    @GetMapping
    public ResponseEntity<List<Parent>> getAll() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<Parent> parents = parentService.findBySchool(schoolId);
        if (parents.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(parents);
    }

    @PatchMapping("/toggle")
    public ResponseEntity<?> toggleActive(@RequestParam Integer id, @RequestParam Boolean isActive) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return parentService.toggleParent(id, isActive, schoolId, empId);
    }

    // Role-specific endpoints for PARENT role
    @GetMapping("/profile")
    public ResponseEntity<ParentResponse> getParentProfile() {
        String username = SecurityUtil.getUsernameFromToken();
        ParentResponse response = parentService.getParentProfile(username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/children")
    public ResponseEntity<List<StudentResponse>> getChildren() {
        String username = SecurityUtil.getUsernameFromToken();
        List<StudentResponse> response = parentService.getChildren(username);
        return ResponseEntity.ok(response);
    }
}