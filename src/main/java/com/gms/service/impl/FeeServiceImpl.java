package com.gms.service.impl;

import com.gms.model.entity.Fee;
import com.gms.model.entity.School;
import com.gms.model.entity.User;
import com.gms.model.request.FeeRequest;
import com.gms.model.response.FeeResponse;
import com.gms.repository.FeeRepository;
import com.gms.repository.SchoolRepository;
import com.gms.repository.UserRepository;
import com.gms.service.AbstractCRUDService;
import com.gms.service.FeeService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class FeeServiceImpl extends AbstractCRUDService<Fee, Integer> implements FeeService {

    private final FeeRepository feeRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    public FeeServiceImpl(FeeRepository feeRepository,
                         SchoolRepository schoolRepository,
                         UserRepository userRepository) {
        super(feeRepository);
        this.feeRepository = feeRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<FeeResponse> createFee(FeeRequest request, Integer schoolId, Integer empId) {
        // Validate school exists
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new EntityNotFoundException("School not found"));

        // Validate creator exists
        User creator = userRepository.findByEmployee_Id(empId)
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
    public ResponseEntity<FeeResponse> updateFee(Integer id, FeeRequest request, Integer schoolId, Integer empId) {
        // Validate fee exists
        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee not found"));

        // Validate school
        if (!fee.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
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
    public ResponseEntity<?> deleteFee(Integer id, Integer schoolId, Integer empId) {
        // Validate fee exists
        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee not found"));

        // Validate school
        if (!fee.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        // Validate updater exists
        User updater = userRepository.findByEmployee_Id(empId)
                .orElseThrow(() -> new EntityNotFoundException("Updater user not found"));

        // Soft delete by setting status to ARCHIVED
        fee.setStatus("ARCHIVED");
        fee.setUpdatedBy(updater);

        feeRepository.save(fee);
        return ResponseEntity.ok("Fee deleted successfully");
    }

    @Override
    public ResponseEntity<List<Fee>> getAllFees(Integer schoolId) {
        List<Fee> fees = feeRepository.findBySchoolIdAndStatus(schoolId, "ACTIVE");
        return ResponseEntity.ok(fees);
    }

    @Override
    public ResponseEntity<Fee> getFeeById(Integer id, Integer schoolId) {
        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fee not found"));

        if (!fee.getSchool().getId().equals(schoolId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(fee);
    }

    @Override
    public ResponseEntity<List<Fee>> getFeesByAcademicYear(Integer schoolId, String academicYear) {
        List<Fee> fees = feeRepository.findBySchoolIdAndAcademicYearAndStatus(schoolId, academicYear, "ACTIVE");
        return ResponseEntity.ok(fees);
    }

    @Override
    public BigDecimal getTotalFeeAmountBySchoolIdAndAcademicYear(Integer schoolId, String academicYear) {
        return feeRepository.getTotalFeeAmountBySchoolIdAndAcademicYear(schoolId, academicYear);
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