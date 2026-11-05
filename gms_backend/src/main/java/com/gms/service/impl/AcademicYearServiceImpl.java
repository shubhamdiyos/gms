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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AcademicYearServiceImpl implements AcademicYearService {

    @Autowired
    private AcademicYearRepository academicYearRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Override
    @Transactional
    public ResponseEntity<?> createAcademicYear(AcademicYearRequest academicYearRequest) {
        School school = schoolRepository.findById(TenantContext.schoolId()).orElse(null);
        if (school == null) {
            return ResponseEntity.badRequest().body("School not found");
        }

        if (academicYearRepository.existsBySchool_IdAndName(TenantContext.schoolId(), academicYearRequest.getName())) {
            return ResponseEntity.badRequest().body("Academic year with the same name already exists");
        }

        if (academicYearRequest.isCurrent()) {
            clearCurrentYear(null);
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
    @Transactional
    public ResponseEntity<?> updateAcademicYear(Integer id, AcademicYearRequest academicYearRequest) {
        // The load is scoped to the caller's school, so a foreign id yields nothing and the
        // hand-written school comparison the mechanism supersedes is gone with it.
        Optional<AcademicYear> academicYearOptional = academicYearRepository.findById(id);
        if (academicYearOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AcademicYear academicYear = academicYearOptional.get();
        if (academicYearRequest.isCurrent()) {
            clearCurrentYear(academicYear.getId());
        }
        academicYear.setName(academicYearRequest.getName());
        academicYear.setStartDate(academicYearRequest.getStartDate());
        academicYear.setEndDate(academicYearRequest.getEndDate());
        academicYear.setCurrent(academicYearRequest.isCurrent());

        return ResponseEntity.ok(academicYearRepository.save(academicYear));
    }

    @Override
    @Transactional
    public ResponseEntity<?> deleteAcademicYear(Integer id) {
        // Same scoped load as the update path: absent for a foreign id, so notFound rather than
        // a 400 that tells the caller the request was malformed when it was merely not theirs.
        Optional<AcademicYear> academicYearOptional = academicYearRepository.findById(id);
        if (academicYearOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AcademicYear academicYear = academicYearOptional.get();
        academicYear.setStatus("0");
        // The current-year index ignores status, so a soft-deleted year that kept the flag would
        // keep the school from naming a successor.
        academicYear.setCurrent(false);
        academicYearRepository.save(academicYear);
        return ResponseEntity.ok("Academic year deleted successfully");
    }

    @Override
    public ResponseEntity<List<AcademicYear>> getAllAcademicYears() {
        return ResponseEntity.ok(academicYearRepository.findBySchool_IdAndStatus(TenantContext.schoolId(), "1"));
    }

    /**
     * Hands the current flag over to a single year.
     *
     * <p>{@code uq_academic_years_one_current} is a partial unique index, so two current rows for
     * one school are a constraint violation rather than a second "current". Hibernate executes
     * inserts before updates within a flush, so the old row has to be written and flushed before
     * the new one is saved; otherwise the insert still sees the old flag and is rejected.</p>
     *
     * @param keepId the year that is about to receive the flag, or {@code null} on create
     */
    private void clearCurrentYear(Integer keepId) {
        List<AcademicYear> currentYears = academicYearRepository.findCurrentYears(TenantContext.schoolId());
        boolean changed = false;
        for (AcademicYear year : currentYears) {
            if (keepId == null || !keepId.equals(year.getId())) {
                year.setCurrent(false);
                changed = true;
            }
        }
        if (changed) {
            academicYearRepository.saveAll(currentYears);
            academicYearRepository.flush();
        }
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
