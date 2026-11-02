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
        // The load is scoped to the caller's school, so a foreign id yields nothing and the
        // hand-written school comparison the mechanism supersedes is gone with it.
        Optional<AcademicYear> academicYearOptional = academicYearRepository.findById(id);
        if (academicYearOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AcademicYear academicYear = academicYearOptional.get();
        academicYear.setName(academicYearRequest.getName());
        academicYear.setStartDate(academicYearRequest.getStartDate());
        academicYear.setEndDate(academicYearRequest.getEndDate());
        academicYear.setCurrent(academicYearRequest.isCurrent());

        return ResponseEntity.ok(academicYearRepository.save(academicYear));
    }

    @Override
    public ResponseEntity<?> deleteAcademicYear(Integer id) {
        // Same scoped load as the update path: absent for a foreign id, so notFound rather than
        // a 400 that tells the caller the request was malformed when it was merely not theirs.
        Optional<AcademicYear> academicYearOptional = academicYearRepository.findById(id);
        if (academicYearOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AcademicYear academicYear = academicYearOptional.get();
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
        // The scoped load already confines the answer to the caller's school, so the
        // hand-written comparison and its 403 are gone — a foreign id is 404.
        Optional<AcademicYear> academicYearOptional = academicYearRepository.findById(id);
        if (academicYearOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(academicYearOptional.get());
    }
}
