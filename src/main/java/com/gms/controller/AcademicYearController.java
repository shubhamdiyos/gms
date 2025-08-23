package com.gms.controller;

import com.gms.model.entity.AcademicYear;
import com.gms.model.request.AcademicYearRequest;
import com.gms.service.AcademicYearService;
import com.gms.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/academic-years")
public class AcademicYearController {

    @Autowired
    private AcademicYearService academicYearService;

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> createAcademicYear(@RequestBody AcademicYearRequest academicYearRequest) {
        return academicYearService.createAcademicYear(academicYearRequest, SecurityUtil.getSchoolIdFromToken());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> updateAcademicYear(@PathVariable Integer id, @RequestBody AcademicYearRequest academicYearRequest) {
        return academicYearService.updateAcademicYear(id, academicYearRequest, SecurityUtil.getSchoolIdFromToken());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteAcademicYear(@PathVariable Integer id) {
        return academicYearService.deleteAcademicYear(id, SecurityUtil.getSchoolIdFromToken());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<AcademicYear>> getAllAcademicYears() {
        return academicYearService.getAllAcademicYears(SecurityUtil.getSchoolIdFromToken());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<AcademicYear> getAcademicYearById(@PathVariable Integer id) {
        return academicYearService.getAcademicYearById(id, SecurityUtil.getSchoolIdFromToken());
    }
}