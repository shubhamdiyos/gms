package com.gms.controller;

import com.gms.model.entity.GradingScheme;
import com.gms.model.request.GradingSchemeRequest;
import com.gms.model.response.GradingSchemeResponse;
import com.gms.service.GradingSchemeService;
import com.gms.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/grading-schemes")
public class GradingSchemeController {

    private final GradingSchemeService gradingSchemeService;

    public GradingSchemeController(GradingSchemeService gradingSchemeService) {
        this.gradingSchemeService = gradingSchemeService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<GradingSchemeResponse> createGradingScheme(@Valid @RequestBody GradingSchemeRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return gradingSchemeService.createGradingScheme(request, schoolId, empId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<GradingSchemeResponse> updateGradingScheme(@PathVariable Integer id,
                                                                   @Valid @RequestBody GradingSchemeRequest request) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return gradingSchemeService.updateGradingScheme(id, request, schoolId, empId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteGradingScheme(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        Integer empId = SecurityUtil.getEmpIdFromToken();
        return gradingSchemeService.deleteGradingScheme(id, schoolId, empId);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<List<GradingScheme>> getAllGradingSchemes() {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return gradingSchemeService.getAllGradingSchemes(schoolId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<GradingScheme> getGradingSchemeById(@PathVariable Integer id) {
        Integer schoolId = SecurityUtil.getSchoolIdFromToken();
        return gradingSchemeService.getGradingSchemeById(id, schoolId);
    }
}