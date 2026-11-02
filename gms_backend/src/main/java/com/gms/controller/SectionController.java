package com.gms.controller;

import com.gms.model.entity.Section;
import com.gms.model.request.SectionRequest;
import com.gms.model.response.SectionResponse;
import com.gms.security.TenantContext;
import com.gms.service.SectionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
@RequestMapping("/api/v1/sections")
public class SectionController {

    @Autowired
    private SectionService sectionService;

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<SectionResponse> createSection(@Valid @RequestBody SectionRequest sectionRequest) {
        return sectionService.createSection(sectionRequest);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<SectionResponse> updateSection(@PathVariable Integer id, 
                                                        @Valid @RequestBody SectionRequest sectionRequest) {
        return sectionService.updateSection(id, sectionRequest);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteSection(@PathVariable Integer id) {
        return sectionService.deleteSection(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<List<Section>> getAllSections() {
        return sectionService.getAllSections();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'TEACHER')")
    public ResponseEntity<Section> getSectionById(@PathVariable Integer id) {
        return sectionService.getSectionById(id);
    }
}