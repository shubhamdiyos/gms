package com.gms.controller;

import com.gms.model.entity.School;
import com.gms.model.request.SchoolRequest;
import com.gms.model.response.SchoolResponse;
import com.gms.service.SchoolService;
import com.gms.service.impl.SchoolServiceImpl;
import com.gms.util.Constants;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:3000"})
@RequestMapping(Constants.API_BASE_V1 + "/schools")
public class SchoolController extends AbstractCRUDController<School, Integer> {

    private final SchoolService schoolService;
    private final SchoolServiceImpl schoolServiceImpl;

    public SchoolController(SchoolService schoolService, SchoolServiceImpl schoolServiceImpl) {
        super(schoolServiceImpl);
        this.schoolService = schoolService;
        this.schoolServiceImpl = schoolServiceImpl;
    }

    @PostMapping("/create")
    public ResponseEntity<SchoolResponse> create(@Valid @RequestBody SchoolRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return schoolService.create(request, empId, schoolId);
    }

    @PutMapping("/update")
    public ResponseEntity<SchoolResponse> update(@Valid @RequestBody SchoolRequest request) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return schoolService.update(request, empId, schoolId);
    }

    @GetMapping
    public ResponseEntity<List<School>> getAll() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        List<School> list = schoolService.findBySchool(schoolId);
        if (list.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<School> getById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            School school = schoolService.getById(id, schoolId);
            return ResponseEntity.ok(school);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/toggle")
    public ResponseEntity<?> toggleActive(@RequestParam Integer id, @RequestParam Boolean isActive) {
        Integer empId = SecurityUtil.getEmpIdFromToken();
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        if (empId == null || schoolId == null) {
            return ResponseEntity.status(401).build();
        }
        return schoolService.toggleSchool(id, isActive, schoolId, empId);
    }
}
