package com.gms.service.impl;

import com.gms.model.entity.FeeStructure;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.FeeStructureRequest;
import com.gms.model.response.FeeStructureResponse;
import com.gms.repository.FeeStructureRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.FeeStructureService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FeeStructureServiceImpl extends AbstractCRUDService<FeeStructure, Integer> implements FeeStructureService {

    private final FeeStructureRepository feeStructureRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public FeeStructureServiceImpl(FeeStructureRepository feeStructureRepository,
                                 SchoolRepository schoolRepository,
                                 UserRepository userRepository) {
        super(feeStructureRepository);
        this.feeStructureRepository = feeStructureRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<FeeStructureResponse> createFeeStructure(FeeStructureRequest request, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Create fee structure
        FeeStructure feeStructure = new FeeStructure();
        feeStructure.setSchool(school);
        feeStructure.setName(request.getName());
        feeStructure.setDescription(request.getDescription());
        feeStructure.setFeeCategory(request.getFeeCategory());
        feeStructure.setAcademicYear(request.getAcademicYear());
        feeStructure.setClassGrade(request.getClassGrade());
        feeStructure.setStudentCategory(request.getStudentCategory());
        feeStructure.setAmount(request.getAmount());
        feeStructure.setIsMandatory(request.getIsMandatory());
        feeStructure.setIsActive(request.getIsActive());
        feeStructure.setEffectiveFrom(request.getEffectiveFrom());
        feeStructure.setEffectiveTo(request.getEffectiveTo());
        feeStructure.setCreatedBy(creator);
        feeStructure.setUpdatedBy(creator);

        FeeStructure savedFeeStructure = feeStructureRepository.save(feeStructure);

        return ResponseEntity.ok(mapToResponse(savedFeeStructure));
    }

    @Override
    public ResponseEntity<FeeStructureResponse> updateFeeStructure(Integer id, FeeStructureRequest request, Integer schoolId, Integer empId) {
        // Validate fee structure exists
        FeeStructure feeStructure = feeStructureRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee structure not found"));

        // Validate school
        if (!feeStructure.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update fee structure
        feeStructure.setName(request.getName());
        feeStructure.setDescription(request.getDescription());
        feeStructure.setFeeCategory(request.getFeeCategory());
        feeStructure.setAcademicYear(request.getAcademicYear());
        feeStructure.setClassGrade(request.getClassGrade());
        feeStructure.setStudentCategory(request.getStudentCategory());
        feeStructure.setAmount(request.getAmount());
        feeStructure.setIsMandatory(request.getIsMandatory());
        feeStructure.setIsActive(request.getIsActive());
        feeStructure.setEffectiveFrom(request.getEffectiveFrom());
        feeStructure.setEffectiveTo(request.getEffectiveTo());
        feeStructure.setUpdatedBy(updater);

        FeeStructure savedFeeStructure = feeStructureRepository.save(feeStructure);

        return ResponseEntity.ok(mapToResponse(savedFeeStructure));
    }

    @Override
    public ResponseEntity<?> deleteFeeStructure(Integer id, Integer schoolId, Integer empId) {
        // Validate fee structure exists
        FeeStructure feeStructure = feeStructureRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee structure not found"));

        // Validate school
        if (!feeStructure.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting active to false
        feeStructure.setIsActive(false);
        feeStructure.setUpdatedBy(updater);

        feeStructureRepository.save(feeStructure);
        return ResponseEntity.ok("Fee structure deleted successfully");
    }

    @Override
    public ResponseEntity<List<FeeStructure>> getAllFeeStructures(Integer schoolId) {
        List<FeeStructure> feeStructures = feeStructureRepository.findBySchoolIdAndIsActive(schoolId, true);
        return ResponseEntity.ok(feeStructures);
    }

    @Override
    public ResponseEntity<FeeStructure> getFeeStructureById(Integer id, Integer schoolId) {
        FeeStructure feeStructure = feeStructureRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee structure not found"));

        if (!feeStructure.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(feeStructure);
    }

    @Override
    public ResponseEntity<List<FeeStructure>> getFeeStructuresByAcademicYear(Integer schoolId, String academicYear) {
        List<FeeStructure> feeStructures = feeStructureRepository.findBySchoolIdAndAcademicYearAndIsActive(schoolId, academicYear, true);
        return ResponseEntity.ok(feeStructures);
    }

    private FeeStructureResponse mapToResponse(FeeStructure feeStructure) {
        FeeStructureResponse response = new FeeStructureResponse();
        response.setId(feeStructure.getId());
        response.setSchoolId(feeStructure.getSchool().getId());
        response.setName(feeStructure.getName());
        response.setDescription(feeStructure.getDescription());
        response.setFeeCategory(feeStructure.getFeeCategory());
        response.setAcademicYear(feeStructure.getAcademicYear());
        response.setClassGrade(feeStructure.getClassGrade());
        response.setStudentCategory(feeStructure.getStudentCategory());
        response.setAmount(feeStructure.getAmount());
        response.setIsMandatory(feeStructure.getIsMandatory());
        response.setIsActive(feeStructure.getIsActive());
        response.setEffectiveFrom(feeStructure.getEffectiveFrom());
        response.setEffectiveTo(feeStructure.getEffectiveTo());
        return response;
    }
}