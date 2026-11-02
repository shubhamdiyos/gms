package com.gms.controller;

import com.gms.model.entity.GradingScheme;
import com.gms.model.request.GradingSchemeRequest;
import com.gms.model.response.GradingSchemeResponse;
import com.gms.security.TenantContext;
import com.gms.service.GradingSchemeService;
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
        return gradingSchemeService.createGradingScheme(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<GradingSchemeResponse> updateGradingScheme(@PathVariable Integer id,
                                                                   @Valid @RequestBody GradingSchemeRequest request) {
        return gradingSchemeService.updateGradingScheme(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteGradingScheme(@PathVariable Integer id) {
        return gradingSchemeService.deleteGradingScheme(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<List<GradingScheme>> getAllGradingSchemes() {
        return gradingSchemeService.getAllGradingSchemes();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<GradingScheme> getGradingSchemeById(@PathVariable Integer id) {
        return gradingSchemeService.getGradingSchemeById(id);
    }
}