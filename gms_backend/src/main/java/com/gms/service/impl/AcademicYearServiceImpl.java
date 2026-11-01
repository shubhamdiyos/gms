package com.gms.service.impl;

import com.gms.model.entity.AcademicYear;
import com.gms.model.entity.School;
import com.gms.model.request.AcademicYearRequest;
import com.gms.repository.AcademicYearRepository;
import com.gms.repository.SchoolRepository;
import com.gms.security.TenantContext;
import com.gms.service.AcademicYearService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AcademicYearServiceImpl implements AcademicYearService {

    @Autowired
    private AcademicYearRepository academicYearRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Override
    public ResponseEntity<?> createAcademicYear(AcademicYearRequest academicYearRequest) {
        School school = schoolRepository.findById(TenantContext.schoolId()).orElse(null);
        if (school == null) {
            return ResponseEntity.badRequest().body("School not found");
        }

        if (academicYearRepository.existsBySchool_IdAndName(TenantContext.schoolId(), academicYearRequest.getName())) {
            return ResponseEntity.badRequest().body("Academic year with the same name already exists");
        }

        AcademicYear academicYear = new AcademicYear();
        academicYear.setSchool(school);
        academicYear.setName(academicYearRequest.getName());
        academicYear.setStartDate(academicYearRequest.getStartDate());
        academicYear.setEndDate(academicYearRequest.getEndDate());
        academicYear.setCurrent(academicYearRequest.isCurrent());

        return ResponseEntity.ok(academicYearRepository.save(academicYear));
    }

    @Override
    public ResponseEntity<?> updateAcademicYear(Integer id, AcademicYearRequest academicYearRequest) {
        Optional<AcademicYear> academicYearOptional = academicYearRepository.findById(id);
        if (academicYearOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("Academic year not found");
        }

        AcademicYear academicYear = academicYearOptional.get();
        if (!academicYear.getSchool().getId().equals(TenantContext.schoolId())) {
            return ResponseEntity.status(403).body("You are not authorized to update this academic year");
        }

        academicYear.setName(academicYearRequest.getName());
        academicYear.setStartDate(academicYearRequest.getStartDate());
        academicYear.setEndDate(academicYearRequest.getEndDate());
        academicYear.setCurrent(academicYearRequest.isCurrent());

        return ResponseEntity.ok(academicYearRepository.save(academicYear));
    }

    @Override
    public ResponseEntity<?> deleteAcademicYear(Integer id) {
        Optional<AcademicYear> academicYearOptional = academicYearRepository.findById(id);
        if (academicYearOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("Academic year not found");
        }

        AcademicYear academicYear = academicYearOptional.get();
        if (!academicYear.getSchool().getId().equals(TenantContext.schoolId())) {
            return ResponseEntity.status(403).body("You are not authorized to delete this academic year");
        }

        academicYear.setStatus("0");
        academicYearRepository.save(academicYear);
        return ResponseEntity.ok("Academic year deleted successfully");
    }

    @Override
    public ResponseEntity<List<AcademicYear>> getAllAcademicYears() {
        return ResponseEntity.ok(academicYearRepository.findBySchool_IdAndStatus(TenantContext.schoolId(), "1"));
    }

    @Override
    public ResponseEntity<AcademicYear> getAcademicYearById(Integer id) {
        Optional<AcademicYear> academicYearOptional = academicYearRepository.findById(id);
        if (academicYearOptional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        AcademicYear academicYear = academicYearOptional.get();
        if (!academicYear.getSchool().getId().equals(TenantContext.schoolId())) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(academicYear);
    }
}
