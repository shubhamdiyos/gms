package com.gms.service.impl;

import com.gms.model.entity.GradingScheme;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.GradingSchemeRequest;
import com.gms.model.response.GradingSchemeResponse;
import com.gms.repository.GradingSchemeRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.GradingSchemeService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GradingSchemeServiceImpl extends AbstractCRUDService<GradingScheme, Integer> implements GradingSchemeService {

    private final GradingSchemeRepository gradingSchemeRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public GradingSchemeServiceImpl(GradingSchemeRepository gradingSchemeRepository,
                                  SchoolRepository schoolRepository,
                                  UserRepository userRepository) {
        super(gradingSchemeRepository);
        this.gradingSchemeRepository = gradingSchemeRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<GradingSchemeResponse> createGradingScheme(GradingSchemeRequest request, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Create grading scheme
        GradingScheme gradingScheme = new GradingScheme();
        gradingScheme.setSchool(school);
        gradingScheme.setName(request.getName());
        gradingScheme.setDescription(request.getDescription());
        gradingScheme.setMinPercentage(request.getMinPercentage());
        gradingScheme.setMaxPercentage(request.getMaxPercentage());
        gradingScheme.setGrade(request.getGrade());
        gradingScheme.setGradePoint(request.getGradePoint());
        gradingScheme.setRemarks(request.getRemarks());
        gradingScheme.setIsActive(request.getIsActive());
        gradingScheme.setCreatedBy(creator);
        gradingScheme.setUpdatedBy(creator);

        GradingScheme savedGradingScheme = gradingSchemeRepository.save(gradingScheme);

        return ResponseEntity.ok(mapToResponse(savedGradingScheme));
    }

    @Override
    public ResponseEntity<GradingSchemeResponse> updateGradingScheme(Integer id, GradingSchemeRequest request, Integer schoolId, Integer empId) {
        // Validate grading scheme exists
        GradingScheme gradingScheme = gradingSchemeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Grading scheme not found"));

        // Validate school
        if (!gradingScheme.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update grading scheme
        gradingScheme.setName(request.getName());
        gradingScheme.setDescription(request.getDescription());
        gradingScheme.setMinPercentage(request.getMinPercentage());
        gradingScheme.setMaxPercentage(request.getMaxPercentage());
        gradingScheme.setGrade(request.getGrade());
        gradingScheme.setGradePoint(request.getGradePoint());
        gradingScheme.setRemarks(request.getRemarks());
        gradingScheme.setIsActive(request.getIsActive());
        gradingScheme.setUpdatedBy(updater);

        GradingScheme savedGradingScheme = gradingSchemeRepository.save(gradingScheme);

        return ResponseEntity.ok(mapToResponse(savedGradingScheme));
    }

    @Override
    public ResponseEntity<?> deleteGradingScheme(Integer id, Integer schoolId, Integer empId) {
        // Validate grading scheme exists
        GradingScheme gradingScheme = gradingSchemeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Grading scheme not found"));

        // Validate school
        if (!gradingScheme.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting active to false
        gradingScheme.setIsActive(false);
        gradingScheme.setUpdatedBy(updater);

        gradingSchemeRepository.save(gradingScheme);
        return ResponseEntity.ok("Grading scheme deleted successfully");
    }

    @Override
    public ResponseEntity<List<GradingScheme>> getAllGradingSchemes(Integer schoolId) {
        List<GradingScheme> gradingSchemes = gradingSchemeRepository.findBySchoolIdAndIsActive(schoolId, true);
        return ResponseEntity.ok(gradingSchemes);
    }

    @Override
    public ResponseEntity<GradingScheme> getGradingSchemeById(Integer id, Integer schoolId) {
        GradingScheme gradingScheme = gradingSchemeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Grading scheme not found"));

        if (!gradingScheme.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(gradingScheme);
    }

    private GradingSchemeResponse mapToResponse(GradingScheme gradingScheme) {
        GradingSchemeResponse response = new GradingSchemeResponse();
        response.setId(gradingScheme.getId());
        response.setSchoolId(gradingScheme.getSchool().getId());
        response.setName(gradingScheme.getName());
        response.setDescription(gradingScheme.getDescription());
        response.setMinPercentage(gradingScheme.getMinPercentage());
        response.setMaxPercentage(gradingScheme.getMaxPercentage());
        response.setGrade(gradingScheme.getGrade());
        response.setGradePoint(gradingScheme.getGradePoint());
        response.setRemarks(gradingScheme.getRemarks());
        response.setIsActive(gradingScheme.getIsActive());
        return response;
    }
}