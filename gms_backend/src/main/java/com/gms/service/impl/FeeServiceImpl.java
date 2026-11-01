package com.gms.service.impl;

import com.gms.model.entity.Fee;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.FeeRequest;
import com.gms.model.response.FeeResponse;
import com.gms.repository.FeeRepository;
import com.gms.security.TenantContext;
import com.gms.service.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class FeeServiceImpl implements FeeService {

    private final FeeRepository feeRepository;
    private final SchoolService schoolService;
    private final UserService userService;

    public FeeServiceImpl(FeeRepository feeRepository,
                         SchoolService schoolService,
                         UserService userService) {
        this.feeRepository = feeRepository;
        this.schoolService = schoolService;
        this.userService = userService;
    }

    @Override
    public ResponseEntity<FeeResponse> createFee(FeeRequest request) {
        // Validate school exists
        School school = schoolService.findById(TenantContext.schoolId())
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userService.findByEmployee_Id()
                .orElseThrow(() -> new EntityNotFoundException("Creator user not found"));

        // Create fee
        Fee fee = new Fee();
        fee.setSchool(school);
        fee.setFeeType(request.getFeeType());
        fee.setDescription(request.getDescription());
        fee.setAmount(request.getAmount());
        fee.setDueDate(request.getDueDate());
        fee.setAcademicYear(request.getAcademicYear());
        fee.setClassGrade(request.getClassGrade());
        fee.setStudentCategory(request.getStudentCategory());
        fee.setIsMandatory(request.getIsMandatory());
        fee.setStatus("ACTIVE");
        fee.setCreatedBy(creator);
        fee.setUpdatedBy(creator);

        Fee savedFee = feeRepository.save(fee);

        return ResponseEntity.ok(mapToResponse(savedFee));
    }

    @Override
    public ResponseEntity<FeeResponse> updateFee(Integer id, FeeRequest request) {
        // Validate fee exists
        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee not found"));

        // Validate school
        if (!fee.getSchool().getId().equals(TenantContext.schoolId())) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userService.findByEmployee_Id()
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Update fee
        fee.setFeeType(request.getFeeType());
        fee.setDescription(request.getDescription());
        fee.setAmount(request.getAmount());
        fee.setDueDate(request.getDueDate());
        fee.setAcademicYear(request.getAcademicYear());
        fee.setClassGrade(request.getClassGrade());
        fee.setStudentCategory(request.getStudentCategory());
        fee.setIsMandatory(request.getIsMandatory());
        fee.setUpdatedBy(updater);

        Fee savedFee = feeRepository.save(fee);

        return ResponseEntity.ok(mapToResponse(savedFee));
    }

    @Override
    public ResponseEntity<?> deleteFee(Integer id) {
        // Validate fee exists
        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee not found"));

        // Validate school
        if (!fee.getSchool().getId().equals(TenantContext.schoolId())) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userService.findByEmployee_Id()
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting status to ARCHIVED
        fee.setStatus("ARCHIVED");
        fee.setUpdatedBy(updater);

        feeRepository.save(fee);
        return ResponseEntity.ok("Fee deleted successfully");
    }

    @Override
    public ResponseEntity<List<Fee>> getAllFees() {
        List<Fee> fees = feeRepository.findBySchoolIdAndStatus(TenantContext.schoolId(), "ACTIVE");
        return ResponseEntity.ok(fees);
    }

    @Override
    public ResponseEntity<Fee> getFeeById(Integer id) {
        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee not found"));

        if (!fee.getSchool().getId().equals(TenantContext.schoolId())) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(fee);
    }

    @Override
    public ResponseEntity<List<Fee>> getFeesByAcademicYear(String academicYear) {
        List<Fee> fees = feeRepository.findBySchoolIdAndAcademicYearAndStatus(TenantContext.schoolId(), academicYear, "ACTIVE");
        return ResponseEntity.ok(fees);
    }

    @Override
    public BigDecimal getTotalFeeAmountBySchoolIdAndAcademicYear(String academicYear) {
        return feeRepository.getTotalFeeAmountBySchoolIdAndAcademicYear(TenantContext.schoolId(), academicYear);
    }

    private FeeResponse mapToResponse(Fee fee) {
        FeeResponse response = new FeeResponse();
        response.setId(fee.getId());
        response.setFeeType(fee.getFeeType());
        response.setAmount(fee.getAmount());
        response.setDueDate(fee.getDueDate());
        response.setAcademicYear(fee.getAcademicYear());
        return response;
    }
}